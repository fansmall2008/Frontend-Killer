package com.gamelist.model;

/**
 * 系统级导出格式偏好：只按 systemId 记住上次选择。
 * preferredFormat 取值：bare / zip / 7z / original。
 */
public class SystemVariantPreference {
    private Integer systemId;
    private String preferredFormat;
    private String updatedAt;

    public Integer getSystemId() {
        return systemId;
    }

    public void setSystemId(Integer systemId) {
        this.systemId = systemId;
    }

    public String getPreferredFormat() {
        return preferredFormat;
    }

    public void setPreferredFormat(String preferredFormat) {
        this.preferredFormat = preferredFormat;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
