package com.gamelist.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用数据文件解析结果。
 * <p>
 * 无论输入是 XML、JSON 还是纯文本，解析器都将其统一转换为这个结构：
 * <ul>
 *   <li><b>systemFields</b> — 系统级 key-value 对（平台名、启动命令等）</li>
 *   <li><b>games</b> — 游戏列表，每个游戏是一组 key-value 对</li>
 * </ul>
 * 后续的模板映射层再根据模板的 fieldMappings / mediaMappings 将这些 key-value 映射到数据库列。
 */
public class ParsedDataFile {

    /** 系统级字段（只出现一次的信息：平台名、启动命令、扩展名列表等） */
    private Map<String, String> systemFields = new LinkedHashMap<>();

    /** 游戏列表，每个游戏是一组 key-value 对 */
    private List<Map<String, String>> games = new ArrayList<>();

    // ==================== 系统字段 ====================

    public Map<String, String> getSystemFields() {
        return systemFields;
    }

    public void setSystemFields(Map<String, String> systemFields) {
        this.systemFields = systemFields;
    }

    public String getSystemField(String key) {
        return systemFields != null ? systemFields.get(key) : null;
    }

    public void putSystemField(String key, String value) {
        systemFields.put(key, value);
    }

    // ==================== 游戏列表 ====================

    public List<Map<String, String>> getGames() {
        return games;
    }

    public void setGames(List<Map<String, String>> games) {
        this.games = games;
    }

    public void addGame(Map<String, String> gameFields) {
        games.add(gameFields);
    }

    public int getGameCount() {
        return games.size();
    }

    /**
     * 获取所有游戏中出现过的 key 集合（用于自动发现"表头"）。
     */
    public List<String> getAllKeys() {
        List<String> keys = new ArrayList<>();
        for (Map<String, String> game : games) {
            for (String key : game.keySet()) {
                if (!keys.contains(key)) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }
}
