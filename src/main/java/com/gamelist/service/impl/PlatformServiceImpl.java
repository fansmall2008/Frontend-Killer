package com.gamelist.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gamelist.mapper.GameMapper;
import com.gamelist.mapper.MediaDownloadTaskMapper;
import com.gamelist.mapper.PlatformMapper;
import com.gamelist.mapper.TempSubsetMapper;
import com.gamelist.model.BackgroundTask;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.model.ScraperSystem;
import com.gamelist.model.TempSubset;
import com.gamelist.model.TempSubsetGame;
import com.gamelist.service.PlatformService;
import com.gamelist.service.ScraperSettingsService;
import com.gamelist.service.ScraperSystemService;
import com.gamelist.service.ScreenScraperApiService;
import com.gamelist.service.TaskService;
import com.gamelist.xml.GameListXml;

@Service
public class PlatformServiceImpl implements PlatformService {
    
    private static final Logger logger = LoggerFactory.getLogger(PlatformServiceImpl.class);
    
    @Autowired
    private PlatformMapper platformMapper;
    
    @Autowired
    private GameMapper gameMapper;
    
    @Autowired
    private MediaDownloadTaskMapper mediaDownloadTaskMapper;
    
    @Autowired
    private TaskService taskService;

    @Autowired
    private TempSubsetMapper tempSubsetMapper;
    
    @Autowired
    private ScreenScraperApiService screenScraperApiService;
    
    @Autowired
    private ScraperSettingsService scraperSettingsService;

    @Autowired
    private ScraperSystemService scraperSystemService;

    @Override
    public Platform savePlatform(GameListXml.Provider providerXml) {
        logger.info("开始保存平台，providerXml: {}", providerXml != null ? providerXml.toString() : "null");
        Platform platform = savePlatform(providerXml, "Unknown");
        logger.info("平台保存完成，平台ID: {}", platform != null ? platform.getId() : "null");
        return platform;
    }
    
    @Override
    public TempSubset createTempSubset(String name, String description, Map<String, Object> filterParams) {
        logger.info("创建临时子集开始: name={}, description={}, filterParams={}", name, description, filterParams);
        
        TempSubset tempSubset = null;
        try {
            // 创建临时子集
            tempSubset = createAndSaveTempSubset(name, description);
            
            // 获取筛选后的游戏
            List<Game> games = getFilteredGames(filterParams);
            logger.info("获取到筛选后的游戏数量: {}", games.size());
            
            // 将游戏添加到临时子集
            if (!games.isEmpty()) {
                addGamesToTempSubset(tempSubset, games);
                logger.info("游戏添加完成，临时子集ID: {}", tempSubset.getId());
            } else {
                logger.warn("没有符合条件的游戏，临时子集创建完成但为空，subsetId={}", tempSubset.getId());
            }
            
            // 验证游戏是否已正确添加
            List<TempSubsetGame> addedGames = tempSubsetMapper.selectTempSubsetGamesBySubsetId(tempSubset.getId());
            logger.info("验证临时子集游戏数量: {}", addedGames.size());
            
            // 设置游戏数量
            tempSubset.setGameCount(addedGames.size());
            
            logger.info("创建临时子集完成，返回临时子集: {}, 游戏数量: {}", tempSubset.getId(), addedGames.size());
            return tempSubset;
        } catch (Exception e) {
            logger.error("创建临时子集失败: {}", e.getMessage(), e);
            // 如果临时子集已经创建，删除它
            if (tempSubset != null && tempSubset.getId() != null) {
                try {
                    deleteTempSubset(tempSubset.getId());
                    logger.info("删除失败的临时子集: {}", tempSubset.getId());
                } catch (Exception deleteException) {
                    logger.error("删除失败的临时子集时出错: {}", deleteException.getMessage(), deleteException);
                }
            }
            throw e;
        }
    }
    
    /**
     * 创建并保存临时子集
     */
    private TempSubset createAndSaveTempSubset(String name, String description) {
        TempSubset tempSubset = new TempSubset();
        tempSubset.setName(name);
        tempSubset.setDescription(description);
        tempSubset.setCreatorId(1L);
        tempSubset.setStatus("ACTIVE");
        
        logger.info("准备插入临时子集: id={}, name={}, creatorId={}, status={}", tempSubset.getId(), tempSubset.getName(), tempSubset.getCreatorId(), tempSubset.getStatus());
        tempSubsetMapper.insertTempSubset(tempSubset);
        logger.info("临时子集创建成功，ID: {}", tempSubset.getId());
        
        return tempSubset;
    }
    
    /**
     * 获取筛选后的游戏
     */
    private List<Game> getFilteredGames(Map<String, Object> filterParams) {
        logger.info("准备获取筛选后的游戏");
        List<Game> games;
        if (filterParams == null || filterParams.isEmpty()) {
            // 如果没有筛选参数，返回所有游戏
            games = gameMapper.selectAllGames();
            logger.info("没有筛选参数，返回所有游戏，数量: {}", games.size());
        } else {
            // 否则使用筛选参数
            games = gameMapper.selectGamesByFilter(filterParams);
            logger.info("获取到筛选后的游戏数量: {}", games.size());
        }
        return games;
    }
    
