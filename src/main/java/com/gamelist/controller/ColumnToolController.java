package com.gamelist.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private static final LinkedHashMap<String, String> COLUMN_ORDER = new LinkedHashMap<>();
    static {
        // 第一组 - 游戏核心信息
        COLUMN_ORDER.put("name", "游戏名称");
        COLUMN_ORDER.put("desc", "描述");
        COLUMN_ORDER.put("translated_name", "翻译名称");
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
        COLUMN_ORDER.put("box2d", "2D盒子");
        COLUMN_ORDER.put("box2d_back", "2D盒子背面");
        COLUMN_ORDER.put("box2d_side", "2D盒子侧面");
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
        COLUMN_ORDER.put("support2d", "支撑2D");
        COLUMN_ORDER.put("support_scan", "支撑扫描");
        COLUMN_ORDER.put("screenmarquee", "屏幕遮罩");
    }

    private static final List<String> ALLOWED_COLUMNS = new ArrayList<>(COLUMN_ORDER.keySet());

    private static final Map<String, String> COLUMN_LABELS = Map.copyOf(COLUMN_ORDER);

    private void validateColumn(String column) {
        if (!ALLOWED_COLUMNS.contains(column)) {
            throw new IllegalArgumentException("不允许的列名: " + column);
        }
    }

    private String quoteColumn(String column) {
        return "\"" + column + "\"";
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

            int affectedRows;

            switch (operation) {
                case "copy":
                    if (sourceColumn == null || sourceColumn.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "copy 操作需要 sourceColumn"));
                    }
                    affectedRows = previewCopy(sourceColumn, targetColumn, platformId);
                    break;
                case "swap":
                    if (sourceColumn == null || sourceColumn.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "swap 操作需要 sourceColumn"));
                    }
                    affectedRows = previewSwap(sourceColumn, targetColumn, platformId);
                    break;
                case "overwrite":
                    if (sourceColumn == null || sourceColumn.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "overwrite 操作需要 sourceColumn"));
                    }
                    affectedRows = previewOverwrite(sourceColumn, targetColumn, platformId);
                    break;
                case "replace":
                    String findText = (String) request.get("findText");
                    if (findText == null || findText.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "replace 操作需要 findText"));
                    }
                    affectedRows = previewReplace(targetColumn, findText, platformId);
                    break;
                default:
                    return ResponseEntity.badRequest()
                            .body(Map.of("success", false, "error", "不支持的操作: " + operation));
            }

            return ResponseEntity.ok(Map.of("success", true, "affectedRows", affectedRows));
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

            int affectedRows;

            switch (operation) {
                case "copy":
                    if (sourceColumn == null || sourceColumn.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "copy 操作需要 sourceColumn"));
                    }
                    affectedRows = executeCopy(sourceColumn, targetColumn, platformId);
                    break;
                case "swap":
                    if (sourceColumn == null || sourceColumn.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "swap 操作需要 sourceColumn"));
                    }
                    affectedRows = executeSwap(sourceColumn, targetColumn, platformId);
                    break;
                case "overwrite":
                    if (sourceColumn == null || sourceColumn.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("success", false, "error", "overwrite 操作需要 sourceColumn"));
                    }
                    affectedRows = executeOverwrite(sourceColumn, targetColumn, platformId);
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
                    affectedRows = executeReplace(targetColumn, findText, replaceText, platformId);
                    break;
                default:
                    return ResponseEntity.badRequest()
                            .body(Map.of("success", false, "error", "不支持的操作: " + operation));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("affectedRows", affectedRows);
            result.put("operation", operation);
            if (sourceColumn != null && !sourceColumn.isBlank()) {
                result.put("sourceColumn", sourceColumn);
            }
            result.put("targetColumn", targetColumn);

            return ResponseEntity.ok(result);
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

    private int previewCopy(String source, String target, Long platformId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM game WHERE ")
                .append(quoteColumn(source)).append(" IS NOT NULL");
        List<Object> params = new ArrayList<>();
        if (platformId != null) {
            sql.append(" AND platform_id = ?");
            params.add(platformId);
        }
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    private int previewSwap(String source, String target, Long platformId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM game WHERE (")
                .append(quoteColumn(source)).append(" IS NOT NULL OR ")
                .append(quoteColumn(target)).append(" IS NOT NULL)");
        List<Object> params = new ArrayList<>();
        if (platformId != null) {
            sql.append(" AND platform_id = ?");
            params.add(platformId);
        }
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    private int previewOverwrite(String source, String target, Long platformId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM game WHERE ")
                .append(quoteColumn(source)).append(" IS NOT NULL AND (")
                .append(quoteColumn(target)).append(" IS NULL OR ")
                .append(quoteColumn(target)).append(" = '')");
        List<Object> params = new ArrayList<>();
        if (platformId != null) {
            sql.append(" AND platform_id = ?");
            params.add(platformId);
        }
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    private int previewReplace(String column, String findText, Long platformId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM game WHERE ")
                .append(quoteColumn(column)).append(" LIKE CONCAT('%', ?, '%')");
        List<Object> params = new ArrayList<>();
        params.add(findText);
        if (platformId != null) {
            sql.append(" AND platform_id = ?");
            params.add(platformId);
        }
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    // --- Execute methods ---

    private int executeCopy(String source, String target, Long platformId) {
        StringBuilder sql = new StringBuilder("UPDATE game SET ")
                .append(quoteColumn(target)).append(" = ").append(quoteColumn(source));
        List<Object> params = new ArrayList<>();
        if (platformId != null) {
            sql.append(" WHERE platform_id = ?");
            params.add(platformId);
        }
        return jdbcTemplate.update(sql.toString(), params.toArray());
    }

    private int executeSwap(String source, String target, Long platformId) {
        StringBuilder sql = new StringBuilder("UPDATE game SET ")
                .append(quoteColumn(source)).append(" = ").append(quoteColumn(target)).append(", ")
                .append(quoteColumn(target)).append(" = ").append(quoteColumn(source));
        List<Object> params = new ArrayList<>();
        if (platformId != null) {
            sql.append(" WHERE platform_id = ?");
            params.add(platformId);
        }
        return jdbcTemplate.update(sql.toString(), params.toArray());
    }

    private int executeOverwrite(String source, String target, Long platformId) {
        StringBuilder sql = new StringBuilder("UPDATE game SET ")
                .append(quoteColumn(target)).append(" = ").append(quoteColumn(source))
                .append(" WHERE (").append(quoteColumn(target)).append(" IS NULL OR ")
                .append(quoteColumn(target)).append(" = '')");
        List<Object> params = new ArrayList<>();
        if (platformId != null) {
            sql.append(" AND platform_id = ?");
            params.add(platformId);
        }
        return jdbcTemplate.update(sql.toString(), params.toArray());
    }

    private int executeReplace(String column, String findText, String replaceText, Long platformId) {
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
        return jdbcTemplate.update(sql.toString(), params.toArray());
    }
}
