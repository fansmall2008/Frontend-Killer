package com.gamelist.service;

import java.util.Map;

public interface ScraperSettingsService {
    
    Map<String, String> getSettings();
    
    void saveSettings(String username, String password);
    
    void clearSettings();
}