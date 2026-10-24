package com.gamelist.service;

import java.util.List;
import java.util.Map;

import com.gamelist.model.Game;
import com.gamelist.model.MediaDownloadTask;
import com.gamelist.model.ScrapeTask;
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

    /**
     * 从 manifest 缓存回填缺失元数据（genre/genreid/releasedate），零 SS 请求
     * @return 回填统计（updated/alreadyOk/noManifest/failed）
     */
    Map<String, Object> backfillMetadataFromManifest();
    
    /**
     * 批量创建游戏信息刮削任务（写入 scrape_task 表）
     * @param games 游戏列表
     * @param request 刮削请求参数
     * @param systemId SS 系统 ID
     * @param taskId 后台任务 ID
     * @param priority 优先级
     */
    void enqueueGameInfoTasks(List<Game> games, ScraperRequest request, Integer systemId, Long taskId, int priority);
    
    /**
     * 执行游戏信息刮削任务（由 ScrapeWorkerPool 调用）
     * @param task 刮削任务
     * @return 是否成功
     */
    boolean executeGameInfoTask(ScrapeTask task);
    
    /**
     * 执行媒体下载任务（由 ScrapeWorkerPool 调用）
     * @param task 刮削任务
     * @return 是否成功
     */
    boolean executeMediaDownloadTask(ScrapeTask task);
    
    /**
     * 执行媒体下载任务（MediaDownloadTask 版本，由 ScrapeWorkerPool 调用）
     * @param task 媒体下载任务
     * @return 是否成功
     */
    boolean executeMediaDownloadTask(MediaDownloadTask task);

    /**
     * 从 SS 候选中应用指定游戏元数据到本地游戏，并确认匹配 (matched=true).
     * @param gameId 本地游戏 ID
     * @param ssGameId SS 游戏id (字符串，避免前端精度丢失)
     * @return 操作结果 (success / message)
     */
    Map<String, Object> applySsCandidate(Long gameId, String ssGameId);

    /**
     * 获取所有平台的实时进度（用于媒体下载页面显示）
     * @return 包含 platforms 列表、globalPhase、isPaused、maxThreads 的 Map
     */
    Map<String, Object> getPlatformProgress();

    /**
     * 暂停所有平台的信息刮削和媒体下载
     */
    void pauseAll();

    /**
     * 恢复所有平台的信息刮削和媒体下载
     */
    void resumeAll();

    /**
     * 停止所有平台的信息刮削和媒体下载
     */
    void stopAll();

    /**
     * 暂停指定平台的信息刮削和媒体下载
     * @param platformId 平台ID
     */
    void pausePlatform(Long platformId);

    /**
     * 恢复指定平台的信息刮削和媒体下载
     * @param platformId 平台ID
     */
    void resumePlatform(Long platformId);

    /**
     * 停止指定平台的信息刮削和媒体下载
     * @param platformId 平台ID
     */
    void stopPlatform(Long platformId);
}