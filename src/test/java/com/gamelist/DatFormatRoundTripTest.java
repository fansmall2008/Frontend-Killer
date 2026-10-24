package com.gamelist;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamelist.config.RomDbPlatformMapping;
import com.gamelist.model.RomDb;
import com.gamelist.util.ClrMameProDatParser;

/**
 * DAT 生成器格式契约回环测试（无 DB / 无需重启）。
 * <p>
 * 以 DatToolServiceImpl 的写出格式（writeHeader/line/writeRom/esc/tokenRomName 的字面产物）
 * 构造样例 .dat，交给既有 ClrMameProDatParser 解析，验证生成→回灌闭环的字段抽取一致；
 * 并验证 game→DAT 的文件名 systemId 反查回环（RomDbPlatformMapping 正反双向）。
 */
class DatFormatRoundTripTest {

    /** 复刻 writeRomDbDat 的字面输出：属性加引号、哈希裸小写、空字段省略、rom 名单行。 */
    private static String sampleRomDbDat() {
        StringBuilder sb = new StringBuilder();
        // clrmamepro ( name/description/version )
        sb.append("clrmamepro (\n");
        sb.append("\tname \"Sony - PlayStation\"\n");
        sb.append("\tdescription \"Sony - PlayStation\"\n");
        sb.append("\tversion 20240101\n");
        sb.append(")\n\n");
        // game 1：全字段 + 裸 rom 名
        sb.append("game (\n");
        sb.append("\tname \"Final Fantasy VII (USA)\"\n");
        sb.append("\tregion \"USA\"\n");
        sb.append("\tdeveloper \"Square\"\n");
        sb.append("\tpublisher \"Sony Computer Entertainment\"\n");
        sb.append("\treleaseyear \"1997\"\n");
        sb.append("\tgenre \"Role Playing Games\"\n");
        sb.append("\tserial \"SLUS-00700\"\n");
        sb.append("\trom ( name ff7.zip crc 1a2b3c4d md5 0123456789abcdef0123456789abcdef sha1 da39a3ee5e6b4b0d3255bfef95601890afd80709 )\n");
        sb.append(")\n\n");
        // game 2：仅 name + serial（无 rom 哈希，serial 兜底有效性）
        sb.append("game (\n");
        sb.append("\tname \"Bare Entry\"\n");
        sb.append("\tserial \"SLES-12345\"\n");
        sb.append("\trom ( name bare.zip )\n");
        sb.append(")\n\n");
        // game 3：rom 名含空格 → 加引号
        sb.append("game (\n");
        sb.append("\tname \"Quoted Rom Game\"\n");
        sb.append("\tcomment not used\n");
        sb.append("\trom ( name \"Super Game (USA).bin\" crc deadbeef )\n");
        sb.append(")\n\n");
        // game 4：无效条目 —— 有名但既无 crc 也无 serial → 解析器应丢弃
        sb.append("game (\n");
        sb.append("\tname \"Invalid No Hash No Serial\"\n");
        sb.append("\trom ( name invalid.zip )\n");
        sb.append(")\n\n");
        return sb.toString();
    }

    @Test
    void generatedDatIsParseableByClrMameProParser() throws Exception {
        File tmp = File.createTempFile("dat-roundtrip-", ".dat");
        tmp.deleteOnExit();
        Files.write(tmp.toPath(), sampleRomDbDat().getBytes(StandardCharsets.UTF_8));

        String platformName = ClrMameProDatParser.platformNameFromFileName(tmp.getName());
        int systemId = RomDbPlatformMapping.getSystemId("Sony - PlayStation");
        assertEquals(57, systemId, "Sony - PlayStation 应映射到 SS systemId 57");

        String source = ClrMameProDatParser.detectSource(tmp);
        assertEquals("redump", source, "含 region 属性应识别为 redump");

        List<RomDb> rows = ClrMameProDatParser.parse(tmp, "Sony - PlayStation", systemId, source);
        // game1+game2+game3 有效；game4 无 crc 且无 serial → 丢弃
        assertEquals(3, rows.size(), "无效条目（无名/无哈希无serial）应被解析器丢弃");

        RomDb ff7 = rows.get(0);
        assertEquals("Final Fantasy VII (USA)", ff7.getGameName());
        assertEquals("USA", ff7.getRegion());
        assertEquals("Square", ff7.getDeveloper());
        assertEquals("Sony Computer Entertainment", ff7.getPublisher());
        assertEquals("1997", ff7.getReleaseyear());
        assertEquals("Role Playing Games", ff7.getGenre());
        // 解析器 serial 去空格转大写
        assertEquals("SLUS-00700", ff7.getSerial());
        // 解析器哈希统一大写
        assertEquals("1A2B3C4D", ff7.getCrc32());
        assertEquals("0123456789ABCDEF0123456789ABCDEF", ff7.getMd5());
        assertEquals("DA39A3EE5E6B4B0D3255BFEF95601890AFD80709", ff7.getSha1());
        assertEquals("ff7.zip", ff7.getRomFile());
        assertEquals("ff7", ff7.getRomFileNorm());
        assertEquals(systemId, ff7.getSystemId());

        RomDb bare = rows.get(1);
        assertEquals("Bare Entry", bare.getGameName());
        assertEquals("SLES-12345", bare.getSerial());
        assertNull(bare.getCrc32(), "仅 serial 条目不应有 crc");

        RomDb quoted = rows.get(2);
        assertEquals("Quoted Rom Game", quoted.getGameName());
        assertEquals("DEADBEEF", quoted.getCrc32());
        // rom 名加引号也能被 ROM_ENTRY_ATTR 正确剥离
        assertEquals("Super Game (USA).bin", quoted.getRomFile());
    }

    @Test
    void gameDatFileNameResolvesBackToSameSystemId() {
        // game→DAT：由 systemId 反查规范 libretro 名作文件名，导回后须落同一 systemId
        for (int sid : new int[] {1, 3, 4, 14, 22, 23, 57, 58, 75}) {
            String canonical = RomDbPlatformMapping.getPlatformNameBySystemId(sid);
            assertNotNull(canonical, "systemId " + sid + " 应能反查到规范 libretro 名");
            int back = RomDbPlatformMapping.getSystemId(canonical);
            assertEquals(sid, back, "反查名再解析应回到同一 systemId（回灌不失配）");
        }
    }

    @Test
    void systemIdZeroHasNoCanonicalName() {
        // systemId=0（无 SS 对应）不纳入反查，走平台名裸名 + 预检橙警
        assertNull(RomDbPlatformMapping.getPlatformNameBySystemId(0));
        // 无 SS 对应的 libretro 平台名（e-Reader）正向仍解析为 0
        assertEquals(0, RomDbPlatformMapping.getSystemId("Nintendo - e-Reader"));
    }
}
