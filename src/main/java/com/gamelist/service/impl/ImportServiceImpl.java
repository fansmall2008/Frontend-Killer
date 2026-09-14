package com.gamelist.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.gamelist.mapper.GameMapper;
import com.gamelist.mapper.PlatformMapper;
import com.gamelist.model.FilterResult;
import com.gamelist.model.Game;
import com.gamelist.model.ImportStatistics;
import com.gamelist.model.ImportTemplateV2;
import com.gamelist.model.MediaType;
import com.gamelist.model.Platform;
import com.gamelist.model.ScraperSystem;
import com.gamelist.service.ImportService;
import com.gamelist.service.PlatformService;
import com.gamelist.service.ScraperSystemService;
import com.gamelist.service.TaskService;
import com.gamelist.util.ErrorLogWriter;
import com.gamelist.util.GameFieldAccessor;
import com.gamelist.util.ImportMediaMatcher;
import com.gamelist.util.PegasusMetadataParser;
import com.gamelist.util.PathResolver;
import com.gamelist.util.TemplateExpressionEngine;
import com.gamelist.xml.GameListParser;
import com.gamelist.xml.GameListXml;

/**
 * 新版导入服务实现。
 * 使用 ImportTemplateV2 + GameFieldAccessor + ImportMediaMatcher + PathResolver 统一基础设施。
 */
@Service
public class ImportServiceImpl implements ImportService {

    private static final Logger logger = LoggerFactory.getLogger(ImportServiceImpl.class);

    @Autowired
    private GameMapper gameMapper;

    @Autowired
    private PlatformMapper platformMapper;

    @Autowired
    private PlatformService platformService;

    @Autowired
    private ScraperSystemService scraperSystemService;

    @Autowired
    private TaskService taskService;

    // ==================== 有数据文件导入 ====================

    @Override
    public ImportStatistics importWithDataFile(String filePath, String templateName,
                                                int threadCount, Long scraperSystemId) {
        ImportStatistics stats = new ImportStatistics();
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new RuntimeException("文件不存在: " + filePath);
            }

            // 加载 v2 模板
            ImportTemplateV2 template = null;
            if (templateName != null && !templateName.isEmpty()) {
                template = ImportTemplateV2.loadTemplate(templateName);
                if (template == null) {
                    logger.warn("无法加载 v2 模板: {}，将使用默认映射", templateName);
                } else {
                    List<String> invalidTypes = template.validateMediaTypes();
                    if (!invalidTypes.isEmpty()) {
                        logger.warn("模板中存在无效的媒体类型: {}", invalidTypes);
                    }
                }
            }

