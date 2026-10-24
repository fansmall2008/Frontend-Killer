package com.gamelist.mapper;

import com.gamelist.model.RomDb;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * ROM 识别数据库 Mapper
 */
public interface RomDbMapper {

    /** 按 systemId + serial 精确查找（光盘平台） */
    RomDb findBySystemAndSerial(@Param("systemId") int systemId, @Param("serial") String serial);

    /** 按 systemId + serial 查找，忽略双方连字符（Genesis 等 serial 格式不一致的回退） */
    RomDb findBySystemAndSerialNormalized(@Param("systemId") int systemId, @Param("serial") String serial);

    /** 按 systemId + serial 前缀匹配（GB/GBC 的 CGB-XXXX-REGION 格式回退） */
    RomDb findBySystemAndSerialPrefix(@Param("systemId") int systemId, @Param("prefix") String prefix);

    /** 按 systemId + game code 匹配（Genesis: 提取 "MK1054" → 匹配 DB "MK-1054-00"） */
    RomDb findBySystemAndGameCode(@Param("systemId") int systemId, @Param("gameCode") String gameCode);

    /** 按 ID 查找 */
    RomDb findById(@Param("id") long id);

    /** 按 systemId + crc32 精确查找（卡带平台） */
    RomDb findBySystemAndCrc32(@Param("systemId") int systemId, @Param("crc32") String crc32);

    /** 按 systemId + 归一化文件名精确查找（街机 zip 短名匹配） */
    RomDb findBySystemAndRomFile(@Param("systemId") int systemId, @Param("romFile") String romFile);

    /** 按 platform + serial 查找（systemId=0 的未映射平台） */
    RomDb findByPlatformAndSerial(@Param("platform") String platform, @Param("serial") String serial);

    /** 按 platform + crc32 查找（systemId=0 的未映射平台） */
    RomDb findByPlatformAndCrc32(@Param("platform") String platform, @Param("crc32") String crc32);

    /** 插入单条（MERGE 语义：同 system_id+serial 或 system_id+crc32 已存在则忽略） */
    int insertIgnore(RomDb romDb);

    /** 批量插入（用于初始化导入） */
    int batchInsert(@Param("list") List<RomDb> list);

    /** 按平台统计数据量 */
    List<Map<String, Object>> countByPlatform();

    /** DAT 预检：按平台统计总行数与有效行数（名称非空且(crc32或serial非空)） */
    List<Map<String, Object>> countDatStatsByPlatform();

    /** 按 systemId 统计数据量 */
    List<Map<String, Object>> countBySystem();

    /** 按来源统计数据量 */
    List<Map<String, Object>> countBySource();

    /** 查询指定平台的记录数 */
    int countByPlatformName(@Param("platform") String platform);

    /** 查询指定平台 + 指定来源的记录数（用于 rdb 幂等判断） */
    int countByPlatformAndSource(@Param("platform") String platform, @Param("source") String source);

    /** 删除指定平台的所有数据 */
    int deleteByPlatform(@Param("platform") String platform);

    /** 删除指定 systemId 的所有数据 */
    int deleteBySystemId(@Param("systemId") int systemId);

    /** 清空全表 */
    int deleteAll();

    /** 总记录数 */
    int countAll();

    /** 查询全部记录（用于增量合并导入） */
    List<RomDb> findAll();

    /** 查询指定 systemId 的记录数 */
    int countBySystemId(@Param("systemId") int systemId);

    /** 按 systemId 批量查询全部记录（用于内存预加载） */
    List<RomDb> findBySystemId(@Param("systemId") int systemId);

    /** 按平台名查询全部记录（用于翻译工具 rom_db 范围取数） */
    List<RomDb> findByPlatform(@Param("platform") String platform);

    /** 模糊搜索游戏名（LIMIT 限制） */
    List<RomDb> searchByName(@Param("keyword") String keyword, @Param("limit") int limit);

    /** 按 systemId 模糊搜索 */
    List<RomDb> searchBySystemAndName(@Param("systemId") int systemId,
                                      @Param("keyword") String keyword,
                                      @Param("limit") int limit);
}
