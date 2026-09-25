package com.gamelist.model;

import java.util.List;
import java.util.Map;

public class ExportRequest {
    private Long platformId;
    private List<Long> platformIds;
    private String frontend;
    private String outputPath;
    private boolean copyRoms;
    private boolean copyMedia;
    private boolean generateDataFile;
    private int threadCount;
    // 整目录拷贝模式：存在未刮削游戏时，用户选择直接把平台源 ROM 目录全量拷贝（不依赖刮削关联性）
    private boolean wholeDirectoryCopy;
    // 模板声明变量的用户填写值（key=变量名, value=用户输入），执行前注入表达式与路径占位符
    private Map<String, String> templateVariables;

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public List<Long> getPlatformIds() {
        return platformIds;
    }

    public void setPlatformIds(List<Long> platformIds) {
        this.platformIds = platformIds;
    }

    public String getFrontend() {
        return frontend;
    }

    public void setFrontend(String frontend) {
        this.frontend = frontend;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public boolean isCopyRoms() {
        return copyRoms;
    }

    public void setCopyRoms(boolean copyRoms) {
        this.copyRoms = copyRoms;
    }

    public boolean isCopyMedia() {
        return copyMedia;
    }

    public void setCopyMedia(boolean copyMedia) {
        this.copyMedia = copyMedia;
    }

    public boolean isGenerateDataFile() {
        return generateDataFile;
    }

    public void setGenerateDataFile(boolean generateDataFile) {
        this.generateDataFile = generateDataFile;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public boolean isWholeDirectoryCopy() {
        return wholeDirectoryCopy;
    }

    public void setWholeDirectoryCopy(boolean wholeDirectoryCopy) {
        this.wholeDirectoryCopy = wholeDirectoryCopy;
    }

    public Map<String, String> getTemplateVariables() {
        return templateVariables;
    }

    public void setTemplateVariables(Map<String, String> templateVariables) {
        this.templateVariables = templateVariables;
    }
}
