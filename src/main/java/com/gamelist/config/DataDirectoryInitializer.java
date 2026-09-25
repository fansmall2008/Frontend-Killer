package com.gamelist.config;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class DataDirectoryInitializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static boolean initialized = false;

    private static final Logger logger = LoggerFactory.getLogger(DataDirectoryInitializer.class);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        if (initialized) {
            return;
        }
        initialized = true;
        
        logger.info("========== 初始化数据目录 ==========");
        
        try {
            String datasourceUrl = event.getEnvironment().getProperty(
                "spring.datasource.url", 
                "jdbc:h2:file:./data/database/database.db"
            );
            String dbPath = extractDbPathFromUrl(datasourceUrl);
            File dbDir = new File(dbPath);
            if (!dbDir.exists()) {
                boolean created = dbDir.mkdirs();
                if (created) {
                    logger.info("数据库目录创建成功: {}", dbDir.getAbsolutePath());
                } else {
                    logger.warn("数据库目录创建失败: {}", dbDir.getAbsolutePath());
                }
            } else {
                logger.info("数据库目录已存在: {}", dbDir.getAbsolutePath());
            }

            File dataDir = new File("./data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
                logger.info("数据根目录创建成功: {}", dataDir.getAbsolutePath());
            }

            File rulesDir = new File("./data/rules");
            if (!rulesDir.exists()) {
                rulesDir.mkdirs();
                logger.info("规则目录创建成功: {}", rulesDir.getAbsolutePath());
            }

            File importDir = new File("./data/rules/import");
            if (!importDir.exists()) {
                importDir.mkdirs();
                logger.info("导入规则目录创建成功: {}", importDir.getAbsolutePath());
            }

            File exportDir = new File("./data/rules/export");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
                logger.info("导出规则目录创建成功: {}", exportDir.getAbsolutePath());
            }

            File outputDir = new File("./data/output");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
                logger.info("输出目录创建成功: {}", outputDir.getAbsolutePath());
            }

            File inputDir = new File("./data/input");
            if (!inputDir.exists()) {
                inputDir.mkdirs();
                logger.info("输入目录创建成功: {}", inputDir.getAbsolutePath());
            }

            File logsDir = new File("./logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
                logger.info("日志目录创建成功: {}", logsDir.getAbsolutePath());
            }

            File themesDir = new File("./data/themes");
            if (!themesDir.exists()) {
                themesDir.mkdirs();
                logger.info("主题包目录创建成功: {}", themesDir.getAbsolutePath());
            }

            logger.info("========== 数据目录初始化完成 ==========");

        } catch (Exception e) {
            logger.error("数据目录初始化失败: {}", e.getMessage(), e);
        }
    }

    private String extractDbPathFromUrl(String url) {
        if (url == null || !url.startsWith("jdbc:h2:file:")) {
            return "./data/database";
        }
        String path = url.substring("jdbc:h2:file:".length());
        int semicolonIndex = path.indexOf(';');
        if (semicolonIndex > 0) {
            path = path.substring(0, semicolonIndex);
        }
        File dbFile = new File(path);
        return dbFile.getParent();
    }
}