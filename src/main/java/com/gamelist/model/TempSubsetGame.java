package com.gamelist.model;

import java.util.Date;

public class TempSubsetGame {
    private Long id;
    private Long subsetId;
    private Long originalGameId;
    private String gameId;
    private Long ssGameId;
    private String source;
    private String path;
    private String name;
    private String desc;
    private String translatedName;
    private String translatedDesc;
    private String image;
    private String video;
    private String marquee;
    private String thumbnail;
    private String manual;
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
    private String platformPath;
    private String sortBy;
    // 状态字段
    private Boolean scraped;
    private Boolean edited;
    private Boolean exists;
    private String absolutePath;
    // ========== ScreenScraper 标准媒体字段 ==========
    // --- 包装盒类 ---
    private String box2d;
    private String box2dBack;
    private String box2dSide;
    private String box3D;
    private String boxTexture;
    private String boxScan;
    // --- 支撑类 ---
    private String support2d;
    private String supportTexture;
    private String supportScan;
    // --- 轮盘类 ---
    private String wheel;
    private String wheelHd;
    private String wheelCarbon;
    private String wheelSteel;
    // --- 霓虹灯类 ---
    private String screenmarquee;
    // --- 通用媒体类 ---
    private String ss;
    private String sstitle;
    private String steamgrid;
    private String fanart;
    private String overlay;
    private String videoNormalized;
    // --- 次要媒体类 ---
    private String flyer;
    private String manuel;
    private String maps;
    private String figurine;
    // --- 边框类 ---
    private String bezel43;
    private String bezel43V;
    private String bezel43Cocktail;
    private String bezel169;
    private String bezel169V;
    private String bezel169Cocktail;
    // --- 混合类 ---
    private String mixrbv1;
    private String mixrbv2;
    // --- 主题类 ---
    private String themehb;
    private String themehs;
    // --- 弹球台专用 ---
    private String ssdmd;
    private String ssfronton169;
    private String ssfronton11;
    private String ssfronton43;
    private String sstable;
    private String sstopper;
    private String videodmd;
    private String videofronton43;
    private String videofronton169;
    private String videotable;
    private String videotable4k;
    private String videotopper;
    private String wheelTarcisios;
    // ========== 非 ScreenScraper 遗留字段 ==========
    private String boxFront;
    private String boxBack;
    private String boxSpine;
    private String boxFull;
    private String cartridge;
    private String logo;
    private String bezel;
    private String panel;
    private String cabinetLeft;
    private String cabinetRight;
    private String tile;
    private String banner;
    private String steam;
    private String poster;
    private String background;
    private String music;
    private String screenshot;
    private String titlescreen;
    private String boxtexture;
    private String supporttexture;
    private String videonormalized;
    private String wheelcarbon;
    private String wheelsteel;
    private String screenmarqueesmall;
    private String boxside;
    private String pictoliste;
    private String pictomonochrome;
    private String pictomonochromesvg;
    private String pictocouleur;
    private String wallpaper;
    // 时间戳
    private Date createdAt;
    private Date updatedAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSubsetId() { return subsetId; }
    public void setSubsetId(Long subsetId) { this.subsetId = subsetId; }
    public Long getOriginalGameId() { return originalGameId; }
    public void setOriginalGameId(Long originalGameId) { this.originalGameId = originalGameId; }
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public Long getSsGameId() { return ssGameId; }
    public void setSsGameId(Long ssGameId) { this.ssGameId = ssGameId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public String getTranslatedName() { return translatedName; }
    public void setTranslatedName(String translatedName) { this.translatedName = translatedName; }
    public String getTranslatedDesc() { return translatedDesc; }
    public void setTranslatedDesc(String translatedDesc) { this.translatedDesc = translatedDesc; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public String getVideo() { return video; }
    public void setVideo(String video) { this.video = video; }
    public String getMarquee() { return marquee; }
    public void setMarquee(String marquee) { this.marquee = marquee; }
    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }
    public String getManual() { return manual; }
    public void setManual(String manual) { this.manual = manual; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public String getReleasedate() { return releasedate; }
    public void setReleasedate(String releasedate) { this.releasedate = releasedate; }
    public String getDeveloper() { return developer; }
    public void setDeveloper(String developer) { this.developer = developer; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getPlayers() { return players; }
    public void setPlayers(String players) { this.players = players; }
    public String getCrc32() { return crc32; }
    public void setCrc32(String crc32) { this.crc32 = crc32; }
    public String getMd5() { return md5; }
    public void setMd5(String md5) { this.md5 = md5; }
    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
    public String getGenreid() { return genreid; }
    public void setGenreid(String genreid) { this.genreid = genreid; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public String getPlatformType() { return platformType; }
    public void setPlatformType(String platformType) { this.platformType = platformType; }
    public Long getPlatformId() { return platformId; }
    public void setPlatformId(Long platformId) { this.platformId = platformId; }
    public String getPlatformPath() { return platformPath; }
    public void setPlatformPath(String platformPath) { this.platformPath = platformPath; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public Boolean getScraped() { return scraped; }
    public void setScraped(Boolean scraped) { this.scraped = scraped; }
    public Boolean getEdited() { return edited; }
    public void setEdited(Boolean edited) { this.edited = edited; }
    public Boolean getExists() { return exists; }
    public void setExists(Boolean exists) { this.exists = exists; }
    public String getAbsolutePath() { return absolutePath; }
    public void setAbsolutePath(String absolutePath) { this.absolutePath = absolutePath; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    // ========== SS 标准媒体字段 Getter/Setter ==========
    public String getBox2d() { return box2d; }
    public void setBox2d(String box2d) { this.box2d = box2d; }
    public String getBox2dBack() { return box2dBack; }
    public void setBox2dBack(String box2dBack) { this.box2dBack = box2dBack; }
    public String getBox2dSide() { return box2dSide; }
    public void setBox2dSide(String box2dSide) { this.box2dSide = box2dSide; }
    public String getBox3D() { return box3D; }
    public void setBox3D(String box3D) { this.box3D = box3D; }
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
    public String getWheel() { return wheel; }
    public void setWheel(String wheel) { this.wheel = wheel; }
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
    public String getSteamgrid() { return steamgrid; }
    public void setSteamgrid(String steamgrid) { this.steamgrid = steamgrid; }
    public String getFanart() { return fanart; }
    public void setFanart(String fanart) { this.fanart = fanart; }
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
    public String getFigurine() { return figurine; }
    public void setFigurine(String figurine) { this.figurine = figurine; }
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

    // ========== 遗留字段 Getter/Setter ==========
    public String getBoxFront() { return boxFront; }
    public void setBoxFront(String boxFront) { this.boxFront = boxFront; }
    public String getBoxBack() { return boxBack; }
    public void setBoxBack(String boxBack) { this.boxBack = boxBack; }
    public String getBoxSpine() { return boxSpine; }
    public void setBoxSpine(String boxSpine) { this.boxSpine = boxSpine; }
    public String getBoxFull() { return boxFull; }
    public void setBoxFull(String boxFull) { this.boxFull = boxFull; }
    public String getCartridge() { return cartridge; }
    public void setCartridge(String cartridge) { this.cartridge = cartridge; }
    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }
    public String getBezel() { return bezel; }
    public void setBezel(String bezel) { this.bezel = bezel; }
    public String getPanel() { return panel; }
    public void setPanel(String panel) { this.panel = panel; }
    public String getCabinetLeft() { return cabinetLeft; }
    public void setCabinetLeft(String cabinetLeft) { this.cabinetLeft = cabinetLeft; }
    public String getCabinetRight() { return cabinetRight; }
    public void setCabinetRight(String cabinetRight) { this.cabinetRight = cabinetRight; }
    public String getTile() { return tile; }
    public void setTile(String tile) { this.tile = tile; }
    public String getBanner() { return banner; }
    public void setBanner(String banner) { this.banner = banner; }
    public String getSteam() { return steam; }
    public void setSteam(String steam) { this.steam = steam; }
    public String getPoster() { return poster; }
    public void setPoster(String poster) { this.poster = poster; }
    public String getBackground() { return background; }
    public void setBackground(String background) { this.background = background; }
    public String getMusic() { return music; }
    public void setMusic(String music) { this.music = music; }
    public String getScreenshot() { return screenshot; }
    public void setScreenshot(String screenshot) { this.screenshot = screenshot; }
    public String getTitlescreen() { return titlescreen; }
    public void setTitlescreen(String titlescreen) { this.titlescreen = titlescreen; }
    public String getBoxtexture() { return boxtexture; }
    public void setBoxtexture(String boxtexture) { this.boxtexture = boxtexture; }
    public String getSupporttexture() { return supporttexture; }
    public void setSupporttexture(String supporttexture) { this.supporttexture = supporttexture; }
    public String getVideonormalized() { return videonormalized; }
    public void setVideonormalized(String videonormalized) { this.videonormalized = videonormalized; }
    public String getWheelcarbon() { return wheelcarbon; }
    public void setWheelcarbon(String wheelcarbon) { this.wheelcarbon = wheelcarbon; }
    public String getWheelsteel() { return wheelsteel; }
    public void setWheelsteel(String wheelsteel) { this.wheelsteel = wheelsteel; }
    public String getScreenmarqueesmall() { return screenmarqueesmall; }
    public void setScreenmarqueesmall(String screenmarqueesmall) { this.screenmarqueesmall = screenmarqueesmall; }
    public String getBoxside() { return boxside; }
    public void setBoxside(String boxside) { this.boxside = boxside; }
    public String getPictoliste() { return pictoliste; }
    public void setPictoliste(String pictoliste) { this.pictoliste = pictoliste; }
    public String getPictomonochrome() { return pictomonochrome; }
    public void setPictomonochrome(String pictomonochrome) { this.pictomonochrome = pictomonochrome; }
    public String getPictomonochromesvg() { return pictomonochromesvg; }
    public void setPictomonochromesvg(String pictomonochromesvg) { this.pictomonochromesvg = pictomonochromesvg; }
    public String getPictocouleur() { return pictocouleur; }
    public void setPictocouleur(String pictocouleur) { this.pictocouleur = pictocouleur; }
    public String getWallpaper() { return wallpaper; }
    public void setWallpaper(String wallpaper) { this.wallpaper = wallpaper; }
}
