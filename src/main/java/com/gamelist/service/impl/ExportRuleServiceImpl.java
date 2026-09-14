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
import com.gamelist.model.ExportRule;
import com.gamelist.model.TemplateV3;
import com.gamelist.service.ExportRuleService;

@Service
public class ExportRuleServiceImpl implements ExportRuleService {
    private static final Logger logger = LoggerFactory.getLogger(ExportRuleServiceImpl.class);
    private final Map<String, ExportRule> rules = new HashMap<>();
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

        // 只从外部路径（data/rules/export/）加载规则
        File externalRulesDir = new File(rulesPath);
        logger.info("Checking externalRulesDir: {}, exists: {}, isDirectory: {}",
                    externalRulesDir.getAbsolutePath(),
                    externalRulesDir.exists(),
                    externalRulesDir.isDirectory());

        if (externalRulesDir.exists() && externalRulesDir.isDirectory()) {
            logger.info("Loading export rules from external path: {}", rulesPath);
            boolean loaded = loadRulesFromDirectory(externalRulesDir.toPath());
            logger.info("loadRulesFromDirectory returned: {}, rules size: {}", loaded, rules.size());
        } else {
            logger.warn("External rules directory does not exist or is not a directory: {}", externalRulesDir.getAbsolutePath());
        }

        if (rules.isEmpty()) {
            logger.warn("No export rules loaded. Final rules size: {}", rules.size());
        } else {
            logger.info("Export rules loaded successfully. Total rules: {}", rules.size());
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

                    // 优先尝试加载 v3 模板
                    TemplateV3 v3Template = TemplateV3.loadFromFile(path.toFile());
                    if (v3Template != null && v3Template.getTemplateInfo() != null
                            && v3Template.getTemplateInfo().isExport()) {
                        // v3 导出模板：从文件名提取 frontend key（去掉 -v3 后缀）
                        String frontend = extractFrontendFromFilename(fileName);
                        v3Rules.put(frontend, v3Template);
                        logger.info("Loaded v3 export template: {} -> {}", fileName, frontend);
                        loadedCount++;
                        continue;
                    }

                    // 回退到 v2 模板加载
                    try (InputStream is = Files.newInputStream(path)) {
                        ExportRule rule = objectMapper.readValue(is, ExportRule.class);
                        if (rule != null && rule.getFrontend() != null) {
                            rules.put(rule.getFrontend(), rule);
                            logger.info("Loaded v2 export rule: {} -> {}", fileName, rule.getFrontend());
                            loadedCount++;
                        } else {
                            logger.warn("Rule file {} has null frontend, skipping", fileName);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to parse rule file: {} - {}", fileName, e.getMessage());
                    }
                }
                logger.info("Total rules loaded: v2={}, v3={}", rules.size(), v3Rules.size());
            }
            return !rules.isEmpty() || !v3Rules.isEmpty();
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
    
    private boolean loadRulesFromClasspath() {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            URL rulesUrl = classLoader.getResource("export-rules");
            
            if (rulesUrl != null) {
                logger.info("Rules directory found in classpath at: {}", rulesUrl);
                logger.info("Protocol: {}", rulesUrl.getProtocol());
                
                if (rulesUrl.getProtocol().equals("file")) {
                    try {
                        Path rulesPath = Paths.get(rulesUrl.toURI());
                        return loadRulesFromDirectory(rulesPath);
                    } catch (URISyntaxException e) {
                        logger.error("Error converting rules URL to path", e);
                    }
                } else if (rulesUrl.getProtocol().equals("jar")) {
                    String[] ruleFiles = {"esde.json", "pegasus.json", "retrobat.json", "lakka.json", "emuelec.json", "template.json"};
                    for (String fileName : ruleFiles) {
                        String resourcePath = "export-rules/" + fileName;
                        URL resourceUrl = classLoader.getResource(resourcePath);
                        if (resourceUrl != null) {
                            try (InputStream is = resourceUrl.openStream()) {
                                ExportRule rule = objectMapper.readValue(is, ExportRule.class);
                                if (rule != null && rule.getFrontend() != null) {
                                    rules.put(rule.getFrontend(), rule);
                                    logger.info("Loaded export rule from classpath: {}", fileName);
                                }
                            } catch (IOException e) {
                                logger.error("Failed to load rule file from classpath: {}", fileName, e);
                            }
                        }
                    }
                    return !rules.isEmpty();
                }
            } else {
                logger.warn("Rules directory not found in classpath: export-rules");
            }
        } catch (Exception e) {
            logger.error("Error loading export rules from classpath", e);
        }
        return false;
    }

    @Override
    public Map<String, ExportRule> getRules() {
        return Collections.unmodifiableMap(rules);
    }

    @Override
    public ExportRule getRuleByFrontend(String frontend) {
        return rules.get(frontend);
    }

    @Override
    public List<ExportRule> getRuleList() {
        return new ArrayList<>(rules.values());
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
