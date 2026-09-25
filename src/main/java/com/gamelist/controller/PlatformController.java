package com.gamelist.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.gamelist.service.SystemMatchService;

@RestController
@RequestMapping("/api/platforms")
public class PlatformController {

    private static final Logger logger = LoggerFactory.getLogger(PlatformController.class);

    @Autowired
    private PlatformService platformService;

    @Autowired
    private SystemMatchService systemMatchService;

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
        platform.setId(id);
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
            Boolean overwrite = (Boolean) request.get("overwrite");
            
            Map<String, Object> result = platformService.mergePlatforms(sourcePlatformIds, newPlatformName, newPlatformFolderPath, overwrite);
            
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
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("errorMessage", e.getMessage());
            return new ResponseEntity<>(errorResult, HttpStatus.OK);
        }
    }
    
    @PostMapping("/{id}/scrape")
    public ResponseEntity<Map<String, Object>> scrapePlatform(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            Integer systemId = (Integer) request.get("systemId");
            String region = (String) request.get("region");
            @SuppressWarnings("unchecked")
            List<String> mediaTypes = (List<String>) request.get("mediaTypes");
            
            Map<String, Object> result = platformService.scrapePlatform(id, systemId, region, mediaTypes);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("errorMessage", e.getMessage());
            return new ResponseEntity<>(errorResult, HttpStatus.OK);
        }
    }

    /**
     * TODO #9：平台未绑定系统时，返回智能匹配的候选系统列表。
     * 返回 {success, bound, terms, candidates}，bound=true 表示已绑定无需弹窗。
     */
    @GetMapping("/{id}/match-systems")
    public ResponseEntity<Map<String, Object>> matchSystems(@PathVariable Long id) {
        Platform platform = platformService.getPlatformById(id);
        if (platform == null) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("message", "平台不存在");
            return new ResponseEntity<>(errorResult, HttpStatus.NOT_FOUND);
        }
        Map<String, Object> result = systemMatchService.matchSystems(platform);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    /**
     * TODO #9：将平台绑定到指定刮削系统；若该系统媒体未刮削，自动发起系统媒体刮削。
     */
    @PostMapping("/{id}/bind-system")
    public ResponseEntity<Map<String, Object>> bindSystem(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        Object rawSystemId = request.get("systemId");
        Integer systemId = null;
        if (rawSystemId instanceof Number) {
            systemId = ((Number) rawSystemId).intValue();
        } else if (rawSystemId instanceof String) {
            try {
                systemId = Integer.valueOf((String) rawSystemId);
            } catch (NumberFormatException ignored) {
                // systemId 无效，交给 service 校验
            }
        }
        Map<String, Object> result = platformService.bindSystem(id, systemId);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

}
