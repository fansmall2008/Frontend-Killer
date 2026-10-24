package com.gamelist.service.impl;

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

import com.gamelist.mapper.FieldTranslationMapper;
import com.gamelist.mapper.GameMapper;
import com.gamelist.mapper.RomDbMapper;
import com.gamelist.model.FieldTranslation;
import com.gamelist.model.Game;
import com.gamelist.model.RomDb;
import com.gamelist.service.TranslationToolService;
import com.gamelist.service.TranslationService;

/**
 * 通用翻译工具实现：按范围取出待译文本，去重后逐条调用所选引擎，结果写入 field_translation。
 */
@Service
public class TranslationToolServiceImpl implements TranslationToolService {

    private static final Logger logger = LoggerFactory.getLogger(TranslationToolServiceImpl.class);

    /** 尾部括号/书名号等注释性后缀（如 "(USA) (Disc 1)"、"（限定版）"），rom_db 名称仅翻核心、后缀原样保留。 */
    private static final Pattern TRAILING_ANNOT =
            Pattern.compile("(?:\\s*[\\(\\[（【<].*[\\)\\]）】>])+\\s*$");

    private static final int MAX_LOG_LINES = 500;

    @Autowired
    private FieldTranslationMapper fieldTranslationMapper;

    @Autowired
    private GameMapper gameMapper;

    @Autowired
    private RomDbMapper romDbMapper;

    private final Map<String, Map<String, Object>> progressMap = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

    @Override
    public String start(Map<String, Object> request) {
        String srcTable = str(request.get("srcTable"));
        String srcField = str(request.get("srcField"));
        String sourceLang = str(request.get("sourceLang"));
        String targetLang = str(request.get("targetLang"));
        String serviceId = str(request.get("serviceId"));
        boolean overwrite = bool(request.get("overwriteExisting"));
        Long platformId = toLong(request.get("platformId"));
        List<Long> gameIds = toLongList(request.get("gameIds"));
        List<String> platforms = toStrList(request.get("platforms"));
        boolean all = bool(request.get("all"));

        if (srcTable == null || srcField == null || targetLang == null || targetLang.isBlank()) {
            throw new IllegalArgumentException("参数不完整：srcTable/srcField/targetLang 必填");
        }
        final TranslationService ts = TranslationServiceManager.getInstance().getTranslationService(serviceId);
        if (ts == null) {
            throw new IllegalArgumentException("所选翻译引擎不可用：" + serviceId);
        }

        // 1) 收集 (id, 原文) 行
        List<Object[]> rows = new ArrayList<>(); // {Long id, String text}
        if ("game".equals(srcTable)) {
            List<Game> games = collectGames(platformId, gameIds, all);
            for (Game g : games) {
                if (g.getId() == null) continue;
                String text = "desc".equals(srcField) ? g.getDesc() : g.getName();
                rows.add(new Object[]{g.getId(), text});
            }
        } else if ("rom_db".equals(srcTable)) {
            List<RomDb> roms = collectRomDb(platforms, all);
            for (RomDb r : roms) {
                if (r.getId() == null) continue;
                rows.add(new Object[]{r.getId(), r.getGameName()});
            }
        } else {
            throw new IllegalArgumentException("不支持的来源表：" + srcTable);
        }

        // 2) 跳过已翻（overwrite=false）
        Set<Long> already = new HashSet<>();
        if (!overwrite && !rows.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (Object[] r : rows) ids.add((Long) r[0]);
            already.addAll(fieldTranslationMapper.selectTranslatedIds(srcTable, srcField, targetLang, ids));
        }

        // 3) 按核心文本去重分组：key -> list of (id, suffix)
        boolean useCore = "rom_db".equals(srcTable) && "game_name".equals(srcField);
        Map<String, List<Object[]>> groups = new LinkedHashMap<>();
        for (Object[] r : rows) {
            Long id = (Long) r[0];
            String text = (String) r[1];
            if (already.contains(id)) continue;
            if (text == null || text.isBlank()) continue;
            String core;
            String suffix;
            if (useCore) {
                Matcher m = TRAILING_ANNOT.matcher(text);
                if (m.find() && m.start() > 0) {
                    core = text.substring(0, m.start()).trim();
                    suffix = text.substring(m.start());
                } else {
                    core = text.trim();
                    suffix = "";
                }
            } else {
                core = text.trim();
                suffix = "";
            }
            if (core.isEmpty()) continue;
            groups.computeIfAbsent(core, k -> new ArrayList<>()).add(new Object[]{id, suffix});
        }

        final String taskId = "tt_" + System.currentTimeMillis();
        final Map<String, Object> progress = new ConcurrentHashMap<>();
        final List<String> logs = java.util.Collections.synchronizedList(new ArrayList<>());
        final List<Map<String, Object>> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        progress.put("total", groups.size());
        progress.put("completed", 0);
        progress.put("success", 0);
        progress.put("failed", 0);
        progress.put("status", groups.isEmpty() ? "completed" : "processing");
        progress.put("logs", logs);
        progress.put("errors", errors);
        progressMap.put(taskId, progress);

        final AtomicBoolean stopFlag = new AtomicBoolean(false);
        stopFlags.put(taskId, stopFlag);

        final String effSourceLang = (sourceLang == null || sourceLang.isBlank()) ? "auto" : sourceLang;
        new Thread(() -> {
            int completed = 0, success = 0, failed = 0;
            for (Map.Entry<String, List<Object[]>> e : groups.entrySet()) {
                if (stopFlag.get()) {
                    progress.put("status", "stopped");
                    appendLog(logs, "已停止。");
                    break;
                }
                String core = e.getKey();
                try {
                    String translated = ts.translate(core, effSourceLang, targetLang);
                    if (translated == null || translated.isBlank()) {
                        throw new RuntimeException("引擎返回空结果");
                    }
                    for (Object[] idSuffix : e.getValue()) {
                        Long id = (Long) idSuffix[0];
                        String suffix = (String) idSuffix[1];
                        String value = suffix.isEmpty() ? translated.trim() : translated.trim() + suffix;
                        fieldTranslationMapper.upsert(new FieldTranslation(srcTable, id, srcField, targetLang, value));
                    }
                    success++;
                    appendLog(logs, "✔ " + core + "  →  " + translated.trim());
                } catch (Exception ex) {
                    failed++;
                    appendLog(logs, "✘ " + core + "  →  失败: " + ex.getMessage());
                    Map<String, Object> err = new HashMap<>();
                    err.put("text", core);
                    err.put("count", e.getValue().size());
                    err.put("error", ex.getMessage());
                    errors.add(err);
                    logger.warn("翻译失败: text={}, err={}", core, ex.getMessage());
                } finally {
                    completed++;
                    progress.put("completed", completed);
                    progress.put("success", success);
                    progress.put("failed", failed);
                }
            }
            if (!"stopped".equals(progress.get("status"))) {
                progress.put("status", "completed");
            }
        }, "translation-tool-" + taskId).start();

        return taskId;
    }

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

