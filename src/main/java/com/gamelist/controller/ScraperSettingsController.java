package com.gamelist.controller;

import com.gamelist.service.ScraperSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings/scraper")
public class ScraperSettingsController {
    
    private final ScraperSettingsService scraperSettingsService;
    
    public ScraperSettingsController(ScraperSettingsService scraperSettingsService) {
        this.scraperSettingsService = scraperSettingsService;
    }
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSettings() {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, String> settings = scraperSettingsService.getSettings();
            response.put("success", true);
            response.put("data", settings);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "获取设置失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    @PostMapping
    public ResponseEntity<Map<String, Object>> saveSettings(@RequestBody Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String username = payload.get("username");
            String password = payload.get("password");
            scraperSettingsService.saveSettings(username, password);
            response.put("success", true);
            response.put("message", "设置保存成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "保存设置失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearSettings() {
        Map<String, Object> response = new HashMap<>();
        try {
            scraperSettingsService.clearSettings();
            response.put("success", true);
            response.put("message", "设置已清除");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "清除设置失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}