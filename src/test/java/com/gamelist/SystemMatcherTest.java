package com.gamelist;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamelist.model.ScraperSystem;
import com.gamelist.util.SystemMatcher;
import com.gamelist.util.SystemMatcher.MatchResult;

/**
 * SystemMatcher 智能匹配器单元测试（TODO #9 核心算法）。
 */
class SystemMatcherTest {

    // ==================== 归一化 / 词元 / 编辑距离 ====================

    @Test
    void testNormalize() {
        assertEquals("sfc", SystemMatcher.normalize("SFC (USA)"));
        assertEquals("sfc", SystemMatcher.normalize("SFC (En,Ja)"));
        assertEquals("supernintendo", SystemMatcher.normalize("Super Nintendo"));
        assertEquals("nintendofc", SystemMatcher.normalize("Nintendo FC"));
        assertEquals("sfc", SystemMatcher.normalize(" sfc "));
        assertEquals("任天堂", SystemMatcher.normalize("任天堂"));
        assertEquals("", SystemMatcher.normalize(null));
        assertEquals("", SystemMatcher.normalize(""));
    }

    @Test
    void testTokenize() {
        assertEquals(Arrays.asList("nintendo", "fc"), SystemMatcher.tokenize("Nintendo FC"));
        assertEquals(Arrays.asList("super", "nintendo"), SystemMatcher.tokenize("Super Nintendo (USA)"));
        // CJK 不做词元拆分
        assertTrue(SystemMatcher.tokenize("超级任天堂").isEmpty());
        // 弱词过滤
        assertEquals(Arrays.asList("nintendo"), SystemMatcher.tokenize("nintendo usa"));
    }

    @Test
    void testLevenshtein() {
        assertEquals(0, SystemMatcher.levenshtein("sfc", "sfc", 5));
        assertEquals(1, SystemMatcher.levenshtein("sfc", "sfx", 5));
        // 超过阈值剪枝
        assertTrue(SystemMatcher.levenshtein("sfc", "supernintendo", 2) > 2);
    }

    // ==================== 匹配链路 ====================

    private static ScraperSystem sys(int systemId, String name, String nameCn, String nomEu,
                                     String nomRecalbox, String extensions) {
        ScraperSystem s = new ScraperSystem();
        s.setSystemId(systemId);
        s.setName(name);
        s.setNameCn(nameCn);
        s.setNomEu(nomEu);
        s.setNomRecalbox(nomRecalbox);
        s.setExtensions(extensions);
        return s;
    }

    private static List<ScraperSystem> sampleSystems() {
        List<ScraperSystem> systems = new ArrayList<>();
        systems.add(sys(3, "Nintendo Entertainment System", "红白机", "NES", "nes", "nes,fds,nsf,zip"));
        systems.add(sys(4, "Super Nintendo Entertainment System", "超级任天堂", "Super Nintendo", "snes", "sfc,smc,zip"));
        systems.add(sys(1, "Sega Genesis", "世嘉MD", "Megadrive", "genesis,megadrive", "gen,md,smd,zip"));
        systems.add(sys(2, "Sega Master System", "世嘉MS", "Master System", "mastersystem", "sms,zip"));
        systems.add(sys(9, "Nintendo Game Boy", "GB", "Game Boy", "gb", "gb,zip"));
        return systems;
    }

    @Test
    void testMappingHit_sfc() {
        Map<String, String> aliasMap = new HashMap<>();
        aliasMap.put("sfc", "4");
        List<MatchResult> results = SystemMatcher.match(
                Collections.singletonList("sfc"), sampleSystems(), Collections.emptySet(), aliasMap);

        assertFalse(results.isEmpty());
        assertEquals(4, results.get(0).getSystemId());
        assertTrue(results.get(0).getReasons().contains(SystemMatcher.REASON_MAPPING));
        // 映射表命中确定性最高分
        assertTrue(results.get(0).getScore() >= SystemMatcher.SCORE_MAPPING);
    }

