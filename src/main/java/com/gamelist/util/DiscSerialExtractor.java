package com.gamelist.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 光盘标识提取器：从文件名 / 路径中提取各平台的 serial 编号。
 * <p>
 * 命名规则参考：
 * - PS1/PS2: SLUS-12345, SLES-12345, SLPS-12345, SCUS-12345, SCES-12345 ...
 * - PSP: ULUS-12345, ULES-12345, ULJS-12345, UCUS-12345 ...
 * - Saturn: MK-81234, T-12345H, GS-9123 ...
 * - Mega-CD: MK-41234, T-12345F, 41234 ...
 * - PCE-CD: PCE-CD1234, NX-1234 ...
 * - Dreamcast: T12345M, MK-12345, 6107234 ...
 * - Wii: RGE... , RGEP..., D... (disc IDs, 6 chars)
 * - GameCube: G... (disc IDs, 4-6 chars)
 * - NGCD: NGCD-12345
 * - 3DO: 3DO-...
 */
public class DiscSerialExtractor {

    private DiscSerialExtractor() {}

    // ========== 各平台正则 ==========

    /** PS1 / PS2: 4字母 + 5数字 (SLUS-12345, SLES-12345, SCUS-12345 ...) */
    private static final Pattern PS_PATTERN = Pattern.compile(
            "\\b([A-Z]{4}[-]?\\d{5})\\b");

    /** PSP: 4字母 + 5数字 (ULUS-12345, ULES-12345 ...) — 与 PS 相同格式 */
    // 复用 PS_PATTERN，靠 systemId 区分

