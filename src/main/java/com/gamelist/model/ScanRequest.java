package com.gamelist.model;

/**
 * 扫描请求模型
 * 用于接收前端提交的扫描请求，包含扫描路径和深度
 */
public class ScanRequest {
    private String path;
    private int scanDepth;
    
    // getters and setters
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public int getScanDepth() {
        return scanDepth;
    }
    
    public void setScanDepth(int scanDepth) {
        this.scanDepth = scanDepth;
    }
}