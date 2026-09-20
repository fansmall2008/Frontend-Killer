package com.gamelist.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelist.mapper.GameMapper;
import com.gamelist.mapper.PlatformStatsCacheMapper;
import com.gamelist.model.BackgroundTask;
import com.gamelist.model.Game;
import com.gamelist.model.MediaType;
import com.gamelist.model.Platform;
import com.gamelist.model.PlatformStatsCache;
import com.gamelist.service.PlatformService;
import com.gamelist.service.PlatformStatsScanner;
import com.gamelist.service.TaskService;
import com.gamelist.util.GameFieldAccessor;

/**
 * 平台体量统计扫描服务实现
 * <p>
 * 扫描算法：
 * <ol>
 *   <li>加载平台下所有 Game 记录</li>
 *   <li>使用 8 线程并发遍历，每个游戏：
 *     <ul>
 *       <li>ROM 大小：优先 absolutePath，回退到双基准解析 path</li>
 *       <li>媒体大小：遍历 MediaType.getDownloadableTypes()，双基准解析后取 Files.size()</li>
 *     </ul>
 *   </li>
 *   <li>汇总后 UPSERT 到 platform_stats_cache</li>
 * </ol>
 * <p>
 * 异步执行：使用 {@link CompletableFuture#runAsync} 避免 Spring @Async 自调用陷阱。
 */
@Service
public class PlatformStatsScannerImpl implements PlatformStatsScanner {

    private static final Logger logger = LoggerFactory.getLogger(PlatformStatsScannerImpl.class);

    /** 扫描并发线程数 */
    private static final int SCAN_THREADS = 8;

    /** 每处理多少个游戏上报一次进度 */
    private static final int PROGRESS_BATCH = 50;

    /** 任务类型标识 */
    private static final String TASK_TYPE = "PLATFORM_STATS_SCAN";

    @Autowired
    private GameMapper gameMapper;

    @Autowired
    private PlatformStatsCacheMapper cacheMapper;

    @Autowired
    private PlatformService platformService;

    @Autowired
    private TaskService taskService;

    /** platformId → taskId 的运行中任务映射，防止同一平台被并发扫描 */
    private final Map<Long, Long> runningTasks = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 公共接口 ====================

    @Override
    public Long scanAsync(Long platformId) {
        if (platformId == null) {
            throw new IllegalArgumentException("platformId 不能为空");
        }

        // 幂等：已在扫描中直接返回现有任务 ID
        Long existing = runningTasks.get(platformId);
        if (existing != null) {
            logger.info("平台 {} 已在扫描中，返回现有任务 {}", platformId, existing);
            return existing;
        }

        Platform platform = platformService.getPlatformById(platformId);
        if (platform == null) {
            throw new IllegalArgumentException("平台不存在: " + platformId);
        }

        // 创建后台任务
        String desc = "扫描平台体量统计: " + (platform.getName() != null ? platform.getName() : platform.getSystem());
        BackgroundTask task = taskService.createTask(TASK_TYPE, desc);
        Long taskId = task.getId();

        // 先占位，防止并发触发
        runningTasks.put(platformId, taskId);

        // 使用 CompletableFuture.runAsync 确保异步执行（避免 @Async 自调用陷阱）
        CompletableFuture.runAsync(() -> {
            try {
                doScan(platformId, platform, taskId);
            } catch (Throwable t) {
                logger.error("平台 {} 扫描失败", platformId, t);
                taskService.failTask(taskId, "扫描失败", t.getMessage());
            } finally {
                runningTasks.remove(platformId);
            }
        });

        return taskId;
    }

    @Override
    public PlatformStatsCache getCache(Long platformId) {
        if (platformId == null) return null;
        return cacheMapper.selectByPlatformId(platformId);
    }

    @Override
    public boolean isScanning(Long platformId) {
        return platformId != null && runningTasks.containsKey(platformId);
    }

    // ==================== 扫描主逻辑 ====================

