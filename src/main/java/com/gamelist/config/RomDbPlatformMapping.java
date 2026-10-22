package com.gamelist.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * libretro-database 平台名 → ScreenScraper systemId 映射。
 * <p>
 * 映射来源：term_mapping (system_alias) + system-baseline.json。
 * systemId = 0 表示无对应 ScreenScraper 系统（数据照样入库，不参与刮削匹配）。
 * <p>
 * key = libretro-database 的 DAT 文件名（不含 .dat 后缀），
 * 如 "Sony - PlayStation 2" 对应文件 Sony - PlayStation 2.dat。
 */
public final class RomDbPlatformMapping {

    private RomDbPlatformMapping() {}

    /** libretro-database 平台名 → ScreenScraper systemId */
    private static final Map<String, Integer> MAP;

    static {
        Map<String, Integer> m = new LinkedHashMap<>();

        // ========== 任天堂 ==========
        m.put("Nintendo - Nintendo Entertainment System", 3);    // NES / FC
        m.put("Nintendo - Super Nintendo Entertainment System", 4); // SFC / SNES
        m.put("Nintendo - Game Boy", 9);
        m.put("Nintendo - Game Boy Color", 10);
        m.put("Nintendo - Game Boy Advance", 12);
        m.put("Nintendo - Nintendo 64", 14);
        m.put("Nintendo - GameCube", 13);
        m.put("Nintendo - Nintendo DS", 15);
        m.put("Nintendo - Wii", 16);
        m.put("Nintendo - Nintendo 3DS", 17);
        m.put("Nintendo - Wii U", 18);
        m.put("Nintendo - Virtual Boy", 11);
        m.put("Nintendo - Family Computer Disk System", 106);    // FDS
        m.put("Nintendo - Nintendo 64DD", 143);
        m.put("Nintendo - Satellaview", 107);
        m.put("Nintendo - Sufami Turbo", 112);
        m.put("Nintendo - Pokemon Mini", 211);
        m.put("Nintendo - New Nintendo 3DS", 282);
        m.put("Nintendo - Nintendo DSi", 15);   // DSi 共用 NDS 的 systemId
        m.put("Nintendo - e-Reader", 0);         // 无 SS 对应

        // ========== 世嘉 ==========
        m.put("Sega - Mega Drive - Genesis", 1);  // MD/Genesis
        m.put("Sega - Master System - Mark III", 2); // SMS
        m.put("Sega - Game Gear", 21);
        m.put("Sega - Saturn", 22);
        m.put("Sega - Dreamcast", 23);
        m.put("Sega - Mega-CD - Sega CD", 20);
        m.put("Sega - 32X", 19);
        m.put("Sega - SG-1000", 109);
        m.put("Sega - PICO", 0);
        m.put("Sega - Beena", 0);
        m.put("Sega - Naomi", 56);
        m.put("Sega - Naomi 2", 56);  // 共用 Naomi

        // ========== 索尼 ==========
        m.put("Sony - PlayStation", 57);
        m.put("Sony - PlayStation 2", 58);
        m.put("Sony - PlayStation 3", 59);
        m.put("Sony - PlayStation Portable", 61);
        m.put("Sony - PlayStation Vita", 62);
        m.put("Sony - PlayStation 3 (PSN)", 59);     // PSN 归入 PS3
        m.put("Sony - PlayStation Portable (PSN)", 61);
        m.put("Sony - PlayStation Portable (PSX2PSP)", 61);
        m.put("Sony - PlayStation Portable (UMD Music)", 0);
        m.put("Sony - PlayStation Portable (UMD Video)", 0);
        m.put("Sony - PlayStation Vita (PSN)", 62);

        // ========== NEC ==========
        m.put("NEC - PC Engine - TurboGrafx 16", 31);
        m.put("NEC - PC Engine CD - TurboGrafx-CD", 114);
        m.put("NEC - PC Engine SuperGrafx", 31);  // 归入 PCE
        m.put("NEC - PC-FX", 72);
        m.put("NEC - PC-98", 203);

        // ========== SNK ==========
        m.put("SNK - Neo Geo CD", 70);
        m.put("SNK - Neo Geo", 142);
        m.put("SNK - Neo Geo Pocket", 25);
        m.put("SNK - Neo Geo Pocket Color", 82);

        // ========== 微软 ==========
        m.put("Microsoft - Xbox", 32);
        m.put("Microsoft - Xbox 360", 33);
        m.put("Microsoft - Xbox 360 (Digital)", 33);
        m.put("Microsoft - Xbox 360 (Title Updates)", 0);

        // ========== Atari ==========
        m.put("Atari - 2600", 26);
        m.put("Atari - 5200", 40);
        m.put("Atari - 7800", 41);
        m.put("Atari - Jaguar", 27);
        m.put("Atari - Jaguar CD", 27);  // Jaguar CD 归入 Jaguar
        m.put("Atari - Lynx", 28);
        m.put("Atari - ST", 42);
        m.put("Atari - 8-bit Family", 0);

        // ========== Bandai ==========
        m.put("Bandai - WonderSwan", 45);
        m.put("Bandai - WonderSwan Color", 46);

        // ========== Commodore ==========
        m.put("Commodore - 64", 66);
        m.put("Commodore - Amiga", 64);
        m.put("Commodore - CD32", 78);
        m.put("Commodore - CDTV", 0);
        m.put("Commodore - PET", 0);
        m.put("Commodore - Plus-4", 0);
        m.put("Commodore - VIC-20", 0);

        // ========== 3DO ==========
        m.put("The 3DO Company - 3DO", 29);

        // ========== Coleco ==========
        m.put("Coleco - ColecoVision", 48);

        // ========== 街机 ==========
        m.put("FBNeo - Arcade Games", 75);   // 归入 MAME/FBA
        m.put("MAME", 75);                   // 归入 MAME/FBA
        m.put("MAME 2000", 75);              // 归入 MAME/FBA
        m.put("MAME 2003", 75);              // 归入 MAME/FBA
        m.put("MAME 2003-Plus", 75);         // 归入 MAME/FBA
        m.put("MAME 2010", 75);              // 归入 MAME/FBA
        m.put("MAME 2015", 75);              // 归入 MAME/FBA
        m.put("MAME 2016", 75);              // 归入 MAME/FBA
        m.put("HBMAME", 75);                 // 归入 MAME/FBA
        m.put("MAME-nonmerged", 75);         // 归入 MAME/FBA

        // ========== 其他 ==========
        m.put("GCE - Vectrex", 43);
        m.put("Magnavox - Odyssey2", 103);
        m.put("Mattel - Intellivision", 110);
        m.put("Microsoft - MSX", 113);
        m.put("Microsoft - MSX2", 116);
        m.put("Amstrad - CPC", 65);
        m.put("Sinclair - ZX Spectrum", 76);
        m.put("Sinclair - ZX 81", 0);
        m.put("Sharp - X68000", 79);
        m.put("Sharp - X1", 227);
        m.put("DOS", 135);
        m.put("Philips - CD-i", 117);
        m.put("Casio - Loopy", 0);
        m.put("Casio - PV-1000", 0);
        m.put("Epoch - Super Cassette Vision", 0);
        m.put("Tiger - Game.com", 0);
        m.put("VTech - V.Smile", 0);
        m.put("VTech - CreatiVision", 0);
        m.put("Watara - Supervision", 208);
        m.put("GamePark - GP32", 0);
        m.put("Emerson - Arcadia 2001", 0);
        m.put("Enterprise - 128", 0);
        m.put("Fairchild - Channel F", 0);
        m.put("Hartung - Game Master", 0);
        m.put("LeapFrog - Leapster Learning Game System", 0);
        m.put("RCA - Studio II", 0);

        MAP = Collections.unmodifiableMap(m);
    }

    /**
     * 获取 libretro-database 平台名对应的 ScreenScraper systemId。
     *
     * @param platformName libretro-database 平台名（DAT 文件名不含 .dat）
     * @return systemId，未映射返回 0
     */
    public static int getSystemId(String platformName) {
        return MAP.getOrDefault(platformName, 0);
    }

    /** 获取完整映射（只读） */
    public static Map<String, Integer> getAll() {
        return MAP;
    }

    /** 判断平台是否有 ScreenScraper 对应 */
    public static boolean hasSystem(String platformName) {
        Integer id = MAP.get(platformName);
        return id != null && id > 0;
    }
}
