package com.gamelist.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PegasusMetadataParser {
    
    public static class GameCollection {
        private String name;
        private String sortBy;
        private String launch;
        private List<Game> games = new ArrayList<>();
        
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public String getSortBy() {
            return sortBy;
        }
        public void setSortBy(String sortBy) {
            this.sortBy = sortBy;
        }
        public String getLaunch() {
            return launch;
        }
        public void setLaunch(String launch) {
            this.launch = launch;
        }
        public List<Game> getGames() {
            return games;
        }
        public void setGames(List<Game> games) {
            this.games = games;
        }
        public void addGame(Game game) {
            this.games.add(game);
        }
    }
    
    public static class Game {
        private String name;
        private List<String> files;
        private String sortBy;
        private String developer;
        private String publisher;
        private String genre;
        private String description;
        private String releaseDate;
        private String players;
        private String rating;
        private String shortname;
        private String fullname;
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
        
        public Game() {
            this.files = new ArrayList<>();
        }
        
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public List<String> getFiles() {
            return files;
        }
        public void setFiles(List<String> files) {
            this.files = files;
        }
        public void addFile(String file) {
            this.files.add(file);
        }
        public String getFile() {
            return files.isEmpty() ? null : files.get(0);
        }
        public void setFile(String file) {
            this.files.clear();
            if (file != null && !file.isEmpty()) {
                this.files.add(file);
            }
        }
        public String getSortBy() {
            return sortBy;
        }
        public void setSortBy(String sortBy) {
            this.sortBy = sortBy;
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
        public String getDescription() {
            return description;
        }
        public void setDescription(String description) {
            this.description = description;
        }
        public String getReleaseDate() {
            return releaseDate;
        }
        public void setReleaseDate(String releaseDate) {
            this.releaseDate = releaseDate;
        }
        public String getPlayers() {
            return players;
        }
        public void setPlayers(String players) {
            this.players = players;
        }
        public String getRating() {
            return rating;
        }
        public void setRating(String rating) {
            this.rating = rating;
        }
        public String getShortname() {
            return shortname;
        }
        public void setShortname(String shortname) {
            this.shortname = shortname;
        }
        public String getFullname() {
            return fullname;
        }
        public void setFullname(String fullname) {
            this.fullname = fullname;
        }
        public String getBoxFront() {
            return boxFront;
        }
        public void setBoxFront(String boxFront) {
            this.boxFront = boxFront;
        }
        public String getVideo() {
            return video;
        }
        public void setVideo(String video) {
            this.video = video;
        }
        public String getLogo() {
            return logo;
        }
        public void setLogo(String logo) {
            this.logo = logo;
        }
        public String getScreenshot() {
            return screenshot;
        }
        public void setScreenshot(String screenshot) {
            this.screenshot = screenshot;
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
        public String getTitlescreen() {
            return titlescreen;
        }
        public void setTitlescreen(String titlescreen) {
            this.titlescreen = titlescreen;
        }
    }
    
    public static List<GameCollection> parseMetadata(File file) throws IOException {
        List<GameCollection> collections = new ArrayList<>();
        GameCollection currentCollection = null;
        Game currentGame = null;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                if (line.startsWith("collection:")) {
                    // 结束当前游戏和集合
                    if (currentGame != null && currentCollection != null) {
                        currentCollection.addGame(currentGame);
                        currentGame = null;
                    }
                    if (currentCollection != null) {
                        collections.add(currentCollection);
                    }
                    
                    // 开始新集合
                    currentCollection = new GameCollection();
                    String name = line.substring(11).trim();
                    currentCollection.setName(name);
                } else if (line.startsWith("game:")) {
                    // 结束当前游戏
                    if (currentGame != null && currentCollection != null) {
                        currentCollection.addGame(currentGame);
                    }
                    
                    // 开始新游戏
                    currentGame = new Game();
                    String name = line.substring(5).trim();
                    currentGame.setName(name);
                } else if (line.startsWith("sort-by:")) {
                    String value = line.substring(8).trim();
                    if (currentGame != null) {
                        currentGame.setSortBy(value);
                    } else if (currentCollection != null) {
                        currentCollection.setSortBy(value);
                    }
                } else if (line.startsWith("launch:")) {
                    String value = line.substring(7).trim();
                    if (currentCollection != null) {
                        currentCollection.setLaunch(value);
                    }
                } else if (line.startsWith("files:") || line.startsWith("file:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        if (line.startsWith("files:")) {
                            // 多行files标签，继续读取后续行
                            currentGame.addFile(value);
                            while ((line = reader.readLine()) != null && (line.startsWith("  ") || line.startsWith("\t"))) {
                                String trimmedLine = line.trim();
                                if (!trimmedLine.isEmpty()) {
                                    currentGame.addFile(trimmedLine);
                                }
                            }
                            if (line != null && !line.trim().isEmpty() && !line.trim().startsWith("#")) {
                                // 如果读取到的行不是空行且不是注释，需要回退一行
                                // 因为外层循环会继续处理这一行
                            }
                        } else {
                            // 单行file标签
                            currentGame.setFile(value);
                        }
                    }
                } else if (line.startsWith("developer:")) {
                    String value = line.substring(10).trim();
                    if (currentGame != null) {
                        currentGame.setDeveloper(value);
                    }
                } else if (line.startsWith("publisher:")) {
                    String value = line.substring(10).trim();
                    if (currentGame != null) {
                        currentGame.setPublisher(value);
                    }
                } else if (line.startsWith("genre:")) {
                    String value = line.substring(6).trim();
                    if (currentGame != null) {
                        currentGame.setGenre(value);
                    }
                } else if (line.startsWith("description:")) {
                    String value = line.substring(12).trim();
                    if (currentGame != null) {
                        currentGame.setDescription(value);
                    }
                } else if (line.startsWith("release_date:")) {
                    String value = line.substring(13).trim();
                    if (currentGame != null) {
                        currentGame.setReleaseDate(value);
                    }
                } else if (line.startsWith("players:")) {
                    String value = line.substring(8).trim();
                    if (currentGame != null) {
                        currentGame.setPlayers(value);
                    }
                } else if (line.startsWith("rating:")) {
                    String value = line.substring(7).trim();
                    if (currentGame != null) {
                        currentGame.setRating(value);
                    }
                } else if (line.startsWith("shortname:")) {
                    String value = line.substring(10).trim();
                    if (currentGame != null) {
                        currentGame.setShortname(value);
                    }
                } else if (line.startsWith("fullname:")) {
                    String value = line.substring(9).trim();
                    if (currentGame != null) {
                        currentGame.setFullname(value);
                    }
                } else if (line.startsWith("assets.boxFront:") || line.startsWith("assets.box_front:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setBoxFront(value);
                    }
                } else if (line.startsWith("assets.video:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setVideo(value);
                    }
                } else if (line.startsWith("assets.logo:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setLogo(value);
                    }
                } else if (line.startsWith("assets.screenshot:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setScreenshot(value);
                    }
                } else if (line.startsWith("assets.boxBack:") || line.startsWith("assets.box_back:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setBoxBack(value);
                    }
                } else if (line.startsWith("assets.boxSpine:") || line.startsWith("assets.box_spine:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setBoxSpine(value);
                    }
                } else if (line.startsWith("assets.boxFull:") || line.startsWith("assets.box_full:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setBoxFull(value);
                    }
                } else if (line.startsWith("assets.cartridge:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setCartridge(value);
                    }
                } else if (line.startsWith("assets.bezel:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setBezel(value);
                    }
                } else if (line.startsWith("assets.panel:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setPanel(value);
                    }
                } else if (line.startsWith("assets.cabinetLeft:") || line.startsWith("assets.cabinet_left:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setCabinetLeft(value);
                    }
                } else if (line.startsWith("assets.cabinetRight:") || line.startsWith("assets.cabinet_right:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setCabinetRight(value);
                    }
                } else if (line.startsWith("assets.tile:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setTile(value);
                    }
                } else if (line.startsWith("assets.banner:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setBanner(value);
                    }
                } else if (line.startsWith("assets.steam:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setSteam(value);
                    }
                } else if (line.startsWith("assets.poster:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setPoster(value);
                    }
                } else if (line.startsWith("assets.background:") || line.startsWith("assets.bg:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setBackground(value);
                    }
                } else if (line.startsWith("assets.music:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setMusic(value);
                    }
                } else if (line.startsWith("assets.titlescreen:")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (currentGame != null) {
                        currentGame.setTitlescreen(value);
                    }
                } else if (line.startsWith("assets:")) {
                    // assets块开始，继续处理后续的缩进行
                    while ((line = reader.readLine()) != null && (line.startsWith("  ") || line.startsWith("\t"))) {
                        String trimmedLine = line.trim();
                        if (trimmedLine.startsWith("box_front:") || trimmedLine.startsWith("boxFront:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setBoxFront(value);
                            }
                        } else if (trimmedLine.startsWith("video:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setVideo(value);
                            }
                        } else if (trimmedLine.startsWith("logo:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setLogo(value);
                            }
                        } else if (trimmedLine.startsWith("screenshot:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setScreenshot(value);
                            }
                        } else if (trimmedLine.startsWith("box_back:") || trimmedLine.startsWith("boxBack:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setBoxBack(value);
                            }
                        } else if (trimmedLine.startsWith("box_spine:") || trimmedLine.startsWith("boxSpine:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setBoxSpine(value);
                            }
                        } else if (trimmedLine.startsWith("box_full:") || trimmedLine.startsWith("boxFull:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setBoxFull(value);
                            }
                        } else if (trimmedLine.startsWith("cartridge:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setCartridge(value);
                            }
                        } else if (trimmedLine.startsWith("bezel:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setBezel(value);
                            }
                        } else if (trimmedLine.startsWith("panel:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setPanel(value);
                            }
                        } else if (trimmedLine.startsWith("cabinet_left:") || trimmedLine.startsWith("cabinetLeft:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setCabinetLeft(value);
                            }
                        } else if (trimmedLine.startsWith("cabinet_right:") || trimmedLine.startsWith("cabinetRight:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setCabinetRight(value);
                            }
                        } else if (trimmedLine.startsWith("tile:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setTile(value);
                            }
                        } else if (trimmedLine.startsWith("banner:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setBanner(value);
                            }
                        } else if (trimmedLine.startsWith("steam:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setSteam(value);
                            }
                        } else if (trimmedLine.startsWith("poster:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setPoster(value);
                            }
                        } else if (trimmedLine.startsWith("background:") || trimmedLine.startsWith("bg:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setBackground(value);
                            }
                        } else if (trimmedLine.startsWith("music:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setMusic(value);
                            }
                        } else if (trimmedLine.startsWith("titlescreen:")) {
                            String value = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            if (currentGame != null) {
                                currentGame.setTitlescreen(value);
                            }
                        }
                    }
                    if (line != null && !line.trim().isEmpty() && !line.trim().startsWith("#")) {
                        // 如果读取到的行不是空行且不是注释，需要回退一行
                        // 因为外层循环会继续处理这一行
                    }
                }
            }
            
            // 处理最后一个游戏和集合
            if (currentGame != null && currentCollection != null) {
                currentCollection.addGame(currentGame);
            }
            if (currentCollection != null) {
                collections.add(currentCollection);
            }
        }
        
        return collections;
    }
}
