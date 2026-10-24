package com.gamelist.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gamelist.mapper.GameMapper;
import com.gamelist.mapper.GameVariantMapper;
import com.gamelist.mapper.PlatformMapper;
import com.gamelist.model.BackgroundTask;
import com.gamelist.model.Game;
import com.gamelist.model.GameVariant;
import com.gamelist.model.Platform;
import com.gamelist.model.VariantGenerateResult;
import com.gamelist.model.VariantSummary;
import com.gamelist.service.TaskService;
import com.gamelist.service.VariantCapabilityService;
import com.gamelist.service.VariantService;
import com.gamelist.util.ArchiveException;
import com.gamelist.util.ArchiveUtil;
import com.gamelist.variant.ArtifactKind;
import com.gamelist.variant.ConversionModule;
import com.gamelist.variant.DiscArtifact;
import com.gamelist.variant.DiscImageProbe;
import com.gamelist.variant.PipelinePlan;
import com.gamelist.variant.PipelinePlanner;
import com.gamelist.variant.PipelineResult;
import com.gamelist.variant.PipelineRunner;
import com.gamelist.variant.SystemPipelineLibrary;

@Service
public class VariantServiceImpl implements VariantService {

    private static final Logger logger = LoggerFactory.getLogger(VariantServiceImpl.class);

    private static final int MAX_ERROR_MESSAGE = 1000;
    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_RESULT_MESSAGES = 200;
    /** 体检单次最多探测的游戏数，避免几千条的平台把请求拖死 */
    private static final int MAX_SURVEY_GAMES = 1000;

    @Value("${app.variants.path:./data/variants}")
    private String variantRoot;

    @Autowired
    private GameMapper gameMapper;

    @Autowired
    private PlatformMapper platformMapper;

    @Autowired
    private GameVariantMapper variantMapper;

    @Autowired
    private VariantCapabilityService capabilityService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private DiscImageProbe discImageProbe;

    @Autowired
    private PipelinePlanner pipelinePlanner;

    @Autowired
    private PipelineRunner pipelineRunner;

    @Autowired
    private SystemPipelineLibrary systemPipelineLibrary;

    @Override
    public List<GameVariant> list(Long gameId) {
        if (gameId == null) {
            return List.of();
        }
        return variantMapper.selectByGameId(gameId);
    }

    @Override
    public VariantSummary getSummary(Long gameId) {
        if (gameId == null) {
            return new VariantSummary();
        }
        Game game = gameMapper.selectGameById(gameId);
        if (game == null) {
            return new VariantSummary();
        }
        return getSummaries(List.of(game)).getOrDefault(gameId, new VariantSummary());
    }

    @Override
    public Map<Long, VariantSummary> getSummaries(List<Game> games) {
        Map<Long, VariantSummary> summaries = new LinkedHashMap<>();
        if (games == null || games.isEmpty()) {
            return summaries;
        }
        List<Long> ids = new ArrayList<>();
        for (Game game : games) {
            if (game != null && game.getId() != null) {
                ids.add(game.getId());
            }
        }
        if (ids.isEmpty()) {
            return summaries;
        }

        Map<Long, List<GameVariant>> rowsByGame = new HashMap<>();
        for (GameVariant variant : variantMapper.selectByGameIds(ids)) {
            rowsByGame.computeIfAbsent(variant.getGameId(), k -> new ArrayList<>()).add(variant);
        }

        for (Game game : games) {
            if (game == null || game.getId() == null) {
                continue;
            }
            VariantSummary summary = new VariantSummary();
            Path source = resolveSourcePath(game);
            Long sourceSize = null;
            Long sourceMtime = null;
            if (source != null && Files.isRegularFile(source)) {
                try {
                    sourceSize = Files.size(source);
                    sourceMtime = Files.getLastModifiedTime(source).toMillis();
                } catch (IOException e) {
                    logger.debug("读取主文件状态失败: {} ({})", source, e.getMessage());
                }
            }

            for (GameVariant variant : rowsByGame.getOrDefault(game.getId(), List.of())) {
                Path file = variantFile(variant);
                boolean exists = file != null && Files.isRegularFile(file);
                String status = variant.getStatus() == null ? "" : variant.getStatus().trim();
                if (STATUS_READY.equalsIgnoreCase(status)) {
                    if (!exists) {
                        summary.setFailedCount(summary.getFailedCount() + 1);
                    } else if (sourceSize != null && sourceMtime != null
                            && (!Objects.equals(variant.getSourceSize(), sourceSize)
                            || !Objects.equals(variant.getSourceMtime(), sourceMtime))) {
                        summary.setStaleCount(summary.getStaleCount() + 1);
                    } else {
                        summary.setReadyCount(summary.getReadyCount() + 1);
                        summary.getFormats().add(variant.getFormat());
                    }
                } else if (STATUS_STALE.equalsIgnoreCase(status)) {
                    summary.setStaleCount(summary.getStaleCount() + 1);
                } else {
                    summary.setFailedCount(summary.getFailedCount() + 1);
                }
            }
            summaries.put(game.getId(), summary);
        }
        return summaries;
    }

