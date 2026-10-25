package com.gamelist.variant.audio;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gamelist.variant.ExternalToolSettings;

/**
 * ffmpeg 外部进程封装：只用来把压缩音轨还原成 WAV。
 * <p>
 * CD 音轨是 44.1kHz / 16bit / 立体声的无损 PCM，APE 与 FLAC 都是它的无损压缩，
 * 所以转换不引入任何音质损失，chdman 也能直接按 {@code WAVE} 读回去。
 * <p>
 * 与 {@code Chdman} 一样，这里只管“发命令、收输出”，不解释音频内容。
 */
@Component
public class Ffmpeg {

    private static final Logger logger = LoggerFactory.getLogger(Ffmpeg.class);

    /** 单条流最多保留的字符数 */
    private static final int MAX_CAPTURE = 16 * 1024;

    /** 转一条音轨的上限；整轨 APE 再长也够用 */
    private static final long TIMEOUT_MS = 10L * 60L * 1000L;

    /** 配置文件里的路径（gamelist.ffmpeg.path / FFMPEG），界面设置为空时才用它 */
    private final String defaultPath;
    private final ExternalToolSettings toolSettings;

    private volatile Path executable;
    private volatile boolean located;
    private volatile String version = "";
    private volatile String sourceLabel = "";

    public Ffmpeg(@Value("${gamelist.ffmpeg.path:}") String configuredPath,
                  ExternalToolSettings toolSettings) {
        this.defaultPath = configuredPath == null ? "" : configuredPath.trim();
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
            logger.warn("探测 ffmpeg 失败", e);
        }
        if (executable == null) {
            logger.info("未找到 ffmpeg，APE/FLAC 音轨无法还原为 WAV（可在工具箱 → 外部工具里指定路径，或配置 gamelist.ffmpeg.path）{}",
                    sourceLabel.isEmpty() ? "" : "｜" + sourceLabel);
        } else {
            logger.info("ffmpeg 可用: {}｜来源: {}", executable, sourceLabel);
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

    /** 当前生效路径的来源（界面设置 / gamelist.ffmpeg.path / 环境变量 / PATH / 某个目录） */
    public String source() {
        locate();
        return sourceLabel;
    }

    public synchronized String version() {
        if (!version.isEmpty() || locate() == null) {
            return version;
        }
        try {
            Capture capture = exec(List.of("-hide_banner", "-version"), 5_000L);
            String first = capture.firstLine();
            // `ffmpeg version 4.2 Copyright ...` → `4.2`
            int at = first.indexOf("version ");
            version = at >= 0 ? first.substring(at + "version ".length()).split("\\s+")[0] : first;
        } catch (IOException e) {
            logger.debug("读取 ffmpeg 版本失败: {}", e.getMessage());
        }
        return version;
    }

    /**
     * 压缩音轨 → WAV（PCM 16bit，保持原始采样率与声道数）。
     * <p>
     * 输出必须是 chdman 认得的规范 WAV，所以这里显式指定 {@code pcm_s16le} 与 {@code -f wav}，
     * 不让 ffmpeg 按源文件的容器习惯自行选择。
     */
    public void toWav(Path source, Path target, long totalBytes) throws IOException {
        Path tool = locate();
        if (tool == null) {
            throw new IOException("未找到 ffmpeg，无法把 " + extension(source) + " 音轨还原为 WAV"
                    + "（可配置 gamelist.ffmpeg.path）");
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        List<String> args = List.of(
                "-hide_banner",
                "-nostdin",
                "-y",
                "-loglevel", "error",
                "-i", source.toAbsolutePath().toString(),
                "-vn",
                "-acodec", "pcm_s16le",
                "-f", "wav",
                target.toAbsolutePath().toString());
        Capture capture = exec(args, TIMEOUT_MS);
        if (capture.exit() != 0 || !Files.isRegularFile(target) || Files.size(target) <= 0) {
            throw new IOException("ffmpeg 转换音轨失败（退出码 " + capture.exit() + "）: " + capture.tail()
                    + "｜源 " + source.getFileName() + "（" + totalBytes + " 字节）");
        }
        logger.debug("ffmpeg 完成: {} → {} 字节", source.getFileName(), Files.size(target));
    }

    // ------------------------------------------------------------------ 进程执行

    private Capture exec(List<String> args, long timeoutMs) throws IOException {
        List<String> command = new ArrayList<>(args.size() + 1);
        command.add(locate().toString());
        command.addAll(args);

        Process process = new ProcessBuilder(command).start();
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread outPump = pump(process.getInputStream(), stdout);
        Thread errPump = pump(process.getErrorStream(), stderr);
        try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("ffmpeg 超时（超过 " + (timeoutMs / 60000L) + " 分钟）");
            }
            outPump.join(2_000L);
            errPump.join(2_000L);
            return new Capture(process.exitValue(), stdout.toString(), stderr.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("ffmpeg 执行被中断", e);
        } finally {
            process.destroyForcibly();
        }
    }

    private static Thread pump(InputStream stream, StringBuilder sink) {
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
                }
            } catch (IOException ignored) {
                // 进程被强杀时流会提前中断，属正常路径
            }
        }, "ffmpeg-pump");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    // ------------------------------------------------------------------ 可执行文件定位

    private Path resolveExecutable() {
        String name = isWindows() ? "ffmpeg.exe" : "ffmpeg";

        // 明确写了路径就只认它：填错时报不可用，而不是悄悄换成 PATH 里的另一个版本
        String fromUi = toolSettings.get(ExternalToolSettings.FFMPEG_PATH);
        if (!fromUi.isEmpty()) {
            Path path = probe(fromUi);
            sourceLabel = path != null ? "界面设置" : "界面设置（路径不可用）";
            return path;
        }
        if (!defaultPath.isEmpty()) {
            Path path = probe(defaultPath);
            sourceLabel = path != null ? "gamelist.ffmpeg.path" : "gamelist.ffmpeg.path（路径不可用）";
            return path;
        }
        Path fromEnv = probe(System.getenv("FFMPEG"));
        if (fromEnv != null) {
            sourceLabel = "环境变量 FFMPEG";
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

    private static String extension(Path path) {
        String name = path == null ? "" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "压缩";
    }

    /** 一次命令的原始返回 */
    private record Capture(int exit, String stdout, String stderr) {

        String firstLine() {
            for (String line : (stdout + "\n" + stderr).split("\\R")) {
                if (!line.isBlank()) {
                    return line.trim();
                }
            }
            return "";
        }

        /** 失败时给用户看的摘要：优先 stderr 末尾几行 */
        String tail() {
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
    }
}
