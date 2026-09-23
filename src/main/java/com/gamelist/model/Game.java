package com.gamelist.model;

import java.util.Map;

public class Game {
    private Long id;
    private String gameId;
    /** ScreenScraper 全局游戏ID：媒体目录稳定键（跨平台复用） */
    private Long ssGameId;
    private String source;
    private String path;
    private String name;
    private String desc;
    private String translatedName;
    private String translatedDesc;
    private String image;
    /** @deprecated 使用 {@link #manuel} 代替 (ScreenScraper标准) */
    @Deprecated private String manual;
    private String thumbnail;
    // ========== ScreenScraper 标准媒体字段（以官方 nomcourt 为命名标准） ==========
    // --- 包装盒类 (Elements Boitiers / Boitiers) ---
    private String box2d;           // box-2D (包装盒正面)
    private String box2dBack;       // box-2D-back (包装盒背面)
    private String box2dSide;       // box-2D-side (包装盒侧面)
    private String box3D;           // box-3D (3D包装盒)
    private String boxTexture;      // box-texture (包装盒纹理)
    private String boxScan;         // box-scan (包装盒扫描源)
    // --- 支撑类 (Supports / Sources) ---
    private String support2d;       // support-2D (支撑图2D)
    private String supportTexture;  // support-texture (支撑纹理)
    private String supportScan;     // support-scan (支撑扫描源)
    // --- 轮盘类 (Logos/Wheels) ---
    private String wheel;           // wheel (轮盘)
    private String wheelHd;         // wheel-hd (高清轮盘)
    private String wheelCarbon;     // wheel-carbon (碳纤维轮盘)
    private String wheelSteel;      // wheel-steel (钢铁轮盘)
    // --- 霓虹灯类 (Marquee) ---
    private String marquee;         // marquee (霓虹灯)
    private String screenmarquee;   // screenmarquee (屏幕霓虹灯)
    private String screenmarqueesmall; // screenmarqueesmall (小屏幕霓虹灯)
    // --- 通用媒体类 (Médias) ---
    private String ss;              // ss (截图)
    private String sstitle;         // sstitle (标题截图)
    private String steamgrid;       // steamgrid (Steam网格)
    private String fanart;          // fanart (粉丝艺术)
    private String overlay;         // overlay (覆盖层)
    private String video;           // video (视频)
    private String videoNormalized; // video-normalized (标准化视频)
    // --- 次要媒体类 (Médias Secondaires) ---
    private String flyer;           // flyer (传单)
    private String manuel;          // manuel (手册)
    private String maps;            // maps (地图)
    private String figurine;        // figurine (手办)
    // --- 边框类 (Bezels) ---
    private String bezel43;         // bezel-4-3 (边框4:3横屏)
    private String bezel43V;        // bezel-4-3-v (边框4:3竖屏)
    private String bezel43Cocktail; // bezel-4-3-cocktail (边框4:3鸡尾酒)
    private String bezel169;        // bezel-16-9 (边框16:9横屏)
    private String bezel169V;       // bezel-16-9-v (边框16:9竖屏)
    private String bezel169Cocktail;// bezel-16-9-cocktail (边框16:9鸡尾酒)
    // --- 混合类 (Mixes) ---
    private String mixrbv1;         // mixrbv1
    private String mixrbv2;         // mixrbv2
    // --- 主题类 (Themes) ---
    private String themehb;         // themehb
    private String themehs;         // themehs
    // --- 弹球台专用 (Médias Pincab) ---
    private String ssdmd;           // ssdmd
    private String ssfronton169;    // ssfronton16-9
    private String ssfronton11;     // ssfronton1-1
    private String ssfronton43;     // ssfronton4-3
    private String sstable;         // sstable
    private String sstopper;        // sstopper
    private String videodmd;        // videodmd
    private String videofronton43;  // videofronton4-3
    private String videofronton169; // videofronton16-9
    private String videotable;      // videotable
    private String videotable4k;    // videotable4k
    private String videotopper;     // videotopper
    private String wheelTarcisios;  // wheel-tarcisios

