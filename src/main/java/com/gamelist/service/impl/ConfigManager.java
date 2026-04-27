package com.gamelist.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelist.util.PathUtil;

public class ConfigManager {
    private static final String CONFIG_FILE_PATH = PathUtil.getRulesPath() + "/translation-config.json";
    private static ConfigManager instance;
    private JsonNode config;
    private Map<String, String> variables;
    
    private ConfigManager() {
        loadConfig();
    }
    
    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }
    
    private void loadConfig() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            File configFile = new File(CONFIG_FILE_PATH);
            
            if (configFile.exists()) {
                config = objectMapper.readTree(configFile);
            } else {
                // 创建默认配置
                createDefaultConfig();
            }
            
            // 加载变量
            loadVariables();
            
        } catch (IOException e) {
            e.printStackTrace();
            // 创建默认配置
            createDefaultConfig();
        }
    }
    
    private void createDefaultConfig() {
        // 这里可以创建默认的配置文件
        // 暂时使用内存中的默认配置
        try {
            String defaultConfig = "{" +
                "\"variables\": {" +
                "\"google_api_key\": \"\"," +
                "\"microsoft_api_key\": \"\"," +
                "\"microsoft_region\": \"eastasia\"," +
                "\"deepseek_api_key\": \"\"" +
                "}," +
                "\"translation\": {" +
                "\"services\": [" +
                "{" +
                "\"id\": \"google\"," +
                "\"name\": \"Google Translate\"," +
                "\"type\": \"google\"," +
                "\"url\": \"https://translation.googleapis.com/language/translate/v2\"," +
                "\"method\": \"POST\"," +
                "\"headers\": {" +
                "\"Content-Type\": \"application/json\"" +
                "}," +
                "\"requestBody\": {" +
                "\"q\": \"{text}\"," +
                "\"source\": \"{sourceLang}\"," +
                "\"target\": \"{targetLang}\"," +
                "\"format\": \"text\"," +
                "\"key\": \"${google_api_key}\"" +
                "}," +
                "\"responsePath\": \"data.translations[0].translatedText\"," +
                "\"validation\": {" +
                "\"requiredVariables\": [\"google_api_key\"]" +
                "}" +
                "}," +
                "{" +
                "\"id\": \"microsoft\"," +
                "\"name\": \"Microsoft Translator\"," +
                "\"type\": \"microsoft\"," +
                "\"url\": \"https://${microsoft_region}.api.cognitive.microsoft.com/translate\"," +
                "\"method\": \"POST\"," +
                "\"headers\": {" +
                "\"Content-Type\": \"application/json\"," +
                "\"Ocp-Apim-Subscription-Key\": \"${microsoft_api_key}\"" +
                "}," +
                "\"requestBody\": [" +
                "{" +
                "\"text\": \"{text}\"" +
                "}" +
                "]," +
                "\"params\": {" +
                "\"api-version\": \"3.0\"," +
                "\"from\": \"{sourceLang}\"," +
                "\"to\": \"{targetLang}\"" +
                "}," +
                "\"responsePath\": \"[0].translations[0].text\"," +
                "\"validation\": {" +
                "\"requiredVariables\": [\"microsoft_api_key\", \"microsoft_region\"]" +
                "}" +
                "}," +
                "{" +
                "\"id\": \"deepseek\"," +
                "\"name\": \"DeepSeek\"," +
                "\"type\": \"deepseek\"," +
                "\"url\": \"https://api.deepseek.com/chat/completions\"," +
                "\"method\": \"POST\"," +
                "\"headers\": {" +
                "\"Content-Type\": \"application/json\"," +
                "\"Authorization\": \"Bearer ${deepseek_api_key}\"" +
                "}," +
                "\"requestBody\": {" +
                "\"model\": \"deepseek-chat\"," +
                "\"messages\": [" +
                "{" +
                "\"role\": \"system\"," +
                "\"content\": \"You are a helpful assistant. Please translate the following text from {sourceLang} to {targetLang}.\"" +
                "}," +
                "{" +
                "\"role\": \"user\"," +
                "\"content\": \"{text}\"" +
                "}" +
                "]," +
                "\"stream\": false" +
                "}," +
                "\"responsePath\": \"choices[0].message.content\"," +
                "\"validation\": {" +
                "\"requiredVariables\": [\"deepseek_api_key\"]" +
                "}" +
                "}" +
                "]," +
                "\"defaultService\": \"google\"" +
                "}" +
                "}";
            
            ObjectMapper objectMapper = new ObjectMapper();
            config = objectMapper.readTree(defaultConfig);
            loadVariables();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void loadVariables() {
        variables = new HashMap<>();
        if (config != null && config.has("variables")) {
            JsonNode variablesNode = config.get("variables");
            Iterator<String> fieldNames = variablesNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                variables.put(fieldName, variablesNode.get(fieldName).asText());
            }
        }
    }
    
    public JsonNode getConfig() {
        return config;
    }
    
    public Map<String, String> getVariables() {
        return variables;
    }
    
    public JsonNode getServiceConfig(String serviceId) {
        if (config != null && config.has("translation") && config.get("translation").has("services")) {
            JsonNode servicesNode = config.get("translation").get("services");
            for (JsonNode serviceNode : servicesNode) {
                if (serviceNode.has("id") && serviceId.equals(serviceNode.get("id").asText())) {
                    return serviceNode;
                }
            }
        }
        return null;
    }
    
    public JsonNode getDefaultService() {
        if (config != null && config.has("translation") && config.get("translation").has("defaultService")) {
            String defaultServiceId = config.get("translation").get("defaultService").asText();
            return getServiceConfig(defaultServiceId);
        }
        return null;
    }
    
    public void reloadConfig() {
        loadConfig();
    }
}