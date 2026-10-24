package com.gamelist.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 副本生成结果（同步接口返回，或后台任务汇总）。
 */
public class VariantGenerateResult {
    private int success;
    private int skipped;
    private int failed;
    private List<String> messages = new ArrayList<>();

    public void addSuccess(String message) {
        success++;
        if (message != null) messages.add(message);
    }

    public void addSkipped(String message) {
        skipped++;
        if (message != null) messages.add(message);
    }

    public void addFailed(String message) {
        failed++;
        if (message != null) messages.add(message);
    }

    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public List<String> getMessages() {
        return messages;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }
}
