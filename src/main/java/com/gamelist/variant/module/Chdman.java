package com.gamelist.variant.module;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gamelist.variant.ConversionModule;
import com.gamelist.variant.CueAssets;
import com.gamelist.variant.CueSheet;
import com.gamelist.variant.DiscArtifact;
import com.gamelist.variant.ExternalToolSettings;
import com.gamelist.variant.audio.Ffmpeg;
import com.gamelist.variant.ecm.EcmReader;

/**
 * chdman 外部进程封装：定位可执行文件、跑命令、解析进度、把路径桥接到 ASCII 目录。
 * <p>
 * 它不解释镜像内容，只负责“把输入喂进去、把 chdman 说的话原样带回来”。
 * 产出是否合心意由调用方按自己的标准判断。
 * <p>
 * 唯一必须兜住的是路径：Windows 版 chdman 走 ANSI 接口，任何非 ASCII 的路径片段
 * 都会被按错误代码页解读，必须先把文件搬到纯 ASCII 目录再转。
 */
@Component
public class Chdman {

    private static final Logger logger = LoggerFactory.getLogger(Chdman.class);

    /** 进度行形如 `Compressing, 40.0% complete... (ratio=85.6%)`，写在 stderr */
    private static final Pattern PROGRESS = Pattern.compile("(\\d+[.,]\\d+)\\s*%\\s*complete");

    /** `chdman info` 里每个轨道一行：`TRACK:1 TYPE:AUDIO ...` */
    private static final Pattern TRACK = Pattern.compile("(?m)^\\s*TRACK:\\d+");

    /** 文件名里的轨道号：`Track 01.bin`、`(Track 2) xxx.ape`、`track3` */
    private static final Pattern TRACK_IN_NAME = Pattern.compile("(?i)track[\\s_\\-]*(\\d{1,2})");

