package com.gamelist.model;

import java.util.List;

/**
 * 带选择功能的扫描结果
 * 用于返回可选择的元数据文件列表
 */
public class ScanResultWithSelection {
    private List<ScanItem> items;
    private int totalFiles;
    private String message;
    private boolean success;
    
    // 扫描项
    public static class ScanItem {
        private String filePath;
        private String fileName;
        private String platformName;
        private int estimatedGameCount;
        private boolean selected;
        
        // 构造方法
        public ScanItem() {
        }
        
        public ScanItem(String filePath, String fileName, String platformName) {
            this.filePath = filePath;
            this.fileName = fileName;
            this.platformName = platformName;
            this.estimatedGameCount = 0;
            this.selected = true;
        }
        
        // getters and setters
        public String getFilePath() {
            return filePath;
        }
        
        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
        
        public String getFileName() {
            return fileName;
        }
        
        public void setFileName(String fileName) {
            this.fileName = fileName;
        }
        
        public String getPlatformName() {
            return platformName;
        }
        
        public void setPlatformName(String platformName) {
            this.platformName = platformName;
        }
        
        public int getEstimatedGameCount() {
            return estimatedGameCount;
        }
        
        public void setEstimatedGameCount(int estimatedGameCount) {
            this.estimatedGameCount = estimatedGameCount;
        }
        
        public boolean isSelected() {
            return selected;
        }
        
        public void setSelected(boolean selected) {
            this.selected = selected;
        }
    }
    
    // getters and setters
    public List<ScanItem> getItems() {
        return items;
    }
    
    public void setItems(List<ScanItem> items) {
        this.items = items;
    }
    
    public int getTotalFiles() {
        return totalFiles;
    }
    
    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
}