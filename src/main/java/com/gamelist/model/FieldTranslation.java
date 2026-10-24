package com.gamelist.model;

/**
 * 通用字段译文实体。
 * 以逻辑外键 (srcTable, srcId, srcField) 定位任意表任意字段的某语种译文。
 * 覆盖 game.name / game.desc / rom_db.game_name 等，多语种以 lang 区分。
 */
public class FieldTranslation {
    private Long id;
    private String srcTable;   // 来源表：game / rom_db
    private Long srcId;        // 来源行主键（game.id / rom_db.id）
    private String srcField;   // 来源字段：name / desc / game_name
    private String lang;       // 语种码，如 zh / en / ja
    private String value;      // 译文内容

    public FieldTranslation() {}

    public FieldTranslation(String srcTable, Long srcId, String srcField, String lang, String value) {
        this.srcTable = srcTable;
        this.srcId = srcId;
        this.srcField = srcField;
        this.lang = lang;
        this.value = value;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSrcTable() { return srcTable; }
    public void setSrcTable(String srcTable) { this.srcTable = srcTable; }

    public Long getSrcId() { return srcId; }
    public void setSrcId(Long srcId) { this.srcId = srcId; }

    public String getSrcField() { return srcField; }
    public void setSrcField(String srcField) { this.srcField = srcField; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
