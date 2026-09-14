package com.gamelist.service.impl;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gamelist.model.ExportRule;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.util.GameFieldAccessor;
import com.gamelist.util.TemplateExpressionEngine;
import com.gamelist.util.VariableReplacer;

/**
 * Lakka .lpl 播放列表文件生成器。
 * 继承 AbstractDataFileGenerator 复用公共逻辑，支持表达式引擎。
 */
public class LplDataFileGenerator extends AbstractDataFileGenerator {

    @Override
    public void generateDataFile(List<Game> games, Path dataFilePath, ExportRule rule, Platform platform, Map<String, String> variables) throws Exception {
        StringBuilder content = new StringBuilder();
        
        for (Game game : games) {
            generateGameEntry(content, game, rule, platform, variables, dataFilePath.getParent());
        }
        
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
        String platformName = platform != null ? platform.getName() : "";
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
        
        // 处理每一行格式（支持表达式引擎）
        List<String> lineFormat = lplRule.getLineFormat();
        for (int i = 0; i < lineFormat.size(); i++) {
            String line = lineFormat.get(i);
            String processedLine = processLplLine(line, game, platform, gameVariables);
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
     * 处理 LPL 行格式。
     * 如果行包含表达式语法（函数调用、拼接等），使用表达式引擎。
     * 否则使用 VariableReplacer 进行简单变量替换。
     */
    private String processLplLine(String line, Game game, Platform platform, Map<String, String> variables) {
        // 检查是否包含表达式语法
        if (containsExpression(line)) {
            TemplateExpressionEngine.Context ctx = new TemplateExpressionEngine.Context(game, platform, variables);
            return TemplateExpressionEngine.evaluate(line, ctx);
        }
        // 简单变量替换
        return VariableReplacer.replaceVariables(line, variables, platform, null);
    }

    /**
     * 检查行模板中是否包含表达式语法（在花括号内的表达式）。
     */
    private boolean containsExpression(String line) {
        if (line == null) return false;
        // 检查花括号内是否有表达式语法
        int start = -1;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '{') {
                start = i;
            } else if (c == '}' && start >= 0) {
                String inner = line.substring(start + 1, i);
                if (TemplateExpressionEngine.isExpression(inner)) {
                    return true;
                }
                start = -1;
            }
        }
        return false;
    }

    /**
     * 从游戏对象获取文件名（包含扩展名）。
     */
    private String getGameFileName(Game game) {
        String path = game.getPath();
        if (path != null) {
            int lastSlashIndex = path.lastIndexOf('/');
            int lastBackslashIndex = path.lastIndexOf('\\');
            int lastSeparatorIndex = Math.max(lastSlashIndex, lastBackslashIndex);
            String fileName = lastSeparatorIndex >= 0 ? path.substring(lastSeparatorIndex + 1) : path;
            return fileName;
        }
        if (game.getName() != null) {
            return game.getName() + ".zip";
        }
        return "unknown.zip";
    }
}