    /**
     * 将游戏添加到临时子集
     */
    private void addGamesToTempSubset(TempSubset tempSubset, List<Game> games) {
        logger.info("开始添加游戏到临时子集");
        List<TempSubsetGame> tempSubsetGames = convertGamesToTempSubsetGames(tempSubset, games);
        
        logger.info("准备批量插入临时子集游戏，数量: {}", tempSubsetGames.size());
        try {
            // 分批处理大量游戏数据，每批1000个
            batchInsertTempSubsetGames(tempSubsetGames);
            logger.info("临时子集游戏添加完成，共添加 {} 个游戏", tempSubsetGames.size());
        } catch (Exception e) {
            logger.error("批量插入临时子集游戏失败: {}", e.getMessage(), e);
            throw new RuntimeException("批量插入游戏失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 将Game对象转换为TempSubsetGame对象
     */
    private List<TempSubsetGame> convertGamesToTempSubsetGames(TempSubset tempSubset, List<Game> games) {
        List<TempSubsetGame> tempSubsetGames = new ArrayList<>();
        for (Game game : games) {
            TempSubsetGame tsg = new TempSubsetGame();
            tsg.setSubsetId(tempSubset.getId());
            tsg.setOriginalGameId(game.getId());
            tsg.setGameId(game.getGameId());
            tsg.setSsGameId(game.getSsGameId());
            tsg.setSource(game.getSource());
            tsg.setPath(game.getPath());
            tsg.setName(game.getName());
            tsg.setDesc(game.getDesc());
            tsg.setTranslatedName(game.getTranslatedName());
            tsg.setTranslatedDesc(game.getTranslatedDesc());
            tsg.setImage(game.getImage());
            tsg.setVideo(game.getVideo());
            tsg.setMarquee(game.getMarquee());
            tsg.setThumbnail(game.getThumbnail());
            tsg.setManual(game.getManual());
            tsg.setRating(game.getRating());
            tsg.setReleasedate(game.getReleasedate());
            tsg.setDeveloper(game.getDeveloper());
            tsg.setPublisher(game.getPublisher());
            tsg.setGenre(game.getGenre());
            tsg.setPlayers(game.getPlayers());
            tsg.setCrc32(game.getCrc32());
            tsg.setMd5(game.getMd5());
            tsg.setLang(game.getLang());
            tsg.setGenreid(game.getGenreid());
            tsg.setHash(game.getHash());
            tsg.setPlatformType(game.getPlatformType());
            tsg.setSortBy(game.getSortBy());
            tsg.setPlatformId(game.getPlatformId());
            tsg.setPlatformPath(game.getPlatformPath());
            // 状态字段
            tsg.setScraped(game.getScraped());
            tsg.setEdited(game.getEdited());
            tsg.setExists(game.getExists());
            tsg.setAbsolutePath(game.getAbsolutePath());
            // 遗留媒体字段
            tsg.setBoxFront(game.getBoxFront());
            tsg.setBoxBack(game.getBoxBack());
            tsg.setBoxSpine(game.getBoxSpine());
            tsg.setBoxFull(game.getBoxFull());
            tsg.setCartridge(game.getCartridge());
            tsg.setLogo(game.getLogo());
            tsg.setBezel(game.getBezel());
            tsg.setPanel(game.getPanel());
            tsg.setCabinetLeft(game.getCabinetLeft());
            tsg.setCabinetRight(game.getCabinetRight());
            tsg.setTile(game.getTile());
            tsg.setBanner(game.getBanner());
            tsg.setSteam(game.getSteam());
            tsg.setPoster(game.getPoster());
            tsg.setBackground(game.getBackground());
            tsg.setMusic(game.getMusic());
            tsg.setScreenshot(game.getScreenshot());
            tsg.setTitlescreen(game.getTitlescreen());
            tsg.setBox3D(game.getBox3D());
            tsg.setSteamgrid(game.getSteamgrid());
            tsg.setFanart(game.getFanart());
            tsg.setBoxtexture(game.getBoxtexture());
            tsg.setSupporttexture(game.getSupporttexture());
            tsg.setVideonormalized(game.getVideonormalized());
            tsg.setWheelcarbon(game.getWheelcarbon());
            tsg.setWheelsteel(game.getWheelsteel());
            tsg.setScreenmarqueesmall(game.getScreenmarqueesmall());
            tsg.setBoxside(game.getBoxside());
            tsg.setFigurine(game.getFigurine());
            tsg.setPictoliste(game.getPictoliste());
            tsg.setPictomonochrome(game.getPictomonochrome());
            tsg.setPictomonochromesvg(game.getPictomonochromesvg());
            tsg.setPictocouleur(game.getPictocouleur());
            tsg.setWallpaper(game.getWallpaper());
            // SS 标准媒体字段
            tsg.setBox2d(game.getBox2d());
            tsg.setBox2dBack(game.getBox2dBack());
            tsg.setBox2dSide(game.getBox2dSide());
            tsg.setBoxTexture(game.getBoxTexture());
            tsg.setBoxScan(game.getBoxScan());
            tsg.setSupport2d(game.getSupport2d());
            tsg.setSupportTexture(game.getSupportTexture());
            tsg.setSupportScan(game.getSupportScan());
            tsg.setWheel(game.getWheel());
            tsg.setWheelHd(game.getWheelHd());
            tsg.setWheelCarbon(game.getWheelCarbon());
            tsg.setWheelSteel(game.getWheelSteel());
            tsg.setScreenmarquee(game.getScreenmarquee());
            tsg.setSs(game.getSs());
            tsg.setSstitle(game.getSstitle());
            tsg.setOverlay(game.getOverlay());
            tsg.setVideoNormalized(game.getVideoNormalized());
            tsg.setFlyer(game.getFlyer());
            tsg.setManuel(game.getManuel());
            tsg.setMaps(game.getMaps());
            tsg.setBezel43(game.getBezel43());
            tsg.setBezel43V(game.getBezel43V());
            tsg.setBezel43Cocktail(game.getBezel43Cocktail());
            tsg.setBezel169(game.getBezel169());
            tsg.setBezel169V(game.getBezel169V());
            tsg.setBezel169Cocktail(game.getBezel169Cocktail());
            tsg.setMixrbv1(game.getMixrbv1());
            tsg.setMixrbv2(game.getMixrbv2());
            tsg.setThemehb(game.getThemehb());
            tsg.setThemehs(game.getThemehs());
            tsg.setSsdmd(game.getSsdmd());
            tsg.setSsfronton169(game.getSsfronton169());
            tsg.setSsfronton11(game.getSsfronton11());
            tsg.setSsfronton43(game.getSsfronton43());
            tsg.setSstable(game.getSstable());
            tsg.setSstopper(game.getSstopper());
            tsg.setVideodmd(game.getVideodmd());
            tsg.setVideofronton43(game.getVideofronton43());
            tsg.setVideofronton169(game.getVideofronton169());
            tsg.setVideotable(game.getVideotable());
            tsg.setVideotable4k(game.getVideotable4k());
            tsg.setVideotopper(game.getVideotopper());
            tsg.setWheelTarcisios(game.getWheelTarcisios());
            tempSubsetGames.add(tsg);
        }
        return tempSubsetGames;
    }
    
    /**
     * 批量插入临时子集游戏
     */
    private void batchInsertTempSubsetGames(List<TempSubsetGame> tempSubsetGames) {
        int batchSize = 500; // 减小批量大小以减少数据库压力
        int totalBatches = (tempSubsetGames.size() + batchSize - 1) / batchSize;
        logger.info("准备批量插入临时子集游戏，共 {} 个游戏，分 {} 批处理", tempSubsetGames.size(), totalBatches);
        
        for (int i = 0; i < tempSubsetGames.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, tempSubsetGames.size());
            List<TempSubsetGame> batchGames = tempSubsetGames.subList(i, endIndex);
            logger.info("批量插入临时子集游戏批次: {}/{}", (i/batchSize + 1), totalBatches);
            try {
                tempSubsetMapper.insertTempSubsetGamesBatch(batchGames);
                logger.info("批次插入成功，处理游戏数量: {}", batchGames.size());
            } catch (Exception e) {
                logger.error("批次插入失败: {}", e.getMessage());
                throw e;
            }
            // 每批处理后短暂休息，减少数据库压力
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    @Override
    public List<TempSubset> getAllTempSubsets() {
        logger.info("获取所有临时子集");
        List<TempSubset> subsets = tempSubsetMapper.selectAllTempSubsets();
        
        // 为每个临时子集计算并设置游戏数量
        for (TempSubset subset : subsets) {
            try {
                List<TempSubsetGame> games = tempSubsetMapper.selectTempSubsetGamesBySubsetId(subset.getId());
                subset.setGameCount(games.size());
                logger.info("临时子集ID: {}, 游戏数量: {}", subset.getId(), games.size());
            } catch (Exception e) {
                logger.error("计算临时子集游戏数量时出错: {}", e.getMessage(), e);
                subset.setGameCount(0);
            }
        }
        
        return subsets;
    }
    
    @Override
    public TempSubset getTempSubsetById(Long id) {
        logger.info("获取临时子集 by ID: {}", id);
        return tempSubsetMapper.selectTempSubsetById(id);
    }
    
    @Override
    public List<TempSubsetGame> getTempSubsetGamesBySubsetId(Long subsetId) {
        logger.info("获取临时子集游戏 by subsetId: {}", subsetId);
        return tempSubsetMapper.selectTempSubsetGamesBySubsetId(subsetId);
    }
    
    @Override
    public TempSubsetGame getTempSubsetGameById(Long id) {
        logger.info("获取临时子集游戏 by ID: {}", id);
        return tempSubsetMapper.selectTempSubsetGameById(id);
    }
    
    @Override
    public TempSubsetGame getTempSubsetGameByOriginalGameId(Long originalGameId) {
        logger.info("获取临时子集游戏 by Original ID: {}", originalGameId);
        return tempSubsetMapper.selectTempSubsetGameByOriginalGameId(originalGameId);
    }
    
    @Override
    public int updateTempSubsetGame(TempSubsetGame tempSubsetGame) {
        logger.info("更新临时子集游戏: ID={}", tempSubsetGame.getId());
        return tempSubsetMapper.updateTempSubsetGame(tempSubsetGame);
    }
    
    @Override
    public int syncTempSubsetToMainSet(Long subsetId) {
        logger.info("同步临时子集到主集: subsetId={}", subsetId);
        
        // 获取临时子集的所有游戏
        List<TempSubsetGame> tempSubsetGames = tempSubsetMapper.selectTempSubsetGamesBySubsetId(subsetId);
        logger.info("获取到临时子集游戏数量: {}", tempSubsetGames.size());
        
        int updatedCount = 0;
        for (TempSubsetGame tempGame : tempSubsetGames) {
            // 获取原始游戏
            Game originalGame = gameMapper.selectGameById(tempGame.getOriginalGameId());
            if (originalGame != null) {
                // 同步基础字段
                originalGame.setName(tempGame.getName());
                originalGame.setDesc(tempGame.getDesc());
                originalGame.setTranslatedName(tempGame.getTranslatedName());
                originalGame.setTranslatedDesc(tempGame.getTranslatedDesc());
                originalGame.setImage(tempGame.getImage());
                originalGame.setVideo(tempGame.getVideo());
                originalGame.setMarquee(tempGame.getMarquee());
                originalGame.setThumbnail(tempGame.getThumbnail());
                originalGame.setManual(tempGame.getManual());
                originalGame.setRating(tempGame.getRating());
                originalGame.setReleasedate(tempGame.getReleasedate());
                originalGame.setDeveloper(tempGame.getDeveloper());
                originalGame.setPublisher(tempGame.getPublisher());
                originalGame.setGenre(tempGame.getGenre());
                originalGame.setPlayers(tempGame.getPlayers());
                originalGame.setCrc32(tempGame.getCrc32());
                originalGame.setMd5(tempGame.getMd5());
                originalGame.setLang(tempGame.getLang());
                originalGame.setGenreid(tempGame.getGenreid());
                originalGame.setHash(tempGame.getHash());
                originalGame.setPlatformType(tempGame.getPlatformType());
                originalGame.setSortBy(tempGame.getSortBy());
                // 同步状态字段
                originalGame.setScraped(tempGame.getScraped());
                originalGame.setEdited(tempGame.getEdited());
                originalGame.setExists(tempGame.getExists());
                originalGame.setAbsolutePath(tempGame.getAbsolutePath());
                // 同步遗留媒体字段
                originalGame.setBoxFront(tempGame.getBoxFront());
                originalGame.setBoxBack(tempGame.getBoxBack());
                originalGame.setBoxSpine(tempGame.getBoxSpine());
                originalGame.setBoxFull(tempGame.getBoxFull());
                originalGame.setCartridge(tempGame.getCartridge());
                originalGame.setLogo(tempGame.getLogo());
                originalGame.setBezel(tempGame.getBezel());
                originalGame.setPanel(tempGame.getPanel());
                originalGame.setCabinetLeft(tempGame.getCabinetLeft());
                originalGame.setCabinetRight(tempGame.getCabinetRight());
                originalGame.setTile(tempGame.getTile());
                originalGame.setBanner(tempGame.getBanner());
                originalGame.setSteam(tempGame.getSteam());
                originalGame.setPoster(tempGame.getPoster());
                originalGame.setBackground(tempGame.getBackground());
                originalGame.setMusic(tempGame.getMusic());
                originalGame.setScreenshot(tempGame.getScreenshot());
                originalGame.setTitlescreen(tempGame.getTitlescreen());
                originalGame.setBox3D(tempGame.getBox3D());
                originalGame.setSteamgrid(tempGame.getSteamgrid());
                originalGame.setFanart(tempGame.getFanart());
                originalGame.setBoxtexture(tempGame.getBoxtexture());
                originalGame.setSupporttexture(tempGame.getSupporttexture());
                originalGame.setVideonormalized(tempGame.getVideonormalized());
                originalGame.setWheelcarbon(tempGame.getWheelcarbon());
                originalGame.setWheelsteel(tempGame.getWheelsteel());
                originalGame.setScreenmarqueesmall(tempGame.getScreenmarqueesmall());
                originalGame.setBoxside(tempGame.getBoxside());
                originalGame.setFigurine(tempGame.getFigurine());
                originalGame.setPictoliste(tempGame.getPictoliste());
                originalGame.setPictomonochrome(tempGame.getPictomonochrome());
                originalGame.setPictomonochromesvg(tempGame.getPictomonochromesvg());
                originalGame.setPictocouleur(tempGame.getPictocouleur());
                originalGame.setWallpaper(tempGame.getWallpaper());
                // 同步 SS 标准媒体字段
                originalGame.setBox2d(tempGame.getBox2d());
                originalGame.setBox2dBack(tempGame.getBox2dBack());
                originalGame.setBox2dSide(tempGame.getBox2dSide());
                originalGame.setBoxTexture(tempGame.getBoxTexture());
                originalGame.setBoxScan(tempGame.getBoxScan());
                originalGame.setSupport2d(tempGame.getSupport2d());
                originalGame.setSupportTexture(tempGame.getSupportTexture());
                originalGame.setSupportScan(tempGame.getSupportScan());
                originalGame.setWheel(tempGame.getWheel());
                originalGame.setWheelHd(tempGame.getWheelHd());
                originalGame.setWheelCarbon(tempGame.getWheelCarbon());
                originalGame.setWheelSteel(tempGame.getWheelSteel());
                originalGame.setScreenmarquee(tempGame.getScreenmarquee());
                originalGame.setSs(tempGame.getSs());
                originalGame.setSstitle(tempGame.getSstitle());
                originalGame.setOverlay(tempGame.getOverlay());
                originalGame.setVideoNormalized(tempGame.getVideoNormalized());
                originalGame.setFlyer(tempGame.getFlyer());
                originalGame.setManuel(tempGame.getManuel());
                originalGame.setMaps(tempGame.getMaps());
                originalGame.setBezel43(tempGame.getBezel43());
                originalGame.setBezel43V(tempGame.getBezel43V());
                originalGame.setBezel43Cocktail(tempGame.getBezel43Cocktail());
                originalGame.setBezel169(tempGame.getBezel169());
                originalGame.setBezel169V(tempGame.getBezel169V());
                originalGame.setBezel169Cocktail(tempGame.getBezel169Cocktail());
                originalGame.setMixrbv1(tempGame.getMixrbv1());
                originalGame.setMixrbv2(tempGame.getMixrbv2());
                originalGame.setThemehb(tempGame.getThemehb());
                originalGame.setThemehs(tempGame.getThemehs());
                originalGame.setSsdmd(tempGame.getSsdmd());
                originalGame.setSsfronton169(tempGame.getSsfronton169());
                originalGame.setSsfronton11(tempGame.getSsfronton11());
                originalGame.setSsfronton43(tempGame.getSsfronton43());
                originalGame.setSstable(tempGame.getSstable());
                originalGame.setSstopper(tempGame.getSstopper());
                originalGame.setVideodmd(tempGame.getVideodmd());
                originalGame.setVideofronton43(tempGame.getVideofronton43());
                originalGame.setVideofronton169(tempGame.getVideofronton169());
                originalGame.setVideotable(tempGame.getVideotable());
                originalGame.setVideotable4k(tempGame.getVideotable4k());
                originalGame.setVideotopper(tempGame.getVideotopper());
                originalGame.setWheelTarcisios(tempGame.getWheelTarcisios());
                
                gameMapper.updateGame(originalGame);
                updatedCount++;
                logger.info("更新原始游戏成功，ID: {}", originalGame.getId());
            }
        }
        
        logger.info("同步完成，共更新 {} 个游戏", updatedCount);
        return updatedCount;
    }
    
    @Override
    public Platform createPlatformFromTempSubset(Long subsetId, String platformName) {
        logger.info("从临时子集创建平台: subsetId={}, platformName={}", subsetId, platformName);
        
        // 创建新平台（folderPath 设为空）
        Platform platform = new Platform();
        platform.setSystem(platformName.trim());
        platform.setName(platformName.trim());
        platform.setFolderPath(""); // 从子集创建的平台不继承 folderPath
        platform.setSoftware("WebGamelistOper");
        platform.setDatabase("Custom Database");
        platform.setWeb("http://localhost:8084");
        platformMapper.insertPlatform(platform);
        logger.info("新平台创建成功，ID: {}", platform.getId());
        
        // 获取临时子集的所有游戏
        List<TempSubsetGame> tempSubsetGames = tempSubsetMapper.selectTempSubsetGamesBySubsetId(subsetId);
        logger.info("获取到临时子集游戏数量: {}", tempSubsetGames.size());
        
        if (!tempSubsetGames.isEmpty()) {
            for (TempSubsetGame tempGame : tempSubsetGames) {
                // 创建新游戏，完整复制所有字段
                Game newGame = new Game();
                newGame.setGameId(tempGame.getGameId());
                newGame.setSsGameId(tempGame.getSsGameId());
                newGame.setSource(tempGame.getSource());
                newGame.setPath(tempGame.getPath());
                newGame.setName(tempGame.getName());
                newGame.setDesc(tempGame.getDesc());
                newGame.setTranslatedName(tempGame.getTranslatedName());
                newGame.setTranslatedDesc(tempGame.getTranslatedDesc());
                newGame.setImage(tempGame.getImage());
                newGame.setVideo(tempGame.getVideo());
                newGame.setMarquee(tempGame.getMarquee());
                newGame.setThumbnail(tempGame.getThumbnail());
                newGame.setManual(tempGame.getManual());
                newGame.setRating(tempGame.getRating());
                newGame.setReleasedate(tempGame.getReleasedate());
                newGame.setDeveloper(tempGame.getDeveloper());
                newGame.setPublisher(tempGame.getPublisher());
                newGame.setGenre(tempGame.getGenre());
                newGame.setPlayers(tempGame.getPlayers());
                newGame.setCrc32(tempGame.getCrc32());
                newGame.setMd5(tempGame.getMd5());
                newGame.setLang(tempGame.getLang());
                newGame.setGenreid(tempGame.getGenreid());
                newGame.setHash(tempGame.getHash());
                newGame.setPlatformType(tempGame.getPlatformType());
                newGame.setSortBy(tempGame.getSortBy());
                newGame.setPlatformId(platform.getId());
                newGame.setPlatformPath(tempGame.getPlatformPath());
                // 状态字段
                newGame.setScraped(tempGame.getScraped());
                newGame.setEdited(tempGame.getEdited());
                newGame.setExists(tempGame.getExists());
                newGame.setAbsolutePath(tempGame.getAbsolutePath());
                // 遗留媒体字段
                newGame.setBoxFront(tempGame.getBoxFront());
                newGame.setBoxBack(tempGame.getBoxBack());
                newGame.setBoxSpine(tempGame.getBoxSpine());
                newGame.setBoxFull(tempGame.getBoxFull());
                newGame.setCartridge(tempGame.getCartridge());
                newGame.setLogo(tempGame.getLogo());
                newGame.setBezel(tempGame.getBezel());
                newGame.setPanel(tempGame.getPanel());
                newGame.setCabinetLeft(tempGame.getCabinetLeft());
                newGame.setCabinetRight(tempGame.getCabinetRight());
                newGame.setTile(tempGame.getTile());
                newGame.setBanner(tempGame.getBanner());
                newGame.setSteam(tempGame.getSteam());
                newGame.setPoster(tempGame.getPoster());
                newGame.setBackground(tempGame.getBackground());
                newGame.setMusic(tempGame.getMusic());
                newGame.setScreenshot(tempGame.getScreenshot());
                newGame.setTitlescreen(tempGame.getTitlescreen());
                newGame.setBox3D(tempGame.getBox3D());
                newGame.setSteamgrid(tempGame.getSteamgrid());
                newGame.setFanart(tempGame.getFanart());
                newGame.setBoxtexture(tempGame.getBoxtexture());
                newGame.setSupporttexture(tempGame.getSupporttexture());
                newGame.setVideonormalized(tempGame.getVideonormalized());
                newGame.setWheelcarbon(tempGame.getWheelcarbon());
                newGame.setWheelsteel(tempGame.getWheelsteel());
                newGame.setScreenmarqueesmall(tempGame.getScreenmarqueesmall());
                newGame.setBoxside(tempGame.getBoxside());
                newGame.setFigurine(tempGame.getFigurine());
                newGame.setPictoliste(tempGame.getPictoliste());
                newGame.setPictomonochrome(tempGame.getPictomonochrome());
                newGame.setPictomonochromesvg(tempGame.getPictomonochromesvg());
                newGame.setPictocouleur(tempGame.getPictocouleur());
                newGame.setWallpaper(tempGame.getWallpaper());
                // SS 标准媒体字段
                newGame.setBox2d(tempGame.getBox2d());
                newGame.setBox2dBack(tempGame.getBox2dBack());
                newGame.setBox2dSide(tempGame.getBox2dSide());
                newGame.setBoxTexture(tempGame.getBoxTexture());
                newGame.setBoxScan(tempGame.getBoxScan());
                newGame.setSupport2d(tempGame.getSupport2d());
                newGame.setSupportTexture(tempGame.getSupportTexture());
                newGame.setSupportScan(tempGame.getSupportScan());
                newGame.setWheel(tempGame.getWheel());
                newGame.setWheelHd(tempGame.getWheelHd());
                newGame.setWheelCarbon(tempGame.getWheelCarbon());
                newGame.setWheelSteel(tempGame.getWheelSteel());
                newGame.setScreenmarquee(tempGame.getScreenmarquee());
                newGame.setSs(tempGame.getSs());
                newGame.setSstitle(tempGame.getSstitle());
                newGame.setOverlay(tempGame.getOverlay());
                newGame.setVideoNormalized(tempGame.getVideoNormalized());
                newGame.setFlyer(tempGame.getFlyer());
                newGame.setManuel(tempGame.getManuel());
                newGame.setMaps(tempGame.getMaps());
                newGame.setBezel43(tempGame.getBezel43());
                newGame.setBezel43V(tempGame.getBezel43V());
                newGame.setBezel43Cocktail(tempGame.getBezel43Cocktail());
                newGame.setBezel169(tempGame.getBezel169());
                newGame.setBezel169V(tempGame.getBezel169V());
                newGame.setBezel169Cocktail(tempGame.getBezel169Cocktail());
                newGame.setMixrbv1(tempGame.getMixrbv1());
                newGame.setMixrbv2(tempGame.getMixrbv2());
                newGame.setThemehb(tempGame.getThemehb());
                newGame.setThemehs(tempGame.getThemehs());
                newGame.setSsdmd(tempGame.getSsdmd());
                newGame.setSsfronton169(tempGame.getSsfronton169());
                newGame.setSsfronton11(tempGame.getSsfronton11());
                newGame.setSsfronton43(tempGame.getSsfronton43());
                newGame.setSstable(tempGame.getSstable());
                newGame.setSstopper(tempGame.getSstopper());
                newGame.setVideodmd(tempGame.getVideodmd());
                newGame.setVideofronton43(tempGame.getVideofronton43());
                newGame.setVideofronton169(tempGame.getVideofronton169());
                newGame.setVideotable(tempGame.getVideotable());
                newGame.setVideotable4k(tempGame.getVideotable4k());
                newGame.setVideotopper(tempGame.getVideotopper());
                newGame.setWheelTarcisios(tempGame.getWheelTarcisios());
                
                gameMapper.insertGame(newGame);
            }
            logger.info("游戏添加完成，共添加 {} 个游戏", tempSubsetGames.size());
        }
        
        return platform;
    }
    
    @Override
    public int deleteTempSubset(Long subsetId) {
        logger.info("删除临时子集: subsetId={}", subsetId);
        
        // 先删除临时子集游戏
        tempSubsetMapper.deleteTempSubsetGamesBySubsetId(subsetId);
        // 再删除临时子集
        int result = tempSubsetMapper.deleteTempSubset(subsetId);
        logger.info("临时子集删除完成，结果: {}", result);
        return result;
    }
    
    @Override
    public Platform savePlatform(GameListXml.Provider providerXml, String defaultSystemName) {
        // 记录providerXml的各个属性，而不是调用toString()
        logger.info("开始保存平台，defaultSystemName: {}", defaultSystemName);
        if (providerXml != null) {
            logger.info("Provider属性: System={}, Software={}, Database={}, Web={}", 
                    providerXml.getSystem(), providerXml.getSoftware(), 
                    providerXml.getDatabase(), providerXml.getWeb());
        } else {
            logger.info("Provider为null");
        }
        
        try {
            // 确定平台系统名称 - 始终使用默认系统名称，因为它已经包含了标准命名格式
            String systemName = defaultSystemName != null ? defaultSystemName : "Unknown";
            logger.info("使用默认系统名称: {}", systemName);
            
            // 标准化平台名称：去除首尾空格
            String normalizedSystemName = systemName != null ? systemName.trim() : "unknown";
            logger.info("标准化后的平台名称: {}", normalizedSystemName);
            
            // 检查是否已存在相同系统名称的平台（使用标准化名称）
            logger.info("检查是否已存在平台: {}", normalizedSystemName);
            Platform existingPlatform = platformMapper.selectPlatformBySystem(normalizedSystemName);
            if (existingPlatform != null) {
                logger.info("平台已存在，返回现有平台: {}", existingPlatform.getId());
                return existingPlatform;
            }
            
            // 创建新平台
            logger.info("创建新平台");
            Platform platform = new Platform();
            platform.setSystem(normalizedSystemName);
            platform.setName(normalizedSystemName != null ? normalizedSystemName : "unknown"); // 确保name字段不为null
            platform.setFolderPath(normalizedSystemName); // 设置folderPath字段
            logger.info("设置平台属性: system={}, name={}, folderPath={}", platform.getSystem(), platform.getName(), platform.getFolderPath());
            
            // 如果providerXml不为null，设置其他属性
            if (providerXml != null) {
                platform.setSoftware(providerXml.getSoftware() != null ? providerXml.getSoftware() : "");
                platform.setDatabase(providerXml.getDatabase() != null ? providerXml.getDatabase() : "");
                platform.setWeb(providerXml.getWeb() != null ? providerXml.getWeb() : "");
                logger.info("设置平台其他属性: software={}, database={}, web={}", 
                        platform.getSoftware(), platform.getDatabase(), platform.getWeb());
            } else {
                // 如果providerXml为null，设置默认值
                platform.setSoftware("");
                platform.setDatabase("");
                platform.setWeb("");
                logger.info("Provider为null，设置默认属性值");
            }
            
            // 保存平台到数据库
            logger.info("开始插入平台到数据库");
            // 再次检查并确保name字段不为null
            if (platform.getName() == null || platform.getName().isEmpty()) {
                String fallbackName = normalizedSystemName != null ? normalizedSystemName : "unknown";
                platform.setName(fallbackName);
                platform.setFolderPath(fallbackName); // 同时设置folderPath
                logger.warn("平台name字段为空，设置为默认值: {}", fallbackName);
            }
            logger.info("最终平台属性: system={}, name={}, folderPath={}", platform.getSystem(), platform.getName(), platform.getFolderPath());
            platformMapper.insertPlatform(platform);
            logger.info("平台插入成功，平台ID: {}", platform.getId());
            
            return platform;
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "数据库操作失败";
            logger.error("平台保存失败: {}", errorMessage, e);
            throw new RuntimeException("平台保存失败: " + errorMessage, e);
        }
    }

    @Override
    public Platform getPlatformBySystem(String system) {
        // 标准化平台名称：去除首尾空格，转换为小写
        String normalizedSystemName = system != null ? system.trim().toLowerCase() : "unknown";
        logger.info("获取平台 by system: {}", normalizedSystemName);
        return platformMapper.selectPlatformBySystem(normalizedSystemName);
    }

    @Override
    public List<Platform> getAllPlatforms() {
        logger.info("获取所有平台");
        return platformMapper.selectAllPlatforms();
    }

    @Override
    public Platform getPlatformById(Long id) {
        logger.info("获取平台 by ID: {}", id);
        return platformMapper.selectPlatformById(id);
    }

    @Override
    public Platform updatePlatform(Platform platform) {
        logger.info("更新平台: ID={}, System={}, Name={}", platform.getId(), platform.getSystem(), platform.getName());
        
        // 检查 systemId 是否变更（用于决定是否自动下载 icon）
        Integer oldSystemId = null;
        Platform existing = platformMapper.selectPlatformById(platform.getId());
        if (existing != null) {
            oldSystemId = existing.getSystemId();
        }
        
        // 确保 system 和 name 字段同步（保持一致）
        String system = platform.getSystem();
        String name = platform.getName();
        
        if (system != null && !system.isEmpty()) {
            // 如果 system 有值，name 也设置为相同值
            platform.setName(system);
        } else if (name != null && !name.isEmpty()) {
            // 如果只有 name 有值，system 也设置为相同值
            platform.setSystem(name);
        }
        
        platformMapper.updatePlatform(platform);
        
        // 如果 systemId 变更了，自动下载系统 icon
        Integer newSystemId = platform.getSystemId();
        if (newSystemId != null && !newSystemId.equals(oldSystemId)) {
            try {
                scraperSystemService.scrapeSystemIcon(newSystemId, platform.getLogoRegion());
                logger.info("systemId 变更 {} → {}，已自动下载系统 icon", oldSystemId, newSystemId);
            } catch (Exception e) {
                logger.warn("自动下载系统 icon 失败，不影响保存: systemId={}, error={}", newSystemId, e.getMessage());
            }
        }
        
        return platformMapper.selectPlatformById(platform.getId());
    }

    @Override
    public int addPlatform(Platform platform) {
        logger.info("添加平台: System={}", platform.getSystem());
        return platformMapper.insertPlatform(platform);
    }

    @Override
    public void deletePlatform(Long id) {
        logger.info("删除平台: ID={}", id);
        // 先删除关联的媒体下载任务（解决外键约束）
        mediaDownloadTaskMapper.deleteByPlatformId(id);
        // 再删除关联游戏
        gameMapper.deleteGamesByPlatformId(id);
        // 最后删除平台
        platformMapper.deletePlatform(id);
        logger.info("平台删除完成: ID={}", id);
    }
    
    @Override
    public int getGameCountByPlatformId(Long platformId) {
        logger.info("获取平台游戏数量: platformId={}", platformId);
        return gameMapper.countGamesByPlatformId(platformId);
    }
    
    @Override
    @Transactional
    public Platform createPlatformWithGames(String name, java.util.Map<String, Object> filterParams) {
        logger.info("创建带游戏的平台: name={}, filterParams={}", name, filterParams);
        // 检查平台名称是否为空
        if (name == null || name.trim().isEmpty()) {
            logger.error("平台名称不能为空");
            throw new IllegalArgumentException("平台名称不能为空");
        }
        
        // 从filterParams获取平台ID列表
        java.util.List<Long> platformIds = null;
        if (filterParams != null && filterParams.containsKey("platformIds")) {
            java.util.List<?> tempList = (java.util.List<?>) filterParams.get("platformIds");
            if (tempList != null && !tempList.isEmpty()) {
                platformIds = new java.util.ArrayList<>();
                for (Object id : tempList) {
                    if (id != null) {
                        if (id instanceof Integer) {
                            platformIds.add(((Integer) id).longValue());
                        } else if (id instanceof Long) {
                            platformIds.add((Long) id);
                        } else if (id instanceof String) {
                            try {
                                platformIds.add(Long.parseLong((String) id));
                            } catch (NumberFormatException e) {
                                // 忽略无效的字符串ID
                                logger.warn("无效的平台ID: {}", id);
                            }
                        }
                    }
                }
            }
        }
        
        // 继承原平台的属性（如果有）
        Platform originalPlatform = null;
        if (platformIds != null && !platformIds.isEmpty()) {
            // 获取第一个平台作为主平台来继承属性
            originalPlatform = platformMapper.selectPlatformById(platformIds.get(0));
            logger.info("继承原平台属性: ID={}", originalPlatform != null ? originalPlatform.getId() : "null");
        }
        
        // 创建新平台
        Platform platform = new Platform();
        platform.setSystem(name.trim());
        platform.setName(name.trim());
        platform.setSoftware("WebGamelistOper");
        platform.setDatabase("Custom Database");
        platform.setWeb("http://localhost:8083");
        logger.info("创建新平台: System={}, Name={}", platform.getSystem(), platform.getName());
        
        // 继承原平台的完整属性（folderPath 不继承，设为空）
        if (originalPlatform != null) {
            platform.setSortBy(originalPlatform.getSortBy());
            platform.setLaunch(originalPlatform.getLaunch());
            platform.setFolderPath(""); // 新平台不继承原平台的 folderPath
            platform.setSystemId(originalPlatform.getSystemId());
            platform.setSystemRegion(originalPlatform.getSystemRegion());
            platform.setLogoRegion(originalPlatform.getLogoRegion());
            platform.setLogoType(originalPlatform.getLogoType());
            platform.setExtensions(originalPlatform.getExtensions());
            platform.setIgnoreFiles(originalPlatform.getIgnoreFiles());
            logger.info("继承原平台的 sortBy, launch, systemId, systemRegion 等属性（folderPath 不继承）");
        }
        
        // 保存平台
        platformMapper.insertPlatform(platform);
        logger.info("新平台保存成功，ID: {}", platform.getId());
        
        // 从GameMapper获取筛选后的游戏
        List<com.gamelist.model.Game> games = gameMapper.selectGamesByFilter(filterParams);
        if (games.isEmpty()) {
            logger.warn("筛选结果为 0 个游戏，新平台 {} (ID={}) 将创建为空平台", name, platform.getId());
        }
        logger.info("获取到筛选后的游戏数量: {}", games.size());
        
        // 如果有游戏，复制到新平台后删除原平台中的记录（移动语义）
        if (!games.isEmpty()) {
            logger.info("开始移动游戏到新平台ID: {}", platform.getId());
            java.util.List<Long> originalGameIds = new java.util.ArrayList<>();
            for (com.gamelist.model.Game game : games) {
                // 使用 createGameCopy 完整复制所有字段（包括路径、媒体、状态等），只修改 platformId
                com.gamelist.model.Game newGame = createGameCopy(game, platform.getId());
                // 保存新游戏
                gameMapper.insertGame(newGame);
                // 记录原始游戏ID，稍后删除
                originalGameIds.add(game.getId());
            }
            // 删除原平台中的游戏记录（移动语义）
            for (Long originalGameId : originalGameIds) {
                try {
                    gameMapper.deleteGameById(originalGameId);
                } catch (Exception e) {
                    logger.warn("删除原游戏记录失败，ID: {}, 错误: {}", originalGameId, e.getMessage());
                }
            }
            logger.info("游戏移动完成，共移动 {} 个游戏，删除原记录 {} 个", games.size(), originalGameIds.size());
        }
        
        return platform;
    }
    
    @Override
    public Map<String, Object> mergePlatforms(Long primaryPlatformId, Long secondaryPlatformId) {
        logger.info("开始平台合并，主要平台ID: {}", primaryPlatformId);
        logger.info("次要平台ID: {}", secondaryPlatformId);
        
        try {
            // 获取主要平台和次要平台
            Platform primaryPlatform = platformMapper.selectPlatformById(primaryPlatformId);
            Platform secondaryPlatform = platformMapper.selectPlatformById(secondaryPlatformId);
            
            if (primaryPlatform == null) {
                throw new IllegalArgumentException("主要平台不存在");
            }
            if (secondaryPlatform == null) {
                throw new IllegalArgumentException("次要平台不存在");
            }
            
            logger.info("主要平台名称: {}", primaryPlatform.getName());
            logger.info("次要平台名称: {}", secondaryPlatform.getName());
            
            // 创建后台任务
            String taskDescription = "平台合并: " + secondaryPlatform.getName() + " → " + primaryPlatform.getName();
            BackgroundTask task = taskService.createTask("MERGE", taskDescription);
            
            // 异步执行平台合并
            mergePlatformsAsync(task.getId(), primaryPlatformId, secondaryPlatformId, primaryPlatform.getName(), secondaryPlatform.getName());
            
            // 返回任务ID给前端
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("taskId", task.getId());
            result.put("message", "平台合并任务已启动，请在任务管理页面查看进度");
            
            return result;
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "平台合并失败";
            logger.error("平台合并失败: {}", errorMessage, e);
            
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", false);
            result.put("errorMessage", errorMessage);
            
            return result;
        }
    }
    
    @Override
    public Map<String, Object> mergePlatforms(List<Long> sourcePlatformIds, String newPlatformName, String newPlatformFolderPath, Boolean overwrite) {
        logger.info("开始平台合并，源平台数量: {}", sourcePlatformIds.size());
        logger.info("新平台名称: {}", newPlatformName);
        logger.info("新平台文件夹路径: {}", newPlatformFolderPath);
        logger.info("是否覆盖已存在文件: {}", overwrite);
        
        try {
            // 验证源平台是否存在
            List<Platform> sourcePlatforms = new ArrayList<>();
            for (Long platformId : sourcePlatformIds) {
                Platform platform = platformMapper.selectPlatformById(platformId);
                if (platform == null) {
                    throw new IllegalArgumentException("源平台不存在: " + platformId);
                }
                sourcePlatforms.add(platform);
            }
            
            // 创建新平台
            Platform newPlatform = new Platform();
            newPlatform.setSystem(newPlatformName.trim());
            newPlatform.setName(newPlatformName.trim());
            newPlatform.setSoftware("WebGamelistOper");
            newPlatform.setDatabase("Custom Database");
            newPlatform.setWeb("http://localhost:8083");
            newPlatform.setFolderPath(newPlatformFolderPath);
            platformMapper.insertPlatform(newPlatform);
            logger.info("新平台创建成功，ID: {}", newPlatform.getId());
            
            // 创建后台任务
            String taskDescription = "平台合并: 创建新平台 " + newPlatformName;
            BackgroundTask task = taskService.createTask("MERGE", taskDescription);
            
            // 异步执行平台合并
            mergePlatformsAsync(task.getId(), sourcePlatforms, newPlatform, overwrite);
            
            // 返回任务ID给前端
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("taskId", task.getId());
            result.put("message", "平台合并任务已启动，请在任务管理页面查看进度");
            
            return result;
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "平台合并失败";
            logger.error("平台合并失败: {}", errorMessage, e);
            
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", false);
            result.put("errorMessage", errorMessage);
            
            return result;
        }
    }
    
    /**
     * 异步执行平台合并操作
     */
    @Async
    private void mergePlatformsAsync(Long taskId, Long primaryPlatformId, Long secondaryPlatformId, String primaryPlatformName, String secondaryPlatformName) {
        try {
            taskService.updateTaskProgress(taskId, 10, "开始平台合并操作", 0, 100);
            
            Platform primaryPlatform = platformMapper.selectPlatformById(primaryPlatformId);
            Platform secondaryPlatform = platformMapper.selectPlatformById(secondaryPlatformId);
            
            List<com.gamelist.model.Game> secondaryGames = gameMapper.selectGamesByPlatformId(secondaryPlatformId);
            int totalGames = secondaryGames.size();
            taskService.updateTaskProgress(taskId, 20, "获取次要平台游戏完成，共 " + totalGames + " 个游戏", 0, totalGames);
            
            int addedCount = 0;
            int processedCount = 0;
            
            for (com.gamelist.model.Game secondaryGame : secondaryGames) {
                processedCount++;
                int progress = totalGames > 0 ? 20 + (processedCount * 80) / totalGames : 100;
                taskService.updateTaskProgress(taskId, progress, "处理游戏: " + secondaryGame.getName(), processedCount, totalGames);
                
                logger.debug("添加到主要平台，游戏名称: {}", secondaryGame.getName());
                // 添加详细日志到任务
                taskService.updateTaskLog(taskId, "合并游戏: " + secondaryGame.getName() + " (" + secondaryGame.getPath() + ") 从平台 " + secondaryPlatformName + " 到 " + primaryPlatformName);
                com.gamelist.model.Game newGame = createGameCopy(secondaryGame, primaryPlatformId);
                gameMapper.insertGame(newGame);
                addedCount++;
            }
            
            logger.info("平台合并完成");
            logger.info("添加游戏数量: {}", addedCount);
            
            String resultMessage = String.format("平台合并完成\n主要平台: %s\n次要平台: %s\n添加游戏: %d", 
                primaryPlatformName, secondaryPlatformName, addedCount);
            
            taskService.completeTask(taskId, "平台合并完成", resultMessage);
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "平台合并失败";
            logger.error("平台合并失败: {}", errorMessage, e);
            taskService.failTask(taskId, "平台合并失败", errorMessage);
        }
    }
    
    /**
     * 异步执行多平台合并到新平台操作
     */
    @Async
    private void mergePlatformsAsync(Long taskId, List<Platform> sourcePlatforms, Platform newPlatform, Boolean overwrite) {
        try {
            taskService.updateTaskProgress(taskId, 10, "开始平台合并操作", 0, 100);
            
            StringBuilder sourcePlatformNames = new StringBuilder();
            for (Platform sourcePlatform : sourcePlatforms) {
                if (sourcePlatformNames.length() > 0) {
                    sourcePlatformNames.append(", ");
                }
                sourcePlatformNames.append(sourcePlatform.getName());
            }
            
            List<com.gamelist.model.Game> allGames = new ArrayList<>();
            for (Platform sourcePlatform : sourcePlatforms) {
                List<com.gamelist.model.Game> platformGames = gameMapper.selectGamesByPlatformId(sourcePlatform.getId());
                allGames.addAll(platformGames);
            }
            
            int totalGames = allGames.size();
            taskService.updateTaskProgress(taskId, 20, "获取源平台游戏完成，共 " + totalGames + " 个游戏", 0, totalGames);
            
            int addedCount = 0;
            int processedCount = 0;
            
            for (com.gamelist.model.Game sourceGame : allGames) {
                processedCount++;
                int progress = totalGames > 0 ? 20 + (processedCount * 80) / totalGames : 100;
                taskService.updateTaskProgress(taskId, progress, "处理游戏: " + sourceGame.getName(), processedCount, totalGames);
                
                logger.debug("添加到新平台，游戏名称: {}", sourceGame.getName());
                // 添加详细日志到任务
                taskService.updateTaskLog(taskId, "合并游戏: " + sourceGame.getName() + " (" + sourceGame.getPath() + ") 到新平台 " + newPlatform.getName());
                
                // 检查游戏是否已存在
                com.gamelist.model.Game existingGame = findExistingGame(newPlatform.getId(), sourceGame);
                if (existingGame != null) {
                    if (overwrite != null && overwrite) {
                        // 覆盖已存在的游戏
                        logger.debug("游戏已存在，执行覆盖操作: {}", sourceGame.getName());
                        taskService.updateTaskLog(taskId, "游戏已存在，执行覆盖操作: " + sourceGame.getName());
                        com.gamelist.model.Game updatedGame = createGameCopy(sourceGame, newPlatform.getId());
                        updatedGame.setId(existingGame.getId());
                        gameMapper.updateGame(updatedGame);
                        addedCount++;
                    } else {
                        // 跳过已存在的游戏
                        logger.debug("游戏已存在，跳过: {}", sourceGame.getName());
                        taskService.updateTaskLog(taskId, "游戏已存在，跳过: " + sourceGame.getName());
                    }
                } else {
                    // 添加新游戏
                    com.gamelist.model.Game newGame = createGameCopy(sourceGame, newPlatform.getId());
                    gameMapper.insertGame(newGame);
                    addedCount++;
                }
            }
            
            logger.info("平台合并完成");
            logger.info("添加游戏数量: {}", addedCount);
            
            String resultMessage = String.format("平台合并完成\n新平台: %s\n源平台: %s\n添加游戏: %d", 
                newPlatform.getName(), sourcePlatformNames.toString(), addedCount);
            
            taskService.completeTask(taskId, "平台合并完成", resultMessage);
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "平台合并失败";
            logger.error("平台合并失败: {}", errorMessage, e);
            taskService.failTask(taskId, "平台合并失败", errorMessage);
        }
    }
    
    /**
     * 查找新平台中已存在的匹配游戏
     * 优先使用gameId、crc32等唯一标识符，然后使用文件名和游戏名称
     */
    private com.gamelist.model.Game findExistingGame(Long platformId, com.gamelist.model.Game sourceGame) {
        // 1. 优先使用gameId匹配
        if (sourceGame.getGameId() != null) {
            List<com.gamelist.model.Game> gamesByGameId = gameMapper.selectGamesByGameIdAndPlatformId(sourceGame.getGameId(), platformId);
            if (!gamesByGameId.isEmpty()) {
                return gamesByGameId.get(0);
            }
        }
        
        // 2. 使用crc32匹配
        if (sourceGame.getCrc32() != null) {
            List<com.gamelist.model.Game> gamesByCrc32 = gameMapper.selectGamesByCrc32AndPlatformId(sourceGame.getCrc32(), platformId);
            if (!gamesByCrc32.isEmpty()) {
                return gamesByCrc32.get(0);
            }
        }
        
        // 3. 使用文件名匹配（精确匹配文件名，而不是LIKE）
        String fileName = extractFileName(sourceGame.getPath());
        if (fileName != null) {
            List<com.gamelist.model.Game> gamesByFileName = gameMapper.selectGamesByExactFileNameAndPlatformId(fileName, platformId);
            if (!gamesByFileName.isEmpty()) {
                return gamesByFileName.get(0);
            }
        }
        
        // 4. 使用游戏名称匹配（不区分大小写）
        if (sourceGame.getName() != null) {
            List<com.gamelist.model.Game> gamesByName = gameMapper.selectGamesByNameIgnoreCaseAndPlatformId(sourceGame.getName(), platformId);
            if (!gamesByName.isEmpty()) {
                return gamesByName.get(0);
            }
        }
        
        return null;
    }
    
    /**
     * 从次要游戏更新主要游戏的字段
     */
    private void updateGameFromSecondary(com.gamelist.model.Game primaryGame, com.gamelist.model.Game secondaryGame) {
        // 只更新非空字段
        if (secondaryGame.getName() != null) primaryGame.setName(secondaryGame.getName());
        if (secondaryGame.getDesc() != null) primaryGame.setDesc(secondaryGame.getDesc());
        if (secondaryGame.getTranslatedName() != null) primaryGame.setTranslatedName(secondaryGame.getTranslatedName());
        if (secondaryGame.getTranslatedDesc() != null) primaryGame.setTranslatedDesc(secondaryGame.getTranslatedDesc());
        if (secondaryGame.getImage() != null) primaryGame.setImage(secondaryGame.getImage());
        if (secondaryGame.getVideo() != null) primaryGame.setVideo(secondaryGame.getVideo());
        if (secondaryGame.getMarquee() != null) primaryGame.setMarquee(secondaryGame.getMarquee());
        if (secondaryGame.getThumbnail() != null) primaryGame.setThumbnail(secondaryGame.getThumbnail());
        if (secondaryGame.getManual() != null) primaryGame.setManual(secondaryGame.getManual());
        if (secondaryGame.getRating() != null) primaryGame.setRating(secondaryGame.getRating());
        if (secondaryGame.getReleasedate() != null) primaryGame.setReleasedate(secondaryGame.getReleasedate());
        if (secondaryGame.getDeveloper() != null) primaryGame.setDeveloper(secondaryGame.getDeveloper());
        if (secondaryGame.getPublisher() != null) primaryGame.setPublisher(secondaryGame.getPublisher());
        if (secondaryGame.getGenre() != null) primaryGame.setGenre(secondaryGame.getGenre());
        if (secondaryGame.getPlayers() != null) primaryGame.setPlayers(secondaryGame.getPlayers());
        if (secondaryGame.getCrc32() != null) primaryGame.setCrc32(secondaryGame.getCrc32());
        if (secondaryGame.getLang() != null) primaryGame.setLang(secondaryGame.getLang());
        if (secondaryGame.getGenreid() != null) primaryGame.setGenreid(secondaryGame.getGenreid());
        if (secondaryGame.getSortBy() != null) primaryGame.setSortBy(secondaryGame.getSortBy());
    }
    
    /**
     * 创建游戏副本，修改platformId
     */
    private com.gamelist.model.Game createGameCopy(com.gamelist.model.Game originalGame, Long newPlatformId) {
        com.gamelist.model.Game newGame = new com.gamelist.model.Game();
        newGame.setGameId(originalGame.getGameId());
        newGame.setSsGameId(originalGame.getSsGameId());
        newGame.setSource(originalGame.getSource());
        newGame.setPath(originalGame.getPath());
        newGame.setName(originalGame.getName());
        newGame.setDesc(originalGame.getDesc());
        newGame.setTranslatedName(originalGame.getTranslatedName());
        newGame.setTranslatedDesc(originalGame.getTranslatedDesc());
        newGame.setImage(originalGame.getImage());
        newGame.setVideo(originalGame.getVideo());
        newGame.setMarquee(originalGame.getMarquee());
        newGame.setThumbnail(originalGame.getThumbnail());
        newGame.setManual(originalGame.getManual());
        newGame.setRating(originalGame.getRating());
        newGame.setReleasedate(originalGame.getReleasedate());
        newGame.setDeveloper(originalGame.getDeveloper());
        newGame.setPublisher(originalGame.getPublisher());
        newGame.setGenre(originalGame.getGenre());
        newGame.setPlayers(originalGame.getPlayers());
        newGame.setCrc32(originalGame.getCrc32());
        newGame.setMd5(originalGame.getMd5());
        newGame.setLang(originalGame.getLang());
        newGame.setGenreid(originalGame.getGenreid());
        newGame.setHash(originalGame.getHash());
        newGame.setPlatformType(originalGame.getPlatformType());
        newGame.setPlatformId(newPlatformId);
        newGame.setSortBy(originalGame.getSortBy());
        newGame.setScraped(originalGame.getScraped());
        newGame.setEdited(originalGame.getEdited());
        newGame.setExists(originalGame.getExists());
        newGame.setAbsolutePath(originalGame.getAbsolutePath());
        newGame.setPlatformPath(originalGame.getPlatformPath());
        newGame.setMultiFile(originalGame.getMultiFile());
        newGame.setMultiFileContent(originalGame.getMultiFileContent());
        newGame.setCached(originalGame.getCached());
        newGame.setParentRom(originalGame.getParentRom());
        newGame.setDiscSerial(originalGame.getDiscSerial());
        newGame.setMatched(originalGame.getMatched());
        newGame.setMatchSuspect(originalGame.getMatchSuspect());
        newGame.setMatchMethod(originalGame.getMatchMethod());
        // 遗留字段
        newGame.setBoxFront(originalGame.getBoxFront());
        newGame.setBoxBack(originalGame.getBoxBack());
        newGame.setBoxSpine(originalGame.getBoxSpine());
        newGame.setBoxFull(originalGame.getBoxFull());
        newGame.setCartridge(originalGame.getCartridge());
        newGame.setLogo(originalGame.getLogo());
        newGame.setBezel(originalGame.getBezel());
        newGame.setPanel(originalGame.getPanel());
        newGame.setCabinetLeft(originalGame.getCabinetLeft());
        newGame.setCabinetRight(originalGame.getCabinetRight());
        newGame.setTile(originalGame.getTile());
        newGame.setBanner(originalGame.getBanner());
        newGame.setSteam(originalGame.getSteam());
        newGame.setPoster(originalGame.getPoster());
        newGame.setBackground(originalGame.getBackground());
        newGame.setMusic(originalGame.getMusic());
        newGame.setScreenshot(originalGame.getScreenshot());
        newGame.setTitlescreen(originalGame.getTitlescreen());
        newGame.setBox3D(originalGame.getBox3D());
        newGame.setSteamgrid(originalGame.getSteamgrid());
        newGame.setFanart(originalGame.getFanart());
        newGame.setBoxtexture(originalGame.getBoxtexture());
        newGame.setSupporttexture(originalGame.getSupporttexture());
        newGame.setVideonormalized(originalGame.getVideonormalized());
        newGame.setWheelcarbon(originalGame.getWheelcarbon());
        newGame.setWheelsteel(originalGame.getWheelsteel());
        newGame.setScreenmarqueesmall(originalGame.getScreenmarqueesmall());
        newGame.setBoxside(originalGame.getBoxside());
        newGame.setFigurine(originalGame.getFigurine());
        newGame.setPictoliste(originalGame.getPictoliste());
        newGame.setPictomonochrome(originalGame.getPictomonochrome());
        newGame.setPictomonochromesvg(originalGame.getPictomonochromesvg());
        newGame.setPictocouleur(originalGame.getPictocouleur());
        newGame.setWallpaper(originalGame.getWallpaper());
        // SS 标准媒体字段
        newGame.setBox2d(originalGame.getBox2d());
        newGame.setBox2dBack(originalGame.getBox2dBack());
        newGame.setBox2dSide(originalGame.getBox2dSide());
        newGame.setBoxTexture(originalGame.getBoxTexture());
        newGame.setBoxScan(originalGame.getBoxScan());
        newGame.setSupport2d(originalGame.getSupport2d());
        newGame.setSupportTexture(originalGame.getSupportTexture());
        newGame.setSupportScan(originalGame.getSupportScan());
        newGame.setWheel(originalGame.getWheel());
        newGame.setWheelHd(originalGame.getWheelHd());
        newGame.setWheelCarbon(originalGame.getWheelCarbon());
        newGame.setWheelSteel(originalGame.getWheelSteel());
        newGame.setScreenmarquee(originalGame.getScreenmarquee());
        newGame.setSs(originalGame.getSs());
        newGame.setSstitle(originalGame.getSstitle());
        newGame.setOverlay(originalGame.getOverlay());
        newGame.setVideoNormalized(originalGame.getVideoNormalized());
        newGame.setFlyer(originalGame.getFlyer());
        newGame.setManuel(originalGame.getManuel());
        newGame.setMaps(originalGame.getMaps());
        newGame.setBezel43(originalGame.getBezel43());
        newGame.setBezel43V(originalGame.getBezel43V());
        newGame.setBezel43Cocktail(originalGame.getBezel43Cocktail());
        newGame.setBezel169(originalGame.getBezel169());
        newGame.setBezel169V(originalGame.getBezel169V());
        newGame.setBezel169Cocktail(originalGame.getBezel169Cocktail());
        newGame.setMixrbv1(originalGame.getMixrbv1());
        newGame.setMixrbv2(originalGame.getMixrbv2());
        newGame.setThemehb(originalGame.getThemehb());
        newGame.setThemehs(originalGame.getThemehs());
        newGame.setSsdmd(originalGame.getSsdmd());
        newGame.setSsfronton169(originalGame.getSsfronton169());
        newGame.setSsfronton11(originalGame.getSsfronton11());
        newGame.setSsfronton43(originalGame.getSsfronton43());
        newGame.setSstable(originalGame.getSstable());
        newGame.setSstopper(originalGame.getSstopper());
        newGame.setVideodmd(originalGame.getVideodmd());
        newGame.setVideofronton43(originalGame.getVideofronton43());
        newGame.setVideofronton169(originalGame.getVideofronton169());
        newGame.setVideotable(originalGame.getVideotable());
        newGame.setVideotable4k(originalGame.getVideotable4k());
        newGame.setVideotopper(originalGame.getVideotopper());
        newGame.setWheelTarcisios(originalGame.getWheelTarcisios());
        return newGame;
    }
    
    /**
     * 从路径中提取文件名
     */
    private String extractFileName(String path) {
        if (path == null) return null;
        int lastSeparatorIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSeparatorIndex == -1) return path;
        return path.substring(lastSeparatorIndex + 1);
    }
    
    /**
     * 检查两个游戏对象是否需要更新
     * @param primaryGame 主要平台的游戏
     * @param secondaryGame 次要平台的游戏
     * @return 如果属性不同返回true，否则返回false
     */
    private boolean gamesNeedUpdate(com.gamelist.model.Game primaryGame, com.gamelist.model.Game secondaryGame) {
        // 比较所有相关属性
        if (!equalOrBothNull(primaryGame.getName(), secondaryGame.getName())) return true;
        if (!equalOrBothNull(primaryGame.getDesc(), secondaryGame.getDesc())) return true;
        if (!equalOrBothNull(primaryGame.getTranslatedName(), secondaryGame.getTranslatedName())) return true;
        if (!equalOrBothNull(primaryGame.getTranslatedDesc(), secondaryGame.getTranslatedDesc())) return true;
        if (!equalOrBothNull(primaryGame.getImage(), secondaryGame.getImage())) return true;
        if (!equalOrBothNull(primaryGame.getVideo(), secondaryGame.getVideo())) return true;
        if (!equalOrBothNull(primaryGame.getMarquee(), secondaryGame.getMarquee())) return true;
        if (!equalOrBothNull(primaryGame.getThumbnail(), secondaryGame.getThumbnail())) return true;
        if (!equalOrBothNull(primaryGame.getManual(), secondaryGame.getManual())) return true;
        if (!equalOrBothNull(primaryGame.getRating(), secondaryGame.getRating())) return true;
        if (!equalOrBothNull(primaryGame.getReleasedate(), secondaryGame.getReleasedate())) return true;
        if (!equalOrBothNull(primaryGame.getDeveloper(), secondaryGame.getDeveloper())) return true;
        if (!equalOrBothNull(primaryGame.getPublisher(), secondaryGame.getPublisher())) return true;
        if (!equalOrBothNull(primaryGame.getGenre(), secondaryGame.getGenre())) return true;
        if (!equalOrBothNull(primaryGame.getPlayers(), secondaryGame.getPlayers())) return true;
        if (!equalOrBothNull(primaryGame.getCrc32(), secondaryGame.getCrc32())) return true;
        if (!equalOrBothNull(primaryGame.getLang(), secondaryGame.getLang())) return true;
        if (!equalOrBothNull(primaryGame.getGenreid(), secondaryGame.getGenreid())) return true;
        if (!equalOrBothNull(primaryGame.getSortBy(), secondaryGame.getSortBy())) return true;
        // 所有属性都相同
        return false;
    }
    
    /**
     * 比较两个对象是否相等或都为null
     * @param obj1 第一个对象
     * @param obj2 第二个对象
     * @return 如果相等或都为null返回true，否则返回false
     */
    private boolean equalOrBothNull(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) return true;
        if (obj1 == null || obj2 == null) return false;
        return obj1.equals(obj2);
    }
    
    /**
     * 获取平台统计信息
     */
    @Override
    public Map<String, Object> getPlatformStatistics(Long platformId) {
        Map<String, Object> statistics = new HashMap<>();
        
        // 获取平台的所有游戏
        List<Game> games = gameMapper.selectGamesByPlatformId(platformId);
        
        // 统计刮削状态（4级分类）
        int totalGames = games.size();
        int scraped = 0;
        int originalData = 0;
        int poorQuality = 0;
        int rawRom = 0;
        
        // 用于文件名查重
        Map<String, List<Map<String, Object>>> fileNameMap = new HashMap<>();
        
        for (Game game : games) {
            // 统计刮削状态
            String category = getScrapeCategory(game);
            switch (category) {
                case "scraped": scraped++; break;
                case "originalData": originalData++; break;
                case "poorQuality": poorQuality++; break;
                case "rawRom": rawRom++; break;
            }
            
            // 提取文件名用于查重
            String fileName = extractFileName(game.getPath());
            if (fileName != null) {
                Map<String, Object> gameInfo = new HashMap<>();
                gameInfo.put("gameId", game.getId());
                gameInfo.put("gameName", game.getName());
                gameInfo.put("path", game.getPath());
                gameInfo.put("absolutePath", game.getAbsolutePath());
                gameInfo.put("fileName", fileName);
                
                if (fileNameMap.containsKey(fileName)) {
                    fileNameMap.get(fileName).add(gameInfo);
                } else {
                    List<Map<String, Object>> gameInfos = new ArrayList<>();
                    gameInfos.add(gameInfo);
                    fileNameMap.put(fileName, gameInfos);
                }
            }
        }
        
        // 找出重复文件
        List<Map<String, Object>> duplicateFiles = new ArrayList<>();
        int duplicateFilesCount = 0;
        
        for (Map.Entry<String, List<Map<String, Object>>> entry : fileNameMap.entrySet()) {
            List<Map<String, Object>> gameInfos = entry.getValue();
            if (gameInfos.size() > 1) {
                Map<String, Object> duplicateGroup = new HashMap<>();
                duplicateGroup.put("fileName", entry.getKey());
                duplicateGroup.put("files", gameInfos);
                duplicateFiles.add(duplicateGroup);
                duplicateFilesCount += gameInfos.size() - 1;
            }
        }
        
        // 构建统计结果
        statistics.put("totalGames", totalGames);
        statistics.put("scraped", scraped);
        statistics.put("originalData", originalData);
        statistics.put("poorQuality", poorQuality);
        statistics.put("rawRom", rawRom);
        statistics.put("duplicateFiles", duplicateFiles);
        statistics.put("duplicateFilesCount", duplicateFilesCount);
        
        return statistics;
    }
    
    /**
     * 获取游戏的刮削分类（4级）
     * scraped: 已刮削（scraped=true 且非低质量）
     * originalData: 原始数据（未刮削但有一定数据）
     * poorQuality: 低质量（已刮削但数据不完整）
     * rawRom: 原始ROM（完全没有数据）
     */
    private String getScrapeCategory(Game game) {
        boolean isScraped = Boolean.TRUE.equals(game.getScraped());
        
        // 判断是否为低质量：缺少关键媒体或描述信息
        boolean isPoorQuality = isPoorQualityGame(game);
        
        if (isScraped) {
            return isPoorQuality ? "poorQuality" : "scraped";
        } else {
            return hasOriginalData(game) ? "originalData" : "rawRom";
        }
    }
    
    /**
     * 判断已刮削的游戏是否为低质量
     * 缺少 box2d/ss/wheel 之一，或者描述+开发者+流派都为空
     */
    private boolean isPoorQualityGame(Game game) {
        boolean missingBox2d = game.getBox2d() == null || game.getBox2d().isEmpty();
        boolean missingSs = game.getSs() == null || game.getSs().isEmpty();
        boolean missingWheel = game.getWheel() == null || game.getWheel().isEmpty();
        
        boolean descEmpty = game.getDesc() == null || game.getDesc().isEmpty() || game.getDesc().length() <= 20;
        boolean devEmpty = game.getDeveloper() == null || game.getDeveloper().isEmpty() || "unknown".equals(game.getDeveloper());
        boolean genreEmpty = game.getGenre() == null || game.getGenre().isEmpty() || "unknown".equals(game.getGenre());
        
        return missingBox2d || missingSs || missingWheel || (descEmpty && devEmpty && genreEmpty);
    }
    
    /**
     * 判断未刮削的游戏是否有一定原始数据
     */
    private boolean hasOriginalData(Game game) {
        return hasField(game.getDeveloper()) && !"unknown".equals(game.getDeveloper()) ||
               hasField(game.getPublisher()) && !"unknown".equals(game.getPublisher()) ||
               hasField(game.getGenre()) && !"unknown".equals(game.getGenre()) ||
               hasField(game.getDesc()) && !"unknown".equals(game.getDesc()) && game.getDesc().length() > 10 ||
               hasField(game.getTranslatedDesc()) && game.getTranslatedDesc().length() > 10 ||
               hasField(game.getReleasedate()) ||
               hasField(game.getPlayers()) && !"1".equals(game.getPlayers()) ||
               game.getRating() != null ||
               hasField(game.getImage()) ||
               hasField(game.getBox2d()) ||
               hasField(game.getSs()) ||
               hasField(game.getWheel()) ||
               hasField(game.getVideo()) ||
               hasField(game.getMarquee()) ||
               hasField(game.getThumbnail()) ||
               hasField(game.getManual()) ||
               hasField(game.getSstitle()) ||
               hasField(game.getSteamgrid()) ||
               hasField(game.getFanart()) ||
               hasField(game.getBezel43()) ||
               hasField(game.getBezel169()) ||
               hasField(game.getMixrbv1()) ||
               hasField(game.getMixrbv2()) ||
               hasField(game.getFlyer()) ||
               hasField(game.getMaps()) ||
               hasField(game.getFigurine());
    }
    
    private boolean hasField(String value) {
        return value != null && !value.isEmpty();
    }
    
    @Override
    public Map<String, Object> scrapePlatform(Long platformId, Integer systemId, String region, List<String> mediaTypes) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Platform platform = platformMapper.selectPlatformById(platformId);
            if (platform == null) {
                throw new IllegalArgumentException("平台不存在: " + platformId);
            }
            
            Map<String, String> scraperSettings = scraperSettingsService.getSettings();
            String username = scraperSettings.get("username");
            String password = scraperSettings.get("password");
            
            String taskDescription = "刮削平台: " + platform.getName() + " (系统ID: " + systemId + ")";
            BackgroundTask task = taskService.createTask("SCRAPE", taskDescription);
            
            scrapePlatformAsync(task.getId(), platformId, systemId, region, mediaTypes, username, password);
            
            result.put("success", true);
            result.put("taskId", task.getId());
            result.put("message", "刮削任务已启动，请在任务管理页面查看进度");
            
            return result;
        } catch (Exception e) {
            logger.error("刮削平台失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("errorMessage", e.getMessage());
            return result;
        }
    }

    @Override
    public Map<String, Object> bindSystem(Long platformId, Integer systemId) {
        return bindSystem(platformId, systemId, null);
    }

    @Override
    public Map<String, Object> bindSystem(Long platformId, Integer systemId, String region) {
        Map<String, Object> result = new HashMap<>();

        try {
            Platform platform = platformMapper.selectPlatformById(platformId);
            if (platform == null) {
                throw new IllegalArgumentException("平台不存在: " + platformId);
            }
            if (systemId == null || systemId <= 0) {
                throw new IllegalArgumentException("系统 ID 无效");
            }
            ScraperSystem scraperSystem = scraperSystemService.getBySystemId(systemId);
            if (scraperSystem == null) {
                throw new IllegalArgumentException("刮削系统不存在: systemId=" + systemId);
            }

            // 先记录该系统媒体是否已刮削：updatePlatform 会同步触发 icon 下载并可能置位 media_scraped，
            // 必须在绑定前读取原始状态，否则未刮削的系统会被误判为已刮削而跳过全量媒体刮削
            boolean wasScraped = scraperSystemService.isMediaScraped(systemId);

            // 如果指定了区域，设置平台的 logoRegion
            if (region != null && !region.isEmpty()) {
                platform.setLogoRegion(region);
                logger.info("设置平台 logoRegion: platformId={}, region={}", platformId, region);
            }

            // 1. 绑定 systemId（updatePlatform 检测到 systemId 变更会自动下载系统 icon）
            platform.setSystemId(systemId);
            updatePlatform(platform);

            result.put("success", true);
            result.put("systemId", systemId);
            result.put("systemName", scraperSystem.getName());

            // 2. 该系统媒体未刮削 → 自动发起系统媒体刮削（SS 系统媒体文件，非游戏刮削）
            if (wasScraped) {
                result.put("scraped", true);
                result.put("scrapeStarted", false);
                result.put("message", "已绑定系统 " + scraperSystem.getName());
                return result;
            }

            result.put("scraped", false);
            Map<String, String> settings = scraperSettingsService.getSettings();
            String username = settings.get("username");
            String password = settings.get("password");
            if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
                result.put("scrapeStarted", false);
                result.put("message", "已绑定系统 " + scraperSystem.getName()
                        + "，但未配置 ScreenScraper 凭据，请稍后在系统管理页手动刮削系统媒体");
                return result;
            }

            String targetRegion = platform.getLogoRegion() != null ? platform.getLogoRegion() : "wor";
            Map<String, Object> scrapeResult = scraperSystemService.scrapeSystemAllMedia(systemId, targetRegion);
            result.put("scrapeStarted", Boolean.TRUE.equals(scrapeResult.get("success")));
            result.put("taskId", scrapeResult.get("taskId"));
            result.put("message", "已绑定系统 " + scraperSystem.getName() + "（区域: " + targetRegion + "），系统媒体刮削任务已启动");

            return result;
        } catch (Exception e) {
            logger.error("绑定系统失败: platformId={}, systemId={}, error={}", platformId, systemId, e.getMessage(), e);
            result.put("success", false);
            result.put("errorMessage", e.getMessage());
            return result;
        }
    }
    
