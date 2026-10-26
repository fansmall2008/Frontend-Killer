package com.gamelist.model;

/**
 * ROM 版本记录。
 * <p>
 * 版本是同一游戏的另一种物理格式（如 contra.zip 与 contra.nes 同时存在），
 * 只用于导出选择，不参与匹配/刮削/存在性检测。
 */
public class GameVariant {
    private Long id;
    private Long gameId;
    /** bare / zip / 7z */
    private String format;
    /** 相对版本库根目录的路径，统一使用 / 分隔 */
    private String relPath;
    private Long sizeBytes;
    /** 生成时的主文件大小，用于判断源文件是否变化 */
    private Long sourceSize;
    /** 生成时的主文件修改时间（epoch millis） */
    private Long sourceMtime;
    /** READY / STALE / FAILED */
    private String status;
    /** extract / pack */
    private String recipe;
    private String errorMessage;
    private String createdAt;
    private String updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getRelPath() {
        return relPath;
    }

    public void setRelPath(String relPath) {
        this.relPath = relPath;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Long getSourceSize() {
        return sourceSize;
    }

    public void setSourceSize(Long sourceSize) {
        this.sourceSize = sourceSize;
    }

    public Long getSourceMtime() {
        return sourceMtime;
    }

    public void setSourceMtime(Long sourceMtime) {
        this.sourceMtime = sourceMtime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRecipe() {
        return recipe;
    }

    public void setRecipe(String recipe) {
        this.recipe = recipe;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
