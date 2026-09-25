package com.gamelist.service.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gamelist.model.Game;
import com.gamelist.model.ParsedDataFile;
import com.gamelist.model.Platform;
import com.gamelist.model.TemplateV3;
import com.gamelist.service.TaskService;
import com.gamelist.util.GameFieldAccessor;
import com.gamelist.util.GenericJsonParser;
import com.gamelist.util.GenericTextParser;
import com.gamelist.util.GenericXmlParser;
import com.gamelist.util.TemplateExpressionEngine;

/**
 * v3 模板导入服务。
 * <p>
 * 工作流程：
 * <ol>
 *   <li>加载 v3 模板</li>
 *   <li>根据 dataFileType 选择解析器（text → GenericTextParser，data → GenericXmlParser）</li>
 *   <li>解析数据文件 → ParsedDataFile（统一的 systemFields + games 结构）</li>
 *   <li>根据模板的 fieldMappings / mediaMappings 将 key-value 映射到 Game 对象</li>
 *   <li>注入计算变量（filename、filepath）</li>
 *   <li>多文件游戏展开（files: → 多条 Game 记录）</li>
 *   <li>媒体文件存在性检查 + mediaDiscovery 规则回退</li>
 * </ol>
 */
public class TemplateV3ImportService {

    private static final Logger logger = LoggerFactory.getLogger(TemplateV3ImportService.class);

    /** 是否执行 mediaDiscovery 规则扫描（默认 true，由调用方控制） */
    private boolean enableMediaDiscovery = true;

    /** 任务 ID（可选，用于进度回调） */
    private Long taskId;

    /** 任务服务（可选，用于进度回调） */
    private TaskService taskService;

    /** 目录列举缓存（线程安全），避免重复 listFiles() 调用 */
    private Map<String, File[]> dirListCache;

    /** 全局模板变量（执行前用户设定），注入每条游戏的 rawFields 供表达式与 {name} 占位符使用 */
    private Map<String, String> globalVariables = new HashMap<>();

    public void setGlobalVariables(Map<String, String> globalVariables) {
        this.globalVariables = globalVariables != null ? globalVariables : new HashMap<>();
    }

    public boolean isEnableMediaDiscovery() {
        return enableMediaDiscovery;
    }

    public void setEnableMediaDiscovery(boolean enableMediaDiscovery) {
        this.enableMediaDiscovery = enableMediaDiscovery;
    }

    public void setTaskContext(Long taskId, TaskService taskService) {
        this.taskId = taskId;
        this.taskService = taskService;
    }

    /**
     * 使用 v3 模板导入数据文件。
     *
     * @param dataFile 数据文件（gamelist.xml、metadata.pegasus.txt 等）
     * @param template v3 导入模板
     * @return 解析并映射后的 Game 列表
     */
    public List<Game> importFile(File dataFile, TemplateV3 template) throws Exception {
        return importFileWithHeader(dataFile, template).getGames();
    }

    /**
     * 使用 v3 模板导入数据文件，并同时返回表头（systemFields）。
     * <p>
     * 与 {@link #importFile} 的区别：保留解析出的系统级表头字段，
     * 供调用方通过 {@link #applySystemFieldsToPlatform} 写入 Platform。
     *
     * @param dataFile 数据文件
     * @param template v3 导入模板
     * @return 游戏列表 + 表头字段
     */
    public TemplateV3ImportResult importFileWithHeader(File dataFile, TemplateV3 template) throws Exception {
        if (!template.getTemplateInfo().isImport()) {
            throw new IllegalArgumentException("模板方向不是 import: " + template.getTemplateInfo().getDirection());
        }

        dirListCache = new ConcurrentHashMap<>();
        try {
            // 第一步：解析数据文件
            ParsedDataFile parsed = parseFile(dataFile, template);
            logger.info("解析完成: 系统字段 {} 个, 游戏 {} 条",
                    parsed.getSystemFields().size(), parsed.getGameCount());

            // 第二步：映射到 Game 对象（含 filename 注入、多文件展开、媒体检查）
            File baseDir = dataFile.getParentFile();
            List<Game> games = mapToGames(parsed, template, baseDir);
            logger.info("映射完成: {} 条游戏", games.size());

            return new TemplateV3ImportResult(games, parsed.getSystemFields());
        } finally {
            dirListCache = null;
        }
    }

    /**
     * 根据模板选择解析器并解析数据文件。
     */
    private ParsedDataFile parseFile(File dataFile, TemplateV3 template) throws Exception {
        String fileType = template.getTemplateInfo().getDataFileType();

        if ("text".equalsIgnoreCase(fileType)) {
            return GenericTextParser.parse(dataFile, template);
        } else if ("data".equalsIgnoreCase(fileType)) {
            // data 型按 templateInfo.format 细分（缺省视为 XML，保持向后兼容）
            String format = template.getTemplateInfo().getFormat();
            if ("json".equalsIgnoreCase(format)) {
                return GenericJsonParser.parse(dataFile, template);
            }
            return GenericXmlParser.parse(dataFile, template);
        } else {
            throw new UnsupportedOperationException("不支持的数据文件类型: " + fileType);
        }
    }

