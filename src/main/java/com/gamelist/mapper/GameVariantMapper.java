package com.gamelist.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.gamelist.model.GameVariant;

@Mapper
public interface GameVariantMapper {
    int insert(GameVariant variant);
    int update(GameVariant variant);
    GameVariant selectByGameIdAndFormat(@Param("gameId") Long gameId, @Param("format") String format);
    List<GameVariant> selectByGameId(@Param("gameId") Long gameId);
    List<GameVariant> selectByGameIds(@Param("gameIds") List<Long> gameIds);
    int deleteById(@Param("id") Long id);
    int deleteByGameId(@Param("gameId") Long gameId);
}
