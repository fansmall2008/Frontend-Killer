package com.gamelist.service.impl;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.model.TemplateV3;
import com.gamelist.util.GameFieldAccessor;
import com.gamelist.util.PathResolver;
import com.gamelist.util.TemplateExpressionEngine;

/**
 * v3 模板导出服务。
 * <p>
 * 工作流程：
 * <ol>
 *   <li>加载 v3 导出模板</li>
 *   <li>生成头部（system.header 模板行，替换平台变量）</li>
 *   <li>遍历游戏列表，按模板映射生成每个游戏条目</li>
 *   <li>生成尾部（system.footer 模板行）</li>
 *   <li>写入输出文件</li>
 * </ol>
 * <p>
 * 支持两种输出格式：
 * <ul>
 *   <li>text — 纯文本 key-value 格式（如 Pegasus metadata.txt）</li>
 *   <li>data/xml — XML 标签格式（如 EmulationStation gamelist.xml）</li>
 * </ul>
 */
@Service
public class TemplateV3ExportService {

    private static final Logger logger = LoggerFactory.getLogger(TemplateV3ExportService.class);

    /**
     * 使用 v3 模板生成数据文件。
     * <p>
     * 根据 output.dataFile 确定输出目录和文件名，
     * 根据 output.media.rules 计算媒体路径覆盖。
     *
     * @param games       游戏列表
     * @param template    v3 导出模板
     * @param platform    平台信息
     * @param variables   额外变量（如 outputPath 等）
     */
    public void exportToFile(List<Game> games, TemplateV3 template, Platform platform,
                             Map<String, String> variables) throws Exception {
        if (!template.getTemplateInfo().isExport()) {
            throw new IllegalArgumentException("模板方向不是 export: " + template.getTemplateInfo().getDirection());
        }

        // 从 output.dataFile 确定输出文件路径
        TemplateV3.OutputConfig output = template.getOutput();
        if (output == null || output.getDataFile() == null) {
            throw new IllegalArgumentException("v3 导出模板缺少 output.dataFile 配置");
        }

        Map<String, String> vars = buildVariables(platform, variables);
        String dataFileDir = resolveTemplateString(output.getDataFile().getDirectory(), platform, vars);
        String dataFileName = output.getDataFile().getFilename();
        if (dataFileName == null) {
            dataFileName = template.getTemplateInfo().getDataFile();
        }

        File outputFile = new File(dataFileDir, dataFileName);
        String content = generateContent(games, template, platform, variables);

        // 确保输出目录存在
        Path parentDir = outputFile.toPath().getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        Files.writeString(outputFile.toPath(), content);
        logger.info("v3 导出完成: {} ({} 条游戏)", outputFile.getAbsolutePath(), games.size());
    }

    /**
     * 生成数据文件的完整内容（不写文件，方便预览/测试）。
     * <p>
     * 对每个游戏，先从 output.media.rules 计算媒体路径覆盖，
     * 再传给条目生成方法，确保数据文件中的媒体路径与实际拷贝后的路径一致。
     */
    public String generateContent(List<Game> games, TemplateV3 template, Platform platform,
                                  Map<String, String> variables) {
        Map<String, String> vars = buildVariables(platform, variables);
        String fileType = template.getTemplateInfo().getDataFileType();
        boolean isXml = "data".equalsIgnoreCase(fileType);

        StringBuilder content = new StringBuilder();

        // 1. 头部
        generateHeader(content, template, platform, vars);

        // 2. 游戏条目
        String entrySeparator = getEntrySeparator(template);
        for (int i = 0; i < games.size(); i++) {
            Game game = games.get(i);

            // 计算该游戏的媒体路径覆盖（从 output.media.rules）
            Map<String, String> mediaOverrides = computeMediaPathOverrides(game, template, platform, vars);

            if (isXml) {
                generateXmlGameEntry(content, game, template, platform, vars, mediaOverrides);
            } else {
                generateTextGameEntry(content, game, template, platform, vars, mediaOverrides);
            }
            // 条目分隔符（最后一个条目后不加）
            if (i < games.size() - 1 && entrySeparator != null && !entrySeparator.isEmpty()) {
                content.append(entrySeparator);
            }
        }

        // 3. 尾部
        generateFooter(content, template, platform, vars);

        return content.toString();
    }

