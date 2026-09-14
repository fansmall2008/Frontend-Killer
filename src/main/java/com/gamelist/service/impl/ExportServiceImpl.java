package com.gamelist.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import com.gamelist.model.TemplateV3;
import com.gamelist.service.DataFileGenerator;
import com.gamelist.service.ExportRuleService;
import com.gamelist.service.ExportService;
import com.gamelist.service.PlatformService;
import com.gamelist.service.TaskService;
import com.gamelist.util.GameFieldAccessor;
import com.gamelist.util.TemplateExpressionEngine;

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

    @Autowired
    private ExportOrchestrator exportOrchestrator;

    // 默认线程池大小 - 根据CPU核心数设置
    private static final int DEFAULT_THREAD_POOL_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors());
    // 最大线程池大小
    private static final int MAX_THREAD_POOL_SIZE = Math.max(10, Runtime.getRuntime().availableProcessors() * 2);

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

            // v3 模板分发：如果该前端有 v3 模板，走 v3 导出路径
            if (exportRuleService.isV3Template(frontend)) {
                logger.info("Detected v3 template for frontend: {}, dispatching to ExportOrchestrator", frontend);
                TemplateV3 v3Template = exportRuleService.getV3RuleByFrontend(frontend);
                if (v3Template == null) {
                    result.put("success", false);
                    result.put("error", "v3 template not found for frontend: " + frontend);
                    return result;
                }

                String taskDescription = "导出平台(v3): " + platformName + " (" + frontend + ")";
                final com.gamelist.model.BackgroundTask task = taskService.createTask("export", taskDescription);
                logger.info("Created v3 export task with ID: {}", task.getId());

                ExecutorService executorService = Executors.newSingleThreadExecutor();
                executorService.submit(() -> {
                    try {
                        taskService.updateTaskProgress(task.getId(), 0, "开始 v3 导出", 0, 100);
                        exportOrchestrator.executeExport(v3Template, platform, request, task);
                    } catch (Exception e) {
                        logger.error("v3 export failed", e);
                        taskService.failTask(task.getId(), "v3 导出失败", e.getMessage());
                    }
                });
                executorService.shutdown();

                result.put("success", true);
                result.put("message", "v3 导出任务已启动");
                result.put("taskId", task.getId());
                return result;
            }

            // v2 路径：加载旧版 ExportRule
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
                
                // 预检查游戏文件重命名规则
                boolean enableRename = rule != null && rule.getRules() != null && 
                    rule.getRules().getGameFile() != null && rule.getRules().getGameFile().isEnabled();
                String renameTemplate = enableRename ? rule.getRules().getGameFile().getTemplate() : null;
                
                // 预检查M3U处理规则
                boolean enableM3U = rule != null && rule.getRules() != null && 
                    rule.getRules().getM3u() != null && rule.getRules().getM3u().isEnabled();
                
                for (Game game : games) {
                    executorService.submit(() -> {
                        try {
                            Path sourcePath = null;
                            String gameName = game.getName();
                            String absolutePath = game.getAbsolutePath();
                            String platformPath = game.getPlatformPath();
                            String gamePath = game.getPath();
                            
                            // 尝试从absolutePath获取源路径
                            if (absolutePath != null && !absolutePath.isEmpty()) {
                                sourcePath = Paths.get(absolutePath);
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Using absolutePath for game {}: {}", gameName, absolutePath);
                                }
                            } 
                            // 如果absolutePath为空，尝试从platformPath和path构建
                            else if (platformPath != null && !platformPath.isEmpty() 
                                && gamePath != null && !gamePath.isEmpty()) {
                                sourcePath = Paths.get(platformPath, gamePath);
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Using platformPath + path for game {}: {} + {}", 
                                        gameName, platformPath, gamePath);
                                }
                            } else {
                                if (logger.isWarnEnabled()) {
                                    logger.warn("No valid path found for game: {} (absolutePath: {}, platformPath: {}, path: {})", 
                                        gameName, absolutePath, platformPath, gamePath);
                                }
                                return;
                            }
                            
                            if (Files.exists(sourcePath)) {
                                // 目标路径：目标路径 + PATH 列
                                String targetFileName = gamePath;
                                if (targetFileName == null || targetFileName.isEmpty()) {
                                    targetFileName = sourcePath.getFileName().toString();
                                }
                                
                                // 检查是否配置了游戏文件重命名
                                if (enableRename && renameTemplate != null && !renameTemplate.isEmpty()) {
                                    // 构建变量映射
                                    Map<String, String> renameVariables = new HashMap<>();
                                    renameVariables.put("platform", platformName);
                                    renameVariables.put("name", gameName != null ? gameName.replaceAll("[<>\"/\\|?*]", "_") : "");
                                    renameVariables.put("filename", getSingleGameFieldValue(game, "filename") != null ? getSingleGameFieldValue(game, "filename") : "");
                                    // 获取扩展名
                                    String ext = "";
                                    if (targetFileName.contains(".")) {
                                        ext = targetFileName.substring(targetFileName.lastIndexOf(".") + 1);
                                    }
                                    renameVariables.put("ext", ext);
                                    
                                    // 使用表达式引擎或简单变量替换处理重命名模板
                                    String newFileName;
                                    if (TemplateExpressionEngine.isExpression(renameTemplate)) {
                                        TemplateExpressionEngine.Context ctx = new TemplateExpressionEngine.Context(game, platform, renameVariables);
                                        newFileName = TemplateExpressionEngine.evaluate(renameTemplate, ctx);
                                    } else {
                                        newFileName = renameTemplate;
                                        for (Map.Entry<String, String> entry : renameVariables.entrySet()) {
                                            if (entry.getValue() != null) {
                                                newFileName = newFileName.replace("{" + entry.getKey() + "}", entry.getValue());
                                            }
                                        }
                                    }
                                    // 确保扩展名正确
                                    if (!newFileName.endsWith("." + ext)) {
                                        newFileName = newFileName + "." + ext;
                                    }
                                    targetFileName = newFileName;
                                    if (logger.isInfoEnabled()) {
                                        logger.info("Game file renamed: {} -> {}", sourcePath.getFileName(), targetFileName);
                                    }
                                }
                                
                                Path targetFilePath = Paths.get(romsPath, targetFileName);
                                copyFile(sourcePath, targetFilePath);
                                
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Copied game file: {} -> {}", sourcePath.getFileName(), targetFileName);
                                }
                                
                                // 添加详细日志到任务
                                if (taskId != null) {
                                    taskService.updateTaskLog(taskId, "复制游戏文件: " + sourcePath.toString() + " -> " + targetFilePath.toString());
                                }
                                
                                // 处理 M3U 文件
                                if (targetFileName.toLowerCase().endsWith(".m3u")) {
                                    if (enableM3U) {
                                        processM3UFile(sourcePath, targetFilePath, romsPath, rule, game);
                                    } else {
                                        processM3UFile(sourcePath, targetFilePath, romsPath);
                                    }
                                }
                            } else {
                                if (logger.isWarnEnabled()) {
                                    logger.warn("Source file does not exist: {}", sourcePath);
                                }
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
                
                // 只在DEBUG模式下输出详细的媒体规则信息
                if (logger.isDebugEnabled()) {
                    logger.debug("Media rules size: {}", rule.getRules().getMedia().size());
                    for (String key : rule.getRules().getMedia().keySet()) {
                        logger.debug("Media rule key: {}", key);
                    }
                }
                
                ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
                
                // 预构建媒体规则列表，避免在循环中重复获取
                List<Map.Entry<String, ExportRule.MediaRule>> mediaRules = new ArrayList<>(rule.getRules().getMedia().entrySet());
                
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
                            for (Map.Entry<String, ExportRule.MediaRule> entry : mediaRules) {
                                ExportRule.MediaRule mediaRule = entry.getValue();
                                // v2: source 为空时使用 map key（即 nomcourt 值）
                                String sourceField = mediaRule.getSource();
                                if (sourceField == null || sourceField.isEmpty()) {
                                    sourceField = entry.getKey();
                                }
                                String targetTemplate = mediaRule.getTarget();
                                
                                // 只在DEBUG模式下输出处理信息
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Processing media type: {}, source: {}, target: {}", entry.getKey(), sourceField, targetTemplate);
                                }
                                
                                // 从游戏对象中获取媒体文件相对路径
                                String mediaRelativePath = getMediaFilePathFromGame(game, sourceField);
                                
                                if (mediaRelativePath != null && !mediaRelativePath.isEmpty()) {
                                    // 原路径：PLATFORM_PATH + 媒体文件相对路径
                                    String platformPath = game.getPlatformPath();
                                    if (platformPath != null && !platformPath.isEmpty()) {
                                        Path sourcePath = Paths.get(platformPath, mediaRelativePath);
                                        
                                        if (Files.exists(sourcePath)) {
                                            // 目标路径：根据规则配置来拼接
                                            String targetPathStr = replaceVariables(targetTemplate, threadVariables);
                                            Path targetFilePath = Paths.get(targetPathStr);
                                            copyFile(sourcePath, targetFilePath);
                                            
                                            // 只在DEBUG模式下输出复制信息
                                            if (logger.isDebugEnabled()) {
                                                logger.debug("Copied media file: {} -> {}", sourcePath.getFileName(), targetFilePath.getFileName());
                                            }
                                            
                                            // 添加详细日志到任务
                                            if (taskId != null) {
                                                taskService.updateTaskLog(taskId, "复制媒体文件: " + sourcePath.toString() + " -> " + targetFilePath.toString());
                                            }
                                        } else {
                                            if (logger.isWarnEnabled()) {
                                                logger.warn("Media file does not exist: {}", sourcePath);
                                            }
                                        }
                                    } else {
                                        if (logger.isWarnEnabled()) {
                                            logger.warn("Platform path is null or empty for game: {}", game.getName());
                                        }
                                    }
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
            } else if ("lpl".equals(format)) {
                // Lakka .lpl 格式需要额外处理
                generator = new LplDataFileGenerator();
            }
            
            if (generator != null) {
                // 对于 LPL 格式，委托给 LplDataFileGenerator
                if ("lpl".equals(format)) {
                    if (rule.getRules().getLplExport() != null && rule.getRules().getLplExport().isEnabled()) {
                        generator.generateDataFile(games, dataFilePath, rule, platform, variables);
                    } else {
                        logger.warn("LPL format configured but lplExport is not enabled");
                    }
                } else {
                    generator.generateDataFile(games, dataFilePath, rule, platform, variables);
                }
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
    
    /**
     * @deprecated 使用 {@link GameFieldAccessor#getValue(Game, String)} 替代
     */
    private String getSingleGameFieldValue(Game game, String fieldName) {
        return GameFieldAccessor.getValue(game, fieldName);
    }

    /**
     * 根据 nomcourt 值或旧版字段名获取媒体文件路径。
     * 优先通过 GameFieldAccessor（支持 nomcourt / dbColumn / javaField / 旧别名），
     * 回退到旧版 switch-case 兼容。
     */
    private String getMediaFilePathFromGame(Game game, String sourceField) {
        // 优先使用统一字段访问器（支持 nomcourt、dbColumn、javaField、旧别名）
        String value = GameFieldAccessor.getValue(game, sourceField);
        if (value != null) return value;
        // 回退：旧版 source 名称映射（兼容旧模板）
        return switch (sourceField.toLowerCase().replace("-", "").replace("_", "")) {
            case "box2dfront", "boxfront" -> game.getBoxFront();
            case "box2dback", "boxback" -> game.getBoxBack();
            case "box3d" -> game.getBox3D();
            case "screenshot" -> game.getScreenshot();
            case "video" -> game.getVideo();
            case "wheel" -> game.getLogo();
            case "marquee" -> game.getMarquee();
            case "fanart" -> game.getFanart();
            case "videonormalized" -> game.getVideonormalized();
            case "wheelcarbon" -> game.getWheelcarbon();
            case "wheelsteel" -> game.getWheelsteel();
            case "screenmarqueesmall" -> game.getScreenmarqueesmall();
            case "boxside" -> game.getBoxside();
            case "figurine" -> game.getFigurine();
            case "image" -> game.getImage();
            case "thumbnail" -> game.getThumbnail();
            case "logo" -> game.getLogo();
            case "background" -> game.getBackground();
            case "manual", "manuel" -> game.getManual();
            case "bezel" -> game.getBezel();
            case "steamgrid" -> game.getSteamgrid();
            default -> null;
        };
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
        
        // 使用更快的NIO复制方式，使用较大的缓冲区
        try (java.io.FileInputStream fis = new java.io.FileInputStream(source.toFile());
             java.io.FileOutputStream fos = new java.io.FileOutputStream(target.toFile());
             java.nio.channels.FileChannel inChannel = fis.getChannel();
             java.nio.channels.FileChannel outChannel = fos.getChannel()) {
            
            long size = inChannel.size();
            long position = 0;
            while (position < size) {
                position += inChannel.transferTo(position, 8192 * 1024, outChannel);
            }
        }
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
