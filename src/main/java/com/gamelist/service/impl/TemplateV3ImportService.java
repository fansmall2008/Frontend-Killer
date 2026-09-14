package com.gamelist.service.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gamelist.model.Game;
import com.gamelist.model.ParsedDataFile;
import com.gamelist.model.TemplateV3;
import com.gamelist.util.GameFieldAccessor;
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

    public boolean isEnableMediaDiscovery() {
        return enableMediaDiscovery;
    }

    public void setEnableMediaDiscovery(boolean enableMediaDiscovery) {
        this.enableMediaDiscovery = enableMediaDiscovery;
    }

    /**
     * 使用 v3 模板导入数据文件。
     *
     * @param dataFile 数据文件（gamelist.xml、metadata.pegasus.txt 等）
     * @param template v3 导入模板
     * @return 解析并映射后的 Game 列表
     */
    public List<Game> importFile(File dataFile, TemplateV3 template) throws Exception {
        if (!template.getTemplateInfo().isImport()) {
            throw new IllegalArgumentException("模板方向不是 import: " + template.getTemplateInfo().getDirection());
        }

        // 第一步：解析数据文件
        ParsedDataFile parsed = parseFile(dataFile, template);
        logger.info("解析完成: 系统字段 {} 个, 游戏 {} 条",
                parsed.getSystemFields().size(), parsed.getGameCount());

        // 第二步：映射到 Game 对象（含 filename 注入、多文件展开、媒体检查）
        File baseDir = dataFile.getParentFile();
        List<Game> games = mapToGames(parsed, template, baseDir);
        logger.info("映射完成: {} 条游戏", games.size());

        return games;
    }

    /**
     * 根据模板选择解析器并解析数据文件。
     */
    private ParsedDataFile parseFile(File dataFile, TemplateV3 template) throws Exception {
        String fileType = template.getTemplateInfo().getDataFileType();

        if ("text".equalsIgnoreCase(fileType)) {
            return GenericTextParser.parse(dataFile, template);
        } else if ("data".equalsIgnoreCase(fileType)) {
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

        for (Map<String, String> rawFields : parsed.getGames()) {
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

            // —— 多文件游戏展开（从模板 multiFile 配置读取） ——
            List<Game> expandedGames = expandMultiFile(game, rawFields, template);

            // —— 处理每个展开的 Game：媒体存在性检查 + 规则回退 ——
            for (Game g : expandedGames) {
                processMediaPaths(g, mediaValues, baseDir, mediaDiscovery);
                games.add(g);
            }
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
            logger.info("多文件游戏展开: {} → {} 条记录 (field={})", game.getName(), result.size(), fieldName);
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
                                    File baseDir, TemplateV3.MediaDiscovery mediaDiscovery) {
        Set<String> processedTypes = new HashSet<>();

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
            if (!found && enableMediaDiscovery && mediaDiscovery != null && mediaDiscovery.isEnabled()) {
                applyMediaDiscovery(game, nomcourt, mediaDiscovery, baseDir);
            } else if (!found) {
                logger.debug("媒体文件不存在且跳过 mediaDiscovery: {} → {}", nomcourt, rawPath);
            }
        }

        // —— 阶段二：仅在 enableMediaDiscovery=true 时，对未在 data file 中声明的类型尝试 mediaDiscovery ——
        if (enableMediaDiscovery && mediaDiscovery != null && mediaDiscovery.isEnabled()
                && mediaDiscovery.getRules() != null) {
            for (String nomcourt : mediaDiscovery.getRules().keySet()) {
                if (processedTypes.contains(nomcourt)) continue;

                // 跳过已设置的字段
                String existing = GameFieldAccessor.getValue(game, nomcourt);
                if (existing != null && !existing.isEmpty()) continue;

                applyMediaDiscovery(game, nomcourt, mediaDiscovery, baseDir);
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
                                         TemplateV3.MediaDiscovery mediaDiscovery, File baseDir) {
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
            String folderName = resolvePattern(pattern, filename, name);
            if (folderName == null || folderName.isEmpty()) continue;

            if (tryDiscoveryRules(game, nomcourt, rules, folderName, name, extensions, mediaBaseDir, baseDir)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 将模式字符串解析为具体值。
     * <p>
     * 支持 {filename} 和 {name} 占位符，如果模式就是其中一个则直接返回对应值。
     *
     * @param pattern  模式字符串，如 "{filename}" 或 "{name}"
     * @param filename ROM 文件名去扩展名
     * @param name     游戏显示名
     * @return 解析后的具体值
     */
    private String resolvePattern(String pattern, String filename, String name) {
        if (pattern == null || pattern.isEmpty()) return null;
        return pattern
                .replace("{filename}", filename != null ? filename : "")
                .replace("{name}", name != null ? name : "");
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
     * 用指定的 folderName 尝试所有 discovery 规则。
     *
     * @param folderName 用于替换 {filename} 和 {name} 的文件夹名
     * @return true 如果找到并设置了该媒体字段
     */
    private boolean tryDiscoveryRules(Game game, String nomcourt, List<String> rules,
                                       String folderName, String name,
                                       List<String> extensions,
                                       File mediaBaseDir, File baseDir) {
        for (String rule : rules) {
            String mediaPath = rule
                    .replace("{filename}", folderName)
                    .replace("{name}", name != null ? name : folderName);

            if (mediaPath.contains("{ext}")) {
                String basePath = mediaPath.substring(0, mediaPath.indexOf("{ext}"));
                for (String ext : extensions) {
                    File mediaFile = findFileCaseInsensitive(mediaBaseDir, basePath + ext);
                    if (mediaFile != null) {
                        String relativePath = getRelativeMediaPath(mediaFile, baseDir);
                        GameFieldAccessor.setValue(game, nomcourt, relativePath);
                        logger.info("mediaDiscovery 匹配: {} → {} (folder={})", nomcourt, relativePath, folderName);
                        return true;
                    }
                }
            } else {
                File mediaFile = findFileCaseInsensitive(mediaBaseDir, mediaPath);
                if (mediaFile != null) {
                    String relativePath = getRelativeMediaPath(mediaFile, baseDir);
                    GameFieldAccessor.setValue(game, nomcourt, relativePath);
                    logger.info("mediaDiscovery 匹配: {} → {} (folder={})", nomcourt, relativePath, folderName);
                    return true;
                }
            }
        }
        return false;
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
            // 大小写不敏感查找
            File[] children = current.listFiles();
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

}
