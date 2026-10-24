package com.gamelist.variant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.gamelist.variant.ciso.CisoReader;

/**
 * 镜像探测：把磁盘上的文件归一化成 {@link DiscArtifact}。
 * <p>
 * 判断顺序是“魔数 → 结构 → 扩展名”，扩展名只在内容无法自证时作为提示。
 * 这样 .bin 里装的其实是 ISO、.iso 里装的其实是 CSO 这一类冒充都能被识别出来。
 * <p>
 * 探测只是路由建议，最终签字的是每个模块的 {@link ConversionModule#verify}。
 */
@Component
public class DiscImageProbe {

    private static final Logger logger = LoggerFactory.getLogger(DiscImageProbe.class);

    public DiscArtifact probe(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return DiscArtifact.unknown(file);
        }
        try {
            long size = Files.size(file);
            if (size <= 0) {
                return DiscArtifact.unknown(file);
            }

            // 1. 容器魔数：这些格式自证性最强，先判
            if (ImageEvidence.isCiso(file)) {
                return probeCiso(file);
            }
            if (ImageEvidence.isZiso(file)) {
                return DiscArtifact.of(ArtifactKind.ZSO, file, size, 0, "ZSO 暂不支持");
            }
            if (ImageEvidence.isChd(file)) {
                return DiscArtifact.of(ArtifactKind.CHD, file, size, 0, "CHD 暂不支持");
            }
            if (ImageEvidence.isEcm(file)) {
                return probeEcm(file, size);
            }
            if (ImageEvidence.isPbp(file)) {
                return DiscArtifact.of(ArtifactKind.PBP, file, size, 0, "PBP 暂不支持");
            }
            if (ImageEvidence.isNrg(file)) {
                return DiscArtifact.of(ArtifactKind.NRG, file, size, 0, "Nero 镜像");
            }
            if (ImageEvidence.isZip(file) || ImageEvidence.isSevenZ(file)) {
                return DiscArtifact.of(ArtifactKind.ARCHIVE, file, size, 0, archiveDetail(file));
            }

            // 2. 轨道索引文件是文本或索引用，不是扇区镜像；必须先于扇区判定
            String ext = extensionOf(file);
            if ("cue".equals(ext)) {
                return probeCue(file);
            }
            if ("gdi".equals(ext) || ImageEvidence.isGdi(file) && "".equals(ext)) {
                return probeGdi(file);
            }
            if ("toc".equals(ext) || ImageEvidence.isToc(file)) {
                return probeToc(file);
            }

            // 3. 2352 原始镜像：同步头比尺寸可靠
            if (size % ImageEvidence.SECTOR_2352 == 0 && ImageEvidence.hasRaw2352Sync(file)) {
                return probeRaw2352(file, size);
            }

            // 4. 2048 扇区镜像
            if (size % ImageEvidence.SECTOR_2048 == 0) {
                if (ImageEvidence.hasIsoPvd2048(file)) {
                    String volumeId = ImageEvidence.isoVolumeId(file);
                    return DiscArtifact.of(ArtifactKind.ISO_2048, file, size, ImageEvidence.SECTOR_2048,
                            volumeId.isBlank() ? "ISO9660 · 单数据轨" : "ISO9660 · " + volumeId);
                }
                if ("iso".equalsIgnoreCase(extensionOf(file))) {
                    return DiscArtifact.of(ArtifactKind.ISO_2048, file, size, ImageEvidence.SECTOR_2048,
                            "未找到卷描述符");
                }
            }

            return DiscArtifact.unknown(file);
        } catch (Exception e) {
            logger.debug("镜像探测失败: {} ({})", file, e.getMessage());
            return DiscArtifact.unknown(file);
        }
    }

    /**
     * 只按扩展名路由的轻量判定：不读文件头，供平台批量盘点使用。
     * <p>
     * 比 {@link #probe(Path)} 粗一档（.bin/.cue 一律当 2352 原始镜像），
     * 但足够回答“这个游戏还缺不缺某个格式”。真生成时仍走 {@link #probe(Path)} 校准。
     */
    public DiscArtifact probeByExtension(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return DiscArtifact.unknown(file);
        }
        long size = sizeOf(file);
        String ext = extensionOf(file);
        ArtifactKind kind = switch (ext) {
            case "iso" -> ArtifactKind.ISO_2048;
            case "bin", "cue" -> ArtifactKind.RAW_2352;
            case "gdi" -> ArtifactKind.GDI;
            case "nrg" -> ArtifactKind.NRG;
            case "toc" -> ArtifactKind.TOC;
            case "cso" -> ArtifactKind.CSO;
            case "zso" -> ArtifactKind.ZSO;
            case "ecm" -> ArtifactKind.ECM;
            case "chd" -> ArtifactKind.CHD;
            case "pbp" -> ArtifactKind.PBP;
            case "zip", "7z" -> ArtifactKind.ARCHIVE;
            default -> ArtifactKind.UNKNOWN;
        };
        if (kind == ArtifactKind.UNKNOWN) {
            return new DiscArtifact(ArtifactKind.UNKNOWN, file, size, 0,
                    ext.isBlank() ? "无扩展名" : "扩展名 ." + ext + " 无法判定", List.of(), null);
        }
        return DiscArtifact.of(kind, file, size, 0, "按扩展名推断");
    }

    private DiscArtifact probeCiso(Path file) {
        try {
            CisoReader.Header header = CisoReader.readHeader(file);
            return DiscArtifact.of(ArtifactKind.CSO, file, header.totalBytes(),
                    ImageEvidence.SECTOR_2048, "块 " + header.blockSize() + " · v" + header.version());
        } catch (IOException e) {
            // 魔数对但头部坏了：仍标成 CSO，让模块在分析阶段给出具体损坏原因
            logger.debug("CSO 头部解析失败: {} ({})", file, e.getMessage());
            return new DiscArtifact(ArtifactKind.CSO, file, sizeOf(file), 0,
                    "头部损坏: " + e.getMessage(), List.of(), null);
        }
    }

    /**
     * 裸 ECM：只有容器头能确认形态。解包才能读到卷描述符，探测阶段不做这件事，
     * 真正的解码结果由 {@code ecm2bin} 模块在分析/转换阶段给出。
     */
    private DiscArtifact probeEcm(Path file, long size) {
        return DiscArtifact.of(ArtifactKind.ECM, file, size, 0, "ECM 容器 · 解包后还原 2352 扇区");
    }

    /** 2352 原始镜像：抽样看数据扇区占比，发现单文件里混入的音轨 */
    private DiscArtifact probeRaw2352(Path file, long size) {
        String mode = ImageEvidence.raw2352Mode(file);
        double ratio = ImageEvidence.dataSectorRatio(file, ImageEvidence.SECTOR_2352);
        if (ratio <= 0) {
            return DiscArtifact.of(ArtifactKind.RAW_2352_MULTI, file, size, ImageEvidence.SECTOR_2352,
                    "无数据扇区，疑似纯音轨");
        }
        if (ratio < 0.9) {
            return DiscArtifact.of(ArtifactKind.RAW_2352_MULTI, file, size, ImageEvidence.SECTOR_2352,
                    mode + "/2352 · 含音轨（数据扇区约 " + Math.round(ratio * 100) + "%）");
        }
        return DiscArtifact.of(ArtifactKind.RAW_2352, file, size, ImageEvidence.SECTOR_2352,
                mode + "/2352 · 单数据轨");
    }

    private DiscArtifact probeCue(Path cue) throws IOException {
        CueSheet sheet = CueSheet.parse(cue);
        List<CueAssets.Asset> assets = CueAssets.resolve(sheet);
        Path dataBin = null;
        for (CueAssets.Asset asset : assets) {
            if (!asset.audio() && asset.present()) {
                dataBin = asset.file();
                break;
            }
        }
        if (dataBin == null) {
            return DiscArtifact.of(ArtifactKind.UNKNOWN, cue, sizeOf(cue), 0, "CUE 未找到可用的数据轨");
        }
        List<Path> related = relatedFiles(sheet, assets);
        CueSheet.Track first = sheet.firstDataTrack();
        String mode = first == null ? "MODE2/2352" : first.mode() + "/" + first.sectorSize();
        int dataTracks = sheet.dataTracks().size();
        int audioTracks = sheet.audioTracks().size();
        int sectorSize = first == null ? ImageEvidence.SECTOR_2352 : first.sectorSize();
        // 载荷被压缩过（ECM/APE）时写进说明：用户能一眼看出这条产线要多跑两道解压
        String payload = CueAssets.payloadNote(assets);

        if (audioTracks > 0 || dataTracks > 1) {
            String detail = audioTracks > 0
                    ? mode + " · " + dataTracks + " 数据轨 + " + audioTracks + " 音轨"
                    : mode + " · " + dataTracks + " 数据轨";
            return new DiscArtifact(ArtifactKind.RAW_2352_MULTI, dataBin, sizeOf(dataBin), sectorSize,
                    payload.isEmpty() ? detail : detail + " · " + payload, related, null);
        }
        String detail = mode + " · 单数据轨";
        return new DiscArtifact(ArtifactKind.RAW_2352, dataBin, sizeOf(dataBin), sectorSize,
                payload.isEmpty() ? detail : detail + " · " + payload, related, null);
    }

    private List<Path> relatedFiles(CueSheet sheet, List<CueAssets.Asset> assets) {
        Set<Path> files = new LinkedHashSet<>();
        files.add(sheet.cueFile());
        for (CueAssets.Asset asset : assets) {
            if (asset.present()) {
                files.add(asset.file());
            }
        }
        return new ArrayList<>(files);
    }

    /** GDI：首行轨道数，后续行 `track lba type sectorsize filename offset`，type=4 是数据轨 */
    private DiscArtifact probeGdi(Path gdi) {
        String[] lines = ImageEvidence.textLines(gdi, 16384);
        if (lines.length == 0) {
            return DiscArtifact.unknown(gdi);
        }
        Set<Path> related = new LinkedHashSet<>();
        related.add(gdi);
        int dataTracks = 0;
        int audioTracks = 0;
        int sectorSize = 0;
        for (int i = 1; i < lines.length; i++) {
            String[] cols = lines[i].split("\\s+");
            if (cols.length < 5) {
                continue;
            }
            try {
                int type = Integer.parseInt(cols[2]);
                int size = Integer.parseInt(cols[3]);
                if (type == 4) {
                    dataTracks++;
                    if (sectorSize == 0) {
                        sectorSize = size;
                    }
                } else if (type == 0) {
                    audioTracks++;
                }
                // 文件名可能带空格：最后一个字段是 offset，前面从第 5 列起都是文件名
                int end = cols.length;
                try {
                    Integer.parseInt(cols[cols.length - 1]);
                    end = cols.length - 1;
                } catch (NumberFormatException ignored) {
                    // 没有 offset 列，整个后半段都是文件名
                }
                if (end > 4) {
                    StringBuilder name = new StringBuilder(cols[4]);
                    for (int c = 5; c < end; c++) {
                        name.append(' ').append(cols[c]);
                    }
                    Path resolved = gdi.resolveSibling(name.toString().replace("\"", "").trim());
                    if (Files.isRegularFile(resolved)) {
                        related.add(resolved);
                    }
                }
            } catch (NumberFormatException ignored) {
                // 非轨道行（注释、空行）
            }
        }
        String detail = "GD-ROM · " + dataTracks + " 数据轨 + " + audioTracks + " 音轨";
        if (dataTracks == 0 && audioTracks == 0) {
            return new DiscArtifact(ArtifactKind.GDI, gdi, sizeOf(gdi), 0,
                    "GDI 未解析出轨道", List.of(gdi), null);
        }
        return new DiscArtifact(ArtifactKind.GDI, gdi, sizeOf(gdi), sectorSize, detail,
                List.copyOf(related), null);
    }

    /** cdrdao TOC：TRACK 行定轨道数，DATAFILE 行定关联文件 */
    private DiscArtifact probeToc(Path toc) {
        String[] lines = ImageEvidence.textLines(toc, 65536);
        if (lines.length == 0) {
            return DiscArtifact.unknown(toc);
        }
        Set<Path> related = new LinkedHashSet<>();
        related.add(toc);
        int tracks = 0;
        for (String line : lines) {
            if (line.startsWith("TRACK ")) {
                tracks++;
            } else if (line.startsWith("DATAFILE")) {
                int first = line.indexOf('"');
                int last = line.lastIndexOf('"');
                if (first >= 0 && last > first) {
                    Path resolved = toc.resolveSibling(line.substring(first + 1, last));
                    if (Files.isRegularFile(resolved)) {
                        related.add(resolved);
                    }
                }
            }
        }
        return new DiscArtifact(ArtifactKind.TOC, toc, sizeOf(toc), 0,
                "cdrdao · " + tracks + " 轨", List.copyOf(related), null);
    }

    private String archiveDetail(Path file) {
        String format = ImageEvidence.isSevenZ(file) ? "7z" : "zip";
        return format + " 压缩包 · 不是镜像，需要先解压出 ISO/BIN";
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static long sizeOf(Path path) {
        try {
            return path != null && Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }
}
