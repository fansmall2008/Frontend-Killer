package com.gamelist.controller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 默认备份根目录
    private static final String DEFAULT_BACKUP_ROOT = "/data/backup";
    // 数据库备份子目录
    private static final String DB_BACKUP_DIR = "database";
    // 源文件备份子目录
    private static final String SOURCE_BACKUP_DIR = "source_files";

    /**
     * 保存系统设置
     */
    @PostMapping("/save")
    public ResponseEntity<String> saveSettings(@RequestParam Map<String, String> settings) {
        try {
            for (Map.Entry<String, String> entry : settings.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                
                // 先检查是否存在该设置
                String checkSql = "SELECT COUNT(*) FROM system_settings WHERE setting_key = ?";
                Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, key);
                
                if (count != null && count > 0) {
                    // 更新现有设置
                    String updateSql = "UPDATE system_settings SET setting_value = ?, updated_at = CURRENT_TIMESTAMP WHERE setting_key = ?";
                    jdbcTemplate.update(updateSql, value, key);
                } else {
                    // 插入新设置
                    String insertSql = "INSERT INTO system_settings (setting_key, setting_value, description, created_at, updated_at) " +
                                      "VALUES (?, ?, '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
                    jdbcTemplate.update(insertSql, key, value);
                }
            }
            
            return ResponseEntity.ok("设置保存成功！");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("保存设置失败：" + e.getMessage());
        }
    }

    /**
     * 获取系统设置
     */
    @GetMapping("/get")
    public ResponseEntity<Map<String, String>> getSettings() {
        try {
            Map<String, String> settings = new HashMap<>();
            
            String sql = "SELECT setting_key, setting_value FROM system_settings";
            jdbcTemplate.query(sql, (rs) -> {
                String key = rs.getString("setting_key");
                String value = rs.getString("setting_value");
                settings.put(key, value);
            });
            
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    /**
     * 数据库备份
     */
    @PostMapping("/backup/database")
    public ResponseEntity<String> backupDatabase(@RequestParam("backupPath") String backupPath) {
        try {
            // 获取完整备份路径
            Path backupDir = getBackupPath(backupPath, DB_BACKUP_DIR);
            
            // 生成备份文件名
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String backupFileName = "database_backup_" + timestamp + ".sql";
            Path backupFile = backupDir.resolve(backupFileName);
            
            // 创建备份文件
            try (java.io.FileWriter writer = new java.io.FileWriter(backupFile.toFile())) {
                writer.write("-- 数据库备份文件\n");
                writer.write("-- 备份时间: " + new Date() + "\n");
                writer.write("-- 数据库: H2 文件模式\n\n");
                
                // 写入平台表结构
                writer.write("-- 平台表结构\n");
                writer.write("CREATE TABLE IF NOT EXISTS platform (\n");
                writer.write("    id BIGINT AUTO_INCREMENT PRIMARY KEY,\n");
                writer.write("    system VARCHAR(100) NOT NULL,\n");
                writer.write("    software VARCHAR(100) NOT NULL,\n");
                writer.write("    database VARCHAR(100) NOT NULL,\n");
                writer.write("    web VARCHAR(200) NOT NULL,\n");
                writer.write("    name VARCHAR(255) NOT NULL,\n");
                writer.write("    sort_by VARCHAR(50),\n");
                writer.write("    launch TEXT,\n");
                writer.write("    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n");
                writer.write("    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n");
                writer.write(");\n\n");
                
                // 写入平台数据
                writer.write("-- 平台数据\n");
                List<Map<String, Object>> platforms = jdbcTemplate.queryForList("SELECT * FROM platform");
                for (Map<String, Object> platform : platforms) {
                    writer.write("INSERT INTO platform (id, system, software, database, web, name, sort_by, launch, created_at, updated_at) VALUES (");
                    writer.write(platform.get("id") + ", ");
                    writer.write("'" + escapeSql(platform.get("system")) + "', ");
                    writer.write("'" + escapeSql(platform.get("software")) + "', ");
                    writer.write("'" + escapeSql(platform.get("database")) + "', ");
                    writer.write("'" + escapeSql(platform.get("web")) + "', ");
                    writer.write("'" + escapeSql(platform.get("name")) + "', ");
                    writer.write(platform.get("sort_by") == null ? "NULL, " : "'" + escapeSql(platform.get("sort_by")) + "', ");
                    writer.write(platform.get("launch") == null ? "NULL, " : "'" + escapeSql(platform.get("launch")) + "', ");
                    writer.write("'" + platform.get("created_at") + "', ");
                    writer.write("'" + platform.get("updated_at") + "'\n");
                    writer.write(");\n");
                }
                writer.write("\n");
                
                // 写入游戏表结构
                writer.write("-- 游戏表结构\n");
                writer.write("CREATE TABLE IF NOT EXISTS game (\n");
                writer.write("    id BIGINT AUTO_INCREMENT PRIMARY KEY,\n");
                writer.write("    game_id VARCHAR(100) NOT NULL,\n");
                writer.write("    source VARCHAR(100),\n");
                writer.write("    `path` VARCHAR(1024) NOT NULL,\n");
                writer.write("    `name` VARCHAR(255) NOT NULL,\n");
                writer.write("    `desc` TEXT,\n");
                writer.write("    translated_name VARCHAR(255),\n");
                writer.write("    translated_desc TEXT,\n");
                writer.write("    image VARCHAR(255),\n");
                writer.write("    video VARCHAR(255),\n");
                writer.write("    marquee VARCHAR(255),\n");
                writer.write("    thumbnail VARCHAR(255),\n");
                writer.write("    manual VARCHAR(255),\n");
                writer.write("    rating DOUBLE,\n");
                writer.write("    releasedate VARCHAR(50),\n");
                writer.write("    developer VARCHAR(100),\n");
                writer.write("    publisher VARCHAR(100),\n");
                writer.write("    genre VARCHAR(100),\n");
                writer.write("    players VARCHAR(20),\n");
                writer.write("    crc32 VARCHAR(20),\n");
                writer.write("    md5 VARCHAR(32),\n");
                writer.write("    lang VARCHAR(20),\n");
                writer.write("    genreid VARCHAR(20),\n");
                writer.write("    sort_by VARCHAR(50),\n");
                writer.write("    platform_id BIGINT,\n");
                writer.write("    scraped BOOLEAN DEFAULT false,\n");
                writer.write("    edited BOOLEAN DEFAULT false,\n");
                writer.write("    `exists` BOOLEAN DEFAULT false,\n");
                writer.write("    absolute_path VARCHAR(2048),\n");
                writer.write("    platform_path VARCHAR(2048),\n");
                writer.write("    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n");
                writer.write("    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n");
                writer.write("    box_front VARCHAR(255),\n");
                writer.write("    box_back VARCHAR(255),\n");
                writer.write("    box_spine VARCHAR(255),\n");
                writer.write("    box_full VARCHAR(255),\n");
                writer.write("    cartridge VARCHAR(255),\n");
                writer.write("    logo VARCHAR(255),\n");
                writer.write("    bezel VARCHAR(255),\n");
                writer.write("    panel VARCHAR(255),\n");
                writer.write("    cabinet_left VARCHAR(255),\n");
                writer.write("    cabinet_right VARCHAR(255),\n");
                writer.write("    tile VARCHAR(255),\n");
                writer.write("    banner VARCHAR(255),\n");
                writer.write("    steam VARCHAR(255),\n");
                writer.write("    poster VARCHAR(255),\n");
                writer.write("    background VARCHAR(255),\n");
                writer.write("    music VARCHAR(255),\n");
                writer.write("    screenshot VARCHAR(255),\n");
                writer.write("    titlescreen VARCHAR(255),\n");
                writer.write("    box3d VARCHAR(255),\n");
                writer.write("    steamgrid VARCHAR(255),\n");
                writer.write("    fanart VARCHAR(255),\n");
                writer.write("    boxtexture VARCHAR(255),\n");
                writer.write("    supporttexture VARCHAR(255),\n");
                writer.write("    platform_type VARCHAR(50),\n");
                writer.write("    hash VARCHAR(50),\n");
                writer.write("    FOREIGN KEY (platform_id) REFERENCES platform(id) ON DELETE CASCADE\n");
                writer.write(");\n\n");
                
                // 写入游戏数据
                writer.write("-- 游戏数据\n");
                List<Map<String, Object>> games = jdbcTemplate.queryForList("SELECT * FROM game");
                for (Map<String, Object> game : games) {
                    writer.write("INSERT INTO game (id, game_id, source, path, name, desc, translated_name, translated_desc, image, video, marquee, thumbnail, manual, rating, releasedate, developer, publisher, genre, players, crc32, md5, lang, genreid, sort_by, platform_id, scraped, edited, exists, absolute_path, platform_path, created_at, updated_at, box_front, box_back, box_spine, box_full, cartridge, logo, bezel, panel, cabinet_left, cabinet_right, tile, banner, steam, poster, background, music, screenshot, titlescreen, box3d, steamgrid, fanart, boxtexture, supporttexture, platform_type, hash) VALUES (");
                    writer.write(game.get("id") + ", ");
                    writer.write("'" + escapeSql(game.get("game_id")) + "', ");
                    writer.write(game.get("source") == null ? "NULL, " : "'" + escapeSql(game.get("source")) + "', ");
                    writer.write("'" + escapeSql(game.get("path")) + "', ");
                    writer.write("'" + escapeSql(game.get("name")) + "', ");
                    writer.write(game.get("desc") == null ? "NULL, " : "'" + escapeSql(game.get("desc")) + "', ");
                    writer.write(game.get("translated_name") == null ? "NULL, " : "'" + escapeSql(game.get("translated_name")) + "', ");
                    writer.write(game.get("translated_desc") == null ? "NULL, " : "'" + escapeSql(game.get("translated_desc")) + "', ");
                    writer.write(game.get("image") == null ? "NULL, " : "'" + escapeSql(game.get("image")) + "', ");
                    writer.write(game.get("video") == null ? "NULL, " : "'" + escapeSql(game.get("video")) + "', ");
                    writer.write(game.get("marquee") == null ? "NULL, " : "'" + escapeSql(game.get("marquee")) + "', ");
                    writer.write(game.get("thumbnail") == null ? "NULL, " : "'" + escapeSql(game.get("thumbnail")) + "', ");
                    writer.write(game.get("manual") == null ? "NULL, " : "'" + escapeSql(game.get("manual")) + "', ");
                    writer.write(game.get("rating") == null ? "NULL, " : game.get("rating") + ", ");
                    writer.write(game.get("releasedate") == null ? "NULL, " : "'" + escapeSql(game.get("releasedate")) + "', ");
                    writer.write(game.get("developer") == null ? "NULL, " : "'" + escapeSql(game.get("developer")) + "', ");
                    writer.write(game.get("publisher") == null ? "NULL, " : "'" + escapeSql(game.get("publisher")) + "', ");
                    writer.write(game.get("genre") == null ? "NULL, " : "'" + escapeSql(game.get("genre")) + "', ");
                    writer.write(game.get("players") == null ? "NULL, " : "'" + escapeSql(game.get("players")) + "', ");
                    writer.write(game.get("crc32") == null ? "NULL, " : "'" + escapeSql(game.get("crc32")) + "', ");
                    writer.write(game.get("md5") == null ? "NULL, " : "'" + escapeSql(game.get("md5")) + "', ");
                    writer.write(game.get("lang") == null ? "NULL, " : "'" + escapeSql(game.get("lang")) + "', ");
                    writer.write(game.get("genreid") == null ? "NULL, " : "'" + escapeSql(game.get("genreid")) + "', ");
                    writer.write(game.get("sort_by") == null ? "NULL, " : "'" + escapeSql(game.get("sort_by")) + "', ");
                    writer.write(game.get("platform_id") == null ? "NULL, " : game.get("platform_id") + ", ");
                    writer.write(game.get("scraped") == null ? "NULL, " : game.get("scraped") + ", ");
                    writer.write(game.get("edited") == null ? "NULL, " : game.get("edited") + ", ");
                    writer.write(game.get("exists") == null ? "NULL, " : game.get("exists") + ", ");
                    writer.write(game.get("absolute_path") == null ? "NULL, " : "'" + escapeSql(game.get("absolute_path")) + "', ");
                    writer.write(game.get("platform_path") == null ? "NULL, " : "'" + escapeSql(game.get("platform_path")) + "', ");
                    writer.write("'" + game.get("created_at") + "', ");
                    writer.write("'" + game.get("updated_at") + "', ");
                    writer.write(game.get("box_front") == null ? "NULL, " : "'" + escapeSql(game.get("box_front")) + "', ");
                    writer.write(game.get("box_back") == null ? "NULL, " : "'" + escapeSql(game.get("box_back")) + "', ");
                    writer.write(game.get("box_spine") == null ? "NULL, " : "'" + escapeSql(game.get("box_spine")) + "', ");
                    writer.write(game.get("box_full") == null ? "NULL, " : "'" + escapeSql(game.get("box_full")) + "', ");
                    writer.write(game.get("cartridge") == null ? "NULL, " : "'" + escapeSql(game.get("cartridge")) + "', ");
                    writer.write(game.get("logo") == null ? "NULL, " : "'" + escapeSql(game.get("logo")) + "', ");
                    writer.write(game.get("bezel") == null ? "NULL, " : "'" + escapeSql(game.get("bezel")) + "', ");
                    writer.write(game.get("panel") == null ? "NULL, " : "'" + escapeSql(game.get("panel")) + "', ");
                    writer.write(game.get("cabinet_left") == null ? "NULL, " : "'" + escapeSql(game.get("cabinet_left")) + "', ");
                    writer.write(game.get("cabinet_right") == null ? "NULL, " : "'" + escapeSql(game.get("cabinet_right")) + "', ");
                    writer.write(game.get("tile") == null ? "NULL, " : "'" + escapeSql(game.get("tile")) + "', ");
                    writer.write(game.get("banner") == null ? "NULL, " : "'" + escapeSql(game.get("banner")) + "', ");
                    writer.write(game.get("steam") == null ? "NULL, " : "'" + escapeSql(game.get("steam")) + "', ");
                    writer.write(game.get("poster") == null ? "NULL, " : "'" + escapeSql(game.get("poster")) + "', ");
                    writer.write(game.get("background") == null ? "NULL, " : "'" + escapeSql(game.get("background")) + "', ");
                    writer.write(game.get("music") == null ? "NULL, " : "'" + escapeSql(game.get("music")) + "', ");
                    writer.write(game.get("screenshot") == null ? "NULL, " : "'" + escapeSql(game.get("screenshot")) + "', ");
                    writer.write(game.get("titlescreen") == null ? "NULL, " : "'" + escapeSql(game.get("titlescreen")) + "', ");
                    writer.write(game.get("box3d") == null ? "NULL, " : "'" + escapeSql(game.get("box3d")) + "', ");
                    writer.write(game.get("steamgrid") == null ? "NULL, " : "'" + escapeSql(game.get("steamgrid")) + "', ");
                    writer.write(game.get("fanart") == null ? "NULL, " : "'" + escapeSql(game.get("fanart")) + "', ");
                    writer.write(game.get("boxtexture") == null ? "NULL, " : "'" + escapeSql(game.get("boxtexture")) + "', ");
                    writer.write(game.get("supporttexture") == null ? "NULL, " : "'" + escapeSql(game.get("supporttexture")) + "', ");
                    writer.write(game.get("platform_type") == null ? "NULL, " : "'" + escapeSql(game.get("platform_type")) + "', ");
                    writer.write(game.get("hash") == null ? "NULL" : "'" + escapeSql(game.get("hash")) + "'");
                    writer.write("\n");
                    writer.write(");\n");
                }
                writer.write("\n");
                
                // 写入备份完成信息
                writer.write("-- 备份完成\n");
                writer.write("-- 注意：此备份文件包含表结构和数据，可用于重建数据库\n");
            }
            
            // 验证备份文件是否生成且不为空
            if (!Files.exists(backupFile) || Files.size(backupFile) == 0) {
                throw new Exception("备份文件未生成或为空");
            }
            
            return ResponseEntity.ok("数据库备份成功！备份文件：" + backupFile.toString() + " (包含 " + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM platform", Integer.class) + " 个平台，" + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM game", Integer.class) + " 个游戏)");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("备份失败：" + e.getMessage());
        }
    }
    
    /**
     * SQL转义函数
     */
    private String escapeSql(Object value) {
        if (value == null) {
            return null;
        }
        String str = value.toString();
        return str.replace("'", "''");
    }

    /**
     * 数据库恢复
     */
    @PostMapping("/restore/database")
    public ResponseEntity<String> restoreDatabase(@RequestParam("backupFile") String backupFile) {
        try {
            // 检查备份文件是否存在
            Path backupFilePath = Paths.get(backupFile);
            if (!Files.exists(backupFilePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("备份文件不存在：" + backupFile);
            }
            
            // 先删除现有表结构，避免外键约束冲突
            jdbcTemplate.execute("DROP TABLE IF EXISTS game CASCADE");
            jdbcTemplate.execute("DROP TABLE IF EXISTS platform CASCADE");
            jdbcTemplate.execute("DROP TABLE IF EXISTS system_settings CASCADE");
            jdbcTemplate.execute("DROP TABLE IF EXISTS temp_subset CASCADE");
            jdbcTemplate.execute("DROP TABLE IF EXISTS temp_subset_game CASCADE");
            jdbcTemplate.execute("DROP TABLE IF EXISTS merge_report CASCADE");
            jdbcTemplate.execute("DROP TABLE IF EXISTS merge_conflict CASCADE");
            
            // 读取备份文件内容
            String sqlContent = Files.readString(backupFilePath);
            
            // 执行SQL语句
            List<String> statements = new ArrayList<>();
            StringBuilder currentStatement = new StringBuilder();
            
            // 按行处理SQL文件
            String[] lines = sqlContent.split("\n");
            for (String line : lines) {
                String trimmedLine = line.trim();
                // 跳过空行和注释行
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("--")) {
                    continue;
                }
                currentStatement.append(trimmedLine).append(" ");
                // 如果遇到分号，说明这是一个完整的SQL语句
                if (trimmedLine.endsWith(";")) {
                    statements.add(currentStatement.toString());
                    currentStatement = new StringBuilder();
                }
            }
            
            int executedStatements = 0;
            for (String sql : statements) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    jdbcTemplate.execute(sql);
                    executedStatements++;
                }
            }
            
            return ResponseEntity.ok("数据库恢复成功！执行了 " + executedStatements + " 条SQL语句。");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("恢复失败：" + e.getMessage());
        }
    }

    /**
     * 获取完整备份路径
     */
    private Path getBackupPath(String basePath, String subDir) {
        // 检查并使用默认备份路径
        String backupRoot = DEFAULT_BACKUP_ROOT;
        if (basePath != null && !basePath.trim().isEmpty()) {
            backupRoot = basePath;
        }
        
        // 确保根备份目录存在
        Path rootDir = Paths.get(backupRoot);
        if (!Files.exists(rootDir)) {
            try {
                Files.createDirectories(rootDir);
            } catch (IOException e) {
                throw new RuntimeException("创建根备份目录失败：" + e.getMessage(), e);
            }
        }
        
        // 确保子目录存在
        Path backupDir = rootDir.resolve(subDir);
        if (!Files.exists(backupDir)) {
            try {
                Files.createDirectories(backupDir);
            } catch (IOException e) {
                throw new RuntimeException("创建备份子目录失败：" + e.getMessage(), e);
            }
        }
        return backupDir;
    }



    /**
     * 获取备份文件列表
     */
    @GetMapping("/backups/list")
    public ResponseEntity<List<BackupFileInfo>> getBackupFiles(@RequestParam("backupPath") String backupPath) {
        try {
            List<BackupFileInfo> backupFiles = new ArrayList<>();
            
            Path backupDir = Paths.get(backupPath);
            if (Files.exists(backupDir) && Files.isDirectory(backupDir)) {
                // 递归遍历所有子目录
                Files.walk(backupDir, Integer.MAX_VALUE)
                     .filter(Files::isRegularFile)
                     .forEach(path -> {
                         File file = path.toFile();
                         BackupFileInfo info = new BackupFileInfo();
                         info.setName(file.getName());
                         info.setPath(file.getAbsolutePath());
                         info.setSize(file.length());
                         info.setLastModified(file.lastModified());
                         backupFiles.add(info);
                     });
            }
            
            return ResponseEntity.ok(backupFiles);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    /**
     * 数据库初始化
     */
    @PostMapping("/database/init")
    public ResponseEntity<String> initDatabase() {
        try {
            // 从classpath读取初始化SQL文件
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("sql/init.sql")) {
                if (inputStream == null) {
                    throw new Exception("初始化SQL文件不存在");
                }
                
                // 读取SQL文件内容
                String sqlContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                
                // 首先删除现有表结构
                jdbcTemplate.execute("DROP TABLE IF EXISTS merge_conflict CASCADE");
                jdbcTemplate.execute("DROP TABLE IF EXISTS merge_report CASCADE");
                jdbcTemplate.execute("DROP TABLE IF EXISTS temp_subset_game CASCADE");
                jdbcTemplate.execute("DROP TABLE IF EXISTS temp_subset CASCADE");
                jdbcTemplate.execute("DROP TABLE IF EXISTS system_settings CASCADE");
                jdbcTemplate.execute("DROP TABLE IF EXISTS game CASCADE");
                jdbcTemplate.execute("DROP TABLE IF EXISTS platform CASCADE");
                
                // 执行SQL语句
                List<String> statements = new ArrayList<>();
                StringBuilder currentStatement = new StringBuilder();
                
                // 按行处理SQL文件
                String[] lines = sqlContent.split("\n");
                for (String line : lines) {
                    String trimmedLine = line.trim();
                    // 跳过空行和注释行
                    if (trimmedLine.isEmpty() || trimmedLine.startsWith("--")) {
                        continue;
                    }
                    currentStatement.append(trimmedLine).append(" ");
                    // 如果遇到分号，说明这是一个完整的SQL语句
                    if (trimmedLine.endsWith(";")) {
                        statements.add(currentStatement.toString());
                        currentStatement = new StringBuilder();
                    }
                }
                
                int executedStatements = 0;
                for (String sql : statements) {
                    sql = sql.trim();
                    if (!sql.isEmpty()) {
                        jdbcTemplate.execute(sql);
                        executedStatements++;
                    }
                }
                
                return ResponseEntity.ok("数据库初始化成功！表结构已重建，数据已清空。执行了 " + executedStatements + " 条SQL语句。");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("初始化失败：" + e.getMessage());
        }
    }



    /**
     * 备份文件信息类
     */
    public static class BackupFileInfo {
        private String name;
        private String path;
        private long size;
        private long lastModified;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public long getLastModified() {
            return lastModified;
        }

        public void setLastModified(long lastModified) {
            this.lastModified = lastModified;
        }
    }
}
