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
import com.gamelist.variant.ecm.EcmReader;

/**
 * ECM → BIN：纯 Java 解包为 2352 原始扇区镜像。
 * <p>
 * ECM 不压缩数据，只是把每个扇区里能算出来的 EDC/ECC（以及 MODE2 的重复 subheader）挖掉，
 * 所以解包是纯计算，不依赖 chdman 或任何外部工具。
 * <p>
 * 解包器会用文件尾的整盘累计 EDC 自校验：补出来的字节只要与原盘有一处不一致就抛异常，
 * 这是唯一能证明“解码真的对”的手段，比长度检查可靠得多。
 */
@Component
public class EcmToBinModule implements ConversionModule {

    private static final ModuleSpec SPEC = new ModuleSpec(
            "ecm2bin",
            ArtifactKind.ECM,
            ArtifactKind.RAW_2352,
            25,
            true,
            ModuleState.AVAILABLE,
            "纯 Java 解包：还原同步头并重算 EDC/ECC，尾部累计 EDC 不符即判失败",
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
        if (ImageEvidence.isEcm(path)) {
            return Verdict.accept();
        }
        return Verdict.reject("缺少 ECM 容器头（45 43 4D 00），不是错误码建模压缩镜像");
    }

    @Override
    public Analysis analyze(DiscArtifact input) {
        // ECM 只有解到底才能知道装了什么，分析阶段只看容器本身，把真正的结论留给转换
        return Analysis.builder(ArtifactKind.ECM)
                .totalBytes(sizeOf(input.path()))
                .sectorSize(ImageEvidence.SECTOR_2352)
                .summary("ECM 容器 · 解包后还原 2352 扇区原始镜像")
                .fact("container", "ECM")
                .build();
    }

    @Override
    public DiscArtifact convert(DiscArtifact input, Analysis analysis, Path output, ProgressSink progress)
            throws IOException {
        Path part = AtomicOutput.partOf(output);
        AtomicOutput.discard(part);
        EcmReader.Result result;
        try {
            result = EcmReader.decode(input.path(), part, progress);
            AtomicOutput.commit(part, output, result.bytes());
        } catch (IOException | RuntimeException e) {
            AtomicOutput.discard(part);
            throw e;
        }

        // 解出来的扇区数多于 ECM 记录的扇区数，说明剩下的部分是按 literal 直存的原样字节
        // （通常是音轨，也可能是原盘 EDC 为 0 的 Form2 扇区），这种镜像不能再按单数据轨处理
        long rawSectors = result.bytes() / ImageEvidence.SECTOR_2352;
        long straightSectors = Math.max(0, rawSectors - result.sectors());
        ArtifactKind kind = straightSectors > 0 ? ArtifactKind.RAW_2352_MULTI : ArtifactKind.RAW_2352;
        String detail = result.mode() + " · " + rawSectors + " 扇区"
                + (straightSectors > 0 ? "（含 " + straightSectors + " 个原样存储扇区，疑似音轨）" : "");
        return new DiscArtifact(kind, output, result.bytes(), ImageEvidence.SECTOR_2352, detail,
                List.of(input.path()), null);
    }

    private static long sizeOf(Path path) {
        try {
            return path == null ? 0L : Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }
}
