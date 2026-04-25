package com.gamelist.model;

public class PlatformStatistics {
    private Long platformId;
    private String platformName;
    private long totalGames;
    private long fullyScraped;
    private long partiallyScraped;
    private long notScraped;
    private long missingGames;
    
    public Long getPlatformId() {
        return platformId;
    }
    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }
    public String getPlatformName() {
        return platformName;
    }
    public void setPlatformName(String platformName) {
        this.platformName = platformName;
    }
    public long getTotalGames() {
        return totalGames;
    }
    public void setTotalGames(long totalGames) {
        this.totalGames = totalGames;
    }
    public long getFullyScraped() {
        return fullyScraped;
    }
    public void setFullyScraped(long fullyScraped) {
        this.fullyScraped = fullyScraped;
    }
    public long getPartiallyScraped() {
        return partiallyScraped;
    }
    public void setPartiallyScraped(long partiallyScraped) {
        this.partiallyScraped = partiallyScraped;
    }
    public long getNotScraped() {
        return notScraped;
    }
    public void setNotScraped(long notScraped) {
        this.notScraped = notScraped;
    }
    public long getMissingGames() {
        return missingGames;
    }
    public void setMissingGames(long missingGames) {
        this.missingGames = missingGames;
    }
}
