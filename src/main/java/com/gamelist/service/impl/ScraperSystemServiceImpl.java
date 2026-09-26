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

    /**
     * 自代理：自调用 @Async 方法会绕过 Spring 代理导致同步执行，
     * 必须通过代理对象调用才能真正异步（否则刮削会阻塞 HTTP 请求）。
     */
    @Autowired
    @org.springframework.context.annotation.Lazy
    private ScraperSystemServiceImpl self;

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
            
            self.scrapeSystemAsync(task.getId(), systemId, regions, mediaTypes, username, password);
            
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
    public void scrapeSystemAsync(Long taskId, Integer systemId, List<String> regions, List<String> mediaTypes, String username, String password) {
        try {
            taskService.updateTaskProgress(taskId, 10, "开始刮削系统", 0, 100);
            
            int totalRegions = regions.size();
            int totalMediaTypes = mediaTypes.size();
            int totalTasks = totalRegions * totalMediaTypes;
            int completedTasks = 0;
            
            int successCount = 0;
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
                                successCount++;
                            }
                        }
                    }
                }
            }
            
            taskService.updateTaskProgress(taskId, 100, "刮削完成", totalTasks, totalTasks);
            taskService.completeTask(taskId, "刮削系统完成", "");
            
            // 只有实际下载成功时才标记系统媒体已刮削
            if (successCount > 0) {
                markAsScraped(systemId);
            } else {
                logger.warn("系统媒体刮削完成但无有效下载，不标记为已刮削: systemId={}", systemId);
            }
            
        } catch (Exception e) {
            logger.error("刮削系统失败: {}", e.getMessage(), e);
            taskService.failTask(taskId, "刮削失败", e.getMessage());
        }
    }
    
    private String downloadAndSaveMedia(String url, Integer systemId, String region, String mediaType) {
        try {
            java.io.File baseDir = new java.io.File(com.gamelist.util.PathUtil.getDataPath() + "/scraper/system/" + systemId + "/" + region + "/" + mediaType);
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }
            
            String fileName = mediaType + ".png";
            java.io.File outputFile = new java.io.File(baseDir, fileName);
            
            java.net.URL downloadUrl = new java.net.URL(url);
            // 先下载到临时文件，验证后再移动
            java.io.File tempFile = new java.io.File(baseDir, fileName + ".tmp");
            try (java.io.InputStream is = downloadUrl.openStream();
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            
            // 检查是否为 NOMEDIA 响应（ScreenScraper 无媒体时返回 7 字节 "NOMEDIA"）
            if (tempFile.length() <= 100) {
                byte[] content = java.nio.file.Files.readAllBytes(tempFile.toPath());
                String text = new String(content, java.nio.charset.StandardCharsets.UTF_8).trim();
                if ("NOMEDIA".equalsIgnoreCase(text) || content.length <= 7) {
                    tempFile.delete();
                    logger.warn("ScreenScraper 返回 NOMEDIA，跳过保存: systemId={}, type={}, region={}", systemId, mediaType, region);
                    return null;
                }
            }
            
            // 验证通过，移动临时文件到最终位置
            if (tempFile.renameTo(outputFile)) {
                return outputFile.getAbsolutePath();
            } else {
                tempFile.delete();
                logger.warn("重命名临时文件失败: {}", tempFile.getAbsolutePath());
                return null;
            }
        } catch (Exception e) {
            logger.error("下载媒体文件失败: url={}, error={}", url, e.getMessage());
            return null;
        }
    }
    
    @Override
    public void markAsScraped(Integer systemId) {
        try {
            scraperSystemMapper.updateMediaScraped(systemId, true);
            logger.info("已标记系统媒体已刮削: systemId={}", systemId);
        } catch (Exception e) {
            logger.warn("标记系统媒体刮削状态失败: systemId={}, error={}", systemId, e.getMessage());
        }
    }
    
    @Override
    public boolean isMediaScraped(Integer systemId) {
        try {
            ScraperSystem system = scraperSystemMapper.selectBySystemId(systemId);
            return system != null && Boolean.TRUE.equals(system.getMediaScraped());
        } catch (Exception e) {
            logger.warn("查询系统媒体刮削状态失败: systemId={}, error={}", systemId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public void scrapeSystemIcon(Integer systemId) {
        scrapeSystemIcon(systemId, null);
    }

    @Override
    public void scrapeSystemIcon(Integer systemId, String targetRegion) {
        if (systemId == null) {
            return;
        }
        
        // 检查是否已刮削且文件实际存在
        if (isMediaScraped(systemId) && hasIconOnDisk(systemId)) {
            logger.debug("系统 icon 已存在，跳过下载: systemId={}", systemId);
            return;
        }
        
        try {
            logger.info("开始自动下载系统 icon: systemId={}, targetRegion={}", systemId, targetRegion);
            Map<String, String> scraperSettings = scraperSettingsService.getSettings();
            String username = scraperSettings.get("username");
            String password = scraperSettings.get("password");
            
            // ScreenScraper 系统媒体类型: wheel, wheel-carbon, logo-svg, logo-monochrome 等
            String[] mediaTypes = {"wheel", "wheel-carbon", "logo-svg", "logo-monochrome"};
            
            // 如果指定了目标区域，只尝试该区域；否则按优先级 fallback
            String[] regions = (targetRegion != null && !targetRegion.isEmpty())
                    ? new String[]{targetRegion}
                    : new String[]{"wor", "us", "eu", "jp"};
            
            boolean downloaded = false;
            for (String mediaType : mediaTypes) {
                if (downloaded) break;
                for (String region : regions) {
                    if (downloaded) break;
                    
                    Map<String, Object> mediaResult = screenScraperApiService.fetchSystemMedia(
                            systemId, region, java.util.Collections.singletonList(mediaType), username, password);
                    
                    if ((Boolean) mediaResult.get("success")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> mediaUrls = (List<Map<String, String>>) mediaResult.get("mediaUrls");
                        
                        for (Map<String, String> mediaInfo : mediaUrls) {
                            String url = mediaInfo.get("url");
                            String reg = mediaInfo.get("region");
                            
                            // 下载并保存为 icon.png（前端统一查找 icon 类型）
                            String filePath = downloadAndSaveAsIcon(url, systemId, reg);
                            if (filePath != null) {
                                logger.info("系统 icon 下载成功 (类型={}, 区域={}): {}", mediaType, reg, filePath);
                                downloaded = true;
                                break;
                            }
                        }
                    }
                }
            }
            
            if (downloaded) {
                markAsScraped(systemId);
            } else {
                logger.warn("ScreenScraper 无此系统的可用 icon/logo/screen: systemId={}", systemId);
            }
        } catch (Exception e) {
            logger.warn("自动下载系统 icon 失败: systemId={}, error={}", systemId, e.getMessage());
        }
    }
    
    /**
     * 下载并保存为 icon.png 或 icon.svg（前端统一查找 icon 类型）
     */
    private String downloadAndSaveAsIcon(String url, Integer systemId, String region) {
        try {
            java.io.File baseDir = new java.io.File(com.gamelist.util.PathUtil.getDataPath() + "/scraper/system/" + systemId + "/" + region + "/icon");
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }
            
            // 从 URL 推断文件格式（logo-svg 返回 SVG，其他返回 PNG）
            String extension = "png";
            if (url != null && (url.contains("logo-svg") || url.contains(".svg"))) {
                extension = "svg";
            }
            
            java.io.File tempFile = new java.io.File(baseDir, "icon." + extension + ".tmp");
            java.net.URL downloadUrl = new java.net.URL(url);
            try (java.io.InputStream is = downloadUrl.openStream();
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            
            // 检查 NOMEDIA
            if (tempFile.length() <= 100) {
                byte[] content = java.nio.file.Files.readAllBytes(tempFile.toPath());
                String text = new String(content, java.nio.charset.StandardCharsets.UTF_8).trim();
                if ("NOMEDIA".equalsIgnoreCase(text) || content.length <= 7) {
                    tempFile.delete();
                    return null;
                }
            }
            
            // 检测实际内容格式（以防 URL 推断不准确）
            byte[] head = new byte[Math.min(200, (int) tempFile.length())];
            try (java.io.FileInputStream fis = new java.io.FileInputStream(tempFile)) {
                fis.read(head);
            }
            String headStr = new String(head, java.nio.charset.StandardCharsets.UTF_8).trim();
            boolean isPng = head.length >= 4 && head[0] == (byte) 0x89 && head[1] == (byte) 0x50 && head[2] == (byte) 0x4E && head[3] == (byte) 0x47; // \x89PNG
            boolean isJpeg = head.length >= 3 && head[0] == (byte) 0xFF && head[1] == (byte) 0xD8 && head[2] == (byte) 0xFF; // \xFF\xD8\xFF
            if (headStr.startsWith("<svg") || headStr.startsWith("<?xml")) {
                extension = "svg";
            } else if (!isPng && !isJpeg && !headStr.startsWith("<")) {
                // 不是 SVG、PNG 或 JPEG，可能是 NOMEDIA 文本
                tempFile.delete();
                return null;
            }
            
            java.io.File outputFile = new java.io.File(baseDir, "icon." + extension);
            if (tempFile.renameTo(outputFile)) {
                return outputFile.getAbsolutePath();
            } else {
                tempFile.delete();
                return null;
            }
        } catch (Exception e) {
            logger.warn("下载 icon 失败: url={}, error={}", url, e.getMessage());
            return null;
        }
    }
    
    /**
     * 检查系统 icon 文件是否实际存在于磁盘（检查多个区域）
     */
    private boolean hasIconOnDisk(Integer systemId) {
        String[] regions = {"wor", "us", "eu", "jp"};
        try {
            for (String region : regions) {
                java.io.File iconDir = new java.io.File(com.gamelist.util.PathUtil.getDataPath() + "/scraper/system/" + systemId + "/" + region + "/icon");
                if (iconDir.exists() && iconDir.isDirectory()) {
                    java.io.File[] files = iconDir.listFiles((dir, name) -> {
                        String lower = name.toLowerCase();
                        return lower.startsWith("icon.") && !lower.endsWith(".tmp");
                    });
                    if (files != null) {
                        for (java.io.File f : files) {
                            if (f.length() > 100) { // 排除 NOMEDIA 等无效文件
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("检查 icon 文件是否存在失败: systemId={}, error={}", systemId, e.getMessage());
        }
        return false;
    }
    
    @Override
    public Map<String, Object> scrapeSystemAllMedia(Integer systemId) {
        return scrapeSystemAllMedia(systemId, null);
    }
    
    @Override
    public Map<String, Object> scrapeSystemAllMedia(Integer systemId, String targetRegion) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            ScraperSystem system = scraperSystemMapper.selectBySystemId(systemId);
            if (system == null) {
                throw new IllegalArgumentException("刮削系统不存在: systemId=" + systemId);
            }
            
            Map<String, String> scraperSettings = scraperSettingsService.getSettings();
            String username = scraperSettings.get("username");
            String password = scraperSettings.get("password");
            
            String regionDesc = targetRegion != null ? " (区域: " + targetRegion + ")" : " (所有区域)";
            String taskDescription = "刮削系统媒体: " + system.getName() + regionDesc + " (systemId: " + systemId + ")";
            BackgroundTask task = taskService.createTask("SCRAPE", taskDescription);
            
            self.scrapeSystemAllMediaAsync(task.getId(), systemId, username, password, targetRegion);
            
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
    public void scrapeSystemAllMediaAsync(Long taskId, Integer systemId, String username, String password, String targetRegion) {
        try {
            taskService.updateTaskProgress(taskId, 0, "正在获取系统信息...", 0, 0);
            
            Map<String, Object> details = screenScraperApiService.fetchSystemDetails(systemId, username, password);
            
            if (!(Boolean) details.get("success")) {
                taskService.failTask(taskId, "获取系统信息失败", (String) details.get("message"));
                return;
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, String>> mediaList = (List<Map<String, String>>) details.get("mediaList");
            
            // 按目标区域过滤
            if (targetRegion != null && !targetRegion.isEmpty()) {
                List<Map<String, String>> filtered = new java.util.ArrayList<>();
                for (Map<String, String> media : mediaList) {
                    String region = media.get("region");
                    if (targetRegion.equalsIgnoreCase(region)) {
                        filtered.add(media);
                    }
                }
                logger.info("区域过滤: {} -> {} (目标区域: {})", mediaList.size(), filtered.size(), targetRegion);
                mediaList = filtered;
            }
            
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
            
            // 标记系统媒体已刮削
            markAsScraped(systemId);
            
        } catch (Exception e) {
            logger.error("刮削系统失败: {}", e.getMessage(), e);
            taskService.failTask(taskId, "刮削失败", e.getMessage());
        }
    }
    
    private String downloadMediaWithFormat(String url, Integer systemId, String region, String mediaType, String format) {
        try {
            String regionPath = (region != null && !region.isEmpty()) ? region : "wor";
            java.io.File baseDir = new java.io.File(com.gamelist.util.PathUtil.getDataPath() + "/scraper/system/" + systemId + "/" + regionPath + "/" + mediaType);
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