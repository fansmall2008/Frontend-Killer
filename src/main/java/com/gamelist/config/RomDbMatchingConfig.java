package com.gamelist.config;

import org.springframework.stereotype.Component;
import java.util.Set;

/**
 * rom_db 本地匹配黑白名单配置。
 * 
 * <p>设计原则：
 * <ul>
 *   <li><b>白名单</b>：能从 ROM 内部可靠提取标识符的系统（SYSTEM.CNF/PARAM.SFO），走 serial 路径</li>
 *   <li><b>黑名单</b>：以光盘为载体但无法可靠提取 serial 的系统，禁止本地匹配（避免 CRC 全文件计算）</li>
 *   <li><b>默认</b>：卡带/街机平台走通用 CRC32 路径</li>
 * </ul>
 */
@Component
public class RomDbMatchingConfig {

    /**
     * 白名单系统：能从 ROM 内部可靠提取标识符的系统（优先走头部提取路径）。
     * <p>
     * 光盘平台：
     * - PS1 (57): SYSTEM.CNF → boot = scex:/SLUS_123.45;1 → SLUS-12345
     * - PS2 (58): SYSTEM.CNF → boot2 = cdrom0:\SLUS_123.45;1 → SLUS-12345
     * - PS3 (59): PARAM.SFO → TITLE_ID = BLUS-12345
     * - NGC (13): 镜像头部 6 字节 game ID（如 GALE01）
     * <p>
     * 卡带平台（头部 ID 提取，毫秒级，远快于 CRC32）：
     * - GB (9): 偏移 0x0134，16 字节 title（如 DMG-WQJ、BO7E）
     * - GBC (10): 偏移 0x0134，16 字节 title（如 CGB-VP9E、VPBE）
     * - GBA (12): 偏移 0xAC，4 字节 game code
     * - N64 (14): 偏移 0x3C，2+1 字节 cart ID + country
     * - Genesis (1): 偏移 0x180，16 字节 domestic serial
     * - SNES (4): LoROM 0x7FC0 / HiROM 0xFFC0，21 字节 internal title
     * <p>
     * 世嘉光盘（IP.BIN 头部提取，BIN/CUE/ISO；Dreamcast 另支持 CDI/GDI）：
     * - Saturn (22): 搜索 "SEGA SEGASATURN" magic → magic+0x20 读产品编号
     * - MegaCD (20): 搜索 "SEGA SEGAMEGADRIVE" magic → magic+0x20 读产品编号
     * - Dreamcast (23): 搜索 "SEGA SEGAKATANA" magic → magic+0x40 读产品编号（注意偏移区别于 Saturn/MegaCD 的 +0x20）
     * <p>
     * 任天堂掌机 / Wii（新增，头部标识符提取）：
     * - NDS (15): 偏移 0x0C，4 字节 game code（卡带 else 分支，CartridgeHeaderReader.extractNDS）
     * - 3DS (17): .3ds/.cci 头部搜索 product code（如 CTR-P-A3RP，实测稳定于 0x1150）
     * - Wii (16): iso @0x00 / wbfs @0x200 取 6 字节 disc header 前 4 位 game code（disc 分支，matchByDiscFile.extractWiiGameId）
     */
    private static final Set<Integer> WHITELISTED_SERIAL_SYSTEMS = Set.of(
        // 光盘平台
        57,  // Sony - PlayStation (SYSTEM.CNF)
        58,  // Sony - PlayStation 2 (SYSTEM.CNF)
        59,  // Sony - PlayStation 3 (PARAM.SFO)
        13,  // Nintendo - GameCube (镜像头部 6 字节 game ID)
        // 卡带平台（头部 ID 提取）
        9,   // Nintendo - Game Boy (0x0134, 16 bytes title)
        10,  // Nintendo - Game Boy Color (0x0134, 16 bytes title)
        12,  // Nintendo - Game Boy Advance (0xAC, 4 bytes)
        14,  // Nintendo - Nintendo 64 (0x3C, 3 bytes)
        1,   // Sega - Genesis / Mega Drive (0x180, 16 bytes)
        4,   // Nintendo - SNES/SFC (LoROM 0x7FC0 / HiROM 0xFFC0, 21 bytes internal title)
        // 世嘉光盘（IP.BIN）
        22,  // Sega Saturn (BIN/CUE/ISO, magic+0x20)
        20,  // Sega Mega-CD / Sega CD (BIN/CUE/ISO, magic+0x20)
        23,  // Sega Dreamcast (BIN/CUE/ISO/CDI/GDI, magic+0x40)
        // 任天堂掌机 / Wii（新增）
        15,  // Nintendo - Nintendo DS (0x0C, 4 bytes game code)
        17,  // Nintendo - Nintendo 3DS (.3ds/.cci product code @0x1150)
        16   // Nintendo - Wii (iso @0x00 / wbfs @0x200, 4-byte game code)
    );

    /**
     * 黑名单系统：以光盘为载体但无法可靠提取 serial，禁止本地匹配。
     * <p>
     * 原因：
     * <ul>
     *   <li>无法从 ROM 内部提取 serial（无 SYSTEM.CNF/PARAM.SFO/标准文件系统）</li>
     *   <li>CRC32 全文件计算耗时过长（单文件 30-60s，NAS 网络延迟）</li>
     *   <li>汉化版 ROM 即使 CRC 匹配成功，元数据也全空，仍需 ScreenScraper 刮削</li>
     * </ul>
     */
    private static final Set<Integer> BLACKLISTED_SYSTEMS = Set.of(
        72,  // NEC - PC-FX (CD-ROM, 无标准文件系统，实测无法提取 serial)
        // 22 已移至白名单（Saturn IP.BIN 头部提取，BIN/CUE 格式）
        // 20 已移至白名单（MegaCD IP.BIN 头部提取，BIN/CUE 格式）
        // 23 已移至白名单（Dreamcast IP.BIN 头部提取，BIN/CUE 格式）
        114, // PC Engine CD-Rom (光盘，文件名正则不可靠)
        // 16 已移至白名单（Wii game ID 头部提取：iso @0x00 / wbfs @0x200，实测 8/8 命中）
        // 13 已移至白名单（NGC 镜像头部 6 字节 game ID 提取，实测 100% 可靠）
        70,  // Neo Geo CD (光盘，文件名正则不可靠)
        29,  // 3DO Interactive Multiplayer (光盘，文件名正则不可靠)
        61   // Sony PSP (UMD 光盘，文件名正则不可靠，待验证 internal file 提取)
    );

    /**
     * 判断是否为白名单系统（优先走 serial 提取路径）。
     */
    public boolean isWhitelisted(int systemId) {
        return WHITELISTED_SERIAL_SYSTEMS.contains(systemId);
    }

    /**
     * 判断是否为黑名单系统（禁止本地匹配）。
     */
    public boolean isBlacklisted(int systemId) {
        return BLACKLISTED_SYSTEMS.contains(systemId);
    }

    /**
     * 获取白名单系统集合（供前端 API 返回）。
     */
    public Set<Integer> getWhitelistedSystems() {
        return WHITELISTED_SERIAL_SYSTEMS;
    }

    /**
     * 获取黑名单系统集合（供前端 API 返回）。
     */
    public Set<Integer> getBlacklistedSystems() {
        return BLACKLISTED_SYSTEMS;
    }
}
