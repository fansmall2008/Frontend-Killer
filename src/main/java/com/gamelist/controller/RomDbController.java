package com.gamelist.controller;

import com.gamelist.config.RomDbDataInitializer;
import com.gamelist.config.RomDbMatchingConfig;
import com.gamelist.model.BackgroundTask;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.model.RomDb;
import com.gamelist.service.GameService;
import com.gamelist.service.PlatformService;
import com.gamelist.service.RomDbService;
import com.gamelist.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ROM 识别数据库 REST API。
 * 提供统计查询、serial/CRC 匹配、模糊搜索等接口。
 */
@RestController
@RequestMapping("/api/rom-db")
public class RomDbController {

    private static final Logger logger = LoggerFactory.getLogger(RomDbController.class);

    @Autowired(required = false)
    private RomDbService romDbService;

    @Autowired(required = false)
    private GameService gameService;

    @Autowired(required = false)
    private RomDbDataInitializer romDbDataInitializer;

    @Autowired(required = false)
    private RomDbMatchingConfig matchingConfig;

    @Autowired(required = false)
    private PlatformService platformService;

    @Autowired(required = false)
    private TaskService taskService;

    /** 获取 rom_db 总体统计 */
    @GetMapping("/stats")
    public ResponseEntity<Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (romDbService == null) {
            result.put("success", false);
            result.put("message", "RomDbService not available");
            return ResponseEntity.ok(result);
        }
        try {
            result.put("success", true);
            result.put("data", romDbService.getStats());
        } catch (Exception e) {
            logger.error("查询 rom_db 统计失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /** 查询指定 systemId 是否有 rom_db 数据 */
    @GetMapping("/has-data")
    public ResponseEntity<Object> hasData(@RequestParam("systemId") int systemId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (romDbService == null) {
            result.put("success", false);
            result.put("hasData", false);
            result.put("count", 0);
            return ResponseEntity.ok(result);
        }
        try {
            boolean has = romDbService.hasDataForSystem(systemId);
            result.put("success", true);
            result.put("hasData", has);
            if (has) {
                // 返回该系统的记录数（用于前端显示）
                result.put("count", romDbService.getStats().get("totalRecords"));
            } else {
                result.put("count", 0);
            }
        } catch (Exception e) {
            logger.error("查询 rom_db 数据失败: systemId={}", systemId, e);
            result.put("success", false);
            result.put("hasData", false);
            result.put("count", 0);
        }
        return ResponseEntity.ok(result);
    }

    /** 获取各平台数据统计 */
    @GetMapping("/stats/platforms")
    public ResponseEntity<Object> platformStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            result.put("success", true);
            result.put("data", romDbService != null ? romDbService.getPlatformStats() : Collections.emptyList());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /** 按 serial 查询（兼容旧接口） */
    @GetMapping("/lookup/serial")
    public ResponseEntity<Object> lookupBySerial(
            @RequestParam String serial,
            @RequestParam int systemId) {
        return doLookup(serial, systemId);
    }

    /** 统一查找：先按 serial（含 5 级回退），再按 CRC32 */
    @GetMapping("/lookup")
    public ResponseEntity<Object> lookup(
            @RequestParam String keyword,
            @RequestParam int systemId) {
        return doLookup(keyword, systemId);
    }

    private ResponseEntity<Object> doLookup(String keyword, int systemId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if (romDbService == null) {
                result.put("success", false);
                result.put("message", "RomDbService not available");
                return ResponseEntity.ok(result);
            }
            String kw = keyword.trim();
            // L1: 按 serial 查找（5 级回退链）
            RomDb match = romDbService.lookupBySerial(kw, systemId);
            // L2: serial 未命中 + 看起来像 CRC（8 位十六进制）→ 按 CRC 查找
            if (match == null && kw.matches("(?i)[0-9A-F]{8}")) {
                match = romDbService.matchByCrc32(kw.toUpperCase(), systemId);
            }
            if (match != null) {
                result.put("success", true);
                result.put("data", buildMatchResponse(match));
            } else {
                result.put("success", false);
                result.put("message", "未找到匹配记录");
            }
        } catch (Exception e) {
            logger.error("查询失败: keyword={}", keyword, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /** 模糊搜索 */
    @GetMapping("/search")
    public ResponseEntity<Object> search(
            @RequestParam String keyword,
            @RequestParam(required = false) Integer systemId,
            @RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if (romDbService == null) {
                result.put("success", false);
                result.put("message", "RomDbService not available");
                return ResponseEntity.ok(result);
            }
            List<RomDb> matches;
            if (systemId != null && systemId > 0) {
                matches = romDbService.searchBySystemAndName(systemId, keyword, limit);
            } else {
                matches = romDbService.searchByName(keyword, limit);
            }
            List<Map<String, Object>> dataList = new ArrayList<>();
            for (RomDb m : matches) {
                dataList.add(buildMatchResponse(m));
            }
            result.put("success", true);
            result.put("data", dataList);
            result.put("count", dataList.size());
        } catch (Exception e) {
            logger.error("搜索失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /** 重新导入指定平台 */
    @PostMapping("/reimport")
    public ResponseEntity<Object> reimport(@RequestParam String platform) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if (romDbService == null) {
                result.put("success", false);
                result.put("message", "RomDbService not available");
                return ResponseEntity.ok(result);
            }
            int count = romDbService.reimportPlatform(platform);
            result.put("success", true);
            result.put("imported", count);
            result.put("message", count > 0 ? "重新导入 " + count + " 条" : "未找到数据文件");
        } catch (Exception e) {
            logger.error("重新导入失败: {}", platform, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /** 检查系统是否在 rom_db 中有数据 */
    @GetMapping("/check/{systemId}")
    public ResponseEntity<Object> checkSystem(@PathVariable int systemId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // L1: 黑名单检查（绝对禁止）
            if (matchingConfig != null && matchingConfig.isBlacklisted(systemId)) {
                result.put("success", true);
                result.put("supported", false);
                result.put("blacklisted", true);
                result.put("reason", "该系统暂不支持本地匹配");
                return ResponseEntity.ok(result);
            }
            
            // L2: 白名单提示（优先走 serial 路径）
            boolean isWhitelisted = matchingConfig != null && matchingConfig.isWhitelisted(systemId);
            
            // L3: 默认路径（卡带/街机走 CRC32）
            boolean hasData = romDbService != null && romDbService.hasDataForSystem(systemId);
            result.put("success", true);
            result.put("supported", hasData);
            result.put("blacklisted", false);
            result.put("whitelisted", isWhitelisted);
            result.put("matchMethod", isWhitelisted ? "serial_extraction" : (hasData ? "crc32" : "none"));
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 批量匹配（自动判断光盘/卡带路径）。
     * 请求体：{ "gameIds": [1,2,3], "systemId": 58, "language": "ja", "methods": ["crc","serial","name"] }
     */
    @PostMapping("/match")
    public ResponseEntity<Object> batchMatch(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if (romDbService == null) {
                result.put("success", false);
                result.put("message", "RomDbService not available");
                return ResponseEntity.ok(result);
            }
            
            int systemId = ((Number) body.get("systemId")).intValue();
            String language = (String) body.getOrDefault("language", "ja");
            Boolean fileNameExtract = Boolean.TRUE.equals(body.get("fileNameExtract"));
            
            // 解析 methods 参数（优先于 fileNameExtract）
            Set<String> methods = null;
            @SuppressWarnings("unchecked")
            List<String> methodsList = (List<String>) body.get("methods");
            if (methodsList != null && !methodsList.isEmpty()) {
                methods = new java.util.LinkedHashSet<>();
                for (String m : methodsList) {
                    String ml = m.trim().toLowerCase();
                    if (ml.equals("crc") || ml.equals("serial") || ml.equals("name")) {
                        methods.add(ml);
                    }
                }
                if (methods.isEmpty()) methods = null;
            }
            // 兼容旧调用：如果没有 methods 参数，根据 fileNameExtract 决定
            if (methods == null) {
                methods = fileNameExtract ? Set.of("crc", "serial", "name") : Set.of("crc", "serial");
            }
            
            // L1: 黑名单拦截（绝对禁止）
            if (matchingConfig != null && matchingConfig.isBlacklisted(systemId)) {
                result.put("success", false);
                result.put("message", "系统 " + systemId + " 已被列入黑名单，暂不支持本地匹配功能");
                result.put("blacklisted", true);
                return ResponseEntity.badRequest().body(result);
            }
            
            // L2: 解析 gameIds（支持直接传入或从 filters 解析）
            List<Long> gameIds;
            @SuppressWarnings("unchecked")
            List<Number> idNums = (List<Number>) body.get("gameIds");
            if (idNums != null && !idNums.isEmpty()) {
                gameIds = idNums.stream().map(Number::longValue).toList();
            } else if (gameService != null && body.containsKey("filters")) {
                // “应用于所有搜索结果”模式：从 filters 解析 gameIds
                @SuppressWarnings("unchecked")
                Map<String, Object> filters = (Map<String, Object>) body.get("filters");
                Long platformId = filters.get("platformId") != null ? ((Number) filters.get("platformId")).longValue() : null;
                if (platformId == null) {
                    result.put("success", false);
                    result.put("message", "筛选模式需要 platformId");
                    return ResponseEntity.ok(result);
                }
                @SuppressWarnings("unchecked")
                List<String> scrapeStatuses = (List<String>) filters.get("scrapeStatuses");
                @SuppressWarnings("unchecked")
                List<String> fileStatuses = (List<String>) filters.get("fileStatuses");
                @SuppressWarnings("unchecked")
                List<String> developers = (List<String>) filters.get("developers");
                @SuppressWarnings("unchecked")
                List<String> publishers = (List<String>) filters.get("publishers");
                @SuppressWarnings("unchecked")
                List<String> genres = (List<String>) filters.get("genres");
                @SuppressWarnings("unchecked")
                List<String> players = (List<String>) filters.get("players");
                String search = (String) filters.get("search");
                String folderPath = (String) filters.get("folderPath");
                @SuppressWarnings("unchecked")
                List<String> matchedStatuses = (List<String>) filters.get("matchedStatuses");
                List<Game> games = gameService.getGamesByPlatformId(
                    platformId, search, null, null,
                    developers, genres, players,
                    scrapeStatuses, fileStatuses,
                    folderPath, publishers, matchedStatuses
                );
                gameIds = games.stream().map(Game::getId).toList();
                logger.info("rom_db 筛选模式: 匹配到 {} 个游戏", gameIds.size());
            } else {
                result.put("success", false);
                result.put("message", "缺少 gameIds 或 filters 参数");
                return ResponseEntity.ok(result);
            }
            
            // L3: 执行批量匹配
            Map<String, Integer> matchResult = romDbService.batchMatch(gameIds, systemId, language, methods);
            result.put("success", true);
            result.put("data", matchResult);
            result.put("message", String.format("匹配完成：共 %d 个，命中 %d 个，更新 %d 个",
                    matchResult.get("total"), matchResult.get("matched"), matchResult.get("updated")));
        } catch (Exception e) {
            logger.error("rom_db 批量匹配失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 按平台批量匹配（异步后台任务）。
     * 请求体：{ "platformIds": [1,2,3], "language": "ja", "methods": ["crc","serial","name"] }
     */
    @PostMapping("/match-by-platform")
    public ResponseEntity<Object> matchByPlatform(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (romDbService == null || platformService == null || taskService == null) {
            result.put("success", false);
            result.put("message", "Service not available");
            return ResponseEntity.ok(result);
        }
        try {
            @SuppressWarnings("unchecked")
            List<Number> idNums = (List<Number>) body.get("platformIds");
            String language = (String) body.getOrDefault("language", "en");
            Boolean fileNameExtract = Boolean.TRUE.equals(body.get("fileNameExtract"));
            
            // 解析 methods 参数
            Set<String> methods = null;
            @SuppressWarnings("unchecked")
            List<String> methodsList = (List<String>) body.get("methods");
            if (methodsList != null && !methodsList.isEmpty()) {
                methods = new java.util.LinkedHashSet<>();
                for (String m : methodsList) {
                    String ml = m.trim().toLowerCase();
                    if (ml.equals("crc") || ml.equals("serial") || ml.equals("name")) {
                        methods.add(ml);
                    }
                }
                if (methods.isEmpty()) methods = null;
            }
            if (methods == null) {
                methods = fileNameExtract ? Set.of("crc", "serial", "name") : Set.of("crc", "serial", "name");
            }
            if (idNums == null || idNums.isEmpty()) {
                result.put("success", false);
                result.put("message", "缺少 platformIds 参数");
                return ResponseEntity.ok(result);
            }

            List<Long> platformIds = idNums.stream().map(Number::longValue).toList();

            // 黑名单检查
            if (matchingConfig != null) {
                List<String> blacklistedNames = new ArrayList<>();
                for (Long platformId : platformIds) {
                    Platform platform = platformService.getPlatformById(platformId);
                    if (platform != null && platform.getSystemId() != null && platform.getSystemId() > 0) {
                        if (matchingConfig.isBlacklisted(platform.getSystemId())) {
                            blacklistedNames.add(platform.getName() != null ? platform.getName() : ("ID=" + platformId));
                        }
                    }
                }
                if (!blacklistedNames.isEmpty()) {
                    result.put("success", false);
                    result.put("message", "以下系统暂不支持本地匹配功能：" + String.join("、", blacklistedNames));
                    result.put("blacklisted", true);
                    return ResponseEntity.badRequest().body(result);
                }
            }

            // 创建后台任务
            String langLabel = "ja".equals(language) ? "日文" : "cn".equals(language) ? "中文" : "英文";
            BackgroundTask task = taskService.createTask("ROM_DB_MATCH",
                    "本地匹配-" + langLabel + " (" + platformIds.size() + " 个平台)");

            // 异步执行匹配
            romDbService.batchMatchByPlatformsAsync(task.getId(), platformIds, language, methods);

            result.put("success", true);
            result.put("taskId", task.getId());
            result.put("message", "本地匹配任务已启动，可在任务管理中查看进度");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("启动本地匹配任务失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    private Map<String, Object> buildMatchResponse(RomDb romDb) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gameName", romDb.getGameName());
        m.put("serial", romDb.getSerial());
        m.put("crc32", romDb.getCrc32());
        m.put("platform", romDb.getPlatform());
        m.put("systemId", romDb.getSystemId());
        m.put("region", romDb.getRegion());
        m.put("source", romDb.getSource());
        m.put("developer", romDb.getDeveloper());
        m.put("publisher", romDb.getPublisher());
        m.put("releaseyear", romDb.getReleaseyear());
        m.put("genre", romDb.getGenre());
        return m;
    }

    // ======================== 手动导入接口 ========================

    /** 手动触发 DAT 导入（增量合并模式，不会自动清空） */
    @PostMapping("/import-dat")
    public ResponseEntity<Object> importDat() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (romDbDataInitializer == null) {
            result.put("success", false);
            result.put("message", "RomDbDataInitializer not available");
            return ResponseEntity.ok(result);
        }
        boolean started = romDbDataInitializer.importAllDat();
        result.put("success", started);
        result.put("message", started ? "DAT 导入已启动" : "已有导入任务正在运行，请稍后再试");
        return ResponseEntity.ok(result);
    }

    /** 手动触发 RDB 导入（增量合并模式，不会自动清空） */
    @PostMapping("/import-rdb")
    public ResponseEntity<Object> importRdb() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (romDbDataInitializer == null) {
            result.put("success", false);
            result.put("message", "RomDbDataInitializer not available");
            return ResponseEntity.ok(result);
        }
        boolean started = romDbDataInitializer.importAllRdb();
        result.put("success", started);
        result.put("message", started ? "RDB 导入已启动" : "已有导入任务正在运行，请稍后再试");
        return ResponseEntity.ok(result);
    }

    /** 手动清空匹配库（用户主动触发） */
    @PostMapping("/clear")
    public ResponseEntity<Object> clearAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (romDbDataInitializer == null) {
            result.put("success", false);
            result.put("message", "RomDbDataInitializer not available");
            return ResponseEntity.ok(result);
        }
        try {
            int count = romDbDataInitializer.clearAll();
            result.put("success", true);
            result.put("cleared", count);
            result.put("message", count > 0 ? "已清空 " + count + " 条记录" : "匹配库已为空，无需清空");
        } catch (Exception e) {
            logger.error("清空匹配库失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /** 查询导入状态 */
    @GetMapping("/import-status")
    public ResponseEntity<Object> importStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (romDbDataInitializer == null) {
            result.put("success", false);
            result.put("message", "RomDbDataInitializer not available");
            return ResponseEntity.ok(result);
        }
        result.put("success", true);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("running", romDbDataInitializer.isRunning());
        data.put("status", romDbDataInitializer.getStatus());
        data.put("totalImported", romDbDataInitializer.getTotalImported());
        data.put("totalSkipped", romDbDataInitializer.getTotalSkipped());
        data.put("lastResult", romDbDataInitializer.getLastResult());
        data.put("totalFiles", romDbDataInitializer.getTotalFiles());
        data.put("currentFileIndex", romDbDataInitializer.getCurrentFileIndex());
        data.put("currentFileName", romDbDataInitializer.getCurrentFileName());
        data.put("phase", romDbDataInitializer.getPhase());
        data.put("currentMerged", romDbDataInitializer.getCurrentMerged());
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    /** 模糊匹配：获取候选列表 */
    @PostMapping("/fuzzy-candidates")
    public ResponseEntity<Object> fuzzyCandidates(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (romDbService == null) {
            result.put("success", false);
            result.put("message", "RomDbService not available");
            return ResponseEntity.ok(result);
        }
        try {
            @SuppressWarnings("unchecked")
            List<Number> gameIdNums = (List<Number>) body.get("gameIds");
            int systemId = ((Number) body.get("systemId")).intValue();
            if (gameIdNums == null || gameIdNums.isEmpty()) {
                result.put("success", false);
                result.put("message", "gameIds 不能为空");
                return ResponseEntity.ok(result);
            }
            if (gameIdNums.size() > 30) {
                result.put("success", false);
                result.put("message", "每次最多 30 个游戏");
                return ResponseEntity.ok(result);
            }
            List<Long> gameIds = gameIdNums.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());
            List<Map<String, Object>> candidates = romDbService.getFuzzyCandidates(gameIds, systemId);
            result.put("success", true);
            result.put("data", candidates);
        } catch (Exception e) {
            logger.error("模糊匹配候选查询失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /** 模糊匹配：确认选择 */
    @PostMapping("/fuzzy-confirm")
    public ResponseEntity<Object> fuzzyConfirm(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (romDbService == null) {
            result.put("success", false);
            result.put("message", "RomDbService not available");
            return ResponseEntity.ok(result);
        }
        try {
            long gameId = ((Number) body.get("gameId")).longValue();
            long romDbId = ((Number) body.get("romDbId")).longValue();
            boolean ok = romDbService.confirmFuzzyMatch(gameId, romDbId);
            result.put("success", ok);
            if (!ok) result.put("message", "匹配失败：未找到对应记录");
        } catch (Exception e) {
            logger.error("模糊匹配确认失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}
