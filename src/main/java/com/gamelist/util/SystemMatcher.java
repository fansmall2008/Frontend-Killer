package com.gamelist.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.gamelist.model.ScraperSystem;

/**
 * 平台未绑定系统时的智能匹配器（TODO #9 核心）。
 * 纯静态函数实现，便于单元测试。
 *
 * 匹配链（按优先级，见 match 方法）：
 *   0. 方言映射表（term_mapping category=system_alias）归一化精确命中 → 确定性答案
 *   1. 别名池精确相等（name/nameEn/nameCn/nom* 归一化后全等）
 *   2. 包含 + 覆盖率加权（别名包含词或词包含别名）
 *   3. 词元重叠（解决 "nintendo fc" 类带前缀文件夹名；含 CJK 的词不做词元层）
 *   4. 编辑距离兜底（Levenshtein，阈值 max(2, 长度/4)）
 *   5. 扩展名强信号（平台目录扩展名 ∩ 系统 extensions；稀有扩展名加权更高）
 *
 * 多词融合：同一系统被多个输入词命中加 8 分（封顶 16），文件夹名词命中额外 +5。
 */
public final class SystemMatcher {

    /** 各匹配层分数常量 */
    public static final int SCORE_MAPPING = 100;
    /** 词元级映射命中（如 "nintendo fc" 中的 fc → NES），略低于整词命中 */
    public static final int SCORE_TOKEN_MAPPING = 90;
    public static final int SCORE_ALIAS_EXACT = 95;
    public static final int SCORE_CONTAINS_BASE = 50;
    public static final int SCORE_CONTAINS_RANGE = 40;
    public static final int SCORE_TOKEN_BASE = 40;
    public static final int SCORE_TOKEN_RANGE = 40;
    public static final int SCORE_DISTANCE_BASE = 40;
    public static final int SCORE_DISTANCE_STEP = 6;
    public static final int SCORE_EXT_RARE = 45;
    public static final int SCORE_EXT_COMMON = 10;
    public static final int FOLDER_TERM_BONUS = 5;
    public static final int MULTI_TERM_BONUS = 8;
    public static final int MAX_MULTI_TERM_BONUS = 16;
    /** 低于该总分不进候选列表 */
    public static final int MIN_CANDIDATE_SCORE = 40;
    /** 扩展名稀有度阈值：拥有该扩展名的系统数 ≤ 该值视为稀有（强信号） */
    public static final int EXT_RARE_COUNT = 2;

    /** 命中原因编码（前端映射 i18n 文案） */
    public static final String REASON_MAPPING = "mapping";
    public static final String REASON_ALIAS_EXACT = "alias_exact";
    public static final String REASON_CONTAINS = "contains";
    public static final String REASON_TOKEN = "token";
    public static final String REASON_DISTANCE = "distance";
    public static final String REASON_EXTENSION = "extension";

