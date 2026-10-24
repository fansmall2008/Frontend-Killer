package com.gamelist.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.gamelist.model.SystemVariantCapability;

@Mapper
public interface SystemVariantCapabilityMapper {
    List<SystemVariantCapability> selectAll();
    List<SystemVariantCapability> selectBySystemId(@Param("systemId") Integer systemId);
    SystemVariantCapability selectBySystemIdAndFormat(@Param("systemId") Integer systemId,
                                                      @Param("format") String format);
    int insert(SystemVariantCapability capability);
    int update(SystemVariantCapability capability);
    int upsert(SystemVariantCapability capability);
    int deleteAutoBySystemId(@Param("systemId") Integer systemId);
    int deleteAutoBySystemIdAndFormat(@Param("systemId") Integer systemId,
                                      @Param("format") String format);
}
