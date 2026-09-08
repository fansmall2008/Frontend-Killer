package com.gamelist.service.impl;

import com.gamelist.service.ScraperSettingsService;
import com.gamelist.util.EncryptionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class ScraperSettingsServiceImpl implements ScraperSettingsService {
    
    private static final String SETTINGS_FILE = "/data/config/scraper-settings.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    public void init() {
        try {
            Path configDir = Paths.get("/data/config");
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            File settingsFile = new File(SETTINGS_FILE);
            if (!settingsFile.exists()) {
                settingsFile.createNewFile();
                ObjectNode defaultSettings = objectMapper.createObjectNode();
                defaultSettings.put("username", "");
                defaultSettings.put("password", "");
                objectMapper.writeValue(settingsFile, defaultSettings);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public Map<String, String> getSettings() {
        Map<String, String> settings = new HashMap<>();
        try {
            File settingsFile = new File(SETTINGS_FILE);
            if (settingsFile.exists()) {
                ObjectNode node = objectMapper.readValue(settingsFile, ObjectNode.class);
                String storedUsername = node.has("username") ? node.get("username").asText() : "";
                String storedPassword = node.has("password") ? node.get("password").asText() : "";
                
                // 尝试解密，如果解密失败则直接使用原值
                try {
                    if (EncryptionUtil.isValidEncrypted(storedUsername)) {
                        settings.put("username", EncryptionUtil.decrypt(storedUsername));
                    } else {
                        settings.put("username", storedUsername);
                    }
                } catch (Exception e) {
                    settings.put("username", storedUsername);
                }
                
                try {
                    if (EncryptionUtil.isValidEncrypted(storedPassword)) {
                        settings.put("password", EncryptionUtil.decrypt(storedPassword));
                    } else {
                        settings.put("password", storedPassword);
                    }
                } catch (Exception e) {
                    settings.put("password", storedPassword);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return settings;
    }
    
    @Override
    public void saveSettings(String username, String password) {
        try {
            File settingsFile = new File(SETTINGS_FILE);
            ObjectNode node = objectMapper.createObjectNode();
            
            if (username != null && !username.isEmpty()) {
                node.put("username", EncryptionUtil.encrypt(username));
            } else {
                node.put("username", "");
            }
            
            if (password != null && !password.isEmpty()) {
                node.put("password", EncryptionUtil.encrypt(password));
            } else {
                node.put("password", "");
            }
            
            objectMapper.writeValue(settingsFile, node);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void clearSettings() {
        try {
            File settingsFile = new File(SETTINGS_FILE);
            ObjectNode node = objectMapper.createObjectNode();
            node.put("username", "");
            node.put("password", "");
            objectMapper.writeValue(settingsFile, node);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}