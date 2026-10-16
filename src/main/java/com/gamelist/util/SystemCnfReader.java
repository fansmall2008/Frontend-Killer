package com.gamelist.util;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 从光盘镜像文件中读取 SYSTEM.CNF 提取 serial 编号。
 * <p>
 * 支持格式：
 * <ul>
 *   <li>.iso — 标准 ISO 9660（2048 bytes/sector）</li>
 *   <li>.bin/.img — Raw 格式（2352 bytes/sector，含 sync header）</li>
 *   <li>.cso — Compressed ISO（zlib 块压缩）</li>
 *   <li>.pbp — PSP/PS1 打包格式（内含压缩 ISO）</li>
 * </ul>
 * <p>
 * 解析流程：检测格式 → 读 PVD（sector 16）→ 定位根目录 → 找 SYSTEM.CNF → 解析 BOOT/BOOT2 行。
 */
public class SystemCnfReader {

    private static final Logger logger = LoggerFactory.getLogger(SystemCnfReader.class);

    private static final int SECTOR_SIZE = 2048;
    private static final int RAW_SECTOR_SIZE = 2352;
    private static final int RAW_DATA_OFFSET = 24;
    private static final int PVD_SECTOR = 16;

    // CSO constants
    private static final int CSO_MAGIC = 0x4F534943; // "CISO"

    // PBP constants
    private static final int PBP_MAGIC = 0x00504200; // "\0PBP"

    /**
     * 从光盘镜像文件提取 serial 编号。
     *
     * @param filePath 镜像文件路径（.iso / .bin / .img / .cso / .pbp）
     * @return serial 编号（如 SLPM-65200），失败返回 null
     */
    public static String extractSerial(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) return null;

