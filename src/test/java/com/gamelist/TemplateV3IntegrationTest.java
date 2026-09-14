package com.gamelist;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamelist.model.Game;
import com.gamelist.model.ParsedDataFile;
import com.gamelist.model.Platform;
import com.gamelist.model.TemplateV3;
import com.gamelist.service.impl.TemplateV3ExportService;
import com.gamelist.service.impl.TemplateV3ImportService;
import com.gamelist.util.GenericTextParser;
import com.gamelist.util.GenericXmlParser;

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

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
