package com.gamelist.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.gamelist.model.TermMapping;

@Mapper
public interface TermMappingMapper {
    List<TermMapping> selectAll();
    List<TermMapping> selectByCategory(@Param("category") String category);
    TermMapping selectById(Long id);
    int insert(TermMapping mapping);
    int deleteById(Long id);
}
