package com.gamelist.controller;

import com.gamelist.model.ExportRequest;
import com.gamelist.service.ExportRuleService;
import com.gamelist.service.ExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * 获取导出规则列表
     */
    @GetMapping("/rules")
    public ResponseEntity<Object> getExportRules() {
        try {
            exportRuleService.loadRules();
            return ResponseEntity.ok(exportRuleService.getRuleList());
        } catch (Exception e) {
            logger.error("Get export rules failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Failed to get export rules: " + e.getMessage()
            ));
        }
    }
}
