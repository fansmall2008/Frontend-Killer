package com.gamelist.service;

import com.gamelist.model.ExportRule;

import java.util.List;
import java.util.Map;

public interface ExportRuleService {
    /**
     * 加载所有导出规则
     */
    void loadRules();

    /**
     * 获取所有可用的导出规则
     */
    Map<String, ExportRule> getRules();

    /**
     * 根据前端名称获取导出规则
     */
    ExportRule getRuleByFrontend(String frontend);

    /**
     * 获取规则列表
     */
    List<ExportRule> getRuleList();
}
