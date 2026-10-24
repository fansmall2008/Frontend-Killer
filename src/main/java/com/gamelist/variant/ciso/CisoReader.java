package com.gamelist.variant.ciso;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.gamelist.variant.ConversionModule;

/**
 * CISO v1 读取器：解析头部与块索引，支持整镜像解包与随机块读取。
 */
public final class CisoReader {

    private CisoReader() {
    }

    /**
     * 解析头部与索引。
     */
    public static Header readHeader(Path cso) throws IOException {
        long fileLength = Files.size(cso);
        try (RandomAccessFile in = new RandomAccessFile(cso.toFile(), "r")) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            String magicText = new String(magic, java.nio.charset.StandardCharsets.US_ASCII);
            if (!"CISO".equals(magicText)) {
                throw new IOException(magicText.startsWith("ZISO")
                        ? "这是 ZSO（LZ4 块压缩），当前仅支持 CSO" : "不是 CSO 文件: " + cso.getFileName());
            }
            int headerSize = in.readInt();
            long totalBytes = in.readLong();
            int blockSize = in.readInt();
            int version = in.readUnsignedByte();
            int align = in.readUnsignedByte();
            in.readUnsignedShort(); // reserved

            if (headerSize < 0x18 || headerSize > 0x1000) {
                throw new IOException("CSO 头部长度异常: " + headerSize);
            }
            if (blockSize <= 0 || blockSize > 16 * 1024 * 1024) {
                throw new IOException("CSO 块大小异常: " + blockSize);
            }
            if (version != 1) {
                throw new IOException("暂不支持 CSO v" + version + "，请使用标准 CSO（v1）");
            }
            long blocksLong = (totalBytes + blockSize - 1) / blockSize;
            if (blocksLong <= 0 || blocksLong > Integer.MAX_VALUE - 8) {
                throw new IOException("CSO 块数量异常: " + blocksLong);
            }
            int blocks = (int) blocksLong;
            long indexEnd = headerSize + (long) blocks * 4L;
            if (indexEnd > fileLength) {
                throw new IOException("CSO 索引区超出文件长度，文件可能不完整");
            }
            int[] index = new int[blocks];
            in.seek(headerSize);
            for (int i = 0; i < blocks; i++) {
                index[i] = in.readInt();
            }
            return new Header(totalBytes, blockSize, version, align, blocks, index, fileLength);
        }
    }

    /**
     * 解包为 ISO。
     *
     * @return 解包后的字节数
     */
    public static long extract(Path cso, Path iso, ConversionModule.ProgressSink progress) throws IOException {
        Header header = readHeader(cso);
        ConversionModule.ProgressSink sink = progress != null ? progress : ConversionModule.ProgressSink.NOOP;
        try (RandomAccessFile in = new RandomAccessFile(cso.toFile(), "r");
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(iso), 1 << 20)) {
            Inflater inflater = new Inflater(true);
            byte[] raw = new byte[header.blockSize()];
            try {
                for (int i = 0; i < header.blocks(); i++) {
                    int expected = (int) Math.min(header.blockSize(),
                            header.totalBytes() - (long) i * header.blockSize());
                    int entry = header.index()[i];
                    boolean plain = (entry & 0x80000000) != 0;
                    long offset = (entry & 0x7FFFFFFFL) << header.align();
                    long nextOffset = i + 1 < header.blocks()
                            ? ((long) (header.index()[i + 1] & 0x7FFFFFFF)) << header.align()
                            : header.fileLength();
                    if (offset < 0 || offset >= header.fileLength() || nextOffset < offset) {
                        throw new IOException("CSO 第 " + i + " 块索引损坏");
                    }
                    int stored = (int) Math.min(Integer.MAX_VALUE, nextOffset - offset);
                    if (plain) {
                        in.seek(offset);
                        in.readFully(raw, 0, Math.min(expected, stored));
                        out.write(raw, 0, expected);
                    } else {
                        byte[] compressed = new byte[stored];
                        in.seek(offset);
                        in.readFully(compressed);
                        inflater.reset();
                        inflater.setInput(compressed);
                        int written = 0;
                        try {
                            while (written < expected) {
                                int n = inflater.inflate(raw, written, expected - written);
                                if (n == 0) {
                                    if (inflater.finished() || inflater.needsInput()) {
                                        break;
                                    }
                                }
                                written += n;
                            }
                        } catch (DataFormatException e) {
                            throw new IOException("CSO 第 " + i + " 块解压失败: " + e.getMessage(), e);
                        }
                        if (written != expected) {
                            throw new IOException("CSO 第 " + i + " 块解压长度不符: " + written + "/" + expected);
                        }
                        out.write(raw, 0, expected);
                    }
                    if (i % 128 == 0 || i == header.blocks() - 1) {
                        sink.update(i + 1L, header.blocks());
                    }
                }
            } finally {
                inflater.end();
            }
        }
        return header.totalBytes();
    }

    public record Header(long totalBytes,
                         int blockSize,
                         int version,
                         int align,
                         int blocks,
                         int[] index,
                         long fileLength) {
    }
}
