package com.gamelist.controller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.mapper.GameMapper;
import com.gamelist.mapper.PlatformMapper;
import com.gamelist.model.Game;
import com.gamelist.model.GameVariant;
import com.gamelist.model.Platform;
import com.gamelist.model.VariantGenerateRequest;
import com.gamelist.model.VariantGenerateResult;
import com.gamelist.model.VariantSummary;
import com.gamelist.service.VariantCapabilityService;
import com.gamelist.service.VariantService;
import com.gamelist.variant.ConversionModule;
import com.gamelist.variant.ExternalToolSettings;
import com.gamelist.variant.FormatRegistry;
import com.gamelist.variant.ModuleCatalog;
import com.gamelist.variant.SystemPipelineLibrary;
import com.gamelist.variant.audio.Ffmpeg;
import com.gamelist.variant.module.Chdman;

/**
 * ROM 版本管理接口：能力矩阵、生成、查询、删除、导出弹窗选项。
 */
@RestController
@RequestMapping("/api/variants")
public class VariantController {

    private static final Logger logger = LoggerFactory.getLogger(VariantController.class);

    @Autowired
    private VariantService variantService;

    @Autowired
    private VariantCapabilityService capabilityService;

    @Autowired
    private PlatformMapper platformMapper;

    @Autowired
    private GameMapper gameMapper;

    @Autowired
    private ModuleCatalog moduleCatalog;

    @Autowired
    private SystemPipelineLibrary systemPipelineLibrary;

    @Autowired
    private FormatRegistry formatRegistry;

    @Autowired
    private ExternalToolSettings externalToolSettings;

    @Autowired
    private Chdman chdman;

    @Autowired
    private Ffmpeg ffmpeg;

    // ---------------------------------------------------------------- 外部工具

    /** 外部工具（chdman / ffmpeg）状态：界面配置值、实际生效路径、来源与版本 */
    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> tools() {
        return ResponseEntity.ok(toolsStatus());
    }