    @Async
    private void scrapePlatformAsync(Long taskId, Long platformId, Integer systemId, String region, List<String> mediaTypes, String username, String password) {
        try {
            taskService.updateTaskProgress(taskId, 10, "开始刮削平台", 0, 100);
            
            Platform platform = platformMapper.selectPlatformById(platformId);
            
            String[] regions = region.split(",");
            int totalRegions = regions.length;
            int totalMediaTypes = mediaTypes.size();
            int totalTasks = totalRegions * totalMediaTypes;
            int completedTasks = 0;
            
            for (String r : regions) {
                for (String mediaType : mediaTypes) {
                    completedTasks++;
                    int progress = 10 + (completedTasks * 80) / totalTasks;
                    taskService.updateTaskProgress(taskId, progress, "刮削: " + mediaType + " (" + r + ")", completedTasks, totalTasks);
                    
                    Map<String, Object> mediaResult = screenScraperApiService.fetchSystemMedia(systemId, r, java.util.Collections.singletonList(mediaType), username, password);
                    
                    if ((Boolean) mediaResult.get("success")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> mediaUrls = (List<Map<String, String>>) mediaResult.get("mediaUrls");
                        
                        for (Map<String, String> mediaInfo : mediaUrls) {
                            String url = mediaInfo.get("url");
                            String type = mediaInfo.get("type");
                            String reg = mediaInfo.get("region");
                            
                            String filePath = downloadAndSaveMedia(url, systemId, reg, type);
                            if (filePath != null) {
                                taskService.updateTaskLog(taskId, "下载成功: " + type + "/" + reg + " -> " + filePath);
                            }
                        }
                    } else {
                        taskService.updateTaskLog(taskId, "获取媒体失败: " + mediaType + " (" + r + ") - " + mediaResult.get("message"));
                    }
                }
            }
            
            platform.setSystemId(systemId);
            platform.setSystemRegion(region);
            platformMapper.updatePlatform(platform);
            
            taskService.completeTask(taskId, "刮削完成", "平台 " + platform.getName() + " 刮削完成");
            
        } catch (Exception e) {
            logger.error("刮削平台失败: {}", e.getMessage(), e);
            taskService.failTask(taskId, "刮削失败", e.getMessage());
        }
    }
    
