package com.gamelist.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamelist.mapper.ScrapeTaskMapper;
import com.gamelist.model.ScrapeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * ScreenScraper 配额状态管理
 * 
 * 职责：
 * 1. 存储从 SS API 响应中提取的配额信息
 * 2. 验证 maxThreads 变化并通知线程池动态调整
 * 3. 提供前端 thread-status API 查询接口
 * 4. 支持主动查询 SS 配额（媒体下载期间定期刷新）
 */
@Component
public class ScrapeStatus {
    
    private static final Logger logger = LoggerFactory.getLogger(ScrapeStatus.class);
    
    @Autowired
    private ScrapeTaskMapper scrapeTaskMapper;
    
    @Autowired
    private ScraperService scraperService;
    
    // 线程池引用（延迟注入，避免循环依赖）
    private volatile ScrapeWorkerPool scrapeWorkerPool;
    
    @Lazy
    @Autowired(required = false)
    public void setScrapeWorkerPool(ScrapeWorkerPool scrapeWorkerPool) {
        this.scrapeWorkerPool = scrapeWorkerPool;
    }
    
    // ========== ScreenScraper 配额信息（从 API 响应动态更新） ==========
    private volatile int requestsToday = 0;           // 今日已用请求数
    private volatile int maxRequestsPerDay = 0;       // 每日请求上限
    private volatile int maxRequestsPerMin = 0;       // 每分钟请求上限
    private volatile int maxDownloadSpeed = 0;        // 最大下载速度
    private volatile int requestsKoToday = 0;         // 今日失败请求数
    private volatile String userNiveau = "";          // 用户等级
    private volatile String userContribution = "";    // 贡献等级
    private volatile int maxThreads = 4;              // SS 服务器允许的最大并发线程数
    
    /**
     * 从 SS API 响应（ssuser 节点）中提取并更新配额信息
     * 每次刮削/查询 API 时调用
     */
    public void updateQuotaFromSsuser(JsonNode ssuserNode) {
        if (ssuserNode == null) return;
        
        try {
            if (ssuserNode.has("requeststoday")) {
                requestsToday = ssuserNode.get("requeststoday").asInt(0);
            }
            if (ssuserNode.has("maxrequeststoday")) {
                maxRequestsPerDay = ssuserNode.get("maxrequeststoday").asInt(0);
            }
            if (ssuserNode.has("maxrequestpermin")) {
                maxRequestsPerMin = ssuserNode.get("maxrequestpermin").asInt(0);
            }
            if (ssuserNode.has("maxdownloadspeed")) {
                maxDownloadSpeed = ssuserNode.get("maxdownloadspeed").asInt(0);
            }
            if (ssuserNode.has("requestskotoday")) {
                requestsKoToday = ssuserNode.get("requestskotoday").asInt(0);
            }
            if (ssuserNode.has("niveau")) {
                userNiveau = ssuserNode.get("niveau").asText("");
            }
            if (ssuserNode.has("contribution")) {
                userContribution = ssuserNode.get("contribution").asText("");
            }
            if (ssuserNode.has("maxthreads")) {
                int newMaxThreads = ssuserNode.get("maxthreads").asInt(0);
                updateMaxThreads(newMaxThreads);
            }
            
            logger.debug("配额更新: requestsToday={}/{}, maxThreads={}", 
                         requestsToday, maxRequestsPerDay, maxThreads);
        } catch (Exception e) {
            logger.warn("解析 ssuser 配额信息失败: {}", e.getMessage());
        }
    }
    
    /**
     * 更新 maxThreads 并通知线程池动态调整
     * maxThreads=0 表示用户被禁止使用 API，不调整线程池
     */
    private void updateMaxThreads(int newMaxThreads) {
        if (newMaxThreads < 0) {
            logger.warn("SS 返回负数 maxThreads={}，忽略", newMaxThreads);
            return;
        }
        
        if (newMaxThreads == 0) {
            logger.warn("SS 返回 maxThreads=0，当前用户状态可能被禁止使用 API");
            maxThreads = 0;
            return;
        }
        
        if (newMaxThreads != maxThreads) {
            int oldMaxThreads = maxThreads;
            maxThreads = newMaxThreads;
            logger.info("SS maxThreads 变更: {} → {}", oldMaxThreads, newMaxThreads);
            
            // 通知线程池动态调整大小
            if (scrapeWorkerPool != null) {
                scrapeWorkerPool.resize(newMaxThreads);
            }
        }
    }
    
