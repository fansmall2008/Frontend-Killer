package com.gamelist.mapper;

import com.gamelist.model.Platform;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PlatformMapper {
    int insertPlatform(Platform platform);
    Platform selectPlatformBySystem(String system);
    List<Platform> selectAllPlatforms();
    Platform selectPlatformById(Long id);
    int updatePlatform(Platform platform);
    int deletePlatform(Long id);
}
