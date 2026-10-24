package com.gamelist.variant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gamelist.variant.module.CsoToIsoModule;
import com.gamelist.variant.module.IsoToCsoModule;

/**
 * 真实素材验证：拿本机光盘镜像目录里的 ISO / BIN+CUE / CHD 过一遍形态判断与往返。
 * <p>
 * 目录不存在时整体跳过，不影响其他环境构建。
 */
class RealSampleProbeTest {

    private static final Path SAMPLES = Path.of("D:\\tools\\OpenROM-v3.9.0_Windows_Portable\\iso");

    private static final Path ISO = SAMPLES.resolve("Nekketsu Oyako (Japan).iso");
    private static final Path BIN = SAMPLES.resolve("Nekketsu Oyako (Japan).bin");
    private static final Path CUE = SAMPLES.resolve("Nekketsu Oyako (Japan).cue");

    @Test
    void probesRealFormats() throws IOException {
        assumeTrue(Files.isDirectory(SAMPLES), "本机素材目录不存在，跳过");
        DiscImageProbe probe = new DiscImageProbe();

        assumeTrue(Files.isRegularFile(ISO), "缺少 ISO 素材");
        DiscArtifact iso = probe.probe(ISO);
        assertEquals(ArtifactKind.ISO_2048, iso.kind(), "ISO 应识别为 2048 扇区镜像：" + iso.detail());
        assertEquals(0, iso.totalBytes() % ImageEvidence.SECTOR_2048);

        assumeTrue(Files.isRegularFile(BIN), "缺少 BIN 素材");
        DiscArtifact bin = probe.probe(BIN);
        assertTrue(bin.kind() == ArtifactKind.RAW_2352 || bin.kind() == ArtifactKind.RAW_2352_MULTI,
                "BIN 应识别为 2352 原始镜像，实际：" + bin.kind() + " / " + bin.detail());
        assertEquals(0, bin.totalBytes() % ImageEvidence.SECTOR_2352);

        assumeTrue(Files.isRegularFile(CUE), "缺少 CUE 素材");
        DiscArtifact cue = probe.probe(CUE);
        assertEquals(ArtifactKind.RAW_2352, cue.kind(), "单数据轨 CUE 应落到 RAW_2352：" + cue.detail());
        assertEquals(BIN.toAbsolutePath(), cue.path().toAbsolutePath(), "CUE 应解析到同名 BIN");
        assertFalse(cue.related().isEmpty(), "CUE 应带出关联文件");

        Path chd = firstWithExtension("chd");
        if (chd != null) {
            DiscArtifact chdArtifact = probe.probe(chd);
            assertEquals(ArtifactKind.CHD, chdArtifact.kind(), "CHD 魔数未被识别：" + chd.getFileName());
        }
    }

    @Test
    void isoToCsoRoundTripOnRealFile(@TempDir Path dir) throws IOException {
        assumeTrue(Files.isRegularFile(ISO), "本机素材目录不存在或缺 ISO，跳过");

        ModuleCatalog catalog = new ModuleCatalog(List.of(new IsoToCsoModule(), new CsoToIsoModule()));
        PipelinePlanner planner = new PipelinePlanner(catalog);
        PipelineRunner runner = new PipelineRunner(catalog, planner);
        DiscImageProbe probe = new DiscImageProbe();

        DiscArtifact source = probe.probe(ISO);
        assertEquals(ArtifactKind.ISO_2048, source.kind());

        Path cso = dir.resolve("sample.cso");
        PipelineResult forward = runner.run(planner.plan(source.kind(), ArtifactKind.CSO), source, cso,
                dir.resolve("work-forward"), ConversionModule.ProgressSink.NOOP);
        assertEquals(PipelineResult.Status.SUCCESS, forward.status(), forward.describe());
        assertTrue(ImageEvidence.isCiso(cso), "产线输出的 CSO 必须带魔数");
        long isoSize = Files.size(ISO);
        long csoSize = Files.size(cso);
        long blocks = isoSize / ImageEvidence.SECTOR_2048;
        // 索引 + 头部是固定开销，数据块最大也就是原样存储
        assertTrue(csoSize <= isoSize + CisoWriterOverhead.bytes(blocks) + 64,
                "CSO 体积超出预期: " + csoSize + " vs ISO " + isoSize);

        Path restored = dir.resolve("restored.iso");
        PipelineResult backward = runner.run(planner.plan(ArtifactKind.CSO, ArtifactKind.ISO_2048),
                probe.probe(cso), restored, dir.resolve("work-backward"), ConversionModule.ProgressSink.NOOP);
        assertEquals(PipelineResult.Status.SUCCESS, backward.status(), backward.describe());
        assertEquals(-1L, Files.mismatch(ISO, restored), "iso→cso→iso 往返内容不一致");
    }

