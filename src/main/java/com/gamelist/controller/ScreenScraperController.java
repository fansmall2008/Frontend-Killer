package com.gamelist.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.config.ScreenScraperConfig;
import com.gamelist.service.ScraperSettingsService;
import com.gamelist.service.ScraperSystemService;
import com.gamelist.service.ScreenScraperApiService;

@RestController
@RequestMapping("/api/screenscraper")
public class ScreenScraperController {
    
    private final ScreenScraperConfig scraperConfig;
    private final ScraperSystemService scraperSystemService;
    private final ScraperSettingsService scraperSettingsService;
    private final ScreenScraperApiService screenScraperApiService;
    
    public ScreenScraperController(ScreenScraperConfig scraperConfig, 
                                   ScraperSystemService scraperSystemService,
                                   ScraperSettingsService scraperSettingsService,
                                   ScreenScraperApiService screenScraperApiService) {
        this.scraperConfig = scraperConfig;
        this.scraperSystemService = scraperSystemService;
        this.scraperSettingsService = scraperSettingsService;
        this.screenScraperApiService = screenScraperApiService;
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> settings = scraperSettingsService.getSettings();
        
        boolean hasUserCredentials = settings.containsKey("username") && !settings.get("username").isEmpty()
                && settings.containsKey("password") && !settings.get("password").isEmpty();
        
        response.put("success", true);
        response.put("hasUserCredentials", hasUserCredentials);
        response.put("message", hasUserCredentials ? "已配置用户凭证" : "未配置用户凭证，将使用游客模式（有限制）");
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody(required = false) Map<String, String> credentials) {
        Map<String, Object> response = new HashMap<>();
        
        String username = null;
        String password = null;
        
        if (credentials != null) {
            username = credentials.get("username");
            password = credentials.get("password");
        }
        
        boolean useGuestMode = (username == null || username.isEmpty() || password == null || password.isEmpty());
        
        try {
            boolean connected = screenScraperApiService.testConnection(username, password);
            
            if (connected) {
                if (useGuestMode) {
                    response.put("success", true);
                    response.put("message", "连接测试成功（游客模式）- 每日请求限制：约20000次");
                    response.put("mode", "guest");
                } else {
                    response.put("success", true);
                    response.put("message", "连接测试成功（用户模式）- 每日请求限制更高");
                    response.put("mode", "user");
                }
            } else {
                response.put("success", false);
                response.put("message", useGuestMode ? "游客模式连接失败" : "用户凭证验证失败");
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "连接失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> initSystems() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, String> settings = scraperSettingsService.getSettings();
            String username = settings.getOrDefault("username", "");
            String password = settings.getOrDefault("password", "");
            
            scraperSystemService.clearAll();
            
            int count = screenScraperApiService.fetchAndSaveSystems(username, password, scraperSystemService);
            
            if (count == 0) {
                response.put("success", false);
                response.put("message", "未获取到系统数据");
                return ResponseEntity.ok(response);
            }
            
            response.put("success", true);
            response.put("message", "系统初始化成功");
            response.put("count", count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "初始化失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}