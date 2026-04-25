package com.gamelist.util;

public class LanguageDetector {
    
    // 判断字符串是否包含中文
    public static boolean containsChinese(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (char c : str.toCharArray()) {
            if (c >= '\u4E00' && c <= '\u9FA5') {
                return true;
            }
        }
        return false;
    }
    
    // 从文件路径中提取文件名（不含扩展名）
    public static String extractFileName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        
        // 处理不同操作系统的路径分隔符
        String separator = path.contains("\\") ? "\\" : "/";
        int lastSeparatorIndex = path.lastIndexOf(separator);
        String fileName = lastSeparatorIndex != -1 ? path.substring(lastSeparatorIndex + 1) : path;
        
        // 移除文件扩展名
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex != -1 ? fileName.substring(0, dotIndex) : fileName;
    }
}
