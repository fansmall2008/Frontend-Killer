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
import com.gamelist.variant.ciso.CisoWriter;

/**
 * ISO → CSO：CISO v1 写入（2048 块 + 原始 deflate）。
 * <p>
 * 判断要求“2048 对齐 + 有 ISO9660 卷描述符（或扩展名明确是 .iso）”，
 * 只有扩展名对、内容却是 2352 原始镜像或 CSO 的文件会在验证阶段被踢出去。
 */
@Component
public class IsoToCsoModule implements ConversionModule {

    private static final ModuleSpec SPEC = new ModuleSpec(
            "iso2cso",
            ArtifactKind.ISO_2048,
            ArtifactKind.CSO,
            30,
            true,
            ModuleState.AVAILABLE,
            "CISO v1：2048 字节块 + 原始 deflate；块不可压时原样存储",
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
            return Verdict.mismatch(ArtifactKind.CSO, "这已经是 CSO 文件，不需要再压一次");
        }
        if (ImageEvidence.isZiso(path)) {
            return Verdict.mismatch(ArtifactKind.ZSO, "这是 ZSO（LZ4 块压缩），不是 ISO");
        }
        long size = sizeOf(path);
        if (size <= 0) {
            return Verdict.reject("文件为空");
        }
        if (size % ImageEvidence.SECTOR_2048 != 0) {
            return Verdict.reject("大小不是 2048 的整数倍（" + size + " 字节），不是 2048 扇区镜像");
        }
        if (ImageEvidence.hasRaw2352Sync(path)) {
            return Verdict.mismatch(ArtifactKind.RAW_2352, "检测到 2352 扇区同步头，这是 BIN 原始镜像");
        }
        if (!ImageEvidence.hasIsoPvd2048(path) && !"iso".equalsIgnoreCase(extensionOf(path))) {
            return Verdict.reject("未找到 ISO9660 卷描述符，且扩展名不是 .iso，无法确认是 ISO 镜像");
        }
        return Verdict.accept();
    }

    @Override
    public Analysis analyze(DiscArtifact input) {
        Path path = input.path();
        long size = sizeOf(path);
        long blocks = size / ImageEvidence.SECTOR_2048;
        Analysis.Builder builder = Analysis.builder(ArtifactKind.ISO_2048)
                .totalBytes(size)
                .sectorSize(ImageEvidence.SECTOR_2048)
                .mode("MODE1")
                .tracks(1, 0)
                .fact("blocks", String.valueOf(blocks))
                .fact("container", "ISO9660");

        if (!ImageEvidence.hasIsoPvd2048(path)) {
            builder.summary("2048 扇区镜像，未找到 ISO9660 卷描述符")
                    .warn("未找到 ISO9660 卷描述符，可能是素材盘或非标准镜像，转换后仍保持原始字节");
        } else {
            String volumeId = ImageEvidence.isoVolumeId(path);
            builder.summary("ISO9660" + (volumeId.isBlank() ? "" : " · " + volumeId));
            if (!volumeId.isBlank()) {
                builder.fact("volumeId", volumeId);
            }
        }

        // 31 位偏移最坏情况估算：头部 + 索引 + 全部块原样存储
        long dataStart = CisoWriter.HEADER_SIZE + blocks * 4L;
        if (dataStart + size > CisoWriter.MAX_OFFSET) {
            builder.block("镜像约 " + DiscArtifact.formatBytes(size)
                    + "，超出 CSO v1 的 31 位偏移上限（约 2GB），需要 ZSO/CHD 或分段");
        }
        if (blocks > Integer.MAX_VALUE - 8) {
            builder.block("块数量超出索引范围");
        }
        return builder.build();
    }

    @Override
    public DiscArtifact convert(DiscArtifact input, Analysis analysis, Path output, ProgressSink progress)
            throws IOException {
        Path part = AtomicOutput.partOf(output);
        AtomicOutput.discard(part);
        try {
            long written = CisoWriter.write(input.path(), part, progress);
            long totalBytes = analysis == null ? sizeOf(input.path()) : analysis.totalBytes();
            long blocks = totalBytes / ImageEvidence.SECTOR_2048;
            long minimum = CisoWriter.HEADER_SIZE + blocks * 4L + blocks;
            if (written < minimum) {
                throw new IOException("CSO 产出体积异常: " + written + " < " + minimum);
            }
            // 头部回读：确认写入的 totalBytes 与输入一致，索引区未越界
            CisoReader.Header header = CisoReader.readHeader(part);
            if (header.totalBytes() != totalBytes) {
                throw new IOException("CSO 头部记录的总长与输入不符: "
                        + header.totalBytes() + "/" + totalBytes);
            }
            AtomicOutput.commit(part, output, written);
        } catch (IOException | RuntimeException e) {
            AtomicOutput.discard(part);
            throw e;
        }
        return new DiscArtifact(ArtifactKind.CSO, output, Files.size(output),
                ImageEvidence.SECTOR_2048, "块 2048 · zlib", List.of(input.path()), null);
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1) : "";
    }
}
