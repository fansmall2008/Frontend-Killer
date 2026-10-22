package com.gamelist.model;

import java.util.List;
import java.util.Map;

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
    private Boolean preserveLocalData;      // 保留本地已有数据（不覆盖开发商/发行商/年份等）
    
    // ========== “应用于所有搜索结果”模式：筛选参数 ==========
    private String search;                   // 搜索关键词
    private List<String> scrapeStatuses;     // 刮削状态筛选
    private List<String> fileStatuses;       // 文件状态筛选
    private List<String> matchedStatuses;     // 匹配状态筛选 (matched/suspect/unmatched 多选；空=不过滤)
    private List<String> developers;         // 开发商筛选
    private List<String> publishers;         // 发行商筛选
    private List<String> genres;             // 类型筛选
    private List<String> players;            // 玩家数筛选
    private String folderPath;               // 文件夹路径筛选

    // 前端“应用于所有搜索结果”统一发送的嵌套 filters 对象（与其余批量端点契约一致）
    private Map<String, Object> filters;

    public Map<String, Object> getFilters() { return filters; }
    public void setFilters(Map<String, Object> filters) { this.filters = filters; }

    /**
     * 将嵌套 filters 解包到扁平字段，供 hasFilters()/各 getter 使用。
     * 空数组、空串一律视为“无筛选”，保持 null，避免只填部分条件时把 [] 传入 SQL 造成 0 行。
     */
    @SuppressWarnings("unchecked")
    public void flattenFilters() {
        if (filters == null) return;
        if (isBlank(search)) {
            Object s = filters.get("search");
            if (s instanceof String && !((String) s).isEmpty()) search = (String) s;
        }
        if (isBlank(folderPath)) {
            Object fp = filters.get("folderPath");
            if (fp instanceof String && !((String) fp).isEmpty()) folderPath = (String) fp;
        }
        scrapeStatuses = pickList(scrapeStatuses, (List<String>) filters.get("scrapeStatuses"));
        fileStatuses   = pickList(fileStatuses,   (List<String>) filters.get("fileStatuses"));
        matchedStatuses= pickList(matchedStatuses,(List<String>) filters.get("matchedStatuses"));
        developers     = pickList(developers,     (List<String>) filters.get("developers"));
        publishers     = pickList(publishers,     (List<String>) filters.get("publishers"));
        genres         = pickList(genres,         (List<String>) filters.get("genres"));
        players        = pickList(players,        (List<String>) filters.get("players"));
    }

    private static boolean isBlank(String s) { return s == null || s.isEmpty(); }

    private static List<String> pickList(List<String> current, List<String> incoming) {
        if (current != null && !current.isEmpty()) return current;
        return (incoming != null && !incoming.isEmpty()) ? incoming : null;
    }

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

    public Boolean getPreserveLocalData() {
        return preserveLocalData;
    }

    public void setPreserveLocalData(Boolean preserveLocalData) {
        this.preserveLocalData = preserveLocalData;
    }

    // 筛选参数 Getters and Setters
    public String getSearch() { return search; }
    public void setSearch(String search) { this.search = search; }

    public List<String> getScrapeStatuses() { return scrapeStatuses; }
    public void setScrapeStatuses(List<String> scrapeStatuses) { this.scrapeStatuses = scrapeStatuses; }

    public List<String> getFileStatuses() { return fileStatuses; }
    public void setFileStatuses(List<String> fileStatuses) { this.fileStatuses = fileStatuses; }

    public List<String> getMatchedStatuses() { return matchedStatuses; }
    public void setMatchedStatuses(List<String> matchedStatuses) { this.matchedStatuses = matchedStatuses; }

    public List<String> getDevelopers() { return developers; }
    public void setDevelopers(List<String> developers) { this.developers = developers; }

    public List<String> getPublishers() { return publishers; }
    public void setPublishers(List<String> publishers) { this.publishers = publishers; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }

    public List<String> getPlayers() { return players; }
    public void setPlayers(List<String> players) { this.players = players; }

    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }

    /**
     * 判断是否携带筛选参数（“应用于所有搜索结果”模式）
     */
    public boolean hasFilters() {
        return (search != null && !search.isEmpty())
                || (scrapeStatuses != null && !scrapeStatuses.isEmpty())
                || (fileStatuses != null && !fileStatuses.isEmpty())
                || (matchedStatuses != null && !matchedStatuses.isEmpty())
                || (developers != null && !developers.isEmpty())
                || (publishers != null && !publishers.isEmpty())
                || (genres != null && !genres.isEmpty())
                || (players != null && !players.isEmpty())
                || (folderPath != null && !folderPath.isEmpty());
    }
}