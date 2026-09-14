package com.gamelist.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 线程资源管理器
 * 
 * 核心功能：
 * 1. 动态分配线程资源，确保总并发数不超过限制
 * 2. 游戏信息刮削线程优先级高于媒体下载线程
 * 3. 当只有一种线程类型时，分配全部资源
 * 4. 当两种线程类型同时存在时，优先给游戏信息线程
 * 
 * 关键保证：
 * - 占用归还机制必须及时且准确
 * - 使用 try-finally 确保资源一定会归还
 * - 添加超时机制避免死锁
 */
@Component
public class ThreadResourceManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ThreadResourceManager.class);
    
    // 最大线程数（动态配置）
    private volatile int maxThreads = 4;
    
    // 当前可用的线程数
    private final AtomicInteger availableThreads = new AtomicInteger(4);
    
    // 游戏信息线程活跃数
    private final AtomicInteger gameInfoActive = new AtomicInteger(0);
    
    // 媒体下载线程活跃数
    private final AtomicInteger mediaActive = new AtomicInteger(0);
    
    // 等待游戏信息资源的线程数
    private final AtomicInteger gameInfoWaiting = new AtomicInteger(0);
    
    // 等待媒体资源的线程数
    private final AtomicInteger mediaWaiting = new AtomicInteger(0);
    
    // ========== ScreenScraper 配额信息（从 API 响应动态更新） ==========
    private volatile int requestsToday = 0;           // 今日已用请求数
    private volatile int maxRequestsPerDay = 0;       // 每日请求上限
    private volatile int maxRequestsPerMin = 0;       // 每分钟请求上限
    private volatile int maxDownloadSpeed = 0;        // 最大下载速度
    private volatile int requestsKoToday = 0;         // 今日失败请求数
    private volatile String userNiveau = "";          // 用户等级
    private volatile String userContribution = "";    // 贡献等级
    
    // 锁保护状态变更
    private final ReentrantLock lock = new ReentrantLock();
    
    // 游戏信息线程等待条件
    private final Condition gameInfoCondition = lock.newCondition();
    
    // 媒体线程等待条件
    private final Condition mediaCondition = lock.newCondition();
    
    // 默认超时时间（毫秒）
    private static final long DEFAULT_TIMEOUT_MS = 30000;
    
    /**
     * 更新最大线程数配置
     * 应该在刮削任务开始时调用
     */
    public void updateMaxThreads(int newMaxThreads) {
        lock.lock();
        try {
            int oldMaxThreads = this.maxThreads;
            // 允许 0：服务器返回 0 表示当前用户状态不允许刮削
            this.maxThreads = Math.max(0, newMaxThreads);
            
            // 计算可用线程数的变化
            int delta = this.maxThreads - oldMaxThreads;
            if (delta > 0) {
                // 增加了线程数，更新可用线程数
                availableThreads.addAndGet(delta);
                logger.info("最大线程数更新: {} -> {}, 可用线程数: {}", 
                    oldMaxThreads, this.maxThreads, availableThreads.get());
                
                // 通知等待的线程
                if (gameInfoWaiting.get() > 0) {
                    gameInfoCondition.signalAll();
                }
                if (mediaWaiting.get() > 0) {
                    mediaCondition.signalAll();
                }
            } else if (delta < 0) {
                // 减少了线程数，需要谨慎处理
                // 不立即减少可用线程数，让自然归还来调整
                logger.info("最大线程数更新: {} -> {}, 当前活跃线程将在归还时调整", 
                    oldMaxThreads, this.maxThreads);
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 从 ScreenScraper API 响应中动态更新线程配额
     * 每次解析到 ssuser.maxthreads 时调用，实现服务器驱动的动态调整
     * 
     * @param serverMaxThreads 服务器返回的 maxthreads 值
     */
    public void updateFromServerResponse(int serverMaxThreads) {
        int effective = Math.max(0, serverMaxThreads);
        if (effective != this.maxThreads) {
            logger.info("服务器动态更新线程配额: {} -> {}", this.maxThreads, effective);
        }
        updateMaxThreads(effective);
    }
    
    /**
     * 从 ScreenScraper API 响应中更新完整配额信息
     * 每次调用 jeuInfos.php 或 ssuserInfos.php 时调用
     */
    public void updateQuotaFromServer(int requestsToday, int maxRequestsPerDay, 
                                       int maxRequestsPerMin, int maxDownloadSpeed,
                                       int requestsKoToday, String niveau, String contribution) {
        this.requestsToday = requestsToday;
        this.maxRequestsPerDay = maxRequestsPerDay;
        this.maxRequestsPerMin = maxRequestsPerMin;
        this.maxDownloadSpeed = maxDownloadSpeed;
        this.requestsKoToday = requestsKoToday;
        this.userNiveau = niveau != null ? niveau : "";
        this.userContribution = contribution != null ? contribution : "";
        
        logger.debug("配额信息更新: 今日请求={}/{}, 用户等级={}, 贡献={}", 
            requestsToday, maxRequestsPerDay, niveau, contribution);
    }
    
    /**
     * 获取最大线程数
     */
    public int getMaxThreads() {
        return maxThreads;
    }
    
    /**
     * 游戏信息线程请求资源（高优先级）
     * 
     * @return true 表示成功获取资源，false 表示超时或被中断
     */
    public boolean acquireForGameInfo() {
        return acquireForGameInfo(DEFAULT_TIMEOUT_MS);
    }
    
    /**
     * 游戏信息线程请求资源（高优先级，带超时）
     */
    public boolean acquireForGameInfo(long timeoutMs) {
        lock.lock();
        try {
            gameInfoWaiting.incrementAndGet();
            long startTime = System.currentTimeMillis();
            
            while (availableThreads.get() <= 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= timeoutMs) {
                    gameInfoWaiting.decrementAndGet();
                    logger.warn("游戏信息线程获取资源超时: {}ms, 可用: {}, 活跃游戏: {}, 活跃媒体: {}", 
                        timeoutMs, availableThreads.get(), gameInfoActive.get(), mediaActive.get());
                    return false;
                }
                
                try {
                    boolean signaled = gameInfoCondition.await(timeoutMs - elapsed, TimeUnit.MILLISECONDS);
                    if (!signaled) {
                        // 超时
                        gameInfoWaiting.decrementAndGet();
                        logger.warn("游戏信息线程获取资源超时");
                        return false;
                    }
                } catch (InterruptedException e) {
                    gameInfoWaiting.decrementAndGet();
                    Thread.currentThread().interrupt();
                    logger.warn("游戏信息线程获取资源被中断");
                    return false;
                }
            }
            
            gameInfoWaiting.decrementAndGet();
            availableThreads.decrementAndGet();
            int active = gameInfoActive.incrementAndGet();
            
            logger.info("游戏信息线程获取资源成功: 活跃游戏={}, 活跃媒体={}, 可用={}", 
                active, mediaActive.get(), availableThreads.get());
            
            return true;
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 媒体下载线程请求资源（低优先级）
     * 
     * @return true 表示成功获取资源，false 表示超时或被中断
     */
    public boolean acquireForMedia() {
        return acquireForMedia(DEFAULT_TIMEOUT_MS);
    }
    
    /**
     * 媒体下载线程请求资源（低优先级，带超时）
     * 
     * 关键逻辑：
     * - 如果有游戏信息线程在等待，媒体线程需要让出资源
     * - 只有在没有游戏信息线程等待时，媒体线程才能获取资源
     */
    public boolean acquireForMedia(long timeoutMs) {
        lock.lock();
        try {
            mediaWaiting.incrementAndGet();
            long startTime = System.currentTimeMillis();
            
            while (true) {
                // 检查超时
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= timeoutMs) {
                    mediaWaiting.decrementAndGet();
                    logger.warn("媒体线程获取资源超时: {}ms", timeoutMs);
                    return false;
                }
                
                // 关键判断：如果有游戏信息线程在等待，媒体线程需要让出
                if (gameInfoWaiting.get() > 0) {
                    logger.debug("媒体线程让出资源，有 {} 个游戏信息线程在等待", gameInfoWaiting.get());
                    try {
                        boolean signaled = mediaCondition.await(timeoutMs - elapsed, TimeUnit.MILLISECONDS);
                        if (!signaled) {
                            mediaWaiting.decrementAndGet();
                            logger.warn("媒体线程获取资源超时（让出资源后）");
                            return false;
                        }
                    } catch (InterruptedException e) {
                        mediaWaiting.decrementAndGet();
                        Thread.currentThread().interrupt();
                        logger.warn("媒体线程获取资源被中断");
                        return false;
                    }
                    continue;
                }
                
                // 检查是否有可用资源
                if (availableThreads.get() <= 0) {
                    try {
                        boolean signaled = mediaCondition.await(timeoutMs - elapsed, TimeUnit.MILLISECONDS);
                        if (!signaled) {
                            mediaWaiting.decrementAndGet();
                            logger.warn("媒体线程获取资源超时（无可用资源）");
                            return false;
                        }
                    } catch (InterruptedException e) {
                        mediaWaiting.decrementAndGet();
                        Thread.currentThread().interrupt();
                        logger.warn("媒体线程获取资源被中断");
                        return false;
                    }
                    continue;
                }
                
                // 成功获取资源
                break;
            }
            
            mediaWaiting.decrementAndGet();
            availableThreads.decrementAndGet();
            int active = mediaActive.incrementAndGet();
            
            logger.info("媒体线程获取资源成功: 活跃游戏={}, 活跃媒体={}, 可用={}", 
                gameInfoActive.get(), active, availableThreads.get());
            
            return true;
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 游戏信息线程归还资源
     * 必须在 try-finally 中调用确保资源归还
     */
    public void releaseForGameInfo() {
        lock.lock();
        try {
            int active = gameInfoActive.decrementAndGet();
            int available = availableThreads.incrementAndGet();
            
            logger.info("游戏信息线程归还资源: 活跃游戏={}, 活跃媒体={}, 可用={}", 
                active, mediaActive.get(), available);
            
            // 通知等待的线程
            // 优先通知游戏信息线程
            if (gameInfoWaiting.get() > 0) {
                gameInfoCondition.signal();
            } else if (mediaWaiting.get() > 0) {
                mediaCondition.signal();
            }
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 媒体下载线程归还资源
     * 必须在 try-finally 中调用确保资源归还
     */
    public void releaseForMedia() {
        lock.lock();
        try {
            int active = mediaActive.decrementAndGet();
            int available = availableThreads.incrementAndGet();
            
            logger.info("媒体线程归还资源: 活跃游戏={}, 活跃媒体={}, 可用={}", 
                gameInfoActive.get(), active, available);
            
            // 通知等待的线程
            // 优先通知游戏信息线程
            if (gameInfoWaiting.get() > 0) {
                gameInfoCondition.signal();
            } else if (mediaWaiting.get() > 0) {
                mediaCondition.signal();
            }
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 获取当前状态快照
     * 用于日志和监控
     */
    public ResourceSnapshot getSnapshot() {
        lock.lock();
        try {
            return new ResourceSnapshot(
                maxThreads,
                availableThreads.get(),
                gameInfoActive.get(),
                mediaActive.get(),
                gameInfoWaiting.get(),
                mediaWaiting.get(),
                requestsToday,
                maxRequestsPerDay,
                maxRequestsPerMin,
                maxDownloadSpeed,
                requestsKoToday,
                userNiveau,
                userContribution
            );
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 重置资源管理器状态
     * 应该在刮削任务结束时调用
     */
    public void reset() {
        lock.lock();
        try {
            // 检查是否有未归还的资源
            int activeGame = gameInfoActive.get();
            int activeMedia = mediaActive.get();
            
            if (activeGame > 0 || activeMedia > 0) {
                logger.warn("重置资源管理器时仍有活跃线程: 游戏={}, 媒体={}", activeGame, activeMedia);
            }
            
            // 重置状态
            availableThreads.set(maxThreads);
            gameInfoActive.set(0);
            mediaActive.set(0);
            gameInfoWaiting.set(0);
            mediaWaiting.set(0);
            
            logger.info("资源管理器已重置: maxThreads={}", maxThreads);
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 资源状态快照
     */
    public static class ResourceSnapshot {
        public final int maxThreads;
        public final int availableThreads;
        public final int gameInfoActive;
        public final int mediaActive;
        public final int gameInfoWaiting;
        public final int mediaWaiting;
        // 配额信息
        public final int requestsToday;
        public final int maxRequestsPerDay;
        public final int maxRequestsPerMin;
        public final int maxDownloadSpeed;
        public final int requestsKoToday;
        public final String userNiveau;
        public final String userContribution;
        
        public ResourceSnapshot(int maxThreads, int availableThreads, 
                               int gameInfoActive, int mediaActive,
                               int gameInfoWaiting, int mediaWaiting,
                               int requestsToday, int maxRequestsPerDay,
                               int maxRequestsPerMin, int maxDownloadSpeed,
                               int requestsKoToday, String userNiveau, String userContribution) {
            this.maxThreads = maxThreads;
            this.availableThreads = availableThreads;
            this.gameInfoActive = gameInfoActive;
            this.mediaActive = mediaActive;
            this.gameInfoWaiting = gameInfoWaiting;
            this.mediaWaiting = mediaWaiting;
            this.requestsToday = requestsToday;
            this.maxRequestsPerDay = maxRequestsPerDay;
            this.maxRequestsPerMin = maxRequestsPerMin;
            this.maxDownloadSpeed = maxDownloadSpeed;
            this.requestsKoToday = requestsKoToday;
            this.userNiveau = userNiveau;
            this.userContribution = userContribution;
        }
        
        @Override
        public String toString() {
            return String.format("ResourceSnapshot[max=%d, available=%d, gameInfo=%d, media=%d, gameWaiting=%d, mediaWaiting=%d, requests=%d/%d]",
                maxThreads, availableThreads, gameInfoActive, mediaActive, gameInfoWaiting, mediaWaiting, requestsToday, maxRequestsPerDay);
        }
    }
    
    // ========== 配额信息 Getter ==========
    public int getRequestsToday() { return requestsToday; }
    public int getMaxRequestsPerDay() { return maxRequestsPerDay; }
    public int getMaxRequestsPerMin() { return maxRequestsPerMin; }
    public int getMaxDownloadSpeed() { return maxDownloadSpeed; }
    public int getRequestsKoToday() { return requestsKoToday; }
    public String getUserNiveau() { return userNiveau; }
    public String getUserContribution() { return userContribution; }
}