    @Test
    void realBinNeedsReservedModule() throws IOException {
        assumeTrue(Files.isRegularFile(BIN), "本机素材目录不存在或缺 BIN，跳过");

        DiscImageProbe probe = new DiscImageProbe();
        DiscArtifact bin = probe.probe(BIN);
        assertEquals(ArtifactKind.RAW_2352, bin.kind());

        ModuleCatalog catalog = new ModuleCatalog(List.of(new IsoToCsoModule(), new CsoToIsoModule()));
        PipelinePlanner planner = new PipelinePlanner(catalog);

        PipelinePlan plan = planner.plan(bin.kind(), ArtifactKind.CSO);
        assertEquals(PipelinePlan.Status.INCOMPLETE, plan.status());
        assertEquals("bin2iso", plan.missingModuleId());
        assertEquals("bin ▸[bin2iso]▸ iso ▸[iso2cso]▸ cso", plan.chainText());
    }

    /**
     * 大文件往返：650MB 的 PS1 镜像，验证 31 位偏移与流式处理。
     * <p>
     * 默认不跑（耗时），需要时设环境变量 VARIANT_BIG_SAMPLE=1。
     */
    @Test
    void largeIsoRoundTripWhenEnabled(@TempDir Path dir) throws IOException {
        assumeTrue("1".equals(System.getenv("VARIANT_BIG_SAMPLE")), "未开启大文件用例（VARIANT_BIG_SAMPLE=1）");
        Path big = SAMPLES.resolve("Final Fantasy VII International (Japan) (Disc 1).iso");
        assumeTrue(Files.isRegularFile(big), "缺少大文件素材");

        ModuleCatalog catalog = new ModuleCatalog(List.of(new IsoToCsoModule(), new CsoToIsoModule()));
        PipelinePlanner planner = new PipelinePlanner(catalog);
        PipelineRunner runner = new PipelineRunner(catalog, planner);
        DiscImageProbe probe = new DiscImageProbe();

        DiscArtifact source = probe.probe(big);
        assertEquals(ArtifactKind.ISO_2048, source.kind());
        long isoSize = Files.size(big);

        Path cso = dir.resolve("big.cso");
        PipelineResult forward = runner.run(planner.plan(ArtifactKind.ISO_2048, ArtifactKind.CSO), source, cso,
                dir.resolve("work-big-forward"), ConversionModule.ProgressSink.NOOP);
        assertEquals(PipelineResult.Status.SUCCESS, forward.status(), forward.describe());

        Path restored = dir.resolve("big-restored.iso");
        PipelineResult backward = runner.run(planner.plan(ArtifactKind.CSO, ArtifactKind.ISO_2048), probe.probe(cso),
                restored, dir.resolve("work-big-backward"), ConversionModule.ProgressSink.NOOP);
        assertEquals(PipelineResult.Status.SUCCESS, backward.status(), backward.describe());
        assertEquals(isoSize, Files.size(restored));
        assertEquals(-1L, Files.mismatch(big, restored), "大文件 iso→cso→iso 内容不一致");
    }

    private static Path firstWithExtension(String extension) throws IOException {
        try (Stream<Path> stream = Files.list(SAMPLES)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith("." + extension))
                    .findFirst()
                    .orElse(null);
        }
    }

    /** CISO v1 固定开销：头部 + 每块 4 字节索引（块内最坏是原样存储） */
    private static final class CisoWriterOverhead {
        private CisoWriterOverhead() {
        }

        static long bytes(long blocks) {
            return 24 + blocks * 4L;
        }
    }
}