    /**
     * 主动查询 SS 配额（媒体下载期间每 100 个任务调用一次）
     * 因为媒体下载不产生 SS API 响应，配额信息不会自然更新
     */
    public void refreshQuotaFromSS() {
        try {
            // 调用 ssuserInfos.php 获取最新配额
            JsonNode userInfo = scraperService.fetchUserInfosFromSS();
            if (userInfo != null && userInfo.has("response")) {
                JsonNode responseNode = userInfo.get("response");
                if (responseNode.has("ssuser")) {
                    updateQuotaFromSsuser(responseNode.get("ssuser"));
                    logger.info("配额主动刷新完成: requestsToday={}/{}, maxThreads={}", 
                                requestsToday, maxRequestsPerDay, maxThreads);
                }
            }
        } catch (Exception e) {
            logger.warn("主动查询 SS 配额失败（不影响媒体下载）: {}", e.getMessage());
        }
    }
    
    /**
     * 获取线程状态快照（供前端 thread-status API 使用）
     * 保持与原有 ResourceSnapshot 格式兼容
     */
    public Map<String, Object> getStatusSnapshot() {
        Map<String, Object> data = new HashMap<>();
        
        // 线程状态（简化版，不再区分 gameInfo/media 活跃数）
        data.put("maxThreads", maxThreads);
        data.put("availableThreads", maxThreads);  // 简化：总是显示最大可用
        data.put("gameInfoActive", scrapeTaskMapper.countByTypeAndStatus(ScrapeTask.TYPE_GAME_INFO, ScrapeTask.STATUS_RUNNING));
        data.put("mediaActive", scrapeTaskMapper.countByTypeAndStatus(ScrapeTask.TYPE_MEDIA_DOWNLOAD, ScrapeTask.STATUS_RUNNING));
        data.put("gameInfoWaiting", 0);  // 新架构不再跟踪等待数
        data.put("mediaWaiting", 0);
        data.put("cachedMaxThreads", maxThreads);
        
        // 配额信息
        Map<String, Object> quota = new HashMap<>();
        quota.put("requestsToday", requestsToday);
        quota.put("maxRequestsPerDay", maxRequestsPerDay);
        quota.put("maxRequestsPerMin", maxRequestsPerMin);
        quota.put("maxDownloadSpeed", maxDownloadSpeed);
        quota.put("requestsKoToday", requestsKoToday);
        quota.put("userNiveau", userNiveau);
        quota.put("userContribution", userContribution);
        
        // 计算配额使用百分比
        if (maxRequestsPerDay > 0) {
            quota.put("usagePercent", Math.round((float) requestsToday / maxRequestsPerDay * 100));
        } else {
            quota.put("usagePercent", 0);
        }
        data.put("quota", quota);
        
        // 任务池统计
        Map<String, Object> taskStats = new HashMap<>();
        taskStats.put("pendingGameInfo", scrapeTaskMapper.countByTypeAndStatus(ScrapeTask.TYPE_GAME_INFO, ScrapeTask.STATUS_PENDING));
        taskStats.put("runningGameInfo", scrapeTaskMapper.countByTypeAndStatus(ScrapeTask.TYPE_GAME_INFO, ScrapeTask.STATUS_RUNNING));
        taskStats.put("pendingMedia", scrapeTaskMapper.countByTypeAndStatus(ScrapeTask.TYPE_MEDIA_DOWNLOAD, ScrapeTask.STATUS_PENDING));
        taskStats.put("runningMedia", scrapeTaskMapper.countByTypeAndStatus(ScrapeTask.TYPE_MEDIA_DOWNLOAD, ScrapeTask.STATUS_RUNNING));
        data.put("taskStats", taskStats);
        
        return data;
    }
    
    // ========== Getters ==========
    
    public int getMaxThreads() { return maxThreads; }
    public int getRequestsToday() { return requestsToday; }
    public int getMaxRequestsPerDay() { return maxRequestsPerDay; }
    public int getMaxRequestsPerMin() { return maxRequestsPerMin; }
    public int getMaxDownloadSpeed() { return maxDownloadSpeed; }
    public int getRequestsKoToday() { return requestsKoToday; }
    public String getUserNiveau() { return userNiveau; }
    public String getUserContribution() { return userContribution; }
}
