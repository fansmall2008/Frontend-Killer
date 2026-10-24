package com.gamelist.service;

import java.util.Map;

/**
 * 通用翻译工具服务：对 database 中任意受支持字段按目标语种批量翻译，写入 field_translation。
 * 采用后台线程 + 进度/停止模型（复用原 TranslationController 的运行范式）。
 */
public interface TranslationToolService {

    /**
     * 启动一次翻译任务。
     * @param request 含 srcTable / srcField / scope(platformId|gameIds|platforms|all) /
     *                sourceLang / targetLang / serviceId / overwriteExisting
     * @return taskId
     */
    String start(Map<String, Object> request);

    /** 查询任务进度（total/completed/success/failed/status/logs/errors）。 */
    Map<String, Object> getProgress(String taskId);

    /** 请求停止任务。 */
    void stop(String taskId);
}
