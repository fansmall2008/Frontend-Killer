package com.gamelist.model;

import java.util.Date;

/**
 * 统一刮削任务池实体
 * 支持三种任务类型：游戏信息刮削、媒体下载、街机自动下载
 * 通过 priority 字段实现优先级调度（数值越小越先执行）
 */
public class ScrapeTask {
    private Long id;
    private String taskType;        // 'GAME_INFO' | 'MEDIA_DOWNLOAD'
    private Long gameId;
    private Long platformId;
    
    // GAME_INFO 专用字段
    private String romFilename;     // ROM 文件名（用于 SS API 搜索）
    private Integer systemId;       // SS 系统 ID
    private Long ssGameId;          // SS 游戏 ID（街机自动下载/导出缓存场景）
    private String mediaScope;      // 媒体范围（GAME_INFO 任务用）："*"=全部, "ss,box-2D"=指定类型, null=不刮媒体
    
    // MEDIA_DOWNLOAD 专用字段
    private String mediaType;       // 媒体类型（nomcourt 值）
    private String mediaRegion;     // 媒体区域（wor/us/eu/jp）
    private String downloadUrl;     // 下载 URL
    private String localPath;       // 本地存储路径
    private Long fileSize;          // 文件大小
    
    // 通用字段
    private String status;          // PENDING/RUNNING/COMPLETED/FAILED/STOPPED
    private int priority;           // 用户刮削=0, 媒体下载=10, 街机自动=50
    private long orderIndex;        // 同优先级内的排序
    private int retryCount;
    private String errorMessage;
    private Date createdAt;
    private Date updatedAt;

    // 状态常量
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_STOPPED = "STOPPED";

    // 任务类型常量
    public static final String TYPE_GAME_INFO = "GAME_INFO";
    public static final String TYPE_MEDIA_DOWNLOAD = "MEDIA_DOWNLOAD";

    // 优先级常量
    public static final int PRIORITY_USER_SCRAPE = 0;      // 用户触发刮削（最高优先级）
    public static final int PRIORITY_MEDIA_DOWNLOAD = 10;   // 媒体下载
    public static final int PRIORITY_AUTO_SCRAPE = 50;      // 街机自动下载（最低优先级）

    public ScrapeTask() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public Long getGameId() { return gameId; }
    public void setGameId(Long gameId) { this.gameId = gameId; }

    public Long getPlatformId() { return platformId; }
    public void setPlatformId(Long platformId) { this.platformId = platformId; }

    public String getRomFilename() { return romFilename; }
    public void setRomFilename(String romFilename) { this.romFilename = romFilename; }

    public Integer getSystemId() { return systemId; }
    public void setSystemId(Integer systemId) { this.systemId = systemId; }

    public Long getSsGameId() { return ssGameId; }
    public void setSsGameId(Long ssGameId) { this.ssGameId = ssGameId; }

    public String getMediaScope() { return mediaScope; }
    public void setMediaScope(String mediaScope) { this.mediaScope = mediaScope; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public String getMediaRegion() { return mediaRegion; }
    public void setMediaRegion(String mediaRegion) { this.mediaRegion = mediaRegion; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public long getOrderIndex() { return orderIndex; }
    public void setOrderIndex(long orderIndex) { this.orderIndex = orderIndex; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "ScrapeTask{id=" + id + ", type=" + taskType + ", gameId=" + gameId + 
               ", priority=" + priority + ", status=" + status + "}";
    }
}
