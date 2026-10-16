package com.gamelist.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * RARCHDB 二进制格式解析器（libretro-database 的 .rdb 文件）。
 * <p>
 * 格式规范（来自 libretro-db/README.md + 实测逆向）：
 * <ul>
 *   <li>文件头 16 字节：8 字节 "RARCHDB\0" + 8 字节 uint64 metadata_offset（大端）</li>
 *   <li>数据区从 offset 16 开始：一串 msgpack 编码的 map（每个 map = 一个游戏条目）</li>
 *   <li>以 NIL 哨兵 (0xc0) 结束数据区</li>
 *   <li>末尾是 metadata（本解析器不关心）</li>
 * </ul>
 * <p>
 * 本解析器只实现 RARCHDB 实际用到的 msgpack 子集：
 * fixmap/map16, fixstr/str8/str16, bin8/bin16, positive fixint/uint8-64/int8, nil。
 * <p>
 * 返回结构：List&lt;Map&lt;String, Object&gt;&gt;，value 类型为 String / byte[] / Long / null。
 */
public final class RdbParser {

    private static final Logger logger = LoggerFactory.getLogger(RdbParser.class);

    private static final byte[] MAGIC = "RARCHDB\0".getBytes(StandardCharsets.US_ASCII);

    private RdbParser() {}

    /**
     * 解析一个 .rdb 文件，返回所有游戏条目。
     *
     * @param file .rdb 文件
     * @return 条目列表（每个条目是 key→value 的 map），解析失败返回空列表
     */
    public static List<Map<String, Object>> parse(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] data = new byte[(int) raf.length()];
            raf.readFully(data);
            return parse(data);
        } catch (IOException e) {
            logger.error("RDB 读取失败: {} - {}", file.getName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 RARCHDB 字节数组。
     */
    public static List<Map<String, Object>> parse(byte[] data) {
        if (data == null || data.length < 17) {
            return Collections.emptyList();
        }
        // 验证文件头
        for (int i = 0; i < MAGIC.length; i++) {
            if (data[i] != MAGIC[i]) {
                return Collections.emptyList();
            }
        }
        // 跳过 16 字节头，从 offset 16 开始读 msgpack 流
        Cursor cursor = new Cursor(data, 16);
        List<Map<String, Object>> entries = new ArrayList<>();

        while (cursor.pos < data.length) {
            int b = data[cursor.pos] & 0xFF;
            if (b == 0xc0) {
                // NIL 哨兵 = 数据区结束
                break;
            }
            Object val = decode(cursor);
            if (val instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) val;
                entries.add(map);
            } else {
                // 非 map 类型 = 异常，停止
                logger.debug("RDB 解析到非 map 类型，停止: pos={}", cursor.pos);
                break;
            }
        }
        return entries;
    }

    // ======================== msgpack 解码 ========================

    private static Object decode(Cursor c) {
        int b = c.readU8();

        // nil
        if (b == 0xc0) return null;

        // positive fixint (0x00 - 0x7f)
        if (b <= 0x7f) return (long) b;

        // negative fixint (0xe0 - 0xff)
        if (b >= 0xe0) return (long) (b - 256);

        // fixmap (0x80 - 0x8f)
        if ((b & 0xf0) == 0x80) return decodeMap(c, b & 0x0f);

        // fixstr (0xa0 - 0xbf)
        if ((b & 0xe0) == 0xa0) return decodeStr(c, b & 0x1f);

        // fixarray (0x90 - 0x9f) — RARCHDB 中少见，但跳过
        if ((b & 0xf0) == 0x90) return decodeArray(c, b & 0x0f);

        switch (b) {
            // --- map ---
            case 0xde: return decodeMap(c, c.readU16());
            case 0xdf: return decodeMap(c, (int) c.readU32());

            // --- array ---
            case 0xdc: return decodeArray(c, c.readU16());
            case 0xdd: return decodeArray(c, (int) c.readU32());

            // --- str ---
            case 0xd9: return decodeStr(c, c.readU8());
            case 0xda: return decodeStr(c, c.readU16());
            case 0xdb: return decodeStr(c, (int) c.readU32());

            // --- bin ---
            case 0xc4: return decodeBin(c, c.readU8());
            case 0xc5: return decodeBin(c, c.readU16());
            case 0xc6: return decodeBin(c, (int) c.readU32());

            // --- uint ---
            case 0xcc: return (long) c.readU8();
            case 0xcd: return (long) c.readU16();
            case 0xce: return c.readU32();
            case 0xcf: return c.readU64();

            // --- int ---
            case 0xd0: return (long) (byte) c.readU8();
            case 0xd1: return (long) (short) c.readU16();
            case 0xd2: return (long) (int) c.readU32();
            case 0xd3: return c.readU64();

            // --- bool ---
            case 0xc2: return Boolean.FALSE;
            case 0xc3: return Boolean.TRUE;

            // --- float/double (RARCHDB 不用，但保留) ---
            case 0xca: return Float.intBitsToFloat((int) c.readU32());
            case 0xcb: return Double.longBitsToDouble(c.readU64());

            default:
                logger.debug("msgpack 未知类型 0x{} at pos={}", Integer.toHexString(b), c.pos - 1);
                return null;
        }
    }

    private static Map<String, Object> decodeMap(Cursor c, int size) {
        Map<String, Object> map = new LinkedHashMap<>(Math.min(size, 32));
        for (int i = 0; i < size; i++) {
            Object key = decode(c);
            Object val = decode(c);
            String k = (key instanceof byte[]) ? new String((byte[]) key, StandardCharsets.UTF_8) : String.valueOf(key);
            map.put(k, val);
        }
        return map;
    }

    private static List<Object> decodeArray(Cursor c, int size) {
        List<Object> list = new ArrayList<>(Math.min(size, 16));
        for (int i = 0; i < size; i++) {
            list.add(decode(c));
        }
        return list;
    }

    private static String decodeStr(Cursor c, int len) {
        byte[] buf = c.readBytes(len);
        return new String(buf, StandardCharsets.UTF_8);
    }

    private static byte[] decodeBin(Cursor c, int len) {
        return c.readBytes(len);
    }

    // ======================== 游标 ========================

    private static final class Cursor {
        final byte[] data;
        int pos;

        Cursor(byte[] data, int pos) {
            this.data = data;
            this.pos = pos;
        }

        int readU8() {
            return data[pos++] & 0xFF;
        }

        int readU16() {
            int v = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;
            return v;
        }

        long readU32() {
            long v = ((long) (data[pos] & 0xFF) << 24)
                    | ((long) (data[pos + 1] & 0xFF) << 16)
                    | ((long) (data[pos + 2] & 0xFF) << 8)
                    | (data[pos + 3] & 0xFF);
            pos += 4;
            return v;
        }

        long readU64() {
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (data[pos + i] & 0xFF);
            }
            pos += 8;
            return v;
        }

        byte[] readBytes(int len) {
            byte[] buf = new byte[len];
            System.arraycopy(data, pos, buf, 0, len);
            pos += len;
            return buf;
        }
    }
}
