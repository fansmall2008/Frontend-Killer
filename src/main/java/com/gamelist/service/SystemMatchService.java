package com.gamelist.service;

import java.util.Map;

import com.gamelist.model.Platform;

/**
 * 平台未绑定系统时的智能匹配服务（TODO #9）。
 */
public interface SystemMatchService {

    /**
     * 根据平台文件夹名/平台名/系统名在刮削系统列表中模糊匹配候选系统。
     *
     * @return {success, bound, terms, candidates}：
     *         bound=true 表示平台已绑定 systemId，前端无需弹窗；
     *         candidates 为按分数降序的候选列表（含 systemId/name/nameCn/score/reasons）。
     */
    Map<String, Object> matchSystems(Platform platform);
}
