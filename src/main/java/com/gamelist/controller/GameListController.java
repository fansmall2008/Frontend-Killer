package com.gamelist.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
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

import com.gamelist.mapper.GameMapper;
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

    @Autowired
    private GameMapper gameMapper;

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
            @RequestParam(required = false) List<String> publishers,
            @RequestParam(required = false) List<String> genres,
            @RequestParam(required = false) List<String> players,
            @RequestParam(required = false) List<String> scrapeStatuses,
            @RequestParam(required = false) String folderPath,
            @RequestParam(required = false) List<String> matchedStatuses) {
        try {
            List<Game> games = gameService.getAllGames(search, startDate, endDate, developers, genres, players, scrapeStatuses, folderPath, publishers, matchedStatuses);
            
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
            @RequestParam(required = false) List<String> publishers,
            @RequestParam(required = false) List<String> genres,
            @RequestParam(required = false) List<String> players,
            @RequestParam(required = false) List<String> scrapeStatuses,
            @RequestParam(required = false) List<String> fileStatuses,
            @RequestParam(required = false) String folderPath,
            @RequestParam(required = false) List<String> matchedStatuses) {
        try {
            List<Game> games = gameService.getGamesByPlatformId(platformId, search, startDate, endDate, developers, genres, players, scrapeStatuses, fileStatuses, folderPath, publishers, matchedStatuses);
            
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
     * 获取检索筛选选项（开发商、发行商、游戏类型两级树），platformId 可选（不传或 0 为全库）
     */
    @GetMapping("/filter-options")
    public ResponseEntity<?> getFilterOptions(@RequestParam(required = false) Long platformId) {
        try {
            Map<String, Object> options = gameService.getFilterOptions(platformId);
            return ResponseEntity.ok(options);
        } catch (Exception e) {
            logger.error("获取检索筛选选项失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("error", "获取检索筛选选项失败"));
        }
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
            logger.error("获取平台统计信息失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    /**
     * 仅获取单个平台的统计信息（命中 idx_game_platform_scraped，避免全库扫描）
     */
    @GetMapping("/statistics/platforms/{platformId}")
    public ResponseEntity<PlatformStatistics> getPlatformStatisticsById(@PathVariable Long platformId) {
        try {
            PlatformStatistics stats = gameService.getPlatformStatisticsById(platformId);
            if (stats == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("获取平台 {} 统计信息失败", platformId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
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
            // 检查文件存在性并更新 exists 字段
            if (game.getPath() != null && !game.getPath().isEmpty()) {
                java.io.File gameFile = new java.io.File(game.getPath());
                game.setExists(gameFile.exists());
            }
            
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
            
            if (updates == null || updates.isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "更新内容不能为空"));
            }

            // 解析 gameIds（支持直接指定或从 filters 解析）
            java.util.List<Long> gameIds = new java.util.ArrayList<>();
            if (gameIdsObj != null && !gameIdsObj.isEmpty()) {
                for (Object obj : gameIdsObj) {
                    if (obj instanceof Number) gameIds.add(((Number) obj).longValue());
                    else if (obj instanceof String) { try { gameIds.add(Long.parseLong((String) obj)); } catch (NumberFormatException e) { /* skip */ } }
                }
            }
            if (gameIds.isEmpty()) {
                gameIds = resolveGameIdsFromFilters(request);
            }
            if (gameIds == null || gameIds.isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "游戏ID列表不能为空且未提供有效筛选条件"));
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
            
            if (targetPlatformIdObj == null) {
                return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "目标平台ID不能为空"));
            }

            // 解析 gameIds（支持直接指定或从 filters 解析）
            java.util.List<Long> gameIds = new java.util.ArrayList<>();
            if (gameIdsObj != null && !gameIdsObj.isEmpty()) {
                for (Object obj : gameIdsObj) {
                    if (obj instanceof Number) gameIds.add(((Number) obj).longValue());
                    else if (obj instanceof String) { try { gameIds.add(Long.parseLong((String) obj)); } catch (NumberFormatException e) { /* skip */ } }
                }
            }
            if (gameIds.isEmpty()) {
                gameIds = resolveGameIdsFromFilters(request);
            }
            if (gameIds == null || gameIds.isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "游戏ID列表不能为空且未提供有效筛选条件"));
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
     * 按平台切换译文（占位，功能已迁至翻译工具，保留注释避免误删其他逻辑）
     */

    /**
     * 确认当前已抓的 SS 信息对应同一游戏（将 matched 置 true，不改动其它字段）
     */
    @PostMapping("/games/{id}/confirm-match")
    public ResponseEntity<Object> confirmMatch(@PathVariable Long id) {
        try {
            Game game = gameService.getGameById(id);
            if (game == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of(
                        "success", false, "message", "游戏不存在: " + id));
            }
            game.setMatched(true);
            gameService.updateGame(game);
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "匹配已确认"));
        } catch (Exception e) {
            logger.error("确认匹配失败 id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                    "success", false, "message", e.getMessage() == null ? "未知错误" : e.getMessage()));
        }
    }

    /**
     * 将当前游戏标为“以上都不是”： scraped=false, matched=false, ssGameId 清空.
     */
    @PostMapping("/games/{id}/reject-match")
    public ResponseEntity<Object> rejectMatch(@PathVariable Long id) {
        try {
            Game game = gameService.getGameById(id);
            if (game == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of(
                        "success", false, "message", "游戏不存在: " + id));
            }
            game.setMatched(false);
            game.setScraped(false);
            game.setSsGameId(null);
            gameService.updateGame(game);
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "已标为待人工处理"));
        } catch (Exception e) {
            logger.error("拒绝匹配失败 id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                    "success", false, "message", e.getMessage() == null ? "未知错误" : e.getMessage()));
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
     * 合盘操作（mergeName 为用户填写的合盘名称，可为空回退第一个游戏名）
     */
    @PostMapping("/merge-discs")
    public ResponseEntity<java.util.Map<String, Object>> mergeDiscs(@RequestBody java.util.Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        java.util.List<Number> rawIds = (java.util.List<Number>) request.get("gameIds");
        java.util.List<Long> gameIds = rawIds != null ? rawIds.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList()) : null;
        String mergeName = request.get("name") != null ? String.valueOf(request.get("name")) : null;
        java.util.Map<String, Object> result = gameService.mergeDiscs(gameIds, mergeName);
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
    public ResponseEntity<java.util.Map<String, Object>> batchDeleteGames(@RequestBody java.util.Map<String, Object> request) {
        try {
            java.util.List<Long> gameIds = new java.util.ArrayList<>();
            @SuppressWarnings("unchecked")
            java.util.List<Number> rawIds = (java.util.List<Number>) request.get("gameIds");
            if (rawIds != null && !rawIds.isEmpty()) {
                gameIds = rawIds.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());
            }
            if (gameIds.isEmpty()) {
                gameIds = resolveGameIdsFromFilters(request);
            }
            if (gameIds == null || gameIds.isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Collections.singletonMap("error", "游戏ID列表不能为空且未提供有效筛选条件"));
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
            // 读取导入模板目录（兼容 Docker 和本地路径）
            java.io.File templatesDir = new java.io.File(com.gamelist.util.PathUtil.getRulesPath() + "/import");
            java.util.List<java.util.Map<String, Object>> templates = new java.util.ArrayList<>();
            
            if (templatesDir.exists() && templatesDir.isDirectory()) {
                java.io.File[] templateFiles = templatesDir.listFiles((dir, name) -> name.endsWith(".json"));
                if (templateFiles != null) {
                    for (java.io.File file : templateFiles) {
                        try {
                            // 解析JSON文件
                            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            java.util.Map<String, Object> templateData = objectMapper.readValue(file, java.util.Map.class);
                            
                            // 创建模板信息对象（v3 格式）
                            java.util.Map<String, Object> templateInfo = new java.util.HashMap<>();
                            templateInfo.put("fileName", file.getName());
                            
                            // v3 格式：元数据在 templateInfo 块内
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> v3Info = (java.util.Map<String, Object>) templateData.get("templateInfo");
                            if (v3Info != null && v3Info.containsKey("version") && 
                                    Integer.parseInt(v3Info.get("version").toString()) == 3) {
                                templateInfo.put("version", 3);
                                templateInfo.put("name", v3Info.getOrDefault("description", file.getName()));
                                templateInfo.put("frontend", v3Info.get("dataFile"));
                                templateInfo.put("description", v3Info.getOrDefault("description", ""));
                                templateInfo.put("notes", v3Info.getOrDefault("notes", ""));
                                templateInfo.put("direction", v3Info.get("direction"));
                                templateInfo.put("dataFileType", v3Info.get("dataFileType"));

                                // 附加模板声明的执行前变量（供前端渲染变量设定弹窗）
                                com.gamelist.model.TemplateV3 v3Template = com.gamelist.model.TemplateV3.loadFromFile(file);
                                if (v3Template != null) {
                                    java.util.List<java.util.Map<String, Object>> varDecls = new java.util.ArrayList<>();
                                    for (com.gamelist.model.TemplateV3.TemplateVariable var : v3Template.getValidVariables()) {
                                        java.util.Map<String, Object> m = new java.util.HashMap<>();
                                        m.put("name", var.getName());
                                        m.put("label", var.getLabel() != null && !var.getLabel().isEmpty() ? var.getLabel() : var.getName());
                                        m.put("description", var.getDescription());
                                        m.put("type", var.getType() != null ? var.getType() : "text");
                                        m.put("default", var.getDefaultValue());
                                        m.put("required", var.isRequired());
                                        varDecls.add(m);
                                    }
                                    templateInfo.put("variables", varDecls);
                                }
                            }
                            
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

    /**
     * 重新检测指定平台下所有游戏的文件存在性，更新 exists 字段。
     * 直接使用导入时存储的 absolutePath 进行 Files.exists() 检测。
     * 多文件游戏（multiFile=true）则逐行检查多文件文本中的每个文件，全部存在才算存在。
     */
    @PostMapping("/platforms/{platformId}/recheck-exists")
    public ResponseEntity<Map<String, Object>> recheckExists(@PathVariable Long platformId) {
        try {
            List<Game> games = gameMapper.selectGamesByPlatformId(platformId);
            int total = games.size();
            int existCount = 0;
            int missingCount = 0;
            int changedCount = 0;
            int skippedCount = 0;

            // 多文件游戏的相对路径基于平台目录解析
            String platformFolder = null;
            try {
                Platform platform = platformService.getPlatformById(platformId);
                if (platform != null) {
                    platformFolder = platform.getFolderPath();
                }
            } catch (Exception e) {
                logger.warn("获取平台 {} 目录失败: {}", platformId, e.getMessage());
            }

            logger.info("平台 {} 重新检测开始: 游戏数={}, 平台目录={}", platformId, total, platformFolder);

            int gameIndex = 0;
            for (Game game : games) {
                Boolean oldExists = game.getExists();
                gameIndex++;
                boolean exists = false;
                boolean checked = false;

                if (Boolean.TRUE.equals(game.getMultiFile())
                        && game.getMultiFileContent() != null && !game.getMultiFileContent().isEmpty()) {
                    // 多文件游戏：逐行检查多文件文本，全部存在才算存在
                    try {
                        exists = true;
                        for (String line : game.getMultiFileContent().split("\\r?\\n")) {
                            String t = line.trim();
                            if (t.isEmpty() || t.startsWith("#")) continue;
                            if (t.startsWith("./") || t.startsWith(".\\")) {
                                t = t.substring(2);
                            }
                            java.io.File f = new java.io.File(t);
                            if (!f.isAbsolute() && platformFolder != null && !platformFolder.isEmpty()) {
                                f = new java.io.File(platformFolder, t);
                            }
                            if (!Files.exists(f.toPath())) {
                                exists = false;
                                logger.info("[检测] 游戏#{} '{}': 多文件缺失 -> {}", gameIndex, game.getName(), t);
                                break;
                            }
                        }
                        checked = true;
                        logger.info("[检测] 游戏#{} '{}': 多文件检测 exists={}", gameIndex, game.getName(), exists);
                    } catch (Exception e) {
                        logger.info("[检测] 游戏#{} '{}': 多文件检测异常: {}", gameIndex, game.getName(), e.getMessage());
                        checked = true;
                    }
                } else {
                    String absPath = game.getAbsolutePath();
                    if (absPath != null && !absPath.isEmpty()) {
                        String checkPath = absPath.split("\\r?\\n")[0].trim();
                        try {
                            exists = Files.exists(Paths.get(checkPath));
                            checked = true;
                            logger.info("[检测] 游戏#{} '{}': absolutePath='{}' → Files.exists={}",
                                    gameIndex, game.getName(), checkPath, exists);
                        } catch (Exception e) {
                            logger.info("[检测] 游戏#{} '{}': absolutePath='{}' → 异常: {}",
                                    gameIndex, game.getName(), checkPath, e.getMessage());
                            checked = true;
                        }
                    } else {
                        logger.info("[检测] 游戏#{} '{}': absolutePath 为空，跳过", gameIndex, game.getName());
                        skippedCount++;
                    }
                }

                if (checked) {
                    game.setExists(exists);
                    gameMapper.updateGame(game);
                }

                if (exists) {
                    existCount++;
                } else {
                    missingCount++;
                }
                if (checked && (oldExists == null || !oldExists.equals(exists))) {
                    changedCount++;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("total", total);
            result.put("existCount", existCount);
            result.put("missingCount", missingCount);
            result.put("changedCount", changedCount);
            result.put("skippedCount", skippedCount);
            logger.info("平台 {} 文件存在性重新检测完成: 总计={}, 存在={}, 缺失={}, 状态变更={}, 跳过={}",
                    platformId, total, existCount, missingCount, changedCount, skippedCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("重新检测文件存在性失败: platformId={}", platformId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "检测失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 从请求体中的 filters 对象解析出匹配的游戏ID列表（"应用于所有搜索结果"模式）。
     * 返回 null 表示没有 filters 或无匹配。
     */
    @SuppressWarnings("unchecked")
    private List<Long> resolveGameIdsFromFilters(Map<String, Object> request) {
        Map<String, Object> filters = (Map<String, Object>) request.get("filters");
        if (filters == null) return null;

        Number platformIdNum = (Number) filters.get("platformId");
        Long platformId = platformIdNum != null ? platformIdNum.longValue() : null;
        if (platformId == null) return null;

        String search = (String) filters.get("search");
        String startDate = (String) filters.get("startDate");
        String endDate = (String) filters.get("endDate");
        List<String> developers = (List<String>) filters.get("developers");
        List<String> genres = (List<String>) filters.get("genres");
        List<String> players = (List<String>) filters.get("players");
        List<String> scrapeStatuses = (List<String>) filters.get("scrapeStatuses");
        List<String> fileStatuses = (List<String>) filters.get("fileStatuses");
        String folderPath = (String) filters.get("folderPath");
        List<String> publishers = (List<String>) filters.get("publishers");
        @SuppressWarnings("unchecked")
        List<String> matchedStatuses = (List<String>) filters.get("matchedStatuses");

        boolean hasAnyFilter = (search != null && !search.isEmpty())
                || (scrapeStatuses != null && !scrapeStatuses.isEmpty())
                || (fileStatuses != null && !fileStatuses.isEmpty())
                || (matchedStatuses != null && !matchedStatuses.isEmpty())
                || (developers != null && !developers.isEmpty())
                || (publishers != null && !publishers.isEmpty())
                || (genres != null && !genres.isEmpty())
                || (players != null && !players.isEmpty())
                || (folderPath != null && !folderPath.isEmpty());

        if (!hasAnyFilter) {
            // 没有任何筛选条件 = 操作整个平台
            List<Game> allGames = gameService.getGamesByPlatformId(platformId);
            List<Long> ids = new ArrayList<>();
            for (Game g : allGames) ids.add(g.getId());
            return ids;
        }

        List<Game> games = gameService.getGamesByPlatformId(
            platformId, search, startDate, endDate,
            developers, genres, players,
            scrapeStatuses, fileStatuses,
            folderPath, publishers, matchedStatuses);
        List<Long> ids = new ArrayList<>();
        for (Game g : games) ids.add(g.getId());
        logger.info("筛选模式解析: 匹配到 {} 个游戏", ids.size());
        return ids;
    }
}
