package com.gamelist.model;

import java.sql.Timestamp;

/**
 * 平台体量统计缓存
 * <p>
 * 由 PlatformStatsScanner 服务按需扫描后写入，用于导出前预估数据量。
 * 每一行代表一个平台的最新扫描快照。
 */
public class PlatformStatsCache {

    /** 平台 ID（主键，与 platform.id 一一对应） */
    private Long platformId;

    /** 游戏记录数（Pegasus 多文件游戏已展开） */
    private int totalGames;

    /** ROM 文件数（磁盘实际存在） */
    private int totalRomCount;

    /** 数据库有路径但磁盘缺失的 ROM 数 */
    private int romMissingCount;

    /** ROM 总字节数 */
    private long totalRomSize;

    /** 媒体文件数（磁盘实际存在） */
    private int totalMediaCount;

    /** 数据库有路径但磁盘缺失的媒体文件数 */
    private int mediaMissingCount;

    /** 媒体总字节数 */
    private long totalMediaSize;

    /** JSON: {"box-2D":800,"video":200,...} */
    private String mediaCountByType;

    /** JSON: {"box-2D":800000,"video":6500000000,...} */
    private String mediaSizeByType;

    /** 上次扫描完成时间 */
    private Timestamp lastScannedAt;

    /** 上次扫描耗时（毫秒） */
    private long scanDurationMs;

    /** 关联的 BackgroundTask ID */
    private Long lastTaskId;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    // ==================== Getter / Setter ====================

    public Long getPlatformId() { return platformId; }
    public void setPlatformId(Long platformId) { this.platformId = platformId; }

    public int getTotalGames() { return totalGames; }
    public void setTotalGames(int totalGames) { this.totalGames = totalGames; }

    public int getTotalRomCount() { return totalRomCount; }
    public void setTotalRomCount(int totalRomCount) { this.totalRomCount = totalRomCount; }

    public int getRomMissingCount() { return romMissingCount; }
    public void setRomMissingCount(int romMissingCount) { this.romMissingCount = romMissingCount; }

    public long getTotalRomSize() { return totalRomSize; }
    public void setTotalRomSize(long totalRomSize) { this.totalRomSize = totalRomSize; }

    public int getTotalMediaCount() { return totalMediaCount; }
    public void setTotalMediaCount(int totalMediaCount) { this.totalMediaCount = totalMediaCount; }

    public int getMediaMissingCount() { return mediaMissingCount; }
    public void setMediaMissingCount(int mediaMissingCount) { this.mediaMissingCount = mediaMissingCount; }

    public long getTotalMediaSize() { return totalMediaSize; }
    public void setTotalMediaSize(long totalMediaSize) { this.totalMediaSize = totalMediaSize; }

    public String getMediaCountByType() { return mediaCountByType; }
    public void setMediaCountByType(String mediaCountByType) { this.mediaCountByType = mediaCountByType; }

    public String getMediaSizeByType() { return mediaSizeByType; }
    public void setMediaSizeByType(String mediaSizeByType) { this.mediaSizeByType = mediaSizeByType; }

    public Timestamp getLastScannedAt() { return lastScannedAt; }
    public void setLastScannedAt(Timestamp lastScannedAt) { this.lastScannedAt = lastScannedAt; }

    public long getScanDurationMs() { return scanDurationMs; }
    public void setScanDurationMs(long scanDurationMs) { this.scanDurationMs = scanDurationMs; }

    public Long getLastTaskId() { return lastTaskId; }
    public void setLastTaskId(Long lastTaskId) { this.lastTaskId = lastTaskId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
