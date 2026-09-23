package com.gamelist.service;

import java.util.List;
import java.util.Map;

import com.gamelist.model.ExportRequest;

public interface ExportService {
    /**
     * 导出平台
     */
    Map<String, Object> exportPlatform(ExportRequest request);

    /**
     * 批量导出平台（单任务顺序执行，避免线程爆炸）
     */
    Map<String, Object> batchExport(List<Long> platformIds, ExportRequest request);

    /**
     * 导出预检：基于 scraped 计数判断关联性风险，不启动导出。
     */
    Map<String, Object> preflight(ExportRequest request);
}
