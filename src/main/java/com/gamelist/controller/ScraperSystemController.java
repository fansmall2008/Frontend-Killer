package com.gamelist.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.model.ScraperSystem;
import com.gamelist.service.ScraperSystemService;

@RestController
@RequestMapping("/api/scraper-systems")
public class ScraperSystemController {
    
    private static final Logger logger = LoggerFactory.getLogger(ScraperSystemController.class);
    
    @Autowired
    private ScraperSystemService scraperSystemService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllSystems() {
        logger.info("获取所有刮削系统列表");
        Map<String, Object> response = new HashMap<>();
        try {
            List<ScraperSystem> systems = scraperSystemService.getAll();
            response.put("success", true);
            response.put("data", systems);
            response.put("count", systems.size());
        } catch (Exception e) {
            logger.error("获取刮削系统列表失败", e);
            response.put("success", false);
            response.put("message", "获取刮削系统列表失败: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSystemById(@PathVariable Long id) {
        logger.info("获取刮削系统详情: id={}", id);
        Map<String, Object> response = new HashMap<>();
        try {
            ScraperSystem system = scraperSystemService.getById(id);
            if (system != null) {
                response.put("success", true);
                response.put("data", system);
            } else {
                response.put("success", false);
                response.put("message", "刮削系统不存在");
            }
        } catch (Exception e) {
            logger.error("获取刮削系统详情失败", e);
            response.put("success", false);
            response.put("message", "获取刮削系统详情失败: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createSystem(@RequestBody ScraperSystem system) {
        logger.info("创建刮削系统: name={}", system.getName());
        Map<String, Object> response = new HashMap<>();
        try {
            ScraperSystem saved = scraperSystemService.save(system);
            response.put("success", true);
            response.put("data", saved);
            response.put("message", "创建成功");
        } catch (Exception e) {
            logger.error("创建刮削系统失败", e);
            response.put("success", false);
            response.put("message", "创建失败: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateSystem(
            @PathVariable Long id, 
            @RequestBody ScraperSystem system) {
        logger.info("更新刮削系统: id={}, name={}", id, system.getName());
        Map<String, Object> response = new HashMap<>();
        try {
            system.setId(id);
            ScraperSystem updated = scraperSystemService.update(system);
            response.put("success", true);
            response.put("data", updated);
            response.put("message", "更新成功");
        } catch (Exception e) {
            logger.error("更新刮削系统失败", e);
            response.put("success", false);
            response.put("message", "更新失败: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteSystem(@PathVariable Long id) {
        logger.info("删除刮削系统: id={}", id);
        Map<String, Object> response = new HashMap<>();
        try {
            scraperSystemService.deleteById(id);
            response.put("success", true);
            response.put("message", "删除成功");
        } catch (Exception e) {
            logger.error("删除刮削系统失败", e);
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearAllSystems() {
        logger.info("清空所有刮削系统");
        Map<String, Object> response = new HashMap<>();
        try {
            scraperSystemService.clearAll();
            response.put("success", true);
            response.put("message", "清空成功");
        } catch (Exception e) {
            logger.error("清空刮削系统失败", e);
            response.put("success", false);
            response.put("message", "清空失败: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/simple")
    public ResponseEntity<List<ScraperSystem>> getSimpleSystems() {
        logger.info("获取简化刮削系统列表");
        try {
            List<ScraperSystem> systems = scraperSystemService.getAll();
            return ResponseEntity.ok(systems);
        } catch (Exception e) {
            logger.error("获取简化刮削系统列表失败", e);
            return ResponseEntity.ok(new ArrayList<>());
        }
    }
    
    @PostMapping("/scrape")
    public ResponseEntity<Map<String, Object>> scrapeSystem(@RequestBody Map<String, Object> request) {
        logger.info("刮削系统请求");
        Map<String, Object> response = new HashMap<>();
        try {
            Integer systemId = (Integer) request.get("systemId");
            @SuppressWarnings("unchecked")
            List<String> regions = (List<String>) request.get("regions");
            @SuppressWarnings("unchecked")
            List<String> mediaTypes = (List<String>) request.get("mediaTypes");
            
            Map<String, Object> result = scraperSystemService.scrapeSystem(systemId, regions, mediaTypes);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("刮削系统失败", e);
            response.put("success", false);
            response.put("message", "刮削失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    @PostMapping("/scrape-all")
    public ResponseEntity<Map<String, Object>> scrapeSystemAllMedia(@RequestBody Map<String, Object> request) {
        logger.info("刮削系统所有媒体请求");
        Map<String, Object> response = new HashMap<>();
        try {
            Integer systemId = (Integer) request.get("systemId");
            
            Map<String, Object> result = scraperSystemService.scrapeSystemAllMedia(systemId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("刮削系统所有媒体失败", e);
            response.put("success", false);
            response.put("message", "刮削失败: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}