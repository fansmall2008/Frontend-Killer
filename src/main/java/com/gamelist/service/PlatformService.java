package com.gamelist.service;

import java.util.List;
import java.util.Map;

import com.gamelist.model.Platform;
import com.gamelist.model.TempSubset;
import com.gamelist.model.TempSubsetGame;
import com.gamelist.xml.GameListXml;

public interface PlatformService {
    Platform savePlatform(GameListXml.Provider providerXml);
    Platform savePlatform(GameListXml.Provider providerXml, String defaultSystemName);
    Platform getPlatformBySystem(String system);
    List<Platform> getAllPlatforms();
    Platform getPlatformById(Long id);
    Platform updatePlatform(Platform platform);
    void deletePlatform(Long id);
    int addPlatform(Platform platform);
    Platform createPlatformWithGames(String name, java.util.Map<String, Object> filterParams);
    Map<String, Object> mergePlatforms(Long primaryPlatformId, Long secondaryPlatformId);
    Map<String, Object> mergePlatforms(List<Long> sourcePlatformIds, String newPlatformName, String newPlatformFolderPath, Boolean overwrite);
    int getGameCountByPlatformId(Long platformId);
    
    // 临时子集相关方法
    TempSubset createTempSubset(String name, String description, java.util.Map<String, Object> filterParams);
    List<TempSubset> getAllTempSubsets();
    TempSubset getTempSubsetById(Long id);
    List<TempSubsetGame> getTempSubsetGamesBySubsetId(Long subsetId);
    TempSubsetGame getTempSubsetGameById(Long id);
    TempSubsetGame getTempSubsetGameByOriginalGameId(Long originalGameId);
    int updateTempSubsetGame(TempSubsetGame tempSubsetGame);
    int syncTempSubsetToMainSet(Long subsetId);
    Platform createPlatformFromTempSubset(Long subsetId, String platformName);
    int deleteTempSubset(Long subsetId);
    Map<String, Object> getPlatformStatistics(Long platformId);
}
