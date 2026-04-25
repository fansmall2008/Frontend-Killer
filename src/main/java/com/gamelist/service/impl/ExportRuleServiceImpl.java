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

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelist.model.ExportRule;
import com.gamelist.service.ExportRuleService;

@Service
public class ExportRuleServiceImpl implements ExportRuleService {
    private static final Logger logger = LoggerFactory.getLogger(ExportRuleServiceImpl.class);
    private final Map<String, ExportRule> rules = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${app.export.rules.path:/data/rules/export-rules}")
    private String rulesPath;
    
    @Value("${app.rules.directory:/data/rules}")
    private String rulesDirectory;

    @PostConstruct
    @Override
    public void loadRules() {
        boolean loaded = false;
        
        // 首先尝试从外部路径加载
        File externalRulesDir = new File(rulesPath);
        if (externalRulesDir.exists() && externalRulesDir.isDirectory()) {
            logger.info("Loading export rules from external path: {}", rulesPath);
            loaded = loadRulesFromDirectory(externalRulesDir.toPath());
        }
        
        // 如果外部路径没有找到规则文件，尝试从classpath加载
        if (!loaded) {
            logger.info("Trying to load export rules from classpath");
            loaded = loadRulesFromClasspath();
        }
        
        if (!loaded) {
            logger.warn("No export rules loaded from any source");
        }
    }
    
    private boolean loadRulesFromDirectory(Path rulesDirPath) {
        try {
            logger.info("Scanning directory for rules: {}", rulesDirPath);
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(rulesDirPath, "*.json")) {
                for (Path path : stream) {
                    try (InputStream is = Files.newInputStream(path)) {
                        ExportRule rule = objectMapper.readValue(is, ExportRule.class);
                        if (rule != null && rule.getFrontend() != null) {
                            rules.put(rule.getFrontend(), rule);
                            logger.info("Loaded export rule from file: {} -> {}", path.getFileName(), rule.getFrontend());
                        }
                    } catch (IOException e) {
                        logger.error("Failed to load rule file: {}", path.getFileName(), e);
                    }
                }
            }
            return !rules.isEmpty();
        } catch (IOException e) {
            logger.error("Error loading rules from directory: {}", rulesDirPath, e);
            return false;
        }
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
                    String[] ruleFiles = {"esde.json", "pegasus.json", "retrobat.json", "template.json"};
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
}
