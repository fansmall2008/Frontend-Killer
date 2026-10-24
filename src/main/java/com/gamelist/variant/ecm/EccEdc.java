package com.gamelist.variant.ecm;

import java.util.Arrays;

/**
 * CD-ROM 扇区的 EDC/ECC 生成。
 * <p>
 * 这两样东西是黄皮书里写死的：EDC 是 CRC32（多项式 D8018001），ECC 是 Reed-Solomon 的
 * P/Q 两级校验。它们跟 ECM 没有关系——ECM 只是把可重算的校验码从镜像里剥掉，解码时
 * 再按原样算回来。算法本身是公开规范，这里的实现依照扇区布局独立编写。
 * <p>
 * 扇区布局（偏移单位：字节）：
 * <pre>
 * MODE1        sync 0-11 | header 12-15 | data 16-2063 | EDC 2064 | 零 2068-2075 | ECC 2076
 * MODE2 Form1  sync 0-11 | header 12-15 | subheader 16-23 | data 24-2071 | EDC 2072 | ECC 2076
 * MODE2 Form2  sync 0-11 | header 12-15 | subheader 16-23 | data 24-2347 | EDC 2348
 * </pre>
 */
public final class EccEdc {

    public static final int SECTOR_RAW = 2352;
    /** MODE2 去掉 sync+header 后的部分，ECM 里就是按这个宽度拼接的 */
    public static final int SECTOR_MODE2 = 2336;

    public static final int SYNC_SIZE = 12;
    public static final int HEADER_SIZE = 4;
    public static final int SUBHEADER_SIZE = 8;
    public static final int DATA_SIZE = 2048;
    public static final int FORM2_DATA_SIZE = 2324;

    public static final int OFFSET_SYNC = 0;
    public static final int OFFSET_HEADER = 12;
    public static final int OFFSET_SUBHEADER = 16;
    /** MODE1 的用户数据紧接在 header 之后（没有 subheader） */
    public static final int OFFSET_MODE1_DATA = 16;
    /** MODE2 的用户数据在 8 字节 subheader 之后 */
    public static final int OFFSET_DATA = 24;

    public static final int OFFSET_MODE1_EDC = 2064;
    public static final int OFFSET_MODE2_EDC = 2072;
    public static final int OFFSET_FORM2_EDC = 2348;
    public static final int OFFSET_ECC = 2076;
    public static final int ECC_P_SIZE = 172;
    public static final int ECC_Q_SIZE = 104;

