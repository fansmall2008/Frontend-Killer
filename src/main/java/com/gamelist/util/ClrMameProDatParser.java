package com.gamelist.util;

import com.gamelist.model.RomDb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 clrmamepro 格式的 DAT 文件（libretro-database metadat）。
 * <p>
 * 支持两种变体：
 * <ul>
 *   <li>Redump/No-Intro 格式：game 块含 name/region/serial/rom(crc...) 属性</li>
 *   <li>Serial 格式：game 块含 comment(游戏名)/serial/rom(crc) 属性</li>
 * </ul>
 */
public class ClrMameProDatParser {

    private static final Logger logger = LoggerFactory.getLogger(ClrMameProDatParser.class);

    // 快速正则提取
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern CRC_PATTERN = Pattern.compile("\\bcrc\\s+([0-9A-Fa-f]{8})\\b");
    private static final Pattern MD5_PATTERN = Pattern.compile("\\bmd5\\s+([0-9A-Fa-f]{32})\\b");
    private static final Pattern SHA1_PATTERN = Pattern.compile("\\bsha1\\s+([0-9A-Fa-f]{40})\\b");
    private static final Pattern SERIAL_ATTR = Pattern.compile("\\bserial\\s+\"([^\"]+)\"");
    private static final Pattern NAME_ATTR = Pattern.compile("\\bname\\s+\"([^\"]+)\"");
    // ROM 文件名：rom ( name <X> ... ) 中的 X，可为裸名（88games.zip）或带引号名（"Super Game (USA).sfc"）。
    // 仅锚定 rom 级 name，不会误抓 game 级带引号标题。
    private static final Pattern ROM_ENTRY_ATTR = Pattern.compile("rom\\s*\\(\\s*name\\s+(\"[^\"]*\"|[^\\s)]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGION_ATTR = Pattern.compile("\\bregion\\s+\"([^\"]+)\"");
    private static final Pattern COMMENT_ATTR = Pattern.compile("\\bcomment\\s+\"([^\"]+)\"");
    private static final Pattern DEVELOPER_ATTR = Pattern.compile("\\bdeveloper\\s+\"([^\"]+)\"");
    private static final Pattern PUBLISHER_ATTR = Pattern.compile("\\bpublisher\\s+\"([^\"]+)\"");
    private static final Pattern RELEASEYEAR_ATTR = Pattern.compile("\\breleaseyear\\s+\"([^\"]+)\"");
    private static final Pattern GENRE_ATTR = Pattern.compile("\\bgenre\\s+\"([^\"]+)\"");

    private ClrMameProDatParser() {}

