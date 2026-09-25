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
        // 注入模板声明变量的用户填写值（未填时用模板 default 按当前上下文解析）
        applyTemplateVariables(vars, template, request.getTemplateVariables(), platform);

        // 获取游戏列表
        List<Game> games = gameMapper.selectGamesByPlatformId(platformId);
        logger.info("v3 导出: 平台={}, 游戏数={}, 线程数={}", platform.getName(), games.size(), threadCount);

        try {
            int progress = 0;

            // 1. 创建目录结构
            taskService.updateTaskProgress(task.getId(), 5, "创建目录结构", 0, 100);
            createDirectories(output, platform, vars);

            // 2. 复制 ROM 文件
            List<String> missingParents = new ArrayList<>();
            if (request.isCopyRoms() && output.getRoms() != null && output.getRoms().isEnabled()) {
                if (request.isWholeDirectoryCopy()) {
                    // 整目录拷贝：不逐游戏依赖 scraped 关联性，直接把平台源 ROM 目录全量搬到 romsDir
                    taskService.updateTaskProgress(task.getId(), 10, "开始整目录拷贝游戏文件", 0, 100);
                    String romsDir = resolveTemplateString(output.getRoms().getDirectory(), platform, vars);
                    copyWholeDirectory(platform, romsDir, task.getId());
                } else {
                    taskService.updateTaskProgress(task.getId(), 10, "开始复制游戏文件", 0, 100);
                    missingParents = copyGameFiles(games, output.getRoms(), platform, vars, threadCount, task.getId());
                }
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

            // 5. 生成使用说明（output.readme 声明，模板驱动）
            if (output.getReadme() != null && output.getReadme().isEnabled()) {
                generateReadme(output.getReadme(), platform, vars);
            }

            taskService.completeTask(task.getId(), "v3 导出完成", buildCompletionResult(request.getOutputPath(), missingParents));
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
    private List<String> copyGameFiles(List<Game> games, TemplateV3.RomOutput romsConfig,
                                Platform platform, Map<String, String> vars,
                                int threadCount, Long taskId) {
        // roms.directory 可能含 per-game 表达式（如 if(genre,...)），在 createDirectories 中已创建基础路径
        // 此处为每个游戏重新求值，确保 genre 等字段正确参与计算
        String rawDirTemplate = romsConfig.getDirectory();
        String baseRomsDir = resolveTemplateString(rawDirTemplate, platform, vars);
        String rawFilenameTemplate = romsConfig.getFilename();
        final String filenameTemplate = (rawFilenameTemplate == null || rawFilenameTemplate.isEmpty())
                ? "{filename}{ext}" : rawFilenameTemplate;

        logger.info("复制 ROM 文件: {} 个游戏 → {}, 文件名模板: {}", games.size(), baseRomsDir, filenameTemplate);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        boolean enableM3U = romsConfig.getM3u() != null && romsConfig.getM3u().isEnabled();
        // 窄口径缺件报告：仅收集"父 rom 在源目录找不到"的游戏（线程安全）
        final List<String> missingParents = java.util.Collections.synchronizedList(new ArrayList<>());

        for (Game game : games) {
            executor.submit(() -> {
                try {
                    // 为当前游戏重新求值 roms.directory（支持 genre 等 per-game 字段）
                    String romsDirForGame = resolveRomDirForGame(rawDirTemplate, game, platform, vars);
                    copySingleGameFile(game, romsDirForGame, filenameTemplate, platform, vars, enableM3U, romsConfig, taskId);
                    // 街机 clone 父 rom 找齐：本体拷贝后，按 parent_rom 到源目录 copy-if-missing
                    copyParentRomIfMissing(game, romsDirForGame, taskId, missingParents);
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
        return missingParents;
    }

    /**
     * 街机 clone 父 rom 找齐（copy-if-missing）。
     * <p>
     * 仅当游戏带有 parent_rom（街机类且已缓存 manifest 时由刮削投影写入）才触发；
     * 默认关闭时 parent_rom 恒为 null → 本方法零影响。
     * 目标已存在则跳过；源找不到才计入缺件报告（不报错、不中断导出）。
     */
    private void copyParentRomIfMissing(Game game, String romsDir, Long taskId, List<String> missingParents) {
        String parentRom = game.getParentRom();
        if (parentRom == null || parentRom.trim().isEmpty()) {
            return;
        }
        parentRom = parentRom.trim();
        try {
            Path target = Paths.get(romsDir, parentRom);
            if (Files.exists(target)) {
                return; // copy-if-missing：目标已有，跳过
            }
            Path src = locateParentRomSource(game, parentRom);
            if (src == null) {
                missingParents.add(game.getName() + " → " + parentRom);
                if (taskId != null) {
                    taskService.updateTaskLog(taskId, "缺父rom(源未找到): " + game.getName() + " 需要 " + parentRom);
                }
                return;
            }
            copyFile(src, target);
            if (taskId != null) {
                taskService.updateTaskLog(taskId, "复制父rom: " + parentRom + " → " + romsDir);
            }
        } catch (Exception e) {
            logger.error("复制父rom失败: game={}, parentRom={}, err={}", game.getName(), parentRom, e.getMessage());
        }
    }

    /**
     * 在有限的候选目录内定位父 rom 源文件（不做全盘递归扫描）：
     *   1) 本体 ROM 所在目录
     *   2) 平台 ROM 根目录 platformPath
     */
    private Path locateParentRomSource(Game game, String parentRom) {
        List<Path> candidateDirs = new ArrayList<>();
        Path self = resolveSourcePath(game);
        if (self != null && self.getParent() != null) {
            candidateDirs.add(self.getParent());
        }
        String platformPath = game.getPlatformPath();
        if (platformPath != null && !platformPath.isEmpty()) {
            candidateDirs.add(Paths.get(platformPath));
        }
        for (Path dir : candidateDirs) {
            Path cand = dir.resolve(parentRom);
            if (Files.exists(cand)) {
                return cand;
            }
        }
        return null;
    }

    /**
     * 整目录拷贝：把平台源 ROM 目录（folderPath）下所有文件递归全量拷到 romsDir，
     * 保持相对目录结构。不按扩展名过滤（保完整），也不逐游戏判关联。
     */
    private void copyWholeDirectory(Platform platform, String romsDir, Long taskId) throws Exception {
        String srcRoot = platform.getFolderPath();
        if (srcRoot == null || srcRoot.trim().isEmpty()) {
            String msg = "平台未配置源目录(folderPath)，无法整目录拷贝: " + platform.getName();
            logger.warn(msg);
            if (taskId != null) taskService.updateTaskLog(taskId, msg);
            return;
        }
        Path source = Paths.get(srcRoot);
        if (!Files.exists(source) || !Files.isDirectory(source)) {
            String msg = "平台源目录不存在或不是目录: " + srcRoot;
            logger.warn(msg);
            if (taskId != null) taskService.updateTaskLog(taskId, msg);
            return;
        }
        Path targetRoot = Paths.get(romsDir);
        Files.createDirectories(targetRoot);
        int count = 0;
        try (java.util.stream.Stream<Path> stream = Files.walk(source)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(p)) continue;
                Path rel = source.relativize(p);
                Path target = targetRoot.resolve(rel.toString());
                copyFile(p, target);
                count++;
            }
        }
        logger.info("整目录拷贝完成: {} → {}, 共 {} 个文件", srcRoot, romsDir, count);
        if (taskId != null) {
            taskService.updateTaskLog(taskId, "整目录拷贝完成: 共 " + count + " 个文件");
        }
    }

    /**
     * 组装导出结果字符串；若有缺件父 rom，则附窄口径清单（最多列 20 条）。
     */
    private String buildCompletionResult(String outputPath, List<String> missingParents) {
        StringBuilder sb = new StringBuilder("导出路径: " + outputPath);
        if (missingParents != null && !missingParents.isEmpty()) {
            sb.append("；缺失父rom ").append(missingParents.size()).append(" 个: ");
            int show = Math.min(20, missingParents.size());
            sb.append(String.join(" | ", missingParents.subList(0, show)));
            if (missingParents.size() > show) {
                sb.append(" …");
            }
        }
        return sb.toString();
    }

    /**
     * 为单个游戏求值 roms.directory（支持 genre 等 per-game 字段的表达式）。
     */
    private String resolveRomDirForGame(String dirTemplate, Game game, Platform platform, Map<String, String> vars) {
        logger.info("[DEBUG] resolveRomDirForGame: template={}, genre={}, isExpression={}", 
            dirTemplate, game.getGenre(), TemplateExpressionEngine.isExpression(dirTemplate));
        if (dirTemplate == null || !TemplateExpressionEngine.isExpression(dirTemplate)) {
            return resolveTemplateString(dirTemplate, platform, vars);
        }
        try {
            Map<String, String> gameVars = buildGameVariables(game, vars);
            TemplateExpressionEngine.Context ctx = new TemplateExpressionEngine.Context(game, platform, gameVars);
            String result = TemplateExpressionEngine.evaluate(dirTemplate, ctx);
            logger.info("[DEBUG] evaluate result: {}", result);
            if (result != null && !result.isEmpty()) {
                return result;
            }
        } catch (Exception e) {
            logger.warn("[DEBUG] roms.directory 表达式求值失败: {}", e.getMessage(), e);
        }
        return resolveTemplateString(dirTemplate, platform, vars);
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

        // 多文件游戏：拷贝多文件文本中的所有盘文件，并按模板 m3u 配置生成 m3u 文件
        if (Boolean.TRUE.equals(game.getMultiFile())
                && game.getMultiFileContent() != null && !game.getMultiFileContent().isEmpty()) {
            copyMultiFileGameFiles(game, romsDir, gameVars, enableM3U, taskId);
            return;
        }

        String targetFileName = evaluateFilenameTemplate(filenameTemplate, game, platform, gameVars, sourcePath);

        Path targetPath = Paths.get(romsDir, targetFileName);
        copyFile(sourcePath, targetPath);

        if (taskId != null) {
            taskService.updateTaskLog(taskId, "复制 ROM: " + sourcePath.getFileName() + " → " + targetFileName);
        }

        // 处理 M3U 文件（path 指向 m3u 文件的旧数据：复制 m3u 引用的所有文件）
        if (enableM3U && targetFileName.toLowerCase().endsWith(".m3u")) {
            processM3UFile(sourcePath, targetPath, romsDir, romsConfig, game, platform, gameVars);
        }
    }

    /**
     * 拷贝多文件游戏（多盘/合盘）：将多文件文本中的每个文件复制到 romsDir（保持相对目录结构）。
     * 模板 roms.m3u.enabled=true 时额外生成 {filename}.m3u 文件（内容为各盘相对路径）。
     */
    private void copyMultiFileGameFiles(Game game, String romsDir, Map<String, String> gameVars,
                                        boolean enableM3U, Long taskId) throws Exception {
        // 平台 ROM 根目录：多文件文本中的路径相对此目录解析
        Path romRoot = null;
        String platformPath = game.getPlatformPath();
        if (platformPath != null && !platformPath.isEmpty()) {
            romRoot = Paths.get(platformPath);
        } else {
            String absPath = game.getAbsolutePath();
            if (absPath != null && !absPath.isEmpty()) {
                romRoot = Paths.get(absPath.split("\\r?\\n")[0].trim()).getParent();
            }
        }

        List<String> lines = new ArrayList<>();
        for (String line : game.getMultiFileContent().split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            if (t.startsWith("./") || t.startsWith(".\\")) t = t.substring(2);
            t = t.replace('\\', '/');
            lines.add(t);

            Path src;
            Path p = Paths.get(t);
            if (p.isAbsolute()) {
                src = p;
            } else if (romRoot != null) {
                src = romRoot.resolve(t);
            } else {
                src = p; // 相对工作目录，尽力而为
            }
            if (!Files.exists(src)) {
                logger.warn("多文件条目文件不存在: {}", src);
                continue;
            }
            Path target = Paths.get(romsDir, t);
            copyFile(src, target);
            if (taskId != null) {
                taskService.updateTaskLog(taskId, "复制多盘文件: " + t);
            }
        }

        // 生成 m3u 文件（模板 roms.m3u.enabled=true，即前端支持 m3u）
        if (enableM3U) {
            String filename = gameVars.get("filename");
            if (filename == null || filename.isEmpty()) filename = game.getName();
            String m3uFileName = filename + ".m3u";
            Path m3uPath = Paths.get(romsDir, m3uFileName);
            Files.write(m3uPath, lines);
            logger.info("生成 m3u 文件: {}", m3uPath);
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
                        // target 支持表达式引擎（如 sanitize(name, charset)），失败回退到变量替换
                        String targetTemplate = rule.getTarget();
                        if (targetTemplate == null || targetTemplate.isEmpty()) continue;
                        String resolvedTarget = evaluateFilenameTemplate(targetTemplate, game, platform, gameVars, sourcePath);
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

    /**
     * 生成使用说明文件（output.readme 声明）。
     * <p>
     * directory/filename/content 均支持模板变量替换（{outputPath}、{platform.xxx} 等），
     * 说明内容本身由模板定义，Java 只负责替换变量与写文件。
     */
    private void generateReadme(TemplateV3.ReadmeOutput readme, Platform platform, Map<String, String> vars) {
        try {
            String dir = resolveTemplateString(readme.getDirectory(), platform, vars);
            String fileName = resolveTemplateString(readme.getFilename(), platform, vars);
            if (dir == null || dir.isEmpty() || fileName == null || fileName.isEmpty()) {
                logger.warn("readme 配置缺少 directory/filename，跳过生成");
                return;
            }
            Path dirPath = Paths.get(dir);
            Files.createDirectories(dirPath);

            StringBuilder sb = new StringBuilder();
            if (readme.getContent() != null) {
                for (String line : readme.getContent()) {
                    String resolved = resolveTemplateString(line, platform, vars);
                    sb.append(resolved != null ? resolved : "").append("\n");
                }
            }
            Path readmePath = dirPath.resolve(fileName);
            Files.writeString(readmePath, sb.toString());
            logger.info("使用说明生成完成: {}", readmePath.toAbsolutePath());
        } catch (Exception e) {
            logger.error("使用说明生成失败", e);
        }
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
     * 注入模板声明变量的用户填写值。
     * <p>
     * 仅处理 {@link TemplateV3#getValidVariables()} 声明的合法变量（变量名已排除与内置变量冲突）。
     * 用户填写值优先；未填写时若模板声明了 default，则以当前 vars 为上下文解析 default
     * （支持 {platform.xxx} 等占位符，按平台自动展开）后填入。
     * 这样变量即可用于 {name} 路径占位符替换与表达式引擎的裸标识符拼接（如 URL）。
     */
    private void applyTemplateVariables(Map<String, String> vars, TemplateV3 template,
                                       Map<String, String> userVars, Platform platform) {
        if (template == null) return;
        java.util.List<TemplateV3.TemplateVariable> declared = template.getValidVariables();
        if (declared.isEmpty()) return;
        for (TemplateV3.TemplateVariable var : declared) {
            String name = var.getName();
            String value = userVars != null ? userVars.get(name) : null;
            if (value == null || value.isEmpty()) {
                String def = var.getDefaultValue();
                if (def != null && !def.isEmpty()) {
                    value = resolveTemplateString(def, platform, vars);
                }
            }
            if (value != null) {
                vars.put(name, value);
            }
        }
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
     * <p>
     * 若模板是表达式（含函数调用），则优先用表达式引擎求值；失败回退到简单变量替换。
     */
    private String resolveTemplateString(String template, Platform platform, Map<String, String> vars) {
        if (template == null) return null;

        // 尝试表达式引擎（支持 if/concat/map/sanitize 等）
        if (TemplateExpressionEngine.isExpression(template)) {
            try {
                TemplateExpressionEngine.Context ctx = new TemplateExpressionEngine.Context(null, platform, vars);
                String result = TemplateExpressionEngine.evaluate(template, ctx);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            } catch (Exception e) {
                logger.debug("表达式求值失败，回退到变量替换: {}", e.getMessage());
            }
        }

        // 回退：简单变量替换
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
