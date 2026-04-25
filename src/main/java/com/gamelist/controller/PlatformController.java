package com.gamelist.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.model.Platform;
import com.gamelist.service.PlatformService;

@RestController
@RequestMapping("/api/platforms")
public class PlatformController {

    @Autowired
    private PlatformService platformService;

    @GetMapping
    public ResponseEntity<List<Platform>> getAllPlatforms() {
        List<Platform> platforms = platformService.getAllPlatforms();
        return new ResponseEntity<>(platforms, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Platform> getPlatformById(@PathVariable Long id) {
        Platform platform = platformService.getPlatformById(id);
        if (platform == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(platform, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Platform> updatePlatform(@PathVariable Long id, @RequestBody Platform platform) {
        if (!id.equals(platform.getId())) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        Platform updatedPlatform = platformService.updatePlatform(platform);
        return new ResponseEntity<>(updatedPlatform, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlatform(@PathVariable Long id) {
        platformService.deletePlatform(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> addPlatform(@RequestBody Platform platform) {
        try {
            int result = platformService.addPlatform(platform);
            if (result > 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "平台添加成功");
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "平台添加失败");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "平台添加失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @PostMapping("/create-with-games")
    public ResponseEntity<Object> createPlatformWithGames(@RequestBody Map<String, Object> request) {
        try {
            String name = (String) request.get("name");
            Map<String, Object> filter = (Map<String, Object>) request.get("filter");
            
            Platform platform = platformService.createPlatformWithGames(name, filter);
            
            // 计算添加的游戏数量
            int gameCount = platformService.getGameCountByPlatformId(platform.getId());
            
            // 返回成功响应，包含新平台信息
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("platformName", platform.getName());
            result.put("platformId", platform.getId());
            result.put("gameCount", gameCount);
            
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            // 返回错误响应，使用200状态码，便于前端处理
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("errorMessage", e.getMessage());
            return new ResponseEntity<>(errorResult, HttpStatus.OK);
        }
    }
    
    @PostMapping("/merge")
    public ResponseEntity<Object> mergePlatforms(@RequestBody Map<String, Object> request) {
        try {
            // 处理 sourcePlatformIds，确保它是 List<Long> 类型
            List<?> rawPlatformIds = (List<?>) request.get("sourcePlatformIds");
            List<Long> sourcePlatformIds = new java.util.ArrayList<>();
            if (rawPlatformIds != null) {
                for (Object id : rawPlatformIds) {
                    if (id instanceof String) {
                        sourcePlatformIds.add(Long.parseLong((String) id));
                    } else if (id instanceof Long) {
                        sourcePlatformIds.add((Long) id);
                    } else if (id instanceof Integer) {
                        sourcePlatformIds.add(((Integer) id).longValue());
                    }
                }
            }
            
            String newPlatformName = (String) request.get("newPlatformName");
            String newPlatformFolderPath = (String) request.get("newPlatformFolderPath");
            
            Map<String, Object> result = platformService.mergePlatforms(sourcePlatformIds, newPlatformName, newPlatformFolderPath);
            
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            // 返回错误响应，使用200状态码，便于前端处理
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("errorMessage", e.getMessage());
            return new ResponseEntity<>(errorResult, HttpStatus.OK);
        }
    }
    
    @GetMapping("/{id}/statistics")
    public ResponseEntity<Map<String, Object>> getPlatformStatistics(@PathVariable Long id) {
        try {
            Map<String, Object> statistics = platformService.getPlatformStatistics(id);
            return new ResponseEntity<>(statistics, HttpStatus.OK);
        } catch (Exception e) {
            // 返回错误响应，使用200状态码，便于前端处理
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("errorMessage", e.getMessage());
            return new ResponseEntity<>(errorResult, HttpStatus.OK);
        }
    }
}
