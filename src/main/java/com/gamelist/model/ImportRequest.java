package com.gamelist.model;

import java.util.List;

/**
 * 导入请求模型
 * 用于接收前端提交的导入请求，包含要导入的文件路径列表
 */
public class ImportRequest {
    private List<String> filePaths;
    private int scanDepth;
    
    // getters and setters
    public List<String> getFilePaths() {
        return filePaths;
    }
    
    public void setFilePaths(List<String> filePaths) {
        this.filePaths = filePaths;
    }
    
    public int getScanDepth() {
        return scanDepth;
    }
    
    public void setScanDepth(int scanDepth) {
        this.scanDepth = scanDepth;
    }
}