package com.gamelist.model;

import java.nio.file.Path;

/**
 * 导出时解析出的实际源文件。
 * <p>
 * variant=false 表示回退到主文件；sourcePath=null 表示主文件也不存在，应跳过。
 */
public class ResolvedVariant {
    private final Path sourcePath;
    private final String format;
    private final String extension;
    private final boolean variant;
    private final Long variantId;
    /** 导出时该系统的目标格式（original 表示不转换） */
    private final String requestedFormat;
    /** 回退到主文件的原因；未回退时为 null */
    private final String fallbackReason;

    public ResolvedVariant(Path sourcePath, String format, String extension,
                           boolean variant, Long variantId) {
        this(sourcePath, format, extension, variant, variantId, format, null);
    }

    public ResolvedVariant(Path sourcePath, String format, String extension,
                           boolean variant, Long variantId,
                           String requestedFormat, String fallbackReason) {
        this.sourcePath = sourcePath;
        this.format = format;
        this.extension = extension;
        this.variant = variant;
        this.variantId = variantId;
        this.requestedFormat = requestedFormat;
        this.fallbackReason = fallbackReason;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public String getFormat() {
        return format;
    }

    /** 含点扩展名，如 .nes / .zip / .7z */
    public String getExtension() {
        return extension;
    }

    public boolean isVariant() {
        return variant;
    }

    public Long getVariantId() {
        return variantId;
    }

    public String getRequestedFormat() {
        return requestedFormat;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }
}
