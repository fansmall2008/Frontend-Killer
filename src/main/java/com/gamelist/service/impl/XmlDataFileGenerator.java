package com.gamelist.service.impl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gamelist.model.ExportRule;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.util.GameFieldAccessor;
import com.gamelist.util.VariableReplacer;

/**
 * XML 格式数据文件生成器（gamelist.xml）。
 * 继承 AbstractDataFileGenerator 复用公共逻辑，仅保留 XML 特有处理。
 */
public class XmlDataFileGenerator extends AbstractDataFileGenerator {

    @Override
    public void generateDataFile(List<Game> games, Path dataFilePath, ExportRule rule, Platform platform, Map<String, String> variables) throws Exception {
        StringBuilder content = new StringBuilder();
        
        generateHeader(content, rule, platform, variables);
        
        for (Game game : games) {
            generateGameEntry(content, game, rule, platform, variables, dataFilePath.getParent());
        }
        
        generateFooter(content, rule, platform, variables);
        
        java.nio.file.Files.write(dataFilePath, content.toString().getBytes());
        logger.info("Generated XML data file: {}", dataFilePath);
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
        content.append("\n  <game>");

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
                content.append("\n    <").append(targetField).append(">").append(escapeXml(value)).append("</").append(targetField).append(">");
            }
        }
        
        // 处理媒体字段
        for (Map.Entry<String, ExportRule.MediaRule> mediaEntry : rule.getRules().getMedia().entrySet()) {
            ExportRule.MediaRule mediaRule = mediaEntry.getValue();
            String dataFileTag = mediaRule.getDataFileTag();
            if (dataFileTag != null && !dataFileTag.isEmpty()) {
                String relativePath = resolveMediaPath(game, mediaRule, mediaEntry, basePath, variables, dataFileRule.getPathFormat());
                if (relativePath != null) {
                    content.append("\n    <").append(dataFileTag).append(">").append(escapeXml(relativePath)).append("</").append(dataFileTag).append(">");
                }
            }
        }
        
        content.append("\n  </game>\n");
    }

    @Override
    public void generateFooter(StringBuilder content, ExportRule rule, Platform platform, Map<String, String> variables) {
        ExportRule.DataFileRule dataFileRule = rule.getRules().getDataFile();
        if (dataFileRule.getFooter() != null) {
            for (String line : dataFileRule.getFooter()) {
                String processedLine = processLine(line, rule, platform, variables);
                content.append("\n").append(processedLine);
            }
        }
    }

    /**
     * 处理表头/尾部模板行：替换平台字段和环境变量（XML 转义）。
     */
    private String processLine(String line, ExportRule rule, Platform platform, Map<String, String> variables) {
        Map<String, String> platformFields = null;
        if (rule.getRules().getDataFile().getHeader() != null) {
            platformFields = rule.getRules().getDataFile().getHeader().getFields();
        }
        
        String result = line;
        if (platform != null && platformFields != null) {
            for (Map.Entry<String, String> entry : platformFields.entrySet()) {
                String variable = entry.getKey();
                String fieldName = entry.getValue();
                String value = getPlatformFieldValue(platform, fieldName);
                if (value != null) {
                    result = result.replace("{" + variable + "}", escapeXml(value));
                }
            }
        }
        
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", escapeXml(entry.getValue()));
            }
        }
        
        return result;
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
