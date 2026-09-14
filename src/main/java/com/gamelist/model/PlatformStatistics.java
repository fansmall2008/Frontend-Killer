package com.gamelist.model;

/**
 * 平台统计数据
 * 
 * 刮削状态分 4 级：
 * 1. scraped - 已刮削（scraped=true 且非低质量）
 * 2. originalData - 原始数据（未刮削但有一定数据）
 * 3. poorQuality - 低质量（已刮削但数据不完整）
 * 4. rawRom - 原始ROM（完全没有数据）
 */
public class PlatformStatistics {
    private Long platformId;
    private String platformName;
    private long totalGames;
    private long scraped;          // 已刮削
    private long originalData;     // 原始数据
    private long poorQuality;      // 低质量
    private long rawRom;           // 原始ROM
    private long missingGames;     // 缺失游戏
    
    public Long getPlatformId() { return platformId; }
    public void setPlatformId(Long platformId) { this.platformId = platformId; }
    public String getPlatformName() { return platformName; }
    public void setPlatformName(String platformName) { this.platformName = platformName; }
    public long getTotalGames() { return totalGames; }
    public void setTotalGames(long totalGames) { this.totalGames = totalGames; }
    public long getScraped() { return scraped; }
    public void setScraped(long scraped) { this.scraped = scraped; }
    public long getOriginalData() { return originalData; }
    public void setOriginalData(long originalData) { this.originalData = originalData; }
    public long getPoorQuality() { return poorQuality; }
    public void setPoorQuality(long poorQuality) { this.poorQuality = poorQuality; }
    public long getRawRom() { return rawRom; }
    public void setRawRom(long rawRom) { this.rawRom = rawRom; }
    public long getMissingGames() { return missingGames; }
    public void setMissingGames(long missingGames) { this.missingGames = missingGames; }
}
