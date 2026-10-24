package com.gamelist.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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

/**
 * 独立译文表（field_translation）迁移初始化器。
 *
 * 项目已禁用 Flyway（spring.flyway.enabled=false），schema 由 DatabaseInitializer 通过 init.sql 同步，
 * 因此 V1.0.29__field_translation.sql 中的数据迁移与旧列删除由此初始化器负责：
 *   1. 幂等 DDL：建 field_translation 表 + 索引（init.sql 也会建，此处双重保险，表存在则跳过）；
 *   2. 仅当 game 表仍存在 translated_name/translated_desc 旧列时，把旧 zh 译文迁入 field_translation，
 *      随后删除旧列（不可逆，发布前照例备份快照）。
 * 删除旧列后 guard 判定为已迁移，重启安全；单条 INSERT 唯一冲突仅告警跳过（配合列存在判断防重复迁移）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class FieldTranslationMigrationInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(FieldTranslationMigrationInitializer.class);

    /** 迁移 SQL 路径（Flyway 禁用后由本类直接执行，保持与 db/migration 单一数据源一致） */
    private static final String MIGRATION_SQL_PATH = "db/migration/V1.0.29__field_translation.sql";

    private static final String SRC_TABLE = "GAME";

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            List<String> statements = loadSqlStatements();

            // 1. 幂等 DDL：建表 + 索引（已存在则跳过）
            for (String sql : statements) {
                String upper = sql.trim().toUpperCase();
                if (upper.startsWith("CREATE TABLE IF NOT EXISTS")
                        || upper.startsWith("CREATE INDEX IF NOT EXISTS")) {
                    execute(connection, sql, "DDL");
                }
            }

            // 2. 数据迁移 + 旧列删除：仅当旧列仍存在时执行一次（重启安全）
            if (!hasLegacyColumn(connection, "TRANSLATED_NAME") && !hasLegacyColumn(connection, "TRANSLATED_DESC")) {
                logger.info("game 旧译文列已移除，field_translation 迁移无需执行");
                return;
            }
            int migrated = 0;
            for (String sql : statements) {
                String upper = sql.trim().toUpperCase();
                if (upper.startsWith("INSERT INTO")) {
                    if (execute(connection, sql, "译文迁移")) {
                        migrated++;
                    }
                }
            }
            for (String sql : statements) {
                String upper = sql.trim().toUpperCase();
                if (upper.startsWith("ALTER TABLE") && upper.contains("DROP COLUMN")) {
                    execute(connection, sql, "旧列删除");
                }
            }
            logger.info("field_translation 迁移完成：执行 {} 条译文迁移语句，旧译文列已删除", migrated);
        } catch (Exception e) {
            logger.warn("field_translation 迁移初始化失败（不影响启动，可手动执行迁移脚本）: {}", e.getMessage());
        }
    }

    /** 检查 game 表是否存在指定旧列（H2 未加引号标识符默认大写） */
    private boolean hasLegacyColumn(Connection connection, String columnName) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, SRC_TABLE, columnName)) {
                if (rs.next()) {
                    return true;
                }
            }
            // 兼容小写存储的库
            try (ResultSet rs = metaData.getColumns(null, null, SRC_TABLE.toLowerCase(), columnName.toLowerCase())) {
                return rs.next();
            }
        } catch (Exception e) {
            logger.warn("检查 game.{} 列失败: {}", columnName, e.getMessage());
            return false;
        }
    }

    /** 执行单条语句：失败仅告警不中断，返回是否成功 */
    private boolean execute(Connection connection, String sql, String label) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            return true;
        } catch (Exception e) {
            logger.warn("field_translation {}执行失败(已跳过): {}", label, e.getMessage());
            return false;
        }
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
