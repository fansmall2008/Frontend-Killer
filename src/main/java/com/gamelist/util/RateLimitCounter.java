package com.gamelist.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 限流计数器工具类
 * 实现10秒内20次访问则休息20秒的限流逻辑
 */
public class RateLimitCounter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitCounter.class);

    private static final int REQUEST_THRESHOLD = 20;
    private static final long TIME_WINDOW_MS = 10000; // 10秒
    private static final long PAUSE_DURATION_MS = 0; // 0秒，测试用

    private final ConcurrentLinkedDeque<Long> requestTimestamps = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private volatile long lastPauseEndTime = 0;

    /**
     * 尝试获取访问权限
     * 如果10秒内访问次数超过阈值，线程将等待20秒
     *
     * @return true表示可以继续访问，false表示被限流（在等待期间调用）
     */
    public boolean tryAcquire() {
        // 检查是否正在暂停中
        if (isPaused.get()) {
            long now = System.currentTimeMillis();
            if (now < lastPauseEndTime) {
                return false;
            } else {
                // 暂停时间已过，重置状态
                isPaused.set(false);
                requestTimestamps.clear();
                logger.info("限流暂停结束，继续处理请求");
            }
        }

        long now = System.currentTimeMillis();

        // 清理过期的时间戳（超过10秒的）
        while (!requestTimestamps.isEmpty() && now - requestTimestamps.peekFirst() > TIME_WINDOW_MS) {
            requestTimestamps.pollFirst();
        }

        // 检查当前窗口内的请求数
        if (requestTimestamps.size() >= REQUEST_THRESHOLD) {
            logger.warn("10秒内出现{}次访问，需要休息20秒", REQUEST_THRESHOLD);
            isPaused.set(true);
            lastPauseEndTime = now + PAUSE_DURATION_MS;

            // 在单独的线程中等待，避免阻塞调用线程
            Thread pauseThread = new Thread(() -> {
                try {
                    Thread.sleep(PAUSE_DURATION_MS);
                    isPaused.set(false);
                    requestTimestamps.clear();
                    logger.info("限流暂停结束，继续处理请求");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("限流暂停线程被中断");
                }
            });
            pauseThread.setDaemon(true);
            pauseThread.start();

            return false;
        }

        // 记录当前请求时间戳
        requestTimestamps.addLast(now);
        return true;
    }

    /**
     * 获取当前窗口内的请求数
     *
     * @return 当前窗口内的请求数
     */
    public int getCurrentRequestCount() {
        long now = System.currentTimeMillis();
        // 清理过期的时间戳
        while (!requestTimestamps.isEmpty() && now - requestTimestamps.peekFirst() > TIME_WINDOW_MS) {
            requestTimestamps.pollFirst();
        }
        return requestTimestamps.size();
    }

    /**
     * 检查是否正在暂停中
     *
     * @return true表示正在暂停，false表示正常
     */
    public boolean isPaused() {
        if (!isPaused.get()) {
            return false;
        }
        // 检查暂停是否已过期
        long now = System.currentTimeMillis();
        if (now >= lastPauseEndTime) {
            isPaused.set(false);
            return false;
        }
        return true;
    }

    /**
     * 获取剩余暂停时间（毫秒）
     *
     * @return 剩余暂停时间，如果没有暂停则返回0
     */
    public long getRemainingPauseTime() {
        if (!isPaused.get()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (now >= lastPauseEndTime) {
            isPaused.set(false);
            return 0;
        }
        return lastPauseEndTime - now;
    }

    /**
     * 重置计数器
     */
    public void reset() {
        requestTimestamps.clear();
        isPaused.set(false);
        lastPauseEndTime = 0;
    }
}