package com.gamelist.variant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gamelist.variant.ciso.CisoReader;
import com.gamelist.variant.ciso.CisoWriter;
import com.gamelist.variant.module.CsoToIsoModule;
import com.gamelist.variant.module.IsoToCsoModule;

/**
 * 内核自测：CSO 往返必须逐字节一致，规划器要能区分“能转”“还没做”“不需要转”。
 */
class CisoRoundTripTest {

    private static final int BLOCK = ImageEvidence.SECTOR_2048;

    @Test
    void randomDataRoundTrip(@TempDir Path dir) throws IOException {
        byte[] original = randomBytes(256 * BLOCK, 42);
        assertRoundTrip(dir, original, "random");
    }

    @Test
    void compressibleDataRoundTrip(@TempDir Path dir) throws IOException {
        byte[] original = new byte[512 * BLOCK];
        // 每个块内重复，保证走“压缩后更小”的分支
        for (int block = 0; block < 512; block++) {
            Arrays.fill(original, block * BLOCK, (block + 1) * BLOCK, (byte) (block % 7));
        }
        assertRoundTrip(dir, original, "compressible");
    }

    private void assertRoundTrip(Path dir, byte[] original, String label) throws IOException {
        Path iso = dir.resolve(label + ".iso");
        Path cso = dir.resolve(label + ".cso");
        Path restored = dir.resolve(label + "-restored.iso");
        Files.write(iso, original);

        long written = CisoWriter.write(iso, cso, ConversionModule.ProgressSink.NOOP);
        assertEquals(Files.size(cso), written);

        CisoReader.Header header = CisoReader.readHeader(cso);
        assertEquals(original.length, header.totalBytes());
        assertEquals(BLOCK, header.blockSize());
        assertTrue(ImageEvidence.isCiso(cso), "输出必须带 CISO 魔数");
        // 可压数据应当明显小于原文件
        if ("compressible".equals(label)) {
            assertTrue(written < original.length / 2, "可压数据压缩率异常: " + written);
        }

        long extracted = CisoReader.extract(cso, restored, ConversionModule.ProgressSink.NOOP);
        assertEquals(original.length, extracted);
        assertArrayEquals(original, Files.readAllBytes(restored), label + " 往返内容不一致");
    }

    @Test
    void plannerPicksSingleStep() {
        ModuleCatalog catalog = new ModuleCatalog(List.of(new IsoToCsoModule(), new CsoToIsoModule()));
        PipelinePlanner planner = new PipelinePlanner(catalog);

        PipelinePlan plan = planner.plan(ArtifactKind.ISO_2048, ArtifactKind.CSO);
        assertEquals(PipelinePlan.Status.READY, plan.status());
        assertEquals(List.of("iso2cso"), plan.moduleIds());
        assertEquals("iso ▸[iso2cso]▸ cso", plan.chainText());

        PipelinePlan reverse = planner.plan(ArtifactKind.CSO, ArtifactKind.ISO_2048);
        assertEquals(PipelinePlan.Status.READY, reverse.status());
        assertEquals("cso ▸[cso2iso]▸ iso", reverse.chainText());

        assertEquals(PipelinePlan.Status.ALREADY, planner.plan(ArtifactKind.CSO, ArtifactKind.CSO).status());
    }

    @Test
    void plannerReportsReservedModule() {
        ModuleCatalog catalog = new ModuleCatalog(List.of(new IsoToCsoModule(), new CsoToIsoModule()));
        PipelinePlanner planner = new PipelinePlanner(catalog);

        // bin → cso 需要 bin2iso，目前只有 JSON 占位
        PipelinePlan plan = planner.plan(ArtifactKind.RAW_2352, ArtifactKind.CSO);
        assertEquals(PipelinePlan.Status.INCOMPLETE, plan.status());
        assertEquals("bin2iso", plan.missingModuleId());
        assertEquals("bin ▸[bin2iso]▸ iso ▸[iso2cso]▸ cso", plan.chainText());

        // 压缩包没有任何出边
        assertEquals(PipelinePlan.Status.UNSUPPORTED,
                planner.plan(ArtifactKind.ARCHIVE, ArtifactKind.CSO).status());
    }