    // ==================== 头部 / 尾部 ====================

    private void generateHeader(StringBuilder content, TemplateV3 template,
                                Platform platform, Map<String, String> vars) {
        if (template.getSystem() == null || template.getSystem().getHeader() == null) return;

        for (String lineTemplate : template.getSystem().getHeader()) {
            String line = evaluateTemplateLine(lineTemplate, null, platform, vars);
            content.append(line).append("\n");
        }
    }

    private void generateFooter(StringBuilder content, TemplateV3 template,
                                Platform platform, Map<String, String> vars) {
        if (template.getSystem() == null || template.getSystem().getFooter() == null) return;

        for (String lineTemplate : template.getSystem().getFooter()) {
            String line = evaluateTemplateLine(lineTemplate, null, platform, vars);
            content.append(line).append("\n");
        }
    }

    // ==================== 文本格式游戏条目 ====================

    /**
     * 生成文本格式的游戏条目。
     * <p>
     * 格式：key + delimiter + value，每个字段一行。
     * 多行值（如 files）使用换行 + 缩进。
     *
     * @param mediaOverrides 媒体路径覆盖（dataFileTag → 计算后的路径），来自 output.media.rules
     */
    private void generateTextGameEntry(StringBuilder content, Game game, TemplateV3 template,
                                       Platform platform, Map<String, String> vars,
                                       Map<String, String> mediaOverrides) {
        String delimiter = template.getTemplateInfo().getDelimiter();
        if (delimiter == null || delimiter.isEmpty()) delimiter = ": ";

        String gameStartMarker = template.getGame() != null ? template.getGame().getGameStartMarker() : null;
        if (gameStartMarker == null) gameStartMarker = "game:";

        TemplateV3.GameMapping gameMapping = template.getGame();
        Map<String, Object> gameInfoMapping = gameMapping != null ? gameMapping.getGameInfo() : null;
        Map<String, Object> mediaInfoMapping = gameMapping != null ? gameMapping.getMediaInfo() : null;

        // 确保条目前有空行（与 header 或上一个条目分隔）
        if (content.length() > 0 && !content.toString().endsWith("\n\n")) {
            if (content.toString().endsWith("\n")) {
                content.append("\n");
            } else {
                content.append("\n\n");
            }
        }

        boolean firstField = true;

        // 游戏信息字段
        if (gameInfoMapping != null) {
            for (Map.Entry<String, Object> entry : gameInfoMapping.entrySet()) {
                String outputKey = entry.getKey();       // 输出到文件的 key（如 "game"、"file"）
                String sourceExpr = TemplateV3.toExpression(entry.getValue()); // 源字段或表达式
                String value = resolveExportValue(sourceExpr, game, platform, vars);

                if (value == null || value.isEmpty()) continue;

                // 处理 path 字段（根据 pathFormat 处理路径）
                if ("path".equals(sourceExpr)) {
                    String pathFormat = getPathFormat(template);
                    value = PathResolver.formatExportPath(value, pathFormat);
                }

                // 多行值：每行缩进（如 files 多盘游戏）
                if (value.contains("\n")) {
                    String[] lines = value.split("\n");
                    // 第一行用 key + delimiter
                    content.append(outputKey).append(delimiter).append(lines[0].trim()).append("\n");
                    // 后续行缩进
                    for (int i = 1; i < lines.length; i++) {
                        if (!lines[i].trim().isEmpty()) {
                            content.append("  ").append(lines[i].trim()).append("\n");
                        }
                    }
                } else {
                    content.append(outputKey).append(delimiter).append(value).append("\n");
                }
                firstField = false;
            }
        }

        // 媒体信息字段（优先使用 output.media.rules 计算的路径）
        if (mediaInfoMapping != null) {
            for (Map.Entry<String, Object> entry : mediaInfoMapping.entrySet()) {
                String outputKey = entry.getKey();       // 输出 key（如 "assets.boxFront"）
                String sourceNomcourt = TemplateV3.toExpression(entry.getValue()); // 源 nomcourt 值

                // 优先使用 output.media.rules 计算的路径覆盖
                String value = mediaOverrides.getOrDefault(outputKey, null);
                if (value == null) {
                    // 回退到 DB 中的原始媒体路径
                    value = GameFieldAccessor.getValue(game, sourceNomcourt);
                }

                if (value == null || value.isEmpty()) continue;

                content.append(outputKey).append(delimiter).append(value).append("\n");
            }
        }
    }

