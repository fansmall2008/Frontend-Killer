package com.gamelist.util;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 从 PS3 光盘镜像文件中读取 PARAM.SFO 提取 TITLE_ID。
 * <p>
 * PS3 ISO 使用 ISO9660 文件系统，根目录下有 PS3_GAME/ 子目录，
 * 其中包含 PARAM.SFO 文件，内含 TITLE_ID（如 BLJM61303）。
 * <p>
 * 支持格式：.iso / .bin / .img（标准 ISO9660 和 Raw 2352 扇区格式）。
 */
public class ParamSfoReader {

    private static final Logger logger = LoggerFactory.getLogger(ParamSfoReader.class);

    private static final int SECTOR_SIZE = 2048;
    private static final int RAW_SECTOR_SIZE = 2352;
    private static final int RAW_DATA_OFFSET = 24;
    private static final int PVD_SECTOR = 16;

    // SFO magic: \0PSF
    private static final int SFO_MAGIC = 0x00505346;

    /**
     * 从 PS3 光盘镜像文件提取 TITLE_ID。
     *
     * @param filePath 镜像文件路径（.iso / .bin / .img）
     * @return TITLE_ID（如 BLJM-61303），失败返回 null
     */
    public static String extractSerial(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) return null;

        String ext = SystemCnfReader.getFileExtension(filePath).toLowerCase();
        if (!ext.equals("iso") && !ext.equals("bin") && !ext.equals("img")) return null;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < SECTOR_SIZE * (PVD_SECTOR + 1)) return null;

            boolean isRaw = SystemCnfReader.detectRawFormat(raf);

            // Step 1: Read PVD
            byte[] pvd = readIsoSector(raf, isRaw, PVD_SECTOR);
            if (pvd == null) return null;

            // Validate PVD
            if (pvd[0] != 1 || pvd[1] != 'C' || pvd[2] != 'D' || pvd[3] != '0'
                    || pvd[4] != '0' || pvd[5] != '1') {
                logger.debug("非标准 ISO9660: {}", filePath);
                return null;
            }

            // Step 2: Find root directory
            int rootExtent = SystemCnfReader.readLittleEndianInt(pvd, 156 + 2);
            int rootSize = SystemCnfReader.readLittleEndianInt(pvd, 156 + 10);
            if (rootExtent <= 0 || rootSize <= 0 || rootSize > 10 * 1024 * 1024) return null;

            // Step 3: Scan root directory for PS3_GAME
            byte[] rootDir = readDirData(raf, isRaw, rootExtent, rootSize);
            if (rootDir == null) return null;

            int[] ps3GameLoc = findDirInDirectory(rootDir, "PS3_GAME");
            if (ps3GameLoc == null) {
                logger.debug("未找到 PS3_GAME 目录: {}", filePath);
                return null;
            }

            // Step 4: Scan PS3_GAME directory for PARAM.SFO
            byte[] ps3GameDir = readDirData(raf, isRaw, ps3GameLoc[0], ps3GameLoc[1]);
            if (ps3GameDir == null) return null;

            int[] sfoLoc = findFileInDirectory(ps3GameDir, "PARAM.SFO");
            if (sfoLoc == null) {
                sfoLoc = findFileInDirectory(ps3GameDir, "PARAM.SFO;1");
            }
            if (sfoLoc == null) {
                logger.debug("未找到 PARAM.SFO: {}", filePath);
                return null;
            }

            // Step 5: Read PARAM.SFO
            byte[] sfoData = readDirData(raf, isRaw, sfoLoc[0], sfoLoc[1]);
            if (sfoData == null) return null;

            // Step 6: Parse SFO and extract TITLE_ID
            return parseTitleIdFromSfo(sfoData);

        } catch (Exception e) {
            logger.debug("读取 PARAM.SFO 失败: {} - {}", filePath, e.getMessage());
            return null;
        }
    }

    /**
     * 解析 PARAM.SFO 文件内容，提取 TITLE_ID。
     */
    static String parseTitleIdFromSfo(byte[] data) {
        if (data.length < 20) return null;

        int magic = readBigEndianInt(data, 0);
        if (magic != SFO_MAGIC) {
            logger.debug("非 SFO 格式: magic=0x{}", Integer.toHexString(magic));
            return null;
        }

        int keyTableOffset = readLittleEndianInt(data, 8);
        int dataTableOffset = readLittleEndianInt(data, 12);
        int numEntries = readLittleEndianInt(data, 16);

        if (numEntries <= 0 || numEntries > 100) return null;
        if (keyTableOffset >= data.length || dataTableOffset >= data.length) return null;

        for (int i = 0; i < numEntries; i++) {
            int idxOffset = 20 + i * 16;
            if (idxOffset + 16 > data.length) break;

            int keyOffset = readLittleEndianInt(data, idxOffset) & 0xFFFF;
            int dataLen = readLittleEndianInt(data, idxOffset + 4);
            int dataValueOffset = readLittleEndianInt(data, idxOffset + 12);

            // Read key string
            int keyStart = keyTableOffset + keyOffset;
            if (keyStart >= data.length) continue;
            int keyEnd = keyStart;
            while (keyEnd < data.length && data[keyEnd] != 0) keyEnd++;
            String key = new String(data, keyStart, keyEnd - keyStart, StandardCharsets.UTF_8);

            if ("TITLE_ID".equals(key)) {
                int valStart = dataTableOffset + dataValueOffset;
                int valEnd = Math.min(valStart + dataLen, data.length);
                String titleId = new String(data, valStart, valEnd - valStart, StandardCharsets.UTF_8).trim();

                // Format: BLJM61303 -> BLJM-61303
                return formatSerial(titleId);
            }
        }

        logger.debug("PARAM.SFO 中未找到 TITLE_ID");
        return null;
    }

    /**
     * 格式化 PS3 serial: BLJM61303 -> BLJM-61303
     */
    static String formatSerial(String serial) {
        if (serial == null || serial.isEmpty()) return null;
        // Remove any whitespace
        String s = serial.replaceAll("\\s+", "");
        // Already formatted: BLES-01234
        if (s.matches("[A-Z]{4}-\\d{4,5}")) return s;
        // Unformatted: BLES01234 or BLJM61303
        if (s.matches("[A-Z]{4}\\d{4,5}")) {
            return s.substring(0, 4) + "-" + s.substring(4);
        }
        return s;
    }

    // ==================== ISO 读取辅助方法 ====================

    private static byte[] readIsoSector(RandomAccessFile raf, boolean isRaw, long sector) throws Exception {
        long offset = isRaw ? sector * RAW_SECTOR_SIZE + RAW_DATA_OFFSET : sector * SECTOR_SIZE;
        raf.seek(offset);
        byte[] data = new byte[SECTOR_SIZE];
        raf.readFully(data);
        return data;
    }

    private static byte[] readDirData(RandomAccessFile raf, boolean isRaw, int startSector, int size) throws Exception {
        long offset = isRaw ? startSector * RAW_SECTOR_SIZE + RAW_DATA_OFFSET : startSector * SECTOR_SIZE;
        raf.seek(offset);
        byte[] data = new byte[size];
        int toRead = size;
        while (toRead > 0) {
            int read = raf.read(data, size - toRead, toRead);
            if (read < 0) break;
            toRead -= read;
            // Handle raw sector boundaries
            if (isRaw && toRead > 0) {
                long curPos = raf.getFilePointer();
                long logicalPos = curPos - RAW_DATA_OFFSET;
                long nextSector = (logicalPos / RAW_SECTOR_SIZE + 1) * RAW_SECTOR_SIZE + RAW_DATA_OFFSET;
                raf.seek(nextSector);
            }
        }
        return data;
    }

    /** 在目录中查找子目录 */
    static int[] findDirInDirectory(byte[] dirData, String targetName) {
        int pos = 0;
        while (pos < dirData.length) {
            int recordLen = dirData[pos] & 0xFF;
            if (recordLen == 0) {
                pos = ((pos / SECTOR_SIZE) + 1) * SECTOR_SIZE;
                continue;
            }
            if (pos + 33 >= dirData.length) break;

            int nameLen = dirData[pos + 32] & 0xFF;
            boolean isDir = (dirData[pos + 25] & 0x02) != 0;

            if (nameLen > 0 && pos + 33 + nameLen <= dirData.length) {
                String name = new String(dirData, pos + 33, nameLen, StandardCharsets.ISO_8859_1);
                // Remove version number
                int semi = name.indexOf(';');
                if (semi > 0) name = name.substring(0, semi);

                if (name.equalsIgnoreCase(targetName) && isDir) {
                    int extent = SystemCnfReader.readLittleEndianInt(dirData, pos + 2);
                    int fileSize = SystemCnfReader.readLittleEndianInt(dirData, pos + 10);
                    return new int[]{extent, fileSize};
                }
            }
            pos += recordLen;
        }
        return null;
    }

    /** 在目录中查找文件 */
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
                    int extent = SystemCnfReader.readLittleEndianInt(dirData, pos + 2);
                    int fileSize = SystemCnfReader.readLittleEndianInt(dirData, pos + 10);
                    return new int[]{extent, fileSize};
                }
            }
            pos += recordLen;
        }
        return null;
    }

    private static int readBigEndianInt(byte[] data, int offset) {
        if (offset + 3 >= data.length) return 0;
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static int readLittleEndianInt(byte[] data, int offset) {
        return SystemCnfReader.readLittleEndianInt(data, offset);
    }
}
