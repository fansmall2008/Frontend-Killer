package com.gamelist.service;

import com.gamelist.model.ExportRule;
import com.gamelist.model.TemplateV3;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ExportRuleService {
    /**
     * 加载所有导出规则（v2 + v3）
     */
    void loadRules();

    /**
     * 获取所有可用的 v2 导出规则
     */
    Map<String, ExportRule> getRules();

    /**
     * 根据前端名称获取 v2 导出规则
     */
    ExportRule getRuleByFrontend(String frontend);

    /**
     * 获取 v2 规则列表
     */
    List<ExportRule> getRuleList();

    // ==================== v3 模板支持 ====================

    /**
     * 根据前端名称获取 v3 导出模板
     */
    TemplateV3 getV3RuleByFrontend(String frontend);

    /**
     * 获取 v3 模板列表
     */
    List<TemplateV3> getV3RuleList();

    /**
     * 判断某前端是否有 v3 模板
     */
    boolean isV3Template(String frontend);

    /**
     * 获取所有 v3 模板的 frontend key 集合
     */
    Set<String> getV3FrontendKeys();
}
