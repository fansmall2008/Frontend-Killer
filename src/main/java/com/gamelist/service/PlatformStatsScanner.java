package com.gamelist.service;

import com.gamelist.model.PlatformStatsCache;

/**
 * 平台体量统计扫描服务
 * <p>
 * 按需扫描指定平台下所有游戏的 ROM 文件和媒体文件，
 * 统计数量、总大小、缺失数，并写入 platform_stats_cache 表。
 * <p>
 * 扫描为异步任务，通过 BackgroundTask 上报进度。
 */
public interface PlatformStatsScanner {

    /**
     * 异步触发平台扫描。
     *
     * @param platformId 平台 ID
     * @return 创建的 BackgroundTask ID；若已有相同平台的扫描任务在运行则返回该任务 ID
     */
    Long scanAsync(Long platformId);

    /**
     * 获取指定平台的缓存统计（不触发扫描）。
     *
     * @param platformId 平台 ID
     * @return 缓存对象；若从未扫描过返回 null
     */
    PlatformStatsCache getCache(Long platformId);

    /**
     * 判断指定平台当前是否有扫描任务正在运行。
     */
    boolean isScanning(Long platformId);
}
