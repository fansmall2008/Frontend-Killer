package com.gamelist.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.service.DatToolService;

/**
 * 工具箱 DAT 生成器 API：预检摘要 / 启动生成 / 进度 / 停止。
 * 与翻译工具 {@link ToolsController} 同属工具箱，路径独立于 /api/tools/dat。
 */
@RestController
@RequestMapping("/api/tools/dat")
public class DatController {

    private static final Logger logger = LoggerFactory.getLogger(DatController.class);

    @Autowired
    private DatToolService datToolService;

    /** 两来源逐平台预检摘要（总行数/有效行数/systemId 可解析性）。 */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        try {
            return datToolService.meta();
        } catch (Exception e) {
            logger.warn("DAT 预检失败: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        }
    }

    /** 启动生成。body: {source:'rom_db'|'game', platforms:[...], autoImport:false}。 */
    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String taskId = datToolService.start(request);
            result.put("success", true);
            result.put("taskId", taskId);
        } catch (Exception e) {
            logger.warn("启动 DAT 生成失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/progress/{taskId}")
    public Map<String, Object> progress(@PathVariable String taskId) {
        return datToolService.getProgress(taskId);
    }

    @PostMapping("/stop/{taskId}")
    public Map<String, Object> stop(@PathVariable String taskId) {
        datToolService.stop(taskId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }
}
