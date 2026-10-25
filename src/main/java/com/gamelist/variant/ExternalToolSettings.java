package com.gamelist.variant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 外部工具（chdman / ffmpeg）的可执行文件路径设置，落在 {@code system_settings} 表里。
 * <p>
 * 界面留空表示“跟随自动探测”（配置文件 → 环境变量 → PATH → 程序目录），
 * 这样 Docker 这类已内置工具的部署不需要任何设置，而把工具放在别处的用户可以在
 * 工具箱里直接指定，无需改配置文件再重启。
 */
@Component
public class ExternalToolSettings {

    /** system_settings 的键名 */
    public static final String CHDMAN_PATH = "chdman.path";
    public static final String FFMPEG_PATH = "ffmpeg.path";

    private static final Logger logger = LoggerFactory.getLogger(ExternalToolSettings.class);

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    @Autowired
    public ExternalToolSettings(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 界面里配置的路径；未配置或读库失败时返回空串，由调用方回退到自动探测 */
    public String get(String key) {
        if (key == null) {
            return "";
        }
        if (!loaded) {
            reload();
        }
        String value = cache.get(key);
        return value == null ? "" : value.trim();
    }

    /**
     * 保存路径。空串会原样存下（而不是删行），表示“回到自动探测”，
     * 免去区分“没配置”和“配置了空的”两种状态。
     */
    public synchronized void save(String key, String value) {
        if (key == null) {
            return;
        }
        String normalized = value == null ? "" : value.trim();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_settings WHERE setting_key = ?", Integer.class, key);
        if (count != null && count > 0) {
            jdbcTemplate.update(
                    "UPDATE system_settings SET setting_value = ?, updated_at = CURRENT_TIMESTAMP WHERE setting_key = ?",
                    normalized, key);
        } else {
            jdbcTemplate.update(
                    "INSERT INTO system_settings (setting_key, setting_value, description, created_at, updated_at) "
                            + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    key, normalized, "外部工具可执行文件路径");
        }
        cache.put(key, normalized);
    }

    /** 重新从库里读一遍，供外部直接改库后同步 */
    public synchronized void reload() {
        Map<String, String> fresh = new LinkedHashMap<>();
        try {
            // 按列序号取值：H2 会把列标签转成大写，用列名查容易拿到 null
            List<String[]> rows = jdbcTemplate.query(
                    "SELECT setting_key, setting_value FROM system_settings WHERE setting_key IN (?, ?)",
                    (rs, rowNum) -> new String[]{rs.getString(1), rs.getString(2)},
                    CHDMAN_PATH, FFMPEG_PATH);
            for (String[] row : rows) {
                if (row[0] != null) {
                    fresh.put(row[0], row[1] == null ? "" : row[1]);
                }
            }
        } catch (Exception e) {
            logger.debug("读取外部工具路径失败: {}", e.getMessage());
        }
        cache.clear();
        cache.putAll(fresh);
        loaded = true;
    }
}
