package com.gamelist.controller;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.model.PlatformStatistics;
import com.gamelist.model.ScanRequest;
import com.gamelist.model.ScanResult;
import com.gamelist.model.Statistics;
import com.gamelist.service.GameService;
import com.gamelist.service.PlatformService;
import com.gamelist.xml.GameListParser;
import com.gamelist.xml.GameListXml;

import jakarta.xml.bind.JAXBException;

@RestController
@RequestMapping("/api/gamelist")
public class GameListController {
    
    private static final Logger logger = LoggerFactory.getLogger(GameListController.class);
    
    @Autowired
    private GameService gameService;
    
    @Autowired
    private PlatformService platformService;

    /**
     * 导入游戏列表XML文件
     */
    @PostMapping("/import")
    public ResponseEntity<String> importGameList(@RequestParam("file") MultipartFile file) {
        try {
            // 保存上传的文件到临时目录
            File tempFile = File.createTempFile("gamelist", ".xml");
            file.transferTo(tempFile);
            
            // 解析XML并导入数据库
            GameListXml gameListXml = GameListParser.parseGameList(tempFile);
            gameService.importGamesFromXml(gameListXml);
            
            // 删除临时文件
            tempFile.delete();
            
            return ResponseEntity.ok("游戏列表导入成功！");
        } catch (IOException | JAXBException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("导入失败：" + e.getMessage());
        }
    }
    
