package com.gamelist.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tools/column")
public class ColumnToolController {

    private static final Logger logger = LoggerFactory.getLogger(ColumnToolController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final LinkedHashMap<String, String> COLUMN_ORDER = new LinkedHashMap<>();
    static {
        // 第一组 - 游戏核心信息
        COLUMN_ORDER.put("name", "游戏名称");
        COLUMN_ORDER.put("translated_name", "翻译名称");
        COLUMN_ORDER.put("desc", "描述");
        COLUMN_ORDER.put("translated_desc", "翻译描述");
        // 第二组 - 元数据
        COLUMN_ORDER.put("developer", "开发商");
        COLUMN_ORDER.put("publisher", "发行商");
        COLUMN_ORDER.put("genre", "类型");
        COLUMN_ORDER.put("lang", "语言");
        COLUMN_ORDER.put("releasedate", "发行日期");
        COLUMN_ORDER.put("players", "玩家数");
        COLUMN_ORDER.put("rating", "评分");
        COLUMN_ORDER.put("sort_by", "排序名");
        COLUMN_ORDER.put("parent_rom", "父ROM");
        COLUMN_ORDER.put("source", "来源");
        // 第三组 - 媒体路径
        COLUMN_ORDER.put("image", "图片");
        COLUMN_ORDER.put("thumbnail", "缩略图");
        COLUMN_ORDER.put("box_2d", "2D盒子");
        COLUMN_ORDER.put("box_2d_back", "2D盒子背面");
        COLUMN_ORDER.put("box_2d_side", "2D盒子侧面");
        COLUMN_ORDER.put("box3d", "3D盒子");
        COLUMN_ORDER.put("box_scan", "盒子扫描");
        COLUMN_ORDER.put("wheel", "滚轮");
        COLUMN_ORDER.put("ss", "截图");
        COLUMN_ORDER.put("sstitle", "SS标题");
        COLUMN_ORDER.put("marquee", "遮罩");
        COLUMN_ORDER.put("video", "视频");
        COLUMN_ORDER.put("video_normalized", "标准化视频");
        COLUMN_ORDER.put("manuel", "手册");
        COLUMN_ORDER.put("fanart", "同人画");
        COLUMN_ORDER.put("flyer", "传单");
        COLUMN_ORDER.put("steamgrid", "Steam网格");
        COLUMN_ORDER.put("overlay", "覆盖层");
        COLUMN_ORDER.put("maps", "地图");
        COLUMN_ORDER.put("figurine", "手办");
        COLUMN_ORDER.put("bezel_4_3", "边框4:3");
        COLUMN_ORDER.put("bezel_16_9", "边框16:9");
        COLUMN_ORDER.put("mixrbv1", "混合RBV1");
        COLUMN_ORDER.put("mixrbv2", "混合RBV2");
        COLUMN_ORDER.put("themehb", "主题HB");
        COLUMN_ORDER.put("themehs", "主题HS");
        COLUMN_ORDER.put("support_2d", "支撑2D");
        COLUMN_ORDER.put("support_scan", "支撑扫描");
        COLUMN_ORDER.put("screenmarquee", "屏幕遮罩");
    }

    private static final List<String> ALLOWED_COLUMNS = new ArrayList<>(COLUMN_ORDER.keySet());

    private static final Map<String, String> COLUMN_LABELS = Map.copyOf(COLUMN_ORDER);

    /** H2 保留字：这些列在 SQL 中必须加引号，使用双引号小写形式（与 init.sql 反引号创建一致） */
    private static final java.util.Set<String> H2_RESERVED = java.util.Set.of(
            "desc", "exists", "path", "order", "value", "key", "check", "cross", "group",
            "index", "limit", "offset", "primary", "select", "where", "from", "table",
            "default", "unique", "not", "null", "and", "or", "like", "between", "in",
            "is", "as", "on", "join", "having", "case", "when", "then", "else", "end"
    );

    // --- Translation column helpers ---

    private boolean isTranslationColumn(String column) {
        return "translated_name".equals(column) || "translated_desc".equals(column);
    }

    private String getTranslationSrcField(String column) {
        if ("translated_name".equals(column)) return "name";
        if ("translated_desc".equals(column)) return "desc";
        return null;
    }

    private String getTranslationValue(Long gameId, String srcField) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT trans_value FROM field_translation WHERE src_table='game' AND src_id=? AND src_field=? AND lang='zh'",
                String.class, gameId, srcField);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private void upsertTranslation(Long gameId, String srcField, String value) {
        if (value == null || value.isEmpty()) {
            jdbcTemplate.update(
                "DELETE FROM field_translation WHERE src_table='game' AND src_id=? AND src_field=? AND lang='zh'",
                gameId, srcField);
        } else {
            jdbcTemplate.update(
                "MERGE INTO field_translation (src_table, src_id, src_field, lang, trans_value) KEY (src_table, src_id, src_field, lang) VALUES ('game', ?, ?, 'zh', ?)",
                gameId, srcField, value);
        }
    }

