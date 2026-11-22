package com.gamelist.variant.module;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gamelist.variant.ConversionModule;
import com.gamelist.variant.ExternalToolSettings;

/**
 * wit（Wiimms ISO Tools）外部进程封装：定位可执行文件、跑 {@code wit copy}、把 ISO 转成 WBFS。
 * <p>
 * 与 {@link Chdman} 同级、同套路：只负责“把输入喂进去、把 wit 说的话原样带回来”，
 * 不解释镜像内容。scrub 由 wit 自动完成（WBFS 本就是清洗分区）。
 * <p>
 * 定位顺序：界面设置（{@code wit.path}）→ 配置文件（{@code gamelist.wit.path}）
 * → 环境变量 {@code WIT} → {@code PATH} → 程序目录（tools/、data/tools、bin/）。
 * Docker/Linux 镜像内置 wit 时留空即可自动命中；Windows 把官方 Cygwin 版放进 tools/ 也能命中。
 */
@Component
public class Wit {

    private static final Logger logger = LoggerFactory.getLogger(Wit.class);

    /** 单次转换上限；整盘 Wii 也够用 */
    private static final long TIMEOUT_MS = 30L * 60L * 1000L;

    /** 单条流最多保留的字符数，防止异常输出撑爆内存 */
    private static final int MAX_CAPTURE = 64 * 1024;

    /** 进度行里的百分比：wit 输出形如 `... 42 %` 或 `42.5%` */
    private static final Pattern PROGRESS = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*%");

    /** 配置文件里的路径（gamelist.wit.path），界面设置为空时才用它 */
    private final String defaultPath;
    private final ExternalToolSettings toolSettings;

    private volatile Path executable;
    private volatile boolean located;
    private volatile String version = "";
    private volatile String sourceLabel = "";

    public Wit(@Value("${gamelist.wit.path:}") String configuredPath,
               ExternalToolSettings toolSettings) {
        this.defaultPath = configuredPath == null ? "" : configuredPath.trim();
        this.toolSettings = toolSettings;
    }

    public boolean available() {
        return locate() != null;
    }

