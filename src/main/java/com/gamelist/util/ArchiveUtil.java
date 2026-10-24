package com.gamelist.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.compress.PasswordRequiredException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.LZMA2Options;

/**
 * zip / 7z 单文件读写工具，供 ROM 副本生成使用。
 * <p>
 * 设计约束：
 * <ul>
 *   <li>纯 Java 实现，不调用外部 7z 进程；</li>
 *   <li>加密包、分卷包、PPMd 等模拟器无法直接加载的形态一律拒绝；</li>
 *   <li>所有写入都先落临时文件，再原子移动，避免生成半截副本；</li>
 *   <li>解压前校验条目路径与大小，防止 zip-slip 与压缩炸弹。</li>
 * </ul>
 */
public final class ArchiveUtil {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveUtil.class);

    /** ZIP 文件名编码回退顺序，与现有刮削代码保持一致 */
    private static final Charset[] ZIP_CHARSETS = {
            Charset.forName("cp437"), Charset.forName("GBK"), StandardCharsets.UTF_8
    };

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long MAX_ENTRY_BYTES = 2L * 1024 * 1024 * 1024; // 单文件 2 GiB
    private static final int ZIP_LEVEL = 6;
    private static final int SEVEN_Z_PRESET = 5;

    /** 压缩包内的说明文件，不作为 ROM 候选 */
    private static final Set<String> JUNK_EXTENSIONS = Set.of(
            "txt", "nfo", "info", "diz", "md5", "sha1", "sha256", "sfv", "dat",
            "xml", "json", "yml", "yaml", "url", "html", "htm", "log", "m3u", "db");

    private ArchiveUtil() {
    }

    public static boolean isZip(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    public static boolean isSevenZ(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".7z");
    }

    public static boolean isArchive(String fileName) {
        return isZip(fileName) || isSevenZ(fileName);
    }

    /** 去掉最后一个扩展名，只处理文件名部分 */
    public static String stripExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        int dot = fileName.lastIndexOf('.');
        if (dot <= slash || dot == fileName.length() - 1) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    /** 取小写扩展名（不含点），无扩展名返回空串 */
    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        int dot = fileName.lastIndexOf('.');
        if (dot <= slash || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 选出压缩包内的主 ROM 条目：优先与压缩包同名，否则取体积最大的非说明文件。
     */
    public static ArchiveEntryInfo pickMainEntry(Path archive) {
        requireReadable(archive);
        String fileName = archive.getFileName().toString();
        if (isSevenZ(fileName)) {
            return pickMainEntrySevenZ(archive);
        }
        if (isZip(fileName)) {
            return pickMainEntryZip(archive);
        }
        throw new ArchiveException(ArchiveException.Reason.NOT_ARCHIVE,
                "不是支持的压缩包格式: " + fileName);
    }

    private static ArchiveEntryInfo pickMainEntryZip(Path archive) {
        Exception lastError = null;
        for (Charset charset : ZIP_CHARSETS) {
            try (ZipFile zip = ZipFile.builder().setFile(archive.toFile()).setCharset(charset).get()) {
                List<ArchiveEntryInfo> candidates = new ArrayList<>();
                Enumeration<ZipArchiveEntry> entries = zip.getEntries();
                while (entries.hasMoreElements()) {
                    ZipArchiveEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    candidates.add(new ArchiveEntryInfo(entry.getName(), entry.getSize(), isJunk(entry.getName())));
                }
                ArchiveEntryInfo picked = choose(candidates, archive.getFileName().toString());
                if (picked != null) {
                    return picked;
                }
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw wrapReadFailure(archive, lastError, "读取 ZIP 失败");
    }

    private static ArchiveEntryInfo pickMainEntrySevenZ(Path archive) {
        try (SevenZFile sevenZ = openSevenZ(archive)) {
            List<ArchiveEntryInfo> candidates = new ArrayList<>();
            SevenZArchiveEntry entry;
            while ((entry = sevenZ.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                candidates.add(new ArchiveEntryInfo(entry.getName(), entry.getSize(), isJunk(entry.getName())));
            }
            ArchiveEntryInfo picked = choose(candidates, archive.getFileName().toString());
            if (picked != null) {
                return picked;
            }
            throw new ArchiveException(ArchiveException.Reason.EMPTY_ARCHIVE,
                    "压缩包内没有可用文件: " + archive.getFileName());
        } catch (ArchiveException e) {
            throw e;
        } catch (IOException e) {
            throw classify(archive, e);
        }
    }

    private static ArchiveEntryInfo choose(List<ArchiveEntryInfo> candidates, String archiveFileName) {
        String baseName = stripExtension(archiveFileName);
        ArchiveEntryInfo named = null;
        ArchiveEntryInfo largest = null;
        ArchiveEntryInfo largestAny = null;

        for (ArchiveEntryInfo info : candidates) {
            if (largestAny == null || info.effectiveSize() > largestAny.effectiveSize()) {
                largestAny = info;
            }
            if (info.isJunk()) {
                continue;
            }
            if (largest == null || info.effectiveSize() > largest.effectiveSize()) {
                largest = info;
            }
            if (info.baseName().equalsIgnoreCase(baseName)
                    && (named == null || info.effectiveSize() > named.effectiveSize())) {
                named = info;
            }
        }
        if (named != null) {
            return named;
        }
        return largest != null ? largest : largestAny;
    }

    /**
     * 将压缩包内指定条目解压到目标文件（目标路径由调用方决定，不跟随条目路径）。
     */
    public static void extractEntry(Path archive, String entryName, Path target) {
        requireReadable(archive);
        if (entryName == null || entryName.isBlank()) {
            throw new ArchiveException(ArchiveException.Reason.ENTRY_NOT_FOUND, "未指定压缩包条目");
        }
        String safeName = normalizeEntryName(entryName);
        String fileName = archive.getFileName().toString();
        if (isSevenZ(fileName)) {
            extractEntrySevenZ(archive, safeName, target);
        } else if (isZip(fileName)) {
            extractEntryZip(archive, safeName, target);
        } else {
            throw new ArchiveException(ArchiveException.Reason.NOT_ARCHIVE,
                    "不是支持的压缩包格式: " + fileName);
        }
    }

    private static void extractEntryZip(Path archive, String entryName, Path target) {
        Exception lastError = null;
        boolean opened = false;
        for (Charset charset : ZIP_CHARSETS) {
            ZipFile zip;
            try {
                zip = ZipFile.builder().setFile(archive.toFile()).setCharset(charset).get();
            } catch (Exception e) {
                lastError = e;
                continue;
            }
            opened = true;
            try (ZipFile openedZip = zip) {
                ZipArchiveEntry entry = openedZip.getEntry(entryName);
                if (entry == null) {
                    continue; // 换一种编码再找
                }
                validateZipEntry(openedZip, entry);
                try (InputStream in = openedZip.getInputStream(entry)) {
                    writeAtomically(in, target, entry.getSize(), entryName);
                }
                return;
            } catch (ArchiveException e) {
                throw e;
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (!opened) {
            throw wrapReadFailure(archive, lastError, "读取 ZIP 失败");
        }
        if (lastError != null) {
            throw classify(archive, lastError);
        }
        throw new ArchiveException(ArchiveException.Reason.ENTRY_NOT_FOUND,
                "压缩包内未找到条目: " + entryName);
    }

    private static void validateZipEntry(ZipFile zip, ZipArchiveEntry entry) {
        if (entry.isDirectory()) {
            throw new ArchiveException(ArchiveException.Reason.UNSAFE_ENTRY,
                    "条目不是文件: " + entry.getName());
        }
        if (entry.isUnixSymlink()) {
            throw new ArchiveException(ArchiveException.Reason.UNSAFE_ENTRY,
                    "压缩包内含符号链接，已拒绝: " + entry.getName());
        }
        if (entry.getGeneralPurposeBit() != null && entry.getGeneralPurposeBit().usesEncryption()) {
            throw new ArchiveException(ArchiveException.Reason.ENCRYPTED,
                    "ZIP 已加密，模拟器无法直接加载，请重新打包为未加密文件");
        }
        if (!zip.canReadEntryData(entry)) {
            throw new ArchiveException(ArchiveException.Reason.UNSUPPORTED_METHOD,
                    "ZIP 使用了不支持的压缩算法，请重新打包: " + entry.getName());
        }
        if (entry.getSize() > MAX_ENTRY_BYTES) {
            throw new ArchiveException(ArchiveException.Reason.TOO_LARGE,
                    "条目解压后超过 2 GiB 限制: " + entry.getName());
        }
    }

    private static void extractEntrySevenZ(Path archive, String entryName, Path target) {
        try (SevenZFile sevenZ = openSevenZ(archive)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZ.getNextEntry()) != null) {
                if (entry.isDirectory() || !entryName.equals(entry.getName())) {
                    continue;
                }
                if (entry.getSize() > MAX_ENTRY_BYTES) {
                    throw new ArchiveException(ArchiveException.Reason.TOO_LARGE,
                            "条目解压后超过 2 GiB 限制: " + entry.getName());
                }
                try (InputStream in = sevenZ.getInputStream(entry)) {
                    writeAtomically(in, target, entry.getSize(), entry.getName());
                }
                return;
            }
            throw new ArchiveException(ArchiveException.Reason.ENTRY_NOT_FOUND,
                    "压缩包内未找到条目: " + entryName);
        } catch (ArchiveException e) {
            throw e;
        } catch (IOException e) {
            throw classify(archive, e);
        }
    }

    /** 用 ZIP Deflate level 6 打包单个文件 */
    public static void createZip(Path source, String entryName, Path target) {
        requireReadable(source);
        String safeName = normalizeEntryName(entryName);
        Path temp = tempFor(target);
        try {
            try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(temp.toFile())) {
                out.setLevel(ZIP_LEVEL);
                out.setEncoding(StandardCharsets.UTF_8.name());
                out.setUseLanguageEncodingFlag(true);

                ZipArchiveEntry entry = new ZipArchiveEntry(safeName);
                entry.setSize(Files.size(source));
                entry.setTime(Files.getLastModifiedTime(source).toMillis());
                out.putArchiveEntry(entry);
                Files.copy(source, out);
                out.closeArchiveEntry();
                out.finish();
            }
            moveIntoPlace(temp, target);
        } catch (ArchiveException e) {
            deleteQuietly(temp);
            throw e;
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new ArchiveException(ArchiveException.Reason.IO,
                    "创建 ZIP 失败: " + e.getMessage(), e);
        }
    }

    /** 用 7z LZMA2 preset 5 打包单个文件 */
    public static void createSevenZ(Path source, String entryName, Path target) {
        requireReadable(source);
        String safeName = normalizeEntryName(entryName);
        Path temp = tempFor(target);
        LZMA2Options options = new LZMA2Options();
        try {
            options.setPreset(SEVEN_Z_PRESET);
        } catch (org.tukaani.xz.UnsupportedOptionsException e) {
            throw new ArchiveException(ArchiveException.Reason.IO,
                    "7z 压缩参数无效: " + e.getMessage(), e);
        }
        try {
            try (SevenZOutputFile out = new SevenZOutputFile(temp.toFile())) {
                out.setContentMethods(Collections.singletonList(
                        new SevenZMethodConfiguration(SevenZMethod.LZMA2, options)));
                SevenZArchiveEntry entry = out.createArchiveEntry(source.toFile(), safeName);
                out.putArchiveEntry(entry);
                try (InputStream in = new BufferedInputStream(Files.newInputStream(source))) {
                    out.write(in);
                }
                out.closeArchiveEntry();
                out.finish();
            }
            moveIntoPlace(temp, target);
        } catch (ArchiveException e) {
            deleteQuietly(temp);
            throw e;
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new ArchiveException(ArchiveException.Reason.IO,
                    "创建 7z 失败: " + e.getMessage(), e);
        }
    }

    /** 复制文件（先写临时文件再原子移动），供 original 副本使用 */
    public static void copyFile(Path source, Path target) {
        requireReadable(source);
        Path temp = tempFor(target);
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            moveIntoPlace(temp, target);
        } catch (ArchiveException e) {
            deleteQuietly(temp);
            throw e;
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new ArchiveException(ArchiveException.Reason.IO,
                    "复制文件失败: " + e.getMessage(), e);
        }
    }

    public static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    logger.warn("删除失败: {} ({})", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.warn("遍历目录失败: {} ({})", root, e.getMessage());
        }
    }

    /** 将数据库中的相对路径安全地解析到根目录下 */
    public static Path resolveInside(Path root, String relative) {
        if (relative == null || relative.isBlank()) {
            throw new ArchiveException(ArchiveException.Reason.UNSAFE_ENTRY, "副本相对路径为空");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(relative.replace('\\', '/')).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new ArchiveException(ArchiveException.Reason.UNSAFE_ENTRY,
                    "副本路径越界: " + relative);
        }
        return resolved;
    }

    private static void writeAtomically(InputStream in, Path target, long declaredSize, String entryName) {
        if (declaredSize > MAX_ENTRY_BYTES) {
            throw new ArchiveException(ArchiveException.Reason.TOO_LARGE,
                    "条目解压后超过 2 GiB 限制: " + entryName);
        }
        Path temp = tempFor(target);
        long written = 0;
        try (OutputStream out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                written += read;
                if (written > MAX_ENTRY_BYTES) {
                    throw new ArchiveException(ArchiveException.Reason.TOO_LARGE,
                            "条目解压后超过 2 GiB 限制: " + entryName);
                }
                out.write(buffer, 0, read);
            }
        } catch (ArchiveException e) {
            deleteQuietly(temp);
            throw e;
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new ArchiveException(ArchiveException.Reason.IO,
                    "解压条目失败: " + entryName + " (" + e.getMessage() + ")", e);
        }
        if (written == 0) {
            deleteQuietly(temp);
            throw new ArchiveException(ArchiveException.Reason.EMPTY_ENTRY,
                    "条目内容为空: " + entryName);
        }
        moveIntoPlace(temp, target);
    }

    private static void moveIntoPlace(Path temp, Path target) {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                deleteQuietly(temp);
                throw new ArchiveException(ArchiveException.Reason.IO,
                        "写入目标文件失败: " + ex.getMessage(), ex);
            }
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new ArchiveException(ArchiveException.Reason.IO,
                    "写入目标文件失败: " + e.getMessage(), e);
        }
    }

    private static Path tempFor(Path target) {
        Path absolute = target.toAbsolutePath();
        Path parent = absolute.getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new ArchiveException(ArchiveException.Reason.IO,
                    "创建目录失败: " + parent + " (" + e.getMessage() + ")", e);
        }
        return parent.resolve("." + absolute.getFileName() + "." + UUID.randomUUID() + ".tmp");
    }

    private static SevenZFile openSevenZ(Path archive) {
        try {
            return SevenZFile.builder().setFile(archive.toFile()).get();
        } catch (Exception e) {
            throw classify(archive, e);
        }
    }

    private static ArchiveException classify(Path archive, Exception e) {
        if (e instanceof ArchiveException archiveException) {
            return archiveException;
        }
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (e instanceof PasswordRequiredException
                || message.contains("password") || message.contains("encrypt")) {
            return new ArchiveException(ArchiveException.Reason.ENCRYPTED,
                    "压缩包已加密，模拟器无法直接加载，请重新打包为未加密文件", e);
        }
        if (message.contains("multi") && message.contains("volume")
                || message.contains("multi-volume") || message.contains("multivolume")) {
            return new ArchiveException(ArchiveException.Reason.MULTI_VOLUME,
                    "不支持分卷压缩包，请合并后重新打包", e);
        }
        if (message.contains("unsupported") || message.contains("not implemented")
                || message.contains("ppm") || message.contains("deflate64")) {
            return new ArchiveException(ArchiveException.Reason.UNSUPPORTED_METHOD,
                    "压缩包使用了模拟器无法直接加载的算法（PPMd/Deflate64），请用 LZMA2 或 Deflate 重新打包", e);
        }
        return new ArchiveException(ArchiveException.Reason.IO,
                "读取压缩包失败: " + archive.getFileName() + " (" + e.getMessage() + ")", e);
    }

    private static ArchiveException wrapReadFailure(Path archive, Exception lastError, String prefix) {
        if (lastError == null) {
            return new ArchiveException(ArchiveException.Reason.IO,
                    prefix + ": " + archive.getFileName());
        }
        ArchiveException classified = classify(archive, lastError);
        return new ArchiveException(classified.getReason(), prefix + ": " + classified.getMessage(), lastError);
    }

    private static void requireReadable(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new ArchiveException(ArchiveException.Reason.IO, "文件不存在: " + path);
        }
    }

    private static String normalizeEntryName(String entryName) {
        String normalized = entryName.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || normalized.matches("^[A-Za-z]:.*")
                || normalized.equals("..") || normalized.startsWith("../")
                || normalized.contains("/../")) {
            throw new ArchiveException(ArchiveException.Reason.UNSAFE_ENTRY,
                    "压缩包条目路径不安全: " + entryName);
        }
        return normalized;
    }

    private static boolean isJunk(String entryName) {
        if (entryName == null) {
            return true;
        }
        String name = entryName.replace('\\', '/');
        String baseName = name.substring(name.lastIndexOf('/') + 1);
        if (baseName.isBlank() || baseName.startsWith(".") || baseName.startsWith("__")) {
            return true;
        }
        if (name.startsWith("__MACOSX/") || name.contains("/__MACOSX/")) {
            return true;
        }
        return JUNK_EXTENSIONS.contains(extensionOf(baseName));
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.debug("删除临时文件失败: {} ({})", path, e.getMessage());
        }
    }

    /** 压缩包条目信息 */
    public static final class ArchiveEntryInfo {
        private final String name;
        private final long size;
        private final boolean junk;

        public ArchiveEntryInfo(String name, long size, boolean junk) {
            this.name = name;
            this.size = size;
            this.junk = junk;
        }

        public String getName() {
            return name;
        }

        public long getSize() {
            return size;
        }

        public boolean isJunk() {
            return junk;
        }

        public long effectiveSize() {
            return size > 0 ? size : 0;
        }

        public String baseName() {
            String normalized = name.replace('\\', '/');
            return stripExtension(normalized.substring(normalized.lastIndexOf('/') + 1));
        }

        public String extension() {
            return extensionOf(name);
        }
    }
}
