package com.gamelist.service.impl;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gamelist.model.ExportRule;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.util.GameFieldAccessor;
import com.gamelist.util.VariableReplacer;

/**
 * Text 格式数据文件生成器（Pegasus metadata.pegasus.txt）。
 * 继承 AbstractDataFileGenerator 复用公共逻辑，仅保留 Text 特有处理。
 */
public class TextDataFileGenerator extends AbstractDataFileGenerator {

    @Override
    public void generateDataFile(List<Game> games, Path dataFilePath, ExportRule rule, Platform platform, Map<String, String> variables) throws Exception {
        StringBuilder content = new StringBuilder();
        
        generateHeader(content, rule, platform, variables);
        
        for (Game game : games) {
            generateGameEntry(content, game, rule, platform, variables, dataFilePath.getParent());
        }
        
        generateFooter(content, rule, platform, variables);
        
        java.nio.file.Files.write(dataFilePath, content.toString().getBytes());
        logger.info("Generated text data file: {}", dataFilePath);
    }

    @Override
    public void generateHeader(StringBuilder content, ExportRule rule, Platform platform, Map<String, String> variables) {
        ExportRule.DataFileRule dataFileRule = rule.getRules().getDataFile();
        if (dataFileRule.getHeader() != null && dataFileRule.getHeader().getStructure() != null) {
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
        Map<String, ExportRule.TransformRule> fieldTransforms = dataFileRule.getFieldTransforms();

        // 处理普通字段（支持表达式引擎）
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String targetField = entry.getKey();
            String sourceField = entry.getValue();
            String value = getGameFieldValue(game, sourceField, dataFileRule.getPathFormat());

            // 应用字段转换规则
            if (fieldTransforms != null && fieldTransforms.containsKey(targetField)) {
                value = applyTransform(value, fieldTransforms.get(targetField));
            }

            if (value != null && !value.isEmpty()) {
                content.append(targetField).append(": ").append(value).append(getFieldSeparator(dataFileRule));
            }
        }
        
        // 处理媒体字段
        for (Map.Entry<String, ExportRule.MediaRule> mediaEntry : rule.getRules().getMedia().entrySet()) {
            ExportRule.MediaRule mediaRule = mediaEntry.getValue();
            String dataFileTag = mediaRule.getDataFileTag();
            if (dataFileTag != null && !dataFileTag.isEmpty()) {
                String relativePath = resolveMediaPath(game, mediaRule, mediaEntry, basePath, variables, dataFileRule.getPathFormat());
                if (relativePath != null) {
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

    /**
     * 处理表头/尾部模板行：替换平台字段和环境变量。
     */
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
}
