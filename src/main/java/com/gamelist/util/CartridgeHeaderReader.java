package com.gamelist.util;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 卡带 / 世嘉光盘 IP.BIN 头部标识符提取器。
 * <p>
 * 参考公开硬件文档（GBATEK、YAGCD、SegaRetro）实现，
 * 从 ROM 文件头部固定偏移读取游戏编号，性能远优于 CRC32 全文件计算。
 * <p>
 * 支持平台：
 * <ul>
 *   <li>GB/GBC (systemId=9/10): 偏移 0x0134，16 字节 title（如 DMG-WQJ、BO7E）</li>
 *   <li>GBA (systemId=12): 偏移 0xAC，4 字节 game code</li>
 *   <li>N64 (systemId=14): 偏移 0x3C，2+1 字节 cart ID + country（支持大/小端及字节交换）</li>
 *   <li>Genesis/Mega Drive (systemId=1): 偏移 0x180，16 字节 domestic serial</li>
 *   <li>SNES/SFC (systemId=4): LoROM 0x7FC0 / HiROM 0xFFC0，21 字节 internal title</li>
 *   <li>Saturn (systemId=22): 搜索 "SEGA SEGASATURN" magic → magic+0x20 读 10 字节（仅 .bin + .cue）</li>
 *   <li>MegaCD (systemId=20): 搜索 "SEGA SEGAMEGADRIVE" magic → magic+0x20 读 10 字节（仅 .bin + .cue）</li>
 *   <li>Dreamcast (systemId=23): 搜索 "SEGA SEGAKATANA" magic → magic+0x40 读 10 字节（.bin/.cue/.iso/.cdi/.gdi；CDI 需全文件扫描，GDI 需解析索引遍历数据轨）</li>
 *   <li>NDS (systemId=15): 偏移 0x0C，4 字节 game code（如 YUGJ、AMCP）</li>
 *   <li>3DS (systemId=17): .3ds/.cci 头部搜索 product code（形如 CTR-P-A3RP，实测稳定于 0x1150）</li>
 *   <li>Wii (systemId=16): .iso game ID @0x00 / .wbfs disc header @0x200，取前 4 字节 game code（见 extractWiiGameId，由 matchByDiscFile 调用）</li>
 * </ul>
 */
public final class CartridgeHeaderReader {

    private static final Logger logger = LoggerFactory.getLogger(CartridgeHeaderReader.class);

    /** N64 大端序魔数 */
    private static final byte N64_BE_MAGIC = (byte) 0x80;
    /** N64 小端序魔数 */
    private static final byte N64_LE_MAGIC = (byte) 0x40;
    /** N64 字节交换魔数 */
    private static final byte N64_BS_MAGIC = (byte) 0x37;

    /** SNES LoROM 头部起始偏移 */
    private static final int SNES_LOROM_HEADER_START = 0x7FC0;
    /** SNES HiROM 头部起始偏移 */
    private static final int SNES_HIROM_HEADER_START = 0xFFC0;

    /** Saturn IP.BIN 魔数 */
    private static final byte[] SATURN_MAGIC = "SEGA SEGASATURN".getBytes(StandardCharsets.US_ASCII);
    /** MegaCD / Sega CD IP.BIN 魔数 */
    private static final byte[] MEGACD_MAGIC = "SEGA SEGAMEGADRIVE".getBytes(StandardCharsets.US_ASCII);
    /** Dreamcast IP.BIN 魔数 */
    private static final byte[] DC_MAGIC = "SEGA SEGAKATANA".getBytes(StandardCharsets.US_ASCII);

    /** 支持头部提取的 systemId 集合 */
    // 注意：NES (systemId=3) 不支持从 ROM 头部提取编号，NES 编号来自 No-Intro 命名约定
    // NES 应通过 CRC32 匹配 rom_db 获取 serial
    private static final Set<Integer> SUPPORTED_SYSTEMS = Set.of(12, 14, 1, 22, 20, 23, 9, 10, 4, 15, 17);

    private CartridgeHeaderReader() {}

    /**
     * 判断指定 systemId 是否支持头部提取。
     */
    public static boolean isSupported(int systemId) {
        return SUPPORTED_SYSTEMS.contains(systemId);
    }

