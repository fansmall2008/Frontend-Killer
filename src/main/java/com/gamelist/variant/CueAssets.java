package com.gamelist.variant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * CUE 载荷解析：把 cue 里声明的文件名对回磁盘上的真实文件。
 * <p>
 * 这套 PSX ECM 备份是“名字对不上”的：cue 写的是 Redump 原始名（{@code 游戏名.bin}），
 * 磁盘上却叫 {@code Track 01.bin.ecm}，音轨则压成 {@code 游戏名 (Track 2) [编号].ape}。
 * cue 本身没错，错的是文件被改名并压缩过，所以这里按“声明的名字”回头找，而不是拿磁盘名去撞。
 * <p>
 * 认领顺序：字面同名 → 追加压缩后缀（{@code X.bin.ecm}）→ 换扩展名（{@code X.ecm / X.ape}）→
 * 按 FILE 序号认领 {@code Track NN.bin.ecm} → 只剩一个候选时直接认领。
 */
public final class CueAssets {

    /** cue 引用的候选载荷扩展名 */
    private static final Set<String> PAYLOAD_EXTENSIONS =
            Set.of("bin", "img", "iso", "raw", "ecm", "ape", "flac", "wav");

    /** 认领时拿来当“整盘镜像”的体积下限；低于它的多半是 BIOS 字库这类附带文件 */
    private static final long MIN_DISC_BYTES = 2L << 20;

    /** 载荷的落盘形态 */
    public enum Form {
        /** chdman 能直接读 */
        RAW,
        /** ECM 容器，要先解包成 2352 镜像 */
        ECM,
        /** APE/FLAC 压缩音轨，要先转成 WAV */
        AUDIO;

        public static Form of(Path file) {
            String ext = extensionOf(file);
            return switch (ext) {
                case "ecm" -> ECM;
                case "ape", "flac" -> AUDIO;
                default -> RAW;
            };
        }
    }

    /**
     * @param declared cue 里写的文件名
     * @param file     磁盘上认领到的文件，找不到为 null
     * @param audio    这个 FILE 下的轨道是否全是音轨
     * @param form     载荷形态，决定要不要先解压/转码
     */
    public record Asset(String declared, Path file, boolean audio, Form form) {

        public boolean present() {
            return file != null && Files.isRegularFile(file);
        }

        /** chdman 读不了，必须先加工 */
        public boolean needsWork() {
            return form != Form.RAW;
        }
    }

    private CueAssets() {
    }

    public static List<Asset> resolve(CueSheet sheet) {
        if (sheet == null) {
            return List.of();
        }
        List<String> declared = declaredNames(sheet);
        if (declared.isEmpty()) {
            return List.of();
        }
        Path dir = sheet.cueFile() == null ? null : sheet.cueFile().toAbsolutePath().getParent();
        Map<String, Path> pool = payloads(dir);
        Map<String, Boolean> audio = audioByName(sheet);
        Set<String> claimed = new LinkedHashSet<>();

        List<Asset> assets = new ArrayList<>(declared.size());
        for (int i = 0; i < declared.size(); i++) {
            String name = declared.get(i);
            Path file = match(name, i + 1, pool, claimed);
            if (file != null) {
                claimed.add(key(file));
            }
            assets.add(new Asset(name, file, Boolean.TRUE.equals(audio.get(name)), Form.of(file)));
        }
        return assets;
    }

    /** 有载荷需要解压或转码 */
    public static boolean needsWork(List<Asset> assets) {
        for (Asset asset : assets) {
            if (asset.needsWork()) {
                return true;
            }
        }
        return false;
    }

    /** 载荷形态摘要，用于探测结果的说明文字 */
    public static String payloadNote(List<Asset> assets) {
        boolean ecm = false;
        boolean audio = false;
        for (Asset asset : assets) {
            ecm |= asset.form() == Form.ECM;
            audio |= asset.form() == Form.AUDIO;
        }
        if (ecm && audio) {
            return "ECM 数据轨 + APE 音轨";
        }
        if (ecm) {
            return "ECM 载荷";
        }
        if (audio) {
            return "APE 音轨";
        }
        return "";
    }

    /** cue 里出现过的 FILE 声明，按出现顺序去重 */
    private static List<String> declaredNames(CueSheet sheet) {
        List<String> names = new ArrayList<>();
        for (CueSheet.Track track : sheet.tracks()) {
            String name = track.fileName();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (names.isEmpty() || !names.get(names.size() - 1).equals(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /** 每个 FILE 下的轨道是否全是音轨（混装的数据轨按数据轨算） */
    private static Map<String, Boolean> audioByName(CueSheet sheet) {
        Map<String, Boolean> audio = new LinkedHashMap<>();
        for (CueSheet.Track track : sheet.tracks()) {
            String name = track.fileName();
            if (name == null || name.isBlank()) {
                continue;
            }
            audio.merge(name, track.audio(), (existing, current) -> existing && current);
        }
        return audio;
    }

    /** 同目录下像载荷的文件，键是文件名小写（SMB 共享上大小写不敏感，统一折一下） */
    private static Map<String, Path> payloads(Path dir) {
        Map<String, Path> pool = new LinkedHashMap<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return pool;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> PAYLOAD_EXTENSIONS.contains(extensionOf(path)))
                    .sorted()
                    .forEach(path -> pool.put(key(path), path));
        } catch (Exception e) {
            // 目录读不了就当作没有载荷，交给调用方报“找不到数据轨”
        }
        return pool;
    }

    private static Path match(String declared, int ordinal, Map<String, Path> pool, Set<String> claimed) {
        String base = baseName(declared);
        String stem = stripExtension(base);
        String[] candidates = {
                base,
                base + ".ecm",
                base + ".ape",
                base + ".flac",
                stem + ".ecm",
                stem + ".ape",
                stem + ".flac",
                stem + ".bin.ecm",
                String.format(Locale.ROOT, "track %02d.bin.ecm", ordinal),
                String.format(Locale.ROOT, "track%02d.bin.ecm", ordinal),
        };
        for (String candidate : candidates) {
            Path file = pool.get(candidate.toLowerCase(Locale.ROOT));
            if (file != null && !claimed.contains(key(file))) {
                return file;
            }
        }
        // 名字完全对不上时：只剩一个就直接给它，多个则挑最像整盘镜像的那个
        List<Path> free = new ArrayList<>();
        for (Map.Entry<String, Path> entry : pool.entrySet()) {
            if (!claimed.contains(entry.getKey())) {
                free.add(entry.getValue());
            }
        }
        if (free.size() == 1) {
            return free.get(0);
        }
        return largestDisc(free);
    }

    /**
     * 多个候选时按“像不像整盘镜像”挑：排除压缩音轨，只要 2352 对齐且不小于 2 MB，
     * 取体积最大的那个。汉化包里常见的 BIOS 字库 bin 体积小且不按扇区对齐，会被排除。
     */
    private static Path largestDisc(List<Path> candidates) {
        Path best = null;
        long bestSize = 0;
        for (Path candidate : candidates) {
            if (Form.of(candidate) == Form.AUDIO) {
                continue;
            }
            long size = sizeOf(candidate);
            if (size < MIN_DISC_BYTES || size % ImageEvidence.SECTOR_2352 != 0) {
                continue;
            }
            if (size > bestSize) {
                bestSize = size;
                best = candidate;
            }
        }
        return best;
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String key(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT);
    }

    private static String baseName(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String extensionOf(Path file) {
        if (file == null) {
            return "";
        }
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
