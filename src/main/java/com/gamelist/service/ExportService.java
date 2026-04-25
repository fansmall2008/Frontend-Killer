package com.gamelist.service;

import java.util.Map;

import com.gamelist.model.ExportRequest;

public interface ExportService {
    /**
     * 导出平台
     */
    Map<String, Object> exportPlatform(ExportRequest request);

    /**
     * 复制游戏文件
     */
    void copyGameFiles(Long platformId, String targetPath, String frontend, String platformName, int threadCount);

    /**
     * 复制游戏文件（带任务ID）
     */
    void copyGameFiles(Long platformId, String targetPath, String frontend, String platformName, int threadCount, Long taskId);

    /**
     * 复制媒体文件
     */
    void copyMediaFiles(Long platformId, String targetPath, String frontend, String platformName, int threadCount);

    /**
     * 复制媒体文件（带任务ID）
     */
    void copyMediaFiles(Long platformId, String targetPath, String frontend, String platformName, int threadCount, Long taskId);

    /**
     * 生成数据文件
     */
    void generateDataFile(Long platformId, String targetPath, String frontend, String platformName);
}
