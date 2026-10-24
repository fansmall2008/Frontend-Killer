package com.gamelist.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 翻译工具与导出统一使用的语种清单。
 * 保证写入 field_translation 的 lang 与读取（导出/展示）时的 lang 一致。
 * code 为 ISO 风格码，直接作为 {targetLang}/{sourceLang} 传给各 provider。
 */
public final class TranslationLanguages {

    private TranslationLanguages() {}

    // 顺序即下拉展示顺序；auto 仅作为源语种
    private static final String[][] LANGS = {
        {"auto", "自动检测"},
        {"en", "英语 (English)"},
        {"ja", "日语 (日本語)"},
        {"zh", "简体中文"},
        {"fr", "法语 (Français)"},
        {"es", "西班牙语 (Español)"},
        {"de", "德语 (Deutsch)"},
        {"it", "意大利语 (Italiano)"},
        {"ko", "韩语 (한국어)"},
        {"pt", "葡萄牙语 (Português)"},
        {"ru", "俄语 (Русский)"},
    };

    /** 全部语种（含 auto），供源语种选择器。 */
    public static List<Map<String, String>> all() {
        List<Map<String, String>> list = new ArrayList<>();
        for (String[] l : LANGS) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", l[0]);
            m.put("label", l[1]);
            list.add(m);
        }
        return list;
    }

    /** 目标语种（不含 auto）。 */
    public static List<Map<String, String>> targets() {
        List<Map<String, String>> list = new ArrayList<>();
        for (String[] l : LANGS) {
            if ("auto".equals(l[0])) continue;
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", l[0]);
            m.put("label", l[1]);
            list.add(m);
        }
        return list;
    }

    /** 判断是否为已知语种码。 */
    public static boolean isKnown(String code) {
        if (code == null) return false;
        for (String[] l : LANGS) {
            if (l[0].equals(code)) return true;
        }
        return false;
    }
}
