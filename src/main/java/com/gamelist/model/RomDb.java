package com.gamelist.model;

import java.time.LocalDateTime;

/**
 * ROM 识别数据库实体。
 * 统一存储卡带平台（CRC32）和光盘平台（serial）的匹配数据。
 * 数据来源：libretro-database（No-Intro + Redump 合并）。
 */
public class RomDb {

    private Long id;
    private Integer systemId;    // ScreenScraper systemId（0 = 无对应系统）
    private String platform;     // libretro-database 平台名
    private String crc32;        // 卡带类平台用（8位十六进制）
    private String serial;       // 光盘类平台用
    private String md5;          // MD5 哈希（32位十六进制）
    private String sha1;         // SHA1 哈希（40位十六进制）
    private String gameName;     // 标准游戏名称（英文）
    private String gameNameJp;   // 日文名称
    private String gameNameCn;   // 中文名称
    private String region;       // 区域（Japan/USA/Europe 等）
    private String source;       // 数据来源: 'redump' / 'no-intro' / 'serial' / 'rdb'
    private String developer;    // 开发商
    private String publisher;    // 发行商
    private String releaseyear;  // 发行年份
    private String genre;        // 游戏类型
    private String romFile;      // 原始 ROM 文件名（DAT 中 rom ( name ... ) 的真名，带扩展名），供生成器/备份还原用
    private String romFileNorm;  // romFile 归一化形式（去扩展名、小写），供按文件名精准匹配（建索引）
    private String gameNameLocalized; // 非持久化：由 field_translation 预取的当前语种译文（服务层 localizeRomDb 注入）
    private LocalDateTime createdAt;

    public RomDb() {}

    public RomDb(Integer systemId, String platform, String crc32, String serial,
                 String gameName, String region, String source) {
        this.systemId = systemId;
        this.platform = platform;
        this.crc32 = crc32;
        this.serial = serial;
        this.gameName = gameName;
        this.region = region;
        this.source = source;
    }

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getSystemId() { return systemId; }
    public void setSystemId(Integer systemId) { this.systemId = systemId; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getCrc32() { return crc32; }
    public void setCrc32(String crc32) { this.crc32 = crc32; }

    public String getSerial() { return serial; }
    public void setSerial(String serial) { this.serial = serial; }

    public String getMd5() { return md5; }
    public void setMd5(String md5) { this.md5 = md5; }

    public String getSha1() { return sha1; }
    public void setSha1(String sha1) { this.sha1 = sha1; }

    public String getGameName() { return gameName; }
    public void setGameName(String gameName) { this.gameName = gameName; }

    public String getGameNameJp() { return gameNameJp; }
    public void setGameNameJp(String gameNameJp) { this.gameNameJp = gameNameJp; }

    public String getGameNameCn() { return gameNameCn; }
    public void setGameNameCn(String gameNameCn) { this.gameNameCn = gameNameCn; }

    /**
     * 按语言偏好返回游戏名。
     * 优先使用服务层预取的 field_translation 译文（gameNameLocalized），
     * 无则回退到旧多语言列 game_name_jp/cn，最后回退英文名。
     * @param language "ja" → 日文名, "cn"/"zh" → 中文名, 其他 → 英文名（回退到可用名称）
     */
    public String getGameNameByLang(String language) {
        if (gameNameLocalized != null && !gameNameLocalized.isBlank()) {
            return gameNameLocalized;
        }
        if ("ja".equals(language)) {
            return (gameNameJp != null && !gameNameJp.isBlank()) ? gameNameJp : gameName;
        } else if ("cn".equals(language) || "zh".equals(language)) {
            return (gameNameCn != null && !gameNameCn.isBlank()) ? gameNameCn : gameName;
        }
        return gameName;
    }

    public String getGameNameLocalized() { return gameNameLocalized; }
    public void setGameNameLocalized(String gameNameLocalized) { this.gameNameLocalized = gameNameLocalized; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDeveloper() { return developer; }
    public void setDeveloper(String developer) { this.developer = developer; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public String getReleaseyear() { return releaseyear; }
    public void setReleaseyear(String releaseyear) { this.releaseyear = releaseyear; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getRomFile() { return romFile; }
    public void setRomFile(String romFile) { this.romFile = romFile; }

    public String getRomFileNorm() { return romFileNorm; }
    public void setRomFileNorm(String romFileNorm) { this.romFileNorm = romFileNorm; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