    private static final int[] EDC_TABLE = new int[256];
    private static final int[] ECC_F = new int[256];
    private static final int[] ECC_B = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int value = i;
            for (int bit = 0; bit < 8; bit++) {
                value = (value >>> 1) ^ ((value & 1) != 0 ? 0xD8018001 : 0);
            }
            EDC_TABLE[i] = value;
        }
        for (int i = 0; i < 256; i++) {
            // 0x11D 是本原多项式，异或后高位被抵消，结果仍在 0-255 内
            int j = ((i << 1) ^ ((i & 0x80) != 0 ? 0x11D : 0)) & 0xFF;
            ECC_F[i] = j;
            ECC_B[i ^ j] = i;
        }
    }

    private EccEdc() {
    }

    /** 12 字节同步头：一个 00，十个 FF，再一个 00 */
    public static void writeSync(byte[] sector) {
        sector[0] = 0;
        for (int i = 1; i <= 10; i++) {
            sector[i] = (byte) 0xFF;
        }
        sector[11] = 0;
    }

    /** 累加 EDC；整盘校验就是把它一路滚下去，最后跟文件尾的 4 字节比 */
    public static int edc(int crc, byte[] data, int offset, int length) {
        int value = crc;
        for (int i = 0; i < length; i++) {
            value = (value >>> 8) ^ EDC_TABLE[(value ^ data[offset + i]) & 0xFF];
        }
        return value;
    }

    /** MODE1：EDC 覆盖 sync 到数据末尾，随后补 ECC */
    public static void mode1(byte[] sector) {
        writeInt(sector, OFFSET_MODE1_EDC, edc(0, sector, 0, OFFSET_MODE1_EDC));
        // EDC 与 ECC 之间那 8 字节按规范置零
        Arrays.fill(sector, OFFSET_MODE1_EDC + 4, OFFSET_ECC, (byte) 0);
        // MODE1 的 ECC 保护地址字段，头部按真实值参与
        ecc(sector, false);
    }

    /** MODE2 Form1：EDC 从 subheader 起算，随后补 ECC */
    public static void mode2Form1(byte[] sector) {
        writeInt(sector, OFFSET_MODE2_EDC,
                edc(0, sector, OFFSET_SUBHEADER, OFFSET_MODE2_EDC - OFFSET_SUBHEADER));
        // MODE2 的 ECC 不保护地址，算之前要把头部当零
        ecc(sector, true);
    }

    /** MODE2 Form2：只有 EDC，没有 ECC */
    public static void mode2Form2(byte[] sector) {
        writeInt(sector, OFFSET_FORM2_EDC,
                edc(0, sector, OFFSET_SUBHEADER, OFFSET_FORM2_EDC - OFFSET_SUBHEADER));
    }

    /**
     * P/Q 两级校验。数据区从 header 开始算。
     * <p>
     * Q 级的覆盖范围（12..2247）包含刚算出的 P 级，所以必须在 P 写入之后再算——
     * 如果两级都从同一份“没有 P”的副本算，P 对得上而 Q 全错。
     *
     * @param zeroAddress MODE2 的 ECC 不覆盖地址字段，计算前要把它当零；MODE1 相反
     */
    private static void ecc(byte[] sector, boolean zeroAddress) {
        byte[] address = null;
        if (zeroAddress) {
            address = Arrays.copyOfRange(sector, OFFSET_HEADER, OFFSET_SUBHEADER);
            Arrays.fill(sector, OFFSET_HEADER, OFFSET_SUBHEADER, (byte) 0);
        }
        // P：86 组 × 24 字节，跨距 86
        eccBlock(sector, 86, 24, 2, 86, sector, OFFSET_ECC);
        // Q：52 组 × 43 字节，跨距 88，紧跟在 P 之后
        eccBlock(sector, 52, 43, 86, 88, sector, OFFSET_ECC + ECC_P_SIZE);
        if (address != null) {
            System.arraycopy(address, 0, sector, OFFSET_HEADER, address.length);
        }
    }

    private static void eccBlock(byte[] source, int majorCount, int minorCount, int majorMult,
                                 int minorInc, byte[] target, int targetOffset) {
        int size = majorCount * minorCount;
        for (int major = 0; major < majorCount; major++) {
            int index = (major >> 1) * majorMult + (major & 1);
            int eccA = 0;
            int eccB = 0;
            for (int minor = 0; minor < minorCount; minor++) {
                int temp = source[OFFSET_HEADER + index] & 0xFF;
                index += minorInc;
                if (index >= size) {
                    index -= size;
                }
                eccA ^= temp;
                eccB ^= temp;
                eccA = ECC_F[eccA];
            }
            eccA = ECC_B[ECC_F[eccA] ^ eccB];
            target[targetOffset + major] = (byte) eccA;
            target[targetOffset + major + majorCount] = (byte) (eccA ^ eccB);
        }
    }

    /** 扇区里的整数一律小端 */
    public static void writeInt(byte[] sector, int offset, int value) {
        sector[offset] = (byte) value;
        sector[offset + 1] = (byte) (value >>> 8);
        sector[offset + 2] = (byte) (value >>> 16);
        sector[offset + 3] = (byte) (value >>> 24);
    }

    public static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    /** 把 4 字节 subheader 补齐成 8 字节：前后两副本内容相同 */
    public static void duplicateSubheader(byte[] sector) {
        System.arraycopy(sector, OFFSET_SUBHEADER, sector, OFFSET_SUBHEADER + 4, 4);
    }
}
