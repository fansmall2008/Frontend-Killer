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

/**
 * Lakka .lpl 文件生成器
 * 用于生成 Lakka RetroArch 播放列表文件
 */
public class LplDataFileGenerator implements DataFileGenerator {
    private static final Logger logger = LoggerFactory.getLogger(LplDataFileGenerator.class);

    @Override
    public void generateDataFile(List<Game> games, Path dataFilePath, ExportRule rule, Platform platform, Map<String, String> variables) throws Exception {
        StringBuilder content = new StringBuilder();
        
        // 生成游戏条目
        for (Game game : games) {
            generateGameEntry(content, game, rule, platform, variables, dataFilePath.getParent());
        }
        
        // 写入文件
        java.nio.file.Files.write(dataFilePath, content.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        logger.info("Generated LPL data file: {}", dataFilePath);
    }

    @Override
    public void generateHeader(StringBuilder content, ExportRule rule, Platform platform, Map<String, String> variables) {
        // LPL 文件不需要表头
    }

    @Override
    public void generateGameEntry(StringBuilder content, Game game, ExportRule rule, Platform platform, Map<String, String> variables, Path basePath) {
        ExportRule.LplExportRule lplRule = rule.getRules().getLplExport();
        if (lplRule == null || lplRule.getLineFormat() == null) {
            logger.warn("LPL export rule or line format not configured");
            return;
        }
        
        // 获取核心映射
        Map<String, ExportRule.CoreMapping> coreMappings = rule.getRules().getCoreMappings();
        
        // 获取平台映射
        Map<String, String> platformMappings = rule.getRules().getPlatformMappings();
        
        // 获取平台名
        String platformName = platform.getName();
        String mappedPlatformName = platformName;
        if (platformMappings != null && platformMappings.containsKey(platformName)) {
            mappedPlatformName = platformMappings.get(platformName);
        }
        
        // 获取核心信息
        ExportRule.CoreMapping coreMapping = null;
        if (coreMappings != null) {
            if (coreMappings.containsKey(platformName)) {
                coreMapping = coreMappings.get(platformName);
            } else if (coreMappings.containsKey("default")) {
                coreMapping = coreMappings.get("default");
            }
        }
        
        String corePath = coreMapping != null ? coreMapping.getPath() : "DETECT";
        String coreName = coreMapping != null ? coreMapping.getName() : "DETECT";
        
        // 构建游戏特定变量
        Map<String, String> gameVariables = new HashMap<>(variables);
        
        // 获取游戏文件名
        String gameFileName = getGameFileName(game);
        gameVariables.put("gameFileName", gameFileName);
        gameVariables.put("gameName", game.getName() != null ? game.getName() : gameFileName);
        gameVariables.put("platformName", mappedPlatformName);
        gameVariables.put("corePath", corePath);
        gameVariables.put("coreName", coreName);
        
        // 处理每一行格式
        List<String> lineFormat = lplRule.getLineFormat();
        for (int i = 0; i < lineFormat.size(); i++) {
            String line = lineFormat.get(i);
            String processedLine = VariableReplacer.replaceVariables(line, gameVariables, platform, null);
            content.append(processedLine);
            
            // 最后一行不加换行符（避免文件末尾有空行）
            if (i < lineFormat.size() - 1) {
                content.append("\n");
            }
        }
        
        // 添加游戏条目分隔符（空行）
        content.append("\n");
    }

    @Override
    public void generateFooter(StringBuilder content, ExportRule rule, Platform platform, Map<String, String> variables) {
        // LPL 文件不需要尾部
    }

    /**
     * 从游戏对象获取文件名（包含扩展名）
     */
    private String getGameFileName(Game game) {
        String path = game.getPath();
        if (path != null) {
            // 提取文件名
            int lastSlashIndex = path.lastIndexOf('/');
            int lastBackslashIndex = path.lastIndexOf('\\');
            int lastSeparatorIndex = Math.max(lastSlashIndex, lastBackslashIndex);
            String fileName = lastSeparatorIndex >= 0 ? path.substring(lastSeparatorIndex + 1) : path;
            return fileName;
        }
        // 如果没有路径，使用游戏名称
        if (game.getName() != null) {
            return game.getName() + ".zip";
        }
        return "unknown.zip";
    }
}