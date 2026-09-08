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

    ImportStatistics importGamesFromPegasusMetadata(String filePath);
    ImportStatistics importGamesFromPegasusMetadata(String filePath, boolean metadataOnly, int threadCount);
    ImportStatistics importGamesFromPegasusMetadata(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount);
    ImportStatistics importGamesFromPegasusMetadata(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount, Long scraperSystemId);

    ImportStatistics importGamesFromLplFile(String filePath);
    ImportStatistics importGamesFromLplFile(String filePath, boolean metadataOnly, int threadCount);
    ImportStatistics importGamesFromLplFile(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount);
    ImportStatistics importGamesFromLplFile(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount, Long scraperSystemId);

    ImportStatistics importGamesFromFileScan(String scanPath, String fileExtensions, String importTemplate, int threadCount, Long taskId);
    ImportStatistics importGamesFromFileScan(String scanPath, String fileExtensions, String importTemplate, int threadCount, Long taskId, Long scraperSystemId);
    ScanResult scanAndImportGames(String rootPath);
    ScanResult scanAndImportGames(String rootPath, int scanDepth);
    ScanResult scanAndImportPegasusMetadata(String rootPath);
    ScanResult scanAndImportPegasusMetadata(String rootPath, int scanDepth);
    int saveGame(Game game);
    int saveGamesBatch(List<Game> games);
    List<Game> getAllGames();
    List<Game> getAllGames(String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses);
    List<Game> getAllGames(String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses, String folderPath);
    List<Game> getGamesByPlatformId(Long platformId);
    List<Game> getGamesByPlatformId(Long platformId, String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses);
    List<Game> getGamesByPlatformId(Long platformId, String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses, List<String> fileStatuses);
    List<Game> getGamesByPlatformId(Long platformId, String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses, List<String> fileStatuses, String folderPath);
    Game getGameById(Long id);
    int deleteAllGames();
    Statistics getOverallStatistics();
    List<PlatformStatistics> getPlatformStatistics();
    List<Game> getGamesByScrapeStatus(Long platformId, String status, int page, int size);
    int updateGame(Game game);
    List<Game> getGamesByFilter(Map<String, Object> filterParams);
    long getGamesCountByFilter(Map<String, Object> filterParams);
    Map<String, List<String>> getUniqueGameValues();
    Map<String, Object> mergeDiscs(List<?> gameIds);
    int batchUpdateGames(List<Long> gameIds, Map<String, Object> updates);
    int migrateGames(List<Long> gameIds, Long targetPlatformId);
    int addGame(Game game);
    int batchDeleteGames(List<?> gameIds);
}