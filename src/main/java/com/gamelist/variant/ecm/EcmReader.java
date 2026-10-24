package com.gamelist.variant.ecm;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import com.gamelist.variant.ConversionModule;

/**
 * ECM（Error Code Modeler）解码器。
 * <p>
 * ECM 不压缩数据，只把“能算出来的字节”挖掉再补回来：每个扇区放弃 EDC/ECC，
 * MODE2 还要放弃重复的 subheader，剩下的按记录串起来。所以解码是纯计算，无外部依赖。
 * <p>
 * 记录格式（头 4 字节 {@code 45 43 4D 00} 之后）：
 * <pre>
 * byte0  type = c &amp; 3, count = (c &gt;&gt; 2) &amp; 0x1F, c &amp; 0x80 为续位
 * 续位   每字节 count |= (c &amp; 0x7F) &lt;&lt; bits, bits += 7
 * 数量   num = count + 1；num == 0xFFFFFFFF 为终止符
 * </pre>
 * 四类记录：
 * <pre>
 * 0 literal  num 是<strong>字节数</strong>，原样落地（扇区同步头就是这么来的）
 * 1 MODE1    num 是扇区数，每扇区 4 字节地址 + 2048 数据，同步头与 EDC/ECC 现算
 * 2 MODE2/1  num 是扇区数，每扇区 4 字节 subheader + 2048 数据
 * 3 MODE2/2  num 是扇区数，每扇区 4 字节 subheader + 2324 数据
 * </pre>
 * 最容易踩的是 literal 的单位：它是字节，其余是扇区。
 * <p>
 * 终止符之后 4 字节是整盘累计 EDC（就是 CRC32 一路滚下来的结果），
 * 比完之后才能确认解码正确——这是唯一能证明“补齐的 EDC/ECC 真的和原盘一致”的手段。
 */
public final class EcmReader {

    /** 容器头 4 字节 */
    private static final byte[] MAGIC = {'E', 'C', 'M', 0};

    private static final int TYPE_LITERAL = 0;
    private static final int TYPE_MODE1 = 1;
    private static final int TYPE_MODE2_FORM1 = 2;
    private static final int TYPE_MODE2_FORM2 = 3;

    /** 终止符后面跟着整盘累计 EDC */
    private static final int EDC_SIZE = 4;

    /** MODE1 每扇区存 4 字节地址 + 2048 数据 */
    private static final int MODE1_PAYLOAD = EccEdc.HEADER_SIZE + EccEdc.DATA_SIZE;
    /** MODE2 每扇区只存一份 4 字节 subheader（另一半是重复的）+ 用户数据 */
    private static final int MODE2_FORM1_PAYLOAD = 4 + EccEdc.DATA_SIZE;
    private static final int MODE2_FORM2_PAYLOAD = 4 + EccEdc.FORM2_DATA_SIZE;

    private static final int BUFFER = 1 << 20;
    /** 进度上报的最小间隔（输入侧字节），避免每 2KB 一次回调 */
    private static final long PROGRESS_STEP = 1 << 20;

    private EcmReader() {
    }

    public static boolean isEcm(Path file) {
        byte[] head = read(file, MAGIC.length);
        return looksLikeEcm(head);
    }

