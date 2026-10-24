package com.gamelist.variant;

import java.nio.file.Path;
import java.util.List;

/**
 * 探测得到的镜像工件：流水线的输入/输出单位。
 *
 * @param kind       工件形态
 * @param path       实际文件
 * @param totalBytes 镜像字节数
 * @param sectorSize 扇区大小，未知为 0
 * @param detail     人类可读的补充说明（如 “MODE2/2352，单数据轨”）
 * @param related    关联文件（cue 引用的 bin、多盘等）
 * @param innerName  预留：压缩包内层条目名（当前压缩包不进流水线）
 */
public record DiscArtifact(ArtifactKind kind,
                           Path path,
                           long totalBytes,
                           int sectorSize,
                           String detail,
                           List<Path> related,
                           String innerName) {

    public DiscArtifact {
        related = related == null ? List.of() : List.copyOf(related);
        detail = detail == null ? "" : detail;
    }

    public static DiscArtifact unknown(Path path) {
        return new DiscArtifact(ArtifactKind.UNKNOWN, path, sizeOf(path), 0, "", List.of(), null);
    }

    public static DiscArtifact of(ArtifactKind kind, Path path, long totalBytes, int sectorSize, String detail) {
        return new DiscArtifact(kind, path, totalBytes, sectorSize, detail, List.of(), null);
    }

    /** 探测结论被模块修正时使用 */
    public DiscArtifact withKind(ArtifactKind newKind, String newDetail) {
        return new DiscArtifact(newKind, path, totalBytes, sectorSize,
                newDetail == null ? detail : newDetail, related, innerName);
    }

    /** 界面展示用：ISO（2048 扇区）（单数据轨）· 1.2 GB */
    public String describe() {
        StringBuilder sb = new StringBuilder(kind.display());
        if (detail != null && !detail.isBlank()) {
            sb.append("（").append(detail).append("）");
        }
        if (totalBytes > 0) {
            sb.append(" · ").append(formatBytes(totalBytes));
        }
        return sb.toString();
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
        }
        return String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    private static long sizeOf(Path path) {
        try {
            return path != null && java.nio.file.Files.isRegularFile(path)
                    ? java.nio.file.Files.size(path) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
