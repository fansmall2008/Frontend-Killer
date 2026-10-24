package com.gamelist.variant;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 模块目录：已实现的模块来自 Spring Bean，未实现的模块来自 JSON 占位声明。
 * <p>
 * 规则：目录声明“存在性”，Bean 提供“实现”。同 id 的 Bean 出现后，占位自动变为 AVAILABLE——
 * 这就是“先留位置、后补实现”的落地点。
 */
@Component
public class ModuleCatalog {

    private static final Logger logger = LoggerFactory.getLogger(ModuleCatalog.class);
    private static final String CATALOG_PATH = "rules/variants/pipeline-modules.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<ConversionModule.ModuleSpec> specs = new ArrayList<>();
    private final Map<String, ConversionModule> implementations = new LinkedHashMap<>();

    public ModuleCatalog(List<ConversionModule> modules) {
        if (modules != null) {
            for (ConversionModule module : modules) {
                if (module != null && module.spec() != null) {
                    implementations.put(lower(module.spec().id()), module);
                }
            }
        }
        load();
    }

    private void load() {
        Map<String, ConversionModule.ModuleSpec> byId = new LinkedHashMap<>();
        // 先放已实现的模块
        for (ConversionModule module : implementations.values()) {
            byId.put(lower(module.spec().id()), module.spec());
        }
        // 再读占位声明：同 id 已被实现则忽略占位
        ClassPathResource resource = new ClassPathResource(CATALOG_PATH);
        if (resource.exists()) {
            try (InputStream in = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                JsonNode modules = root.get("modules");
                if (modules != null && modules.isArray()) {
                    for (JsonNode node : modules) {
                        String id = text(node, "id");
                        if (id.isBlank() || byId.containsKey(id)) {
                            continue;
                        }
                        ArtifactKind input = kind(text(node, "input"));
                        ArtifactKind output = kind(text(node, "output"));
                        if (input == null || output == null) {
                            logger.warn("跳过无效模块声明: id={}, input={}, output={}",
                                    id, text(node, "input"), text(node, "output"));
                            continue;
                        }
                        byId.put(id, new ConversionModule.ModuleSpec(
                                id, input, output,
                                node.has("cost") ? node.get("cost").asInt(10) : 10,
                                !node.has("lossless") || node.get("lossless").asBoolean(true),
                                ConversionModule.ModuleState.RESERVED,
                                text(node, "note"),
                                stringList(node.get("externalDeps"))));
                    }
                }
            } catch (Exception e) {
                logger.warn("读取流水线模块目录失败: {}", e.getMessage());
            }
        } else {
            logger.warn("流水线模块目录不存在: {}", CATALOG_PATH);
        }
        specs.clear();
        specs.addAll(byId.values());
    }

    /** 保持声明顺序的全部模块（含预留） */
    public List<ConversionModule.ModuleSpec> specs() {
        return List.copyOf(specs);
    }

    public List<ConversionModule> availableModules() {
        List<ConversionModule> result = new ArrayList<>();
        for (ConversionModule.ModuleSpec spec : specs) {
            ConversionModule module = implementations.get(lower(spec.id()));
            if (spec.available() && module != null) {
                result.add(module);
            }
        }
        return result;
    }

    public ConversionModule implementation(String id) {
        return implementations.get(lower(id));
    }

    public ConversionModule.ModuleSpec spec(String id) {
        String key = lower(id);
        for (ConversionModule.ModuleSpec spec : specs) {
            if (lower(spec.id()).equals(key)) {
                return spec;
            }
        }
        return null;
    }

    /** 同时接受枚举名与短码（ISO_2048 / iso） */
    private static ArtifactKind kind(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return ArtifactKind.byCode(name);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && !item.asText("").isBlank()) {
                    values.add(item.asText());
                }
            }
        }
        return values;
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
