package com.gamelist.util;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaFileFinder {

    public static class MediaFiles {
        private String boxFront;
        private String video;
        private String logo;
        private String screenshot;
        private String boxBack;
        private String boxSpine;
        private String boxFull;
        private String cartridge;
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
        private String titlescreen;
        private String manual;
        private String box3d;
        private String steamgrid;
        private String fanart;
        private String boxtexture;
        private String supporttexture;

        public String getBoxFront() { return boxFront; }
        public void setBoxFront(String boxFront) { this.boxFront = boxFront; }
        public String getVideo() { return video; }
        public void setVideo(String video) { this.video = video; }
        public String getLogo() { return logo; }
        public void setLogo(String logo) { this.logo = logo; }
        public String getScreenshot() { return screenshot; }
        public void setScreenshot(String screenshot) { this.screenshot = screenshot; }
        public String getBoxBack() { return boxBack; }
        public void setBoxBack(String boxBack) { this.boxBack = boxBack; }
        public String getBoxSpine() { return boxSpine; }
        public void setBoxSpine(String boxSpine) { this.boxSpine = boxSpine; }
        public String getBoxFull() { return boxFull; }
        public void setBoxFull(String boxFull) { this.boxFull = boxFull; }
        public String getCartridge() { return cartridge; }
        public void setCartridge(String cartridge) { this.cartridge = cartridge; }
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
        public String getTitlescreen() { return titlescreen; }
        public void setTitlescreen(String titlescreen) { this.titlescreen = titlescreen; }
        public String getManual() { return manual; }
        public void setManual(String manual) { this.manual = manual; }
        public String getBox3d() { return box3d; }
        public void setBox3d(String box3d) { this.box3d = box3d; }
        public String getSteamgrid() { return steamgrid; }
        public void setSteamgrid(String steamgrid) { this.steamgrid = steamgrid; }
        public String getFanart() { return fanart; }
        public void setFanart(String fanart) { this.fanart = fanart; }
        public String getBoxtexture() { return boxtexture; }
        public void setBoxtexture(String boxtexture) { this.boxtexture = boxtexture; }
        public String getSupporttexture() { return supporttexture; }
        public void setSupporttexture(String supporttexture) { this.supporttexture = supporttexture; }
    }

    public static MediaFiles findMediaFiles(String romFilePath, String metadataDir, Map<String, String> metadataAssets) {
        MediaFiles result = new MediaFiles();

        if (romFilePath == null || romFilePath.isEmpty()) {
            return result;
        }

        File romFile = new File(romFilePath);
        String gameName = getGameNameWithoutExtension(romFile.getName());
        String baseDir = metadataDir != null ? metadataDir : (romFile.getParent() != null ? romFile.getParent() : "");

        Map<String, String> foundMedia = new HashMap<>();

        findBoxFrontMedia(baseDir, gameName, foundMedia);
        findVideoMedia(baseDir, gameName, foundMedia);
        findLogoMedia(baseDir, gameName, foundMedia);
        findScreenshotMedia(baseDir, gameName, foundMedia);
        findBoxBackMedia(baseDir, gameName, foundMedia);
        findBoxSpineMedia(baseDir, gameName, foundMedia);
        findBoxFullMedia(baseDir, gameName, foundMedia);
        findCartridgeMedia(baseDir, gameName, foundMedia);
        findBezelMedia(baseDir, gameName, foundMedia);
        findPanelMedia(baseDir, gameName, foundMedia);
        findCabinetLeftMedia(baseDir, gameName, foundMedia);
        findCabinetRightMedia(baseDir, gameName, foundMedia);
        findTileMedia(baseDir, gameName, foundMedia);
        findBannerMedia(baseDir, gameName, foundMedia);
        findSteamMedia(baseDir, gameName, foundMedia);
        findPosterMedia(baseDir, gameName, foundMedia);
        findBackgroundMedia(baseDir, gameName, foundMedia);
        findMusicMedia(baseDir, gameName, foundMedia);
        findTitlescreenMedia(baseDir, gameName, foundMedia);
        findManualMedia(baseDir, gameName, foundMedia);
        findBox3dMedia(baseDir, gameName, foundMedia);
        findSteamgridMedia(baseDir, gameName, foundMedia);
        findFanartMedia(baseDir, gameName, foundMedia);
        findBoxtextureMedia(baseDir, gameName, foundMedia);
        findSupporttextureMedia(baseDir, gameName, foundMedia);

        if (metadataAssets != null) {
            validateMetadataAssets(metadataAssets, baseDir, foundMedia);
        }

        result.setBoxFront(foundMedia.get("boxFront"));
        result.setVideo(foundMedia.get("video"));
        result.setLogo(foundMedia.get("logo"));
        result.setScreenshot(foundMedia.get("screenshot"));
        result.setBoxBack(foundMedia.get("boxBack"));
        result.setBoxSpine(foundMedia.get("boxSpine"));
        result.setBoxFull(foundMedia.get("boxFull"));
        result.setCartridge(foundMedia.get("cartridge"));
        result.setBezel(foundMedia.get("bezel"));
        result.setPanel(foundMedia.get("panel"));
        result.setCabinetLeft(foundMedia.get("cabinetLeft"));
        result.setCabinetRight(foundMedia.get("cabinetRight"));
        result.setTile(foundMedia.get("tile"));
        result.setBanner(foundMedia.get("banner"));
        result.setSteam(foundMedia.get("steam"));
        result.setPoster(foundMedia.get("poster"));
        result.setBackground(foundMedia.get("background"));
        result.setMusic(foundMedia.get("music"));
        result.setTitlescreen(foundMedia.get("titlescreen"));
        result.setManual(foundMedia.get("manual"));
        result.setBox3d(foundMedia.get("box3d"));
        result.setSteamgrid(foundMedia.get("steamgrid"));
        result.setFanart(foundMedia.get("fanart"));
        result.setBoxtexture(foundMedia.get("boxtexture"));
        result.setSupporttexture(foundMedia.get("supporttexture"));

        return result;
    }

    private static String getGameNameWithoutExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    private static void findBoxFrontMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/boxFront.png",
            baseDir + "/media/" + gameName + "/boxFront.jpg",
            baseDir + "/media/" + gameName + "/box_front.png",
            baseDir + "/media/" + gameName + "/box_front.jpg",
            baseDir + "/media/" + gameName + "/box.png",
            baseDir + "/media/" + gameName + "/box.jpg",
            baseDir + "/media/" + gameName + "/front.png",
            baseDir + "/media/" + gameName + "/front.jpg",
            baseDir + "/media/" + gameName + ".png",
            baseDir + "/media/" + gameName + ".jpg",
            baseDir + "/media/images/" + gameName + ".png",
            baseDir + "/media/images/" + gameName + ".jpg",
            baseDir + "/media/images/" + gameName + "/boxFront.png",
            baseDir + "/media/images/" + gameName + "/boxFront.jpg",
            baseDir + "/media/boxFront/" + gameName + ".png",
            baseDir + "/media/boxFront/" + gameName + ".jpg",
            baseDir + "/media/box_front/" + gameName + ".png",
            baseDir + "/media/box_front/" + gameName + ".jpg",
            baseDir + "/media/box2dfront/" + gameName + ".png",
            baseDir + "/media/box2dfront/" + gameName + ".jpg",
            baseDir + "/media/box2d/" + gameName + ".png",
            baseDir + "/media/box2d/" + gameName + ".jpg",
            baseDir + "/media/" + gameName + "-boxFront.png",
            baseDir + "/media/" + gameName + "-boxFront.jpg",
            baseDir + "/media/" + gameName + "-box_front.png",
            baseDir + "/media/" + gameName + "-box_front.jpg",
            baseDir + "/media/" + gameName + "-box2dfront.png",
            baseDir + "/media/" + gameName + "-box2dfront.jpg",
            baseDir + "/media/" + gameName + "-box2d.png",
            baseDir + "/media/" + gameName + "-box2d.jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("boxFront", relativePath);
                System.out.println("[MediaFileFinder] findBoxFrontMedia FOUND: " + relativePath);
                return;
            }
        }
    }

    private static void findVideoMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/video.mp4",
            baseDir + "/media/" + gameName + "/video.mkv",
            baseDir + "/media/" + gameName + "/video.avi",
            baseDir + "/media/" + gameName + "/gameplay.mp4",
            baseDir + "/media/" + gameName + "/gameplay.mkv",
            baseDir + "/media/" + gameName + ".mp4",
            baseDir + "/media/" + gameName + ".mkv",
            baseDir + "/media/videos/" + gameName + ".mp4",
            baseDir + "/media/videos/" + gameName + "/video.mp4",
            baseDir + "/media/video/" + gameName + ".mp4",
            baseDir + "/media/video/" + gameName + "/video.mp4"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("video", relativePath);
                return;
            }
        }
    }

    private static void findLogoMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/logo.png",
            baseDir + "/media/" + gameName + "/marquee.png",
            baseDir + "/media/" + gameName + "/wheel.png",
            baseDir + "/media/" + gameName + "-logo.png",
            baseDir + "/media/" + gameName + "-marquee.png",
            baseDir + "/media/" + gameName + "-wheel.png",
            baseDir + "/media/" + gameName + "/logo.jpg",
            baseDir + "/media/" + gameName + "/marquee.jpg",
            baseDir + "/media/" + gameName + "/wheel.jpg",
            baseDir + "/media/marquees/" + gameName + ".png",
            baseDir + "/media/marquees/" + gameName + "/logo.png",
            baseDir + "/media/logos/" + gameName + ".png",
            baseDir + "/media/screenmarquee/" + gameName + ".png",
            baseDir + "/media/screenmarquee/" + gameName + ".jpg",
            baseDir + "/media/screenmarqueesmall/" + gameName + ".png",
            baseDir + "/media/screenmarqueesmall/" + gameName + ".jpg",
            baseDir + "/media/wheel/" + gameName + ".png",
            baseDir + "/media/wheel/" + gameName + ".jpg",
            baseDir + "/media/wheelcarbon/" + gameName + ".png",
            baseDir + "/media/wheelcarbon/" + gameName + ".jpg",
            baseDir + "/media/wheelssteel/" + gameName + ".png",
            baseDir + "/media/wheelssteel/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("logo", relativePath);
                return;
            }
        }
    }

    private static void findScreenshotMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/screenshot.png",
            baseDir + "/media/" + gameName + "/screenshot.jpg",
            baseDir + "/media/" + gameName + "/screen.png",
            baseDir + "/media/" + gameName + "/screen.jpg",
            baseDir + "/media/screenshots/" + gameName + ".png",
            baseDir + "/media/screenshots/" + gameName + "/screenshot.png",
            baseDir + "/media/thumbnails/" + gameName + ".png",
            baseDir + "/media/thumbnails/" + gameName + "/screenshot.png",
            baseDir + "/media/screenshot/" + gameName + ".png",
            baseDir + "/media/screenshot/" + gameName + ".jpg",
            baseDir + "/media/screenshotitle/" + gameName + ".png",
            baseDir + "/media/screenshotitle/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("screenshot", relativePath);
                return;
            }
        }
    }

    private static void findBoxBackMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/boxBack.png",
            baseDir + "/media/" + gameName + "/boxBack.jpg",
            baseDir + "/media/" + gameName + "/box_back.png",
            baseDir + "/media/" + gameName + "/box_back.jpg",
            baseDir + "/media/" + gameName + "/back.png",
            baseDir + "/media/" + gameName + "/back.jpg",
            baseDir + "/media/" + gameName + "/box.png",
            baseDir + "/media/" + gameName + "/box.jpg",
            baseDir + "/media/boxBack/" + gameName + ".png",
            baseDir + "/media/boxBack/" + gameName + ".jpg",
            baseDir + "/media/box_back/" + gameName + ".png",
            baseDir + "/media/box_back/" + gameName + ".jpg",
            baseDir + "/media/box2dback/" + gameName + ".png",
            baseDir + "/media/box2dback/" + gameName + ".jpg",
            baseDir + "/media/" + gameName + "-boxBack.png",
            baseDir + "/media/" + gameName + "-boxBack.jpg",
            baseDir + "/media/" + gameName + "-box_back.png",
            baseDir + "/media/" + gameName + "-box_back.jpg",
            baseDir + "/media/" + gameName + "-box2dback.png",
            baseDir + "/media/" + gameName + "-box2dback.jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("boxBack", relativePath);
                return;
            }
        }
    }

    private static void findBoxSpineMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/boxSpine.png",
            baseDir + "/media/" + gameName + "/boxSpine.jpg",
            baseDir + "/media/" + gameName + "/box_spine.png",
            baseDir + "/media/" + gameName + "/box_spine.jpg",
            baseDir + "/media/" + gameName + "/spine.png",
            baseDir + "/media/" + gameName + "/spine.jpg",
            baseDir + "/media/" + gameName + "/side.png",
            baseDir + "/media/" + gameName + "/side.jpg",
            baseDir + "/media/boxSpine/" + gameName + ".png",
            baseDir + "/media/boxSpine/" + gameName + ".jpg",
            baseDir + "/media/box_spine/" + gameName + ".png",
            baseDir + "/media/box_spine/" + gameName + ".jpg",
            baseDir + "/media/box2dside/" + gameName + ".png",
            baseDir + "/media/box2dside/" + gameName + ".jpg",
            baseDir + "/media/" + gameName + "-boxSpine.png",
            baseDir + "/media/" + gameName + "-boxSpine.jpg",
            baseDir + "/media/" + gameName + "-box_spine.png",
            baseDir + "/media/" + gameName + "-box_spine.jpg",
            baseDir + "/media/" + gameName + "-box2dside.png",
            baseDir + "/media/" + gameName + "-box2dside.jpg",
            baseDir + "/media/" + gameName + "-side.png",
            baseDir + "/media/" + gameName + "-side.jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("boxSpine", relativePath);
                return;
            }
        }
    }

    private static void findBoxFullMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/boxFull.png",
            baseDir + "/media/" + gameName + "/boxFull.jpg",
            baseDir + "/media/" + gameName + "/box_full.png",
            baseDir + "/media/" + gameName + "/box_full.jpg",
            baseDir + "/media/" + gameName + "/full.png",
            baseDir + "/media/" + gameName + "/full.jpg",
            baseDir + "/media/boxFull/" + gameName + ".png",
            baseDir + "/media/boxFull/" + gameName + ".jpg",
            baseDir + "/media/box_full/" + gameName + ".png",
            baseDir + "/media/box_full/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("boxFull", relativePath);
                return;
            }
        }
    }

    private static void findCartridgeMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/cartridge.png",
            baseDir + "/media/" + gameName + "/cartridge.jpg",
            baseDir + "/media/" + gameName + "/cart.png",
            baseDir + "/media/" + gameName + "/cart.jpg",
            baseDir + "/media/" + gameName + "/disc.png",
            baseDir + "/media/" + gameName + "/disc.jpg",
            baseDir + "/media/cartridge/" + gameName + ".png",
            baseDir + "/media/cartridge/" + gameName + ".jpg",
            baseDir + "/media/cart/" + gameName + ".png",
            baseDir + "/media/cart/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("cartridge", relativePath);
                return;
            }
        }
    }

    private static void findBezelMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/bezel.png",
            baseDir + "/media/" + gameName + "/bezel.jpg",
            baseDir + "/media/" + gameName + "/frame.png",
            baseDir + "/media/" + gameName + "/frame.jpg",
            baseDir + "/media/bezel/" + gameName + ".png",
            baseDir + "/media/bezel/" + gameName + ".jpg",
            baseDir + "/media/frame/" + gameName + ".png",
            baseDir + "/media/frame/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("bezel", relativePath);
                return;
            }
        }
    }

    private static void findPanelMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/panel.png",
            baseDir + "/media/" + gameName + "/panel.jpg",
            baseDir + "/media/" + gameName + "/control.png",
            baseDir + "/media/" + gameName + "/control.jpg",
            baseDir + "/media/panel/" + gameName + ".png",
            baseDir + "/media/panel/" + gameName + ".jpg",
            baseDir + "/media/control/" + gameName + ".png",
            baseDir + "/media/control/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("panel", relativePath);
                return;
            }
        }
    }

    private static void findCabinetLeftMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/cabinetLeft.png",
            baseDir + "/media/" + gameName + "/cabinetLeft.jpg",
            baseDir + "/media/" + gameName + "/cabinet_left.png",
            baseDir + "/media/" + gameName + "/cabinet_left.jpg",
            baseDir + "/media/" + gameName + "/left.png",
            baseDir + "/media/" + gameName + "/left.jpg",
            baseDir + "/media/cabinetLeft/" + gameName + ".png",
            baseDir + "/media/cabinetLeft/" + gameName + ".jpg",
            baseDir + "/media/cabinet_left/" + gameName + ".png",
            baseDir + "/media/cabinet_left/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("cabinetLeft", relativePath);
                return;
            }
        }
    }

    private static void findCabinetRightMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/cabinetRight.png",
            baseDir + "/media/" + gameName + "/cabinetRight.jpg",
            baseDir + "/media/" + gameName + "/cabinet_right.png",
            baseDir + "/media/" + gameName + "/cabinet_right.jpg",
            baseDir + "/media/" + gameName + "/right.png",
            baseDir + "/media/" + gameName + "/right.jpg",
            baseDir + "/media/cabinetRight/" + gameName + ".png",
            baseDir + "/media/cabinetRight/" + gameName + ".jpg",
            baseDir + "/media/cabinet_right/" + gameName + ".png",
            baseDir + "/media/cabinet_right/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("cabinetRight", relativePath);
                return;
            }
        }
    }

    private static void findTileMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/tile.png",
            baseDir + "/media/" + gameName + "/tile.jpg",
            baseDir + "/media/tile/" + gameName + ".png",
            baseDir + "/media/tile/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("tile", relativePath);
                return;
            }
        }
    }

    private static void findBannerMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/banner.png",
            baseDir + "/media/" + gameName + "/banner.jpg",
            baseDir + "/media/banner/" + gameName + ".png",
            baseDir + "/media/banner/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("banner", relativePath);
                return;
            }
        }
    }

    private static void findSteamMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/steam.png",
            baseDir + "/media/" + gameName + "/steam.jpg",
            baseDir + "/media/" + gameName + "/grid.png",
            baseDir + "/media/" + gameName + "/grid.jpg",
            baseDir + "/media/steam/" + gameName + ".png",
            baseDir + "/media/steam/" + gameName + ".jpg",
            baseDir + "/media/grid/" + gameName + ".png",
            baseDir + "/media/grid/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("steam", relativePath);
                return;
            }
        }
    }

    private static void findPosterMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/poster.png",
            baseDir + "/media/" + gameName + "/poster.jpg",
            baseDir + "/media/poster/" + gameName + ".png",
            baseDir + "/media/poster/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("poster", relativePath);
                return;
            }
        }
    }

    private static void findBackgroundMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/background.png",
            baseDir + "/media/" + gameName + "/background.jpg",
            baseDir + "/media/" + gameName + "/bg.png",
            baseDir + "/media/" + gameName + "/bg.jpg",
            baseDir + "/media/background/" + gameName + ".png",
            baseDir + "/media/background/" + gameName + ".jpg",
            baseDir + "/media/bg/" + gameName + ".png",
            baseDir + "/media/bg/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("background", relativePath);
                return;
            }
        }
    }

    private static void findMusicMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/music.mp3",
            baseDir + "/media/" + gameName + "/music.ogg",
            baseDir + "/media/" + gameName + "/music.flac",
            baseDir + "/media/music/" + gameName + ".mp3",
            baseDir + "/media/music/" + gameName + ".ogg",
            baseDir + "/media/music/" + gameName + ".flac"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("music", relativePath);
                return;
            }
        }
    }

    private static void findTitlescreenMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/titlescreen.png",
            baseDir + "/media/" + gameName + "/titlescreen.jpg",
            baseDir + "/media/" + gameName + "/title.png",
            baseDir + "/media/" + gameName + "/title.jpg",
            baseDir + "/media/titlescreen/" + gameName + ".png",
            baseDir + "/media/titlescreen/" + gameName + ".jpg",
            baseDir + "/media/title/" + gameName + ".png",
            baseDir + "/media/title/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("titlescreen", relativePath);
                return;
            }
        }
    }

    private static void findManualMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/manual.pdf",
            baseDir + "/media/manuals/" + gameName + ".pdf",
            baseDir + "/media/support/" + gameName + ".pdf",
            baseDir + "/media/" + gameName + "-manual.pdf",
            baseDir + "/media/" + gameName + "-support.pdf"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("manual", relativePath);
                return;
            }
        }
    }

    private static void findBox3dMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/box3d.png",
            baseDir + "/media/" + gameName + "/box3d.jpg",
            baseDir + "/media/box3d/" + gameName + ".png",
            baseDir + "/media/box3d/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("box3d", relativePath);
                return;
            }
        }
    }

    private static void findSteamgridMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/steamgrid.png",
            baseDir + "/media/" + gameName + "/steamgrid.jpg",
            baseDir + "/media/steamgrid/" + gameName + ".png",
            baseDir + "/media/steamgrid/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("steamgrid", relativePath);
                return;
            }
        }
    }

    private static void findFanartMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/fanart.png",
            baseDir + "/media/" + gameName + "/fanart.jpg",
            baseDir + "/media/fanart/" + gameName + ".png",
            baseDir + "/media/fanart/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("fanart", relativePath);
                return;
            }
        }
    }

    private static void findBoxtextureMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/boxtexture.png",
            baseDir + "/media/" + gameName + "/boxtexture.jpg",
            baseDir + "/media/boxtexture/" + gameName + ".png",
            baseDir + "/media/boxtexture/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("boxtexture", relativePath);
                return;
            }
        }
    }

    private static void findSupporttextureMedia(String baseDir, String gameName, Map<String, String> foundMedia) {
        String[] patterns = {
            baseDir + "/media/" + gameName + "/supporttexture.png",
            baseDir + "/media/" + gameName + "/supporttexture.jpg",
            baseDir + "/media/supporttexture/" + gameName + ".png",
            baseDir + "/media/supporttexture/" + gameName + ".jpg"
        };

        for (String path : patterns) {
            if (fileExists(path)) {
                String relativePath = getRelativePath(path, baseDir);
                foundMedia.put("supporttexture", relativePath);
                return;
            }
        }
    }

    private static void validateMetadataAssets(Map<String, String> metadataAssets, String baseDir, Map<String, String> foundMedia) {
        for (Map.Entry<String, String> entry : metadataAssets.entrySet()) {
            String assetType = entry.getKey();
            String assetPath = entry.getValue();

            if (assetPath == null || assetPath.isEmpty()) {
                continue;
            }

            // 统一相对路径格式，确保以 ./ 开头
            if (!assetPath.startsWith("./") && !assetPath.startsWith("/") && !assetPath.matches("^[A-Za-z]:.*")) {
                assetPath = "./" + assetPath;
            }

            String fullPath = resolvePath(assetPath, baseDir);
            if (fileExists(fullPath)) {
                switch (assetType) {
                    case "boxFront":
                    case "box_front":
                        if (foundMedia.get("boxFront") == null) {
                            foundMedia.put("boxFront", assetPath); // 保存相对路径
                        }
                        break;
                    case "video":
                        if (foundMedia.get("video") == null) {
                            foundMedia.put("video", assetPath); // 保存相对路径
                        }
                        break;
                    case "logo":
                        if (foundMedia.get("logo") == null) {
                            foundMedia.put("logo", assetPath); // 保存相对路径
                        }
                        break;
                    case "screenshot":
                        if (foundMedia.get("screenshot") == null) {
                            foundMedia.put("screenshot", assetPath); // 保存相对路径
                        }
                        break;
                    case "boxBack":
                    case "box_back":
                        if (foundMedia.get("boxBack") == null) {
                            foundMedia.put("boxBack", assetPath); // 保存相对路径
                        }
                        break;
                    case "boxSpine":
                    case "box_spine":
                        if (foundMedia.get("boxSpine") == null) {
                            foundMedia.put("boxSpine", assetPath); // 保存相对路径
                        }
                        break;
                    case "boxFull":
                    case "box_full":
                        if (foundMedia.get("boxFull") == null) {
                            foundMedia.put("boxFull", assetPath); // 保存相对路径
                        }
                        break;
                    case "cartridge":
                        if (foundMedia.get("cartridge") == null) {
                            foundMedia.put("cartridge", assetPath); // 保存相对路径
                        }
                        break;
                    case "bezel":
                        if (foundMedia.get("bezel") == null) {
                            foundMedia.put("bezel", assetPath); // 保存相对路径
                        }
                        break;
                    case "panel":
                        if (foundMedia.get("panel") == null) {
                            foundMedia.put("panel", assetPath); // 保存相对路径
                        }
                        break;
                    case "cabinetLeft":
                    case "cabinet_left":
                        if (foundMedia.get("cabinetLeft") == null) {
                            foundMedia.put("cabinetLeft", assetPath); // 保存相对路径
                        }
                        break;
                    case "cabinetRight":
                    case "cabinet_right":
                        if (foundMedia.get("cabinetRight") == null) {
                            foundMedia.put("cabinetRight", assetPath); // 保存相对路径
                        }
                        break;
                    case "tile":
                        if (foundMedia.get("tile") == null) {
                            foundMedia.put("tile", assetPath); // 保存相对路径
                        }
                        break;
                    case "banner":
                        if (foundMedia.get("banner") == null) {
                            foundMedia.put("banner", assetPath); // 保存相对路径
                        }
                        break;
                    case "steam":
                        if (foundMedia.get("steam") == null) {
                            foundMedia.put("steam", assetPath); // 保存相对路径
                        }
                        break;
                    case "poster":
                        if (foundMedia.get("poster") == null) {
                            foundMedia.put("poster", assetPath); // 保存相对路径
                        }
                        break;
                    case "background":
                        if (foundMedia.get("background") == null) {
                            foundMedia.put("background", assetPath); // 保存相对路径
                        }
                        break;
                    case "music":
                        if (foundMedia.get("music") == null) {
                            foundMedia.put("music", assetPath); // 保存相对路径
                        }
                        break;
                    case "titlescreen":
                    case "title":
                        if (foundMedia.get("titlescreen") == null) {
                            foundMedia.put("titlescreen", assetPath); // 保存相对路径
                        }
                        break;
                }
            }
        }
    }

    private static String resolvePath(String assetPath, String baseDir) {
        if (assetPath == null) return baseDir;
        if (assetPath.startsWith("/") || assetPath.matches("^[A-Za-z]:.*")) {
            return assetPath;
        }
        // 移除 ./ 前缀后拼接
        String relativePath = assetPath.startsWith("./") ? assetPath.substring(2) : assetPath;
        return baseDir + "/" + relativePath;
    }

    private static String getRelativePath(String fullPath, String baseDir) {
        if (fullPath == null || baseDir == null) return fullPath;
        if (fullPath.startsWith(baseDir)) {
            String relativePath = fullPath.substring(baseDir.length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            return "./" + relativePath;
        }
        return fullPath;
    }

    private static boolean fileExists(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return new File(path).exists();
    }

    /**
     * 基于模板查找媒体文件
     */
    public static MediaFiles findMediaFiles(String romFilePath, String metadataDir, Object template) {
        MediaFiles result = new MediaFiles();

        if (romFilePath == null || romFilePath.isEmpty()) {
            return result;
        }

        File romFile = new File(romFilePath);
        String gameName = getGameNameWithoutExtension(romFile.getName());
        String baseDir = metadataDir != null ? metadataDir : (romFile.getParent() != null ? romFile.getParent() : "");

        Map<String, String> foundMedia = new HashMap<>();

        if (template != null && template instanceof com.gamelist.service.impl.GameServiceImpl.ImportTemplate) {
            com.gamelist.service.impl.GameServiceImpl.ImportTemplate importTemplate = 
                (com.gamelist.service.impl.GameServiceImpl.ImportTemplate) template;
            
            // 使用模板规则查找媒体文件
            findMediaByTemplate(baseDir, gameName, importTemplate, foundMedia);
        } else {
            // 使用默认规则查找媒体文件
            findBoxFrontMedia(baseDir, gameName, foundMedia);
            findVideoMedia(baseDir, gameName, foundMedia);
            findLogoMedia(baseDir, gameName, foundMedia);
            findScreenshotMedia(baseDir, gameName, foundMedia);
            findBoxBackMedia(baseDir, gameName, foundMedia);
            findBoxSpineMedia(baseDir, gameName, foundMedia);
            findBoxFullMedia(baseDir, gameName, foundMedia);
            findCartridgeMedia(baseDir, gameName, foundMedia);
            findBezelMedia(baseDir, gameName, foundMedia);
            findPanelMedia(baseDir, gameName, foundMedia);
            findCabinetLeftMedia(baseDir, gameName, foundMedia);
            findCabinetRightMedia(baseDir, gameName, foundMedia);
            findTileMedia(baseDir, gameName, foundMedia);
            findBannerMedia(baseDir, gameName, foundMedia);
            findSteamMedia(baseDir, gameName, foundMedia);
            findPosterMedia(baseDir, gameName, foundMedia);
            findBackgroundMedia(baseDir, gameName, foundMedia);
            findMusicMedia(baseDir, gameName, foundMedia);
            findTitlescreenMedia(baseDir, gameName, foundMedia);
            findManualMedia(baseDir, gameName, foundMedia);
            findBox3dMedia(baseDir, gameName, foundMedia);
            findSteamgridMedia(baseDir, gameName, foundMedia);
            findFanartMedia(baseDir, gameName, foundMedia);
            findBoxtextureMedia(baseDir, gameName, foundMedia);
            findSupporttextureMedia(baseDir, gameName, foundMedia);
        }

        result.setBoxFront(foundMedia.get("boxFront"));
        result.setVideo(foundMedia.get("video"));
        result.setLogo(foundMedia.get("logo"));
        result.setScreenshot(foundMedia.get("screenshot"));
        result.setBoxBack(foundMedia.get("boxBack"));
        result.setBoxSpine(foundMedia.get("boxSpine"));
        result.setBoxFull(foundMedia.get("boxFull"));
        result.setCartridge(foundMedia.get("cartridge"));
        result.setBezel(foundMedia.get("bezel"));
        result.setPanel(foundMedia.get("panel"));
        result.setCabinetLeft(foundMedia.get("cabinetLeft"));
        result.setCabinetRight(foundMedia.get("cabinetRight"));
        result.setTile(foundMedia.get("tile"));
        result.setBanner(foundMedia.get("banner"));
        result.setSteam(foundMedia.get("steam"));
        result.setPoster(foundMedia.get("poster"));
        result.setBackground(foundMedia.get("background"));
        result.setMusic(foundMedia.get("music"));
        result.setTitlescreen(foundMedia.get("titlescreen"));
        result.setManual(foundMedia.get("manual"));
        result.setBox3d(foundMedia.get("box3d"));
        result.setSteamgrid(foundMedia.get("steamgrid"));
        result.setFanart(foundMedia.get("fanart"));
        result.setBoxtexture(foundMedia.get("boxtexture"));
        result.setSupporttexture(foundMedia.get("supporttexture"));

        return result;
    }

    /**
     * 根据模板规则查找媒体文件
     */
    private static void findMediaByTemplate(String baseDir, String gameName, 
                                           com.gamelist.service.impl.GameServiceImpl.ImportTemplate template, 
                                           Map<String, String> foundMedia) {
        if (template == null || template.getMediaRules() == null) {
            return;
        }

        // 获取媒体规则
        Map<String, List<String>> mediaRules = template.getMediaRules();
        // 获取扩展名配置
        Map<String, List<String>> extensions = template.getExtensions();

        // 处理每种媒体类型
        processMediaRule(baseDir, gameName, mediaRules, extensions, "boxFront", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "video", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "logo", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "screenshot", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "boxBack", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "boxSpine", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "boxFull", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "cartridge", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "bezel", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "panel", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "cabinetLeft", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "cabinetRight", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "tile", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "banner", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "steam", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "poster", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "background", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "music", foundMedia);
        processMediaRule(baseDir, gameName, mediaRules, extensions, "titlescreen", foundMedia);
    }

    /**
     * 处理单个媒体规则
     */
    private static void processMediaRule(String baseDir, String gameName, 
                                        Map<String, List<String>> mediaRules, 
                                        Map<String, List<String>> extensions, 
                                        String mediaType, 
                                        Map<String, String> foundMedia) {
        // 获取该媒体类型的规则
        List<String> rules = mediaRules.get(mediaType);
        if (rules == null || rules.isEmpty()) {
            return;
        }

        // 获取该媒体类型的扩展名
        List<String> exts = getMediaExtensions(extensions, mediaType);

        // 尝试每个规则
        for (String rule : rules) {
            // 替换变量
            String processedRule = rule.replace("{gameName}", gameName);

            // 尝试每个扩展名
            for (String ext : exts) {
                String path = processedRule.replace("{ext}", ext);
                String fullPath = baseDir + "/" + path;

                if (fileExists(fullPath)) {
                    String relativePath = getRelativePath(fullPath, baseDir);
                    foundMedia.put(mediaType, relativePath);
                    return;
                }
            }
        }
    }

    /**
     * 获取媒体类型的扩展名
     */
    private static List<String> getMediaExtensions(Map<String, List<String>> extensions, String mediaType) {
        if (extensions == null) {
            return getDefaultExtensions(mediaType);
        }

        // 根据媒体类型获取扩展名
        switch (mediaType) {
            case "video":
            case "music":
                return extensions.getOrDefault("video", getDefaultExtensions(mediaType));
            default:
                return extensions.getOrDefault("image", getDefaultExtensions(mediaType));
        }
    }

    /**
     * 获取默认扩展名
     */
    private static List<String> getDefaultExtensions(String mediaType) {
        switch (mediaType) {
            case "video":
                return java.util.Arrays.asList("mp4", "mkv", "avi");
            case "music":
                return java.util.Arrays.asList("mp3", "ogg", "flac");
            default:
                return java.util.Arrays.asList("png", "jpg", "jpeg");
        }
    }

    /**
     * 自动媒体文件查找方法
     * 
     * 此方法用于在数据文件中的媒体类型缺失时，自动扫描目录查找可能的媒体文件。
     * 
     * 搜索策略：
     * 1. 优先查找数据文件中已有的媒体类型
     * 2. 对于缺失的类型，按照以下优先级查找：
     *    - Pegasus风格：./media/游戏名/类型.扩展名
     *    - ES-DE风格：./media/类型/游戏名.扩展名
     *    - RetroBat风格：./images/游戏名-类型.扩展名, ./videos/游戏名-视频.扩展名, ./manuals/游戏名-手册.pdf
     * 
     * 支持的媒体类型：
     * boxFront, boxBack, boxSpine, boxFull, cartridge, logo, bezel, panel, 
     * cabinetLeft, cabinetRight, tile, banner, steam, poster, background, 
     * music, screenshot, titlescreen, video, manual
     * 
     * 支持的扩展名：
     * - 图片：.png, .jpg, .jpeg, .gif, .webp
     * - 视频：.mp4, .avi, .mkv
     * - 音乐：.mp3, .ogg, .wav
     * - 手册：.pdf
     * 
     * @param baseDir 基础目录
     * @param gameName 游戏名称
     * @param gamePath 游戏文件路径
     * @param metadataAssets 从数据文件中已解析的媒体类型
     * @return 包含所有媒体类型的MediaFiles对象
     */
    public static MediaFiles autoFindMediaFiles(String baseDir, String gameName, String gamePath, 
                                                Map<String, String> metadataAssets) {
        MediaFiles mediaFiles = new MediaFiles();
        
        // 支持的媒体类型
        String[] mediaTypes = {
            "boxFront", "boxBack", "boxSpine", "boxFull", "cartridge", 
            "logo", "bezel", "panel", "cabinetLeft", "cabinetRight", 
            "tile", "banner", "steam", "poster", "background", 
            "music", "screenshot", "titlescreen", "video", "manual"
        };
        
        // 扩展名映射
        Map<String, String[]> extensionMap = new HashMap<>();
        extensionMap.put("image", new String[]{"png", "jpg", "jpeg", "gif", "webp"});
        extensionMap.put("video", new String[]{"mp4", "avi", "mkv"});
        extensionMap.put("music", new String[]{"mp3", "ogg", "wav"});
        extensionMap.put("manual", new String[]{"pdf"});
        
        // 媒体类型到扩展名类型的映射
        Map<String, String> mediaTypeToExtensionType = new HashMap<>();
        mediaTypeToExtensionType.put("boxFront", "image");
        mediaTypeToExtensionType.put("boxBack", "image");
        mediaTypeToExtensionType.put("boxSpine", "image");
        mediaTypeToExtensionType.put("boxFull", "image");
        mediaTypeToExtensionType.put("cartridge", "image");
        mediaTypeToExtensionType.put("logo", "image");
        mediaTypeToExtensionType.put("bezel", "image");
        mediaTypeToExtensionType.put("panel", "image");
        mediaTypeToExtensionType.put("cabinetLeft", "image");
        mediaTypeToExtensionType.put("cabinetRight", "image");
        mediaTypeToExtensionType.put("tile", "image");
        mediaTypeToExtensionType.put("banner", "image");
        mediaTypeToExtensionType.put("steam", "image");
        mediaTypeToExtensionType.put("poster", "image");
        mediaTypeToExtensionType.put("background", "image");
        mediaTypeToExtensionType.put("screenshot", "image");
        mediaTypeToExtensionType.put("titlescreen", "image");
        mediaTypeToExtensionType.put("video", "video");
        mediaTypeToExtensionType.put("music", "music");
        mediaTypeToExtensionType.put("manual", "manual");
        
        // 类型名称变体映射（处理不同命名风格）
        Map<String, String[]> typeVariants = new HashMap<>();
        typeVariants.put("boxFront", new String[]{"boxFront", "box_front", "boxfront", "box2dfront", "box2d"});
        typeVariants.put("boxBack", new String[]{"boxBack", "box_back", "boxback", "box2dback"});
        typeVariants.put("boxSpine", new String[]{"boxSpine", "box_spine", "boxspine", "box2dside", "side"});
        typeVariants.put("boxFull", new String[]{"boxFull", "box_full", "boxfull"});
        typeVariants.put("logo", new String[]{"logo", "marquee", "screenmarquee", "screenmarqueesmall", "wheel", "wheelcarbon", "wheelssteel"});
        typeVariants.put("tile", new String[]{"tile"});
        typeVariants.put("background", new String[]{"background", "backdrop"});
        typeVariants.put("screenshot", new String[]{"screenshot", "screen", "screenshotitle"});
        typeVariants.put("poster", new String[]{"poster"});
        typeVariants.put("manual", new String[]{"manual", "support"});
        typeVariants.put("cabinetLeft", new String[]{"cabinetLeft", "cabinet_left", "cabinetleft"});
        typeVariants.put("cabinetRight", new String[]{"cabinetRight", "cabinet_right", "cabinetright"});
        typeVariants.put("box3d", new String[]{"box3d", "box_3d"});
        typeVariants.put("steamgrid", new String[]{"steamgrid", "steam_grid"});
        typeVariants.put("fanart", new String[]{"fanart"});
        typeVariants.put("boxtexture", new String[]{"boxtexture", "box_texture"});
        typeVariants.put("supporttexture", new String[]{"supporttexture", "support_texture"});
        
        // 处理每个媒体类型
        for (String mediaType : mediaTypes) {
            // 如果数据文件中已经有该类型，跳过
            if (metadataAssets != null && metadataAssets.containsKey(mediaType)) {
                continue;
            }
            
            String extensionType = mediaTypeToExtensionType.get(mediaType);
            if (extensionType == null) {
                continue;
            }
            
            String[] extensions = extensionMap.get(extensionType);
            if (extensions == null) {
                continue;
            }
            
            // 获取类型变体
            String[] variants = typeVariants.getOrDefault(mediaType, new String[]{mediaType});
            
            // 1. 查找Pegasus风格：./media/游戏名/类型.扩展名
            String pegasusPath = findMediaFile(baseDir, "media", gameName, null, variants, extensions);
            if (pegasusPath != null) {
                setMediaType(mediaFiles, mediaType, pegasusPath);
                continue;
            }
            
            // 2. 查找ES-DE风格：./media/类型/游戏名.扩展名
            for (String variant : variants) {
                String esdePath = findMediaFile(baseDir, "media", variant, gameName, null, extensions);
                if (esdePath != null) {
                    setMediaType(mediaFiles, mediaType, esdePath);
                    break;
                }
            }
            
            // 3. 查找RetroBat风格：./images/游戏名-类型.扩展名
            if ("image".equals(extensionType)) {
                String retrobatPath = findMediaFile(baseDir, "images", null, gameName, variants, extensions, "-");
                if (retrobatPath != null) {
                    setMediaType(mediaFiles, mediaType, retrobatPath);
                }
            } else if ("video".equals(extensionType)) {
                String retrobatPath = findMediaFile(baseDir, "videos", null, gameName, new String[]{"video"}, extensions, "-");
                if (retrobatPath != null) {
                    setMediaType(mediaFiles, mediaType, retrobatPath);
                }
            } else if ("manual".equals(extensionType)) {
                String retrobatPath = findMediaFile(baseDir, "manuals", null, gameName, new String[]{"manual"}, extensions, "-");
                if (retrobatPath != null) {
                    setMediaType(mediaFiles, mediaType, retrobatPath);
                }
            }
        }
        
        return mediaFiles;
    }
    
    /**
     * 查找媒体文件
     * 
     * @param baseDir 基础目录
     * @param subDir 子目录
     * @param dirName 目录名称（可选）
     * @param fileName 文件名称（可选）
     * @param typeVariants 类型变体
     * @param extensions 扩展名列表
     * @param separator 文件名与类型之间的分隔符
     * @return 找到的文件路径，不存在则返回null
     */
    private static String findMediaFile(String baseDir, String subDir, String dirName, 
                                       String fileName, String[] typeVariants, 
                                       String[] extensions, String... separator) {
        String sep = separator.length > 0 ? separator[0] : ".";
        
        // 构建目录路径
        StringBuilder dirPathBuilder = new StringBuilder(baseDir);
        if (subDir != null) {
            dirPathBuilder.append(File.separator).append(subDir);
        }
        if (dirName != null) {
            dirPathBuilder.append(File.separator).append(dirName);
        }
        String dirPath = dirPathBuilder.toString();
        
        File directory = new File(dirPath);
        if (!directory.exists() || !directory.isDirectory()) {
            return null;
        }
        
        // 遍历目录中的文件
        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }
        
        for (File file : files) {
            if (file.isFile()) {
                String fileNameLower = file.getName().toLowerCase();
                
                // 检查类型变体
                if (typeVariants != null) {
                    for (String variant : typeVariants) {
                        String variantLower = variant.toLowerCase();
                        
                        // 检查文件名匹配
                        if (fileName != null) {
                            String expectedPattern = (fileName + sep + variantLower).toLowerCase();
                            if (fileNameLower.startsWith(expectedPattern)) {
                                // 检查扩展名
                                for (String ext : extensions) {
                                    if (fileNameLower.endsWith("." + ext.toLowerCase())) {
                                        return getRelativePath(file.getAbsolutePath(), baseDir);
                                    }
                                }
                            }
                        } else {
                            // 直接匹配类型作为文件名
                            for (String ext : extensions) {
                                String expectedName = (variantLower + "." + ext).toLowerCase();
                                if (fileNameLower.equals(expectedName)) {
                                    return getRelativePath(file.getAbsolutePath(), baseDir);
                                }
                            }
                        }
                    }
                } else if (fileName != null) {
                    // 直接匹配文件名
                    for (String ext : extensions) {
                        String expectedName = (fileName + "." + ext).toLowerCase();
                        if (fileNameLower.equals(expectedName)) {
                            return getRelativePath(file.getAbsolutePath(), baseDir);
                        }
                    }
                }
            }
        }
        
        return null;
    }
    

    
    /**
     * 设置媒体类型到MediaFiles对象
     * 
     * @param mediaFiles MediaFiles对象
     * @param mediaType 媒体类型
     * @param path 文件路径
     */
    private static void setMediaType(MediaFiles mediaFiles, String mediaType, String path) {
        switch (mediaType) {
            case "boxFront":
                mediaFiles.setBoxFront(path);
                break;
            case "boxBack":
                mediaFiles.setBoxBack(path);
                break;
            case "boxSpine":
                mediaFiles.setBoxSpine(path);
                break;
            case "boxFull":
                mediaFiles.setBoxFull(path);
                break;
            case "cartridge":
                mediaFiles.setCartridge(path);
                break;
            case "logo":
                mediaFiles.setLogo(path);
                break;
            case "bezel":
                mediaFiles.setBezel(path);
                break;
            case "panel":
                mediaFiles.setPanel(path);
                break;
            case "cabinetLeft":
                mediaFiles.setCabinetLeft(path);
                break;
            case "cabinetRight":
                mediaFiles.setCabinetRight(path);
                break;
            case "tile":
                mediaFiles.setTile(path);
                break;
            case "banner":
                mediaFiles.setBanner(path);
                break;
            case "steam":
                mediaFiles.setSteam(path);
                break;
            case "poster":
                mediaFiles.setPoster(path);
                break;
            case "background":
                mediaFiles.setBackground(path);
                break;
            case "music":
                mediaFiles.setMusic(path);
                break;
            case "screenshot":
                mediaFiles.setScreenshot(path);
                break;
            case "titlescreen":
                mediaFiles.setTitlescreen(path);
                break;
            case "video":
                mediaFiles.setVideo(path);
                break;
            case "manual":
                mediaFiles.setManual(path);
                break;
            case "box3d":
                mediaFiles.setBox3d(path);
                break;
            case "steamgrid":
                mediaFiles.setSteamgrid(path);
                break;
            case "fanart":
                mediaFiles.setFanart(path);
                break;
            case "boxtexture":
                mediaFiles.setBoxtexture(path);
                break;
            case "supporttexture":
                mediaFiles.setSupporttexture(path);
                break;
        }
    }
}