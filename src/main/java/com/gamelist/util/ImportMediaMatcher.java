package com.gamelist.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一媒体文件匹配器 — 替代 GameServiceImpl 中分散的媒体文件查找逻辑。
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>默认策略：基于 nomcourt 目录结构自动匹配（无需模板）</li>
 *   <li>模板增强：使用模板的 mediaMappings 规则匹配</li>
 * </ul>
 */
public final class ImportMediaMatcher {

    private static final Logger logger = LoggerFactory.getLogger(ImportMediaMatcher.class);

    /** 默认支持的图片扩展名 */
    private static final String[] IMAGE_EXTENSIONS = {"png", "jpg", "jpeg", "gif", "webp", "bmp"};
    /** 默认支持的视频扩展名 */
    private static final String[] VIDEO_EXTENSIONS = {"mp4", "mkv", "avi", "wmv", "webm", "flv"};

    private ImportMediaMatcher() {}

    // ==================== 默认策略匹配 ====================

    /**
     * 默认媒体匹配策略 — 在 media 目录中按 nomcourt 标准目录结构查找。
     * <p>
     * 搜索路径模式（按优先级）：
     * <ol>
     *   <li>{baseDir}/media/{filename}/{nomcourt}.{ext}</li>
     *   <li>{baseDir}/media/{nomcourt}/{filename}.{ext}</li>
     *   <li>{gameDir}/{nomcourt}.{ext}</li>
     * </ol>
     *
     * @param baseDir   ROM 所在目录（数据文件目录）
     * @param gameDir   当前游戏 ROM 所在目录
     * @param filename  游戏文件名（不含扩展名）
     * @param nomcourt  媒体类型的 nomcourt 值（如 "box-2D"、"ss"、"wheel"）
     * @return 找到的媒体文件相对路径（相对于 baseDir），未找到返回 null
     */
    public static String matchDefault(File baseDir, File gameDir, String filename, String nomcourt) {
        if (baseDir == null || filename == null || nomcourt == null) return null;

        String[] extensions = getAllExtensions();

        // 模式 1: media/{filename}/{nomcourt}.{ext}
        File mediaGameDir = new File(baseDir, "media/" + filename);
        if (mediaGameDir.isDirectory()) {
            String found = findFileInDir(mediaGameDir, nomcourt, extensions);
            if (found != null) return toRelative(found, baseDir);
        }

        // 模式 2: media/{nomcourt}/{filename}.{ext}
        File mediaTypeDir = new File(baseDir, "media/" + nomcourt);
        if (mediaTypeDir.isDirectory()) {
            String found = findFileInDir(mediaTypeDir, filename, extensions);
            if (found != null) return toRelative(found, baseDir);
        }

        // 模式 3: {gameDir}/{nomcourt}.{ext}
        if (gameDir != null && gameDir.isDirectory()) {
            String found = findFileInDir(gameDir, nomcourt, extensions);
            if (found != null) return toRelative(found, baseDir);
        }

        return null;
    }

    // ==================== 模板规则匹配 ====================

    /**
     * 使用模板媒体规则匹配媒体文件。
     *
     * @param baseDir     ROM 所在目录
     * @param gameDir     游戏 ROM 所在目录
     * @param filename    游戏文件名（不含扩展名）
     * @param rules       模板中的媒体路径规则列表（如 ["media/{filename}/boxFront.{ext}", ...]）
     * @param extensions  扩展名映射（如 {"image": ["png","jpg"], "video": ["mp4"]}），为 null 时使用默认
     * @return 找到的媒体文件相对路径（相对于 baseDir），未找到返回 null
     */
    public static String matchWithRules(File baseDir, File gameDir, String filename,
                                         List<String> rules, Map<String, List<String>> extensions) {
        if (baseDir == null || filename == null || rules == null) return null;

        Map<String, List<String>> ext = extensions != null ? extensions : getDefaultExtensions();

        for (String rulePattern : rules) {
            // 替换 {filename}
            String resolved = rulePattern.replace("{filename}", filename);
            // 替换 {filepath}（游戏文件完整相对路径）
            if (gameDir != null) {
                String filepath = toRelative(gameDir.getAbsolutePath(), baseDir);
                resolved = resolved.replace("{filepath}", filepath != null ? filepath : "");
            }

            // 尝试每种扩展名
            for (Map.Entry<String, List<String>> extEntry : ext.entrySet()) {
                for (String fileExt : extEntry.getValue()) {
                    String pathWithExt = resolved.replace("{ext}", fileExt);
                    File candidate = new File(baseDir, pathWithExt);
                    if (candidate.exists() && candidate.isFile()) {
                        return toRelative(candidate.getAbsolutePath(), baseDir);
                    }
                }
            }
        }

        return null;
    }