    // ==================== XML 格式游戏条目 ====================

    /**
     * 生成 XML 格式的游戏条目。
     * <p>
     * 格式：&lt;game&gt;...&lt;/game&gt;，每个字段一个子标签。
     *
     * @param mediaOverrides 媒体路径覆盖（dataFileTag → 计算后的路径），来自 output.media.rules
     */
    private void generateXmlGameEntry(StringBuilder content, Game game, TemplateV3 template,
                                      Platform platform, Map<String, String> vars,
                                      Map<String, String> mediaOverrides) {
        String gameTag = template.getGame() != null && template.getGame().getGameStartMarker() != null
                ? template.getGame().getGameStartMarker() : "game";

        TemplateV3.GameMapping gameMapping = template.getGame();
        Map<String, Object> gameInfoMapping = gameMapping != null ? gameMapping.getGameInfo() : null;
        Map<String, Object> mediaInfoMapping = gameMapping != null ? gameMapping.getMediaInfo() : null;

        content.append("  <").append(gameTag).append(">\n");

        // 游戏信息字段
        if (gameInfoMapping != null) {
            for (Map.Entry<String, Object> entry : gameInfoMapping.entrySet()) {
                String xmlTag = entry.getKey();           // XML 标签名（如 "path"、"name"）
                String sourceExpr = TemplateV3.toExpression(entry.getValue());
                String value = resolveExportValue(sourceExpr, game, platform, vars);

                if (value == null || value.isEmpty()) continue;

                // 处理 path 字段
                if ("path".equals(sourceExpr)) {
                    String pathFormat = getPathFormat(template);
                    value = PathResolver.formatExportPath(value, pathFormat);
                }

                content.append("    <").append(xmlTag).append(">")
                       .append(escapeXml(value))
                       .append("</").append(xmlTag).append(">\n");
            }
        }

        // 媒体信息字段（优先使用 output.media.rules 计算的路径）
        if (mediaInfoMapping != null) {
            for (Map.Entry<String, Object> entry : mediaInfoMapping.entrySet()) {
                String xmlTag = entry.getKey();           // XML 标签名（如 "image"、"video"）
                String sourceNomcourt = TemplateV3.toExpression(entry.getValue());

                // 优先使用 output.media.rules 计算的路径覆盖
                String value = mediaOverrides.getOrDefault(xmlTag, null);
                if (value == null) {
                    // 回退到 DB 中的原始媒体路径
                    value = GameFieldAccessor.getValue(game, sourceNomcourt);
                }

                if (value == null || value.isEmpty()) continue;

                content.append("    <").append(xmlTag).append(">")
                       .append(escapeXml(value))
                       .append("</").append(xmlTag).append(">\n");
            }
        }

        content.append("  </").append(gameTag).append(">\n");
    }

    // ==================== 值解析 ====================

    /**
     * 解析导出值。支持表达式和直接字段引用。
     */
    private String resolveExportValue(String sourceExpr, Game game, Platform platform,
                                      Map<String, String> vars) {
        if (sourceExpr == null || sourceExpr.isEmpty()) return null;

        // 使用表达式引擎（支持纯字段名和表达式）
        TemplateExpressionEngine.Context ctx = new TemplateExpressionEngine.Context(game, platform, vars);
        return TemplateExpressionEngine.evaluate(sourceExpr, ctx);
    }

