package com.gamelist.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.gamelist.model.PlatformStatsCache;

/**
 * 平台体量统计缓存 Mapper
 */
@Mapper
public interface PlatformStatsCacheMapper {

    /** 按平台 ID 查询单条缓存 */
    PlatformStatsCache selectByPlatformId(@Param("platformId") Long platformId);

    /** 查询所有平台的缓存 */
    List<PlatformStatsCache> selectAll();

    /** UPSERT：存在则更新，不存在则插入 */
    int upsert(PlatformStatsCache cache);

    /** 按平台 ID 删除缓存（平台删除时联动） */
    int deleteByPlatformId(@Param("platformId") Long platformId);
}
