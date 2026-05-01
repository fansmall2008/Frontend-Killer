package com.gamelist.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏过滤结果类
 * 包含过滤后的新游戏列表以及被跳过/失败的游戏信息
 */
public class FilterResult {
    
    /**
     * 过滤后的新游戏列表
     */
    private List<Game> newGames;
    
    /**
     * 被跳过的游戏路径列表（已存在）
     */
    private List<String> skippedPaths;
    
    /**
     * 路径为空的游戏名称列表
     */
    private List<String> nullPathGames;
    
    public FilterResult() {
        this.newGames = new ArrayList<>();
        this.skippedPaths = new ArrayList<>();
        this.nullPathGames = new ArrayList<>();
    }
    
    public FilterResult(List<Game> newGames, List<String> skippedPaths, List<String> nullPathGames) {
        this.newGames = newGames != null ? newGames : new ArrayList<>();
        this.skippedPaths = skippedPaths != null ? skippedPaths : new ArrayList<>();
        this.nullPathGames = nullPathGames != null ? nullPathGames : new ArrayList<>();
    }
    
    public List<Game> getNewGames() {
        return newGames;
    }
    
    public void setNewGames(List<Game> newGames) {
        this.newGames = newGames;
    }
    
    public List<String> getSkippedPaths() {
        return skippedPaths;
    }
    
    public void setSkippedPaths(List<String> skippedPaths) {
        this.skippedPaths = skippedPaths;
    }
    
    public List<String> getNullPathGames() {
        return nullPathGames;
    }
    
    public void setNullPathGames(List<String> nullPathGames) {
        this.nullPathGames = nullPathGames;
    }
    
    public int getTotalSkipped() {
        return skippedPaths.size() + nullPathGames.size();
    }
}