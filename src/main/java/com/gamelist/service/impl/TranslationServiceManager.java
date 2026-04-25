package com.gamelist.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamelist.service.TranslationService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TranslationServiceManager {
    private static TranslationServiceManager instance;
    private ConfigManager configManager;
    private Map<String, TranslationService> serviceCache;
    
    private TranslationServiceManager() {
        configManager = ConfigManager.getInstance();
        serviceCache = new HashMap<>();
    }
    
    public static TranslationServiceManager getInstance() {
        if (instance == null) {
            instance = new TranslationServiceManager();
        }
        return instance;
    }
    
    public TranslationService getTranslationService(String serviceId) {
        if (serviceCache.containsKey(serviceId)) {
            return serviceCache.get(serviceId);
        }
        
        JsonNode serviceConfig = configManager.getServiceConfig(serviceId);
        if (serviceConfig == null) {
            return null;
        }
        
        TranslationService service = new GenericTranslationService(serviceConfig, configManager.getVariables());
        serviceCache.put(serviceId, service);
        return service;
    }
    
    public List<Map<String, Object>> getValidServices() {
        List<Map<String, Object>> validServices = new ArrayList<>();
        
        if (configManager.getConfig() != null && configManager.getConfig().has("translation") && 
            configManager.getConfig().get("translation").has("services")) {
            
            JsonNode servicesNode = configManager.getConfig().get("translation").get("services");
            for (JsonNode serviceNode : servicesNode) {
                if (isServiceValid(serviceNode)) {
                    Map<String, Object> serviceInfo = new HashMap<>();
                    serviceInfo.put("id", serviceNode.get("id").asText());
                    serviceInfo.put("name", serviceNode.get("name").asText());
                    serviceInfo.put("valid", true);
                    validServices.add(serviceInfo);
                }
            }
        }
        
        return validServices;
    }
    
    public boolean isServiceValid(JsonNode serviceNode) {
        if (!serviceNode.has("validation")) {
            return true;
        }
        
        JsonNode validationNode = serviceNode.get("validation");
        if (validationNode.has("requiredVariables")) {
            JsonNode requiredVariablesNode = validationNode.get("requiredVariables");
            Map<String, String> variables = configManager.getVariables();
            
            for (JsonNode variableNode : requiredVariablesNode) {
                String variableName = variableNode.asText();
                if (!variables.containsKey(variableName) || variables.get(variableName) == null || variables.get(variableName).isEmpty()) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    public Map<String, Object> validateService(String serviceId) {
        Map<String, Object> result = new HashMap<>();
        JsonNode serviceConfig = configManager.getServiceConfig(serviceId);
        
        if (serviceConfig == null) {
            result.put("valid", false);
            result.put("error", "Service not found");
            return result;
        }
        
        if (isServiceValid(serviceConfig)) {
            result.put("valid", true);
            result.put("error", null);
        } else {
            result.put("valid", false);
            result.put("error", "Missing required variables");
        }
        
        return result;
    }
    
    public void reloadServices() {
        configManager.reloadConfig();
        serviceCache.clear();
    }
}