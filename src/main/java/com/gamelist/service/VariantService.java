package com.gamelist.service;

import java.util.List;
import java.util.Map;

import com.gamelist.model.Game;
import com.gamelist.model.GameVariant;
import com.gamelist.model.VariantGenerateResult;
import com.gamelist.model.VariantSummary;

/**
 * ROM 版本的生成、查询与删除。
 * <p>
 * 版本是真实落盘文件，生成与导出完全分离；导出只通过 {@code VariantResolver} 查表复制。
 */
public interface VariantService {

    String FORMAT_BARE = "bare";
    String FORMAT_ZIP = "zip";
    String FORMAT_SEVEN_Z = "7z";
    String FORMAT_ISO = "iso";
    String FORMAT_CSO = "cso";
    String FORMAT_CHD = "chd";
    String FORMAT_ORIGINAL = "original";

    String STATUS_READY = "READY";
    String STATUS_STALE = "STALE";
    String STATUS_FAILED = "FAILED";

    List<GameVariant> list(Long gameId);

    VariantSummary getSummary(Long gameId);

    Map<Long, VariantSummary> getSummaries(List<Game> games);

    /**
     * 同步生成单个游戏的版本。
     *
     * @param formats 目标格式（bare/zip/7z/iso/cso/chd），空集合表示该系统支持的全部格式
     * @param force   true 时忽略已存在且未过期的版本，强制重建
     */
    VariantGenerateResult generateForGame(Long gameId, List<String> formats, boolean force);

    /**
     * 同步生成整个平台的版本（跨游戏 4 线程并行）。
     */
    VariantGenerateResult generateForPlatform(Long platformId, List<String> formats, boolean force);

    /**
     * 后台任务方式生成整个平台的版本，返回任务 ID。
     */
    Long generateForPlatformAsync(Long platformId, List<String> formats, boolean force);

    /** 删除单个格式的版本（文件 + 数据库记录） */
    boolean deleteVariant(Long gameId, String format);

    /** 删除某个游戏的全部版本（文件 + 数据库记录） */
    int deleteByGameId(Long gameId);

    /**
     * 诊断：不落盘，只探测源文件形态并给出每个目标格式的产线结论。
     * 用于弹窗实时提示“为什么转不了”。
     */
    Map<String, Object> diagnose(Long gameId);

    /**
     * 平台级格式体检：按扩展名把每个游戏归到源形态，再按 (游戏 × 目标格式) 给出四态结论。
     * <p>
     * 只读且只读元数据：不读文件头、不生成、不落盘、不改数据库。
     * 结论是“够不够用”的粗判，真生成时会重新校准。
     *
     * @param limit 最多盘点的游戏数，防止几千条的平台阻塞；超出时 summary.truncated=true
     */
    Map<String, Object> survey(Long platformId, int limit);
}
