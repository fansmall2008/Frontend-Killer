package com.gamelist.variant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * CUE 载荷解析：真实 PSX ECM 备份里 cue 的 FILE 名与磁盘文件名一律对不上，
 * 这里逐条固定认领规则，避免以后改动悄悄把整盘认成一个不存在的数据轨。
 */
class CueAssetsTest {

    /** 一个 ecm 装整盘：13 轨共用一个 FILE，磁盘上只有 Track 01.bin.ecm */
    private static final String CUE_NAME = "Paranoia Scape (Japan) [SLPS-01375].cue";

    private static final String SINGLE_FILE_CUE = """
            FILE "Paranoia Scape (Japan) [SLPS-01375].bin" BINARY
              TRACK 01 MODE2/2352
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                INDEX 01 51:14:55
              TRACK 03 AUDIO
                INDEX 01 55:26:40
            """;

    /** 数据轨 ECM + 音轨 APE：cue 照样写 Redump 的 .bin / .wav 名 */
    private static final String SPLIT_CUE = """
            FILE "Advan Racing (Japan) [SLPS-01689].bin" BINARY
              TRACK 01 MODE2/2352
                INDEX 01 00:00:00
            FILE "Advan Racing (Japan) (Track 2) [SLPS-01689].wav" WAVE
              TRACK 02 AUDIO
                INDEX 01 00:00:00
            """;

    @Test
    void resolvesWholeDiscEcmAsSingleAsset(@TempDir Path dir) throws IOException {
        write(dir.resolve(CUE_NAME), SINGLE_FILE_CUE);
        Path ecm = dir.resolve("Track 01.bin.ecm");
        writeEcmHeader(ecm);

        CueSheet sheet = CueSheet.parse(dir.resolve(CUE_NAME));
        List<CueAssets.Asset> assets = CueAssets.resolve(sheet);

        assertEquals(1, assets.size(), "两条音轨共用同一个 FILE，只应认领一次");
        CueAssets.Asset asset = assets.get(0);
        assertEquals("Paranoia Scape (Japan) [SLPS-01375].bin", asset.declared());
        assertEquals(ecm.getFileName().toString(), asset.file().getFileName().toString());
        assertEquals(CueAssets.Form.ECM, asset.form());
        assertFalse(asset.audio(), "这个 FILE 里有数据轨，不能当纯音轨");
        assertTrue(asset.needsWork(), "ECM 必须先解包");
        assertTrue(CueAssets.needsWork(assets));
        assertEquals("ECM 载荷", CueAssets.payloadNote(assets));
    }

    @Test
    void resolvesEcmDataTrackAndApeAudioTrack(@TempDir Path dir) throws IOException {
        write(dir.resolve("Advan Racing (Japan) [SLPS-01689].cue"), SPLIT_CUE);
        writeEcmHeader(dir.resolve("Track 01.bin.ecm"));
        writeSparse(dir.resolve("Advan Racing (Japan) (Track 2) [SLPS-01689].ape"), 4096);

        CueSheet sheet = CueSheet.parse(dir.resolve("Advan Racing (Japan) [SLPS-01689].cue"));
        List<CueAssets.Asset> assets = CueAssets.resolve(sheet);

        assertEquals(2, assets.size());
        assertEquals(CueAssets.Form.ECM, assets.get(0).form(), "数据轨应认领 Track 01.bin.ecm");
        assertEquals("Track 01.bin.ecm", assets.get(0).file().getFileName().toString());
        assertFalse(assets.get(0).audio());
        assertEquals(CueAssets.Form.AUDIO, assets.get(1).form(), ".wav 声明应认领同名 .ape");
        assertEquals("Advan Racing (Japan) (Track 2) [SLPS-01689].ape",
                assets.get(1).file().getFileName().toString());
        assertTrue(assets.get(1).audio());
        assertEquals("ECM 数据轨 + APE 音轨", CueAssets.payloadNote(assets));
    }

    @Test
    void fallsBackToLargestDiscWhenNamesAreForeign(@TempDir Path dir) throws IOException {
        write(dir.resolve("Koukon no Ode (J).cue"), """
                FILE "Koukon no Ode - Ode to The Sunset Era (J).BIN" BINARY
                  TRACK 01 MODE2/2352
                    INDEX 01 00:00:00
                """);
        // 汉化包常在同目录塞 BIOS 字库：512 KB、不按 2352 对齐，不能当整盘
        writeSparse(dir.resolve("[BIOS]FONT1.BIN"), 512 * 1024);
        writeSparse(dir.resolve("[BIOS]FONT2.BIN"), 512 * 1024);
        Path disc = dir.resolve("[SLPS_00532]Koukon no Ode.bin");
        writeSparse(disc, ImageEvidence.SECTOR_2352 * 1000L);

        CueSheet sheet = CueSheet.parse(dir.resolve("Koukon no Ode (J).cue"));
        List<CueAssets.Asset> assets = CueAssets.resolve(sheet);

        assertEquals(1, assets.size());
        assertNotNull(assets.get(0).file(), "多个候选时应挑出整盘镜像");
        assertEquals(disc.getFileName().toString(), assets.get(0).file().getFileName().toString());
    }

    @Test
    void plainBinNeedsNoWork(@TempDir Path dir) throws IOException {
        Path cue = dir.resolve("Nekketsu Oyako (Japan).cue");
        write(cue, """
                FILE "Nekketsu Oyako (Japan).bin" BINARY
                  TRACK 01 MODE2/2352
                    INDEX 01 00:00:00
                """);
        writeSparse(dir.resolve("Nekketsu Oyako (Japan).bin"), ImageEvidence.SECTOR_2352 * 10L);

        List<CueAssets.Asset> assets = CueAssets.resolve(CueSheet.parse(cue));

        assertEquals(1, assets.size());
        assertEquals(CueAssets.Form.RAW, assets.get(0).form());
        assertTrue(assets.get(0).present());
        assertFalse(assets.get(0).needsWork(), "名字与格式都能直接用，不该进暂存");
        assertEquals("", CueAssets.payloadNote(assets));
    }

    /** 探测层要把“载荷是 ECM”写进说明，界面才看得出这条产线要多跑一道解包 */
    @Test
    void probeReportsPayloadForm(@TempDir Path dir) throws IOException {
        write(dir.resolve(CUE_NAME), SINGLE_FILE_CUE);
        Path ecm = dir.resolve("Track 01.bin.ecm");
        writeEcmHeader(ecm);

        DiscImageProbe probe = new DiscImageProbe();

        DiscArtifact bare = probe.probe(ecm);
        assertEquals(ArtifactKind.ECM, bare.kind(), "ECM 容器头未被识别");

        DiscArtifact disc = probe.probe(dir.resolve(CUE_NAME));
        assertEquals(ArtifactKind.RAW_2352_MULTI, disc.kind(), "13 轨应判为多轨镜像：" + disc.detail());
        assertEquals(ecm.toAbsolutePath(), disc.path().toAbsolutePath(), "数据轨应指向 ecm 载荷");
        assertTrue(disc.detail().contains("ECM 载荷"), "说明里应标出载荷形态：" + disc.detail());
        assertTrue(disc.related().contains(ecm), "关联文件要带出 ecm，暂存阶段才解得开");
    }

    private static void write(Path file, String text) throws IOException {
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** 只写容器头，探测与认领都不读后面的内容 */
    private static void writeEcmHeader(Path file) throws IOException {
        byte[] header = {'E', 'C', 'M', 0, 'x', 'x', 'x', 'x'};
        Files.write(file, header);
    }

    private static void writeSparse(Path file, long size) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(file,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(size - 1);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }
    }
}
