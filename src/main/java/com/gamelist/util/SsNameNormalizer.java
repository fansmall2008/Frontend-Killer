package com.gamelist.util;

import java.util.regex.Pattern;

/**
 * ScreenScraper 名称匹配专用的归一化工具。
 *
 * <p>用途：当 {@code jeuInfos.php} 用 CRC/romnom 未命中时，回退到 {@code jeuRecherche.php}
 * 拉候选列表；本类负责把"查询名"和"每个候选名"归一化后做**字符串相等**比较，
 * 只有完全相等才视为同一个游戏，避免 Contra 被认成 Super Contra。</p>
 *
 * <p>归一化规则（{@link #normalize(String)}）：
 * <ol>
 *   <li>剥掉 No-Intro/Redump 风格括号 &amp; 方括号标签：{@code (USA)}、{@code [!] }、{@code (Rev 1.0)}、
 *       {@code (En,Fr,De)}、{@code Disc 1}、{@code Track N} 等；</li>
 *   <li>转小写；</li>
 *   <li>非字母数字（含中日韩与假名以外的一切符号、连字符、下划线、点、空格）替换为空格；</li>
 *   <li>折叠连续空格、去首尾空白。</li>
 * </ol>
 * 这样 {@code "Contra - Hardcore"} 与 {@code "Contra Hardcore"} 都归一化成 {@code "contra hardcore"}，
 * 而 {@code "Super Contra"} 归一化后是 {@code "super contra"}，永远不会与前两者相等。</p>
 *
 * <p>清洗规则（{@link #stripDumpTags(String)}）用于在首次 jeuInfos 请求前把 romnom 里的
 * 标签剥掉，提高严格匹配的一次命中率；跟 {@link #normalize(String)} 的区别是保留大小写与
 * 原始字符，只剥标签。</p>
 */
public final class SsNameNormalizer {

    private SsNameNormalizer() {}

    /** 括号 / 方括号 dump 标签：{@code (XX)}、{@code [XX]}、{@code {XX}}。 */
    private static final Pattern PAREN_TAG = Pattern.compile("[\\(\\[\\{][^\\)\\]\\}]*[\\)\\]\\}]");

    /** 常见光盘/多碟/版本尾缀：Disc 1 / Disk 2 / Track 03 / Rev A / v1.0 / (Rev-1) 等。 */
    private static final Pattern TRAILING_DUMP_TAIL = Pattern.compile(
            "(?i)\\b(disc|disk|track|rev|version|ver)[\\s\\.\\-]?[a-z0-9]+\\b");

    /** 归一化：只保留字母数字与空格。 */
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");

    /** 空白折叠。 */
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    /**
     * 剥掉常见的 No-Intro/Redump dump 标签，返回"接近原始游戏标题"的字符串。
     * 大小写与内部空格保留原样，只做剔除。
     *
     * <p>例：{@code "Contra (USA) [!] Disc 1"} → {@code "Contra"}</p>
     */
    public static String stripDumpTags(String name) {
        if (name == null) return null;
        String s = name.trim();
        if (s.isEmpty()) return s;
        s = PAREN_TAG.matcher(s).replaceAll(" ");
        s = TRAILING_DUMP_TAIL.matcher(s).replaceAll(" ");
        s = MULTI_SPACE.matcher(s).replaceAll(" ").trim();
        return s;
    }

    /**
     * 完全归一化：剥标签 + 小写 + 非字母数字替换为空格 + 折叠空格。
     * 用于把两个来自不同源（rom_db / SS 候选）的名字放到同一坐标系做 equals 比较。
     *
     * <p>例：{@code "Contra - Hardcore"} → {@code "contra hardcore"}；
     * {@code "Super Contra"} → {@code "super contra"}。</p>
     */
    public static String normalize(String name) {
        if (name == null) return "";
        String s = stripDumpTags(name);
        s = s.toLowerCase();
        s = NON_ALNUM.matcher(s).replaceAll(" ");
        s = MULTI_SPACE.matcher(s).replaceAll(" ").trim();
        return s;
    }

    /** 判断两个名字归一化后是否相等（任一为空返回 false）。 */
    public static boolean normalizedEquals(String a, String b) {
        if (a == null || b == null) return false;
        String na = normalize(a);
        String nb = normalize(b);
        return !na.isEmpty() && na.equals(nb);
    }

    /**
     * 从"文件路径/文件名"里提取核心游戏名（无扩展名 + 剥标签）。
     * 与 {@link #stripDumpTags(String)} 的区别是先去扩展名。
     */
    public static String fileNameToCoreName(String fileName) {
        if (fileName == null) return null;
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String base = slash >= 0 ? fileName.substring(slash + 1) : fileName;
        int dot = base.lastIndexOf('.');
        // 只在"点不是首字符"且"点在最后 8 个字符内"时视为扩展名，避免把 "Sonic 2.5" 里的点错当扩展名
        if (dot > 0 && dot >= base.length() - 8) {
            base = base.substring(0, dot);
        }
        return stripDumpTags(base);
    }
}
