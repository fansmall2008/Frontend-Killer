package com.gamelist.service.impl;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.gamelist.mapper.GameMapper;
import com.gamelist.mapper.FieldTranslationMapper;
import com.gamelist.model.BackgroundTask;
import com.gamelist.model.ExportRequest;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.model.ResolvedVariant;
import com.gamelist.model.TemplateV3;
import com.gamelist.service.PlatformService;
import com.gamelist.service.TaskService;
import com.gamelist.service.VariantService;
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
    private FieldTranslationMapper fieldTranslationMapper;

    @Autowired
    private PlatformService platformService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TemplateV3ExportService v3ExportService;

    @Autowired
    private VariantResolver variantResolver;

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

        // 构建基础变量
        Map<String, String> vars = buildBaseVariables(request.getOutputPath(), platform);
        // 注入模板声明变量的用户填写值（未填时用模板 default 按当前上下文解析）
        applyTemplateVariables(vars, template, request.getTemplateVariables(), platform);

        // 获取游戏列表
        List<Game> games = gameMapper.selectGamesByPlatformId(platformId);
        logger.info("v3 导出: 平台={}, 游戏数={}", platform.getName(), games.size());
        // 按所选语种从 field_translation 回填瞬态 translatedName/translatedDesc（供 coalesce/-translated 模板消费）
        applyTranslationInjection(games, request.getTranslationLang());

        // 按系统选择版本格式；未选择时用系统偏好（无偏好则 original）
        Map<Long, ResolvedVariant> resolvedVariants = Collections.emptyMap();
        boolean useVariants = request.isCopyRoms()
                && output.getRoms() != null && output.getRoms().isEnabled()
                && !request.isWholeDirectoryCopy();
        if (useVariants) {
            String requestedFormat = resolveRequestedVariantFormat(request, platform);
            resolvedVariants = variantResolver.resolveBatch(games, platform.getSystemId(), requestedFormat);
        }

        try {
            int progress = 0;

            // 1. 创建目录结构
            taskService.updateTaskProgress(task.getId(), 5, "创建目录结构", 0, 100);
            createDirectories(output, platform, vars);

            // 2. 复制 ROM 文件
            RomCopyResult copyResult = new RomCopyResult();
            if (request.isCopyRoms() && output.getRoms() != null && output.getRoms().isEnabled()) {
                if (request.isWholeDirectoryCopy()) {
                    // 整目录拷贝：不逐游戏依赖 scraped 关联性，直接把平台源 ROM 目录全量搬到 romsDir
                    taskService.updateTaskProgress(task.getId(), 10, "开始整目录拷贝游戏文件", 0, 100);
                    String romsDir = resolveTemplateString(output.getRoms().getDirectory(), platform, vars);
                    copyWholeDirectory(platform, romsDir, task.getId());
                } else {
                    taskService.updateTaskProgress(task.getId(), 10, "开始复制游戏文件", 0, 100);
                    copyResult = copyGameFiles(games, output.getRoms(), platform, vars,
                            task.getId(), resolvedVariants);
                }
                progress = 40;
                taskService.updateTaskProgress(task.getId(), progress, "游戏文件复制完成", 0, 100);
            } else {
                logger.info("v3 导出: 跳过游戏文件复制");
            }

            // 3. 复制媒体文件
            if (request.isCopyMedia() && output.getMedia() != null && output.getMedia().getRules() != null) {
                taskService.updateTaskProgress(task.getId(), progress, "开始复制媒体文件", 0, 100);
                copyMediaFiles(games, output.getMedia(), platform, vars, task.getId());
                progress = 70;
                taskService.updateTaskProgress(task.getId(), progress, "媒体文件复制完成", 0, 100);
            } else {
                logger.info("v3 导出: 跳过媒体文件复制");
            }

            // 4. 预计算 ROM 导出路径（单一计算源，保证拷贝路径与 metadata 写入路径一致）
            Map<Long, String> romExportPaths = null;
            if (!request.isWholeDirectoryCopy()
                    && output.getRoms() != null && output.getRoms().isEnabled()
                    && output.getRoms().getDirectory() != null
                    && request.isCopyRoms()) {
                romExportPaths = new LinkedHashMap<>();
                String romDirTemplate = output.getRoms().getDirectory();
                String romFilenameTemplate = output.getRoms().getFilename();
                if (romFilenameTemplate == null || romFilenameTemplate.isEmpty()) {
                    romFilenameTemplate = "{filename}{ext}";
                }
                for (Game game : games) {
                    if (game.getId() == null) continue;
                    try {
                        String romDir = resolveRomDirForGame(romDirTemplate, game, platform, vars);
                        Map<String, String> gameVars = buildGameVariables(game, vars);
                        String romFilename = evaluateFilenameTemplate(romFilenameTemplate, game, platform,
                                gameVars, resolveSourcePath(game));
                        String romTargetAbs = romDir + "/" + romFilename;
                        romExportPaths.put(game.getId(), romTargetAbs);
                    } catch (Exception e) {
                        logger.warn("预计算 ROM 导出路径失败: game={}, error={}", game.getName(), e.getMessage());
                    }
                }
            }

            // 5. 生成数据文件
            if (request.isGenerateDataFile() && output.getDataFile() != null) {
                taskService.updateTaskProgress(task.getId(), progress, "开始生成数据文件", 0, 100);
                generateDataFile(games, template, platform, vars, romExportPaths);
                progress = 100;
                taskService.updateTaskProgress(task.getId(), progress, "数据文件生成完成", 0, 100);
            } else {
                logger.info("v3 导出: 跳过数据文件生成");
            }

            // 6. 生成使用说明（output.readme 声明，模板驱动）
            if (output.getReadme() != null && output.getReadme().isEnabled()) {
                generateReadme(output.getReadme(), platform, vars);
            }

            taskService.completeTask(task.getId(), "v3 导出完成", buildCompletionResult(request.getOutputPath(), copyResult));
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
            String romsDir = ensurePathResolved(
                    resolveTemplateString(output.getRoms().getDirectory(), platform, vars),
                    output.getRoms().getDirectory());
            Files.createDirectories(Paths.get(romsDir));
            logger.info("创建 ROM 目录: {}", romsDir);
        }

        // 媒体目录
        if (output.getMedia() != null && output.getMedia().getDirectory() != null) {
            String mediaDir = ensurePathResolved(
                    resolveTemplateString(output.getMedia().getDirectory(), platform, vars),
                    output.getMedia().getDirectory());
            Files.createDirectories(Paths.get(mediaDir));
            logger.info("创建媒体目录: {}", mediaDir);
        }

        // 数据文件目录
        if (output.getDataFile() != null && output.getDataFile().getDirectory() != null) {
            String dataDir = ensurePathResolved(
                    resolveTemplateString(output.getDataFile().getDirectory(), platform, vars),
                    output.getDataFile().getDirectory());
            Files.createDirectories(Paths.get(dataDir));
            logger.info("创建数据文件目录: {}", dataDir);
        }
    }

    // ==================== 译文注入 ====================

    /**
     * 按所选语种从 field_translation 批量回填 game 的瞬态 translatedName/translatedDesc。
     * lang 为空则不注入（模板 coalesce 自然回退原文）。
     */
    private void applyTranslationInjection(List<Game> games, String lang) {
        if (games == null || games.isEmpty() || lang == null || lang.isBlank()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (Game g : games) {
            if (g.getId() != null) ids.add(g.getId());
        }
        if (ids.isEmpty()) return;
        Map<Long, String> nameMap = new HashMap<>();
        Map<Long, String> descMap = new HashMap<>();
        for (Map<String, Object> row : fieldTranslationMapper.selectValues("game", "name", lang, ids)) {
            Object sid = row.get("srcId");
            Object v = row.get("value");
            if (sid != null && v != null) nameMap.put(((Number) sid).longValue(), v.toString());
        }
        for (Map<String, Object> row : fieldTranslationMapper.selectValues("game", "desc", lang, ids)) {
            Object sid = row.get("srcId");
            Object v = row.get("value");
            if (sid != null && v != null) descMap.put(((Number) sid).longValue(), v.toString());
        }
        for (Game g : games) {
            if (g.getId() == null) continue;
            String n = nameMap.get(g.getId());
            String d = descMap.get(g.getId());
            if (n != null && !n.isBlank()) g.setTranslatedName(n);
            if (d != null && !d.isBlank()) g.setTranslatedDesc(d);
        }
    }

    // ==================== ROM 文件复制 ====================

    /**
     * 复制 ROM 文件到目标目录。
     * <p>
     * 文件名由 output.roms.filename 模板驱动（支持表达式引擎）。
     * 默认 "{filename}{ext}" 表示保持原名。
     *
     * @return 缺件报告（父 rom 缺失 + CUE 关联文件缺件，按 roms.cue.onMissing 规则收集）
     */
    private RomCopyResult copyGameFiles(List<Game> games, TemplateV3.RomOutput romsConfig,
                                Platform platform, Map<String, String> vars,
                                Long taskId, Map<Long, ResolvedVariant> resolvedVariants) {
        // roms.directory 可能含 per-game 表达式（如 if(genre,...)），在 createDirectories 中已创建基础路径
        // 此处为每个游戏重新求值，确保 genre 等字段正确参与计算
        String rawDirTemplate = romsConfig.getDirectory();
        String baseRomsDir = resolveTemplateString(rawDirTemplate, platform, vars);
        String rawFilenameTemplate = romsConfig.getFilename();
        final String filenameTemplate = (rawFilenameTemplate == null || rawFilenameTemplate.isEmpty())
                ? "{filename}{ext}" : rawFilenameTemplate;

        logger.info("复制 ROM 文件: {} 个游戏 → {}, 文件名模板: {}", games.size(), baseRomsDir, filenameTemplate);

        boolean enableM3U = romsConfig.getM3u() != null && romsConfig.getM3u().isEnabled();
        // cue 配置：缺省（旧模板无 cue 段）视为 enabled=true，行为向后兼容
        TemplateV3.CueConfig cueConfig = romsConfig.getCue();
        boolean cueEnabled = cueConfig == null || cueConfig.isEnabled();
        String cueOnMissing = (cueConfig != null && cueConfig.getOnMissing() != null
                && !cueConfig.getOnMissing().trim().isEmpty())
                ? cueConfig.getOnMissing().trim().toLowerCase() : "warn";
        CueContext cueCtx = new CueContext(taskId, cueEnabled, cueOnMissing);
        // 窄口径缺件报告：父 rom 缺失 + CUE 关联文件缺件（线程安全）
        RomCopyResult result = new RomCopyResult();
        final List<String> missingParents = result.missingParents;
        int variantUsed = 0;
        int variantFallback = 0;

        for (Game game : games) {
            ResolvedVariant resolved = resolvedVariants.get(game.getId());
            if (resolved != null && resolved.isVariant()) {
                variantUsed++;
            } else if (resolved != null && resolved.getRequestedFormat() != null
                    && !VariantService.FORMAT_ORIGINAL.equals(resolved.getRequestedFormat())) {
                variantFallback++;
            }
            try {
                // 为当前游戏重新求值 roms.directory（支持 genre 等 per-game 字段）
                String romsDirForGame = resolveRomDirForGame(rawDirTemplate, game, platform, vars);
                copySingleGameFile(game, romsDirForGame, filenameTemplate, platform, vars,
                        enableM3U, romsConfig, taskId, resolved, cueCtx);
                // 街机 clone 父 rom 找齐：本体拷贝后，按 parent_rom 到源目录 copy-if-missing
                copyParentRomIfMissing(game, romsDirForGame, taskId, missingParents);
            } catch (Exception e) {
                // cue.onMissing=fail 时也会在此捕获：仅中断该游戏的复制，不中断整批导出
                logger.error("复制 ROM 文件失败: {}", game.getName(), e);
            }
        }
        if (variantUsed > 0 || variantFallback > 0) {
            logger.info("版本使用情况: 使用版本 {} 个, 回退主文件 {} 个", variantUsed, variantFallback);
            if (taskId != null && variantFallback > 0) {
                taskService.updateTaskLog(taskId,
                        "版本回退: " + variantFallback + " 个游戏使用主文件（版本未生成/已过期）");
            }
        }
        return result;
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
     * 组装导出结果字符串；若有缺件父 rom / CUE 关联文件缺件，则附窄口径清单（各最多列 20 条）。
     */
    private String buildCompletionResult(String outputPath, RomCopyResult copyResult) {
        StringBuilder sb = new StringBuilder("导出路径: " + outputPath);
        if (copyResult != null) {
            appendMissingSummary(sb, "缺失父rom", copyResult.missingParents);
            appendMissingSummary(sb, "CUE缺件", copyResult.missingCueFiles);
        }
        return sb.toString();
    }

    /**
     * 追加一类缺件清单摘要（最多列 20 条，超出以 … 截断）。
     */
    private void appendMissingSummary(StringBuilder sb, String label, List<String> items) {
        if (items == null || items.isEmpty()) return;
        sb.append("；").append(label).append(" ").append(items.size()).append(" 个: ");
        int show = Math.min(20, items.size());
        sb.append(String.join(" | ", items.subList(0, show)));
        if (items.size() > show) {
            sb.append(" …");
        }
    }

    /**
     * 为单个游戏求值 roms.directory（支持 genre 等 per-game 字段的表达式）。
     */
    public static String resolveRomDirForGame(String dirTemplate, Game game, Platform platform, Map<String, String> vars) {
        logger.info("[DEBUG] resolveRomDirForGame: template={}, genre={}, isExpression={}", 
            dirTemplate, game.getGenre(), TemplateExpressionEngine.isExpression(dirTemplate));
        if (dirTemplate == null) {
            return null;
        }
        if (!TemplateExpressionEngine.isExpression(dirTemplate)) {
            return ensurePathResolved(resolveTemplateString(dirTemplate, platform, vars), dirTemplate);
        }
        try {
            Map<String, String> gameVars = buildGameVariables(game, vars);
            TemplateExpressionEngine.Context ctx = new TemplateExpressionEngine.Context(game, platform, gameVars);
            String result = TemplateExpressionEngine.evaluate(dirTemplate, ctx);
            logger.info("[DEBUG] evaluate result: {}", result);
            if (result != null && !result.isEmpty()) {
                // 表达式字符串字面量内的 {outputPath} 等占位符由引擎原样返回，
                // 求值结果必须再做一次变量替换 + 残留检测，否则路径会带字面量 {outputPath}
                return ensurePathResolved(applySimpleVars(result, platform, gameVars), dirTemplate);
            }
        } catch (IllegalArgumentException e) {
            throw e; // 占位符残留防御异常直接上抛，不吞
        } catch (Exception e) {
            logger.warn("[DEBUG] roms.directory 表达式求值失败: {}", e.getMessage(), e);
        }
        return ensurePathResolved(resolveTemplateString(dirTemplate, platform, vars), dirTemplate);
    }

    /**
     * 复制单个游戏的 ROM 文件。
     */
    private void copySingleGameFile(Game game, String romsDir, String filenameTemplate,
                                     Platform platform, Map<String, String> vars,
                                     boolean enableM3U, TemplateV3.RomOutput romsConfig,
                                     Long taskId, ResolvedVariant resolved, CueContext cueCtx) throws Exception {
        // 确定源路径：有可用版本则用版本，否则回退主文件
        Path sourcePath = resolved != null && resolved.getSourcePath() != null
                ? resolved.getSourcePath() : resolveSourcePath(game);
        if (sourcePath == null || !Files.exists(sourcePath)) {
            if (sourcePath != null) {
                logger.warn("ROM 文件不存在: {}", sourcePath);
            }
            return;
        }

        boolean appliedVariant = resolved != null && resolved.isVariant();
        if (appliedVariant) {
            // 让 path / ext 表达式先看到版本扩展名，确保文件名模板求出正确后缀
            game.setPath(replaceExtension(game.getPath(), resolved.getExtension()));
            if (resolved.getFallbackReason() == null) {
                logger.debug("使用版本: game={}, format={}", game.getName(), resolved.getFormat());
            }
        }

        // 计算目标文件名
        Map<String, String> gameVars = buildGameVariables(game, vars);
        if (appliedVariant) {
            gameVars.put("ext", resolved.getExtension());
        }

        // 多文件游戏：拷贝多文件文本中的所有盘文件，并按模板 m3u 配置生成 m3u 文件
        if (Boolean.TRUE.equals(game.getMultiFile())
                && game.getMultiFileContent() != null && !game.getMultiFileContent().isEmpty()) {
            copyMultiFileGameFiles(game, romsDir, gameVars, enableM3U, taskId, cueCtx);
            return;
        }

        String targetFileName = evaluateFilenameTemplate(filenameTemplate, game, platform, gameVars, sourcePath);
        logger.info("[DIAG] game={}, genre={}, filenameTemplate={}, targetFileName={}, romsDir={}",
                game.getName(), game.getGenre(), filenameTemplate, targetFileName, romsDir);

        Path targetPath = Paths.get(romsDir, targetFileName);
        copyFile(sourcePath, targetPath);

        if (taskId != null) {
            String prefix = appliedVariant ? "复制版本(" + resolved.getFormat() + "): " : "复制 ROM: ";
            taskService.updateTaskLog(taskId, prefix + sourcePath.getFileName() + " → " + targetFileName);
        }

        if (appliedVariant) {
            // 数据文件里的 path 必须指向实际导出的文件名（扩展名可能已变）；
            // absolutePath 保持源主文件路径，避免后续父 ROM 定位误把版本目录当源目录
            game.setPath(targetFileName);
        } else if (resolved != null && resolved.getFallbackReason() != null
                && resolved.getRequestedFormat() != null
                && !VariantService.FORMAT_ORIGINAL.equals(resolved.getRequestedFormat())) {
            logger.info("版本不可用，回退主文件: game={}, format={}, reason={}",
                    game.getName(), resolved.getRequestedFormat(), resolved.getFallbackReason());
        }

        // 处理 M3U 文件（path 指向 m3u 文件的旧数据：复制 m3u 引用的所有文件）
        if (enableM3U && targetFileName.toLowerCase().endsWith(".m3u")) {
            processM3UFile(sourcePath, targetPath, romsDir, romsConfig, game, platform, gameVars, cueCtx, taskId);
        }

        // 处理 CUE 文件（裸 cue 游戏：解析 FILE 引用收集 bin/wav 等关联文件，缺失按 cue.onMissing 处理）
        if (cueCtx.enabled && targetFileName.toLowerCase().endsWith(".cue")) {
            copyCueReferencedFiles(sourcePath, targetPath, cueCtx, game, taskId);
        }
    }

    /**
     * 解析本次导出请求的版本格式（按 systemId 取值）。
     * 返回 null 表示未显式指定，由 VariantResolver 使用系统偏好。
     */
    private String resolveRequestedVariantFormat(ExportRequest request, Platform platform) {
        if (platform == null || platform.getSystemId() == null) {
            return VariantService.FORMAT_ORIGINAL;
        }
        Map<String, String> formats = request.getSystemVariantFormats();
        if (formats == null || formats.isEmpty()) {
            return null;
        }
        String selected = formats.get(String.valueOf(platform.getSystemId()));
        if (selected == null) {
            selected = formats.get(platform.getSystemId());
        }
        return selected;
    }

    /**
     * 把 path 的扩展名替换为版本扩展名，保留目录前缀与 # 片段。
     */
    private String replaceExtension(String path, String extension) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        String value = path.split("\\r?\\n")[0];
        int hash = value.indexOf('#');
        String suffix = hash >= 0 ? value.substring(hash) : "";
        String base = hash >= 0 ? value.substring(0, hash) : value;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        int dot = base.lastIndexOf('.');
        String withoutExt = dot > slash ? base.substring(0, dot) : base;
        return withoutExt + (extension != null ? extension : "") + suffix;
    }

    /**
     * 拷贝多文件游戏（多盘/合盘）：将多文件文本中的每个文件复制到 romsDir（保持相对目录结构）。
     * <p>
     * 条目递归展开：条目为 .cue → 同时收集其 FILE 引用的关联文件（如 .bin）；
     * 条目为 .m3u → 继续展开其中条目（visited 集合 + 深度限制防无限递归）。
     * 所有待复制文件去重后统一复制；引用文件缺失按 cue.onMissing 规则处理。
     * <p>
     * 模板 roms.m3u.enabled=true 时额外生成 {filename}.m3u 文件（内容为各盘相对路径）。
     */
    private void copyMultiFileGameFiles(Game game, String romsDir, Map<String, String> gameVars,
                                        boolean enableM3U, Long taskId, CueContext cueCtx) throws Exception {
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
        // 统一收集待复制文件（LinkedHashMap 按源绝对路径保序去重）：源绝对路径 → 目标绝对路径
        Map<Path, Path> pending = new LinkedHashMap<>();
        Set<Path> visited = new HashSet<>();

        for (String line : game.getMultiFileContent().split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            if (t.startsWith("./") || t.startsWith(".\\")) t = t.substring(2);
            t = t.replace('\\', '/');
            lines.add(t);

            Path src = resolveEntrySource(Paths.get(t), romRoot);
            if (!Files.exists(src)) {
                // 条目缺失：按 cue.onMissing 规则处理（warn → 任务日志+汇总；fail → 中断该游戏复制）
                cueCtx.handleMissing(game, "多盘条目 " + t);
                continue;
            }
            // 防目录逃逸：绝对路径/前导 ../ 段降级，目标保持在 romsDir 内
            Path target = Paths.get(romsDir, safeRelativePath(t));
            pending.putIfAbsent(src.normalize().toAbsolutePath(), target);
            // 条目为 cue/m3u 时递归展开关联文件
            collectEntryChain(src, target, pending, cueCtx, game, taskId, visited, 0);
        }

        // 统一复制（去重后）
        for (Map.Entry<Path, Path> e : pending.entrySet()) {
            copyFile(e.getKey(), e.getValue());
            if (taskId != null) {
                taskService.updateTaskLog(taskId, "复制多盘文件: " + e.getValue().getFileName());
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
                                 Long taskId) {
        String mediaDir = resolveTemplateString(mediaConfig.getDirectory(), platform, vars);
        Map<String, TemplateV3.MediaOutputRule> rules = mediaConfig.getRules();

        logger.info("复制媒体文件: {} 个游戏, {} 种媒体类型 → {}", games.size(), rules.size(), mediaDir);

        for (Game game : games) {
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
        }
    }

    // ==================== 数据文件生成 ====================

    /**
     * 生成数据文件，委托给 TemplateV3ExportService。
     */
    private void generateDataFile(List<Game> games, TemplateV3 template,
                                   Platform platform, Map<String, String> vars,
                                   Map<Long, String> romExportPaths) throws Exception {
        v3ExportService.exportToFile(games, template, platform, vars, romExportPaths);
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
    private static Map<String, String> buildGameVariables(Game game, Map<String, String> baseVars) {
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
    private static String evaluateFilenameTemplate(String template, Game game, Platform platform,
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
     * 若模板是表达式（含函数调用），则优先用表达式引擎求值。
     * 注意：表达式引擎对字符串字面量（如 '{outputPath}/xxx'）原样返回、不做 {变量} 替换，
     * 因此求值结果也必须走一遍兜底变量替换（applySimpleVars）；失败才回退到纯变量替换。
     */
    private static String resolveTemplateString(String template, Platform platform, Map<String, String> vars) {
        if (template == null) return null;

        // 尝试表达式引擎（支持 if/concat/map/sanitize 等）
        if (TemplateExpressionEngine.isExpression(template)) {
            try {
                TemplateExpressionEngine.Context ctx = new TemplateExpressionEngine.Context(null, platform, vars);
                String result = TemplateExpressionEngine.evaluate(template, ctx);
                if (result != null && !result.isEmpty()) {
                    // 求值结果中的 {outputPath} 等占位符位于字符串字面量内，引擎不替换，需后置替换
                    return applySimpleVars(result, platform, vars);
                }
            } catch (Exception e) {
                logger.debug("表达式求值失败，回退到变量替换: {}", e.getMessage());
            }
        }

        // 回退：简单变量替换
        return applySimpleVars(template, platform, vars);
    }

    /**
     * 对模板串执行简单 {变量} 占位符替换（{platform.xxx} + vars 内变量）。
     * <p>
     * 复用点：非表达式模板的直接替换；表达式求值结果的后置替换（引擎不处理字面量内的 {变量}）。
     */
    private static String applySimpleVars(String template, Platform platform, Map<String, String> vars) {
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

    /** 未解析占位符检测模式：{xxx}（路径中不应出现任何残留占位符） */
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\{[^}]+\\}");

    /**
     * 防御加固：检测解析后的路径是否仍残留 {xxx} 占位符（模板变量未解析）。
     * <p>
     * 残留的典型原因：表达式引擎对字符串字面量原样返回而调用方未做后置变量替换。
     * 若不拦截，字面量路径会在 cwd 下创建 "{outputPath}" 垃圾目录
     * （NAS 容器 cwd=/app 不可写时直接 AccessDeniedException）。
     *
     * @param resolvedPath 已解析路径
     * @param rawTemplate  原始模板值（用于错误定位）
     * @return 校验通过的路径
     * @throws IllegalArgumentException 检测到残留占位符
     */
    private static String ensurePathResolved(String resolvedPath, String rawTemplate) {
        if (resolvedPath == null) return null;
        Matcher m = UNRESOLVED_PLACEHOLDER.matcher(resolvedPath);
        if (m.find()) {
            throw new IllegalArgumentException("导出路径模板变量未解析: " + m.group()
                    + "（模板: " + rawTemplate + "）");
        }
        return resolvedPath;
    }

    // ==================== CUE/M3U 关联文件收集 ====================

    /** CUE 文件 FILE 行（带引号形式，引号内可含空格路径） */
    private static final Pattern CUE_FILE_QUOTED =
            Pattern.compile("^FILE\\s+\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    /** CUE 文件 FILE 行（不带引号形式，文件名不含空格） */
    private static final Pattern CUE_FILE_UNQUOTED =
            Pattern.compile("^FILE\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    /** 条目嵌套展开的最大深度（m3u→cue→m3u 链），防无限递归 */
    private static final int MAX_NESTING_DEPTH = 16;

    /**
     * CUE 关联文件收集上下文：封装 roms.cue 配置（enabled/onMissing）与缺件清单。
     * <p>
     * onMissing 规则：
     * <ul>
     *   <li>warn（默认）— 记录任务日志并收集到完成结果汇总</li>
     *   <li>skip — 静默（仅 debug 日志）</li>
     *   <li>fail — 写任务日志后抛异常，中断该游戏的复制（由 copyGameFiles 的单游戏 try-catch 捕获）</li>
     * </ul>
     */
    private class CueContext {
        final boolean enabled;
        final String onMissing;
        final Long taskId;
        final List<String> missing = Collections.synchronizedList(new ArrayList<>());

        CueContext(Long taskId, boolean enabled, String onMissing) {
            this.taskId = taskId;
            this.enabled = enabled;
            this.onMissing = onMissing;
        }

        /**
         * 按 onMissing 规则处理一条缺件记录。
         *
         * @param game   所属游戏（用于汇总清单定位）
         * @param detail 缺件描述（如 "Game.cue 引用的 Track1.bin"）
         */
        void handleMissing(Game game, String detail) {
            String gameName = (game != null && game.getName() != null) ? game.getName() : "?";
            String item = gameName + " → " + detail;
            if ("skip".equals(onMissing)) {
                logger.debug("CUE 关联文件缺失(skip): {}", item);
                return;
            }
            if ("fail".equals(onMissing)) {
                if (taskId != null) {
                    taskService.updateTaskLog(taskId, "CUE缺件(中断): " + item);
                }
                logger.warn("CUE 关联文件缺失(fail): {}", item);
                throw new IllegalStateException("CUE 关联文件缺失: " + item);
            }
            // warn（含未知取值回退）
            missing.add(item);
            if (taskId != null) {
                taskService.updateTaskLog(taskId, "CUE缺件: " + item);
            }
            logger.warn("CUE 关联文件缺失: {}", item);
        }
    }

    /**
     * ROM 复制阶段的缺件报告：父 rom 缺失清单 + CUE 关联文件缺件清单。
     */
    private static class RomCopyResult {
        final List<String> missingParents = Collections.synchronizedList(new ArrayList<>());
        final List<String> missingCueFiles = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * 解析 CUE 文件中的 FILE 引用行，返回以 cue 所在目录为基准解析的引用文件路径列表。
     * <p>
     * 支持带引号（引号内空格路径）与不带引号两种形式；跳过空行、;/# 注释与 BOM；
     * 不校验文件是否存在（由调用方按 cue.onMissing 规则处理）。
     */
    private List<Path> parseCueFileReferences(Path cueFile) throws Exception {
        List<Path> refs = new ArrayList<>();
        Path baseDir = cueFile.getParent() != null ? cueFile.getParent() : Paths.get(".");
        for (String rawLine : Files.readAllLines(cueFile)) {
            if (rawLine.startsWith("\uFEFF")) {
                rawLine = rawLine.substring(1); // 去 UTF-8 BOM
            }
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue;
            String name = null;
            Matcher quoted = CUE_FILE_QUOTED.matcher(line);
            if (quoted.find()) {
                name = quoted.group(1);
            } else {
                Matcher unquoted = CUE_FILE_UNQUOTED.matcher(line);
                if (unquoted.find()) {
                    name = unquoted.group(1);
                }
            }
            if (name == null || name.trim().isEmpty()) continue;
            refs.add(baseDir.resolve(name.trim().replace('\\', '/')).normalize());
        }
        return refs;
    }

    /**
     * 处理 CUE 文件（单文件分支）：解析 cue 内 FILE 引用，收集关联文件复制到 cue 目标旁。
     * 引用文件缺失按 cue.onMissing 规则处理（warn → 任务日志 + 完成汇总；skip → 静默；fail → 中断该游戏复制）。
     */
    private void copyCueReferencedFiles(Path sourceCue, Path targetCue, CueContext cueCtx,
                                        Game game, Long taskId) throws Exception {
        Map<Path, Path> pending = new LinkedHashMap<>();
        collectEntryChain(sourceCue, targetCue, pending, cueCtx, game, taskId, new HashSet<>(), 0);
        for (Map.Entry<Path, Path> e : pending.entrySet()) {
            copyFile(e.getKey(), e.getValue());
            if (taskId != null) {
                taskService.updateTaskLog(taskId, "复制 CUE 关联文件: " + e.getValue().getFileName()
                        + " → " + targetCue.getParent().getFileName());
            }
        }
    }

    /**
     * 递归收集一个 ROM 条目的关联文件到 pending（源绝对路径 → 目标绝对路径，保序去重）。
     * <p>
     * 条目为 .cue（cueCtx.enabled 时）→ 解析 FILE 引用，关联文件按源中相对 cue 的位置落到 cue 目标旁；
     * 条目为 .m3u → 继续展开其中条目（visited 集合 + MAX_NESTING_DEPTH 深度限制防无限递归）。
     * 引用文件缺失按 cue.onMissing 规则处理；调用方负责将条目自身放入 pending。
     */
    private void collectEntryChain(Path src, Path target, Map<Path, Path> pending,
                                   CueContext cueCtx, Game game, Long taskId,
                                   Set<Path> visited, int depth) throws Exception {
        if (depth > MAX_NESTING_DEPTH) {
            logger.warn("条目嵌套展开深度超过 {}，跳过: {}", MAX_NESTING_DEPTH, src);
            return;
        }
        String name = src.getFileName() != null ? src.getFileName().toString() : src.toString();
        String lower = name.toLowerCase();
        Path baseDir = src.getParent() != null ? src.getParent() : Paths.get(".");
        Path targetBaseDir = target.getParent() != null ? target.getParent() : Paths.get(".");

        if (lower.endsWith(".cue")) {
            if (!cueCtx.enabled) return;
            for (Path ref : parseCueFileReferences(src)) {
                if (!Files.exists(ref)) {
                    cueCtx.handleMissing(game, name + " 引用的 " + ref.getFileName());
                    continue;
                }
                String refRel = relativizeSafe(baseDir, ref);
                Path refTarget = targetBaseDir.resolve(refRel);
                boolean first = pending.putIfAbsent(ref.normalize().toAbsolutePath(), refTarget) == null;
                // 链式 cue（cue 引用 cue）继续展开；重复出现过的不再展开（防环）
                if (first && ref.getFileName().toString().toLowerCase().endsWith(".cue")) {
                    collectEntryChain(ref, refTarget, pending, cueCtx, game, taskId, visited, depth + 1);
                }
            }
        } else if (lower.endsWith(".m3u")) {
            // visited 防环：同一 m3u 只展开一次
            if (!visited.add(src.normalize().toAbsolutePath())) return;
            for (String raw : Files.readAllLines(src)) {
                String t = raw.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                if (t.startsWith("./") || t.startsWith(".\\")) t = t.substring(2);
                t = t.replace('\\', '/');
                Path child = baseDir.resolve(t).normalize();
                if (!Files.exists(child)) {
                    cueCtx.handleMissing(game, name + " 引用的 " + t);
                    continue;
                }
                String childRel = relativizeSafe(baseDir, child);
                Path childTarget = targetBaseDir.resolve(childRel);
                boolean first = pending.putIfAbsent(child.normalize().toAbsolutePath(), childTarget) == null;
                if (first) {
                    collectEntryChain(child, childTarget, pending, cueCtx, game, taskId, visited, depth + 1);
                }
            }
        }
    }

    /**
     * 解析多文件条目的源路径：绝对路径直接用；相对路径以 romRoot（平台 ROM 根目录）为基准。
     */
    private Path resolveEntrySource(Path entry, Path romRoot) {
        if (entry.isAbsolute()) return entry;
        if (romRoot != null) return romRoot.resolve(entry);
        return entry; // 相对工作目录，尽力而为
    }

    /**
     * 计算条目目标相对路径（防目录逃逸）：绝对路径降级为文件名，前导 ../ 段丢弃，
     * 确保目标路径不会逃出 romsDir。
     */
    private String safeRelativePath(String rel) {
        String result = rel.replace('\\', '/');
        try {
            Path p = Paths.get(result);
            if (p.isAbsolute()) {
                return p.getFileName() != null ? p.getFileName().toString() : result;
            }
        } catch (Exception e) {
            // 非法路径字符等，降级用原始串继续处理
        }
        while (result.startsWith("../")) {
            result = result.substring(3);
        }
        return result;
    }

    /**
     * 计算 file 相对 baseDir 的相对路径并归一分隔符为 /；
     * 若逃逸出 baseDir（前导 ../）或无法 relativize（跨盘符），降级返回文件名。
     */
    private String relativizeSafe(Path baseDir, Path file) {
        try {
            String rel = baseDir.relativize(file.normalize()).toString().replace('\\', '/');
            if (rel.startsWith("../") || rel.equals("..")) {
                return file.getFileName().toString();
            }
            return rel;
        } catch (IllegalArgumentException e) {
            // 跨盘符等无法 relativize 的情况，降级为文件名
            return file.getFileName().toString();
        }
    }

    // ==================== M3U 处理 ====================

    /**
     * 处理 M3U 文件：复制 M3U 引用的所有文件到目标目录。
     * <p>
     * 条目递归展开：条目为 .cue → 同时收集其 FILE 引用的关联文件；
     * 条目为 .m3u → 继续展开其中条目（visited 集合 + 深度限制防无限递归）。
     * 所有待复制文件去重后统一复制；引用文件缺失按 cue.onMissing 规则处理。
     * 重写的引用行路径分隔符归一为 /。
     */
    private void processM3UFile(Path sourceM3U, Path targetM3U, String romsDir,
                                 TemplateV3.RomOutput romsConfig, Game game,
                                 Platform platform, Map<String, String> gameVars,
                                 CueContext cueCtx, Long taskId) throws Exception {
        List<String> lines = Files.readAllLines(sourceM3U);
        List<String> updatedLines = new ArrayList<>();
        Path sourceDir = sourceM3U.getParent();
        Path targetDir = targetM3U.getParent();

        String m3uTarget = romsConfig.getM3u().getTarget();
        // 引用文件统一目标目录（模板 m3u.target 优先，否则 m3u 本体所在目标目录）
        Path refTargetDir = targetDir;
        if (m3uTarget != null && !m3uTarget.isEmpty()) {
            String resolvedTarget = resolveTemplateString(m3uTarget, platform, gameVars);
            if (resolvedTarget != null && !resolvedTarget.isEmpty()) {
                refTargetDir = Paths.get(resolvedTarget);
            }
        }

        // 统一收集待复制文件（按源绝对路径保序去重）：源绝对路径 → 目标绝对路径
        Map<Path, Path> pending = new LinkedHashMap<>();
        Set<Path> visited = new HashSet<>();

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

            if (!Files.exists(filePath)) {
                // 引用缺失：按 cue.onMissing 规则处理，m3u 行保留原样
                cueCtx.handleMissing(game, sourceM3U.getFileName() + " 引用的 " + filePath.getFileName());
                updatedLines.add(line);
                continue;
            }

            // 重写行：相对 m3u 所在目录的路径，分隔符归一为 /
            String rel = relativizeSafe(sourceDir, filePath);
            Path refTargetPath = refTargetDir.resolve(rel);
            pending.putIfAbsent(filePath.normalize().toAbsolutePath(), refTargetPath);
            // 条目为 cue/m3u 时递归展开关联文件
            collectEntryChain(filePath, refTargetPath, pending, cueCtx, game, taskId, visited, 0);

            updatedLines.add(rel);
        }

        // 统一复制（去重后）
        for (Map.Entry<Path, Path> e : pending.entrySet()) {
            copyFile(e.getKey(), e.getValue());
            if (taskId != null) {
                taskService.updateTaskLog(taskId, "复制 M3U 引用文件: " + e.getValue().getFileName()
                        + " → " + e.getValue().getParent().getFileName());
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

    private static String nvl(String value) {
        return value != null ? value : "";
    }
}
