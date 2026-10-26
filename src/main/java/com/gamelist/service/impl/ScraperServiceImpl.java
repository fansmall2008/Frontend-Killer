package com.gamelist.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelist.config.ScreenScraperConfig;
import com.gamelist.mapper.MediaDownloadTaskMapper;
import com.gamelist.mapper.ScrapeTaskMapper;
import com.gamelist.mapper.FieldTranslationMapper;
import com.gamelist.model.BackgroundTask;
import com.gamelist.model.FileType;
import com.gamelist.model.FieldTranslation;
import com.gamelist.model.Game;
import com.gamelist.model.GameFileInfo;
import com.gamelist.model.MediaDownloadTask;
import com.gamelist.model.Platform;
import com.gamelist.model.ScrapeTask;
import com.gamelist.model.ScraperRequest;
import com.gamelist.model.ScraperSystem;
import com.gamelist.service.GameService;
import com.gamelist.service.MediaDownloadService;
import com.gamelist.service.NotificationService;
import com.gamelist.service.PlatformService;
import com.gamelist.service.ScrapeStatus;
import com.gamelist.service.ScrapeWorkerPool;
import com.gamelist.service.ScraperService;
import com.gamelist.service.ScraperSettingsService;
import com.gamelist.service.ScraperSystemService;
import com.gamelist.service.TaskService;
import com.gamelist.service.ThreadResourceManager;
import com.gamelist.util.ScreenScraperApiException;
import com.gamelist.util.ScreenScraperStatusHandler;
import com.gamelist.util.EncryptionUtil;
import com.gamelist.util.PathResolver;
import com.gamelist.util.SsNameNormalizer;

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
    private ScrapeTaskMapper scrapeTaskMapper;

    @Autowired
    private FieldTranslationMapper fieldTranslationMapper;
    
    @Autowired
    private ThreadResourceManager threadResourceManager;
    
    @Autowired
    private MediaDownloadService mediaDownloadService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private com.gamelist.service.GameManifestService gameManifestService;

    @Autowired
    private com.gamelist.service.SystemSettingsService systemSettingsService;

    @Autowired(required = false)
    private com.gamelist.service.RomDbService romDbService;

    @Autowired(required = false)
    private com.gamelist.config.RomDbMatchingConfig romDbMatchingConfig;
    
    @Lazy
    @Autowired(required = false)
    private ScrapeWorkerPool scrapeWorkerPool;
    
    @Lazy
    @Autowired
    private ScrapeStatus scrapeStatus;

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
    
    public ScraperServiceImpl(@Qualifier("sharedOkHttpClient") OkHttpClient httpClient) {
        this.httpClient = httpClient;
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
        
        // 4. 重置控制标志
        isScrapingStopped.set(false);
        isScrapingPaused.set(false);
        
        // ★ 恢复工作线程池（防止上次停止后 listener 仍处于暂停状态）
        if (scrapeWorkerPool != null) {
            scrapeWorkerPool.resume();
        }
        
        // 5. 创建后台任务
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
        logger.info("正在创建刮削任务...");
        
        // 5. 获取游戏列表并创建任务
        List<Game> games = getTargetGames(request);
        int totalGames = games.size();
        
        if (totalGames == 0) {
            taskService.completeTask(taskId, "没有游戏需要刮削", "没有游戏需要刮削");
            return Map.of("success", true, "taskId", taskId, "message", "没有游戏需要刮削");
        }
        
        // 6. 创建游戏信息刮削任务（写入 scrape_task 表）
        enqueueGameInfoTasks(games, request, platform.getSystemId(), taskId, ScrapeTask.PRIORITY_USER_SCRAPE);
        
        logger.info("已创建 {} 个游戏信息刮削任务，等待 ScrapeWorkerPool 处理", totalGames);
        
        return Map.of(
            "success", true,
            "taskId", taskId,
            "message", String.format("刮削任务已创建，共 %d 个游戏。系统会自动处理。", totalGames)
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
            // ★ 关键：刮削启动时先重置资源管理器，确保不受上一次任务（刮削或媒体下载）的残留状态影响
            // 修复日志中 observed 的计数器损坏问题：mediaActive=-6, availableThreads=12, gameInfoActive=12
            threadResourceManager.reset();
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
            boolean scrapeAllMedia = Boolean.TRUE.equals(request.getScrapeAllMedia());
            List<String> requestedMediaTypes = request.getMediaTypes();
            
            logger.info("刮削配置: scrapeGameInfo={}, scrapeMedia={}, scrapeAllMedia={}, mediaTypes={}", 
                scrapeGameInfo, scrapeMedia, scrapeAllMedia, requestedMediaTypes);
            
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
                    boolean acquired = false;
                    try {
                        // ★ 暂停检查放在获取资源之前，避免暂停时占用资源槽位
                        while (isScrapingPaused.get() && !isScrapingStopped.get()) {
                            Thread.sleep(1000);
                        }
                        if (isScrapingStopped.get()) {
                            return;
                        }
                        
                        // 获取资源（在暂停检查之后）
                        acquired = threadResourceManager.acquireForGameInfo(60000); // 60秒超时
                        if (!acquired) {
                            logger.error("游戏信息刮削获取资源超时: {}", game.getName());
                            failedCount.incrementAndGet();
                            return;
                        }
                        
                        // 二次检查：获取资源期间可能被暂停/停止
                        if (isScrapingStopped.get()) {
                            return;
                        }
                        while (isScrapingPaused.get() && !isScrapingStopped.get()) {
                            // 释放资源后再等待暂停，避免占用槽位
                            threadResourceManager.releaseForGameInfo();
                            acquired = false;
                            logger.info("游戏刮削任务已暂停，已释放资源，等待恢复...");
                            while (isScrapingPaused.get() && !isScrapingStopped.get()) {
                                Thread.sleep(1000);
                            }
                            if (isScrapingStopped.get()) {
                                return;
                            }
                            // 恢复后重新获取资源
                            acquired = threadResourceManager.acquireForGameInfo(60000);
                            if (!acquired) {
                                logger.error("恢复后获取资源超时: {}", game.getName());
                                failedCount.incrementAndGet();
                                return;
                            }
                        }
                        
                        logger.info("开始刮削游戏: {} [资源状态: {}]", game.getName(), 
                            threadResourceManager.getSnapshot());
                        
                        // 处理游戏文件
                        GameFileInfo fileInfo = processGameFile(game, system);
                        
                        // 调用ScreenScraper API搜索游戏
                        Map<String, Object> searchResult = searchGameWithStatus(fileInfo, system.getSystemId(), request);
                        
                        // 限额错误：暂停刮削（可恢复），线程进入等待而非退出
                        if (Boolean.TRUE.equals(searchResult.get("shouldPause"))) {
                            String warningMessage = (String) searchResult.get("message");
                            Integer statusCode = (Integer) searchResult.get("statusCode");
                            logger.error("限额触发，暂停刮削: 状态码={}, 消息={}", statusCode, warningMessage);
                            isScrapingPaused.set(true);
                            taskService.updateTaskLog(taskId, warningMessage);
                            sendNotification("刮削警告", warningMessage, "warning");
                            // ★ 不 return，释放资源后进入 finally，latch 正常 countDown
                            // 其他尚未开始的游戏线程会在暂停检查处等待
                        } else if ((Boolean) searchResult.get("shouldStop")) {
                            String errorMessage = (String) searchResult.get("message");
                            logger.error("遇到不可恢复的错误，停止刮削: {}", errorMessage);
                            isScrapingStopped.set(true);
                            taskService.updateTaskLog(taskId, "刮削已停止: " + errorMessage);
                            sendNotification("刮削停止", errorMessage, "error");
                            return;
                        } else if ((Boolean) searchResult.get("found")) {
                            logger.info("游戏已找到，开始处理: gameId={}, gameName={}", game.getId(), game.getName());
                            
                            Map<String, Object> data = (Map<String, Object>) searchResult.get("data");
                            
                            if (scrapeGameInfo) {
                                // v1.3: 光盘编号写回 game 记录
                                String discSerial = (String) searchResult.get("discSerial");
                                if (discSerial != null) {
                                    game.setDiscSerial(discSerial);
                                }
                                updateGameRecord(game, data, request);
                            }

                            if (scrapeMedia) {
                                int taskCount = saveMediaTasksToDb(game, data, request, system.getName(), request.getPlatformId(), systemId, taskId, mediaTaskCounter);
                                logger.info("媒体任务保存完成，创建了 {} 个任务", taskCount);
                            }

                            successCount.incrementAndGet();
                            taskService.updateTaskLog(taskId, "刮削完成: " + game.getName());
                        } else {
                            if (shouldStopDueToNotFound()) {
                                logger.error("10秒内出现10次404错误，停止刮削");
                                isScrapingStopped.set(true);
                                sendNotification("刮削停止", "10秒内出现10次404错误，已停止刮削", "error");
                            }
                            taskService.updateTaskLog(taskId, "未找到: " + game.getName());
                        }
                        
                    } catch (Throwable e) {
                        failedCount.incrementAndGet();
                        logger.error("刮削游戏失败: {} - {} ({})", game.getName(), e.getMessage(), e.getClass().getName());
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
            
            // 等待游戏信息刮削完成（Phase 1）
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
            logger.info("  ✅ Phase 1 完成：游戏信息刮削全部结束");
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        
            // ★ Phase 2：信息刮削完成后，启动媒体下载（串行执行，maxThreads 全部给媒体下载用）
            if (scrapeMedia && !isScrapingStopped.get()) {
                long pendingMediaCount = mediaDownloadTaskMapper.countPendingByTaskId(taskId);
                logger.info("Phase 2 启动: 待下载媒体任务数 = {}", pendingMediaCount);
                            
                if (pendingMediaCount > 0) {
                    logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    logger.info("  📍 Phase 2: 开始媒体下载 (maxThreads={})", maxThreads);
                    logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                                
                    // 调用 MediaDownloadService 启动媒体下载任务
                    // 此时信息刮削已结束，ThreadResourceManager 的 gameInfoWaiting=0，媒体可占满 maxThreads
                    mediaDownloadService.startMediaDownloadTask(taskId, maxThreads);
                                
                    // 等待媒体下载完成（轮询 MediaDownloadService 的运行状态）
                    logger.info("等待媒体下载完成...");
                    long lastPendingCount = -1;
                    int noChangeCount = 0;
                    while (!isScrapingStopped.get()) {
                        // 暂停时不检测“卡住”，等待恢复即可
                        if (isScrapingPaused.get()) {
                            Thread.sleep(1000);
                            continue;
                        }
                                    
                        // 检查媒体下载服务是否还在运行
                        if (!mediaDownloadService.isRunning()) {
                            logger.info("媒体下载服务已停止运行，检查任务状态...");
                            // 再次确认数据库中是否还有待处理任务
                            long remaining = mediaDownloadTaskMapper.countPendingByTaskId(taskId) 
                                + mediaDownloadTaskMapper.countByTaskIdAndStatus(taskId, MediaDownloadTask.STATUS_DOWNLOADING);
                            if (remaining == 0) {
                                logger.info("所有媒体下载任务已完成");
                                break;
                            } else {
                                logger.warn("媒体下载服务已停止但仍有 {} 条未完成任务，继续等待...", remaining);
                            }
                        }
                                    
                        long pendingCount = mediaDownloadTaskMapper.countPendingByTaskId(taskId);
                        long downloadingCount = mediaDownloadTaskMapper.countByTaskIdAndStatus(taskId, MediaDownloadTask.STATUS_DOWNLOADING);
                                    
                        // 检测是否卡住（连绕20次pending数量不变）
                        if (pendingCount == lastPendingCount && downloadingCount == 0) {
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
                } else {
                    logger.info("没有待下载的媒体任务，跳过 Phase 2");
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
            // 完成通知由 TaskService.completeTask 统一发送，避免重复
            
        } catch (Exception e) {
            logger.error("刮削任务失败: {}", e.getMessage(), e);
            taskService.failTask(taskId, "刮削失败", e.getMessage());
            // 失败通知由 TaskService.failTask 统一发送，避免重复
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
     * 搜索游戏（带状态码处理）
     * 关键改进：检测 ScreenScraper 限额状态码，返回 shouldPause 而非 shouldStop
     */
    private Map<String, Object> searchGameWithStatus(GameFileInfo fileInfo, Integer systemId, ScraperRequest request) {
        try {
            Map<String, Object> searchResult = searchGame(fileInfo, systemId, request);
            
            // 检查是否遇到限额错误（可恢复的暂停）
            Boolean isLimitError = (Boolean) searchResult.get("isLimitError");
            Integer apiStatusCode = (Integer) searchResult.get("apiStatusCode");
            if (Boolean.TRUE.equals(isLimitError) && apiStatusCode != null) {
                String warningMessage = ScreenScraperStatusHandler.getLimitWarningMessage(apiStatusCode);
                logger.error("检测到限额限制，暂停刮削: 状态码={}, 消息={}", apiStatusCode, warningMessage);
                
                Map<String, Object> result = new HashMap<>();
                result.put("found", false);
                result.put("shouldStop", false);
                result.put("shouldPause", true);
                result.put("message", warningMessage);
                result.put("statusCode", apiStatusCode);
                result.put("data", searchResult);
                return result;
            }
            
            // 检查是否需要因404过多而停止
            if (!(Boolean) searchResult.get("found")) {
                if (shouldStopDueToNotFound()) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("found", false);
                    result.put("shouldStop", true);
                    result.put("shouldPause", false);
                    result.put("message", "10秒内出现10次404错误");
                    result.put("data", searchResult);
                    return result;
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("found", searchResult.get("found"));
            result.put("shouldStop", false);
            result.put("shouldPause", false);
            result.put("data", searchResult);
            return result;
        } catch (Exception e) {
            logger.error("搜索游戏异常: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("found", false);
            result.put("shouldStop", false);
            result.put("shouldPause", false);
            return result;
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
        // 暂停工作线程池
        if (scrapeWorkerPool != null) {
            scrapeWorkerPool.pause();
        }
        logger.info("刮削任务已暂停");
    }
    
    /**
     * 恢复刮削任务
     */
    public void resumeScraping() {
        isScrapingPaused.set(false);
        isScrapingStopped.set(false);
        // 恢复工作线程池
        if (scrapeWorkerPool != null) {
            scrapeWorkerPool.resume();
        }
        
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
        // 通知工作线程池停止
        if (scrapeWorkerPool != null) {
            scrapeWorkerPool.stopAll();
        }
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

            String response;
            try {
                response = executeRequest(url);
            } catch (ScreenScraperApiException e) {
                if (e.isLimitError()) {
                    logger.error("获取用户信息时遇到限额限制: 状态码={}, 消息={}", e.getStatusCode(), e.getLimitWarningMessage());
                } else {
                    logger.error("获取用户信息时遇到 API 错误: 状态码={}, 消息={}", e.getStatusCode(), e.getMessage());
                }
                return 1;
            }
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
                    
                    // 同步更新 ScrapeStatus 配额（确保前端 platform-progress API 读到最新值）
                    scrapeStatus.updateQuotaFromSsuser(ssuserNode);
                    
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
    
    private void sendNotification(String title, String message, String type) {
        notificationService.send(title, message, type, "scraper");
    }
    
    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new java.util.HashMap<>();
        
        // 从 scrape_task 表读取实时数据（不再依赖可能过期的 AtomicInteger 计数器）
        int pending = scrapeTaskMapper.countByStatus(ScrapeTask.STATUS_PENDING);
        int running = scrapeTaskMapper.countByStatus(ScrapeTask.STATUS_RUNNING);
        int completed = scrapeTaskMapper.countByStatus(ScrapeTask.STATUS_COMPLETED);
        int failed = scrapeTaskMapper.countByStatus(ScrapeTask.STATUS_FAILED);
        int stopped = scrapeTaskMapper.countByStatus(ScrapeTask.STATUS_STOPPED);
        int total = pending + running + completed + failed + stopped;
        int scraped = completed + failed + stopped;
        
        boolean hasActiveTasks = (pending + running) > 0;
        boolean isRunning = hasActiveTasks && !isScrapingStopped.get();
        
        status.put("isRunning", isRunning);
        status.put("isPaused", isScrapingPaused.get());
        status.put("scrapedCount", scraped);
        status.put("totalCount", total);
        status.put("pendingCount", pending);
        status.put("processingCount", running);
        status.put("failedCount", failed);
        status.put("stoppedCount", stopped);
        
        // 分类统计：游戏信息 vs 媒体下载
        int giPending = scrapeTaskMapper.countByTypeAndStatus(ScrapeTask.TYPE_GAME_INFO, ScrapeTask.STATUS_PENDING);
        int giRunning = scrapeTaskMapper.countByTypeAndStatus(ScrapeTask.TYPE_GAME_INFO, ScrapeTask.STATUS_RUNNING);
        int giCompleted = scrapeTaskMapper.countByTypeAndStatus(ScrapeTask.TYPE_GAME_INFO, ScrapeTask.STATUS_COMPLETED);
        int giFailed = scrapeTaskMapper.countByTypeAndStatus(ScrapeTask.TYPE_GAME_INFO, ScrapeTask.STATUS_FAILED);
        
        int mdPending = scrapeTaskMapper.countByTypeAndStatus(ScrapeTask.TYPE_MEDIA_DOWNLOAD, ScrapeTask.STATUS_PENDING);
        int mdRunning = scrapeTaskMapper.countByTypeAndStatus(ScrapeTask.TYPE_MEDIA_DOWNLOAD, ScrapeTask.STATUS_RUNNING);
        int mdCompleted = scrapeTaskMapper.countByTypeAndStatus(ScrapeTask.TYPE_MEDIA_DOWNLOAD, ScrapeTask.STATUS_COMPLETED);
        int mdFailed = scrapeTaskMapper.countByTypeAndStatus(ScrapeTask.TYPE_MEDIA_DOWNLOAD, ScrapeTask.STATUS_FAILED);
        
        status.put("gameInfoPending", giPending);
        status.put("gameInfoRunning", giRunning);
        status.put("gameInfoCompleted", giCompleted);
        status.put("gameInfoFailed", giFailed);
        status.put("mediaPending", mdPending);
        status.put("mediaRunning", mdRunning);
        status.put("mediaCompleted", mdCompleted);
        status.put("mediaFailed", mdFailed);
        
        return status;
    }

    // ==================== 平台进度追踪与统一控制 ====================

    /** 平台级暂停/停止标志（key=platformId） */
    private final ConcurrentHashMap<Long, AtomicBoolean> platformPausedFlags = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicBoolean> platformStoppedFlags = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> getPlatformProgress() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();

        // 1. 查询 scrape_task 按 platform_id + task_type + status 分组统计
        List<Map<String, Object>> scrapeSummary = scrapeTaskMapper.selectPlatformTaskTypeSummary();
        // 2. 查询 media_download_task 按 platform_id + status 分组统计
        List<Map<String, Object>> mediaSummary = mediaDownloadTaskMapper.selectPlatformStatusSummary();
        // 3. 查询当前 RUNNING 的任务（获取 currentGame）
        List<Map<String, Object>> runningTasks = scrapeTaskMapper.selectRunningTasksWithNames();

        // 收集所有出现过的 platformId
        Set<Long> allPlatformIds = new java.util.LinkedHashSet<>();
        for (Map<String, Object> row : scrapeSummary) {
            Object pid = row.get("platformId");
            if (pid != null) allPlatformIds.add(((Number) pid).longValue());
        }
        for (Map<String, Object> row : mediaSummary) {
            Object pid = row.get("platformId");
            if (pid != null) allPlatformIds.add(((Number) pid).longValue());
        }

        // 构建每个平台的统计数据
        // scrape: platformId -> taskType -> status -> cnt
        Map<Long, Map<String, Map<String, Long>>> scrapeStats = new HashMap<>();
        for (Map<String, Object> row : scrapeSummary) {
            long pid = ((Number) row.get("platformId")).longValue();
            String taskType = (String) row.get("taskType");
            String status = (String) row.get("status");
            long cnt = ((Number) row.get("cnt")).longValue();
            scrapeStats.computeIfAbsent(pid, k -> new HashMap<>())
                       .computeIfAbsent(taskType, k -> new HashMap<>())
                       .put(status, cnt);
        }
        // media: platformId -> status -> cnt
        Map<Long, Map<String, Long>> mediaStats = new HashMap<>();
        for (Map<String, Object> row : mediaSummary) {
            long pid = ((Number) row.get("platformId")).longValue();
            String status = (String) row.get("status");
            long cnt = ((Number) row.get("cnt")).longValue();
            mediaStats.computeIfAbsent(pid, k -> new HashMap<>()).put(status, cnt);
        }
        // running: platformId -> gameName
        Map<Long, String> currentGames = new HashMap<>();
        for (Map<String, Object> row : runningTasks) {
            long pid = ((Number) row.get("platformId")).longValue();
            if (!currentGames.containsKey(pid)) {
                String gameName = (String) row.get("gameName");
                String romFilename = (String) row.get("romFilename");
                currentGames.put(pid, gameName != null ? gameName : romFilename);
            }
        }

        // 获取平台名称
        Map<Long, String> platformNames = new HashMap<>();
        for (Long pid : allPlatformIds) {
            try {
                Platform platform = platformService.getPlatformById(pid);
                if (platform != null) {
                    platformNames.put(pid, platform.getName());
                }
            } catch (Exception e) {
                logger.debug("获取平台名称失败: platformId={}", pid);
            }
        }

        // 组装平台列表
        List<Map<String, Object>> platforms = new java.util.ArrayList<>();
        boolean anyInfoRunning = false, anyMediaRunning = false;
        boolean anyInfoPending = false, anyMediaPending = false;

        for (Long pid : allPlatformIds) {
            Map<String, Object> platformData = new java.util.LinkedHashMap<>();
            platformData.put("platformId", pid);
            platformData.put("platformName", platformNames.getOrDefault(pid, "Platform " + pid));

            // 信息刮削统计
            Map<String, Map<String, Long>> pScrape = scrapeStats.getOrDefault(pid, Map.of());
            Map<String, Long> giStats = pScrape.getOrDefault(ScrapeTask.TYPE_GAME_INFO, Map.of());
            long giCompleted = giStats.getOrDefault(ScrapeTask.STATUS_COMPLETED, 0L);
            long giFailed = giStats.getOrDefault(ScrapeTask.STATUS_FAILED, 0L);
            long giRunning = giStats.getOrDefault(ScrapeTask.STATUS_RUNNING, 0L);
            long giPending = giStats.getOrDefault(ScrapeTask.STATUS_PENDING, 0L);
            long giStopped = giStats.getOrDefault(ScrapeTask.STATUS_STOPPED, 0L);
            long infoTotal = giCompleted + giFailed + giRunning + giPending + giStopped;

            // 媒体下载统计（来自 media_download_task 表）
            Map<String, Long> mdStats = mediaStats.getOrDefault(pid, Map.of());
            long mdCompleted = mdStats.getOrDefault("COMPLETED", 0L);
            long mdFailed = mdStats.getOrDefault("FAILED", 0L);
            long mdDownloading = mdStats.getOrDefault("DOWNLOADING", 0L);
            long mdPendingCount = mdStats.getOrDefault("PENDING", 0L);
            long mdStopped = mdStats.getOrDefault("STOPPED", 0L);
            long mediaTotal = mdCompleted + mdFailed + mdDownloading + mdPendingCount + mdStopped;

            // 判断平台阶段
            String phase;
            boolean isPlatformPaused = getPlatformPausedFlag(pid).get();
            boolean isPlatformStopped = getPlatformStoppedFlag(pid).get();
            if (isPlatformPaused) {
                phase = "PAUSED";
            } else if (isPlatformStopped) {
                phase = "STOPPED";
            } else if (giRunning > 0 || giPending > 0) {
                phase = "INFO_SCRAPING";
                anyInfoRunning = anyInfoRunning || giRunning > 0;
                anyInfoPending = anyInfoPending || giPending > 0;
            } else if (mdDownloading > 0 || mdPendingCount > 0) {
                phase = "MEDIA_DOWNLOAD";
                anyMediaRunning = anyMediaRunning || mdDownloading > 0;
                anyMediaPending = anyMediaPending || mdPendingCount > 0;
            } else if (infoTotal == 0 && mediaTotal == 0) {
                phase = "WAITING";
            } else {
                phase = "COMPLETED";
            }

            platformData.put("phase", phase);
            platformData.put("currentGame", currentGames.getOrDefault(pid, ""));
            platformData.put("infoTotal", infoTotal);
            platformData.put("infoCompleted", giCompleted);
            platformData.put("infoFailed", giFailed);
            platformData.put("mediaTotal", mediaTotal);
            platformData.put("mediaCompleted", mdCompleted);
            platformData.put("mediaFailed", mdFailed);
            platformData.put("mediaPending", mdPendingCount);

            platforms.add(platformData);
        }

        // 计算全局阶段
        String globalPhase;
        if (isScrapingPaused.get()) {
            globalPhase = "PAUSED";
        } else if (isScrapingStopped.get()) {
            globalPhase = "STOPPED";
        } else if (anyInfoRunning || anyInfoPending) {
            globalPhase = "INFO_SCRAPING";
        } else if (anyMediaRunning || anyMediaPending) {
            globalPhase = "MEDIA_DOWNLOAD";
        } else if (platforms.isEmpty()) {
            globalPhase = "WAITING";
        } else {
            globalPhase = "COMPLETED";
        }

        result.put("platforms", platforms);
        result.put("globalPhase", globalPhase);
        result.put("isPaused", isScrapingPaused.get());
        result.put("maxThreads", scrapeStatus.getMaxThreads());

        return result;
    }

    @Override
    public void pauseAll() {
        isScrapingPaused.set(true);
        // 暂停工作线程池（信息刮削）
        if (scrapeWorkerPool != null) {
            scrapeWorkerPool.pause();
        }
        // 暂停媒体下载
        mediaDownloadService.pauseMediaDownloadTask();
        logger.info("已暂停所有平台的信息刮削和媒体下载");
    }

    @Override
    public void resumeAll() {
        isScrapingPaused.set(false);
        isScrapingStopped.set(false);
        // 恢复工作线程池
        if (scrapeWorkerPool != null) {
            scrapeWorkerPool.resume();
        }
        // 恢复媒体下载
        mediaDownloadService.resumeMediaDownloadTask();
        // 恢复所有平台的暂停标志
        for (AtomicBoolean flag : platformPausedFlags.values()) {
            flag.set(false);
        }
        logger.info("已恢复所有平台的信息刮削和媒体下载");
    }

    @Override
    public void stopAll() {
        isScrapingStopped.set(true);
        isScrapingPaused.set(false);
        // 停止工作线程池
        if (scrapeWorkerPool != null) {
            scrapeWorkerPool.stopAll();
        }
        // 停止媒体下载
        mediaDownloadService.stopAllPlatformDownload();
        // 设置所有平台的停止标志
        for (Long pid : platformPausedFlags.keySet()) {
            getPlatformStoppedFlag(pid).set(true);
            getPlatformPausedFlag(pid).set(false);
        }
        logger.info("已停止所有平台的信息刮削和媒体下载");
    }

    @Override
    public void pausePlatform(Long platformId) {
        getPlatformPausedFlag(platformId).set(true);
        // 暂停该平台的信息刮削（通过全局暂停 + 平台标志实现选择性暂停）
        // 暂停该平台的媒体下载
        mediaDownloadService.stopPlatformDownload(platformId);
        logger.info("已暂停平台 {} 的刮削和媒体下载", platformId);
    }

    @Override
    public void resumePlatform(Long platformId) {
        getPlatformPausedFlag(platformId).set(false);
        getPlatformStoppedFlag(platformId).set(false);
        // 恢复该平台的媒体下载
        mediaDownloadService.resumePlatformDownload(platformId);
        logger.info("已恢复平台 {} 的刮削和媒体下载", platformId);
    }

    @Override
    public void stopPlatform(Long platformId) {
        getPlatformStoppedFlag(platformId).set(true);
        getPlatformPausedFlag(platformId).set(false);
        // 停止该平台的所有 PENDING scrape_task
        scrapeTaskMapper.stopPendingTasksByPlatformId(platformId);
        // 停止该平台的媒体下载
        mediaDownloadService.stopPlatformDownload(platformId);
        logger.info("已停止平台 {} 的刮削和媒体下载", platformId);
    }

    private AtomicBoolean getPlatformPausedFlag(Long platformId) {
        return platformPausedFlags.computeIfAbsent(platformId, k -> new AtomicBoolean(false));
    }

    private AtomicBoolean getPlatformStoppedFlag(Long platformId) {
        return platformStoppedFlags.computeIfAbsent(platformId, k -> new AtomicBoolean(false));
    }

    /**
     * 检查指定平台是否被暂停（供工作线程查询）
     */
    public boolean isPlatformPaused(Long platformId) {
        AtomicBoolean flag = platformPausedFlags.get(platformId);
        return flag != null && flag.get();
    }

    /**
     * 检查指定平台是否被停止（供工作线程查询）
     */
    public boolean isPlatformStopped(Long platformId) {
        AtomicBoolean flag = platformStoppedFlags.get(platformId);
        return flag != null && flag.get();
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
            
            // ========== v1.3 ROM 数据库匹配 + CRC 管线 ==========
            // 先查 rom_db，用权威 CRC 替代文件 CRC 提高 SS 匹配精度
            // 匹配顺序：CRC（所有平台）→ serial（白名单系统，委托 RomDbMatchingConfig）
            String discSerialGameName = null;
            String extractedSerial = null;
            String filePath = fileInfo.getFilePath();
            com.gamelist.model.RomDb romDbMatch = null; // rom_db 匹配结果（用于 CRC 管线）
            if (filePath != null && !filePath.isEmpty() && systemId != null) {
                if (romDbService != null && romDbService.isReady()) {
                    // L1: 先 CRC（所有平台）
                    if (fileInfo.getCrc32() != null) {
                        romDbMatch = romDbService.matchByCrc32(fileInfo.getCrc32(), systemId);
                    }

                    // L2: CRC 未命中 → 尝试 serial 提取（白名单系统走完整光盘路径）
                    if (romDbMatch == null && isSerialMatchSystem(systemId)) {
                        // L2a: 光盘系统 → matchByDiscFile（SYSTEM.CNF/PARAM.SFO/IP.BIN/NGC头部）
                        if (com.gamelist.util.DiscSerialExtractor.isSupported(systemId)) {
                            romDbMatch = romDbService.matchByDiscFile(filePath, systemId);
                        }
                    }

                    // L3: 卡带头部 ID 提取（毫秒级，对所有支持的系统都尝试，不受白名单限制）
                    if (romDbMatch == null && com.gamelist.util.CartridgeHeaderReader.isSupported(systemId)) {
                        try {
                            String headerSerial = com.gamelist.util.CartridgeHeaderReader.extractSerial(filePath, systemId);
                            if (headerSerial != null) {
                                romDbMatch = romDbService.matchBySerial(headerSerial, systemId);
                                if (romDbMatch != null) {
                                    logger.info("rom_db 卡带头部匹配: systemId={}, serial={}, file={}",
                                        systemId, headerSerial, new File(filePath).getName());
                                } else {
                                    // SNES 等平台的头部提取得到的是内部标题（非 serial），回退到名称模糊匹配
                                    java.util.List<com.gamelist.model.RomDb> nameCandidates = romDbService.searchBySystemAndName(systemId, headerSerial, 1);
                                    if (!nameCandidates.isEmpty()) {
                                        romDbMatch = nameCandidates.get(0);
                                        logger.info("rom_db 卡带头部名称匹配: systemId={}, title={}, file={}, matchedName={}",
                                            systemId, headerSerial, new File(filePath).getName(), romDbMatch.getGameName());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.debug("rom_db 卡带头部提取失败: systemId={}, error={}", systemId, e.getMessage());
                        }
                    }

                    if (romDbMatch != null) {
                        extractedSerial = romDbMatch.getSerial();
                        discSerialGameName = romDbMatch.getGameName();
                        logger.info("rom_db 匹配: platform={}, serial={}, crc={}, gameName={}, file={}",
                            romDbMatch.getPlatform(), romDbMatch.getSerial(), romDbMatch.getCrc32(),
                            discSerialGameName, new File(filePath).getName());
                    }
                }
            }
            
            // CRC 管线：优先用 rom_db 权威 CRC，其次文件 CRC
            String crcForSS = fileInfo.getCrc32(); // 文件 CRC
            if (romDbMatch != null && romDbMatch.getCrc32() != null && !romDbMatch.getCrc32().isEmpty()) {
                crcForSS = romDbMatch.getCrc32(); // 权威 CRC（来自 No-Intro/Redump）
                if (!crcForSS.equals(fileInfo.getCrc32())) {
                    logger.info("CRC 管线: 使用 rom_db 权威 CRC {} 替代文件 CRC {} (file={})",
                            crcForSS, fileInfo.getCrc32(), filePath != null ? new File(filePath).getName() : "?");
                }
            }
            if (crcForSS != null && !crcForSS.isEmpty()) {
                urlBuilder.addQueryParameter("crc", crcForSS);
            }
            
            // 始终添加romnom作为备用匹配条件；SS 端对 No-Intro/Redump 标签不敏感，先剥一层提高首次命中率
            String romName;
            if (discSerialGameName != null) {
                // 光盘编号缓存命中 → 用标准游戏名作为 romnom（比原始文件名准确得多）
                romName = discSerialGameName;
            } else {
                romName = fileInfo.getGameName();
                if (romName == null || romName.isEmpty()) {
                    // 如果gameName为空，从文件路径中提取
                    if (filePath != null && !filePath.isEmpty()) {
                        File file = new File(filePath);
                        romName = file.getName();
                        int lastDot = romName.lastIndexOf('.');
                        if (lastDot > 0) {
                            romName = romName.substring(0, lastDot);
                        }
                    }
                }
            }
            // romName 保留原样作为 fuzzy 兜底查询基准；发给 SS 的 romnom 走清洗后的版本
            if (romName != null && !romName.isEmpty()) {
                String romnomForSS = SsNameNormalizer.stripDumpTags(romName);
                if (romnomForSS == null || romnomForSS.isEmpty()) {
                    romnomForSS = romName; // 极端情况：整串都是标签，回退原值
                }
                urlBuilder.addQueryParameter("romnom", romnomForSS);
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
            
            String response;
            try {
                response = executeRequest(url);
            } catch (ScreenScraperApiException e) {
                // 将 API 状态码传递给调用方（限额检测关键）
                logger.error("搜索游戏遇到 API 状态码: {} - {}", e.getStatusCode(), e.getMessage());
                result.put("apiStatusCode", e.getStatusCode());
                result.put("isLimitError", e.isLimitError());
                return result;
            }
            if (response == null || response.isEmpty()) {
                logger.warn("搜索游戏返回空响应");
                return result;
            }
            
            JsonNode root = objectMapper.readTree(response);
                logger.info("API响应结构: has response={}, response has jeu={}", 
                            root.has("response"), root.has("response") && root.get("response").has("jeu"));
                
                if (root.has("response")) {
                    JsonNode responseNode = root.get("response");
                    
                    // 从每次 API 响应中提取 ssuser 信息，动态更新线程配额和配额信息
                    if (responseNode.has("ssuser")) {
                        JsonNode ssuserNode = responseNode.get("ssuser");
                        
                        // 更新线程配额（并发控制）
                        if (ssuserNode.has("maxthreads")) {
                            int serverMaxThreads = ssuserNode.get("maxthreads").asInt();
                            cachedMaxThreads = serverMaxThreads;
                            threadResourceManager.updateFromServerResponse(serverMaxThreads);
                        }
                        
                        // 更新完整配额信息（ScrapeStatus 是前端 thread-status API 的唯一数据源）
                        scrapeStatus.updateQuotaFromSsuser(ssuserNode);
                    }
                    
                    if (responseNode.has("jeu")) {
                    JsonNode jeu = responseNode.get("jeu");
                    result.put("found", true);
                    result.put("game", jeu);
                    
                    // v1.3: 传递提取到的光盘编号给调用方（用于写回 game 记录）
                    if (extractedSerial != null) {
                        result.put("discSerial", extractedSerial);
                    }
                    
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
                    logger.info("jeuInfos 严格匹配未命中: query='{}'，尝试 fuzzy 名字兜底", romName);
                    // v1.3: fuzzy 名字兜底 —— jeuRecherche 拉候选 + 归一化精确相等才要（不做 Top1 猜）。
                    //         命中后元数据完整落库但 matched 由 updateGameRecord 根据 fuzzyHit 保持 false（疑似）。
                    if (systemSettingsService.getBoolean("ss_fuzzy_name_fallback", true)
                            && romName != null && !romName.isEmpty()) {
                        JsonNode fuzzyJeu = tryFuzzyFallbackBySearch(romName, systemId);
                        if (fuzzyJeu != null) {
                            result.put("found", true);
                            result.put("game", fuzzyJeu);
                            result.put("fuzzyHit", true);
                            result.put("fuzzyQuery", romName);
                            if (extractedSerial != null) {
                                result.put("discSerial", extractedSerial);
                            }
                            if (fuzzyJeu.has("medias")) {
                                result.put("medias", fuzzyJeu.get("medias"));
                            }
                            logger.info("SS fuzzy 兜底命中: query='{}' → ssNom='{}' (gameid={})",
                                romName,
                                fuzzyJeu.path("nom").asText(""),
                                fuzzyJeu.path("id").asText(""));
                        } else {
                            logger.debug("SS fuzzy 兜底未命中: query='{}' → 归一化无相等候选，保守放弃", romName);
                        }
                    }
                }
                } // end if (root.has("response"))
            
        } catch (Exception e) {
            logger.error("搜索游戏失败: {}", e.getMessage());
        }
        
        return result;
    }

    // ==================== v1.3 fuzzy 名字兜底 ====================

    /**
     * fuzzy 兜底入口：先调 jeuRecherche.php 拉同系统候选，对候选 nom 做归一化与查询名
     * 精确相等比较；命中则拿 gameid 再调一次 jeuInfos.php 拿完整元数据。任何环节
     * 失败都返回 null，绝不猜 Top1。
     *
     * @param queryName 本地查询名（可为 rom_db 标准名或清洗前的文件名）
     * @param systemId  SS 系统 ID（限定同系统内搜索，避免跨平台串）
     * @return 完整 jeu 节点，或 null
     */
    private JsonNode tryFuzzyFallbackBySearch(String queryName, Integer systemId) {
        try {
            String searchTerm = SsNameNormalizer.stripDumpTags(queryName);
            if (searchTerm == null || searchTerm.isEmpty()) searchTerm = queryName;
            List<Map<String, Object>> candidates = callJeuRecherche(systemId, searchTerm);
            if (candidates == null || candidates.isEmpty()) {
                logger.debug("SS fuzzy: jeuRecherche 无候选 query='{}'", searchTerm);
                return null;
            }
            String normQuery = SsNameNormalizer.normalize(searchTerm);
            Map<String, Object> chosen = null;
            for (Map<String, Object> c : candidates) {
                String nom = (String) c.get("nom");
                if (nom == null || nom.isEmpty()) continue;
                if (SsNameNormalizer.normalize(nom).equals(normQuery)) {
                    chosen = c;
                    break;
                }
            }
            if (chosen == null) {
                logger.debug("SS fuzzy: 候选 {} 条但无归一化相等项 query='{}'", candidates.size(), searchTerm);
                return null;
            }
            String chosenId = (String) chosen.get("id");
            if (chosenId == null || chosenId.isEmpty()) return null;
            logger.info("SS fuzzy 归一化命中: query='{}' 归一化='{}' 候选 nom='{}' (gameid={})",
                searchTerm, normQuery, chosen.get("nom"), chosenId);
            return callJeuInfosById(chosenId, systemId);
        } catch (Exception e) {
            logger.warn("SS fuzzy 兜底异常(忽略): {}", e.getMessage());
            return null;
        }
    }

    /**
     * 调用 ScreenScraper jeuRecherche.php（同系统模糊搜索），返回候选列表。
     * 每项 map 包含：id、nom（优先 ss/eu 区域名）。失败或空结果返回空列表。
     */
    private List<Map<String, Object>> callJeuRecherche(Integer systemId, String searchTerm) {
        String baseUrl = scraperConfig.getBaseUrl();
        okhttp3.HttpUrl.Builder ub = okhttp3.HttpUrl.parse(baseUrl + "/api2/jeuRecherche.php").newBuilder();
        ub.addQueryParameter("devid", scraperConfig.getDevPseudo());
        ub.addQueryParameter("devpassword", scraperConfig.getDevPassword());
        ub.addQueryParameter("softname", "FrontendKiller");
        ub.addQueryParameter("output", "json");
        Map<String, String> settings = scraperSettingsService.getSettings();
        if (settings.containsKey("username") && !settings.get("username").isEmpty()) {
            ub.addQueryParameter("ssid", settings.get("username"));
        }
        if (settings.containsKey("password") && !settings.get("password").isEmpty()) {
            ub.addQueryParameter("sspassword", settings.get("password"));
        }
        ub.addQueryParameter("systemeid", systemId.toString());
        ub.addQueryParameter("recherche", searchTerm);
        String url = ub.build().toString();
        logger.debug("SS fuzzy jeuRecherche URL: {}", url);
        try {
            String response = executeRequest(url);
            if (response == null || response.isEmpty()) return java.util.Collections.emptyList();
            JsonNode root = objectMapper.readTree(response);
            JsonNode jeuxNode = root.path("response").path("jeux");
            if (!jeuxNode.isArray()) return java.util.Collections.emptyList();
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode jn : jeuxNode) {
                Map<String, Object> m = new HashMap<>();
                if (jn.has("id")) m.put("id", jn.get("id").asText());
                String nom = null;
                JsonNode nomsNode = jn.path("noms");
                if (nomsNode.isArray()) {
                    for (JsonNode nn : nomsNode) {
                        String region = nn.path("region").asText("");
                        String text = nn.path("text").asText("");
                        if (text.isEmpty()) continue;
                        if ("ss".equals(region) || "eu".equals(region)) { nom = text; break; }
                        if (nom == null) nom = text;
                    }
                }
                if (nom == null && jn.has("nom")) nom = jn.get("nom").asText("");
                m.put("nom", nom);
                out.add(m);
            }
            return out;
        } catch (ScreenScraperApiException e) {
            logger.warn("SS fuzzy jeuRecherche 失败: HTTP {}", e.getStatusCode());
            return java.util.Collections.emptyList();
        } catch (Exception e) {
            logger.warn("SS fuzzy jeuRecherche 异常: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 以 gameid 主键拉完整 jeu 节点（同系统，无 crc/romnom 参与），供 fuzzy 兜底回提元数据使用。
     */
    private JsonNode callJeuInfosById(String gameId, Integer systemId) {
        String baseUrl = scraperConfig.getBaseUrl();
        okhttp3.HttpUrl.Builder ub = okhttp3.HttpUrl.parse(baseUrl + "/api2/jeuInfos.php").newBuilder();
        ub.addQueryParameter("devid", scraperConfig.getDevPseudo());
        ub.addQueryParameter("devpassword", scraperConfig.getDevPassword());
        ub.addQueryParameter("softname", "FrontendKiller");
        ub.addQueryParameter("output", "json");
        Map<String, String> settings = scraperSettingsService.getSettings();
        if (settings.containsKey("username") && !settings.get("username").isEmpty()) {
            ub.addQueryParameter("ssid", settings.get("username"));
        }
        if (settings.containsKey("password") && !settings.get("password").isEmpty()) {
            ub.addQueryParameter("sspassword", settings.get("password"));
        }
        ub.addQueryParameter("systemeid", systemId.toString());
        ub.addQueryParameter("gameid", gameId);
        String url = ub.build().toString();
        logger.debug("SS fuzzy jeuInfos-by-gameid URL: {}", url);
        try {
            String response = executeRequest(url);
            if (response == null || response.isEmpty()) return null;
            JsonNode root = objectMapper.readTree(response);
            JsonNode jeu = root.path("response").path("jeu");
            return jeu.isMissingNode() || jeu.isNull() ? null : jeu;
        } catch (Exception e) {
            logger.warn("SS fuzzy jeuInfos-by-gameid 异常: {}", e.getMessage());
            return null;
        }
    }
    
    private String executeRequest(String url) throws ScreenScraperApiException {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    int statusCode = response.code();
                    String statusDesc = ScreenScraperStatusHandler.getStatusInfo(statusCode).getDescription();
                    logger.error("HTTP请求失败，状态码: {} - {}", statusCode, statusDesc);

                    if (ScreenScraperStatusHandler.shouldStopImmediately(statusCode)) {
                        logger.error("遇到需要停止刮削的状态码: {} - {}", statusCode, statusDesc);
                        if (ScreenScraperStatusHandler.isSoftwareLimitError(statusCode)) {
                            logger.error("⚠️ 软件级限额触发: {}", ScreenScraperStatusHandler.getLimitWarningMessage(statusCode));
                        } else if (ScreenScraperStatusHandler.isUserLimitError(statusCode)) {
                            logger.error("⚠️ 用户级限额触发: {}", ScreenScraperStatusHandler.getLimitWarningMessage(statusCode));
                        }
                    }

                    throw new ScreenScraperApiException(statusCode, statusDesc);
                }

                return response.body() != null ? response.body().string() : null;
            }
        } catch (ScreenScraperApiException e) {
            throw e;
        } catch (IOException e) {
            logger.error("执行HTTP请求失败: {}", e.getMessage());
            throw new ScreenScraperApiException(-1, "网络请求失败: " + e.getMessage());
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

    /**
     * 将 SS 候选中的指定游戏元数据落库，并强制 matched=true。
     * 任何环节失败都返回 success=false，不会修改本地数据。
     */
    @Override
    public Map<String, Object> applySsCandidate(Long gameId, String ssGameId) {
        Map<String, Object> out = new HashMap<>();
        try {
            if (gameId == null || ssGameId == null || ssGameId.isEmpty()) {
                out.put("success", false);
                out.put("message", "gameId / ssGameId 不能为空");
                return out;
            }
            Game game = gameService.getGameById(gameId);
            if (game == null) {
                out.put("success", false);
                out.put("message", "游戏不存在: " + gameId);
                return out;
            }
            Long platformId = game.getPlatformId();
            if (platformId == null) {
                out.put("success", false);
                out.put("message", "游戏未归属任何平台，无法定位 SS 系统 ID");
                return out;
            }
            Platform platform = platformService.getPlatformById(platformId);
            if (platform == null || platform.getSystemId() == null || platform.getSystemId() == 0) {
                out.put("success", false);
                out.put("message", "平台未绑定 SS 系统，请先在平台管理页配置");
                return out;
            }
            Integer systemId = platform.getSystemId();
            JsonNode jeu = callJeuInfosById(ssGameId, systemId);
            if (jeu == null) {
                out.put("success", false);
                out.put("message", "jeuInfos.php 未返回该游戏元数据 (gameid=" + ssGameId + ")");
                return out;
            }

            // 构造一个最小 ScraperRequest（单条手动刮削），保留区域/语种使用默认
            ScraperRequest req = new ScraperRequest();
            req.setType("single");
            req.setPlatformId(platformId);
            req.setGameIds(java.util.Collections.singletonList(gameId));
            String lang = "en";
            try {
                Map<String, String> settings = scraperSettingsService.getSettings();
                if (settings.containsKey("language") && !settings.get("language").isEmpty()) {
                    lang = settings.get("language");
                }
            } catch (Exception ignore) {}
            req.setLanguage(lang);
            req.setRegion("wor");
            req.setPreserveLocalData(false);

            Map<String, Object> searchResult = new HashMap<>();
            searchResult.put("found", true);
            searchResult.put("game", jeu);
            // 不置 fuzzyHit，下面会手动写 matched=true
            updateGameRecord(game, searchResult, req);
            // 强制确认匹配
            game.setMatched(true);
            gameService.updateGame(game);

            out.put("success", true);
            out.put("message", "已应用 SS 候选 gameid=" + ssGameId);
            out.put("ssGameId", game.getSsGameId());
            out.put("name", game.getName());
            return out;
        } catch (Exception e) {
            logger.error("applySsCandidate 异常 gameId={}, ssGameId={}", gameId, ssGameId, e);
            out.put("success", false);
            out.put("message", e.getMessage() == null ? "未知错误" : e.getMessage());
            return out;
        }
    }

    @Override
    public void downloadGameMedia(Long gameId, String gameName, Long platformId, Map<String, Object> medias) {
        try {
            Platform platform = platformService.getPlatformById(platformId);
            if (platform == null) {
                logger.error("平台不存在: {}", platformId);
                return;
            }

            Game game = gameService.getGameById(gameId);
            if (game == null) {
                logger.error("游戏不存在: {}", gameId);
                return;
            }

            // 手动搜索下载场景：调用方传入的 ss 游戏 ID（gameMap 顶层 "id"）写回数据库
            Object ssIdObj = medias.get("id");
            if (ssIdObj != null) {
                try {
                    game.setSsGameId(Long.parseLong(ssIdObj.toString()));
                    gameService.updateGame(game);
                    logger.info("手动搜索下载: 写回 ss_game_id={} for game {}", ssIdObj, gameName);
                } catch (NumberFormatException ex) {
                    logger.warn("SS游戏ID非数字: {}", ssIdObj);
                }
            }

            // 新路径规则目录
            Path gameMediaDir = PathResolver.resolveGameMediaDir(game, platform.getSystemId());
            Files.createDirectories(gameMediaDir);
            
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
                        // 与 createMediaTask 一致：加密存储，下载 worker 会解密
                        task.setDownloadUrl(EncryptionUtil.encrypt(url));
                        task.setStatus("PENDING");
                        task.setOrderIndex((long) orderIndex++);
                        
                        // 设置localPath（新路径规则：ss 稳定键目录 + 媒体类型命名）
                        String extension = "." + (selectedMedia.containsKey("format") ? selectedMedia.get("format").toLowerCase() : "png");
                        String localPath = PathResolver.normalizeForDb(gameMediaDir.resolve(mediaType + extension));
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
     * 从 manifest 缓存回填缺失元数据（genre/genreid/releasedate），零 SS 请求
     * 只补空字段不覆盖已有值；只处理 cached=true 且已绑 ssGameId 的游戏。
     */
    @Override
    public Map<String, Object> backfillMetadataFromManifest() {
        int updated = 0, alreadyOk = 0, noManifest = 0, failed = 0;
        try {
            List<Game> games = gameService.getAllGames();
            for (Game game : games) {
                if (!Boolean.TRUE.equals(game.getCached())) continue;
                Long ssGameId = game.getSsGameId();
                if (ssGameId == null) continue;
                try {
                    JsonNode jeu = gameManifestService.getCachedGame(ssGameId);
                    if (jeu == null || jeu.isNull()) { noManifest++; continue; }
                    boolean changed = false;

                    // genre / genreid：只补空
                    if ((game.getGenre() == null || game.getGenre().isEmpty())
                            && jeu.has("genres") && jeu.get("genres").isArray()) {
                        StringBuilder genreBuilder = new StringBuilder();
                        StringBuilder genreIdBuilder = new StringBuilder();
                        for (JsonNode genreNode : jeu.get("genres")) {
                            if (!genreNode.isObject()) continue;
                            String genreText = extractTextFromRegionArray(genreNode.get("noms"), "en");
                            if (genreText == null || genreText.isEmpty()) continue;
                            String genreId = genreNode.has("id") ? genreNode.get("id").asText() : "";
                            if (genreBuilder.length() > 0) genreBuilder.append(", ");
                            if (genreIdBuilder.length() > 0) genreIdBuilder.append(", ");
                            genreBuilder.append(genreText);
                            genreIdBuilder.append(isInvalidText(genreId) ? "" : genreId);
                        }
                        if (genreBuilder.length() > 0) {
                            game.setGenre(genreBuilder.toString());
                            game.setGenreid(genreIdBuilder.toString());
                            changed = true;
                        }
                    }

                    // releasedate：只补空
                    if ((game.getReleasedate() == null || game.getReleasedate().isEmpty())
                            && jeu.has("dates") && jeu.get("dates").isArray()) {
                        String releaseDate = extractDateFromRegionArray(jeu.get("dates"), "wor");
                        if (releaseDate != null && !releaseDate.isEmpty()) {
                            game.setReleasedate(releaseDate);
                            changed = true;
                        }
                    }

                    if (changed) {
                        gameService.updateGame(game);
                        updated++;
                    } else {
                        alreadyOk++;
                    }
                } catch (Exception e) {
                    logger.warn("回填元数据失败: game={}, ssGameId={}, err={}", game.getName(), ssGameId, e.getMessage());
                    failed++;
                }
            }
            logger.info("元数据回填完成: updated={}, alreadyOk={}, noManifest={}, failed={}", updated, alreadyOk, noManifest, failed);
            return Map.of("updated", updated, "alreadyOk", alreadyOk, "noManifest", noManifest, "failed", failed);
        } catch (Exception e) {
            logger.error("元数据回填异常: {}", e.getMessage(), e);
            return Map.of("updated", updated, "alreadyOk", alreadyOk, "noManifest", noManifest, "failed", failed, "error", String.valueOf(e.getMessage()));
        }
    }

    @Override
    public int enqueueGameMedia(Long gameId, String gameName, Long platformId, Map<String, Object> medias) {
        try {
            Platform platform = platformService.getPlatformById(platformId);
            if (platform == null) {
                logger.error("平台不存在: {}", platformId);
                return 0;
            }

            Game game = gameService.getGameById(gameId);

            // 调用方传入的 ss 游戏 ID 写回数据库
            Object ssIdObj = medias.get("id");
            if (game != null && ssIdObj != null) {
                try {
                    game.setSsGameId(Long.parseLong(ssIdObj.toString()));
                    gameService.updateGame(game);
                } catch (NumberFormatException ex) {
                    logger.warn("SS游戏ID非数字: {}", ssIdObj);
                }
            }

            // 新路径规则目录
            Path gameMediaDir = (game != null)
                ? PathResolver.resolveGameMediaDir(game, platform.getSystemId())
                : Paths.get(PathResolver.MEDIA_BASE, "unknown", "local", String.valueOf(gameId));
            Files.createDirectories(gameMediaDir);

            // 创建后台任务（用于跟踪进度）
            BackgroundTask bgTask = taskService.createTask("MEDIA_DOWNLOAD", "下载游戏媒体: " + gameName);
            Long bgTaskId = bgTask.getId();

            int orderIndex = 0;
            for (Map.Entry<String, Object> entry : medias.entrySet()) {
                String mediaType = entry.getKey();
                @SuppressWarnings("unchecked")
                List<Map<String, String>> mediaList = (List<Map<String, String>>) entry.getValue();

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
                        MediaDownloadTask task = new MediaDownloadTask();
                        task.setTaskId(bgTaskId);
                        task.setGameId(gameId);
                        task.setGameFieldName(gameFieldName);
                        task.setPlatformId(platformId);
                        task.setPlatformName(platform.getName());
                        task.setGameName(gameName);
                        task.setMediaType(mediaType);
                        task.setDownloadUrl(EncryptionUtil.encrypt(url));
                        task.setStatus("PENDING");
                        task.setOrderIndex((long) orderIndex++);

                        // 计算本地存储路径（新路径规则目录）
                        String extension = "." + (selectedMedia.containsKey("format") ? selectedMedia.get("format").toLowerCase() : "png");
                        String localPath = PathResolver.normalizeForDb(gameMediaDir.resolve(mediaType + extension));
                        task.setLocalPath(localPath);

                        mediaDownloadTaskMapper.insert(task);
                    }
                }
            }

            // 更新后台任务进度（仅记录，不启动下载）
            taskService.updateTaskProgress(bgTaskId, 0, "媒体下载任务已加入队列", 0, orderIndex);
            logger.info("媒体下载任务已加入队列: gameId={}, gameName={}, 任务数={}", gameId, gameName, orderIndex);
            return orderIndex;

        } catch (Exception e) {
            logger.error("加入媒体下载队列失败: {}", e.getMessage());
            return 0;
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
                // 前端“应用于所有搜索结果”发送的是嵌套 filters，先解包到扁平字段
                request.flattenFilters();
                if (request.getGameIds() != null && !request.getGameIds().isEmpty()) {
                    games = request.getGameIds().stream()
                        .map(gameService::getGameById)
                        .filter(g -> g != null)
                        .collect(Collectors.toList());
                } else if (request.hasFilters()) {
                    // “应用于所有搜索结果”模式：按筛选条件查询全量游戏
                    games = gameService.getGamesByPlatformId(
                        request.getPlatformId(),
                        request.getSearch(),
                        null, null, // startDate, endDate
                        request.getDevelopers(),
                        request.getGenres(),
                        request.getPlayers(),
                        request.getScrapeStatuses(),
                        request.getFileStatuses(),
                        request.getFolderPath(),
                        request.getPublishers(),
                        request.getMatchedStatuses()
                    );
                    logger.info("筛选模式刮削: 匹配到 {} 个游戏", games.size());
                } else {
                    // “应用于所有搜索结果”但无任何筛选条件 = 整个平台
                    games = gameService.getGamesByPlatformId(request.getPlatformId());
                    logger.info("全平台刮削(应用于所有，无筛选): 匹配到 {} 个游戏", games.size());
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
     * 判定游戏所属平台是否为街机类（绑定系统 && type 含 "arcade"）。
     * 复用现成谓词，与 calculateCRC32 里的 isArcade 一致。异常/未绑定一律视为非街机。
     */
    private boolean isArcadePlatform(Game game) {
        try {
            if (game == null || game.getPlatformId() == null) {
                return false;
            }
            Platform p = platformService.getPlatformById(game.getPlatformId());
            if (p == null || p.getSystemId() == null || p.getSystemId() == 0) {
                return false;
            }
            ScraperSystem sys = getSystemInfo(p.getSystemId());
            return sys != null && sys.getType() != null && sys.getType().toLowerCase().contains("arcade");
        } catch (Exception e) {
            logger.debug("判定街机平台失败(视为非街机): {}", e.getMessage());
            return false;
        }
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
        String systemType = system.getType();
        File file = new File(absolutePath);
        
        // 判断文件类型
        FileType fileType = determineFileType(romType, file);
        fileInfo.setFileType(fileType);
        
        // 提取游戏名称
        String gameName = extractGameName(file, fileType);
        fileInfo.setGameName(gameName);
        
        // 计算CRC32（传入 systemType 区分街机/主机）
        String crc32 = calculateCRC32(file, fileType, romType, systemType);
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
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".zip")) {
            try (ZipFile zipFile = new ZipFile(file)) {
                int count = 0;
                var entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    // 只计算非目录、非隐藏文件的 ROM 文件
                    if (!entry.isDirectory() && !entry.getName().startsWith("__")) {
                        count++;
                    }
                }
                return count;
            } catch (IOException e) {
                // 标准 ZipFile 失败（如中文编码 ZIP），尝试 Commons Compress
                logger.info("标准ZIP读取失败({})，尝试使用 Commons Compress 回退: {}", e.getMessage(), file.getName());
                return countRomFilesInArchiveFallback(file);
            }
        } else if (fileName.endsWith(".7z")) {
            try (SevenZFile sevenZFile = new SevenZFile(file)) {
                int count = 0;
                SevenZArchiveEntry entry;
                while ((entry = sevenZFile.getNextEntry()) != null) {
                    if (!entry.isDirectory() && !entry.getName().startsWith("__")) {
                        count++;
                    }
                }
                return count;
            } catch (IOException e) {
                logger.error("读取7z文件失败: {}", e.getMessage());
                return 1;
            }
        }
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
    
    private String calculateCRC32(File file, FileType fileType, String romType, String systemType) {
        try {
            boolean isArcade = systemType != null && systemType.toLowerCase().contains("arcade");
            
            if (fileType == FileType.SINGLE_FILE) {
                return calculateSingleFileCRC(file);
            } else if (fileType == FileType.SINGLE_ARCHIVE || fileType == FileType.MULTI_ARCHIVE) {
                if (isArcade) {
                    // 街机平台：ScreenScraper 要压缩包本身的 CRC（无论内含多少 ROM）
                    logger.info("街机平台(systemType={})，使用压缩包自身CRC: {}", systemType, file.getName());
                    return calculateSingleFileCRC(file);
                } else {
                    // 非街机平台（主机/掌机）：ScreenScraper 要内部 ROM 的 CRC
                    return calculateArchiveCRC(file, fileType);
                }
            } else {
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
    
    /**
     * 计算单 ROM 压缩归档文件内 ROM 的 CRC32（仅用于 SINGLE_ARCHIVE）
     * 
     * ScreenScraper 期望的是 ROM 文件本身的 CRC，而非压缩包的 CRC。
     * - ZIP 文件：直接从 ZIP 文件头读取 entry 的 CRC32（无需解压，速度极快）
     * - 7z 文件：需要解压数据并计算 CRC
     * 
     * 优先选择与归档同名的 ROM 文件，否则选最大的 ROM 文件。
     * 
     * 注意：MULTI_ARCHIVE（街机多ROM包）不走此方法，而是使用压缩包自身的 CRC。
     */
    private String calculateArchiveCRC(File archiveFile, FileType fileType) {
        String fileName = archiveFile.getName().toLowerCase();
        
        if (fileName.endsWith(".zip")) {
            return calculateZipCRC(archiveFile);
        } else if (fileName.endsWith(".7z")) {
            return calculate7zCRC(archiveFile);
        }
        return null;
    }
    
    /**
     * 从 ZIP 文件中提取 ROM 的 CRC32
     * 直接读取 ZIP entry header 中的 CRC 值，无需解压数据
     */
    private String calculateZipCRC(File zipFile) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            String archiveBaseName = zipFile.getName();
            // 移除扩展名
            int lastDot = archiveBaseName.lastIndexOf('.');
            if (lastDot > 0) {
                archiveBaseName = archiveBaseName.substring(0, lastDot);
            }
            
            ZipEntry bestMatch = null;
            long largestSize = 0;
            
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                
                // 跳过目录和隐藏文件
                if (entry.isDirectory() || entry.getName().startsWith("__") 
                    || entry.getName().startsWith(".")) {
                    continue;
                }
                
                String entryName = entry.getName();
                // 移除路径前缀（有些 ZIP 内有子目录）
                int lastSlash = entryName.lastIndexOf('/');
                if (lastSlash >= 0) {
                    entryName = entryName.substring(lastSlash + 1);
                }
                // 移除扩展名
                String entryBaseName = entryName;
                int entryDot = entryBaseName.lastIndexOf('.');
                if (entryDot > 0) {
                    entryBaseName = entryBaseName.substring(0, entryDot);
                }
                
                // 优先匹配与归档同名的 ROM
                if (entryBaseName.equalsIgnoreCase(archiveBaseName)) {
                    bestMatch = entry;
                    logger.info("ZIP 中找到同名 ROM: {} (CRC={})", entry.getName(), 
                        String.format("%08X", entry.getCrc()));
                    break;
                }
                
                // 记录最大的 ROM 文件作为备选
                if (entry.getSize() > largestSize) {
                    largestSize = entry.getSize();
                    bestMatch = entry;
                }
            }
            
            if (bestMatch != null) {
                long crc = bestMatch.getCrc();
                if (crc >= 0) {
                    String crcHex = String.format("%08X", crc);
                    logger.info("ZIP CRC32: 文件={}, entry={}, CRC={}", 
                        zipFile.getName(), bestMatch.getName(), crcHex);
                    return crcHex;
                }
            }
            
            logger.warn("ZIP 中未找到有效的 ROM entry: {}", zipFile.getName());
            return null;
            
        } catch (IOException e) {
            // 标准 ZipFile 失败（如中文编码 ZIP），尝试 Commons Compress
            logger.info("标准ZIP读取失败({})，尝试使用 Commons Compress 回退: {}", e.getMessage(), zipFile.getName());
            return calculateZipCRCFallback(zipFile);
        }
    }
    
    /**
     * 使用 Apache Commons Compress 读取 ZIP（回退方案）
     * 适用于非标准编码（如 GBK）的 ZIP 文件，Commons Compress 对 CEN header 更宽容。
     */
    private int countRomFilesInArchiveFallback(File file) {
        // 尝试多种编码打开 ZIP（cp437=ZIP 原始规范, GBK=Windows 中文, UTF-8=标准）
        java.nio.charset.Charset[] charsets = {
            java.nio.charset.Charset.forName("cp437"),
            java.nio.charset.Charset.forName("GBK"),
            java.nio.charset.StandardCharsets.UTF_8
        };
        org.apache.commons.compress.archivers.zip.ZipFile zipFile = null;
        Exception lastError = null;
        for (java.nio.charset.Charset cs : charsets) {
            try {
                zipFile = new org.apache.commons.compress.archivers.zip.ZipFile.Builder()
                    .setFile(file).setCharset(cs).get();
                break;
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (zipFile == null) {
            logger.error("Commons Compress 也无法读取ZIP文件（所有编码均失败）: {}, lastError={}",
                file.getName(), lastError != null ? lastError.getMessage() : "unknown");
            return 1;
        }
        final org.apache.commons.compress.archivers.zip.ZipFile finalZip = zipFile;
        try (finalZip) {
            int count = 0;
            var entries = finalZip.getEntries();
            while (entries.hasMoreElements()) {
                org.apache.commons.compress.archivers.zip.ZipArchiveEntry entry = entries.nextElement();
                if (!entry.isDirectory() && !entry.getName().startsWith("__")) {
                    count++;
                }
            }
            logger.info("Commons Compress 回退计数: {} 内含 {} 个 ROM", file.getName(), count);
            return count;
        } catch (IOException e) {
            logger.error("Commons Compress 读取ZIP失败: {}", e.getMessage());
            return 1;
        }
    }
    
    /**
     * 使用 Apache Commons Compress 计算 ZIP 内 ROM 的 CRC（回退方案）
     */
    private String calculateZipCRCFallback(File zipFile) {
        // 尝试多种编码打开 ZIP（cp437=ZIP 原始规范, GBK=Windows 中文, UTF-8=标准）
        java.nio.charset.Charset[] charsets = {
            java.nio.charset.Charset.forName("cp437"),
            java.nio.charset.Charset.forName("GBK"),
            java.nio.charset.StandardCharsets.UTF_8
        };
        org.apache.commons.compress.archivers.zip.ZipFile zip = null;
        Exception lastError = null;
        for (java.nio.charset.Charset cs : charsets) {
            try {
                zip = new org.apache.commons.compress.archivers.zip.ZipFile.Builder()
                    .setFile(zipFile).setCharset(cs).get();
                break;
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (zip == null) {
            logger.error("Commons Compress 也无法读取ZIP文件（所有编码均失败）: {}, lastError={}",
                zipFile.getName(), lastError != null ? lastError.getMessage() : "unknown");
            return null;
        }
        final org.apache.commons.compress.archivers.zip.ZipFile finalZip = zip;
        try (finalZip) {
            String archiveBaseName = zipFile.getName();
            int lastDot = archiveBaseName.lastIndexOf('.');
            if (lastDot > 0) {
                archiveBaseName = archiveBaseName.substring(0, lastDot);
            }
            
            long bestCrc = -1;
            long largestSize = 0;
            String bestEntryName = null;
            
            var entries = finalZip.getEntries();
            while (entries.hasMoreElements()) {
                org.apache.commons.compress.archivers.zip.ZipArchiveEntry entry = entries.nextElement();
                
                if (entry.isDirectory() || entry.getName().startsWith("__") 
                    || entry.getName().startsWith(".")) {
                    continue;
                }
                
                String entryName = entry.getName();
                int lastSlash = entryName.lastIndexOf('/');
                if (lastSlash >= 0) {
                    entryName = entryName.substring(lastSlash + 1);
                }
                String entryBaseName = entryName;
                int entryDot = entryBaseName.lastIndexOf('.');
                if (entryDot > 0) {
                    entryBaseName = entryBaseName.substring(0, entryDot);
                }
                
                long entryCrc = entry.getCrc();
                
                // 优先匹配与归档同名的 ROM
                if (entryBaseName.equalsIgnoreCase(archiveBaseName)) {
                    String crcHex = String.format("%08X", entryCrc);
                    logger.info("ZIP(fallback) 中找到同名 ROM: {} (CRC={})", entry.getName(), crcHex);
                    return crcHex;
                }
                
                // 记录最大的 ROM 文件作为备选
                if (entry.getSize() > largestSize) {
                    largestSize = entry.getSize();
                    bestCrc = entryCrc;
                    bestEntryName = entry.getName();
                }
            }
            
            if (bestCrc >= 0) {
                String crcHex = String.format("%08X", bestCrc);
                logger.info("ZIP(fallback) CRC32: 文件={}, entry={}, CRC={}", 
                    zipFile.getName(), bestEntryName, crcHex);
                return crcHex;
            }
            
            logger.warn("ZIP(fallback) 中未找到有效的 ROM entry: {}", zipFile.getName());
            return null;
            
        } catch (IOException e) {
            logger.error("Commons Compress 也无法读取 ZIP 文件: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从 7z 文件中提取 ROM 的 CRC32
     * 7z 不像 ZIP 直接在文件头存储 CRC，需要解压数据并计算。
     * 选择策略与 ZIP 相同：优先同名 ROM，否则选最大 ROM。
     */
    private String calculate7zCRC(File sevenZFile) {
        try (SevenZFile archive = new SevenZFile(sevenZFile)) {
            String archiveBaseName = sevenZFile.getName();
            int lastDot = archiveBaseName.lastIndexOf('.');
            if (lastDot > 0) {
                archiveBaseName = archiveBaseName.substring(0, lastDot);
            }
            
            String bestCrc = null;
            long largestSize = 0;
            String bestEntryName = null;
            
            SevenZArchiveEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getName().startsWith("__") 
                    || entry.getName().startsWith(".")) {
                    continue;
                }
                
                String entryName = entry.getName();
                // 移除路径前缀
                int lastSlash = entryName.lastIndexOf('/');
                if (lastSlash >= 0) {
                    entryName = entryName.substring(lastSlash + 1);
                }
                // 也处理反斜杠（Windows 风格路径）
                int lastBackSlash = entryName.lastIndexOf('\\');
                if (lastBackSlash >= 0) {
                    entryName = entryName.substring(lastBackSlash + 1);
                }
                String entryBaseName = entryName;
                int entryDot = entryBaseName.lastIndexOf('.');
                if (entryDot > 0) {
                    entryBaseName = entryBaseName.substring(0, entryDot);
                }
                
                // 解压并计算此 entry 的 CRC32
                CRC32 crc32 = new CRC32();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = archive.read(buffer)) != -1) {
                    crc32.update(buffer, 0, bytesRead);
                }
                String crcHex = String.format("%08X", crc32.getValue());
                
                // 优先匹配与归档同名的 ROM
                if (entryBaseName.equalsIgnoreCase(archiveBaseName)) {
                    logger.info("7z 中找到同名 ROM: {} (CRC={})", entry.getName(), crcHex);
                    return crcHex;
                }
                
                // 记录最大的 ROM 文件作为备选
                if (entry.getSize() > largestSize) {
                    largestSize = entry.getSize();
                    bestCrc = crcHex;
                    bestEntryName = entry.getName();
                }
            }
            
            if (bestCrc != null) {
                logger.info("7z CRC32: 文件={}, entry={}, CRC={}", 
                    sevenZFile.getName(), bestEntryName, bestCrc);
                return bestCrc;
            }
            
            logger.warn("7z 中未找到有效的 ROM entry: {}", sevenZFile.getName());
            return null;
            
        } catch (IOException e) {
            logger.error("读取 7z 文件失败: {}", e.getMessage());
            return null;
        }
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
                // ScreenScraper 全局游戏ID：媒体目录稳定键（跨平台复用）
                try {
                    game.setSsGameId(jeu.get("id").asLong());
                } catch (NumberFormatException ex) {
                    logger.warn("SS游戏ID非数字: {}", jeu.get("id").asText());
                }
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
                String ssPublisher = getTextValue(jeu.get("editeur"));
                boolean preserve = Boolean.TRUE.equals(request.getPreserveLocalData());
                if (!preserve || game.getPublisher() == null || game.getPublisher().isBlank()) {
                    game.setPublisher(ssPublisher);
                }
            }
            
            // 开发商
            if (jeu.has("developpeur")) {
                String ssDeveloper = getTextValue(jeu.get("developpeur"));
                boolean preserve = Boolean.TRUE.equals(request.getPreserveLocalData());
                if (!preserve || game.getDeveloper() == null || game.getDeveloper().isBlank()) {
                    game.setDeveloper(ssDeveloper);
                }
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
            
            // 类型 - 从 genres 数组中提取（SS 结构: [{id, noms:[{langue,text}]}]）
            if (jeu.has("genres") && jeu.get("genres").isArray()) {
                StringBuilder genreBuilder = new StringBuilder();
                StringBuilder genreIdBuilder = new StringBuilder();
                for (JsonNode genreNode : jeu.get("genres")) {
                    if (!genreNode.isObject()) continue;
                    // genre 是对象：文本在 noms 数组里（按 langue 区分），id 是数字串
                    String genreText = extractTextFromRegionArray(genreNode.get("noms"), preferredLang);
                    if (genreText == null || genreText.isEmpty()) continue;
                    String genreId = genreNode.has("id") ? genreNode.get("id").asText() : "";
                                
                    if (genreBuilder.length() > 0) genreBuilder.append(", ");
                    if (genreIdBuilder.length() > 0) genreIdBuilder.append(", ");
                    genreBuilder.append(genreText);
                    genreIdBuilder.append(isInvalidText(genreId) ? "" : genreId);
                }
                boolean preserve = Boolean.TRUE.equals(request.getPreserveLocalData());
                if (!preserve || game.getGenre() == null || game.getGenre().isBlank()) {
                    game.setGenre(genreBuilder.toString());
                    game.setGenreid(genreIdBuilder.toString());
                }
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
                    boolean preserve = Boolean.TRUE.equals(request.getPreserveLocalData());
                    // 保留本地数据时：只有当本地只有年份（≤4字符）时才允许 SS 覆盖为完整日期
                    if (!preserve || game.getReleasedate() == null || game.getReleasedate().isBlank()
                            || game.getReleasedate().length() <= 4) {
                        game.setReleasedate(releaseDate);
                    }
                }
            }
            
            // 标记为已刮削
            game.setScraped(true);
            // 设置数据来源
            game.setSource("ScreenScraper");
            // 平台全量刮削 = 权威数据源自动匹配，标记 matched=true
            // batch/single = 用户手动触发，不自动标记，需用户在编辑页确认
            // v1.3 fuzzy 兜底命中时保持 matched=false（"疑似"），需用户从候选弹窗确认后手动置 true
            boolean fuzzyHit = Boolean.TRUE.equals(searchResult.get("fuzzyHit"));
            if ("platform".equals(request.getType()) && !fuzzyHit) {
                game.setMatched(true);
            }

            // 所有刮削成功的游戏无条件缓存 manifest（只要有 ssGameId 就缓存）
            // 用手里已有的完整 jeu 落 manifest（零额外请求）+ 从 roms[] 投影 parent_rom
            // + 按真实落库结果置 cached。任何异常都不得影响刮削。
            try {
                Long ssGameIdForCache = game.getSsGameId();
                if (ssGameIdForCache != null) {
                    boolean stored = gameManifestService.storeManifest(ssGameIdForCache, jeu);
                    game.setParentRom(gameManifestService.projectParentRom(jeu, game.getCrc32()));
                    game.setCached(stored);
                    logger.info("缓存 manifest {}: game={}, ssGameId={}, parentRom={}",
                            stored ? "成功" : "失败", game.getName(), ssGameIdForCache, game.getParentRom());
                }
            } catch (Exception e) {
                logger.warn("缓存 manifest/投影父rom失败(忽略，不影响刮削): {}", e.getMessage());
            }

            gameService.updateGame(game);
            // 刮削免费得到的中文名称/简介写入通用译文表（lang=zh），接管旧 translated_name/desc 列
            if (game.getId() != null) {
                if (game.getTranslatedName() != null && !game.getTranslatedName().isBlank()) {
                    fieldTranslationMapper.upsert(new FieldTranslation("game", game.getId(), "name", "zh", game.getTranslatedName()));
                }
                if (game.getTranslatedDesc() != null && !game.getTranslatedDesc().isBlank()) {
                    fieldTranslationMapper.upsert(new FieldTranslation("game", game.getId(), "desc", "zh", game.getTranslatedDesc()));
                }
            }
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
            // SS 的 noms 用 region 键（noms/dates），synopsis/genres 的 noms 用 langue 键，两者都兼容
            String region = item.has("region") ? item.get("region").asText().toLowerCase()
                    : item.has("langue") ? item.get("langue").asText().toLowerCase()
                    : "wor";
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
            // SS 的 dates 用 text 键存日期（如 {"region":"wor","text":"1992"}），date 键兼容旧格式
            String date = item.has("date") ? item.get("date").asText()
                    : item.has("text") ? item.get("text").asText()
                    : null;
            
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
                                   String platformName, Long platformId, Integer ssSystemId, Long taskId, AtomicInteger mediaTaskCounter) {
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

            // 兜底写回 ss_game_id：仅刮媒体不刮信息（scope=media）时 updateGameRecord 不会执行
            if (game.getSsGameId() == null) {
                Object gameNode = searchResult.get("game");
                if (gameNode instanceof JsonNode jeu && jeu.has("id")) {
                    try {
                        game.setSsGameId(jeu.get("id").asLong());
                        gameService.updateGame(game);
                        logger.info("兜底写回 ss_game_id={} for game {}", jeu.get("id").asText(), game.getName());
                    } catch (NumberFormatException ex) {
                        logger.warn("SS游戏ID非数字: {}", jeu.get("id").asText());
                    }
                }
            }

            // local → ss 目录平滑迁移（未匹配游戏升级为匹配后，把旧 local 目录媒体搬到 ss 目录）
            migrateLocalMediaToSs(game, ssSystemId);

            // 新路径规则: {ssSystemId}/{ssGameId}/ 或 {ssSystemId}/local/{stem}/（不再使用平台名/本地ID/region 层）
            Path gameMediaDir = PathResolver.resolveGameMediaDir(game, ssSystemId);
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
     * local → ss 目录平滑迁移：
     * 未匹配游戏升级为匹配后（写入 ss_game_id），把 {ssSystemId}/local/{stem}/ 下
     * 已刮削/上传的媒体移动到 {ssSystemId}/{ssGameId}/，并同步更新 game 媒体字段路径。
     * 目标已存在同名文件时跳过移动（保留 SS 目录版本）。
     */
    private void migrateLocalMediaToSs(Game game, Integer ssSystemId) {
        if (game.getSsGameId() == null || ssSystemId == null) {
            return;
        }
        String stem = PathResolver.extractGameStem(game);
        Path localDir = Paths.get(PathResolver.MEDIA_BASE, String.valueOf(ssSystemId), "local", stem);
        if (!Files.isDirectory(localDir)) {
            return;
        }
        try {
            Path ssDir = Paths.get(PathResolver.MEDIA_BASE, String.valueOf(ssSystemId), String.valueOf(game.getSsGameId()));
            Files.createDirectories(ssDir);

            String localDirDb = PathResolver.normalizeForDb(localDir);

            // 1. 移动文件（目标同名文件已存在则跳过）
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(localDir)) {
                for (Path file : stream) {
                    if (!Files.isRegularFile(file)) {
                        continue;
                    }
                    Path target = ssDir.resolve(file.getFileName());
                    if (Files.exists(target)) {
                        logger.info("local→ss 迁移跳过（目标已存在）: {}", file.getFileName());
                        continue;
                    }
                    Files.move(file, target);
                    logger.info("local→ss 媒体迁移: {} -> {}", file.getFileName(), target);
                }
            }

            // 2. 更新 game 媒体字段中指向 local 目录的路径
            boolean fieldUpdated = false;
            for (com.gamelist.model.MediaType mt : com.gamelist.model.MediaType.values()) {
                String value = readMediaField(game, mt);
                if (value == null) {
                    continue;
                }
                String normalized = value.replace('\\', '/');
                if (!normalized.startsWith(localDirDb)) {
                    continue;
                }
                String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
                writeMediaField(game, mt, PathResolver.normalizeForDb(ssDir.resolve(fileName)));
                fieldUpdated = true;
            }
            if (fieldUpdated) {
                gameService.updateGame(game);
            }

            // 3. local 目录已空则删除
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(localDir)) {
                if (!stream.iterator().hasNext()) {
                    Files.deleteIfExists(localDir);
                    logger.info("local→ss 迁移完成，删除空目录: {}", localDir);
                }
            }
        } catch (Exception e) {
            logger.warn("local→ss 迁移失败: game={}, error={}", game.getName(), e.getMessage());
        }
    }

    /** 反射读取 game 的媒体字段值 */
    private String readMediaField(Game game, com.gamelist.model.MediaType mt) {
        try {
            String getterName = mt.getGetterName();
            if ("getBox3d".equals(getterName)) {
                getterName = "getBox3D";
            }
            java.lang.reflect.Method getter = Game.class.getMethod(getterName);
            Object value = getter.invoke(game);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 反射写入 game 的媒体字段 */
    private void writeMediaField(Game game, com.gamelist.model.MediaType mt, String path) {
        try {
            String setterName = mt.getSetterName();
            if ("setBox3d".equals(setterName)) {
                setterName = "setBox3D";
            }
            java.lang.reflect.Method setter = Game.class.getMethod(setterName, String.class);
            setter.invoke(game, path);
        } catch (Exception e) {
            logger.warn("反射写入媒体字段失败: field={}, error={}", mt.getJavaField(), e.getMessage());
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

            // 目录中已有该类型的媒体文件（任意扩展名）优先复用
            Path existingFile = PathResolver.findExistingMedia(gameMediaDir, mediaType);
            boolean fileExists = existingFile != null;
            boolean overwrite = Boolean.TRUE.equals(request.getOverwrite());
            boolean onlyMissing = Boolean.TRUE.equals(request.getOnlyMissing());

            if (fileExists && !overwrite) {
                if (onlyMissing) {
                    // 仅刮削缺失内容模式：文件已存在则完全跳过
                    return;
                }
                // 文件已存在但不覆盖：创建已完成状态的任务，并直接更新游戏记录的媒体路径
                MediaDownloadTask existingTask = new MediaDownloadTask();
                existingTask.setTaskId(taskId);
                existingTask.setGameId(gameId);
                existingTask.setGameName(gameName);
                existingTask.setPlatformId(platformId);
                existingTask.setPlatformName(platformName);
                existingTask.setMediaType(mediaType);
                existingTask.setDownloadUrl(EncryptionUtil.encrypt(url));
                existingTask.setLocalPath(PathResolver.normalizeForDb(existingFile));
                existingTask.setStatus(MediaDownloadTask.STATUS_COMPLETED);
                existingTask.setOrderIndex(Long.valueOf(mediaTaskCounter.getAndIncrement()));
                tasksToSave.add(existingTask);
                // 文件已存在，直接更新游戏记录的媒体路径（COMPLETED 任务不会被下载线程拾取，需在此处同步更新）
                updateGameMediaPath(gameId, mediaType, PathResolver.normalizeForDb(existingFile));
                logger.info("媒体文件已存在，标记为已完成: {} (区域: {}) for game {}", mediaType, region, gameName);
                return;
            }

            if (fileExists) {
                // 覆盖模式：删除旧扩展名文件，避免同类型多文件并存
                try {
                    Files.deleteIfExists(existingFile);
                } catch (IOException e) {
                    logger.warn("删除旧媒体文件失败: {}", existingFile);
                }
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
     * 下载媒体文件（带状态码检查）
     * 遇到限额状态码时抛出 ScreenScraperApiException，以便调用方处理限额暂停逻辑
     */
    private void downloadMediaFile(String urlStr, String localPath) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            connection.disconnect();
            if (ScreenScraperStatusHandler.shouldStopImmediately(responseCode)) {
                throw new ScreenScraperApiException(responseCode, 
                    ScreenScraperStatusHandler.getStatusInfo(responseCode).getDescription());
            }
            throw new Exception("媒体下载HTTP错误: " + responseCode + " - " + 
                ScreenScraperStatusHandler.getStatusInfo(responseCode).getDescription());
        }

        try (java.io.InputStream inputStream = connection.getInputStream();
             java.io.FileOutputStream outputStream = new java.io.FileOutputStream(localPath)) {

            byte[] buffer = new byte[65536]; // 64KB 缓冲区，提升大文件下载吞吐量
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } finally {
            connection.disconnect();
        }
    }
    
    // ==================== 统一任务池相关方法 ====================
    
    @Override
    public void enqueueGameInfoTasks(List<Game> games, ScraperRequest request, Integer systemId, Long taskId, int priority) {
        if (games == null || games.isEmpty()) {
            return;
        }
        
        List<ScrapeTask> tasks = new ArrayList<>();
        long orderIndex = System.currentTimeMillis();
        
        for (Game game : games) {
            ScrapeTask task = new ScrapeTask();
            task.setTaskType(ScrapeTask.TYPE_GAME_INFO);
            task.setGameId(game.getId());
            task.setPlatformId(request.getPlatformId());
            // 从 path 提取文件名作为 romFilename
            if (game.getPath() != null) {
                String path = game.getPath();
                int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                if (lastSlash >= 0) {
                    task.setRomFilename(path.substring(lastSlash + 1));
                } else {
                    task.setRomFilename(path);
                }
            }
            task.setSystemId(systemId);
            task.setSsGameId(game.getSsGameId());
            // 存储媒体偏好到任务中，以便 worker 执行时知道要下载哪些媒体
            // ★ overwrite 标志编码到 mediaScope 前缀 "overwrite|" 中，供 executeGameInfoTask 恢复
            boolean overwriteMedia = Boolean.TRUE.equals(request.getOverwrite());
            String prefix = overwriteMedia ? "overwrite|" : "";
            boolean scrapeAllMedia = Boolean.TRUE.equals(request.getScrapeAllMedia());
            if (scrapeAllMedia) {
                task.setMediaScope(prefix + "*");
            } else if (request.getMediaTypes() != null && !request.getMediaTypes().isEmpty()) {
                task.setMediaScope(prefix + String.join(",", request.getMediaTypes()));
            }
            // else: mediaScope = null (不刮媒体)
            task.setStatus(ScrapeTask.STATUS_PENDING);
            task.setPriority(priority);
            task.setTaskId(taskId);
            task.setOrderIndex(orderIndex++);
            tasks.add(task);
        }
        
        scrapeTaskMapper.batchInsert(tasks);
        logger.info("已创建 {} 个游戏信息刮削任务，优先级={}", tasks.size(), priority);
    }
    
    @Override
    public boolean executeGameInfoTask(ScrapeTask task) {
        logger.info("执行游戏信息刮削任务: taskId={}, gameId={}", task.getId(), task.getGameId());
        
        try {
            // 获取游戏信息
            Game game = gameService.getGameById(task.getGameId());
            if (game == null) {
                logger.error("游戏不存在: gameId={}", task.getGameId());
                return false;
            }
            
            // ★ 缓存/已刮削检查：避免浪费 SS API 配额
            boolean alreadyScraped = Boolean.TRUE.equals(game.getScraped());
            boolean alreadyCached = Boolean.TRUE.equals(game.getCached());
            
            if (alreadyScraped || alreadyCached) {
                if (alreadyCached) {
                    // 已缓存：从缓存 manifest 创建媒体下载任务（0 次 SS API 调用）
                    com.fasterxml.jackson.databind.JsonNode cachedManifest = 
                        gameManifestService.getCachedGame(game.getSsGameId());
                    if (cachedManifest != null && task.getMediaScope() != null) {
                        ScraperRequest cachedRequest = new ScraperRequest();
                        cachedRequest.setPlatformId(task.getPlatformId());
                        cachedRequest.setType("single");
                        // ★ 从 mediaScope 恢复 overwrite 标志和实际媒体范围
                        String rawScope = task.getMediaScope();
                        boolean overwriteMedia = rawScope.startsWith("overwrite|");
                        if (overwriteMedia) {
                            cachedRequest.setOverwrite(true);
                            rawScope = rawScope.substring("overwrite|".length());
                        }
                        if ("*".equals(rawScope)) {
                            cachedRequest.setScrapeAllMedia(true);
                        } else {
                            cachedRequest.setMediaTypes(java.util.Arrays.asList(rawScope.split(",")));
                        }
                        
                        // 将 cached manifest 包装成 enqueueMediaTasksFromGameInfo 期望的 Map 格式
                        Map<String, Object> cachedData = new HashMap<>();
                        cachedData.put("medias", cachedManifest.get("medias"));
                        cachedData.put("game", cachedManifest);
                        
                        ScraperSystem system = getSystemInfo(task.getSystemId());
                        String platformName = system != null ? system.getName() : "unknown";
                        enqueueMediaTasksFromGameInfo(game, cachedData, cachedRequest,
                            platformName, task.getPlatformId(), task.getSystemId(), task.getTaskId());
                        logger.info("游戏已缓存，从 manifest 创建媒体任务（跳过 SS API）: gameId={}", game.getId());
                    } else {
                        logger.info("游戏已缓存，无媒体需求，跳过: gameId={}", game.getId());
                    }
                } else {
                    // 已刮削但未缓存：跳过 SS API 调用，但尝试复用已有的 FAILED 媒体任务 URL
                    logger.info("游戏已刮削，跳过 SS API 调用: gameId={}", game.getId());
                    
                    // 复用 FAILED 任务的已存储 URL（避免重新调 jeuInfos）
                    if (task.getMediaScope() != null) {
                        try {
                            int reused = mediaDownloadTaskMapper.resetFailedTasksToPending(game.getId());
                            if (reused > 0) {
                                logger.info("复用 {} 条 FAILED 媒体任务的已存储 URL，重置为 PENDING: gameId={}", reused, game.getId());
                            }
                        } catch (Exception e) {
                            logger.warn("复用 FAILED 媒体任务失败（不影响流程）: gameId={}, err={}", game.getId(), e.getMessage());
                        }
                    }
                }
                return true;
            }
            
            // 获取系统配置
            ScraperSystem system = getSystemInfo(task.getSystemId());
            if (system == null) {
                logger.error("无法获取系统配置: systemId={}", task.getSystemId());
                return false;
            }
            
            // 处理游戏文件
            GameFileInfo fileInfo = processGameFile(game, system);
            
            // 调用 ScreenScraper API 搜索游戏
            ScraperRequest request = new ScraperRequest();
            request.setPlatformId(task.getPlatformId());
            request.setType("single");
            
            // 从 mediaScope 恢复媒体偏好（含 overwrite 标志）
            if (task.getMediaScope() != null) {
                String rawScope = task.getMediaScope();
                boolean overwriteMedia = rawScope.startsWith("overwrite|");
                if (overwriteMedia) {
                    request.setOverwrite(true);
                    rawScope = rawScope.substring("overwrite|".length());
                }
                if ("*".equals(rawScope)) {
                    request.setScrapeAllMedia(true);
                } else {
                    request.setMediaTypes(java.util.Arrays.asList(rawScope.split(",")));
                }
            }
            
            Map<String, Object> searchResult = searchGameWithStatus(fileInfo, system.getSystemId(), request);
            
            if (Boolean.TRUE.equals(searchResult.get("found"))) {
                Map<String, Object> data = (Map<String, Object>) searchResult.get("data");
                
                // 更新游戏记录
                updateGameRecord(game, data, request);
                
                // 创建媒体下载任务
                enqueueMediaTasksFromGameInfo(game, data, request, system.getName(), task.getPlatformId(),
                    task.getSystemId(), task.getTaskId());
                
                logger.info("游戏信息刮削完成: gameId={}, gameName={}", game.getId(), game.getName());
                return true;
            } else {
                logger.warn("游戏未找到: gameId={}, gameName={}", game.getId(), game.getName());
                return false;
            }
        } catch (Exception e) {
            logger.error("执行游戏信息刮削任务失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean executeMediaDownloadTask(ScrapeTask task) {
        logger.info("执行媒体下载任务: taskId={}, gameId={}, mediaType={}", 
            task.getId(), task.getGameId(), task.getMediaType());
        
        try {
            if (task.getDownloadUrl() == null || task.getLocalPath() == null) {
                logger.error("媒体下载任务缺少必要参数: taskId={}", task.getId());
                return false;
            }
            
            // ★ 文件已存在检查：避免重复下载浪费带宽
            java.io.File targetFile = new java.io.File(task.getLocalPath());
            if (targetFile.exists() && targetFile.length() > 0) {
                logger.info("媒体文件已存在，跳过下载: taskId={}, path={}", task.getId(), task.getLocalPath());
                updateGameMediaPath(task.getGameId(), task.getMediaType(), task.getLocalPath());
                return true;
            }
            
            // ★ 同类型文件检查：目录中已有该类型的其他媒体文件（不同扩展名）也视为已存在
            java.nio.file.Path localFilePath = java.nio.file.Paths.get(task.getLocalPath());
            java.nio.file.Path gameMediaDir = localFilePath.getParent();
            java.nio.file.Path existingFile = PathResolver.findExistingMedia(gameMediaDir, task.getMediaType());
            if (existingFile != null) {
                logger.info("同类型媒体文件已存在，跳过下载: taskId={}, existingPath={}", task.getId(), existingFile);
                updateGameMediaPath(task.getGameId(), task.getMediaType(), PathResolver.normalizeForDb(existingFile));
                return true;
            }
            
            // 解密下载 URL
            String decryptedUrl = EncryptionUtil.decrypt(task.getDownloadUrl());
            
            // 下载文件
            downloadMediaFile(decryptedUrl, task.getLocalPath());
            
            // 更新游戏媒体路径
            updateGameMediaPath(task.getGameId(), task.getMediaType(), task.getLocalPath());
            
            logger.info("媒体下载完成: taskId={}, path={}", task.getId(), task.getLocalPath());
            return true;
        } catch (Exception e) {
            logger.error("执行媒体下载任务失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean executeMediaDownloadTask(MediaDownloadTask task) {
        logger.info("执行媒体下载任务(MediaDownloadTask): taskId={}, gameId={}, mediaType={}", 
            task.getId(), task.getGameId(), task.getMediaType());
        
        try {
            if (task.getDownloadUrl() == null || task.getLocalPath() == null) {
                logger.error("媒体下载任务缺少必要参数: taskId={}", task.getId());
                return false;
            }
            
            // ★ 文件已存在检查：避免重复下载浪费带宽
            java.io.File targetFile = new java.io.File(task.getLocalPath());
            if (targetFile.exists() && targetFile.length() > 0) {
                logger.info("媒体文件已存在，跳过下载: taskId={}, path={}", task.getId(), task.getLocalPath());
                updateGameMediaPath(task.getGameId(), task.getMediaType(), task.getLocalPath());
                return true;
            }
            
            // ★ 同类型文件检查：目录中已有该类型的其他媒体文件（不同扩展名）也视为已存在
            java.nio.file.Path localFilePath = java.nio.file.Paths.get(task.getLocalPath());
            java.nio.file.Path gameMediaDir = localFilePath.getParent();
            java.nio.file.Path existingFile = PathResolver.findExistingMedia(gameMediaDir, task.getMediaType());
            if (existingFile != null) {
                logger.info("同类型媒体文件已存在，跳过下载: taskId={}, existingPath={}", task.getId(), existingFile);
                updateGameMediaPath(task.getGameId(), task.getMediaType(), PathResolver.normalizeForDb(existingFile));
                return true;
            }
            
            // 解密下载 URL
            String decryptedUrl = EncryptionUtil.decrypt(task.getDownloadUrl());
            
            // 下载文件
            downloadMediaFile(decryptedUrl, task.getLocalPath());
            
            // 更新游戏媒体路径
            updateGameMediaPath(task.getGameId(), task.getMediaType(), task.getLocalPath());
            
            logger.info("媒体下载完成(MediaDownloadTask): taskId={}, path={}", task.getId(), task.getLocalPath());
            return true;
        } catch (Exception e) {
            logger.error("执行媒体下载任务失败(MediaDownloadTask): taskId={}, error={}", task.getId(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 从游戏信息刮削结果创建媒体下载任务
     * <p>路径规则对齐 MEMO 设计: {ssSystemId}/{ssGameId}/{type}.{ext}
     * <p>每种媒体类型只保留一个文件（优先区域优先），与 createMediaTask 逻辑一致
     */
    private void enqueueMediaTasksFromGameInfo(Game game, Map<String, Object> searchResult, 
            ScraperRequest request, String platformName, Long platformId, Integer ssSystemId, Long taskId) {
        try {
            JsonNode medias = (JsonNode) searchResult.get("medias");
            if (medias == null || !medias.isArray() || medias.size() == 0) {
                logger.info("无媒体文件需要下载: gameId={}", game.getId());
                return;
            }
            
            String preferredRegion = request.getRegion() != null ? request.getRegion().toLowerCase() : "wor";
            Long gameId = game.getId();
            
            // 确保 ss_game_id 已写入
            if (game.getSsGameId() == null) {
                Object gameNode = searchResult.get("game");
                if (gameNode instanceof JsonNode jeu && jeu.has("id")) {
                    try {
                        game.setSsGameId(jeu.get("id").asLong());
                        gameService.updateGame(game);
                    } catch (NumberFormatException ex) {
                        logger.warn("SS游戏ID非数字: {}", jeu.get("id").asText());
                    }
                }
            }
            
            // 创建媒体目录
            Path gameMediaDir = PathResolver.resolveGameMediaDir(game, ssSystemId);
            Files.createDirectories(gameMediaDir);
            
            List<MediaDownloadTask> mediaTasks = new ArrayList<>();
            long orderIndex = System.currentTimeMillis();
            
            boolean scrapeAllMedia = Boolean.TRUE.equals(request.getScrapeAllMedia());
            List<String> requestedTypes = request.getMediaTypes();
            
            // ★ 按类型去重：优先区域优先，与 saveMediaTasksToDb/createMediaTask 逻辑一致
            Map<String, JsonNode> preferredMap = new LinkedHashMap<>();
            Map<String, JsonNode> fallbackMap = new LinkedHashMap<>();
            
            for (JsonNode media : medias) {
                if (!media.has("type") || !media.has("url")) continue;
                
                String type = media.get("type").asText();
                String region = media.has("region") ? media.get("region").asText().toLowerCase() : "wor";
                
                // 检查是否需要下载这个类型
                boolean shouldDownload = false;
                if (scrapeAllMedia) {
                    shouldDownload = true;
                } else if (requestedTypes != null) {
                    String screenScraperType = mapMediaType(type);
                    if (screenScraperType == null) screenScraperType = type;
                    shouldDownload = requestedTypes.contains(type) || requestedTypes.contains(screenScraperType);
                }
                if (!shouldDownload) continue;
                
                // 按区域分组：优先区域进 preferredMap，其他区域仅在优先区域没有该类型时进 fallbackMap
                if (region.equals(preferredRegion)) {
                    preferredMap.put(type, media);
                } else {
                    if (!preferredMap.containsKey(type)) {
                        fallbackMap.put(type, media);
                    }
                }
            }
            
            // 合并：优先区域优先，非优先区域补充
            Map<String, JsonNode> selectedMedia = new LinkedHashMap<>(preferredMap);
            for (Map.Entry<String, JsonNode> entry : fallbackMap.entrySet()) {
                if (!selectedMedia.containsKey(entry.getKey())) {
                    selectedMedia.put(entry.getKey(), entry.getValue());
                }
            }
            
            // 为选中的媒体创建下载任务
            for (Map.Entry<String, JsonNode> entry : selectedMedia.entrySet()) {
                String type = entry.getKey();
                JsonNode media = entry.getValue();
                
                // ★ 文件已存在检查：overwrite=true 时跳过检查，强制重新下载
                boolean overwriteMedia = Boolean.TRUE.equals(request.getOverwrite());
                if (!overwriteMedia) {
                    Path existingFile = PathResolver.findExistingMedia(gameMediaDir, type);
                    if (existingFile != null) {
                        logger.debug("媒体文件已存在，跳过创建下载任务: gameId={}, type={}", gameId, type);
                        continue;
                    }
                }
                
                // 文件名: {type}.{ext}（对齐 MEMO 设计）
                String ext = media.has("format") ? media.get("format").asText() : "png";
                String localPath = gameMediaDir.resolve(type + "." + ext).toString();
                String encryptedUrl = EncryptionUtil.encrypt(media.get("url").asText());
                
                MediaDownloadTask mediaTask = new MediaDownloadTask();
                mediaTask.setGameId(gameId);
                mediaTask.setGameName(game.getName());
                mediaTask.setPlatformId(platformId);
                mediaTask.setPlatformName(platformName);
                mediaTask.setGameFieldName(mapMediaTypeToGameField(type));
                mediaTask.setMediaType(type);
                mediaTask.setDownloadUrl(encryptedUrl);
                mediaTask.setLocalPath(localPath);
                mediaTask.setStatus(MediaDownloadTask.STATUS_PENDING);
                mediaTask.setTaskId(taskId);
                mediaTask.setOrderIndex(orderIndex++);
                mediaTasks.add(mediaTask);
            }
            
            if (!mediaTasks.isEmpty()) {
                mediaDownloadTaskMapper.insertBatch(mediaTasks);
                logger.info("已创建 {} 个媒体下载任务（media_download_task表） for gameId={}", mediaTasks.size(), gameId);
            }
        } catch (Exception e) {
            logger.error("创建媒体下载任务失败: gameId={}", game.getId(), e);
        }
    }

    // ==================== rom_db serial 匹配白名单 ====================

    /**
     * 判断该系统是否支持 serial 匹配路径。
     * 委托给 RomDbMatchingConfig 白名单，覆盖所有能从 ROM 内部可靠提取标识符的系统：
     * 光盘：PS1(57)/PS2(58)/PS3(59)/NGC(13)/Saturn(22)/MegaCD(20)/Dreamcast(23)
     * 卡带：GB(9)/GBC(10)/GBA(12)/N64(14)/Genesis(1)
     */
    private boolean isSerialMatchSystem(int systemId) {
        return romDbMatchingConfig != null && romDbMatchingConfig.isWhitelisted(systemId);
    }
}