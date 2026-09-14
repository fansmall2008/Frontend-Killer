package com.gamelist.service;

import java.util.List;
import java.util.Map;

import com.gamelist.model.ScraperSystem;

public interface ScraperSystemService {
    ScraperSystem save(ScraperSystem system);
    ScraperSystem update(ScraperSystem system);
    void deleteById(Long id);
    ScraperSystem getById(Long id);
    ScraperSystem getBySystemId(Integer systemId);
    List<ScraperSystem> getAll();
    int batchInsert(List<ScraperSystem> systems);
    void clearAll();
    Map<String, Object> scrapeSystem(Integer systemId, List<String> regions, List<String> mediaTypes);
    Map<String, Object> scrapeSystemAllMedia(Integer systemId);
    
    /**
     * 标记系统媒体已刮削
     */
    void markAsScraped(Integer systemId);
    
    /**
     * 检查系统媒体是否已刮削
     */
    boolean isMediaScraped(Integer systemId);
    
    /**
     * 同步下载系统 icon（wor 区域），用于导入时立即获取 logo
     */
    void scrapeSystemIcon(Integer systemId);
}