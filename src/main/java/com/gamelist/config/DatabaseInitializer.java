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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    @Autowired
    private DataSource dataSource;

    private static final String INIT_SQL_PATH = "sql/init.sql";
    private static final String CHECK_TABLE = "platform";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("检查数据库初始化状态...");
        
        try (Connection connection = dataSource.getConnection()) {
            boolean databaseExists = checkDatabaseExists(connection);
            
            if (!databaseExists) {
                logger.info("数据库不存在，执行初始化...");
                executeInitSql(connection);
                logger.info("数据库初始化完成");
            } else {
                logger.info("数据库已存在，跳过初始化");
            }
        } catch (Exception e) {
            logger.error("数据库初始化检查失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    private boolean checkDatabaseExists(Connection connection) throws Exception {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet resultSet = metaData.getTables(null, null, CHECK_TABLE.toUpperCase(), null);
            boolean exists = resultSet.next();
            resultSet.close();
            return exists;
        } catch (Exception e) {
            logger.warn("检查数据库表失败: {}", e.getMessage());
            // 如果检查失败，假设数据库已存在（安全起见），避免误删数据
            return true;
        }
    }

    private void executeInitSql(Connection connection) throws Exception {
        ClassPathResource resource = new ClassPathResource(INIT_SQL_PATH);
        
        if (!resource.exists()) {
            logger.error("初始化脚本不存在: {}", INIT_SQL_PATH);
            throw new RuntimeException("初始化脚本不存在: " + INIT_SQL_PATH);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            
            List<String> statements = parseSqlStatements(reader);
            
            try (Statement statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                
                for (String sql : statements) {
                    if (sql.trim().isEmpty()) {
                        continue;
                    }
                    try {
                        statement.execute(sql);
                        logger.debug("执行SQL: {}", sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
                    } catch (Exception e) {
                        logger.warn("执行SQL失败: {} - {}", sql.length() > 100 ? sql.substring(0, 100) + "..." : sql, e.getMessage());
                    }
                }
                
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private List<String> parseSqlStatements(BufferedReader reader) throws Exception {
        List<String> statements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();
        
        String line;
        while ((line = reader.readLine()) != null) {
            // 跳过注释行
            if (line.trim().startsWith("--")) {
                continue;
            }
            
            currentStatement.append(line);
            
            // 检查是否到达语句结束
            if (line.trim().endsWith(";")) {
                statements.add(currentStatement.toString());
                currentStatement = new StringBuilder();
            }
        }
        
        // 添加最后一个语句（如果没有分号结尾）
        if (currentStatement.length() > 0 && currentStatement.toString().trim().length() > 0) {
            statements.add(currentStatement.toString());
        }
        
        return statements;
    }
}
