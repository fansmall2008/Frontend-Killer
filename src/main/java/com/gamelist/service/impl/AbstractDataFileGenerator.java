package com.gamelist.service.impl;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gamelist.model.ExportRule;
import com.gamelist.model.Game;
import com.gamelist.model.MediaType;
import com.gamelist.model.Platform;
import com.gamelist.service.DataFileGenerator;
import com.gamelist.util.GameFieldAccessor;
import com.gamelist.util.PathResolver;
import com.gamelist.util.TemplateExpressionEngine;
import com.gamelist.util.VariableReplacer;

/**
 * 数据文件生成器抽象基类。
 * 提取 XmlDataFileGenerator 和 TextDataFileGenerator 的公共逻辑：
 * - 媒体文件路径获取（通过 GameFieldAccessor + MediaType 反射）
 * - 相对路径计算
 * - 字段值获取（支持表达式引擎）
 * - 平台字段值获取
 * - 变量替换
 * - 字段转换（trim/upper/lower/replace/path）
 */
public abstract class AbstractDataFileGenerator implements DataFileGenerator {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // ==================== 字段值获取（支持表达式） ====================

    /**
     * 获取游戏字段值。支持多值匹配（逗号分隔）和表达式。
     * 如果 sourceField 是表达式（如 name + ".jpg"），使用表达式引擎计算。
     * 如果是普通字段名，通过 GameFieldAccessor 获取。
     */
    protected String getGameFieldValue(Game game, String sourceField, String pathFormat) {
        // 支持多值匹配，用逗号分隔（仅当不是表达式时）
        if (!TemplateExpressionEngine.isExpression(sourceField) && sourceField.contains(",")) {
            String[] fieldNames = sourceField.split(",");
            for (String name : fieldNames) {
                name = name.trim();
                String value = getSingleGameFieldValue(game, name, pathFormat);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
            return null;
        }

        // 表达式或单字段
        return getSingleGameFieldValue(game, sourceField, pathFormat);
    }

    /**
     * 获取单个游戏字段值。
     */
    protected String getSingleGameFieldValue(Game game, String fieldName, String pathFormat) {
        // 特殊处理 path 字段（需要 pathFormat 处理）
        if ("path".equals(fieldName)) {
            String path = game.getPath();
            return PathResolver.formatExportPath(path, pathFormat);
        }

        // 检查是否是表达式
        if (TemplateExpressionEngine.isExpression(fieldName)) {
            TemplateExpressionEngine.Context ctx = new TemplateExpressionEngine.Context(game, null, null);
            return TemplateExpressionEngine.evaluate(fieldName, ctx);
        }

        // 普通字段名
        return GameFieldAccessor.getValue(game, fieldName);
    }

    /**
     * 使用表达式引擎获取字段值（带完整上下文）。
     */
    protected String evaluateExpression(String expr, Game game, Platform platform, Map<String, String> variables) {
        if (expr == null || expr.isEmpty()) return null;

        // 如果是表达式，使用表达式引擎
        if (TemplateExpressionEngine.isExpression(expr)) {
            TemplateExpressionEngine.Context ctx = new TemplateExpressionEngine.Context(game, platform, variables);
            return TemplateExpressionEngine.evaluate(expr, ctx);
        }

        // 否则直接取字段值
        return GameFieldAccessor.getValue(game, expr);
    }

    // ==================== 媒体文件路径获取 ====================

    /**
     * 根据 nomcourt 值从 Game 对象获取媒体文件路径。
     * 优先通过 MediaType 枚举反射获取，回退到旧版兼容映射。
     */
    protected String getMediaFilePathFromGame(Game game, String sourceField) {
        // 1. 尝试通过 MediaType 枚举查找
        MediaType mt = MediaType.fromNomcourt(sourceField);
        if (mt == null) {
            mt = MediaType.fromNomcourtLenient(sourceField);
        }
        if (mt != null) {
            String value = GameFieldAccessor.getValue(game, mt.getNomcourt());
            if (value != null) return value;
        }

        // 2. 直接通过 GameFieldAccessor 尝试
        String value = GameFieldAccessor.getValue(game, sourceField);
        if (value != null) return value;

        // 3. 回退：非 SS 标准字段和遗留旧名称
        return getLegacyMediaField(game, sourceField);
    }

    /**
     * 旧版媒体字段兼容映射。
     */
    private String getLegacyMediaField(Game game, String sourceField) {
        switch (sourceField.toLowerCase()) {
            case "box2dfront", "boxfront": return game.getBoxFront();
            case "box2dback", "boxback": return game.getBoxBack();
            case "box3d": return game.getBox3D();
            case "screenshot": return game.getScreenshot();
            case "video": return game.getVideo();
            case "wheel": return game.getLogo();
            case "marquee": return game.getMarquee();
            case "fanart": return game.getFanart();
            case "image": return game.getImage();
            case "thumbnail": return game.getThumbnail();
            case "logo": return game.getLogo();
            case "background": return game.getBackground();
            case "manual", "manuel": return game.getManual();
            case "bezel": return game.getBezel();
            case "steamgrid": return game.getSteamgrid();
            default: return null;
        }
    }

    // ==================== 路径处理 ====================

    /**
     * 计算相对路径，处理跨盘符等异常情况。
     */
    protected String getRelativePath(Path basePath, Path targetPath) {
        try {
            return basePath.relativize(targetPath).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to relativize paths: {}", e.getMessage());

            String basePathStr = basePath.toString().replace('\\', '/');
            String targetPathStr = targetPath.toString().replace('\\', '/');

            if (!targetPathStr.startsWith("/")) {
                return targetPathStr;
            }

            // 找共同前缀
            int minLength = Math.min(basePathStr.length(), targetPathStr.length());
            int commonPrefixLength = 0;
            while (commonPrefixLength < minLength
                    && basePathStr.charAt(commonPrefixLength) == targetPathStr.charAt(commonPrefixLength)) {
                commonPrefixLength++;
            }

            int lastSlashIndex = basePathStr.lastIndexOf('/', commonPrefixLength);
            if (lastSlashIndex == -1) lastSlashIndex = 0;

            StringBuilder relativePath = new StringBuilder();
            String remainingBase = basePathStr.substring(lastSlashIndex);
            int slashCount = remainingBase.length() - remainingBase.replace("/", "").length();
            for (int i = 0; i < slashCount; i++) {
                relativePath.append("../");
            }
            relativePath.append(targetPathStr.substring(lastSlashIndex));

            return relativePath.toString().replace('\\', '/');
        }
    }

    /**
     * 根据 pathFormat 处理媒体文件路径格式。
     */
    protected String formatMediaPath(String relativePath, String pathFormat) {
        if (relativePath == null) return null;
        if ("absoluteWithDot".equals(pathFormat)) {
            if (!relativePath.startsWith("./") && !relativePath.startsWith(".\\")
                    && !relativePath.matches("^[A-Za-z]:.*") && !relativePath.startsWith("/") 
                    && !relativePath.startsWith("\\")) {
                return "./" + relativePath;
            }
        } else {
            if (relativePath.startsWith("./") || relativePath.startsWith(".\\")) {
                return relativePath.substring(2);
            }
        }
        return relativePath;
    }

    // ==================== 平台字段 ====================

    protected String getPlatformFieldValue(Platform platform, String fieldName) {
        if (platform == null || fieldName == null) return null;
        switch (fieldName) {
            case "system": return platform.getSystem();
            case "name": return platform.getName();
            case "launch": return platform.getLaunch();
            case "software": return platform.getSoftware();
            case "database": return platform.getDatabase();
            case "web": return platform.getWeb();
            case "folderPath": return platform.getFolderPath();
            default: return null;
        }
    }

    // ==================== 变量替换 ====================

    protected String replaceVariables(String template, Map<String, String> variables) {
        return VariableReplacer.replaceVariables(template, variables);
    }

    protected String replaceVariables(String template, Map<String, String> variables,
                                       Platform platform, Map<String, String> platformFields) {
        return VariableReplacer.replaceVariables(template, variables, platform, platformFields);
    }

    // ==================== 字段转换 ====================

    /**
     * 应用字段转换规则（trim/caseType/path/replace）。
     */
    protected String applyTransform(String value, ExportRule.TransformRule transform) {
        if (value == null || transform == null) return value;

        if (transform.isTrim()) {
            value = value.trim();
        }
        if ("upper".equals(transform.getCaseType())) {
            value = value.toUpperCase();
        } else if ("lower".equals(transform.getCaseType())) {
            value = value.toLowerCase();
        }
        if (transform.getPath() != null) {
            value = transformPath(value, transform.getPath());
        }
        if (transform.getReplace() != null) {
            value = value.replace(transform.getReplace().getFrom(),
                                  transform.getReplace().getTo() != null ? transform.getReplace().getTo() : "");
        }
        return value;
    }

    private String transformPath(String path, String mode) {
        if (path == null) return null;
        if ("yes".equals(mode)) {
            if (!path.startsWith("./") && !path.startsWith(".\\")
                    && !path.matches("^[A-Za-z]:.*") && !path.startsWith("/") && !path.startsWith("\\")) {
                return "./" + path;
            }
        } else if ("no".equals(mode)) {
            if (path.startsWith("./") || path.startsWith(".\\")) {
                return path.substring(2);
            }
        }
        return path;
    }

    // ==================== 媒体路径处理辅助 ====================

    /**
     * 处理媒体文件路径并返回最终结果（相对路径计算 + 格式处理）。
     * 供子类在 generateGameEntry 中调用。
     */
    protected String resolveMediaPath(Game game, ExportRule.MediaRule mediaRule,
                                       Map.Entry<String, ExportRule.MediaRule> mediaEntry,
                                       Path basePath, Map<String, String> variables,
                                       String pathFormat) {
        // v2: source 为空时使用 map key（即 nomcourt 值）
        String sourceField = mediaRule.getSource();
        if (sourceField == null || sourceField.isEmpty()) {
            sourceField = mediaEntry.getKey();
        }

        String mediaFilePath = getMediaFilePathFromGame(game, sourceField);
        if (mediaFilePath == null || mediaFilePath.isEmpty()) return null;

        // 构建目标路径
        Map<String, String> mediaVariables = new HashMap<>(variables);
        String filename = GameFieldAccessor.getValue(game, "filename");
        if (filename == null) filename = game.getName();
        mediaVariables.put("gameName", filename);

        String targetPathStr = replaceVariables(mediaRule.getTarget(), mediaVariables);
        Path targetFilePath = java.nio.file.Paths.get(targetPathStr);

        String relativePath = getRelativePath(basePath, targetFilePath);
        return formatMediaPath(relativePath, pathFormat);
    }
}
