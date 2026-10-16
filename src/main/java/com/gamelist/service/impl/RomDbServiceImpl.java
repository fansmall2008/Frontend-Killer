package com.gamelist.service.impl;

import com.gamelist.config.RomDbDataInitializer;
import com.gamelist.config.RomDbPlatformMapping;
import com.gamelist.mapper.GameMapper;
import com.gamelist.mapper.RomDbMapper;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.model.RomDb;
import com.gamelist.service.NotificationService;
import com.gamelist.service.PlatformService;
import com.gamelist.service.RomDbService;
import com.gamelist.service.TaskService;
import com.gamelist.util.ClrMameProDatParser;
import com.gamelist.util.CartridgeHeaderReader;
import com.gamelist.util.DiscSerialExtractor;
import com.gamelist.util.NgcHeaderReader;
import com.gamelist.util.ParamSfoReader;
import com.gamelist.util.SystemCnfReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.zip.CRC32;

@Service
public class RomDbServiceImpl implements RomDbService {

    private static final Logger logger = LoggerFactory.getLogger(RomDbServiceImpl.class);

    private final RomDbMapper romDbMapper;
    private final RomDbDataInitializer initializer;
    private final GameMapper gameMapper;

    @Autowired(required = false)
    private PlatformService platformService;

    @Autowired(required = false)
    private TaskService taskService;

    @Autowired(required = false)
    private NotificationService notificationService;

    public RomDbServiceImpl(RomDbMapper romDbMapper, RomDbDataInitializer initializer, GameMapper gameMapper) {
        this.romDbMapper = romDbMapper;
        this.initializer = initializer;
        this.gameMapper = gameMapper;
    }

    @Override
    public RomDb matchBySerial(String serial, int systemId) {
        if (serial == null || serial.isBlank()) return null;
        String normalized = serial.replaceAll("\\s+", "").toUpperCase();

        // 先按 systemId 查
        RomDb result = romDbMapper.findBySystemAndSerial(systemId, normalized);
        if (result != null) return result;

        // 回退：忽略连字符差异（Genesis 提取器去 dash 但 DB 保留 dash）
        String dashless = normalized.replace("-", "");
        if (!dashless.equals(normalized)) {
            result = romDbMapper.findBySystemAndSerial(systemId, dashless);
            if (result != null) return result;
        }
        // 回退：DB 端去连字符比较（提取器无 dash、DB 有 dash 的场景）
        result = romDbMapper.findBySystemAndSerialNormalized(systemId, dashless);
        if (result != null) return result;

        // 回退：前缀匹配（GB/GBC 的 game code → CGB-XXXX-REGION / DMG-XXXX 格式）
        // 提取的 game code 通常是 3-6 字符，如 "VP9E"、"BO7E"
        String gameCode = dashless;
        // 如果提取的 title 包含 "-"（如 "CGB-VP9E"），取后半部分作为 game code
        if (gameCode.contains("-")) {
            String[] parts = gameCode.split("-");
            if (parts.length >= 2) {
                gameCode = parts[parts.length - 1].length() >= 3
                        ? parts[parts.length - 1] : parts[0];
            }
        }
        if (gameCode.length() >= 3 && gameCode.length() <= 8) {
            result = romDbMapper.findBySystemAndSerialPrefix(systemId, gameCode);
            if (result != null) return result;
            // 回退：game code 匹配（Genesis: 提取 "MK1054" → DB "MK-1054-00" 去 dash 后 "MK105400" 以 "MK1054" 开头 + dash）
            result = romDbMapper.findBySystemAndGameCode(systemId, gameCode);
            if (result != null) return result;
        }

        return null;
    }

    @Override
    public RomDb matchByCrc32(String crc32, int systemId) {
        if (crc32 == null || crc32.isBlank()) return null;
        String normalized = crc32.toUpperCase().trim();

        // 先按 systemId 查
        RomDb result = romDbMapper.findBySystemAndCrc32(systemId, normalized);
        if (result != null) return result;

        return null;
    }

