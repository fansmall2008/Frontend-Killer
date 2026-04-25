package com.gamelist.model;

import java.util.List;

public class ScanResult {
    private boolean success;
    private String message;
    private int foundFiles;
    private int importedFiles;
    private List<Detail> details;

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getFoundFiles() {
        return foundFiles;
    }

    public void setFoundFiles(int foundFiles) {
        this.foundFiles = foundFiles;
    }

    public int getImportedFiles() {
        return importedFiles;
    }

    public void setImportedFiles(int importedFiles) {
        this.importedFiles = importedFiles;
    }

    public List<Detail> getDetails() {
        return details;
    }

    public void setDetails(List<Detail> details) {
        this.details = details;
    }

    public static class Detail {
        private boolean success;
        private String message;
        private String filePath;
        private String type;

        // Getters and Setters
        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
