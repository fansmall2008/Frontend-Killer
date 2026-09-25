package com.gamelist;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.model.TemplateV3;
import com.gamelist.service.impl.TemplateV3ExportService;
import com.gamelist.service.impl.TemplateV3ImportService;
import com.gamelist.util.TemplateExpressionEngine;

/**
 * RetroArch .lpl 导入导出回环测试。
 * <p>
 * 样本覆盖两种真实形态：
 * <ul>
 *   <li>MD 风格：path 为 zip#entry，crc32 为 DETECT</li>
 *   <li>FBNeo 风格：path 为纯 zip，crc32 为 {@code XXXXXXXX|crc}，label 含 / 等非法字符</li>
 * </ul>
 * 验证链路：导入（剥离 #、crc 校验、系统字段提升）→ 导出（6 字段、JSON 1.5 顶层、
 * DETECT 回填）→ 再导入（数据稳定）。
 */
class RetroArchV3IntegrationTest {

    private static final String SAMPLE_LPL = """
{
  "version": "1.5",
  "default_core_path": "",
  "default_core_name": "",
  "label_display_mode": 0,
  "right_thumbnail_mode": 0,
  "left_thumbnail_mode": 0,
  "sort_mode": 0,
  "items": [
    {
      "path": "G:\\\\emu\\\\out\\\\MD\\\\MD ACT\\\\Sonic The Hedgehog 2 (World).zip#Sonic The Hedgehog 2 (World).md",
      "label": "Sonic The Hedgehog 2 (World)",
      "core_path": "DETECT",
      "core_name": "DETECT",
      "crc32": "DETECT",
      "db_name": "MD - Mega Drive.lpl"
    },
    {
      "path": "G:\\\\emu\\\\out\\\\FBNEO\\\\FBNEO ACT\\\\warriorb.zip",
      "label": "Warriors of Fate (World 921002)",
      "core_path": "DETECT",
      "core_name": "DETECT",
      "crc32": "12345678|crc",
      "db_name": "MD - Mega Drive.lpl"
    },
    {
      "path": "G:\\\\emu\\\\out\\\\FBNEO\\\\FBNEO ACT\\\\sfa3.zip",
      "label": "Street Fighter Alpha 3 (Euro 1998/06/29)",
      "core_path": "DETECT",
      "core_name": "DETECT",
      "crc32": "",
      "db_name": "MD - Mega Drive.lpl"
    }
  ]
}
""";

    private final ObjectMapper mapper = new ObjectMapper();

    // ==================== 表达式引擎回归 ====================

    @Test
    void testExpressionEngine_ifMatchesAndSanitize() {
        // if(condition, ...) 必须把 "false" 当假（matches 的返回值约定）
        String r1 = TemplateExpressionEngine.evaluate(
                "if(matches('12345678|crc', '^[0-9A-Fa-f]{8}\\|crc$'), 'hit', 'miss')", (Game) null, null);
        assertEquals("hit", r1, "合法 crc 应命中");

        String r2 = TemplateExpressionEngine.evaluate(
                "if(matches('DETECT', '^[0-9A-Fa-f]{8}\\|crc$'), 'hit', 'miss')", (Game) null, null);
        assertEquals("miss", r2, "DETECT 不应命中（matches 返回 \"false\" 必须为假）");

        // sanitize + concat：导出缩略图 target 的表达式（label 中的 / 替换为 _）
        Game g = new Game();
        g.setName("Street Fighter Alpha 3 (Euro 1998/06/29)");
        TemplateExpressionEngine.Context ctx = new TemplateExpressionEngine.Context(g, null, new HashMap<>());
        String target = TemplateExpressionEngine.evaluate(
                "concat('Named_Boxarts/', sanitize(name, '&*/:\\<>?|'), '.png')", ctx);
        assertEquals("Named_Boxarts/Street Fighter Alpha 3 (Euro 1998_06_29).png", target);
    }

    // ==================== 导入 ====================

