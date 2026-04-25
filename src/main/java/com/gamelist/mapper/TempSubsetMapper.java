package com.gamelist.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.gamelist.model.TempSubset;
import com.gamelist.model.TempSubsetGame;

@Mapper
public interface TempSubsetMapper {
    int insertTempSubset(TempSubset tempSubset);
    TempSubset selectTempSubsetById(Long id);
    List<TempSubset> selectAllTempSubsets();
    int updateTempSubset(TempSubset tempSubset);
    int deleteTempSubset(Long id);
    
    int insertTempSubsetGame(TempSubsetGame tempSubsetGame);
    int insertTempSubsetGamesBatch(List<TempSubsetGame> tempSubsetGames);
    List<TempSubsetGame> selectTempSubsetGamesBySubsetId(Long subsetId);
    TempSubsetGame selectTempSubsetGameById(Long id);
    TempSubsetGame selectTempSubsetGameByOriginalGameId(Long originalGameId);
    int updateTempSubsetGame(TempSubsetGame tempSubsetGame);
    int deleteTempSubsetGame(Long id);
    int deleteTempSubsetGamesBySubsetId(Long subsetId);
}