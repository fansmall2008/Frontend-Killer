package com.gamelist.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据库管理工具 API
 * 提供表浏览、结构查看、SQL 查询执行等功能
 */
@RestController
@RequestMapping("/api/database")
public class DatabaseController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 单次查询最大返回行数 */
    private static final int MAX_ROWS = 500;

    /**
     * 获取所有用户表列表（排除内部表）
     */
    @GetMapping("/tables")
    public ResponseEntity<List<Map<String, Object>>> listTables() {
        try {
            String sql = "SELECT TABLE_NAME, " +
                    "(SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS c WHERE c.TABLE_NAME = t.TABLE_NAME AND c.TABLE_SCHEMA = t.TABLE_SCHEMA) AS COLUMN_COUNT " +
                    "FROM INFORMATION_SCHEMA.TABLES t " +
                    "WHERE t.TABLE_SCHEMA = 'PUBLIC' " +
                    "AND t.TABLE_TYPE IN ('TABLE', 'BASE TABLE') " +
                    "ORDER BY TABLE_NAME";
            List<Map<String, Object>> tables = jdbcTemplate.queryForList(sql);

            // 附加每张表的行数
            for (Map<String, Object> table : tables) {
                String tableName = (String) table.get("TABLE_NAME");
                try {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM \"" + tableName + "\"", Integer.class);
                    table.put("ROW_COUNT", count);
                } catch (Exception e) {
                    table.put("ROW_COUNT", -1);
                }
            }
            return ResponseEntity.ok(tables);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * 获取表结构（列信息）
     */
    @GetMapping("/table/{tableName}/columns")
    public ResponseEntity<List<Map<String, Object>>> getTableColumns(@PathVariable String tableName) {
        try {
            String sql = "SELECT COLUMN_NAME, TYPE_NAME, CHARACTER_MAXIMUM_LENGTH, " +
                    "IS_NULLABLE, COLUMN_DEFAULT, IS_IDENTITY " +
                    "FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ? " +
                    "ORDER BY ORDINAL_POSITION";
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql, tableName);
            return ResponseEntity.ok(columns);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * 分页查询表数据
     */
    @GetMapping("/table/{tableName}/data")
    public ResponseEntity<Map<String, Object>> getTableData(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String orderBy) {
        try {
            // 安全校验：表名只允许字母数字下划线
            if (!tableName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                return ResponseEntity.badRequest().body(Map.of("error", "非法表名"));
            }
            if (limit > MAX_ROWS) limit = MAX_ROWS;

            String orderClause = (orderBy != null && orderBy.matches("[a-zA-Z_][a-zA-Z0-9_,\\s]*"))
                    ? " ORDER BY \"" + orderBy.replaceAll(",", "\", \"") + "\""
                    : "";
            String sql = "SELECT * FROM \"" + tableName + "\"" + orderClause
                    + " LIMIT " + limit + " OFFSET " + offset;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            // 总行数
            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM \"" + tableName + "\"", Integer.class);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("rows", rows);
            result.put("total", total);
            result.put("offset", offset);
            result.put("limit", limit);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 执行自定义 SQL 查询（只读）
     */
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> executeQuery(@RequestBody Map<String, String> body) {
        try {
            String sql = body.get("sql");
            if (sql == null || sql.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "SQL 不能为空"));
            }
            sql = sql.trim();

            // 安全检查：只允许 SELECT / SHOW / EXPLAIN / 以 SELECT 开头的查询
            String upperSql = sql.toUpperCase();
            if (upperSql.startsWith("SELECT") || upperSql.startsWith("SHOW")
                    || upperSql.startsWith("EXPLAIN") || upperSql.startsWith("WITH")) {
                // 查询类语句
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
                // 限制返回行数
                if (rows.size() > MAX_ROWS) {
                    rows = new ArrayList<>(rows.subList(0, MAX_ROWS));
                }

                // 提取列名
                List<String> columns = new ArrayList<>();
                if (!rows.isEmpty()) {
                    columns.addAll(rows.get(0).keySet());
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("columns", columns);
                result.put("rows", rows);
                result.put("rowCount", rows.size());
                result.put("truncated", rows.size() >= MAX_ROWS);
                result.put("type", "query");
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "只允许执行 SELECT / SHOW / EXPLAIN / WITH 查询语句"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取数据库统计概览
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDatabaseStats() {
        try {
            // 表数量
            Integer tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE IN ('TABLE', 'BASE TABLE')",
                    Integer.class);

            // 总行数
            List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE IN ('TABLE', 'BASE TABLE')");
            long totalRows = 0;
            for (Map<String, Object> t : tables) {
                String name = (String) t.get("TABLE_NAME");
                try {
                    Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM \"" + name + "\"", Integer.class);
                    totalRows += c;
                } catch (Exception ignored) {
                }
            }

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("tableCount", tableCount);
            stats.put("totalRows", totalRows);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }
}
