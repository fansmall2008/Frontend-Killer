package com.gamelist.model;

import java.util.Date;

/**
 * 通知中心实体：持久化所有后台事件（任务创建/完成/失败、刮削、媒体下载、配额警告等）。
 * 铃铛角标与通知面板读取本表，新事件通过 SSE 实时推送。
 */
public class Notification {

    private Long id;
    private String title;
    private String message;
    /** 通知类型：success / warning / error / info */
    private String type;
    /** 事件来源：task / scraper / media-download 等 */
    private String source;
    private Boolean read;
    private Date createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Boolean getRead() {
        return read;
    }

    public void setRead(Boolean read) {
        this.read = read;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