    @Override
    public RomDb matchByDiscFile(String filePath, int systemId) {
        if (filePath == null || filePath.isEmpty()) return null;

        String fileName = new File(filePath).getName();

        // ★ L0-FAST: 文件名正则提取（零 I/O，优先尝试）
        // PS2/PS1/PSP 等文件名通常含 serial（SLUS-20001, SLUS_200.01 等）
        {
            String fnNormalized = fileName.toUpperCase()
                    .replace('_', '-').replace(".", "");
            String fnSerial = DiscSerialExtractor.extract(fnNormalized, systemId);
            if (fnSerial != null) {
                RomDb result = matchBySerial(fnSerial, systemId);
                if (result != null) {
                    logger.info("rom_db: 文件名快速匹配 {} → serial={}", fileName, fnSerial);
                    return result;
                }
            }
        }

        // L1: SYSTEM.CNF 提取（PS1/PS2）— 需要读 ISO，NAS 上较慢
        if ((systemId == 57 || systemId == 58) && SystemCnfReader.isSupportedFormat(filePath)) {
            try {
                String serial = SystemCnfReader.extractSerial(filePath);
                if (serial != null) {
                    logger.info("rom_db: SYSTEM.CNF 提取 serial={} from {}", serial, fileName);
                    RomDb result = matchBySerial(serial, systemId);
                    if (result != null) return result;
                }
            } catch (Exception e) {
                logger.debug("rom_db: SYSTEM.CNF 读取失败: {} - {}", fileName, e.getMessage());
            }
        }

        // L1-PS3: PARAM.SFO 提取
        if (systemId == 59) {
            try {
                String serial = ParamSfoReader.extractSerial(filePath);
                if (serial != null) {
                    logger.info("rom_db: PARAM.SFO 提取 serial={} from {}", serial, fileName);
                    RomDb result = matchBySerial(serial, systemId);
                    if (result != null) return result;
                }
            } catch (Exception e) {
                logger.debug("rom_db: PARAM.SFO 读取失败: {} - {}", fileName, e.getMessage());
            }
        }

        // L1-NGC: 镜像头部 6 字节 game ID 提取（仅 NGC 平台，systemId=13）
        if (systemId == 13 && NgcHeaderReader.isSupportedFormat(filePath)) {
            try {
                String serial = NgcHeaderReader.extractSerial(filePath);
                if (serial != null) {
                    logger.info("rom_db: NGC 头部提取 serial={} from {}", serial, fileName);
                    RomDb result = matchBySerial(serial, systemId);
                    if (result != null) return result;
                }
            } catch (Exception e) {
                logger.debug("rom_db: NGC 头部读取失败: {} - {}", fileName, e.getMessage());
            }
        }

        // L1-Saturn/MegaCD/Dreamcast: IP.BIN 头部提取（BIN/CUE 格式）
        if (systemId == 22 || systemId == 20 || systemId == 23) {
            try {
                String serial = CartridgeHeaderReader.extractSerial(filePath, systemId);
                if (serial != null) {
                    String platform = systemId == 22 ? "Saturn" : (systemId == 20 ? "MegaCD" : "Dreamcast");
                    logger.info("rom_db: {} IP.BIN 提取 serial={} from {}", platform, serial, fileName);
                    RomDb result = matchBySerial(serial, systemId);
                    if (result != null) return result;
                }
            } catch (Exception e) {
                logger.debug("rom_db: 世嘉光盘 IP.BIN 读取失败: {} - {}", fileName, e.getMessage());
            }
        }

        // L1-Wii: game ID 头部提取（iso @0x00 / wbfs @0x200），取前 4 位 game code 反查 rom_db
        if (systemId == 16) {
            try {
                String serial = CartridgeHeaderReader.extractWiiGameId(filePath);
                if (serial != null) {
                    logger.info("rom_db: Wii game ID 头部提取 serial={} from {}", serial, fileName);
                    RomDb result = matchBySerial(serial, systemId);
                    if (result != null) return result;
                }
            } catch (Exception e) {
                logger.debug("rom_db: Wii game ID 读取失败: {} - {}", fileName, e.getMessage());
            }
        }

        // （文件名正则已移至顶部 L0-FAST 优先尝试）

        return null;
    }

    @Override
    public RomDb matchByFileCrc32(String filePath, int systemId) {
        if (filePath == null || filePath.isEmpty()) return null;

        try {
            String crc32 = calculateCrc32(filePath);
            if (crc32 != null) {
                RomDb result = matchByCrc32(crc32, systemId);
                if (result == null) {
                    // 诊断日志：记录未匹配的 CRC32
                    String fileName = new File(filePath).getName();
                    logger.info("rom_db CRC 未匹配: file={}, crc32={}, systemId={}", fileName, crc32, systemId);
                }
                return result;
            }
        } catch (IOException e) {
            logger.debug("rom_db: CRC32 计算失败: {} - {}", filePath, e.getMessage());
        }
        return null;
    }

    /**
     * 名称匹配回退：当 CRC 匹配失败时，尝试按文件名搜索 rom_db。
     * 用于处理 hacked/modified ROM 导致 CRC 不匹配的情况。
     */
    private RomDb matchByNameFallback(String fileName, int systemId) {
        // 移除扩展名
        String baseName = fileName;
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) baseName = baseName.substring(0, lastDot);

        // 清理常见标签：[b], [h], [t], (ch), (hack), (Unl) 等
        String cleaned = baseName
                .replaceAll("\\[.*?\\]", "")  // 移除 [...]
                .replaceAll("\\(.*?\\)", "")  // 移除 (...)
                .replaceAll("[_\\-](ch|hack|Hack|unl|Unl|beta|Beta|proto|Proto).*$", "")
                .trim();

        if (cleaned.isEmpty() || cleaned.length() < 2) return null;