    /** 索引里的引号内容，cue/toc 的文件引用长这样 */
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]{1,255})\"");

    /** cue 的 FILE 行：引号里是载荷名，后面跟类型（BINARY / WAVE） */
    private static final Pattern FILE_LINE =
            Pattern.compile("(?i)^(\\s*FILE\\s+\")([^\"]*)(\"\\s+)([A-Za-z0-9]+)(\\s*)$");

    /** 单次转换上限；多轨大容量盘也够用 */
    private static final long TIMEOUT_MS = 30L * 60L * 1000L;

    /** 单条流最多保留的字符数，防止异常输出撑爆内存 */
    private static final int MAX_CAPTURE = 64 * 1024;

    /** 并发压缩线程数，给 Web 线程留余量 */
    private static final String PROCESSORS =
            String.valueOf(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));

    /** 配置文件里的路径（gamelist.chdman.path / CHDMAN_PATH），界面设置为空时才用它 */
    private final String defaultPath;
    private final Ffmpeg ffmpeg;
    private final ExternalToolSettings toolSettings;

    private volatile Path executable;
    private volatile boolean located;
    private volatile String version = "";
    private volatile String sourceLabel = "";

    public Chdman(@Value("${gamelist.chdman.path:}") String configuredPath,
                  Ffmpeg ffmpeg,
                  ExternalToolSettings toolSettings) {
        this.defaultPath = configuredPath == null ? "" : configuredPath.trim();
        this.ffmpeg = ffmpeg;
        this.toolSettings = toolSettings;
    }

    public boolean available() {
        return locate() != null;
    }

    /** 按“配置 → 环境变量 → PATH → 程序目录”的顺序找可执行文件，结果只探测一次 */
    public synchronized Path locate() {
        if (located) {
            return executable;
        }
        located = true;
        try {
            executable = resolveExecutable();
        } catch (RuntimeException e) {
            // 探测只是辅助信息，不能让一条异常路径把接口整成 500
            executable = null;
            sourceLabel = "探测失败：" + e.getMessage();
            logger.warn("探测 chdman 失败", e);
        }
        if (executable == null) {
            logger.info("未找到 chdman，CHD 产线不可用（可在工具箱 → 外部工具里指定路径，或配置 gamelist.chdman.path）{}",
                    sourceLabel.isEmpty() ? "" : "｜" + sourceLabel);
        } else {
            logger.info("chdman 可用: {}｜来源: {}", executable, sourceLabel);
        }
        return executable;
    }

    /** 路径改过之后重新探测，不必重启 */
    public synchronized void refresh() {
        located = false;
        executable = null;
        version = "";
        sourceLabel = "";
    }

    /** 当前生效路径的来源（界面设置 / gamelist.chdman.path / 环境变量 / PATH / 某个目录） */
    public String source() {
        locate();
        return sourceLabel;
    }

    public synchronized String version() {
        if (!version.isEmpty() || locate() == null) {
            return version;
        }
        try {
            Result result = execRaw(executable, List.of("--help"), null, 5_000L, 0L,
                    ConversionModule.ProgressSink.NOOP);
            String first = firstLine(result.stdout());
            if (first.isEmpty()) {
                first = firstLine(result.stderr());
            }
            version = first.replace("chdman - ", "").trim();
        } catch (IOException e) {
            logger.debug("读取 chdman 版本失败: {}", e.getMessage());
        }
        return version;
    }

    /**
     * 把输入转成 CHD 写到 output。
     * <p>
     * 输入目录和产物目录都不必是 ASCII，方法内部会桥接；调用方只需要保证父目录存在。
     *
     * @param output 最终产物路径（调用方通常传 .part）
     */
    public Result createcd(DiscArtifact input, Path output, ConversionModule.ProgressSink progress)
            throws IOException {
        Path tool = locate();
        if (tool == null) {
            throw new IOException("未找到 chdman，无法生成 CHD");
        }
        Stage stage = Stage.prepare(input, ffmpeg);
        try {
            List<String> args = List.of(
                    "createcd",
                    "-i", stage.inputArg(),
                    "-o", stage.outputArg(),
                    "-f",
                    "-np", PROCESSORS);
            Result result = execRaw(tool, args, stage.workDir(), TIMEOUT_MS, input.totalBytes(), progress);
            if (result.exit() != 0) {
                throw new IOException("chdman 转换失败（退出码 " + result.exit() + "）: " + result.tail());
            }
            if (!Files.isRegularFile(stage.stagedOutput()) || Files.size(stage.stagedOutput()) <= 0) {
                throw new IOException("chdman 未产出文件: " + result.tail());
            }
            // 退出码为 0 并不代表成功：裸 BIN 无轨道表时会产出一个 0 轨的空壳。
            // 唯一能识破空壳的是轨道表，就在临时目录里回读，不必再折腾一次路径桥。
            int tracks = countTracks(tool, stage.stagedOutput());
            if (tracks == 0) {
                throw new IOException("chdman 产出 0 轨镜像：输入缺少轨道表（cue/gdi/toc），"
                        + "裸 BIN 需要配套索引文件");
            }
            logger.debug("chdman 完成: {} → {} 字节｜{} 轨｜{}", input.path().getFileName(),
                    Files.size(stage.stagedOutput()), tracks, result.summary());
            stage.publish(output);
            return new Result(result.exit(), result.stdout(), result.stderr(), tracks);
        } finally {
            stage.close();
        }
    }
    
    /**
     * 回读轨道数：`chdman info` 里每个轨道一行 `TRACK:n`。
     *
     * @return 轨道数；读取失败返回 -1（不据此判失败，避免环境差异误杀）
     */
    private int countTracks(Path tool, Path chd) {
        try {
            Result info = execRaw(tool, List.of("info", "-i", chd.toAbsolutePath().toString()),
                    null, 30_000L, 0L, ConversionModule.ProgressSink.NOOP);
            String text = info.stdout() + "\n" + info.stderr();
            Matcher matcher = TRACK.matcher(text);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            return count;
        } catch (IOException e) {
            logger.debug("读取 CHD 轨道信息失败: {}", e.getMessage());
            return -1;
        }
    }

    // ------------------------------------------------------------------ 进程执行

    private Result execRaw(Path tool, List<String> args, Path workDir, long timeoutMs,
                           long totalBytes, ConversionModule.ProgressSink progress) throws IOException {
        List<String> command = new ArrayList<>(args.size() + 1);
        command.add(tool.toString());
        command.addAll(args);

        ProcessBuilder builder = new ProcessBuilder(command);
        if (workDir != null) {
            builder.directory(workDir.toFile());
        }
        Process process = builder.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread outPump = pump(process.getInputStream(), stdout, null, 0L);
        // 进度写在 stderr，stdout 里是轨道数、时长这类摘要
        Thread errPump = pump(process.getErrorStream(), stderr, progress, totalBytes);

        try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("chdman 超时（超过 " + (timeoutMs / 60000L) + " 分钟）");
            }
            outPump.join(2_000L);
            errPump.join(2_000L);
            return new Result(process.exitValue(), stdout.toString(), stderr.toString(), -1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("chdman 执行被中断", e);
        } finally {
            process.destroyForcibly();
        }
    }

    private static Thread pump(InputStream stream, StringBuilder sink,
                               ConversionModule.ProgressSink progress, long totalBytes) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (sink) {
                        if (sink.length() < MAX_CAPTURE) {
                            sink.append(line).append('\n');
                        }
                    }
                    if (progress != null && totalBytes > 0 && line.indexOf('%') >= 0) {
                        reportProgress(line, progress, totalBytes);
                    }
                }
            } catch (IOException ignored) {
                // 进程被强杀时流会提前中断，属正常路径
            }
        }, "chdman-pump");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void reportProgress(String line, ConversionModule.ProgressSink progress, long totalBytes) {
        Matcher matcher = PROGRESS.matcher(line);
        if (!matcher.find()) {
            return;
        }
        try {
            double percent = Double.parseDouble(matcher.group(1).replace(',', '.'));
            double bounded = Math.max(0, Math.min(100, percent));
            progress.update((long) (totalBytes * bounded / 100.0), totalBytes);
        } catch (NumberFormatException ignored) {
            // 进度行格式变化时跳过，不能因为进度影响转换
        }
    }

    // ------------------------------------------------------------------ 可执行文件定位

    private Path resolveExecutable() {
        String name = isWindows() ? "chdman.exe" : "chdman";

        // 明确写了路径就只认它：填错时报不可用，而不是悄悄换成 PATH 里的另一个版本
        String fromUi = toolSettings.get(ExternalToolSettings.CHDMAN_PATH);
        if (!fromUi.isEmpty()) {
            Path path = probe(fromUi);
            sourceLabel = path != null ? "界面设置" : "界面设置（路径不可用）";
            return path;
        }
        if (!defaultPath.isEmpty()) {
            Path path = probe(defaultPath);
            sourceLabel = path != null ? "gamelist.chdman.path" : "gamelist.chdman.path（路径不可用）";
            return path;
        }
        Path fromEnv = probe(System.getenv("CHDMAN"));
        if (fromEnv != null) {
            sourceLabel = "环境变量 CHDMAN";
            return fromEnv;
        }
        Path fromPathEnv = scanPath(name);
        if (fromPathEnv != null) {
            sourceLabel = "PATH";
            return fromPathEnv;
        }
        Path appDir = Path.of(System.getProperty("user.dir", "."));
        for (String sub : new String[]{"", "tools", "data" + File.separator + "tools", "bin"}) {
            Path dir = sub.isEmpty() ? appDir : appDir.resolve(sub);
            Path candidate = probe(dir.resolve(name).toString());
            if (candidate != null) {
                sourceLabel = dir.toAbsolutePath().toString();
                return candidate;
            }
        }
        sourceLabel = "";
        return null;
    }

    private static Path scanPath(String name) {
        String raw = System.getenv("PATH");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (String dir : raw.split(File.pathSeparator)) {
            if (dir == null || dir.isBlank()) {
                continue;
            }
            // PATH 里偶尔混进用空格拼起来的多目录串，Path.of 会抛 InvalidPathException
            String candidatePath;
            try {
                candidatePath = Path.of(dir.trim(), name).toString();
            } catch (RuntimeException e) {
                continue;
            }
            Path candidate = probe(candidatePath);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static Path probe(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(raw.trim());
            return Files.isRegularFile(path) && Files.isExecutable(path) ? path.toAbsolutePath() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    // ------------------------------------------------------------------ 路径桥

    /**
     * 一次转换的路径准备：决定工作目录、-i 参数、-o 参数，并在需要时把文件搬到 ASCII 目录。
     */
    private static final class Stage implements AutoCloseable {

        private final Path workDir;
        private final String inputArg;
        private final Path stagedOutput;
        private final Path tempDir;

        private Stage(Path workDir, String inputArg, Path stagedOutput, Path tempDir) {
            this.workDir = workDir;
            this.inputArg = inputArg;
            this.stagedOutput = stagedOutput;
            this.tempDir = tempDir;
        }

        /**
         * 准备一次转换的落地路径。
         * <p>
         * 两种情形必须先把文件搬到 ASCII 临时目录：路径里有非 ASCII 片段（chdman 走 ANSI 接口），
         * 或者索引引用的载荷需要加工（ECM 解包、APE 转 WAV、声明的名字与磁盘不一致）。
         */
        static Stage prepare(DiscArtifact input, Ffmpeg ffmpeg) throws IOException {
            // cue/gdi/toc 才是 chdman 的入口，数据 bin 只是被索引的负载
            Path source = indexOrSelf(input);
            Path tempDir = Files.createTempDirectory(asciiTempRoot(), "fk-chd-");
            Path stagedOutput = tempDir.resolve("out.chd");

            if (isAscii(source.toAbsolutePath().toString()) && !needsStaging(source)) {
                // 整条路径都是 ASCII，索引里的名字也与磁盘一致，直接原地转
                return new Stage(source.getParent(), source.getFileName().toString(), stagedOutput, tempDir);
            }
            String stagedInput = "cue".equals(extensionOf(source))
                    ? stageCue(source, tempDir, ffmpeg)
                    : copyToAscii(input, tempDir);
            return new Stage(tempDir, stagedInput, stagedOutput, tempDir);
        }

        Path workDir() {
            return workDir;
        }

        String inputArg() {
            return inputArg;
        }

        Path stagedOutput() {
            return stagedOutput;
        }

        /** 临时目录本身是 ASCII 时给绝对路径，否则给相对名（工作目录就是它） */
        String outputArg() {
            String absolute = stagedOutput.toAbsolutePath().toString();
            return isAscii(absolute) ? absolute : stagedOutput.getFileName().toString();
        }

        /** 把产物挪到目标位置；跨盘或目标含中文都由 Java 处理，与 chdman 无关 */
        void publish(Path target) throws IOException {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try {
                Files.move(stagedOutput, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(stagedOutput, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        @Override
        public void close() {
            if (tempDir == null) {
                return;
            }
            try (Stream<Path> stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // 清理失败不影响主流程
                    }
                });
            } catch (IOException ignored) {
                // 同上
            }
        }
    }

    /**
     * 选出应该交给 chdman 的文件：索引文件优先，没有索引才退回数据文件。
     * <p>
     * {@code DiscImageProbe} 对 cue 的探测结果是数据 bin，cue 在 related 里，
     * 直接传 bin 会让 chdman 丢掉整张轨道表。
     */
    public static Path indexOrSelf(DiscArtifact input) {
        if (input == null) {
            return null;
        }
        Path primary = input.path();
        if (primary == null) {
            return null;
        }
        if (isIndexFile(extensionOf(primary))) {
            return primary;
        }
        for (Path related : input.related()) {
            if (related != null && isIndexFile(extensionOf(related))) {
                return related;
            }
        }
        return primary;
    }

    /** 索引里的载荷能不能原样交给 chdman */
    private static boolean needsStaging(Path source) {
        if (!"cue".equals(extensionOf(source))) {
            return false;
        }
        try {
            for (CueAssets.Asset asset : CueAssets.resolve(CueSheet.parse(source))) {
                if (!asset.present() || asset.needsWork()) {
                    return true;
                }
                // 声明的名字与磁盘不一致：不暂存就没法把 cue 改成正确的文件名
                if (!asset.file().getFileName().toString().equals(baseName(asset.declared()))) {
                    return true;
                }
            }
        } catch (IOException e) {
            // 解析失败就交给暂存阶段去报具体原因
            return true;
        }
        return false;
    }

    /**
     * 索引里的载荷 chdman 读不了：ECM 要先解包成 bin，APE/FLAC 要先还原成 WAV。
     * <p>
     * 这套 PSX ECM 备份的 cue 写的是 Redump 原始名，磁盘上却是 {@code Track 01.bin.ecm}
     * 和 {@code 游戏名 (Track 2) [编号].ape}，所以名字和类型都要一起改写，
     * 否则 chdman 会按 BINARY 去找一个不存在的 bin。
     */
    private static String stageCue(Path cue, Path tempDir, Ffmpeg ffmpeg) throws IOException {
        CueSheet sheet = CueSheet.parse(cue);
        List<CueAssets.Asset> assets = CueAssets.resolve(sheet);
        if (assets.isEmpty()) {
            throw new IOException("cue 里没有可用的 FILE 声明: " + cue.getFileName());
        }

        Map<String, String> names = new LinkedHashMap<>();
        int index = 1;
        for (CueAssets.Asset asset : assets) {
            if (!asset.present()) {
                throw new IOException("cue 引用的文件不存在: " + asset.declared());
            }
            String stem = "t" + String.format(Locale.ROOT, "%02d", index++);
            Path source = asset.file();
            names.put(asset.declared(), switch (asset.form()) {
                case ECM -> stageEcm(source, tempDir, stem);
                case AUDIO -> stageAudio(source, tempDir, stem, ffmpeg);
                case RAW -> stageRaw(source, tempDir, stem);
            });
        }

        byte[] content = Files.readAllBytes(cue);
        for (Map.Entry<String, String> entry : names.entrySet()) {
            content = replaceName(content, entry.getKey(), entry.getValue());
        }
        content = fixFileType(content);
        Path stagedCue = tempDir.resolve("index.cue");
        Files.write(stagedCue, content);
        return stagedCue.getFileName().toString();
    }

    /** ECM 解包成 2352 原始镜像；解包器自带整盘累计 EDC 校验 */
    private static String stageEcm(Path source, Path dir, String stem) throws IOException {
        String name = stem + ".bin";
        logger.debug("解包 ECM: {} → {}", source.getFileName(), name);
        EcmReader.decode(source, dir.resolve(name), ConversionModule.ProgressSink.NOOP);
        return name;
    }

    /** APE/FLAC 音轨还原成 WAV：CD 音轨是无损 PCM，转换不丢任何东西 */
    private static String stageAudio(Path source, Path dir, String stem, Ffmpeg ffmpeg) throws IOException {
        String name = stem + ".wav";
        logger.debug("还原音轨: {} → {}", source.getFileName(), name);
        ffmpeg.toWav(source, dir.resolve(name), sizeOf(source));
        return name;
    }

    /** 已经是 chdman 能读的形态，原样复制 */
    private static String stageRaw(Path source, Path dir, String stem) throws IOException {
        String ext = extensionOf(source);
        String name = stem + (ext.isEmpty() ? "" : "." + ext);
        Files.copy(source, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    /**
     * 暂存后的 WAV 音轨必须按 {@code WAVE} 声明。
     * <p>
     * 原 cue 对音轨写的是 {@code BINARY}（它当时确实是个 2352 的 bin），换成 WAV 后类型不改，
     * chdman 会把 WAV 文件头当成扇区数据去读。
     */
    private static byte[] fixFileType(byte[] content) {
        // ISO-8859-1 是无损单字节映射，可以安全地当字节容器用
        String text = new String(content, StandardCharsets.ISO_8859_1);
        String[] lines = text.split("\n", -1);
        boolean changed = false;
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = FILE_LINE.matcher(lines[i]);
            if (!matcher.matches() || "WAVE".equalsIgnoreCase(matcher.group(4))
                    || !matcher.group(2).toLowerCase(Locale.ROOT).endsWith(".wav")) {
                continue;
            }
            lines[i] = matcher.group(1) + matcher.group(2) + matcher.group(3) + "WAVE" + matcher.group(5);
            changed = true;
        }
        return changed ? String.join("\n", lines).getBytes(StandardCharsets.ISO_8859_1) : content;
    }

    /** cue 里的载荷形态（ECM / APE）；名字对得上、无需加工时返回空串 */
    public String payloadNote(DiscArtifact input) {
        Path index = indexOrSelf(input);
        if (index == null || !"cue".equals(extensionOf(index))) {
            return "";
        }
        try {
            return CueAssets.payloadNote(CueAssets.resolve(CueSheet.parse(index)));
        } catch (IOException e) {
            return "";
        }
    }

    /** 这条输入是否需要 ffmpeg 参与（cue 里有 APE/FLAC 音轨） */
    public boolean needsAudioTool(DiscArtifact input) {
        Path index = indexOrSelf(input);
        if (index == null || !"cue".equals(extensionOf(index))) {
            return false;
        }
        try {
            for (CueAssets.Asset asset : CueAssets.resolve(CueSheet.parse(index))) {
                if (asset.form() == CueAssets.Form.AUDIO) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    public boolean audioToolReady() {
        return ffmpeg.available();
    }

    private static long sizeOf(Path path) {
        try {
            return path == null ? 0L : Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * 把所有相关文件复制到 ASCII 目录并改写索引中的引用，返回应交给 chdman 的文件名。
     * <p>
     * cue/gdi/toc 里的文件名是相对路径，改名后必须同步改写，否则 chdman 找不到数据轨。
     */
    private static String copyToAscii(DiscArtifact input, Path tempDir) throws IOException {
        Path source = input.path();
        List<Path> files = new ArrayList<>();
        files.add(source);
        for (Path related : input.related()) {
            if (related != null && Files.isRegularFile(related) && !files.contains(related)) {
                files.add(related);
            }
        }

        Map<Path, String> names = new LinkedHashMap<>();
        int index = 1;
        for (Path file : files) {
            String ext = extensionOf(file);
            names.put(file, "f" + index++ + (ext.isEmpty() ? "" : "." + ext));
        }

        for (Path file : files) {
            Files.copy(file, tempDir.resolve(names.get(file)), StandardCopyOption.REPLACE_EXISTING);
        }

        for (Path file : files) {
            String ext = extensionOf(file);
            if (isIndexFile(ext)) {
                rewriteIndex(tempDir.resolve(names.get(file)), files, names);
            }
        }

        // 索引文件优先：chdman 需要靠它解析轨道，而不是数据文件本身
        for (Path file : files) {
            if (isIndexFile(extensionOf(file))) {
                return names.get(file);
            }
        }
        return names.get(source);
    }

    /**
     * 改写索引里的文件引用。
     * <p>
     * 正常盘里索引写的名字和磁盘上一致，直接换名即可；但这批 PSX 备份是反过来的——
     * cue 写的是 Redump 原始名（`游戏名.bin`），磁盘上却被改名成 `Track 01.bin` 再压成 `.ecm`。
     * cue 本身没错，错的是文件被改了名，所以这里按“索引声明的名字”回头找实际文件，
     * 而不是拿磁盘名去撞。
     */
    private static void rewriteIndex(Path stagedIndex, List<Path> files, Map<Path, String> names)
            throws IOException {
        byte[] content = Files.readAllBytes(stagedIndex);
        List<String> declared = quotedTokens(content);
        Map<String, String> mapping = new LinkedHashMap<>();
        Set<Path> claimed = new HashSet<>();

        if (declared.isEmpty()) {
            // gdi/toc 的引用不一定带引号，退回到按磁盘上的真实文件名替换
            for (Map.Entry<Path, String> entry : names.entrySet()) {
                String oldName = entry.getKey().getFileName().toString();
                if (!oldName.equals(entry.getValue())) {
                    mapping.put(oldName, entry.getValue());
                }
            }
        } else {
            for (String name : declared) {
                Path exact = findByName(name, files);
                if (exact != null && claimed.add(exact)) {
                    putMapping(mapping, name, names.get(exact));
                }
            }
            // 索引里写了、磁盘上却没有：按轨道号或“剩下的唯一候选”认领
            for (String name : declared) {
                if (mapping.containsKey(name)) {
                    continue;
                }
                Path actual = matchDeclared(name, files, claimed);
                if (actual != null && claimed.add(actual)) {
                    putMapping(mapping, name, names.get(actual));
                }
            }
        }

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            content = replaceName(content, entry.getKey(), entry.getValue());
        }
        Files.write(stagedIndex, content);
    }

    private static void putMapping(Map<String, String> mapping, String declared, String staged) {
        if (declared == null || staged == null || declared.equals(staged)) {
            return;
        }
        mapping.put(declared, staged);
    }

    /** 索引引用的文件名 → 磁盘上的实际文件，字面同名优先 */
    private static Path findByName(String declared, List<Path> files) {
        String base = baseName(declared);
        for (Path file : files) {
            if (file.getFileName().toString().equals(base)) {
                return file;
            }
        }
        return null;
    }

    /** 名字对不上时的退路：先比轨道号，再接受“只剩一个候选” */
    private static Path matchDeclared(String declared, List<Path> files, Set<Path> claimed) {
        Integer track = trackOf(declared);
        if (track != null) {
            for (Path file : files) {
                if (claimed.contains(file) || isIndexFile(extensionOf(file))) {
                    continue;
                }
                if (track.equals(trackOf(file.getFileName().toString()))) {
                    return file;
                }
            }
        }
        List<Path> remaining = new ArrayList<>();
        for (Path file : files) {
            if (!claimed.contains(file) && !isIndexFile(extensionOf(file))) {
                remaining.add(file);
            }
        }
        return remaining.size() == 1 ? remaining.get(0) : null;
    }

    /** 取文件名里的轨道号，取不到返回 null */
    private static Integer trackOf(String name) {
        Matcher matcher = TRACK_IN_NAME.matcher(name);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 索引里引号括起来的内容，两种可疑编码都过一遍 */
    private static List<String> quotedTokens(byte[] content) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Charset charset : new Charset[]{gbkCharset(), StandardCharsets.UTF_8}) {
            Matcher matcher = QUOTED.matcher(new String(content, charset));
            while (matcher.find()) {
                String token = matcher.group(1);
                if (!token.isBlank() && seen.add(token)) {
                    result.add(token);
                }
            }
        }
        return result;
    }

    private static byte[] replaceName(byte[] content, String oldName, String newName) {
        if (oldName == null || oldName.isBlank() || oldName.equals(newName)) {
            return content;
        }
        // 索引文件的编码不确定：cue 常见 ANSI/GBK，也有人存成 UTF-8，两种都试一遍
        for (String charset : new String[]{"GBK", "UTF-8"}) {
            byte[] needle = bytesOf(oldName, charset);
            if (needle != null && needle.length > 0) {
                content = replaceBytes(content, needle, newName.getBytes(StandardCharsets.US_ASCII));
            }
        }
        return content;
    }

    private static String baseName(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private static Charset gbkCharset() {
        try {
            return Charset.forName("GB18030");
        } catch (RuntimeException e) {
            return StandardCharsets.ISO_8859_1;
        }
    }

    private static byte[] bytesOf(String text, String charset) {
        try {
            return text.getBytes(Charset.forName(charset));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 字节级替换：ISO-8859-1 是无损单字节映射，可以安全地当字节容器用 */
    private static byte[] replaceBytes(byte[] haystack, byte[] needle, byte[] replacement) {
        String source = new String(haystack, StandardCharsets.ISO_8859_1);
        String from = new String(needle, StandardCharsets.ISO_8859_1);
        if (!source.contains(from)) {
            return haystack;
        }
        String to = new String(replacement, StandardCharsets.ISO_8859_1);
        return source.replace(from, to).getBytes(StandardCharsets.ISO_8859_1);
    }

    private static boolean isIndexFile(String extension) {
        return "cue".equals(extension) || "gdi".equals(extension) || "toc".equals(extension);
    }

    /** 选一个路径全为 ASCII 的临时根目录，找不到就明确失败而不是让 chdman 报乱码错 */
    private static Path asciiTempRoot() throws IOException {
        String[] candidates = {System.getProperty("java.io.tmpdir"), System.getProperty("user.dir")};
        for (String candidate : candidates) {
            if (candidate == null || !isAscii(candidate)) {
                continue;
            }
            Path path = Path.of(candidate);
            if (Files.isDirectory(path) && Files.isWritable(path)) {
                return path;
            }
        }
        throw new IOException("临时目录路径含非 ASCII 字符，chdman 无法在此环境下工作；"
                + "可通过 gamelist.chdman.path 配置，或使用 Docker 部署");
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String firstLine(String text) {
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return "";
    }

    /** chdman 的原始返回：退出码 + 两条流的完整文本 + 回读到的轨道数 */
    public record Result(int exit, String stdout, String stderr, int tracks) {

        public boolean ok() {
            return exit == 0;
        }

        /** 失败时给用户看的摘要：优先 stderr 末尾几行 */
        public String tail() {
            String text = stderr == null || stderr.isBlank() ? stdout : stderr;
            if (text == null || text.isBlank()) {
                return "（无输出）";
            }
            String[] lines = text.split("\\R");
            StringBuilder builder = new StringBuilder();
            int from = Math.max(0, lines.length - 4);
            for (int i = from; i < lines.length; i++) {
                if (!lines[i].isBlank()) {
                    builder.append(lines[i].trim()).append(' ');
                }
            }
            String summary = builder.toString().trim();
            return summary.length() > 300 ? summary.substring(0, 300) + "…" : summary;
        }

        /** 成功时留痕用的摘要：抓 stdout 的轨道数与逻辑大小 */
        public String summary() {
            StringBuilder builder = new StringBuilder();
            for (String line : stdout.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("Input tracks") || trimmed.startsWith("Input length")
                        || trimmed.startsWith("Logical size")) {
                    builder.append(trimmed).append(' ');
                }
            }
            String text = builder.toString().trim();
            return text.isEmpty() ? "无摘要" : text;
        }
    }
}
