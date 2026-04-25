package com.gamelist.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.gamelist.model.ExportRule;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;

public interface DataFileGenerator {
    
    /**
     * 生成数据文件
     * @param games 游戏列表
     * @param dataFilePath 数据文件路径
     * @param rule 导出规则
     * @param platform 平台信息
     * @param variables 变量映射
     * @throws Exception 生成过程中的异常
     */
    void generateDataFile(List<Game> games, Path dataFilePath, ExportRule rule, Platform platform, Map<String, String> variables) throws Exception;
    
    /**
     * 生成表头
     * @param content 内容构建器
     * @param rule 导出规则
     * @param platform 平台信息
     * @param variables 变量映射
     */
    void generateHeader(StringBuilder content, ExportRule rule, Platform platform, Map<String, String> variables);
    
    /**
     * 生成游戏条目
     * @param content 内容构建器
     * @param game 游戏信息
     * @param rule 导出规则
     * @param platform 平台信息
     * @param variables 变量映射
     * @param basePath 基础路径（用于计算相对路径）
     */
    void generateGameEntry(StringBuilder content, Game game, ExportRule rule, Platform platform, Map<String, String> variables, Path basePath);
    
    /**
     * 生成尾部
     * @param content 内容构建器
     * @param rule 导出规则
     * @param platform 平台信息
     * @param variables 变量映射
     */
    void generateFooter(StringBuilder content, ExportRule rule, Platform platform, Map<String, String> variables);
}