    // ========== 非 ScreenScraper 遗留字段（保留兼容，标记废弃） ==========
    /** @deprecated 使用 {@link #box2d} 代替 */
    @Deprecated private String boxFront;
    /** @deprecated 使用 {@link #box2dBack} 代替 */
    @Deprecated private String boxBack;
    /** @deprecated 使用 {@link #box2dSide} 代替 */
    @Deprecated private String boxSpine;
    private String boxFull;
    /** @deprecated 使用 {@link #support2d} 代替 */
    @Deprecated private String cartridge;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String logo;
    /** @deprecated 使用 {@link #bezel43} 或 {@link #bezel169} 代替 */
    @Deprecated private String bezel;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String panel;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String cabinetLeft;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String cabinetRight;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String tile;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String banner;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String steam;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String poster;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String background;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String music;
    /** @deprecated 使用 {@link #ss} 代替 */
    @Deprecated private String screenshot;
    /** @deprecated 使用 {@link #sstitle} 代替 */
    @Deprecated private String titlescreen;
    /** @deprecated 使用 {@link #boxTexture} 代替 */
    @Deprecated private String boxtexture;
    /** @deprecated 使用 {@link #supportTexture} 代替 */
    @Deprecated private String supporttexture;
    /** @deprecated 使用 {@link #videoNormalized} 代替 */
    @Deprecated private String videonormalized;
    /** @deprecated 使用 {@link #wheelCarbon} 代替 */
    @Deprecated private String wheelcarbon;
    /** @deprecated 使用 {@link #wheelSteel} 代替 */
    @Deprecated private String wheelsteel;
    /** @deprecated 使用 {@link #box2dSide} 代替 */
    @Deprecated private String boxside;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String pictoliste;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String pictomonochrome;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String pictomonochromesvg;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String pictocouleur;
    /** @deprecated 非 ScreenScraper 标准类型 */
    @Deprecated private String wallpaper;
    private Double rating;
    private String releasedate;
    private String developer;
    private String publisher;
    private String genre;
    private String players;
    private String crc32;
    private String md5;
    private String lang;
    private String genreid;
    private String hash;
    private String platformType;
    private Long platformId;
    private String sortBy;
    private Map<String, String> platformSpecificFields;
    private Boolean scraped;
    private Boolean edited;
    private Boolean exists;
    private String absolutePath;
    private String platformPath;
    // ========== 多文件游戏（多盘/合盘） ==========
    private Boolean multiFile;         // 是否多文件（多盘游戏）
    private String multiFileContent;   // 多文件文本（m3u 格式：一行一个文件路径，相对平台目录，不带 ./）
    // ========== 游戏信息缓存（SS manifest） ==========
    private Boolean cached;            // 是否已缓存完整 jeuInfos manifest（按 ssGameId）
    private String parentRom;          // 父 rom 文件名（从 cloneof 投影，仅街机类，导出时 copy-if-missing）
    
    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public Long getSsGameId() {
        return ssGameId;
    }

