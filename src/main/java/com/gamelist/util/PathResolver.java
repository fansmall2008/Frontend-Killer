package com.gamelist.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一路径解析工具 — 替代 GameServiceImpl / XmlDataFileGenerator / TextDataFileGenerator
 * 中分散的路径处理逻辑。
 */
public final class PathResolver {

    private static final Logger logger = LoggerFactory.getLogger(PathResolver.class);

    private PathResolver() {}

    // ==================== 导入路径解析 ====================

    /**
     * 将数据文件中的路径转为绝对路径。
     * <ul>
     *   <li>绝对路径 → 直接返回</li>
     *   <li>以 "./" 或 ".\\" 开头 → 去掉前缀后拼接到 baseDir</li>
     *   <li>相对路径 → 拼接到 baseDir</li>
     * </ul>
     *
     * @param rawPath 数据文件中的原始路径
     * @param baseDir 数据文件所在目录（或 ROM 根目录）
     * @return 解析后的绝对路径字符串，rawPath 为空时返回 null
     */
    public static String resolveImportPath(String rawPath, File baseDir) {
        if (rawPath == null || rawPath.isEmpty()) return null;
        if (baseDir == null) return rawPath;

        String cleaned = rawPath.replace('\\', '/');
        // 去掉 ./ 前缀
        if (cleaned.startsWith("./")) {
            cleaned = cleaned.substring(2);
        }

        File pathFile = new File(cleaned);
        if (pathFile.isAbsolute()) {
            return pathFile.getAbsolutePath();
        }

        // 相对路径 → 拼接到 baseDir
        File resolved = new File(baseDir, cleaned);
        try {
            return resolved.getCanonicalPath();
        } catch (Exception e) {
            return resolved.getAbsolutePath();
        }
    }

    /**
     * 将媒体文件路径转为相对于 ROM 目录的相对路径（用于存储到数据库）。
     *
     * @param mediaAbsolutePath 媒体文件绝对路径
     * @param romRoot           ROM 根目录
     * @return 相对路径字符串（使用 / 分隔符），无法计算时返回原始路径
     */
    public static String toRelativePath(String mediaAbsolutePath, String romRoot) {
        if (mediaAbsolutePath == null || mediaAbsolutePath.isEmpty()) return mediaAbsolutePath;
        if (romRoot == null || romRoot.isEmpty()) return mediaAbsolutePath;

        try {
            Path basePath = Paths.get(romRoot).normalize();
            Path targetPath = Paths.get(mediaAbsolutePath).normalize();
            String relative = basePath.relativize(targetPath).toString().replace('\\', '/');
            return relative;
        } catch (Exception e) {
            logger.trace("无法计算相对路径: base={}, target={}", romRoot, mediaAbsolutePath);
            return mediaAbsolutePath;
        }
    }

    // ==================== 导出路径格式处理 ====================

    /**
     * 根据 pathFormat 处理导出路径格式。
     *
     * @param path       原始路径
     * @param pathFormat 路径格式："absoluteWithDot"（带 ./ 前缀）或其他（不带 ./ 前缀）
     * @return 处理后的路径
     */
    public static String formatExportPath(String path, String pathFormat) {
        if (path == null) return null;

        if ("absoluteWithDot".equals(pathFormat)) {
            // 确保带 ./ 前缀（Windows 盘符路径除外）
            if (!path.startsWith("./") && !path.startsWith(".\\")
                    && !path.matches("^[A-Za-z]:.*")
                    && !path.startsWith("/") && !path.startsWith("\\")) {
                return "./" + path;
            }
        } else {
            // 默认：去掉 ./ 前缀
            if (path.startsWith("./") || path.startsWith(".\\")) {
                return path.substring(2);
            }
        }
        return path;
    }

    // ==================== 相对路径计算 ====================

    /**
     * 计算从 basePath 到 targetPath 的相对路径。
     * 跨盘符等无法计算的情况会回退到字符串处理。
     */
    public static String relativize(Path basePath, Path targetPath) {
        try {
            return basePath.relativize(targetPath).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            // 跨盘符等情况，回退到字符串处理
            String baseStr = basePath.toString().replace('\\', '/');
            String targetStr = targetPath.toString().replace('\\', '/');

            if (!targetStr.startsWith("/")) {
                return targetStr;
            }

            // 找共同前缀
            int minLen = Math.min(baseStr.length(), targetStr.length());
            int commonLen = 0;
            while (commonLen < minLen && baseStr.charAt(commonLen) == targetStr.charAt(commonLen)) {
                commonLen++;
            }

            int lastSlash = baseStr.lastIndexOf('/', commonLen);
            if (lastSlash == -1) lastSlash = 0;

            String remaining = baseStr.substring(lastSlash);
            int upCount = (int) remaining.chars().filter(c -> c == '/').count();

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < upCount; i++) sb.append("../");
            sb.append(targetStr.substring(lastSlash));
            return sb.toString().replace('\\', '/');
        }
    }
}