    /** Saturn: MK-数字, T-数字+字母, GS-数字 */
    private static final Pattern SATURN_PATTERN = Pattern.compile(
            "\\b((?:MK|T|GS|H)\\s*-?\\s*[A-Z0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE);

    /** Mega-CD: MK-数字, T-数字+字母, 纯数字 */
    private static final Pattern MEGACD_PATTERN = Pattern.compile(
            "\\b((?:MK|T)\\s*-?\\s*[A-Z0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE);

    /** PCE-CD: PCE-CD+数字, NX-数字 */
    private static final Pattern PCECD_PATTERN = Pattern.compile(
            "\\b((?:PCE[- ]?CD|NX)\\s*[-]?\\s*[A-Z0-9]{3,8})\\b", Pattern.CASE_INSENSITIVE);

    /** Dreamcast: T+数字+字母, MK-数字, 6-7位纯数字 */
    private static final Pattern DC_PATTERN = Pattern.compile(
            "\\b((?:T\\d{5}[A-Z0-9]|MK\\s*-?\\s*\\d{4,6}|\\d{6,7}))\\b", Pattern.CASE_INSENSITIVE);

    /** Wii / GC: 1字母 + 2-4字母 + 2字母 (RGE, D, G...) 共 4-6 字符 */
    private static final Pattern WII_GC_PATTERN = Pattern.compile(
            "\\b([A-Z][A-Z0-9]{3,5})\\b");

    /** NGCD: NGCD-数字 */
    private static final Pattern NGCD_PATTERN = Pattern.compile(
            "\\b(NGCD[-]?\\d{1,5})\\b", Pattern.CASE_INSENSITIVE);

    /** 3DO: 纯数字 (如 3DO-12345 或直接数字) */
    private static final Pattern THREE_DO_PATTERN = Pattern.compile(
            "\\b((?:3DO[-]?)?\\d{4,6})\\b");

    // ========== ScreenScraper systemId → 正则映射 ==========

    /** 已知支持光盘编号匹配的平台 systemId */
    private static final Map<Integer, Pattern[]> SYSTEM_PATTERNS = new HashMap<>();

    static {
        // SS systemId 参考: https://www.screenscraper.fr/systemes.php
        SYSTEM_PATTERNS.put(57,  new Pattern[]{ PS_PATTERN });          // PS1
        SYSTEM_PATTERNS.put(58,  new Pattern[]{ PS_PATTERN });          // PS2
        SYSTEM_PATTERNS.put(61,  new Pattern[]{ PSP_PATTERNS() });      // PSP
        SYSTEM_PATTERNS.put(22,  new Pattern[]{ SATURN_PATTERN });      // Saturn
        SYSTEM_PATTERNS.put(20,  new Pattern[]{ MEGACD_PATTERN });      // Mega-CD / Sega CD
        SYSTEM_PATTERNS.put(114, new Pattern[]{ PCECD_PATTERN });       // PC Engine CD-Rom
        SYSTEM_PATTERNS.put(23,  new Pattern[]{ DC_PATTERN });          // Dreamcast
        SYSTEM_PATTERNS.put(16,  new Pattern[]{ WII_GC_PATTERN });      // Wii
        SYSTEM_PATTERNS.put(13,  new Pattern[]{ WII_GC_PATTERN });      // GameCube
        SYSTEM_PATTERNS.put(70,  new Pattern[]{ NGCD_PATTERN });        // Neo Geo CD
        SYSTEM_PATTERNS.put(29,  new Pattern[]{ THREE_DO_PATTERN });    // 3DO
    }

    /** PSP 用与 PS 相同的正则 */
    private static Pattern PSP_PATTERNS() {
        return PS_PATTERN;
    }

    /**
     * 从文件名中提取 serial 编号。
     *
     * @param fileName 文件名（含或不含扩展名均可）
     * @param systemId ScreenScraper systemId，用于选择正则
     * @return 提取到的 serial（大写、去空格），未匹配返回 null
     */
    public static String extract(String fileName, int systemId) {
        if (fileName == null || fileName.isBlank()) return null;

        // 去掉扩展名
        String base = fileName;
        int dotIdx = base.lastIndexOf('.');
        if (dotIdx > 0) base = base.substring(0, dotIdx);

        Pattern[] patterns = SYSTEM_PATTERNS.get(systemId);
        if (patterns == null) {
            // 未知平台，尝试通用 PS 格式（最常见的 serial 格式）
            patterns = new Pattern[]{ PS_PATTERN };
        }

        // L0: 先用完整文件名匹配（保留方括号中的 serial，如 [SLPM-65843]）
        String upperFull = base.toUpperCase();
        for (Pattern p : patterns) {
            Matcher m = p.matcher(upperFull);
            if (m.find()) {
                return normalize(m.group(1));
            }
        }

        // L1: 去掉圆括号及内容 (如 "(Disc 1)", "（日版）") 后再匹配
        // 注意：只去圆括号 () / （），不去方括号 [] / 【】（serial 常存放在方括号中）
        String stripped = base.replaceAll("[\\(（].*?[\\)）]", " ");
        String upperStripped = stripped.toUpperCase();
        if (!upperStripped.equals(upperFull)) {
            for (Pattern p : patterns) {
                Matcher m = p.matcher(upperStripped);
                if (m.find()) {
                    return normalize(m.group(1));
                }
            }
        }

        return null;
    }

    /**
     * 尝试从文件名提取 serial，不限定平台（用于自动检测）。
     * 按优先级尝试：PS格式 > NGCD > DC > Saturn > PCE-CD > Wii/GC
     *
     * @param fileName 文件名
     * @return 提取到的 serial，未匹配返回 null
     */
    public static String extractAny(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;

        String base = fileName;
        int dotIdx = base.lastIndexOf('.');
        if (dotIdx > 0) base = base.substring(0, dotIdx);

        // 按特异性从高到低排列
        Pattern[] allPatterns = {
            NGCD_PATTERN, PS_PATTERN, DC_PATTERN, SATURN_PATTERN,
            PCECD_PATTERN, WII_GC_PATTERN
        };

        // L0: 先用完整文件名匹配（保留方括号中的 serial）
        String upperFull = base.toUpperCase();
        for (Pattern p : allPatterns) {
            Matcher m = p.matcher(upperFull);
            if (m.find()) {
                return normalize(m.group(1));
            }
        }

        // L1: 去掉圆括号后再匹配
        String stripped = base.replaceAll("[\\(（].*?[\\)）]", " ");
        String upperStripped = stripped.toUpperCase();
        if (!upperStripped.equals(upperFull)) {
            for (Pattern p : allPatterns) {
                Matcher m = p.matcher(upperStripped);
                if (m.find()) {
                    return normalize(m.group(1));
                }
            }
        }

        return null;
    }

    /** 归一化：去空格、去多余连字符 */
    private static String normalize(String raw) {
        return raw.replaceAll("\\s+", "").toUpperCase();
    }

    /**
     * 判断指定 systemId 是否支持光盘编号提取。
     */
    public static boolean isSupported(int systemId) {
        return SYSTEM_PATTERNS.containsKey(systemId);
    }
}
