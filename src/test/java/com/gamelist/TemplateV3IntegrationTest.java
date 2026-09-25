package com.gamelist;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelist.model.Game;
import com.gamelist.model.ParsedDataFile;
import com.gamelist.model.Platform;
import com.gamelist.model.TemplateV3;
import com.gamelist.service.impl.TemplateV3ExportService;
import com.gamelist.service.impl.TemplateV3ImportService;
import com.gamelist.util.GenericTextParser;
import com.gamelist.util.GenericXmlParser;
import com.gamelist.util.TemplateExpressionEngine;

/**
 * v3 模板系统端到端集成测试。
 * 使用实际的 Pegasus (metadata.pegasus.txt) 和 ES (gamelist.xml) 数据文件验证。
 */
class TemplateV3IntegrationTest {

    // ==================== 解析器测试 ====================

    @Test
    void testGenericTextParser_pegasus() throws Exception {
        File dataFile = new File("c:\\Users\\fansm\\Downloads\\metadata.pegasus.txt");
        if (!dataFile.exists()) {
            System.out.println("SKIP: metadata.pegasus.txt not found");
            return;
        }

        TemplateV3 template = TemplateV3.loadFromFile(
                new File("d:\\code\\qoder\\webGamelistOper\\rules\\import\\pegasus-v3.json"));
        assertNotNull(template, "Pegasus v3 模板加载失败");

        ParsedDataFile parsed = GenericTextParser.parse(dataFile, template);

        // 验证系统字段
        System.out.println("=== Pegasus 系统字段 ===");
        for (Map.Entry<String, String> e : parsed.getSystemFields().entrySet()) {
            System.out.println("  " + e.getKey() + " = " + truncate(e.getValue(), 60));
        }
        assertTrue(parsed.getSystemFields().containsKey("collection"), "应有 collection 系统字段");
        assertEquals("3DO", parsed.getSystemField("collection"));

        // 验证游戏数量
        System.out.println("\n=== Pegasus 游戏数量: " + parsed.getGameCount() + " ===");
        assertTrue(parsed.getGameCount() >= 10, "应有至少10条游戏");

        // 验证第一个游戏
        Map<String, String> firstGame = parsed.getGames().get(0);
        System.out.println("\n=== 第一个游戏字段 ===");
        for (Map.Entry<String, String> e : firstGame.entrySet()) {
            System.out.println("  " + e.getKey() + " = " + truncate(e.getValue(), 60));
        }
        assertTrue(firstGame.containsKey("game"), "第一个游戏应有 game 字段");
    }

    @Test
    void testGenericXmlParser_esde() throws Exception {
        File dataFile = new File("c:\\Users\\fansm\\Downloads\\gamelist.xml");
        if (!dataFile.exists()) {
            System.out.println("SKIP: gamelist.xml not found");
            return;
        }

        TemplateV3 template = TemplateV3.loadFromFile(
                new File("d:\\code\\qoder\\webGamelistOper\\rules\\import\\esde-v3.json"));
        assertNotNull(template, "ES v3 模板加载失败");

        ParsedDataFile parsed = GenericXmlParser.parse(dataFile, template);

        // 验证系统字段
        System.out.println("=== ES 系统字段 ===");
        for (Map.Entry<String, String> e : parsed.getSystemFields().entrySet()) {
            System.out.println("  " + e.getKey() + " = " + truncate(e.getValue(), 60));
        }
        assertTrue(parsed.getSystemFields().containsKey("system"), "应有 system 系统字段");

        // 验证游戏数量
        System.out.println("\n=== ES 游戏数量: " + parsed.getGameCount() + " ===");
        assertTrue(parsed.getGameCount() >= 20, "应有至少20条游戏");

        // 验证第一个游戏
        Map<String, String> firstGame = parsed.getGames().get(0);
        System.out.println("\n=== 第一个游戏字段 ===");
        for (Map.Entry<String, String> e : firstGame.entrySet()) {
            System.out.println("  " + e.getKey() + " = " + truncate(e.getValue(), 60));
        }
        assertTrue(firstGame.containsKey("name"), "第一个游戏应有 name 字段");
    }

