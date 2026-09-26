package com.gamelist.service;

import com.gamelist.mapper.ScrapeTaskMapper;
import com.gamelist.model.ScrapeTask;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 刮削工作线程池
 * 
 * 核心设计：
 * 1. 使用 ThreadPoolExecutor 支持运行时动态调整线程数
 * 2. 常驻监听线程持续从任务池取活，按优先级分发
 * 3. 优先级：用户刮削(0) > 媒体下载(10) > 街机自动(50)
 * 4. 每 100 个媒体任务主动查询 SS 配额
 */
@Component
public class ScrapeWorkerPool {
    
    private static final Logger logger = LoggerFactory.getLogger(ScrapeWorkerPool.class);
    
    @Autowired
    private ScrapeTaskMapper scrapeTaskMapper;
    
    @Lazy
    @Autowired
    private ScrapeStatus scrapeStatus;
    
    @Lazy
    @Autowired
    private ScraperService scraperService;
    
    @Autowired
    private GameManifestService gameManifestService;
    
    // 使用 ThreadPoolExecutor 支持运行时动态调整线程数
    private ThreadPoolExecutor workerPool;
    
    // 控制标志
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // 媒体任务计数器（每 100 个刷新一次配额）
    private final AtomicLong mediaTaskCounter = new AtomicLong(0);
    
    // 默认线程数
    private static final int DEFAULT_MAX_THREADS = 6;
    
    @PostConstruct
    public void init() {
        // 应用启动时：重置上次残留的 RUNNING 任务 → PENDING
        int resetCount = scrapeTaskMapper.resetRunningToPending();
        if (resetCount > 0) {
            logger.info("启动时重置 {} 个残留的 RUNNING 任务为 PENDING", resetCount);
        }
        
        // 创建可动态调整的线程池
        // core=1（最少保持 1 个线程），max=默认6（SS API 返回后动态调整）
        // 空闲 60s 的线程自动回收
        workerPool = new ThreadPoolExecutor(
            1, DEFAULT_MAX_THREADS, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ScrapeWorkerThreadFactory()
        );
        // 允许核心线程超时回收
        workerPool.allowCoreThreadTimeOut(true);
        
        logger.info("刮削工作线程池已创建，初始最大线程数: {}", DEFAULT_MAX_THREADS);
        
        // 启动常驻监听线程
        startListener();
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭刮削工作线程池...");
        running.set(false);
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("刮削工作线程池已关闭");
    }
    
    /**
     * 动态调整线程数（SS API 返回 maxThreads 变化时调用）
     */
    public synchronized void resize(int newMaxThreads) {
        if (newMaxThreads <= 0) {
            logger.warn("SS 返回 maxThreads=0，不调整线程池（用户可能被禁止使用 API）");
            return;
        }
        
        int oldMax = workerPool.getMaximumPoolSize();
        if (newMaxThreads == oldMax) {
            return;
        }
        
        // 先扩上限再调核心，避免 core > max 异常
        if (newMaxThreads > oldMax) {
            workerPool.setMaximumPoolSize(newMaxThreads);
            workerPool.setCorePoolSize(newMaxThreads);
        } else {
            workerPool.setCorePoolSize(newMaxThreads);
            workerPool.setMaximumPoolSize(newMaxThreads);
        }
        
        logger.info("线程池动态调整: {} → {} 个线程", oldMax, newMaxThreads);
    }
    
    /**
     * 启动常驻监听线程（持续从任务池取活）
     * 使用独立线程，不占用 worker pool 的线程配额
     */
    private void startListener() {
        running.set(true);
        Thread listenerThread = new Thread(this::listenerLoop, "scrape-listener");
        listenerThread.setDaemon(false);
        listenerThread.start();
        logger.info("任务监听线程已启动");
    }
    
    /**
     * 监听线程主循环（带背压控制，避免瞬间 claim 所有任务）
     */
    private void listenerLoop() {
        logger.info("监听线程开始运行");
        
        while (running.get()) {
            try {
                // 暂停检查
                while (paused.get() && running.get()) {
                    Thread.sleep(500);
                }
                
                if (!running.get()) break;
                
                // 背压控制：仅在线程池有空闲容量时才认领新任务
                int queueSize = workerPool.getQueue().size();
                int activeCount = workerPool.getActiveCount();
                int maxPoolSize = workerPool.getMaximumPoolSize();
                int availableSlots = Math.max(0, maxPoolSize - activeCount - queueSize);
                
                if (availableSlots <= 0) {
                    // 线程池已满，等待 1s 后重试
                    Thread.sleep(1000);
                    continue;
                }
                
                // 取任务：ORDER BY priority ASC, order_index ASC
                ScrapeTask task = scrapeTaskMapper.pickNextPendingTask();
                
                if (task == null) {
                    // 任务池空，等待 2s 后重试
                    Thread.sleep(2000);
                    continue;
                }
                
                logger.debug("发现待处理任务: id={}, type={}, priority={}", task.getId(), task.getTaskType(), task.getPriority());
                
                // 乐观锁抢占
                if (scrapeTaskMapper.tryClaimTask(task.getId()) == 0) {
                    // 被其他线程抢走了，继续取下一个
                    logger.debug("任务 {} 被其他线程抢占", task.getId());
                    continue;
                }
                
                logger.info("任务已认领: id={}, type={}, gameId={}, 分发到工作线程池执行", task.getId(), task.getTaskType(), task.getGameId());
                
                // 分发到线程池执行（异步，不阻塞监听线程）
                workerPool.submit(() -> executeTask(task));
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("监听线程异常: {}", e.getMessage(), e);
            }
        }
        
        logger.info("监听线程已退出");
    }
    