    /**
     * 处理模板行中的 {platform.xxx} 变量替换。
     * 如果有 game 上下文，也支持游戏字段替换。
     */
    private String evaluateTemplateLine(String lineTemplate, Game game, Platform platform,
                                        Map<String, String> vars) {
        String result = lineTemplate;

        // 替换 {platform.xxx} 格式的平台变量
        if (platform != null) {
            result = replacePlatformVariables(result, platform);
        }

        // 替换 {xxx} 格式的环境变量
        result = replaceVars(result, vars);

        // 如果有游戏上下文，处理表达式
        if (game != null && TemplateExpressionEngine.isExpression(result)) {
            TemplateExpressionEngine.Context ctx = new TemplateExpressionEngine.Context(game, platform, vars);
            result = TemplateExpressionEngine.evaluate(result, ctx);
        }

        return result;
    }

    private String replacePlatformVariables(String template, Platform platform) {
        if (template == null) return null;
        return template
                .replace("{platform.system}", nvl(platform.getSystem()))
                .replace("{platform.name}", nvl(platform.getName()))
                .replace("{platform.launch}", nvl(platform.getLaunch()))
                .replace("{platform.software}", nvl(platform.getSoftware()))
                .replace("{platform.database}", nvl(platform.getDatabase()))
                .replace("{platform.web}", nvl(platform.getWeb()))
                .replace("{platform.folderPath}", nvl(platform.getFolderPath()));
    }

    private String replaceVars(String template, Map<String, String> vars) {
        if (template == null || vars == null) return template;
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }

    // ==================== 媒体路径计算 ====================

