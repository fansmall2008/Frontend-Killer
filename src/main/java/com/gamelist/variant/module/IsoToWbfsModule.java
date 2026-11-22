package com.gamelist.variant.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

import com.gamelist.util.CartridgeHeaderReader;
import com.gamelist.variant.Analysis;
import com.gamelist.variant.ArtifactKind;
import com.gamelist.variant.AtomicOutput;
import com.gamelist.variant.ConversionModule;
import com.gamelist.variant.DiscArtifact;
import com.gamelist.variant.Verdict;

/**
 * ISO → WBFS：交给 {@code wit copy} 生成清洗分区（scrub 由 wit 自动完成）。
 * <p>
 * WBFS 有损、不可逆（丢掉的扇区回不来），因此 {@code lossless=false}，规划器不会自动选它，
 * 只有用户显式指定目标为 WBFS 时才走这条线。判断阶段确认三件事：源是有效 Wii 光盘镜像、
 * 不是已经是 WBFS、wit 可执行文件已就位。
 */
@Component
public class IsoToWbfsModule implements ConversionModule {

    private static final byte[] WBFS_MAGIC = {'W', 'B', 'F', 'S'};

    private final Wit wit;

    private final ModuleSpec spec;

    public IsoToWbfsModule(Wit wit) {
        this.wit = wit;
        this.spec = new ModuleSpec(
                "iso2wbfs",
                ArtifactKind.ISO_2048,
                ArtifactKind.WBFS,
                45,
                false,
                ModuleState.AVAILABLE,
                "wit copy：ISO → WBFS 清洗分区，scrub 由 wit 完成；有损不可逆，不做 wbfs→iso 回退",
                List.of("wit"));
    }

    @Override
    public ModuleSpec spec() {
        return spec;
    }

    @Override
    public Verdict verify(DiscArtifact input) {
        Path path = input == null ? null : input.path();
        if (path == null || !Files.isRegularFile(path)) {
            return Verdict.reject("源文件不存在");
        }
        if (isWbfsFile(path)) {
            return Verdict.mismatch(ArtifactKind.WBFS, "这已经是 WBFS 文件，不需要再转一次");
        }
        if (input.kind() != ArtifactKind.ISO_2048) {
            return Verdict.mismatch(input.kind());
        }
        // 有效 Wii 光盘镜像：头部能读到 6 位 disc ID（ISO @0x00）
        if (!isWiiDisc(path)) {
            return Verdict.reject("头部没有有效的 Wii disc ID，不是 Wii 光盘镜像");
        }
        if (!wit.available()) {
            return Verdict.reject("未检测到 wit，无法生成 WBFS（可配置 gamelist.wit.path，"
                    + "或在工具箱 → 外部工具里指定 wit 可执行文件）");
        }
        return Verdict.accept();
    }

    @Override
    public Analysis analyze(DiscArtifact input) {
        Path path = input.path();
        long size = input.totalBytes() > 0 ? input.totalBytes() : sizeOf(path);
        Analysis.Builder builder = Analysis.builder(ArtifactKind.ISO_2048)
                .totalBytes(size)
                .sectorSize(2048)
                .mode("Wii")
                .fact("container", "WBFS")
                .fact("wit", wit.version().isBlank() ? "已就绪" : wit.version())
                .summary("交给 wit copy 生成 WBFS（自动 scrub）");

        String discId = wiiDiscId(path);
        if (discId != null && !discId.isBlank()) {
            builder.fact("discId", discId);
        }
        builder.warn("WBFS 是有损清洗分区：盘上未使用的扇区会被丢弃，无法还原成原 ISO");
        return builder.build();
    }

    @Override
    public DiscArtifact convert(DiscArtifact input, Analysis analysis, Path output, ProgressSink progress)
            throws IOException {
        Path part = AtomicOutput.partOf(output);
        AtomicOutput.discard(part);
        try {
            wit.copyToWbfs(input.path(), part, progress);
            AtomicOutput.commit(part, output, -1);
        } catch (IOException | RuntimeException e) {
            AtomicOutput.discard(part);
            throw e;
        }
        long bytes = Files.size(output);
        return new DiscArtifact(ArtifactKind.WBFS, output, bytes, 2048, "WBFS · wit scrub",
                List.of(input.path()), null);
    }

    // ------------------------------------------------------------------ 辅助

    private static boolean isWiiDisc(Path path) {
        try {
            return CartridgeHeaderReader.extractWiiDiscId(path.toString()) != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static String wiiDiscId(Path path) {
        try {
            return CartridgeHeaderReader.extractWiiDiscId(path.toString());
        } catch (IOException e) {
            return null;
        }
    }

    /** 扩展名是 .wbfs，或头 4 字节是 "WBFS" 魔数 */
    private static boolean isWbfsFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".wbfs")) {
            return true;
        }
        try (var in = Files.newInputStream(path)) {
            byte[] head = in.readNBytes(4);
            if (head.length < 4) {
                return false;
            }
            for (int i = 0; i < 4; i++) {
                if (head[i] != WBFS_MAGIC[i]) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static long sizeOf(Path path) {
        try {
            return path == null ? 0L : Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }
}