    @Test
    void moduleRejectsDisguisedInput(@TempDir Path dir) throws IOException {
        // 301056 = 128 × 2352 = 147 × 2048：尺寸骗得过 2048 检查，同步头骗不过
        byte[] fakeIso = new byte[301056];
        fakeIso[0] = 0x00;
        fakeIso[11] = 0x00;
        for (int i = 1; i <= 10; i++) {
            fakeIso[i] = (byte) 0xFF;
        }
        fakeIso[15] = 0x02;
        Path disguised = dir.resolve("disguised.iso");
        Files.write(disguised, fakeIso);

        IsoToCsoModule module = new IsoToCsoModule();
        Verdict verdict = module.verify(DiscArtifact.of(ArtifactKind.ISO_2048, disguised,
                fakeIso.length, BLOCK, ""));
        assertEquals(Verdict.Result.MISMATCH, verdict.result());
        assertEquals(ArtifactKind.RAW_2352, verdict.actualKind());

        // 真 CSO 被要求再压一次：应识别为已经压过
        Path realIso = dir.resolve("real.iso");
        Files.write(realIso, randomBytes(16 * BLOCK, 7));
        Path cso = dir.resolve("real.cso");
        CisoWriter.write(realIso, cso, ConversionModule.ProgressSink.NOOP);
        Verdict already = module.verify(DiscArtifact.of(ArtifactKind.CSO, cso, Files.size(cso), BLOCK, ""));
        assertEquals(Verdict.Result.MISMATCH, already.result());
        assertEquals(ArtifactKind.CSO, already.actualKind());

        // 反向：cso2iso 不接普通 ISO
        CsoToIsoModule reverse = new CsoToIsoModule();
        assertFalse(reverse.verify(DiscArtifact.of(ArtifactKind.ISO_2048, realIso,
                Files.size(realIso), BLOCK, "")).accepted());
        assertTrue(reverse.verify(DiscArtifact.of(ArtifactKind.CSO, cso,
                Files.size(cso), BLOCK, "")).accepted());
    }

    @Test
    void pipelineRoundTripsIsoAndCso(@TempDir Path dir) throws IOException {
        ModuleCatalog catalog = new ModuleCatalog(List.of(new IsoToCsoModule(), new CsoToIsoModule()));
        PipelinePlanner planner = new PipelinePlanner(catalog);
        PipelineRunner runner = new PipelineRunner(catalog, planner);

        byte[] original = new byte[64 * BLOCK];
        for (int block = 0; block < 64; block++) {
            Arrays.fill(original, block * BLOCK, (block + 1) * BLOCK, (byte) (block % 5));
        }
        // 第 16 扇区放 ISO9660 PVD 标识，避开“无 PVD 靠扩展名兜底”的模糊地带
        original[16 * BLOCK + 1] = 'C';
        original[16 * BLOCK + 2] = 'D';
        original[16 * BLOCK + 3] = '0';
        original[16 * BLOCK + 4] = '0';
        original[16 * BLOCK + 5] = '1';
        Path iso = dir.resolve("game.iso");
        Files.write(iso, original);

        Path cso = dir.resolve("game.cso");
        PipelineResult forward = runner.run(planner.plan(ArtifactKind.ISO_2048, ArtifactKind.CSO),
                DiscArtifact.of(ArtifactKind.ISO_2048, iso, original.length, BLOCK, ""),
                cso, dir.resolve("work-forward"), ConversionModule.ProgressSink.NOOP);
        assertEquals(PipelineResult.Status.SUCCESS, forward.status(), forward.describe());
        assertTrue(ImageEvidence.isCiso(cso), "产线输出的 CSO 必须带魔数");

        Path restored = dir.resolve("restored.iso");
        PipelineResult backward = runner.run(planner.plan(ArtifactKind.CSO, ArtifactKind.ISO_2048),
                DiscArtifact.of(ArtifactKind.CSO, cso, Files.size(cso), BLOCK, ""),
                restored, dir.resolve("work-backward"), ConversionModule.ProgressSink.NOOP);
        assertEquals(PipelineResult.Status.SUCCESS, backward.status(), backward.describe());
        assertArrayEquals(original, Files.readAllBytes(restored), "iso→cso→iso 内容不一致");
    }

    private static byte[] randomBytes(int length, long seed) {
        byte[] data = new byte[length];
        new Random(seed).nextBytes(data);
        return data;
    }
}
