package com.gamelist.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamelist.model.Game;
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
     * 查询 SS 用户信息（ssuserInfos.php）
     * 用于主动刷新配额信息
     * @return 完整的用户信息 JSON 节点
     */
    JsonNode fetchUserInfosFromSS();
    
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
}