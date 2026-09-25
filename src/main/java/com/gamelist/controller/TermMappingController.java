package com.gamelist.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.model.TermMapping;
import com.gamelist.service.TermMappingService;

/**
 * 方言映射管理 API（TODO #10）。
 * 供系统设置页的方言映射管理 UI 使用；变更后模板 map() 函数立即生效。
 */
@RestController
@RequestMapping("/api/term-mappings")
public class TermMappingController {

    private static final Logger logger = LoggerFactory.getLogger(TermMappingController.class);

    private final TermMappingService termMappingService;

    public TermMappingController(TermMappingService termMappingService) {
        this.termMappingService = termMappingService;
    }

    /** 查询映射列表；category 为空时返回全部 */
    @GetMapping
    public ResponseEntity<Object> list(@RequestParam(value = "category", required = false) String category) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<TermMapping> mappings = category != null && !category.trim().isEmpty()
                    ? termMappingService.listByCategory(category.trim())
                    : termMappingService.listAll();
            result.put("success", true);
            result.put("data", mappings);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("查询方言映射失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /** 新增映射 {sourceTerm, targetTerm, category} */
    @PostMapping
    public ResponseEntity<Object> add(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String error = termMappingService.addMapping(
                    body.get("sourceTerm"), body.get("targetTerm"), body.get("category"));
            if (error != null) {
                result.put("success", false);
                result.put("message", error);
                return ResponseEntity.ok(result);
            }
            result.put("success", true);
            result.put("message", "映射已添加，模板 map() 已生效");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("新增方言映射失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /** 删除映射 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            termMappingService.deleteMapping(id);
            result.put("success", true);
            result.put("message", "映射已删除");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("删除方言映射失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
}
