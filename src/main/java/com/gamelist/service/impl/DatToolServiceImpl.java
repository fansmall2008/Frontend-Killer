package com.gamelist.service.impl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.gamelist.config.RomDbDataInitializer;
import com.gamelist.config.RomDbPlatformMapping;
import com.gamelist.mapper.GameMapper;
import com.gamelist.mapper.RomDbMapper;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.model.RomDb;
import com.gamelist.service.DatToolService;
import com.gamelist.service.PlatformService;

/**
 * DAT 生成器实现：忠实移植离线 tools/romdb-dat-export 与 romdb-translator 的 writeDat 逻辑，
 * 逐平台写标准 ClrMamePro 文本 DAT 到 data/rom-db/metadat/generated/（被 import-dat 递归扫描）。
 */
@Service
public class DatToolServiceImpl implements DatToolService {

    private static final Logger logger = LoggerFactory.getLogger(DatToolServiceImpl.class);

    /** 与 RomDbDataInitializer.doImportDat() 扫描目录保持一致（CWD 相对），保证生成的 DAT 能被吃回。 */
    private static final String DAT_DIR = "data/rom-db/metadat/generated";

    private static final int MAX_LOG_LINES = 500;
    private static final DateTimeFormatter VERSION_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** releasedate 前 4 位数字（年）。 */
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");

    @Autowired
    private RomDbMapper romDbMapper;

    @Autowired
    private GameMapper gameMapper;

    @Autowired
    private PlatformService platformService;

    @Autowired
    private RomDbDataInitializer romDbDataInitializer;

    private final Map<String, Map<String, Object>> progressMap = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

    /** 生成进行中标志（供导入侧互斥护栏）。 */
    private final AtomicBoolean generating = new AtomicBoolean(false);

    // ==================== 预检 ====================

    @Override
    public Map<String, Object> meta() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> romDbList = new ArrayList<>();
        for (Map<String, Object> raw : romDbMapper.countDatStatsByPlatform()) {
            Map<String, Object> row = lowerKeys(raw); // H2 返回大写列标签，按小写键读取
            String platform = str(row.get("platform"));
            if (platform == null) continue;
            int systemId = RomDbPlatformMapping.getSystemId(platform);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("platform", platform);
            m.put("systemId", systemId);
            m.put("totalRows", toLong(row.get("totalrows")));
            m.put("validRows", toLong(row.get("validrows")));
            m.put("resolvable", systemId > 0);
            romDbList.add(m);
        }

        Map<Long, Long> validByPlatform = new HashMap<>();
        Map<Long, Long> totalByPlatform = new HashMap<>();
        for (Map<String, Object> raw : gameMapper.countDatStatsByPlatform()) {
            Map<String, Object> row = lowerKeys(raw);
            Long pid = toLong(row.get("platformid"));
            if (pid == null) continue;
            totalByPlatform.put(pid, toLong0(row.get("totalgames")));
            validByPlatform.put(pid, toLong0(row.get("validgames")));
        }
        List<Map<String, Object>> gameList = new ArrayList<>();
        for (Platform p : platformService.getAllPlatforms()) {
            if (p.getId() == null) continue;
            Integer sid = p.getSystemId();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("platformId", p.getId());
            m.put("platformName", (p.getName() != null && !p.getName().isBlank()) ? p.getName() : p.getSystem());
            m.put("systemId", sid == null ? 0 : sid);
            m.put("totalGames", totalByPlatform.getOrDefault(p.getId(), 0L));
            m.put("validGames", validByPlatform.getOrDefault(p.getId(), 0L));
            m.put("resolvable", sid != null && sid > 0 && RomDbPlatformMapping.getPlatformNameBySystemId(sid) != null);
            gameList.add(m);
        }