    /**
     * 将解析结果映射为 Game 对象列表。
     * <p>
     * 包含以下增强逻辑：
     * <ul>
     *   <li>注入 filename / filepath 计算变量</li>
     *   <li>多文件游戏展开（path 含换行符 → 多条 Game）</li>
     *   <li>媒体路径存在性验证 + mediaDiscovery 回退</li>
     * </ul>
     */
    private List<Game> mapToGames(ParsedDataFile parsed, TemplateV3 template, File baseDir) {
        List<Game> games = new ArrayList<>();
        Map<String, Object> gameInfoMapping = template.getGame() != null ? template.getGame().getGameInfo() : null;
        Map<String, Object> mediaInfoMapping = template.getGame() != null ? template.getGame().getMediaInfo() : null;
        TemplateV3.MediaDiscovery mediaDiscovery = template.getGame() != null ? template.getGame().getMediaDiscovery() : null;

        List<Map<String, String>> parsedGames = parsed.getGames();
        int totalGames = parsedGames.size();

        // —— 第一步：顺序完成字段映射 + 多文件展开（纯 CPU 操作，很快） ——
        // 收集所有展开后的 (Game, mediaValues) 对
        List<Game> allGames = new ArrayList<>();
        List<Map<String, String>> allMediaValues = new ArrayList<>();
        List<Map<String, String>> allRawFields = new ArrayList<>();

        for (int idx = 0; idx < totalGames; idx++) {
            Map<String, String> rawFields = parsedGames.get(idx);

            // —— 进度回调 ——
            if (taskId != null && taskService != null && (idx % 10 == 0 || idx == totalGames - 1)) {
                int progress = (int) ((double) idx / totalGames * 90) + 5;
                taskService.updateTaskProgress(taskId, progress, "解析游戏 " + (idx + 1) + "/" + totalGames, idx, totalGames);
            }

            // —— 注入全局模板变量（用户执行前设定），游戏自身解析字段优先 ——
            for (Map.Entry<String, String> gv : globalVariables.entrySet()) {
                if (gv.getValue() != null) {
                    rawFields.putIfAbsent(gv.getKey(), gv.getValue());
                }
            }

            // —— 注入计算变量（从模板 computedVariables 读取） ——
            injectComputedVariables(rawFields, template);

            // —— 映射游戏信息字段 ——
            Game game = new Game();
            if (gameInfoMapping != null) {
                for (Map.Entry<String, Object> entry : gameInfoMapping.entrySet()) {
                    String dbColumn = entry.getKey();
                    List<String> candidates = TemplateV3.toCandidateList(entry.getValue());
                    String value = resolveValue(rawFields, candidates);
                    if (value != null && !value.isEmpty()) {
                        GameFieldAccessor.setValue(game, dbColumn, value);
                    }
                }
            }

            // —— 映射媒体信息字段（先保留原始值，后续统一处理存在性） ——
            Map<String, String> mediaValues = new LinkedHashMap<>();
            if (mediaInfoMapping != null) {
                for (Map.Entry<String, Object> entry : mediaInfoMapping.entrySet()) {
                    String nomcourt = entry.getKey();
                    List<String> candidates = TemplateV3.toCandidateList(entry.getValue());
                    String value = resolveValue(rawFields, candidates);
                    if (value != null && !value.isEmpty()) {
                        mediaValues.put(nomcourt, value);
                    }
                }
            }

            // —— 多文件检测（模板 multiFileDetection 配置）：写入 multiFile/multiFileContent ——
            boolean multiFileDetected = applyMultiFileDetection(game, rawFields, template, baseDir);

            // —— 多文件游戏展开（从模板 multiFile 配置读取） ——
            List<Game> expandedGames;
            if (multiFileDetected) {
                // 已检测为多文件条目，保持单条记录，不再展开
                expandedGames = new ArrayList<>();
                expandedGames.add(game);
            } else {
                expandedGames = expandMultiFile(game, rawFields, template);
            }

            for (Game g : expandedGames) {
                allGames.add(g);
                allMediaValues.add(mediaValues);
                allRawFields.add(rawFields);
            }
        }

        // —— 第二步：并行处理媒体路径（I/O 密集型，用线程池加速） ——
        int poolSize = Math.min(4, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        try {
            List<Future<?>> futures = new ArrayList<>();
            AtomicInteger completedCount = new AtomicInteger(0);

            for (int i = 0; i < allGames.size(); i++) {
                final Game g = allGames.get(i);
                final Map<String, String> mv = allMediaValues.get(i);
                final Map<String, String> rf = allRawFields.get(i);
                final int gameIdx = i;

                futures.add(executor.submit(() -> {
                    processMediaPaths(g, mv, baseDir, mediaDiscovery, rf);

                    // 进度回调（每 10 个游戏更新一次）
                    int done = completedCount.incrementAndGet();
                    if (taskId != null && taskService != null && (done % 10 == 0 || done == allGames.size())) {
                        int progress = (int) ((double) done / allGames.size() * 90) + 5;
                        taskService.updateTaskProgress(taskId, progress,
                                "媒体发现 " + done + "/" + allGames.size(), done, allGames.size());
                    }
                }));
            }

            // 等待所有任务完成
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    logger.error("媒体处理任务失败", e);
                }
            }

            games.addAll(allGames);
            logger.info("媒体发现完成: {} 个游戏, 线程池大小: {}", allGames.size(), poolSize);
        } finally {
            executor.shutdownNow();
        }

