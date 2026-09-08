package com.gamelist.service.impl;

import com.gamelist.mapper.MediaDownloadTaskMapper;
import com.gamelist.mapper.GameMapper;
import com.gamelist.model.MediaDownloadTask;
import com.gamelist.model.Game;
import com.gamelist.service.MediaDownloadService;
import com.gamelist.service.ThreadResourceManager;
import com.gamelist.util.RateLimitCounter;
import com.gamelist.util.ScreenScraperStatusHandler;
import com.gamelist.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MediaDownloadServiceImpl implements MediaDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(MediaDownloadServiceImpl.class);

    @Autowired
    private MediaDownloadTaskMapper mediaDownloadTaskMapper;
    
    @Autowired
    private GameMapper gameMapper;
    
    @Lazy
    @Autowired
    private com.gamelist.service.ScraperService scraperService;
    
    @Autowired
    private com.gamelist.service.ScraperSettingsService scraperSettingsService;
    
    @Autowired
    private ThreadResourceManager threadResourceManager;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private static final int BATCH_SIZE = 100;
    private static final long BATCH_PAUSE_MS = 30000;

    // 限流计数器 - 10秒内20次访问则休息20秒
    private final RateLimitCounter rateLimitCounter = new RateLimitCounter();

    // 404计数器
    private final AtomicInteger notFoundCount = new AtomicInteger(0);
    private volatile long notFoundWindowStart = System.currentTimeMillis();
    private static final int NOT_FOUND_THRESHOLD = 100;
    private static final long NOT_FOUND_WINDOW_MS = 10000;

    @Override
    @Async
    public void startMediaDownloadTask(Long taskId, int maxThreads) {
        if (!isRunning.compareAndSet(false, true)) {
            logger.warn("媒体下载任务已在运行中");
            return;
        }
        isPaused.set(false);
        isStopped.set(false);

        logger.info("开始媒体下载任务，任务ID: {}, 最大线程数: {}", taskId, maxThreads);

        // 更新线程资源管理器的最大线程数（实际并发由资源管理器控制）
        threadResourceManager.updateMaxThreads(maxThreads);
        
        // 创建线程池：至少 2 个工作线程，保证 maxThreads=1 时下载不会完全串行
        // 实际并发仍由 ThreadResourceManager.acquireForMedia() 严格控制
        int poolSize = Math.max(maxThreads, 2);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        AtomicInteger totalProcessed = new AtomicInteger(0);

        try {
            long totalPending = mediaDownloadTaskMapper.countPendingByTaskId(taskId);
            logger.info("待下载媒体文件总数: {}", totalPending);

            // 启动下载工作线程
            for (int i = 0; i < poolSize; i++) {
                executor.submit(() -> {
                    try {
                        while (isRunning.get() && !isStopped.get() && !stopRequested.get()) {
                            // 检查全局暂停
                            while (isPaused.get() && isRunning.get() && !isStopped.get() && !stopRequested.get()) {
                                Thread.sleep(500);
                            }

                            if (!isRunning.get() || isStopped.get() || stopRequested.get()) {
                                break;
                            }

                            // 检查限流状态
                            while (rateLimitCounter.isPaused() && !isStopped.get() && !stopRequested.get()) {
                                Thread.sleep(200);
                            }

                            if (isStopped.get() || stopRequested.get()) {
                                break;
                            }

                            // 关键：使用线程资源管理器获取资源（低优先级，游戏信息线程优先）
                            boolean acquired = threadResourceManager.acquireForMedia(5000);
                            if (!acquired) {
                                // 获取资源失败，可能是有游戏信息线程在等待，短暂等待后重试
                                Thread.sleep(100);
                                continue;
                            }

                            try {
                                // 获取一个待下载任务
                                MediaDownloadTask mediaTask = mediaDownloadTaskMapper.selectOnePendingTask(taskId);
                                if (mediaTask == null) {
                                    // 没有待下载任务，短暂等待后继续循环
                                    Thread.sleep(500);
                                    continue;
                                }

                                // 尝试更新状态为下载中（乐观锁）
                                int updated = mediaDownloadTaskMapper.tryUpdateStatus(
                                    mediaTask.getId(), 
                                    MediaDownloadTask.STATUS_PENDING, 
                                    MediaDownloadTask.STATUS_DOWNLOADING
                                );
                                
                                if (updated == 0) {
                                    // 状态已被其他线程修改，跳过
                                    Thread.yield();
                                    continue;
                                }

                                try {
                                    // 尝试获取限流许可
                                    if (!rateLimitCounter.tryAcquire()) {
                                        mediaDownloadTaskMapper.updateStatusById(mediaTask.getId(), MediaDownloadTask.STATUS_PENDING, null);
                                        Thread.sleep(100);
                                        continue;
                                    }

                                    // 下载媒体文件（带状态码处理）
                                    Map<String, Object> downloadResult = downloadMediaFileWithStatus(mediaTask.getDownloadUrl(), mediaTask.getLocalPath());
                                    boolean success = (Boolean) downloadResult.get("success");
                                    int statusCode = (Integer) downloadResult.get("statusCode");

                                    if (success) {
                                        mediaDownloadTaskMapper.updateStatusById(mediaTask.getId(), MediaDownloadTask.STATUS_COMPLETED, null);
                                        totalProcessed.incrementAndGet();
                                        updateGameMediaPath(mediaTask);
                                        resetNotFoundCount();
                                    } else {
                                        String errorMessage = (String) downloadResult.get("message");
                                        
                                        if (statusCode == 404) {
                                            if (shouldStopDueToNotFound()) {
                                                logger.error("10秒内出现10次404错误，停止媒体下载");
                                                isStopped.set(true);
                                                sendNotification("媒体下载停止", "10秒内出现10次404错误，已停止媒体下载");
                                            }
                                        } else if (ScreenScraperStatusHandler.shouldStopImmediately(statusCode)) {
                                            logger.error("遇到特殊状态码 {}，停止媒体下载: {}", statusCode, errorMessage);
                                            isStopped.set(true);
                                            sendNotification("媒体下载停止", ScreenScraperStatusHandler.getSuggestion(statusCode));
                                        }

                                        mediaDownloadTaskMapper.updateStatusById(mediaTask.getId(), MediaDownloadTask.STATUS_FAILED, errorMessage);
                                    }

                                } catch (Exception e) {
                                    logger.error("下载媒体文件失败: {}", e.getMessage());
                                    mediaDownloadTaskMapper.updateStatusById(mediaTask.getId(), MediaDownloadTask.STATUS_FAILED, e.getMessage());
                                }
                            } finally {
                                // 关键：必须归还资源
                                threadResourceManager.releaseForMedia();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            // 等待所有任务完成或被停止
            while (isRunning.get() && !isStopped.get()) {
                long pendingCount = mediaDownloadTaskMapper.countPendingByTaskId(taskId);
                long downloadingCount = mediaDownloadTaskMapper.countDownloadingByTaskId(taskId);
                
                if (pendingCount == 0 && downloadingCount == 0) {
                    logger.info("所有媒体下载任务已完成");
                    break;
                }
                
                Thread.sleep(1000);
            }

            logger.info("媒体下载任务完成，共处理 {} 个文件", totalProcessed.get());

        } catch (Exception e) {
            logger.error("媒体下载任务执行异常: {}", e.getMessage());
        } finally {
            // 重置资源管理器
            threadResourceManager.reset();
            
            isRunning.set(false);
            isPaused.set(false);
            isStopped.set(false);
            stopRequested.set(false);
            rateLimitCounter.reset();
            resetNotFoundCount();
            
            // 关闭线程池
            executor.shutdownNow();
            try {
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            logger.info("媒体下载任务结束");
        }
    }

    @Override
    public void stopMediaDownloadTask() {
        logger.info("收到停止媒体下载任务请求（等待当前文件下载完成后停止）");
        stopRequested.set(true);
        // 设置 isStopped 标志，让主线程循环检测到后退出
        isStopped.set(true);
    }

    @Override
    public void pauseMediaDownloadTask() {
        if (isRunning.get() && !isPaused.get()) {
            isPaused.set(true);
            logger.info("媒体下载任务已暂停");
        }
    }

    @Override
    public void resumeMediaDownloadTask() {
        if (isRunning.get() && isPaused.get()) {
            isPaused.set(false);
            logger.info("媒体下载任务已恢复");
        }
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public boolean isPaused() {
        return isPaused.get();
    }

    /**
     * 下载媒体文件，带状态码处理
     */
    private Map<String, Object> downloadMediaFileWithStatus(String urlStr, String localPath) {
        try {
            // 解密URL
            String decryptedUrl = EncryptionUtil.decrypt(urlStr);
            String authenticatedUrl = addAuthenticationParams(decryptedUrl);
            URL url = new URL(authenticatedUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);

            int statusCode = connection.getResponseCode();
            logger.debug("正在下载媒体文件 - URL: {}, 本地路径: {}", maskSensitiveParams(authenticatedUrl), localPath);

            if (statusCode == 200) {
                try (InputStream inputStream = connection.getInputStream();
                     FileOutputStream outputStream = new FileOutputStream(localPath)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                connection.disconnect();
                return Map.of("success", true, "statusCode", 200, "message", "下载成功");
            } else {
                // 处理非200状态码
                String errorMessage = ScreenScraperStatusHandler.getStatusInfo(statusCode).getDescription();
                connection.disconnect();
                return Map.of("success", false, "statusCode", statusCode, "message", errorMessage);
            }
        } catch (Exception e) {
            return Map.of("success", false, "statusCode", -1, "message", e.getMessage());
        }
    }
    
    /**
     * 为下载URL添加或更新用户认证参数
     * 处理逻辑：
     * 1. URL无认证 + 用户已登录 → 添加当前用户的认证参数
     * 2. URL有认证(test) + 用户已登录(admin) → 替换为当前用户(admin)的认证参数
     * 3. URL有认证 + 用户未登录 → 移除URL中的认证参数
     */
    private String addAuthenticationParams(String urlStr) {
        try {
            Map<String, String> settings = scraperSettingsService.getSettings();
            String currentUsername = settings.get("username");
            String currentPassword = settings.get("password");
            boolean isLoggedIn = (currentUsername != null && !currentUsername.isEmpty() 
                                 && currentPassword != null && !currentPassword.isEmpty());
            
            URL url = new URL(urlStr);
            String protocol = url.getProtocol();
            String host = url.getHost();
            String path = url.getPath();
            String query = url.getQuery();
            
            // 解析现有查询参数，移除旧的ssid和sspassword
            StringBuilder newQuery = new StringBuilder();
            if (query != null && !query.isEmpty()) {
                String[] params = query.split("&");
                for (String param : params) {
                    // 跳过旧的ssid和sspassword参数
                    if (param.startsWith("ssid=") || param.startsWith("sspassword=")) {
                        continue;
                    }
                    if (newQuery.length() > 0) {
                        newQuery.append("&");
                    }
                    newQuery.append(param);
                }
            }
            
            // 根据登录状态决定是否添加认证参数
            if (isLoggedIn) {
                // 用户已登录，添加当前用户的认证参数
                if (newQuery.length() > 0) {
                    newQuery.append("&");
                }
                newQuery.append("ssid=").append(currentUsername).append("&sspassword=").append(currentPassword);
                logger.debug("已为URL添加认证参数: {}", currentUsername);
            } else {
                // 用户未登录，确保URL中没有认证参数（已在上一步移除）
                logger.debug("用户未登录，移除URL中的认证参数");
            }
            
            String finalUrl = protocol + "://" + host + path;
            if (newQuery.length() > 0) {
                finalUrl += "?" + newQuery.toString();
            }
            
            return finalUrl;
        } catch (Exception e) {
            logger.error("处理URL认证参数失败: {}", e.getMessage());
            return urlStr;
        }
    }

    /**
     * 日志脱敏方法 - 隐藏URL中的敏感参数
     */
    private String maskSensitiveParams(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        // 隐藏 ssid 和 sspassword 参数
        return url.replaceAll("ssid=[^&]*", "ssid=***")
                  .replaceAll("sspassword=[^&]*", "sspassword=***");
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
            logger.error("10秒内出现{}次404错误，停止媒体下载", count);
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
     * 发送通知（保留接口，与ScraperServiceImpl保持一致）
     */
    private void sendNotification(String title, String message) {
        logger.info("发送通知: {} - {}", title, message);
        // TODO: 集成到现有的通知机制
    }
    
    /**
     * 更新游戏记录中的媒体文件路径
     */
    private void updateGameMediaPath(MediaDownloadTask mediaTask) {
        try {
            String mediaType = mediaTask.getMediaType();
            
            // 优先使用 gameFieldName（从 ScraperServiceImpl 传入的映射），如果为空则使用 mediaType 映射
            String gameField = mediaTask.getGameFieldName();
            if (gameField == null || gameField.isEmpty()) {
                gameField = mapMediaTypeToGameField(mediaType);
            }
            
            if ("ignore".equals(gameField)) {
                // 忽略的媒体类型（如mixrbv1, mixrbv2），不更新
                return;
            }
            if (gameField == null) {
                logger.debug("媒体类型 [{}] 无法映射到 game 表字段，跳过更新", mediaType);
                return;
            }
            
            Game game = gameMapper.selectGameById(mediaTask.getGameId());
            if (game == null) {
                logger.warn("游戏ID {} 不存在，无法更新媒体路径", mediaTask.getGameId());
                return;
            }
            
            String localPath = mediaTask.getLocalPath();
            logger.info("更新游戏 {} 的媒体字段 {} (mediaType={}, gameFieldName={}): {}", 
                game.getName(), gameField, mediaType, mediaTask.getGameFieldName(), localPath);
            
            // 根据字段名设置媒体路径（使用 MediaType 枚举 + 反射替代硬编码 switch-case）
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
                    default -> logger.warn("未知的游戏字段: {}", gameField);
                }
            }
            
            if (!fieldSet) {
                return;
            }
            
            gameMapper.updateGame(game);
            logger.info("更新游戏 {} 的媒体字段 {}: {}", game.getName(), gameField, localPath);
            
        } catch (Exception e) {
            logger.error("更新游戏媒体路径失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 将媒体类型映射到Game表的字段名
     */
    private String mapMediaTypeToGameField(String mediaType) {
        // 1. 精确匹配 nomcourt
        com.gamelist.model.MediaType mt = com.gamelist.model.MediaType.fromNomcourt(mediaType);
        if (mt != null) { return mt.getDbColumn(); }
        // 2. 模糊匹配（忽略大小写和连字符）
        mt = com.gamelist.model.MediaType.fromNomcourtLenient(mediaType);
        if (mt != null) { return mt.getDbColumn(); }
        // 3. 遗留别名回退
        return switch (mediaType.toLowerCase()) {
            case "ssmap" -> "ss";
            case "boxvierge" -> "box_2d";
            case "minicon" -> "thumbnail";
            case "intro" -> "video";
            case "photo", "illustration", "controller" -> "fanart";
            default -> null;
        };
    }

    @Override
    public void stopPlatformDownload(Long platformId) {
        logger.info("停止平台{}的媒体下载（等待当前文件下载完成后停止）", platformId);
        // 设置停止请求，等待当前文件下载完成后停止
        stopRequested.set(true);
        // 设置 isStopped 标志，让工作线程和主线程都能检测到停止请求
        isStopped.set(true);
        // 立即重置 isRunning，允许下次恢复时启动新任务
        // 注意：这可能导致正在下载的任务完成后不再更新状态为COMPLETED，但避免了下次恢复时无法启动的问题
        isRunning.set(false);
        // 将该平台所有待下载的任务标记为停止（正在下载的任务等下载完成后再处理）
        mediaDownloadTaskMapper.updateStatusByPlatformId(platformId, MediaDownloadTask.STATUS_STOPPED);
        logger.info("平台{}的媒体下载停止请求已发送", platformId);
    }

    @Override
    public void resumePlatformDownload(Long platformId) {
        logger.info("恢复平台{}的媒体下载", platformId);
        // 将该平台所有停止的任务恢复为待下载状态
        mediaDownloadTaskMapper.updateStoppedToPendingByPlatformId(platformId);
        // 同时将失败的任务也恢复为待下载状态，以便重试
        mediaDownloadTaskMapper.updateFailedToPendingByPlatformId(platformId);
        
        // 启动下载任务（并发由 ThreadResourceManager 统一管控）
        if (!isRunning.get()) {
            // 获取第一个待下载任务的taskId来启动下载
            MediaDownloadTask pendingTask = mediaDownloadTaskMapper.selectPendingTasksByPlatformId(platformId, 1).stream().findFirst().orElse(null);
            if (pendingTask != null) {
                // 直接使用服务器返回的 maxthreads，并发由 ThreadResourceManager 统一控制
                int maxThreads = scraperService.getUserMaxThreads();
                logger.info("启动平台{}的媒体下载任务，taskId={}, 线程配额={}", platformId, pendingTask.getTaskId(), maxThreads);
                startMediaDownloadTask(pendingTask.getTaskId(), maxThreads);
            }
        }
        
        logger.info("平台{}的媒体下载已恢复（包括失败任务）", platformId);
    }

    @Override
    public void retryFailedDownloads(Long platformId) {
        logger.info("重试平台{}的失败下载任务", platformId);
        mediaDownloadTaskMapper.updateFailedToPendingByPlatformId(platformId);
        logger.info("平台{}的失败下载任务已重置为待下载状态", platformId);
    }

    @Override
    public void stopAllPlatformDownload() {
        logger.info("停止所有平台的媒体下载");
        List<Long> platformIds = mediaDownloadTaskMapper.selectDistinctPlatformIds();
        for (Long platformId : platformIds) {
            stopPlatformDownload(platformId);
        }
        // 同时停止全局下载任务
        stopMediaDownloadTask();
        logger.info("所有平台的媒体下载已停止");
    }

    @Override
    public Map<String, Object> getPlatformDownloadStats(Long platformId) {
        return Map.of(
            "platformId", platformId,
            "total", mediaDownloadTaskMapper.countByPlatformId(platformId),
            "pending", mediaDownloadTaskMapper.countPendingByPlatformId(platformId),
            "downloading", mediaDownloadTaskMapper.countDownloadingByPlatformId(platformId),
            "completed", mediaDownloadTaskMapper.countCompletedByPlatformId(platformId),
            "failed", mediaDownloadTaskMapper.countFailedByPlatformId(platformId),
            "stopped", mediaDownloadTaskMapper.countStoppedByPlatformId(platformId)
        );
    }

    @Override
    public long countDownloadingByPlatformId(Long platformId) {
        return mediaDownloadTaskMapper.countDownloadingByPlatformId(platformId);
    }
}