    /**
     * 扫描指定路径及其子目录下的gamelist.xml文件并导入
     */
    @PostMapping("/scan")
    public ResponseEntity<ScanResult> scanAndImportGameList(@RequestBody ScanRequest request) {
        try {
            ScanResult result = gameService.scanAndImportGames(request.getPath(), request.getScanDepth());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            ScanResult errorResult = new ScanResult();
            errorResult.setSuccess(false);
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            errorResult.setMessage("扫描过程中发生错误：" + errorMsg);
            errorResult.setFoundFiles(0);
            errorResult.setImportedFiles(0);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }
    
    /**
     * 扫描指定路径及其子目录下的metadata.pegasus.txt文件并导入
     */
    @PostMapping("/scan-pegasus")
    public ResponseEntity<ScanResult> scanAndImportPegasusMetadata(@RequestBody ScanRequest request) {
        try {
            ScanResult result = gameService.scanAndImportPegasusMetadata(request.getPath(), request.getScanDepth());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            ScanResult errorResult = new ScanResult();
            errorResult.setSuccess(false);
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            errorResult.setMessage("扫描过程中发生错误：" + errorMsg);
            errorResult.setFoundFiles(0);
            errorResult.setImportedFiles(0);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }
    
    /**
     * 获取所有平台
     */
    @GetMapping("/platforms")
    public ResponseEntity<List<Platform>> getAllPlatforms() {
        List<Platform> platforms = platformService.getAllPlatforms();
        return ResponseEntity.ok(platforms);
    }
    
    /**
     * 获取所有游戏
     */
    @GetMapping("/games")
    public ResponseEntity<Map<String, Object>> getAllGames(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) List<String> developers,
            @RequestParam(required = false) List<String> genres,
            @RequestParam(required = false) List<String> players,
            @RequestParam(required = false) List<String> scrapeStatuses) {
        try {
            List<Game> games = gameService.getAllGames(search, startDate, endDate, developers, genres, players, scrapeStatuses);
            
            // 处理分页
            int totalElements = games.size();
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);
            int startIndex = (page - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalElements);
            List<Game> paginatedGames = games.subList(startIndex, endIndex);
            
            Map<String, Object> response = new HashMap<>();
            response.put("games", paginatedGames);
            response.put("totalPages", totalPages);
            response.put("totalElements", totalElements);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取所有游戏列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("error", "获取所有游戏列表失败"));
        }
    }
    
    /**
     * 根据平台ID获取游戏
     */
    @GetMapping("/platforms/{platformId}/games")
    public ResponseEntity<Map<String, Object>> getGamesByPlatformId(
            @PathVariable Long platformId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) List<String> developers,
            @RequestParam(required = false) List<String> genres,
            @RequestParam(required = false) List<String> players,
            @RequestParam(required = false) List<String> scrapeStatuses) {
        try {
            List<Game> games = gameService.getGamesByPlatformId(platformId, search, startDate, endDate, developers, genres, players, scrapeStatuses);
            
            // 处理分页
            int totalElements = games.size();
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);
            int startIndex = (page - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalElements);
            List<Game> paginatedGames = games.subList(startIndex, endIndex);
            
            Map<String, Object> response = new HashMap<>();
            response.put("games", paginatedGames);
            response.put("totalPages", totalPages);
            response.put("totalElements", totalElements);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取平台游戏列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("error", "获取平台游戏列表失败"));
        }
    }
    
    /**
     * 获取单个游戏详情
     */
    @GetMapping("/games/{gameId}")
    public ResponseEntity<Game> getGameById(@PathVariable Long gameId) {
        Game game = gameService.getGameById(gameId);
        return game != null ? ResponseEntity.ok(game) : ResponseEntity.notFound().build();
    }
    
    /**
     * 清空所有游戏数据
     */
    @DeleteMapping("/games")
    public ResponseEntity<String> deleteAllGames() {
        int count = gameService.deleteAllGames();
        return ResponseEntity.ok("已删除 " + count + " 个游戏记录");
    }
    
    /**
     * 获取总体统计信息
     */
    @GetMapping("/statistics/overall")
    public ResponseEntity<Statistics> getOverallStatistics() {
        try {
            Statistics stats = gameService.getOverallStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }
    
    /**
     * 获取各平台统计信息
     */
    @GetMapping("/statistics/platforms")
    public ResponseEntity<List<PlatformStatistics>> getPlatformStatistics() {
        try {
            List<PlatformStatistics> stats = gameService.getPlatformStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }
    
    /**
     * 按刮削状态获取游戏列表
     */
    @GetMapping("/games/by-status")
    public ResponseEntity<List<Game>> getGamesByScrapeStatus(
            @RequestParam(value = "platformId", required = false) Long platformId,
            @RequestParam("status") String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        try {
            List<Game> games = gameService.getGamesByScrapeStatus(platformId, status, page, size);
            return ResponseEntity.ok(games);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }
    
    /**
     * 更新游戏信息
     */
    @PutMapping("/games")
    public ResponseEntity<String> updateGame(@RequestBody Game game) {
        try {
            int result = gameService.updateGame(game);
            if (result > 0) {
                return ResponseEntity.ok("游戏信息更新成功！");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("游戏信息更新失败：未找到指定游戏");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("更新失败：" + e.getMessage());
        }
    }

    /**
     * 批量更新游戏信息
     */
    @PutMapping("/games/batch")
    public ResponseEntity<java.util.Map<String, Object>> batchUpdateGames(@RequestBody java.util.Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Object> gameIdsObj = (java.util.List<Object>) request.get("gameIds");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> updates = (java.util.Map<String, Object>) request.get("updates");
            
            if (gameIdsObj == null || gameIdsObj.isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "游戏ID列表不能为空"));
            }
            if (updates == null || updates.isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "更新内容不能为空"));
            }
            
            // 转换gameIds为Long类型
            java.util.List<Long> gameIds = new java.util.ArrayList<>();
            for (Object obj : gameIdsObj) {
                if (obj != null) {
                    if (obj instanceof Integer) {
                        gameIds.add(((Integer) obj).longValue());
                    } else if (obj instanceof Long) {
                        gameIds.add((Long) obj);
                    } else if (obj instanceof String) {
                        try {
                            gameIds.add(Long.parseLong((String) obj));
                        } catch (NumberFormatException e) {
                            // 忽略无效的ID
                        }
                    }
                }
            }
            
            if (gameIds.isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "游戏ID列表为空或包含无效ID"));
            }
            
            int updatedCount = gameService.batchUpdateGames(gameIds, updates);
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("updatedCount", updatedCount);
            result.put("message", "成功更新 " + updatedCount + " 个游戏");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            java.util.Map<String, Object> errorResult = new java.util.HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", "批量更新失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }
    
    /**
     * 迁移游戏到目标平台
     */
    @PutMapping("/games/migrate")
    public ResponseEntity<java.util.Map<String, Object>> migrateGames(@RequestBody java.util.Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Object> gameIdsObj = (java.util.List<Object>) request.get("gameIds");
            Object targetPlatformIdObj = request.get("targetPlatformId");
            
            if (gameIdsObj == null || gameIdsObj.isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "游戏ID列表不能为空"));
            }
            if (targetPlatformIdObj == null) {
                return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "目标平台ID不能为空"));
            }
            
            // 转换gameIds为Long类型
            java.util.List<Long> gameIds = new java.util.ArrayList<>();
            for (Object obj : gameIdsObj) {
                if (obj != null) {
                    if (obj instanceof Integer) {
                        gameIds.add(((Integer) obj).longValue());
                    } else if (obj instanceof Long) {
                        gameIds.add((Long) obj);
                    } else if (obj instanceof String) {
                        try {
                            gameIds.add(Long.parseLong((String) obj));
                        } catch (NumberFormatException e) {
                            // 忽略无效的ID
                        }
                    }
                }
            }
            
            if (gameIds.isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "游戏ID列表为空或包含无效ID"));
            }
            
            // 转换targetPlatformId为Long类型
            Long targetPlatformId = null;
            if (targetPlatformIdObj instanceof Integer) {
                targetPlatformId = ((Integer) targetPlatformIdObj).longValue();
            } else if (targetPlatformIdObj instanceof Long) {
                targetPlatformId = (Long) targetPlatformIdObj;
            } else if (targetPlatformIdObj instanceof String) {
                try {
                    targetPlatformId = Long.parseLong((String) targetPlatformIdObj);
                } catch (NumberFormatException e) {
                    return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "目标平台ID无效"));
                }
            }
            
            if (targetPlatformId == null) {
                return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "目标平台ID无效"));
            }
            
            int migratedCount = gameService.migrateGames(gameIds, targetPlatformId);
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("migratedCount", migratedCount);
            result.put("message", "成功迁移 " + migratedCount + " 个游戏到目标平台");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            java.util.Map<String, Object> errorResult = new java.util.HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", "迁移游戏失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }

    
    /**
     * 根据条件筛选游戏
     */
    @PostMapping("/games/filter")
    public ResponseEntity<Object> filterGames(@RequestBody java.util.Map<String, Object> filterParams) {
        try {
            long count = gameService.getGamesCountByFilter(filterParams);
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("count", count);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("筛选失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取游戏的唯一值，包括开发商、发行商、游戏类型和玩家数量
     */
    @GetMapping("/games/unique-values")
    public ResponseEntity<java.util.Map<String, java.util.List<String>>> getUniqueGameValues() {
        try {
            java.util.Map<String, java.util.List<String>> uniqueValues = gameService.getUniqueGameValues();
            return ResponseEntity.ok(uniqueValues);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }
    
    /**
     * 合盘操作
     */
    @PostMapping("/merge-discs")
    public ResponseEntity<java.util.Map<String, Object>> mergeDiscs(@RequestBody java.util.Map<String, java.util.List<Long>> request) {
        java.util.List<Long> gameIds = request.get("gameIds");
        java.util.Map<String, Object> result = gameService.mergeDiscs(gameIds);
        if (result.containsKey("success") && (boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 新增游戏
     */
    @PostMapping("/games")
    public ResponseEntity<String> addGame(@RequestBody Game game) {
        try {
            int result = gameService.addGame(game);
            if (result > 0) {
                return ResponseEntity.ok("游戏新增成功！");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("游戏新增失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("新增失败：" + e.getMessage());
        }
    }
    
    /**
     * 批量删除游戏
     */
    @DeleteMapping("/games/batch-delete")
    public ResponseEntity<java.util.Map<String, Object>> batchDeleteGames(@RequestBody java.util.Map<String, java.util.List<Long>> request) {
        try {
            java.util.List<Long> gameIds = request.get("gameIds");
            
            if (gameIds == null || gameIds.isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "游戏ID列表不能为空"));
            }
            
            int deletedCount = gameService.batchDeleteGames(gameIds);
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("deletedCount", deletedCount);
            result.put("message", "成功删除 " + deletedCount + " 个游戏");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            java.util.Map<String, Object> errorResult = new java.util.HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", "批量删除失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }
    
    /**
     * 获取导入模板列表
     */
    @GetMapping("/import/templates")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getImportTemplates() {
        try {
            // 读取导入模板目录
            java.io.File templatesDir = new java.io.File("/data/rules/import");
            java.util.List<java.util.Map<String, Object>> templates = new java.util.ArrayList<>();
            
            if (templatesDir.exists() && templatesDir.isDirectory()) {
                java.io.File[] templateFiles = templatesDir.listFiles((dir, name) -> name.endsWith(".json"));
                if (templateFiles != null) {
                    for (java.io.File file : templateFiles) {
                        try {
                            // 解析JSON文件
                            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            java.util.Map<String, Object> templateData = objectMapper.readValue(file, java.util.Map.class);
                            
                            // 创建模板信息对象
                            java.util.Map<String, Object> templateInfo = new java.util.HashMap<>();
                            templateInfo.put("fileName", file.getName());
                            templateInfo.put("name", templateData.getOrDefault("name", file.getName()));
                            templateInfo.put("frontend", templateData.get("frontend"));
                            templateInfo.put("version", templateData.get("version"));
                            templateInfo.put("description", templateData.get("description"));
                            
                            templates.add(templateInfo);
                        } catch (Exception e) {
                            logger.warn("解析模板文件失败: {}", file.getName(), e);
                        }
                    }
                }
            }
            
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            logger.error("获取导入模板列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