    // ==================== 导入服务测试 ====================

    @Test
    void testImportService_pegasus() throws Exception {
        File dataFile = new File("c:\\Users\\fansm\\Downloads\\metadata.pegasus.txt");
        if (!dataFile.exists()) {
            System.out.println("SKIP: metadata.pegasus.txt not found");
            return;
        }

        TemplateV3 template = TemplateV3.loadFromFile(
                new File("d:\\code\\qoder\\webGamelistOper\\rules\\import\\pegasus-v3.json"));
        assertNotNull(template);

        TemplateV3ImportService importService = new TemplateV3ImportService();
        List<Game> games = importService.importFile(dataFile, template);

        System.out.println("\n=== Pegasus 导入结果: " + games.size() + " 条游戏 ===");
        assertFalse(games.isEmpty(), "导入结果不应为空");

        for (int i = 0; i < Math.min(3, games.size()); i++) {
            Game g = games.get(i);
            System.out.println("  [" + i + "] name=" + g.getName()
                    + ", developer=" + g.getDeveloper()
                    + ", path=" + truncate(g.getPath(), 50));
        }

        // 至少第一个游戏应有名称
        assertNotNull(games.get(0).getName(), "第一个游戏应有名称");
    }

    @Test
    void testImportService_esde() throws Exception {
        File dataFile = new File("c:\\Users\\fansm\\Downloads\\gamelist.xml");
        if (!dataFile.exists()) {
            System.out.println("SKIP: gamelist.xml not found");
            return;
        }

        TemplateV3 template = TemplateV3.loadFromFile(
                new File("d:\\code\\qoder\\webGamelistOper\\rules\\import\\esde-v3.json"));
        assertNotNull(template);

        TemplateV3ImportService importService = new TemplateV3ImportService();
        List<Game> games = importService.importFile(dataFile, template);

        System.out.println("\n=== ES 导入结果: " + games.size() + " 条游戏 ===");
        assertFalse(games.isEmpty(), "导入结果不应为空");

        for (int i = 0; i < Math.min(3, games.size()); i++) {
            Game g = games.get(i);
            System.out.println("  [" + i + "] name=" + g.getName()
                    + ", developer=" + g.getDeveloper()
                    + ", path=" + truncate(g.getPath(), 50));
        }

        assertNotNull(games.get(0).getName(), "第一个游戏应有名称");
    }

    // ==================== 导出服务测试 ====================

    @Test
    void testExportService_pegasus() throws Exception {
        // 先导入再导出（round-trip）
        File dataFile = new File("c:\\Users\\fansm\\Downloads\\metadata.pegasus.txt");
        if (!dataFile.exists()) {
            System.out.println("SKIP: metadata.pegasus.txt not found");
            return;
        }

        TemplateV3 importTemplate = TemplateV3.loadFromFile(
                new File("d:\\code\\qoder\\webGamelistOper\\rules\\import\\pegasus-v3.json"));
        TemplateV3 exportTemplate = TemplateV3.loadFromFile(
                new File("d:\\code\\qoder\\webGamelistOper\\rules\\export\\pegasus-v3.json"));
        assertNotNull(importTemplate);
        assertNotNull(exportTemplate);

        // 导入
        TemplateV3ImportService importService = new TemplateV3ImportService();
        List<Game> games = importService.importFile(dataFile, importTemplate);

        // 构建 Platform
        Platform platform = new Platform();
        platform.setSystem("3DO");
        platform.setLaunch("retroarch -L cores/opera_libretro.dll");

        // 导出
        TemplateV3ExportService exportService = new TemplateV3ExportService();
        String output = exportService.generateContent(games, exportTemplate, platform, null);

        System.out.println("\n=== Pegasus 导出预览（前500字符） ===");
        System.out.println(output.substring(0, Math.min(500, output.length())));
        System.out.println("...");
        System.out.println("总长度: " + output.length() + " 字符");

        // 验证输出包含关键内容
        assertTrue(output.contains("collection: 3DO"), "应包含 collection 头");
        assertTrue(output.contains("game:"), "应包含 game: 标记");
    }

