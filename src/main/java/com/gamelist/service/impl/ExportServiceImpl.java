package com.gamelist.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.gamelist.mapper.GameMapper;
import com.gamelist.model.ExportRequest;
import com.gamelist.model.ExportRule;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.service.DataFileGenerator;
import com.gamelist.service.ExportRuleService;
import com.gamelist.service.ExportService;
import com.gamelist.service.PlatformService;
import com.gamelist.service.TaskService;

@Service
public class ExportServiceImpl implements ExportService {
    private static final Logger logger = LoggerFactory.getLogger(ExportServiceImpl.class);

    @Autowired
    private GameMapper gameMapper;

    @Autowired
    private ExportRuleService exportRuleService;

    @Autowired
    private PlatformService platformService;

    @Autowired
    private TaskService taskService;

    // 默认线程池大小
    private static final int DEFAULT_THREAD_POOL_SIZE = 4;
    // 最大线程池大小
    private static final int MAX_THREAD_POOL_SIZE = 10;

    @Override
    public Map<String, Object> exportPlatform(ExportRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 记录导出请求参数
            logger.info("Starting platform export with request: {}", request);
            
            String outputPath = request.getOutputPath() != null ? request.getOutputPath() : "/data/output";
            Long platformId = request.getPlatformId();
            String frontend = request.getFrontend();
            final int threadCount;
            {
                int tempThreadCount = request.getThreadCount() > 0 ? request.getThreadCount() : DEFAULT_THREAD_POOL_SIZE;
                // 确保线程数在合理范围内
                tempThreadCount = Math.min(tempThreadCount, MAX_THREAD_POOL_SIZE);
                tempThreadCount = Math.max(tempThreadCount, 1);
                threadCount = tempThreadCount;
            }

            logger.info("Export parameters - platformId: {}, frontend: {}, outputPath: {}, threadCount: {}", 
                platformId, frontend, outputPath, threadCount);
            logger.info("Export options - copyRoms: {}, copyMedia: {}, generateDataFile: {}", 
                request.isCopyRoms(), request.isCopyMedia(), request.isGenerateDataFile());

            // 获取平台信息
            logger.info("Getting platform information for ID: {}", platformId);
            Platform platform = platformService.getPlatformById(platformId);
            if (platform == null) {
                logger.error("Platform not found: {}", platformId);
                result.put("success", false);
                result.put("error", "Platform not found");
                return result;
            }
            
            final String platformName;
            {
                String tempPlatformName = platform.getName();
                if (tempPlatformName == null || tempPlatformName.isEmpty()) {
                    tempPlatformName = "platform_" + platformId;
                    logger.warn("Platform name is empty, using default: {}", tempPlatformName);
                }
                platformName = tempPlatformName;
            }
            logger.info("Platform information: id={}, name={}, folderPath={}", 
                platform.getId(), platform.getName(), platform.getFolderPath());

            // 加载规则
            logger.info("Loading export rules");
            exportRuleService.loadRules();
            logger.info("Getting export rule for frontend: {}", frontend);
            ExportRule rule = exportRuleService.getRuleByFrontend(frontend);

            if (rule == null) {
                logger.error("Export rule not found for frontend: {}", frontend);
                result.put("success", false);
                result.put("error", "Export rule not found for frontend: " + frontend);
                return result;
            }
            logger.info("Found export rule for frontend: {}", frontend);

            // 创建任务
            String taskDescription = "导出平台: " + platformName + " (" + frontend + ")";
            final com.gamelist.model.BackgroundTask task = taskService.createTask("export", taskDescription);
            logger.info("Created task with ID: {}", task.getId());

            // 在线程中执行导出操作
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.submit(() -> {
                try {
                    // 更新任务状态为运行中
                    taskService.updateTaskProgress(task.getId(), 0, "开始导出平台", 0, 100);

                    // 创建目标目录结构
                    taskService.updateTaskProgress(task.getId(), 10, "创建目标目录结构", 10, 100);
                    createDirectoryStructure(rule, platformId, outputPath, platformName);

                    // 复制游戏文件
                        int progress = 10;
                        if (request.isCopyRoms()) {
                            taskService.updateTaskProgress(task.getId(), 20, "开始复制游戏文件", 20, 100);
                            copyGameFiles(platformId, outputPath, frontend, platformName, threadCount, task.getId());
                            progress += 30;
                            taskService.updateTaskProgress(task.getId(), progress, "游戏文件复制完成", progress, 100);
                        } else {
                            logger.info("Skipping game file copy (disabled in options)");
                        }

                        // 复制媒体文件
                        if (request.isCopyMedia()) {
                            taskService.updateTaskProgress(task.getId(), progress, "开始复制媒体文件", progress, 100);
                            copyMediaFiles(platformId, outputPath, frontend, platformName, threadCount, task.getId());
                            progress += 30;
                            taskService.updateTaskProgress(task.getId(), progress, "媒体文件复制完成", progress, 100);
                        } else {
                            logger.info("Skipping media file copy (disabled in options)");
                        }

                    // 生成数据文件
                    if (request.isGenerateDataFile()) {
                        taskService.updateTaskProgress(task.getId(), progress, "开始生成数据文件", progress, 100);
                        generateDataFile(platformId, outputPath, frontend, platformName);
                        progress += 30;
                        taskService.updateTaskProgress(task.getId(), progress, "数据文件生成完成", progress, 100);
                    } else {
                        logger.info("Skipping data file generation (disabled in options)");
                    }

                    // 完成任务
                    taskService.completeTask(task.getId(), "平台导出成功", "导出路径: " + outputPath);
                    logger.info("Platform export completed successfully");
                } catch (Exception e) {
                    logger.error("Export platform failed", e);
                    taskService.failTask(task.getId(), "平台导出失败", e.getMessage());
                }
            });

            // 关闭线程池
            executorService.shutdown();

            // 返回任务ID给前端
            result.put("success", true);
            result.put("message", "导出任务已启动");
            result.put("taskId", task.getId());
        } catch (Exception e) {
            logger.error("Export platform failed", e);
            result.put("success", false);
            result.put("error", "Export failed: " + e.getMessage());
        }
        return result;
    }

    @Override
    public void copyGameFiles(Long platformId, String targetPath, String frontend, String platformName, int threadCount) {
        copyGameFiles(platformId, targetPath, frontend, platformName, threadCount, null);
    }

    @Override
    public void copyGameFiles(Long platformId, String targetPath, String frontend, String platformName, int threadCount, Long taskId) {
        try {
            // 获取平台下的所有游戏
            List<Game> games = gameMapper.selectGamesByPlatformId(platformId);
            logger.info("Found {} games for platform {}", games.size(), platformId);
            
            // 加载导出规则
            ExportRule rule = exportRuleService.getRuleByFrontend(frontend);
            if (rule == null) {
                logger.error("Export rule not found for frontend: {}", frontend);
                return;
            }
            
            // 打印第一个游戏的信息用于调试
            if (games.size() > 0) {
                Game firstGame = games.get(0);
                logger.info("First game info - name: {}, path: {}, absolutePath: {}, platformPath: {}", 
                    firstGame.getName(), firstGame.getPath(), firstGame.getAbsolutePath(), firstGame.getPlatformPath());
            }
            
            // 构建目标目录
            Map<String, String> variables = new HashMap<>();
            variables.put("outputPath", targetPath);
            variables.put("platform", platformName);
            
            // 获取平台信息，添加平台相关变量
            Platform platform = platformService.getPlatformById(platformId);
            if (platform != null) {
                variables.put("platform.system", platform.getSystem());
                variables.put("platform.name", platform.getName());
                variables.put("platform.launch", platform.getLaunch());
                variables.put("platform.software", platform.getSoftware());
                variables.put("platform.database", platform.getDatabase());
                variables.put("platform.web", platform.getWeb());
            }
            
            String romsPath = replaceVariables(rule.getRules().getDirectory().getRoms(), variables);
            logger.info("Copying game files to: {}", romsPath);
            
            // 创建目标目录
            Files.createDirectories(Paths.get(romsPath));
            
            // 实现游戏文件复制逻辑
            // 使用线程池处理文件复制，提高性能
            if (games != null && !games.isEmpty()) {
                // 使用传入的线程数
                logger.info("Using {} threads for game file copy", threadCount);
                
                ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
                
                for (Game game : games) {
                    executorService.submit(() -> {
                        try {
                            Path sourcePath = null;
                            
                            // 尝试从absolutePath获取源路径
                            if (game.getAbsolutePath() != null && !game.getAbsolutePath().isEmpty()) {
                                sourcePath = Paths.get(game.getAbsolutePath());
                                logger.debug("Using absolutePath for game {}: {}", game.getName(), game.getAbsolutePath());
                            } 
                            // 如果absolutePath为空，尝试从platformPath和path构建
                            else if (game.getPlatformPath() != null && !game.getPlatformPath().isEmpty() 
                                && game.getPath() != null && !game.getPath().isEmpty()) {
                                sourcePath = Paths.get(game.getPlatformPath(), game.getPath());
                                logger.debug("Using platformPath + path for game {}: {} + {}", 
                                    game.getName(), game.getPlatformPath(), game.getPath());
                            } else {
                                logger.warn("No valid path found for game: {} (absolutePath: {}, platformPath: {}, path: {})", 
                                    game.getName(), game.getAbsolutePath(), game.getPlatformPath(), game.getPath());
                                return;
                            }
                            
                            if (Files.exists(sourcePath)) {
                                // 目标路径：目标路径 + PATH 列
                                String targetFileName = game.getPath();
                                if (targetFileName == null || targetFileName.isEmpty()) {
                                    targetFileName = sourcePath.getFileName().toString();
                                }
                                
                                // 检查是否配置了游戏文件重命名
                                if (rule != null && rule.getRules() != null && rule.getRules().getGameFile() != null && rule.getRules().getGameFile().isEnabled()) {
                                    String template = rule.getRules().getGameFile().getTemplate();
                                    if (template != null && !template.isEmpty()) {
                                        // 构建变量映射
                                        Map<String, String> renameVariables = new HashMap<>();
                                        renameVariables.put("platform", platformName);
                                        renameVariables.put("name", game.getName() != null ? game.getName().replaceAll("[<>\"/\\|?*]", "_") : "");
                                        renameVariables.put("filename", getSingleGameFieldValue(game, "filename") != null ? getSingleGameFieldValue(game, "filename") : "");
                                        // 获取扩展名
                                        String ext = "";
                                        if (targetFileName.contains(".")) {
                                            ext = targetFileName.substring(targetFileName.lastIndexOf(".") + 1);
                                        }
                                        renameVariables.put("ext", ext);
                                        
                                        // 替换模板中的变量
                                        String newFileName = template;
                                        for (Map.Entry<String, String> entry : renameVariables.entrySet()) {
                                            if (entry.getValue() != null) {
                                                newFileName = newFileName.replace("{" + entry.getKey() + "}", entry.getValue());
                                            }
                                        }
                                        // 确保扩展名正确
                                        if (!newFileName.endsWith("." + ext)) {
                                            newFileName = newFileName + "." + ext;
                                        }
                                        targetFileName = newFileName;
                                        logger.info("Game file renamed: {} -> {}", sourcePath.getFileName(), targetFileName);
                                    }
                                }
                                
                                Path targetFilePath = Paths.get(romsPath, targetFileName);
                                copyFile(sourcePath, targetFilePath);
                                logger.info("Copied game file: {} -> {}", sourcePath.getFileName(), targetFileName);
                                // 添加详细日志到任务
                                if (taskId != null) {
                                    taskService.updateTaskLog(taskId, "复制游戏文件: " + sourcePath.toString() + " -> " + targetFilePath.toString());
                                }
                                
                                // 处理 M3U 文件
                                if (targetFileName.toLowerCase().endsWith(".m3u")) {
                                    // 检查导出规则中是否启用了 M3U 处理
                                    if (rule != null && rule.getRules() != null && rule.getRules().getM3u() != null && rule.getRules().getM3u().isEnabled()) {
                                        processM3UFile(sourcePath, targetFilePath, romsPath, rule, game);
                                    } else {
                                        // 如果没有启用 M3U 处理，使用默认方式
                                        processM3UFile(sourcePath, targetFilePath, romsPath);
                                    }
                                }
                            } else {
                                logger.warn("Source file does not exist: {}", sourcePath);
                            }
                        } catch (Exception e) {
                            logger.error("Failed to copy game file: {}", game.getName(), e);
                        }
                    });
                }
                
                // 关闭线程池
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(60, TimeUnit.MINUTES)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            logger.error("Copy game files failed", e);
        }
    }

    @Override
    public void copyMediaFiles(Long platformId, String targetPath, String frontend, String platformName, int threadCount) {
        copyMediaFiles(platformId, targetPath, frontend, platformName, threadCount, null);
    }

    @Override
    public void copyMediaFiles(Long platformId, String targetPath, String frontend, String platformName, int threadCount, Long taskId) {
        try {
            // 加载导出规则
            ExportRule rule = exportRuleService.getRuleByFrontend(frontend);
            if (rule == null) {
                logger.error("Export rule not found for frontend: {}", frontend);
                return;
            }
            
            // 构建目标目录
            Map<String, String> variables = new HashMap<>();
            variables.put("outputPath", targetPath);
            variables.put("platform", platformName);
            
            // 获取平台信息，添加平台相关变量
            Platform platform = platformService.getPlatformById(platformId);
            if (platform != null) {
                variables.put("platform.system", platform.getSystem());
                variables.put("platform.name", platform.getName());
                variables.put("platform.launch", platform.getLaunch());
                variables.put("platform.software", platform.getSoftware());
                variables.put("platform.database", platform.getDatabase());
                variables.put("platform.web", platform.getWeb());
            }
            
            String mediaPath = replaceVariables(rule.getRules().getDirectory().getMedia(), variables);
            variables.put("mediaPath", mediaPath);
            logger.info("Copying media files to: {}", mediaPath);
            
            // 创建目标目录
            Files.createDirectories(Paths.get(mediaPath));
            
            // 实现媒体文件复制逻辑
            // 获取平台下的所有游戏
            List<Game> games = gameMapper.selectGamesByPlatformId(platformId);
            logger.info("Found {} games for media export", games.size());
            
            if (games != null && !games.isEmpty()) {
                // 使用传入的线程数
                logger.info("Using {} threads for media file copy", threadCount);
                logger.info("Media rules size: {}", rule.getRules().getMedia().size());
                for (String key : rule.getRules().getMedia().keySet()) {
                    logger.info("Media rule key: {}", key);
                }
                
                ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
                
                for (Game game : games) {
                    executorService.submit(() -> {
                        try {
                            // 创建线程局部的变量映射，避免线程安全问题
                            Map<String, String> threadVariables = new HashMap<>(variables);
                            // 使用 filename 替代 gameName
                            String filename = getSingleGameFieldValue(game, "filename");
                            if (filename == null) {
                                filename = game.getName();
                            }
                            threadVariables.put("filename", filename);
                            threadVariables.put("gameName", filename);
                            
                            // 处理每种媒体类型
                            for (Map.Entry<String, ExportRule.MediaRule> entry : rule.getRules().getMedia().entrySet()) {
                                ExportRule.MediaRule mediaRule = entry.getValue();
                                String sourceField = mediaRule.getSource();
                                String targetTemplate = mediaRule.getTarget();
                                logger.info("Processing media type: {}, source: {}, target: {}", entry.getKey(), sourceField, targetTemplate);
                                
                                // 从游戏对象中获取媒体文件相对路径
                                String mediaRelativePath = getMediaFilePathFromGame(game, sourceField);
                                logger.info("Media field: {}, value: {}", sourceField, mediaRelativePath);
                                
                                if (mediaRelativePath != null && !mediaRelativePath.isEmpty()) {
                                    // 原路径：PLATFORM_PATH + 媒体文件相对路径
                                    String platformPath = game.getPlatformPath();
                                    if (platformPath != null && !platformPath.isEmpty()) {
                                        Path sourcePath = Paths.get(platformPath, mediaRelativePath);
                                        logger.debug("Media file source path: {}", sourcePath);
                                        if (Files.exists(sourcePath)) {
                                            // 目标路径：根据规则配置来拼接
                                            String targetPathStr = replaceVariables(targetTemplate, threadVariables);
                                            Path targetFilePath = Paths.get(targetPathStr);
                                            copyFile(sourcePath, targetFilePath);
                                            logger.info("Copied media file: {} -> {}", sourcePath.getFileName(), targetFilePath.getFileName());
                                            // 添加详细日志到任务
                                            if (taskId != null) {
                                                taskService.updateTaskLog(taskId, "复制媒体文件: " + sourcePath.toString() + " -> " + targetFilePath.toString());
                                            }
                                        } else {
                                            logger.warn("Media file does not exist: {}", sourcePath);
                                        }
                                    } else {
                                        logger.warn("Platform path is null or empty for game: {}", game.getName());
                                    }
                                } else {
                                    logger.info("Media relative path is null or empty for game: {}, source field: {}", game.getName(), sourceField);
                                }
                            }
                        } catch (Exception e) {
                            logger.error("Failed to copy media files for game: {}", game.getName(), e);
                        }
                    });
                }
                
                // 关闭线程池
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(60, TimeUnit.MINUTES)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            logger.error("Copy media files failed", e);
        }
    }

    @Override
    public void generateDataFile(Long platformId, String targetPath, String frontend, String platformName) {
        try {
            // 加载导出规则
            ExportRule rule = exportRuleService.getRuleByFrontend(frontend);
            if (rule == null) {
                logger.error("Export rule not found for frontend: {}", frontend);
                return;
            }
            
            // 获取平台信息
            Platform platform = platformService.getPlatformById(platformId);
            if (platform == null) {
                logger.error("Platform not found: {}", platformId);
                return;
            }
            
            // 构建目标文件路径
            Map<String, String> variables = new HashMap<>();
            variables.put("outputPath", targetPath);
            variables.put("platform", platformName);
            variables.put("platform.system", platform.getSystem());
            variables.put("platform.name", platform.getName());
            variables.put("platform.launch", platform.getLaunch());
            variables.put("platform.software", platform.getSoftware());
            variables.put("platform.database", platform.getDatabase());
            variables.put("platform.web", platform.getWeb());
            
            String romsPath = replaceVariables(rule.getRules().getDirectory().getRoms(), variables);
            String dataFileName = rule.getRules().getDataFile().getFilename();
            Path dataFilePath = Paths.get(romsPath, dataFileName);
            
            logger.info("Generating data file: {}", dataFilePath);
            
            // 创建目标目录
            Files.createDirectories(dataFilePath.getParent());
            
            // 计算媒体目录路径
            String mediaPath = replaceVariables(rule.getRules().getDirectory().getMedia(), variables);
            variables.put("mediaPath", mediaPath);
            
            // 实现数据文件生成逻辑
            // 获取平台下的所有游戏
            List<Game> games = gameMapper.selectGamesByPlatformId(platformId);
            
            // 根据格式选择合适的生成器
            DataFileGenerator generator = null;
            String format = rule.getRules().getDataFile().getFormat();
            if ("text".equals(format) || "pegasus".equals(format)) {
                generator = new TextDataFileGenerator();
            } else if ("xml".equals(format) || "gamelist".equals(format)) {
                generator = new XmlDataFileGenerator();
            }
            
            if (generator != null) {
                generator.generateDataFile(games, dataFilePath, rule, platform, variables);
            } else {
                logger.error("Unsupported data file format: {}", format);
            }
        } catch (Exception e) {
            logger.error("Generate data file failed", e);
        }
    }

    private void createDirectoryStructure(ExportRule rule, Long platformId, String outputPath, String platformName) throws IOException {
        // 构建变量映射
        Map<String, String> variables = new HashMap<>();
        variables.put("outputPath", outputPath);
        variables.put("platform", platformName);
        
        // 获取平台信息，添加平台相关变量
        Platform platform = platformService.getPlatformById(platformId);
        if (platform != null) {
            logger.info("Platform found: {}", platform.getName());
            logger.info("Platform system: {}", platform.getSystem());
            variables.put("platform.system", platform.getSystem());
            variables.put("platform.name", platform.getName());
            variables.put("platform.launch", platform.getLaunch());
            variables.put("platform.software", platform.getSoftware());
            variables.put("platform.database", platform.getDatabase());
            variables.put("platform.web", platform.getWeb());
        } else {
            logger.warn("Platform not found for ID: {}", platformId);
        }
        
        // 日志记录变量映射
        logger.info("Variables: {}", variables);
        
        // 日志记录原始路径模板
        logger.info("Original ROMs path template: {}", rule.getRules().getDirectory().getRoms());
        logger.info("Original media path template: {}", rule.getRules().getDirectory().getMedia());
        
        // 创建ROMs目录
        String romsPath = replaceVariables(rule.getRules().getDirectory().getRoms(), variables);
        logger.info("Replaced ROMs path: {}", romsPath);
        Files.createDirectories(Paths.get(romsPath));
        logger.info("Created ROMs directory: {}", romsPath);
        
        // 创建媒体目录
        String mediaPath = replaceVariables(rule.getRules().getDirectory().getMedia(), variables);
        logger.info("Replaced media path: {}", mediaPath);
        Files.createDirectories(Paths.get(mediaPath));
        logger.info("Created media directory: {}", mediaPath);
        
        // 创建媒体子目录
        for (Map.Entry<String, ExportRule.MediaRule> entry : rule.getRules().getMedia().entrySet()) {
            ExportRule.MediaRule mediaRule = entry.getValue();
            String targetTemplate = mediaRule.getTarget();
            variables.put("gameName", "placeholder"); // 临时占位符
            String targetPathStr = replaceVariables(targetTemplate, variables);
            Path targetPath = Paths.get(targetPathStr);
            Files.createDirectories(targetPath.getParent());
        }
    }



    private String getGameFieldValue(Game game, String fieldName) {
        // 支持多值匹配，用逗号分隔
        String[] fieldNames = fieldName.split(",");
        for (String name : fieldNames) {
            name = name.trim();
            String value = getSingleGameFieldValue(game, name);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }
    
    private String getSingleGameFieldValue(Game game, String fieldName) {
        switch (fieldName) {
            case "name":
                return game.getName();
            case "translatedName":
                return game.getTranslatedName();
            case "description":
                return game.getDesc();
            case "translatedDesc":
                return game.getTranslatedDesc();
            case "rating":
                return game.getRating() != null ? game.getRating().toString() : null;
            case "releaseYear":
                String releaseDate = game.getReleasedate();
                if (releaseDate != null && releaseDate.length() >= 4) {
                    return releaseDate.substring(0, 4);
                }
                return null;
            case "developer":
                return game.getDeveloper();
            case "publisher":
                return game.getPublisher();
            case "genre":
                return game.getGenre();
            case "players":
                return game.getPlayers();
            case "region":
                return game.getLang();
            case "filename":
                // 从path中提取文件名，去掉扩展名和前面的路径
                String path = game.getPath();
                if (path != null) {
                    // 提取文件名
                    int lastSlashIndex = path.lastIndexOf('/');
                    int lastBackslashIndex = path.lastIndexOf('\\');
                    int lastSeparatorIndex = Math.max(lastSlashIndex, lastBackslashIndex);
                    String fileName = lastSeparatorIndex >= 0 ? path.substring(lastSeparatorIndex + 1) : path;
                    // 去掉扩展名
                    int lastDotIndex = fileName.lastIndexOf('.');
                    if (lastDotIndex >= 0) {
                        fileName = fileName.substring(0, lastDotIndex);
                    }
                    return fileName;
                }
                return null;
            default:
                return null;
        }
    }

    private String getMediaFilePathFromGame(Game game, String sourceField) {
        switch (sourceField) {
            case "box2dfront":
                return game.getBoxFront();
            case "box2dback":
                return game.getBoxBack();
            case "box3d":
                return game.getBox3d();
            case "screenshot":
                return game.getScreenshot();
            case "video":
                return game.getVideo();
            case "wheel":
                return game.getLogo(); // 使用 logo 作为 wheel
            case "marquee":
                return game.getMarquee();
            case "fanart":
                return game.getFanart();
            default:
                return null;
        }
    }

    private String getRelativePath(Path basePath, Path targetPath) {
        try {
            // 尝试计算相对路径
            return basePath.relativize(targetPath).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            // 如果路径类型不同，尝试使用字符串处理
            logger.warn("Failed to relativize paths: {}", e.getMessage());
            
            // 转换为字符串并确保使用相同的分隔符
            String basePathStr = basePath.toString().replace('\\', '/');
            String targetPathStr = targetPath.toString().replace('\\', '/');
            
            // 检查 targetPath 是否是相对路径
            if (!targetPathStr.startsWith("/")) {
                // 如果是相对路径，直接返回
                return targetPathStr;
            }
            
            // 尝试找到共同的前缀
            int minLength = Math.min(basePathStr.length(), targetPathStr.length());
            int commonPrefixLength = 0;
            
            while (commonPrefixLength < minLength && basePathStr.charAt(commonPrefixLength) == targetPathStr.charAt(commonPrefixLength)) {
                commonPrefixLength++;
            }
            
            // 找到最后一个斜杠的位置
            int lastSlashIndex = basePathStr.lastIndexOf('/', commonPrefixLength);
            if (lastSlashIndex == -1) {
                lastSlashIndex = 0;
            }
            
            // 构建相对路径
            StringBuilder relativePath = new StringBuilder();
            
            // 添加向上的路径
            String remainingBase = basePathStr.substring(lastSlashIndex);
            int slashCount = remainingBase.length() - remainingBase.replace("/", "").length();
            for (int i = 0; i < slashCount; i++) {
                relativePath.append("../");
            }
            
            // 添加目标路径的剩余部分
            relativePath.append(targetPathStr.substring(lastSlashIndex));
            
            return relativePath.toString().replace('\\', '/');
        }
    }

    private String escapeXml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private void copyFile(Path source, Path target) throws IOException {
        // 确保目标目录存在
        Files.createDirectories(target.getParent());
        // 复制文件
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private String replaceVariables(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue();
            if (value != null) {
                result = result.replace("{" + entry.getKey() + "}", value);
            }
        }
        return result;
    }
    
    /**
     * 处理 M3U 文件，复制文件中引用的所有文件
     */
    private void processM3UFile(Path sourceM3UPath, Path targetM3UPath, String romsPath) throws IOException {
        processM3UFile(sourceM3UPath, targetM3UPath, romsPath, null, null);
    }
    
    /**
     * 处理 M3U 文件，复制文件中引用的所有文件（支持导出规则）
     */
    private void processM3UFile(Path sourceM3UPath, Path targetM3UPath, String romsPath, ExportRule rule, Game game) throws IOException {
        // 读取 M3U 文件内容
        List<String> lines = Files.readAllLines(sourceM3UPath);
        List<String> updatedLines = new ArrayList<>();
        
        // 获取 M3U 文件所在的目录
        Path sourceM3UDir = sourceM3UPath.getParent();
        Path targetM3UDir = targetM3UPath.getParent();
        
        for (String line : lines) {
            // 跳过注释行和空行
            if (line.startsWith("#") || line.trim().isEmpty()) {
                updatedLines.add(line);
                continue;
            }
            
            // 解析文件路径
            Path filePath;
            if (line.startsWith("/")) {
                // 绝对路径
                filePath = Paths.get(line);
            } else {
                // 相对路径，相对于 M3U 文件的目录
                filePath = sourceM3UDir.resolve(line).normalize();
            }
            
            // 检查文件是否存在
            if (Files.exists(filePath)) {
                // 计算目标文件路径
                Path targetFilePath;
                if (rule != null && rule.getRules() != null && rule.getRules().getM3u() != null && rule.getRules().getM3u().getTarget() != null) {
                    // 使用导出规则中指定的目标路径模板
                    Map<String, String> variables = new HashMap<>();
                    variables.put("romsPath", romsPath);
                    if (game != null) {
                        // 使用 filename 替代 gameName
                        String filename = getSingleGameFieldValue(game, "filename");
                        if (filename == null) {
                            filename = game.getName();
                        }
                        variables.put("filename", filename);
                    }
                    String targetTemplate = rule.getRules().getM3u().getTarget();
                    String resolvedTarget = replaceVariables(targetTemplate, variables);
                    // 保持相对于 M3U 文件的相对路径
                    Path relativePath = sourceM3UDir.relativize(filePath);
                    targetFilePath = Paths.get(resolvedTarget, relativePath.toString());
                } else {
                    // 保持相对于 M3U 文件的相对路径
                    Path relativePath = sourceM3UDir.relativize(filePath);
                    targetFilePath = targetM3UDir.resolve(relativePath);
                }
                
                // 复制文件
                copyFile(filePath, targetFilePath);
                logger.info("Copied M3U referenced file: {} -> {}", filePath.getFileName(), targetFilePath.getFileName());
                
                // 更新 M3U 文件中的路径为相对路径
                updatedLines.add(sourceM3UDir.relativize(filePath).toString());
            } else {
                logger.warn("M3U referenced file does not exist: {}", filePath);
                updatedLines.add(line);
            }
        }
        
        // 写回更新后的 M3U 文件
        Files.write(targetM3UPath, updatedLines);
        logger.info("Updated M3U file: {}", targetM3UPath);
    }
}
