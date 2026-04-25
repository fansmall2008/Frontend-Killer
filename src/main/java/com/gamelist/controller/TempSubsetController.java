package com.gamelist.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.model.Platform;
import com.gamelist.model.TempSubset;
import com.gamelist.model.TempSubsetGame;
import com.gamelist.service.PlatformService;

@RestController
@RequestMapping("/api/temp-subsets")
public class TempSubsetController {

    private static final Logger logger = LoggerFactory.getLogger(TempSubsetController.class);

    @Autowired
    private PlatformService platformService;

    @GetMapping
    public ResponseEntity<List<TempSubset>> getAllTempSubsets() {
        List<TempSubset> subsets = platformService.getAllTempSubsets();
        return new ResponseEntity<>(subsets, getJsonHeaders(), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TempSubset> getTempSubsetById(@PathVariable Long id) {
        TempSubset subset = platformService.getTempSubsetById(id);
        if (subset == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(subset, getJsonHeaders(), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<Object> createTempSubset(@RequestBody Map<String, Object> request) {
        try {
            String name = (String) request.get("name");
            String description = (String) request.get("description");
            // 支持两种参数名：filterParams（前端使用）和filter（兼容旧版本）
            Map<String, Object> filter = (Map<String, Object>) request.get("filterParams");
            if (filter == null) {
                filter = (Map<String, Object>) request.get("filter");
            }
            
            TempSubset tempSubset = platformService.createTempSubset(name, description, filter);
            // 重新获取游戏列表以确保准确性
            List<TempSubsetGame> games = platformService.getTempSubsetGamesBySubsetId(tempSubset.getId());
            int gameCount = games.size();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("subsetId", tempSubset.getId());
            result.put("subsetName", tempSubset.getName());
            result.put("gameCount", gameCount);
            return new ResponseEntity<>(result, getJsonHeaders(), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("创建临时子集失败: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("errorMessage", e.getMessage() != null ? e.getMessage() : "创建临时子集失败");
            return new ResponseEntity<>(errorResult, getJsonHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}/games")
    public ResponseEntity<List<TempSubsetGame>> getTempSubsetGames(@PathVariable Long id) {
        List<TempSubsetGame> games = platformService.getTempSubsetGamesBySubsetId(id);
        return new ResponseEntity<>(games, getJsonHeaders(), HttpStatus.OK);
    }

    @GetMapping("/games/{id}")
    public ResponseEntity<TempSubsetGame> getTempSubsetGameById(@PathVariable Long id) {
        TempSubsetGame game = platformService.getTempSubsetGameById(id);
        if (game == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(game, getJsonHeaders(), HttpStatus.OK);
    }
    
    @GetMapping("/games/original/{originalGameId}")
    public ResponseEntity<TempSubsetGame> getTempSubsetGameByOriginalGameId(@PathVariable Long originalGameId) {
        TempSubsetGame game = platformService.getTempSubsetGameByOriginalGameId(originalGameId);
        if (game == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(game, getJsonHeaders(), HttpStatus.OK);
    }

    @PostMapping("/games/update")
    public ResponseEntity<Object> updateTempSubsetGame(@RequestBody TempSubsetGame game) {
        try {
            platformService.updateTempSubsetGame(game);
            Map<String, Object> result = createSuccessResponse("游戏更新成功");
            return new ResponseEntity<>(result, getJsonHeaders(), HttpStatus.OK);
        } catch (Exception e) {
            Map<String, Object> errorResult = createErrorResponse("更新游戏失败", e.getMessage());
            return new ResponseEntity<>(errorResult, getJsonHeaders(), HttpStatus.OK);
        }
    }

    @PostMapping("/{id}/sync-to-main")
    public ResponseEntity<Object> syncTempSubsetToMain(@PathVariable Long id) {
        try {
            int syncedCount = platformService.syncTempSubsetToMainSet(id);
            Map<String, Object> result = createSuccessResponse("同步成功，共更新 " + syncedCount + " 个游戏");
            return new ResponseEntity<>(result, getJsonHeaders(), HttpStatus.OK);
        } catch (Exception e) {
            Map<String, Object> errorResult = createErrorResponse("同步失败", e.getMessage());
            return new ResponseEntity<>(errorResult, getJsonHeaders(), HttpStatus.OK);
        }
    }

    @PostMapping("/{id}/create-platform")
    public ResponseEntity<Object> createPlatformFromTempSubset(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            String platformName = (String) request.get("platformName");
            Platform platform = platformService.createPlatformFromTempSubset(id, platformName);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("platformId", platform.getId());
            result.put("platformName", platform.getName());
            result.put("message", "平台创建成功");
            return new ResponseEntity<>(result, getJsonHeaders(), HttpStatus.OK);
        } catch (Exception e) {
            Map<String, Object> errorResult = createErrorResponse("创建平台失败", e.getMessage());
            return new ResponseEntity<>(errorResult, getJsonHeaders(), HttpStatus.OK);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteTempSubset(@PathVariable Long id) {
        try {
            platformService.deleteTempSubset(id);
            Map<String, Object> result = createSuccessResponse("临时子集删除成功");
            return new ResponseEntity<>(result, getJsonHeaders(), HttpStatus.OK);
        } catch (Exception e) {
            Map<String, Object> errorResult = createErrorResponse("删除临时子集失败", e.getMessage());
            return new ResponseEntity<>(errorResult, getJsonHeaders(), HttpStatus.OK);
        }
    }
    
    /**
     * 获取JSON响应头，设置UTF-8编码
     */
    private HttpHeaders getJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Content-Type", "application/json;charset=utf-8");
        return headers;
    }
    
    /**
     * 创建成功响应
     */
    private Map<String, Object> createSuccessResponse(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", message);
        return result;
    }
    
    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String defaultMessage, String errorMessage) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", errorMessage != null ? errorMessage : defaultMessage);
        return result;
    }
}