    private void validateColumn(String column) {
        if (!ALLOWED_COLUMNS.contains(column)) {
            throw new IllegalArgumentException("不允许的列名: " + column);
        }
    }

    /**
     * 列名引用：非保留字不加引号（H2 自动折叠为大写匹配存储名），
     * 保留字用双引号小写形式（与 init.sql 反引号创建的 case-sensitive 列一致）。
     */
    private String quoteColumn(String column) {
        if (H2_RESERVED.contains(column.toLowerCase())) {
            return "\"" + column.toLowerCase() + "\"";
        }
        return column;
    }

    @GetMapping("/columns")
    public ResponseEntity<?> getColumns() {
        try {
            List<Map<String, String>> columns = new ArrayList<>();
            for (String col : ALLOWED_COLUMNS) {
                Map<String, String> entry = new HashMap<>();
                entry.put("name", col);
                entry.put("label", COLUMN_LABELS.getOrDefault(col, col));
                columns.add(entry);
            }
            return ResponseEntity.ok(Map.of("success", true, "columns", columns));
        } catch (Exception e) {
            logger.error("获取列列表失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", "获取列列表失败: " + e.getMessage()));
        }
    }

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody Map<String, Object> request) {
        try {
            String operation = (String) request.get("operation");
            if (operation == null || operation.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "operation 不能为空"));
            }

            String targetColumn = (String) request.get("targetColumn");
            if (targetColumn == null || targetColumn.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "targetColumn 不能为空"));
            }
            validateColumn(targetColumn);

            String sourceColumn = (String) request.get("sourceColumn");
            if (sourceColumn != null && !sourceColumn.isBlank()) {
                validateColumn(sourceColumn);
            }

            Number platformIdNum = (Number) request.get("platformId");
            Long platformId = (platformIdNum != null && platformIdNum.longValue() != 0)
                    ? platformIdNum.longValue() : null;

            Map<String, Object> previewResult;

            switch (operation) {
                case "copy":
                    if (sourceColumn == null || sourceColumn.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "copy 操作需要 sourceColumn"));
                    }
                    previewResult = previewCopy(sourceColumn, targetColumn, platformId);
                    break;
                case "swap":
                    if (sourceColumn == null || sourceColumn.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "swap 操作需要 sourceColumn"));
                    }
                    previewResult = previewSwap(sourceColumn, targetColumn, platformId);
                    break;
                case "overwrite":
                    if (sourceColumn == null || sourceColumn.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "overwrite 操作需要 sourceColumn"));
                    }
                    previewResult = previewOverwrite(sourceColumn, targetColumn, platformId);
                    break;
                case "replace":
                    String findText = (String) request.get("findText");
                    if (findText == null || findText.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "replace 操作需要 findText"));
                    }
                    previewResult = previewReplace(targetColumn, findText, platformId);
                    break;
                default:
                    return ResponseEntity.badRequest()
                            .body(Map.of("success", false, "error", "不支持的操作: " + operation));
            }

            return ResponseEntity.ok(previewResult);
        } catch (IllegalArgumentException e) {
            logger.warn("参数校验失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            logger.error("预览操作失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", "预览失败: " + e.getMessage()));
        }
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody Map<String, Object> request) {
        try {
            String operation = (String) request.get("operation");
            if (operation == null || operation.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "operation 不能为空"));
            }

            String targetColumn = (String) request.get("targetColumn");
            if (targetColumn == null || targetColumn.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "targetColumn 不能为空"));
            }
            validateColumn(targetColumn);

            String sourceColumn = (String) request.get("sourceColumn");
            if (sourceColumn != null && !sourceColumn.isBlank()) {
                validateColumn(sourceColumn);
            }

            Number platformIdNum = (Number) request.get("platformId");
            Long platformId = (platformIdNum != null && platformIdNum.longValue() != 0)
                    ? platformIdNum.longValue() : null;

            Map<String, Object> execResult;

            switch (operation) {
                case "copy":
                    if (sourceColumn == null || sourceColumn.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "copy 操作需要 sourceColumn"));
                    }
                    execResult = executeCopy(sourceColumn, targetColumn, platformId);
                    break;
                case "swap":
                    if (sourceColumn == null || sourceColumn.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "swap 操作需要 sourceColumn"));
                    }
                    execResult = executeSwap(sourceColumn, targetColumn, platformId);
                    break;
                case "overwrite":
                    if (sourceColumn == null || sourceColumn.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "overwrite 操作需要 sourceColumn"));
                    }
                    execResult = executeOverwrite(sourceColumn, targetColumn, platformId);
                    break;
                case "replace":
                    String findText = (String) request.get("findText");
                    String replaceText = (String) request.get("replaceText");
                    if (findText == null || findText.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "replace 操作需要 findText"));
                    }
                    if (replaceText == null) {
                        replaceText = "";
                    }
                    execResult = executeReplace(targetColumn, findText, replaceText, platformId);
                    break;
                default:
                    return ResponseEntity.badRequest()
                            .body(Map.of("success", false, "error", "不支持的操作: " + operation));
            }

            execResult.put("operation", operation);
            if (sourceColumn != null && !sourceColumn.isBlank()) {
                execResult.put("sourceColumn", sourceColumn);
            }
            execResult.put("targetColumn", targetColumn);

            return ResponseEntity.ok(execResult);
        } catch (IllegalArgumentException e) {
            logger.warn("参数校验失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            logger.error("执行操作失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", "执行失败: " + e.getMessage()));
        }
    }

    // --- Preview methods ---

    private Map<String, Object> previewResult(int affectedRows) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("affectedRows", affectedRows);
        return result;
    }

    private Map<String, Object> previewResult(int affectedRows, int skippedDueToNull) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("affectedRows", affectedRows);
        result.put("skippedDueToNull", skippedDueToNull);
        return result;
    }

    private Map<String, Object> execResult(int affectedRows) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("affectedRows", affectedRows);
        return result;
    }

    private Map<String, Object> execResult(int affectedRows, int skippedDueToNull) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("affectedRows", affectedRows);
        result.put("skippedDueToNull", skippedDueToNull);
        return result;
    }

    private Map<String, Object> previewCopy(String source, String target, Long platformId) {
        boolean srcTrans = isTranslationColumn(source);
        boolean tgtTrans = isTranslationColumn(target);

        if (!srcTrans && !tgtTrans) {
            // 两个都是 game 表列
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM game WHERE ")
                    .append(quoteColumn(source)).append(" IS NOT NULL");
            List<Object> params = new ArrayList<>();
            if (platformId != null) {
                sql.append(" AND platform_id = ?");
                params.add(platformId);
            }
            Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray(new Object[0]));
            return previewResult(count != null ? count : 0);
        } else if (srcTrans && !tgtTrans) {
            // 源是翻译列，目标是 game 列
            String srcField = getTranslationSrcField(source);
            StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM field_translation ft JOIN game g ON g.id = ft.src_id WHERE ft.src_table='game' AND ft.src_field=? AND ft.lang='zh' AND ft.trans_value IS NOT NULL");
            List<Object> params = new ArrayList<>();
            params.add(srcField);
            if (platformId != null) {
                sql.append(" AND g.platform_id = ?");
                params.add(platformId);
            }
            Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray(new Object[0]));
            return previewResult(count != null ? count : 0);
        } else if (!srcTrans && tgtTrans) {
            // 源是 game 列，目标是翻译列
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM game WHERE ")
                    .append(quoteColumn(source)).append(" IS NOT NULL");
            List<Object> params = new ArrayList<>();
            if (platformId != null) {
                sql.append(" AND platform_id = ?");
                params.add(platformId);
            }
            Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray(new Object[0]));
            return previewResult(count != null ? count : 0);
        } else {
            // 两个都是翻译列
            String srcField = getTranslationSrcField(source);
            StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM field_translation WHERE src_table='game' AND src_field=? AND lang='zh' AND trans_value IS NOT NULL");
            List<Object> params = new ArrayList<>();
            params.add(srcField);
            if (platformId != null) {
                sql.append(" AND src_id IN (SELECT id FROM game WHERE platform_id = ?)");
                params.add(platformId);
            }
            Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray(new Object[0]));
            return previewResult(count != null ? count : 0);
        }
    }

    private Map<String, Object> previewSwap(String source, String target, Long platformId) {
        boolean srcTrans = isTranslationColumn(source);
        boolean tgtTrans = isTranslationColumn(target);

        if (!srcTrans && !tgtTrans) {
            // 两个都是 game 表列：仅両列とも非NULL的行可以互换
            StringBuilder sqlAffected = new StringBuilder("SELECT COUNT(*) FROM game WHERE ")
                    .append(quoteColumn(source)).append(" IS NOT NULL AND ")
                    .append(quoteColumn(target)).append(" IS NOT NULL");
            // 跳过的行：恰好一列为 NULL（両NULL 无需互换也不影响）
            StringBuilder sqlSkipped = new StringBuilder("SELECT COUNT(*) FROM game WHERE (")
                    .append(quoteColumn(source)).append(" IS NULL AND ")
                    .append(quoteColumn(target)).append(" IS NOT NULL) OR (")
                    .append(quoteColumn(source)).append(" IS NOT NULL AND ")
                    .append(quoteColumn(target)).append(" IS NULL)");
            List<Object> paramsAffected = new ArrayList<>();
            List<Object> paramsSkipped = new ArrayList<>();
            if (platformId != null) {
                sqlAffected.append(" AND platform_id = ?");
                sqlSkipped.append(" AND platform_id = ?");
                paramsAffected.add(platformId);
                paramsSkipped.add(platformId);
            }
            Integer affected = jdbcTemplate.queryForObject(sqlAffected.toString(), Integer.class, paramsAffected.toArray(new Object[0]));
            Integer skipped = jdbcTemplate.queryForObject(sqlSkipped.toString(), Integer.class, paramsSkipped.toArray(new Object[0]));
            return previewResult(affected != null ? affected : 0, skipped != null ? skipped : 0);
        } else if (srcTrans && !tgtTrans) {
            // 源是翻译列，目标是 game 列
            String srcField = getTranslationSrcField(source);
            // 両方とも非NULL：可以互换
            StringBuilder sqlAffected = new StringBuilder(
                "SELECT COUNT(*) FROM game g LEFT JOIN field_translation ft ON ft.src_table='game' AND ft.src_id=g.id AND ft.src_field=? AND ft.lang='zh' WHERE g.")
                .append(quoteColumn(target)).append(" IS NOT NULL AND ft.trans_value IS NOT NULL");
            // 恰好一列为 NULL：会被跳过
            StringBuilder sqlSkipped = new StringBuilder(
                "SELECT COUNT(*) FROM game g LEFT JOIN field_translation ft ON ft.src_table='game' AND ft.src_id=g.id AND ft.src_field=? AND ft.lang='zh' WHERE (g.")
                .append(quoteColumn(target)).append(" IS NULL AND ft.trans_value IS NOT NULL) OR (g.")
                .append(quoteColumn(target)).append(" IS NOT NULL AND ft.trans_value IS NULL)");
            List<Object> paramsAffected = new ArrayList<>();
            List<Object> paramsSkipped = new ArrayList<>();
            paramsAffected.add(srcField);
            paramsSkipped.add(srcField);
            if (platformId != null) {
                sqlAffected.append(" AND g.platform_id = ?");
                sqlSkipped.append(" AND g.platform_id = ?");
                paramsAffected.add(platformId);
                paramsSkipped.add(platformId);
            }
            Integer affected = jdbcTemplate.queryForObject(sqlAffected.toString(), Integer.class, paramsAffected.toArray(new Object[0]));
            Integer skipped = jdbcTemplate.queryForObject(sqlSkipped.toString(), Integer.class, paramsSkipped.toArray(new Object[0]));
            return previewResult(affected != null ? affected : 0, skipped != null ? skipped : 0);
        } else if (!srcTrans && tgtTrans) {
            // 源是 game 列，目标是翻译列
            String tgtField = getTranslationSrcField(target);
            StringBuilder sqlAffected = new StringBuilder(
                "SELECT COUNT(*) FROM game g LEFT JOIN field_translation ft ON ft.src_table='game' AND ft.src_id=g.id AND ft.src_field=? AND ft.lang='zh' WHERE g.")
                .append(quoteColumn(source)).append(" IS NOT NULL AND ft.trans_value IS NOT NULL");
            StringBuilder sqlSkipped = new StringBuilder(
                "SELECT COUNT(*) FROM game g LEFT JOIN field_translation ft ON ft.src_table='game' AND ft.src_id=g.id AND ft.src_field=? AND ft.lang='zh' WHERE (g.")
                .append(quoteColumn(source)).append(" IS NULL AND ft.trans_value IS NOT NULL) OR (g.")
                .append(quoteColumn(source)).append(" IS NOT NULL AND ft.trans_value IS NULL)");
            List<Object> paramsAffected = new ArrayList<>();
            List<Object> paramsSkipped = new ArrayList<>();
            paramsAffected.add(tgtField);
            paramsSkipped.add(tgtField);
            if (platformId != null) {
                sqlAffected.append(" AND g.platform_id = ?");
                sqlSkipped.append(" AND g.platform_id = ?");
                paramsAffected.add(platformId);
                paramsSkipped.add(platformId);
            }
            Integer affected = jdbcTemplate.queryForObject(sqlAffected.toString(), Integer.class, paramsAffected.toArray(new Object[0]));
            Integer skipped = jdbcTemplate.queryForObject(sqlSkipped.toString(), Integer.class, paramsSkipped.toArray(new Object[0]));
            return previewResult(affected != null ? affected : 0, skipped != null ? skipped : 0);
        } else {
            // 两个都是翻译列：翻訳列は NULL 許可なのでスキップなし
            String srcField = getTranslationSrcField(source);
            String tgtField = getTranslationSrcField(target);
            StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM game g WHERE EXISTS (SELECT 1 FROM field_translation WHERE src_table='game' AND src_id=g.id AND src_field IN (?, ?) AND lang='zh' AND trans_value IS NOT NULL)");
            List<Object> params = new ArrayList<>();
            params.add(srcField);
            params.add(tgtField);
            if (platformId != null) {
                sql.append(" AND g.platform_id = ?");
                params.add(platformId);
            }
            Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray(new Object[0]));
            return previewResult(count != null ? count : 0);
        }
    }

    private Map<String, Object> previewOverwrite(String source, String target, Long platformId) {
        boolean srcTrans = isTranslationColumn(source);
        boolean tgtTrans = isTranslationColumn(target);

        if (!srcTrans && !tgtTrans) {
            // 两个都是 game 表列
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM game WHERE ")
                    .append(quoteColumn(source)).append(" IS NOT NULL AND (")
                    .append(quoteColumn(target)).append(" IS NULL OR ")
                    .append(quoteColumn(target)).append(" = '')");
            List<Object> params = new ArrayList<>();
            if (platformId != null) {
                sql.append(" AND platform_id = ?");
                params.add(platformId);
            }
            Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray(new Object[0]));
            return previewResult(count != null ? count : 0);
        } else if (srcTrans && !tgtTrans) {
            // 源是翻译列，目标是 game 列：目标为空时
            String srcField = getTranslationSrcField(source);
            StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM game g JOIN field_translation ft ON ft.src_table='game' AND ft.src_id=g.id AND ft.src_field=? AND ft.lang='zh' WHERE ft.trans_value IS NOT NULL AND (g.")
                .append(quoteColumn(target)).append(" IS NULL OR g.").append(quoteColumn(target)).append(" = '')");
            List<Object> params = new ArrayList<>();
            params.add(srcField);
            if (platformId != null) {
                sql.append(" AND g.platform_id = ?");
                params.add(platformId);
            }
            Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray(new Object[0]));
            return previewResult(count != null ? count : 0);
        } else if (!srcTrans && tgtTrans) {
            // 源是 game 列，目标是翻译列：目标（翻译）不存在或为空时
            String tgtField = getTranslationSrcField(target);
            StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM game g WHERE g.")
                .append(quoteColumn(source)).append(" IS NOT NULL AND NOT EXISTS (SELECT 1 FROM field_translation ft WHERE ft.src_table='game' AND ft.src_id=g.id AND ft.src_field=? AND ft.lang='zh' AND ft.trans_value IS NOT NULL AND ft.trans_value <> '')");
            List<Object> params = new ArrayList<>();
            params.add(tgtField);
            if (platformId != null) {
                sql.append(" AND g.platform_id = ?");
                params.add(platformId);
            }
            Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray(new Object[0]));
            return previewResult(count != null ? count : 0);
        } else {
            // 两个都是翻译列
            String srcField = getTranslationSrcField(source);
            String tgtField = getTranslationSrcField(target);
            StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM game g WHERE EXISTS (SELECT 1 FROM field_translation WHERE src_table='game' AND src_id=g.id AND src_field=? AND lang='zh' AND trans_value IS NOT NULL) AND NOT EXISTS (SELECT 1 FROM field_translation WHERE src_table='game' AND src_id=g.id AND src_field=? AND lang='zh' AND trans_value IS NOT NULL AND trans_value <> '')");
            List<Object> params = new ArrayList<>();
            params.add(srcField);
            params.add(tgtField);
            if (platformId != null) {
                sql.append(" AND g.platform_id = ?");
                params.add(platformId);
            }
            Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray(new Object[0]));
            return previewResult(count != null ? count : 0);
        }
    }

    private Map<String, Object> previewReplace(String column, String findText, Long platformId) {
        if (isTranslationColumn(column)) {
            String srcField = getTranslationSrcField(column);
            StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM field_translation ft JOIN game g ON g.id = ft.src_id WHERE ft.src_table='game' AND ft.src_field=? AND ft.lang='zh' AND ft.trans_value LIKE CONCAT('%', ?, '%')");
            List<Object> params = new ArrayList<>();
            params.add(srcField);
            params.add(findText);
            if (platformId != null) {
                sql.append(" AND g.platform_id = ?");
                params.add(platformId);
            }
            Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray(new Object[0]));
            return previewResult(count != null ? count : 0);
        } else {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM game WHERE ")
                    .append(quoteColumn(column)).append(" LIKE CONCAT('%', ?, '%')");
            List<Object> params = new ArrayList<>();
            params.add(findText);
            if (platformId != null) {
                sql.append(" AND platform_id = ?");
                params.add(platformId);
            }
            Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray(new Object[0]));
            return previewResult(count != null ? count : 0);
        }
    }

    // --- Execute methods ---

    private Map<String, Object> executeCopy(String source, String target, Long platformId) {
        boolean srcTrans = isTranslationColumn(source);
        boolean tgtTrans = isTranslationColumn(target);

        if (!srcTrans && !tgtTrans) {
            // 两个都是 game 表列
            StringBuilder sql = new StringBuilder("UPDATE game SET ")
                    .append(quoteColumn(target)).append(" = ").append(quoteColumn(source));
            List<Object> params = new ArrayList<>();
            if (platformId != null) {
                sql.append(" WHERE platform_id = ?");
                params.add(platformId);
            }
            return execResult(jdbcTemplate.update(sql.toString(), params.toArray(new Object[0])));
        } else if (srcTrans && !tgtTrans) {
            // 源是翻译列，目标是 game 列
            return executeCopyTransToGame(source, target, platformId);
        } else if (!srcTrans && tgtTrans) {
            // 源是 game 列，目标是翻译列
            return executeCopyGameToTrans(source, target, platformId);
        } else {
            // 两个都是翻译列
            return executeCopyTransToTrans(source, target, platformId);
        }
    }

    private Map<String, Object> executeCopyTransToGame(String source, String target, Long platformId) {
        String srcField = getTranslationSrcField(source);
        // 获取受影响的 game id 列表
        StringBuilder sql = new StringBuilder(
            "SELECT g.id, ft.trans_value FROM game g JOIN field_translation ft ON ft.src_table='game' AND ft.src_id=g.id AND ft.src_field=? AND ft.lang='zh' AND ft.trans_value IS NOT NULL");
        List<Object> params = new ArrayList<>();
        params.add(srcField);
        if (platformId != null) {
            sql.append(" AND g.platform_id = ?");
            params.add(platformId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray(new Object[0]));
        int count = 0;
        for (Map<String, Object> row : rows) {
            Long gameId = ((Number) row.get("id")).longValue();
            String transValue = (String) row.get("trans_value");
            int updated = jdbcTemplate.update("UPDATE game SET " + quoteColumn(target) + " = ? WHERE id = ?", transValue, gameId);
            count += updated;
        }
        return execResult(count);
    }

    private Map<String, Object> executeCopyGameToTrans(String source, String target, Long platformId) {
        String tgtField = getTranslationSrcField(target);
        StringBuilder sql = new StringBuilder("SELECT id, ").append(quoteColumn(source)).append(" AS src_val FROM game WHERE ")
                .append(quoteColumn(source)).append(" IS NOT NULL");
        List<Object> params = new ArrayList<>();
        if (platformId != null) {
            sql.append(" AND platform_id = ?");
            params.add(platformId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray(new Object[0]));
        int count = 0;
        for (Map<String, Object> row : rows) {
            Long gameId = ((Number) row.get("id")).longValue();
            String srcValue = (String) row.get("src_val");
            upsertTranslation(gameId, tgtField, srcValue);
            count++;
        }
        return execResult(count);
    }

    private Map<String, Object> executeCopyTransToTrans(String source, String target, Long platformId) {
        String srcField = getTranslationSrcField(source);
        String tgtField = getTranslationSrcField(target);
        StringBuilder sql = new StringBuilder(
            "SELECT g.id, ft.trans_value FROM game g JOIN field_translation ft ON ft.src_table='game' AND ft.src_id=g.id AND ft.src_field=? AND ft.lang='zh' AND ft.trans_value IS NOT NULL");
        List<Object> params = new ArrayList<>();
        params.add(srcField);
        if (platformId != null) {
            sql.append(" AND g.platform_id = ?");
            params.add(platformId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray(new Object[0]));
        int count = 0;
        for (Map<String, Object> row : rows) {
            Long gameId = ((Number) row.get("id")).longValue();
            String transValue = (String) row.get("trans_value");
            upsertTranslation(gameId, tgtField, transValue);
            count++;
        }
        return execResult(count);
    }

    private Map<String, Object> executeSwap(String source, String target, Long platformId) {
        boolean srcTrans = isTranslationColumn(source);
        boolean tgtTrans = isTranslationColumn(target);

        if (!srcTrans && !tgtTrans) {
            // 两个都是 game 表列：仅互换両列とも非NULL的行，避免 NULL 写入 NOT NULL 列
            StringBuilder sql = new StringBuilder("UPDATE game SET ")
                    .append(quoteColumn(source)).append(" = ").append(quoteColumn(target)).append(", ")
                    .append(quoteColumn(target)).append(" = ").append(quoteColumn(source))
                    .append(" WHERE ").append(quoteColumn(source)).append(" IS NOT NULL AND ")
                    .append(quoteColumn(target)).append(" IS NOT NULL");
            List<Object> params = new ArrayList<>();
            if (platformId != null) {
                sql.append(" AND platform_id = ?");
                params.add(platformId);
            }
            return execResult(jdbcTemplate.update(sql.toString(), params.toArray(new Object[0])));
        } else if (srcTrans && !tgtTrans) {
            // 源是翻译列，目标是 game 列
            return transactionTemplate.execute(status -> executeSwapCrossTable(platformId, target, source));
        } else if (!srcTrans && tgtTrans) {
            // 源是 game 列，目标是翻译列
            return transactionTemplate.execute(status -> executeSwapCrossTable(platformId, source, target));
        } else {
            // 两个都是翻译列
            return transactionTemplate.execute(status -> executeSwapTransToTrans(source, target, platformId));
        }
    }

    /**
     * 跨表 swap：game 列 ↔ 翻译列，按游戏逐行处理保证 1-to-1 对应
     */
    private Map<String, Object> executeSwapCrossTable(Long platformId, String gameColumn, String translationColumn) {
        String srcField = getTranslationSrcField(translationColumn);
        // 获取所有相关游戏 ID
        StringBuilder sql = new StringBuilder("SELECT id FROM game");
        List<Object> params = new ArrayList<>();
        if (platformId != null) {
            sql.append(" WHERE platform_id = ?");
            params.add(platformId);
        }
        List<Long> gameIds = jdbcTemplate.queryForList(sql.toString(), Long.class, params.toArray(new Object[0]));
        int count = 0;
        int skipped = 0;
        for (Long gameId : gameIds) {
            // 1. 读 game 表值
            String gameValue = jdbcTemplate.queryForObject(
                "SELECT " + quoteColumn(gameColumn) + " FROM game WHERE id = ?",
                String.class, gameId);

            // 2. 读 field_translation 值
            String transValue = getTranslationValue(gameId, srcField);

            // 3. 如果任一值为 null，跳过该行（避免将 null 写入 NOT NULL 列）
            if (gameValue == null || transValue == null) {
                logger.debug("跳过 gameId={} 的互换操作，因为存在 null 值 (gameValue={}, transValue={})", gameId, gameValue, transValue);
                skipped++;
                continue;
            }

            // 4. 写 game 表（用翻译值覆盖）
            jdbcTemplate.update(
                "UPDATE game SET " + quoteColumn(gameColumn) + " = ? WHERE id = ?",
                transValue, gameId);

            // 5. 写 field_translation（用 game 值覆盖）
            upsertTranslation(gameId, srcField, gameValue);
            count++;
        }
        if (skipped > 0) {
            logger.info("跨表互换完成：实际互换 {} 行，因 null 值跳过 {} 行", count, skipped);
        }
        return execResult(count, skipped);
    }

    /**
     * 两个翻译列互换
     */
    private Map<String, Object> executeSwapTransToTrans(String source, String target, Long platformId) {
        String srcField = getTranslationSrcField(source);
        String tgtField = getTranslationSrcField(target);
        StringBuilder sql = new StringBuilder("SELECT id FROM game");
        List<Object> params = new ArrayList<>();
        if (platformId != null) {
            sql.append(" WHERE platform_id = ?");
            params.add(platformId);
        }
        List<Long> gameIds = jdbcTemplate.queryForList(sql.toString(), Long.class, params.toArray(new Object[0]));
        int count = 0;
        for (Long gameId : gameIds) {
            String srcValue = getTranslationValue(gameId, srcField);
            String tgtValue = getTranslationValue(gameId, tgtField);
            upsertTranslation(gameId, srcField, tgtValue);
            upsertTranslation(gameId, tgtField, srcValue);
            count++;
        }
        return execResult(count);
    }

    private Map<String, Object> executeOverwrite(String source, String target, Long platformId) {
        boolean srcTrans = isTranslationColumn(source);
        boolean tgtTrans = isTranslationColumn(target);

        if (!srcTrans && !tgtTrans) {
            // 两个都是 game 表列
            StringBuilder sql = new StringBuilder("UPDATE game SET ")
                    .append(quoteColumn(target)).append(" = ").append(quoteColumn(source))
                    .append(" WHERE (").append(quoteColumn(target)).append(" IS NULL OR ")
                    .append(quoteColumn(target)).append(" = '')");
            List<Object> params = new ArrayList<>();
            if (platformId != null) {
                sql.append(" AND platform_id = ?");
                params.add(platformId);
            }
            return execResult(jdbcTemplate.update(sql.toString(), params.toArray(new Object[0])));
        } else if (srcTrans && !tgtTrans) {
            // 源是翻译列，目标是 game 列：仅当 game 列为空时写入
            return executeOverwriteTransToGame(source, target, platformId);
        } else if (!srcTrans && tgtTrans) {
            // 源是 game 列，目标是翻译列：仅当翻译为空时写入
            return executeOverwriteGameToTrans(source, target, platformId);
        } else {
            // 两个都是翻译列
            return executeOverwriteTransToTrans(source, target, platformId);
        }
    }

    private Map<String, Object> executeOverwriteTransToGame(String source, String target, Long platformId) {
        String srcField = getTranslationSrcField(source);
        StringBuilder sql = new StringBuilder(
            "SELECT g.id, ft.trans_value FROM game g JOIN field_translation ft ON ft.src_table='game' AND ft.src_id=g.id AND ft.src_field=? AND ft.lang='zh' WHERE ft.trans_value IS NOT NULL AND (g.")
            .append(quoteColumn(target)).append(" IS NULL OR g.").append(quoteColumn(target)).append(" = '')");
        List<Object> params = new ArrayList<>();
        params.add(srcField);
        if (platformId != null) {
            sql.append(" AND g.platform_id = ?");
            params.add(platformId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray(new Object[0]));
        int count = 0;
        for (Map<String, Object> row : rows) {
            Long gameId = ((Number) row.get("id")).longValue();
            String transValue = (String) row.get("trans_value");
            count += jdbcTemplate.update("UPDATE game SET " + quoteColumn(target) + " = ? WHERE id = ?", transValue, gameId);
        }
        return execResult(count);
    }

    private Map<String, Object> executeOverwriteGameToTrans(String source, String target, Long platformId) {
        String tgtField = getTranslationSrcField(target);
        StringBuilder sql = new StringBuilder(
            "SELECT g.id, g.").append(quoteColumn(source)).append(" AS src_val FROM game g WHERE g.")
            .append(quoteColumn(source)).append(" IS NOT NULL AND NOT EXISTS (SELECT 1 FROM field_translation ft WHERE ft.src_table='game' AND ft.src_id=g.id AND ft.src_field=? AND ft.lang='zh' AND ft.trans_value IS NOT NULL AND ft.trans_value <> '')");
        List<Object> params = new ArrayList<>();
        params.add(tgtField);
        if (platformId != null) {
            sql.append(" AND g.platform_id = ?");
            params.add(platformId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray(new Object[0]));
        int count = 0;
        for (Map<String, Object> row : rows) {
            Long gameId = ((Number) row.get("id")).longValue();
            String srcValue = (String) row.get("src_val");
            upsertTranslation(gameId, tgtField, srcValue);
            count++;
        }
        return execResult(count);
    }

    private Map<String, Object> executeOverwriteTransToTrans(String source, String target, Long platformId) {
        String srcField = getTranslationSrcField(source);
        String tgtField = getTranslationSrcField(target);
        StringBuilder sql = new StringBuilder(
            "SELECT g.id, ft_src.trans_value FROM game g JOIN field_translation ft_src ON ft_src.src_table='game' AND ft_src.src_id=g.id AND ft_src.src_field=? AND ft_src.lang='zh' WHERE ft_src.trans_value IS NOT NULL AND NOT EXISTS (SELECT 1 FROM field_translation ft_tgt WHERE ft_tgt.src_table='game' AND ft_tgt.src_id=g.id AND ft_tgt.src_field=? AND ft_tgt.lang='zh' AND ft_tgt.trans_value IS NOT NULL AND ft_tgt.trans_value <> '')");
        List<Object> params = new ArrayList<>();
        params.add(srcField);
        params.add(tgtField);
        if (platformId != null) {
            sql.append(" AND g.platform_id = ?");
            params.add(platformId);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray(new Object[0]));
        int count = 0;
        for (Map<String, Object> row : rows) {
            Long gameId = ((Number) row.get("id")).longValue();
            String transValue = (String) row.get("trans_value");
            upsertTranslation(gameId, tgtField, transValue);
            count++;
        }
        return execResult(count);
    }

    private Map<String, Object> executeReplace(String column, String findText, String replaceText, Long platformId) {
        if (isTranslationColumn(column)) {
            String srcField = getTranslationSrcField(column);
            StringBuilder sql = new StringBuilder(
                "SELECT ft.id, ft.trans_value, g.id AS game_id FROM field_translation ft JOIN game g ON g.id = ft.src_id WHERE ft.src_table='game' AND ft.src_field=? AND ft.lang='zh' AND ft.trans_value LIKE CONCAT('%', ?, '%')");
            List<Object> params = new ArrayList<>();
            params.add(srcField);
            params.add(findText);
            if (platformId != null) {
                sql.append(" AND g.platform_id = ?");
                params.add(platformId);
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray(new Object[0]));
            int count = 0;
            for (Map<String, Object> row : rows) {
                Long gameId = ((Number) row.get("game_id")).longValue();
                String oldValue = (String) row.get("trans_value");
                String newValue = oldValue.replace(findText, replaceText);
                upsertTranslation(gameId, srcField, newValue);
                count++;
            }
            return execResult(count);
        } else {
            StringBuilder sql = new StringBuilder("UPDATE game SET ")
                    .append(quoteColumn(column)).append(" = REPLACE(").append(quoteColumn(column)).append(", ?, ?)")
                    .append(" WHERE ").append(quoteColumn(column)).append(" LIKE CONCAT('%', ?, '%')");
            List<Object> params = new ArrayList<>();
            params.add(findText);
            params.add(replaceText);
            params.add(findText);
            if (platformId != null) {
                sql.append(" AND platform_id = ?");
                params.add(platformId);
            }
            return execResult(jdbcTemplate.update(sql.toString(), params.toArray(new Object[0])));
        }
    }
}
