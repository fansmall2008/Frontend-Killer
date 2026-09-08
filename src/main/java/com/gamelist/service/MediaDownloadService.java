package com.gamelist.service;

import java.util.Map;

public interface MediaDownloadService {
    void startMediaDownloadTask(Long taskId, int maxThreads);
    void stopMediaDownloadTask();
    void pauseMediaDownloadTask();
    void resumeMediaDownloadTask();
    boolean isRunning();
    boolean isPaused();
    
    void stopPlatformDownload(Long platformId);
    void resumePlatformDownload(Long platformId);
    void retryFailedDownloads(Long platformId);
    void stopAllPlatformDownload();
    Map<String, Object> getPlatformDownloadStats(Long platformId);
    long countDownloadingByPlatformId(Long platformId);
}
