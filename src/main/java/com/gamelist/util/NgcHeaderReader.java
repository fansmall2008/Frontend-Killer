package com.gamelist.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * NGC (GameCube) 光盘镜像头部标识符提取器。
 * <p>
 * NGC 光盘镜像（.iso / .gcm / .bin）的前 6 个字节即为游戏编号（game ID），
 * 例如：GALE01 = 任天堂明星大乱斗DX（美版），GALJ01 = 同作日版。
 * <p>
 * 这是所有光盘平台中最简单的提取方式 — 只需读取文件头 6 字节 ASCII，
 * 无需解析文件系统，零性能开销。
 * <p>
 * 支持的扩展名：.iso, .gcm, .bin
 */
public final class NgcHeaderReader {

    private static final Logger logger = LoggerFactory.getLogger(NgcHeaderReader.class);

    /** NGC game ID 固定长度 */
    private static final int GAME_ID_LENGTH = 6;

    private NgcHeaderReader() {}

    /**
     * 从 NGC 光盘镜像文件提取游戏编号。
     *
     * @param filePath 镜像文件路径（.iso / .gcm / .bin）
     * @return 6 位游戏编号（如 GALE01），失败返回 null
     */
    public static String extractSerial(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) return null;

        if (!isSupportedFormat(filePath)) {
            logger.debug("不支持的 NGC 镜像格式: {}", filePath);
            return null;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < GAME_ID_LENGTH) return null;

            byte[] buf = new byte[GAME_ID_LENGTH];
            raf.readFully(buf);

            // 验证：6 字节必须全部为可打印 ASCII（A-Z, 0-9）
            for (byte b : buf) {
                int c = b & 0xFF;
                if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) {
                    logger.debug("NGC 镜像头部非标准游戏 ID: {}", filePath);
                    return null;
                }
            }

            String gameId = new String(buf, StandardCharsets.US_ASCII).toUpperCase();
            logger.debug("NGC 游戏 ID 提取: {} from {}", gameId, file.getName());
            return gameId;
        } catch (Exception e) {
            logger.debug("NGC 镜像读取失败: {} - {}", file.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 判断文件是否为支持的 NGC 镜像格式。
     */
    public static boolean isSupportedFormat(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        String ext = getFileExtension(filePath);
        return "iso".equalsIgnoreCase(ext)
            || "gcm".equalsIgnoreCase(ext)
            || "bin".equalsIgnoreCase(ext);
    }

    private static String getFileExtension(String filePath) {
        int dot = filePath.lastIndexOf('.');
        return dot >= 0 ? filePath.substring(dot + 1) : "";
    }
}
