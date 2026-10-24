package com.gamelist.variant.ciso;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;

import com.gamelist.variant.ConversionModule;

/**
 * CISO v1 写入器：2048 字节块 + 原始 deflate + 32 位块索引（最高位标记未压缩块）。
 * <p>
 * 结构：24 字节头 + 索引表 + 数据块。索引偏移是 31 位，align=0 时上限 2GB；
 * 超限会显式报错而不是写出损坏文件（未来做 PS2 的 ZSO 必须启用 align 或 cso2）。
 */
public final class CisoWriter {

    public static final int BLOCK_SIZE = 2048;
    public static final int HEADER_SIZE = 0x18;
    /** 31 位偏移上限 */
    public static final long MAX_OFFSET = 0x7FFFFFFFL;

    private CisoWriter() {
    }

    public static long write(Path iso, Path cso, ConversionModule.ProgressSink progress) throws IOException {
        long total = Files.size(iso);
        if (total <= 0) {
            throw new IOException("ISO 文件为空: " + iso.getFileName());
        }
        if (total % BLOCK_SIZE != 0) {
            throw new IOException("ISO 大小不是 2048 的整数倍，无法按扇区块写入 CSO: "
                    + iso.getFileName() + " (" + total + " 字节)");
        }
        if (total / BLOCK_SIZE > Integer.MAX_VALUE - 8) {
            throw new IOException("ISO 块数量超出支持范围: " + iso.getFileName());
        }

        int blocks = (int) (total / BLOCK_SIZE);
        int[] index = new int[blocks];
        long dataStart = HEADER_SIZE + (long) blocks * 4L;
        long pos = dataStart;

        ConversionModule.ProgressSink sink = progress != null ? progress : ConversionModule.ProgressSink.NOOP;

        try (RandomAccessFile in = new RandomAccessFile(iso.toFile(), "r");
             RandomAccessFile out = new RandomAccessFile(cso.toFile(), "rw")) {
            out.setLength(0);
            writeHeader(out, total, BLOCK_SIZE, 1, 0);
            // 索引区先占位，最后回填
            out.seek(dataStart);

            Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
            byte[] raw = new byte[BLOCK_SIZE];
            byte[] compressed = new byte[BLOCK_SIZE + 512];
            try {
                for (int i = 0; i < blocks; i++) {
                    in.readFully(raw);
                    deflater.reset();
                    deflater.setInput(raw, 0, BLOCK_SIZE);
                    deflater.finish();
                    int compressedLength = 0;
                    while (!deflater.finished() && compressedLength < compressed.length) {
                        int n = deflater.deflate(compressed, compressedLength, compressed.length - compressedLength);
                        if (n <= 0) {
                            break;
                        }
                        compressedLength += n;
                    }

                    if (pos > MAX_OFFSET) {
                        throw new IOException("CSO 数据偏移超过 2GB 上限（当前索引需要 64 位偏移，尚未实现）");
                    }
                    out.seek(pos);
                    if (compressedLength > 0 && compressedLength < BLOCK_SIZE) {
                        out.write(compressed, 0, compressedLength);
                        index[i] = (int) pos;
                        pos += compressedLength;
                    } else {
                        out.write(raw, 0, BLOCK_SIZE);
                        index[i] = (int) (pos | 0x80000000L);
                        pos += BLOCK_SIZE;
                    }
                    if (sink != null && (i % 128 == 0 || i == blocks - 1)) {
                        sink.update(i + 1L, blocks);
                    }
                }
            } finally {
                deflater.end();
            }

            out.seek(HEADER_SIZE);
            for (int value : index) {
                out.writeInt(value);
            }
            out.setLength(pos);
        }
        return Files.size(cso);
    }

    private static void writeHeader(RandomAccessFile out, long totalBytes, int blockSize,
                                    int version, int align) throws IOException {
        out.seek(0);
        out.writeBytes("CISO");
        out.writeInt(HEADER_SIZE);
        out.writeLong(totalBytes);
        out.writeInt(blockSize);
        out.writeByte(version);
        out.writeByte(align);
        out.writeShort(0);
    }
}
