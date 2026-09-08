package com.gamelist.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelist.config.ScreenScraperConfig;
import com.gamelist.mapper.MediaDownloadTaskMapper;
import com.gamelist.model.BackgroundTask;
import com.gamelist.model.FileType;
import com.gamelist.model.Game;
import com.gamelist.model.GameFileInfo;
import com.gamelist.model.MediaDownloadTask;
import com.gamelist.model.Platform;
import com.gamelist.model.ScraperRequest;
import com.gamelist.model.ScraperSystem;
import com.gamelist.service.GameService;
import com.gamelist.service.MediaDownloadService;
import com.gamelist.service.PlatformService;
import com.gamelist.service.ScraperService;
import com.gamelist.service.ScraperSettingsService;
import com.gamelist.service.ScraperSystemService;
import com.gamelist.service.TaskService;
import com.gamelist.service.ThreadResourceManager;
import com.gamelist.util.ScreenScraperStatusHandler;
import com.gamelist.util.EncryptionUtil;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Service
public class ScraperServiceImpl implements ScraperService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScraperServiceImpl.class);
    
    @Autowired
    private PlatformService platformService;
    
    @Autowired
    private GameService gameService;
    
    @Autowired
    private ScraperSystemService scraperSystemService;
    
    @Autowired
    private TaskService taskService;
    
    @Autowired
    private ScreenScraperConfig scraperConfig;
    
    @Autowired
    private ScraperSettingsService scraperSettingsService;

    @Autowired
    private MediaDownloadTaskMapper mediaDownloadTaskMapper;
    
    @Autowired
    private ThreadResourceManager threadResourceManager;
    
    @Autowired
    private MediaDownloadService mediaDownloadService;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    // 缓存系统信息
    private Map<Integer, ScraperSystem> systemInfoCache = new ConcurrentHashMap<>();
    
    // 缓存从 API 响应中获取的 maxthreads，避免重复调用 ssuserInfos.php
    private volatile int cachedMaxThreads = 0;
    
    // 刮削任务控制标志
    private final AtomicBoolean isScrapingPaused = new AtomicBoolean(false);
    private final AtomicBoolean isScrapingStopped = new AtomicBoolean(false);
    
    // 404计数器
    private final AtomicInteger notFoundCount = new AtomicInteger(0);
    private volatile long notFoundWindowStart = System.currentTimeMillis();
    private static final int NOT_FOUND_THRESHOLD = 100;
    private static final long NOT_FOUND_WINDOW_MS = 10000;
    
    // 当前刮削任务ID（用于暂停/恢复控制）
    private volatile Long currentScrapingTaskId = null;
    
    // 刮削进度计数器
    private final AtomicInteger scrapedCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    private final AtomicInteger processingCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicInteger stoppedCount = new AtomicInteger(0);
    
    public ScraperServiceImpl() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public Map<String, Object> startScraping(ScraperRequest request) {
        logger.info("========== 开始刮削任务 ==========");
        logger.info("刮削类型: {}", request.getType());
        logger.info("平台ID: {}", request.getPlatformId());
        logger.info("游戏数量: {}", request.getGameIds() != null ? request.getGameIds().size() : "全部");
        logger.info("刮削范围: {}", request.getScope());
        logger.info("地区: {}", request.getRegion());
        
        // 1. 验证平台系统绑定
        Platform platform = platformService.getPlatformById(request.getPlatformId());
        logger.info("查询到的平台: {}", platform);
        logger.info("平台ID: {}, 平台名称: {}, SystemId: {}", 
            platform != null ? platform.getId() : "null",
            platform != null ? platform.getName() : "null",
            platform != null ? platform.getSystemId() : "null");
        
        if (platform == null || platform.getSystemId() == null || platform.getSystemId() == 0) {
            logger.error("平台未绑定刮削系统!");
            return Map.of("success", false, "message", "请先绑定系统后再进行刮削");
        }
        
        // 2. 验证媒体类型 - 只有选择了媒体刮削时才验证
        List<String> scope = request.getScope();
        boolean scrapeMedia = scope == null || scope.isEmpty() || scope.contains("media");
        if (scrapeMedia && (request.getMediaTypes() == null || request.getMediaTypes().isEmpty())) {
            logger.error("未选择媒体类型!");
            return Map.of("success", false, "message", "请至少选择一种媒体类型");
        }
        
        // 3. 获取线程数限制
        int maxThreads = getUserMaxThreads();
        if (maxThreads <= 0) {
            logger.error("服务器返回线程配额为0，无法进行刮削");
            return Map.of("success", false, "message", "ScreenScraper 服务器返回线程配额为0，当前用户状态不允许刮削。请登录 ScreenScraper 账号后重试。");
        }
        
        // 4. 创建后台任务
        String taskType = switch (request.getType()) {
            case "platform" -> "SCRAPE_PLATFORM";
            case "batch" -> "SCRAPE_BATCH";
            case "single" -> "SCRAPE_GAME";
            default -> "SCRAPE_UNKNOWN";
        };
        
        String taskDescription = buildTaskDescription(request);
        BackgroundTask task = taskService.createTask(taskType, taskDescription);
        Long taskId = task.getId();
        currentScrapingTaskId = taskId;
        
        logger.info("任务ID: {}, 描述: {}", taskId, taskDescription);
        logger.info("正在异步执行刮削...");
        
        // 5. 异步执行刮削（使用两阶段线程分配策略）
        scrapeGamesWithTwoPhaseStrategy(taskId, request, platform.getSystemId(), maxThreads);
        
        return Map.of(
            "success", true,
            "taskId", taskId,
            "message", "刮削任务已启动，请在任务管理页面查看进度。游戏信息和媒体文件将异步进行。"
        );
    }
    
    /**
     * 两阶段线程分配策略（使用动态资源管理）
     * 
     * 核心改进：
     * 1. 使用 ThreadResourceManager 动态管理线程资源
     * 2. 游戏信息刮削优先级高于媒体下载
     * 3. 当只有一种任务时，分配全部资源
     * 4. 当两种任务同时存在时，优先给游戏信息任务
     */
    @Async
    public void scrapeGamesWithTwoPhaseStrategy(Long taskId, ScraperRequest request, Integer systemId, int maxThreads) {
        isScrapingPaused.set(false);
        isScrapingStopped.set(false);
        resetNotFoundCount();
        
        try {
            // 初始化线程资源管理器
            threadResourceManager.updateMaxThreads(maxThreads);
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.info("  🎯 动态线程资源管理策略");
            logger.info("  📊 最大并发线程数: {}", maxThreads);
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            // 获取系统配置
            ScraperSystem system = getSystemInfo(systemId);
            if (system == null) {
                taskService.failTask(taskId, "刮削失败", "无法获取系统配置");
                return;
            }
            
            // 获取游戏列表
            List<Game> games = getTargetGames(request);
            int totalGames = games.size();
            
            if (totalGames == 0) {
                taskService.completeTask(taskId, "没有游戏需要刮削", "没有游戏需要刮削");
                return;
            }
            
            // 初始化刮削状态计数器
            scrapedCount.set(0);
            totalCount.set(totalGames);
            
            logger.info("  🎮 待刮削游戏数: {}", totalGames);
            
            // 更新进度 - 开始
            taskService.updateTaskProgress(taskId, 0, "开始刮削 (最大并发: " + maxThreads + ")", 0, totalGames);

            // 原子计数器
            AtomicInteger processedCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger mediaTaskCounter = new AtomicInteger(0);
            
            // 根据scope参数决定是否刮削媒体
            List<String> scope = request.getScope();
            boolean scrapeMedia = scope == null || scope.isEmpty() || scope.contains("media");
            boolean scrapeGameInfo = scope == null || scope.isEmpty() || scope.contains("gameInfo");
            
            // 线程池大小 = maxThreads * 4，留余量给 ThreadResourceManager 调度
            // 实际并发数由 ThreadResourceManager 控制，避免创建过多空转线程导致资源竞争超时
            int poolSize = maxThreads * 4;
            ExecutorService executor = Executors.newFixedThreadPool(poolSize);
            logger.info("线程池大小: {} (maxThreads={}, 游戏数={})", poolSize, maxThreads, totalGames);
            
            // 游戏信息刮削的 CountDownLatch
            CountDownLatch gameScrapeLatch = new CountDownLatch(totalGames);
            
            // 提交游戏信息刮削任务
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.info("  📍 开始游戏信息刮削");
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            for (Game game : games) {
                executor.submit(() -> {
                    // 尝试获取资源
                    boolean acquired = false;
                    try {
                        acquired = threadResourceManager.acquireForGameInfo(60000); // 60秒超时
                        if (!acquired) {
                            logger.error("游戏信息刮削获取资源超时: {}", game.getName());
                            failedCount.incrementAndGet();
                            return;
                        }
                        
                        if (isScrapingStopped.get()) {
                            return;
                        }
                        
                        // 检查暂停状态
                        while (isScrapingPaused.get() && !isScrapingStopped.get()) {
                            logger.info("游戏刮削任务已暂停，等待恢复...");
                            Thread.sleep(2000);
                        }
                        
                        if (isScrapingStopped.get()) {
                            return;
                        }
                        
                        logger.info("开始刮削游戏: {} [资源状态: {}]", game.getName(), 
                            threadResourceManager.getSnapshot());
                        
                        // 处理游戏文件
                        GameFileInfo fileInfo = processGameFile(game, system);
                        
                        // 调用ScreenScraper API搜索游戏
                        Map<String, Object> searchResult = searchGameWithStatus(fileInfo, system.getSystemId(), request);
                        
                        if ((Boolean) searchResult.get("shouldStop")) {
                            String errorMessage = (String) searchResult.get("message");
                            logger.error("遇到特殊状态码，停止刮削: {}", errorMessage);
                            isScrapingStopped.set(true);
                            sendNotification("刮削停止", errorMessage);
                            return;
                        }
                        
                        if ((Boolean) searchResult.get("found")) {
                            logger.info("游戏已找到，开始处理: gameId={}, gameName={}", game.getId(), game.getName());
                            
                            Map<String, Object> data = (Map<String, Object>) searchResult.get("data");
                            
                            if (scrapeGameInfo) {
                                updateGameRecord(game, data, request);
                            }

                            if (scrapeMedia) {
                                int taskCount = saveMediaTasksToDb(game, data, request, system.getName(), request.getPlatformId(), taskId, mediaTaskCounter);
                                logger.info("媒体任务保存完成，创建了 {} 个任务", taskCount);
                            }

                            successCount.incrementAndGet();
                            taskService.updateTaskLog(taskId, "刮削完成: " + game.getName());
                        } else {
                            if (shouldStopDueToNotFound()) {
                                logger.error("10秒内出现10次404错误，停止刮削");
                                isScrapingStopped.set(true);
                                sendNotification("刮削停止", "10秒内出现10次404错误，已停止刮削");
                            }
                            taskService.updateTaskLog(taskId, "未找到: " + game.getName());
                        }
                        
                    } catch (Exception e) {
                        failedCount.incrementAndGet();
                        logger.error("刮削游戏失败: {} - {}", game.getName(), e.getMessage());
                        taskService.updateTaskLog(taskId, "失败: " + game.getName() + " - " + e.getMessage());
                    } finally {
                        // 关键：必须归还资源
                        if (acquired) {
                            threadResourceManager.releaseForGameInfo();
                        }
                        // 更新进度
                        int current = processedCount.incrementAndGet();
                        scrapedCount.incrementAndGet();
                        int progress = (current * 100) / totalGames;
                        taskService.updateTaskProgress(taskId, progress, 
                            "正在刮削: " + game.getName(), current, totalGames);
                        gameScrapeLatch.countDown();
                    }
                });
            }
            
            // 如果选择了媒体刮削，启动多个媒体下载线程
            if (scrapeMedia) {
                // 根据最大线程数启动多个媒体下载线程
                // 注意：游戏信息刮削优先，所以媒体线程数设为 maxThreads（实际并发由资源管理器控制）
                for (int i = 0; i < maxThreads; i++) {
                    final int threadIndex = i;
                    executor.submit(() -> {
                        try {
                            // 只在第一个线程打印启动日志
                            if (threadIndex == 0) {
                                logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                                logger.info("  📍 启动媒体下载任务 ({}个线程)", maxThreads);
                                logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                            }
                            
                            while (!isScrapingStopped.get()) {
                                // 尝试获取资源（低优先级，游戏信息线程优先）
                                boolean acquired = threadResourceManager.acquireForMedia(5000);
                                if (!acquired) {
                                    // 获取资源失败，可能是有游戏信息任务在等待或超时
                                    Thread.sleep(100);
                                    continue;
                                }
                                
                                try {
                                    // 尝试获取一个待下载任务
                                    MediaDownloadTask task = mediaDownloadTaskMapper.selectOnePendingTask(taskId);
                                    if (task != null) {
                                        try {
                                            // 标记为下载中（乐观锁）
                                            int updated = mediaDownloadTaskMapper.tryUpdateStatus(task.getId(), 
                                                MediaDownloadTask.STATUS_PENDING, MediaDownloadTask.STATUS_DOWNLOADING);
                                            
                                            if (updated > 0) {
                                                String decryptedUrl = EncryptionUtil.decrypt(task.getDownloadUrl());
                                                downloadMediaFile(decryptedUrl, task.getLocalPath());
                                                mediaDownloadTaskMapper.updateStatusById(task.getId(), MediaDownloadTask.STATUS_COMPLETED, null);
                                                updateGameMediaPath(task.getGameId(), task.getMediaType(), task.getLocalPath());
                                                logger.info("媒体下载完成: {} [资源状态: {}]", task.getLocalPath(), 
                                                    threadResourceManager.getSnapshot());
                                            }
                                        } catch (Exception e) {
                                            logger.error("下载媒体文件失败: {}", e.getMessage());
                                            mediaDownloadTaskMapper.updateStatusById(task.getId(), MediaDownloadTask.STATUS_FAILED, e.getMessage());
                                        }
                                    } else {
                                        // 检查是否所有游戏信息刮削已完成
                                        if (gameScrapeLatch.getCount() == 0) {
                                            // 检查是否还有待下载任务
                                            long pending = mediaDownloadTaskMapper.countPendingByTaskId(taskId);
                                            if (pending == 0) {
                                                logger.info("媒体下载线程完成: 活跃游戏={}, 活跃媒体={}, 可用={}", 
                                                    threadResourceManager.getSnapshot().gameInfoActive,
                                                    threadResourceManager.getSnapshot().mediaActive,
                                                    threadResourceManager.getSnapshot().availableThreads);
                                                break;
                                            }
                                        }
                                        // 短暂等待后重试
                                        Thread.sleep(200);
                                    }
                                } finally {
                                    // 关键：必须归还资源
                                    threadResourceManager.releaseForMedia();
                                }
                            }
                        } catch (Exception e) {
                            logger.error("媒体下载线程异常: {}", e.getMessage());
                        }
                    });
                }
            }
            
            // 等待游戏信息刮削完成
            try {
                gameScrapeLatch.await();
            } catch (InterruptedException e) {
                logger.warn("刮削任务被中断");
                isScrapingStopped.set(true);
                Thread.currentThread().interrupt();
                executor.shutdownNow();
                taskService.failTask(taskId, "刮削被中断", "刮削任务在等待期间被中断");
                return;
            }
            
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.info("  ✅ 游戏信息刮削完成");
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            // 等待媒体下载完成（如果有）
            if (scrapeMedia) {
                logger.info("等待媒体下载完成...");
                // 等待所有媒体任务完成
                long lastPendingCount = -1;
                int noChangeCount = 0;
                while (!isScrapingStopped.get()) {
                    long pendingCount = mediaDownloadTaskMapper.countPendingByTaskId(taskId);
                    long downloadingCount = mediaDownloadTaskMapper.countByTaskIdAndStatus(taskId, MediaDownloadTask.STATUS_DOWNLOADING);
                    
                    if (pendingCount == 0 && downloadingCount == 0) {
                        logger.info("所有媒体下载任务已完成");
                        break;
                    }
                    
                    // 检测是否卡住（连续10次pending数量不变）
                    if (pendingCount == lastPendingCount) {
                        noChangeCount++;
                        if (noChangeCount > 20) { // 20 * 1秒 = 20秒
                            logger.warn("媒体下载可能卡住，pending={}, downloading={}", pendingCount, downloadingCount);
                            break;
                        }
                    } else {
                        noChangeCount = 0;
                    }
                    lastPendingCount = pendingCount;
                    
                    Thread.sleep(1000);
                }
            }
            
            // 关闭线程池
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            // 检查是否被停止
            if (isScrapingStopped.get()) {
                logger.info("刮削任务已被停止");
                taskService.failTask(taskId, "刮削被停止", "刮削任务已被用户停止或遇到错误");
                return;
            }
            
            // 完成任务
            int totalSuccess = successCount.get();
            int totalFailed = failedCount.get();
            int totalProcessed = totalSuccess + totalFailed;
            double successRate = totalProcessed > 0 ? (totalSuccess * 100.0 / totalProcessed) : 0;
            
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.info("  🎉 刮削任务完成!");
            logger.info("  ✅ 成功: {}", totalSuccess);
            logger.info("  ❌ 失败: {}", totalFailed);
            logger.info("  📊 总计处理: {} / {} 个游戏", totalProcessed, totalGames);
            logger.info("  📈 成功率: {}%", String.format("%.2f", successRate));
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            int totalMediaTasks = mediaTaskCounter.get();
            String resultMsg;
            if (totalMediaTasks > 0) {
                resultMsg = String.format("搜索完成，成功: %d, 失败: %d (成功率: %.2f%%)，待下载媒体: %d 个",
                    totalSuccess, totalFailed, successRate, totalMediaTasks);
            } else {
                resultMsg = String.format("搜索完成，成功: %d, 失败: %d (成功率: %.2f%%)",
                    totalSuccess, totalFailed, successRate);
            }
            taskService.completeTask(taskId, resultMsg, resultMsg);
            
            // 重置刮削状态计数器
            scrapedCount.set(0);
            totalCount.set(0);

            // 发送通知
            sendNotification(resultMsg, "success");
            
        } catch (Exception e) {
            logger.error("刮削任务失败: {}", e.getMessage(), e);
            taskService.failTask(taskId, "刮削失败", e.getMessage());
            sendNotification("刮削任务失败: " + e.getMessage(), "error");
        } finally {
            // 关键：重置资源管理器
            threadResourceManager.reset();
            currentScrapingTaskId = null;
            isScrapingPaused.set(false);
            isScrapingStopped.set(false);
            resetNotFoundCount();
            scrapedCount.set(0);
            totalCount.set(0);
        }
    }
    
    /**
     * 提前开始媒体下载（阶段1期间）
     */
    private void startEarlyMediaDownload(Long taskId, ExecutorService executor) {
        // 获取线程池大小
        int poolSize = ((java.util.concurrent.ThreadPoolExecutor) executor).getMaximumPoolSize();
        
        logger.info("提前媒体下载启动，线程数: {}", poolSize);
        
        // 为每个线程提交一个下载工作任务
        for (int i = 0; i < poolSize; i++) {
            executor.submit(() -> {
                try {
                    while (!isScrapingStopped.get()) {
                        // 尝试获取一个待下载任务
                        MediaDownloadTask task = mediaDownloadTaskMapper.selectOnePendingTask(taskId);
                        if (task != null) {
                            try {
                                // 标记为下载中
                                int updated = mediaDownloadTaskMapper.tryUpdateStatus(task.getId(), 
                                    MediaDownloadTask.STATUS_PENDING, MediaDownloadTask.STATUS_DOWNLOADING);
                                if (updated > 0) {
                                    String decryptedUrl = EncryptionUtil.decrypt(task.getDownloadUrl());
                                    downloadMediaFile(decryptedUrl, task.getLocalPath());
                                    mediaDownloadTaskMapper.updateStatusById(task.getId(), MediaDownloadTask.STATUS_COMPLETED, null);
                                    // 将下载的媒体文件路径更新到游戏记录中
                                    updateGameMediaPath(task.getGameId(), task.getMediaType(), task.getLocalPath());
                                    logger.debug("提前下载完成: {}", task.getLocalPath());
                                }
                            } catch (Exception e) {
                                logger.error("提前下载媒体文件失败: {}", e.getMessage());
                                mediaDownloadTaskMapper.updateStatusById(task.getId(), MediaDownloadTask.STATUS_FAILED, e.getMessage());
                            }
                        } else {
                            // 没有待下载任务，短暂等待
                            Thread.sleep(500);
                        }
                    }
                } catch (Exception e) {
                    logger.error("提前媒体下载线程异常: {}", e.getMessage());
                }
            });
        }
    }
    
    /**
     * 启动完整的媒体下载（阶段2）
     */
    private void startFullMediaDownload(Long taskId, ExecutorService executor, int maxThreads) {
        long pendingCount = mediaDownloadTaskMapper.countPendingByTaskId(taskId);
        logger.info("开始完整媒体下载，待下载文件数: {}", pendingCount);
        
        if (pendingCount == 0) {
            logger.info("没有待下载的媒体文件");
            return;
        }
        
        // 使用多线程下载
        AtomicInteger processedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch((int) pendingCount);
        
        // 获取所有待下载任务
        List<MediaDownloadTask> allPendingTasks = mediaDownloadTaskMapper.selectByTaskId(taskId).stream()
            .filter(t -> MediaDownloadTask.STATUS_PENDING.equals(t.getStatus()))
            .collect(Collectors.toList());
        
        for (MediaDownloadTask task : allPendingTasks) {
            executor.submit(() -> {
                if (isScrapingStopped.get()) {
                    latch.countDown();
                    return;
                }
                
                try {
                    // 检查暂停状态
                    while (isScrapingPaused.get() && !isScrapingStopped.get()) {
                        logger.info("媒体下载任务已暂停，等待恢复...");
                        Thread.sleep(2000);
                    }
                    
                    if (isScrapingStopped.get()) {
                        latch.countDown();
                        return;
                    }
                    
                    mediaDownloadTaskMapper.updateStatusById(task.getId(), MediaDownloadTask.STATUS_DOWNLOADING, null);
                    String decryptedUrl = EncryptionUtil.decrypt(task.getDownloadUrl());
                    downloadMediaFile(decryptedUrl, task.getLocalPath());
                    mediaDownloadTaskMapper.updateStatusById(task.getId(), MediaDownloadTask.STATUS_COMPLETED, null);
                    
                    // 将下载的媒体文件路径更新到游戏记录中
                    updateGameMediaPath(task.getGameId(), task.getMediaType(), task.getLocalPath());
                    
                    int current = processedCount.incrementAndGet();
                    logger.info("媒体下载进度: {}/{}", current, allPendingTasks.size());
                    
                } catch (Exception e) {
                    logger.error("下载媒体文件失败: {}", e.getMessage());
                    mediaDownloadTaskMapper.updateStatusById(task.getId(), MediaDownloadTask.STATUS_FAILED, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 搜索游戏（带状态码处理）
     */
    private Map<String, Object> searchGameWithStatus(GameFileInfo fileInfo, Integer systemId, ScraperRequest request) {
        try {
            Map<String, Object> searchResult = searchGame(fileInfo, systemId, request);
            
            // 检查是否需要停止
            if (!(Boolean) searchResult.get("found")) {
                // 检查404计数
                if (shouldStopDueToNotFound()) {
                    return Map.of("found", false, "shouldStop", true, "message", "10秒内出现10次404错误");
                }
            }
            
            return Map.of("found", searchResult.get("found"), "shouldStop", false, "data", searchResult);
        } catch (Exception e) {
            logger.error("搜索游戏异常: {}", e.getMessage());
            return Map.of("found", false, "shouldStop", false);
        }
    }
    
    /**
     * 检查是否因404过多需要停止
     */
    private synchronized boolean shouldStopDueToNotFound() {
        long now = System.currentTimeMillis();
        if (now - notFoundWindowStart > NOT_FOUND_WINDOW_MS) {
            notFoundWindowStart = now;
            notFoundCount.set(1);
            return false;
        }

        int count = notFoundCount.incrementAndGet();
        if (count >= NOT_FOUND_THRESHOLD) {
            logger.error("10秒内出现{}次404错误，停止刮削", count);
            notFoundCount.set(0);
            return true;
        }
        return false;
    }
    
    /**
     * 重置404计数器
     */
    private void resetNotFoundCount() {
        notFoundCount.set(0);
        notFoundWindowStart = System.currentTimeMillis();
    }
    
    /**
     * 暂停刮削任务
     */
    public void pauseScraping() {
        isScrapingPaused.set(true);
        logger.info("刮削任务已暂停");
    }
    
    /**
     * 恢复刮削任务
     */
    public void resumeScraping() {
        isScrapingPaused.set(false);
        
        // 将暂停期间添加的媒体任务从 STOPPED 状态恢复为 PENDING 状态
        if (currentScrapingTaskId != null) {
            int updated = mediaDownloadTaskMapper.updateStoppedToPendingByTaskId(currentScrapingTaskId);
            if (updated > 0) {
                logger.info("已恢复 {} 个暂停的媒体任务", updated);
            }
        }
        
        logger.info("刮削任务已恢复");
    }
    
    /**
     * 停止刮削任务
     */
    public void stopScraping() {
        isScrapingStopped.set(true);
        isScrapingPaused.set(false);
        logger.info("刮削任务已停止");
    }
    
    /**
     * 检查是否正在刮削
     */
    public boolean isScraping() {
        return currentScrapingTaskId != null;
    }
    
    /**
     * 检查是否已暂停
     */
    public boolean isScrapingPaused() {
        return isScrapingPaused.get();
    }
    
    @Override
    public Map<String, Object> getTaskStatus(Long taskId) {
        BackgroundTask task = taskService.getTaskById(taskId);
        if (task == null) {
            return Map.of("success", false, "message", "任务不存在");
        }
        
        return Map.of(
            "success", true,
            "taskId", task.getId(),
            "status", task.getStatus(),
            "progress", task.getProgress(),
            "message", task.getMessage(),
            "createdAt", task.getCreatedAt(),
            "updatedAt", task.getUpdatedAt()
        );
    }
    
    @Override
    public int getUserMaxThreads() {
        // 如果已经从 API 响应中缓存了 maxthreads，直接返回，不再额外调用 ssuserInfos.php
        if (cachedMaxThreads > 0) {
            logger.info("使用缓存的线程配额: {}", cachedMaxThreads);
            return cachedMaxThreads;
        }
        
        try {
            Map<String, String> settings = scraperSettingsService.getSettings();
            String username = settings.get("username");
            String password = settings.get("password");

            if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
                logger.warn("未设置ScreenScraper用户凭证，使用默认线程数1");
                return 1;
            }

            String baseUrl = scraperConfig.getBaseUrl();
            okhttp3.HttpUrl.Builder urlBuilder = okhttp3.HttpUrl.parse(baseUrl + "/api2/ssuserInfos.php").newBuilder();
            urlBuilder.addQueryParameter("devid", scraperConfig.getDevPseudo());
            urlBuilder.addQueryParameter("devpassword", scraperConfig.getDevPassword());
            urlBuilder.addQueryParameter("softname", "FrontendKiller");
            urlBuilder.addQueryParameter("output", "json");
            urlBuilder.addQueryParameter("ssid", username);
            urlBuilder.addQueryParameter("sspassword", password);

            String url = urlBuilder.build().toString();
            logger.info("正在调用ssuserInfos.php获取用户信息...");
            logger.info("请求URL: {}", url.replace(password, "***"));

            String response = executeRequest(url);
            logger.info("ssuserInfos.php原始响应: {}", response);

            if (response == null || response.isEmpty()) {
                logger.warn("ssuserInfos.php返回空响应，使用默认线程数1");
                return 1;
            }

            JsonNode root = objectMapper.readTree(response);
            logger.info("成功解析JSON响应");

            if (root.has("response")) {
                logger.info("响应包含response节点");
                JsonNode responseNode = root.get("response");

                Integer maxThreads = null;

                if (responseNode.has("ssuser")) {
                    logger.info("响应包含ssuser节点");
                    JsonNode ssuserNode = responseNode.get("ssuser");
                    logger.info("ssuser节点内容: {}", ssuserNode.toString());
                    if (ssuserNode.has("maxthreads")) {
                        maxThreads = ssuserNode.get("maxthreads").asInt();
                        logger.info("从ssuser.maxthreads获取到线程数: {}", maxThreads);
                    } else {
                        logger.warn("ssuser节点中没有maxthreads字段");
                    }
                } else {
                    logger.warn("响应中没有ssuser节点");
                }

                if (maxThreads == null && responseNode.has("maxthreads")) {
                    maxThreads = responseNode.get("maxthreads").asInt();
                    logger.info("从response.maxthreads获取到线程数: {}", maxThreads);
                }

                if (maxThreads != null) {
                    // 缓存线程配额，后续刮削中的 API 响应也会动态更新此值
                    cachedMaxThreads = maxThreads;
                    
                    if (maxThreads <= 0) {
                        logger.warn("服务器返回线程配额为0，当前用户状态不允许刮削（可能需要登录）");
                        return 0;
                    }
                    
                    logger.info("✅ 成功获取到用户最大线程数: {}", maxThreads);
                    
                    if (root.has("ssuser")) {
                        JsonNode ssuserNode = root.get("ssuser");
                        if (ssuserNode.has("id")) {
                            logger.info("用户ID: {}", ssuserNode.get("id").asText());
                        }
                        if (ssuserNode.has("niveau")) {
                            logger.info("用户等级: {}", ssuserNode.get("niveau").asText());
                        }
                        if (ssuserNode.has("contribution")) {
                            logger.info("财务贡献等级: {}", ssuserNode.get("contribution").asText());
                        }
                    }
                    
                    return maxThreads;
                }
            }

            logger.warn("ssuserInfos.php未返回maxthreads字段，使用默认线程数1");
            return 1;
        } catch (Exception e) {
            logger.error("调用ssuserInfos.php获取用户信息失败: {}", e.getMessage(), e);
            return 1;
        }
    }
    
    private String buildTaskDescription(ScraperRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("刮削任务 - ");
        sb.append(request.getType());
        sb.append(" - 平台ID: ").append(request.getPlatformId());
        if (request.getGameIds() != null && !request.getGameIds().isEmpty()) {
            sb.append(" - 游戏数: ").append(request.getGameIds().size());
        }
        return sb.toString();
    }
    
    private void sendNotification(String message, String type) {
        logger.info("发送通知: [{}] {}", type, message);
        // TODO: 集成到现有的通知机制
    }
    
    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new java.util.HashMap<>();
        boolean running = currentScrapingTaskId != null;
        status.put("isRunning", running);
        status.put("isPaused", isScrapingPaused.get());
        status.put("scrapedCount", scrapedCount.get());
        status.put("totalCount", totalCount.get());
        status.put("pendingCount", pendingCount.get());
        status.put("processingCount", processingCount.get());
        status.put("failedCount", failedCount.get());
        status.put("stoppedCount", stoppedCount.get());
        return status;
    }
    
    // ==================== 原有方法保持不变 ====================
    
    /**
     * 搜索游戏
     */
    private Map<String, Object> searchGame(GameFileInfo fileInfo, Integer systemId, ScraperRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("found", false);
        
        try {
            String baseUrl = scraperConfig.getBaseUrl();
            okhttp3.HttpUrl.Builder urlBuilder = okhttp3.HttpUrl.parse(baseUrl + "/api2/jeuInfos.php").newBuilder();
            
            // 基础参数
            urlBuilder.addQueryParameter("devid", scraperConfig.getDevPseudo());
            urlBuilder.addQueryParameter("devpassword", scraperConfig.getDevPassword());
            urlBuilder.addQueryParameter("softname", "FrontendKiller");
            urlBuilder.addQueryParameter("output", "json");
            
            // 用户凭证
            Map<String, String> settings = scraperSettingsService.getSettings();
            if (settings.containsKey("username") && settings.containsKey("password")) {
                urlBuilder.addQueryParameter("ssid", settings.get("username"));
                urlBuilder.addQueryParameter("sspassword", settings.get("password"));
            }
            
            // 系统ID
            urlBuilder.addQueryParameter("systemeid", systemId.toString());
            
            // 游戏名称或CRC - 同时添加crc和romnom以提高匹配成功率
            if (fileInfo.getCrc32() != null && !fileInfo.getCrc32().isEmpty()) {
                urlBuilder.addQueryParameter("crc", fileInfo.getCrc32());
            }
            
            // 始终添加romnom作为备用匹配条件
            String romName = fileInfo.getGameName();
            if (romName == null || romName.isEmpty()) {
                // 如果gameName为空，从文件路径中提取
                String filePath = fileInfo.getFilePath();
                if (filePath != null && !filePath.isEmpty()) {
                    File file = new File(filePath);
                    romName = file.getName();
                    int lastDot = romName.lastIndexOf('.');
                    if (lastDot > 0) {
                        romName = romName.substring(0, lastDot);
                    }
                }
            }
            if (romName != null && !romName.isEmpty()) {
                urlBuilder.addQueryParameter("romnom", romName);
            }
            
            // 文件大小
            if (fileInfo.getFileSize() > 0) {
                urlBuilder.addQueryParameter("romtaille", String.valueOf(fileInfo.getFileSize()));
            }
            
            // 地区
            if (request.getRegion() != null && !request.getRegion().isEmpty()) {
                urlBuilder.addQueryParameter("region", request.getRegion());
            }
            
            String url = urlBuilder.build().toString();
            logger.info("搜索游戏URL: {}", url);
            
            String response = executeRequest(url);
            if (response == null || response.isEmpty()) {
                logger.warn("搜索游戏返回空响应");
                return result;
            }
            
            JsonNode root = objectMapper.readTree(response);
                logger.info("API响应结构: has response={}, response has jeu={}", 
                            root.has("response"), root.has("response") && root.get("response").has("jeu"));
                
                if (root.has("response")) {
                    JsonNode responseNode = root.get("response");
                    
                    // 从每次 API 响应中提取 ssuser.maxthreads，动态更新线程配额
                    if (responseNode.has("ssuser")) {
                        JsonNode ssuserNode = responseNode.get("ssuser");
                        if (ssuserNode.has("maxthreads")) {
                            int serverMaxThreads = ssuserNode.get("maxthreads").asInt();
                            cachedMaxThreads = serverMaxThreads;
                            threadResourceManager.updateFromServerResponse(serverMaxThreads);
                        }
                    }
                    
                    if (responseNode.has("jeu")) {
                    JsonNode jeu = responseNode.get("jeu");
                    result.put("found", true);
                    result.put("game", jeu);
                    
                    // 提取媒体信息
                    logger.info("jeu.has(\"medias\"): {}", jeu.has("medias"));
                    if (jeu.has("medias")) {
                        JsonNode mediasNode = jeu.get("medias");
                        logger.info("mediasNode != null: {}", mediasNode != null);
                        logger.info("mediasNode.isArray(): {}, mediasNode.size(): {}", mediasNode.isArray(), mediasNode.isArray() ? mediasNode.size() : "N/A");
                        result.put("medias", mediasNode);
                        logger.info("媒体信息已存入searchResult");
                    } else {
                        logger.info("未找到medias字段");
                    }
                    
                    logger.info("找到游戏: {}", jeu.has("nom") ? jeu.get("nom").asText() : "未知");
                } else {
                    logger.info("未找到游戏: {}", fileInfo.getGameName());
                }
                } // end if (root.has("response"))
            
        } catch (Exception e) {
            logger.error("搜索游戏失败: {}", e.getMessage());
        }
        
        return result;
    }
    
    private String executeRequest(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    int statusCode = response.code();
                    logger.error("HTTP请求失败，状态码: {}", statusCode);

                    // 处理特殊状态码
                    if (ScreenScraperStatusHandler.shouldStopImmediately(statusCode)) {
                        String message = ScreenScraperStatusHandler.getSuggestion(statusCode);
                        logger.error("遇到特殊状态码，需要停止刮削: {}", message);
                        // 这里可以添加通知逻辑
                    }

                    return null;
                }

                return response.body() != null ? response.body().string() : null;
            }
        } catch (IOException e) {
            logger.error("执行HTTP请求失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> searchGame(Long platformId, String searchTerm) {
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        
        try {
            // 获取平台信息，使用system_id作为systemeid参数
            Platform platform = platformService.getPlatformById(platformId);
            if (platform == null) {
                logger.error("平台不存在: {}", platformId);
                return results;
            }
            
            Integer systemId = platform.getSystemId();
            if (systemId == null) {
                logger.error("平台未配置system_id: {}", platformId);
                return results;
            }
            
            String baseUrl = scraperConfig.getBaseUrl();
            
            okhttp3.HttpUrl.Builder urlBuilder = okhttp3.HttpUrl.parse(baseUrl + "/api2/jeuRecherche.php").newBuilder();
            
            // 添加基础参数
            urlBuilder.addQueryParameter("devid", scraperConfig.getDevPseudo());
            urlBuilder.addQueryParameter("devpassword", scraperConfig.getDevPassword());
            urlBuilder.addQueryParameter("softname", "FrontendKiller");
            urlBuilder.addQueryParameter("output", "json");
            
            // 添加用户凭证（可选）
            Map<String, String> settings = scraperSettingsService.getSettings();
            if (settings.containsKey("username") && !settings.get("username").isEmpty()) {
                urlBuilder.addQueryParameter("ssid", settings.get("username"));
            }
            if (settings.containsKey("password") && !settings.get("password").isEmpty()) {
                urlBuilder.addQueryParameter("sspassword", settings.get("password"));
            }
            
            // 添加平台的system_id和搜索关键词
            urlBuilder.addQueryParameter("systemeid", systemId.toString());
            urlBuilder.addQueryParameter("recherche", searchTerm);
            
            String url = urlBuilder.build().toString();
            logger.info("搜索游戏URL: {}", url);
            
            String response = executeRequest(url);
            
            if (response != null && !response.isEmpty()) {
                try {
                    JsonNode rootNode = objectMapper.readTree(response);
                    JsonNode responseNode = rootNode.get("response");
                    
                    if (responseNode != null) {
                        JsonNode jeuxNode = responseNode.get("jeux");
                        if (jeuxNode != null && jeuxNode.isArray()) {
                            for (JsonNode jeuNode : jeuxNode) {
                                Map<String, Object> gameMap = new java.util.HashMap<>();
                                
                                // 游戏ID
                                if (jeuNode.has("id")) {
                                    gameMap.put("id", jeuNode.get("id").asText());
                                }
                                
                                // 解析游戏名称（noms数组）
                                String gameName = null;
                                JsonNode nomsNode = jeuNode.get("noms");
                                if (nomsNode != null && nomsNode.isArray()) {
                                    Map<String, String> nomsMap = new java.util.HashMap<>();
                                    for (JsonNode nomNode : nomsNode) {
                                        if (nomNode.has("region") && nomNode.has("text")) {
                                            String region = nomNode.get("region").asText();
                                            String text = nomNode.get("text").asText();
                                            nomsMap.put(region, text);
                                            
                                            // 优先使用ss（ScreenScraper默认）或eu（欧洲）名称
                                            if (gameName == null && ("ss".equals(region) || "eu".equals(region))) {
                                                gameName = text;
                                            }
                                        }
                                    }
                                    if (gameName == null && !nomsMap.isEmpty()) {
                                        gameName = nomsMap.values().iterator().next();
                                    }
                                    if (!nomsMap.isEmpty()) {
                                        gameMap.put("noms", nomsMap);
                                    }
                                }
                                if (gameName != null) {
                                    gameMap.put("nom", gameName);
                                }
                                
                                // 解析发行商（editeur对象）
                                JsonNode editeurNode = jeuNode.get("editeur");
                                if (editeurNode != null && editeurNode.has("text")) {
                                    gameMap.put("editeur", editeurNode.get("text").asText());
                                }
                                
                                // 解析开发商（developpeur对象）
                                JsonNode developpeurNode = jeuNode.get("developpeur");
                                if (developpeurNode != null && developpeurNode.has("text")) {
                                    gameMap.put("developpeur", developpeurNode.get("text").asText());
                                }
                                
                                // 解析玩家数量（joueurs对象）
                                JsonNode joueursNode = jeuNode.get("joueurs");
                                if (joueursNode != null && joueursNode.has("text")) {
                                    gameMap.put("joueurs", joueursNode.get("text").asText());
                                }
                                
                                // 解析评分（note对象）
                                JsonNode noteNode = jeuNode.get("note");
                                if (noteNode != null && noteNode.has("text")) {
                                    gameMap.put("note", noteNode.get("text").asText());
                                }
                                
                                // 解析发布日期（dates数组）
                                JsonNode datesNode = jeuNode.get("dates");
                                if (datesNode != null && datesNode.isArray()) {
                                    for (JsonNode dateNode : datesNode) {
                                        if (dateNode.has("text")) {
                                            String dateText = dateNode.get("text").asText();
                                            // 提取年份（格式：1992-07-28）
                                            if (dateText.length() >= 4) {
                                                gameMap.put("annee", dateText.substring(0, 4));
                                                break;
                                            }
                                        }
                                    }
                                }
                                
                                // 解析游戏类型（genres数组）
                                JsonNode genresNode = jeuNode.get("genres");
                                if (genresNode != null && genresNode.isArray()) {
                                    for (JsonNode genreNode : genresNode) {
                                        JsonNode genreNomsNode = genreNode.get("noms");
                                        if (genreNomsNode != null && genreNomsNode.isArray()) {
                                            for (JsonNode genreNomNode : genreNomsNode) {
                                                if (genreNomNode.has("langue") && genreNomNode.has("text")) {
                                                    String langue = genreNomNode.get("langue").asText();
                                                    String text = genreNomNode.get("text").asText();
                                                    // 优先使用英文类型
                                                    if ("en".equals(langue)) {
                                                        gameMap.put("genre", text);
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                        if (!gameMap.containsKey("genre") && genreNomsNode != null && genreNomsNode.isArray() && genreNomsNode.size() > 0) {
                                            // 如果没有找到英文，使用第一个
                                            JsonNode firstGenreNom = genreNomsNode.get(0);
                                            if (firstGenreNom.has("text")) {
                                                gameMap.put("genre", firstGenreNom.get("text").asText());
                                            }
                                        }
                                    }
                                }
                                
                                // 解析游戏简介（synopsis数组）
                                JsonNode synopsisNode = jeuNode.get("synopsis");
                                if (synopsisNode != null && synopsisNode.isArray()) {
                                    for (JsonNode synNode : synopsisNode) {
                                        if (synNode.has("langue") && synNode.has("text")) {
                                            String langue = synNode.get("langue").asText();
                                            String text = synNode.get("text").asText();
                                            // 优先使用英文简介
                                            if ("en".equals(langue)) {
                                                gameMap.put("description", text);
                                                break;
                                            }
                                        }
                                    }
                                    if (!gameMap.containsKey("description") && synopsisNode.size() > 0) {
                                        // 如果没有找到英文，使用第一个
                                        JsonNode firstSyn = synopsisNode.get(0);
                                        if (firstSyn.has("text")) {
                                            gameMap.put("description", firstSyn.get("text").asText());
                                        }
                                    }
                                }
                                
                                // 解析媒体文件（medias数组）
                                JsonNode mediasNode = jeuNode.get("medias");
                                if (mediasNode != null && mediasNode.isArray()) {
                                    Map<String, List<Map<String, String>>> mediasMap = new java.util.HashMap<>();
                                    
                                    for (JsonNode mediaNode : mediasNode) {
                                        // 只处理parent为jeu的媒体（排除发行商、类型等图标）
                                        if (mediaNode.has("parent") && "jeu".equals(mediaNode.get("parent").asText())) {
                                            String mediaType = mediaNode.has("type") ? mediaNode.get("type").asText() : "unknown";
                                            
                                            Map<String, String> mediaInfo = new java.util.HashMap<>();
                                            if (mediaNode.has("url")) mediaInfo.put("url", mediaNode.get("url").asText());
                                            if (mediaNode.has("region")) mediaInfo.put("region", mediaNode.get("region").asText());
                                            if (mediaNode.has("format")) mediaInfo.put("format", mediaNode.get("format").asText());
                                            if (mediaNode.has("size")) mediaInfo.put("size", mediaNode.get("size").asText());
                                            
                                            if (!mediaInfo.isEmpty()) {
                                                mediasMap.computeIfAbsent(mediaType, k -> new java.util.ArrayList<>()).add(mediaInfo);
                                            }
                                        }
                                    }
                                    
                                    if (!mediasMap.isEmpty()) {
                                        gameMap.put("medias", mediasMap);
                                    }
                                }
                                
                                results.add(gameMap);
                            }
                        }
                    }
                } catch (JsonProcessingException e) {
                    logger.error("解析搜索游戏响应失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("搜索游戏失败: {}", e.getMessage());
        }
        
        return results;
    }

    @Override
    public void downloadGameMedia(Long gameId, String gameName, Long platformId, Map<String, Object> medias) {
        try {
            Platform platform = platformService.getPlatformById(platformId);
            if (platform == null) {
                logger.error("平台不存在: {}", platformId);
                return;
            }
            
            // 创建后台任务
            BackgroundTask bgTask = taskService.createTask("MEDIA_DOWNLOAD", "下载游戏媒体: " + gameName);
            Long bgTaskId = bgTask.getId();
            
            // 使用 MediaType 统一枚举替代硬编码映射表
            
            // 遍历媒体类型
            int orderIndex = 0;
            for (Map.Entry<String, Object> entry : medias.entrySet()) {
                String mediaType = entry.getKey();
                @SuppressWarnings("unchecked")
                List<Map<String, String>> mediaList = (List<Map<String, String>>) entry.getValue();
                
                // 通过 MediaType 统一枚举获取对应的 Java 字段名
                com.gamelist.model.MediaType mt = com.gamelist.model.MediaType.fromNomcourt(mediaType);
                String gameFieldName = (mt != null) ? mt.getJavaField() : null;
                
                if (mediaList != null && !mediaList.isEmpty()) {
                    // 优先选择英文或无语言限制的媒体
                    Map<String, String> selectedMedia = null;
                    for (Map<String, String> media : mediaList) {
                        String langue = media.get("langue");
                        if ("en".equals(langue) || langue == null || langue.isEmpty()) {
                            selectedMedia = media;
                            break;
                        }
                    }
                    
                    if (selectedMedia == null) {
                        selectedMedia = mediaList.get(0);
                    }
                    
                    String url = selectedMedia.get("url");
                    if (url != null && !url.isEmpty()) {
                        // 创建媒体下载任务
                        MediaDownloadTask task = new MediaDownloadTask();
                        task.setTaskId(bgTaskId);
                        task.setGameId(gameId);
                        task.setGameFieldName(gameFieldName);
                        task.setPlatformId(platformId);
                        task.setPlatformName(platform.getName());
                        task.setGameName(gameName);
                        task.setMediaType(mediaType);
                        task.setDownloadUrl(url);
                        task.setStatus("PENDING");
                        task.setOrderIndex((long) orderIndex++);
                        
                        // 设置localPath（需要根据mediaType和平台路径计算）
                        String fileName = url.substring(url.lastIndexOf('/') + 1);
                        String localPath = platform.getFolderPath() + "/" + mediaType + "/" + fileName;
                        task.setLocalPath(localPath);
                        
                        mediaDownloadTaskMapper.insert(task);
                        logger.info("创建媒体下载任务: taskId={}, gameId={}, mediaType={}, url={}", bgTaskId, gameId, mediaType, url);
                    }
                }
            }
            
            // 更新后台任务的总项目数
            taskService.updateTaskProgress(bgTaskId, 0, "媒体下载任务已创建", 0, orderIndex);
            
            // 获取用户最大线程数并启动媒体下载任务
            int maxThreads = getUserMaxThreads();
            logger.info("启动媒体下载任务，taskId={}, 最大线程数={}", bgTaskId, maxThreads);
            mediaDownloadService.startMediaDownloadTask(bgTaskId, maxThreads);
            
        } catch (Exception e) {
            logger.error("创建媒体下载任务失败: {}", e.getMessage());
        }
    }

    /**
     * 获取目标游戏列表
     */
    private List<Game> getTargetGames(ScraperRequest request) {
        List<Game> games;
        
        switch (request.getType()) {
            case "platform":
                games = gameService.getGamesByPlatformId(request.getPlatformId());
                break;
            case "batch":
                if (request.getGameIds() != null && !request.getGameIds().isEmpty()) {
                    games = request.getGameIds().stream()
                        .map(gameService::getGameById)
                        .filter(g -> g != null)
                        .collect(Collectors.toList());
                } else {
                    games = new ArrayList<>();
                }
                break;
            case "single":
                Game game = gameService.getGameById(request.getGameIds().get(0));
                games = game != null ? List.of(game) : new ArrayList<>();
                break;
            default:
                games = new ArrayList<>();
        }
        
        // 如果勾选了"仅刮削信息不全的游戏"，过滤掉已完全刮削的游戏
        if (Boolean.TRUE.equals(request.getOnlyMissing())) {
            games = games.stream()
                .filter(game -> game.getScraped() == null || !game.getScraped())
                .collect(Collectors.toList());
            logger.info("仅刮削信息不全的游戏，过滤后剩余 {} 个游戏", games.size());
        }
        
        return games;
    }
    
    /**
     * 获取系统信息
     */
    private ScraperSystem getSystemInfo(Integer systemId) {
        if (systemId == null) return null;
        
        // 先从缓存获取
        ScraperSystem cached = systemInfoCache.get(systemId);
        if (cached != null) {
            return cached;
        }
        
        // 从数据库获取
        ScraperSystem system = scraperSystemService.getBySystemId(systemId);
        if (system != null) {
            systemInfoCache.put(systemId, system);
        }
        
        return system;
    }
    
    /**
     * 处理游戏文件，提取名称和计算CRC
     */
    private GameFileInfo processGameFile(Game game, ScraperSystem system) {
        GameFileInfo fileInfo = new GameFileInfo();
        String absolutePath = game.getAbsolutePath();
        fileInfo.setFilePath(absolutePath);
        
        logger.info("=== 处理游戏文件 ===");
        logger.info("游戏ID: {}, 游戏名: {}", game.getId(), game.getName());
        logger.info("absolutePath: {}", absolutePath);
        
        if (absolutePath == null || absolutePath.isEmpty()) {
            logger.error("absolutePath 为空，无法处理游戏: {}", game.getName());
            return fileInfo;
        }
        
        String romType = system.getRomType();
        File file = new File(absolutePath);
        
        // 判断文件类型
        FileType fileType = determineFileType(romType, file);
        fileInfo.setFileType(fileType);
        
        // 提取游戏名称
        String gameName = extractGameName(file, fileType);
        fileInfo.setGameName(gameName);
        
        // 计算CRC32
        String crc32 = calculateCRC32(file, fileType, romType);
        fileInfo.setCrc32(crc32);
        
        // 获取文件大小
        long fileSize = getFileSize(file);
        fileInfo.setFileSize(fileSize);
        
        // 更新游戏的crc32字段
        if (crc32 != null && !crc32.isEmpty()) {
            game.setCrc32(crc32);
            gameService.updateGame(game);
        }
        
        return fileInfo;
    }
    
    private FileType determineFileType(String romType, File file) {
        String fileName = file.getName().toLowerCase();
        boolean isArchiveFile = fileName.endsWith(".zip") || fileName.endsWith(".7z");
        
        if (isArchiveFile) {
            int romCount = countRomFilesInArchive(file);
            return romCount == 1 ? FileType.SINGLE_ARCHIVE : FileType.MULTI_ARCHIVE;
        } else {
            if (file.isDirectory()) {
                return FileType.MULTI_FILE;
            } else {
                return FileType.SINGLE_FILE;
            }
        }
    }
    
    private int countRomFilesInArchive(File file) {
        // 简化实现，实际应该解压检查
        return 1;
    }
    
    private String extractGameName(File file, FileType fileType) {
        String fileName = file.getName();
        // 移除扩展名
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            fileName = fileName.substring(0, lastDot);
        }
        // 移除常见后缀
        fileName = fileName.replaceAll("\\s*\\([^)]*\\)$", "");
        fileName = fileName.replaceAll("\\s*\\[[^\\]]*\\]$", "");
        return fileName;
    }
    
    private String calculateCRC32(File file, FileType fileType, String romType) {
        try {
            if (fileType == FileType.SINGLE_FILE) {
                return calculateSingleFileCRC(file);
            } else {
                // 简化实现
                return null;
            }
        } catch (Exception e) {
            logger.error("计算CRC32失败: {}", e.getMessage());
            return null;
        }
    }
    
    private String calculateSingleFileCRC(File file) throws IOException {
        CRC32 crc32 = new CRC32();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                crc32.update(buffer, 0, bytesRead);
            }
        }
        return String.format("%08X", crc32.getValue());
    }
    
    private long getFileSize(File file) {
        if (file.isDirectory()) {
            // 简化实现
            return 0;
        }
        return file.length();
    }
    
    /**
     * 更新游戏记录 - 完整解析 API 返回数据
     */
    private void updateGameRecord(Game game, Map<String, Object> searchResult, ScraperRequest request) {
        try {
            logger.info("========== updateGameRecord 被调用 ==========");
            logger.info("游戏: {}, searchResult keys: {}", game.getName(), searchResult.keySet());
            
            JsonNode jeu = (JsonNode) searchResult.get("game");
            logger.info("jeu == null: {}", jeu == null);
            
            if (jeu == null) {
                logger.info("updateGameRecord: jeu为null，直接返回");
                return;
            }
            
            // 获取用户选择的语种，默认为WOR
            String preferredLang = getLanguageMapping(request.getLanguage());
            logger.info("preferredLang: {}", preferredLang);
            
            // 游戏ID
            if (jeu.has("id")) {
                game.setGameId(jeu.get("id").asText());
            }
            
            // 游戏名称 - 从 noms 数组中提取，优先使用用户选择的语种
            if (jeu.has("noms") && jeu.get("noms").isArray()) {
                // 优先使用用户选择的语种
                String gameName = extractTextFromRegionArray(jeu.get("noms"), preferredLang);
                // 如果用户选择的语种没有，使用WOR
                if (gameName == null || gameName.isEmpty()) {
                    gameName = extractTextFromRegionArray(jeu.get("noms"), "wor");
                }
                if (gameName != null && !gameName.isEmpty()) {
                    game.setName(gameName);
                }
                
                // 尝试获取中文翻译名称作为备用
                if (!"zh".equals(preferredLang)) {
                    String translatedName = extractTextFromRegionArray(jeu.get("noms"), "zh");
                    if (translatedName != null && !translatedName.isEmpty() && !translatedName.equals(gameName)) {
                        game.setTranslatedName(translatedName);
                    }
                }
            }
            
            // 发行商
            if (jeu.has("editeur")) {
                game.setPublisher(getTextValue(jeu.get("editeur")));
            }
            
            // 开发商
            if (jeu.has("developpeur")) {
                game.setDeveloper(getTextValue(jeu.get("developpeur")));
            }
            
            // 玩家数量
            if (jeu.has("joueurs")) {
                game.setPlayers(getTextValue(jeu.get("joueurs")));
            }
            
            // 评分
            if (jeu.has("note")) {
                String note = getTextValue(jeu.get("note"));
                try {
                    game.setRating(Double.parseDouble(note));
                } catch (NumberFormatException e) {
                    logger.warn("解析评分失败: {}", note);
                }
            }
            
            // 类型 - 从 genres 数组中提取
            if (jeu.has("genres") && jeu.get("genres").isArray()) {
                StringBuilder genreBuilder = new StringBuilder();
                StringBuilder genreIdBuilder = new StringBuilder();
                for (JsonNode genreNode : jeu.get("genres")) {
                    String genreText = getTextValue(genreNode);
                    if (genreText == null || isInvalidText(genreText)) continue;
                    
                    if (genreBuilder.length() > 0) genreBuilder.append(", ");
                    if (genreIdBuilder.length() > 0) genreIdBuilder.append(", ");
                    genreBuilder.append(genreText);
                    String genreId = genreNode.has("id") ? genreNode.get("id").asText() : "";
                    genreIdBuilder.append(isInvalidText(genreId) ? "" : genreId);
                }
                game.setGenre(genreBuilder.toString());
                game.setGenreid(genreIdBuilder.toString());
            }
            
            // 简介 - 从 synopsis 数组中提取，优先使用用户选择的语种
            if (jeu.has("synopsis") && jeu.get("synopsis").isArray()) {
                // 优先使用用户选择的语种
                String desc = extractTextFromRegionArray(jeu.get("synopsis"), preferredLang);
                // 如果用户选择的语种没有，使用WOR
                if (desc == null || desc.isEmpty()) {
                    desc = extractTextFromRegionArray(jeu.get("synopsis"), "wor");
                }
                if (desc != null && !desc.isEmpty()) {
                    game.setDesc(desc);
                }
                
                // 尝试获取中文翻译简介作为备用
                if (!"zh".equals(preferredLang)) {
                    String translatedDesc = extractTextFromRegionArray(jeu.get("synopsis"), "zh");
                    if (translatedDesc != null && !translatedDesc.isEmpty() && !translatedDesc.equals(desc)) {
                        game.setTranslatedDesc(translatedDesc);
                    }
                }
            }
            
            // 发行日期 - 从 dates 数组中提取（使用WOR区域）
            if (jeu.has("dates") && jeu.get("dates").isArray()) {
                String releaseDate = extractDateFromRegionArray(jeu.get("dates"), "wor");
                if (releaseDate != null && !releaseDate.isEmpty()) {
                    game.setReleasedate(releaseDate);
                }
            }
            
            // 标记为已刮削
            game.setScraped(true);
            // 设置数据来源
            game.setSource("ScreenScraper");
            
            gameService.updateGame(game);
            logger.info("更新游戏记录: {} (语种: {})", game.getName(), preferredLang);
            
        } catch (Exception e) {
            logger.error("更新游戏记录失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 将语种代码映射到ScreenScraper使用的区域代码
     */
    private String getLanguageMapping(String language) {
        if (language == null || language.isEmpty()) {
            return "wor";
        }
        
        return switch (language.toLowerCase()) {
            case "cn", "zh" -> "zh";
            case "en" -> "en";
            case "jp", "ja" -> "ja";
            case "fr" -> "fr";
            case "de" -> "de";
            case "es" -> "es";
            case "it" -> "it";
            case "pt" -> "pt";
            case "kr", "ko" -> "ko";
            case "tw" -> "zh"; // 繁体中文使用zh区域
            case "ru" -> "ru";
            case "nl" -> "nl";
            case "pl" -> "pl";
            case "se", "sv" -> "sv";
            default -> "wor";
        };
    }
    
    /**
     * 检查文本是否为无效值
     * 包括：null、纯空白、"null"、"nil"、"N/A"、"n/a"、"none" 等
     */
    private boolean isInvalidText(String text) {
        if (text == null || text.isEmpty()) return true;
        String trimmed = text.trim().toLowerCase();
        return trimmed.isEmpty() ||
               "null".equals(trimmed) ||
               "nil".equals(trimmed) ||
               "n/a".equals(trimmed) ||
               "none".equals(trimmed) ||
               "n/a".equals(trimmed.replaceAll("[^a-z/]", "")) ||
               "<null>".equals(trimmed) ||
               "&lt;null&gt;".equals(trimmed) ||
               "undefined".equals(trimmed) ||
               "unknown".equals(trimmed);
    }
    
    /**
     * 从对象中提取 text 字段值
     */
    private String getTextValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String text = null;
        if (node.has("text")) {
            text = node.get("text").asText();
        } else if (node.isTextual()) {
            text = node.asText();
        } else {
            return null;
        }
        if (isInvalidText(text)) {
            return null;
        }
        return text.trim();
    }
    
    /**
     * 从区域数组中提取指定区域的文本
     */
    private String extractTextFromRegionArray(JsonNode arrayNode, String preferredRegion) {
        if (arrayNode == null || !arrayNode.isArray()) return null;
        
        String fallback = null;
        
        for (JsonNode item : arrayNode) {
            String region = item.has("region") ? item.get("region").asText().toLowerCase() : "wor";
            String text = item.has("text") ? item.get("text").asText() : null;
            
            if (isInvalidText(text)) continue;
            
            if (region.equals(preferredRegion.toLowerCase())) {
                return text.trim();
            }
            
            // 优先使用英文作为备用
            if (fallback == null && region.equals("en")) {
                fallback = text;
            }
            
            // 如果还没有备用，使用第一个非空值
            if (fallback == null) {
                fallback = text;
            }
        }
        
        return fallback != null ? fallback.trim() : null;
    }
    
    /**
     * 从日期数组中提取指定区域的日期
     */
    private String extractDateFromRegionArray(JsonNode arrayNode, String preferredRegion) {
        if (arrayNode == null || !arrayNode.isArray()) return null;
        
        String fallback = null;
        
        for (JsonNode item : arrayNode) {
            String region = item.has("region") ? item.get("region").asText().toLowerCase() : "wor";
            String date = item.has("date") ? item.get("date").asText() : null;
            
            if (isInvalidText(date)) continue;
            
            if (region.equals(preferredRegion.toLowerCase())) {
                return date.trim();
            }
            
            if (fallback == null) {
                fallback = date;
            }
        }
        
        return fallback != null ? fallback.trim() : null;
    }
    
    /**
     * 将媒体任务保存到数据库
     * 媒体文件提取逻辑：
     * 1. 如果客户指定了刮削媒体文件的范围，优先取 WOR 区域的客户要求的媒体文件，如果没有 WOR 的就从其他区域取
     * 2. 如果客户要求下载全部种类的媒体文件，优先取 WOR 区域的所有媒体文件，如果有 WOR 区域没有的其他媒体文件也要下载
     */
    private int saveMediaTasksToDb(Game game, Map<String, Object> searchResult, ScraperRequest request,
                                   String platformName, Long platformId, Long taskId, AtomicInteger mediaTaskCounter) {
        try {
            logger.info("========== saveMediaTasksToDb 被调用 ==========");
            logger.info("游戏: {}, searchResult keys: {}", game.getName(), searchResult.keySet());
            
            JsonNode medias = (JsonNode) searchResult.get("medias");
            logger.info("medias 对象: {}", medias);
            logger.info("medias == null: {}", medias == null);
            
            if (medias == null) {
                logger.info("媒体任务未创建: medias字段为空, 游戏: {}", game.getName());
                return 0;
            }
            logger.info("medias.isArray(): {}, medias.size(): {}", medias.isArray(), medias.isArray() ? medias.size() : "N/A");
            
            if (!medias.isArray() || medias.size() == 0) {
                logger.info("媒体任务未创建: medias不是数组或为空数组, 游戏: {}", game.getName());
                return 0;
            }

            String preferredRegion = request.getRegion() != null ? request.getRegion().toLowerCase() : "wor";
            Long gameId = game.getId();

            Path gameMediaDir = Paths.get("/data/scraper/games", platformName, String.valueOf(gameId), preferredRegion);
            Files.createDirectories(gameMediaDir);

            List<MediaDownloadTask> tasksToSave = new ArrayList<>();

            if (Boolean.TRUE.equals(request.getScrapeAllMedia())) {
                // 模式1: 下载全部种类的媒体文件
                // 优先取 WOR 区域的所有媒体文件，如果有 WOR 区域没有的其他媒体文件也要下载
                Map<String, JsonNode> worMediaMap = new HashMap<>();
                Map<String, JsonNode> otherMediaMap = new HashMap<>();

                for (JsonNode media : medias) {
                    if (!media.has("type") || !media.has("url")) continue;

                    String type = media.get("type").asText();
                    String region = media.has("region") ? media.get("region").asText().toLowerCase() : "wor";

                    if (region.equals(preferredRegion)) {
                        worMediaMap.put(type, media);
                    } else {
                        // 非优先区域的媒体，只有在优先区域没有该类型时才保存
                        if (!worMediaMap.containsKey(type)) {
                            otherMediaMap.put(type, media);
                        }
                    }
                }

                // 先处理优先区域的媒体
                for (Map.Entry<String, JsonNode> entry : worMediaMap.entrySet()) {
                    createMediaTask(entry.getKey(), entry.getValue(), gameId, game.getName(), 
                                   platformId, platformName, preferredRegion, gameMediaDir, 
                                   taskId, request, mediaTaskCounter, tasksToSave);
                }

                // 再处理非优先区域的媒体（优先区域没有的类型）
                for (Map.Entry<String, JsonNode> entry : otherMediaMap.entrySet()) {
                    createMediaTask(entry.getKey(), entry.getValue(), gameId, game.getName(), 
                                   platformId, platformName, entry.getValue().has("region") ? 
                                   entry.getValue().get("region").asText().toLowerCase() : "wor", 
                                   gameMediaDir, taskId, request, mediaTaskCounter, tasksToSave);
                }

                logger.info("刮削全部媒体模式: WOR区域 {} 种, 其他区域补充 {} 种, 总计 {}", 
                           worMediaMap.size(), otherMediaMap.size(), worMediaMap.size() + otherMediaMap.size());

            } else {
                // 模式2: 只下载客户指定的媒体类型
                // 优先取 WOR 区域的指定媒体文件，如果没有 WOR 的就从其他区域取
                List<String> requestedTypes = request.getMediaTypes();
                
                for (String mediaType : requestedTypes) {
                    String screenScraperType = mapMediaType(mediaType);
                    if (screenScraperType == null) {
                        screenScraperType = mediaType;
                    }

                    // 查找该类型的媒体文件，优先优先区域
                    JsonNode preferredMedia = null;
                    JsonNode fallbackMedia = null;

                    for (JsonNode media : medias) {
                        if (!media.has("type") || !media.has("url")) continue;

                        String type = media.get("type").asText();
                        if (!type.equalsIgnoreCase(screenScraperType)) continue;

                        String region = media.has("region") ? media.get("region").asText().toLowerCase() : "wor";

                        if (region.equals(preferredRegion)) {
                            preferredMedia = media;
                            break;
                        } else if (fallbackMedia == null) {
                            fallbackMedia = media;
                        }
                    }

                    JsonNode selectedMedia = preferredMedia != null ? preferredMedia : fallbackMedia;
                    if (selectedMedia != null) {
                        String mediaRegion = selectedMedia.has("region") ? 
                            selectedMedia.get("region").asText().toLowerCase() : "wor";
                        createMediaTask(screenScraperType, selectedMedia, gameId, game.getName(), 
                                       platformId, platformName, mediaRegion, gameMediaDir, 
                                       taskId, request, mediaTaskCounter, tasksToSave);
                    } else {
                        logger.warn("未找到媒体类型 [{}] 的任何区域数据，游戏: {}", screenScraperType, game.getName());
                    }
                }
            }

            if (!tasksToSave.isEmpty()) {
                mediaDownloadTaskMapper.insertBatch(tasksToSave);
                logger.info("已保存 {} 个媒体任务 for game {}", tasksToSave.size(), game.getName());
            }

            return tasksToSave.size();
        } catch (Exception e) {
            logger.error("保存媒体任务失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 创建媒体下载任务
     */
    private void createMediaTask(String mediaType, JsonNode mediaNode, Long gameId, String gameName,
                                 Long platformId, String platformName, String region, Path gameMediaDir,
                                 Long taskId, ScraperRequest request, AtomicInteger mediaTaskCounter,
                                 List<MediaDownloadTask> tasksToSave) {
        try {
            // 检查媒体类型是否能映射到 game 表字段
            String gameField = mapMediaTypeToGameField(mediaType);
            if ("ignore".equals(gameField)) {
                // 忽略的媒体类型（如mixrbv1, mixrbv2），不创建任务也不输出日志
                return;
            }
            if (gameField == null) {
                // 输出未匹配的媒体类型日志
                logger.warn("媒体类型 [{}] 无法映射到 game 表字段，游戏: {}，跳过此媒体", mediaType, gameName);
                return; // 无法映射的媒体类型，不创建任务
            }

            String url = mediaNode.get("url").asText();
            if (isInvalidText(url)) {
                logger.warn("媒体类型 [{}] 的URL无效，跳过处理", mediaType);
                return;
            }
            String format = mediaNode.has("format") ? mediaNode.get("format").asText() : "png";
            if (isInvalidText(format)) {
                format = "png";
            }
            
            // 使用真实的文件扩展名
            String extension = "." + format.toLowerCase();
            Path mediaPath = gameMediaDir.resolve(mediaType + extension);

            if (Files.exists(mediaPath) && !request.getOverwrite()) {
                if (!request.getOnlyMissing()) return;
            }

            MediaDownloadTask mediaTask = new MediaDownloadTask();
            mediaTask.setTaskId(taskId);
            mediaTask.setGameId(gameId);
            mediaTask.setGameName(gameName);
            mediaTask.setPlatformId(platformId);
            mediaTask.setPlatformName(platformName);
            mediaTask.setMediaType(mediaType);
            mediaTask.setDownloadUrl(EncryptionUtil.encrypt(url));
            mediaTask.setLocalPath(mediaPath.toString());
            // 如果刮削任务已暂停，新添加的媒体任务也设置为暂停状态
            mediaTask.setStatus(isScrapingPaused.get() ? MediaDownloadTask.STATUS_STOPPED : MediaDownloadTask.STATUS_PENDING);
            mediaTask.setOrderIndex(Long.valueOf(mediaTaskCounter.getAndIncrement()));

            tasksToSave.add(mediaTask);

            logger.debug("添加媒体任务: {} (区域: {}) for game {}", mediaType, region, gameName);
        } catch (Exception e) {
            logger.warn("创建媒体任务失败[{}]: {}", mediaType, e.getMessage());
        }
    }
    
    /**
     * 媒体类型到 game 表字段的映射
     * 
     * 🖼️ 游戏图片类:
     * - ss: 游戏截图
     * - sstitle: 标题截图
     * - support-2d/support-3d: 2D/3D封面
     * - wheel/wheel-carbon/wheel-steel: 彩色标题图标
     * - marquee/screenmarquee: 横条图
     * - fanart: 背景图
     * - flyer: 海报
     * - mixrbv1/mixrbv2: 混合资源
     * 
     * 🎬 视频类:
     * - video: 游戏视频
     * 
     * 📄 文档类:
     * - manuel: 游戏手册(PDF)
     * 
     * 🏷️ 分类与图标类:
     * - pictoliste: 列表图标
     * - pictomonochrome/pictomonochromesvg: 单色图标
     * - pictocouleur: 彩色图标
     */
    private String mapMediaTypeToGameField(String mediaType) {
        // 优先使用 MediaType 统一枚举精确匹配
        com.gamelist.model.MediaType mt = com.gamelist.model.MediaType.fromNomcourt(mediaType);
        if (mt != null) {
            return mt.getDbColumn();
        }
        // 容错：模糊匹配（忽略大小写和连字符）
        mt = com.gamelist.model.MediaType.fromNomcourtLenient(mediaType);
        if (mt != null) {
            return mt.getDbColumn();
        }
        // 非标准类型回退处理
        return switch (mediaType.toLowerCase()) {
            case "ssmap" -> "ss";
            case "boxvierge" -> "box_2d";
            case "minicon" -> "thumbnail";
            case "intro" -> "video";
            case "photo", "illustration", "controller" -> "fanart";
            default -> null;
        };
    }
    
    /**
     * 将下载的媒体文件路径更新到游戏记录中
     */
    private void updateGameMediaPath(Long gameId, String mediaType, String localPath) {
        try {
            if (gameId == null || mediaType == null || localPath == null) {
                return;
            }
            
            // 将媒体类型映射到游戏字段
            String gameField = mapMediaTypeToGameField(mediaType);
            logger.info("updateGameMediaPath: mediaType={}, gameField={}", mediaType, gameField);
            if (gameField == null || "ignore".equals(gameField)) {
                logger.warn("媒体类型 [{}] 无法映射或被忽略，跳过更新", mediaType);
                return;
            }
            
            // 获取游戏记录
            Game game = gameService.getGameById(gameId);
            if (game == null) {
                logger.warn("游戏不存在，无法更新媒体路径: gameId={}", gameId);
                return;
            }
            
            // 根据字段名设置媒体路径（使用 MediaType 枚举 + 反射替代硬编码 switch-case）
            logger.info("开始设置游戏 {} 的媒体字段: {}", game.getName(), gameField);
            boolean fieldSet = false;
            
            // 1. 尝试通过 MediaType 枚举查找并反射调用 setter
            com.gamelist.model.MediaType mt = com.gamelist.model.MediaType.fromDbColumn(gameField);
            if (mt != null) {
                try {
                    java.lang.reflect.Method setter = Game.class.getMethod(mt.getSetterName(), String.class);
                    setter.invoke(game, localPath);
                    fieldSet = true;
                } catch (Exception e) {
                    logger.warn("反射设置字段失败: setter={}, error={}", mt.getSetterName(), e.getMessage());
                }
            }
            
            // 2. 回退：非 ScreenScraper 标准字段的处理
            if (!fieldSet) {
                switch (gameField) {
                    case "image" -> { game.setImage(localPath); fieldSet = true; }
                    case "video" -> { game.setVideo(localPath); fieldSet = true; }
                    case "marquee" -> { game.setMarquee(localPath); fieldSet = true; }
                    case "thumbnail" -> { game.setThumbnail(localPath); fieldSet = true; }
                    case "wheel" -> { game.setWheel(localPath); fieldSet = true; }
                    case "logo" -> { game.setLogo(localPath); fieldSet = true; }
                    case "panel" -> { game.setPanel(localPath); fieldSet = true; }
                    case "cabinet_left" -> { game.setCabinetLeft(localPath); fieldSet = true; }
                    case "cabinet_right" -> { game.setCabinetRight(localPath); fieldSet = true; }
                    case "tile" -> { game.setTile(localPath); fieldSet = true; }
                    case "banner" -> { game.setBanner(localPath); fieldSet = true; }
                    case "steam" -> { game.setSteam(localPath); fieldSet = true; }
                    case "poster" -> { game.setPoster(localPath); fieldSet = true; }
                    case "background" -> { game.setBackground(localPath); fieldSet = true; }
                    case "music" -> { game.setMusic(localPath); fieldSet = true; }
                    case "pictoliste" -> { game.setPictoliste(localPath); fieldSet = true; }
                    case "pictomonochrome" -> { game.setPictomonochrome(localPath); fieldSet = true; }
                    case "pictomonochromesvg" -> { game.setPictomonochromesvg(localPath); fieldSet = true; }
                    case "pictocouleur" -> { game.setPictocouleur(localPath); fieldSet = true; }
                    case "wallpaper" -> { game.setWallpaper(localPath); fieldSet = true; }
                    default -> logger.warn("无法映射媒体类型到游戏字段: mediaType={}, gameField={}", mediaType, gameField);
                }
            }
            
            if (!fieldSet) {
                return;
            }
            
            // 更新游戏记录
            int result = gameService.updateGame(game);
            if (result > 0) {
                logger.info("更新游戏媒体路径成功: gameId={}, mediaType={}, localPath={}", gameId, mediaType, localPath);
            } else {
                logger.warn("更新游戏媒体路径失败，未影响任何记录: gameId={}, mediaType={}", gameId, mediaType);
            }
            
        } catch (Exception e) {
            logger.error("更新游戏媒体路径失败: gameId={}, mediaType={}, error={}", gameId, mediaType, e.getMessage());
        }
    }
    
    private String mapMediaType(String mediaType) {
        // 1. 精确匹配 nomcourt
        com.gamelist.model.MediaType mt = com.gamelist.model.MediaType.fromNomcourt(mediaType);
        if (mt != null) { return mt.getNomcourt(); }
        // 2. 模糊匹配（忽略大小写和连字符）
        mt = com.gamelist.model.MediaType.fromNomcourtLenient(mediaType);
        if (mt != null) { return mt.getNomcourt(); }
        // 3. 遗留别名回退（非 ScreenScraper 标准的旧名称）
        return switch (mediaType.toLowerCase()) {
            case "boxfront", "box-front" -> "box-2D";
            case "boxspine", "box-spine" -> "box-2D-side";
            case "boxback", "box-back" -> "box-2D-back";
            case "box" -> "box-2D";
            case "support3d", "support-3d" -> "box-3D";
            case "arcademarquee" -> "marquee";
            case "backgrounds" -> "background";
            case "fan-art" -> "fanart";
            case "flyer-2d" -> "flyer";
            case "titlescreens" -> "sstitle";
            default -> mediaType;
        };
    }
    
    private String getMediaExtension(String mediaType) {
        // 简化实现
        return ".png";
    }
    
    private String getMediaUrlWithFallback(JsonNode medias, String mediaType, String preferredRegion) {
        if (medias == null || !medias.isArray()) return null;
        
        String fallbackUrl = null;
        
        for (JsonNode media : medias) {
            if (!media.has("type") || !media.has("url")) continue;
            
            String type = media.get("type").asText();
            if (!type.equalsIgnoreCase(mediaType)) continue;
            
            String url = media.get("url").asText();
            if (isInvalidText(url)) continue;
            
            String region = media.has("region") ? media.get("region").asText().toLowerCase() : "wor";
            
            if (region.equals(preferredRegion.toLowerCase())) {
                return url;
            }
            
            if (fallbackUrl == null) {
                fallbackUrl = url;
            }
        }
        
        return fallbackUrl;
    }
    
    /**
     * 下载媒体文件
     */
    private void downloadMediaFile(String urlStr, String localPath) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);

        try (java.io.InputStream inputStream = connection.getInputStream();
             java.io.FileOutputStream outputStream = new java.io.FileOutputStream(localPath)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } finally {
            connection.disconnect();
        }
    }
}