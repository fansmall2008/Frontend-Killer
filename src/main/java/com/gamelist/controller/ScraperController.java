package com.gamelist.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.model.ScraperRequest;
import com.gamelist.service.ScraperService;
import com.gamelist.service.ThreadResourceManager;

@RestController
@RequestMapping("/api/scraper")
public class ScraperController {
    
    private static final Logger logger = LoggerFactory.getLogger(ScraperController.class);
    
    @Autowired
    private ScraperService scraperService;
    
    @Autowired
    private ThreadResourceManager threadResourceManager;
    
    /**
     * 启动刮削任务
     */
    @PostMapping("/scrape")
    public ResponseEntity<?> startScraping(@RequestBody ScraperRequest request) {
        Map<String, Object> result = scraperService.startScraping(request);
        
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 查询任务状态
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getTaskStatus(@PathVariable Long taskId) {
        Map<String, Object> result = scraperService.getTaskStatus(taskId);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取当前刮削状态（用于媒体下载页面显示）
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        Map<String, Object> status = scraperService.getStatus();
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", true);
        result.put("data", status);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 暂停刮削任务
     */
    @PostMapping("/pause")
    public ResponseEntity<?> pauseScraping() {
        scraperService.pauseScraping();
        return ResponseEntity.ok(Map.of("success", true, "message", "刮削任务已暂停"));
    }
    
    /**
     * 恢复刮削任务
     */
    @PostMapping("/resume")
    public ResponseEntity<?> resumeScraping() {
        scraperService.resumeScraping();
        return ResponseEntity.ok(Map.of("success", true, "message", "刮削任务已恢复"));
    }
    
    /**
     * 停止刮削任务
     */
    @PostMapping("/stop")
    public ResponseEntity<?> stopScraping() {
        scraperService.stopScraping();
        return ResponseEntity.ok(Map.of("success", true, "message", "刮削任务已停止"));
    }
    
    /**
     * 搜索游戏（使用ScreenScraper的jeuRecherche接口）
     * @param platformId 平台ID
     * @param searchTerm 搜索关键词
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchGame(@RequestParam Long platformId, @RequestParam String searchTerm) {
        try {
            List<Map<String, Object>> results = scraperService.searchGame(platformId, searchTerm);
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", true);
            response.put("data", results);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("搜索游戏失败", e);
            return ResponseEntity.ok(Map.of("success", false, "message", "搜索游戏失败: " + e.getMessage()));
        }
    }
    
    /**
     * 下载单个游戏的媒体文件
     */
    @PostMapping("/downloadMedia")
    public ResponseEntity<?> downloadMedia(@RequestBody Map<String, Object> request) {
        try {
            Long gameId = Long.parseLong(request.get("gameId").toString());
            String gameName = (String) request.get("gameName");
            Long platformId = Long.parseLong(request.get("platformId").toString());
            @SuppressWarnings("unchecked")
            Map<String, Object> medias = (Map<String, Object>) request.get("medias");
            
            scraperService.downloadGameMedia(gameId, gameName, platformId, medias);
            
            return ResponseEntity.ok(Map.of("success", true, "message", "媒体文件下载任务已创建"));
        } catch (Exception e) {
            logger.error("下载媒体文件失败", e);
            return ResponseEntity.ok(Map.of("success", false, "message", "下载媒体文件失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取线程资源管理器实时状态
     */
    @GetMapping("/thread-status")
    public ResponseEntity<?> getThreadStatus() {
        try {
            ThreadResourceManager.ResourceSnapshot snapshot = threadResourceManager.getSnapshot();
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("success", true);
            result.put("data", Map.of(
                "maxThreads", snapshot.maxThreads,
                "availableThreads", snapshot.availableThreads,
                "gameInfoActive", snapshot.gameInfoActive,
                "mediaActive", snapshot.mediaActive,
                "gameInfoWaiting", snapshot.gameInfoWaiting,
                "mediaWaiting", snapshot.mediaWaiting,
                "cachedMaxThreads", scraperService.getUserMaxThreads()
            ));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("获取线程状态失败", e);
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }
}