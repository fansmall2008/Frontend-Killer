package com.gamelist.service.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelist.mapper.ScraperSystemMapper;
import com.gamelist.mapper.SystemVariantCapabilityMapper;
import com.gamelist.mapper.SystemVariantPreferenceMapper;
import com.gamelist.model.ScraperSystem;
import com.gamelist.model.SystemVariantCapability;
import com.gamelist.model.SystemVariantPreference;
import com.gamelist.service.VariantCapabilityService;
import com.gamelist.variant.FormatRegistry;

@Service
public class VariantCapabilityServiceImpl implements VariantCapabilityService {

    private static final Logger logger = LoggerFactory.getLogger(VariantCapabilityServiceImpl.class);
    private static final String RULES_PATH = "rules/variants/capability-rules.json";
    private static final String FORMAT_ORIGINAL = "original";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ScraperSystemMapper scraperSystemMapper;

    @Autowired
    private SystemVariantCapabilityMapper capabilityMapper;

    @Autowired
    private SystemVariantPreferenceMapper preferenceMapper;

    @Autowired
    private FormatRegistry formatRegistry;

    private volatile CapabilityRules cachedRules;

    @Override
    public void refresh() {
        List<ScraperSystem> systems = scraperSystemMapper.selectAll();
        if (systems == null || systems.isEmpty()) {
            return;
        }

        CapabilityRules rules = rules();
        Map<Integer, ScraperSystem> byId = new LinkedHashMap<>();
        Set<Integer> eligibleIds = new HashSet<>();

        for (ScraperSystem system : systems) {
            Integer systemId = system.getSystemId();
            if (systemId == null) {
                continue;
            }
            byId.put(systemId, system);
            Set<String> extensions = FormatRegistry.parseExtensions(system.getExtensions());
            boolean genericEligible = genericEligible(system, rules);
            for (FormatRegistry.Format format : formatRegistry.all()) {
                if (!formatRegistry.applies(format, systemId, genericEligible, extensions)) {
                    continue;
                }
                eligibleIds.add(systemId);
                SystemVariantCapability existing = capabilityMapper.selectBySystemIdAndFormat(systemId, format.id());
                if (existing != null) {
                    continue; // auto/manual 都保留，避免覆盖用户选择
                }
                SystemVariantCapability cap = new SystemVariantCapability();
                cap.setSystemId(systemId);
                cap.setFormat(format.id());
                cap.setSupported(format.defaultEnabled());
                cap.setSource("auto");
                cap.setNote("auto: " + nvl(system.getType()) + "/" + nvl(system.getRomType()));
                capabilityMapper.insert(cap);
            }
        }

        // 规则变化后按 (system, format) 清理不再适用的 auto 行；manual 行保留
        for (SystemVariantCapability cap : capabilityMapper.selectAll()) {
            if (!"auto".equalsIgnoreCase(cap.getSource())) {
                continue;
            }
            ScraperSystem system = byId.get(cap.getSystemId());
            FormatRegistry.Format format = formatRegistry.byId(cap.getFormat());
            boolean keep = system != null && format != null
                    && formatRegistry.applies(format, cap.getSystemId(), genericEligible(system, rules),
                            FormatRegistry.parseExtensions(system.getExtensions()));
            if (!keep) {
                capabilityMapper.deleteAutoBySystemIdAndFormat(cap.getSystemId(), cap.getFormat());
            }
        }

        logger.info("副本能力矩阵刷新完成: 合格系统 {} 个", eligibleIds.size());
    }

