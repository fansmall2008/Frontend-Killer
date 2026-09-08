package com.gamelist.service;

import java.util.List;
import java.util.Map;

import com.gamelist.model.ScraperSystem;

public interface ScreenScraperApiService {
    
    List<ScraperSystem> fetchSystems(String username, String password);
    
    boolean testConnection(String username, String password);
    
    int fetchAndSaveSystems(String username, String password, ScraperSystemService systemService);
    
    Map<String, Object> fetchSystemMedia(Integer systemId, String region, List<String> mediaTypes, String username, String password);
    
    Map<String, Object> fetchSystemDetails(Integer systemId, String username, String password);
    
    /**
     * 从内置基础数据 JSON 文件加载系统列表（不访问 API）
     */
    List<ScraperSystem> loadBaselineSystems();
}