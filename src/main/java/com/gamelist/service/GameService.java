package com.gamelist.service;

import java.util.List;
import java.util.Map;

import com.gamelist.model.Game;
import com.gamelist.model.ImportStatistics;
import com.gamelist.model.PlatformStatistics;
import com.gamelist.model.ScanResult;
import com.gamelist.model.Statistics;
import com.gamelist.xml.GameListXml;

public interface GameService {
    void importGamesFromXml(GameListXml gameListXml);
    void importGamesFromXml(GameListXml gameListXml, String defaultPlatformName);
    void importGamesFromXml(GameListXml gameListXml, String defaultPlatformName, String gameListFilePath);
    ImportStatistics importGamesFromXml(String filePath);
    ImportStatistics importGamesFromXml(String filePath, boolean metadataOnly, int threadCount);
    ImportStatistics importGamesFromXml(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount);
    ImportStatistics importGamesFromXml(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount, Long scraperSystemId);
    ImportStatistics importGamesFromXml(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount, Long scraperSystemId, boolean enableMediaDiscovery);
    ImportStatistics importGamesFromXml(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount, Long scraperSystemId, boolean enableMediaDiscovery, Map<String, String> templateVariables);

    ImportStatistics importGamesFromPegasusMetadata(String filePath);
    ImportStatistics importGamesFromPegasusMetadata(String filePath, boolean metadataOnly, int threadCount);
    ImportStatistics importGamesFromPegasusMetadata(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount);
    ImportStatistics importGamesFromPegasusMetadata(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount, Long scraperSystemId);
    ImportStatistics importGamesFromPegasusMetadata(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount, Long scraperSystemId, boolean enableMediaDiscovery);
    ImportStatistics importGamesFromPegasusMetadata(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount, Long scraperSystemId, boolean enableMediaDiscovery, Long taskId);
    ImportStatistics importGamesFromPegasusMetadata(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount, Long scraperSystemId, boolean enableMediaDiscovery, Long taskId, Map<String, String> templateVariables);
    
        /**
         * 按 v3 模板导入任意 data 型数据文件（如 RetroArch .lpl），
         * 复用通用 v3 管线（表头/系统字段映射、gameId/absolutePath/exists 补全、批量入库）。
         */
        ImportStatistics importGamesFromTemplate(String filePath, String importTemplate, int threadCount, Long scraperSystemId, boolean enableMediaDiscovery);
        ImportStatistics importGamesFromTemplate(String filePath, String importTemplate, int threadCount, Long scraperSystemId, boolean enableMediaDiscovery, Map<String, String> templateVariables);
    
        /**
         * 根据文件名匹配 v3 导入模板：扫描 rules/import/*.json，
         * 返回 templateInfo.dataFile 模式（如 "*.lpl"）匹配的模板文件名，无匹配返回 null。
         */
        String findImportTemplateForFile(String fileName);

    ImportStatistics importGamesFromFileScan(String scanPath, String fileExtensions, String importTemplate, int threadCount, Long taskId);
    ImportStatistics importGamesFromFileScan(String scanPath, String fileExtensions, String importTemplate, int threadCount, Long taskId, Long scraperSystemId);
    ImportStatistics importGamesFromFileScan(String scanPath, String fileExtensions, String importTemplate, int threadCount, Long taskId, Long scraperSystemId, List<Integer> levels);
    ImportStatistics importGamesFromFileScan(String scanPath, String fileExtensions, String importTemplate, int threadCount, Long taskId, Long scraperSystemId, List<Integer> levels, boolean enableMediaDiscovery);
    ScanResult scanAndImportGames(String rootPath);
    ScanResult scanAndImportGames(String rootPath, int scanDepth);
    ScanResult scanAndImportPegasusMetadata(String rootPath);
    ScanResult scanAndImportPegasusMetadata(String rootPath, int scanDepth);
    int saveGame(Game game);
    int saveGamesBatch(List<Game> games);
    List<Game> getAllGames();
    List<Game> getAllGames(String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses);
    List<Game> getAllGames(String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses, String folderPath);
    List<Game> getAllGames(String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses, String folderPath, List<String> publishers);
    List<Game> getGamesByPlatformId(Long platformId);
    List<Game> getGamesByPlatformId(Long platformId, String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses);
    List<Game> getGamesByPlatformId(Long platformId, String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses, List<String> fileStatuses);
    List<Game> getGamesByPlatformId(Long platformId, String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses, List<String> fileStatuses, String folderPath);
    List<Game> getGamesByPlatformId(Long platformId, String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses, List<String> fileStatuses, String folderPath, List<String> publishers);
    
    /**
     * 获取检索筛选选项：开发商、发行商、游戏类型（两级树）。platformId 为 null 或 0 时返回全库范围。
     */
    Map<String, Object> getFilterOptions(Long platformId);
    Game getGameById(Long id);
    int deleteAllGames();
    Statistics getOverallStatistics();
    List<PlatformStatistics> getPlatformStatistics();
    PlatformStatistics getPlatformStatisticsById(Long platformId);
    List<Game> getGamesByScrapeStatus(Long platformId, String status, int page, int size);
    int updateGame(Game game);
    List<Game> getGamesByFilter(Map<String, Object> filterParams);
    long getGamesCountByFilter(Map<String, Object> filterParams);
    Map<String, List<String>> getUniqueGameValues();
    Map<String, Object> mergeDiscs(List<?> gameIds, String mergeName);
    int batchUpdateGames(List<Long> gameIds, Map<String, Object> updates);
    int migrateGames(List<Long> gameIds, Long targetPlatformId);
    int addGame(Game game);
    int batchDeleteGames(List<?> gameIds);
    int swapTranslations(List<Long> gameIds);
    int swapTranslationsByPlatformId(Long platformId);
}