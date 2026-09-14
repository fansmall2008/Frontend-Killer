package com.gamelist.service.impl;

import java.io.File;
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
import org.springframework.stereotype.Component;

import com.gamelist.mapper.GameMapper;
import com.gamelist.model.BackgroundTask;
import com.gamelist.model.ExportRequest;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.model.TemplateV3;
import com.gamelist.service.PlatformService;
import com.gamelist.service.TaskService;
import com.gamelist.util.GameFieldAccessor;
import com.gamelist.util.TemplateExpressionEngine;

/**
 * v3 导出编排器 — 文件操作编排层。
 * <p>
 * 读取模板的 output 块 + 用户勾选 → 编排 ROM/媒体/数据文件的导出操作。
 * 所有路径和文件名均由 JSON 模板驱动，Java 引擎零硬编码。
 * <p>
 * 职责划分：
 * <ul>
 *   <li>ExportOrchestrator — 文件拷贝、目录创建、任务编排</li>
 *   <li>TemplateV3ExportService — 纯数据文件内容生成</li>
 * </ul>
 */
@Component
public class ExportOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(ExportOrchestrator.class);

    @Autowired
    private GameMapper gameMapper;

    @Autowired
    private PlatformService platformService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TemplateV3ExportService v3ExportService;

    /**
     * 执行 v3 导出。
     * <p>
     * 根据 output 块和用户勾选，依次执行：
     * <ol>
     *   <li>创建目录结构</li>
     *   <li>复制 ROM 文件（copyRoms && output.roms.enabled）</li>
     *   <li>复制媒体文件（copyMedia && output.media != null）</li>
     *   <li>生成数据文件（generateDataFile && output.dataFile != null）</li>
     * </ol>
     */
    public void executeExport(TemplateV3 template, Platform platform, ExportRequest request,
                              BackgroundTask task) {
        TemplateV3.OutputConfig output = template.getOutput();
        if (output == null) {
            taskService.failTask(task.getId(), "导出失败", "v3 模板缺少 output 配置块");
            return;
        }

        Long platformId = request.getPlatformId();
        final int threadCount = Math.max(1, Math.min(request.getThreadCount(), 10));

        // 构建基础变量
        Map<String, String> vars = buildBaseVariables(request.getOutputPath(), platform);

        // 获取游戏列表
        List<Game> games = gameMapper.selectGamesByPlatformId(platformId);
        logger.info("v3 导出: 平台={}, 游戏数={}, 线程数={}", platform.getName(), games.size(), threadCount);

        try {
            int progress = 0;

            // 1. 创建目录结构
            taskService.updateTaskProgress(task.getId(), 5, "创建目录结构", 0, 100);
            createDirectories(output, platform, vars);

            // 2. 复制 ROM 文件
            if (request.isCopyRoms() && output.getRoms() != null && output.getRoms().isEnabled()) {
                taskService.updateTaskProgress(task.getId(), 10, "开始复制游戏文件", 0, 100);
                copyGameFiles(games, output.getRoms(), platform, vars, threadCount, task.getId());
                progress = 40;
                taskService.updateTaskProgress(task.getId(), progress, "游戏文件复制完成", 0, 100);
            } else {
                logger.info("v3 导出: 跳过游戏文件复制");
            }

            // 3. 复制媒体文件
            if (request.isCopyMedia() && output.getMedia() != null && output.getMedia().getRules() != null) {
                taskService.updateTaskProgress(task.getId(), progress, "开始复制媒体文件", 0, 100);
                copyMediaFiles(games, output.getMedia(), platform, vars, threadCount, task.getId());
                progress = 70;
                taskService.updateTaskProgress(task.getId(), progress, "媒体文件复制完成", 0, 100);
            } else {
                logger.info("v3 导出: 跳过媒体文件复制");
            }

            // 4. 生成数据文件
            if (request.isGenerateDataFile() && output.getDataFile() != null) {
                taskService.updateTaskProgress(task.getId(), progress, "开始生成数据文件", 0, 100);
                generateDataFile(games, template, platform, vars);
                progress = 100;
                taskService.updateTaskProgress(task.getId(), progress, "数据文件生成完成", 0, 100);
            } else {
                logger.info("v3 导出: 跳过数据文件生成");
            }

            taskService.completeTask(task.getId(), "v3 导出完成", "导出路径: " + request.getOutputPath());
            logger.info("v3 导出完成: {}", platform.getName());

        } catch (Exception e) {
            logger.error("v3 导出失败", e);
            taskService.failTask(task.getId(), "v3 导出失败", e.getMessage());
        }
    }

    // ==================== 目录创建 ====================

    /**
     * 根据 output 块创建目标目录结构。
     */
    private void createDirectories(TemplateV3.OutputConfig output, Platform platform,
                                    Map<String, String> vars) throws Exception {
        // ROM 目录
        if (output.getRoms() != null && output.getRoms().getDirectory() != null) {
            String romsDir = resolveTemplateString(output.getRoms().getDirectory(), platform, vars);
            Files.createDirectories(Paths.get(romsDir));
            logger.info("创建 ROM 目录: {}", romsDir);
        }

        // 媒体目录
        if (output.getMedia() != null && output.getMedia().getDirectory() != null) {
            String mediaDir = resolveTemplateString(output.getMedia().getDirectory(), platform, vars);
            Files.createDirectories(Paths.get(mediaDir));
            logger.info("创建媒体目录: {}", mediaDir);
        }

        // 数据文件目录
        if (output.getDataFile() != null && output.getDataFile().getDirectory() != null) {
            String dataDir = resolveTemplateString(output.getDataFile().getDirectory(), platform, vars);
            Files.createDirectories(Paths.get(dataDir));
            logger.info("创建数据文件目录: {}", dataDir);
        }
    }

    // ==================== ROM 文件复制 ====================

    /**
     * 复制 ROM 文件到目标目录。
     * <p>
     * 文件名由 output.roms.filename 模板驱动（支持表达式引擎）。
     * 默认 "{filename}{ext}" 表示保持原名。
     */
    private void copyGameFiles(List<Game> games, TemplateV3.RomOutput romsConfig,
                                Platform platform, Map<String, String> vars,
                                int threadCount, Long taskId) {
        String romsDir = resolveTemplateString(romsConfig.getDirectory(), platform, vars);
        String rawFilenameTemplate = romsConfig.getFilename();
        final String filenameTemplate = (rawFilenameTemplate == null || rawFilenameTemplate.isEmpty())
                ? "{filename}{ext}" : rawFilenameTemplate;

        logger.info("复制 ROM 文件: {} 个游戏 → {}, 文件名模板: {}", games.size(), romsDir, filenameTemplate);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        boolean enableM3U = romsConfig.getM3u() != null && romsConfig.getM3u().isEnabled();

        for (Game game : games) {
            executor.submit(() -> {
                try {
                    copySingleGameFile(game, romsDir, filenameTemplate, platform, vars, enableM3U, romsConfig, taskId);
                } catch (Exception e) {
                    logger.error("复制 ROM 文件失败: {}", game.getName(), e);
                }
            });
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 复制单个游戏的 ROM 文件。
     */
    private void copySingleGameFile(Game game, String romsDir, String filenameTemplate,
                                     Platform platform, Map<String, String> vars,
                                     boolean enableM3U, TemplateV3.RomOutput romsConfig,
                                     Long taskId) throws Exception {
        // 确定源路径
        Path sourcePath = resolveSourcePath(game);
        if (sourcePath == null || !Files.exists(sourcePath)) {
            if (sourcePath != null) {
                logger.warn("ROM 文件不存在: {}", sourcePath);
            }
            return;
        }

        // 计算目标文件名
        Map<String, String> gameVars = buildGameVariables(game, vars);
        String targetFileName = evaluateFilenameTemplate(filenameTemplate, game, platform, gameVars, sourcePath);

        Path targetPath = Paths.get(romsDir, targetFileName);
        copyFile(sourcePath, targetPath);

        if (taskId != null) {
            taskService.updateTaskLog(taskId, "复制 ROM: " + sourcePath.getFileName() + " → " + targetFileName);
        }

        // 处理 M3U 文件
        if (enableM3U && targetFileName.toLowerCase().endsWith(".m3u")) {
            processM3UFile(sourcePath, targetPath, romsDir, romsConfig, game, platform, gameVars);
        }
    }

    // ==================== 媒体文件复制 ====================

    /**
     * 复制媒体文件到目标目录。
     * <p>
     * 遍历 output.media.rules，每种媒体类型独立处理。
     * 目标路径由 mediaDirectory + rule.target 模板求值。
     */
    private void copyMediaFiles(List<Game> games, TemplateV3.MediaOutput mediaConfig,
                                 Platform platform, Map<String, String> vars,
                                 int threadCount, Long taskId) {
        String mediaDir = resolveTemplateString(mediaConfig.getDirectory(), platform, vars);
        Map<String, TemplateV3.MediaOutputRule> rules = mediaConfig.getRules();

        logger.info("复制媒体文件: {} 个游戏, {} 种媒体类型 → {}", games.size(), rules.size(), mediaDir);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (Game game : games) {
            executor.submit(() -> {
                try {
                    Map<String, String> gameVars = buildGameVariables(game, vars);

                    for (Map.Entry<String, TemplateV3.MediaOutputRule> entry : rules.entrySet()) {
                        String nomcourt = entry.getKey();
                        TemplateV3.MediaOutputRule rule = entry.getValue();

                        // 从 Game 对象获取媒体相对路径
                        String mediaRelativePath = GameFieldAccessor.getValue(game, nomcourt);
                        if (mediaRelativePath == null || mediaRelativePath.isEmpty()) continue;

                        // 清理路径：统一分隔符，去掉 ./ 前缀
                        String cleanMediaPath = mediaRelativePath.replace("\\", "/");
                        if (cleanMediaPath.startsWith("./")) {
                            cleanMediaPath = cleanMediaPath.substring(2);
                        }

                        // 存在性检测解析源文件路径（与 MediaController.resolveMediaPath 一致）
                        Path sourcePath = null;
                        // 策略1: 工作目录 + 路径（刮削/上传媒体，如 data/scraper/games/...）
                        Path workDirPath = Paths.get(cleanMediaPath).toAbsolutePath().normalize();
                        if (Files.exists(workDirPath)) {
                            sourcePath = workDirPath;
                        }
                        // 策略2: platformPath + 路径（导入媒体，如 media/xxx/...）
                        if (sourcePath == null) {
                            String platformPath = game.getPlatformPath();
                            if (platformPath != null && !platformPath.isEmpty()) {
                                Path platformRelativePath = Paths.get(platformPath, cleanMediaPath).normalize();
                                if (Files.exists(platformRelativePath)) {
                                    sourcePath = platformRelativePath;
                                }
                            }
                        }
                        if (sourcePath == null) {
                            logger.debug("媒体文件不存在（双基准均未命中）: {}", mediaRelativePath);
                            continue;
                        }

                        // 目标路径 = mediaDirectory + resolvedTarget
                        String resolvedTarget = resolveTemplateString(rule.getTarget(), platform, gameVars);
                        if (resolvedTarget == null || resolvedTarget.isEmpty()) continue;

                        Path targetPath = Paths.get(mediaDir, resolvedTarget);
                        copyFile(sourcePath, targetPath);

                        if (taskId != null) {
                            taskService.updateTaskLog(taskId,
                                "复制媒体: " + nomcourt + " " + sourcePath.getFileName() + " → " + resolvedTarget);
                        }
                    }
                } catch (Exception e) {
                    logger.error("复制媒体文件失败: {}", game.getName(), e);
                }
            });
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 数据文件生成 ====================

    /**
     * 生成数据文件，委托给 TemplateV3ExportService。
     */
    private void generateDataFile(List<Game> games, TemplateV3 template,
                                   Platform platform, Map<String, String> vars) throws Exception {
        v3ExportService.exportToFile(games, template, platform, vars);
    }

    // ==================== 变量构建 ====================

    /**
     * 构建基础变量（平台级 + 用户输入）。
     */
    private Map<String, String> buildBaseVariables(String outputPath, Platform platform) {
        Map<String, String> vars = new HashMap<>();
        vars.put("outputPath", outputPath != null ? outputPath : "/data/output");

        if (platform != null) {
            vars.put("platform.system", nvl(platform.getSystem()));
            vars.put("platform.name", nvl(platform.getName()));
            vars.put("platform.launch", nvl(platform.getLaunch()));
            vars.put("platform.software", nvl(platform.getSoftware()));
            vars.put("platform.database", nvl(platform.getDatabase()));
            vars.put("platform.web", nvl(platform.getWeb()));
            vars.put("platform", nvl(platform.getName())); // 兼容旧模板 {platform}
        }

        return vars;
    }

    /**
     * 构建游戏级变量（在基础变量上叠加 filename/gameName/ext 等）。
     */
    private Map<String, String> buildGameVariables(Game game, Map<String, String> baseVars) {
        Map<String, String> gameVars = new HashMap<>(baseVars);

        // filename: 从 path 字段计算（去目录、去扩展名）
        String filename = GameFieldAccessor.getValue(game, "filename");
        String ext = "";
        if (filename == null || filename.isEmpty()) {
            String path = game.getPath();
            if (path != null && !path.isEmpty()) {
                int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                String fileNameWithExt = sep >= 0 ? path.substring(sep + 1) : path;
                int dot = fileNameWithExt.lastIndexOf('.');
                if (dot > 0) {
                    ext = fileNameWithExt.substring(dot); // 含 . 的扩展名
                    filename = fileNameWithExt.substring(0, dot);
                } else {
                    filename = fileNameWithExt;
                }
            } else {
                filename = game.getName();
            }
        } else {
            // 从 path 提取 ext
            String path = game.getPath();
            if (path != null) {
                int dot = path.lastIndexOf('.');
                if (dot > 0) {
                    ext = path.substring(dot);
                }
            }
        }

        // 清理文件名中的非法字符
        String cleanFilename = filename != null ? filename.replaceAll("[<>\"/\\\\|?*]", "_") : "";

        gameVars.put("filename", cleanFilename);
        gameVars.put("gameName", game.getName() != null ? game.getName() : "");
        gameVars.put("name", game.getName() != null ? game.getName() : "");
        gameVars.put("ext", ext);

        return gameVars;
    }

    // ==================== 文件名模板求值 ====================

    /**
     * 求值文件名模板。
     * <p>
     * 优先使用表达式引擎（支持函数调用），回退到简单变量替换。
     */
    private String evaluateFilenameTemplate(String template, Game game, Platform platform,
                                             Map<String, String> gameVars, Path sourcePath) {
        if (template == null || template.isEmpty()) {
            return sourcePath.getFileName().toString();
        }

        // 确保 ext 有值
        if (!gameVars.containsKey("ext") || gameVars.get("ext").isEmpty()) {
            String originalName = sourcePath.getFileName().toString();
            int dot = originalName.lastIndexOf('.');
            if (dot > 0) {
                gameVars.put("ext", originalName.substring(dot));
            } else {
                gameVars.put("ext", "");
            }
        }

        // 尝试表达式引擎
        if (TemplateExpressionEngine.isExpression(template)) {
            try {
                TemplateExpressionEngine.Context ctx = new TemplateExpressionEngine.Context(game, platform, gameVars);
                return TemplateExpressionEngine.evaluate(template, ctx);
            } catch (Exception e) {
                logger.warn("文件名表达式求值失败: {}, 回退到变量替换", template);
            }
        }

        // 回退：简单变量替换
        String result = template;
        for (Map.Entry<String, String> entry : gameVars.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }

    // ==================== 路径解析 ====================

    /**
     * 解析游戏的 ROM 源路径。
     * 优先 absolutePath，回退到 platformPath + path。
     */
    private Path resolveSourcePath(Game game) {
        String absolutePath = game.getAbsolutePath();
        if (absolutePath != null && !absolutePath.isEmpty()) {
            return Paths.get(absolutePath);
        }

        String platformPath = game.getPlatformPath();
        String gamePath = game.getPath();
        if (platformPath != null && !platformPath.isEmpty() && gamePath != null && !gamePath.isEmpty()) {
            return Paths.get(platformPath, gamePath);
        }

        return null;
    }

    /**
     * 解析模板字符串：替换 {platform.xxx} 和 {variable} 占位符。
     */
    private String resolveTemplateString(String template, Platform platform, Map<String, String> vars) {
        if (template == null) return null;
        String result = template;
        if (platform != null) {
            result = result
                .replace("{platform.system}", nvl(platform.getSystem()))
                .replace("{platform.name}", nvl(platform.getName()))
                .replace("{platform.launch}", nvl(platform.getLaunch()))
                .replace("{platform.software}", nvl(platform.getSoftware()))
                .replace("{platform.database}", nvl(platform.getDatabase()))
                .replace("{platform.web}", nvl(platform.getWeb()))
                .replace("{platform.folderPath}", nvl(platform.getFolderPath()));
        }
        if (vars != null) {
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                if (entry.getValue() != null) {
                    result = result.replace("{" + entry.getKey() + "}", entry.getValue());
                }
            }
        }
        return result;
    }

    // ==================== M3U 处理 ====================

    /**
     * 处理 M3U 文件：复制 M3U 引用的所有文件到目标目录。
     */
    private void processM3UFile(Path sourceM3U, Path targetM3U, String romsDir,
                                 TemplateV3.RomOutput romsConfig, Game game,
                                 Platform platform, Map<String, String> gameVars) throws Exception {
        List<String> lines = Files.readAllLines(sourceM3U);
        List<String> updatedLines = new ArrayList<>();
        Path sourceDir = sourceM3U.getParent();
        Path targetDir = targetM3U.getParent();

        String m3uTarget = romsConfig.getM3u().getTarget();

        for (String line : lines) {
            if (line.startsWith("#") || line.trim().isEmpty()) {
                updatedLines.add(line);
                continue;
            }

            Path filePath;
            if (line.startsWith("/") || line.startsWith("\\")) {
                filePath = Paths.get(line);
            } else {
                filePath = sourceDir.resolve(line).normalize();
            }

            if (Files.exists(filePath)) {
                // 确定引用文件的目标路径
                Path refTargetDir;
                if (m3uTarget != null && !m3uTarget.isEmpty()) {
                    String resolvedTarget = resolveTemplateString(m3uTarget, platform, gameVars);
                    refTargetDir = Paths.get(resolvedTarget);
                } else {
                    refTargetDir = targetDir;
                }

                Path relativePath = sourceDir.relativize(filePath);
                Path refTargetPath = refTargetDir.resolve(relativePath.toString());
                copyFile(filePath, refTargetPath);

                updatedLines.add(sourceDir.relativize(filePath).toString());
            } else {
                logger.warn("M3U 引用文件不存在: {}", filePath);
                updatedLines.add(line);
            }
        }

        Files.write(targetM3U, updatedLines);
        logger.info("M3U 文件处理完成: {}", targetM3U);
    }

    // ==================== 文件拷贝 ====================

    /**
     * 使用 NIO 高速文件拷贝。
     */
    private void copyFile(Path source, Path target) throws Exception {
        Files.createDirectories(target.getParent());
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

    private String nvl(String value) {
        return value != null ? value : "";
    }
}
