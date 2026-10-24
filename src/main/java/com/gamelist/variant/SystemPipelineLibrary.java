package com.gamelist.variant;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 系统产线库：哪个系统允许“从什么形态 → 到什么格式”。
 * <p>
 * 这一层是<b>按系统</b>声明的，全局只放模块定义。这样给 PS1/PSP 开 CSO 不会连带把
 * 另外几十个光盘系统一起暴露出来；新增系统只需要在 JSON 里追加一条。
 */
@Component
public class SystemPipelineLibrary {

    private static final Logger logger = LoggerFactory.getLogger(SystemPipelineLibrary.class);
    private static final String PATH = "rules/variants/system-pipelines.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PipelinePlanner planner;
    private volatile Map<Integer, Declaration> cache;

    public SystemPipelineLibrary(PipelinePlanner planner) {
        this.planner = planner;
    }

    /**
     * @param format  目标格式（与 capability 表的 format 一致，如 cso / iso）
     * @param enabled 默认是否勾选
     * @param sources 允许的源形态
     */
    public record Target(String format, boolean enabled, List<ArtifactKind> sources) {

        public Target {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }

    public record Declaration(int systemId, String name, Map<String, Target> targets) {

        public Declaration {
            targets = targets == null ? Map.of() : Map.copyOf(targets);
        }

        public List<String> formats() {
            return List.copyOf(targets.keySet());
        }
    }

    /** 界面展示用：某系统某目标格式下的一条候选产线 */
    public record PipelineView(String format, ArtifactKind source, PipelinePlan plan) {
    }

    public Declaration declaration(int systemId) {
        return all().get(systemId);
    }

    /** 全部系统声明，供诊断接口与产线页展示 */
    public List<Declaration> declarations() {
        return List.copyOf(all().values());
    }

    public boolean has(int systemId) {
        return all().containsKey(systemId);
    }

    public List<Target> targets(int systemId) {
        Declaration declaration = declaration(systemId);
        return declaration == null ? List.of() : List.copyOf(declaration.targets().values());
    }

    public Target target(int systemId, String format) {
        Declaration declaration = declaration(systemId);
        if (declaration == null || format == null) {
            return null;
        }
        return declaration.targets().get(format.trim().toLowerCase(Locale.ROOT));
    }

    /** 系统是否声明“允许从这个源形态转到这个格式” */
    public boolean allows(int systemId, ArtifactKind source, String format) {
        Target target = target(systemId, format);
        return target != null && source != null && target.sources().contains(source);
    }

    /**
     * 该系统全部候选产线：界面用它显示“iso ▸[iso2cso]▸ cso”以及是否就绪。
     */
    public List<PipelineView> pipelines(int systemId) {
        List<PipelineView> views = new ArrayList<>();
        for (Target target : targets(systemId)) {
            for (ArtifactKind source : target.sources()) {
                views.add(new PipelineView(target.format(), source, planner.plan(source, targetKind(target.format()))));
            }
        }
        return views;
    }

    /** 目标格式 id → 工件形态；格式表与模块图之间的唯一约定 */
    public static ArtifactKind targetKind(String format) {
        if (format == null) {
            return null;
        }
        switch (format.trim().toLowerCase(Locale.ROOT)) {
            case "cso":
                return ArtifactKind.CSO;
            case "zso":
                return ArtifactKind.ZSO;
            case "iso":
                return ArtifactKind.ISO_2048;
            case "chd":
                return ArtifactKind.CHD;
            case "pbp":
                return ArtifactKind.PBP;
            default:
                return null;
        }
    }

    private Map<Integer, Declaration> all() {
        Map<Integer, Declaration> current = cache;
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

    private Map<Integer, Declaration> load() {
        Map<Integer, Declaration> result = new LinkedHashMap<>();
        ClassPathResource resource = new ClassPathResource(PATH);
        if (!resource.exists()) {
            logger.warn("系统产线声明不存在: {}", PATH);
            return result;
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            JsonNode systems = root.get("systems");
            if (systems == null || !systems.isArray()) {
                return result;
            }
            for (JsonNode node : systems) {
                int systemId = node.has("systemId") ? node.get("systemId").asInt(0) : 0;
                if (systemId <= 0) {
                    continue;
                }
                String name = node.has("name") ? node.get("name").asText("") : "";
                Map<String, Target> targets = new LinkedHashMap<>();
                JsonNode formats = node.get("targets");
                if (formats != null && formats.isObject()) {
                    formats.fields().forEachRemaining(entry -> {
                        String format = entry.getKey().toLowerCase(Locale.ROOT);
                        JsonNode value = entry.getValue();
                        Target target = new Target(format,
                                !value.has("enabled") || value.get("enabled").asBoolean(true),
                                kinds(value.get("sources")));
                        targets.put(format, target);
                    });
                }
                result.put(systemId, new Declaration(systemId, name, targets));
                logger.info("系统产线声明: {} {} → {}", systemId, name, targets.keySet());
            }
        } catch (Exception e) {
            logger.warn("读取系统产线声明失败: {}", e.getMessage());
        }
        return result;
    }

    private static List<ArtifactKind> kinds(JsonNode node) {
        Set<ArtifactKind> kinds = new LinkedHashSet<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                ArtifactKind kind = ArtifactKind.byCode(item.asText(""));
                if (kind != null) {
                    kinds.add(kind);
                }
            }
        }
        return new ArrayList<>(kinds);
    }

    /** 供诊断接口使用：清空缓存后重新读盘 */
    public void invalidate() {
        cache = null;
    }
}