    @Override
    public List<Map<String, Object>> getMatrix() {
        refresh();

        List<ScraperSystem> systems = scraperSystemMapper.selectAll();
        Map<Integer, List<SystemVariantCapability>> capsBySystem = new HashMap<>();
        for (SystemVariantCapability cap : capabilityMapper.selectAll()) {
            capsBySystem.computeIfAbsent(cap.getSystemId(), k -> new ArrayList<>()).add(cap);
        }
        Map<Integer, String> preferences = new HashMap<>();
        for (SystemVariantPreference preference : preferenceMapper.selectAll()) {
            preferences.put(preference.getSystemId(), preference.getPreferredFormat());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScraperSystem system : systems) {
            List<SystemVariantCapability> caps = capsBySystem.getOrDefault(system.getSystemId(), List.of());
            Map<String, Boolean> formats = applicableFormats(system, caps);
            if (formats.isEmpty()) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("systemId", system.getSystemId());
            row.put("name", system.getName());
            row.put("nameEn", system.getNameEn());
            row.put("type", system.getType());
            row.put("romType", system.getRomType());
            row.put("extensions", system.getExtensions());
            row.put("formats", formats);
            String preferred = preferences.get(system.getSystemId());
            row.put("preferredFormat", normalizePreference(preferred, formats));
            rows.add(row);
        }

        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("name"))));
        return rows;
    }

    @Override
    public boolean isEligible(ScraperSystem system) {
        return isEligible(system, rules());
    }

    /**
     * 通用格式（bare/zip/7z）是否对该系统开放。
     * <p>
     * 除了旧的类型规则，格式表里显式点名过的系统（如 PS1/PSP）也算合格；
     * 否则 romType=iso 的光盘系统会把裸 ROM/ZIP 一并挡掉。
     */
    private boolean genericEligible(ScraperSystem system, CapabilityRules rules) {
        if (system == null || system.getSystemId() == null) {
            return false;
        }
        return isEligible(system, rules) || formatRegistry.declaredSystemIds().contains(system.getSystemId());
    }

    private boolean isEligible(ScraperSystem system, CapabilityRules rules) {
        if (system == null || system.getSystemId() == null) {
            return false;
        }
        String type = lower(system.getType());
        if (!rules.eligibleTypes.contains(type)) {
            return false;
        }
        String romType = lower(system.getRomType());
        if (!romType.isEmpty()) {
            for (String excluded : rules.excludeRomTypes) {
                if (romType.contains(excluded)) {
                    return false;
                }
            }
        }
        Set<String> extensions = parseExtensions(system.getExtensions());
        if (extensions.isEmpty()) {
            return true; // 部分基础数据没有 extensions，先按 type/rom_type 放行
        }
        for (String extension : extensions) {
            if (rules.eligibleExtensions.contains(extension)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isSupported(Integer systemId, String format) {
        if (systemId == null || format == null) {
            return false;
        }
        String normalized = lower(format);
        if (FORMAT_ORIGINAL.equals(normalized)) {
            return true;
        }
        FormatRegistry.Format spec = formatRegistry.byId(normalized);
        if (spec == null) {
            return false;
        }
        SystemVariantCapability cap = capabilityMapper.selectBySystemIdAndFormat(systemId, normalized);
        if (cap != null) {
            return Boolean.TRUE.equals(cap.getSupported());
        }
        ScraperSystem system = scraperSystemMapper.selectBySystemId(systemId);
        if (system == null) {
            return false;
        }
        return formatRegistry.applies(spec, systemId, genericEligible(system, rules()),
                FormatRegistry.parseExtensions(system.getExtensions())) && spec.defaultEnabled();
    }

    @Override
    public List<String> getSupportedFormats(Integer systemId) {
        List<String> formats = new ArrayList<>();
        for (String format : formatRegistry.ids()) {
            if (isSupported(systemId, format)) {
                formats.add(format);
            }
        }
        return formats;
    }

    @Override
    public String getPreferredFormat(Integer systemId) {
        if (systemId == null) {
            return FORMAT_ORIGINAL;
        }
        SystemVariantPreference preference = preferenceMapper.selectBySystemId(systemId);
        String preferred = preference != null ? preference.getPreferredFormat() : null;
        if (preferred == null || preferred.isBlank()) {
            return FORMAT_ORIGINAL;
        }
        preferred = lower(preferred);
        if (FORMAT_ORIGINAL.equals(preferred) || isSupported(systemId, preferred)) {
            return preferred;
        }
        return FORMAT_ORIGINAL;
    }

    @Override
    public void savePreference(Integer systemId, String format) {
        if (systemId == null || format == null) {
            return;
        }
        String normalized = lower(format);
        if (!FORMAT_ORIGINAL.equals(normalized) && formatRegistry.byId(normalized) == null) {
            return;
        }
        SystemVariantPreference preference = new SystemVariantPreference();
        preference.setSystemId(systemId);
        preference.setPreferredFormat(normalized);
        preferenceMapper.upsert(preference);
    }

    @Override
    public void updateCapability(Integer systemId, String format, boolean supported) {
        if (systemId == null || format == null) {
            return;
        }
        String normalized = lower(format);
        if (formatRegistry.byId(normalized) == null) {
            return;
        }
        SystemVariantCapability cap = new SystemVariantCapability();
        cap.setSystemId(systemId);
        cap.setFormat(normalized);
        cap.setSupported(supported);
        cap.setSource("manual");
        cap.setNote("manual");
        capabilityMapper.upsert(cap);

        if (!supported && normalized.equals(getPreferredFormat(systemId))) {
            savePreference(systemId, FORMAT_ORIGINAL);
        }
        logger.info("副本能力手动更新: systemId={}, format={}, supported={}", systemId, normalized, supported);
    }

    /** 当前系统适用的格式及其开关状态（只包含适用格式，不拿全局格式往所有系统上套） */
    private Map<String, Boolean> applicableFormats(ScraperSystem system, List<SystemVariantCapability> caps) {
        Map<String, Boolean> formats = new LinkedHashMap<>();
        if (system == null || system.getSystemId() == null) {
            return formats;
        }
        Set<String> extensions = FormatRegistry.parseExtensions(system.getExtensions());
        boolean genericEligible = genericEligible(system, rules());
        for (FormatRegistry.Format format : formatRegistry.all()) {
            if (!formatRegistry.applies(format, system.getSystemId(), genericEligible, extensions)) {
                continue;
            }
            formats.put(format.id(), supportedFromCaps(caps, format.id(), format.defaultEnabled()));
        }
        return formats;
    }

    private boolean supportedFromCaps(List<SystemVariantCapability> caps, String format, boolean defaultValue) {
        for (SystemVariantCapability cap : caps) {
            if (format.equalsIgnoreCase(cap.getFormat())) {
                return Boolean.TRUE.equals(cap.getSupported());
            }
        }
        return defaultValue;
    }

    private String normalizePreference(String preferred, Map<String, Boolean> formats) {
        if (preferred == null || preferred.isBlank()) {
            return FORMAT_ORIGINAL;
        }
        String normalized = lower(preferred);
        if (FORMAT_ORIGINAL.equals(normalized)) {
            return normalized;
        }
        return Boolean.TRUE.equals(formats.get(normalized)) ? normalized : FORMAT_ORIGINAL;
    }

    private Set<String> parseExtensions(String raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.toLowerCase().split("[,;\\s|/]+")) {
            String extension = part.replace(".", "").trim();
            if (!extension.isEmpty()) {
                result.add(extension);
            }
        }
        return result;
    }

    private CapabilityRules rules() {
        CapabilityRules current = cachedRules;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (cachedRules == null) {
                cachedRules = loadRules();
            }
            return cachedRules;
        }
    }

    private CapabilityRules loadRules() {
        CapabilityRules rules = CapabilityRules.defaults();
        ClassPathResource resource = new ClassPathResource(RULES_PATH);
        if (!resource.exists()) {
            logger.warn("副本能力规则不存在，使用内置默认值: {}", RULES_PATH);
            return rules;
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            rules.eligibleTypes = readStringSet(root, "eligibleTypes", rules.eligibleTypes);
            rules.excludeRomTypes = readStringSet(root, "excludeRomTypes", rules.excludeRomTypes);
            rules.eligibleExtensions = readStringSet(root, "eligibleExtensions", rules.eligibleExtensions);
        } catch (Exception e) {
            logger.warn("读取副本能力规则失败，使用内置默认值: {}", e.getMessage());
        }
        return rules;
    }

    private Set<String> readStringSet(JsonNode root, String field, Set<String> fallback) {
        List<String> values = readStringList(root, field, new ArrayList<>(fallback));
        return new LinkedHashSet<>(values);
    }

    private List<String> readStringList(JsonNode root, String field, List<String> fallback) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && !item.asText().isBlank()) {
                values.add(lower(item.asText()));
            }
        }
        return values;
    }

    private String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private static final class CapabilityRules {
        private Set<String> eligibleTypes;
        private Set<String> excludeRomTypes;
        private Set<String> eligibleExtensions;

        private static CapabilityRules defaults() {
            CapabilityRules rules = new CapabilityRules();
            rules.eligibleTypes = new LinkedHashSet<>(List.of("console", "handheld"));
            rules.excludeRomTypes = new LinkedHashSet<>(List.of(
                    "iso", "cue", "cd", "dvd", "hdd", "disk", "optical", "tape"));
            rules.eligibleExtensions = new LinkedHashSet<>(List.of(
                    "nes", "fds", "unf", "unif", "smc", "sfc", "swc", "fig",
                    "gb", "gbc", "gba", "agb", "sms", "gg", "md", "gen", "bin", "32x", "sg",
                    "pce", "ngp", "ngc", "ws", "wsc", "a26", "a52", "a78", "lnx", "col",
                    "int", "vec", "o2", "chf", "msx", "rom", "n64", "z64", "v64", "nds",
                    "j64", "vb", "min"));
            return rules;
        }
    }
}