    private String downloadAndSaveMedia(String url, Integer systemId, String region, String mediaType) {
        try {
            java.io.File baseDir = new java.io.File("/data/scraper/system/" + systemId + "/" + region + "/" + mediaType);
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }
            
            // 根据 mediaType 和 region 生成合理的文件名
            String fileName = mediaType + ".png";
            java.io.File outputFile = new java.io.File(baseDir, fileName);
            
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            
            okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    // 检查响应内容类型，确定文件扩展名
                    String contentType = response.header("Content-Type", "");
                    if (contentType.contains("image/jpeg") || contentType.contains("image/jpg")) {
                        fileName = mediaType + ".jpg";
                        outputFile = new java.io.File(baseDir, fileName);
                    } else if (contentType.contains("video/mp4")) {
                        fileName = mediaType + ".mp4";
                        outputFile = new java.io.File(baseDir, fileName);
                    }
                    
                    try (java.io.InputStream is = response.body().byteStream();
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    return outputFile.getAbsolutePath();
                } else {
                    // 处理 NOMEDIA 响应（媒体不存在）
                    if (response.body() != null) {
                        String body = response.body().string().trim();
                        if ("NOMEDIA".equals(body)) {
                            logger.warn("Media not found for systemId={}, region={}, mediaType={}", systemId, region, mediaType);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("下载媒体文件失败: {}", e.getMessage());
        }
        return null;
    }

}
