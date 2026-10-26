package com.gamelist.model;

/**
 * 系统版本能力：某个系统是否支持 bare / zip / 7z。
 * <p>
 * source=auto 由属性规则派生，source=manual 由用户在系统管理页手动调整；
 * 初始化器不会覆盖 manual 行。
 */
public class SystemVariantCapability {
    private Long id;
    private Integer systemId;
    /** bare / zip / 7z */
    private String format;
    private Boolean supported;
    /** auto / manual */
    private String source;
    private String note;
    private String updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getSystemId() {
        return systemId;
    }

    public void setSystemId(Integer systemId) {
        this.systemId = systemId;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Boolean getSupported() {
        return supported;
    }

    public void setSupported(Boolean supported) {
        this.supported = supported;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