    // ==================== 批量匹配 ====================

    /**
     * 批量匹配多种媒体类型。
     *
     * @param baseDir       ROM 所在目录
     * @param gameDir       游戏 ROM 所在目录
     * @param filename      游戏文件名（不含扩展名）
     * @param nomcourtList  要匹配的 nomcourt 值列表
     * @return Map: nomcourt → 找到的相对路径
     */
    public static Map<String, String> matchMultiple(File baseDir, File gameDir, String filename,
                                                     List<String> nomcourtList) {
        Map<String, String> results = new HashMap<>();
        for (String nomcourt : nomcourtList) {
            String path = matchDefault(baseDir, gameDir, filename, nomcourt);
            if (path != null) {
                results.put(nomcourt, path);
            }
        }
        return results;
    }

    // ==================== 内部方法 ====================

    /**
     * 在目录中查找匹配指定名称和任一扩展名的文件。
     */
    private static String findFileInDir(File dir, String nameWithoutExt, String[] extensions) {
        for (String ext : extensions) {
            File candidate = new File(dir, nameWithoutExt + "." + ext);
            if (candidate.exists() && candidate.isFile()) {
                return candidate.getAbsolutePath();
            }
        }
        // 也尝试不区分大小写
        try {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String fname = f.getName();
                    int dotIdx = fname.lastIndexOf('.');
                    if (dotIdx > 0) {
                        String baseName = fname.substring(0, dotIdx);
                        String fileExt = fname.substring(dotIdx + 1);
                        if (baseName.equalsIgnoreCase(nameWithoutExt)) {
                            for (String ext : extensions) {
                                if (fileExt.equalsIgnoreCase(ext)) {
                                    return f.getAbsolutePath();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.trace("不区分大小写匹配失败: dir={}, name={}", dir, nameWithoutExt);
        }
        return null;
    }

    private static String[] getAllExtensions() {
        List<String> all = new ArrayList<>();
        for (String ext : IMAGE_EXTENSIONS) all.add(ext);
        for (String ext : VIDEO_EXTENSIONS) all.add(ext);
        return all.toArray(new String[0]);
    }

    private static Map<String, List<String>> getDefaultExtensions() {
        Map<String, List<String>> map = new HashMap<>();
        map.put("image", List.of(IMAGE_EXTENSIONS));
        map.put("video", List.of(VIDEO_EXTENSIONS));
        return map;
    }

    private static String toRelative(String absolutePath, File baseDir) {
        try {
            Path base = baseDir.toPath().normalize();
            Path target = new File(absolutePath).toPath().normalize();
            return base.relativize(target).toString().replace('\\', '/');
        } catch (Exception e) {
            return absolutePath.replace('\\', '/');
        }
    }

    private static String toRelative(String absolutePath, String baseDirStr) {
        try {
            Path base = new File(baseDirStr).toPath().normalize();
            Path target = new File(absolutePath).toPath().normalize();
            return base.relativize(target).toString().replace('\\', '/');
        } catch (Exception e) {
            return absolutePath.replace('\\', '/');
        }
    }
}
