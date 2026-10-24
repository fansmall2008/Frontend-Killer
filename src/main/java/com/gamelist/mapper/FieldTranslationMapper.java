package com.gamelist.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.gamelist.model.FieldTranslation;

/**
 * 通用字段译文持久化。
 */
@Mapper
public interface FieldTranslationMapper {

    /** 存在则更新、否则插入（按 src_table/src_id/src_field/lang 唯一）。 */
    int upsert(FieldTranslation ft);

    /** 单条读取某字段某语种译文。 */
    String selectValue(@Param("srcTable") String srcTable,
                       @Param("srcId") Long srcId,
                       @Param("srcField") String srcField,
                       @Param("lang") String lang);

    /** 批量读取指定 id 集合在某字段某语种的译文，返回 [{srcId, value}]。 */
    List<Map<String, Object>> selectValues(@Param("srcTable") String srcTable,
                                           @Param("srcField") String srcField,
                                           @Param("lang") String lang,
                                           @Param("ids") List<Long> ids);

    /** 返回指定 id 集合中该字段该语种已存在译文的 id 列表（用于跳过已翻）。 */
    List<Long> selectTranslatedIds(@Param("srcTable") String srcTable,
                                   @Param("srcField") String srcField,
                                   @Param("lang") String lang,
                                   @Param("ids") List<Long> ids);

    /** 统计指定 id 集合中该字段该语种已翻译数量。 */
    long countTranslated(@Param("srcTable") String srcTable,
                         @Param("srcField") String srcField,
                         @Param("lang") String lang,
                         @Param("ids") List<Long> ids);

    /** 删除某来源行译文；srcField 为空则删除该行全部字段译文。 */
    int deleteBySource(@Param("srcTable") String srcTable,
                       @Param("srcId") Long srcId,
                       @Param("srcField") String srcField);
}