        String ext = getFileExtension(filePath).toLowerCase();
        if (!isSupportedFormat(filePath)) return null;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < SECTOR_SIZE * (PVD_SECTOR + 1)) return null;

            // 检测格式（只做一次，传递给后续方法）
            boolean isRaw = ext.equals("iso") || ext.equals("bin") || ext.equals("img")
                    ? detectRawFormat(raf) : false;

            // 检测格式并读取 ISO 数据
            byte[] pvd;
            if (ext.equals("cso")) {
                pvd = readPvdFromCso(raf);
            } else if (ext.equals("pbp")) {
                pvd = readPvdFromPbp(raf);
            } else {
                pvd = readPvdFromIso(raf, isRaw);
            }

            if (pvd == null) return null;

            // 验证 PVD 标志
            if (pvd[0] != 1 || pvd[1] != 'C' || pvd[2] != 'D' || pvd[3] != '0'
                    || pvd[4] != '0' || pvd[5] != '1') {
                logger.debug("非标准 ISO9660: {}", filePath);
                return null;
            }

            // 根目录记录在 PVD 偏移 156
            int rootExtent = readLittleEndianInt(pvd, 156 + 2);
            int rootSize = readLittleEndianInt(pvd, 156 + 10);
            if (rootExtent <= 0 || rootSize <= 0 || rootSize > 10 * 1024 * 1024) return null;

            // 读根目录
            byte[] rootDir;
            if (ext.equals("cso")) {
                rootDir = readDirFromCso(raf, rootExtent, rootSize);
            } else if (ext.equals("pbp")) {
                rootDir = readDirFromPbp(raf, rootExtent, rootSize);
            } else {
                rootDir = readDirFromIso(raf, rootExtent, rootSize, isRaw);
            }

            // ★ 优先：扫描根目录文件名提取 serial（零额外 I/O）
            // PS1/PS2 光盘根目录有可执行文件，文件名本身就是编号（如 SLUS_200.01）
            String rootSerial = scanRootDirForSerial(rootDir);
            if (rootSerial != null) {
                logger.debug("SystemCnfReader: 根目录文件名提取 serial={} (跳过 SYSTEM.CNF)", rootSerial);
                return rootSerial;
            }

            // 回退：读 SYSTEM.CNF 提取 serial（需要额外一次 seek）
            int[] cnfLoc = findFileInDirectory(rootDir, "SYSTEM.CNF;1");
            if (cnfLoc == null) cnfLoc = findFileInDirectory(rootDir, "SYSTEM.CNF");
            if (cnfLoc == null) {
                logger.debug("未找到 SYSTEM.CNF: {}", filePath);
                return null;
            }

            // 读 SYSTEM.CNF 内容
            byte[] cnfData;
            if (ext.equals("cso")) {
                cnfData = readDirFromCso(raf, cnfLoc[0], cnfLoc[1]);
            } else if (ext.equals("pbp")) {
                cnfData = readDirFromPbp(raf, cnfLoc[0], cnfLoc[1]);
            } else {
                cnfData = readDirFromIso(raf, cnfLoc[0], cnfLoc[1], isRaw);
            }

            String cnfContent = new String(cnfData, StandardCharsets.ISO_8859_1).trim();
            return parseSerialFromCnf(cnfContent);

        } catch (Exception e) {
            logger.debug("读取 SYSTEM.CNF 失败: {} - {}", filePath, e.getMessage());
            return null;
        }
    }

    // ==================== ISO / Raw BIN ====================

    /** 从 ISO/Raw 文件读 PVD */
    private static byte[] readPvdFromIso(RandomAccessFile raf, boolean isRaw) throws Exception {
        return readIsoSector(raf, isRaw, PVD_SECTOR);
    }

    /** 从 ISO/Raw 读目录数据（优化：一次性读取，避免多次 seek） */
    private static byte[] readDirFromIso(RandomAccessFile raf, int startSector, int size, boolean isRaw) throws Exception {
        if (!isRaw) {
            // 标准 ISO：连续存储，一次 read 即可
            long offset = (long) startSector * SECTOR_SIZE;
            raf.seek(offset);
            byte[] data = new byte[size];
            int toRead = size;
            int totalRead = 0;
            while (toRead > 0) {
                int read = raf.read(data, totalRead, toRead);
                if (read < 0) break;
                totalRead += read;
                toRead -= read;
            }
            return data;
        } else {
            // Raw BIN：每个扇区有 24 字节头部，需要跳过
            long offset = (long) startSector * RAW_SECTOR_SIZE + RAW_DATA_OFFSET;
            raf.seek(offset);
            byte[] data = new byte[size];
            int toRead = size;
            int totalRead = 0;
            while (toRead > 0) {
                // 当前扇区剩余可用字节
                long curPos = raf.getFilePointer();
                long sectorStart = ((curPos - RAW_DATA_OFFSET) / RAW_SECTOR_SIZE) * RAW_SECTOR_SIZE + RAW_DATA_OFFSET;
                int available = (int) (sectorStart + RAW_SECTOR_SIZE - curPos);
                if (available <= 0) {
                    // 跳到下一个扇区的数据区
                    long nextSector = ((curPos - RAW_DATA_OFFSET) / RAW_SECTOR_SIZE + 1) * RAW_SECTOR_SIZE + RAW_DATA_OFFSET;
                    raf.seek(nextSector);
                    continue;
                }
                int toReadNow = Math.min(toRead, available);
                int read = raf.read(data, totalRead, toReadNow);
                if (read < 0) break;
                totalRead += read;
                toRead -= read;
                if (toRead > 0) {
                    // 跳到下一个扇区的数据区
                    long nextSector = ((raf.getFilePointer() - RAW_DATA_OFFSET) / RAW_SECTOR_SIZE + 1) * RAW_SECTOR_SIZE + RAW_DATA_OFFSET;
                    raf.seek(nextSector);
                }
            }
            return data;
        }
    }

    /** 读取一个逻辑扇区（ISO/Raw） */
    private static byte[] readIsoSector(RandomAccessFile raf, boolean isRaw, long sector) throws Exception {
        long offset = isRaw ? sector * RAW_SECTOR_SIZE + RAW_DATA_OFFSET : sector * SECTOR_SIZE;
        raf.seek(offset);
        byte[] data = new byte[SECTOR_SIZE];
        raf.readFully(data);
        return data;
    }

    // ==================== CSO ====================

    /**
     * 从 CSO 文件读 PVD。
     * CSO 结构：Header(24) + IndexTable(N+1 entries) + Compressed Data Blocks
     */
    private static byte[] readPvdFromCso(RandomAccessFile raf) throws Exception {
        raf.seek(0);
        byte[] header = new byte[24];
        raf.readFully(header);

        int magic = readBigEndianInt(header, 0);
        if (magic != CSO_MAGIC) {
            logger.debug("非 CSO 格式: magic=0x{}", Integer.toHexString(magic));
            return null;
        }

        int blockSize = readBigEndianInt(header, 8);
        if (blockSize <= 0 || blockSize > 1024 * 1024) {
            logger.debug("CSO block size 异常: {}", blockSize);
            return null;
        }

        // 读 index table（需要 PVD sector 所在的 block + 1）
        int pvdByteOffset = PVD_SECTOR * SECTOR_SIZE;
        int targetBlock = pvdByteOffset / blockSize;
        int[] indexTable = readCsoIndexTable(raf, targetBlock + 1);
        if (indexTable == null) return null;

        // 读 PVD 所在的 block 并解压
        byte[] decompressed = decompressCsoBlock(raf, indexTable, targetBlock, blockSize);
        if (decompressed == null) return null;

        // 从解压后的 block 中提取 PVD sector
        int offsetInBlock = pvdByteOffset - targetBlock * blockSize;
        if (offsetInBlock + SECTOR_SIZE > decompressed.length) {
            logger.debug("CSO block 内 PVD 偏移越界");
            return null;
        }
        byte[] pvd = new byte[SECTOR_SIZE];
        System.arraycopy(decompressed, offsetInBlock, pvd, 0, SECTOR_SIZE);
        return pvd;
    }

    /** 从 CSO 读目录数据 */
    private static byte[] readDirFromCso(RandomAccessFile raf, int startSector, int size) throws Exception {
        raf.seek(0);
        byte[] header = new byte[24];
        raf.readFully(header);
        int blockSize = readBigEndianInt(header, 8);

        int startByte = startSector * SECTOR_SIZE;
        int firstBlock = startByte / blockSize;
        int lastBlock = (startByte + size - 1) / blockSize;
        int[] indexTable = readCsoIndexTable(raf, lastBlock + 1);
        if (indexTable == null) return null;

        // 解压需要的 blocks 并拼接
        byte[] result = new byte[size];
        int resultOffset = 0;
        int currentByte = startByte;

        while (resultOffset < size) {
            int blockIdx = currentByte / blockSize;
            int offsetInBlock = currentByte - blockIdx * blockSize;

            byte[] decompressed = decompressCsoBlock(raf, indexTable, blockIdx, blockSize);
            if (decompressed == null) break;

            int available = decompressed.length - offsetInBlock;
            int needed = Math.min(available, size - resultOffset);
            System.arraycopy(decompressed, offsetInBlock, result, resultOffset, needed);
            resultOffset += needed;
            currentByte += needed;
        }
        return result;
    }

    /** 读 CSO index table */
    private static int[] readCsoIndexTable(RandomAccessFile raf, int minEntries) throws Exception {
        raf.seek(24); // 跳过 header
        // 先估算需要的 entries 数量，读足够的数据
        int entriesToRead = Math.max(minEntries + 2, 1024);
        byte[] indexData = new byte[entriesToRead * 4];
        int read = raf.read(indexData);
        if (read < (minEntries + 1) * 4) {
            // 数据不够，尝试读更多
            entriesToRead = read / 4;
        }

        int[] index = new int[entriesToRead];
        for (int i = 0; i < entriesToRead; i++) {
            index[i] = readBigEndianInt(indexData, i * 4);
        }
        return index;
    }

    /** 解压 CSO 单个 block */
    private static byte[] decompressCsoBlock(RandomAccessFile raf, int[] indexTable, int blockIdx, int blockSize) throws Exception {
        if (blockIdx >= indexTable.length - 1) return null;

        int entry = indexTable[blockIdx];
        int nextEntry = indexTable[blockIdx + 1];

        boolean uncompressed = (entry & 0x80000000) != 0;
        long fileOffset = (long) (entry & 0x7FFFFFFF) << 0; // 实际偏移需要看 CSO 版本
        // CSO v1: offset 是 4 字节对齐的，低 0 位是偏移
        fileOffset = entry & 0x7FFFFFFFL;
        long nextOffset = nextEntry & 0x7FFFFFFFL;
        long compressedSize = nextOffset - fileOffset;

        if (compressedSize <= 0 || compressedSize > blockSize * 2) return null;

        raf.seek(fileOffset);
        byte[] compressed = new byte[(int) compressedSize];
        raf.readFully(compressed);

        if (uncompressed) {
            return compressed;
        }

        // zlib 解压
        byte[] output = new byte[blockSize];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            int decompressedSize = inflater.inflate(output);
            if (decompressedSize <= 0) {
                logger.debug("CSO block {} zlib 解压失败", blockIdx);
                return null;
            }
            if (decompressedSize < blockSize) {
                byte[] trimmed = new byte[decompressedSize];
                System.arraycopy(output, 0, trimmed, 0, decompressedSize);
                return trimmed;
            }
            return output;
        } catch (DataFormatException e) {
            logger.debug("CSO block {} 解压异常: {}", blockIdx, e.getMessage());
            return null;
        } finally {
            inflater.end();
        }
    }

    // ==================== PBP ====================

    /**
     * 从 PBP 文件读 PVD。
     * PBP 结构：Header(40) + DATA.PSAR（内含 zlib 压缩的 ISO）
     */
    private static byte[] readPvdFromPbp(RandomAccessFile raf) throws Exception {
        long[] pbpOffsets = readPbpHeader(raf);
        if (pbpOffsets == null) return null;

        // DATA.PSAR 在 offset[5]，结束于 offset[6] 或文件末尾
        long isoStart = pbpOffsets[5];
        long isoEnd = pbpOffsets[6] > isoStart ? pbpOffsets[6] : raf.length();

        if (isoStart <= 0 || isoEnd <= isoStart) return null;

        // DATA.PSAR 内部结构：
        // 前 4 字节: "\0PSP" 或 "PSAR"
        raf.seek(isoStart);
        byte[] psarHeader = new byte[8];
        raf.read(psarHeader);

        long dataStart;
        if (psarHeader[0] == 0x00 && psarHeader[1] == 'P' && psarHeader[2] == 'S' && psarHeader[3] == 'P') {
            // PSP 格式: 偏移 0x24 处有 ISO 表
            dataStart = isoStart + 0x28;
        } else if (psarHeader[0] == 'P' && psarHeader[1] == 'S' && psarHeader[2] == 'A' && psarHeader[3] == 'R') {
            dataStart = isoStart + 0x28;
        } else {
            // 直接是 ISO 数据
            dataStart = isoStart;
        }

        // 尝试读取并解压 ISO 数据
        // PBP 中的 ISO 通常是一个大的 zlib 压缩流
        return decompressPbpIso(raf, dataStart, isoEnd);
    }

    /** 从 PBP 读目录数据 */
    private static byte[] readDirFromPbp(RandomAccessFile raf, int startSector, int size) throws Exception {
        long[] pbpOffsets = readPbpHeader(raf);
        if (pbpOffsets == null) return null;

        long isoStart = pbpOffsets[5];
        long isoEnd = pbpOffsets[6] > isoStart ? pbpOffsets[6] : raf.length();

        raf.seek(isoStart);
        byte[] psarHeader = new byte[8];
        raf.read(psarHeader);
        long dataStart;
        if ((psarHeader[0] == 0x00 || psarHeader[0] == 'P') && psarHeader[1] == 'P') {
            dataStart = isoStart + 0x28;
        } else {
            dataStart = isoStart;
        }

        // 解压整个 ISO 数据到临时 byte array（PBP 通常不大，PS1 游戏 < 700MB）
        byte[] isoData = decompressFullPbpIso(raf, dataStart, isoEnd);
        if (isoData == null) return null;

        int startByte = startSector * SECTOR_SIZE;
        if (startByte + size > isoData.length) {
            size = isoData.length - startByte;
        }
        if (size <= 0) return null;

        byte[] result = new byte[size];
        System.arraycopy(isoData, startByte, result, 0, size);
        return result;
    }

    /** 读 PBP header，返回 8 个 section offset */
    private static long[] readPbpHeader(RandomAccessFile raf) throws Exception {
        raf.seek(0);
        byte[] header = new byte[40];
        raf.readFully(header);

        int magic = readLittleEndianInt(header, 0);
        // PBP magic: 0x00504200 ("\0PBP\0")
        if ((magic & 0x00FFFFFF) != 0x00504200 && header[0] != 0x00) {
            // 也接受直接检查 "\0PBP"
            if (header[0] != 0x00 || header[1] != 'P' || header[2] != 'B' || header[3] != 'P') {
                logger.debug("非 PBP 格式");
                return null;
            }
        }

        long[] offsets = new long[8];
        for (int i = 0; i < 8; i++) {
            offsets[i] = readLittleEndianInt(header, 8 + i * 4) & 0xFFFFFFFFL;
        }
        return offsets;
    }

    /** 尝试从 PBP 的 DATA.PSAR 解压 PVD sector */
    private static byte[] decompressPbpIso(RandomAccessFile raf, long dataStart, long dataEnd) throws Exception {
        // 读取压缩数据
        long dataSize = dataEnd - dataStart;
        if (dataSize <= 0 || dataSize > 500 * 1024 * 1024) return null;

        raf.seek(dataStart);
        byte[] compressed = new byte[(int) Math.min(dataSize, 10 * 1024 * 1024)]; // 先读 10MB
        int readBytes = raf.read(compressed);
        if (readBytes <= 0) return null;

        // 尝试 zlib 解压
        byte[] output = new byte[SECTOR_SIZE * (PVD_SECTOR + 2)]; // 至少解压到 PVD
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed, 0, readBytes);
            int decompressedSize = inflater.inflate(output);
            if (decompressedSize < SECTOR_SIZE * (PVD_SECTOR + 1)) {
                logger.debug("PBP 解压数据不足: {} bytes", decompressedSize);
                return null;
            }

            byte[] pvd = new byte[SECTOR_SIZE];
            System.arraycopy(output, PVD_SECTOR * SECTOR_SIZE, pvd, 0, SECTOR_SIZE);
            return pvd;
        } catch (DataFormatException e) {
            logger.debug("PBP zlib 解压失败: {}", e.getMessage());
            return null;
        } finally {
            inflater.end();
        }
    }

    /** 完整解压 PBP ISO 数据 */
    private static byte[] decompressFullPbpIso(RandomAccessFile raf, long dataStart, long dataEnd) throws Exception {
        long dataSize = dataEnd - dataStart;
        if (dataSize <= 0) return null;

        raf.seek(dataStart);
        // 分块读取压缩数据
        int chunkSize = (int) Math.min(dataSize, 50 * 1024 * 1024);
        byte[] compressed = new byte[chunkSize];
        int readBytes = raf.read(compressed);
        if (readBytes <= 0) return null;

        // 用 Inflater 解压（可能很大）
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed, 0, readBytes);
            // 逐步解压
            byte[] result = new byte[SECTOR_SIZE * 2048]; // 初始 4MB
            int totalDecompressed = 0;
            byte[] buf = new byte[65536];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n <= 0) {
                    if (inflater.needsInput()) break;
                    if (inflater.finished()) break;
                    logger.debug("PBP 解压异常");
                    return null;
                }
                // 扩展 result 数组
                if (totalDecompressed + n > result.length) {
                    byte[] newResult = new byte[result.length * 2];
                    System.arraycopy(result, 0, newResult, 0, totalDecompressed);
                    result = newResult;
                }
                System.arraycopy(buf, 0, result, totalDecompressed, n);
                totalDecompressed += n;
                // 安全限制：最大 800MB
                if (totalDecompressed > 800 * 1024 * 1024) break;
            }
            if (totalDecompressed <= 0) return null;

            byte[] finalResult = new byte[totalDecompressed];
            System.arraycopy(result, 0, finalResult, 0, totalDecompressed);
            return finalResult;
        } catch (DataFormatException e) {
            logger.debug("PBP 完整解压失败: {}", e.getMessage());
            return null;
        } finally {
            inflater.end();
        }
    }

    // ==================== 通用工具方法 ====================

    /** 从 SYSTEM.CNF 内容解析 serial */
    static String parseSerialFromCnf(String content) {
        for (String line : content.split("[\\r\\n]+")) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String upper = line.toUpperCase();
            if (upper.startsWith("BOOT2") || upper.startsWith("BOOT")) {
                int eqIdx = line.indexOf('=');
                if (eqIdx < 0) continue;

                String path = line.substring(eqIdx + 1).trim();
                int colonIdx = path.indexOf(':');
                if (colonIdx < 0) continue;

                String serial = path.substring(colonIdx + 1);
                serial = serial.replaceAll("^\\\\+", "");
                serial = serial.replaceAll(";.*$", "");
                serial = serial.replace('_', '-');
                serial = serial.replace(".", "");
                serial = serial.trim().toUpperCase();

                if (!serial.isEmpty()) return serial;
            }
        }
        return null;
    }

    /** 检测 raw BIN 格式 */
    static boolean detectRawFormat(RandomAccessFile raf) throws Exception {
        raf.seek(0);
        byte[] sync = new byte[12];
        if (raf.read(sync) < 12) return false;
        return sync[0] == 0x00 && (sync[1] & 0xFF) == 0xFF && (sync[2] & 0xFF) == 0xFF
                && (sync[3] & 0xFF) == 0xFF && (sync[4] & 0xFF) == 0xFF && (sync[5] & 0xFF) == 0xFF
                && (sync[6] & 0xFF) == 0xFF && (sync[7] & 0xFF) == 0xFF && (sync[8] & 0xFF) == 0xFF
                && (sync[9] & 0xFF) == 0xFF && (sync[10] & 0xFF) == 0xFF && sync[11] == 0x00;
    }

    /** 在目录数据中查找文件 */
    static int[] findFileInDirectory(byte[] dirData, String targetName) {
        int pos = 0;
        while (pos < dirData.length) {
            int recordLen = dirData[pos] & 0xFF;
            if (recordLen == 0) {
                pos = ((pos / SECTOR_SIZE) + 1) * SECTOR_SIZE;
                continue;
            }
            if (pos + 33 >= dirData.length) break;

            int nameLen = dirData[pos + 32] & 0xFF;
            if (nameLen > 0 && pos + 33 + nameLen <= dirData.length) {
                String name = new String(dirData, pos + 33, nameLen, StandardCharsets.ISO_8859_1);
                if (name.equalsIgnoreCase(targetName)) {
                    int extent = readLittleEndianInt(dirData, pos + 2);
                    int fileSize = readLittleEndianInt(dirData, pos + 10);
                    return new int[]{extent, fileSize};
                }
            }
            pos += recordLen;
        }
        return null;
    }

    /** PS1/PS2 光盘根目录文件名 serial 正则（SLUS_200.01 格式） */
    private static final Pattern ROOT_SERIAL_PATTERN = Pattern.compile(
            "^([A-Z]{4}[-]?\\d{5})$");

    /**
     * 扫描根目录文件名，尝试提取 PS1/PS2 serial。
     * <p>
     * PS1/PS2 光盘根目录通常有可执行文件，文件名本身就是编号，
     * 如 SLUS_200.01;1 → SLUS-20001。无需读取 SYSTEM.CNF。
     *
     * @param rootDir 根目录数据
     * @return 归一化后的 serial（如 SLUS-20001），未匹配返回 null
     */
    static String scanRootDirForSerial(byte[] rootDir) {
        if (rootDir == null || rootDir.length == 0) return null;

        int pos = 0;
        while (pos < rootDir.length) {
            int recordLen = rootDir[pos] & 0xFF;
            if (recordLen == 0) {
                pos = ((pos / SECTOR_SIZE) + 1) * SECTOR_SIZE;
                continue;
            }
            if (pos + 33 >= rootDir.length) break;

            int nameLen = rootDir[pos + 32] & 0xFF;
            if (nameLen > 0 && nameLen <= 32 && pos + 33 + nameLen <= rootDir.length) {
                String rawName = new String(rootDir, pos + 33, nameLen, StandardCharsets.ISO_8859_1);

                // 去掉 ISO 9660 版本号后缀 (;1 等)
                String cleanName = rawName.replaceAll(";\\d+$", "").trim();
                // 规范化：去点、下划线→横线、大写（与 parseSerialFromCnf 一致）
                String normalized = cleanName.replace(".", "").replace('_', '-').toUpperCase();

                // 必须以 S 开头（Sony 标准编号前缀）且长度合理
                if (normalized.startsWith("S") && normalized.length() >= 8 && normalized.length() <= 12) {
                    Matcher m = ROOT_SERIAL_PATTERN.matcher(normalized);
                    if (m.matches()) {
                        return m.group(1);
                    }
                }
            }
            pos += recordLen;
        }
        return null;
    }

    static int readLittleEndianInt(byte[] data, int offset) {
        if (offset + 3 >= data.length) return 0;
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    static int readBigEndianInt(byte[] data, int offset) {
        if (offset + 3 >= data.length) return 0;
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    static String getFileExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : "";
    }

    /** 判断文件是否为支持的光盘镜像格式 */
    public static boolean isSupportedFormat(String filePath) {
        if (filePath == null) return false;
        String ext = getFileExtension(filePath).toLowerCase();
        return ext.equals("iso") || ext.equals("bin") || ext.equals("img")
                || ext.equals("cso") || ext.equals("pbp");
    }
}