    @Test
    void testExportService_esde() throws Exception {
        File dataFile = new File("c:\\Users\\fansm\\Downloads\\gamelist.xml");
        if (!dataFile.exists()) {
            System.out.println("SKIP: gamelist.xml not found");
            return;
        }

        TemplateV3 importTemplate = TemplateV3.loadFromFile(
                new File("d:\\code\\qoder\\webGamelistOper\\rules\\import\\esde-v3.json"));
        TemplateV3 exportTemplate = TemplateV3.loadFromFile(
                new File("d:\\code\\qoder\\webGamelistOper\\rules\\export\\esde-v3.json"));
        assertNotNull(importTemplate);
        assertNotNull(exportTemplate);

        // 导入
        TemplateV3ImportService importService = new TemplateV3ImportService();
        List<Game> games = importService.importFile(dataFile, importTemplate);

        // 构建 Platform
        Platform platform = new Platform();
        platform.setSystem("model2");
        platform.setSoftware("ARRM");

        // 导出
        TemplateV3ExportService exportService = new TemplateV3ExportService();
        String output = exportService.generateContent(games, exportTemplate, platform, null);

        System.out.println("\n=== ES 导出预览（前500字符） ===");
        System.out.println(output.substring(0, Math.min(500, output.length())));
        System.out.println("...");
        System.out.println("总长度: " + output.length() + " 字符");

        // 验证输出包含关键内容
        assertTrue(output.contains("<?xml version=\"1.0\"?>"), "应包含 XML 声明");
        assertTrue(output.contains("<gameList>"), "应包含 gameList 标签");
        assertTrue(output.contains("<game>"), "应包含 game 标签");
        assertTrue(output.contains("<provider>"), "应包含 provider 标签");
    }

    // ==================== 模板变量机制测试 ====================

    /** 1. 含 variables 块的模板 JSON 反序列化 + 合法变量过滤 */
    @Test
    void testVariablesBlockDeserialization() throws Exception {
        String json = "{\"templateInfo\":{\"version\":3,\"direction\":\"export\"},"
                + "\"variables\":["
                + "{\"name\":\"cdnBase\",\"label\":\"CDN 前缀\",\"type\":\"text\",\"default\":\"https://cdn\",\"required\":true},"
                + "{\"name\":\"romSubdir\",\"type\":\"text\",\"default\":\"{platform.system}\"},"
                + "{\"name\":\"outputPath\"}"
                + "]}";
        TemplateV3 template = new ObjectMapper().readValue(json, TemplateV3.class);

        List<String> names = template.getDeclaredVariableNames();
        // outputPath 与内置变量重名，应被过滤
        assertEquals(2, names.size(), "与内置重名的变量应被过滤");
        assertTrue(names.contains("cdnBase"));
        assertTrue(names.contains("romSubdir"));
        assertFalse(names.contains("outputPath"));

        TemplateV3.TemplateVariable cdn = template.getValidVariables().get(0);
        assertEquals("CDN 前缀", cdn.getLabel());
        assertEquals("https://cdn", cdn.getDefaultValue());
        assertTrue(cdn.isRequired());
    }

    /** 2. 用户填写值优先，未填时回退到 default（buildEffectiveVariables） */
    @Test
    void testBuildEffectiveVariables() throws Exception {
        String json = "{\"templateInfo\":{\"version\":3},\"variables\":["
                + "{\"name\":\"a\",\"default\":\"defA\"},"
                + "{\"name\":\"b\",\"default\":\"defB\"}]}";
        TemplateV3 template = new ObjectMapper().readValue(json, TemplateV3.class);
        Map<String, String> user = new HashMap<>();
        user.put("a", "userA");
        Map<String, String> effective = template.buildEffectiveVariables(user);
        assertEquals("userA", effective.get("a"), "用户填写值优先");
        assertEquals("defB", effective.get("b"), "未填时回退 default");
    }

