package com.gamelist.model;

public class Platform {
    private Long id;
    private String system;
    private String software;
    private String database;
    private String web;
    private String name;
    private String sortBy;
    private String launch;
    private String folderPath;
    private Integer systemId;
    private String systemRegion;
    private String logoRegion;
    private String logoType;
    private String createdAt;
    private String updatedAt;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public String getSoftware() {
        return software;
    }

    public void setSoftware(String software) {
        this.software = software;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getWeb() {
        return web;
    }

    public void setWeb(String web) {
        this.web = web;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getLaunch() {
        return launch;
    }

    public void setLaunch(String launch) {
        this.launch = launch;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public Integer getSystemId() {
        return systemId;
    }

    public void setSystemId(Integer systemId) {
        this.systemId = systemId;
    }

    public String getSystemRegion() {
        return systemRegion;
    }

    public void setSystemRegion(String systemRegion) {
        this.systemRegion = systemRegion;
    }

    public String getLogoRegion() {
        return logoRegion;
    }

    public void setLogoRegion(String logoRegion) {
        this.logoRegion = logoRegion;
    }

    public String getLogoType() {
        return logoType;
    }

    public void setLogoType(String logoType) {
        this.logoType = logoType;
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
}