    /**
     * 从 ROM 文件头部提取游戏编号。
     *
     * @param filePath ROM 文件路径
     * @param systemId ScreenScraper systemId
     * @return 提取到的 serial（已 trim），失败返回 null
     */
    public static String extractSerial(String filePath, int systemId) {
        if (filePath == null || filePath.isEmpty()) {
            logger.info("extractSerial 跳过: filePath 为空, systemId={}", systemId);
            return null;
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            logger.info("extractSerial 跳过: 文件不存在, path={}, exists={}, isFile={}", filePath, file.exists(), file.isFile());
            return null;
        }

        String ext = getFileExtension(filePath).toLowerCase();

        // 7z/rar 暂不支持
        if ("7z".equals(ext) || "rar".equals(ext)) {
            logger.info("extractSerial 跳过: 不支持的格式 ext={}", ext);
            return null;
        }

        try {
            // ZIP 文件：从内部 entry 读取头部字节
            if ("zip".equals(ext)) {
                String result = extractFromZip(filePath, systemId);
                logger.info("extractFromZip 结果: file={}, result={}", new File(filePath).getName(), result);
                return result;
            }

            switch (systemId) {
                case 12: return extractGBA(filePath, ext);
                case 14: return extractN64(filePath);
                case 1:  return extractGenesis(filePath);
                case 22: return extractSegaDisc(filePath, ext, SATURN_MAGIC, "Saturn", 0x20);
                case 20: return extractSegaDisc(filePath, ext, MEGACD_MAGIC, "MegaCD", 0x20);
                case 23: return extractSegaDisc(filePath, ext, DC_MAGIC, "Dreamcast", 0x40);
                case 9:  // GB
                case 10: return extractGBGBC(filePath, ext); // GBC
                case 4:  return extractSNES(filePath); // SNES/SFC
                case 15: return extractNDS(filePath, ext); // NDS
                case 17: return extract3DS(filePath, ext); // 3DS
                default: return null;
            }
        } catch (Exception e) {
            logger.info("卡带头部提取异常: systemId={}, file={}, error={}", systemId, file.getName(), e.getMessage());
            return null;
        }
    }

    // ========== ZIP 内部读取 ==========

    /** ZIP 内 ROM 文件的有效扩展名 */
    private static final Set<String> ZIP_ROM_EXTENSIONS = Set.of(
            "bin", "md", "gen", "smd", "32x",   // Genesis/32X
            "gba",                                // GBA
            "gb", "gbc",                          // GB/GBC
            "n64", "z64", "v64",                  // N64
            "sfc", "smc",                         // SNES/SFC
            "rom", "iso"                          // 通用
    );