    public void setSsGameId(Long ssGameId) {
        this.ssGameId = ssGameId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getTranslatedName() {
        return translatedName;
    }

    public void setTranslatedName(String translatedName) {
        this.translatedName = translatedName;
    }

    public String getTranslatedDesc() {
        return translatedDesc;
    }

    public void setTranslatedDesc(String translatedDesc) {
        this.translatedDesc = translatedDesc;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getVideo() {
        return video;
    }

    public void setVideo(String video) {
        this.video = video;
    }

    public String getMarquee() {
        return marquee;
    }

    public void setMarquee(String marquee) {
        this.marquee = marquee;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getWheel() {
        return wheel;
    }

    public void setWheel(String wheel) {
        this.wheel = wheel;
    }

    public String getManual() {
        return manual;
    }

    public void setManual(String manual) {
        this.manual = manual;
    }

    public String getBoxFront() {
        return boxFront;
    }

    public void setBoxFront(String boxFront) {
        this.boxFront = boxFront;
    }

    public String getBoxBack() {
        return boxBack;
    }

    public void setBoxBack(String boxBack) {
        this.boxBack = boxBack;
    }

    public String getBoxSpine() {
        return boxSpine;
    }

    public void setBoxSpine(String boxSpine) {
        this.boxSpine = boxSpine;
    }

    public String getBoxFull() {
        return boxFull;
    }

    public void setBoxFull(String boxFull) {
        this.boxFull = boxFull;
    }

    public String getCartridge() {
        return cartridge;
    }

    public void setCartridge(String cartridge) {
        this.cartridge = cartridge;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getBezel() {
        return bezel;
    }

    public void setBezel(String bezel) {
        this.bezel = bezel;
    }

    public String getPanel() {
        return panel;
    }

    public void setPanel(String panel) {
        this.panel = panel;
    }

    public String getCabinetLeft() {
        return cabinetLeft;
    }

    public void setCabinetLeft(String cabinetLeft) {
        this.cabinetLeft = cabinetLeft;
    }

    public String getCabinetRight() {
        return cabinetRight;
    }

    public void setCabinetRight(String cabinetRight) {
        this.cabinetRight = cabinetRight;
    }

    public String getTile() {
        return tile;
    }

    public void setTile(String tile) {
        this.tile = tile;
    }

    public String getBanner() {
        return banner;
    }

    public void setBanner(String banner) {
        this.banner = banner;
    }

    public String getSteam() {
        return steam;
    }

    public void setSteam(String steam) {
        this.steam = steam;
    }

    public String getPoster() {
        return poster;
    }

    public void setPoster(String poster) {
        this.poster = poster;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public String getMusic() {
        return music;
    }

    public void setMusic(String music) {
        this.music = music;
    }

    public String getScreenshot() {
        return screenshot;
    }

    public void setScreenshot(String screenshot) {
        this.screenshot = screenshot;
    }

    public String getTitlescreen() {
        return titlescreen;
    }

    public void setTitlescreen(String titlescreen) {
        this.titlescreen = titlescreen;
    }

    public String getBox3D() {
        return box3D;
    }

    public void setBox3D(String box3D) {
        this.box3D = box3D;
    }

    public String getSteamgrid() {
        return steamgrid;
    }

    public void setSteamgrid(String steamgrid) {
        this.steamgrid = steamgrid;
    }

    public String getFanart() {
        return fanart;
    }

    public void setFanart(String fanart) {
        this.fanart = fanart;
    }

    public String getBoxtexture() {
        return boxtexture;
    }

    public void setBoxtexture(String boxtexture) {
        this.boxtexture = boxtexture;
    }

    public String getSupporttexture() {
        return supporttexture;
    }

    public void setSupporttexture(String supporttexture) {
        this.supporttexture = supporttexture;
    }

    public String getVideonormalized() {
        return videonormalized;
    }

    public void setVideonormalized(String videonormalized) {
        this.videonormalized = videonormalized;
    }

    public String getWheelcarbon() {
        return wheelcarbon;
    }

    public void setWheelcarbon(String wheelcarbon) {
        this.wheelcarbon = wheelcarbon;
    }

    public String getWheelsteel() {
        return wheelsteel;
    }

    public void setWheelsteel(String wheelsteel) {
        this.wheelsteel = wheelsteel;
    }

    public String getScreenmarqueesmall() {
        return screenmarqueesmall;
    }

    public void setScreenmarqueesmall(String screenmarqueesmall) {
        this.screenmarqueesmall = screenmarqueesmall;
    }

    public String getBoxside() {
        return boxside;
    }

    public void setBoxside(String boxside) {
        this.boxside = boxside;
    }

    public String getFigurine() {
        return figurine;
    }

    public void setFigurine(String figurine) {
        this.figurine = figurine;
    }

    public String getPictoliste() {
        return pictoliste;
    }

    public void setPictoliste(String pictoliste) {
        this.pictoliste = pictoliste;
    }

    public String getPictomonochrome() {
        return pictomonochrome;
    }

    public void setPictomonochrome(String pictomonochrome) {
        this.pictomonochrome = pictomonochrome;
    }

    public String getPictomonochromesvg() {
        return pictomonochromesvg;
    }

    public void setPictomonochromesvg(String pictomonochromesvg) {
        this.pictomonochromesvg = pictomonochromesvg;
    }

    public String getPictocouleur() {
        return pictocouleur;
    }

    public void setPictocouleur(String pictocouleur) {
        this.pictocouleur = pictocouleur;
    }

    public String getWallpaper() {
        return wallpaper;
    }

    public void setWallpaper(String wallpaper) {
        this.wallpaper = wallpaper;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public String getReleasedate() {
        return releasedate;
    }

    public void setReleasedate(String releasedate) {
        this.releasedate = releasedate;
    }

    public String getDeveloper() {
        return developer;
    }

    public void setDeveloper(String developer) {
        this.developer = developer;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getPlayers() {
        return players;
    }

    public void setPlayers(String players) {
        this.players = players;
    }

    public String getCrc32() {
        return crc32;
    }

    public void setCrc32(String crc32) {
        this.crc32 = crc32;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getGenreid() {
        return genreid;
    }

    public void setGenreid(String genreid) {
        this.genreid = genreid;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getPlatformType() {
        return platformType;
    }

    public void setPlatformType(String platformType) {
        this.platformType = platformType;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public Map<String, String> getPlatformSpecificFields() {
        return platformSpecificFields;
    }

    public void setPlatformSpecificFields(Map<String, String> platformSpecificFields) {
        this.platformSpecificFields = platformSpecificFields;
    }

    public Boolean getScraped() {
        return scraped;
    }

    public void setScraped(Boolean scraped) {
        this.scraped = scraped;
    }

    public Boolean getEdited() {
        return edited;
    }

    public void setEdited(Boolean edited) {
        this.edited = edited;
    }

    public Boolean getExists() {
        return exists;
    }

    public void setExists(Boolean exists) {
        this.exists = exists;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(String absolutePath) {
        this.absolutePath = absolutePath;
    }

    public String getPlatformPath() {
        return platformPath;
    }

    public void setPlatformPath(String platformPath) {
        this.platformPath = platformPath;
    }

    public Boolean getMultiFile() {
        return multiFile;
    }

    public void setMultiFile(Boolean multiFile) {
        this.multiFile = multiFile;
    }

    public String getMultiFileContent() {
        return multiFileContent;
    }

    public void setMultiFileContent(String multiFileContent) {
        this.multiFileContent = multiFileContent;
    }

    public Boolean getCached() {
        return cached;
    }

    public void setCached(Boolean cached) {
        this.cached = cached;
    }

    public String getParentRom() {
        return parentRom;
    }

    public void setParentRom(String parentRom) {
        this.parentRom = parentRom;
    }


    // ========== ScreenScraper 标准媒体字段 Getter/Setter ==========

    public String getBox2d() { return box2d; }
    public void setBox2d(String box2d) { this.box2d = box2d; }
    public String getBox2dBack() { return box2dBack; }
    public void setBox2dBack(String box2dBack) { this.box2dBack = box2dBack; }
    public String getBox2dSide() { return box2dSide; }
    public void setBox2dSide(String box2dSide) { this.box2dSide = box2dSide; }
    public String getBoxTexture() { return boxTexture; }
    public void setBoxTexture(String boxTexture) { this.boxTexture = boxTexture; }
    public String getBoxScan() { return boxScan; }
    public void setBoxScan(String boxScan) { this.boxScan = boxScan; }
    public String getSupport2d() { return support2d; }
    public void setSupport2d(String support2d) { this.support2d = support2d; }
    public String getSupportTexture() { return supportTexture; }
    public void setSupportTexture(String supportTexture) { this.supportTexture = supportTexture; }
    public String getSupportScan() { return supportScan; }
    public void setSupportScan(String supportScan) { this.supportScan = supportScan; }
    public String getWheelHd() { return wheelHd; }
    public void setWheelHd(String wheelHd) { this.wheelHd = wheelHd; }
    public String getWheelCarbon() { return wheelCarbon; }
    public void setWheelCarbon(String wheelCarbon) { this.wheelCarbon = wheelCarbon; }
    public String getWheelSteel() { return wheelSteel; }
    public void setWheelSteel(String wheelSteel) { this.wheelSteel = wheelSteel; }
    public String getScreenmarquee() { return screenmarquee; }
    public void setScreenmarquee(String screenmarquee) { this.screenmarquee = screenmarquee; }
    public String getSs() { return ss; }
    public void setSs(String ss) { this.ss = ss; }
    public String getSstitle() { return sstitle; }
    public void setSstitle(String sstitle) { this.sstitle = sstitle; }
    public String getOverlay() { return overlay; }
    public void setOverlay(String overlay) { this.overlay = overlay; }
    public String getVideoNormalized() { return videoNormalized; }
    public void setVideoNormalized(String videoNormalized) { this.videoNormalized = videoNormalized; }
    public String getFlyer() { return flyer; }
    public void setFlyer(String flyer) { this.flyer = flyer; }
    public String getManuel() { return manuel; }
    public void setManuel(String manuel) { this.manuel = manuel; }
    public String getMaps() { return maps; }
    public void setMaps(String maps) { this.maps = maps; }
    public String getBezel43() { return bezel43; }
    public void setBezel43(String bezel43) { this.bezel43 = bezel43; }
    public String getBezel43V() { return bezel43V; }
    public void setBezel43V(String bezel43V) { this.bezel43V = bezel43V; }
    public String getBezel43Cocktail() { return bezel43Cocktail; }
    public void setBezel43Cocktail(String bezel43Cocktail) { this.bezel43Cocktail = bezel43Cocktail; }
    public String getBezel169() { return bezel169; }
    public void setBezel169(String bezel169) { this.bezel169 = bezel169; }
    public String getBezel169V() { return bezel169V; }
    public void setBezel169V(String bezel169V) { this.bezel169V = bezel169V; }
    public String getBezel169Cocktail() { return bezel169Cocktail; }
    public void setBezel169Cocktail(String bezel169Cocktail) { this.bezel169Cocktail = bezel169Cocktail; }
    public String getMixrbv1() { return mixrbv1; }
    public void setMixrbv1(String mixrbv1) { this.mixrbv1 = mixrbv1; }
    public String getMixrbv2() { return mixrbv2; }
    public void setMixrbv2(String mixrbv2) { this.mixrbv2 = mixrbv2; }
    public String getThemehb() { return themehb; }
    public void setThemehb(String themehb) { this.themehb = themehb; }
    public String getThemehs() { return themehs; }
    public void setThemehs(String themehs) { this.themehs = themehs; }
    public String getSsdmd() { return ssdmd; }
    public void setSsdmd(String ssdmd) { this.ssdmd = ssdmd; }
    public String getSsfronton169() { return ssfronton169; }
    public void setSsfronton169(String ssfronton169) { this.ssfronton169 = ssfronton169; }
    public String getSsfronton11() { return ssfronton11; }
    public void setSsfronton11(String ssfronton11) { this.ssfronton11 = ssfronton11; }
    public String getSsfronton43() { return ssfronton43; }
    public void setSsfronton43(String ssfronton43) { this.ssfronton43 = ssfronton43; }
    public String getSstable() { return sstable; }
    public void setSstable(String sstable) { this.sstable = sstable; }
    public String getSstopper() { return sstopper; }
    public void setSstopper(String sstopper) { this.sstopper = sstopper; }
    public String getVideodmd() { return videodmd; }
    public void setVideodmd(String videodmd) { this.videodmd = videodmd; }
    public String getVideofronton43() { return videofronton43; }
    public void setVideofronton43(String videofronton43) { this.videofronton43 = videofronton43; }
    public String getVideofronton169() { return videofronton169; }
    public void setVideofronton169(String videofronton169) { this.videofronton169 = videofronton169; }
    public String getVideotable() { return videotable; }
    public void setVideotable(String videotable) { this.videotable = videotable; }
    public String getVideotable4k() { return videotable4k; }
    public void setVideotable4k(String videotable4k) { this.videotable4k = videotable4k; }
    public String getVideotopper() { return videotopper; }
    public void setVideotopper(String videotopper) { this.videotopper = videotopper; }
    public String getWheelTarcisios() { return wheelTarcisios; }
    public void setWheelTarcisios(String wheelTarcisios) { this.wheelTarcisios = wheelTarcisios; }
}
