package com.gamelist.service.impl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.gamelist.mapper.TermMappingMapper;
import com.gamelist.model.TermMapping;
import com.gamelist.service.TermMappingService;
import com.gamelist.util.SystemMatcher;
import com.gamelist.util.TemplateExpressionEngine;

import jakarta.annotation.PostConstruct;

/**
 * 方言映射服务实现（TODO #10）。
 * 启动时把 term_mapping 表加载为（category → 归一化 source → target）结构，
 * 注入 {@link TemplateExpressionEngine} 供模板 map() 函数使用；
 * 增删映射后重新加载，保证模板输出实时生效。
 */
@Service
public class TermMappingServiceImpl implements TermMappingService {

    private static final Logger logger = LoggerFactory.getLogger(TermMappingServiceImpl.class);

    /** 系统匹配引导专用分类，不参与模板 map(value) 的默认查找 */
    private static final String CATEGORY_SYSTEM_ALIAS = "system_alias";

    @Autowired
    private TermMappingMapper termMappingMapper;

    @PostConstruct
    public void init() {
        reload();
    }

    @Override
    public List<TermMapping> listAll() {
        return termMappingMapper.selectAll();
    }

    @Override
    public List<TermMapping> listByCategory(String category) {
        return termMappingMapper.selectByCategory(category);
    }

    @Override
    public String addMapping(String sourceTerm, String targetTerm, String category) {
        if (isBlank(sourceTerm) || isBlank(targetTerm) || isBlank(category)) {
            return "sourceTerm、targetTerm、category 均不能为空";
        }
        // 唯一性按归一化后的 source 检查（与 map() 查找键一致），
        // 避免 "Beat'em Up" 与 "beatemup" 语义重复但字形不同的映射
        String normKey = SystemMatcher.normalize(sourceTerm);
        for (TermMapping m : termMappingMapper.selectByCategory(category)) {
            if (SystemMatcher.normalize(m.getSourceTerm()).equals(normKey)) {
                return "已存在映射: " + m.getSourceTerm() + " → " + m.getTargetTerm();
            }
        }
        TermMapping mapping = new TermMapping();
        mapping.setSourceTerm(sourceTerm);
        mapping.setTargetTerm(targetTerm);
        mapping.setCategory(category);
        termMappingMapper.insert(mapping);
        reload();
        return null;
    }

    @Override
    public void deleteMapping(Long id) {
        termMappingMapper.deleteById(id);
        reload();
    }

    /** 从数据库重载全部映射，注入模板表达式引擎缓存 */
    private void reload() {
        Map<String, Map<String, String>> maps = new HashMap<>();
        try {
            for (TermMapping m : termMappingMapper.selectAll()) {
                String category = m.getCategory() != null && !m.getCategory().isEmpty()
                        ? m.getCategory() : CATEGORY_SYSTEM_ALIAS;
                String key = SystemMatcher.normalize(m.getSourceTerm());
                if (key.isEmpty()) continue;
                maps.computeIfAbsent(category, k -> new LinkedHashMap<>())
                        .putIfAbsent(key, m.getTargetTerm());
            }
            TemplateExpressionEngine.setDialectMaps(maps);
            int total = 0;
            for (Map<String, String> m : maps.values()) {
                total += m.size();
            }
            logger.info("方言映射已加载: {} 条, {} 个分类", total, maps.size());
        } catch (Exception e) {
            logger.warn("加载方言映射失败（模板 map() 将无数据）: {}", e.getMessage());
            TemplateExpressionEngine.setDialectMaps(new HashMap<>());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
