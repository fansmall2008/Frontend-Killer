package com.gamelist.export.action;

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
 * 导出动作目录：读取 {@code rules/variants/export-actions.json}，提供动作声明的查询。
 * <p>
 * 与 {@code ModuleCatalog}/{@code FormatRegistry} 同一套路——声明“存在性与约束”，
 * 行为由 Java bean 提供。加载一次常驻内存，格式非法只 WARN 跳过、不影响主流程。
 */
@Component
public class ExportActionCatalog {

    private static final Logger logger = LoggerFactory.getLogger(ExportActionCatalog.class);
    private static final String PATH = "rules/variants/export-actions.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ExportAction> byId = new LinkedHashMap<>();

    public ExportActionCatalog() {
        load();
    }

    private void load() {
        byId.clear();
        ClassPathResource resource = new ClassPathResource(PATH);
        if (!resource.exists()) {
            logger.info("导出动作目录不存在，动作机制关闭: {}", PATH);
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            JsonNode actions = root.get("actions");
            if (actions == null || !actions.isArray()) {
                return;
            }
            for (JsonNode node : actions) {
                String id = text(node, "id");
                if (id.isBlank()) {
                    continue;
                }
                ExportAction.Builder builder = ExportAction.builder(id)
                        .label(text(node, "label"))
                        .variableType(text(node, "variableType"))
                        .singlePlatform(node.has("singlePlatform") && node.get("singlePlatform").asBoolean(false))
                        .optionsSource(text(node, "optionsSource"))
                        .note(text(node, "note"));
                JsonNode scope = node.get("scope");
                if (scope != null && scope.has("systemIds") && scope.get("systemIds").isArray()) {
                    for (JsonNode item : scope.get("systemIds")) {
                        builder.addSystemId(item.asInt(0));
                    }
                }
                JsonNode chain = node.get("chain");
                if (chain != null && chain.isArray()) {
                    for (JsonNode item : chain) {
                        String step = item.asText("").trim();
                        if (!step.isEmpty()) {
                            builder.addChainStep(step);
                        }
                    }
                }
                JsonNode params = node.get("params");
                if (params != null && params.isObject()) {
                    params.fields().forEachRemaining(e -> builder.param(e.getKey(), e.getValue().asText("")));
                }
                JsonNode strategies = node.get("strategies");
                if (strategies != null && strategies.isObject()) {
                    strategies.fields().forEachRemaining(e -> {
                        try {
                            builder.strategy(Integer.parseInt(e.getKey().trim()), e.getValue().asText(""));
                        } catch (NumberFormatException ignored) {
                            logger.warn("忽略非法 strategy 键（应为 systemId）: action={}, key={}", id, e.getKey());
                        }
                    });
                }
                ExportAction action = builder.build();
                byId.put(lower(id), action);
                logger.info("导出动作声明: {} [{}] scope={} chain={}", id, action.variableType(),
                        action.systemIds(), action.chain());
            }
        } catch (Exception e) {
            logger.warn("读取导出动作目录失败: {}", e.getMessage());
        }
    }

    public ExportAction get(String id) {
        return id == null ? null : byId.get(lower(id));
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    public List<ExportAction> all() {
        return new ArrayList<>(byId.values());
    }

    /** 声明了此动作、且该系统在作用范围内的动作集合（供校验/候选计算） */
    public List<ExportAction> forSystem(int systemId) {
        List<ExportAction> result = new ArrayList<>();
        for (ExportAction action : byId.values()) {
            if (action.supportsSystem(systemId)) {
                result.add(action);
            }
        }
        return result;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
