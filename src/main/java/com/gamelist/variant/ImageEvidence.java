package com.gamelist.variant;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 镜像证据：只认内容，不认扩展名。
 * <p>
 * 探测与模块判断共用同一套判据，避免“探测说是 ISO、模块又按另一套规则判断”的分裂。
 */
public final class ImageEvidence {

    public static final int SECTOR_2048 = 2048;
    public static final int SECTOR_2352 = 2352;
    /** ISO9660 主卷描述符位于第 16 扇区 */
    public static final int PVD_SECTOR = 16;

    private ImageEvidence() {
    }

    public static boolean isCiso(Path file) {
        return hasMagic(file, "CISO");
    }

    public static boolean isZiso(Path file) {
        return hasMagic(file, "ZISO");
    }

    public static boolean isChd(Path file) {
        return hasMagic(file, "MComprHD");
    }

    /** ECM：容器头是 {@code 45 43 4D 00}（"ECM" + 零字节），不是纯文本魔数 */
    public static boolean isEcm(Path file) {
        return hasBytes(file, 0, new byte[]{'E', 'C', 'M', 0});
    }

    public static boolean isPbp(Path file) {
        return hasMagic(file, 1, "PBP");
    }

    /** Nero 镜像：文件头标识 NER5（新）或 NERO（旧） */
    public static boolean isNrg(Path file) {
        return hasMagic(file, "NER5") || hasMagic(file, "NERO");
    }

    /** cdrdao 映像：文本首行是 CD_DA / CD_ROM / CD_ROM_XA */
    public static boolean isToc(Path file) {
        String first = firstLine(file);
        return "CD_DA".equals(first) || "CD_ROM".equals(first) || "CD_ROM_XA".equals(first);
    }

    /** Dreamcast GDI：文本首行是轨道数（纯数字） */
    public static boolean isGdi(Path file) {
        String first = firstLine(file);
        return first != null && first.matches("\\d+");
    }

    /** 文本文件的第一个非空行，去空白与 BOM */
    public static String firstLine(Path file) {
        byte[] head = read(file, 0, 512);
        if (head.length == 0) {
            return null;
        }
        String text = new String(head, StandardCharsets.ISO_8859_1);
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.replace("\uFEFF", "").trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    public static boolean isZip(Path file) {
        return hasMagic(file, 0, "PK") && hasMagic(file, 2, "\u0003\u0004");
    }

    public static boolean isSevenZ(Path file) {
        byte[] magic = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
        return hasBytes(file, 0, magic);
    }

    /** 2352 扇区原始镜像：同步头 00 FF FF FF FF FF FF FF FF FF FF 00 */
    public static boolean hasRaw2352Sync(Path file) {
        byte[] head = read(file, 0, 16);
        if (head.length < 16) {
            return false;
        }
        if ((head[0] & 0xFF) != 0x00 || (head[11] & 0xFF) != 0x00) {
            return false;
        }
        for (int i = 1; i <= 10; i++) {
            if ((head[i] & 0xFF) != 0xFF) {
                return false;
            }
        }
        return true;
    }

    /** MODE1 / MODE2 / RAW */
    public static String raw2352Mode(Path file) {
        byte[] head = read(file, 15, 1);
        if (head.length < 1) {
            return "RAW";
        }
        int mode = head[0] & 0xFF;
        if (mode == 0x01) {
            return "MODE1";
        }
        if (mode == 0x02) {
            return "MODE2";
        }
        return "RAW";
    }

    /** ISO9660 卷描述符：第 16 扇区偏移 1 处为 "CD001" */
    public static boolean hasIsoPvd(Path file, int sectorSize, int userDataOffset) {
        long offset = (long) PVD_SECTOR * sectorSize + userDataOffset;
        byte[] head = read(file, offset + 1, 5);
        return head.length == 5
                && head[0] == 'C' && head[1] == 'D' && head[2] == '0'
                && head[3] == '0' && head[4] == '1';
    }

    public static boolean hasIsoPvd2048(Path file) {
        return hasIsoPvd(file, SECTOR_2048, 0);
    }

    /**
     * 扫描数据扇区比例：用于发现“单个 bin 里混入了音轨”。
     * <p>
     * 音轨扇区没有同步头，数据轨每个扇区都有。抽样覆盖文件首尾与中段，
     * 返回带同步头的扇区占比（0~1）。
     */
    public static double dataSectorRatio(Path file, int sectorSize) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            return 0;
        }
        if (size < sectorSize * 2L) {
            return 0;
        }
        long totalSectors = size / sectorSize;
        int samples = (int) Math.min(96, totalSectors);
        int data = 0;
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            for (int i = 0; i < samples; i++) {
                long sector = totalSectors <= 96 ? i : (long) i * (totalSectors - 1) / (samples - 1);
                byte[] head = new byte[12];
                in.seek(sector * sectorSize);
                in.readFully(head);
                if (isSync(head)) {
                    data++;
                }
            }
        } catch (IOException e) {
            return 0;
        }
        return samples == 0 ? 0 : (double) data / samples;
    }

    /** 单行文本的全部非空行（用于解析 gdi/toc 这类轨道索引） */
    public static String[] textLines(Path file, int maxBytes) {
        byte[] head = read(file, 0, maxBytes);
        if (head.length == 0) {
            return new String[0];
        }
        String text = new String(head, StandardCharsets.ISO_8859_1);
        String[] lines = text.split("\\r?\\n");
        java.util.List<String> kept = new java.util.ArrayList<>();
        for (String line : lines) {
            String trimmed = line.replace("\uFEFF", "").trim();
            if (!trimmed.isEmpty()) {
                kept.add(trimmed);
            }
        }
        return kept.toArray(new String[0]);
    }

    private static boolean isSync(byte[] head) {
        if (head.length < 12) {
            return false;
        }
        if ((head[0] & 0xFF) != 0x00 || (head[11] & 0xFF) != 0x00) {
            return false;
        }
        for (int i = 1; i <= 10; i++) {
            if ((head[i] & 0xFF) != 0xFF) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasMagic(Path file, String magic) {
        return hasMagic(file, 0, magic);
    }

    public static boolean hasMagic(Path file, int offset, String magic) {
        byte[] expected = magic.getBytes(StandardCharsets.US_ASCII);
        return hasBytes(file, offset, expected);
    }

    public static boolean hasBytes(Path file, int offset, byte[] expected) {
        byte[] actual = read(file, offset, expected.length);
        if (actual.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (actual[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    public static byte[] read(Path file, long offset, int length) {
        if (file == null || length <= 0) {
            return new byte[0];
        }
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            if (offset < 0 || offset >= in.length()) {
                return new byte[0];
            }
            int available = (int) Math.min(length, in.length() - offset);
            byte[] buffer = new byte[available];
            in.seek(offset);
            in.readFully(buffer);
            return buffer;
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /** 读取 ISO9660 卷描述符中的卷标识（可能为空） */
    public static String isoVolumeId(Path iso) {
        byte[] pvd = read(iso, (long) PVD_SECTOR * SECTOR_2048, SECTOR_2048);
        if (pvd.length < 72 || pvd[0] != 1 || pvd[1] != 'C' || pvd[2] != 'D'
                || pvd[3] != '0' || pvd[4] != '0' || pvd[5] != '1') {
            return "";
        }
        String raw = new String(pvd, 40, 32, StandardCharsets.US_ASCII);
        return raw.replace("\u0000", "").trim();
    }
}
