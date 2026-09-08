package com.gamelist.mapper;

import java.util.List;

import com.gamelist.model.ScraperSystem;

public interface ScraperSystemMapper {
    int insert(ScraperSystem system);
    int update(ScraperSystem system);
    int deleteById(Long id);
    ScraperSystem selectById(Long id);
    ScraperSystem selectBySystemId(Integer systemId);
    List<ScraperSystem> selectAll();
    int deleteAll();
}