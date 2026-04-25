package com.gamelist.model;

import java.util.List;

public class ExportRequest {
    private Long platformId;
    private String frontend;
    private String outputPath;
    private boolean copyRoms;
    private boolean copyMedia;
    private boolean generateDataFile;
    private int threadCount;

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public String getFrontend() {
        return frontend;
    }

    public void setFrontend(String frontend) {
        this.frontend = frontend;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public boolean isCopyRoms() {
        return copyRoms;
    }

    public void setCopyRoms(boolean copyRoms) {
        this.copyRoms = copyRoms;
    }

    public boolean isCopyMedia() {
        return copyMedia;
    }

    public void setCopyMedia(boolean copyMedia) {
        this.copyMedia = copyMedia;
    }

    public boolean isGenerateDataFile() {
        return generateDataFile;
    }

    public void setGenerateDataFile(boolean generateDataFile) {
        this.generateDataFile = generateDataFile;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }
}