        result.put("success", true);
        result.put("romDb", romDbList);
        result.put("game", gameList);
        return result;
    }

    // ==================== 跑批 ====================

    @Override
    public String start(Map<String, Object> request) {
        if (generating.get()) {
            throw new IllegalStateException("已有 DAT 生成任务正在运行，请稍后再试");
        }
        if (romDbDataInitializer.isRunning()) {
            throw new IllegalStateException("rom_db 导入正在进行中，请等待其完成后再触发生成");
        }
        String source = str(request.get("source"));
        boolean autoImport = bool(request.get("autoImport"));
        List<String> platforms = toStrList(request.get("platforms"));
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source 必填（rom_db | game）");
        }
        if (platforms == null || platforms.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个平台");
        }

        // 归一化任务项：每项 {label, pathName}
        List<String[]> items = new ArrayList<>();
        String version = LocalDate.now().format(VERSION_FMT);
        if ("rom_db".equals(source)) {
            Set<String> used = new HashSet<>();
            for (String p : platforms) {
                items.add(new String[]{p, uniqueBase(used, sanitize(p))});
            }
        } else if ("game".equals(source)) {
            Map<Integer, String> sysToCanonical = new HashMap<>();
            for (Map.Entry<String, Integer> e : RomDbPlatformMapping.getAll().entrySet()) {
                int sid = e.getValue();
                if (sid > 0) sysToCanonical.putIfAbsent(sid, e.getKey());
            }
            Set<String> used = new HashSet<>();
            for (String pidStr : platforms) {
                Long pid = toLong(pidStr);
                if (pid == null) continue;
                Platform p = platformService.getPlatformById(pid);
                if (p == null) continue;
                String label = (p.getName() != null && !p.getName().isBlank()) ? p.getName() : p.getSystem();
                Integer sid = p.getSystemId();
                String pathName;
                if (sid != null && sid > 0 && sysToCanonical.containsKey(sid)) {
                    pathName = sysToCanonical.get(sid);
                } else {
                    pathName = label != null ? label : ("platform_" + pid);
                }
                items.add(new String[]{pidStr, uniqueBase(used, sanitize(pathName))});
            }
        } else {
            throw new IllegalArgumentException("不支持的来源：" + source);
        }

        if (items.isEmpty()) {
            throw new IllegalArgumentException("无有效平台可生成");
        }

        final String taskId = "dat_" + System.currentTimeMillis();
        final Map<String, Object> progress = new ConcurrentHashMap<>();
        final List<String> logs = java.util.Collections.synchronizedList(new ArrayList<>());
        final List<Map<String, Object>> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        progress.put("total", items.size());
        progress.put("completed", 0);
        progress.put("filesWritten", 0);
        progress.put("gamesWritten", 0L);
        progress.put("skipped", 0);
        progress.put("status", "processing");
        progress.put("logs", logs);
        progress.put("errors", errors);
        progressMap.put(taskId, progress);

        final AtomicBoolean stopFlag = new AtomicBoolean(false);
        stopFlags.put(taskId, stopFlag);
        generating.set(true);

        final String effSource = source;
        new Thread(() -> {
            try {
                runGeneration(effSource, items, version, progress, logs, errors, stopFlag);
            } finally {
                generating.set(false);
                if ("completed".equals(progress.get("status")) && autoImport) {
                    appendLog(logs, "✔ 生成完成，正在触发 rom_db 导入...");
                    runAutoImport(progress, logs);
                }
            }
        }, "dat-tool-" + taskId).start();

        return taskId;
    }

    private void runGeneration(String source, List<String[]> items, String version,
                               Map<String, Object> progress, List<String> logs,
                               List<Map<String, Object>> errors, AtomicBoolean stopFlag) {
        try {
            Files.createDirectories(Paths.get(DAT_DIR));
        } catch (IOException e) {
            progress.put("status", "error");
            appendLog(logs, "✘ 无法创建输出目录：" + e.getMessage());
            return;
        }
        int completed = 0;
        int filesWritten = 0;
        long gamesWritten = 0;
        int skipped = 0;
        for (String[] item : items) {
            if (stopFlag.get()) {
                progress.put("status", "stopped");
                appendLog(logs, "已停止。");
                break;
            }
            String label = item[0];
            String fileName = item[1] + ".dat";
            try {
                int g;
                if ("rom_db".equals(source)) {
                    g = writeRomDbDat(label, fileName, version);
                } else {
                    g = writeGameDat(toLong(label), fileName, version);
                }
                if (g == 0) {
                    skipped++;
                    appendLog(logs, "○ " + label + " → 无有效条目，已跳过");
                } else {
                    filesWritten++;
                    gamesWritten += g;
                    appendLog(logs, "✔ " + label + " → " + g + " 条 → " + fileName);
                }
            } catch (Exception e) {
                skipped++;
                appendLog(logs, "✘ " + label + " → 失败: " + e.getMessage());
                Map<String, Object> err = new HashMap<>();
                err.put("platform", label);
                err.put("error", e.getMessage());
                errors.add(err);
                logger.warn("DAT 生成失败: platform={}, err={}", label, e.getMessage());
            } finally {
                completed++;
                progress.put("completed", completed);
                progress.put("filesWritten", filesWritten);
                progress.put("gamesWritten", gamesWritten);
                progress.put("skipped", skipped);
            }
        }
        if (!"stopped".equals(progress.get("status"))) {
            progress.put("status", "completed");
        }
    }

    private void runAutoImport(Map<String, Object> progress, List<String> logs) {
        boolean started = romDbDataInitializer.importAllDat();
        if (!started) {
            appendLog(logs, "⚠ 导入未能启动（已有任务在运行）");
            return;
        }
        int guard = 0;
        while (romDbDataInitializer.isRunning() && guard++ < 3600) {
            appendLog(logs, "… 导入中 [" + romDbDataInitializer.getPhase() + "] "
                    + romDbDataInitializer.getCurrentFileIndex() + "/" + romDbDataInitializer.getTotalFiles()
                    + " · 合并 " + romDbDataInitializer.getCurrentMerged() + " 条");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        appendLog(logs, "✔ " + romDbDataInitializer.getLastResult());
    }

    // ==================== 写 DAT ====================

    private int writeRomDbDat(String platform, String fileName, String version) throws IOException {
        List<RomDb> rows = romDbMapper.findByPlatform(platform);
        Path out = Paths.get(DAT_DIR, fileName);
        int games = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            writeHeader(w, platform, version);
            for (RomDb r : rows) {
                String name = r.getGameName();
                String crc = r.getCrc32();
                String serial = r.getSerial();
                if (isBlank(name)) continue;
                if (isBlank(crc) && isBlank(serial)) continue;
                w.write("game (\n");
                w.write("\tname \"" + esc(name) + "\"\n");
                line(w, "region", r.getRegion());
                line(w, "developer", r.getDeveloper());
                line(w, "publisher", r.getPublisher());
                line(w, "releaseyear", r.getReleaseyear());
                line(w, "genre", r.getGenre());
                line(w, "serial", serial);
                String romName = !isBlank(r.getRomFile()) ? r.getRomFile().trim() : name.trim();
                writeRom(w, romName, crc, r.getMd5(), r.getSha1());
                w.write(")\n\n");
                games++;
            }
        }
        return games;
    }

    private int writeGameDat(Long platformId, String fileName, String version) throws IOException {
        List<Game> games = gameMapper.selectGamesByPlatformId(platformId);
        String displayName = fileName.endsWith(".dat") ? fileName.substring(0, fileName.length() - 4) : fileName;
        Path out = Paths.get(DAT_DIR, fileName);
        int written = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            writeHeader(w, displayName, version);
            for (Game g : games) {
                String name = g.getName();
                String crc = g.getCrc32();
                String serial = g.getDiscSerial();
                if (isBlank(name)) continue;
                if (isBlank(crc) && isBlank(serial)) continue;
                w.write("game (\n");
                w.write("\tname \"" + esc(name) + "\"\n");
                line(w, "developer", g.getDeveloper());
                line(w, "publisher", g.getPublisher());
                line(w, "releaseyear", extractYear(g.getReleasedate()));
                line(w, "genre", g.getGenre());
                line(w, "serial", serial);
                String romName = fileNameOfPath(g.getPath());
                if (isBlank(romName)) romName = name.trim();
                writeRom(w, romName, crc, g.getMd5(), null);
                w.write(")\n\n");
                written++;
            }
        }
        return written;
    }

    private void writeHeader(BufferedWriter w, String platform, String version) throws IOException {
        w.write("clrmamepro (\n");
        w.write("\tname \"" + esc(platform) + "\"\n");
        w.write("\tdescription \"" + esc(platform) + "\"\n");
        w.write("\tversion " + version + "\n");
        w.write(")\n\n");
    }

    private void writeRom(BufferedWriter w, String romName, String crc, String md5, String sha1) throws IOException {
        StringBuilder rom = new StringBuilder("\trom ( name ");
        rom.append(tokenRomName(romName));
        if (!isBlank(crc)) rom.append(" crc ").append(crc.trim().toLowerCase());
        if (!isBlank(md5)) rom.append(" md5 ").append(md5.trim().toLowerCase());
        if (!isBlank(sha1)) rom.append(" sha1 ").append(sha1.trim().toLowerCase());
        rom.append(" )\n");
        w.write(rom.toString());
    }

    // ==================== 格式化辅助（对齐 ClrMameProDatParser） ====================

    /** 属性行：非空才写，值加引号。 */
    private void line(BufferedWriter w, String key, String val) throws IOException {
        if (!isBlank(val)) w.write("\t" + key + " \"" + esc(val.trim()) + "\"\n");
    }

    /** 游戏名/平台名转义：双引号翻倍。 */
    private static String esc(String s) { return s.replace("\"", "\"\""); }

    /** rom 文件名 token：含空格/特殊字符时加引号，否则裸写（与解析器 ROM_ENTRY_ATTR 兼容）。 */
    private static String tokenRomName(String name) {
        String n = name.trim();
        if (n.matches("[\\w.+\\-]+")) {
            return n;
        }
        return "\"" + esc(n) + "\"";
    }

    /** path → 文件名（末段）。目录路径取末段名，可接受简化。 */
    private static String fileNameOfPath(String path) {
        if (path == null) return null;
        String p = path.replace('\\', '/');
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        int idx = p.lastIndexOf('/');
        return idx >= 0 ? p.substring(idx + 1) : p;
    }

    /** releasedate 前 4 位数字年。 */
    private static String extractYear(String releasedate) {
        if (releasedate == null) return null;
        Matcher m = YEAR_PATTERN.matcher(releasedate);
        return m.find() ? m.group(1) : null;
    }

    /** 文件名安全化：非法字符替换为下划线。 */
    private static String sanitize(String name) {
        if (name == null) return "platform";
        String s = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return s.isEmpty() ? "platform" : s;
    }

    /** NTFS 大小写撞车去重：同一小写基名第二次起追加 _2/_3。 */
    private static String uniqueBase(Set<String> used, String base) {
        String key = base.toLowerCase();
        if (used.add(key)) return base;
        int n = 2;
        String candidate;
        do {
            candidate = base + "_" + n;
            n++;
        } while (!used.add(candidate.toLowerCase()));
        return candidate;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    // ==================== 状态查询 ====================

    @Override
    public Map<String, Object> getProgress(String taskId) {
        Map<String, Object> progress = progressMap.get(taskId);
        Map<String, Object> result = new HashMap<>();
        if (progress == null) {
            result.put("success", false);
            result.put("message", "任务不存在");
            return result;
        }
        result.put("success", true);
        result.put("progress", progress);
        return result;
    }

    @Override
    public void stop(String taskId) {
        AtomicBoolean flag = stopFlags.get(taskId);
        if (flag != null) flag.set(true);
    }

    @Override
    public boolean isGenerating() {
        return generating.get();
    }

    private void appendLog(List<String> logs, String line) {
        logs.add(line);
        if (logs.size() > MAX_LOG_LINES) {
            logs.subList(0, logs.size() - MAX_LOG_LINES).clear();
        }
    }

    // ==================== 类型转换辅助 ====================

    private static String str(Object o) { return o == null ? null : o.toString(); }

    /** 将 resultType=map 的行（H2 返回大写列标签）归一为小写键，供跨大小写读取。 */
    private static Map<String, Object> lowerKeys(Map<String, Object> row) {
        Map<String, Object> m = new HashMap<>();
        if (row != null) {
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getKey() != null) m.put(e.getKey().toLowerCase(), e.getValue());
            }
        }
        return m;
    }

    private static boolean bool(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        return Boolean.parseBoolean(o.toString());
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    private static long toLong0(Object o) {
        Long v = toLong(o);
        return v == null ? 0L : v;
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStrList(Object o) {
        List<String> list = new ArrayList<>();
        if (o instanceof List) {
            for (Object item : (List<Object>) o) {
                if (item != null) list.add(item.toString());
            }
        }
        return list;
    }
}
