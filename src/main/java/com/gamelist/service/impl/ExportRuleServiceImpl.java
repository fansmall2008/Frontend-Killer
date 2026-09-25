package com.gamelist.service.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelist.model.TemplateV3;
import com.gamelist.service.ExportRuleService;

@Service
public class ExportRuleServiceImpl implements ExportRuleService {
    private static final Logger logger = LoggerFactory.getLogger(ExportRuleServiceImpl.class);
    private final Map<String, TemplateV3> v3Rules = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${app.export.rules.path:#{T(com.gamelist.util.PathUtil).getRulesPath() + '/export'}}")
    private String rulesPath;

    @Value("${app.rules.directory:#{T(com.gamelist.util.PathUtil).getRulesPath()}}")
    private String rulesDirectory;

    @PostConstruct
    @Override
    public void loadRules() {
        logger.info("=== ExportRuleService loadRules() called ===");
        logger.info("rulesPath value: {}", rulesPath);
        logger.info("rulesDirectory value: {}", rulesDirectory);

        // 清空旧数据，确保完全重载
        v3Rules.clear();

        // 只从外部路径（data/rules/export/）加载规则
        File externalRulesDir = new File(rulesPath);
        logger.info("Checking externalRulesDir: {}, exists: {}, isDirectory: {}",
                    externalRulesDir.getAbsolutePath(),
                    externalRulesDir.exists(),
                    externalRulesDir.isDirectory());

        if (externalRulesDir.exists() && externalRulesDir.isDirectory()) {
            logger.info("Loading export rules from external path: {}", rulesPath);
            boolean loaded = loadRulesFromDirectory(externalRulesDir.toPath());
            logger.info("loadRulesFromDirectory returned: {}, v3 rules size: {}", loaded, v3Rules.size());
        } else {
            logger.warn("External rules directory does not exist or is not a directory: {}", externalRulesDir.getAbsolutePath());
        }

        if (v3Rules.isEmpty()) {
            logger.warn("No export rules loaded. Final v3 rules size: {}", v3Rules.size());
        } else {
            logger.info("Export rules loaded successfully. Total v3 rules: {}", v3Rules.size());
        }
    }
    
    private boolean loadRulesFromDirectory(Path rulesDirPath) {
        try {
            logger.info("Scanning directory for rules: {}", rulesDirPath);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(rulesDirPath, "*.json")) {
                int loadedCount = 0;
                for (Path path : stream) {
                    String fileName = path.getFileName().toString();
                    logger.info("Processing rule file: {}", fileName);

                    // 尝试加载 v3 模板
                    TemplateV3 v3Template = TemplateV3.loadFromFile(path.toFile());
                    if (v3Template != null && v3Template.getTemplateInfo() != null
                            && v3Template.getTemplateInfo().isExport()) {
                        String frontend = extractFrontendFromFilename(fileName);
                        v3Rules.put(frontend, v3Template);
                        logger.info("Loaded v3 export template: {} -> {}", fileName, frontend);
                        loadedCount++;
                    } else {
                        logger.debug("Skipping non-v3 or non-export template: {}", fileName);
                    }
                }
                logger.info("Total v3 rules loaded: {}", v3Rules.size());
            }
            return !v3Rules.isEmpty();
        } catch (IOException e) {
            logger.error("Error loading rules from directory: {}", rulesDirPath, e);
            return false;
        }
    }

    /**
     * 从文件名提取 frontend key。
     * 例如: "pegasus-v3.json" → "pegasus", "esde-v3.json" → "esde"
     */
    private String extractFrontendFromFilename(String fileName) {
        String name = fileName;
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - 5);
        }
        if (name.endsWith("-v3")) {
            name = name.substring(0, name.length() - 3);
        }
        return name;
    }

    // ==================== v3 模板支持 ====================

    @Override
    public TemplateV3 getV3RuleByFrontend(String frontend) {
        return v3Rules.get(frontend);
    }

    @Override
    public List<TemplateV3> getV3RuleList() {
        return new ArrayList<>(v3Rules.values());
    }

    @Override
    public boolean isV3Template(String frontend) {
        return v3Rules.containsKey(frontend);
    }

    @Override
    public Set<String> getV3FrontendKeys() {
        return Collections.unmodifiableSet(v3Rules.keySet());
    }
}
