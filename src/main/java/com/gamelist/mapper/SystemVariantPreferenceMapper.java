package com.gamelist.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.gamelist.model.SystemVariantPreference;

@Mapper
public interface SystemVariantPreferenceMapper {
    SystemVariantPreference selectBySystemId(@Param("systemId") Integer systemId);
    List<SystemVariantPreference> selectAll();
    int upsert(SystemVariantPreference preference);
}
