package com.gamelist.controller;

import com.gamelist.model.ExportRequest;
import com.gamelist.model.ExportRule;
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
     * 获取导出规则列表（v2 + v3 合并）
     */
    @GetMapping("/rules")
    public ResponseEntity<Object> getExportRules() {
        try {
            exportRuleService.loadRules();

            // 合并 v2 + v3 模板为统一格式
            List<Map<String, Object>> mergedList = new ArrayList<>();

            // v2 规则
            List<ExportRule> v2Rules = exportRuleService.getRuleList();
            for (ExportRule rule : v2Rules) {
                // 如果同名 v3 模板存在，跳过 v2（v3 优先）
                if (exportRuleService.isV3Template(rule.getFrontend())) {
                    continue;
                }
                Map<String, Object> item = new HashMap<>();
                item.put("frontend", rule.getFrontend());
                item.put("name", rule.getName());
                item.put("version", 2);
                mergedList.add(item);
            }

            // v3 规则
            for (String frontend : exportRuleService.getV3FrontendKeys()) {
                TemplateV3 v3 = exportRuleService.getV3RuleByFrontend(frontend);
                if (v3 == null) continue;

                Map<String, Object> item = new HashMap<>();
                item.put("frontend", frontend);
                // 使用 description 或 frontend 名作为显示名
                String displayName = (v3.getTemplateInfo() != null && v3.getTemplateInfo().getDescription() != null)
                        ? v3.getTemplateInfo().getDescription() : frontend;
                item.put("name", displayName);
                item.put("version", 3);

                // 提取 exportOptions
                if (v3.getOutput() != null && v3.getOutput().getExportOptions() != null) {
                    TemplateV3.ExportOptions opts = v3.getOutput().getExportOptions();
                    Map<String, Object> exportOptions = new HashMap<>();
                    exportOptions.put("gameFiles", opts.isGameFiles());
                    exportOptions.put("mediaFiles", opts.isMediaFiles());
                    exportOptions.put("gameListFile", opts.isGameListFile());
                    exportOptions.put("showWarning", opts.isShowWarning());
                    item.put("exportOptions", exportOptions);
                }

                mergedList.add(item);
            }

            return ResponseEntity.ok(mergedList);
        } catch (Exception e) {
            logger.error("Get export rules failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Failed to get export rules: " + e.getMessage()
            ));
        }
    }
}
