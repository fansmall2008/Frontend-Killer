package com.gamelist.model;

public class Statistics {
    private long totalGames;
    private long fullyScraped;
    private long partiallyScraped;
    private long notScraped;
    private long totalPlatforms;
    
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
    public long getTotalPlatforms() {
        return totalPlatforms;
    }
    public void setTotalPlatforms(long totalPlatforms) {
        this.totalPlatforms = totalPlatforms;
    }
}
