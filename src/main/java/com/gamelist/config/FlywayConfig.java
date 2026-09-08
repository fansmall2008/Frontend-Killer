package com.gamelist.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flyway配置类 - 处理迁移失败的自动修复
 */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfig.class);

    private final DataSource dataSource;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String locations;

    @Value("${spring.flyway.baseline-version:1.0.0}")
    private String baselineVersion;

    public FlywayConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void init() {
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(locations)
                    .baselineVersion(baselineVersion)
                    .baselineOnMigrate(true)
                    .validateOnMigrate(false)
                    .load();

            logger.info("检查Flyway schema history状态...");
            
            // 检查迁移状态
            MigrationInfo[] migrationInfo = flyway.info().all();
            boolean hasFailedMigration = false;
            for (MigrationInfo info : migrationInfo) {
                if (info.getState() != null && info.getState().name().contains("FAILED")) {
                    logger.warn("发现失败的迁移: version={}, description={}", 
                        info.getVersion(), info.getDescription());
                    hasFailedMigration = true;
                }
            }
            
            if (hasFailedMigration) {
                logger.info("执行Flyway修复...");
                flyway.repair();
                logger.info("Flyway修复完成");
            }
            
            // 执行迁移
            logger.info("执行Flyway迁移...");
            flyway.migrate();
            logger.info("Flyway迁移完成");
            
        } catch (Exception e) {
            logger.error("Flyway初始化失败: {}", e.getMessage());
            throw e;
        }
    }
}