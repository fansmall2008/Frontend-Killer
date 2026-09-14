package com.gamelist.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamelist.model.ParsedDataFile;
import com.gamelist.model.TemplateV3;

/**
 * 通用文本数据文件解析器。
 * <p>
 * 根据模板中的配置（分隔符、gameStartMarker、multiLine 等）将纯文本数据文件
 * 解析为统一的 {@link ParsedDataFile} 结构。
 * <p>
 * 支持的格式特征：
 * <ul>
 *   <li>key-value 对通过分隔符（如 ":"）分隔</li>
 *   <li>通过 gameStartMarker 区分系统信息和游戏信息</li>
 *   <li>可选的多行续行支持（缩进行追加到上一个 key 的 value）</li>
 *   <li>空行作为条目分隔符</li>
 *   <li># 开头的行视为注释</li>
 *   <li>key 大小写不敏感（统一转为小写存储）</li>
 * </ul>
 */
public class GenericTextParser {

    /**
     * 解析文本数据文件。
     *
     * @param file     数据文件
     * @param template v3 模板（需提供 delimiter、gameStartMarker、multiLine 配置）
     * @return 解析结果
     */
    public static ParsedDataFile parse(File file, TemplateV3 template) throws Exception {
        // ---- 从 parsing 块读取解析参数，缺失时使用默认值保持向后兼容 ----
        TemplateV3.TextParsingConfig textConfig = null;
        if (template.getParsing() != null) {
            textConfig = template.getParsing().getText();
        }

        // 分隔符：parsing.text.delimiter > templateInfo.delimiter > 默认 ":"
        String delimiter = (textConfig != null && textConfig.getDelimiter() != null)
                ? textConfig.getDelimiter()
                : template.getTemplateInfo().getDelimiter();
        if (delimiter == null || delimiter.isEmpty()) {
            delimiter = ":";
        }

        // 注释符列表：默认 ["#"]
        List<String> commentChars = (textConfig != null && textConfig.getCommentChars() != null)
                ? textConfig.getCommentChars()
                : Arrays.asList("#");

        // 条目分隔方式：默认 "emptyLine"
        String entrySeparator = (textConfig != null && textConfig.getEntrySeparator() != null)
                ? textConfig.getEntrySeparator()
                : "emptyLine";
        boolean separateByEmptyLine = "emptyLine".equalsIgnoreCase(entrySeparator);

        // key 大小写策略：默认 "lower"
        String keyCase = (textConfig != null && textConfig.getKeyCase() != null)
                ? textConfig.getKeyCase().toLowerCase()
                : "lower";

        // 文件编码：默认 UTF-8
        Charset charset = StandardCharsets.UTF_8;
        if (textConfig != null && textConfig.getEncoding() != null && !textConfig.getEncoding().isEmpty()) {
            try {
                charset = Charset.forName(textConfig.getEncoding());
            } catch (Exception e) {
                // 无效编码名，回退 UTF-8
            }
        }

        // game 块配置
        String gameStartMarker = null;
        boolean multiLine = false;
        List<String> indentChars = Arrays.asList("  ", "\t");
        if (template.getGame() != null) {
            gameStartMarker = template.getGame().getGameStartMarker();
            multiLine = template.getGame().isMultiLine();
        }
        // 多行续行缩进字符：从 parsing.text.multiLine.indentChars 读取
        if (textConfig != null && textConfig.getMultiLine() != null
                && textConfig.getMultiLine().getIndentChars() != null
                && !textConfig.getMultiLine().getIndentChars().isEmpty()) {
            indentChars = textConfig.getMultiLine().getIndentChars();
        }

        ParsedDataFile result = new ParsedDataFile();
        Map<String, String> currentGame = null;
        String lastKey = null;         // 用于多行续行
        boolean lastKeyInGame = false; // lastKey 属于游戏字段还是系统字段
        boolean inGameSection = false;

        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis, charset);
             BufferedReader reader = new BufferedReader(isr)) {

            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                // 跳过注释行（按模板配置的注释符列表匹配）
                boolean isComment = false;
                for (String commentChar : commentChars) {
                    if (commentChar != null && trimmed.startsWith(commentChar)) {
                        isComment = true;
                        break;
                    }
                }
                if (isComment) {
                    continue;
                }

                // 空行处理
                if (trimmed.isEmpty()) {
                    // 空行作为条目分隔符（当 entrySeparator 为 "emptyLine" 时）
                    if (separateByEmptyLine && currentGame != null && !currentGame.isEmpty()) {
                        result.addGame(currentGame);
                        currentGame = null;
                        lastKey = null;
                        inGameSection = false;
                    }
                    continue;
                }

                // 检查是否为续行（缩进开头 + multiLine 模式 + 有上一个 key）
                if (multiLine && lastKey != null) {
                    boolean isIndented = false;
                    for (String indent : indentChars) {
                        if (line.startsWith(indent)) {
                            isIndented = true;
                            break;
                        }
                    }
                    if (isIndented) {
                        // 续行：追加到上一个 key 的 value
                        Map<String, String> target = (lastKeyInGame && currentGame != null) ? currentGame : null;
                        if (target == null && !lastKeyInGame) {
                            // 追加到系统字段
                            String existing = result.getSystemField(lastKey);
                            if (existing != null) {
                                result.putSystemField(lastKey, existing + "\n" + trimmed);
                            }
                        } else if (target != null) {
                            String existing = target.get(lastKey);
                            if (existing != null) {
                                target.put(lastKey, existing + "\n" + trimmed);
                            }
                        }
                        continue;
                    }
                }

                // 普通行：按分隔符拆分 key-value
                int delimIndex = line.indexOf(delimiter);
                if (delimIndex <= 0) {
                    // 没有分隔符或分隔符在行首，跳过
                    continue;
                }

                String rawKey = line.substring(0, delimIndex).trim();
                String value = line.substring(delimIndex + delimiter.length()).trim();

                // 按模板配置的 keyCase 策略处理大小写
                String processedKey;
                switch (keyCase) {
                    case "upper":  processedKey = rawKey.toUpperCase(); break;
                    case "preserve": processedKey = rawKey; break;
                    default:       processedKey = rawKey.toLowerCase(); break;
                }

                // 检查是否为游戏起始标记
                String markerProcessed = null;
                if (gameStartMarker != null) {
                    switch (keyCase) {
                        case "upper":  markerProcessed = gameStartMarker.toUpperCase(); break;
                        case "preserve": markerProcessed = gameStartMarker; break;
                        default:       markerProcessed = gameStartMarker.toLowerCase(); break;
                    }
                    markerProcessed = markerProcessed.replace(":", "").trim();
                }
                if (markerProcessed != null && processedKey.equals(markerProcessed)) {
                    // 遇到新的游戏开始
                    if (currentGame != null && !currentGame.isEmpty()) {
                        result.addGame(currentGame);
                    }
                    currentGame = new LinkedHashMap<>();
                    currentGame.put(processedKey, value);
                    lastKey = processedKey;
                    lastKeyInGame = true;
                    inGameSection = true;
                    continue;
                }

                // 普通 key-value
                if (inGameSection && currentGame != null) {
                    // 游戏字段
                    currentGame.put(processedKey, value);
                    lastKey = processedKey;
                    lastKeyInGame = true;
                } else {
                    // 系统字段
                    result.putSystemField(processedKey, value);
                    lastKey = processedKey;
                    lastKeyInGame = false;
                }
            }

            // 处理最后一个游戏
            if (currentGame != null && !currentGame.isEmpty()) {
                result.addGame(currentGame);
            }
        }

        return result;
    }

    /**
     * 简化版：直接指定分隔符和游戏起始标记解析（不依赖模板对象）。
     */
    public static ParsedDataFile parse(File file, String delimiter, String gameStartMarker, boolean multiLine) throws Exception {
        TemplateV3 mockTemplate = new TemplateV3();

        TemplateV3.TemplateInfo info = new TemplateV3.TemplateInfo();
        info.setDelimiter(delimiter);
        mockTemplate.setTemplateInfo(info);

        TemplateV3.GameMapping game = new TemplateV3.GameMapping();
        game.setGameStartMarker(gameStartMarker);
        game.setMultiLine(multiLine);
        mockTemplate.setGame(game);

        return parse(file, mockTemplate);
    }
}
