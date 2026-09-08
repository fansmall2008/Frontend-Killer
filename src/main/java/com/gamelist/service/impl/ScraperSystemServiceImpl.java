package com.gamelist.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gamelist.mapper.ScraperSystemMapper;
import com.gamelist.model.BackgroundTask;
import com.gamelist.model.ScraperSystem;
import com.gamelist.service.ScraperSettingsService;
import com.gamelist.service.ScraperSystemService;
import com.gamelist.service.ScreenScraperApiService;
import com.gamelist.service.TaskService;

@Service
public class ScraperSystemServiceImpl implements ScraperSystemService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScraperSystemServiceImpl.class);
    
    @Autowired
    private ScraperSystemMapper scraperSystemMapper;
    
    @Autowired
    private ScreenScraperApiService screenScraperApiService;
    
    @Autowired
    private ScraperSettingsService scraperSettingsService;
    
    @Autowired
    private TaskService taskService;

    @Override
    @Transactional
    public ScraperSystem save(ScraperSystem system) {
        logger.info("保存刮削系统: systemId={}, name={}", system.getSystemId(), system.getName());
        
        if (system.getId() != null) {
            int updated = scraperSystemMapper.update(system);
            logger.info("更新刮削系统完成，更新记录数: {}", updated);
        } else {
            scraperSystemMapper.insert(system);
            logger.info("插入刮削系统完成，ID: {}", system.getId());
        }
        
        return system;
    }

    @Override
    @Transactional
    public ScraperSystem update(ScraperSystem system) {
        logger.info("更新刮削系统: id={}, systemId={}", system.getId(), system.getSystemId());
        int updated = scraperSystemMapper.update(system);
        logger.info("更新完成，影响行数: {}", updated);
        return scraperSystemMapper.selectById(system.getId());
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        logger.info("删除刮削系统: id={}", id);
        scraperSystemMapper.deleteById(id);
        logger.info("删除完成");
    }

    @Override
    public ScraperSystem getById(Long id) {
        logger.debug("查询刮削系统: id={}", id);
        return scraperSystemMapper.selectById(id);
    }

    @Override
    public ScraperSystem getBySystemId(Integer systemId) {
        logger.debug("查询刮削系统: systemId={}", systemId);
        return scraperSystemMapper.selectBySystemId(systemId);
    }

    @Override
    public List<ScraperSystem> getAll() {
        logger.debug("查询所有刮削系统");
        return scraperSystemMapper.selectAll();
    }

    @Override
    @Transactional
    public int batchInsert(List<ScraperSystem> systems) {
        logger.info("批量插入/更新刮削系统，数量: {}", systems.size());
        int inserted = 0;
        int updated = 0;
        
        for (ScraperSystem system : systems) {
            try {
                ScraperSystem existing = scraperSystemMapper.selectBySystemId(system.getSystemId());
                if (existing != null) {
                    system.setId(existing.getId());
                    scraperSystemMapper.update(system);
                    updated++;
                } else {
                    scraperSystemMapper.insert(system);
                    inserted++;
                }
            } catch (Exception e) {
                logger.warn("处理刮削系统失败: systemId={}, error={}", system.getSystemId(), e.getMessage());
            }
        }
        logger.info("批量插入/更新完成，插入: {}, 更新: {}", inserted, updated);
        return inserted + updated;
    }

    @Override
    @Transactional
    public void clearAll() {
        logger.info("清空所有刮削系统");
        scraperSystemMapper.deleteAll();
        logger.info("清空完成");
    }

    @Override
    public Map<String, Object> scrapeSystem(Integer systemId, List<String> regions, List<String> mediaTypes) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            ScraperSystem system = scraperSystemMapper.selectBySystemId(systemId);
            if (system == null) {
                throw new IllegalArgumentException("刮削系统不存在: systemId=" + systemId);
            }
            
            Map<String, String> scraperSettings = scraperSettingsService.getSettings();
            String username = scraperSettings.get("username");
            String password = scraperSettings.get("password");
            
            String taskDescription = "刮削系统: " + system.getName() + " (systemId: " + systemId + ")";
            BackgroundTask task = taskService.createTask("SCRAPE", taskDescription);
            
            scrapeSystemAsync(task.getId(), systemId, regions, mediaTypes, username, password);
            
            result.put("success", true);
            result.put("taskId", task.getId());
            result.put("message", "刮削任务已启动，请在任务管理页面查看进度");
            
            return result;
        } catch (Exception e) {
            logger.error("刮削系统失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("errorMessage", e.getMessage());
            return result;
        }
    }
    
    @Async
    private void scrapeSystemAsync(Long taskId, Integer systemId, List<String> regions, List<String> mediaTypes, String username, String password) {
        try {
            taskService.updateTaskProgress(taskId, 10, "开始刮削系统", 0, 100);
            
            int totalRegions = regions.size();
            int totalMediaTypes = mediaTypes.size();
            int totalTasks = totalRegions * totalMediaTypes;
            int completedTasks = 0;
            
            for (String region : regions) {
                for (String mediaType : mediaTypes) {
                    completedTasks++;
                    int progress = 10 + (completedTasks * 80) / totalTasks;
                    taskService.updateTaskProgress(taskId, progress, "刮削: " + mediaType + " (" + region + ")", completedTasks, totalTasks);
                    
                    Map<String, Object> mediaResult = screenScraperApiService.fetchSystemMedia(systemId, region, java.util.Collections.singletonList(mediaType), username, password);
                    
                    if ((Boolean) mediaResult.get("success")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> mediaUrls = (List<Map<String, String>>) mediaResult.get("mediaUrls");
                        
                        for (Map<String, String> mediaInfo : mediaUrls) {
                            String url = mediaInfo.get("url");
                            String type = mediaInfo.get("type");
                            String reg = mediaInfo.get("region");
                            
                            String filePath = downloadAndSaveMedia(url, systemId, reg, type);
                            if (filePath != null) {
                                logger.info("媒体文件下载成功: {}", filePath);
                            }
                        }
                    }
                }
            }
            
            taskService.updateTaskProgress(taskId, 100, "刮削完成", totalTasks, totalTasks);
            taskService.completeTask(taskId, "刮削系统完成", "");
            
        } catch (Exception e) {
            logger.error("刮削系统失败: {}", e.getMessage(), e);
            taskService.failTask(taskId, "刮削失败", e.getMessage());
        }
    }
    
    private String downloadAndSaveMedia(String url, Integer systemId, String region, String mediaType) {
        try {
            java.io.File baseDir = new java.io.File("/data/scraper/system/" + systemId + "/" + region + "/" + mediaType);
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }
            
            String fileName = mediaType + ".png";
            java.io.File outputFile = new java.io.File(baseDir, fileName);
            
            java.net.URL downloadUrl = new java.net.URL(url);
            try (java.io.InputStream is = downloadUrl.openStream();
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            
            return outputFile.getAbsolutePath();
        } catch (Exception e) {
            logger.error("下载媒体文件失败: url={}, error={}", url, e.getMessage());
            return null;
        }
    }
    
    @Override
    @Transactional
    public Map<String, Object> scrapeSystemAllMedia(Integer systemId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            ScraperSystem system = scraperSystemMapper.selectBySystemId(systemId);
            if (system == null) {
                throw new IllegalArgumentException("刮削系统不存在: systemId=" + systemId);
            }
            
            Map<String, String> scraperSettings = scraperSettingsService.getSettings();
            String username = scraperSettings.get("username");
            String password = scraperSettings.get("password");
            
            String taskDescription = "刮削系统所有媒体: " + system.getName() + " (systemId: " + systemId + ")";
            BackgroundTask task = taskService.createTask("SCRAPE", taskDescription);
            
            scrapeSystemAllMediaAsync(task.getId(), systemId, username, password);
            
            result.put("success", true);
            result.put("taskId", task.getId());
            result.put("message", "刮削任务已启动，请在任务管理页面查看进度");
            
            return result;
        } catch (Exception e) {
            logger.error("刮削系统失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("errorMessage", e.getMessage());
            return result;
        }
    }
    
    @Async
    private void scrapeSystemAllMediaAsync(Long taskId, Integer systemId, String username, String password) {
        try {
            taskService.updateTaskProgress(taskId, 0, "正在获取系统信息...", 0, 0);
            
            Map<String, Object> details = screenScraperApiService.fetchSystemDetails(systemId, username, password);
            
            if (!(Boolean) details.get("success")) {
                taskService.failTask(taskId, "获取系统信息失败", (String) details.get("message"));
                return;
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, String>> mediaList = (List<Map<String, String>>) details.get("mediaList");
            int totalTasks = mediaList.size();
            
            if (totalTasks == 0) {
                taskService.completeTask(taskId, "刮削完成", "该系统没有可用的媒体文件");
                return;
            }
            
            taskService.updateTaskLog(taskId, "找到 " + totalTasks + " 个媒体文件");
            
            int successCount = 0;
            int failCount = 0;
            
            for (int i = 0; i < mediaList.size(); i++) {
                Map<String, String> media = mediaList.get(i);
                String url = media.get("url");
                String type = media.get("type");
                String region = media.get("region");
                String format = media.get("format");
                
                taskService.updateTaskProgress(taskId, (int) ((i * 100) / totalTasks), 
                    "正在下载 " + (i + 1) + "/" + totalTasks + ": " + type + " (" + region + ")", 
                    i + 1, totalTasks);
                
                String filePath = downloadMediaWithFormat(url, systemId, region, type, format);
                
                if (filePath != null) {
                    successCount++;
                    logger.info("媒体文件下载成功: {}", filePath);
                } else {
                    failCount++;
                    logger.warn("媒体文件下载失败: type={}, region={}", type, region);
                }
            }
            
            taskService.updateTaskProgress(taskId, 100, "刮削完成", totalTasks, totalTasks);
            taskService.completeTask(taskId, "刮削完成", "成功下载 " + successCount + " 个文件，失败 " + failCount + " 个");
            
        } catch (Exception e) {
            logger.error("刮削系统失败: {}", e.getMessage(), e);
            taskService.failTask(taskId, "刮削失败", e.getMessage());
        }
    }
    
    private String downloadMediaWithFormat(String url, Integer systemId, String region, String mediaType, String format) {
        try {
            String regionPath = (region != null && !region.isEmpty()) ? region : "wor";
            java.io.File baseDir = new java.io.File("/data/scraper/system/" + systemId + "/" + regionPath + "/" + mediaType);
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }
            
            String fileName = mediaType + "." + format;
            java.io.File outputFile = new java.io.File(baseDir, fileName);
            
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            
            okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String contentType = response.header("Content-Type", "");
                    if (contentType.startsWith("image/") || contentType.startsWith("video/") || contentType.startsWith("application/")) {
                        try (java.io.InputStream is = response.body().byteStream();
                             java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                        }
                        
                        if (outputFile.length() > 100) {
                            return outputFile.getAbsolutePath();
                        } else {
                            outputFile.delete();
                            logger.warn("下载的文件太小，可能是 NOMEDIA 响应: {}", url);
                            return null;
                        }
                    } else {
                        logger.warn("响应不是媒体文件: Content-Type={}, url={}", contentType, url);
                        return null;
                    }
                } else {
                    logger.warn("下载失败: HTTP {}, url={}", response.code(), url);
                    return null;
                }
            }
        } catch (Exception e) {
            logger.error("下载媒体文件失败: url={}, error={}", url, e.getMessage());
            return null;
        }
    }
}