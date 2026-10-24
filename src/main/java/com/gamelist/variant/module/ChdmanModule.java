package com.gamelist.variant.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import com.gamelist.variant.Analysis;
import com.gamelist.variant.ArtifactKind;
import com.gamelist.variant.AtomicOutput;
import com.gamelist.variant.ConversionModule;
import com.gamelist.variant.DiscArtifact;
import com.gamelist.variant.Verdict;

/**
 * 交给 {@code chdman createcd} 的模块基类。
 * <p>
 * 它不解析镜像，只负责三件事：确认输入是能交给 chdman 的形态、把命令发出去、把 chdman 的
 * 反馈原样带回来。产物好不好用是用户自己的判断，我们只管“转没转成”。
 * <p>
 * 各子类只声明“我要哪种输入形态”，差异全部落在 {@link #spec()} 与是否有轨道表上。
 */
public abstract class ChdmanModule implements ConversionModule {

    protected final Chdman chdman;

    private final ModuleSpec spec;

    protected ChdmanModule(Chdman chdman, String id, ArtifactKind input, int cost, String note) {
        this.chdman = chdman;
        this.spec = new ModuleSpec(id, input, ArtifactKind.CHD, cost, true,
                ModuleState.AVAILABLE, note, List.of("chdman"));
    }

    @Override
    public ModuleSpec spec() {
        return spec;
    }

    /**
     * 是否必须由外部索引文件提供轨道表。
     * <p>
     * ISO 是单文件自带扇区布局，GDI/NRG/TOC 自带轨道表，只有裸 BIN 需要 cue 指路。
     */
    protected boolean needsIndex() {
        return spec.input() == ArtifactKind.RAW_2352 || spec.input() == ArtifactKind.RAW_2352_MULTI;
    }

    @Override
    public Verdict verify(DiscArtifact input) {
        Path path = input == null ? null : input.path();
        if (path == null || !Files.isRegularFile(path)) {
            return Verdict.reject("源文件不存在");
        }
        if (input.kind() == ArtifactKind.CHD) {
            return Verdict.mismatch(ArtifactKind.CHD, "这已经是 CHD，不需要再转一次");
        }
        if (input.kind() != spec.input()) {
            return Verdict.mismatch(input.kind());
        }
        if (!chdman.available()) {
            return Verdict.reject("未检测到 chdman，无法生成 CHD（可配置 gamelist.chdman.path）");
        }
        // 裸 BIN 没有轨道表时，chdman 会产出一个 0 轨空壳且退出码仍为 0
        if (needsIndex() && !hasIndexFile(input)) {
            return Verdict.reject("缺少 cue 轨道表：裸 BIN 交给 chdman 只会得到 0 轨空镜像");
        }
        // cue 里的音轨是 APE/FLAC 时，得先靠 ffmpeg 还原成 WAV
        if (chdman.needsAudioTool(input) && !chdman.audioToolReady()) {
            return Verdict.reject("音轨是 APE/FLAC，需要 ffmpeg 才能还原成 WAV"
                    + "（可配置 gamelist.ffmpeg.path）");
        }
        return Verdict.accept();
    }

    @Override
    public Analysis analyze(DiscArtifact input) {
        Path entry = Chdman.indexOrSelf(input);
        long size = input.totalBytes() > 0 ? input.totalBytes() : sizeOf(input.path());
        Analysis.Builder builder = Analysis.builder(spec.input())
                .totalBytes(size)
                .sectorSize(input.sectorSize())
                .fact("entry", entry == null ? "输入自身" : entry.getFileName().toString())
                .fact("chdman", chdman.version().isBlank() ? "已就绪" : chdman.version())
                .summary("交给 chdman createcd，轨道表来自 "
                        + (entry == null || entry.equals(input.path())
                        ? "输入文件自身" : entry.getFileName().toString()));

        if (input.kind() == ArtifactKind.ISO_2048) {
            builder.warn("2048 扇区输入：chdman 会补齐同步头与 EDC/ECC，"
                    + "日后抽回得到的是 2352 镜像，字节不等于原 ISO");
        }
        if (input.related().size() > 1) {
            builder.fact("attachments", String.valueOf(input.related().size()));
        }
        if (chdman.needsAudioTool(input)) {
            builder.fact("audio", "APE/FLAC 音轨由 ffmpeg 现场还原为 WAV");
        }
        // 载荷是 ECM 时先解包成完整 2352 镜像，临时目录要放得下一个整盘
        String payload = chdman.payloadNote(input);
        if (!payload.isEmpty()) {
            builder.fact("payload", payload);
        }
        if (payload.contains("ECM")) {
            builder.warn("载荷是 ECM：chdman 前先解包成 2352 镜像，临时目录需要一个整盘大小的空闲空间");
        }
        return builder.build();
    }

    @Override
    public DiscArtifact convert(DiscArtifact input, Analysis analysis, Path output, ProgressSink progress)
            throws IOException {
        Path part = AtomicOutput.partOf(output);
        AtomicOutput.discard(part);
        try {
            Chdman.Result result = chdman.createcd(input, part, progress);
            AtomicOutput.commit(part, output, -1);
            long bytes = Files.size(output);
            String detail = result.tracks() > 0 ? result.tracks() + " 轨 · CHD" : "CHD";
            return new DiscArtifact(ArtifactKind.CHD, output, bytes, 0, detail,
                    List.of(input.path()), null);
        } catch (IOException | RuntimeException e) {
            AtomicOutput.discard(part);
            throw e;
        }
    }

    /** 索引文件是否真的存在（{@code indexOrSelf} 在找不到时会退回数据文件） */
    private static boolean hasIndexFile(DiscArtifact input) {
        Path index = Chdman.indexOrSelf(input);
        if (index == null) {
            return false;
        }
        String name = index.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return "cue".equals(ext) || "gdi".equals(ext) || "toc".equals(ext);
    }

    private static long sizeOf(Path path) {
        try {
            return path == null ? 0L : Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }
}