    /** 魔数判定；只读文件头，探测阶段用 */
    public static boolean looksLikeEcm(byte[] head) {
        if (head == null || head.length < MAGIC.length) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (head[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解包成原始扇区镜像。
     *
     * @param ecm      输入
     * @param target   输出（调用方负责 .part 与改名）
     * @param progress 进度回调，按输入字节数上报
     */
    public static Result decode(Path ecm, Path target, ConversionModule.ProgressSink progress)
            throws IOException {
        long total = Files.size(ecm);
        ConversionModule.ProgressSink sink = progress != null ? progress : ConversionModule.ProgressSink.NOOP;
        Decoder decoder;
        try (CountingInputStream in = new CountingInputStream(
                new BufferedInputStream(new FileInputStream(ecm.toFile()), BUFFER));
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(target), BUFFER)) {
            decoder = new Decoder(in, out, total, sink);
            decoder.run();
        }
        return decoder.result();
    }

    /** 统计已消费的输入字节，进度上报用 */
    private static final class CountingInputStream extends FilterInputStream {

        private long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        long count() {
            return count;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                count += read;
            }
            return read;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            count += skipped;
            return skipped;
        }
    }

    private static byte[] read(Path file, int length) {
        if (file == null) {
            return new byte[0];
        }
        try (InputStream in = new FileInputStream(file.toFile())) {
            byte[] head = new byte[length];
            int read = in.read(head);
            if (read <= 0) {
                return new byte[0];
            }
            return read == length ? head : Arrays.copyOf(head, read);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /** 解码结论 */
    public record Result(long bytes,
                         int sectorSize,
                         int literalRuns,
                         int mode1Sectors,
                         int mode2Form1,
                         int mode2Form2) {

        public int sectors() {
            return mode1Sectors + mode2Form1 + mode2Form2;
        }

        public String mode() {
            if (mode1Sectors > 0) {
                return "MODE1/" + sectorSize;
            }
            return "MODE2/" + sectorSize;
        }
    }

    /**
     * 解码状态机。同步头 + header 由前面的 literal 提供，所以 MODE2 只吐 2336 字节的扇区主体；
     * MODE1 没有这个前置 literal，地址和同步头都在记录里（地址）或现算（同步头），整扇区吐出。
     */
    private static final class Decoder {

        private final InputStream in;
        private final OutputStream out;
        private final long total;
        private final ConversionModule.ProgressSink sink;

        private final byte[] sector = new byte[EccEdc.SECTOR_RAW];
        private final byte[] literal = new byte[EccEdc.SECTOR_RAW];
        /** 最近 16 字节输出，用来判断 MODE2 前面有没有同步头 */
        private final byte[] tail = new byte[EccEdc.SYNC_SIZE + EccEdc.HEADER_SIZE];

        private final CountingInputStream counting;

        private int checkedc;
        private long outBytes;
        private long reported;

        private int type;
        private long count;

        private int literalRuns;
        private int mode1Sectors;
        private int form1Sectors;
        private int form2Sectors;
        private boolean sawRaw;

        Decoder(CountingInputStream in, OutputStream out, long total, ConversionModule.ProgressSink sink) {
            this.in = in;
            this.counting = in;
            this.out = out;
            this.total = total;
            this.sink = sink;
        }

        void run() throws IOException {
            // 容器头只是外壳，不落盘也不参与累计 EDC
            byte[] head = new byte[MAGIC.length];
            readFully(head, 0, MAGIC.length);
            if (!looksLikeEcm(head)) {
                throw new IOException("不是 ECM：容器头缺失");
            }
            while (nextRecord()) {
                dispatch();
            }
            verifyTrailer();
            out.flush();
            sink.update(total, total);
        }

        /** 读一条记录的头部；false 表示终止符或流结束 */
        private boolean nextRecord() throws IOException {
            int c = in.read();
            if (c < 0) {
                return false;
            }
            type = c & 0x03;
            long num = (c >> 2) & 0x1F;
            int bits = 5;
            while ((c & 0x80) != 0) {
                c = in.read();
                if (c < 0) {
                    throw new EOFException("ECM 记录长度字段被截断");
                }
                if (bits > 31) {
                    throw new IOException("ECM 记录长度字段溢出");
                }
                num |= ((long) (c & 0x7F)) << bits;
                bits += 7;
            }
            if (num == 0xFFFFFFFFL) {
                return false;
            }
            count = num + 1;
            return true;
        }

        private void dispatch() throws IOException {
            if (type == TYPE_LITERAL) {
                literalRuns++;
                while (count > 0) {
                    int chunk = (int) Math.min(EccEdc.SECTOR_RAW, count);
                    readFully(literal, 0, chunk);
                    checkedc = EccEdc.edc(checkedc, literal, 0, chunk);
                    out.write(literal, 0, chunk);
                    pushTail(literal, 0, chunk);
                    count -= chunk;
                    progress();
                }
                return;
            }
            for (long i = 0; i < count; i++) {
                switch (type) {
                    case TYPE_MODE1 -> mode1();
                    case TYPE_MODE2_FORM1 -> mode2(true);
                    case TYPE_MODE2_FORM2 -> mode2(false);
                    default -> throw new IOException("ECM 记录类型非法: " + type);
                }
                progress();
            }
        }

        /** MODE1：同步头现造，地址和数据来自流，EDC/ECC 现算，整扇区 2352 字节输出 */
        private void mode1() throws IOException {
            Arrays.fill(sector, (byte) 0);
            EccEdc.writeSync(sector);
            sector[EccEdc.OFFSET_HEADER + 3] = 0x01;
            readFully(sector, EccEdc.OFFSET_HEADER, EccEdc.HEADER_SIZE);
            readFully(sector, EccEdc.OFFSET_MODE1_DATA, EccEdc.DATA_SIZE);
            EccEdc.mode1(sector);
            checkedc = EccEdc.edc(checkedc, sector, 0, EccEdc.SECTOR_RAW);
            out.write(sector, 0, EccEdc.SECTOR_RAW);
            pushTail(sector, 0, EccEdc.SECTOR_RAW);
            mode1Sectors++;
        }

        /**
         * MODE2 Form1/Form2：流里只有 4 字节 subheader 和用户数据，
         * 另一半 subheader 复制补齐，EDC/ECC 现算，只输出 2336 字节的扇区主体。
         */
        private void mode2(boolean form1) throws IOException {
            // 必须在写入之前判断：写完之后 tail 就是这个扇区自己的尾巴了
            boolean raw = hasSyncHeaderTail();
            if (raw) {
                sawRaw = true;
            }

            Arrays.fill(sector, (byte) 0);
            int payload = form1 ? MODE2_FORM1_PAYLOAD : MODE2_FORM2_PAYLOAD;
            readFully(sector, EccEdc.OFFSET_SUBHEADER, 4);
            EccEdc.duplicateSubheader(sector);
            readFully(sector, EccEdc.OFFSET_DATA, payload - 4);
            if (form1) {
                EccEdc.mode2Form1(sector);
                form1Sectors++;
            } else {
                EccEdc.mode2Form2(sector);
                form2Sectors++;
            }
            checkedc = EccEdc.edc(checkedc, sector, EccEdc.OFFSET_SUBHEADER, EccEdc.SECTOR_MODE2);
            out.write(sector, EccEdc.OFFSET_SUBHEADER, EccEdc.SECTOR_MODE2);
            pushTail(sector, EccEdc.OFFSET_SUBHEADER, EccEdc.SECTOR_MODE2);
        }

        /** 终止符之后 4 字节：整盘累计 EDC，小端 */
        private void verifyTrailer() throws IOException {
            byte[] trailer = new byte[EDC_SIZE];
            readFully(trailer, 0, EDC_SIZE);
            int expected = EccEdc.readInt(trailer, 0);
            if (expected != checkedc) {
                throw new IOException(String.format(
                        "ECM 校验失败：文件尾 EDC 为 %08X，重算得到 %08X（补齐的 EDC/ECC 与原盘不一致）",
                        expected, checkedc));
            }
        }

        private void pushTail(byte[] data, int offset, int length) {
            if (length >= tail.length) {
                System.arraycopy(data, offset + length - tail.length, tail, 0, tail.length);
            } else {
                System.arraycopy(tail, length, tail, 0, tail.length - length);
                System.arraycopy(data, offset, tail, tail.length - length, length);
            }
            outBytes += length;
        }

        /** 最近的输出是否正好是“同步头 + MODE2 头”，是则本扇区按 2352 拼装 */
        private boolean hasSyncHeaderTail() {
            if (outBytes < tail.length) {
                return false;
            }
            if ((tail[0] & 0xFF) != 0x00 || (tail[11] & 0xFF) != 0x00) {
                return false;
            }
            for (int i = 1; i <= 10; i++) {
                if ((tail[i] & 0xFF) != 0xFF) {
                    return false;
                }
            }
            return tail[15] == 0x02;
        }

        private void readFully(byte[] buffer, int offset, int length) throws IOException {
            int done = 0;
            while (done < length) {
                int read = in.read(buffer, offset + done, length - done);
                if (read < 0) {
                    throw new EOFException("ECM 数据被截断（期望 " + length + " 字节，只读到 " + done + "）");
                }
                done += read;
            }
        }

        private void progress() {
            long done = counting.count();
            if (done - reported < PROGRESS_STEP) {
                return;
            }
            reported = done;
            sink.update(done, total > 0 ? total : done);
        }

        Result result() {
            // 有同步头才算完整的 2352 扇区；整盘都是裸 MODE2 主体则是 2336
            int sectorSize = sawRaw || mode1Sectors > 0 ? EccEdc.SECTOR_RAW : EccEdc.SECTOR_MODE2;
            return new Result(outBytes, sectorSize, literalRuns, mode1Sectors, form1Sectors, form2Sectors);
        }
    }
}