    /** 词元弱词过滤表（区域后缀、弱冠词等） */
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "the", "of", "and", "for", "usa", "us", "eur", "eu", "jpn", "jp", "jap",
            "world", "europe", "version", "rev", "hack", "hacks", "bonus"));

    /** 扩展名信号过滤：通用扩展名对系统识别无贡献 */
    public static final Set<String> GENERIC_EXTS = new HashSet<>(Arrays.asList(
            "zip", "7z", "rar", "iso", "bin", "cue", "chd", "img", "rom", "m3u",
            "txt", "nfo", "ini", "cfg", "xml", "json", "png", "jpg", "jpeg", "gif",
            "bmp", "webp", "db", "dat", "md5", "sfv", "log", "lnk", "url", "exe",
            "dll", "sav", "srm", "state", "mcr", "bak"));

    private SystemMatcher() {
    }

    /**
     * 归一化：去括号内容（区域/版本标签）→ NFKC（全角转半角）→ 小写 → 仅保留字母数字。
     * "SFC (USA)" → "sfc"，"Super Nintendo" → "supernintendo"，中文名原样保留。
     */
    public static String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String s = input.replaceAll("\\([^)]*\\)", " ").replaceAll("\\[[^\\]]*\\]", " ");
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);
        s = s.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 词元拆分：仅对不含 CJK 的词做拉丁分词，过滤弱词元。
     * 中文名（如"超级任天堂"）返回空列表，走包含/编辑距离层。
     */
    public static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        if (input == null || input.isEmpty()) {
            return tokens;
        }
        String lower = input.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') {
                return tokens;
            }
        }
        for (String part : lower.split("[^a-z0-9]+")) {
            if (part.length() < 2 || STOPWORDS.contains(part)) {
                continue;
            }
            tokens.add(part);
        }
        return tokens;
    }

    /**
     * Levenshtein 编辑距离，超过 maxDist 直接返回 maxDist+1（剪枝提速）。
     */
    public static int levenshtein(String a, String b, int maxDist) {
        int la = a.length();
        int lb = b.length();
        if (Math.abs(la - lb) > maxDist) {
            return maxDist + 1;
        }
        int[] prev = new int[lb + 1];
        int[] curr = new int[lb + 1];
        for (int j = 0; j <= lb; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= la; i++) {
            curr[0] = i;
            int rowMin = curr[0];
            for (int j = 1; j <= lb; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, curr[j]);
            }
            if (rowMin > maxDist) {
                return maxDist + 1;
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[lb];
    }

    /**
     * 解析系统 extensions 字段（逗号/空白分隔，去点、小写）。
     */
    public static Set<String> splitExtensions(String extensions) {
        Set<String> set = new LinkedHashSet<>();
        if (extensions == null || extensions.isEmpty()) {
            return set;
        }
        for (String part : extensions.split("[,;\\s]+")) {
            String e = part.trim().toLowerCase(Locale.ROOT);
            if (e.startsWith(".")) {
                e = e.substring(1);
            }
            if (!e.isEmpty()) {
                set.add(e);
            }
        }
        return set;
    }

    /** 匹配结果项（直接 JSON 序列化给前端） */
    public static class MatchResult {
        private Integer systemId;
        private String name;
        private String nameCn;
        private int score;
        private List<String> reasons = new ArrayList<>();

        public Integer getSystemId() {
            return systemId;
        }

        public String getName() {
            return name;
        }

        public String getNameCn() {
            return nameCn;
        }

        public int getScore() {
            return score;
        }

        public List<String> getReasons() {
            return reasons;
        }
    }

    /**
     * 核心匹配入口。
     *
     * @param rawTerms     原始输入词，按优先级排序（首个为平台文件夹名）
     * @param systems      全量 ScraperSystem 列表
     * @param platformExts 平台扩展名信号（已过滤通用扩展名；可为空集）
     * @param aliasMap     方言映射表（归一化 source_term → target systemId 字符串；可为 null/空）
     * @return 按分数降序、分数 ≥ {@link #MIN_CANDIDATE_SCORE} 的候选列表
     */
    public static List<MatchResult> match(List<String> rawTerms, List<ScraperSystem> systems,
                                          Set<String> platformExts, Map<String, String> aliasMap) {
        List<MatchResult> results = new ArrayList<>();
        if (systems == null || systems.isEmpty()) {
            return results;
        }

        // 1. 归一化输入词（去重、保序，首项为文件夹名）；同时保留原始串供词元层使用
        List<String> terms = new ArrayList<>();
        List<String> rawTermList = new ArrayList<>();
        if (rawTerms != null) {
            for (String raw : rawTerms) {
                String t = normalize(raw);
                if (!t.isEmpty() && !terms.contains(t)) {
                    terms.add(t);
                    rawTermList.add(raw);
                }
            }
        }
        if (terms.isEmpty()) {
            return results;
        }

        // 2. 预构建系统画像：别名（归一化）+ 词元 + 扩展名集合
        Map<Integer, ScraperSystem> systemById = new LinkedHashMap<>();
        Map<Integer, List<String>> aliasBySystem = new HashMap<>();
        Map<Integer, List<List<String>>> tokenBySystem = new HashMap<>();
        Map<Integer, Set<String>> extBySystem = new HashMap<>();
        Map<String, Integer> extCount = new HashMap<>();

        for (ScraperSystem s : systems) {
            if (s.getSystemId() == null) {
                continue;
            }
            systemById.put(s.getSystemId(), s);

            Set<String> aliasSet = new LinkedHashSet<>();
            for (String rawAlias : rawAliasesOf(s)) {
                String a = normalize(rawAlias);
                if (!a.isEmpty()) {
                    aliasSet.add(a);
                }
            }
            List<String> aliases = new ArrayList<>(aliasSet);
            aliasBySystem.put(s.getSystemId(), aliases);

            // 词元层必须用原始串拆分（归一化后分隔符已丢失，如 "Nintendo FC" → "nintendofc"）
            List<List<String>> tokenLists = new ArrayList<>();
            for (String rawAlias : rawAliasesOf(s)) {
                List<String> toks = tokenize(rawAlias);
                if (!toks.isEmpty()) {
                    tokenLists.add(toks);
                }
            }
            tokenBySystem.put(s.getSystemId(), tokenLists);

            Set<String> exts = splitExtensions(s.getExtensions());
            extBySystem.put(s.getSystemId(), exts);
            for (String e : exts) {
                extCount.merge(e, 1, Integer::sum);
            }
        }
        if (systemById.isEmpty()) {
            return results;
        }

        // 3. 逐词打分：每系统保留该词的最高分与原因，记录命中词下标（用于多词融合）
        Map<Integer, Integer> bestScore = new HashMap<>();
        Map<Integer, String> bestReason = new HashMap<>();
        Map<Integer, Set<Integer>> matchedTermIdx = new HashMap<>();

        for (int ti = 0; ti < terms.size(); ti++) {
            String term = terms.get(ti);
            // 词元用原始串拆分（保留分隔符）
            List<String> termTokens = tokenize(rawTermList.get(ti));

            // 3.0 方言映射表命中（确定性答案）：整词命中 100；词元命中 90（如 "nintendo fc" 中的 fc）
            if (aliasMap != null && !aliasMap.isEmpty()) {
                String mapped = aliasMap.get(term);
                boolean fullHit = mapped != null;
                if (!fullHit) {
                    for (String tok : termTokens) {
                        mapped = aliasMap.get(tok);
                        if (mapped != null) {
                            break;
                        }
                    }
                }
                if (mapped != null) {
                    Integer target = parseIntSafe(mapped);
                    if (target != null && systemById.containsKey(target)) {
                        int score = fullHit ? SCORE_MAPPING : SCORE_TOKEN_MAPPING;
                        record(target, score, REASON_MAPPING, ti,
                                bestScore, bestReason, matchedTermIdx);
                    }
                }
            }

            // 3.1-3.4 别名池各层
            for (Map.Entry<Integer, ScraperSystem> e : systemById.entrySet()) {
                Integer sysId = e.getKey();
                List<String> aliases = aliasBySystem.get(sysId);
                List<List<String>> tokenLists = tokenBySystem.get(sysId);

                int layerBest = 0;
                String layerReason = null;
                for (int ai = 0; ai < aliases.size(); ai++) {
                    String alias = aliases.get(ai);
                    if (alias.equals(term)) {
                        if (SCORE_ALIAS_EXACT > layerBest) {
                            layerBest = SCORE_ALIAS_EXACT;
                            layerReason = REASON_ALIAS_EXACT;
                        }
                        continue;
                    }
                    if (layerBest >= SCORE_ALIAS_EXACT) {
                        continue;
                    }
                    // 包含 + 覆盖率
                    if (alias.contains(term) || term.contains(alias)) {
                        int minL = Math.min(alias.length(), term.length());
                        int maxL = Math.max(alias.length(), term.length());
                        int score = SCORE_CONTAINS_BASE + (int) (SCORE_CONTAINS_RANGE * (double) minL / maxL);
                        if (score > layerBest) {
                            layerBest = score;
                            layerReason = REASON_CONTAINS;
                        }
                    }
                    // 词元重叠
                    if (layerBest < SCORE_CONTAINS_BASE) {
                        if (!termTokens.isEmpty()) {
                            for (List<String> aliasTokens : tokenLists) {
                                if (aliasTokens.isEmpty()) {
                                    continue;
                                }
                                int shared = 0;
                                for (String t : termTokens) {
                                    if (aliasTokens.contains(t)) {
                                        shared++;
                                    }
                                }
                                if (shared > 0) {
                                    double overlap = (double) shared / Math.min(termTokens.size(), aliasTokens.size());
                                    if (overlap >= 0.5) {
                                        int score = SCORE_TOKEN_BASE + (int) (SCORE_TOKEN_RANGE * overlap);
                                        if (score > layerBest) {
                                            layerBest = score;
                                            layerReason = REASON_TOKEN;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // 编辑距离兜底
                    if (layerBest == 0) {
                        int maxDist = Math.max(2, term.length() / 4);
                        int dist = levenshtein(alias, term, maxDist);
                        if (dist <= maxDist) {
                            int score = SCORE_DISTANCE_BASE - SCORE_DISTANCE_STEP * dist;
                            if (score > layerBest) {
                                layerBest = score;
                                layerReason = REASON_DISTANCE;
                            }
                        }
                    }
                }
                if (layerBest > 0) {
                    record(sysId, layerBest, layerReason, ti,
                            bestScore, bestReason, matchedTermIdx);
                }
            }
        }

        // 4. 扩展名信号（每个系统只计一次）
        if (platformExts != null && !platformExts.isEmpty()) {
            for (Map.Entry<Integer, Set<String>> e : extBySystem.entrySet()) {
                boolean hit = false;
                boolean rareHit = false;
                for (String pe : platformExts) {
                    if (e.getValue().contains(pe)) {
                        hit = true;
                        if (extCount.getOrDefault(pe, 999) <= EXT_RARE_COUNT) {
                            rareHit = true;
                        }
                    }
                }
                if (hit) {
                    int boost = rareHit ? SCORE_EXT_RARE : SCORE_EXT_COMMON;
                    bestScore.merge(e.getKey(), boost, Integer::sum);
                    bestReason.merge(e.getKey(), REASON_EXTENSION, (old, neu) -> old);
                    matchedTermIdx.computeIfAbsent(e.getKey(), k -> new HashSet<>());
                }
            }
        }

        // 5. 多词融合 + 汇总排序
        for (Map.Entry<Integer, Integer> e : bestScore.entrySet()) {
            Integer sysId = e.getKey();
            Set<Integer> idxs = matchedTermIdx.getOrDefault(sysId, new HashSet<>());
            int bonus = 0;
            if (idxs.contains(0)) {
                bonus += FOLDER_TERM_BONUS;
            }
            if (idxs.size() > 1) {
                bonus += Math.min(MAX_MULTI_TERM_BONUS, MULTI_TERM_BONUS * (idxs.size() - 1));
            }
            int total = e.getValue() + bonus;
            if (total < MIN_CANDIDATE_SCORE) {
                continue;
            }
            ScraperSystem sys = systemById.get(sysId);
            MatchResult r = new MatchResult();
            r.systemId = sysId;
            r.name = sys.getName();
            r.nameCn = sys.getNameCn();
            r.score = total;
            r.reasons.add(bestReason.get(sysId));
            results.add(r);
        }
        // 平局按 systemId 升序，保证结果确定性
        results.sort((a, b) -> b.score != a.score
                ? Integer.compare(b.score, a.score)
                : Integer.compare(a.systemId, b.systemId));
        return results;
    }

    /** 记录某系统在某个词上的最佳得分 */
    private static void record(Integer sysId, int score, String reason, int termIdx,
                               Map<Integer, Integer> bestScore, Map<Integer, String> bestReason,
                               Map<Integer, Set<Integer>> matchedTermIdx) {
        Integer old = bestScore.get(sysId);
        if (old == null || score > old) {
            bestScore.put(sysId, score);
            bestReason.put(sysId, reason);
        }
        matchedTermIdx.computeIfAbsent(sysId, k -> new HashSet<>()).add(termIdx);
    }

    /** 收集系统的全部别名原始串（含逗号分隔的 nomsCommun/nomRetropie） */
    private static List<String> rawAliasesOf(ScraperSystem s) {
        List<String> out = new ArrayList<>();
        addAlias(out, s.getName());
        addAlias(out, s.getNameEn());
        addAlias(out, s.getNameCn());
        addAlias(out, s.getNameFr());
        addAlias(out, s.getNameJp());
        addAlias(out, s.getNomEu());
        addAlias(out, s.getNomLaunchbox());
        addAlias(out, s.getNomHyperspin());
        addAlias(out, s.getNomRecalbox());
        addAlias(out, s.getNomRetropie());
        addAlias(out, s.getNomsCommun());
        return out;
    }

    private static void addAlias(List<String> out, String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
    }

    private static Integer parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
