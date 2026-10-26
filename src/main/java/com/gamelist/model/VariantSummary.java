package com.gamelist.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏列表第四标志所需的版本摘要。
 */
public class VariantSummary {
    private int readyCount;
    private int staleCount;
    private int failedCount;
    private List<String> formats = new ArrayList<>();

    public int getReadyCount() {
        return readyCount;
    }

    public void setReadyCount(int readyCount) {
        this.readyCount = readyCount;
    }

    public int getStaleCount() {
        return staleCount;
    }

    public void setStaleCount(int staleCount) {
        this.staleCount = staleCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public List<String> getFormats() {
        return formats;
    }

    public void setFormats(List<String> formats) {
        this.formats = formats;
    }

    public boolean isEmpty() {
        return readyCount == 0 && staleCount == 0 && failedCount == 0;
    }
}
