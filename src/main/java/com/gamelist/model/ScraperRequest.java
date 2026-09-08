package com.gamelist.model;

import java.util.List;

public class ScraperRequest {
    
    private String type;                    // platform | batch | single
    private Long platformId;                // 平台ID
    private List<Long> gameIds;             // 游戏ID列表
    private List<String> scope;             // 刮削范围: gameInfo, media
    private String region;                  // 地区: WOR, EU, US, JP, FR, ASI
    private String language;                 // 语种: cn/en/jp/fr/de/es/it/pt/kr/tw/ru/nl/pl/se
    private List<String> mediaTypes;        // 媒体类型列表
    private Boolean scrapeAllMedia;         // 是否刮削全部媒体
    private String searchMode;              // crc | filename | name
    private Boolean overwrite;              // 是否覆盖已存在文件
    private Boolean onlyMissing;            // 是否仅刮削缺失内容
    
    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public List<Long> getGameIds() {
        return gameIds;
    }

    public void setGameIds(List<Long> gameIds) {
        this.gameIds = gameIds;
    }

    public List<String> getScope() {
        return scope;
    }

    public void setScope(List<String> scope) {
        this.scope = scope;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public List<String> getMediaTypes() {
        return mediaTypes;
    }

    public void setMediaTypes(List<String> mediaTypes) {
        this.mediaTypes = mediaTypes;
    }

    public Boolean getScrapeAllMedia() {
        return scrapeAllMedia;
    }

    public void setScrapeAllMedia(Boolean scrapeAllMedia) {
        this.scrapeAllMedia = scrapeAllMedia;
    }

    public String getSearchMode() {
        return searchMode;
    }

    public void setSearchMode(String searchMode) {
        this.searchMode = searchMode;
    }

    public Boolean getOverwrite() {
        return overwrite;
    }

    public void setOverwrite(Boolean overwrite) {
        this.overwrite = overwrite;
    }

    public Boolean getOnlyMissing() {
        return onlyMissing;
    }

    public void setOnlyMissing(Boolean onlyMissing) {
        this.onlyMissing = onlyMissing;
    }
}