package com.gamelist.config;

import com.gamelist.mapper.RomDbMapper;
import com.gamelist.model.RomDb;
import com.gamelist.util.ClrMameProDatParser;
import com.gamelist.util.NameSplitter;
import com.gamelist.util.RdbParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ROM 识别数据库手动导入器。
 * <p>
 * 不再开机自动导入。由系统设置页的两个按钮触发：
 * <ul>
 *   <li>「导入 DAT」扫描 data/rom-db/metadat/ 下的 .dat 文件（自动检测 redump/no-intro/serial 格式）</li>
 *   <li>「导入 RDB」扫描 data/rom-db/rdb/ 下的 .rdb 文件（RARCHDB / MessagePack 格式）</li>
 * </ul>
 * 幂等设计：已有数据的平台自动跳过。
 */
@Component
public class RomDbDataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(RomDbDataInitializer.class);

    /** 批量插入每批大小 */
    private static final int BATCH_SIZE = 500;

    private final RomDbMapper romDbMapper;

    private final AtomicInteger totalImported = new AtomicInteger(0);
    private final AtomicInteger totalSkipped = new AtomicInteger(0);
    private volatile boolean running = false;
    private volatile String status = "IDLE";
    private volatile String lastResult = "";

    // ===== 文件级进度追踪 =====
    private volatile int totalFiles = 0;
    private volatile int currentFileIndex = 0;
    private volatile String currentFileName = "";
    private volatile String phase = "";          // SCANNING / LOADING / MERGING / WRITING
    private volatile int currentMerged = 0;      // 当前内存 mergeMap 累计条数

    public RomDbDataInitializer(RomDbMapper romDbMapper) {
        this.romDbMapper = romDbMapper;
    }

    // ======================== 公开触发接口 ========================

    /**
     * 手动触发 DAT 导入（后台线程）。
     * @return 如果已在运行返回 false，否则返回 true 表示已启动
     */
    public synchronized boolean importAllDat() {
        if (running) return false;
        running = true;
        totalImported.set(0);
        totalSkipped.set(0);
        totalFiles = 0;
        currentFileIndex = 0;
        currentFileName = "";
        currentMerged = 0;
        phase = "SCANNING";
        status = "IMPORTING_DAT";
        lastResult = "";
        new Thread(() -> {
            try {
                doImportDat();
                lastResult = String.format("DAT 导入完成：共导入 %d 条", totalImported.get());
                status = "IDLE";
            } catch (Exception e) {
                logger.error("DAT 导入失败", e);
                lastResult = "DAT 导入失败: " + e.getMessage();
                status = "IDLE";
            } finally {
                running = false;
            }
        }, "RomDbImport-DAT").start();
        return true;
    }

    /**
     * 手动触发 RDB 导入（后台线程）。
     * @return 如果已在运行返回 false，否则返回 true 表示已启动
     */
    public synchronized boolean importAllRdb() {
        if (running) return false;
        running = true;
        totalImported.set(0);
        totalSkipped.set(0);
        totalFiles = 0;
        currentFileIndex = 0;
        currentFileName = "";
        currentMerged = 0;
        phase = "SCANNING";
        status = "IMPORTING_RDB";
        lastResult = "";
        new Thread(() -> {
            try {
                doImportRdb();
                lastResult = String.format("RDB 导入完成：共导入 %d 条", totalImported.get());
                status = "IDLE";
            } catch (Exception e) {
                logger.error("RDB 导入失败", e);
                lastResult = "RDB 导入失败: " + e.getMessage();
                status = "IDLE";
            } finally {
                running = false;
            }
        }, "RomDbImport-RDB").start();
        return true;
    }

    // ======================== DAT 导入（合并模式） ========================

    /**
     * 导入单个 DAT 文件，将解析结果合并到内存 map 中。
     * 合并规则：同 (systemId + CRC32) 先到的 gameName 锁定，后续只补空字段。
     * @return 本次合并的条目数
     */
    private int mergeDatFile(File datFile, String source, Map<String, RomDb> mergeMap) {
        String fileName = datFile.getName();
        String platformName = ClrMameProDatParser.platformNameFromFileName(fileName);
        int systemId = RomDbPlatformMapping.getSystemId(platformName);

        List<RomDb> entries;
        try {
            entries = ClrMameProDatParser.parse(datFile, platformName, systemId, source);
        } catch (Exception e) {
            logger.error("    [DAT] 解析失败: {} - {}", datFile.getName(), e.getMessage());
            return 0;
        }

        if (entries.isEmpty()) {
            return 0;
        }

        int merged = 0;
        for (RomDb entry : entries) {
            detectAndSetLanguage(entry); // 检测语言并分离到对应字段
            String crc = entry.getCrc32();
            if (crc == null || crc.isBlank()) {
                // 无 CRC 的条目（纯 serial 光盘数据）直接加入，用 serial 作 key
                String serial = entry.getSerial();
                if (serial != null && !serial.isBlank()) {
                    String key = systemId + ":S:" + serial;
                    if (!mergeMap.containsKey(key)) {
                        mergeMap.put(key, entry);
                        merged++;
                    } else {
                        mergeFields(mergeMap.get(key), entry);
                        merged++;
                    }
                }
                continue;
            }

            String key = systemId + ":C:" + crc;
            if (mergeMap.containsKey(key)) {
                // 已存在：补充空字段，不覆盖已有非空字段
                mergeFields(mergeMap.get(key), entry);
            } else {
                mergeMap.put(key, entry);
            }
            merged++;
        }

        logger.info("    [DAT] {} (systemId={}): 解析 {} 条，合并后累计 {} 条",
                platformName, systemId, entries.size(), merged);
        return merged;
    }

    /**
     * 将 src 的非空字段补充到 target 的空字段上（不覆盖已有值）。
     */
    private void mergeFields(RomDb target, RomDb src) {
        if (target.getGameName() == null && src.getGameName() != null) target.setGameName(src.getGameName());
        if (target.getGameNameJp() == null && src.getGameNameJp() != null) target.setGameNameJp(src.getGameNameJp());
        if (target.getGameNameCn() == null && src.getGameNameCn() != null) target.setGameNameCn(src.getGameNameCn());
        if (target.getSerial() == null && src.getSerial() != null) target.setSerial(src.getSerial());
        if (target.getRegion() == null && src.getRegion() != null) target.setRegion(src.getRegion());
        if (target.getMd5() == null && src.getMd5() != null) target.setMd5(src.getMd5());
        if (target.getSha1() == null && src.getSha1() != null) target.setSha1(src.getSha1());
        if (target.getDeveloper() == null && src.getDeveloper() != null) target.setDeveloper(src.getDeveloper());
        if (target.getPublisher() == null && src.getPublisher() != null) target.setPublisher(src.getPublisher());
        if (target.getReleaseyear() == null && src.getReleaseyear() != null) target.setReleaseyear(src.getReleaseyear());
        if (target.getGenre() == null && src.getGenre() != null) target.setGenre(src.getGenre());
    }

    /**
     * 检测游戏名语言并设置到对应字段。
     * <ul>
     *   <li>ENGLISH → gameName</li>
     *   <li>JAPANESE → gameNameJp</li>
     *   <li>CHINESE → gameNameCn</li>
     *   <li>MIXED_EN_JP → gameName + gameNameJp</li>
     *   <li>MIXED_EN_CN → gameName（整体存英文字段）</li>
     * </ul>
     */
    private void detectAndSetLanguage(RomDb romDb) {
        String name = romDb.getGameName();
        if (name == null || name.isBlank()) return;

        NameSplitter.DetectionResult result = NameSplitter.detect(name);
        switch (result.getType()) {
            case JAPANESE:
                romDb.setGameNameJp(name);
                romDb.setGameName(null); // 无英文名，清空
                break;
            case CHINESE:
                romDb.setGameNameCn(name);
                romDb.setGameName(null);
                break;
            case MIXED_EN_JP:
                romDb.setGameName(result.getEnglishName());
                romDb.setGameNameJp(result.getJapaneseName());
                break;
            case MIXED_EN_CN:
                // 英中混合整体存 gameName，未来可拆分
                break;
            case ENGLISH:
            default:
                // 纯英文，保持原样
                break;
        }
    }

    private void doImportDat() {
        File datDir = new File("data/rom-db/metadat");
        if (!datDir.exists() || !datDir.isDirectory()) {
            logger.info("  data/rom-db/metadat/ 目录不存在，跳过 DAT 导入。");
            return;
        }

        // 递归收集所有 .dat 文件（包括子目录如 serial/, developer/ 等）
        List<File> datFileList = new ArrayList<>();
        collectDatFiles(datDir, datFileList);
        
        if (datFileList.isEmpty()) {
            logger.info("  [DAT] metadat/ 目录及其子目录无 .dat 文件，跳过。");
            return;
        }

        logger.info("  [DAT] 扫描 metadat/: {} 个 .dat 文件（含子目录），开始增量合并导入", datFileList.size());
        totalFiles = datFileList.size();
        phase = "LOADING";

        // 加载现有数据到内存 mergeMap
        Map<String, RomDb> mergeMap = loadExistingData();
        int existingCount = mergeMap.size();
        currentMerged = existingCount;
        logger.info("  [DAT] 已加载 rom_db 现有 {} 条记录到内存", existingCount);

        // 合并新 DAT 文件
        phase = "MERGING";
        for (int i = 0; i < datFileList.size(); i++) {
            File datFile = datFileList.get(i);
            currentFileIndex = i + 1;
            currentFileName = datFile.getName();
            try {
                String source = ClrMameProDatParser.detectSource(datFile);
                int merged = mergeDatFile(datFile, source, mergeMap);
                totalImported.addAndGet(merged);
                currentMerged = mergeMap.size();
            } catch (Exception e) {
                logger.error("  [DAT] 导入失败: {} - {}", datFile.getName(), e.getMessage());
            }
        }
        currentFileName = "";

        int newCount = mergeMap.size();
        logger.info("  [DAT] 合并完成: 现有 {} 条 + DAT 合并 → {} 条唯一记录，开始写回", existingCount, newCount);

        // 全量写回：TRUNCATE + batch insert
        phase = "WRITING";
        writeBackAll(mergeMap);

        logger.info("  [DAT] 完成: rom_db 总计 {} 条（新增/补充 {} 条）", newCount, newCount - existingCount);
    }

    /**
     * 递归收集目录下所有 .dat 文件
     */
    private void collectDatFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectDatFiles(f, result);
            } else if (f.getName().endsWith(".dat")) {
                result.add(f);
            }
        }
    }

    // ======================== RDB 导入 ========================

    private void doImportRdb() {
        File rdbDir = new File("data/rom-db/rdb");
        if (!rdbDir.exists() || !rdbDir.isDirectory()) {
            logger.info("  data/rom-db/rdb/ 目录不存在，跳过 RDB 导入。");
            return;
        }

        File[] rdbFiles = rdbDir.listFiles((d, name) -> name.endsWith(".rdb"));
        if (rdbFiles == null || rdbFiles.length == 0) {
            logger.info("  [RDB] rdb/ 目录为空，跳过。");
            return;
        }

        logger.info("  [RDB] 扫描 rdb/: {} 个 .rdb 文件，开始增量合并导入", rdbFiles.length);
        totalFiles = rdbFiles.length;
        phase = "LOADING";

        // 加载现有数据到内存 mergeMap
        Map<String, RomDb> mergeMap = loadExistingData();
        int existingCount = mergeMap.size();
        currentMerged = existingCount;
        logger.info("  [RDB] 已加载 rom_db 现有 {} 条记录到内存", existingCount);

        // 合并 RDB 文件
        phase = "MERGING";
        for (int i = 0; i < rdbFiles.length; i++) {
            File rdbFile = rdbFiles[i];
            currentFileIndex = i + 1;
            currentFileName = rdbFile.getName();
            try {
                int merged = mergeRdbFile(rdbFile, mergeMap);
                totalImported.addAndGet(merged);
                currentMerged = mergeMap.size();
            } catch (Exception e) {
                logger.error("  [RDB] 导入失败: {} - {}", rdbFile.getName(), e.getMessage());
            }
        }
        currentFileName = "";

        int newCount = mergeMap.size();
        logger.info("  [RDB] 合并完成: 现有 {} 条 + RDB 合并 → {} 条唯一记录，开始写回", existingCount, newCount);

        // 全量写回
        phase = "WRITING";
        writeBackAll(mergeMap);

        logger.info("  [RDB] 完成: rom_db 总计 {} 条（新增/补充 {} 条）", newCount, newCount - existingCount);
    }

    /**
     * 将单个 RDB 文件合并到 mergeMap 中。
     * @return 本次合并的条目数
     */
    private int mergeRdbFile(File rdbFile, Map<String, RomDb> mergeMap) {
        String fileName = rdbFile.getName();
        String platformName = fileName.endsWith(".rdb")
                ? fileName.substring(0, fileName.length() - 4) : fileName;
        int systemId = RomDbPlatformMapping.getSystemId(platformName);

        List<Map<String, Object>> entries = RdbParser.parse(rdbFile);
        if (entries.isEmpty()) {
            logger.warn("    [RDB] {}: 解析出 0 条记录", platformName);
            return 0;
        }

        int merged = 0;
        for (Map<String, Object> entry : entries) {
            RomDb r = new RomDb();
            r.setPlatform(platformName);
            r.setSystemId(systemId);
            r.setSource("rdb");
            r.setGameName(safeStr(entry.get("name")));
            detectAndSetLanguage(r); // 检测语言并分离到对应字段

            // crc 特殊处理
            Object crcVal = entry.get("crc");
            if (crcVal instanceof byte[]) {
                r.setCrc32(bytesToHex((byte[]) crcVal));
            } else {
                String s = safeStr(crcVal);
                r.setCrc32(s != null ? s.toUpperCase() : null);
            }

            r.setSerial(safeStr(entry.get("serial")));
            r.setRegion(safeStr(entry.get("region")));
            r.setDeveloper(safeStr(entry.get("developer")));
            r.setPublisher(safeStr(entry.get("publisher")));
            r.setReleaseyear(safeStr(entry.get("releaseyear")));
            r.setGenre(safeStr(entry.get("genre")));

            // md5/sha1
            Object md5Val = entry.get("md5");
            if (md5Val instanceof byte[]) {
                r.setMd5(bytesToHex((byte[]) md5Val));
            } else {
                String md5Str = safeStr(md5Val);
                r.setMd5(md5Str != null ? md5Str.toUpperCase() : null);
            }
            Object sha1Val = entry.get("sha1");
            if (sha1Val instanceof byte[]) {
                r.setSha1(bytesToHex((byte[]) sha1Val));
            } else {
                String sha1Str = safeStr(sha1Val);
                r.setSha1(sha1Str != null ? sha1Str.toUpperCase() : null);
            }

            if (r.getGameName() == null || r.getGameName().isBlank()) continue;

            // 合并到 mergeMap
            String crc = r.getCrc32();
            if (crc != null && !crc.isBlank()) {
                String key = systemId + ":C:" + crc;
                if (mergeMap.containsKey(key)) {
                    mergeFields(mergeMap.get(key), r);
                } else {
                    mergeMap.put(key, r);
                }
            } else if (r.getSerial() != null && !r.getSerial().isBlank()) {
                String key = systemId + ":S:" + r.getSerial();
                if (mergeMap.containsKey(key)) {
                    mergeFields(mergeMap.get(key), r);
                } else {
                    mergeMap.put(key, r);
                }
            }
            merged++;
        }

        // 统计
        long serialCount = entries.stream().filter(e -> {
            Object s = e.get("serial");
            if (s instanceof byte[]) return ((byte[]) s).length > 0;
            if (s instanceof String) return !((String) s).isBlank();
            return false;
        }).count();
        if (serialCount > 0) {
            logger.info("    [RDB] {}: {}/{} 条含 serial，合并 {} 条", platformName, serialCount, entries.size(), merged);
        } else {
            logger.info("    [RDB] {}: 解析 {} 条，合并 {} 条", platformName, entries.size(), merged);
        }
        return merged;
    }

    // ======================== 通用合并工具方法 ========================

    /**
     * 从数据库加载现有数据到 mergeMap。
     */
    private Map<String, RomDb> loadExistingData() {
        Map<String, RomDb> map = new LinkedHashMap<>();
        List<RomDb> existing = romDbMapper.findAll();
        for (RomDb r : existing) {
            String key;
            if (r.getCrc32() != null && !r.getCrc32().isBlank()) {
                key = r.getSystemId() + ":C:" + r.getCrc32();
            } else if (r.getSerial() != null && !r.getSerial().isBlank()) {
                key = r.getSystemId() + ":S:" + r.getSerial();
            } else {
                continue; // 无 CRC 无 serial 的跳过
            }
            map.put(key, r);
        }
        return map;
    }

    /**
     * 全量写回：DELETE ALL + batch insert。
     */
    private void writeBackAll(Map<String, RomDb> mergeMap) {
        romDbMapper.deleteAll();
        List<RomDb> allEntries = new ArrayList<>(mergeMap.values());
        int inserted = 0;
        List<RomDb> batch = new ArrayList<>(BATCH_SIZE);
        for (RomDb entry : allEntries) {
            entry.setId(null); // 清除旧 ID，让数据库重新生成
            batch.add(entry);
            if (batch.size() >= BATCH_SIZE) {
                inserted += batchInsert(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            inserted += batchInsert(batch);
        }
        logger.info("  [写回] DELETE ALL + 批量插入 {} 条完成", inserted);
    }

    /**
     * 手动清空匹配库（用户主动触发）。
     */
    public int clearAll() {
        int count = romDbMapper.countAll();
        if (count > 0) {
            romDbMapper.deleteAll();
            logger.info("  [清零] 已清空 rom_db {} 条记录", count);
        }
        return count;
    }

    /** byte[] → 大写 hex 字符串 */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * 通用 msgpack 值转 String。
     * RDB 中字段可能是 String / byte[] / Long / null，统一处理。
     */
    private static String safeStr(Object val) {
        if (val == null) return null;
        if (val instanceof String) return ((String) val).trim();
        if (val instanceof byte[]) return new String((byte[]) val, java.nio.charset.StandardCharsets.UTF_8).trim();
        if (val instanceof Long) return String.valueOf(val);
        if (val instanceof Integer) return String.valueOf(val);
        return null;
    }

    // ======================== 通用批量插入 ========================

    /**
     * 批量插入（容错：单条失败不影响整批）。
     */
    private int batchInsert(List<RomDb> batch) {
        try {
            romDbMapper.batchInsert(batch);
            return batch.size();
        } catch (Exception e) {
            logger.warn("  批量插入失败，回退逐条: {}", e.getMessage());
            int count = 0;
            for (RomDb entry : batch) {
                try {
                    romDbMapper.insertIgnore(entry);
                    count++;
                } catch (Exception ex) {
                    logger.warn("    单条插入失败: {} - gameName={}, platform={}", ex.getMessage(), entry.getGameName(), entry.getPlatform());
                }
            }
            return count;
        }
    }

    // ======================== 状态查询 ========================

    public boolean isRunning() { return running; }
    public String getStatus() { return status; }
    public int getTotalImported() { return totalImported.get(); }
    public int getTotalSkipped() { return totalSkipped.get(); }
    public String getLastResult() { return lastResult; }
    public int getTotalFiles() { return totalFiles; }
    public int getCurrentFileIndex() { return currentFileIndex; }
    public String getCurrentFileName() { return currentFileName; }
    public String getPhase() { return phase; }
    public int getCurrentMerged() { return currentMerged; }
}
