package com.gamelist.variant.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

import com.gamelist.variant.Analysis;
import com.gamelist.variant.ArtifactKind;
import com.gamelist.variant.AtomicOutput;
import com.gamelist.variant.ConversionModule;
import com.gamelist.variant.DiscArtifact;
import com.gamelist.variant.ImageEvidence;
import com.gamelist.variant.Verdict;
import com.gamelist.variant.ciso.CisoReader;

/**
 * CSO → ISO：按块索引解包为 2048 扇区镜像。
 * <p>
 * 判断只认 CISO 魔数；ZSO 会被识别成另一种形态交给调度器重算，
 * 剩下的一律视为“不是 CSO”并附上证据。
 */
@Component
public class CsoToIsoModule implements ConversionModule {

    private static final ModuleSpec SPEC = new ModuleSpec(
            "cso2iso",
            ArtifactKind.CSO,
            ArtifactKind.ISO_2048,
            30,
            true,
            ModuleState.AVAILABLE,
            "按块索引解包为 ISO",
            List.of());

    @Override
    public ModuleSpec spec() {
        return SPEC;
    }

    @Override
    public Verdict verify(DiscArtifact input) {
        Path path = input == null ? null : input.path();
        if (path == null || !Files.isRegularFile(path)) {
            return Verdict.reject("源文件不存在");
        }
        if (ImageEvidence.isCiso(path)) {
            return Verdict.accept();
        }
        if (ImageEvidence.isZiso(path)) {
            return Verdict.mismatch(ArtifactKind.ZSO, "这是 ZSO（LZ4 块压缩），不是 CSO");
        }
        return Verdict.reject("缺少 CISO 魔数，不是块压缩镜像");
    }

    @Override
    public Analysis analyze(DiscArtifact input) throws IOException {
        CisoReader.Header header;
        try {
            header = CisoReader.readHeader(input.path());
        } catch (IOException e) {
            return Analysis.builder(ArtifactKind.CSO)
                    .totalBytes(sizeOf(input.path()))
                    .summary("CSO 头部无法解析")
                    .block(e.getMessage())
                    .build();
        }

        Analysis.Builder builder = Analysis.builder(ArtifactKind.CSO)
                .totalBytes(header.totalBytes())
                .sectorSize(header.blockSize())
                .mode("CSO v" + header.version())
                .tracks(1, 0)
                .summary("块 " + header.blockSize() + " · " + header.blocks() + " 块")
                .fact("blocks", String.valueOf(header.blocks()))
                .fact("blockSize", String.valueOf(header.blockSize()))
                .fact("align", String.valueOf(header.align()));

        long packed = sizeOf(input.path());
        if (packed > 0 && header.totalBytes() > 0) {
            builder.fact("ratio", String.format("%.1f%%", packed * 100.0 / header.totalBytes()));
        }
        if (header.align() != 0) {
            builder.warn("该 CSO 使用 4KB 对齐（align=" + header.align() + "），解包不受影响");
        }
        if (header.totalBytes() % ImageEvidence.SECTOR_2048 != 0) {
            builder.warn("解包后长度不是 2048 的整数倍，可能不是标准 ISO");
        }
        return builder.build();
    }

    @Override
    public DiscArtifact convert(DiscArtifact input, Analysis analysis, Path output, ProgressSink progress)
            throws IOException {
        Path part = AtomicOutput.partOf(output);
        AtomicOutput.discard(part);
        long expected;
        try {
            CisoReader.Header header = CisoReader.readHeader(input.path());
            expected = header.totalBytes();
            long written = CisoReader.extract(input.path(), part, progress);
            if (written != expected) {
                throw new IOException("解包字节数不符: " + written + "/" + expected);
            }
            AtomicOutput.commit(part, output, expected);
        } catch (IOException | RuntimeException e) {
            AtomicOutput.discard(part);
            throw e;
        }
        return new DiscArtifact(ArtifactKind.ISO_2048, output, expected,
                ImageEvidence.SECTOR_2048, "解包自 CSO", List.of(input.path()), null);
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }
}
