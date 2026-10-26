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
    // 整目录拷贝模式：存在未刮削游戏时，用户选择直接把平台源 ROM 目录全量拷贝（不依赖刮削关联性）
    private boolean wholeDirectoryCopy;
    // 模板声明变量的用户填写值（key=变量名, value=用户输入），执行前注入表达式与路径占位符
    private Map<String, String> templateVariables;
    // 可选：导出时按此语种从 field_translation 回填 translatedName/translatedDesc（空=不注入，用原文）
    private String translationLang;
    // 按系统选择版本格式：key=systemId, value=bare/zip/7z/original；未提供时用系统偏好
    private Map<String, String> systemVariantFormats;

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

    public String getTranslationLang() {
        return translationLang;
    }

    public void setTranslationLang(String translationLang) {
        this.translationLang = translationLang;
    }

    public Map<String, String> getSystemVariantFormats() {
        return systemVariantFormats;
    }

    public void setSystemVariantFormats(Map<String, String> systemVariantFormats) {
        this.systemVariantFormats = systemVariantFormats;
    }
}