    /**
     * 从 ZIP 文件内部读取头部字节并提取 serial。
     * <p>
     * 使用 Apache Commons Compress 的 ZipFile 而非 java.util.zip.ZipFile，
     * 因为 JDK 17+ 的 ZipFile 对 CEN header 验证严格，
     * 会拒绝含非 UTF-8 entry name 的 ZIP（报错 "invalid CEN header (bad entry name)"）。
     * Commons Compress 是纯 Java 实现，支持指定编码，对非标准 ZIP 更宽容。
     */
    private static String extractFromZip(String zipPath, int systemId) throws IOException {
        // Saturn IP.BIN 不支持 ZIP（需要 CUE/BIN 原始文件）
        if (systemId == 22 || systemId == 20 || systemId == 23) return null;

        // 尝试多种编码打开 ZIP（cp437=ZIP 原始规范, GBK=Windows 中文, UTF-8=标准）
        Charset[] charsets = { Charset.forName("cp437"), Charset.forName("GBK"), StandardCharsets.UTF_8 };
        ZipFile zip = null;
        Exception lastError = null;
        for (Charset cs : charsets) {
            try {
                zip = new ZipFile.Builder().setFile(new File(zipPath)).setCharset(cs).get();
                break; // 成功打开
            } catch (Exception e) {
                lastError = e;
                // 尝试下一个编码
            }
        }
        if (zip == null) {
            logger.info("ZIP 打开失败（所有编码均失败）: {}, lastError={}", new File(zipPath).getName(), lastError != null ? lastError.getMessage() : "unknown");
            return null;
        }

        final ZipFile zipFile = zip;
        try (zipFile) {
            // 找到目标 ROM entry（优先 ROM 扩展名，否则回退到第一个非目录 entry）
            ZipArchiveEntry romEntry = null;
            ZipArchiveEntry fallbackEntry = null;
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String entryExt = getFileExtension(entry.getName()).toLowerCase();
                if (ZIP_ROM_EXTENSIONS.contains(entryExt)) {
                    romEntry = entry;
                    break;
                }
                if (fallbackEntry == null) {
                    fallbackEntry = entry;
                }
            }

            // 回退：使用第一个非目录 entry
            if (romEntry == null && fallbackEntry != null) {
                logger.info("ZIP 内无 ROM 扩展名匹配 entry，回退到: {}", fallbackEntry.getName());
                romEntry = fallbackEntry;
            }

            if (romEntry == null) {
                logger.info("ZIP 内无可用 entry: {}", new File(zipPath).getName());
                return null;
            }

            // 读取头部字节（根据平台调整大小：SNES 需要 >= 0xFFE0 才能覆盖 HiROM 头部）
            int headerSize = getHeaderReadSize(systemId);
            long entrySize = romEntry.getSize();
            if (entrySize > 0 && entrySize < headerSize) {
                headerSize = (int) entrySize;
            }

            byte[] header = new byte[headerSize];
            try (InputStream is = zipFile.getInputStream(romEntry)) {
                int totalRead = 0;
                while (totalRead < headerSize) {
                    int read = is.read(header, totalRead, headerSize - totalRead);
                    if (read < 0) break;
                    totalRead += read;
                }
                if (totalRead < headerSize) {
                    byte[] actual = new byte[totalRead];
                    System.arraycopy(header, 0, actual, 0, totalRead);
                    header = actual;
                }
            }

            String zipName = new File(zipPath).getName();
            logger.debug("ZIP 内部 ROM: zip={}, entry={}, entrySize={}, headerLen={}, headerHex={}",
                    zipName, romEntry.getName(), entrySize, header.length,
                    bytesToHex(header, Math.min(header.length, 32)));

            String fileName = zipName + "!" + romEntry.getName();
            return extractFromHeaderBytes(header, systemId, fileName);
        }
    }

    /**
     * 从已读取的头部字节数组提取 serial（ZIP 内部读取的统一入口）。
     */
    private static String extractFromHeaderBytes(byte[] header, int systemId, String fileName) {
        try {
            switch (systemId) {
                case 12: return extractGBAFromBytes(header, fileName);
                case 14: return extractN64FromBytes(header, fileName);
                case 1:  return extractGenesisFromBytes(header, fileName);
                case 9:
                case 10: return extractGBGBCFromBytes(header, fileName);
                case 3:  return extractNESFromBytes(header, fileName);
                case 4:  return extractSNESFromBytes(header, fileName);
                default: return null;
            }
        } catch (Exception e) {
            logger.debug("ZIP 头部提取失败: systemId={}, file={}, error={}", systemId, fileName, e.getMessage());
            return null;
        }
    }

    /**
     * 获取指定平台从 ZIP 中需要读取的头部字节大小。
     * <p>
     * 默认 0x200，但 SNES 需要读取到 HiROM 头部末尾（0xFFC0 + 32 = 0xFFE0）。
     */
    private static int getHeaderReadSize(int systemId) {
        switch (systemId) {
            case 4:  return 0xFFE0;   // SNES: HiROM 头部在 0xFFC0，需多读 32 字节
            default: return 0x200;
        }
    }

    // ========== GB / GBC (systemId=9/10) ==========

    /**
     * GB/GBC: 偏移 0x0134 读取 16 字节 title 区域。
     * <p>
     * 早期 GB 游戏 title 即游戏标识（如 "DR.MARIO"），
     * GBC 时代 title 区域包含 game code（如 "VP9E"）或完整编号前缀（如 "CGB-VP9E"）。
     * 提取后由 matchBySerial 做多格式回退匹配。
     */
    private static String extractGBGBC(String filePath, String ext) throws IOException {
        String lower = ext.toLowerCase();
        if (!"gb".equals(lower) && !"gbc".equals(lower) && !"bin".equals(lower)
                && !"rom".equals(lower) && !"iso".equals(lower)) {
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            if (raf.length() < 0x0144) return null;
            byte[] buf = new byte[16];
            raf.seek(0x0134);
            raf.readFully(buf);
            return parseGBGBCSerial(buf, new File(filePath).getName());
        }
    }

    private static String extractGBGBCFromBytes(byte[] header, String fileName) {
        if (header.length < 0x0144) return null;
        byte[] buf = new byte[16];
        System.arraycopy(header, 0x0134, buf, 0, 16);
        return parseGBGBCSerial(buf, fileName);
    }

    private static String parseGBGBCSerial(byte[] buf, String fileName) {
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) {
            if (b == 0) break;
            char c = (char) (b & 0xFF);
            if (c >= 0x20 && c <= 0x7E) sb.append(c);
        }
        String title = sb.toString().trim();
        if (title.length() < 3) return null;
        logger.debug("GB/GBC 头部提取: title=[{}] from {}", title, fileName);
        return title;
    }

    // ========== GBA (systemId=12) ==========

    /**
     * GBA: 偏移 0xAC 读取 4 字节 game code（如 "BJBE"）。
     */
    private static String extractGBA(String filePath, String ext) throws IOException {
        if (!"gba".equals(ext)) return null;
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            if (raf.length() < 0xB0) return null;
            byte[] buf = new byte[4];
            raf.seek(0xAC);
            raf.readFully(buf);
            return parseGBASerial(buf, new File(filePath).getName());
        }
    }

    private static String extractGBAFromBytes(byte[] header, String fileName) {
        if (header.length < 0xB0) return null;
        byte[] buf = new byte[4];
        System.arraycopy(header, 0xAC, buf, 0, 4);
        return parseGBASerial(buf, fileName);
    }

    private static String parseGBASerial(byte[] buf, String fileName) {
        String code = new String(buf, StandardCharsets.US_ASCII).trim();
        if (code.length() < 3 || !isPrintableAscii(code)) return null;
        logger.debug("GBA 头部提取: gameCode={} from {}", code, fileName);
        return code;
    }

    // ========== N64 (systemId=14) ==========

    /**
     * N64: 偏移 0x3C 读取 2 字节 cart ID + 1 字节 country code。
     * 支持大端（0x80 开头）、小端（0x40 开头）、字节交换（0x37 开头）三种格式。
     */
    private static String extractN64(String filePath) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            if (raf.length() < 0x40) return null;
            byte[] header = new byte[0x40];
            raf.readFully(header);
            return parseN64Serial(header, new File(filePath).getName());
        }
    }

    private static String extractN64FromBytes(byte[] header, String fileName) {
        if (header.length < 0x40) return null;
        // 只传前 0x40 字节
        byte[] h = new byte[0x40];
        System.arraycopy(header, 0, h, 0, 0x40);
        return parseN64Serial(h, fileName);
    }

    private static String parseN64Serial(byte[] header, String fileName) {
        byte firstByte = header[0];
        byte cartId0, cartId1, country;

        if (firstByte == N64_BE_MAGIC) {
            cartId0 = header[0x3C]; cartId1 = header[0x3D]; country = header[0x3E];
        } else if (firstByte == N64_LE_MAGIC) {
            cartId0 = header[0x3D]; cartId1 = header[0x3C]; country = header[0x3F];
        } else if (firstByte == N64_BS_MAGIC) {
            cartId0 = header[0x3D]; cartId1 = header[0x3C]; country = header[0x3E];
        } else {
            logger.debug("N64 未知字节序: 0x{}", Integer.toHexString(firstByte & 0xFF));
            return null;
        }

        char c0 = (char) (cartId0 & 0xFF);
        char c1 = (char) (cartId1 & 0xFF);
        char c2 = (char) (country & 0xFF);
        if (!isPrintableAsciiChar(c0) || !isPrintableAsciiChar(c1) || !isPrintableAsciiChar(c2)) return null;

        String serial = "" + c0 + c1 + c2;
        logger.debug("N64 头部提取: serial={} from {}", serial, fileName);
        return serial;
    }

    // ========== Genesis / Mega Drive (systemId=1) ==========

    /**
     * Genesis: 偏移 0x180 读取 16 字节 domestic serial。
     * 清理 #、-、空格后得到 rom_db 匹配键（如 "T4807300"、"MK1281"）。
     */
    private static String extractGenesis(String filePath) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            if (raf.length() < 0x18C) return null;
            byte[] buf = new byte[12]; // 0x180-0x18B = domestic serial (12 bytes), 0x18C-0x18D = version
            raf.seek(0x180);
            raf.readFully(buf);
            return parseGenesisSerial(buf, new File(filePath).getName());
        }
    }

    private static String extractGenesisFromBytes(byte[] header, String fileName) {
        if (header.length < 0x18C) {
            logger.info("Genesis 头部不足: file={}, len={} (需要 0x18C={})", fileName, header.length, 0x18C);
            return null;
        }
        byte[] buf = new byte[12];
        System.arraycopy(header, 0x180, buf, 0, 12);
        logger.debug("Genesis 0x180 区域: file={}, hex={}", fileName, bytesToHex(buf, 12));
        return parseGenesisSerial(buf, fileName);
    }

    private static String parseGenesisSerial(byte[] buf, String fileName) {
        String raw = new String(buf, StandardCharsets.US_ASCII);
        // 去掉 "GM " 前缀（版权标识，非 serial 部分）
        String cleaned = raw.trim();
        if (cleaned.startsWith("GM")) {
            cleaned = cleaned.substring(2).trim();
        }
        // 再去 #、-、空格
        String serial = cleaned.replace("#", "").replace("-", "").replace(" ", "").replace(",", "").trim();
        if (serial.length() < 3 || !isPrintableAscii(serial)) {
            logger.info("Genesis serial 无效: file={}, raw=[{}], cleaned=[{}], serial=[{}], len={}, printable={}",
                    fileName, raw.trim(), cleaned, serial, serial.length(), isPrintableAscii(serial));
            return null;
        }
        logger.debug("Genesis 头部提取: serial={} (raw=[{}]) from {}", serial, raw.trim(), fileName);
        return serial;
    }

    // ========== NDS (systemId=15) ==========

    /**
     * NDS: 固定 0x200 头部，偏移 0x0C 读取 4 字节 game code（如 "YUGJ"、"AMCP"）。
     * game code = 3 位游戏码 + 1 位区域，rom_db 即按此 4 位码建键。
     */
    private static String extractNDS(String filePath, String ext) throws IOException {
        if (!"nds".equals(ext) && !"ds".equals(ext)) return null;
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            if (raf.length() < 0x10) return null;
            byte[] buf = new byte[4];
            raf.seek(0x0C);
            raf.readFully(buf);
            String code = new String(buf, StandardCharsets.US_ASCII).trim();
            if (code.length() < 3 || !isPrintableAscii(code)) {
                logger.debug("NDS game code 无效: file={}, raw=[{}]", new File(filePath).getName(), code);
                return null;
            }
            logger.debug("NDS 头部提取: gameCode={} from {}", code, new File(filePath).getName());
            return code;
        }
    }

    // ========== 3DS (systemId=17) ==========

    /** 3DS product code 形如 CTR-P-A3RP / SKR-U-ABCD：3位码-1字母-2~4位。 */
    private static final Pattern PRODUCT_CODE_PATTERN =
            Pattern.compile("[A-Z0-9]{3}-[A-Z]-[A-Z0-9]{3,4}");

    /**
     * 3DS: .3ds/.cci 为 NCSD 镜像，product code 明文位于头部（实测稳定于 0x1150，ExHeader 0x4150 也有一份）。
     * 读前 64KB 搜索 product code 正则，兼容不同 dump 布局。不支持加密镜像/.cia 等容器变体。
     */
    private static String extract3DS(String filePath, String ext) throws IOException {
        if (!"3ds".equals(ext) && !"cci".equals(ext)) return null;
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            int readLen = (int) Math.min(raf.length(), 64L * 1024);
            if (readLen < 16) return null;
            byte[] header = new byte[readLen];
            raf.seek(0);
            raf.readFully(header);
            String txt = new String(header, StandardCharsets.ISO_8859_1);
            Matcher m = PRODUCT_CODE_PATTERN.matcher(txt);
            if (m.find()) {
                String code = m.group();
                logger.debug("3DS 头部提取: productCode={} (@0x{}) from {}",
                        code, Integer.toHexString(m.start()), new File(filePath).getName());
                return code;
            }
            logger.debug("3DS 未找到 product code: {}", new File(filePath).getName());
            return null;
        }
    }

    // ========== Wii (systemId=16) ==========

    /** Wii disc magic 0x5D1C9EA3（iso 在 @0x18，wbfs 在 @0x218）。 */
    private static final byte[] WII_DISC_MAGIC = { 0x5D, 0x1C, (byte) 0x9E, (byte) 0xA3 };

    /**
     * Wii: game ID 位于 disc header 前 6 字节。
     * <ul>
     *   <li>.iso/.gcm：disc header @0x000（Wii magic @0x18）</li>
     *   <li>.wbfs：WBFS 单游戏容器预置 512B 头，disc header @0x200（Wii magic @0x218）</li>
     * </ul>
     * 返回前 4 字节 game code（如 "RSPW"），因 rom_db 多按 4 位码建键（6 位全码常命中空元数据行）。
     * 由 RomDbServiceImpl.matchByDiscFile 对 systemId=16 调用（Wii 属 disc 系统，不走卡带 else 分支）。
     */
    public static String extractWiiGameId(String filePath) throws IOException {
        String ext = getFileExtension(filePath).toLowerCase();
        boolean wbfs = "wbfs".equals(ext);
        if (!wbfs && !"iso".equals(ext) && !"gcm".equals(ext)) return null;
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            long base = wbfs ? 0x200L : 0x0L;
            if (raf.length() < base + 0x1C) return null;
            byte[] gid = new byte[6];
            raf.seek(base);
            raf.readFully(gid);
            String code = new String(gid, StandardCharsets.US_ASCII).trim();
            if (code.length() < 4 || !isPrintableAscii(code.substring(0, 4))) {
                logger.debug("Wii game ID 无效: file={}, raw=[{}]", new File(filePath).getName(), code);
                return null;
            }
            byte[] magic = new byte[4];
            raf.seek(base + 0x18);
            raf.readFully(magic);
            boolean magicOk = magic[0] == WII_DISC_MAGIC[0] && magic[1] == WII_DISC_MAGIC[1]
                    && magic[2] == WII_DISC_MAGIC[2] && magic[3] == WII_DISC_MAGIC[3];
            if (!magicOk) {
                logger.debug("Wii disc magic 不匹配(仍尝试匹配): file={}, gid={}", new File(filePath).getName(), code);
            }
            String gameCode = code.substring(0, 4).toUpperCase();
            logger.debug("Wii 头部提取: gameCode={} (gid={}) from {}", gameCode, code, new File(filePath).getName());
            return gameCode;
        }
    }

    // ========== NES (systemId=3) ==========

    /**
     * NES: 从 iNES 头部提取产品编号。
     * <p>
     * iNES 头部格式：
     * - Bytes 0-3: "NES\x1A" 魔数
     * - Bytes 16-31: iNES 标题（16 字节，可能包含产品编号如 "NES-43-USA"）
     * <p>
     * 如果标题区域无有效编号，扫描 ROM 数据前 1KB 查找 "NES-" 模式。
     */
    private static String extractNESFromBytes(byte[] header, String fileName) {
        if (header.length < 16) {
            logger.debug("NES 头部不足: file={}, len={}", fileName, header.length);
            return null;
        }

        // 验证 iNES 魔数
        if (header[0] != 'N' || header[1] != 'E' || header[2] != 'S' || header[3] != 0x1A) {
            logger.debug("NES 魔数无效: file={}, header={}", fileName, bytesToHex(header, 4));
            return null;
        }

        // 尝试从 iNES 标题区域（bytes 16-31）提取
        if (header.length >= 32) {
            byte[] titleBytes = new byte[16];
            System.arraycopy(header, 16, titleBytes, 0, 16);
            String title = new String(titleBytes, StandardCharsets.US_ASCII).trim();
            logger.info("NES iNES 标题: file={}, title=[{}]", fileName, title);

            // 尝试从标题中提取 NES-XX 或 NES-XX-YYY 模式
            String serial = extractNESPattern(title);
            if (serial != null) {
                logger.info("NES 标题提取: serial={} from {}", serial, fileName);
                return serial;
            }
        }

        // 扫描 ROM 数据前 1KB 查找 NES- 模式
        int scanLen = Math.min(header.length, 1024);
        String romData = new String(header, 0, scanLen, StandardCharsets.US_ASCII);
        String serial = extractNESPattern(romData);
        if (serial != null) {
            logger.info("NES ROM 数据提取: serial={} from {}", serial, fileName);
            return serial;
        }

        logger.info("NES 未找到 serial: {}", fileName);
        return null;
    }

    /**
     * 从文本中提取 NES 产品编号模式（如 NES-43-USA, NES-BK）。
     */
    private static String extractNESPattern(String text) {
        if (text == null || text.isEmpty()) return null;
        // 匹配 NES-XX 或 NES-XX-YYY 模式
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("NES-[A-Z0-9]{2}(-[A-Z]{3})?");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    // ========== SNES/SFC (systemId=4) ==========

    /**
     * 从 SNES ROM 文件提取内部标题（internal title）。
     * <p>
     * SNES ROM 头部格式：
     * - LoROM: 偏移 0x7FC0
     * - HiROM: 偏移 0xFFC0
     * - 内部标题: 21 字节（偏移 0-20）
     * - 校验和验证: checksum + complement == 0xFFFF
     */
    private static String extractSNES(String filePath) throws IOException {
        File file = new File(filePath);
        String ext = getFileExtension(filePath).toLowerCase();

        // 读取整个文件（SNES ROM 通常较小，< 6MB）
        byte[] data = Files.readAllBytes(file.toPath());

        // 移除可选的 512 字节头部（如果文件大小 % 1024 == 512）
        int offset = 0;
        if (data.length % 1024 == 512) {
            offset = 512;
        }

        return extractSNESFromData(data, offset, file.getName());
    }

    /**
     * 从已读取的头部字节数组提取 SNES 内部标题。
     * <p>
     * ZIP 内预取了 0xFFE0 字节，足以覆盖 HiROM 头部（偏移 0xFFC0 + 32）。
     * 无法从 ZIP 部分读取中確认文件总长，因此同时尝试 offset=0 与 offset=512（带头部）。
     */
    private static String extractSNESFromBytes(byte[] header, String fileName) {
        // 优先尝试无头部（offset=0），失败时尝试带 512 字节头部
        String result = extractSNESFromData(header, 0, fileName + "#zip-nohdr");
        if (result != null) return result;
        return extractSNESFromData(header, 512, fileName + "#zip-hdr512");
    }

    /**
     * 从 SNES ROM 数据提取内部标题。
     */
    private static String extractSNESFromData(byte[] data, int offset, String fileName) {
        // 尝试 LoROM 和 HiROM 头部位置
        int headerStart = -1;
        for (int startAddr : new int[]{SNES_LOROM_HEADER_START, SNES_HIROM_HEADER_START}) {
            int absAddr = offset + startAddr;
            if (absAddr + 32 > data.length) continue;

            // 读取校验和（字节 30-31）和校验和补码（字节 28-29）
            int cs1 = data[absAddr + 30] & 0xFF;
            int cs2 = data[absAddr + 31] & 0xFF;
            int checksum = (cs2 << 8) | cs1;

            int csc1 = data[absAddr + 28] & 0xFF;
            int csc2 = data[absAddr + 29] & 0xFF;
            int checksumComplement = (csc2 << 8) | csc1;

            // 验证：checksum + complement == 0xFFFF
            if (checksum + checksumComplement == 0xFFFF) {
                headerStart = absAddr;
                break;
            }
        }

        if (headerStart < 0) {
            logger.debug("SNES 头部验证失败: {}", fileName);
            return null;
        }

        // 读取内部标题（21 字节）
        byte[] titleBytes = new byte[21];
        System.arraycopy(data, headerStart, titleBytes, 0, 21);

        // 转换为 ASCII 字符串，过滤非打印字符
        StringBuilder sb = new StringBuilder();
        for (byte b : titleBytes) {
            int c = b & 0xFF;
            if (c >= 0x20 && c <= 0x7E) {
                sb.append((char) c);
            } else if (c != 0) {
                sb.append(' ');
            }
        }
        String internalTitle = sb.toString().trim();

        if (internalTitle.isEmpty()) {
            logger.debug("SNES 内部标题为空: {}", fileName);
            return null;
        }

        logger.info("SNES 内部标题提取: title=[{}] from {}", internalTitle, fileName);
        return internalTitle;
    }

    // ========== 世嘉光盘 IP.BIN（Saturn/MegaCD/Dreamcast） ==========

    /**
     * 世嘉光盘通用 IP.BIN 提取：在第一数据轨的 BIN 文件中搜索指定魔数，
     * 然后从 magic+0x20 读取 10 字节，取第一个空格前的部分作为 serial。
     * <p>
     * 支持 .bin（直接读取）和 .cue（解析找到第一数据轨 BIN）。
     *
     * @param magic    平台魔数（SEGA SEGASATURN / SEGA SEGAMEGADRIVE / SEGA SEGAKATANA）
     * @param platform 平台名称（用于日志）
     */
    private static String extractSegaDisc(String filePath, String ext, byte[] magic, String platform, int serialOffset) throws IOException {
        if ("cue".equals(ext)) {
            String binPath = findFirstDataTrackBin(filePath);
            if (binPath == null) {
                logger.debug("{} CUE 解析失败: 未找到数据轨 BIN: {}", platform, filePath);
                return null;
            }
            return readSegaSerialFromBin(binPath, magic, platform, serialOffset, 256);
        } else if ("bin".equals(ext) || "iso".equals(ext)) {
            return readSegaSerialFromBin(filePath, magic, platform, serialOffset, 256);
        } else if ("cdi".equals(ext)) {
            // CDI (DiscJuggler)：数据未压缩，但 self-boot 的 IP.BIN 可能位于文件任意深度
            // （实测从 0x558C8 到 500MB+ 不等，取决于数据轨在镜像中的位置），故需全文件搜索魔数。
            return readSegaSerialFromBin(filePath, magic, platform, serialOffset, Long.MAX_VALUE);
        } else if ("gdi".equals(ext)) {
            // GDI (GD-ROM)：一个 .gdi 纯文本索引 + 多个 trackXX.bin/.raw 数据文件。
            // IP.BIN 位于数据轨 .bin 的轨头附近；因布局因 dump 而异，遍历 .gdi 里所有 .bin 数据轨，
            // 各扫前 64MB 搜魔数，命中即取 serial。
            List<String> dataBins = findGdiDataTrackBins(filePath);
            for (String bin : dataBins) {
                String serial = readSegaSerialFromBin(bin, magic, platform, serialOffset, 64L * 1024 * 1024);
                if (serial != null) return serial;
            }
            logger.debug("{} GDI 未在 {} 个数据轨中命中魔数: {}", platform, dataBins.size(), filePath);
            return null;
        } else {
            return null;
        }
    }

    /**
     * 从单个世嘉光盘 BIN 文件提取 IP.BIN serial。
     *
     * @param maxScan 搜索魔数的最大字节范围：&le;256 走“前 256 字节”快速路径（Saturn/MegaCD 的 BIN/CUE）；
     *                更大值走分窗口搜索（CDI 传 Long.MAX_VALUE，GDI 传 64MB）
     */
    private static String readSegaSerialFromBin(String binPath, byte[] magic, String platform, int serialOffset, long maxScan) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(binPath, "r")) {
            long fileLen = raf.length();
            long magicOffset;

            if (maxScan <= 256) {
                // 快速路径：IP.BIN 在前 256 字节内（Saturn/MegaCD 的 BIN/CUE）
                int readLen = (int) Math.min(fileLen, 256);
                if (readLen < magic.length + serialOffset + 12) return null;
                byte[] header = new byte[readLen];
                raf.readFully(header);
                int idx = findBytes(header, magic);
                if (idx < 0) {
                    logger.debug("{} 魔数未找到: {}", platform, new File(binPath).getName());
                    return null;
                }
                magicOffset = idx;
            } else {
                magicOffset = findMagicInFile(raf, magic, Math.min(fileLen, maxScan));
                if (magicOffset < 0) {
                    logger.debug("{} 魔数未找到(扫前 {}MB): {}", platform, maxScan / (1024 * 1024), new File(binPath).getName());
                    return null;
                }
            }

            // 从 magic + serialOffset 读取产品编号（Saturn/MegaCD: +0x20；Dreamcast: +0x40）
            int need = serialOffset + 12;
            if (magicOffset + need > fileLen) return null;
            byte[] blk = new byte[need];
            raf.seek(magicOffset);
            raf.readFully(blk);

            String rawId = new String(blk, serialOffset, 10, StandardCharsets.US_ASCII);
            // 取第一个空格前的部分
            String serial = rawId.trim().split("\\s+")[0].replace("-", "").trim();

            if (serial.length() < 3 || !isPrintableAscii(serial)) return null;

            logger.debug("{} IP.BIN 提取: serial={} from {}", platform, serial, new File(binPath).getName());
            return serial;
        }
    }

    /**
     * 解析 GDI 索引文件（GD-ROM 多文件镜像），返回所有数据轨 .bin 的绝对路径。
     * <p>
     * .gdi 格式：第一行为轨道总数；之后每行一个轨道：
     * {@code trackNo lba sectorType sectorSize fileName waveOffset}。
     * sectorType=0 为音频轨（对应 .raw），其余为数据轨（对应 .bin）。IP.BIN 在数据轨中。
     */
    private static List<String> findGdiDataTrackBins(String gdiPath) throws IOException {
        List<String> result = new ArrayList<>();
        File gdiFile = new File(gdiPath);
        String dir = gdiFile.getParent();
        List<String> lines = Files.readAllLines(Paths.get(gdiPath), StandardCharsets.ISO_8859_1);
        // 第 0 行 = 轨道总数（不依赖，直接遍历后续行）
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+");
            if (p.length < 5) continue;
            String sectorType = p[2];
            String fileName = p[4];
            // 只取 .bin 数据轨（type!=0）；.raw 音频轨无 IP.BIN，跳过
            if (fileName.toLowerCase().endsWith(".bin") && !"0".equals(sectorType)) {
                File f = (dir != null) ? new File(dir, fileName) : new File(fileName);
                if (f.exists()) result.add(f.getAbsolutePath());
            }
        }
        return result;
    }

    /**
     * 在大文件中分窗口搜索魔数（用于 CDI：IP.BIN 位置不固定，可能深至数百 MB）。
     * 用 ISO-8859-1 解码窗口 + String.indexOf（JVM intrinsified）逼近 memcmp 速度；
     * 窗口间保留 pattern.length-1 字节重叠，避免魔数跨窗口边界漏检。
     *
     * @return 魔数首次出现的绝对偏移；未找到返回 -1
     */
    private static long findMagicInFile(RandomAccessFile raf, byte[] magic, long fileLen) throws IOException {
        final int WINDOW = 8 * 1024 * 1024;   // 8MB 读窗口
        final int OVERLAP = magic.length - 1; // 防止魔数跨窗口边界漏检
        final String magicStr = new String(magic, StandardCharsets.ISO_8859_1);
        byte[] buf = new byte[WINDOW + OVERLAP];
        long base = 0;
        while (base < fileLen) {
            raf.seek(base);
            int read = raf.read(buf, 0, buf.length);
            if (read < magic.length) break;
            String win = new String(buf, 0, read, StandardCharsets.ISO_8859_1);
            int idx = win.indexOf(magicStr);
            if (idx >= 0) return base + idx;
            if (read < buf.length) break; // 已到文件末尾
            base += WINDOW;
        }
        return -1;
    }

    // ========== CUE 解析 ==========

    /**
     * 解析 CUE 文件，找到第一个 MODE1/MODE2 数据轨对应的 BIN 文件路径。
     */
    private static String findFirstDataTrackBin(String cuePath) throws IOException {
        String cueDir = new File(cuePath).getParent();
        List<String> lines = Files.readAllLines(Paths.get(cuePath), StandardCharsets.ISO_8859_1);

        String currentFile = null;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.toUpperCase().startsWith("FILE ")) {
                // 提取文件名（引号内）
                int q1 = trimmed.indexOf('"');
                int q2 = trimmed.indexOf('"', q1 + 1);
                if (q1 >= 0 && q2 > q1) {
                    currentFile = trimmed.substring(q1 + 1, q2);
                } else {
                    // 无引号：FILE xxx BINARY
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length >= 2) {
                        currentFile = parts[1];
                    }
                }
            } else if (trimmed.toUpperCase().startsWith("TRACK ")) {
                // 检查是否为数据轨
                String upper = trimmed.toUpperCase();
                if ((upper.contains("MODE1") || upper.contains("MODE2")) && currentFile != null) {
                    // 找到第一个数据轨
                    File binFile = new File(cueDir, currentFile);
                    if (binFile.exists()) {
                        return binFile.getAbsolutePath();
                    }
                    // 尝试原始路径
                    File rawFile = new File(currentFile);
                    if (rawFile.exists()) {
                        return rawFile.getAbsolutePath();
                    }
                }
            }
        }
        return null;
    }

    // ========== 工具方法 ==========

    /**
     * 在 byte 数组中搜索子数组，返回首次出现的偏移。
     */
    private static int findBytes(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static String getFileExtension(String filePath) {
        int dot = filePath.lastIndexOf('.');
        return dot >= 0 ? filePath.substring(dot + 1) : "";
    }

    private static boolean isPrintableAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!isPrintableAsciiChar(s.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isPrintableAsciiChar(char c) {
        return c >= 0x20 && c <= 0x7E;
    }

    private static String bytesToHex(byte[] bytes, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len && i < bytes.length; i++) {
            sb.append(String.format("%02X", bytes[i] & 0xFF));
            if (i < len - 1) sb.append(' ');
        }
        return sb.toString();
    }
}
