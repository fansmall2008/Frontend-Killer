package com.gamelist.service;

/**
 * 系统设置（system_settings key/value 表）轻量读取服务。
 *
 * 现有 SettingController 直接用 JdbcTemplate 读写本表；此服务仅为后端各服务
 * 提供一处类型化、带默认值的读取入口，避免各处散拼 SQL。
 */
public interface SystemSettingsService {

    /** 读取字符串设置，缺省返回 defaultValue */
    String get(String key, String defaultValue);

    /** 读取布尔设置（"true"/"1"/"yes" 视为 true，大小写不敏感），缺省返回 defaultValue */
    boolean getBoolean(String key, boolean defaultValue);

    /** 写入/更新设置（存在则更新，不存在则插入） */
    void set(String key, String value);
}
