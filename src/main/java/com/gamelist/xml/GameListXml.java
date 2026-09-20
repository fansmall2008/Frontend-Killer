package com.gamelist.xml;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "gameList")
public class GameListXml {
    private Provider provider;
    private List<GameXml> game;

    @XmlElement(name = "provider")
    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    @XmlElement(name = "game")
    public List<GameXml> getGame() {
        return game;
    }

    public void setGame(List<GameXml> game) {
        this.game = game;
    }

    public static class Provider {
        private String system;
        private String software;
        private String database;
        private String web;

        @XmlElement(name = "System")
        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system;
        }

        @XmlElement(name = "software")
        public String getSoftware() {
            return software;
        }

        public void setSoftware(String software) {
            this.software = software;
        }

        @XmlElement(name = "database")
        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        @XmlElement(name = "web")
        public String getWeb() {
            return web;
        }

        public void setWeb(String web) {
            this.web = web;
        }
    }

    public static class GameXml {
        private String id; // <game>标签的id属性
        private String source; // <game>标签的source属性
        private String path;
        private String name;
        private String desc;
        private String image;
        private String video;
        private String marquee;
        private String thumbnail;
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
        private String gameid; // <gameid>子标签
        private String idTag; // <id>子标签
        private Scrap scrap;

        @XmlAttribute(name = "id")
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @XmlAttribute(name = "source")
        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        @XmlElement(name = "idTag")
        public String getIdTag() {
            return idTag;
        }

        public void setIdTag(String idTag) {
            this.idTag = idTag;
        }

        @XmlElement(name = "gameid")
        public String getGameid() {
            return gameid;
        }

        public void setGameid(String gameid) {
            this.gameid = gameid;
        }

        @XmlElement(name = "path")
        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        @XmlElement(name = "name")
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @XmlElement(name = "desc")
        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }

        @XmlElement(name = "image")
        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        @XmlElement(name = "video")
        public String getVideo() {
            return video;
        }

        public void setVideo(String video) {
            this.video = video;
        }

        @XmlElement(name = "marquee")
        public String getMarquee() {
            return marquee;
        }

        public void setMarquee(String marquee) {
            this.marquee = marquee;
        }

        @XmlElement(name = "thumbnail")
        public String getThumbnail() {
            return thumbnail;
        }

        public void setThumbnail(String thumbnail) {
            this.thumbnail = thumbnail;
        }

        @XmlElement(name = "manual")
        public String getManual() {
            return manual;
        }

        public void setManual(String manual) {
            this.manual = manual;
        }

        @XmlElement(name = "boxFront")
        public String getBoxFront() {
            return boxFront;
        }

        public void setBoxFront(String boxFront) {
            this.boxFront = boxFront;
        }

        @XmlElement(name = "boxBack")
        public String getBoxBack() {
            return boxBack;
        }

        public void setBoxBack(String boxBack) {
            this.boxBack = boxBack;
        }

        @XmlElement(name = "boxSpine")
        public String getBoxSpine() {
            return boxSpine;
        }

        public void setBoxSpine(String boxSpine) {
            this.boxSpine = boxSpine;
        }

        @XmlElement(name = "boxFull")
        public String getBoxFull() {
            return boxFull;
        }

        public void setBoxFull(String boxFull) {
            this.boxFull = boxFull;
        }

        @XmlElement(name = "cartridge")
        public String getCartridge() {
            return cartridge;
        }

        public void setCartridge(String cartridge) {
            this.cartridge = cartridge;
        }

        @XmlElement(name = "logo")
        public String getLogo() {
            return logo;
        }

        public void setLogo(String logo) {
            this.logo = logo;
        }

        @XmlElement(name = "bezel")
        public String getBezel() {
            return bezel;
        }

        public void setBezel(String bezel) {
            this.bezel = bezel;
        }

        @XmlElement(name = "panel")
        public String getPanel() {
            return panel;
        }

        public void setPanel(String panel) {
            this.panel = panel;
        }

        @XmlElement(name = "cabinetLeft")
        public String getCabinetLeft() {
            return cabinetLeft;
        }

        public void setCabinetLeft(String cabinetLeft) {
            this.cabinetLeft = cabinetLeft;
        }

        @XmlElement(name = "cabinetRight")
        public String getCabinetRight() {
            return cabinetRight;
        }

        public void setCabinetRight(String cabinetRight) {
            this.cabinetRight = cabinetRight;
        }

        @XmlElement(name = "tile")
        public String getTile() {
            return tile;
        }

        public void setTile(String tile) {
            this.tile = tile;
        }

        @XmlElement(name = "banner")
        public String getBanner() {
            return banner;
        }

        public void setBanner(String banner) {
            this.banner = banner;
        }

        @XmlElement(name = "steam")
        public String getSteam() {
            return steam;
        }

        public void setSteam(String steam) {
            this.steam = steam;
        }

        @XmlElement(name = "poster")
        public String getPoster() {
            return poster;
        }

        public void setPoster(String poster) {
            this.poster = poster;
        }

        @XmlElement(name = "background")
        public String getBackground() {
            return background;
        }

        public void setBackground(String background) {
            this.background = background;
        }

        @XmlElement(name = "music")
        public String getMusic() {
            return music;
        }

        public void setMusic(String music) {
            this.music = music;
        }

        @XmlElement(name = "screenshot")
        public String getScreenshot() {
            return screenshot;
        }

        public void setScreenshot(String screenshot) {
            this.screenshot = screenshot;
        }

        @XmlElement(name = "titlescreen")
        public String getTitlescreen() {
            return titlescreen;
        }

        public void setTitlescreen(String titlescreen) {
            this.titlescreen = titlescreen;
        }

        @XmlElement(name = "rating")
        public Double getRating() {
            return rating;
        }

        public void setRating(Double rating) {
            this.rating = rating;
        }

        @XmlElement(name = "releasedate")
        public String getReleasedate() {
            return releasedate;
        }

        public void setReleasedate(String releasedate) {
            this.releasedate = releasedate;
        }

        @XmlElement(name = "developer")
        public String getDeveloper() {
            return developer;
        }

        public void setDeveloper(String developer) {
            this.developer = developer;
        }

        @XmlElement(name = "publisher")
        public String getPublisher() {
            return publisher;
        }

        public void setPublisher(String publisher) {
            this.publisher = publisher;
        }

        @XmlElement(name = "genre")
        public String getGenre() {
            return genre;
        }

        public void setGenre(String genre) {
            this.genre = genre;
        }

        @XmlElement(name = "players")
        public String getPlayers() {
            return players;
        }

        public void setPlayers(String players) {
            this.players = players;
        }

        @XmlElement(name = "crc32")
        public String getCrc32() {
            return crc32;
        }

        public void setCrc32(String crc32) {
            this.crc32 = crc32;
        }

        @XmlElement(name = "md5")
        public String getMd5() {
            return md5;
        }

        public void setMd5(String md5) {
            this.md5 = md5;
        }

        @XmlElement(name = "lang")
        public String getLang() {
            return lang;
        }

        public void setLang(String lang) {
            this.lang = lang;
        }

        @XmlElement(name = "genreid")
        public String getGenreid() {
            return genreid;
        }

        public void setGenreid(String genreid) {
            this.genreid = genreid;
        }

        @XmlElement(name = "hash")
        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        @XmlElement(name = "scrap")
        public Scrap getScrap() {
            return scrap;
        }

        public void setScrap(Scrap scrap) {
            this.scrap = scrap;
        }

        public static class Scrap {
            private String name;
            private String date;

            @XmlElement(name = "name")
            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            @XmlElement(name = "date")
            public String getDate() {
                return date;
            }

            public void setDate(String date) {
                this.date = date;
            }
        }
    }
}