    @Test
    void testRetroArchImport() throws Exception {
        TemplateV3 importTemplate = TemplateV3.loadFromFile(
                new File("data/rules/import/retroarch-v3.json"));
        assertNotNull(importTemplate, "导入模板加载失败");

        Path tmpDir = Files.createTempDirectory("ra-lpl-test");
        File lplFile = tmpDir.resolve("MD - Mega Drive.lpl").toFile();
        Files.writeString(lplFile.toPath(), SAMPLE_LPL, StandardCharsets.UTF_8);

        TemplateV3ImportService importService = new TemplateV3ImportService();
        importService.setEnableMediaDiscovery(true);
        TemplateV3ImportService.TemplateV3ImportResult result =
                importService.importFileWithHeader(lplFile, importTemplate);
        List<Game> games = result.getGames();

        assertEquals(3, games.size(), "应导入 3 条游戏");

        // 条目1：zip#entry → 剥离 # 存纯 zip；DETECT crc → 空
        Game g0 = games.get(0);
        assertEquals("Sonic The Hedgehog 2 (World)", g0.getName());
        assertEquals("G:\\emu\\out\\MD\\MD ACT\\Sonic The Hedgehog 2 (World).zip", g0.getPath(),
                "path 应剥离 # 后缀");
        assertTrue(g0.getCrc32() == null || g0.getCrc32().isEmpty(), "DETECT crc 应导入为空");

        // 条目2：纯 zip 原样保留；|crc 校验后剥离为 8 位 hex
        Game g1 = games.get(1);
        assertEquals("Warriors of Fate (World 921002)", g1.getName());
        assertEquals("G:\\emu\\out\\FBNEO\\FBNEO ACT\\warriorb.zip", g1.getPath());
        assertEquals("12345678", g1.getCrc32(), "crc32 应为剥离 |crc 后的 8 位 hex");

        // 条目3：空 crc → 空
        Game g2 = games.get(2);
        assertTrue(g2.getCrc32() == null || g2.getCrc32().isEmpty());

        // 条目间一致的 db_name 自动提升为系统字段；platform.name = stem(db_name)
        assertEquals("MD - Mega Drive.lpl", result.getSystemFields().get("db_name"));
        Platform platform = new Platform();
        importService.applySystemFieldsToPlatform(platform, result.getSystemFields(), importTemplate);
        assertEquals("MD - Mega Drive", platform.getName(), "platform.name 应为 stem(db_name)");
        assertEquals("MD - Mega Drive.lpl", platform.getDatabase(), "platform.database 应为 db_name 原文");
    }

    // ==================== 导出 + 再导入回环 ====================

    @Test
    void testRetroArchExportRoundTrip() throws Exception {
        TemplateV3 importTemplate = TemplateV3.loadFromFile(
                new File("data/rules/import/retroarch-v3.json"));
        TemplateV3 exportTemplate = TemplateV3.loadFromFile(
                new File("data/rules/export/retroarch-v3.json"));
        assertNotNull(importTemplate);
        assertNotNull(exportTemplate);

        Path tmpDir = Files.createTempDirectory("ra-lpl-test");
        File lplFile = tmpDir.resolve("MD - Mega Drive.lpl").toFile();
        Files.writeString(lplFile.toPath(), SAMPLE_LPL, StandardCharsets.UTF_8);

        // 导入
        TemplateV3ImportService importService = new TemplateV3ImportService();
        importService.setEnableMediaDiscovery(true);
        TemplateV3ImportService.TemplateV3ImportResult result =
                importService.importFileWithHeader(lplFile, importTemplate);
        List<Game> games = result.getGames();

        Platform platform = new Platform();
        importService.applySystemFieldsToPlatform(platform, result.getSystemFields(), importTemplate);

        // 导出
        Map<String, String> vars = new HashMap<>();
        vars.put("outputPath", tmpDir.toString());
        TemplateV3ExportService exportService = new TemplateV3ExportService();
        String json = exportService.generateContent(games, exportTemplate, platform, vars);

        JsonNode doc = mapper.readTree(json);

        // 顶层字段：version 必须是字符串 "1.5"，mode 必须是数字
        assertEquals("1.5", doc.get("version").asText());
        assertTrue(doc.get("version").isTextual(), "version 应为 JSON 字符串");
        assertEquals(0, doc.get("label_display_mode").asInt());
        assertTrue(doc.get("label_display_mode").isIntegralNumber(), "label_display_mode 应为 JSON 数字");
        assertEquals("", doc.get("default_core_path").asText());

        JsonNode items = doc.get("items");
        assertNotNull(items, "应有 items 数组");
        assertEquals(3, items.size());

        // 条目1：DETECT crc → 导出回填 DETECT；db_name = platform.name + .lpl
        JsonNode i0 = items.get(0);
        assertEquals("Sonic The Hedgehog 2 (World)", i0.get("label").asText());
        assertEquals("DETECT", i0.get("core_path").asText());
        assertEquals("DETECT", i0.get("crc32").asText());
        assertEquals("MD - Mega Drive.lpl", i0.get("db_name").asText());
        assertFalse(i0.get("path").asText().contains("#"), "导出 path 应为纯 zip 形式");

        // 条目2：crc32 → XXXXXXXX|crc
        JsonNode i1 = items.get(1);
        assertEquals("G:\\emu\\out\\FBNEO\\FBNEO ACT\\warriorb.zip", i1.get("path").asText());
        assertEquals("12345678|crc", i1.get("crc32").asText());

        // 条目3：空 crc → DETECT
        assertEquals("DETECT", items.get(2).get("crc32").asText());

        // 再导入：导出的内容必须能被本模板再次导入且数据一致
        File roundTripFile = tmpDir.resolve("roundtrip.lpl").toFile();
        Files.writeString(roundTripFile.toPath(), json, StandardCharsets.UTF_8);
        TemplateV3ImportService.TemplateV3ImportResult result2 =
                importService.importFileWithHeader(roundTripFile, importTemplate);
        List<Game> games2 = result2.getGames();

        assertEquals(3, games2.size(), "再导入应保持 3 条游戏");
        assertEquals("Warriors of Fate (World 921002)", games2.get(1).getName());
        assertEquals("G:\\emu\\out\\FBNEO\\FBNEO ACT\\warriorb.zip", games2.get(1).getPath());
        assertEquals("12345678", games2.get(1).getCrc32(), "再导入 crc32 应还原为 8 位 hex");
    }
}
