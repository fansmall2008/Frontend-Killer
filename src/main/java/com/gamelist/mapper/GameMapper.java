package com.gamelist.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

import com.gamelist.model.Game;
import com.gamelist.model.PlatformStatistics;

@Mapper
public interface GameMapper {
    int insertGame(Game game);
    int insertGamesBatch(List<Game> games);
    List<Game> selectAllGames();
    List<Game> selectAllGamesWithFilter(String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players);
    List<Game> selectAllGamesWithFilter(String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses);
    List<Game> selectGamesByPlatformId(Long platformId);
    List<Game> selectGamesByPlatformIdWithFilter(Long platformId, String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players);
    List<Game> selectGamesByPlatformIdWithFilter(Long platformId, String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses);
    Game selectGameById(Long id);
    int deleteAllGames();
    
    // 统计相关方法
    long countTotalGames();
    long countFullyScrapedGames();
    long countPartiallyScrapedGames();
    long countNotScrapedGames();
    List<PlatformStatistics> selectPlatformStatistics();
    List<Game> selectGamesByScrapeStatus(Long platformId, String status, int offset, int limit);
    int updateGame(Game game);
    int deleteGamesByPlatformId(Long platformId);
    Game selectGameByPath(String path);
    
    // 游戏筛选相关方法
    List<Game> selectGamesByFilter(Map<String, Object> filterParams);
    long countGamesByFilter(Map<String, Object> filterParams);
    
    // 查询所有游戏路径
    List<String> selectAllGamePaths();
    
    // 批量查询已存在的游戏路径
    List<String> selectExistingGamePaths(List<String> paths);
    
    // 平台合并相关方法
    Game selectGameByGameIdAndPlatformId(String gameId, Long platformId);
    List<Game> selectGamesByGameId(String gameId);
    List<Game> selectGamesByFileName(String fileName);
    List<Game> selectGamesByFileNameAndPlatformId(String fileName, Long platformId);
    List<Game> selectGamesByNameAndFileName(String name, String fileName, Long platformId);
    List<Game> selectGamesByNameAndPlatformId(String name, Long platformId);
    List<Game> selectGamesByGameIdAndPlatformId(String gameId, Long platformId);
    List<Game> selectGamesByCrc32AndPlatformId(String crc32, Long platformId);
    List<Game> selectGamesByExactFileNameAndPlatformId(String fileName, Long platformId);
    List<Game> selectGamesByNameIgnoreCaseAndPlatformId(String name, Long platformId);
    
    // 获取平台游戏数量
    int countGamesByPlatformId(Long platformId);
    
    // 获取唯一值
    List<String> selectUniqueDevelopers();
    List<String> selectUniquePublishers();
    List<String> selectUniqueGenres();
    List<String> selectUniquePlayers();
    
    // 删除游戏
    int deleteGameById(Long id);
    
    // 更新平台下所有游戏的PLATFORM_PATH
    int updatePlatformPathForAllGames(Long platformId, String platformPath);
}
