package com.gamelist.model;

import java.util.Date;

public class BackgroundTask {
    private Long id;
    private String type; // IMPORT, EXPORT, TRANSLATE
    private String status; // PENDING, RUNNING, COMPLETED, FAILED
    private int progress;
    private String message;
    private long totalItems;
    private long processedItems;
    private Date startTime;
    private Date endTime;
    private String errorMessage;
    private String description;
    private String result;
    private String log; // 详细任务日志
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public int getProgress() {
        return progress;
    }
    public void setProgress(int progress) {
        this.progress = progress;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public long getTotalItems() {
        return totalItems;
    }
    public void setTotalItems(long totalItems) {
        this.totalItems = totalItems;
    }
    public long getProcessedItems() {
        return processedItems;
    }
    public void setProcessedItems(long processedItems) {
        this.processedItems = processedItems;
    }
    public Date getStartTime() {
        return startTime;
    }
    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }
    public Date getEndTime() {
        return endTime;
    }
    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getResult() {
        return result;
    }
    public void setResult(String result) {
        this.result = result;
    }
    public String getLog() {
        return log;
    }
    public void setLog(String log) {
        this.log = log;
    }
}