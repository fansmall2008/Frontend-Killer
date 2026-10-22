package com.gamelist.service;

import com.gamelist.model.RomDb;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ROM 识别数据库服务。
 * 统一提供 CRC32（卡带）、serial（光盘）、模糊搜索三种匹配路径。
 */
public interface RomDbService {

    /**
     * 按 serial + systemId 匹配（光盘平台）。
     *
     * @param serial   光盘编号（如 SLUS-20751）
     * @param systemId ScreenScraper systemId
     * @return 匹配结果，未命中返回 null
     */
    RomDb matchBySerial(String serial, int systemId);

    /**
     * 按 CRC32 + systemId 匹配（卡带平台）。
     *
     * @param crc32    8位十六进制 CRC32
     * @param systemId ScreenScraper systemId
     * @return 匹配结果，未命中返回 null
     */
    RomDb matchByCrc32(String crc32, int systemId);

    /**
     * 从镜像文件匹配（自动判断路径：SYSTEM.CNF / PARAM.SFO / 文件名正则 → serial → 查 rom_db）。
     * 用于刮削流程中的光盘平台匹配。
     *
     * @param filePath 完整文件路径
     * @param systemId ScreenScraper systemId
     * @return 匹配结果（含 serial、gameName），未命中返回 null
     */
    RomDb matchByDiscFile(String filePath, int systemId);

    /**
     * 从文件计算 CRC32 并匹配（卡带平台）。
     *
     * @param filePath 完整文件路径
     * @param systemId ScreenScraper systemId
     * @return 匹配结果，未命中返回 null
     */
    RomDb matchByFileCrc32(String filePath, int systemId);

    /**
     * 模糊搜索游戏名。
     *
     * @param keyword 关键词
     * @param limit   最大返回数
     * @return 匹配列表
     */
    List<RomDb> searchByName(String keyword, int limit);

    /**
     * 按 systemId + 游戏名模糊搜索。
     */
    List<RomDb> searchBySystemAndName(int systemId, String keyword, int limit);

    /**
     * 根据 serial 查询（用于游戏编辑页手动查询）。
     */
    RomDb lookupBySerial(String serial, int systemId);

    /**
     * 判断 rom_db 是否已初始化完成。
     */
    boolean isReady();

    /**
     * 获取 rom_db 统计信息。
     */
    Map<String, Object> getStats();

    /**
     * 获取各平台数据统计。
     */
    List<Map<String, Object>> getPlatformStats();

    /**
     * 获取各 systemId 数据统计。
     */
    List<Map<String, Object>> getSystemStats();

    /**
     * 重新导入指定平台的数据（删除旧数据后重新解析）。
     *
     * @param platformName libretro-database 平台名
     * @return 导入条数
     */
    int reimportPlatform(String platformName);

    /**
     * 检查 rom_db 中是否有指定 systemId 的数据。
     */
    boolean hasDataForSystem(int systemId);

    /**
     * 批量匹配游戏（自动判断光盘/卡带路径）。
     *
     * @param gameIds  选中的游戏 ID 列表
     * @param systemId ScreenScraper systemId
     * @param language 语言偏好（"ja" / "en"）
     * @return {total, matched, updated}
     */
    Map<String, Integer> batchMatch(List<Long> gameIds, int systemId, String language);

    /**
     * 批量匹配游戏（支持文件名提取选项）。
     *
     * @param gameIds          选中的游戏 ID 列表
     * @param systemId         ScreenScraper systemId
     * @param language         语言偏好（"ja" / "en"）
     * @param fileNameExtract  若为 true，CRC/文件内提取均未命中时，额外尝试从文件名正则提取 serial 查 rom_db
     * @return {total, matched, updated}
     */
    Map<String, Integer> batchMatch(List<Long> gameIds, int systemId, String language, boolean fileNameExtract);

    /**
     * 批量匹配游戏（支持按方法选择）。
     *
     * @param gameIds  选中的游戏 ID 列表
     * @param systemId ScreenScraper systemId
     * @param language 语言偏好（"ja" / "en" / "cn"）
     * @param methods  启用的匹配方法集合，固定顺序 serial → CRC → filename_exact → name_serial → name_fuzzy。
     *                 可包含 "crc"、"serial"、"filename_exact"、"name_serial"、"name_fuzzy"（"name" 为旧别名，等价 name_serial+name_fuzzy）。
     *                 其中 crc/serial 为内容级→命中置 matched（🟢）；filename_exact/name_serial/name_fuzzy 为名字级→置 match_suspect（🟡）待人工确认。
     * @return {total, matched, updated}
     */
    Map<String, Integer> batchMatch(List<Long> gameIds, int systemId, String language, Set<String> methods);

    /**
     * 清洗文件名：去扩展名/括号/方括号内容，下划线转空格，转小写。
     */
    String cleanFileName(String fileName);

    /**
     * 获取模糊匹配候选列表。
     *
     * @param gameIds  游戏 ID 列表（最多 30 个）
     * @param systemId ScreenScraper systemId
     * @return 每个游戏的候选列表
     */
    List<Map<String, Object>> getFuzzyCandidates(List<Long> gameIds, int systemId);

    /**
     * 确认模糊匹配：用 romDbId 对应的记录回填游戏信息。
     */
    boolean confirmFuzzyMatch(long gameId, long romDbId);

    /**
     * 异步按平台批量匹配（后台任务，可在任务管理页查看进度）。
     *
     * @param taskId      后台任务 ID
     * @param platformIds 平台 ID 列表
     * @param language    语言偏好（"en" / "ja" / "cn"）
     */
    void batchMatchByPlatformsAsync(Long taskId, List<Long> platformIds, String language);

    /**
     * 异步按平台批量匹配（支持按方法选择）。
     *
     * @param taskId      后台任务 ID
     * @param platformIds 平台 ID 列表
     * @param language    语言偏好
     * @param methods     启用的匹配方法集合（"crc"/"serial"/"name"）
     */
    void batchMatchByPlatformsAsync(Long taskId, List<Long> platformIds, String language, Set<String> methods);
}