        try {
            // 先尝试精确名称匹配
            List<RomDb> candidates = romDbMapper.searchBySystemAndName(systemId, cleaned, 5);
            if (candidates.isEmpty()) return null;

            // 如果只有一个候选，直接返回
            if (candidates.size() == 1) {
                return candidates.get(0);
            }

            // 多个候选时，优先选择名称完全匹配的
            for (RomDb c : candidates) {
                if (c.getGameName() != null && c.getGameName().equalsIgnoreCase(cleaned)) {
                    return c;
                }
            }

            // 否则返回第一个候选（最相关的）
            return candidates.get(0);
        } catch (Exception e) {
            logger.debug("rom_db: 名称匹配回退失败: {} - {}", fileName, e.getMessage());
            return null;
        }
    }

    @Override
    public List<RomDb> searchByName(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return Collections.emptyList();
        return romDbMapper.searchByName(keyword.trim(), limit);
    }

    @Override
    public List<RomDb> searchBySystemAndName(int systemId, String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return Collections.emptyList();
        return romDbMapper.searchBySystemAndName(systemId, keyword.trim(), limit);
    }

    @Override
    public RomDb lookupBySerial(String serial, int systemId) {
        return matchBySerial(serial, systemId);
    }

    @Override
    public boolean isReady() {
        // 只要表里有数据就视为可用（不再依赖启动自动导入）
        return romDbMapper.countAll() > 0;
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRecords", romDbMapper.countAll());
        stats.put("running", initializer.isRunning());
        stats.put("status", initializer.getStatus());
        stats.put("lastResult", initializer.getLastResult());
        stats.put("platforms", romDbMapper.countByPlatform().size());

        List<Map<String, Object>> sourceStats = romDbMapper.countBySource();
        for (Map<String, Object> s : sourceStats) {
            stats.put("source_" + s.get("source"), s.get("count"));
        }
        return stats;
    }

    @Override
    public List<Map<String, Object>> getPlatformStats() {
        return romDbMapper.countByPlatform();
    }

    @Override
    public List<Map<String, Object>> getSystemStats() {
        return romDbMapper.countBySystem();
    }

    @Override
    public int reimportPlatform(String platformName) {
        // 删除旧数据
        romDbMapper.deleteByPlatform(platformName);

        // 查找 DAT 文件
        File datFile = findDatFile(platformName);
        if (datFile == null || !datFile.exists()) {
            logger.warn("reimport: 未找到 {} 的 DAT 文件", platformName);
            return 0;
        }

        int systemId = RomDbPlatformMapping.getSystemId(platformName);
        String source = determineSource(datFile);

        try {
            List<RomDb> entries = ClrMameProDatParser.parse(datFile, platformName, systemId, source);
            if (entries.isEmpty()) return 0;

            int inserted = 0;
            List<RomDb> batch = new ArrayList<>(500);
            for (RomDb entry : entries) {
                batch.add(entry);
                if (batch.size() >= 500) {
                    romDbMapper.batchInsert(batch);
                    inserted += batch.size();
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                romDbMapper.batchInsert(batch);
                inserted += batch.size();
            }
            logger.info("reimport: {} 重新导入 {} 条", platformName, inserted);
            return inserted;
        } catch (IOException e) {
            logger.error("reimport: 解析失败 {} - {}", platformName, e.getMessage());
            return 0;
        }
    }

    // ========== 内部方法 ==========

    @Override
    public boolean hasDataForSystem(int systemId) {
        return romDbMapper.countBySystemId(systemId) > 0;
    }

    @Override
    public Map<String, Integer> batchMatch(List<Long> gameIds, int systemId, String language) {
        return batchMatch(gameIds, systemId, language, false);
    }

    @Override
    public Map<String, Integer> batchMatch(List<Long> gameIds, int systemId, String language, boolean fileNameExtract) {
        // 兼容旧调用：fileNameExtract=true 等价于 methods={crc,serial,name}；false 等价于 {crc,serial}
        Set<String> methods = fileNameExtract ? Set.of("crc", "serial", "name") : Set.of("crc", "serial");
        return batchMatch(gameIds, systemId, language, methods);
    }

    @Override
    public Map<String, Integer> batchMatch(List<Long> gameIds, int systemId, String language, Set<String> methods) {
        long t0 = System.currentTimeMillis();
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", gameIds.size());
        boolean isDiscSystem = DiscSerialExtractor.isSupported(systemId);
        logger.info("rom_db 批量匹配: systemId={}, methods={}, isDisc={}", systemId, methods, isDiscSystem);

        // Phase 1: 批量加载游戏 + 并行匹配（I/O 密集）
        int parallelism = Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);

        // 先批量加载游戏对象
        Map<Long, Game> gameMap = new LinkedHashMap<>();
        for (Long gameId : gameIds) {
            try {
                Game game = gameMapper.selectGameById(gameId);
                if (game != null) gameMap.put(gameId, game);
            } catch (Exception e) {
                logger.warn("rom_db: 加载游戏 {} 失败: {}", gameId, e.getMessage());
            }
        }
        long t1 = System.currentTimeMillis();
        logger.info("rom_db 批量匹配: 加载 {}/{} 个游戏 ({}ms)，启动 {} 线程并行匹配", gameMap.size(), gameIds.size(), t1 - t0, parallelism);

        // 并行匹配：每个游戏一个任务
        List<Future<MatchResult>> futures = new ArrayList<>();
        for (Map.Entry<Long, Game> entry : gameMap.entrySet()) {
            Long gameId = entry.getKey();
            Game game = entry.getValue();
            futures.add(executor.submit(() -> matchSingleGame(game, systemId, isDiscSystem, methods)));
        }

        // 收集结果
        List<MatchResult> matchResults = new ArrayList<>();
        for (Future<MatchResult> future : futures) {
            try {
                MatchResult mr = future.get(60, TimeUnit.SECONDS);
                if (mr != null) matchResults.add(mr);
            } catch (Exception e) {
                logger.debug("rom_db: 并行匹配任务异常: {}", e.getMessage());
            }
        }
        executor.shutdown();
        long t2 = System.currentTimeMillis();
        logger.info("rom_db 并行匹配阶段完成: 命中 {} 个 ({}ms)", matchResults.size(), t2 - t1);

        // Phase 2: 串行 DB 更新（快，每次 <5ms）
        int matched = 0;
        int updated = 0;
        for (MatchResult mr : matchResults) {
            matched++;
            Game game = mr.game;
            RomDb romDbResult = mr.romDbResult;
            String matchKey = mr.matchKey;

            boolean nameUpdated = false;
            String fileName = new File(mr.filePath).getName();
            String gameName = romDbResult.getGameNameByLang(language);

            if (gameName != null && (game.getName() == null || game.getName().isBlank()
                    || game.getName().equals(fileName) || isFileNameLike(game.getName()))) {
                game.setName(gameName);
                nameUpdated = true;
            }
            if (romDbResult.getSerial() != null) {
                game.setDiscSerial(romDbResult.getSerial());
            }
            // 回填元数据（仅当 Game 对应字段为空时）
            if (romDbResult.getDeveloper() != null && !romDbResult.getDeveloper().isBlank()
                    && (game.getDeveloper() == null || game.getDeveloper().isBlank())) {
                game.setDeveloper(romDbResult.getDeveloper());
            }
            if (romDbResult.getPublisher() != null && !romDbResult.getPublisher().isBlank()
                    && (game.getPublisher() == null || game.getPublisher().isBlank())) {
                game.setPublisher(romDbResult.getPublisher());
            }
            if (romDbResult.getReleaseyear() != null && !romDbResult.getReleaseyear().isBlank()
                    && (game.getReleasedate() == null || game.getReleasedate().isBlank())) {
                game.setReleasedate(romDbResult.getReleaseyear());
            }
            if (romDbResult.getGenre() != null && !romDbResult.getGenre().isBlank()
                    && (game.getGenre() == null || game.getGenre().isBlank())) {
                game.setGenre(romDbResult.getGenre());
            }
            game.setMatched(true);
            try {
                gameMapper.updateGame(game);
                updated++;
                logger.info("rom_db 匹配成功: {} → {} (source={}), nameUpdated={}",
                        fileName, matchKey, romDbResult.getSource(), nameUpdated);
            } catch (Exception e) {
                logger.warn("rom_db 更新游戏 {} 失败: {}", game.getId(), e.getMessage());
            }
        }

        result.put("matched", matched);
        result.put("updated", updated);
        long t3 = System.currentTimeMillis();
        logger.info("rom_db 批量匹配完成: total={}, matched={}, updated={}, 总耗时 {}ms (加载 {}ms + 匹配 {}ms + 更新 {}ms)",
                gameIds.size(), matched, updated, t3 - t0, t1 - t0, t2 - t1, t3 - t2);
        return result;
    }

    /** 单个游戏匹配结果 */
    private static class MatchResult {
        Game game;
        RomDb romDbResult;
        String matchKey;
        String filePath;
    }

    /** 并行执行：提取标识 + 查 rom_db（不写 DB） */
    private MatchResult matchSingleGame(Game game, int systemId, boolean isDiscSystem, boolean fileNameExtract) {
        try {
            String filePath = game.getAbsolutePath();
            if (filePath == null || filePath.isBlank()) {
                filePath = game.getPath();
            }
            if (filePath == null || filePath.isBlank()) {
                logger.info("rom_db 匹配跳过(id={}): absolutePath 和 path 均为空", game.getId());
                return null;
            }

            String fileName = new File(filePath).getName();
            RomDb romDbResult = null;
            String matchKey = null;

            if (isDiscSystem) {
                romDbResult = matchByDiscFile(filePath, systemId);
                if (romDbResult != null) matchKey = romDbResult.getSerial();
            } else {
                if (CartridgeHeaderReader.isSupported(systemId)) {
                    try {
                        String headerSerial = CartridgeHeaderReader.extractSerial(filePath, systemId);
                        if (headerSerial != null) {
                            romDbResult = matchBySerial(headerSerial, systemId);
                            logger.info("rom_db 卡带头部匹配: file={}, serial={}, matched={}", fileName, headerSerial, romDbResult != null ? romDbResult.getGameName() : "null");
                            if (romDbResult != null) matchKey = romDbResult.getSerial();
                            // 卡带头部提取可能得到的是内部标题（如 SNES 的 “SUPER MARIO WORLD”），非 serial，回退到名称模糊匹配
                            if (romDbResult == null) {
                                java.util.List<RomDb> nameCandidates = romDbMapper.searchBySystemAndName(systemId, headerSerial, 1);
                                if (!nameCandidates.isEmpty()) {
                                    romDbResult = nameCandidates.get(0);
                                    matchKey = romDbResult.getGameName();
                                    logger.info("rom_db 卡带头部名称匹配成功: file={}, title={} → {}", fileName, headerSerial, matchKey);
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.info("rom_db: 卡带头部提取异常: {} - {}", fileName, e.getMessage());
                    }
                }
                if (romDbResult == null) {
                    romDbResult = matchByFileCrc32(filePath, systemId);
                    if (romDbResult != null) {
                        matchKey = romDbResult.getCrc32();
                        logger.info("rom_db CRC 匹配成功: {} → {}", fileName, matchKey);
                    }
                }
                // L3: 名称匹配回退（CRC 失败时尝试按游戏名搜索）
                if (romDbResult == null) {
                    romDbResult = matchByNameFallback(fileName, systemId);
                    if (romDbResult != null) {
                        matchKey = romDbResult.getGameName();
                        logger.info("rom_db 名称匹配成功: {} → {}", fileName, matchKey);
                    }
                }
            }

            if (romDbResult == null && fileNameExtract) {
                String fnSerial = DiscSerialExtractor.extract(fileName, systemId);
                if (fnSerial != null) {
                    romDbResult = matchBySerial(fnSerial, systemId);
                    if (romDbResult != null) matchKey = fnSerial;
                }
            }

            if (romDbResult == null || matchKey == null) return null;

            MatchResult mr = new MatchResult();
            mr.game = game;
            mr.romDbResult = romDbResult;
            mr.matchKey = matchKey;
            mr.filePath = filePath;
            return mr;
        } catch (Exception e) {
            logger.debug("rom_db: 匹配游戏 {} 异常: {}", game.getId(), e.getMessage());
            return null;
        }
    }

    /** 并行执行：按 methods 选择的路由匹配（不写 DB） */
    private MatchResult matchSingleGame(Game game, int systemId, boolean isDiscSystem, Set<String> methods) {
        try {
            String filePath = game.getAbsolutePath();
            if (filePath == null || filePath.isBlank()) {
                filePath = game.getPath();
            }
            if (filePath == null || filePath.isBlank()) {
                logger.info("rom_db 匹配跳过(id={}): absolutePath 和 path 均为空", game.getId());
                return null;
            }

            String fileName = new File(filePath).getName();
            RomDb romDbResult = null;
            String matchKey = null;

            // L1: 编号提取（ROM 内部：光盘内部文件 → 卡带头部；毫秒级，不校验 ROM 完整性）
            if (methods.contains("serial")) {
                // L1a: 光盘系统 → matchByDiscFile（SYSTEM.CNF/PARAM.SFO/IP.BIN/NGC头部/Wii）
                if (isDiscSystem && DiscSerialExtractor.isSupported(systemId)) {
                    romDbResult = matchByDiscFile(filePath, systemId);
                    if (romDbResult != null) matchKey = romDbResult.getSerial();
                }
                // L1b: 卡带头部 ID 提取（毫秒级）
                if (romDbResult == null && CartridgeHeaderReader.isSupported(systemId)) {
                    try {
                        String headerSerial = CartridgeHeaderReader.extractSerial(filePath, systemId);
                        if (headerSerial != null) {
                            romDbResult = matchBySerial(headerSerial, systemId);
                            if (romDbResult != null) {
                                matchKey = romDbResult.getSerial();
                                logger.info("rom_db 卡带头部匹配: file={}, serial={}, matched={}", fileName, headerSerial, romDbResult.getGameName());
                            } else {
                                // SNES 等平台的头部提取得到的是内部标题，回退到名称模糊匹配
                                java.util.List<RomDb> nameCandidates = romDbMapper.searchBySystemAndName(systemId, headerSerial, 1);
                                if (!nameCandidates.isEmpty()) {
                                    romDbResult = nameCandidates.get(0);
                                    matchKey = romDbResult.getGameName();
                                    logger.info("rom_db 卡带头部名称匹配成功: file={}, title={} → {}", fileName, headerSerial, matchKey);
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.info("rom_db: 卡带头部提取异常: {} - {}", fileName, e.getMessage());
                    }
                }
            }

            // L2: CRC 匹配（全文件哈希，最准确但很慢）
            if (romDbResult == null && methods.contains("crc")) {
                romDbResult = matchByFileCrc32(filePath, systemId);
                if (romDbResult != null) {
                    matchKey = romDbResult.getCrc32();
                    logger.info("rom_db CRC 匹配成功: {} → {}", fileName, matchKey);
                }
            }

            // L3: 文件名匹配（从文件名提取编号 → 名称模糊匹配）
            if (romDbResult == null && methods.contains("name")) {
                // L3a: 文件名正则提取编号（如从 "Game (SLUS-12345).iso" 提取 SLUS-12345）
                String fnSerial = DiscSerialExtractor.extract(fileName, systemId);
                if (fnSerial != null) {
                    romDbResult = matchBySerial(fnSerial, systemId);
                    if (romDbResult != null) {
                        matchKey = fnSerial;
                        logger.info("rom_db 文件名编号提取成功: {} → {}", fileName, matchKey);
                    }
                }
                // L3b: 名称模糊匹配（去标签后按游戏名搜索 rom_db）
                if (romDbResult == null) {
                    romDbResult = matchByNameFallback(fileName, systemId);
                    if (romDbResult != null) {
                        matchKey = romDbResult.getGameName();
                        logger.info("rom_db 名称匹配成功: {} → {}", fileName, matchKey);
                    }
                }
            }

            if (romDbResult == null || matchKey == null) return null;

            MatchResult mr = new MatchResult();
            mr.game = game;
            mr.romDbResult = romDbResult;
            mr.matchKey = matchKey;
            mr.filePath = filePath;
            return mr;
        } catch (Exception e) {
            logger.debug("rom_db: 匹配游戏 {} 异常: {}", game.getId(), e.getMessage());
            return null;
        }
    }

    private boolean isFileNameLike(String name) {
        return name.contains(".") || name.contains("(") || name.contains("[")
                || name.contains("_") || name.matches(".*\\d{4,}.*");
    }

    /**
     * 计算文件的 CRC32。
     * 对于归档文件（.zip/.7z），提取内部 ROM entry 的 CRC（而非压缩包自身 CRC）。
     * No-Intro 存储的是内部原始 ROM 文件的 CRC。
     */
    private String calculateCrc32(String filePath) throws IOException {
        File file = new File(filePath);
        String name = file.getName().toLowerCase();

        if (name.endsWith(".zip")) {
            return calculateArchiveCrc(file);
        }
        // 单文件（.pce/.nes/.sfc 等）直接计算
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[8192];
        try (FileInputStream fis = new FileInputStream(file)) {
            int len;
            while ((len = fis.read(buffer)) != -1) {
                crc.update(buffer, 0, len);
            }
        }
        return String.format("%08X", crc.getValue());
    }

    /**
     * 从 ZIP 归档中提取内部 ROM entry 的 CRC32。
     * 策略：优先找与压缩包同名的 entry，否则取最大文件。
     */
    private String calculateArchiveCrc(File zipFile) {
        // 尝试多种编码打开 ZIP（cp437=ZIP 原始规范, GBK=Windows 中文, UTF-8=标准）
        Charset[] charsets = { Charset.forName("cp437"), Charset.forName("GBK"), StandardCharsets.UTF_8 };
        ZipFile zip = null;
        Exception lastError = null;
        for (Charset cs : charsets) {
            try {
                zip = new ZipFile.Builder().setFile(zipFile).setCharset(cs).get();
                break;
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (zip == null) {
            logger.info("rom_db ZIP 打开失败（所有编码均失败）: {}, lastError={}", zipFile.getName(), lastError != null ? lastError.getMessage() : "unknown");
            // 最终回退：计算 ZIP 容器自身的 CRC32（不匹配 rom_db 但至少有值）
            try {
                CRC32 crc = new CRC32();
                byte[] buffer = new byte[8192];
                try (FileInputStream fis = new FileInputStream(zipFile)) {
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        crc.update(buffer, 0, len);
                    }
                }
                return String.format("%08X", crc.getValue());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        String baseName = zipFile.getName();
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) baseName = baseName.substring(0, lastDot);

        final ZipFile finalZip = zip;
        try (finalZip) {
            ZipArchiveEntry bestMatch = null;
            long largestSize = 0;

            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                // 跳过元数据文件
                String entryName = entry.getName().toLowerCase();
                if (entryName.endsWith(".txt") || entryName.endsWith(".info") || entryName.startsWith("__")) continue;

                // 优先同名
                String entryBase = entry.getName();
                int eDot = entryBase.lastIndexOf('.');
                if (eDot > 0) entryBase = entryBase.substring(0, eDot);
                // 去掉路径前缀
                int slash = Math.max(entryBase.lastIndexOf('/'), entryBase.lastIndexOf('\\'));
                if (slash >= 0) entryBase = entryBase.substring(slash + 1);

                if (entryBase.equalsIgnoreCase(baseName)) {
                    bestMatch = entry;
                    break;
                }
                if (entry.getSize() > largestSize) {
                    largestSize = entry.getSize();
                    bestMatch = entry;
                }
            }

            if (bestMatch != null) {
                // 自己计算 CRC32（不信任 ZIP entry 的 CRC 字段，与 GameID 项目一致）
                try (java.io.InputStream is = finalZip.getInputStream(bestMatch)) {
                    CRC32 crc = new CRC32();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        crc.update(buffer, 0, len);
                    }
                    return String.format("%08X", crc.getValue());
                }
            }
            logger.debug("rom_db ZIP 内无有效 entry: {}", zipFile.getName());
            return null;
        } catch (IOException e) {
            logger.info("rom_db ZIP 解析失败({}): {}", zipFile.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 在 data/rom-db/ 下查找指定平台的 DAT 文件。
     */
    private File findDatFile(String platformName) {
        String fileName = platformName + ".dat";
        String[] subDirs = {"redump", "no-intro", "serial"};
        for (String sub : subDirs) {
            File f = new File("data/rom-db/" + sub + "/" + fileName);
            if (f.exists()) return f;
        }
        return null;
    }

    /**
     * 根据文件路径判断数据来源。
     */
    private String determineSource(File datFile) {
        String path = datFile.getAbsolutePath().replace('\\', '/');
        if (path.contains("/redump/")) return "redump";
        if (path.contains("/serial/")) return "serial";
        return "no-intro";
    }

    // ==================== 模糊匹配 ====================

    @Override
    public String cleanFileName(String fileName) {
        if (fileName == null) return "";
        String s = fileName;
        // 去扩展名
        int lastDot = s.lastIndexOf('.');
        if (lastDot > 0) s = s.substring(0, lastDot);
        // 去括号/方括号内容
        s = s.replaceAll("\\([^)]*\\)", "");
        s = s.replaceAll("\\[[^]]*\\]", "");
        // 下划线/连字符 → 空格
        s = s.replace('_', ' ').replace('-', ' ');
        // 去多余空格，转小写
        s = s.trim().replaceAll("\\s+", " ").toLowerCase();
        // 去尾部数字后缀（如 contra2 → contra 2 保留，但纯尾部数字去掉）
        return s.trim();
    }

    @Override
    public List<Map<String, Object>> getFuzzyCandidates(List<Long> gameIds, int systemId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (gameIds == null || gameIds.size() > 30) return result;

        for (Long gameId : gameIds) {
            Game game = gameMapper.selectGameById(gameId);
            if (game == null) continue;

            String filePath = game.getAbsolutePath();
            if (filePath == null || filePath.isBlank()) filePath = game.getPath();
            String fileName = (filePath != null && !filePath.isBlank()) ? new File(filePath).getName() : game.getName();
            if (fileName == null) fileName = "";

            String cleaned = cleanFileName(fileName);
            if (cleaned.isBlank()) continue;

            // 用清洗后的名字按词搜索：取第一个有意义的词（至少3字符）
            String[] words = cleaned.split("\\s+");
            String searchWord = null;
            for (String w : words) {
                if (w.length() >= 3) { searchWord = w; break; }
            }
            if (searchWord == null && words.length > 0) searchWord = words[0];
            if (searchWord == null || searchWord.isBlank()) continue;

            List<RomDb> candidates = romDbMapper.searchBySystemAndName(systemId, searchWord, 10);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("gameId", gameId);
            entry.put("fileName", fileName);
            entry.put("currentName", game.getName());
            List<Map<String, Object>> candList = new ArrayList<>();
            for (RomDb c : candidates) {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("romDbId", c.getId());
                cm.put("gameName", c.getGameName());
                cm.put("serial", c.getSerial());
                cm.put("platform", c.getPlatform());
                candList.add(cm);
            }
            entry.put("candidates", candList);
            result.add(entry);
        }
        return result;
    }

    @Override
    public boolean confirmFuzzyMatch(long gameId, long romDbId) {
        Game game = gameMapper.selectGameById(gameId);
        if (game == null) return false;

        RomDb r = romDbMapper.findById(romDbId);
        if (r == null) return false;

        // 回填所有字段
        if (r.getGameName() != null) game.setName(r.getGameName());
        if (r.getSerial() != null) game.setDiscSerial(r.getSerial());
        if (r.getDeveloper() != null && !r.getDeveloper().isBlank()
                && (game.getDeveloper() == null || game.getDeveloper().isBlank())) {
            game.setDeveloper(r.getDeveloper());
        }
        if (r.getPublisher() != null && !r.getPublisher().isBlank()
                && (game.getPublisher() == null || game.getPublisher().isBlank())) {
            game.setPublisher(r.getPublisher());
        }
        if (r.getReleaseyear() != null && !r.getReleaseyear().isBlank()
                && (game.getReleasedate() == null || game.getReleasedate().isBlank())) {
            game.setReleasedate(r.getReleaseyear());
        }
        if (r.getGenre() != null && !r.getGenre().isBlank()
                && (game.getGenre() == null || game.getGenre().isBlank())) {
            game.setGenre(r.getGenre());
        }
        game.setMatched(true);
        gameMapper.updateGame(game);
        logger.info("模糊匹配确认: gameId={} → romDbId={} ({})", gameId, romDbId, r.getGameName());
        return true;
    }

    @Override
    @Async
    public void batchMatchByPlatformsAsync(Long taskId, List<Long> platformIds, String language) {
        // 兼容旧调用：默认启用所有方法
        batchMatchByPlatformsAsync(taskId, platformIds, language, Set.of("crc", "serial", "name"));
    }

    @Override
    @Async
    public void batchMatchByPlatformsAsync(Long taskId, List<Long> platformIds, String language, Set<String> methods) {
        if (taskService == null || platformService == null) {
            logger.error("batchMatchByPlatformsAsync: TaskService 或 PlatformService 不可用");
            return;
        }
        try {
            int totalGames = 0;
            int totalMatched = 0;
            int totalUpdated = 0;
            int platformCount = 0;
            List<String> skippedPlatforms = new ArrayList<>();

            // 收集所有平台和游戏
            List<Map.Entry<Platform, List<Game>>> platformGamesList = new ArrayList<>();
            for (Long platformId : platformIds) {
                Platform platform = platformService.getPlatformById(platformId);
                if (platform == null || platform.getSystemId() == null || platform.getSystemId() == 0) {
                    if (platform != null) {
                        skippedPlatforms.add(platform.getName() + " (未绑定系统)");
                    }
                    continue;
                }
                if (romDbMapper.countBySystemId(platform.getSystemId()) <= 0) {
                    skippedPlatforms.add(platform.getName() + " (无匹配库数据)");
                    continue;
                }
                List<Game> games = gameMapper.selectGamesByPlatformId(platformId);
                if (games.isEmpty()) continue;
                platformGamesList.add(Map.entry(platform, games));
                totalGames += games.size();
            }

            if (totalGames == 0) {
                String msg = skippedPlatforms.isEmpty()
                        ? "没有可匹配的游戏"
                        : "没有可匹配的平台（跳过: " + String.join(", ", skippedPlatforms) + "）";
                taskService.completeTask(taskId, msg, null);
                if (notificationService != null) {
                    notificationService.send("本地匹配", msg, "info", "task");
                }
                return;
            }

            taskService.updateTaskProgress(taskId, 5, "开始本地匹配...", 0, totalGames);

            int processed = 0;
            for (Map.Entry<Platform, List<Game>> entry : platformGamesList) {
                Platform platform = entry.getKey();
                List<Game> games = entry.getValue();
                platformCount++;

                taskService.updateTaskProgress(taskId, 5 + (processed * 90) / totalGames,
                        "正在匹配: " + platform.getName() + " (" + games.size() + " 个游戏)", processed, totalGames);

                List<Long> gameIds = games.stream().map(Game::getId).toList();
                Map<String, Integer> matchResult = batchMatch(gameIds, platform.getSystemId(), language, methods);

                totalMatched += matchResult.get("matched");
                totalUpdated += matchResult.get("updated");
                processed += games.size();

                taskService.updateTaskProgress(taskId, 5 + (processed * 90) / totalGames,
                        "已完成: " + platform.getName(), processed, totalGames);
            }

            String langLabel = "ja".equals(language) ? "日文" : "cn".equals(language) ? "中文" : "英文";
            StringBuilder msg = new StringBuilder();
            msg.append(String.format("匹配完成（%s）：%d 个平台，共 %d 个游戏，命中 %d 个，更新 %d 个",
                    langLabel, platformCount, totalGames, totalMatched, totalUpdated));
            if (!skippedPlatforms.isEmpty()) {
                msg.append("。跳过: ").append(String.join(", ", skippedPlatforms));
            }

            taskService.completeTask(taskId, msg.toString(), null);
            if (notificationService != null) {
                notificationService.send("本地匹配",
                        String.format("命中 %d/%d 个，更新 %d 个", totalMatched, totalGames, totalUpdated),
                        "success", "task");
            }

        } catch (Exception e) {
            logger.error("异步本地匹配失败", e);
            taskService.failTask(taskId, "匹配失败: " + e.getMessage(), null);
            if (notificationService != null) {
                notificationService.send("本地匹配失败", e.getMessage(), "error", "task");
            }
        }
    }
}