    /**
     * 为单个游戏计算媒体路径覆盖。
     * <p>
     * 遍历 output.media.rules，对每个有 dataFileTag 的规则：
     * <ol>
     *   <li>检查 Game 对象中是否有该 nomcourt 的媒体数据</li>
     *   <li>将 target 模板中的变量替换为实际值</li>
     *   <li>拼接 mediaDirectory + resolvedTarget 得到完整路径</li>
     *   <li>根据 output.dataFile.pathFormat 将路径转为相对路径或带 ./ 前缀的绝对路径</li>
     * </ol>
     *
     * @return dataFileTag → 计算后的媒体路径
     */
    private Map<String, String> computeMediaPathOverrides(Game game, TemplateV3 template,
                                                          Platform platform, Map<String, String> vars) {
        TemplateV3.OutputConfig output = template.getOutput();
        if (output == null || output.getMedia() == null || output.getMedia().getRules() == null) {
            return Collections.emptyMap();
        }

        TemplateV3.MediaOutput mediaOutput = output.getMedia();
        Map<String, String> overrides = new HashMap<>();

        // 读取 pathFormat，控制媒体路径在数据文件中的输出方式
        String pathFormat = getPathFormat(template);

        // 预解析数据文件目录（用于计算相对路径）
        String dataFileDirStr = null;
        if (output.getDataFile() != null && output.getDataFile().getDirectory() != null) {
            dataFileDirStr = resolveTemplateString(output.getDataFile().getDirectory(), platform, vars);
        }

        // 构建游戏级变量（用于 target 模板求值）
        Map<String, String> gameVars = new HashMap<>(vars);
        String filename = GameFieldAccessor.getValue(game, "filename");
        if (filename == null || filename.isEmpty()) {
            // 从 path 字段计算 filename
            String path = game.getPath();
            if (path != null && !path.isEmpty()) {
                int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                String fileNameWithExt = sep >= 0 ? path.substring(sep + 1) : path;
                int dot = fileNameWithExt.lastIndexOf('.');
                filename = dot > 0 ? fileNameWithExt.substring(0, dot) : fileNameWithExt;
            } else {
                filename = game.getName();
            }
        }
        gameVars.put("filename", filename != null ? filename : "");
        gameVars.put("gameName", game.getName() != null ? game.getName() : "");

        for (Map.Entry<String, TemplateV3.MediaOutputRule> entry : mediaOutput.getRules().entrySet()) {
            TemplateV3.MediaOutputRule rule = entry.getValue();
            String dataFileTag = rule.getDataFileTag();
            if (dataFileTag == null || dataFileTag.isEmpty()) continue;

            String nomcourt = entry.getKey();
            String mediaDbValue = GameFieldAccessor.getValue(game, nomcourt);
            if (mediaDbValue == null || mediaDbValue.isEmpty()) continue;

            // 解析 target 模板
            String resolvedTarget = resolveTemplateString(rule.getTarget(), platform, gameVars);
            if (resolvedTarget == null || resolvedTarget.isEmpty()) continue;

            // 拼接媒体目录 + target = 媒体文件完整路径
            String mediaDir = resolveTemplateString(mediaOutput.getDirectory(), platform, gameVars);
            String fullPath = mediaDir + "/" + resolvedTarget;

            // 始终先计算从数据文件目录到媒体文件的相对路径
            String relativePath = fullPath;
            if (dataFileDirStr != null) {
                try {
                    relativePath = PathResolver.relativize(
                            java.nio.file.Paths.get(dataFileDirStr),
                            java.nio.file.Paths.get(fullPath));
                } catch (java.nio.file.InvalidPathException e) {
                    // Windows 不接受路径中含尾部空格（如游戏名 "Shou " → "Shou .png"）
                    // 回退到字符串方式计算相对路径
                    logger.warn("Paths.get() 失败，回退到字符串计算: {}", e.getMessage());
                    String base = dataFileDirStr.replace('\\', '/');
                    String target = fullPath.replace('\\', '/');
                    // 找共同前缀
                    int minLen = Math.min(base.length(), target.length());
                    int commonLen = 0;
                    while (commonLen < minLen && base.charAt(commonLen) == target.charAt(commonLen)) {
                        commonLen++;
                    }
                    int lastSlash = base.lastIndexOf('/', commonLen);
                    if (lastSlash == -1) lastSlash = 0;
                    String remaining = base.substring(lastSlash);
                    int upCount = (int) remaining.chars().filter(c -> c == '/').count();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < upCount; i++) sb.append("../");
                    sb.append(target.substring(lastSlash));
                    relativePath = sb.toString();
                }
            }

            // 再根据 pathFormat 决定输出格式：
            // - "relative": 纯相对路径（如 media/fanart/xxx.png）
            // - "absoluteWithDot": 相对路径加 ./ 前缀（如 ./media/fanart/xxx.png）
            String finalPath = PathResolver.formatExportPath(relativePath, pathFormat);

            overrides.put(dataFileTag, finalPath);
        }

        return overrides;
    }

    /**
     * 解析模板字符串：替换 {platform.xxx} 和 {variable} 占位符。
     */
    private String resolveTemplateString(String template, Platform platform, Map<String, String> vars) {
        if (template == null) return null;
        String result = template;
        if (platform != null) {
            result = replacePlatformVariables(result, platform);
        }
        result = replaceVars(result, vars);
        return result;
    }

    // ==================== 工具方法 ====================

    private Map<String, String> buildVariables(Platform platform, Map<String, String> extraVars) {
        Map<String, String> vars = new HashMap<>();
        if (extraVars != null) {
            vars.putAll(extraVars);
        }
        // 确保平台系统名在变量中
        if (platform != null && platform.getSystem() != null) {
            vars.putIfAbsent("platform.system", platform.getSystem());
        }
        return vars;
    }

    private String getEntrySeparator(TemplateV3 template) {
        if (template.getGame() != null && template.getGame().getEntrySeparator() != null) {
            // 处理转义字符
            return template.getGame().getEntrySeparator()
                    .replace("\\n", "\n")
                    .replace("\\t", "\t");
        }
        return "\n\n"; // 默认双换行
    }

    private String getPathFormat(TemplateV3 template) {
        if (template.getOutput() != null && template.getOutput().getDataFile() != null) {
            return template.getOutput().getDataFile().getPathFormat();
        }
        return null;
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}
