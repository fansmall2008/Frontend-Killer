package com.gamelist.service.impl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gamelist.model.ExportRule;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.service.DataFileGenerator;
import com.gamelist.util.VariableReplacer;

public class TextDataFileGenerator implements DataFileGenerator {
    private static final Logger logger = LoggerFactory.getLogger(TextDataFileGenerator.class);

    @Override
    public void generateDataFile(List<Game> games, Path dataFilePath, ExportRule rule, Platform platform, Map<String, String> variables) throws Exception {
        StringBuilder content = new StringBuilder();
        
        // 生成表头
        generateHeader(content, rule, platform, variables);
        
        // 生成游戏条目
        for (Game game : games) {
            generateGameEntry(content, game, rule, platform, variables, dataFilePath.getParent());
        }
        
        // 生成尾部
        generateFooter(content, rule, platform, variables);
        
        // 写入文件
        java.nio.file.Files.write(dataFilePath, content.toString().getBytes());
        logger.info("Generated text data file: {}", dataFilePath);
    }

    @Override
    public void generateHeader(StringBuilder content, ExportRule rule, Platform platform, Map<String, String> variables) {
        ExportRule.DataFileRule dataFileRule = rule.getRules().getDataFile();
        if (dataFileRule.getHeader() != null && dataFileRule.getHeader().getStructure() != null) {
            // 处理表头结构
            for (String line : dataFileRule.getHeader().getStructure()) {
                String processedLine = processLine(line, rule, platform, variables);
                content.append(processedLine).append("\n");
            }
        }
    }

    @Override
    public void generateGameEntry(StringBuilder content, Game game, ExportRule rule, Platform platform, Map<String, String> variables, Path basePath) {
        ExportRule.DataFileRule dataFileRule = rule.getRules().getDataFile();
        Map<String, String> fields = dataFileRule.getFields();
        
        // 处理字段
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String targetField = entry.getKey();
            String sourceField = entry.getValue();
            String value = getGameFieldValue(game, sourceField, dataFileRule.getPathFormat());
            
            if (value != null && !value.isEmpty()) {
                content.append(targetField).append(": ").append(value).append(getFieldSeparator(dataFileRule));
            }
        }
        
        // 处理媒体文件
        for (Map.Entry<String, ExportRule.MediaRule> mediaEntry : rule.getRules().getMedia().entrySet()) {
            ExportRule.MediaRule mediaRule = mediaEntry.getValue();
            String dataFileTag = mediaRule.getDataFileTag();
            if (dataFileTag != null && !dataFileTag.isEmpty()) {
                String mediaFilePath = getMediaFilePathFromGame(game, mediaRule.getSource());
                if (mediaFilePath != null && !mediaFilePath.isEmpty()) {
                    // 构建媒体文件路径
                    Map<String, String> mediaVariables = new HashMap<>(variables);
                    mediaVariables.put("gameName", game.getName());
                    String targetPathStr = replaceVariables(mediaRule.getTarget(), mediaVariables);
                    Path targetFilePath = Paths.get(targetPathStr);
                    
                    // 计算相对路径
                    String relativePath = getRelativePath(basePath, targetFilePath);
                    // 根据 pathFormat 处理媒体文件路径格式
                    String pathFormat = dataFileRule.getPathFormat();
                    if ("absoluteWithDot".equals(pathFormat)) {
                        // 确保路径带 ./ 前缀
                        if (!relativePath.startsWith("./") && !relativePath.startsWith(".\\") && !relativePath.matches("^[A-Za-z]:.*") && !relativePath.startsWith("/") && !relativePath.startsWith("\\")) {
                            relativePath = "./" + relativePath;
                        }
                    } else {
                        // 默认：确保路径不带 ./ 前缀
                        if (relativePath.startsWith("./") || relativePath.startsWith(".\\")) {
                            relativePath = relativePath.substring(2);
                        }
                    }
                    content.append(dataFileTag).append(": ").append(relativePath).append(getFieldSeparator(dataFileRule));
                }
            }
        }
        
        // 添加条目分隔符
        content.append(getEntrySeparator(dataFileRule));
    }

    @Override
    public void generateFooter(StringBuilder content, ExportRule rule, Platform platform, Map<String, String> variables) {
        ExportRule.DataFileRule dataFileRule = rule.getRules().getDataFile();
        if (dataFileRule.getFooter() != null) {
            for (String line : dataFileRule.getFooter()) {
                String processedLine = processLine(line, rule, platform, variables);
                content.append(processedLine).append("\n");
            }
        }
    }

    private String processLine(String line, ExportRule rule, Platform platform, Map<String, String> variables) {
        Map<String, String> platformFields = null;
        if (rule.getRules().getDataFile().getHeader() != null) {
            platformFields = rule.getRules().getDataFile().getHeader().getFields();
        }
        return VariableReplacer.replaceVariables(line, variables, platform, platformFields);
    }

    private String getFieldSeparator(ExportRule.DataFileRule dataFileRule) {
        return dataFileRule.getFieldSeparator() != null ? dataFileRule.getFieldSeparator() : "\n";
    }

    private String getEntrySeparator(ExportRule.DataFileRule dataFileRule) {
        return dataFileRule.getEntrySeparator() != null ? dataFileRule.getEntrySeparator() : "\n\n";
    }

    private String getPlatformFieldValue(Platform platform, String fieldName) {
        switch (fieldName) {
            case "system":
                return platform.getSystem();
            case "name":
                return platform.getName();
            case "launch":
                return platform.getLaunch();
            case "software":
                return platform.getSoftware();
            case "database":
                return platform.getDatabase();
            case "web":
                return platform.getWeb();
            default:
                return null;
        }
    }

    private String getGameFieldValue(Game game, String fieldName, String pathFormat) {
        // 支持多值匹配，用逗号分隔
        String[] fieldNames = fieldName.split(",");
        for (String name : fieldNames) {
            name = name.trim();
            String value = getSingleGameFieldValue(game, name, pathFormat);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }
    
    private String getSingleGameFieldValue(Game game, String fieldName, String pathFormat) {
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
            case "path":
                String path = game.getPath();
                // 根据 pathFormat 处理路径格式
                if (path != null) {
                    if ("absoluteWithDot".equals(pathFormat)) {
                        // 确保路径带 ./ 前缀
                        if (!path.startsWith("./") && !path.startsWith(".\\") && !path.matches("^[A-Za-z]:.*") && !path.startsWith("/") && !path.startsWith("\\")) {
                            path = "./" + path;
                        }
                    } else {
                        // 默认：确保路径不带 ./ 前缀
                        if (path.startsWith("./") || path.startsWith(".\\")) {
                            path = path.substring(2);
                        }
                    }
                }
                return path;
            case "filename":
                // 从path中提取文件名，去掉扩展名和前面的路径
                String gamePath = game.getPath();
                if (gamePath != null) {
                    // 提取文件名
                    int lastSlashIndex = gamePath.lastIndexOf('/');
                    int lastBackslashIndex = gamePath.lastIndexOf('\\');
                    int lastSeparatorIndex = Math.max(lastSlashIndex, lastBackslashIndex);
                    String fileName = lastSeparatorIndex >= 0 ? gamePath.substring(lastSeparatorIndex + 1) : gamePath;
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

    private String replaceVariables(String template, Map<String, String> variables) {
        return VariableReplacer.replaceVariables(template, variables);
    }
}