    @Test
    void testTokenMapping_nintendoFc() {
        // "nintendo fc" 中的词元 "fc" 命中映射表 → 确定性锁定 NES
        Map<String, String> aliasMap = new HashMap<>();
        aliasMap.put("fc", "3");
        List<MatchResult> results = SystemMatcher.match(
                Arrays.asList("Nintendo FC", "Nintendo FC"), sampleSystems(),
                Collections.emptySet(), aliasMap);

        assertFalse(results.isEmpty());
        assertEquals(3, results.get(0).getSystemId());
        assertTrue(results.get(0).getReasons().contains(SystemMatcher.REASON_MAPPING));
        assertTrue(results.get(0).getScore() >= SystemMatcher.SCORE_TOKEN_MAPPING);
    }

    @Test
    void testTokenMatch_superNintendoRoms() {
        // 纯词元层："super nintendo roms" 与 "Super Nintendo" 共享两个词元（无映射、无包含）
        List<MatchResult> results = SystemMatcher.match(
                Collections.singletonList("super nintendo roms"), sampleSystems(),
                Collections.emptySet(), Collections.emptyMap());

        assertFalse(results.isEmpty());
        assertEquals(4, results.get(0).getSystemId());
    }

    @Test
    void testContainsMatch_superNintendo() {
        List<MatchResult> results = SystemMatcher.match(
                Collections.singletonList("Super Nintendo"), sampleSystems(),
                Collections.emptySet(), Collections.emptyMap());

        assertFalse(results.isEmpty());
        assertEquals(4, results.get(0).getSystemId());
    }

    @Test
    void testExtensionUniqueHit() {
        // 目录里有 .sfc 文件（稀有扩展名）→ 即使无映射也应命中 SNES
        Set<String> exts = new HashSet<>(Collections.singletonList("sfc"));
        List<MatchResult> results = SystemMatcher.match(
                Collections.singletonList("my unknown dir"), sampleSystems(), exts, Collections.emptyMap());

        assertFalse(results.isEmpty());
        assertEquals(4, results.get(0).getSystemId());
        assertTrue(results.get(0).getReasons().contains(SystemMatcher.REASON_EXTENSION));
    }

    @Test
    void testCjkContainsMatch() {
        // 中文包含："任天堂" ⊂ "超级任天堂"
        List<MatchResult> results = SystemMatcher.match(
                Collections.singletonList("任天堂"), sampleSystems(),
                Collections.emptySet(), Collections.emptyMap());

        assertFalse(results.isEmpty());
        assertEquals(4, results.get(0).getSystemId());
    }

    @Test
    void testNoMatchBelowThreshold() {
        List<MatchResult> results = SystemMatcher.match(
                Collections.singletonList("zzzzqqqq"), sampleSystems(),
                Collections.emptySet(), Collections.emptyMap());

        assertTrue(results.isEmpty());
    }

    @Test
    void testEmptySystemsAndTerms() {
        assertTrue(SystemMatcher.match(null, sampleSystems(), Collections.emptySet(), null).isEmpty());
        assertTrue(SystemMatcher.match(Collections.singletonList("sfc"), null, Collections.emptySet(), null).isEmpty());
    }

    @Test
    void testMultiTermBonusOrdering() {
        // 三个词一致指向 SNES 时分数应高于单词命中
        List<MatchResult> multi = SystemMatcher.match(
                Arrays.asList("Super Nintendo", "SNES", "sfc"), sampleSystems(),
                Collections.emptySet(), Collections.emptyMap());
        List<MatchResult> single = SystemMatcher.match(
                Collections.singletonList("Super Nintendo"), sampleSystems(),
                Collections.emptySet(), Collections.emptyMap());

        assertFalse(multi.isEmpty());
        assertFalse(single.isEmpty());
        assertTrue(multi.get(0).getScore() > single.get(0).getScore());
    }
}
