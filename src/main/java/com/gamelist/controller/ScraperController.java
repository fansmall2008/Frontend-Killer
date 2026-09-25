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
import com.gamelist.service.ScrapeStatus;
import com.gamelist.service.ScraperService;

@RestController
@RequestMapping("/api/scraper")
public class ScraperController {
    
    private static final Logger logger = LoggerFactory.getLogger(ScraperController.class);
    
    @Autowired
    private ScraperService scraperService;
    
    @Autowired
    private ScrapeStatus scrapeStatus;
    
    /**
     * 启动刮削任务
     */
    @PostMapping("/scrape")
    public ResponseEntity<?> startScraping(@RequestBody ScraperRequest request) {
        try {
            logger.info("收到刮削请求: type={}, platformId={}, gameIds={}", 
                request.getType(), request.getPlatformId(), 
                request.getGameIds() != null ? request.getGameIds().size() : "null");
            
            Map<String, Object> result = scraperService.startScraping(request);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            logger.error("启动刮削任务失败: type={}, platformId={}", 
                request.getType(), request.getPlatformId(), e);
            return ResponseEntity.status(500).body(
                Map.of("success", false, "message", "启动刮削任务失败: " + e.getMessage()));
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
     * 将游戏媒体下载任务加入队列（不立即下载）
     */
    @PostMapping("/enqueueMedia")
    public ResponseEntity<?> enqueueMedia(@RequestBody Map<String, Object> request) {
        try {
            Long gameId = Long.parseLong(request.get("gameId").toString());
            String gameName = (String) request.get("gameName");
            Long platformId = Long.parseLong(request.get("platformId").toString());
            @SuppressWarnings("unchecked")
            Map<String, Object> medias = (Map<String, Object>) request.get("medias");

            int taskCount = scraperService.enqueueGameMedia(gameId, gameName, platformId, medias);

            return ResponseEntity.ok(Map.of("success", true, "message", "已加入下载队列", "taskCount", taskCount));
        } catch (Exception e) {
            logger.error("加入媒体下载队列失败", e);
            return ResponseEntity.ok(Map.of("success", false, "message", "加入下载队列失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取刮削状态（包含配额信息）
     */
    @GetMapping("/thread-status")
    public ResponseEntity<?> getThreadStatus() {
        try {
            Map<String, Object> snapshot = scrapeStatus.getStatusSnapshot();
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("success", true);
            
            // 线程状态
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("maxThreads", snapshot.getOrDefault("maxThreads", 0));
            data.put("availableThreads", snapshot.getOrDefault("availableThreads", 0));
            data.put("gameInfoActive", snapshot.getOrDefault("gameInfoActive", 0));
            data.put("mediaActive", snapshot.getOrDefault("mediaActive", 0));
            data.put("gameInfoWaiting", snapshot.getOrDefault("gameInfoWaiting", 0));
            data.put("mediaWaiting", snapshot.getOrDefault("mediaWaiting", 0));
            data.put("cachedMaxThreads", scraperService.getUserMaxThreads());
            
            // 配额信息
            Map<String, Object> quota = new java.util.LinkedHashMap<>();
            quota.put("requestsToday", snapshot.getOrDefault("requestsToday", 0));
            quota.put("maxRequestsPerDay", snapshot.getOrDefault("maxRequestsPerDay", 0));
            quota.put("maxRequestsPerMin", snapshot.getOrDefault("maxRequestsPerMin", 0));
            quota.put("maxDownloadSpeed", snapshot.getOrDefault("maxDownloadSpeed", 0));
            quota.put("requestsKoToday", snapshot.getOrDefault("requestsKoToday", 0));
            quota.put("userNiveau", snapshot.getOrDefault("userNiveau", 0));
            quota.put("userContribution", snapshot.getOrDefault("userContribution", 0));
            // 计算配额使用百分比
            Object maxRequestsPerDay = snapshot.get("maxRequestsPerDay");
            Object requestsToday = snapshot.get("requestsToday");
            if (maxRequestsPerDay instanceof Number && ((Number) maxRequestsPerDay).intValue() > 0) {
                int max = ((Number) maxRequestsPerDay).intValue();
                int current = requestsToday instanceof Number ? ((Number) requestsToday).intValue() : 0;
                quota.put("usagePercent", Math.round((float) current / max * 100));
            } else {
                quota.put("usagePercent", 0);
            }
            data.put("quota", quota);
            
            result.put("data", data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("获取刮削状态失败", e);
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 从 manifest 缓存回填缺失元数据（genre/genreid/releasedate），零 SS 请求
     */
    @PostMapping("/backfill-metadata")
    public ResponseEntity<?> backfillMetadata() {
        try {
            Map<String, Object> result = scraperService.backfillMetadataFromManifest();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("元数据回填失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}