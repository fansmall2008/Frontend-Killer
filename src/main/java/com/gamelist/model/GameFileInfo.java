package com.gamelist.model;

import java.util.List;

public class GameFileInfo {
    private String gameName;
    private String crc32;
    private FileType fileType;
    private String filePath;
    private List<String> romFiles;
    private long fileSize;
    
    // Getters and Setters
    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public String getCrc32() {
        return crc32;
    }

    public void setCrc32(String crc32) {
        this.crc32 = crc32;
    }

    public FileType getFileType() {
        return fileType;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public List<String> getRomFiles() {
        return romFiles;
    }

    public void setRomFiles(List<String> romFiles) {
        this.romFiles = romFiles;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
}