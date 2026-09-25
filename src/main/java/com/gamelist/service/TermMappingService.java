package com.gamelist.service;

import java.util.List;

import com.gamelist.model.TermMapping;

/**
 * 方言映射服务（TODO #10 模板函数侧）。
 * 负责把 term_mapping 表加载为模板表达式引擎可用的方言缓存（map() 函数），
 * 并提供映射的增删查；变更后自动刷新引擎缓存。
 */
public interface TermMappingService {

    /** 查询全部映射（按 category + source 排序） */
    List<TermMapping> listAll();

    /** 查询指定分类的映射 */
    List<TermMapping> listByCategory(String category);

    /**
     * 新增映射。唯一键 (source_term, category)，冲突时返回失败信息。
     *
     * @return null 表示成功；否则返回错误提示
     */
    String addMapping(String sourceTerm, String targetTerm, String category);

    /** 删除映射并刷新引擎缓存 */
    void deleteMapping(Long id);
}