    @Override
    public VariantGenerateResult generateForGame(Long gameId, List<String> formats, boolean force) {
        Game game = gameId != null ? gameMapper.selectGameById(gameId) : null;
        if (game == null) {
            VariantGenerateResult result = new VariantGenerateResult();
            result.addFailed("游戏不存在: " + gameId);
            return result;
        }
        Platform platform = game.getPlatformId() != null
                ? platformMapper.selectPlatformById(game.getPlatformId()) : null;
        return generateInternal(game, platform, formats, force);
    }

    @Override
    public VariantGenerateResult generateForPlatform(Long platformId, List<String> formats, boolean force) {
        return generateForPlatform(platformId, formats, force, null);
    }

    @Override
    public Long generateForPlatformAsync(Long platformId, List<String> formats, boolean force) {
        Platform platform = platformId != null ? platformMapper.selectPlatformById(platformId) : null;
        String platformName = platform != null && platform.getName() != null
                ? platform.getName() : String.valueOf(platformId);
        BackgroundTask task = taskService.createTask("variant", "生成 ROM 副本: " + platformName, true);

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "variant-generate-" + task.getId());
            thread.setDaemon(true);
            return thread;
        });
        executor.submit(() -> {
            try {
                taskService.updateTaskProgress(task.getId(), 0, "开始生成 ROM 副本", 0, 100);
                VariantGenerateResult result = generateForPlatform(platformId, formats, force, task.getId());
                String message = String.format("副本生成完成: 成功 %d, 跳过 %d, 失败 %d",
                        result.getSuccess(), result.getSkipped(), result.getFailed());
                // “一个都没生成”不是成功：用警告色把原因顶到通知栏，不打绿勾
                String notificationType = "success";
                if (result.getSuccess() == 0 && result.getSkipped() > 0) {
                    notificationType = "warning";
                    message += "；没有任何副本生成，原因见结果详情";
                } else if (result.getFailed() > 0) {
                    notificationType = "warning";
                }
                taskService.completeTask(task.getId(), message, buildResultDetail(result), notificationType);
            } catch (Exception e) {
                logger.error("生成 ROM 副本失败: platformId={}", platformId, e);
                taskService.failTask(task.getId(), "生成 ROM 副本失败", e.getMessage());
            }
        });
        executor.shutdown();
        return task.getId();
    }

    private VariantGenerateResult generateForPlatform(Long platformId, List<String> formats,
                                                      boolean force, Long taskId) {
        VariantGenerateResult result = new VariantGenerateResult();
        Platform platform = platformId != null ? platformMapper.selectPlatformById(platformId) : null;
        if (platform == null) {
            result.addFailed("平台不存在: " + platformId);
            return result;
        }
        List<Game> games = gameMapper.selectGamesByPlatformId(platformId);
        if (games == null || games.isEmpty()) {
            result.addSkipped("平台下没有游戏: " + platform.getName());
            return result;
        }

        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread thread = new Thread(r, "variant-gen-" + platformId);
            thread.setDaemon(true);
            return thread;
        });
        List<Future<VariantGenerateResult>> futures = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger();
        int total = games.size();
        try {
            for (Game game : games) {
                futures.add(pool.submit(() -> {
                    VariantGenerateResult single = generateInternal(game, platform, formats, force);
                    int done = completed.incrementAndGet();
                    if (taskId != null && total > 0) {
                        taskService.updateTaskProgress(taskId, done * 100 / total,
                                "生成副本 " + done + "/" + total, done, total);
                    }
                    return single;
                }));
            }
        } finally {
            pool.shutdown();
        }

        for (Future<VariantGenerateResult> future : futures) {
            try {
                merge(result, future.get());
            } catch (Exception e) {
                result.addFailed("生成任务异常: " + e.getMessage());
            }
        }
        logger.info("平台副本生成完成: platform={}, 成功={}, 跳过={}, 失败={}",
                platform.getName(), result.getSuccess(), result.getSkipped(), result.getFailed());
        return result;
    }

    private VariantGenerateResult generateInternal(Game game, Platform platform,
                                                   List<String> formats, boolean force) {
        VariantGenerateResult result = new VariantGenerateResult();
        if (game == null || game.getId() == null) {
            result.addFailed("游戏记录无效");
            return result;
        }
        String displayName = game.getName() != null ? game.getName() : String.valueOf(game.getId());
        if (Boolean.TRUE.equals(game.getMultiFile())) {
            result.addSkipped(displayName + ": 多文件游戏不支持副本");
            return result;
        }

        Integer systemId = platform != null ? platform.getSystemId() : null;
        if (systemId == null) {
            result.addSkipped(displayName + ": 平台未关联系统");
            return result;
        }

        List<String> targets = normalizeFormats(formats, systemId);
        if (targets.isEmpty()) {
            result.addSkipped(displayName + ": 系统不支持所选副本格式");
            return result;
        }

        Path source = resolveSourcePath(game);
        if (source == null || !Files.isRegularFile(source)) {
            result.addFailed(displayName + ": 主文件不存在");
            return result;
        }

        long sourceSize;
        long sourceMtime;
        try {
            sourceSize = Files.size(source);
            sourceMtime = Files.getLastModifiedTime(source).toMillis();
        } catch (IOException e) {
            result.addFailed(displayName + ": 读取主文件失败 (" + e.getMessage() + ")");
            return result;
        }

        Map<String, GameVariant> existingByFormat = new HashMap<>();
        for (GameVariant existing : variantMapper.selectByGameId(game.getId())) {
            existingByFormat.put(lower(existing.getFormat()), existing);
        }

        List<String> pending = new ArrayList<>();
        for (String format : targets) {
            GameVariant existing = existingByFormat.get(format);
            if (!force && isFreshReady(existing, sourceSize, sourceMtime)) {
                result.addSkipped(displayName + ": " + format + " 已存在");
            } else {
                pending.add(format);
            }
        }
        if (pending.isEmpty()) {
            return result;
        }

        DiscArtifact sourceArtifact = discImageProbe.probe(source);

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("fk-variant-");
            BareSource bare = null;
            Exception bareFailure = null;
            for (String format : pending) {
                try {
                    // 镜像格式先做可行性预检：把“做不到”的结论和“转换出错”分开
                    String blocked = precheckFormat(platform, format, sourceArtifact);
                    if (blocked != null) {
                        result.addSkipped(displayName + " [" + format + "]: " + blocked);
                        continue;
                    }
                    if (needsBare(format)) {
                        if (bareFailure != null) {
                            throw bareFailure;
                        }
                        if (bare == null) {
                            try {
                                bare = prepareBare(game, source, workDir);
                            } catch (Exception e) {
                                bareFailure = e;
                                throw e;
                            }
                        }
                    }
                    GameVariant generated = buildVariant(game, platform, format, bare, sourceArtifact,
                            workDir, sourceSize, sourceMtime);
                    save(generated, existingByFormat.get(format));
                    result.addSuccess(null);
                } catch (Exception e) {
                    String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    handleFailure(game, platform, format, existingByFormat.get(format),
                            sourceSize, sourceMtime, message);
                    result.addFailed(displayName + " [" + format + "]: " + message);
                }
            }
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            for (String format : pending) {
                handleFailure(game, platform, format, existingByFormat.get(format),
                        sourceSize, sourceMtime, message);
                result.addFailed(displayName + " [" + format + "]: " + message);
            }
        } finally {
            if (workDir != null) {
                ArchiveUtil.deleteRecursively(workDir);
            }
        }
        return result;
    }

    /** 镜像格式的可行性预检：在落盘前把结论说清楚，不把“没产线”记成“转换失败” */
    private String precheckFormat(Platform platform, String format, DiscArtifact source) {
        TargetDiagnosis diagnosis = diagnoseTarget(platform, format, source);
        // DIRECT（非镜像格式）与 READY（产线就绪）才允许继续；SAME 也按“无需生成”跳过
        if ("DIRECT".equals(diagnosis.status()) || "READY".equals(diagnosis.status())) {
            return null;
        }
        return diagnosis.message() == null ? diagnosis.status() : diagnosis.message();
    }

    /**
     * 单个 (游戏, 目标格式) 的判定。生成链路与平台体检共用同一套口径，避免“诊断说能、生成说不能”。
     * <p>
     * 状态：DIRECT 直接打包 / READY 产线就绪 / SAME 源即目标 / INCOMPLETE 缺模块 /
     * UNSUPPORTED 无产线 / BLOCKED 源不可用。message 非空即表示生成时会被跳过。
     */
    private TargetDiagnosis diagnoseTarget(Platform platform, String format, DiscArtifact source) {
        ArtifactKind target = SystemPipelineLibrary.targetKind(format);
        if (target == null) {
            return new TargetDiagnosis("DIRECT", "直接打包，不走流水线", null, null, null);
        }
        if (source == null || source.kind() == ArtifactKind.UNKNOWN) {
            return new TargetDiagnosis("BLOCKED", "无法识别源文件形态，不能生成 " + format, null, null, null);
        }
        if (source.kind() == ArtifactKind.ARCHIVE) {
            return new TargetDiagnosis("BLOCKED", "源文件是压缩包，请先解压出镜像再生成 " + format, null, null, null);
        }
        Integer systemId = platform != null ? platform.getSystemId() : null;
        if (systemId == null || !systemPipelineLibrary.allows(systemId, source.kind(), format)) {
            return new TargetDiagnosis("UNSUPPORTED",
                    "系统未声明从「" + source.kind().display() + "」生成 " + format, null, null, null);
        }
        PipelinePlan plan = pipelinePlanner.plan(source.kind(), target);
        if (plan.status() == PipelinePlan.Status.ALREADY) {
            return new TargetDiagnosis("SAME", plan.message(), plan.pipelineId(), plan.chainText(), null);
        }
        if (plan.status() == PipelinePlan.Status.READY) {
            return new TargetDiagnosis("READY", plan.message(), plan.pipelineId(),
                    plan.chainText(), null);
        }
        return new TargetDiagnosis(plan.status().name(), plan.message(), plan.pipelineId(),
                plan.chainText(), plan.missingModuleId());
    }

    /** 单个目标格式的判定结果；与 {@link PipelinePlan.Status} 的差别是多了 DIRECT 与“已有副本”的合并空间 */
    private record TargetDiagnosis(String status, String message, String pipelineId,
                                   String chainText, String missingModule) {
    }

    private boolean needsBare(String format) {
        return FORMAT_BARE.equals(format) || FORMAT_ZIP.equals(format) || FORMAT_SEVEN_Z.equals(format);
    }

    private BareSource prepareBare(Game game, Path source, Path workDir) {
        String sourceName = source.getFileName().toString();
        if (ArchiveUtil.isArchive(sourceName)) {
            ArchiveUtil.ArchiveEntryInfo entry = ArchiveUtil.pickMainEntry(source);
            String extension = entry.extension();
            if (extension.isBlank()) {
                throw new ArchiveException(ArchiveException.Reason.UNSUPPORTED_METHOD,
                        "压缩包内文件没有扩展名: " + entry.getName());
            }
            if (ArchiveUtil.isArchive("x." + extension)) {
                throw new ArchiveException(ArchiveException.Reason.UNSUPPORTED_METHOD,
                        "压缩包内是嵌套压缩包，无法生成裸文件: " + entry.getName());
            }
            Path extracted = workDir.resolve(safeBaseName(game) + "." + extension);
            ArchiveUtil.extractEntry(source, entry.getName(), extracted);
            return new BareSource(extracted, extension, "extract");
        }

        String extension = ArchiveUtil.extensionOf(sourceName);
        if (extension.isBlank()) {
            throw new ArchiveException(ArchiveException.Reason.UNSUPPORTED_METHOD,
                    "无法识别 ROM 扩展名: " + sourceName);
        }
        return new BareSource(source, extension, "copy");
    }

    private GameVariant buildVariant(Game game, Platform platform, String format, BareSource bare,
                                     DiscArtifact sourceArtifact, Path workDir,
                                     long sourceSize, long sourceMtime) throws IOException {
        String baseName = safeBaseName(game);
        String fileName;
        String recipe;
        switch (format) {
            case FORMAT_BARE:
                fileName = baseName + "." + bare.extension;
                recipe = "copy".equals(bare.recipe) ? "copy" : "extract";
                break;
            case FORMAT_ZIP:
                fileName = baseName + ".zip";
                recipe = "pack-zip";
                break;
            case FORMAT_SEVEN_Z:
                fileName = baseName + ".7z";
                recipe = "pack-7z";
                break;
            case FORMAT_ISO:
            case FORMAT_CSO:
            case FORMAT_CHD:
                fileName = baseName + "." + SystemPipelineLibrary.targetKind(format).extension();
                recipe = "pipeline";
                break;
            default:
                throw new ArchiveException(ArchiveException.Reason.NOT_ARCHIVE,
                        "不支持的副本格式: " + format);
        }

        Path dir = variantRootPath()
                .resolve(String.valueOf(game.getPlatformId()))
                .resolve(String.valueOf(game.getId()))
                .resolve(format);
        Path target = dir.resolve(fileName);
        Files.createDirectories(dir);

        switch (format) {
            case FORMAT_BARE:
                ArchiveUtil.copyFile(bare.path, target);
                break;
            case FORMAT_ZIP:
                ArchiveUtil.createZip(bare.path, baseName + "." + bare.extension, target);
                break;
            case FORMAT_SEVEN_Z:
                ArchiveUtil.createSevenZ(bare.path, baseName + "." + bare.extension, target);
                break;
            case FORMAT_ISO:
            case FORMAT_CSO:
            case FORMAT_CHD: {
                PipelineResult outcome = runPipeline(sourceArtifact, format, target, workDir);
                recipe = outcome.plan().pipelineId();
                break;
            }
            default:
                break;
        }

        GameVariant variant = new GameVariant();
        variant.setGameId(game.getId());
        variant.setFormat(format);
        variant.setRelPath(toRelPath(target));
        variant.setSizeBytes(Files.size(target));
        variant.setSourceSize(sourceSize);
        variant.setSourceMtime(sourceMtime);
        variant.setStatus(STATUS_READY);
        variant.setRecipe(recipe);
        variant.setErrorMessage(null);
        logger.debug("副本生成完成: game={}, format={}, target={}", game.getName(), format, target);
        return variant;
    }

    /** 执行镜像产线；不可执行时抛出带结论的异常，由上层转成失败详情 */
    private PipelineResult runPipeline(DiscArtifact sourceArtifact, String format, Path target,
                                       Path workDir) throws IOException {
        ArtifactKind targetKind = SystemPipelineLibrary.targetKind(format);
        PipelinePlan plan = pipelinePlanner.plan(sourceArtifact.kind(), targetKind);
        if (plan.status() != PipelinePlan.Status.READY) {
            throw new ArchiveException(ArchiveException.Reason.UNSUPPORTED_METHOD, plan.message());
        }
        Path pipeWork = workDir.resolve("pipeline-" + format);
        Files.createDirectories(pipeWork);
        PipelineResult outcome = pipelineRunner.run(plan, sourceArtifact, target, pipeWork,
                ConversionModule.ProgressSink.NOOP);
        if (!outcome.success()) {
            throw new ArchiveException(ArchiveException.Reason.IO, outcome.describe());
        }
        return outcome;
    }

    private void handleFailure(Game game, Platform platform, String format, GameVariant existing,
                               long sourceSize, long sourceMtime, String message) {
        // 已有可用副本时保留原记录，避免强制重建失败后把可用副本标成失败
        if (existing != null && STATUS_READY.equalsIgnoreCase(existing.getStatus())
                && variantFile(existing) != null && Files.isRegularFile(variantFile(existing))) {
            logger.warn("副本重建失败，保留旧副本: game={}, format={}, error={}",
                    game.getName(), format, message);
            return;
        }

        GameVariant row = existing != null ? existing : new GameVariant();
        row.setGameId(game.getId());
        row.setFormat(format);
        if (row.getRelPath() == null || row.getRelPath().isBlank()) {
            row.setRelPath(expectedRelPath(game, format));
        }
        row.setSourceSize(sourceSize);
        row.setSourceMtime(sourceMtime);
        row.setStatus(STATUS_FAILED);
        row.setErrorMessage(truncate(message, MAX_ERROR_MESSAGE));
        if (row.getId() == null) {
            variantMapper.insert(row);
        } else {
            variantMapper.update(row);
        }
    }

    private void save(GameVariant generated, GameVariant existing) {
        if (existing != null) {
            generated.setId(existing.getId());
            variantMapper.update(generated);
            if (existing.getRelPath() != null && !existing.getRelPath().equals(generated.getRelPath())) {
                deleteFile(existing);
            }
        } else {
            variantMapper.insert(generated);
        }
    }

    @Override
    public boolean deleteVariant(Long gameId, String format) {
        if (gameId == null || format == null) {
            return false;
        }
        GameVariant existing = variantMapper.selectByGameIdAndFormat(gameId, lower(format));
        if (existing == null) {
            return false;
        }
        deleteFile(existing);
        variantMapper.deleteById(existing.getId());
        return true;
    }

    @Override
    public int deleteByGameId(Long gameId) {
        if (gameId == null) {
            return 0;
        }
        for (GameVariant variant : variantMapper.selectByGameId(gameId)) {
            deleteFile(variant);
        }
        int deleted = variantMapper.deleteByGameId(gameId);

        Game game = gameMapper.selectGameById(gameId);
        if (game != null && game.getPlatformId() != null) {
            Path gameDir = variantRootPath()
                    .resolve(String.valueOf(game.getPlatformId()))
                    .resolve(String.valueOf(gameId));
            ArchiveUtil.deleteRecursively(gameDir);
        }
        return deleted;
    }

    @Override
    public Map<String, Object> diagnose(Long gameId) {
        Map<String, Object> body = new LinkedHashMap<>();
        Game game = gameId != null ? gameMapper.selectGameById(gameId) : null;
        if (game == null) {
            body.put("success", false);
            body.put("error", "游戏不存在: " + gameId);
            return body;
        }
        Platform platform = game.getPlatformId() != null
                ? platformMapper.selectPlatformById(game.getPlatformId()) : null;
        Integer systemId = platform != null ? platform.getSystemId() : null;
        Path source = resolveSourcePath(game);
        boolean sourceExists = source != null && Files.isRegularFile(source);
        DiscArtifact artifact = sourceExists ? discImageProbe.probe(source) : DiscArtifact.unknown(source);

        body.put("success", true);
        body.put("gameId", gameId);
        body.put("gameName", game.getName() == null ? "" : game.getName());
        body.put("systemId", systemId);
        body.put("sourceExists", sourceExists);
        body.put("source", describeArtifact(artifact));

        List<Map<String, Object>> targets = new ArrayList<>();
        if (systemId != null) {
            for (String format : capabilityService.getSupportedFormats(systemId)) {
                targets.add(describeTarget(artifact, format, platform));
            }
        }
        body.put("targets", targets);
        return body;
    }

    private Map<String, Object> describeArtifact(DiscArtifact artifact) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("path", artifact.path() == null ? "" : artifact.path().toString());
        row.put("kind", artifact.kind().name());
        row.put("kindLabel", artifact.kind().display());
        row.put("detail", artifact.detail());
        row.put("totalBytes", artifact.totalBytes());
        row.put("display", artifact.describe());
        return row;
    }

    private Map<String, Object> describeTarget(DiscArtifact artifact, String format, Platform platform) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("format", format);
        // 与生成链路共用 diagnoseTarget，避免弹窗说“能转”、真生成时又被拦下
        TargetDiagnosis diagnosis = diagnoseTarget(platform, format, artifact);
        row.put("status", diagnosis.status());
        row.put("message", diagnosis.message() == null ? "" : diagnosis.message());
        row.put("pipelineId", diagnosis.pipelineId() == null ? "" : diagnosis.pipelineId());
        row.put("chainText", diagnosis.chainText() == null ? "" : diagnosis.chainText());
        if (diagnosis.missingModule() != null) {
            row.put("missingModule", diagnosis.missingModule());
        }
        List<Map<String, Object>> steps = new ArrayList<>();
        ArtifactKind targetKind = SystemPipelineLibrary.targetKind(format);
        if (targetKind != null) {
            PipelinePlan plan = pipelinePlanner.plan(artifact.kind(), targetKind);
            for (ConversionModule.ModuleSpec spec : plan.specs()) {
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("id", spec.id());
                step.put("arrow", spec.arrow());
                step.put("available", spec.available());
                step.put("note", spec.note());
                steps.add(step);
            }
        }
        row.put("steps", steps);
        return row;
    }

    @Override
    public Map<String, Object> survey(Long platformId, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        Platform platform = platformId != null ? platformMapper.selectPlatformById(platformId) : null;
        if (platform == null) {
            body.put("success", false);
            body.put("error", "平台不存在: " + platformId);
            return body;
        }

        Integer systemId = platform.getSystemId();
        List<String> supported = systemId == null
                ? List.of() : capabilityService.getSupportedFormats(systemId);

        body.put("success", true);
        body.put("platformId", platformId);
        body.put("platformName", platform.getName() == null ? "" : platform.getName());
        body.put("systemId", systemId);
        body.put("formats", supported);
        body.put("games", List.of());
        body.put("summary", Map.of("totalGames", 0, "scanned", 0, "truncated", false));

        if (systemId == null) {
            body.put("message", "平台未关联系统，无法体检");
            return body;
        }
        if (supported.isEmpty()) {
            body.put("message", "该系统未声明任何副本格式，无法体检");
            return body;
        }

        List<Game> games = gameMapper.selectGamesByPlatformId(platformId);
        if (games == null) {
            games = List.of();
        }
        int cap = limit > 0 ? limit : MAX_SURVEY_GAMES;

        List<Long> ids = new ArrayList<>();
        for (Game game : games) {
            if (game != null && game.getId() != null) {
                ids.add(game.getId());
            }
        }
        Map<Long, List<GameVariant>> rowsByGame = new HashMap<>();
        if (!ids.isEmpty()) {
            for (GameVariant row : variantMapper.selectByGameIds(ids)) {
                rowsByGame.computeIfAbsent(row.getGameId(), k -> new ArrayList<>()).add(row);
            }
        }

        Map<String, Map<String, Integer>> byFormat = new LinkedHashMap<>();
        for (String format : supported) {
            Map<String, Integer> counter = new LinkedHashMap<>();
            counter.put("usable", 0);
            counter.put("generatable", 0);
            counter.put("blocked", 0);
            counter.put("broken", 0);
            byFormat.put(format, counter);
        }

        // 顶部汇总只看镜像格式（iso/cso 这类需产线的）；zip/7z 永远“可补”，会把真正的缺口淡化
        Set<String> counted = new LinkedHashSet<>();
        for (String format : supported) {
            if (SystemPipelineLibrary.targetKind(format) != null) {
                counted.add(format);
            }
        }
        if (counted.isEmpty()) {
            counted.addAll(supported);
        }

        List<Map<String, Object>> details = new ArrayList<>();
        int scanned = 0;
        int missingSource = 0;
        int multiFileGames = 0;
        int gamesAllUsable = 0;
        int gamesGeneratable = 0;
        int gamesBlockedOnly = 0;
        int gamesBroken = 0;

        for (Game game : games) {
            if (game == null || game.getId() == null) {
                continue;
            }
            if (scanned >= cap) {
                break;
            }
            scanned++;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("gameId", game.getId());
            row.put("name", game.getName() == null ? "" : game.getName());
            row.put("path", game.getPath() == null ? "" : game.getPath());

            Map<String, Object> targets = new LinkedHashMap<>();
            if (Boolean.TRUE.equals(game.getMultiFile())) {
                multiFileGames++;
                row.put("sourceKind", "MULTI_FILE");
                row.put("sourceLabel", "多文件游戏");
                row.put("sourceExists", true);
                row.put("sourceDisplay", "m3u/多碟条目，不参与副本机制");
                row.put("sourceBytes", 0);
                for (String format : supported) {
                    Map<String, Object> skipped = targetRow("SKIPPED", "多文件游戏不支持副本", null, null);
                    targets.put(format, skipped);
                    byFormat.get(format).merge("blocked", 1, Integer::sum);
                }
                row.put("targets", targets);
                row.put("hasUsable", false);
                row.put("hasGeneratable", false);
                row.put("hasBlocked", true);
                row.put("hasBroken", false);
                details.add(row);
                continue;
            }

            Path source = resolveSourcePath(game);
            boolean sourceExists = source != null && Files.isRegularFile(source);
            long sourceSize = 0L;
            long sourceMtime = 0L;
            if (sourceExists) {
                try {
                    sourceSize = Files.size(source);
                    sourceMtime = Files.getLastModifiedTime(source).toMillis();
                } catch (IOException e) {
                    sourceExists = false;
                }
            }
            if (!sourceExists) {
                missingSource++;
            }

            // 体检只看扩展名：导入时就知道的事，不必为了盘点去读每个文件头
            DiscArtifact artifact = sourceExists
                    ? discImageProbe.probeByExtension(source) : DiscArtifact.unknown(source);
            row.put("sourceExists", sourceExists);
            row.put("sourceKind", artifact.kind().name());
            row.put("sourceLabel", artifact.kind().display());
            row.put("sourceDisplay", sourceExists ? artifact.describe() : "文件不存在");
            row.put("sourceBytes", sourceSize);

            List<GameVariant> existingRows = rowsByGame.getOrDefault(game.getId(), List.of());
            boolean anyUsable = false;
            boolean anyGeneratable = false;
            boolean anyBlocked = false;
            boolean anyBroken = false;

            for (String format : supported) {
                GameVariant existing = findVariant(existingRows, format);
                String status;
                String message = "";
                String chainText = "";
                String missingModule = null;

                if (existing != null && STATUS_FAILED.equalsIgnoreCase(existing.getStatus())) {
                    status = "FAILED";
                    message = existing.getErrorMessage() == null ? "" : existing.getErrorMessage();
                } else if (existing != null && STATUS_READY.equalsIgnoreCase(existing.getStatus())) {
                    if (sourceExists && isFreshReady(existing, sourceSize, sourceMtime)) {
                        status = "EXISTS";
                    } else {
                        status = "STALE";
                        message = sourceExists ? "副本已过期" : "主文件缺失，无法校验新鲜度";
                    }
                } else {
                    TargetDiagnosis diagnosis = diagnoseTarget(platform, format, artifact);
                    status = diagnosis.status();
                    message = diagnosis.message() == null ? "" : diagnosis.message();
                    chainText = diagnosis.chainText() == null ? "" : diagnosis.chainText();
                    missingModule = diagnosis.missingModule();
                }

                String bucket = bucketOf(status);
                byFormat.get(format).merge(bucket, 1, Integer::sum);
                if (counted.contains(format)) {
                    switch (bucket) {
                        case "usable" -> anyUsable = true;
                        case "generatable" -> anyGeneratable = true;
                        case "broken" -> anyBroken = true;
                        default -> anyBlocked = true;
                    }
                }
                targets.put(format, targetRow(status, message, chainText, missingModule));
            }

            row.put("targets", targets);
            row.put("hasUsable", anyUsable);
            row.put("hasGeneratable", anyGeneratable);
            row.put("hasBlocked", anyBlocked);
            row.put("hasBroken", anyBroken);

            if (anyBroken) {
                gamesBroken++;
            } else if (anyGeneratable) {
                gamesGeneratable++;
            } else if (anyUsable) {
                gamesAllUsable++;
            } else if (anyBlocked) {
                gamesBlockedOnly++;
            }
            details.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalGames", games.size());
        summary.put("scanned", scanned);
        summary.put("truncated", games.size() > scanned);
        summary.put("missingSource", missingSource);
        summary.put("multiFile", multiFileGames);
        summary.put("allUsable", gamesAllUsable);
        summary.put("generatable", gamesGeneratable);
        summary.put("blockedOnly", gamesBlockedOnly);
        summary.put("broken", gamesBroken);
        summary.put("byFormat", byFormat);
        summary.put("countedFormats", new ArrayList<>(counted));

        body.put("games", details);
        body.put("summary", summary);
        body.put("message", "");
        return body;
    }

    /** 把六种状态归成四个可展示分组：已有 / 可补 / 不可生成 / 需要修复 */
    private String bucketOf(String status) {
        return switch (status) {
            case "EXISTS", "SAME" -> "usable";
            case "READY", "DIRECT" -> "generatable";
            case "STALE", "FAILED" -> "broken";
            default -> "blocked";
        };
    }

    private GameVariant findVariant(List<GameVariant> rows, String format) {
        for (GameVariant row : rows) {
            if (row.getFormat() != null && row.getFormat().equalsIgnoreCase(format)) {
                return row;
            }
        }
        return null;
    }

    private Map<String, Object> targetRow(String status, String message, String chainText,
                                          String missingModule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", status);
        row.put("message", message == null ? "" : message);
        row.put("chainText", chainText == null ? "" : chainText);
        if (missingModule != null) {
            row.put("missingModule", missingModule);
        }
        return row;
    }

    private List<String> normalizeFormats(List<String> formats, Integer systemId) {
        List<String> supported = capabilityService.getSupportedFormats(systemId);
        if (supported.isEmpty()) {
            return List.of();
        }
        if (formats == null || formats.isEmpty()) {
            return supported;
        }
        Set<String> requested = new LinkedHashSet<>();
        for (String format : formats) {
            String normalized = lower(format);
            if (supported.contains(normalized)) {
                requested.add(normalized);
            }
        }
        return new ArrayList<>(requested);
    }

    private boolean isFreshReady(GameVariant existing, long sourceSize, long sourceMtime) {
        if (existing == null || !STATUS_READY.equalsIgnoreCase(existing.getStatus())) {
            return false;
        }
        if (!Objects.equals(existing.getSourceSize(), sourceSize)
                || !Objects.equals(existing.getSourceMtime(), sourceMtime)) {
            return false;
        }
        Path file = variantFile(existing);
        return file != null && Files.isRegularFile(file);
    }

    private String expectedRelPath(Game game, String format) {
        String baseName = safeBaseName(game);
        String fileName;
        switch (format) {
            case FORMAT_BARE:
                fileName = baseName + ".bin";
                break;
            case FORMAT_ZIP:
                fileName = baseName + ".zip";
                break;
            case FORMAT_SEVEN_Z:
                fileName = baseName + ".7z";
                break;
            case FORMAT_ISO:
                fileName = baseName + ".iso";
                break;
            case FORMAT_CSO:
                fileName = baseName + ".cso";
                break;
            case FORMAT_CHD:
                fileName = baseName + ".chd";
                break;
            default:
                fileName = baseName;
                break;
        }
        return game.getPlatformId() + "/" + game.getId() + "/" + format + "/" + fileName;
    }

    private String toRelPath(Path target) {
        Path root = variantRootPath();
        return root.relativize(target.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private Path variantRootPath() {
        Path root = Paths.get(variantRoot).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new ArchiveException(ArchiveException.Reason.IO,
                    "创建副本目录失败: " + root + " (" + e.getMessage() + ")", e);
        }
        return root;
    }

    private Path variantFile(GameVariant variant) {
        if (variant == null || variant.getRelPath() == null || variant.getRelPath().isBlank()) {
            return null;
        }
        try {
            return ArchiveUtil.resolveInside(variantRootPath(), variant.getRelPath());
        } catch (ArchiveException e) {
            logger.warn("副本路径非法: {}", variant.getRelPath());
            return null;
        }
    }

    private void deleteFile(GameVariant variant) {
        Path file = variantFile(variant);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
            Path parent = file.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                try (var stream = Files.list(parent)) {
                    if (stream.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("删除副本文件失败: {} ({})", file, e.getMessage());
        }
    }

    private Path resolveSourcePath(Game game) {
        if (game == null) {
            return null;
        }
        String absolutePath = game.getAbsolutePath();
        if (absolutePath != null && !absolutePath.isBlank()) {
            return Paths.get(firstLine(absolutePath));
        }
        String platformPath = game.getPlatformPath();
        String gamePath = game.getPath();
        if (platformPath != null && !platformPath.isBlank()
                && gamePath != null && !gamePath.isBlank()) {
            return Paths.get(platformPath, stripFragment(firstLine(gamePath)));
        }
        return null;
    }

    private String safeBaseName(Game game) {
        String base = null;
        String path = game.getPath();
        if (path != null && !path.isBlank()) {
            String first = stripFragment(firstLine(path));
            int separator = Math.max(first.lastIndexOf('/'), first.lastIndexOf('\\'));
            base = separator >= 0 ? first.substring(separator + 1) : first;
            base = ArchiveUtil.stripExtension(base);
        }
        if (base == null || base.isBlank()) {
            base = game.getName();
        }
        if (base == null) {
            base = "game-" + game.getId();
        }

        String cleaned = base.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1f]", "_").trim();
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.isBlank()) {
            cleaned = "game-" + game.getId();
        }
        if (cleaned.length() > MAX_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_NAME_LENGTH);
        }
        return cleaned;
    }

    private String firstLine(String value) {
        return value.split("\\r?\\n")[0].trim();
    }

    private String stripFragment(String value) {
        int hash = value.indexOf('#');
        return hash >= 0 ? value.substring(0, hash) : value;
    }

    private void merge(VariantGenerateResult target, VariantGenerateResult source) {
        if (source == null) {
            return;
        }
        target.setSuccess(target.getSuccess() + source.getSuccess());
        target.setSkipped(target.getSkipped() + source.getSkipped());
        target.setFailed(target.getFailed() + source.getFailed());
        for (String message : source.getMessages()) {
            if (target.getMessages().size() >= MAX_RESULT_MESSAGES) {
                break;
            }
            target.getMessages().add(message);
        }
    }

    private String buildResultDetail(VariantGenerateResult result) {
        if (result.getMessages().isEmpty()) {
            return "没有需要提示的条目";
        }
        int limit = Math.min(50, result.getMessages().size());
        StringBuilder sb = new StringBuilder(String.join("\n", result.getMessages().subList(0, limit)));
        if (result.getMessages().size() > limit) {
            sb.append("\n…共 ").append(result.getMessages().size()).append(" 条");
        }
        return sb.toString();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class BareSource {
        private final Path path;
        private final String extension;
        private final String recipe;

        private BareSource(Path path, String extension, String recipe) {
            this.path = path;
            this.extension = extension;
            this.recipe = recipe;
        }
    }
}
