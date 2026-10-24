package com.gamelist.variant;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CUE 轨道表：把“文件 + 轨道 + 索引”解析成结构化事实。
 * <p>
 * cue 本身不是流水线节点，它只是索引；真正的镜像是它引用的 bin。
 * 单文件里也可能塞了数据轨 + 音轨，只有解析 CUE 才能发现。
 */
public record CueSheet(Path cueFile, List<Track> tracks) {

    /**
     * @param mode        MODE1 / MODE2 / AUDIO / CDI
     * @param sectorSize  该轨道声明的扇区大小
     * @param fileName    TRACK 所属的 FILE
     * @param startSector INDEX 01 的起始扇区（相对该文件）
     * @param index01     是否解析到 INDEX 01
     */
    public record Track(int number, String mode, int sectorSize, String fileName, long startSector,
                        boolean index01) {
        public boolean audio() {
            return "AUDIO".equalsIgnoreCase(mode);
        }

        public boolean data() {
            return !audio();
        }
    }

    public CueSheet {
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }

    public List<Track> dataTracks() {
        List<Track> result = new ArrayList<>();
        for (Track track : tracks) {
            if (track.data()) {
                result.add(track);
            }
        }
        return result;
    }

    public List<Track> audioTracks() {
        List<Track> result = new ArrayList<>();
        for (Track track : tracks) {
            if (track.audio()) {
                result.add(track);
            }
        }
        return result;
    }

    /** 第一条数据轨 */
    public Track firstDataTrack() {
        List<Track> data = dataTracks();
        return data.isEmpty() ? null : data.get(0);
    }

    public Path resolve(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        Path parent = cueFile.toAbsolutePath().getParent();
        return parent != null ? parent.resolve(fileName) : Path.of(fileName);
    }

    public String summary() {
        Track first = firstDataTrack();
        String mode = first == null ? "无数据轨" : first.mode() + "/" + first.sectorSize();
        int audio = audioTracks().size();
        int data = dataTracks().size();
        if (audio > 0) {
            return mode + "，" + data + " 条数据轨 + " + audio + " 条音轨";
        }
        return mode + "，" + data + " 条数据轨";
    }

    public static CueSheet parse(Path cue) throws IOException {
        List<String> lines = readLines(cue);
        List<Track> tracks = new ArrayList<>();
        String currentFile = null;
        Integer number = null;
        String mode = null;
        int sectorSize = 0;
        long startSector = -1;
        boolean index01 = false;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.startsWith("FILE ")) {
                if (number != null) {
                    tracks.add(build(number, mode, sectorSize, currentFile, startSector, index01));
                    number = null;
                    index01 = false;
                }
                currentFile = extractFileName(line);
            } else if (upper.startsWith("TRACK ")) {
                if (number != null) {
                    tracks.add(build(number, mode, sectorSize, currentFile, startSector, index01));
                    index01 = false;
                }
                String[] parts = line.split("\\s+");
                number = parseInt(parts.length > 1 ? parts[1] : null, 0);
                String declared = parts.length > 2 ? parts[2].toUpperCase(Locale.ROOT) : "";
                int slash = declared.indexOf('/');
                mode = slash > 0 ? declared.substring(0, slash) : declared;
                sectorSize = slash > 0 ? parseInt(declared.substring(slash + 1), 0) : 0;
                if (sectorSize <= 0) {
                    sectorSize = "AUDIO".equals(mode) ? 2352 : 0;
                }
                startSector = -1;
            } else if (upper.startsWith("INDEX ")) {
                String[] parts = line.split("\\s+");
                // INDEX 后面可能是 01，也可能是不带前导零的 1；INDEX 00 是前导空白，不是轨道起点
                if (parts.length >= 3 && parseInt(parts[1], -1) == 1) {
                    startSector = frames(parts[2]);
                    index01 = true;
                }
            }
        }
        if (number != null) {
            tracks.add(build(number, mode, sectorSize, currentFile, startSector, index01));
        }
        return new CueSheet(cue, tracks);
    }

    private static Track build(int number, String mode, int sectorSize, String fileName,
                               long startSector, boolean index01) {
        return new Track(number,
                mode == null || mode.isBlank() ? "MODE1" : mode,
                sectorSize > 0 ? sectorSize : 2352,
                fileName,
                Math.max(startSector, 0),
                index01);
    }

    /**
     * 读 cue 的文本行。cue 没有统一编码：汉化组常用 ANSI(GBK)，也有人存成 UTF-8。
     * <p>
     * 按“UTF-8 严格 → GBK → 系统默认 → ISO-8859-1”依次尝试，哪种能完整解码就用哪种；
     * 这样 FILE 行里的中文文件名不会变成伪字符，后面的 resolve 才能找到真实的 bin。
     */
    private static List<String> readLines(Path cue) throws IOException {
        byte[] bytes = Files.readAllBytes(cue);
        for (Charset charset : new Charset[]{StandardCharsets.UTF_8, gbk(), Charset.defaultCharset()}) {
            String text = decodeStrict(bytes, charset);
            if (text != null) {
                return List.of(text.split("\\R"));
            }
        }
        return List.of(new String(bytes, StandardCharsets.ISO_8859_1).split("\\R"));
    }

    private static Charset gbk() {
        try {
            return Charset.forName("GB18030");
        } catch (RuntimeException e) {
            return StandardCharsets.ISO_8859_1;
        }
    }

    /** 严格解码；遇到非法字节返回 null，不做替换字符兼容 */
    private static String decodeStrict(byte[] bytes, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /** mm:ss:ff → 扇区数（75 帧/秒） */
    private static long frames(String value) {
        if (value == null) {
            return 0;
        }
        String[] parts = value.trim().split(":");
        try {
            long minutes = parts.length > 0 ? Long.parseLong(parts[0].trim()) : 0;
            long seconds = parts.length > 1 ? Long.parseLong(parts[1].trim()) : 0;
            long frames = parts.length > 2 ? Long.parseLong(parts[2].trim()) : 0;
            return minutes * 60 * 75 + seconds * 75 + frames;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String extractFileName(String line) {
        int first = line.indexOf('"');
        int second = first >= 0 ? line.indexOf('"', first + 1) : -1;
        if (first >= 0 && second > first) {
            return line.substring(first + 1, second);
        }
        String[] parts = line.split("\\s+");
        return parts.length >= 2 ? parts[1] : null;
    }
}
