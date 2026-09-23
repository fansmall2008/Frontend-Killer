package com.gamelist.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.gamelist.model.GameManifest;

/**
 * 游戏信息缓存（SS manifest）Mapper
 */
@Mapper
public interface GameManifestMapper {

    /** 按 SS 全局游戏ID 查询缓存（命中判断） */
    GameManifest selectBySsGameId(@Param("ssGameId") Long ssGameId);

    /** UPSERT：存在则更新，不存在则插入 */
    int upsert(GameManifest manifest);

    /** 按 SS 全局游戏ID 删除缓存 */
    int deleteBySsGameId(@Param("ssGameId") Long ssGameId);
}
