package com.gamelist.model;

public class MergeOperation {
    private Long id;
    private Long primaryPlatformId;
    private Long secondaryPlatformId;
    private String status;
    private String createdAt;
    private String updatedAt;
    private String completedAt;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPrimaryPlatformId() {
        return primaryPlatformId;
    }

    public void setPrimaryPlatformId(Long primaryPlatformId) {
        this.primaryPlatformId = primaryPlatformId;
    }

    public Long getSecondaryPlatformId() {
        return secondaryPlatformId;
    }

    public void setSecondaryPlatformId(Long secondaryPlatformId) {
        this.secondaryPlatformId = secondaryPlatformId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }
}