package com.gamelist.model;

import java.util.List;

public class ExportResult {
    private boolean success;
    private int platformsExported;
    private int collectionsExported;
    private int gamesExported;
    private int filesCopied;
    private int skippedGames;
    private int failedGames;
    private List<String> backupPaths;
    private String errorMessage;
    private String message;

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getPlatformsExported() {
        return platformsExported;
    }

    public void setPlatformsExported(int platformsExported) {
        this.platformsExported = platformsExported;
    }

    public int getCollectionsExported() {
        return collectionsExported;
    }

    public void setCollectionsExported(int collectionsExported) {
        this.collectionsExported = collectionsExported;
    }

    public int getGamesExported() {
        return gamesExported;
    }

    public void setGamesExported(int gamesExported) {
        this.gamesExported = gamesExported;
    }

    public int getFilesCopied() {
        return filesCopied;
    }

    public void setFilesCopied(int filesCopied) {
        this.filesCopied = filesCopied;
    }

    public int getSkippedGames() {
        return skippedGames;
    }

    public void setSkippedGames(int skippedGames) {
        this.skippedGames = skippedGames;
    }

    public int getFailedGames() {
        return failedGames;
    }

    public void setFailedGames(int failedGames) {
        this.failedGames = failedGames;
    }

    public List<String> getBackupPaths() {
        return backupPaths;
    }

    public void setBackupPaths(List<String> backupPaths) {
        this.backupPaths = backupPaths;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
