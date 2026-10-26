package com.gamelist.model;

import java.util.List;

/**
 * 生成 ROM 版本的请求体。
 */
public class VariantGenerateRequest {
    /** bare / zip / 7z；为空表示该系统支持的全部格式 */
    private List<String> formats;
    /** 是否强制重建 */
    private boolean force;

    public List<String> getFormats() {
        return formats;
    }

    public void setFormats(List<String> formats) {
        this.formats = formats;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }
}