    /**
     * 执行单个任务
     */
    private void executeTask(ScrapeTask task) {
        String threadName = "scrape-worker-" + task.getTaskType() + "-" + task.getId();
        Thread.currentThread().setName(threadName);
        
        try {
            if (ScrapeTask.TYPE_GAME_INFO.equals(task.getTaskType())) {
                processGameInfoTask(task);
            } else if (ScrapeTask.TYPE_MEDIA_DOWNLOAD.equals(task.getTaskType())) {
                processMediaDownloadTask(task);
            } else {
                logger.warn("未知任务类型: {}", task.getTaskType());
                scrapeTaskMapper.updateStatusWithError(task.getId(), 
                    ScrapeTask.STATUS_RUNNING, ScrapeTask.STATUS_FAILED, "未知任务类型");
            }
        } catch (Exception e) {
            logger.error("任务执行异常: task={}, error={}", task.getId(), e.getMessage(), e);
            scrapeTaskMapper.updateStatusWithError(task.getId(), 
                ScrapeTask.STATUS_RUNNING, ScrapeTask.STATUS_FAILED, e.getMessage());
        }
    }
    
    /**
     * 处理游戏信息任务（用户刮削 priority=0 / 街机自动 priority=50）
     */
    private void processGameInfoTask(ScrapeTask task) {
        logger.info("处理游戏信息任务: gameId={}, priority={}", task.getGameId(), task.getPriority());
        
        boolean success = scraperService.executeGameInfoTask(task);
        
        if (success) {
            scrapeTaskMapper.updateStatus(task.getId(), 
                ScrapeTask.STATUS_RUNNING, ScrapeTask.STATUS_COMPLETED);
            logger.info("游戏信息任务完成: taskId={}, gameId={}", task.getId(), task.getGameId());
        } else {
            scrapeTaskMapper.updateStatusWithError(task.getId(), 
                ScrapeTask.STATUS_RUNNING, ScrapeTask.STATUS_FAILED, "游戏信息刮削失败");
            logger.warn("游戏信息任务失败: taskId={}, gameId={}", task.getId(), task.getGameId());
        }
    }
    
    /**
     * 处理媒体下载任务（priority=10）
     */
    private void processMediaDownloadTask(ScrapeTask task) {
        logger.info("处理媒体下载任务: gameId={}, mediaType={}", task.getGameId(), task.getMediaType());
        
        boolean success = scraperService.executeMediaDownloadTask(task);
        
        if (success) {
            scrapeTaskMapper.updateStatus(task.getId(), 
                ScrapeTask.STATUS_RUNNING, ScrapeTask.STATUS_COMPLETED);
            logger.info("媒体下载任务完成: taskId={}, gameId={}", task.getId(), task.getGameId());
        } else {
            scrapeTaskMapper.updateStatusWithError(task.getId(), 
                ScrapeTask.STATUS_RUNNING, ScrapeTask.STATUS_FAILED, "媒体下载失败");
            logger.warn("媒体下载任务失败: taskId={}, gameId={}", task.getId(), task.getGameId());
        }
        
        // 每 100 个媒体任务主动查询一次 SS 配额
        long count = mediaTaskCounter.incrementAndGet();
        if (count % 100 == 0) {
            logger.info("已处理 {} 个媒体任务，主动查询 SS 配额信息...", count);
            scrapeStatus.refreshQuotaFromSS();
        }
    }
    
    // ========== 控制方法 ==========
    
    public void pause() {
        paused.set(true);
        logger.info("刮削工作线程池已暂停");
    }
    
    public void resume() {
        paused.set(false);
        logger.info("刮削工作线程池已恢复");
    }
    
    public void stop() {
        paused.set(false);
        // 重置所有 RUNNING 任务为 PENDING
        int resetCount = scrapeTaskMapper.resetRunningToPending();
        logger.info("刮削工作线程池已停止，重置 {} 个任务", resetCount);
    }
    
    /**
     * 用户主动停止刮削：暂停监听 + 清空队列 + 重置 RUNNING 任务
     */
    public void stopAll() {
        paused.set(true);  // 暂停监听线程
        // 取消线程池中排队等待的任务
        int cancelled = workerPool.getQueue().size();
        workerPool.getQueue().clear();
        // 重置 RUNNING 任务为 PENDING
        int resetCount = scrapeTaskMapper.resetRunningToPending();
        logger.info("用户停止刮削：取消 {} 个排队任务，重置 {} 个运行中任务", cancelled, resetCount);
    }
    
    public boolean isPaused() {
        return paused.get();
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * 获取线程池统计信息
     */
    public Map<String, Object> getPoolStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("poolSize", workerPool.getPoolSize());
        stats.put("activeCount", workerPool.getActiveCount());
        stats.put("corePoolSize", workerPool.getCorePoolSize());
        stats.put("maximumPoolSize", workerPool.getMaximumPoolSize());
        stats.put("queueSize", workerPool.getQueue().size());
        stats.put("completedTaskCount", workerPool.getCompletedTaskCount());
        stats.put("taskCount", workerPool.getTaskCount());
        return stats;
    }
    
    /**
     * 自定义线程工厂（便于日志识别）
     */
    private static class ScrapeWorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "scrape-worker-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
