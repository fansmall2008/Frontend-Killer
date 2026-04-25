package com.gamelist.model;

public class ImportStatistics {
    private int importedPlatforms;
    private int importedGames;
    
    public ImportStatistics() {
        this.importedPlatforms = 0;
        this.importedGames = 0;
    }
    
    public ImportStatistics(int importedPlatforms, int importedGames) {
        this.importedPlatforms = importedPlatforms;
        this.importedGames = importedGames;
    }
    
    public int getImportedPlatforms() {
        return importedPlatforms;
    }
    
    public void setImportedPlatforms(int importedPlatforms) {
        this.importedPlatforms = importedPlatforms;
    }
    
    public int getImportedGames() {
        return importedGames;
    }
    
    public void setImportedGames(int importedGames) {
        this.importedGames = importedGames;
    }
    
    public void incrementPlatforms() {
        this.importedPlatforms++;
    }
    
    public void incrementGames() {
        this.importedGames++;
    }
    
    public void addGames(int count) {
        this.importedGames += count;
    }
}