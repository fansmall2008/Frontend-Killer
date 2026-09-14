package com.gamelist.service;

import java.util.List;
import java.util.Map;

import com.gamelist.model.ScraperRequest;

public interface ScraperService {

    Map<String, Object> startScraping(ScraperRequest request);

    Map<String, Object> getTaskStatus(Long taskId);

    int getUserMaxThreads();
    
    /**
     * 获取当前刮削状态
     * @return 包含isRunning, scrapedCount, totalCount的Map
     */
    Map<String, Object> getStatus();

    /**
     * 暂停刮削任务
     */
    void pauseScraping();

    /**
     * 恢复刮削任务
     */
    void resumeScraping();

    /**
     * 停止刮削任务
     */
    void stopScraping();

    /**
     * 检查是否正在刮削
     */
    boolean isScraping();

    /**
     * 检查是否已暂停
     */
    boolean isScrapingPaused();

    /**
     * 搜索游戏（使用ScreenScraper的jeuRecherche接口）
     * @param platformId 平台ID
     * @param searchTerm 搜索关键词
     * @return 游戏列表
     */
    List<Map<String, Object>> searchGame(Long platformId, String searchTerm);

    /**
     * 下载单个游戏的媒体文件
     * @param gameId 游戏ID
     * @param gameName 游戏名称
     * @param platformId 平台ID
     * @param medias 媒体文件信息
     */
    void downloadGameMedia(Long gameId, String gameName, Long platformId, Map<String, Object> medias);

    /**
     * 将游戏媒体下载任务加入队列（不立即开始下载）
     * @param gameId 游戏ID
     * @param gameName 游戏名称
     * @param platformId 平台ID
     * @param medias 媒体文件信息
     * @return 创建的任务数量
     */
    int enqueueGameMedia(Long gameId, String gameName, Long platformId, Map<String, Object> medias);
}