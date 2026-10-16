package com.gamelist.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 检测并拆分游戏名的语言类型（英文/日文/中文/混合）。
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>{@link #detect(String)} — 检测纯语言类型（EN/JA/CN/MIXED_EN_CN）</li>
 *   <li>{@link #split(String)} — 拆分英日拼接名（向后兼容）</li>
 * </ul>
 * <p>
 * 典型场景：
 * <ul>
 *   <li>"Fantavisionファンタビジョン" → EN="Fantavision", JP="ファンタビジョン"</li>
 *   <li>"DOA2: HardcoreDOA2: HARD・CORE" → EN="DOA2: Hardcore", JP="DOA2: HARD・CORE"</li>
 *   <li>"Dark Cloudダーククラウド" → EN="Dark Cloud", JP="ダーククラウド"</li>
 *   <li>"三国志" → CHINESE</li>
 *   *   <li>"Tekken Tag Tournament" → ENGLISH</li>
 *   <li>"ファンタビジョン" → JAPANESE</li>
 * </ul>
 */
public final class NameSplitter {

    private NameSplitter() {}

    /**
     * 语言类型枚举。
     */
    public enum LanguageType {
        ENGLISH,     // 纯英文（ASCII）
        JAPANESE,    // 纯日文（含假名，可能含汉字）
        CHINESE,     // 中文（CJK 汉字，无日文假名）
        MIXED_EN_JP, // 英日拼接（需拆分）
        MIXED_EN_CN, // 英中混合（未拆分，整体存 game_name）
        UNKNOWN
    }

    /**
     * 拆分结果（英日拆分时使用）。
     */
    public static class SplitResult {
        private final String englishName;
        private final String japaneseName;

        public SplitResult(String englishName, String japaneseName) {
            this.englishName = englishName;
            this.japaneseName = japaneseName;
        }

        public String getEnglishName() { return englishName; }
        public String getJapaneseName() { return japaneseName; }
    }

    /**
     * 语言检测结果。
     */
    public static class DetectionResult {
        private final LanguageType type;
        private final String englishName;   // EN/MIXED_EN_JP 的英文部分
        private final String japaneseName;  // MIXED_EN_JP 的日文部分

        public DetectionResult(LanguageType type, String englishName, String japaneseName) {
            this.type = type;
            this.englishName = englishName;
            this.japaneseName = japaneseName;
        }

        public LanguageType getType() { return type; }
        public String getEnglishName() { return englishName; }
        public String getJapaneseName() { return japaneseName; }

        /** 便捷工厂：纯语言（无拆分） */
        public static DetectionResult of(LanguageType type) {
            return new DetectionResult(type, null, null);
        }

        /** 便捷工厂：英日拆分 */
        public static DetectionResult mixed(String en, String jp) {
            return new DetectionResult(LanguageType.MIXED_EN_JP, en, jp);
        }
    }

    /**
     * 检测游戏名的语言类型。
     * <p>
     * 判定规则：
     * <ul>
     *   <li>含平假名/片假名 + 含 ASCII → MIXED_EN_JP（尝试拆分）</li>
     *   <li>含平假名/片假名 + 无 ASCII → JAPANESE</li>
     *   <li>含 CJK 汉字 + 无假名 + 含 ASCII → MIXED_EN_CN</li>
     *   <li>含 CJK 汉字 + 无假名 + 无 ASCII → CHINESE</li>
     *   <li>仅 ASCII → ENGLISH</li>
     * </ul>
     */
    public static DetectionResult detect(String name) {
        if (name == null || name.isBlank()) return DetectionResult.of(LanguageType.UNKNOWN);

        boolean hasAscii = hasAsciiLetterOrDigit(name);
        boolean hasKana = hasJapaneseKana(name);       // 平假名 or 片假名
        boolean hasCJK = hasCJKUnifiedIdeograph(name);  // CJK 统一汉字
        boolean hasFullWidth = hasFullWidth(name);

        // 含日文假名 → 日文系
        if (hasKana) {
            if (hasAscii) {
                // 尝试拆分英日拼接
                SplitResult split = trySplitEnJp(name);
                if (split != null) {
                    return DetectionResult.mixed(split.getEnglishName(), split.getJapaneseName());
                }
            }
            return DetectionResult.of(LanguageType.JAPANESE);
        }

        // 无假名，含 CJK 汉字 → 中文系（日文汉字一定伴随假名，所以无假名的汉字判定为中文）
        if (hasCJK || hasFullWidth) {
            if (hasAscii) {
                return DetectionResult.of(LanguageType.MIXED_EN_CN);
            }
            return DetectionResult.of(LanguageType.CHINESE);
        }

        // 仅 ASCII
        if (hasAscii) {
            return DetectionResult.of(LanguageType.ENGLISH);
        }

        return DetectionResult.of(LanguageType.UNKNOWN);
    }

    /**
     * 尝试将拼接的游戏名拆分为英文名和日文名（向后兼容）。
     *
     * @param name 原始拼接名称
     * @return 拆分结果，若为纯语言名则返回 null
     */
    public static SplitResult split(String name) {
        if (name == null || name.length() < 4) return null;

        // 纯英文（无日文假名/汉字）→ 不拆分
        if (!hasAnyJapanese(name)) return null;
        // 纯日文（无 ASCII 字母/数字）→ 不拆分
        if (!hasAsciiLetterOrDigit(name)) return null;

        return trySplitEnJp(name);
    }

    // ======================== 内部方法 ========================

    /**
     * 尝试英日拼接拆分。
     */
    private static SplitResult trySplitEnJp(String name) {
        // 策略 1: [a-z0-9)]→[A-Z/日文] 过渡
        Pattern p1 = Pattern.compile("([a-z0-9\\)\\]])([A-Z\\u30A0-\\u30FF\\u3040-\\u309F\\u4E00-\\u9FFF])");
        Matcher m1 = p1.matcher(name);
        if (m1.find()) {
            int splitAt = m1.start() + 1;
            String en = name.substring(0, splitAt).trim();
            String jp = name.substring(splitAt).trim();
            if (isValidSplit(en, jp)) {
                return new SplitResult(en, jp);
            }
        }

        // 策略 2: ASCII → 非ASCII（片假名/平假名/汉字/全角）
        Pattern p2 = Pattern.compile("([A-Za-z0-9\\]\\)\\s])([\\u3040-\\u30FF\\u4E00-\\u9FFF\\uFF00-\\uFFEF])");
        Matcher m2 = p2.matcher(name);
        if (m2.find()) {
            int splitAt = m2.start() + 1;
            String en = name.substring(0, splitAt).trim();
            String jp = name.substring(splitAt).trim();
            if (isValidSplit(en, jp)) {
                return new SplitResult(en, jp);
            }
        }

        return null;
    }

    /**
     * 验证拆分是否合理（英文部分 >= 2 字符且含 ASCII，日文部分含日文字符）。
     */
    private static boolean isValidSplit(String en, String jp) {
        if (en.length() < 2 || jp.length() < 1) return false;
        if (!hasAsciiLetterOrDigit(en)) return false;
        if (!hasAnyJapanese(jp)) return false;
        return true;
    }

    /** 是否包含任意日文文字（平假名/片假名） */
    private static boolean hasJapaneseKana(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '\u3040' && c <= '\u30FF')) return true; // 平假名 + 片假名
        }
        return false;
    }

    /** 是否包含任意日文文字（平假名/片假名/汉字/全角） */
    private static boolean hasAnyJapanese(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '\u3040' && c <= '\u30FF') || (c >= '\u4E00' && c <= '\u9FFF')
                    || (c >= '\uFF00' && c <= '\uFFEF')) return true;
        }
        return false;
    }

    /** 是否包含 CJK 统一汉字 */
    private static boolean hasCJKUnifiedIdeograph(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u4E00' && c <= '\u9FFF') return true;
        }
        return false;
    }

    /** 是否包含全角字符 */
    private static boolean hasFullWidth(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\uFF00' && c <= '\uFFEF') return true;
        }
        return false;
    }

    /** 是否包含 ASCII 字母或数字 */
    private static boolean hasAsciiLetterOrDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) return true;
        }
        return false;
    }
}
