package com.gamelist.service.impl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.gamelist.service.SystemSettingsService;

@Service
public class SystemSettingsServiceImpl implements SystemSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(SystemSettingsServiceImpl.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public String get(String key, String defaultValue) {
        try {
            String sql = "SELECT setting_value FROM system_settings WHERE setting_key = ?";
            List<String> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), key);
            if (!rows.isEmpty() && rows.get(0) != null) {
                return rows.get(0);
            }
        } catch (Exception e) {
            logger.debug("读取系统设置失败(用默认值): key={}, err={}", key, e.getMessage());
        }
        return defaultValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String v = get(key, null);
        if (v == null) {
            return defaultValue;
        }
        String t = v.trim().toLowerCase();
        if (t.isEmpty()) {
            return defaultValue;
        }
        return t.equals("true") || t.equals("1") || t.equals("yes") || t.equals("on");
    }

    @Override
    public void set(String key, String value) {
        try {
            String checkSql = "SELECT COUNT(*) FROM system_settings WHERE setting_key = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, key);
            if (count != null && count > 0) {
                String updateSql = "UPDATE system_settings SET setting_value = ?, updated_at = CURRENT_TIMESTAMP WHERE setting_key = ?";
                jdbcTemplate.update(updateSql, value, key);
            } else {
                String insertSql = "INSERT INTO system_settings (setting_key, setting_value, description, created_at, updated_at) "
                        + "VALUES (?, ?, '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
                jdbcTemplate.update(insertSql, key, value);
            }
        } catch (Exception e) {
            logger.error("写入系统设置失败: key={}, err={}", key, e.getMessage());
        }
    }
}
