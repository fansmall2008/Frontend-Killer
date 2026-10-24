package com.gamelist.variant;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 格式表：副本的“展示与可用范围”定义。
 * <p>
 * 两类格式：
 * <ul>
 *   <li>{@code generic=true}：沿用系统类型/扩展名规则（裸 ROM、ZIP、7z）</li>
 *   <li>带 {@code scope}：只在声明过的系统或扩展名上出现（ISO、CSO）</li>
 * </ul>
 * 这样开 CSO 不会连带把几十个光盘系统暴露出来；给新系统开格式只改 JSON。
 */
@Component
public class FormatRegistry {

    private static final Logger logger = LoggerFactory.getLogger(FormatRegistry.class);
    private static final String PATH = "rules/variants/formats.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile List<Format> cache;

    public record Scope(Set<Integer> systemIds, Set<String> extensions) {

        public Scope {
            systemIds = systemIds == null ? Set.of() : Set.copyOf(systemIds);
            extensions = extensions == null ? Set.of() : Set.copyOf(extensions);
        }

        public boolean matches(int systemId, Set<String> systemExtensions) {
            if (systemIds.contains(systemId)) {
                return true;
            }
            for (String extension : systemExtensions) {
                if (extensions.contains(extension)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * @param id             与 capability 表的 format 字段一致
     * @param label          界面文案
     * @param order          展示顺序
     * @param extension      产物默认扩展名
     * @param generic        true 表示沿用旧的系统类型规则
     * @param defaultEnabled 新播种的系统默认是否勾选
     * @param scope          非 generic 格式的适用范围
     */
    public record Format(String id, String label, int order, String extension,
                         boolean generic, boolean defaultEnabled, Scope scope) {

        public Format {
            scope = scope == null ? new Scope(Set.of(), Set.of()) : scope;
        }
    }

    public List<Format> all() {
        List<Format> current = cache;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (cache == null) {
                cache = load();
            }
            return cache;
        }
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (Format format : all()) {
            ids.add(format.id());
        }
        return ids;
    }

    public Format byId(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (Format format : all()) {
            if (format.id().equals(key)) {
                return format;
            }
        }
        return null;
    }

    /**
     * 格式表里被显式点名的系统（有 scope 声明）。
     * <p>
     * 这些系统已明确纳入副本机制，通用的 bare/zip/7z 不应再被类型规则（如 romType=iso）挡在外面。
     */
    public Set<Integer> declaredSystemIds() {
        Set<Integer> ids = new LinkedHashSet<>();
        for (Format format : all()) {
            ids.addAll(format.scope().systemIds());
        }
        return ids;
    }

    /**
     * 某系统是否适用某格式。
     *
     * @param genericEligible 旧规则（type / romType / extensions）的结论，仅对 generic 格式生效
     */
    public boolean applies(Format format, int systemId, boolean genericEligible, Set<String> systemExtensions) {
        if (format == null) {
            return false;
        }
        if (format.generic()) {
            return genericEligible;
        }
        return format.scope().matches(systemId, systemExtensions);
    }

    /** 解析 "iso,bin|cue" 这样的扩展名串 */
    public static Set<String> parseExtensions(String raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.toLowerCase(Locale.ROOT).split("[,;\\s|/]+")) {
            String extension = part.replace(".", "").trim();
            if (!extension.isEmpty()) {
                result.add(extension);
            }
        }
        return result;
    }

    private List<Format> load() {
        List<Format> formats = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(PATH);
        if (!resource.exists()) {
            logger.warn("格式表不存在，使用内置兜底: {}", PATH);
            formats.add(new Format("bare", "裸 ROM", 10, "rom", true, true, null));
            formats.add(new Format("zip", "ZIP", 20, "zip", true, true, null));
            formats.add(new Format("7z", "7z", 30, "7z", true, true, null));
            return List.copyOf(formats);
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            JsonNode array = root.get("formats");
            if (array != null && array.isArray()) {
                for (JsonNode node : array) {
                    String id = node.has("id") ? node.get("id").asText("").trim().toLowerCase(Locale.ROOT) : "";
                    if (id.isEmpty()) {
                        continue;
                    }
                    Scope scope = null;
                    JsonNode scopeNode = node.get("scope");
                    if (scopeNode != null && scopeNode.isObject()) {
                        Set<Integer> systemIds = new LinkedHashSet<>();
                        JsonNode ids = scopeNode.get("systemIds");
                        if (ids != null && ids.isArray()) {
                            for (JsonNode item : ids) {
                                systemIds.add(item.asInt(0));
                            }
                        }
                        Set<String> extensions = new LinkedHashSet<>();
                        JsonNode ext = scopeNode.get("extensions");
                        if (ext != null && ext.isArray()) {
                            for (JsonNode item : ext) {
                                String value = item.asText("").trim().toLowerCase(Locale.ROOT);
                                if (!value.isEmpty()) {
                                    extensions.add(value);
                                }
                            }
                        }
                        scope = new Scope(systemIds, extensions);
                    }
                    formats.add(new Format(
                            id,
                            node.has("label") ? node.get("label").asText(id) : id,
                            node.has("order") ? node.get("order").asInt(100) : 100,
                            node.has("extension") ? node.get("extension").asText(id) : id,
                            node.has("generic") && node.get("generic").asBoolean(false),
                            !node.has("defaultEnabled") || node.get("defaultEnabled").asBoolean(true),
                            scope));
                }
            }
        } catch (Exception e) {
            logger.warn("读取格式表失败: {}", e.getMessage());
        }
        if (formats.isEmpty()) {
            formats.add(new Format("bare", "裸 ROM", 10, "rom", true, true, null));
            formats.add(new Format("zip", "ZIP", 20, "zip", true, true, null));
            formats.add(new Format("7z", "7z", 30, "7z", true, true, null));
        }
        formats.sort(Comparator.comparingInt(Format::order));
        return List.copyOf(formats);
    }

    public void invalidate() {
        cache = null;
    }
}
