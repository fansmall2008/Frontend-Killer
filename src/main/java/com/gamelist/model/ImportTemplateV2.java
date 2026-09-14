package com.gamelist.model;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 新版导入模板模型（v2）。
 * <p>
 * 与旧版 ImportTemplate 的关键区别：
 * <ul>
 *   <li>{@code fieldMappings}: key = 数据库列名（nomcourt 标准），value = 数据文件中的候选字段名列表</li>
 *   <li>{@code mediaMappings}: key = nomcourt 值（与 MediaType 枚举对齐），value = 数据文件中的候选媒体字段名列表</li>
 *   <li>移除了旧版的 media rules（路径规则数组）、transform、header、extensions 等冗余配置</li>
 *   <li>媒体路径匹配改为统一的 {@link com.gamelist.util.ImportMediaMatcher} 处理</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImportTemplateV2 {

    private static String getTemplatesPathInternal() {
        return com.gamelist.util.PathUtil.getRulesPath() + "/import";
    }

    private int version = 2;
    private String frontend;
    private String name;
    private String description;
    private String dataFile;      // 数据文件名，如 "gamelist.xml"、"metadata.pegasus.txt"
    private String type;          // 数据文件格式：xml / text / lpl
    private String delimiter;     // text 格式的字段分隔符（如 ":"）

    /**
     * 字段映射：dbColumnName → [候选字段名列表]
     * <p>
     * 例：{"desc": ["desc", "description"], "releasedate": ["releasedate", "release"]}
     */
    private Map<String, List<String>> fieldMappings;

    /**
     * 媒体映射：nomcourt → [候选媒体字段名列表]
     * <p>
     * 例：{"box-2D": ["boxFront", "box2dfront"], "ss": ["screenshot"]}
     * <p>
     * 导入时，先通过 fieldMappings 从数据文件中读取候选字段值，
     * 再通过 mediaMappings 将找到的值设置到对应的 nomcourt 字段。
     */
    private Map<String, List<String>> mediaMappings;

    // ==================== 加载方法 ====================

    /**
     * 从外部路径加载 v2 模板。
     * 如果模板 version != 2，返回 null（需要迁移）。
     */
    public static ImportTemplateV2 loadTemplate(String templateName) {
        try {
            File file = new File(getTemplatesPathInternal() + "/" + templateName);
            if (!file.exists()) {
                return null;
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            ImportTemplateV2 template = mapper.readValue(file, ImportTemplateV2.class);
            if (template.getVersion() != 2) {
                return null; // 旧版模板，需要迁移
            }
            return template;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从文件加载 v2 模板。
     */
    public static ImportTemplateV2 loadFromFile(File file) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            ImportTemplateV2 template = mapper.readValue(file, ImportTemplateV2.class);
            return template.getVersion() == 2 ? template : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 校验方法 ====================

    /**
     * 校验 mediaMappings 中的 key 是否与 MediaType 枚举兼容。
     * @return 不兼容的 nomcourt 值列表，空列表表示全部兼容
     */
    public List<String> validateMediaTypes() {
        List<String> invalid = new ArrayList<>();
        if (mediaMappings == null) return invalid;

        for (String nomcourt : mediaMappings.keySet()) {
            MediaType mt = MediaType.fromNomcourt(nomcourt);
            if (mt == null) {
                mt = MediaType.fromNomcourtLenient(nomcourt);
            }
            if (mt == null) {
                invalid.add(nomcourt);
            }
        }
        return invalid;
    }

    /**
     * 获取模板目录路径。
     */
    public static String getTemplatesPath() {
        return getTemplatesPathInternal();
    }

    // ==================== Getters / Setters ====================

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getFrontend() { return frontend; }
    public void setFrontend(String frontend) { this.frontend = frontend; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDataFile() { return dataFile; }
    public void setDataFile(String dataFile) { this.dataFile = dataFile; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDelimiter() { return delimiter; }
    public void setDelimiter(String delimiter) { this.delimiter = delimiter; }

    public Map<String, List<String>> getFieldMappings() { return fieldMappings; }
    public void setFieldMappings(Map<String, List<String>> fieldMappings) { this.fieldMappings = fieldMappings; }

    public Map<String, List<String>> getMediaMappings() { return mediaMappings; }
    public void setMediaMappings(Map<String, List<String>> mediaMappings) { this.mediaMappings = mediaMappings; }
}