            // 根据文件格式分发
            String fileName = file.getName().toLowerCase();
            if (fileName.endsWith("gamelist.xml")) {
                importXmlFile(file, template, threadCount, scraperSystemId, stats);
            } else if (fileName.endsWith("metadata.pegasus.txt")) {
                importPegasusFile(file, template, threadCount, scraperSystemId, stats);
            } else if (fileName.endsWith(".lpl")) {
                // LPL 文件暂时回退到旧方式
                logger.warn("LPL 文件的 v2 导入尚未实现，请使用旧版导入方式");
            } else {
                throw new RuntimeException("不支持的文件格式: " + fileName);
            }
        } catch (Exception e) {
            logger.error("导入失败: {}", filePath, e);
            throw new RuntimeException("导入失败: " + e.getMessage(), e);
        }
        return stats;
    }

    // ==================== XML 导入 ====================

    private void importXmlFile(File file, ImportTemplateV2 template, int threadCount,
                                Long scraperSystemId, ImportStatistics stats) throws Exception {
        GameListXml gameListXml = GameListParser.parseGameList(file);
        if (gameListXml == null || gameListXml.getGame() == null) {
            logger.warn("XML 文件解析为空: {}", file);
            return;
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String folderName = file.getParentFile() != null ? file.getParentFile().getName() : "unknown";
        String platformName = "export_" + timestamp + "_gamelist_" + folderName;

        // 创建平台
        Platform platform = createPlatform(gameListXml.getProvider(), platformName, file.getAbsolutePath(), scraperSystemId);

        // 转换游戏数据
        List<Game> games = new ArrayList<>();
        for (GameListXml.GameXml gameXml : gameListXml.getGame()) {
            Game game = convertXmlToGame(gameXml, platform.getId(), file.getParentFile(), template);
            if (game != null) {
                games.add(game);
            }
        }

        // 批量导入
        if (!games.isEmpty()) {
            batchImport(games, platform, threadCount);
        }

        // 更新平台路径
        updatePlatformPath(platform.getId(), platform.getFolderPath());

        stats.incrementPlatforms();
        stats.addGames(games.size());
    }

    private Game convertXmlToGame(GameListXml.GameXml gameXml, Long platformId, File dataFileDir,
                                   ImportTemplateV2 template) {
        Game game = new Game();
        game.setPlatformId(platformId);

        // 获取所有原始字段
        Map<String, String> rawFields = new HashMap<>();
        if (gameXml.getName() != null) rawFields.put("name", gameXml.getName());
        if (gameXml.getDesc() != null) rawFields.put("desc", gameXml.getDesc());
        if (gameXml.getReleasedate() != null) rawFields.put("releasedate", gameXml.getReleasedate());
        if (gameXml.getDeveloper() != null) rawFields.put("developer", gameXml.getDeveloper());
        if (gameXml.getPublisher() != null) rawFields.put("publisher", gameXml.getPublisher());
        if (gameXml.getGenre() != null) rawFields.put("genre", gameXml.getGenre());
        if (gameXml.getPlayers() != null) rawFields.put("players", gameXml.getPlayers());
        if (gameXml.getRating() != null) rawFields.put("rating", String.valueOf(gameXml.getRating()));
        if (gameXml.getPath() != null) rawFields.put("path", gameXml.getPath());
        if (gameXml.getImage() != null) rawFields.put("image", gameXml.getImage());
        if (gameXml.getVideo() != null) rawFields.put("video", gameXml.getVideo());
        if (gameXml.getThumbnail() != null) rawFields.put("thumbnail", gameXml.getThumbnail());
        if (gameXml.getMarquee() != null) rawFields.put("marquee", gameXml.getMarquee());
        if (gameXml.getHash() != null) rawFields.put("hash", gameXml.getHash());

        if (template != null && template.getFieldMappings() != null) {
            // 使用 v2 模板的 fieldMappings（候选值支持表达式）
            for (Map.Entry<String, List<String>> mapping : template.getFieldMappings().entrySet()) {
                String dbColumn = mapping.getKey();
                List<String> candidates = mapping.getValue();
                String value = resolveValue(rawFields, candidates);
                if (value != null) {
                    GameFieldAccessor.setValue(game, dbColumn, value);
                }
            }
        } else {
            // 无模板时使用默认映射：直接用 dbColumn 名匹配
            for (Map.Entry<String, String> entry : rawFields.entrySet()) {
                GameFieldAccessor.setValue(game, entry.getKey(), entry.getValue());
            }
        }

        // 处理路径
        String rawPath = game.getPath();
        if (rawPath != null && dataFileDir != null) {
            String resolvedPath = PathResolver.resolveImportPath(rawPath, dataFileDir);
            game.setPath(rawPath);
            game.setAbsolutePath(resolvedPath);
        }

        // 处理媒体文件（通过模板的 mediaMappings，候选值支持表达式）
        if (template != null && template.getMediaMappings() != null && dataFileDir != null) {
            for (Map.Entry<String, List<String>> mediaMapping : template.getMediaMappings().entrySet()) {
                String nomcourt = mediaMapping.getKey();
                List<String> candidates = mediaMapping.getValue();
                String mediaPath = resolveValue(rawFields, candidates);
                if (mediaPath != null && !mediaPath.isEmpty()) {
                    String resolvedMediaPath = PathResolver.resolveImportPath(mediaPath, dataFileDir);
                    if (resolvedMediaPath != null) {
                        GameFieldAccessor.setValue(game, nomcourt, mediaPath);
                    }
                }
            }
        }

        game.setGameId(gameXml.getId());
        return game;
    }

    // ==================== Pegasus 导入 ====================

    private void importPegasusFile(File file, ImportTemplateV2 template, int threadCount,
                                    Long scraperSystemId, ImportStatistics stats) throws Exception {
        List<PegasusMetadataParser.GameCollection> collections = PegasusMetadataParser.parseMetadata(file);

        String timestamp = String.valueOf(System.currentTimeMillis());
        String folderName = file.getParentFile() != null ? file.getParentFile().getName() : "unknown";

        for (PegasusMetadataParser.GameCollection collection : collections) {
            String platformName = "export_" + timestamp + "_pegasus_" + folderName;

            Platform platform = new Platform();
            platform.setSystem(platformName);
            platform.setName(platformName);
            platform.setSortBy(collection.getSortBy());
            platform.setLaunch(collection.getLaunch());
            platform.setSoftware("WebGamelistOper");
            platform.setDatabase("Custom Database");
            platform.setWeb("http://localhost:8083");

            if (file.getParentFile() != null) {
                platform.setFolderPath(file.getParentFile().getAbsolutePath());
            }

            applyScraperSystem(platform, scraperSystemId);

            Platform existing = platformService.getPlatformBySystem(platformName);
            if (existing == null) {
                platformMapper.insertPlatform(platform);
            } else {
                platform.setId(existing.getId());
                platformMapper.updatePlatform(platform);
            }

            List<Game> games = new ArrayList<>();
            for (PegasusMetadataParser.Game pegasusGame : collection.getGames()) {
                Game game = convertPegasusToGame(pegasusGame, platform.getId(), file.getParentFile(), template);
                if (game != null) {
                    games.add(game);
                }
            }

            if (!games.isEmpty()) {
                batchImport(games, platform, threadCount);
            }

            updatePlatformPath(platform.getId(), platform.getFolderPath());
            stats.incrementPlatforms();
            stats.addGames(games.size());
        }
    }

    private Game convertPegasusToGame(PegasusMetadataParser.Game pegasusGame, Long platformId,
                                       File dataFileDir, ImportTemplateV2 template) {
        Game game = new Game();
        game.setPlatformId(platformId);

        // 收集原始字段
        Map<String, String> rawFields = new HashMap<>();
        if (pegasusGame.getName() != null) rawFields.put("name", pegasusGame.getName());
        if (pegasusGame.getDescription() != null) rawFields.put("description", pegasusGame.getDescription());
        if (pegasusGame.getReleaseDate() != null) rawFields.put("release", pegasusGame.getReleaseDate());
        if (pegasusGame.getDeveloper() != null) rawFields.put("developer", pegasusGame.getDeveloper());
        if (pegasusGame.getPublisher() != null) rawFields.put("publisher", pegasusGame.getPublisher());
        if (pegasusGame.getGenre() != null) rawFields.put("genre", pegasusGame.getGenre());
        if (pegasusGame.getPlayers() != null) rawFields.put("players", pegasusGame.getPlayers());
        if (pegasusGame.getRating() != null) rawFields.put("rating", pegasusGame.getRating());
        if (pegasusGame.getFiles() != null && !pegasusGame.getFiles().isEmpty()) {
            rawFields.put("files", String.join(",", pegasusGame.getFiles()));
        }
        // 媒体字段
        if (pegasusGame.getBoxFront() != null) rawFields.put("boxFront", pegasusGame.getBoxFront());
        if (pegasusGame.getVideo() != null) rawFields.put("video", pegasusGame.getVideo());
        if (pegasusGame.getScreenshot() != null) rawFields.put("screenshot", pegasusGame.getScreenshot());
        if (pegasusGame.getTitlescreen() != null) rawFields.put("titlescreen", pegasusGame.getTitlescreen());
        if (pegasusGame.getLogo() != null) rawFields.put("logo", pegasusGame.getLogo());

        if (template != null && template.getFieldMappings() != null) {
            for (Map.Entry<String, List<String>> mapping : template.getFieldMappings().entrySet()) {
                String dbColumn = mapping.getKey();
                List<String> candidates = mapping.getValue();
                String value = resolveValue(rawFields, candidates);
                if (value != null) {
                    GameFieldAccessor.setValue(game, dbColumn, value);
                }
            }
        } else {
            // 默认映射
            GameFieldAccessor.setValue(game, "name", rawFields.get("title"));
            GameFieldAccessor.setValue(game, "desc", rawFields.get("description"));
            GameFieldAccessor.setValue(game, "releasedate", rawFields.get("release"));
            GameFieldAccessor.setValue(game, "developer", rawFields.get("developer"));
            GameFieldAccessor.setValue(game, "publisher", rawFields.get("publisher"));
            GameFieldAccessor.setValue(game, "genre", rawFields.get("genre"));
            GameFieldAccessor.setValue(game, "players", rawFields.get("players"));
            GameFieldAccessor.setValue(game, "rating", rawFields.get("rating"));
            String files = rawFields.get("files");
            if (files != null) {
                game.setPath(files.split(",")[0].trim());
            }
        }

        // 处理路径
        String rawPath = game.getPath();
        if (rawPath != null && dataFileDir != null) {
            String resolvedPath = PathResolver.resolveImportPath(rawPath, dataFileDir);
            game.setAbsolutePath(resolvedPath);
        }

        // 处理媒体文件（候选值支持表达式）
        if (template != null && template.getMediaMappings() != null && dataFileDir != null) {
            for (Map.Entry<String, List<String>> mediaMapping : template.getMediaMappings().entrySet()) {
                String nomcourt = mediaMapping.getKey();
                List<String> candidates = mediaMapping.getValue();
                String mediaPath = resolveValue(rawFields, candidates);
                if (mediaPath != null && !mediaPath.isEmpty()) {
                    GameFieldAccessor.setValue(game, nomcourt, mediaPath);
                }
            }
        }

        return game;
    }

    // ==================== 无数据文件导入 ====================

    @Override
    public ImportStatistics importWithoutDataFile(String scanPath, String fileExtensions,
                                                    String templateName, int threadCount,
                                                    Long taskId, Long scraperSystemId) {
        ImportStatistics stats = new ImportStatistics();
        try {
            File scanDir = new File(scanPath);
            if (!scanDir.exists() || !scanDir.isDirectory()) {
                throw new RuntimeException("扫描路径不存在或不是目录: " + scanPath);
            }

            // 加载模板（可选）
            ImportTemplateV2 template = null;
            if (templateName != null && !templateName.isEmpty()) {
                template = ImportTemplateV2.loadTemplate(templateName);
            }

            // 解析扩展名
            Set<String> extensions = new HashSet<>();
            if (fileExtensions != null && !fileExtensions.isEmpty()) {
                for (String ext : fileExtensions.split(",")) {
                    extensions.add(ext.trim().toLowerCase());
                }
            }

            // 扫描 ROM 文件
            List<File> romFiles = new ArrayList<>();
            scanRomFiles(scanDir, extensions, romFiles);

            if (romFiles.isEmpty()) {
                logger.info("未找到任何 ROM 文件: {}", scanPath);
                return stats;
            }

            // 创建平台
            String timestamp = String.valueOf(System.currentTimeMillis());
            String platformName = "unknown_" + scanDir.getName() + "_" + timestamp;
            Platform platform = new Platform();
            platform.setSystem(platformName);
            platform.setName(platformName);
            platform.setSoftware("WebGamelistOper");
            platform.setFolderPath(scanDir.getAbsolutePath());
            applyScraperSystem(platform, scraperSystemId);
            platformMapper.insertPlatform(platform);

            // 创建游戏条目
            List<Game> games = new ArrayList<>();
            for (File romFile : romFiles) {
                Game game = createGameFromRom(romFile, platform.getId(), scanDir, template);
                if (game != null) {
                    games.add(game);
                }
            }

            // 批量导入
            if (!games.isEmpty()) {
                batchImport(games, platform, threadCount);
            }

            updatePlatformPath(platform.getId(), platform.getFolderPath());
            stats.incrementPlatforms();
            stats.addGames(games.size());

        } catch (Exception e) {
            logger.error("无数据文件导入失败: {}", scanPath, e);
            throw new RuntimeException("导入失败: " + e.getMessage(), e);
        }
        return stats;
    }

    private Game createGameFromRom(File romFile, Long platformId, File baseDir,
                                    ImportTemplateV2 template) {
        Game game = new Game();
        game.setPlatformId(platformId);

        String fileName = romFile.getName();
        String nameWithoutExt = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        game.setName(nameWithoutExt);
        game.setPath(fileName);
        game.setAbsolutePath(romFile.getAbsolutePath());

        // 使用默认媒体匹配策略
        File gameDir = romFile.getParentFile();
        if (gameDir != null && baseDir != null) {
            // 常用媒体类型
            String[] commonTypes = {"box-2D", "box-2D-back", "box-3D", "ss", "wheel", "video",
                                     "marquee", "fanart", "bezel-16-9", "sstitle"};
            for (String nomcourt : commonTypes) {
                String mediaPath = ImportMediaMatcher.matchDefault(baseDir, gameDir, nameWithoutExt, nomcourt);
                if (mediaPath != null) {
                    GameFieldAccessor.setValue(game, nomcourt, mediaPath);
                }
            }
        }

        return game;
    }

    private void scanRomFiles(File dir, Set<String> extensions, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isFile()) {
                if (extensions.isEmpty()) {
                    result.add(file);
                } else {
                    String ext = file.getName().contains(".")
                            ? file.getName().substring(file.getName().lastIndexOf('.') + 1).toLowerCase()
                            : "";
                    if (extensions.contains(ext)) {
                        result.add(file);
                    }
                }
            } else if (file.isDirectory()) {
                scanRomFiles(file, extensions, result);
            }
        }
    }

    // ==================== 公共工具方法 ====================

    private void batchImport(List<Game> games, Platform platform, int threadCount) {
        int batchSize = 1000;
        int actualThreads = threadCount > 1 ? Math.min(threadCount, 10) : 1;
        List<String> skippedPaths = new ArrayList<>();
        List<String> nullPathGames = new ArrayList<>();

        if (actualThreads > 1 && games.size() > batchSize) {
            int gamesPerThread = (games.size() + actualThreads - 1) / actualThreads;
            List<List<Game>> chunks = new ArrayList<>();
            for (int i = 0; i < actualThreads; i++) {
                int start = i * gamesPerThread;
                int end = Math.min(start + gamesPerThread, games.size());
                if (start < games.size()) chunks.add(games.subList(start, end));
            }

            ExecutorService executor = Executors.newFixedThreadPool(actualThreads);
            List<Future<Integer>> futures = new ArrayList<>();
            for (List<Game> chunk : chunks) {
                futures.add(executor.submit(() -> {
                    int inserted = 0;
                    for (int i = 0; i < chunk.size(); i += batchSize) {
                        List<Game> batch = chunk.subList(i, Math.min(i + batchSize, chunk.size()));
                        synchronized (ImportServiceImpl.class) {
                            FilterResult result = filterNewGames(batch, platform.getId());
                            skippedPaths.addAll(result.getSkippedPaths());
                            nullPathGames.addAll(result.getNullPathGames());
                            if (!result.getNewGames().isEmpty()) {
                                batchInsert(result.getNewGames());
                                inserted += result.getNewGames().size();
                            }
                        }
                        return inserted;
                    }
                    return inserted;
                }));
            }

            for (Future<Integer> f : futures) {
                try { f.get(); } catch (Exception e) { logger.error("多线程导入任务失败", e); }
            }
            executor.shutdown();
        } else {
            for (int i = 0; i < games.size(); i += batchSize) {
                List<Game> batch = games.subList(i, Math.min(i + batchSize, games.size()));
                FilterResult result = filterNewGames(batch, platform.getId());
                skippedPaths.addAll(result.getSkippedPaths());
                nullPathGames.addAll(result.getNullPathGames());
                if (!result.getNewGames().isEmpty()) {
                    batchInsert(result.getNewGames());
                }
            }
        }

        // 写入错误日志
        try {
            ErrorLogWriter.writeImportErrorLog(platform.getName(), skippedPaths, new ArrayList<>(), nullPathGames);
        } catch (IOException e) {
            logger.error("写入错误日志失败", e);
        }
    }

    private FilterResult filterNewGames(List<Game> games, Long platformId) {
        List<Game> newGames = new ArrayList<>();
        List<String> skippedPaths = new ArrayList<>();
        List<String> nullPathGames = new ArrayList<>();

        if (games.isEmpty()) return new FilterResult(newGames, skippedPaths, nullPathGames);

        Set<String> existingPaths = new HashSet<>();
        int queryBatchSize = 500;
        for (int i = 0; i < games.size(); i += queryBatchSize) {
            List<Game> queryBatch = games.subList(i, Math.min(i + queryBatchSize, games.size()));
            List<String> paths = new ArrayList<>();
            for (Game g : queryBatch) paths.add(g.getPath());

            if (platformId != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("platformId", platformId);
                params.put("paths", paths);
                existingPaths.addAll(gameMapper.selectExistingGamePathsByPlatformId(params));
            } else {
                existingPaths.addAll(gameMapper.selectExistingGamePaths(paths));
            }
        }

        for (Game game : games) {
            if (game.getPath() == null || game.getPath().isEmpty()) {
                nullPathGames.add(game.getName() != null ? game.getName() : "null");
                continue;
            }
            if (!existingPaths.contains(game.getPath())) {
                newGames.add(game);
            } else {
                skippedPaths.add(game.getPath());
            }
        }

        return new FilterResult(newGames, skippedPaths, nullPathGames);
    }

    private void batchInsert(List<Game> games) {
        if (games.isEmpty()) return;
        gameMapper.insertGamesBatch(games);
    }

    private Platform createPlatform(GameListXml.Provider provider, String platformName,
                                     String filePath, Long scraperSystemId) {
        Platform platform = new Platform();
        if (provider != null) {
            platform.setSystem(provider.getSystem());
            platform.setSoftware(provider.getSoftware());
            platform.setDatabase(provider.getDatabase());
            platform.setWeb(provider.getWeb());
        }
        platform.setName(platformName);

        File file = new File(filePath);
        if (file.getParentFile() != null) {
            platform.setFolderPath(file.getParentFile().getAbsolutePath());
        }

        applyScraperSystem(platform, scraperSystemId);

        Platform existing = platformService.getPlatformBySystem(platformName);
        if (existing == null) {
            platformMapper.insertPlatform(platform);
        } else {
            platform.setId(existing.getId());
            platformMapper.updatePlatform(platform);
        }
        return platform;
    }

    private void applyScraperSystem(Platform platform, Long scraperSystemId) {
        if (scraperSystemId == null) return;
        try {
            ScraperSystem ss = scraperSystemService.getById(scraperSystemId);
            if (ss != null && ss.getSystemId() != null) {
                platform.setSystemId(ss.getSystemId());
                try { scraperSystemService.scrapeSystemIcon(ss.getSystemId()); }
                catch (Exception e) { logger.warn("自动下载系统 icon 失败: {}", e.getMessage()); }
            }
        } catch (Exception e) {
            logger.warn("获取 scraper system 失败: {}", scraperSystemId, e);
        }
    }

    private void updatePlatformPath(Long platformId, String folderPath) {
        if (folderPath == null) return;
        gameMapper.updatePlatformPathForAllGames(platformId, folderPath);
    }

    /**
     * 解析候选值：支持表达式和直接字段匹配。
     * 候选值可以是：
     * - 普通字段名（如 "name"、"desc"）
     * - 表达式（如 "sub(releasedate, 0, 4)"、"name + '.jpg'"）
     * - 字符串字面量（如 '"Unknown"'）
     */
    private String resolveValue(Map<String, String> rawFields, List<String> candidates) {
        if (candidates == null) return null;
        for (String candidate : candidates) {
            // 检查是否是表达式
            if (TemplateExpressionEngine.isExpression(candidate)) {
                String value = TemplateExpressionEngine.evaluate(candidate, null, rawFields);
                if (value != null && !value.isEmpty()) return value;
            } else {
                // 普通字段名匹配
                String value = rawFields.get(candidate);
                if (value != null && !value.isEmpty()) return value;
                // 忽略大小写匹配
                for (Map.Entry<String, String> entry : rawFields.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(candidate) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                        return entry.getValue();
                    }
                }
            }
        }
        return null;
    }

    private String findFirstMatch(Map<String, String> rawFields, List<String> candidates) {
        if (candidates == null) return null;
        for (String candidate : candidates) {
            String value = rawFields.get(candidate);
            if (value != null && !value.isEmpty()) return value;
            // 忽略大小写匹配
            for (Map.Entry<String, String> entry : rawFields.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(candidate) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }
}
