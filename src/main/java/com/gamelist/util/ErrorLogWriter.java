package com.gamelist.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 错误日志写入工具类
 * 将导入/导出过程中有问题的游戏信息写入单独的日志文件
 */
public class ErrorLogWriter {
    
    private static final String LOG_DIR = "./logs";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 写入导入错误日志
     * @param platformName 平台名称
     * @param skippedGames 被跳过的游戏列表（路径列表）
     * @param failedGames 转换失败的游戏列表（名称列表）
     * @param nullPathGames 路径为空的游戏列表（名称列表）
     */
    public static void writeImportErrorLog(String platformName, 
                                           List<String> skippedGames,
                                           List<String> failedGames,
                                           List<String> nullPathGames) throws IOException {
        // 如果没有问题游戏，不需要写入日志
        if (skippedGames.isEmpty() && failedGames.isEmpty() && nullPathGames.isEmpty()) {
            return;
        }
        
        String fileName = generateFileName("import", platformName);
        Path filePath = getLogFilePath(fileName);
        
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write("========================================");
            writer.newLine();
            writer.write("导入错误日志 - " + platformName);
            writer.newLine();
            writer.write("生成时间: " + LocalDateTime.now().format(TIMESTAMP_FORMATTER));
            writer.newLine();
            writer.write("========================================");
            writer.newLine();
            writer.newLine();
            
            // 记录被跳过的游戏（已存在）
            if (!skippedGames.isEmpty()) {
                writer.write("【被跳过的游戏 - 路径已存在】");
                writer.newLine();
                writer.write("数量: " + skippedGames.size());
                writer.newLine();
                writer.write("----------------------------------------");
                writer.newLine();
                for (String path : skippedGames) {
                    writer.write("- " + path);
                    writer.newLine();
                }
                writer.newLine();
            }
            
            // 记录转换失败的游戏
            if (!failedGames.isEmpty()) {
                writer.write("【转换失败的游戏】");
                writer.newLine();
                writer.write("数量: " + failedGames.size());
                writer.newLine();
                writer.write("----------------------------------------");
                writer.newLine();
                for (String name : failedGames) {
                    writer.write("- " + name);
                    writer.newLine();
                }
                writer.newLine();
            }
            
            // 记录路径为空的游戏
            if (!nullPathGames.isEmpty()) {
                writer.write("【路径为空的游戏】");
                writer.newLine();
                writer.write("数量: " + nullPathGames.size());
                writer.newLine();
                writer.write("----------------------------------------");
                writer.newLine();
                for (String name : nullPathGames) {
                    writer.write("- " + name);
                    writer.newLine();
                }
                writer.newLine();
            }
            
            writer.write("========================================");
            writer.newLine();
            writer.write("日志结束");
            writer.newLine();
        }
    }
    
    /**
     * 写入导出错误日志
     * @param platformName 平台名称
     * @param failedGames 导出失败的游戏列表
     * @param missingFiles 缺失文件的游戏列表
     */
    public static void writeExportErrorLog(String platformName,
                                           List<String> failedGames,
                                           List<String> missingFiles) throws IOException {
        // 如果没有问题游戏，不需要写入日志
        if (failedGames.isEmpty() && missingFiles.isEmpty()) {
            return;
        }
        
        String fileName = generateFileName("export", platformName);
        Path filePath = getLogFilePath(fileName);
        
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write("========================================");
            writer.newLine();
            writer.write("导出错误日志 - " + platformName);
            writer.newLine();
            writer.write("生成时间: " + LocalDateTime.now().format(TIMESTAMP_FORMATTER));
            writer.newLine();
            writer.write("========================================");
            writer.newLine();
            writer.newLine();
            
            // 记录导出失败的游戏
            if (!failedGames.isEmpty()) {
                writer.write("【导出失败的游戏】");
                writer.newLine();
                writer.write("数量: " + failedGames.size());
                writer.newLine();
                writer.write("----------------------------------------");
                writer.newLine();
                for (String name : failedGames) {
                    writer.write("- " + name);
                    writer.newLine();
                }
                writer.newLine();
            }
            
            // 记录缺失文件的游戏
            if (!missingFiles.isEmpty()) {
                writer.write("【缺失文件的游戏】");
                writer.newLine();
                writer.write("数量: " + missingFiles.size());
                writer.newLine();
                writer.write("----------------------------------------");
                writer.newLine();
                for (String path : missingFiles) {
                    writer.write("- " + path);
                    writer.newLine();
                }
                writer.newLine();
            }
            
            writer.write("========================================");
            writer.newLine();
            writer.write("日志结束");
            writer.newLine();
        }
    }
    
    /**
     * 生成日志文件名
     */
    private static String generateFileName(String operationType, String platformName) {
        String sanitizedPlatformName = platformName.replaceAll("[^a-zA-Z0-9\\-_]", "_");
        String dateStr = LocalDateTime.now().format(DATE_FORMATTER);
        return String.format("errolog-%s-%s-%s.log", operationType, sanitizedPlatformName, dateStr);
    }
    
    /**
     * 获取日志文件路径
     */
    private static Path getLogFilePath(String fileName) throws IOException {
        Path logDir = Paths.get(LOG_DIR);
        if (!Files.exists(logDir)) {
            Files.createDirectories(logDir);
        }
        return logDir.resolve(fileName);
    }
}