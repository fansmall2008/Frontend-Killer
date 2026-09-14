package com.gamelist.service;

import com.gamelist.model.ImportStatistics;

/**
 * 新版导入服务 — 替代 GameServiceImpl 中的导入逻辑。
 * <p>
 * 支持两种导入模式：
 * <ul>
 *   <li>模板导入（有数据文件）：使用 v2 模板 + GameFieldAccessor + ImportMediaMatcher</li>
 *   <li>无数据文件导入：直接扫描 ROM 文件</li>
 * </ul>
 */
public interface ImportService {

    /**
     * 有数据文件的模板导入。
     *
     * @param filePath       数据文件路径（gamelist.xml / metadata.pegasus.txt / .lpl）
     * @param templateName   导入模板文件名（如 "retrobat.json"）
     * @param threadCount    导入线程数
     * @param scraperSystemId 关联的刮削系统 ID（可为 null）
     * @return 导入统计
     */
    ImportStatistics importWithDataFile(String filePath, String templateName,
                                         int threadCount, Long scraperSystemId);

    /**
     * 无数据文件的文件扫描导入。
     *
     * @param scanPath        扫描路径
     * @param fileExtensions  文件扩展名（逗号分隔）
     * @param templateName    导入模板文件名（可为 null，使用默认媒体匹配）
     * @param threadCount     导入线程数
     * @param taskId          后台任务 ID
     * @param scraperSystemId 关联的刮削系统 ID（可为 null）
     * @return 导入统计
     */
    ImportStatistics importWithoutDataFile(String scanPath, String fileExtensions,
                                            String templateName, int threadCount,
                                            Long taskId, Long scraperSystemId);
}
