package com.gamelist.service.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.gamelist.mapper.TermMappingMapper;
import com.gamelist.model.Platform;
import com.gamelist.model.TermMapping;
import com.gamelist.service.ScraperSystemService;
import com.gamelist.service.SystemMatchService;
import com.gamelist.util.SystemMatcher;
import com.gamelist.util.SystemMatcher.MatchResult;

/**
 * 平台系统匹配引导实现（TODO #9）。
 * 匹配算法在 {@link SystemMatcher}（纯函数），本类负责取数：输入词、方言映射表、扩展名信号。
 */
@Service
public class SystemMatchServiceImpl implements SystemMatchService {

    private static final Logger logger = LoggerFactory.getLogger(SystemMatchServiceImpl.class);

    /** 候选列表最多返回条数 */
    private static final int MAX_CANDIDATES = 5;

    /** 目录扫描上限：顶层最多检查文件数（NAS 友好，只列一层） */
    private static final int MAX_DIR_SCAN_FILES = 200;

    /** 系统别名映射分类（与 term_mapping 种子数据一致） */
    private static final String CATEGORY_SYSTEM_ALIAS = "system_alias";

    @Autowired
    private ScraperSystemService scraperSystemService;

    @Autowired
    private TermMappingMapper termMappingMapper;

    @Override
    public Map<String, Object> matchSystems(Platform platform) {
        Map<String, Object> result = new HashMap<>();
        if (platform == null) {
            result.put("success", false);
            result.put("message", "平台不存在");
            return result;
        }
        result.put("success", true);

        // 已绑定系统 → 前端无需弹窗
        if (platform.getSystemId() != null && platform.getSystemId() != 0) {
            result.put("bound", true);
            return result;
        }
        result.put("bound", false);

        // 1. 输入词：文件夹名（最可靠）> 平台名 > 系统名（剥 unknown_ 前缀）
        List<String> rawTerms = new ArrayList<>();
        String folderName = folderNameOf(platform.getFolderPath());
        if (!folderName.isEmpty()) {
            rawTerms.add(folderName);
        }
        if (notBlank(platform.getName()) && !rawTerms.contains(platform.getName())) {
            rawTerms.add(platform.getName());
        }
        String systemName = platform.getSystem();
        if (notBlank(systemName)) {
            String cleaned = systemName.startsWith("unknown_") ? systemName.substring("unknown_".length()) : systemName;
            if (!cleaned.trim().isEmpty() && !rawTerms.contains(cleaned)) {
                rawTerms.add(cleaned.trim());
            }
        }
        result.put("terms", rawTerms);

        // 2. 方言映射表（category=system_alias）→ 归一化 source → target
        Map<String, String> aliasMap = new HashMap<>();
        try {
            for (TermMapping m : termMappingMapper.selectByCategory(CATEGORY_SYSTEM_ALIAS)) {
                String key = SystemMatcher.normalize(m.getSourceTerm());
                if (!key.isEmpty()) {
                    aliasMap.putIfAbsent(key, m.getTargetTerm());
                }
            }
        } catch (Exception e) {
            logger.warn("加载系统别名映射表失败: {}", e.getMessage());
        }

        // 3. 平台扩展名信号（字段优先，目录扫描兜底，过滤通用扩展名）
        Set<String> platformExts = collectPlatformExtensions(platform);

        // 4. 匹配
        List<MatchResult> candidates = SystemMatcher.match(
                rawTerms, scraperSystemService.getAll(), platformExts, aliasMap);
        if (candidates.size() > MAX_CANDIDATES) {
            candidates = candidates.subList(0, MAX_CANDIDATES);
        }
        result.put("candidates", candidates);
        return result;
    }

    /** 取文件夹路径末段作为文件夹名 */
    private String folderNameOf(String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) {
            return "";
        }
        String norm = folderPath.replace('\\', '/');
        int idx = norm.lastIndexOf('/');
        String name = idx >= 0 ? norm.substring(idx + 1) : norm;
        return name.trim();
    }

    /**
     * 平台扩展名信号：优先 platform.extensions 字段，为空则扫描目录顶层文件（限一层、限 200 个）。
     * 过滤 zip/iso/bin 等通用扩展名（见 {@link SystemMatcher#GENERIC_EXTS}）。
     */
    private Set<String> collectPlatformExtensions(Platform platform) {
        Set<String> exts = new LinkedHashSet<>();
        String field = platform.getExtensions();
        if (notBlank(field)) {
            for (String e : SystemMatcher.splitExtensions(field)) {
                if (!SystemMatcher.GENERIC_EXTS.contains(e)) {
                    exts.add(e);
                }
            }
            return exts;
        }
        String folderPath = platform.getFolderPath();
        if (folderPath == null || folderPath.isEmpty()) {
            return exts;
        }
        try {
            File dir = new File(folderPath);
            if (!dir.isDirectory()) {
                return exts;
            }
            File[] files = dir.listFiles();
            if (files == null) {
                return exts;
            }
            int scanned = 0;
            for (File f : files) {
                if (scanned >= MAX_DIR_SCAN_FILES) {
                    break;
                }
                if (f.isDirectory()) {
                    continue;
                }
                String name = f.getName();
                int dot = name.lastIndexOf('.');
                if (dot <= 0 || dot == name.length() - 1) {
                    continue;
                }
                String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (!SystemMatcher.GENERIC_EXTS.contains(ext)) {
                    exts.add(ext);
                }
                scanned++;
            }
        } catch (Exception e) {
            logger.warn("扫描平台目录扩展名失败: folderPath={}, error={}", folderPath, e.getMessage());
        }
        return exts;
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
