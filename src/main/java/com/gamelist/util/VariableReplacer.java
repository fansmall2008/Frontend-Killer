package com.gamelist.util;

import java.util.Map;

import com.gamelist.model.Platform;

public class VariableReplacer {
    
    /**
     * 替换模板中的变量
     * @param template 模板字符串
     * @param variables 环境变量映射
     * @param platform 平台信息
     * @param platformFields 平台字段映射
     * @return 替换后的字符串
     */
    public static String replaceVariables(String template, Map<String, String> variables, Platform platform, Map<String, String> platformFields) {
        String result = template;
        
        // 替换环境变量
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String value = entry.getValue();
                if (value != null) {
                    result = result.replace("{" + entry.getKey() + "}", value);
                }
            }
        }
        
        // 替换平台变量
        if (platform != null && platformFields != null) {
            for (Map.Entry<String, String> entry : platformFields.entrySet()) {
                String variable = entry.getKey();
                String fieldName = entry.getValue();
                String value = getPlatformFieldValue(platform, fieldName);
                if (value != null) {
                    result = result.replace("{" + variable + "}", value);
                }
            }
        }
        
        return result;
    }
    
    /**
     * 替换模板中的变量（仅环境变量）
     * @param template 模板字符串
     * @param variables 变量映射
     * @return 替换后的字符串
     */
    public static String replaceVariables(String template, Map<String, String> variables) {
        String result = template;
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String value = entry.getValue();
                if (value != null) {
                    result = result.replace("{" + entry.getKey() + "}", value);
                }
            }
        }
        return result;
    }
    
    /**
     * 获取平台字段值
     * @param platform 平台信息
     * @param fieldName 字段名称
     * @return 字段值
     */
    private static String getPlatformFieldValue(Platform platform, String fieldName) {
        switch (fieldName) {
            case "system":
                return platform.getSystem();
            case "name":
                return platform.getName();
            case "launch":
                return platform.getLaunch();
            case "software":
                return platform.getSoftware();
            case "database":
                return platform.getDatabase();
            case "web":
                return platform.getWeb();
            case "folderPath":
                return platform.getFolderPath();
            default:
                return null;
        }
    }
}
