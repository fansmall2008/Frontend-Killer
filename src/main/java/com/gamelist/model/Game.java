package com.gamelist.model;

import java.util.Map;

public class Game {
    private Long id;
    private String gameId;
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
    private String wheel;
    private String manual;
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
    private String box3d;
    private String steamgrid;
    private String fanart;
    private String boxtexture;
    private String supporttexture;
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

    public String getBox3d() {
        return box3d;
    }

    public void setBox3d(String box3d) {
        this.box3d = box3d;
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
}