    /** 保存外部工具路径（空 = 跟随自动探测），保存后立即重新探测 */
    @PostMapping("/tools")
    public ResponseEntity<Map<String, Object>> saveTools(@RequestBody Map<String, String> body) {
        try {
            if (body != null && body.containsKey("chdmanPath")) {
                externalToolSettings.save(ExternalToolSettings.CHDMAN_PATH, body.get("chdmanPath"));
            }
            if (body != null && body.containsKey("ffmpegPath")) {
                externalToolSettings.save(ExternalToolSettings.FFMPEG_PATH, body.get("ffmpegPath"));
            }
            chdman.refresh();
            ffmpeg.refresh();
            return ResponseEntity.ok(toolsStatus());
        } catch (Exception e) {
            logger.error("保存外部工具路径失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", nvl(e.getMessage())));
        }
    }

    private Map<String, Object> toolsStatus() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("success", true);
        root.put("chdman", toolRow(chdman.locate(), chdman.version(), chdman.source(),
                externalToolSettings.get(ExternalToolSettings.CHDMAN_PATH)));
        root.put("ffmpeg", toolRow(ffmpeg.locate(), ffmpeg.version(), ffmpeg.source(),
                externalToolSettings.get(ExternalToolSettings.FFMPEG_PATH)));
        return root;
    }

    private Map<String, Object> toolRow(Path executable, String version, String source, String configured) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("available", executable != null);
        row.put("path", executable == null ? "" : executable.toString());
        row.put("version", nvl(version));
        row.put("source", nvl(source));
        row.put("configured", nvl(configured));
        return row;
    }

    /** 格式表：前端渲染列头与文案用，避免把 bare/zip/7z 写死在页面里 */
    @GetMapping("/formats")
    public ResponseEntity<Map<String, Object>> formats() {
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (FormatRegistry.Format format : formatRegistry.all()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", format.id());
                row.put("label", format.label());
                row.put("order", format.order());
                row.put("extension", format.extension());
                row.put("generic", format.generic());
                row.put("defaultEnabled", format.defaultEnabled());
                rows.add(row);
            }
            return ResponseEntity.ok(Map.of("success", true, "formats", rows));
        } catch (Exception e) {
            logger.error("读取版本格式表失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", String.valueOf(e.getMessage())));
        }
    }

    /** 产线总览：全局模块目录 + 各系统的候选产线（只读诊断用） */
    @GetMapping("/pipelines")
    public ResponseEntity<Map<String, Object>> pipelines() {
        try {
            List<Map<String, Object>> modules = new ArrayList<>();
            for (ConversionModule.ModuleSpec spec : moduleCatalog.specs()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", spec.id());
                row.put("input", spec.input().code());
                row.put("inputLabel", spec.input().display());
                row.put("output", spec.output().code());
                row.put("outputLabel", spec.output().display());
                row.put("arrow", spec.arrow());
                row.put("cost", spec.cost());
                row.put("lossless", spec.lossless());
                row.put("available", spec.available());
                row.put("note", spec.note());
                row.put("externalDeps", spec.externalDeps());
                modules.add(row);
            }

            List<Map<String, Object>> systems = new ArrayList<>();
            for (SystemPipelineLibrary.Declaration declaration : systemPipelineLibrary.declarations()) {
                List<Map<String, Object>> views = new ArrayList<>();
                for (SystemPipelineLibrary.PipelineView view : systemPipelineLibrary.pipelines(declaration.systemId())) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("format", view.format());
                    row.put("source", view.source().code());
                    row.put("sourceLabel", view.source().display());
                    row.put("status", view.plan().status().name());
                    row.put("pipelineId", view.plan().pipelineId());
                    row.put("chainText", view.plan().chainText());
                    row.put("message", view.plan().message());
                    views.add(row);
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("systemId", declaration.systemId());
                row.put("name", declaration.name());
                row.put("pipelines", views);
                systems.add(row);
            }
            return ResponseEntity.ok(Map.of("success", true, "modules", modules, "systems", systems));
        } catch (Exception e) {
            logger.error("读取产线总览失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", String.valueOf(e.getMessage())));
        }
    }

    /** 实时诊断：探测源文件形态，并给出每个目标格式的产线结论 */
    @GetMapping("/game/{gameId}/diagnose")
    public ResponseEntity<Map<String, Object>> diagnose(@PathVariable Long gameId) {
        try {
            Map<String, Object> body = new LinkedHashMap<>(variantService.diagnose(gameId));
            body.putIfAbsent("success", true);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.error("诊断版本产线失败: gameId={}", gameId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", String.valueOf(e.getMessage())));
        }
    }

    /** 平台级格式体检：逐游戏给出“已有 / 可补 / 不可生成 / 需要修复”的结论，只读不生成 */
    @GetMapping("/platform/{platformId}/survey")
    public ResponseEntity<Map<String, Object>> survey(@PathVariable Long platformId,
                                                      @RequestParam(value = "limit", defaultValue = "0") int limit) {
        try {
            Map<String, Object> body = new LinkedHashMap<>(variantService.survey(platformId, limit));
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.error("平台格式体检失败: platformId={}", platformId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", String.valueOf(e.getMessage())));
        }
    }

    /** 系统能力矩阵（只含合格系统） */
    @GetMapping("/matrix")
    public ResponseEntity<Map<String, Object>> matrix() {
        try {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "systems", capabilityService.getMatrix()));
        } catch (Exception e) {
            logger.error("读取版本能力矩阵失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /** 手动开关某个系统的版本格式 */
    @PutMapping("/matrix/{systemId}/{format}")
    public ResponseEntity<Map<String, Object>> updateCapability(@PathVariable Integer systemId,
                                                               @PathVariable String format,
                                                               @RequestBody Map<String, Object> body) {
        try {
            boolean supported = Boolean.parseBoolean(String.valueOf(body.get("supported")));
            capabilityService.updateCapability(systemId, format, supported);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            logger.error("更新版本能力失败: systemId={}, format={}", systemId, format, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /** 保存系统首选格式 */
    @PutMapping("/matrix/{systemId}/preference")
    public ResponseEntity<Map<String, Object>> savePreference(@PathVariable Integer systemId,
                                                              @RequestBody Map<String, Object> body) {
        try {
            Object format = body.get("format");
            if (format == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "缺少 format"));
            }
            capabilityService.savePreference(systemId, String.valueOf(format));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            logger.error("保存版本偏好失败: systemId={}", systemId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /** 导出弹窗：按系统聚合所选平台的可选格式、偏好与版本数量 */
    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> options(@RequestParam("platformIds") List<Long> platformIds) {
        try {
            Map<Integer, List<Platform>> platformGroups = new LinkedHashMap<>();
            for (Long platformId : platformIds) {
                Platform platform = platformMapper.selectPlatformById(platformId);
                if (platform == null || platform.getSystemId() == null) {
                    continue;
                }
                platformGroups.computeIfAbsent(platform.getSystemId(), k -> new ArrayList<>()).add(platform);
            }

            Map<Integer, Map<String, Object>> matrixBySystem = new LinkedHashMap<>();
            for (Map<String, Object> row : capabilityService.getMatrix()) {
                Object systemId = row.get("systemId");
                if (systemId instanceof Number number) {
                    matrixBySystem.put(number.intValue(), row);
                }
            }

            List<Map<String, Object>> systems = new ArrayList<>();
            for (Map.Entry<Integer, List<Platform>> entry : platformGroups.entrySet()) {
                Integer systemId = entry.getKey();
                Map<String, Object> matrixRow = matrixBySystem.get(systemId);
                if (matrixRow == null) {
                    continue; // 不合格系统不展示
                }

                List<Game> games = new ArrayList<>();
                List<Map<String, Object>> platformRefs = new ArrayList<>();
                for (Platform platform : entry.getValue()) {
                    platformRefs.add(Map.of("id", platform.getId(), "name", nvl(platform.getName())));
                    List<Game> platformGames = gameMapper.selectGamesByPlatformId(platform.getId());
                    if (platformGames != null) {
                        games.addAll(platformGames);
                    }
                }

                int ready = 0;
                int stale = 0;
                int failed = 0;
                for (VariantSummary summary : variantService.getSummaries(games).values()) {
                    ready += summary.getReadyCount();
                    stale += summary.getStaleCount();
                    failed += summary.getFailedCount();
                }

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("systemId", systemId);
                row.put("systemName", nvl((String) matrixRow.get("name")));
                row.put("systemNameEn", nvl((String) matrixRow.get("nameEn")));
                row.put("formats", matrixRow.get("formats"));
                row.put("preferredFormat", matrixRow.get("preferredFormat"));
                row.put("platforms", platformRefs);
                row.put("totalGames", games.size());
                row.put("readyCount", ready);
                row.put("staleCount", stale);
                row.put("failedCount", failed);
                systems.add(row);
            }

            return ResponseEntity.ok(Map.of("success", true, "systems", systems));
        } catch (Exception e) {
            logger.error("读取版本导出选项失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /** 单个游戏的版本列表与摘要 */
    @GetMapping("/game/{gameId}")
    public ResponseEntity<Map<String, Object>> gameVariants(@PathVariable Long gameId) {
        try {
            List<GameVariant> variants = variantService.list(gameId);
            VariantSummary summary = variantService.getSummary(gameId);

            Integer systemId = null;
            String gameName = "";
            Game game = gameMapper.selectGameById(gameId);
            if (game != null) {
                gameName = game.getName() == null ? "" : game.getName();
                if (game.getPlatformId() != null) {
                    Platform platform = platformMapper.selectPlatformById(game.getPlatformId());
                    if (platform != null) {
                        systemId = platform.getSystemId();
                    }
                }
            }
            List<String> supportedFormats = systemId == null
                    ? java.util.Collections.emptyList()
                    : capabilityService.getSupportedFormats(systemId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("gameName", gameName);
            result.put("systemId", systemId);
            result.put("eligible", systemId != null && !supportedFormats.isEmpty());
            result.put("supportedFormats", supportedFormats);
            result.put("variants", variants);
            result.put("summary", summary);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("读取游戏版本失败: gameId={}", gameId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /** 游戏列表批量摘要 */
    @GetMapping("/summaries")
    public ResponseEntity<Map<String, Object>> summaries(@RequestParam("gameIds") List<Long> gameIds) {
        try {
            List<Game> games = new ArrayList<>();
            for (Long gameId : gameIds) {
                Game game = gameMapper.selectGameById(gameId);
                if (game != null) {
                    games.add(game);
                }
            }
            Map<Long, VariantSummary> summaries = variantService.getSummaries(games);
            return ResponseEntity.ok(Map.of("success", true, "summaries", summaries));
        } catch (Exception e) {
            logger.error("读取版本摘要失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /** 同步生成单个游戏的版本 */
    @PostMapping("/game/{gameId}/generate")
    public ResponseEntity<Map<String, Object>> generateForGame(@PathVariable Long gameId,
                                                               @RequestBody(required = false) VariantGenerateRequest request) {
        try {
            List<String> formats = request != null ? request.getFormats() : null;
            boolean force = request != null && request.isForce();
            VariantGenerateResult result = variantService.generateForGame(gameId, formats, force);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", result.getFailed() == 0);
            body.put("result", result);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.error("生成游戏版本失败: gameId={}", gameId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /** 后台生成整个平台的版本 */
    @PostMapping("/platform/{platformId}/generate")
    public ResponseEntity<Map<String, Object>> generateForPlatform(@PathVariable Long platformId,
                                                                   @RequestBody(required = false) VariantGenerateRequest request) {
        try {
            List<String> formats = request != null ? request.getFormats() : null;
            boolean force = request != null && request.isForce();
            Long taskId = variantService.generateForPlatformAsync(platformId, formats, force);
            return ResponseEntity.ok(Map.of("success", true, "taskId", taskId));
        } catch (Exception e) {
            logger.error("生成平台版本失败: platformId={}", platformId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /** 删除单个格式的版本 */
    @DeleteMapping("/game/{gameId}/{format}")
    public ResponseEntity<Map<String, Object>> deleteVariant(@PathVariable Long gameId,
                                                             @PathVariable String format) {
        try {
            boolean deleted = variantService.deleteVariant(gameId, format);
            return ResponseEntity.ok(Map.of("success", deleted));
        } catch (Exception e) {
            logger.error("删除版本失败: gameId={}, format={}", gameId, format, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /** 删除某个游戏的全部版本 */
    @DeleteMapping("/game/{gameId}")
    public ResponseEntity<Map<String, Object>> deleteGameVariants(@PathVariable Long gameId) {
        try {
            int deleted = variantService.deleteByGameId(gameId);
            return ResponseEntity.ok(Map.of("success", true, "deleted", deleted));
        } catch (Exception e) {
            logger.error("删除游戏版本失败: gameId={}", gameId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