    /**
     * 解析 DAT 文件，返回 RomDb 记录列表。
     *
     * @param file         DAT 文件
     * @param platformName 平台名（从文件名提取，不含 .dat）
     * @param systemId     ScreenScraper systemId
     * @param source       数据来源 ("redump" / "no-intro" / "serial")
     * @return 解析出的记录列表
     */
    public static List<RomDb> parse(File file, String platformName, int systemId, String source) throws IOException {
        List<RomDb> results = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String line;
            boolean inGame = false;
            StringBuilder gameBlock = null;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.startsWith("game") && trimmed.contains("(")) {
                    inGame = true;
                    gameBlock = new StringBuilder();
                    gameBlock.append(trimmed).append('\n');
                } else if (inGame) {
                    gameBlock.append(trimmed).append('\n');
                    if (trimmed.equals(")")) {
                        // 游戏块结束，解析
                        RomDb entry = parseGameBlock(gameBlock.toString(), platformName, systemId, source);
                        if (entry != null) {
                            results.add(entry);
                        }
                        inGame = false;
                        gameBlock = null;
                    }
                }
            }
        }

        logger.debug("Parsed {} entries from {} (platform={})", results.size(), file.getName(), platformName);
        return results;
    }

    /**
     * 解析单个 game 块。
     */
    private static RomDb parseGameBlock(String block, String platformName, int systemId, String source) {
        String gameName = null;
        String region = null;
        String serial = null;
        String crc32 = null;
        String md5 = null;
        String sha1 = null;
        String developer = null;
        String publisher = null;
        String releaseyear = null;
        String genre = null;
        String romFile = null;
        String romFileNorm = null;

        // 提取 name
        Matcher nameMatcher = NAME_ATTR.matcher(block);
        if (nameMatcher.find()) {
            gameName = nameMatcher.group(1);
        }

        // serial/comment 格式用 comment 作为游戏名
        if ("serial".equals(source)) {
            Matcher commentMatcher = COMMENT_ATTR.matcher(block);
            if (commentMatcher.find()) {
                gameName = commentMatcher.group(1);
            }
        }

        // 提取 region
        Matcher regionMatcher = REGION_ATTR.matcher(block);
        if (regionMatcher.find()) {
            region = regionMatcher.group(1);
        }

        // 提取 serial
        Matcher serialMatcher = SERIAL_ATTR.matcher(block);
        if (serialMatcher.find()) {
            serial = serialMatcher.group(1);
        }

        // 提取 crc32（从 rom 行）
        Matcher crcMatcher = CRC_PATTERN.matcher(block);
        if (crcMatcher.find()) {
            crc32 = crcMatcher.group(1).toUpperCase();
        }

        // 提取 md5
        Matcher md5Matcher = MD5_PATTERN.matcher(block);
        if (md5Matcher.find()) {
            md5 = md5Matcher.group(1).toUpperCase();
        }

        // 提取 sha1
        Matcher sha1Matcher = SHA1_PATTERN.matcher(block);
        if (sha1Matcher.find()) {
            sha1 = sha1Matcher.group(1).toUpperCase();
        }

        // 提取 ROM 文件名：扫描块内所有 rom ( name ... ) 条目（全系统），
        // 优先取容器（.zip/.7z），否则取首个；存储时保留原始名（带扩展），另存归一化名供匹配。
        java.util.List<String> romNames = new ArrayList<>();
        Matcher romFileMatcher = ROM_ENTRY_ATTR.matcher(block);
        while (romFileMatcher.find()) {
            String t = romFileMatcher.group(1);
            if (t.startsWith("\"")) t = t.substring(1, t.length() - 1);
            t = t.trim();
            if (!t.isEmpty()) romNames.add(t);
        }
        if (!romNames.isEmpty()) {
            String pick = null;
            for (String rn : romNames) {
                String low = rn.toLowerCase();
                if (low.endsWith(".zip") || low.endsWith(".7z")) { pick = rn; break; }
            }
            if (pick == null) pick = romNames.get(0);
            romFile = pick;                              // 原始文件名（带扩展名）
            romFileNorm = normalizeRomFileNorm(pick);    // 归一化（去扩展名、小写）供匹配
        }

        // 提取元数据字段（developer/publisher/releaseyear/genre）
        Matcher devMatcher = DEVELOPER_ATTR.matcher(block);
        if (devMatcher.find()) {
            developer = devMatcher.group(1);
        }
        Matcher pubMatcher = PUBLISHER_ATTR.matcher(block);
        if (pubMatcher.find()) {
            publisher = pubMatcher.group(1);
        }
        Matcher yearMatcher = RELEASEYEAR_ATTR.matcher(block);
        if (yearMatcher.find()) {
            releaseyear = yearMatcher.group(1);
        }
        Matcher genreMatcher = GENRE_ATTR.matcher(block);
        if (genreMatcher.find()) {
            genre = genreMatcher.group(1);
        }

        // 至少要有游戏名
        if (gameName == null || gameName.isBlank()) {
            return null;
        }

        // 至少要有 crc32 或 serial 之一
        if (crc32 == null && serial == null) {
            return null;
        }

        // 归一化 serial（去空格，转大写）
        if (serial != null) {
            serial = serial.replaceAll("\\s+", "").toUpperCase();
        }

        RomDb romDb = new RomDb(systemId, platformName, crc32, serial, gameName, region, source);
        romDb.setMd5(md5);
        romDb.setSha1(sha1);
        romDb.setDeveloper(developer);
        romDb.setPublisher(publisher);
        romDb.setReleaseyear(releaseyear);
        romDb.setGenre(genre);
        romDb.setRomFile(romFile);
        romDb.setRomFileNorm(romFileNorm);
        return romDb;
    }

    /** 归一化 ROM 文件名供匹配：去扩展名 + trim + 小写（与 RomDbServiceImpl.normalizeRomFileName 一致）。*/
    private static String normalizeRomFileNorm(String fileName) {
        if (fileName == null) return null;
        String s = fileName;
        int dot = s.lastIndexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        return s.trim().toLowerCase();
    }

    /**
     * 自动检测 DAT 文件的来源类型。
     * <p>
     * 读取前 200 行，根据特征字段判断：
     * <ul>
     *   <li>含 comment 属性 → "serial"</li>
     *   <li>含 region 属性 → "redump"</li>
     *   <li>其他 → "no-intro"</li>
     * </ul>
     */
    public static String detectSource(File file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            boolean hasComment = false;
            boolean hasRegion = false;
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 200) {
                lineCount++;
                String trimmed = line.trim();
                if (trimmed.startsWith("comment ") && trimmed.contains("\"")) {
                    hasComment = true;
                }
                if (trimmed.startsWith("region ") && trimmed.contains("\"")) {
                    hasRegion = true;
                }
                if (hasComment && hasRegion) break; // 已足够判断
            }
            if (hasComment) return "serial";
            if (hasRegion) return "redump";
            return "no-intro";
        } catch (IOException e) {
            logger.warn("无法检测 DAT 文件类型: {}，默认为 no-intro", file.getName());
            return "no-intro";
        }
    }

    /**
     * 从文件名提取平台名（去掉 .dat 后缀）。
     */
    public static String platformNameFromFileName(String fileName) {
        if (fileName.endsWith(".dat")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }
}
