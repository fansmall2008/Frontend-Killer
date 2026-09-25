package com.gamelist.controller;

import com.gamelist.model.ExportRequest;
import com.gamelist.model.TemplateV3;
import com.gamelist.service.ExportRuleService;
import com.gamelist.service.ExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/export")
public class ExportController {
    private static final Logger logger = LoggerFactory.getLogger(ExportController.class);

    @Autowired
    private ExportService exportService;

    @Autowired
    private ExportRuleService exportRuleService;

    /**
     * 导出平台
     */
    @PostMapping("/platform")
    public ResponseEntity<Map<String, Object>> exportPlatform(@RequestBody ExportRequest request) {
        try {
            logger.info("Export platform request: {}", request);
            String missingErr = validateTemplateVariables(request.getFrontend(), request.getTemplateVariables());
            if (missingErr != null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", missingErr
                ));
            }
            Map<String, Object> result = exportService.exportPlatform(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Export platform failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Export failed: " + e.getMessage()
            ));
        }
    }

    /**
     * 批量导出平台（单任务顺序执行，避免线程爆炸）
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchExport(@RequestBody ExportRequest request) {
        try {
            List<Long> platformIds = request.getPlatformIds();
            if (platformIds == null || platformIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "No platforms selected"
                ));
            }

            String missingErr = validateTemplateVariables(request.getFrontend(), request.getTemplateVariables());
            if (missingErr != null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", missingErr
                ));
            }

            logger.info("Batch export request: {} platforms, frontend={}", platformIds.size(), request.getFrontend());

            // 委托给 Service 层的批量导出方法（单任务顺序执行）
            Map<String, Object> batchResult = exportService.batchExport(platformIds, request);

            if (Boolean.TRUE.equals(batchResult.get("success"))) {
                return ResponseEntity.ok(batchResult);
            } else {
                return ResponseEntity.badRequest().body(batchResult);
            }
        } catch (Exception e) {
            logger.error("Batch export failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Batch export failed: " + e.getMessage()
            ));
        }
    }

    /**
     * 导出预检：基于 scraped 计数判断关联性风险（不启动导出）。
     * 返回 action：proceed / split(多平台含未刮削→阻断) / suggest_whole_dir(单平台含未刮削)。
     */
    @PostMapping("/preflight")
    public ResponseEntity<Map<String, Object>> preflight(@RequestBody ExportRequest request) {
        try {
            Map<String, Object> result = exportService.preflight(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Preflight failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Preflight failed: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取导出规则列表（v3）
     */
    @GetMapping("/rules")
    public ResponseEntity<Object> getExportRules() {
        try {
            exportRuleService.loadRules();

            List<Map<String, Object>> ruleList = new ArrayList<>();

            for (String frontend : exportRuleService.getV3FrontendKeys()) {
                TemplateV3 v3 = exportRuleService.getV3RuleByFrontend(frontend);
                if (v3 == null) continue;

                Map<String, Object> item = new HashMap<>();
                item.put("frontend", frontend);
                String displayName = (v3.getTemplateInfo() != null && v3.getTemplateInfo().getDescription() != null)
                        ? v3.getTemplateInfo().getDescription() : frontend;
                item.put("name", displayName);
                item.put("version", 3);

                if (v3.getOutput() != null && v3.getOutput().getExportOptions() != null) {
                    TemplateV3.ExportOptions opts = v3.getOutput().getExportOptions();
                    Map<String, Object> exportOptions = new HashMap<>();
                    exportOptions.put("gameFiles", opts.isGameFiles());
                    exportOptions.put("mediaFiles", opts.isMediaFiles());
                    exportOptions.put("gameListFile", opts.isGameListFile());
                    exportOptions.put("showWarning", opts.isShowWarning());
                    item.put("exportOptions", exportOptions);
                }

                // 模板声明的媒体类型列表（源 nomcourt），供刮削模态框"从模板预选"使用
                List<String> mediaTypes = new ArrayList<>();
                if (v3.getOutput() != null && v3.getOutput().getMedia() != null
                        && v3.getOutput().getMedia().getRules() != null) {
                    for (Map.Entry<String, TemplateV3.MediaOutputRule> entry
                            : v3.getOutput().getMedia().getRules().entrySet()) {
                        String nomcourt = entry.getKey();
                        TemplateV3.MediaOutputRule rule = entry.getValue();
                        // key 为源 nomcourt；key 缺失语义时回退到 rule.source
                        if (nomcourt == null || nomcourt.isBlank()) {
                            nomcourt = (rule != null) ? rule.getSource() : null;
                        }
                        if (nomcourt != null && !nomcourt.isBlank() && !mediaTypes.contains(nomcourt)) {
                            mediaTypes.add(nomcourt);
                        }
                    }
                }
                item.put("mediaTypes", mediaTypes);

                // 模板声明的执行前变量（供前端渲染变量设定弹窗）
                List<Map<String, Object>> varDecls = new ArrayList<>();
                for (TemplateV3.TemplateVariable var : v3.getValidVariables()) {
                    varDecls.add(variableToMap(var));
                }
                item.put("variables", varDecls);

                ruleList.add(item);
            }

            return ResponseEntity.ok(ruleList);
        } catch (Exception e) {
            logger.error("Get export rules failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Failed to get export rules: " + e.getMessage()
            ));
        }
    }

    /**
     * 校验模板声明的必填变量是否已由用户填写。
     * @return 缺失时返回错误信息（含缺失变量名），全部满足时返回 null
     */
    private String validateTemplateVariables(String frontend, Map<String, String> userVars) {
        if (frontend == null || frontend.isEmpty()) return null;
        TemplateV3 v3 = exportRuleService.getV3RuleByFrontend(frontend);
        if (v3 == null) return null;
        List<String> missing = new ArrayList<>();
        for (TemplateV3.TemplateVariable var : v3.getValidVariables()) {
            if (!var.isRequired()) continue;
            String value = userVars != null ? userVars.get(var.getName()) : null;
            if (value == null || value.trim().isEmpty()) {
                missing.add(var.getLabel() != null && !var.getLabel().isEmpty() ? var.getLabel() : var.getName());
            }
        }
        if (!missing.isEmpty()) {
            return "请先填写必需的模板变量: " + String.join(", ", missing);
        }
        return null;
    }

    /** 将变量声明转为前端可读的 Map（default 键避免 Java 关键字冲突） */
    private Map<String, Object> variableToMap(TemplateV3.TemplateVariable var) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", var.getName());
        m.put("label", var.getLabel() != null ? var.getLabel() : var.getName());
        m.put("description", var.getDescription());
        m.put("type", var.getType() != null ? var.getType() : "text");
        m.put("default", var.getDefaultValue());
        m.put("required", var.isRequired());
        return m;
    }
}