        return games;
    }

    /**
     * 向 rawFields 注入计算变量。
     * <p>
     * 优先从模板的 game.computedVariables 读取变量定义（key=变量名, value=表达式），
     * 通过表达式引擎求值后注入到 rawFields。
     * <p>
     * 当 computedVariables 为 null 时（旧模板兼容），回退到硬编码逻辑：
     * 从 path 字段计算 filename 和 filepath。
     */
    private void injectComputedVariables(Map<String, String> rawFields, TemplateV3 template) {
        Map<String, String> computedVars = null;
        if (template.getGame() != null) {
            computedVars = template.getGame().getComputedVariables();
        }

        if (computedVars != null && !computedVars.isEmpty()) {
            // JSON 驱动模式：按模板定义的表达式求值
            for (Map.Entry<String, String> entry : computedVars.entrySet()) {
                String varName = entry.getKey();
                String expression = entry.getValue();
                try {
                    String value = TemplateExpressionEngine.evaluate(expression, null, rawFields);
                    if (value != null) {
                        rawFields.put(varName, value);
                    }
                } catch (Exception e) {
                    logger.warn("计算变量求值失败: {} = {} → {}", varName, expression, e.getMessage());
                }
            }
        } else {
            // 向后兼容回退：旧模板无 computedVariables 时执行硬编码逻辑
            injectDefaultComputedVariables(rawFields);
        }
    }

    /**
     * 默认计算变量注入（向后兼容回退）。
     * <ul>
     *   <li>filename — path 的文件名部分（不含扩展名），如 "./game1.rom" → "game1"</li>
     *   <li>filepath — path 去掉 ./ 前缀和扩展名，如 "./sub/game1.rom" → "sub/game1"</li>
     * </ul>
     */
    private void injectDefaultComputedVariables(Map<String, String> rawFields) {
        String path = rawFields.get("path");
        if (path == null || path.isEmpty()) return;

        // 取第一行（多文件场景只用第一个文件计算变量）
        if (path.contains("\n")) {
            path = path.split("\n")[0].trim();
        }

        // 提取文件名（含扩展名）
        String fileNameWithExt = path;
        int lastSep = Math.max(fileNameWithExt.lastIndexOf('/'), fileNameWithExt.lastIndexOf('\\'));
        if (lastSep >= 0) {
            fileNameWithExt = fileNameWithExt.substring(lastSep + 1);
        }

        // filename = 不含扩展名的文件名
        String filename = fileNameWithExt;
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            filename = filename.substring(0, dot);
        }
        rawFields.put("filename", filename);

        // filepath = 去掉 ./ 前缀和扩展名的完整路径
        String cleanPath = path;
        if (cleanPath.startsWith("./") || cleanPath.startsWith(".\\")) {
            cleanPath = cleanPath.substring(2);
        }
        int pathDot = cleanPath.lastIndexOf('.');
        if (pathDot > 0) {
            String ext = cleanPath.substring(pathDot + 1);
            if (!ext.contains("/") && !ext.contains("\\")) {
                cleanPath = cleanPath.substring(0, pathDot);
            }
        }
        rawFields.put("filepath", cleanPath);
    }

    /**
     * 多文件检测（模板 game.multiFileDetection 配置）。
     * <p>
     * 触发条件与内容来源由模板声明，引擎只提供通用动作：
     * <ul>
     *   <li>trigger=fieldExists — source 字段存在即触发（如 Pegasus files: 标签，不论行数）</li>
     *   <li>trigger=endsWith — source 字段首行以 pattern 结尾（如 path 指向 .m3u，大小写不敏感）</li>
     * </ul>
     * 内容：content=fieldValue（源字段值清洗）或 content=fileContent（读文件内容，如 m3u）。
     * pathFrom=firstLine 时 path 取清洗后第一行。触发后设置 multiFile=true。
     *
     * @return 是否触发检测（触发后调用方应保持单条记录，不再展开）
     */
    private boolean applyMultiFileDetection(Game game, Map<String, String> rawFields,
                                            TemplateV3 template, File baseDir) {
        TemplateV3.MultiFileDetectionConfig cfg = template.getGame() != null
                ? template.getGame().getMultiFileDetection() : null;
        if (cfg == null || !cfg.isEnabled()) return false;

        String sourceValue = rawFields.get(cfg.getSource());
        if (sourceValue == null || sourceValue.trim().isEmpty()) return false;

        // —— 触发条件判断 ——
        boolean triggered;
        if ("endsWith".equalsIgnoreCase(cfg.getTrigger())) {
            String firstLine = sourceValue.split("\\r?\\n")[0].trim();
            String pattern = cfg.getPattern() != null ? cfg.getPattern() : ".m3u";
            triggered = firstLine.toLowerCase().endsWith(pattern.toLowerCase());
        } else {
            // fieldExists（默认）：字段存在即触发（非空已在上方判断，如 Pegasus files: 标签）
            triggered = true;
        }
        if (!triggered) return false;

        // —— 内容获取 ——
        String content;
        if ("fileContent".equalsIgnoreCase(cfg.getContent())) {
            content = readFileContentAsM3U(sourceValue.split("\\r?\\n")[0].trim(), baseDir);
        } else {
            content = cleanM3ULines(sourceValue);
        }
        if (content == null || content.isEmpty()) return false;

        // —— pathFrom=firstLine：path 取清洗后第一行 ——
        if ("firstLine".equalsIgnoreCase(cfg.getPathFrom())) {
            game.setPath(content.split("\\r?\\n")[0].trim());
        }

        game.setMultiFile(true);
        game.setMultiFileContent(content);
        logger.debug("多文件检测触发: {} (source={}, trigger={})", game.getName(), cfg.getSource(), cfg.getTrigger());
        return true;
    }

    /**
     * 清洗 m3u 风格文本：过滤 # 注释行与空行，每行 trim，去掉 ./ 或 .\ 前缀。
     */
    private String cleanM3ULines(String raw) {
        if (raw == null) return null;
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            if (t.startsWith("./") || t.startsWith(".\\")) {
                t = t.substring(2);
            }
            sb.append(t).append("\n");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 读取 m3u 文件内容并清洗（相对路径基于 baseDir 解析，绝对路径直接使用）。
     */
    private String readFileContentAsM3U(String pathStr, File baseDir) {
        try {
            File f = new File(pathStr);
            if (!f.isAbsolute() && baseDir != null) {
                f = new File(baseDir, pathStr);
            }
            if (!f.exists()) {
                logger.warn("多文件检测: 文件不存在: {}", f.getAbsolutePath());
                return null;
            }
            String raw = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            return cleanM3ULines(raw);
        } catch (Exception e) {
            logger.warn("多文件检测: 读取文件失败: {} → {}", pathStr, e.getMessage());
            return null;
        }
    }

    /**
     * 多文件游戏展开。
     * <p>
     * 从模板的 game.multiFile 配置读取展开参数：
     * <ul>
     *   <li>enabled — 是否启用展开（默认 false）</li>
     *   <li>field — 要展开的字段（默认 "path"）</li>
     *   <li>separator — 分隔符（默认 "\n"）</li>
     * </ul>
     * 当 multiFile 未配置或 disabled 时，直接返回原 Game。
     */
    private List<Game> expandMultiFile(Game game, Map<String, String> rawFields, TemplateV3 template) {
        List<Game> result = new ArrayList<>();

        // 从模板读取多文件配置
        TemplateV3.MultiFileConfig multiFileConfig = null;
        if (template.getGame() != null) {
            multiFileConfig = template.getGame().getMultiFile();
        }

        // 未配置或禁用时直接返回
        if (multiFileConfig == null || !multiFileConfig.isEnabled()) {
            result.add(game);
            return result;
        }

        String fieldName = multiFileConfig.getField() != null ? multiFileConfig.getField() : "path";
        String separator = multiFileConfig.getSeparator() != null ? multiFileConfig.getSeparator() : "\n";

        String fieldValue = GameFieldAccessor.getValue(game, fieldName);
        if (fieldValue == null || !fieldValue.contains(separator)) {
            result.add(game);
            return result;
        }

        String[] values = fieldValue.split(separator.equals("\n") ? "\n" : java.util.regex.Pattern.quote(separator));
        for (String v : values) {
            String trimmed = v.trim();
            if (trimmed.isEmpty()) continue;

            Game copy;
            if (result.isEmpty()) {
                copy = game; // 第一个使用原对象
            } else {
                copy = cloneGame(game);
            }
            GameFieldAccessor.setValue(copy, fieldName, trimmed);
            result.add(copy);
        }

        if (result.size() > 1) {
            logger.debug("多文件游戏展开: {} → {} 条记录 (field={})", game.getName(), result.size(), fieldName);
        }
        return result;
    }

    /**
     * 浅拷贝 Game 对象（用于多文件展开）。
     */
    private Game cloneGame(Game source) {
        Game copy = new Game();
        copy.setName(source.getName());
        copy.setTranslatedName(source.getTranslatedName());
        copy.setDesc(source.getDesc());
        copy.setTranslatedDesc(source.getTranslatedDesc());
        copy.setReleasedate(source.getReleasedate());
        copy.setDeveloper(source.getDeveloper());
        copy.setPublisher(source.getPublisher());
        copy.setGenre(source.getGenre());
        copy.setPlayers(source.getPlayers());
        copy.setRating(source.getRating());
        copy.setLang(source.getLang());
        copy.setSortBy(source.getSortBy());
        copy.setHash(source.getHash());
        copy.setGameId(source.getGameId());
        copy.setSource(source.getSource());
        copy.setPlatformId(source.getPlatformId());
        copy.setPlatformPath(source.getPlatformPath());
        return copy;
    }

    /**
     * 无数据文件导入模式下的媒体发现入口（公开方法，供 GameServiceImpl 调用）。
     * <p>
     * 与 {@link #processMediaPaths} 的区别：
     * 此方法专为无数据文件导入设计，mediaValues 为空时会触发全量 mediaDiscovery 规则匹配。
     *
     * @param game           游戏对象
     * @param mediaValues    媒体路径映射（无数据文件时传空 Map）
     * @param baseDir        扫描根目录
     * @param mediaDiscovery 模板中的媒体发现配置
     */
    public void processMediaPathsForNoDataFile(Game game, Map<String, String> mediaValues,
                                                File baseDir, TemplateV3.MediaDiscovery mediaDiscovery) {
        processMediaPaths(game, mediaValues, baseDir, mediaDiscovery, null);
    }

    /**
     * 处理媒体路径（两阶段）：
     * <ol>
     *   <li>阶段一：验证数据文件中的媒体路径是否存在 → 存在则写入</li>
     *   <li>阶段二：对尚未设置的媒体类型，若 mediaDiscovery 启用则执行规则匹配</li>
     * </ol>
     * <p>
     * 即使 data file 中没有 assets.* 字段（mediaValues 为空），
     * 只要 mediaDiscovery 启用，仍会扫描磁盘查找媒体文件。
     */
    private void processMediaPaths(Game game, Map<String, String> mediaValues,
                                    File baseDir, TemplateV3.MediaDiscovery mediaDiscovery,
                                    Map<String, String> rawFields) {
        Set<String> processedTypes = new HashSet<>();

        // —— 预构建游戏子目录文件索引（每个游戏只构建一次，所有媒体类型共享） ——
        Map<String, Map<String, File>> subDirIndexes = new HashMap<>();
        boolean needIndex = enableMediaDiscovery && mediaDiscovery != null && mediaDiscovery.isEnabled();
        if (needIndex && baseDir != null) {
            String filename = resolveFilename(game);
            String name = game.getName();
            String baseDirName = mediaDiscovery.getBaseDir();
            if (baseDirName != null && !baseDirName.isEmpty()) {
                File mediaBaseDir = new File(baseDir, baseDirName);
                List<String> subDirPatterns = mediaDiscovery.getSubDirPatterns();
                if (subDirPatterns != null) {
                    for (String pattern : subDirPatterns) {
                        String folderName = resolvePattern(pattern, filename, name, rawFields);
                        if (folderName != null && !folderName.isEmpty()) {
                            subDirIndexes.put(folderName, buildGameFileIndex(mediaBaseDir, folderName));
                        }
                    }
                }
            }
        }

        // —— 阶段一：处理数据文件中声明的媒体路径 ——
        for (Map.Entry<String, String> entry : mediaValues.entrySet()) {
            String nomcourt = entry.getKey();
            String rawPath = entry.getValue();
            processedTypes.add(nomcourt);

            // 检查数据文件中的路径是否存在
            String cleanPath = rawPath;
            if (cleanPath.startsWith("./") || cleanPath.startsWith(".\\")) {
                cleanPath = cleanPath.substring(2);
            }

            boolean found = false;
            if (baseDir != null) {
                File mediaFile = new File(baseDir, cleanPath);
                if (mediaFile.exists()) {
                    GameFieldAccessor.setValue(game, nomcourt, cleanPath);
                    found = true;
                    logger.debug("媒体文件存在: {} → {}", nomcourt, cleanPath);
                }
            }

            // 不存在 → 仅在 enableMediaDiscovery=true 时走 mediaDiscovery 规则
            if (!found && needIndex) {
                applyMediaDiscovery(game, nomcourt, mediaDiscovery, baseDir, subDirIndexes, rawFields);
            } else if (!found) {
                logger.debug("媒体文件不存在且跳过 mediaDiscovery: {} → {}", nomcourt, rawPath);
            }
        }

        // —— 阶段二：仅在 enableMediaDiscovery=true 时，对未在 data file 中声明的类型尝试 mediaDiscovery ——
        if (needIndex && mediaDiscovery.getRules() != null) {
            for (String nomcourt : mediaDiscovery.getRules().keySet()) {
                if (processedTypes.contains(nomcourt)) continue;

                // 跳过已设置的字段
                String existing = GameFieldAccessor.getValue(game, nomcourt);
                if (existing != null && !existing.isEmpty()) continue;

                applyMediaDiscovery(game, nomcourt, mediaDiscovery, baseDir, subDirIndexes, rawFields);
            }
        }
    }

    /**
     * 按 mediaDiscovery 规则在磁盘上查找媒体文件。
     * <p>
     * 遍历模板中定义的 subDirPatterns 列表，按顺序尝试每种命名策略：
     * 例如 ["{filename}", "{name}"] 表示先用 ROM 文件名匹配，找不到再用显示名匹配。
     * <p>
     * 文件夹名匹配大小写不敏感（兼容 Windows 实际文件夹命名）。
     *
     * @return true 如果找到并设置了该媒体字段
     */
    private boolean applyMediaDiscovery(Game game, String nomcourt,
                                         TemplateV3.MediaDiscovery mediaDiscovery, File baseDir,
                                         Map<String, Map<String, File>> subDirIndexes,
                                         Map<String, String> rawFields) {
        if (baseDir == null) return false;

        List<String> rules = mediaDiscovery.getRules().get(nomcourt);
        if (rules == null || rules.isEmpty()) return false;

        // 解析变量值
        String filename = resolveFilename(game);
        String name = game.getName();
        if (filename == null || filename.isEmpty()) return false;

        // 扩展名列表：模板必须显式声明，无默认值
        List<String> extensions = mediaDiscovery.getExtensions();
        if (extensions == null || extensions.isEmpty()) {
            logger.warn("mediaDiscovery 未配置 extensions，跳过媒体发现: {}", nomcourt);
            return false;
        }

        // 基础目录：模板必须显式声明，无默认值
        String baseDirName = mediaDiscovery.getBaseDir();
        if (baseDirName == null || baseDirName.isEmpty()) {
            logger.warn("mediaDiscovery 未配置 baseDir，跳过媒体发现: {}", nomcourt);
            return false;
        }

        File mediaBaseDir = new File(baseDir, baseDirName);

        // 子目录命名策略：模板必须显式声明，无默认值
        List<String> subDirPatterns = mediaDiscovery.getSubDirPatterns();
        if (subDirPatterns == null || subDirPatterns.isEmpty()) {
            logger.warn("mediaDiscovery 未配置 subDirPatterns，跳过媒体发现: {}", nomcourt);
            return false;
        }

        for (String pattern : subDirPatterns) {
            // 将模式解析为具体值
            String folderName = resolvePattern(pattern, filename, name, rawFields);
            if (folderName == null || folderName.isEmpty()) continue;

            // 使用预构建的索引（由 processMediaPaths 为每个游戏统一构建）
            Map<String, File> gameFileIndex = subDirIndexes.getOrDefault(folderName, java.util.Collections.emptyMap());

            if (tryDiscoveryRules(game, nomcourt, rules, folderName, name, extensions, mediaBaseDir, baseDir, gameFileIndex, rawFields)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 将模式字符串解析为具体值。
     * <p>
     * 支持 {filename} 和 {name} 占位符，如果模式就是其中一个则直接返回对应值。
     * 其他 {var} 占位符从 rawFields（含 computedVariables 注入的变量）解析，
     * 使模板可以声明自定义变量（如 sanitize 后的名称）供 mediaDiscovery 规则使用。
     *
     * @param pattern  模式字符串，如 "{filename}" 或 "{name}"
     * @param filename ROM 文件名去扩展名
     * @param name     游戏显示名
     * @param rawFields 原始解析字段（含 computedVariables），可为 null
     * @return 解析后的具体值
     */
    private String resolvePattern(String pattern, String filename, String name, Map<String, String> rawFields) {
        if (pattern == null || pattern.isEmpty()) return null;
        return replaceCustomVars(pattern
                .replace("{filename}", filename != null ? filename : "")
                .replace("{name}", name != null ? name : ""), rawFields);
    }

    /**
     * 将字符串中剩余的 {var} 占位符替换为 rawFields 中同名变量的值。
     */
    private String replaceCustomVars(String value, Map<String, String> rawFields) {
        if (rawFields == null || rawFields.isEmpty() || !value.contains("{")) {
            return value;
        }
        String result = value;
        for (Map.Entry<String, String> entry : rawFields.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    /**
     * 从游戏的 path 字段计算文件名（去扩展名）。
     */
    private String resolveFilename(Game game) {
        String filename = GameFieldAccessor.getValue(game, "filename");
        if (filename != null && !filename.isEmpty()) return filename;

        String path = game.getPath();
        if (path != null && !path.isEmpty()) {
            String fn = path;
            int sep = Math.max(fn.lastIndexOf('/'), fn.lastIndexOf('\\'));
            if (sep >= 0) fn = fn.substring(sep + 1);
            int dot = fn.lastIndexOf('.');
            if (dot > 0) fn = fn.substring(0, dot);
            return fn;
        }
        return null;
    }

    /**
     * 获取文件名的小写扩展名（不含点号）。
     * 如 "boxFront.png" → "png"，无扩展名时返回空字符串。
     */
    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0 && dot < fileName.length() - 1) ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    /**
     * 用指定的 folderName 尝试所有 discovery 规则。
     * <p>
     * 优化策略：对于 {folderName}/xxx.{ext} 形式的规则，直接在预构建的 gameFileIndex 中匹配，
     * 避免对每个规则×扩展名组合都发起文件系统调用。
     *
     * @param folderName 用于替换 {filename} 和 {name} 的文件夹名
     * @param gameFileIndex 游戏子目录的文件索引（小写文件名 → File），可为空 Map
     * @return true 如果找到并设置了该媒体字段
     */
    private boolean tryDiscoveryRules(Game game, String nomcourt, List<String> rules,
                                       String folderName, String name,
                                       List<String> extensions,
                                       File mediaBaseDir, File baseDir,
                                       Map<String, File> gameFileIndex,
                                       Map<String, String> rawFields) {
        for (String rule : rules) {
            String mediaPath = replaceCustomVars(rule
                    .replace("{filename}", folderName)
                    .replace("{name}", name != null ? name : folderName), rawFields);

            if (mediaPath.contains("{ext}")) {
                String basePath = mediaPath.substring(0, mediaPath.indexOf("{ext}"));

                // 判断规则是否指向游戏子目录（{folderName}/xxx.{ext}）
                String prefix = folderName + "/";
                String prefixBack = folderName + "\\";
                if (basePath.startsWith(prefix) || basePath.startsWith(prefixBack)) {
                    // 使用茎名索引匹配：一次查找代替 N 次扩展名遍历
                    String filePart = basePath.substring(folderName.length() + 1);
                    // filePart 形如 "boxFront."，去掉末尾的点号得到茎名
                    String stem = filePart.endsWith(".") ? filePart.substring(0, filePart.length() - 1) : filePart;
                    stem = stem.toLowerCase();
                    File found = gameFileIndex.get(stem);
                    if (found != null) {
                        // 验证扩展名是否在允许列表中
                        String ext = getExtension(found.getName());
                        if (extensions.contains(ext)) {
                            String relativePath = getRelativeMediaPath(found, baseDir);
                            GameFieldAccessor.setValue(game, nomcourt, relativePath);
                            logger.debug("mediaDiscovery 匹配: {} → {} (folder={})", nomcourt, relativePath, folderName);
                            return true;
                        }
                    }
                } else {
                    // 规则指向其他目录，回退到文件系统查找
                    for (String ext : extensions) {
                        File mediaFile = findFileCaseInsensitive(mediaBaseDir, basePath + ext);
                        if (mediaFile != null) {
                            String relativePath = getRelativeMediaPath(mediaFile, baseDir);
                            GameFieldAccessor.setValue(game, nomcourt, relativePath);
                            logger.debug("mediaDiscovery 匹配: {} → {} (folder={})", nomcourt, relativePath, folderName);
                            return true;
                        }
                    }
                }
            } else {
                File mediaFile = findFileCaseInsensitive(mediaBaseDir, mediaPath);
                if (mediaFile != null) {
                    String relativePath = getRelativeMediaPath(mediaFile, baseDir);
                    GameFieldAccessor.setValue(game, nomcourt, relativePath);
                    logger.debug("mediaDiscovery 匹配: {} → {} (folder={})", nomcourt, relativePath, folderName);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 构建游戏子目录的文件茎索引（大小写不敏感）。
     * <p>
     * 将子目录中所有文件的 "小写茎名 → File" 存入 Map，
     * 茎名 = 文件名去掉扩展名（如 "boxFront.png" → "boxfront"）。
     * 规则匹配时只需按茎名查找一次，再验证扩展名即可，
     * 避免对每种扩展名都做一次查找。
     *
     * @param mediaBaseDir 媒体根目录（如 media/）
     * @param folderName   游戏子目录名（如 "BurgerTime (USA)"）
     * @return 小写茎名 → File 的映射，目录不存在时返回空 Map
     */
    private Map<String, File> buildGameFileIndex(File mediaBaseDir, String folderName) {
        Map<String, File> index = new HashMap<>();
        File gameDir = null;

        // 先精确匹配
        File exactDir = new File(mediaBaseDir, folderName);
        if (exactDir.exists() && exactDir.isDirectory()) {
            gameDir = exactDir;
        } else {
            // 大小写不敏感查找（使用缓存）
            File[] mediaChildren = listDirCached(mediaBaseDir);
            if (mediaChildren != null) {
                for (File f : mediaChildren) {
                    if (f.isDirectory() && f.getName().equalsIgnoreCase(folderName)) {
                        gameDir = f;
                        break;
                    }
                }
            }
        }

        if (gameDir == null) return index;

        // 列举游戏子目录中的所有文件，按茎名建索引
        File[] files = listDirCached(gameDir);
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    String name = f.getName();
                    int dot = name.lastIndexOf('.');
                    String stem = (dot > 0) ? name.substring(0, dot).toLowerCase() : name.toLowerCase();
                    index.put(stem, f);
                }
            }
        }

        return index;
    }

    /**
     * 大小写不敏感的文件查找。
     * <p>
     * 将路径按分隔符拆分为段，逐段在文件系统中查找匹配（忽略大小写），
     * 兼容 Windows 上文件夹名大小写与实际不一致的情况。
     * 例如：规则路径 "Game 1/boxFront.png"，实际文件夹 "game 1/" 也能匹配。
     *
     * @param baseDir 搜索起始目录
     * @param relativePath 相对路径（用 / 或 \ 分隔）
     * @return 找到的 File，未找到返回 null
     */
    private File findFileCaseInsensitive(File baseDir, String relativePath) {
        // 先尝试精确匹配（最快路径）
        File exact = new File(baseDir, relativePath);
        if (exact.exists()) return exact;

        // 按分隔符拆分路径段，逐段大小写不敏感匹配
        String[] segments = relativePath.split("[/\\\\]");
        File current = baseDir;
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            File child = new File(current, segment);
            if (child.exists()) {
                current = child;
                continue;
            }
            // 大小写不敏感查找（使用缓存避免重复 listFiles）
            File[] children = listDirCached(current);
            if (children != null) {
                boolean found = false;
                for (File f : children) {
                    if (f.getName().equalsIgnoreCase(segment)) {
                        current = f;
                        found = true;
                        break;
                    }
                }
                if (!found) return null;
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * 带缓存的目录列举，避免对同一目录重复调用 listFiles()。
     */
    private File[] listDirCached(File dir) {
        if (dirListCache == null) {
            return dir.listFiles();
        }
        String key = dir.getAbsolutePath();
        File[] cached = dirListCache.get(key);
        if (cached != null) {
            return cached;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            dirListCache.put(key, children);
        }
        return children;
    }

    /**
     * 计算媒体文件相对于 baseDir 的路径（用 / 分隔）。
     */
    private String getRelativeMediaPath(File mediaFile, File baseDir) {
        String absoluteBase = baseDir.getAbsolutePath();
        String absoluteFile = mediaFile.getAbsolutePath();
        String relative = absoluteFile.substring(absoluteBase.length());
        if (relative.startsWith(File.separator)) {
            relative = relative.substring(1);
        }
        // 统一用 / 分隔
        return relative.replace("\\", "/");
    }

    /**
     * 从原始字段中按候选列表查找第一个有值的字段。
     * 支持表达式：如果候选值包含函数调用，则通过表达式引擎求值。
     */
    private String resolveValue(Map<String, String> rawFields, List<String> candidates) {
        if (candidates == null) return null;

        for (String candidate : candidates) {
            // 检查是否为表达式
            if (TemplateExpressionEngine.isExpression(candidate)) {
                String value = TemplateExpressionEngine.evaluate(candidate, null, rawFields);
                if (value != null && !value.isEmpty()) return value;
            } else {
                // 直接查找（大小写不敏感）
                String value = rawFields.get(candidate.toLowerCase());
                if (value != null && !value.isEmpty()) return value;
                // 兼容原始大小写
                value = rawFields.get(candidate);
                if (value != null && !value.isEmpty()) return value;
            }
        }
        return null;
    }

    // ==================== 表头（systemFields）映射到 Platform ====================

    /**
     * 按模板 system.fields 映射，将解析出的表头写入 Platform。
     * <p>
     * system.fields 的 key 形如 "platform.<字段名>"，value 为候选表头键列表。
     * 仅当解析到非空值时才覆盖 Platform 对应字段（因此调用前可先设好兜底值，
     * 如平台显示名的生成名）。
     *
     * @param platform     目标平台对象
     * @param systemFields 解析出的表头键值对（key 已按模板 keyCase 处理）
     * @param template     v3 模板（提供 system.fields 映射）
     */
    public void applySystemFieldsToPlatform(Platform platform, Map<String, String> systemFields, TemplateV3 template) {
        if (platform == null || systemFields == null || systemFields.isEmpty()) return;
        if (template.getSystem() == null || template.getSystem().getFields() == null) return;

        for (Map.Entry<String, Object> entry : template.getSystem().getFields().entrySet()) {
            String target = entry.getKey();               // 如 "platform.name"
            if (target == null) continue;
            String fieldName = target.startsWith("platform.") ? target.substring("platform.".length()) : target;

            List<String> candidates = TemplateV3.toCandidateList(entry.getValue());
            String value = resolveValue(systemFields, candidates);
            if (value == null) continue;

            // 去掉多行续行带来的前导/尾部空白
            value = value.trim();
            if (value.isEmpty()) continue;

            // 扩展名 / 忽略文件：把续行换行折叠为逗号分隔，保持单行
            if ("extensions".equals(fieldName) || "ignoreFiles".equals(fieldName)) {
                value = value.replaceAll("\\s*\\n\\s*", ", ");
            }

            setPlatformField(platform, fieldName, value);
        }
    }

    /**
     * 按字段名将值写入 Platform（仅支持已知的表头可映射列）。
     */
    private void setPlatformField(Platform platform, String fieldName, String value) {
        switch (fieldName) {
            case "system":      platform.setSystem(value); break;
            case "name":        platform.setName(value); break;
            case "launch":      platform.setLaunch(value); break;
            case "database":    platform.setDatabase(value); break;
            case "sortBy":      platform.setSortBy(value); break;
            case "extensions":  platform.setExtensions(value); break;
            case "ignoreFiles": platform.setIgnoreFiles(value); break;
            default:
                logger.warn("system.fields 映射到未知 Platform 字段，已忽略: {}", fieldName);
        }
    }

    /**
     * v3 导入结果：游戏列表 + 表头字段。
     */
    public static class TemplateV3ImportResult {
        private final List<Game> games;
        private final Map<String, String> systemFields;

        public TemplateV3ImportResult(List<Game> games, Map<String, String> systemFields) {
            this.games = games;
            this.systemFields = systemFields;
        }

        public List<Game> getGames() { return games; }

        public Map<String, String> getSystemFields() { return systemFields; }
    }

}