    /** 3. 用户设定的模板变量注入后，可在表达式中裸标识符拼接 URL / 字符串 */
    @Test
    void testVariableUsedInExpression() {
        Game game = new Game();
        game.setName("Mario");
        // 仅使用用户声明的模板变量（普通标识符），验证其流经表达式引擎的变量查表通道
        Map<String, String> vars = new HashMap<>();
        vars.put("cdnBase", "https://my.cdn");
        vars.put("romSubdir", "snes");

        String url = TemplateExpressionEngine.evaluate(
                "concat(cdnBase, '/', romSubdir, '/', 'logo', '.png')", game, vars);
        assertEquals("https://my.cdn/snes/logo.png", url, "模板变量应可用于 URL 拼接");
    }

    /** 3b. retroarch 示例的 path 表达式：填了 romDir 拼接，未填回退数据库 path */
    @Test
    void testRomDirPathVariableExpression() {
        Game game = new Game();
        game.setName("Mario Bros");                       // 显示名（与文件名不同，验证用的是显示名）
        game.setPath("D:\\old\\roms\\Mario [!].smc#CRC123"); // DB path 带 # 元数据与非法字符

        Platform platform = new Platform();
        platform.setName("Super Nintendo");

        String expr = "if(romDir, concat(romDir, '/', platform.name, '/', "
                + "sanitize(coalesce(name, translatedName), '&*/:\\<>?|'), ext(before(path, '#'))), "
                + "before(path, '#'))";

        // 未填 romDir（空串）→ 回退 before(path,'#')
        Map<String, String> empty = new HashMap<>();
        empty.put("romDir", "");
        String fallback = TemplateExpressionEngine.evaluate(expr,
                new TemplateExpressionEngine.Context(game, platform, empty));
        assertEquals("D:\\old\\roms\\Mario [!].smc", fallback, "未填时应回退数据库 path（去 #）");

        // 填了 romDir → 拼接 目录/平台名/显示名+扩展名
        Map<String, String> filled = new HashMap<>();
        filled.put("romDir", "E:\\Games");
        String result = TemplateExpressionEngine.evaluate(expr,
                new TemplateExpressionEngine.Context(game, platform, filled));
        assertEquals("E:\\Games/Super Nintendo/Mario Bros.smc", result, "填了应用用户目录拼接 path");
    }

    /** 4. 无 variables 的旧模板行为不变（getValidVariables 返回空） */
    @Test
    void testTemplateWithoutVariables() throws Exception {
        String json = "{\"templateInfo\":{\"version\":3,\"direction\":\"export\"}}";
        TemplateV3 template = new ObjectMapper().readValue(json, TemplateV3.class);
        assertNotNull(template);
        assertTrue(template.getValidVariables().isEmpty(), "无变量声明时列表应为空");
        assertTrue(template.buildEffectiveVariables(null).isEmpty());
    }

    /** 5. 示例模板 retroarch-folder-v3.json 可加载并正确解析变量 */
    @Test
    void testSampleTemplateLoads() {
        File f = new File("rules/export/retroarch-folder-v3.json");
        if (!f.exists()) {
            f = new File("d:\\\\code\\\\qoder\\\\webGamelistOper\\\\rules\\\\export\\\\retroarch-folder-v3.json");
        }
        if (!f.exists()) {
            System.out.println("SKIP: retroarch-folder-v3.json not found");
            return;
        }
        TemplateV3 template = TemplateV3.loadFromFile(f);
        assertNotNull(template, "示例模板应能加载");
        List<String> names = template.getDeclaredVariableNames();
        assertTrue(names.contains("romDir"), "示例模板应声明 romDir");
        assertEquals(1, names.size(), "示例模板仅声明一个变量");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