    private void doScan(Long platformId, Platform platform, Long taskId) {
        long startMs = System.currentTimeMillis();
        logger.info("开始扫描平台 {} ({})", platform.getName(), platformId);
        taskService.updateTaskProgress(taskId, 0, "加载游戏列表", 0, 0);

        List<Game> games = gameMapper.selectGamesByPlatformId(platformId);
        int total = games.size();
        logger.info("平台 {} 共 {} 个游戏", platformId, total);
        taskService.updateTaskProgress(taskId, 1, "开始扫描 " + total + " 个游戏", 0, total);

        if (total == 0) {
            // 空平台直接写入 0 快照
            writeSnapshot(platformId, 0, 0, 0, 0, 0, 0, 0,
                    Map.of(), Map.of(), taskId, startMs);
            taskService.completeTask(taskId, "扫描完成（空平台）", "游戏数: 0");
            return;
        }

        // 并发累加器
        LongAdder romCount = new LongAdder();
        LongAdder romMissing = new LongAdder();
        LongAdder romSize = new LongAdder();
        LongAdder mediaCount = new LongAdder();
        LongAdder mediaMissing = new LongAdder();
        LongAdder mediaSize = new LongAdder();

        // 按 nomcourt 分组：{"box-2D": count/size}
        Map<String, LongAdder> mediaCountByType = new ConcurrentHashMap<>();
        Map<String, LongAdder> mediaSizeByType = new ConcurrentHashMap<>();

        // 可下载媒体类型（排除 theme/mix）
        MediaType[] mediaTypes = MediaType.getDownloadableTypes();

        AtomicInteger processed = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(SCAN_THREADS,
                r -> {
                    Thread t = new Thread(r, "stats-scanner-" + platformId);
                    t.setDaemon(true);
                    return t;
                });

        try {
            for (Game game : games) {
                executor.submit(() -> {
                    try {
                        scanOneGame(game, platform, mediaTypes,
                                romCount, romMissing, romSize,
                                mediaCount, mediaMissing, mediaSize,
                                mediaCountByType, mediaSizeByType);
                    } catch (Throwable t) {
                        logger.warn("扫描游戏 {} 失败: {}", game.getId(), t.getMessage());
                    } finally {
                        int done = processed.incrementAndGet();
                        if (done % PROGRESS_BATCH == 0 || done == total) {
                            int percent = (int) (done * 100L / total);
                            taskService.updateTaskProgress(taskId, percent,
                                    "已扫描 " + done + "/" + total, done, total);
                        }
                    }
                });
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.HOURS)) {
                    executor.shutdownNow();
                    logger.warn("平台 {} 扫描超时（>2h），强制终止", platformId);
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 转换 Map<String, LongAdder> → Map<String, Long>
        Map<String, Long> countMap = new java.util.TreeMap<>();
        Map<String, Long> sizeMap = new java.util.TreeMap<>();
        mediaCountByType.forEach((k, v) -> countMap.put(k, v.sum()));
        mediaSizeByType.forEach((k, v) -> sizeMap.put(k, v.sum()));

        writeSnapshot(platformId, total,
                (int) romCount.sum(), (int) romMissing.sum(), romSize.sum(),
                (int) mediaCount.sum(), (int) mediaMissing.sum(), mediaSize.sum(),
                countMap, sizeMap, taskId, startMs);

        long durationMs = System.currentTimeMillis() - startMs;
        String summary = String.format(
                "游戏 %d 个 / ROM %.2f GB / 媒体 %d 个 / %.2f GB / 缺失 %d",
                total, romSize.sum() / 1024.0 / 1024.0 / 1024.0,
                mediaCount.sum(), mediaSize.sum() / 1024.0 / 1024.0 / 1024.0,
                romMissing.sum() + mediaMissing.sum());
        taskService.completeTask(taskId, "扫描完成", summary);
        logger.info("平台 {} 扫描完成，耗时 {} ms: {}", platformId, durationMs, summary);
    }

    /**
     * 扫描单个游戏：累加 ROM 和所有媒体字段的大小。
     */
    private void scanOneGame(Game game, Platform platform, MediaType[] mediaTypes,
                              LongAdder romCount, LongAdder romMissing, LongAdder romSize,
                              LongAdder mediaCount, LongAdder mediaMissing, LongAdder mediaSize,
                              Map<String, LongAdder> mediaCountByType,
                              Map<String, LongAdder> mediaSizeByType) {

        // ---------- ROM ----------
        Path romPath = resolveRomPath(game, platform);
        if (romPath != null) {
            try {
                long size = Files.size(romPath);
                romSize.add(size);
                romCount.increment();
            } catch (IOException e) {
                logger.trace("读取 ROM 大小失败 {}: {}", romPath, e.getMessage());
                romMissing.increment();
            }
        } else if (hasRomReference(game)) {
            // 数据库有路径但磁盘找不到
            romMissing.increment();
        }

        // ---------- 媒体文件 ----------
        for (MediaType mt : mediaTypes) {
            String raw = GameFieldAccessor.getValue(game, mt.getNomcourt());
            if (raw == null || raw.isEmpty()) continue;

            Path p = resolveMediaPath(raw, platform);
            if (p == null) {
                mediaMissing.increment();
                continue;
            }
            try {
                long size = Files.size(p);
                mediaSize.add(size);
                mediaCount.increment();

                mediaCountByType.computeIfAbsent(mt.getNomcourt(), k -> new LongAdder()).increment();
                mediaSizeByType.computeIfAbsent(mt.getNomcourt(), k -> new LongAdder()).add(size);
            } catch (IOException e) {
                logger.trace("读取媒体大小失败 {}: {}", p, e.getMessage());
                mediaMissing.increment();
            }
        }
    }

    /** Game 是否有 ROM 路径引用（path 或 absolutePath 任一非空） */
    private boolean hasRomReference(Game game) {
        return (game.getPath() != null && !game.getPath().isEmpty())
                || (game.getAbsolutePath() != null && !game.getAbsolutePath().isEmpty());
    }

    /** 解析 ROM 磁盘路径：优先 absolutePath，回退到双基准解析 path */
    private Path resolveRomPath(Game game, Platform platform) {
        // 1. absolutePath 直接命中
        String abs = game.getAbsolutePath();
        if (abs != null && !abs.isEmpty()) {
            Path p = Paths.get(abs);
            if (Files.exists(p) && !Files.isDirectory(p)) return p;
        }
        // 2. path 走双基准
        String path = game.getPath();
        if (path == null || path.isEmpty()) return null;
        return resolveMediaPath(path, platform);
    }

    /**
     * 双基准媒体路径解析（简化版，与 MediaController.resolveMediaPath 语义一致）。
     * <ol>
     *   <li>绝对路径 → 存在即返回</li>
     *   <li>相对路径 → 先试工作目录（user.dir，刮削/上传路径）</li>
     *   <li>再试 platform.folderPath（导入路径）</li>
     *   <li>都不存在 → null</li>
     * </ol>
     */
    private Path resolveMediaPath(String rawPath, Platform platform) {
        if (rawPath == null || rawPath.isEmpty()) return null;

        String cleaned = rawPath.replace('\\', '/');
        if (cleaned.startsWith("./")) cleaned = cleaned.substring(2);

        Path p;
        try {
            p = Paths.get(cleaned);
        } catch (Exception e) {
            return null;
        }

        // 1. 绝对路径
        if (p.isAbsolute()) {
            return (Files.exists(p) && !Files.isDirectory(p)) ? p : null;
        }

        // 2. 工作目录 + 相对路径（刮削/上传）
        try {
            Path workDirPath = p.toAbsolutePath();
            if (Files.exists(workDirPath) && !Files.isDirectory(workDirPath)) {
                return workDirPath;
            }
        } catch (Exception ignored) {}

        // 3. platform.folderPath + 相对路径（导入）
        if (platform != null && platform.getFolderPath() != null && !platform.getFolderPath().isEmpty()) {
            try {
                Path platformPath = Paths.get(platform.getFolderPath(), cleaned);
                if (Files.exists(platformPath) && !Files.isDirectory(platformPath)) {
                    return platformPath;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    // ==================== 快照写入 ====================

    private void writeSnapshot(Long platformId, int totalGames,
                                int romCount, int romMissing, long romSize,
                                int mediaCount, int mediaMissing, long mediaSize,
                                Map<String, Long> countByType, Map<String, Long> sizeByType,
                                Long taskId, long startMs) {
        PlatformStatsCache cache = new PlatformStatsCache();
        cache.setPlatformId(platformId);
        cache.setTotalGames(totalGames);
        cache.setTotalRomCount(romCount);
        cache.setRomMissingCount(romMissing);
        cache.setTotalRomSize(romSize);
        cache.setTotalMediaCount(mediaCount);
        cache.setMediaMissingCount(mediaMissing);
        cache.setTotalMediaSize(mediaSize);
        cache.setMediaCountByType(toJson(countByType));
        cache.setMediaSizeByType(toJson(sizeByType));
        cache.setLastScannedAt(new Timestamp(System.currentTimeMillis()));
        cache.setScanDurationMs(System.currentTimeMillis() - startMs);
        cache.setLastTaskId(taskId);

        cacheMapper.upsert(cache);
    }

    private String toJson(Map<String, Long> map) {
        if (map == null || map.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            logger.warn("序列化媒体类型统计失败: {}", e.getMessage());
            return "{}";
        }
    }
}
