package com.gamelist.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelist.service.TermMappingService;

/**
 * 方言映射表（TODO #10）初始化器。
 *
 * 项目已禁用 Flyway（spring.flyway.enabled=false），schema 由 DatabaseInitializer 通过 init.sql 同步，
 * 因此 term_mapping 的建表与种子数据由此初始化器负责：
 *   1. 执行 V1.0.17__term_mapping.sql 中的幂等 DDL（CREATE TABLE IF NOT EXISTS / CREATE UNIQUE INDEX IF NOT EXISTS）；
 *   2. 表为空时才执行 INSERT 种子数据（系统别名 → ScreenScraper systemId）。
 * 全部操作幂等，重启安全；失败仅告警，匹配功能自动降级为无映射表模式。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TermMappingInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(TermMappingInitializer.class);

    /** 迁移 SQL 路径（与 db/migration 下的历史迁移文件一致，Flyway 禁用后由本类直接执行） */
    private static final String MIGRATION_SQL_PATH = "db/migration/V1.0.17__term_mapping.sql";

    /** ScreenScraper 分类 → 业界通用分类缩写映射（category=genre 种子，TODO #10） */
    private static final String GENRE_MAP_PATH = "ss-genre-map.json";

    /** genre 种子分类名 */
    private static final String CATEGORY_GENRE = "genre";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TermMappingService termMappingService;

    @Override
    public void run(ApplicationArguments args) {
        // genre 分类种子独立于 system_alias：逐条补缺（已存在不覆盖，保留用户自定义），
        // 通过 addMapping 写入以自动刷新模板引擎方言缓存。放在最前，避免 system_alias
        // 分支提前 return 跳过本步骤
        seedGenreMappings();
        try (Connection connection = dataSource.getConnection()) {
            List<String> statements = loadSqlStatements();

            // 1. 幂等 DDL：建表 + 唯一索引（表已存在则跳过）
            for (String sql : statements) {
                String upper = sql.trim().toUpperCase();
                if (upper.startsWith("CREATE TABLE IF NOT EXISTS")
                        || upper.startsWith("CREATE UNIQUE INDEX IF NOT EXISTS")) {
                    execute(connection, sql, "DDL");
                }
            }

            // 2. 种子数据：表为空才插入（配合唯一索引双重防重）
            if (hasSeedData(connection)) {
                logger.info("term_mapping 表已有数据，跳过种子初始化");
                return;
            }
            for (String sql : statements) {
                String upper = sql.trim().toUpperCase();
                if (upper.startsWith("INSERT INTO")) {
                    execute(connection, sql, "种子数据");
                }
            }
            logger.info("term_mapping 表初始化完成");
        } catch (Exception e) {
            logger.warn("term_mapping 初始化失败（系统匹配将降级为无映射表模式）: {}", e.getMessage());
        }
    }

    /**
     * 插入 ScreenScraper 分类 → 通用分类（FTG/RPG/STG…）的 genre 方言种子。
     * addMapping 按归一化 source 判重，已存在的映射（含用户修改）保持不变。
     */
    private void seedGenreMappings() {
        try {
            Map<String, String> genreMap = loadGenreMap();
            int inserted = 0;
            int skipped = 0;
            for (Map.Entry<String, String> entry : genreMap.entrySet()) {
                String error = termMappingService.addMapping(entry.getKey(), entry.getValue(), CATEGORY_GENRE);
                if (error == null) {
                    inserted++;
                } else {
                    skipped++;
                }
            }
            logger.info("genre 方言种子初始化完成: 新增 {} 条, 已存在跳过 {} 条", inserted, skipped);
        } catch (Exception e) {
            logger.warn("genre 方言种子初始化失败（模板 map() 将无 genre 映射）: {}", e.getMessage());
        }
    }

    /** 读取 classpath 下的 ss-genre-map.json（跳过 _comment 元数据键） */
    @SuppressWarnings("unchecked")
    private Map<String, String> loadGenreMap() throws Exception {
        ClassPathResource resource = new ClassPathResource(GENRE_MAP_PATH);
        Map<String, String> result = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            Map<String, Object> parsed = new ObjectMapper().readValue(reader, Map.class);
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                if ("_comment".equals(entry.getKey()) || entry.getValue() == null) continue;
                result.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    private void execute(Connection connection, String sql, String label) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception e) {
            logger.warn("term_mapping {}执行失败(已跳过): {}", label, e.getMessage());
        }
    }

    private boolean hasSeedData(Connection connection) {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM term_mapping")) {
            if (rs.next()) {
                return rs.getLong(1) > 0;
            }
        } catch (Exception e) {
            logger.warn("检查 term_mapping 表数据失败: {}", e.getMessage());
        }
        return false;
    }

    /** 读取迁移 SQL：跳过注释行，按分号拆分语句（与 DatabaseInitializer 解析规则一致） */
    private List<String> loadSqlStatements() throws Exception {
        ClassPathResource resource = new ClassPathResource(MIGRATION_SQL_PATH);
        List<String> statements = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder current = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("--")) {
                    continue;
                }
                current.append(line);
                if (line.trim().endsWith(";")) {
                    statements.add(current.toString());
                    current = new StringBuilder();
                }
            }
            if (current.toString().trim().length() > 0) {
                statements.add(current.toString());
            }
        }
        return statements;
    }
}
