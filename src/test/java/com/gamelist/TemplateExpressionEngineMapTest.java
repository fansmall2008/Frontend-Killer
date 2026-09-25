package com.gamelist;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gamelist.util.TemplateExpressionEngine;

/**
 * TemplateExpressionEngine map() 方言映射函数单元测试（TODO #10）。
 * 方言缓存通过 TemplateExpressionEngine.setDialectMaps 注入，模拟 TermMappingService 的加载结果。
 */
class TemplateExpressionEngineMapTest {

    @BeforeEach
    void setUpMaps() {
        Map<String, Map<String, String>> maps = new LinkedHashMap<>();
        // genre 分类（用户自定义归类）
        Map<String, String> genre = new LinkedHashMap<>();
        genre.put("beatemup", "清版游戏");
        genre.put("act", "动作游戏");
        genre.put("action", "动作游戏");
        maps.put("genre", genre);
        // system_alias 分类（系统匹配内部用途，不参与默认查找）
        Map<String, String> alias = new LinkedHashMap<>();
        alias.put("sfc", "4");
        maps.put("system_alias", alias);
        TemplateExpressionEngine.setDialectMaps(maps);
    }

    @AfterEach
    void clearMaps() {
        TemplateExpressionEngine.setDialectMaps(new HashMap<>());
    }

    // ==================== 基础命中 / 未命中 ====================

    @Test
    void testMapHitDefaultLookup() {
        assertEquals("清版游戏", TemplateExpressionEngine.evaluate("map(\"Beat'em Up\")", null, null));
        assertEquals("动作游戏", TemplateExpressionEngine.evaluate("map('act')", null, null));
    }

    @Test
    void testMapNormalizedLookup() {
        // "beat em up" / "BEAT-EM-UP" 归一化后均为 beatemup，命中同一映射
        assertEquals("清版游戏", TemplateExpressionEngine.evaluate("map('beat em up')", null, null));
        assertEquals("清版游戏", TemplateExpressionEngine.evaluate("map('BEAT-EM-UP')", null, null));
    }

    @Test
    void testMapMissReturnsOriginal() {
        assertEquals("unknown-term", TemplateExpressionEngine.evaluate("map('unknown-term')", null, null));
    }

    @Test
    void testMapEmptyArgsReturnsNull() {
        assertNull(TemplateExpressionEngine.evaluate("map()", null, null));
    }

    @Test
    void testMapEmptyValueReturnsOriginal() {
        assertEquals("", TemplateExpressionEngine.evaluate("map('')", null, null));
    }

    // ==================== 指定分类 ====================

    @Test
    void testMapWithExplicitCategory() {
        assertEquals("动作游戏", TemplateExpressionEngine.evaluate("map('act', 'genre')", null, null));
        // 指定不存在的分类 → 原样返回
        assertEquals("act", TemplateExpressionEngine.evaluate("map('act', 'other')", null, null));
    }

    @Test
    void testMapExplicitSystemAliasCategory() {
        // 显式指定 system_alias 才能查系统别名（默认查找会跳过该分类）
        assertEquals("4", TemplateExpressionEngine.evaluate("map('sfc', 'system_alias')", null, null));
    }

    @Test
    void testMapSkipsSystemAliasByDefault() {
        // 默认查找排除 system_alias，避免字段值被误映射为系统 ID
        assertEquals("sfc", TemplateExpressionEngine.evaluate("map('sfc')", null, null));
    }

    // ==================== 多值拆分（逗号分隔逐段映射） ====================

    @Test
    void testMapMultiValueSplit() {
        // 逗号分隔多值：逐段映射后用 ", " 重连（模拟 genre 字段 "Beat'em Up, Action"）
        assertEquals("清版游戏, 动作游戏",
                TemplateExpressionEngine.evaluate("map(\"Beat'em Up, Action\")", null, null));
    }

    @Test
    void testMapMultiValueDedup() {
        // 多段命中同一目标时去重：act 与 Action 均映射为动作游戏
        assertEquals("动作游戏",
                TemplateExpressionEngine.evaluate("map('act, Action')", null, null));
    }

    @Test
    void testMapMultiValuePartialMiss() {
        // 未命中的段原样保留
        assertEquals("清版游戏, unknown-term",
                TemplateExpressionEngine.evaluate("map(\"Beat'em Up, unknown-term\")", null, null));
    }

    @Test
    void testMapMultiValueWhitespaceTrim() {
        // 段首尾空白被裁剪（", " 分隔）
        assertEquals("清版游戏, 动作游戏",
                TemplateExpressionEngine.evaluate("map(\" Beat'em Up ,  Action \")", null, null));
    }

    @Test
    void testMapMultiValueWithCategory() {
        // 多值拆分同样支持显式分类
        assertEquals("清版游戏, 动作游戏",
                TemplateExpressionEngine.evaluate("map(\"Beat'em Up, act\", 'genre')", null, null));
    }

    @Test
    void testMapSingleValueUnchanged() {
        // 无逗号的单值行为与之前完全一致（向后兼容）
        assertEquals("清版游戏", TemplateExpressionEngine.evaluate("map('Beat'em Up')", null, null));
    }

    // ==================== 变量引用与嵌套 ====================

    @Test
    void testMapVariableReference() {
        Map<String, String> vars = new HashMap<>();
        vars.put("genre", "Action");
        assertEquals("动作游戏", TemplateExpressionEngine.evaluate("map(genre)", null, vars));
    }

    @Test
    void testMapNestedExpression() {
        // map 参数支持表达式：map(upper('act'))
        assertEquals("动作游戏", TemplateExpressionEngine.evaluate("map(upper('act'))", null, null));
    }

    @Test
    void testMapResultInConcat() {
        // map 结果可参与拼接
        assertEquals("类型:清版游戏",
                TemplateExpressionEngine.evaluate("'类型:' + map('beat em up')", null, null));
    }

    // ==================== 多分类冲突（确定性） ====================

    @Test
    void testMapMultiCategoryLexicographic() {
        Map<String, Map<String, String>> maps = new LinkedHashMap<>();
        Map<String, String> alpha = new LinkedHashMap<>();
        alpha.put("x", "A类");
        Map<String, String> beta = new LinkedHashMap<>();
        beta.put("x", "B类");
        maps.put("alpha", alpha);
        maps.put("beta", beta);
        TemplateExpressionEngine.setDialectMaps(maps);
        // 同 key 命中多分类时按分类名字典序取第一个
        assertEquals("A类", TemplateExpressionEngine.evaluate("map('x')", null, null));
    }
}