    /** 按“界面 → 配置 → 环境变量 → PATH → 程序目录”的顺序找可执行文件，结果只探测一次 */
    public synchronized Path locate() {
        if (located) {
            return executable;
        }
        located = true;
        try {
            executable = resolveExecutable();
        } catch (RuntimeException e) {
            executable = null;
            sourceLabel = "探测失败：" + e.getMessage();
            logger.warn("探测 wit 失败", e);
        }
        // 文件在≠能跑：Windows 下只拷 wit.exe 缺 cygwin1.dll 时进程根本拉不起来，
        // 空跑一次主程序，跑不动就判不可用（只测主文件，不逐项校验随附 dll）
        if (executable != null && !smokeTest(executable)) {
            executable = null;
            sourceLabel = "主程序能找到但无法运行（常见原因：只拷了 wit.exe 没带同目录的 cygwin1.dll 等随附文件；"
                    + "应解压整个包而不是只拿单个 exe）";
        }
        if (executable == null) {
            logger.info("未找到 wit，WBFS 产线不可用（可在工具箱 → 外部工具里指定路径，或配置 gamelist.wit.path）{}",
                    sourceLabel.isEmpty() ? "" : "｜" + sourceLabel);
        } else {
            logger.info("wit 可用: {}｜来源: {}", executable, sourceLabel);
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

    /** 当前生效路径的来源 */
    public String source() {
        locate();
        return sourceLabel;
    }

    public synchronized String version() {
        if (!version.isEmpty() || locate() == null) {
            return version;
        }
        try {
            Result result = execRaw(executable, List.of("--version"), null, 5_000L, 0L,
                    ConversionModule.ProgressSink.NOOP);
            String first = firstLine(result.stdout());
            if (first.isEmpty()) {
                first = firstLine(result.stderr());
            }
            version = first.trim();
        } catch (IOException e) {
            version = "";
        }
        return version;
    }

    /**
     * ISO → WBFS 单游戏文件。
     * <p>
     * 产物先落纯 ASCII 暂存目录，再由 Java 原子挪到 {@code target}（跨盘、目标含中文都由 Java 处理，
     * 与 wit 无关）。wit 若按 WiiTDB 自动改名而没产出预期的 {@code out.wbfs}，会回退到暂存目录里
     * 实际生成的那个 {@code .wbfs}。
     *
     * @param sourceIso 源 Wii ISO（.iso/.gcm）
     * @param target    最终产物路径（调用方通常传 .part）
     */
    public Result copyToWbfs(Path sourceIso, Path target, ConversionModule.ProgressSink progress)
            throws IOException {
        Path tool = locate();
        if (tool == null) {
            throw new IOException("未找到 wit，无法生成 WBFS");
        }
        long totalBytes = sizeOf(sourceIso);
        Path staging = Files.createTempDirectory(asciiTempRoot(), "fk-wbfs-");
        try {
            Path stagedOut = staging.resolve("out.wbfs");
            String inputArg;
            String abs = sourceIso.toAbsolutePath().toString();
            if (isAscii(abs)) {
                inputArg = abs;
            } else {
                // 非 ASCII 源路径：搬进暂存目录，用相对名喂给 wit
                Path stagedIn = staging.resolve("src.iso");
                Files.copy(sourceIso, stagedIn);
                inputArg = "src.iso";
            }
            List<String> args = List.of("copy", inputArg, "out.wbfs");
            Result result = execRaw(tool, args, staging, TIMEOUT_MS, totalBytes, progress);
            if (result.exit() != 0) {
                throw new IOException("wit 转换失败（退出码 " + result.exit() + "）: " + result.tail());
            }
            Path produced = locateProduced(stagedOut, staging);
            if (produced == null) {
                throw new IOException("wit 未产出 WBFS 文件: " + result.tail());
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try {
                Files.move(produced, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(produced, target, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.debug("wit 完成: {} → {} 字节｜{}", sourceIso.getFileName(),
                    Files.size(target), result.summary());
            return result;
        } finally {
            deleteTree(staging);
        }
    }

    /**
     * 对已存在的 WBFS 做分片（FAT32 单文件上限）。
     * <p>
     * {@code wit copy --split --split-size <size>}：WBFS 也可作为源，输出受 split 控制，
     * 主文件（含 WBFS 头）与兄弟分片一并落在 {@code stagingDir}。用固定 ASCII 名 {@code out.wbf}
     * 喂给 wit，产物按前缀 {@code out.wbf*} 通配收集——不依赖 wit 具体的后缀命名（.wbf1 / .wbf.1 皆命中）。
     *
     * @param sourceWbfs  现有 WBFS 文件
     * @param stagingDir  产物暂存目录（调用方创建、调用方清理）
     * @param splitSize   单片上限（如 {@code 4000M}），空则用 wit 默认
     * @return 暂存目录内实际产出的分片文件（至少 1 个）
     */
    public List<Path> splitWbfs(Path sourceWbfs, Path stagingDir, String splitSize,
                                ConversionModule.ProgressSink progress) throws IOException {
        Path tool = locate();
        if (tool == null) {
            throw new IOException("未找到 wit，无法分片 WBFS");
        }
        long totalBytes = sizeOf(sourceWbfs);
        // 用 ASCII 相对名喂给 wit，规避非 ASCII 源路径
        Path stagedIn = stagingDir.resolve("src.wbf");
        Files.copy(sourceWbfs, stagedIn, StandardCopyOption.REPLACE_EXISTING);
        List<String> args = new ArrayList<>();
        args.add("copy");
        args.add("--split");
        if (splitSize != null && !splitSize.isBlank()) {
            args.add("--split-size");
            args.add(splitSize.trim());
        }
        args.add("src.wbf");
        args.add("out.wbf");
        Result result = execRaw(tool, args, stagingDir, TIMEOUT_MS, totalBytes, progress);
        if (result.exit() != 0) {
            throw new IOException("wit 分片失败（退出码 " + result.exit() + "）: " + result.tail());
        }
        List<Path> produced = new ArrayList<>();
        try (Stream<Path> s = Files.list(stagingDir)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("out.wbf"))
                    .sorted()
                    .forEach(produced::add);
        }
        if (produced.isEmpty()) {
            throw new IOException("wit 未产出分片文件: " + result.tail());
        }
        logger.debug("wit 分片完成: {} → {} 个分片｜{}", sourceWbfs.getFileName(), produced.size(), result.summary());
        return produced;
    }

    /**
     * 从 Wii 镜像（{@code .iso} 或 {@code .wbfs}）头部读取 6 位游戏 ID。
     * <p>
     * 不同容器布局下 DiscID 所在偏移不同，此处按优先级多探：
     * <ul>
     *   <li>{@code 0x18}：标准 ISO 镜像，DiscID 就在镜像头 0x18</li>
     *   <li>{@code 0x118}：单游戏 WBFS 容器（前 0x100 为 WBFS 头，ISO 从 0x100 起）</li>
     *   <li>{@code 0x40118}：含 0x40000 WBFS 头区的变体容器</li>
     * </ul>
     * 任一位置命中 {@code [A-Z][A-Z0-9]{5}} 即返回，均失败时返回 {@code null}。
     * 仅当库里的 {@code discSerial} 为空且文件名兼不中时作为导出前兵底。
     */
    public static String readGameId(Path image) {
        if (image == null || !Files.isRegularFile(image)) {
            return null;
        }
        long[] probes = { 0x18L, 0x118L, 0x40118L };
        try (RandomAccessFile raf = new RandomAccessFile(image.toFile(), "r")) {
            long len = raf.length();
            for (long off : probes) {
                if (len < off + 6L) continue;
                try {
                    raf.seek(off);
                    byte[] buf = new byte[6];
                    raf.readFully(buf);
                    StringBuilder sb = new StringBuilder(6);
                    for (byte b : buf) sb.append((char) (b & 0xFF));
                    String id = sb.toString().trim().toUpperCase(Locale.ROOT);
                    if (id.matches("[A-Z][A-Z0-9]{5}")) {
                        return id;
                    }
                } catch (IOException ignore) {
                    // 单个偏移读失败不中断，继续下一个
                }
            }
            return null;
        } catch (IOException e) {
            logger.debug("读取 Wii 镜像游戏 ID 失败: {}", e.getMessage());
            return null;
        }
    }

    /** 优先取预期的 out.wbfs；wit 自动改名时回退到暂存目录里第一个 .wbfs */
    private static Path locateProduced(Path expected, Path staging) throws IOException {
        if (Files.isRegularFile(expected) && Files.size(expected) > 0) {
            return expected;
        }
        try (Stream<Path> s = Files.list(staging)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wbfs"))
                    .filter(p -> {
                        try {
                            return Files.size(p) > 0;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .findFirst().orElse(null);
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
        Thread outPump = pump(process.getInputStream(), stdout, progress, totalBytes);
        Thread errPump = pump(process.getErrorStream(), stderr, progress, totalBytes);

        try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("wit 超时（超过 " + (timeoutMs / 60000L) + " 分钟）");
            }
            outPump.join(2_000L);
            errPump.join(2_000L);
            return new Result(process.exitValue(), stdout.toString(), stderr.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("wit 执行被中断", e);
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
        }, "wit-pump");
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
        String name = isWindows() ? "wit.exe" : "wit";

        String fromUi = toolSettings.get(ExternalToolSettings.WIT_PATH);
        if (!fromUi.isEmpty()) {
            Path path = probe(fromUi);
            sourceLabel = path != null ? "界面设置" : "界面设置（找不到该文件）";
            return path;
        }
        if (!defaultPath.isEmpty()) {
            Path path = probe(defaultPath);
            sourceLabel = path != null ? "gamelist.wit.path" : "gamelist.wit.path（找不到该文件）";
            return path;
        }
        Path fromEnv = probe(System.getenv("WIT"));
        if (fromEnv != null) {
            sourceLabel = "环境变量 WIT";
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

    /** 空跑一次主程序（--version）验证真能启动；进程拉不起来或 5 秒退不出都算不可用 */
    private static boolean smokeTest(Path tool) {
        try {
            Process process = new ProcessBuilder(tool.toString(), "--version").start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            // 输出丢弃即可，只要进程能正常起来并退出
            process.getInputStream().close();
            process.getErrorStream().close();
            return true;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    // ------------------------------------------------------------------ 路径与临时目录

    /** 选一个路径全为 ASCII 的临时根目录，找不到就退回系统临时目录（多数部署为 ASCII） */
    private static Path asciiTempRoot() throws IOException {
        String[] candidates = {System.getProperty("java.io.tmpdir"), System.getProperty("user.dir")};
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (isAscii(candidate)) {
                Path dir = Path.of(candidate);
                if (Files.isDirectory(dir)) {
                    return dir;
                }
            }
        }
        return Path.of(System.getProperty("java.io.tmpdir", "."));
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    private static void deleteTree(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
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

    private static long sizeOf(Path path) {
        try {
            return path == null ? 0L : Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static String firstLine(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int nl = text.indexOf('\n');
        return (nl >= 0 ? text.substring(0, nl) : text).trim();
    }

    /** wit 的原始返回：退出码 + 两条流文本 */
    public record Result(int exit, String stdout, String stderr) {

        public boolean ok() {
            return exit == 0;
        }

        /** 取尾部若干字符用于报错，避免整段刷屏 */
        public String tail() {
            String all = (stderr == null ? "" : stderr.trim())
                    + (stdout == null || stdout.isBlank() ? "" : "\n" + stdout.trim());
            all = all.trim();
            return all.length() <= 800 ? all : "..." + all.substring(all.length() - 800);
        }

        public String summary() {
            return "exit=" + exit;
        }
    }
}
