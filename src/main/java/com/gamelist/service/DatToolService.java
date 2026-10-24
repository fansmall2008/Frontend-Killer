package com.gamelist.service;

import java.util.List;
import java.util.Map;

/**
 * DAT 生成器服务：把 rom_db 或现有游戏列表(game) 导出为标准 ClrMamePro 文本 DAT，
 * 写入被 import-dat 递归扫描的目录，闭环回流进 rom_db。采用后台线程 + 进度/停止模型。
 */
public interface DatToolService {

    /**
     * 预检摘要：两个来源逐平台的行数/有效行数/systemId 可解析性。
     * @return {romDb:[{platform,systemId,totalRows,validRows,resolvable}], game:[{platformId,platformName,systemId,totalGames,validGames,resolvable}]}
     */
    Map<String, Object> meta();

    /**
     * 启动一次 DAT 生成任务。
     * @param request 含 source('rom_db'|'game') / platforms(List，rom_db 为平台名、game 为 platformId) / autoImport(bool)
     * @return taskId
     */
    String start(Map<String, Object> request);

    /** 查询任务进度（total/completed/filesWritten/gamesWritten/skipped/status/logs/errors）。 */
    Map<String, Object> getProgress(String taskId);

    /** 请求停止任务（停止后不再写新文件，已完成文件保留）。 */
    void stop(String taskId);

    /** 是否正在生成（供导入侧互斥护栏查询）。 */
    boolean isGenerating();
}