    // --- 取数辅助 ---

    private List<Game> collectGames(Long platformId, List<Long> gameIds, boolean all) {
        List<Game> games = new ArrayList<>();
        if (platformId != null) {
            games.addAll(gameMapper.selectGamesByPlatformId(platformId));
        } else if (gameIds != null && !gameIds.isEmpty()) {
            for (Long id : gameIds) {
                Game g = gameMapper.selectGameById(id);
                if (g != null) games.add(g);
            }
        } else if (all) {
            games.addAll(gameMapper.selectAllGames());
        }
        return games;
    }

    private List<RomDb> collectRomDb(List<String> platforms, boolean all) {
        List<RomDb> roms = new ArrayList<>();
        if (platforms != null && !platforms.isEmpty()) {
            for (String p : platforms) {
                roms.addAll(romDbMapper.findByPlatform(p));
            }
        } else if (all) {
            roms.addAll(romDbMapper.findAll());
        }
        return roms;
    }

    private void appendLog(List<String> logs, String line) {
        logs.add(line);
        if (logs.size() > MAX_LOG_LINES) {
            logs.subList(0, logs.size() - MAX_LOG_LINES).clear();
        }
    }

    // --- 类型转换辅助 ---

    private static String str(Object o) { return o == null ? null : o.toString(); }

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

    @SuppressWarnings("unchecked")
    private static List<Long> toLongList(Object o) {
        List<Long> list = new ArrayList<>();
        if (o instanceof List) {
            for (Object item : (List<Object>) o) {
                Long v = toLong(item);
                if (v != null) list.add(v);
            }
        }
        return list;
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
