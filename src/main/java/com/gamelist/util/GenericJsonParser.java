package com.gamelist.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelist.model.ParsedDataFile;
import com.gamelist.model.TemplateV3;

/**
 * 通用 JSON 数据文件解析器。
 * <p>
 * 根据模板中的配置（root 路径、编码）将 JSON 数据文件解析为统一的
 * {@link ParsedDataFile} 结构。
 * <p>
 * 支持的格式特征：
 * <ul>
 *   <li>游戏条目数组通过点号路径定位（如 "items"、"playlist.games"），省略时默认 "items"</li>
 *   <li>顶层标量字段（version、db_name 等）自动收集进 systemFields</li>
 *   <li>条目内字符串/数字/布尔值转为 key-value；嵌套对象/数组不展开（扁平映射）</li>
 *   <li>缺字段容忍：条目字段缺失或为 null 时直接跳过，不报错</li>
 *   <li>支持带 UTF-8 BOM 的文件与自定义编码（parsing.json.encoding）</li>
 * </ul>
 */
public class GenericJsonParser {

    private static final Logger logger = LoggerFactory.getLogger(GenericJsonParser.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 解析 JSON 数据文件。
     *
     * @param file     数据文件
     * @param template v3 模板（需提供 parsing.json.root 配置）
     * @return 解析结果
     */
    public static ParsedDataFile parse(File file, TemplateV3 template) throws Exception {
        // ---- 从 parsing 块读取 JSON 解析参数，缺失时使用默认值 ----
        TemplateV3.JsonParsingConfig jsonConfig = null;
        if (template.getParsing() != null) {
            jsonConfig = template.getParsing().getJson();
        }

        // 游戏条目数组路径：默认 "items"
        String root = (jsonConfig != null && jsonConfig.getRoot() != null && !jsonConfig.getRoot().isEmpty())
                ? jsonConfig.getRoot()
                : "items";

        // 文件编码：默认 UTF-8
        Charset charset = StandardCharsets.UTF_8;
        if (jsonConfig != null && jsonConfig.getEncoding() != null && !jsonConfig.getEncoding().isEmpty()) {
            try {
                charset = Charset.forName(jsonConfig.getEncoding());
            } catch (Exception e) {
                // 无效编码名，回退 UTF-8
            }
        }

        // 读取 JSON（跳过可能的 UTF-8 BOM）
        JsonNode doc;
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            bis.mark(3);
            byte[] bom = new byte[3];
            int read = bis.read(bom);
            if (read != 3 || !(bom[0] == (byte) 0xEF && bom[1] == (byte) 0xBB && bom[2] == (byte) 0xBF)) {
                bis.reset();
            }
            doc = MAPPER.readTree(new InputStreamReader(bis, charset));
        }
        if (doc == null) {
            throw new IllegalArgumentException("JSON 文件为空或无法解析: " + file.getName());
        }

        // 定位游戏条目数组
        JsonNode items = resolvePath(doc, root);
        if (items == null) {
            throw new IllegalArgumentException("JSON 中找不到条目数组路径: " + root + "（文件: " + file.getName() + "）");
        }
        if (!items.isArray()) {
            throw new IllegalArgumentException("JSON 条目路径不是数组: " + root + "（文件: " + file.getName() + "）");
        }

        ParsedDataFile result = new ParsedDataFile();

        // 顶层标量字段（root 首段之外）→ systemFields
        collectTopLevelScalars(doc, root, result);

        // 条目间值完全一致的字段（如 db_name、core_path）→ 提升为系统字段
        Map<String, String> firstValues = new LinkedHashMap<>();
        Set<String> inconsistent = new HashSet<>();

        // 游戏条目 → 扁平 key-value
        for (JsonNode item : items) {
            if (item == null || !item.isObject()) {
                continue;
            }
            Map<String, String> fields = new LinkedHashMap<>();
            item.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    return; // 缺失/空字段容忍
                }
                if (value.isValueNode()) {
                    String text = value.asText();
                    fields.put(entry.getKey(), text);
                    // 一致性追踪
                    if (!firstValues.containsKey(entry.getKey())) {
                        firstValues.put(entry.getKey(), text);
                    } else if (!firstValues.get(entry.getKey()).equals(text)) {
                        inconsistent.add(entry.getKey());
                    }
                }
                // 嵌套对象/数组不展开：扁平 key-value 映射层无法表达层级
            });
            if (!fields.isEmpty()) {
                result.addGame(fields);
            }
        }

        // 一致的字段提升进 systemFields（模板可通过 system.fields 映射）
        for (Map.Entry<String, String> entry : firstValues.entrySet()) {
            if (!inconsistent.contains(entry.getKey())) {
                result.putSystemField(entry.getKey(), entry.getValue());
            }
        }

        logger.debug("JSON 解析完成: {}（条目 {} 个）", file.getName(), result.getGameCount());
        return result;
    }

    /** 按点号路径解析 JSON 节点 */
    private static JsonNode resolvePath(JsonNode doc, String path) {
        if (path == null || path.isEmpty()) {
            return doc;
        }
        JsonNode node = doc;
        for (String segment : path.split("\\.")) {
            if (node == null || !node.isObject()) {
                return null;
            }
            node = node.get(segment);
        }
        return node;
    }

    /** 将顶层标量字段（root 首段之外）收集进 systemFields */
    private static void collectTopLevelScalars(JsonNode doc, String root, ParsedDataFile result) {
        String rootFirst = (root == null || root.isEmpty()) ? null : root.split("\\.")[0];
        doc.fields().forEachRemaining(entry -> {
            if (entry.getKey().equals(rootFirst)) {
                return;
            }
            JsonNode value = entry.getValue();
            if (value != null && value.isValueNode()) {
                result.putSystemField(entry.getKey(), value.asText());
            }
        });
    }
}
