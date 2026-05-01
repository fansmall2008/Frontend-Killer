package com.gamelist.service.impl;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gamelist.mapper.GameMapper;
import com.gamelist.mapper.PlatformMapper;
import com.gamelist.model.FilterResult;
import com.gamelist.model.Game;
import com.gamelist.model.ImportStatistics;
import com.gamelist.model.Platform;
import com.gamelist.model.PlatformStatistics;
import com.gamelist.model.ScanResult;
import com.gamelist.model.Statistics;
import com.gamelist.service.GameService;
import com.gamelist.service.PlatformService;
import com.gamelist.util.ErrorLogWriter;
import com.gamelist.util.LanguageDetector;
import com.gamelist.util.MediaFileFinder;
import com.gamelist.util.PegasusMetadataParser;
import com.gamelist.xml.GameListParser;
import com.gamelist.xml.GameListXml;

@Service
public class GameServiceImpl implements GameService {
    
    private static final Logger logger = LoggerFactory.getLogger(GameServiceImpl.class);
    
    @Autowired
    private GameMapper gameMapper;
    
    @Autowired
    private PlatformMapper platformMapper;
    
    @Autowired
    private PlatformService platformService;
    
    @Value("${app.import.templates.path:#{T(com.gamelist.util.PathUtil).getRulesPath() + '/import'}}")
    private String importTemplatesPath;
    
    @javax.annotation.PostConstruct
    public void init() {
        ImportTemplate.setTemplatesPath(importTemplatesPath);
        logger.info("Import templates path set to: {}", importTemplatesPath);
    }
    
    /**
     * 获取默认平台ID，如果不存在则创建
     * @return 默认平台ID
     */
    private Long getDefaultPlatformId() {
        // 检查是否存在平台
        List<Platform> platforms = platformService.getAllPlatforms();
        if (!platforms.isEmpty()) {
            // 返回第一个平台的ID
            return platforms.get(0).getId();
        }
        
        // 创建默认平台
        Platform defaultPlatform = new Platform();
        defaultPlatform.setSystem("Default");
        defaultPlatform.setName("Default"); // 设置name字段
        defaultPlatform.setSoftware("Default");
        defaultPlatform.setDatabase("Default");
        defaultPlatform.setWeb("Default");
        platformMapper.insertPlatform(defaultPlatform);
        
        // 查询新创建的平台ID
        platforms = platformService.getAllPlatforms();
        return platforms.isEmpty() ? null : platforms.get(0).getId();
    }

    @Override
    public void importGamesFromXml(GameListXml gameListXml) {
        // 默认实现，使用"Unknown"作为平台名
        importGamesFromXml(gameListXml, "Unknown");
    }
    
    /**
     * 从指定文件路径导入游戏列表，当provider为null时使用文件夹名作为平台名
     */
    public void importGamesFromXml(GameListXml gameListXml, String defaultPlatformName) {
        importGamesFromXml(gameListXml, defaultPlatformName, null);
    }
    
    /**
     * 从指定文件路径导入游戏列表，当provider为null时使用文件夹名作为平台名
     * @param gameListFilePath gamelist.xml文件的绝对路径，用于将相对路径转换为绝对路径
     */
    public void importGamesFromXml(GameListXml gameListXml, String defaultPlatformName, String gameListFilePath) {
        // 添加空指针检查
        if (gameListXml == null) {
            logger.error("导入失败: gameListXml为null");
            throw new RuntimeException("导入失败: gameListXml为null");
        }
        
        logger.info("开始保存平台信息，defaultPlatformName: {}", defaultPlatformName);
        logger.info("gameListXml.getProvider(): {}", gameListXml.getProvider() != null ? gameListXml.getProvider() : "null");
        if (gameListXml.getProvider() != null) {
            logger.info("gameListXml.getProvider().getSystem(): {}", gameListXml.getProvider().getSystem());
        }
        
        Platform platform = null;
        try {
            // 保存平台信息
            platform = platformService.savePlatform(gameListXml.getProvider(), defaultPlatformName);
            logger.info("平台保存成功，平台ID: {}", platform != null ? platform.getId() : "null");
        } catch (Exception e) {
            logger.error("平台保存失败: {}", e.getMessage(), e);
            throw new RuntimeException("平台保存失败: " + e.getMessage(), e);
        }
        
        // 检查platform是否为null
        if (platform == null) {
            logger.error("导入失败: 平台保存后返回null");
            throw new RuntimeException("导入失败: 平台保存后返回null");
        }
        
        // 转换游戏数据并保存
        List<Game> games = new ArrayList<>();
        int totalGamesInXml = gameListXml.getGame() != null ? gameListXml.getGame().size() : 0;
        int successfullyConverted = 0;
        int failedConversions = 0;
        List<String> failedGameNames = new ArrayList<>();
        
        logger.info("========== 开始转换游戏数据 ==========");
        logger.info("XML中游戏总数: {}", totalGamesInXml);
        
        // 添加空指针检查
        if (gameListXml.getGame() != null) {
            int gameIndex = 0;
            List<GameListXml.GameXml> gameXmlList = gameListXml.getGame();
            
            // 记录第一个和最后一个游戏
            if (gameXmlList.size() > 0) {
                GameListXml.GameXml firstGame = gameXmlList.get(0);
                GameListXml.GameXml lastGame = gameXmlList.get(gameXmlList.size() - 1);
                logger.info("第一个游戏: name={}, path={}", 
                    firstGame.getName() != null ? firstGame.getName() : "null",
                    firstGame.getPath() != null ? firstGame.getPath() : "null");
                logger.info("最后一个游戏: name={}, path={}", 
                    lastGame.getName() != null ? lastGame.getName() : "null",
                    lastGame.getPath() != null ? lastGame.getPath() : "null");
            }
            
            for (GameListXml.GameXml gameXml : gameXmlList) {
                gameIndex++;
                String gameName = gameXml.getName() != null ? gameXml.getName() : "null";
                String gamePath = gameXml.getPath() != null ? gameXml.getPath() : "null";
                logger.info("处理游戏 #{}/{}: name={}, path={}", gameIndex, totalGamesInXml, gameName, gamePath);
                
                try {
                    Game game = convertToGameModel(gameXml, platform.getId(), gameListFilePath, "generic", false, platform.getFolderPath());
                    games.add(game);
                    successfullyConverted++;
                    logger.debug("游戏转换成功: name={}, gameId={}", gameName, game.getGameId());
                } catch (Exception e) {
                    failedConversions++;
                    failedGameNames.add(gameName);
                    logger.error("转换游戏失败 (#{}/{}): name={}, path={}, 错误: {}", gameIndex, totalGamesInXml, gameName, gamePath, e.getMessage());
                }
            }
            logger.info("游戏转换完成 - 总数: {}, 成功: {}, 失败: {}", totalGamesInXml, successfullyConverted, failedConversions);
            
            // 如果有失败的游戏，记录详细信息
            if (failedConversions > 0) {
                logger.info("失败的游戏列表: {}", failedGameNames);
            }
        } else {
            logger.info("游戏列表为空");
        }
        
        logger.info("转换后游戏列表大小: {}", games.size());
        logger.info("========== 游戏数据转换结束 ==========");
        
        // 记录导入统计信息
        logger.info("========== 导入统计 ==========");
        logger.info("XML文件中的游戏总数: {}", totalGamesInXml);
        logger.info("转换成功的游戏数: {}", successfullyConverted);
        logger.info("转换失败的游戏数: {}", failedConversions);
        logger.info("准备导入的游戏数: {}", games.size());
        
        // 批量保存游戏前检查路径是否已存在
        if (!games.isEmpty()) {
            // 优化：分批处理和批量插入
            importGamesInBatches(games, platform, 1, totalGamesInXml);
        } else if (totalGamesInXml > 0) {
            logger.warn("警告：XML中有 {} 个游戏，但转换后列表为空！", totalGamesInXml);
        }
        
        logger.info("========== 导入统计结束 ==========");
    }
    
    /**
     * 分批处理游戏数据，避免一次性加载所有数据到内存
     * @param allGames 所有游戏数据
     * @param platform 平台信息
     */
    private void importGamesInBatches(List<Game> allGames, Platform platform) {
        importGamesInBatches(allGames, platform, 1, allGames.size());
    }

    /**
     * 分批处理游戏数据，避免一次性加载所有数据到内存，支持多线程
     * @param allGames 所有游戏数据
     * @param platform 平台信息
     * @param threadCount 线程数
     */
    private void importGamesInBatches(List<Game> allGames, Platform platform, int threadCount) {
        importGamesInBatches(allGames, platform, threadCount, allGames.size());
    }

    /**
     * 分批处理游戏数据，避免一次性加载所有数据到内存，支持多线程
     * @param allGames 所有游戏数据
     * @param platform 平台信息
     * @param threadCount 线程数
     * @param totalGamesInXml XML文件中的游戏总数（用于统计对比）
     */
    private void importGamesInBatches(List<Game> allGames, Platform platform, int threadCount, int totalGamesInXml) {
        int batchSize = 1000; // 每批处理1000个游戏
        int actualThreads = threadCount > 1 ? Math.min(threadCount, 10) : 1;
        
        // 收集错误信息用于写入日志
        List<String> allSkippedPaths = Collections.synchronizedList(new ArrayList<>());
        List<String> allNullPathGames = Collections.synchronizedList(new ArrayList<>());

        if (actualThreads > 1 && allGames.size() > batchSize) {
            // 多线程处理
            int gamesPerThread = (allGames.size() + actualThreads - 1) / actualThreads;
            List<List<Game>> gameChunks = new ArrayList<>();

            for (int i = 0; i < actualThreads; i++) {
                int start = i * gamesPerThread;
                int end = Math.min(start + gamesPerThread, allGames.size());
                if (start < allGames.size()) {
                    gameChunks.add(allGames.subList(start, end));
                }
            }

            // 使用线程池并行处理
            ExecutorService executor = Executors.newFixedThreadPool(actualThreads);
            List<Future<Integer>> futures = new ArrayList<>();

            for (List<Game> chunk : gameChunks) {
                futures.add(executor.submit(() -> {
                    int inserted = 0;
                    for (int i = 0; i < chunk.size(); i += batchSize) {
                        int end = Math.min(i + batchSize, chunk.size());
                        List<Game> batchGames = chunk.subList(i, end);
                        
                        // 将过滤和插入作为原子操作，避免竞态条件
                        synchronized (GameServiceImpl.class) {
                            FilterResult result = filterNewGames(batchGames, platform.getId());
                            // 收集跳过的游戏信息
                            allSkippedPaths.addAll(result.getSkippedPaths());
                            allNullPathGames.addAll(result.getNullPathGames());
                            
                            List<Game> newGames = result.getNewGames();
                            if (!newGames.isEmpty()) {
                                batchInsertGames(newGames);
                                inserted += newGames.size();
                            }
                        }
                    }
                    return inserted;
                }));
            }

            // 等待所有任务完成并统计结果
            int totalInsertedCount = 0;
            int failedTasks = 0;
            
            for (Future<Integer> future : futures) {
                try {
                    totalInsertedCount += future.get();
                } catch (Exception e) {
                    failedTasks++;
                    logger.error("多线程导入任务失败", e);
                }
            }

            // 等待线程池关闭，确保所有任务真正完成
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.MINUTES)) {
                    logger.warn("线程池未能在5分钟内关闭，强制关闭");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.error("等待线程池关闭被中断", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            // 对比统计
            int missingCount = totalGamesInXml - totalInsertedCount;
            logger.info("多线程导入完成，共插入 {} 个游戏，失败任务数: {}", totalInsertedCount, failedTasks);
            logger.info("【统计对比】XML游戏总数: {}, 实际插入: {}, 差异: {}", totalGamesInXml, totalInsertedCount, missingCount);
            
            if (missingCount != 0) {
                logger.warn("【注意】存在 {} 个游戏未被导入！", missingCount);
            }
        } else {
            // 单线程处理
            int totalInserted = 0;
            logger.info("开始单线程导入，共 {} 个游戏", allGames.size());
            
            for (int i = 0; i < allGames.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allGames.size());
                List<Game> batchGames = allGames.subList(i, end);

                // 过滤出新游戏（使用数据库批量查询）
                FilterResult result = filterNewGames(batchGames, platform.getId());
                
                // 收集跳过的游戏信息
                allSkippedPaths.addAll(result.getSkippedPaths());
                allNullPathGames.addAll(result.getNullPathGames());

                // 批量插入新游戏
                List<Game> newGames = result.getNewGames();
                if (!newGames.isEmpty()) {
                    batchInsertGames(newGames);
                    totalInserted += newGames.size();
                    logger.info("批次 {}-{}: 过滤后 {} 个新游戏，已插入", i, end, newGames.size());
                } else {
                    logger.info("批次 {}-{}: 没有新游戏需要插入", i, end);
                }
            }
            
            // 对比统计
            int missingCount = totalGamesInXml - totalInserted;
            logger.info("单线程导入完成，共插入 {} 个游戏", totalInserted);
            logger.info("【统计对比】XML游戏总数: {}, 实际插入: {}, 差异: {}", totalGamesInXml, totalInserted, missingCount);
            
            if (missingCount != 0) {
                logger.warn("【注意】存在 {} 个游戏未被导入！", missingCount);
            }
        }
        
        // 写入错误日志文件
        try {
            ErrorLogWriter.writeImportErrorLog(platform.getName(), allSkippedPaths, new ArrayList<>(), allNullPathGames);
            if (!allSkippedPaths.isEmpty() || !allNullPathGames.isEmpty()) {
                logger.info("错误日志已写入: logs/errolog-import-{}-{}.log", 
                        platform.getName().replaceAll("[^a-zA-Z0-9\\-_]", "_"), 
                        java.time.LocalDate.now());
            }
        } catch (IOException e) {
            logger.error("写入错误日志失败", e);
        }
    }
    
    /**
     * 过滤出新游戏，使用数据库批量查询
     * @param games 游戏数据列表
     * @return 过滤结果（包含新游戏和跳过的游戏信息）
     */
    private FilterResult filterNewGames(List<Game> games) {
        return filterNewGames(games, null);
    }
    
    /**
     * 过滤出新游戏，使用数据库批量查询（支持平台ID）
     * @param games 游戏数据列表
     * @param platformId 平台ID（可选，为null时全局查询）
     * @return 过滤结果（包含新游戏和跳过的游戏信息）
     */
    private FilterResult filterNewGames(List<Game> games, Long platformId) {
        List<Game> newGames = new ArrayList<>();
        List<String> skippedPaths = new ArrayList<>();
        List<String> nullPathGames = new ArrayList<>();
        
        // 批量查询已存在的路径
        if (!games.isEmpty()) {
            // 每批查询500个路径
            int queryBatchSize = 500;
            Set<String> existingPaths = new HashSet<>();
            
            for (int i = 0; i < games.size(); i += queryBatchSize) {
                int end = Math.min(i + queryBatchSize, games.size());
                List<Game> queryBatch = games.subList(i, end);
                
                // 提取路径列表
                List<String> paths = new ArrayList<>();
                for (Game game : queryBatch) {
                    paths.add(game.getPath());
                }
                
                // 批量查询已存在的路径（考虑平台ID）
                List<String> existingBatchPaths;
                if (platformId != null) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("platformId", platformId);
                    params.put("paths", paths);
                    existingBatchPaths = gameMapper.selectExistingGamePathsByPlatformId(params);
                    logger.debug("查询批次 {}-{}, 平台ID: {}, 发现已存在 {} 个路径", i, end, platformId, existingBatchPaths.size());
                } else {
                    existingBatchPaths = gameMapper.selectExistingGamePaths(paths);
                    logger.debug("查询批次 {}-{}, 全局查询，发现已存在 {} 个路径", i, end, existingBatchPaths.size());
                }
                existingPaths.addAll(existingBatchPaths);
                
                // 记录查询到的已存在路径
                if (!existingBatchPaths.isEmpty()) {
                    logger.debug("已存在的路径列表: {}", existingBatchPaths);
                }
            }
            
            // 过滤出新游戏
            int skippedCount = 0;
            int nullPathCount = 0;
            
            for (Game game : games) {
                String gamePath = game.getPath();
                String gameName = game.getName() != null ? game.getName() : "null";
                
                // 检查路径是否为空
                if (gamePath == null || gamePath.isEmpty()) {
                    nullPathCount++;
                    nullPathGames.add(gameName);
                    logger.warn("游戏路径为空，跳过该游戏: name={}, gameId={}", gameName, game.getGameId());
                    skippedCount++;
                    continue;
                }
                
                boolean exists = existingPaths.contains(gamePath);
                
                if (!exists) {
                    newGames.add(game);
                } else {
                    skippedCount++;
                    skippedPaths.add(gamePath);
                    logger.info("跳过已存在的游戏: path={}, name={}, gameId={}", 
                            gamePath, gameName, game.getGameId());
                }
            }
            
            // 如果有路径为空的游戏，记录详细信息
            if (nullPathCount > 0) {
                logger.warn("共有 {} 个游戏路径为空被跳过，游戏名称: {}", nullPathCount, nullPathGames);
            }
            
            logger.info("filterNewGames - 总游戏数: {}, 新增游戏数: {}, 跳过游戏数: {}", 
                    games.size(), newGames.size(), skippedCount);
            
            // 如果有跳过的游戏，记录完整列表
            if (skippedCount > 0 && skippedCount < 10) {
                logger.info("跳过的路径列表: {}", skippedPaths);
            } else if (skippedCount >= 10) {
                logger.info("跳过的路径数量较多，仅显示前10个: {}", skippedPaths.subList(0, 10));
            }
        }
        
        return new FilterResult(newGames, skippedPaths, nullPathGames);
    }
    
    /**
     * 批量插入游戏数据，进一步分批以避免内存溢出
     * @param games 游戏数据列表
     */
    private void batchInsertGames(List<Game> games) {
        int insertBatchSize = 500; // 每批插入500个游戏，提高速度
        int totalInserted = 0;
        
        logger.info("开始批量插入 {} 个游戏", games.size());
        
        for (int i = 0; i < games.size(); i += insertBatchSize) {
            int end = Math.min(i + insertBatchSize, games.size());
            List<Game> batch = games.subList(i, end);
            int batchSize = batch.size();
            
            logger.debug("插入批次 {}-{}, 数量: {}", i, end, batchSize);
            
            try {
                long startTime = System.currentTimeMillis();
                gameMapper.insertGamesBatch(batch);
                long endTime = System.currentTimeMillis();
                
                totalInserted += batchSize;
                logger.info("批次 {}-{} 插入成功，耗时: {}ms", i, end, endTime - startTime);
                
                // 每批插入后短暂休眠，给数据库时间处理其他请求
                if (i + insertBatchSize < games.size()) {
                    Thread.sleep(10); // 休眠10毫秒，减少等待时间
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.error("插入过程被中断，当前批次: {}-{}", i, end, ie);
                String errorMessage = ie.getMessage() != null ? ie.getMessage() : "未知错误";
                throw new RuntimeException("插入过程被中断: " + errorMessage, ie);
            } catch (Exception ex) {
                logger.error("批量保存游戏失败，当前批次: {}-{}", i, end, ex);
                String errorMessage = ex.getMessage() != null ? ex.getMessage() : "未知错误";
                throw new RuntimeException("批量保存游戏失败: " + errorMessage, ex);
            }
        }
        
        logger.info("批量插入完成，共插入 {} 个游戏", totalInserted);
    }
    
    @Override
    public ScanResult scanAndImportGames(String rootPath) {
        return scanAndImportGames(rootPath, -1);
    }
    
    /**
     * 扫描指定路径及其子目录下的gamelist.xml文件并导入，支持指定扫描深度
     * @param rootPath 根路径
     * @param scanDepth 扫描深度，-1表示递归所有子目录
     * @return 扫描结果
     */
    public ScanResult scanAndImportGames(String rootPath, int scanDepth) {
        ScanResult result = new ScanResult();
        List<ScanResult.Detail> details = new ArrayList<>();
        
        File rootDir = new File(rootPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            result.setSuccess(false);
            result.setMessage("指定的路径不存在或不是目录");
            result.setFoundFiles(0);
            result.setImportedFiles(0);
            return result;
        }
        
        // 扫描所有gamelist.xml文件（根据指定深度扫描）
        List<File> gameListFiles = scanGameListFiles(rootDir, scanDepth);
        
        result.setFoundFiles(gameListFiles.size());
        
        int importedCount = 0;
        for (File file : gameListFiles) {
            ScanResult.Detail detail = new ScanResult.Detail();
            detail.setFilePath(file.getAbsolutePath());
            
            try {
                // 解析XML文件并检测平台类型
                detail.setMessage("正在解析XML文件...");
                GameListParser.PlatformSpecificGameList platformSpecificResult = GameListParser.parseGameListWithPlatformDetection(file);
                GameListXml gameListXml = platformSpecificResult.getGameListXml();
                String platformType = platformSpecificResult.getPlatformType();
                
                // 从文件路径中提取文件夹名作为默认平台名
                String parentDirName = file.getParentFile().getName();
                detail.setMessage("正在保存平台信息...");
                
                // 导入游戏数据
                importGamesFromXml(gameListXml, parentDirName, file.getAbsolutePath(), platformType);
                
                detail.setSuccess(true);
                detail.setMessage("导入成功");
                importedCount++;
            } catch (Exception e) {
                detail.setSuccess(false);
                String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
                detail.setMessage("导入失败: " + errorMsg);
                logger.error("文件导入失败: {}", file.getAbsolutePath(), e);
            }
            
            details.add(detail);
        }
        
        result.setImportedFiles(importedCount);
        result.setDetails(details);
        result.setSuccess(true);
        result.setMessage("扫描完成");
        
        return result;
    }
    
    /**
     * 扫描指定目录及其子目录下的gamelist.xml文件（可配置深度）
     * @param directory 根目录
     * @param maxDepth 最大扫描深度，-1表示递归扫描所有子目录
     */
    private List<File> scanGameListFiles(File directory, int maxDepth) {
        List<File> gameListFiles = Collections.synchronizedList(new ArrayList<>());
        scanFilesRecursive(directory, gameListFiles, 0, maxDepth, "gamelist.xml");
        return gameListFiles;
    }
    
    /**
     * 扫描指定目录及其子目录下的metadata.pegasus.txt文件（可配置深度）
     * @param directory 根目录
     * @param maxDepth 最大扫描深度，-1表示递归扫描所有子目录
     */
    private List<File> scanPegasusMetadataFiles(File directory, int maxDepth) {
        List<File> metadataFiles = Collections.synchronizedList(new ArrayList<>());
        scanFilesRecursive(directory, metadataFiles, 0, maxDepth, "metadata.pegasus.txt");
        return metadataFiles;
    }
    
    /**
     * 通用文件扫描方法（使用并行流优化性能）
     * @param directory 根目录
     * @param resultFiles 结果文件列表
     * @param currentDepth 当前深度
     * @param maxDepth 最大扫描深度，-1表示递归扫描所有子目录
     * @param fileName 目标文件名
     */
    private void scanFilesRecursive(File directory, List<File> resultFiles, int currentDepth, int maxDepth, String fileName) {
        // 检查深度限制
        if (maxDepth != -1 && currentDepth > maxDepth) {
            return;
        }
        
        // 检查当前目录下是否有目标文件
        File targetFile = new File(directory, fileName);
        if (targetFile.exists() && targetFile.isFile()) {
            resultFiles.add(targetFile);
        }
        
        // 遍历子目录并使用并行流提高性能
        File[] subDirectories = directory.listFiles(File::isDirectory);
        if (subDirectories != null && subDirectories.length > 0) {
            // 对于子目录扫描，使用并行流处理
            Arrays.stream(subDirectories).parallel().forEach(subDir -> {
                scanFilesRecursive(subDir, resultFiles, currentDepth + 1, maxDepth, fileName);
            });
        }
    }
    
    /**
     * 同时扫描gamelist.xml和metadata.pegasus.txt文件（使用并行流优化性能）
     * @param directory 根目录
     * @param maxDepth 最大扫描深度，-1表示递归扫描所有子目录
     * @return 包含所有找到的文件的映射，键为文件名，值为文件列表
     */
    private Map<String, List<File>> scanBothFileTypes(File directory, int maxDepth) {
        Map<String, List<File>> resultMap = Collections.synchronizedMap(new java.util.HashMap<>());
        resultMap.put("gamelist.xml", Collections.synchronizedList(new ArrayList<>()));
        resultMap.put("metadata.pegasus.txt", Collections.synchronizedList(new ArrayList<>()));
        
        scanBothFileTypesRecursive(directory, resultMap, 0, maxDepth);
        return resultMap;
    }
    
    /**
     * 递归扫描目录，同时检查两种文件类型
     */
    private void scanBothFileTypesRecursive(File directory, Map<String, List<File>> resultMap, int currentDepth, int maxDepth) {
        // 检查深度限制
        if (maxDepth != -1 && currentDepth > maxDepth) {
            return;
        }
        
        // 同时检查两种文件类型
        File gamelistFile = new File(directory, "gamelist.xml");
        if (gamelistFile.exists() && gamelistFile.isFile()) {
            resultMap.get("gamelist.xml").add(gamelistFile);
            logger.info("找到目标文件: {}", gamelistFile.getAbsolutePath());
        }
        
        File metadataFile = new File(directory, "metadata.pegasus.txt");
        if (metadataFile.exists() && metadataFile.isFile()) {
            resultMap.get("metadata.pegasus.txt").add(metadataFile);
            logger.info("找到目标文件: {}", metadataFile.getAbsolutePath());
        }
        
        // 遍历子目录并使用并行流提高性能
        File[] subDirectories = directory.listFiles(File::isDirectory);
        if (subDirectories != null && subDirectories.length > 0) {
            // 对于子目录扫描，使用并行流处理
            Arrays.stream(subDirectories).parallel().forEach(subDir -> {
                scanBothFileTypesRecursive(subDir, resultMap, currentDepth + 1, maxDepth);
            });
        }
    }
    
    @Override
    public ScanResult scanAndImportPegasusMetadata(String rootPath) {
        return scanAndImportPegasusMetadata(rootPath, -1);
    }
    
    /**
     * 扫描指定路径及其子目录下的metadata.pegasus.txt文件并导入，支持指定扫描深度
     * @param rootPath 根路径
     * @param scanDepth 扫描深度，-1表示递归所有子目录
     * @return 扫描结果
     */
    public ScanResult scanAndImportPegasusMetadata(String rootPath, int scanDepth) {
        ScanResult result = new ScanResult();
        List<ScanResult.Detail> details = new ArrayList<>();
        
        File rootDir = new File(rootPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            result.setSuccess(false);
            result.setMessage("指定的路径不存在或不是目录");
            result.setFoundFiles(0);
            result.setImportedFiles(0);
            return result;
        }
        
        logger.info("开始扫描metadata.pegasus.txt文件，根路径: {}, 扫描深度: {}", rootPath, scanDepth);
        
        // 扫描所有metadata.pegasus.txt文件（根据指定深度扫描）
        List<File> metadataFiles = scanPegasusMetadataFiles(rootDir, scanDepth);
        logger.info("扫描完成，找到 {} 个metadata.pegasus.txt文件", metadataFiles.size());
        
        result.setFoundFiles(metadataFiles.size());
        
        int importedCount = 0;
        for (File file : metadataFiles) {
            ScanResult.Detail detail = new ScanResult.Detail();
            detail.setFilePath(file.getAbsolutePath());
            
            try {
                // 解析Pegasus元数据文件
                detail.setMessage("正在解析Pegasus元数据文件...");
                logger.info("开始解析文件: {}", file.getAbsolutePath());
                List<PegasusMetadataParser.GameCollection> pegasusCollections = PegasusMetadataParser.parseMetadata(file);
                
                // 处理每个平台数据，直接创建Platform
                for (PegasusMetadataParser.GameCollection pegasusCollection : pegasusCollections) {
                    String platformName = pegasusCollection.getName();
                    if (platformName == null || platformName.isEmpty()) {
                        platformName = "Unknown";
                    }
                    
                    detail.setMessage("正在处理Platform: " + platformName);
                    logger.debug("处理Platform: {}", platformName);
                    
                    // 保存platform信息
                    Platform platform = new Platform();
                    platform.setSystem(platformName);
                    platform.setName(platformName); // 使用已经处理过的platformName，确保不为null
                    
                    // 设置folderPath字段为父文件夹的绝对路径
                    File metadataFile = new File(file.getAbsolutePath());
                    File parentFolder = metadataFile.getParentFile();
                    if (parentFolder != null) {
                        String folderPath = parentFolder.getAbsolutePath();
                        platform.setFolderPath(folderPath);
                    } else {
                        platform.setFolderPath(platformName); // 备选方案
                    }
                    
                    platform.setSortBy(pegasusCollection.getSortBy());
                    platform.setLaunch(pegasusCollection.getLaunch());
                    platform.setSoftware("WebGamelistOper");
                    platform.setDatabase("Custom Database");
                    platform.setWeb("http://localhost:8083");
                    
                    // 检查platform是否已存在
                    logger.debug("检查platform是否已存在: {}", platformName);
                    Platform existingPlatform = platformService.getPlatformBySystem(platformName);
                    if (existingPlatform == null) {
                        logger.debug("Platform不存在，创建新的platform");
                        platformMapper.insertPlatform(platform);
                    } else {
                        logger.debug("Platform已存在，更新platform");
                        platform.setId(existingPlatform.getId());
                        platformMapper.updatePlatform(platform);
                    }
                    
                    // 转换游戏数据并保存
                    List<Game> games = new ArrayList<>();
                    // 获取平台路径
                    String platformPath = platform.getFolderPath();
                    for (PegasusMetadataParser.Game pegasusGame : pegasusCollection.getGames()) {
                        List<Game> gameList = convertToGameModelsFromPegasus(pegasusGame, platform.getId(), file.getAbsolutePath(), false, platformPath);
                        // 确保platformId不为null
                        for (Game game : gameList) {
                            game.setPlatformId(platform.getId());
                            logger.debug("创建游戏: {}，platformId: {}", game.getName(), game.getPlatformId());
                            games.add(game);
                        }
                    }
                    
                    // 批量保存游戏前检查路径是否已存在
                    if (!games.isEmpty()) {
                        // 优化：分批处理和批量插入
                        importGamesInBatches(games, platform);
                    }
                    
                    // 更新该平台下所有游戏的PLATFORM_PATH值
                    updatePlatformPathForAllGames(platform.getId(), platform.getFolderPath());
                }
                
                detail.setSuccess(true);
                detail.setMessage("导入成功");
                importedCount++;
                logger.info("文件导入成功: {}", file.getAbsolutePath());
            } catch (Exception e) {
                detail.setSuccess(false);
                String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
                String stackTrace = getStackTrace(e);
                detail.setMessage("导入失败: " + errorMsg + "\n" + stackTrace);
                logger.error("文件导入失败: {}", file.getAbsolutePath(), e);
            }
            
            details.add(detail);
        }
        
        result.setImportedFiles(importedCount);
        result.setDetails(details);
        result.setSuccess(true);
        result.setMessage("扫描完成");
        
        return result;
    }
    
    /**
     * 将Pegasus游戏模型转换为Game模型
     */
    private Game convertToGameModelFromPegasus(PegasusMetadataParser.Game pegasusGame, Long platformId, String metadataFilePath) {
        List<Game> games = convertToGameModelsFromPegasus(pegasusGame, platformId, metadataFilePath);
        return games.isEmpty() ? new Game() : games.get(0);
    }
    
    private List<Game> convertToGameModelsFromPegasus(PegasusMetadataParser.Game pegasusGame, Long platformId, String metadataFilePath) {
        return convertToGameModelsFromPegasus(pegasusGame, platformId, metadataFilePath, false, null);
    }
    
    private List<Game> convertToGameModelsFromPegasus(PegasusMetadataParser.Game pegasusGame, Long platformId, String metadataFilePath, boolean metadataOnly) {
        return convertToGameModelsFromPegasus(pegasusGame, platformId, metadataFilePath, metadataOnly, null);
    }

    private List<Game> convertToGameModelsFromPegasus(PegasusMetadataParser.Game pegasusGame, Long platformId, String metadataFilePath, boolean metadataOnly, String platformPath) {
        List<Game> games = new ArrayList<>();
        List<String> files = pegasusGame.getFiles();

        if (files == null || files.isEmpty()) {
            games.add(createSingleGameFromPegasus(pegasusGame, platformId, metadataFilePath, null, metadataOnly, platformPath));
            return games;
        }

        for (String file : files) {
            if (file != null && !file.isEmpty()) {
                Game game = createSingleGameFromPegasus(pegasusGame, platformId, metadataFilePath, file, metadataOnly, platformPath);
                games.add(game);
            }
        }

        return games;
    }

    private Game createSingleGameFromPegasus(PegasusMetadataParser.Game pegasusGame, Long platformId, String metadataFilePath, String filePath) {
        return createSingleGameFromPegasus(pegasusGame, platformId, metadataFilePath, filePath, false, null);
    }

    private Game createSingleGameFromPegasus(PegasusMetadataParser.Game pegasusGame, Long platformId, String metadataFilePath, String filePath, boolean metadataOnly, String platformPath) {
        Game game = new Game();
        String originalPath = filePath;
        String path = originalPath;
        String absolutePath = null;
        Boolean exists = false;
        
        // 计算绝对路径并检查文件是否存在
        if (path != null && !path.isEmpty()) {
            File metadataFile = new File(metadataFilePath);
            if (metadataFile.exists()) {
                File metadataDir = metadataFile.getParentFile();
                // 如果是相对路径（不以盘符开头或不以/开头），则转换为绝对路径
                if (!path.matches("^[A-Za-z]:.*") && !path.startsWith("/") && !path.startsWith("\\")) {
                    // 处理路径中的./或.\前缀
                    if (path.startsWith("./") || path.startsWith(".\\")) {
                        path = path.substring(2);
                    }
                    File absolutePathFile = new File(metadataDir, path);
                    absolutePath = absolutePathFile.getAbsolutePath();
                    // 检查文件是否存在
                    exists = absolutePathFile.exists();
                    logger.debug("路径转换: {} -> {}, 存在: {}", originalPath, absolutePath, exists);
                } else {
                    // 已经是绝对路径
                    absolutePath = path;
                    File absolutePathFile = new File(absolutePath);
                    exists = absolutePathFile.exists();
                    logger.debug("绝对路径: {}, 存在: {}", absolutePath, exists);
                }
            }
        }
        
        // 路径已经处理完毕，不再需要保留原始路径
        
        // 确保gameId不为null，使用文件名作为备选方案
        String gameId = null;
        if (path != null && !path.isEmpty()) {
            gameId = LanguageDetector.extractFileName(path);
        }
        if (gameId == null || gameId.isEmpty()) {
            gameId = "game_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        }
        // 确保gameId长度不超过50个字符
        if (gameId.length() > 50) {
            String timestamp = String.valueOf(System.currentTimeMillis());
            gameId = gameId.substring(0, 40) + "_" + timestamp.substring(timestamp.length() - 8);
        }
        game.setGameId(gameId);
        
        // 确保source不为null且长度不超过100
        game.setSource("Pegasus");
        
        // 确保path不为null且长度不超过1024
        if (path == null || path.isEmpty()) {
            path = "unknown_path_" + System.currentTimeMillis();
        } else if (path.length() > 1024) {
            path = path.substring(0, 1024);
        }
        game.setPath(path);
        
        // 设置其他字段，确保长度不超过限制
        game.setPlatformId(platformId);
        
        // 设置platformPath字段
        if (platformPath != null && !platformPath.isEmpty()) {
            game.setPlatformPath(platformPath);
        }
        
        // 处理游戏名称和描述
        String originalName = pegasusGame.getName();
        String originalDesc = pegasusGame.getDescription();
        
        boolean isNameChinese = LanguageDetector.containsChinese(originalName);
        boolean isDescChinese = originalDesc != null && LanguageDetector.containsChinese(originalDesc);
        
        String finalName;
        // 无论中文还是英文，都使用game标签的值作为游戏名称
        finalName = originalName != null && !originalName.isEmpty() ? originalName : "unknown_name_" + System.currentTimeMillis();
        // 确保name长度不超过255
        if (finalName.length() > 255) {
            finalName = finalName.substring(0, 255);
        }
        game.setName(finalName);
        
        // 处理translatedName
        if (isDescChinese) {
            game.setTranslatedName(finalName);
        } else {
            game.setTranslatedName(null);
        }
        
        // 处理游戏描述
        if (originalDesc != null) {
            // 格式化desc字段：去除多余换行、空格
            String formattedDesc = formatDescription(originalDesc);
            if (isDescChinese) {
                // 中文描述：desc字段留空，translatedDesc字段使用格式化后的描述
                game.setDesc(null);
                game.setTranslatedDesc(formattedDesc);
            } else {
                // 英文描述：使用格式化后的描述
                game.setDesc(formattedDesc);
                game.setTranslatedDesc(null);
            }
        } else {
            game.setDesc(null);
            game.setTranslatedDesc(null);
        }
        
        // 设置其他字段
        game.setReleasedate(truncateString(pegasusGame.getReleaseDate(), 50));
        game.setDeveloper(truncateString(pegasusGame.getDeveloper(), 100));
        game.setPublisher(truncateString(pegasusGame.getPublisher(), 100));
        game.setGenre(truncateString(pegasusGame.getGenre(), 100));
        game.setPlayers(truncateString(pegasusGame.getPlayers(), 20));
        
        // 设置绝对路径和文件存在状态
        game.setAbsolutePath(absolutePath);
        game.setExists(exists);

        // 1. 验证数据文件中的媒体文件是否存在
        File metadataFile = new File(metadataFilePath);
        File metadataDir = metadataFile.getParentFile();
        
        // 验证并设置数据文件中的媒体文件
        if (pegasusGame.getBoxFront() != null) {
            String boxFrontPath = pegasusGame.getBoxFront();
            // 处理路径中的./或.\前缀
            if (boxFrontPath.startsWith("./") || boxFrontPath.startsWith(".\\")) {
                boxFrontPath = boxFrontPath.substring(2);
            }
            File boxFrontFile = new File(metadataDir, boxFrontPath);
            if (boxFrontFile.exists()) {
                game.setImage(truncateString(boxFrontPath, 500));
                game.setBoxFront(truncateString(boxFrontPath, 500));
            }
        }
        if (pegasusGame.getVideo() != null) {
            String videoPath = pegasusGame.getVideo();
            // 处理路径中的./或.\前缀
            if (videoPath.startsWith("./") || videoPath.startsWith(".\\")) {
                videoPath = videoPath.substring(2);
            }
            File videoFile = new File(metadataDir, videoPath);
            if (videoFile.exists()) {
                game.setVideo(truncateString(videoPath, 500));
            }
        }
        if (pegasusGame.getLogo() != null) {
            String logoPath = pegasusGame.getLogo();
            // 处理路径中的./或.\前缀
            if (logoPath.startsWith("./") || logoPath.startsWith(".\\")) {
                logoPath = logoPath.substring(2);
            }
            File logoFile = new File(metadataDir, logoPath);
            if (logoFile.exists()) {
                game.setMarquee(truncateString(logoPath, 500));
                game.setLogo(truncateString(logoPath, 500));
            }
        }
        if (pegasusGame.getScreenshot() != null) {
            String screenshotPath = pegasusGame.getScreenshot();
            // 处理路径中的./或.\前缀
            if (screenshotPath.startsWith("./") || screenshotPath.startsWith(".\\")) {
                screenshotPath = screenshotPath.substring(2);
            }
            File screenshotFile = new File(metadataDir, screenshotPath);
            if (screenshotFile.exists()) {
                game.setThumbnail(truncateString(screenshotPath, 500));
                game.setScreenshot(truncateString(screenshotPath, 500));
            }
        }
        if (pegasusGame.getBoxBack() != null) {
            String boxBackPath = pegasusGame.getBoxBack();
            // 处理路径中的./或.\前缀
            if (boxBackPath.startsWith("./") || boxBackPath.startsWith(".\\")) {
                boxBackPath = boxBackPath.substring(2);
            }
            File boxBackFile = new File(metadataDir, boxBackPath);
            if (boxBackFile.exists()) {
                game.setManual(truncateString(boxBackPath, 500));
                game.setBoxBack(truncateString(boxBackPath, 500));
            }
        }
        if (pegasusGame.getBoxSpine() != null) {
            String boxSpinePath = pegasusGame.getBoxSpine();
            // 处理路径中的./或.\前缀
            if (boxSpinePath.startsWith("./") || boxSpinePath.startsWith(".\\")) {
                boxSpinePath = boxSpinePath.substring(2);
            }
            File boxSpineFile = new File(metadataDir, boxSpinePath);
            if (boxSpineFile.exists()) {
                game.setBoxSpine(truncateString(boxSpinePath, 500));
            }
        }
        if (pegasusGame.getBoxFull() != null) {
            String boxFullPath = pegasusGame.getBoxFull();
            // 处理路径中的./或.\前缀
            if (boxFullPath.startsWith("./") || boxFullPath.startsWith(".\\")) {
                boxFullPath = boxFullPath.substring(2);
            }
            File boxFullFile = new File(metadataDir, boxFullPath);
            if (boxFullFile.exists()) {
                game.setBoxFull(truncateString(boxFullPath, 500));
            }
        }
        if (pegasusGame.getCartridge() != null) {
            String cartridgePath = pegasusGame.getCartridge();
            // 处理路径中的./或.\前缀
            if (cartridgePath.startsWith("./") || cartridgePath.startsWith(".\\")) {
                cartridgePath = cartridgePath.substring(2);
            }
            File cartridgeFile = new File(metadataDir, cartridgePath);
            if (cartridgeFile.exists()) {
                game.setCartridge(truncateString(cartridgePath, 500));
            }
        }
        if (pegasusGame.getBezel() != null) {
            String bezelPath = pegasusGame.getBezel();
            // 处理路径中的./或.\前缀
            if (bezelPath.startsWith("./") || bezelPath.startsWith(".\\")) {
                bezelPath = bezelPath.substring(2);
            }
            File bezelFile = new File(metadataDir, bezelPath);
            if (bezelFile.exists()) {
                game.setBezel(truncateString(bezelPath, 500));
            }
        }
        if (pegasusGame.getPanel() != null) {
            String panelPath = pegasusGame.getPanel();
            // 处理路径中的./或.\前缀
            if (panelPath.startsWith("./") || panelPath.startsWith(".\\")) {
                panelPath = panelPath.substring(2);
            }
            File panelFile = new File(metadataDir, panelPath);
            if (panelFile.exists()) {
                game.setPanel(truncateString(panelPath, 500));
            }
        }
        if (pegasusGame.getCabinetLeft() != null) {
            String cabinetLeftPath = pegasusGame.getCabinetLeft();
            // 处理路径中的./或.\前缀
            if (cabinetLeftPath.startsWith("./") || cabinetLeftPath.startsWith(".\\")) {
                cabinetLeftPath = cabinetLeftPath.substring(2);
            }
            File cabinetLeftFile = new File(metadataDir, cabinetLeftPath);
            if (cabinetLeftFile.exists()) {
                game.setCabinetLeft(truncateString(cabinetLeftPath, 500));
            }
        }
        if (pegasusGame.getCabinetRight() != null) {
            String cabinetRightPath = pegasusGame.getCabinetRight();
            // 处理路径中的./或.\前缀
            if (cabinetRightPath.startsWith("./") || cabinetRightPath.startsWith(".\\")) {
                cabinetRightPath = cabinetRightPath.substring(2);
            }
            File cabinetRightFile = new File(metadataDir, cabinetRightPath);
            if (cabinetRightFile.exists()) {
                game.setCabinetRight(truncateString(cabinetRightPath, 500));
            }
        }
        if (pegasusGame.getTile() != null) {
            String tilePath = pegasusGame.getTile();
            // 处理路径中的./或.\前缀
            if (tilePath.startsWith("./") || tilePath.startsWith(".\\")) {
                tilePath = tilePath.substring(2);
            }
            File tileFile = new File(metadataDir, tilePath);
            if (tileFile.exists()) {
                game.setTile(truncateString(tilePath, 500));
            }
        }
        if (pegasusGame.getBanner() != null) {
            String bannerPath = pegasusGame.getBanner();
            // 处理路径中的./或.\前缀
            if (bannerPath.startsWith("./") || bannerPath.startsWith(".\\")) {
                bannerPath = bannerPath.substring(2);
            }
            File bannerFile = new File(metadataDir, bannerPath);
            if (bannerFile.exists()) {
                game.setBanner(truncateString(bannerPath, 500));
            }
        }
        if (pegasusGame.getSteam() != null) {
            String steamPath = pegasusGame.getSteam();
            // 处理路径中的./或.\前缀
            if (steamPath.startsWith("./") || steamPath.startsWith(".\\")) {
                steamPath = steamPath.substring(2);
            }
            File steamFile = new File(metadataDir, steamPath);
            if (steamFile.exists()) {
                game.setSteam(truncateString(steamPath, 500));
            }
        }
        if (pegasusGame.getPoster() != null) {
            String posterPath = pegasusGame.getPoster();
            // 处理路径中的./或.\前缀
            if (posterPath.startsWith("./") || posterPath.startsWith(".\\")) {
                posterPath = posterPath.substring(2);
            }
            File posterFile = new File(metadataDir, posterPath);
            if (posterFile.exists()) {
                game.setPoster(truncateString(posterPath, 500));
            }
        }
        if (pegasusGame.getBackground() != null) {
            String backgroundPath = pegasusGame.getBackground();
            // 处理路径中的./或.\前缀
            if (backgroundPath.startsWith("./") || backgroundPath.startsWith(".\\")) {
                backgroundPath = backgroundPath.substring(2);
            }
            File backgroundFile = new File(metadataDir, backgroundPath);
            if (backgroundFile.exists()) {
                game.setBackground(truncateString(backgroundPath, 500));
            }
        }
        if (pegasusGame.getMusic() != null) {
            String musicPath = pegasusGame.getMusic();
            // 处理路径中的./或.\前缀
            if (musicPath.startsWith("./") || musicPath.startsWith(".\\")) {
                musicPath = musicPath.substring(2);
            }
            File musicFile = new File(metadataDir, musicPath);
            if (musicFile.exists()) {
                game.setMusic(truncateString(musicPath, 500));
            }
        }
        if (pegasusGame.getTitlescreen() != null) {
            String titlescreenPath = pegasusGame.getTitlescreen();
            // 处理路径中的./或.\前缀
            if (titlescreenPath.startsWith("./") || titlescreenPath.startsWith(".\\")) {
                titlescreenPath = titlescreenPath.substring(2);
            }
            File titlescreenFile = new File(metadataDir, titlescreenPath);
            if (titlescreenFile.exists()) {
                game.setTitlescreen(truncateString(titlescreenPath, 500));
            }
        }

        // 2. 运行自动查找流程，查找其他类型的媒体文件（仅当 metadataOnly 为 false 时执行）
        if (!metadataOnly && metadataDir != null) {
            String baseDir = metadataDir.getAbsolutePath();
            MediaFileFinder.MediaFiles mediaFiles = MediaFileFinder.findMediaFiles(absolutePath, baseDir, null);
            Map<String, String> foundMedia = new HashMap<>();
            if (mediaFiles.getBoxFront() != null) foundMedia.put("boxFront", mediaFiles.getBoxFront());
            if (mediaFiles.getVideo() != null) foundMedia.put("video", mediaFiles.getVideo());
            if (mediaFiles.getLogo() != null) foundMedia.put("logo", mediaFiles.getLogo());
            if (mediaFiles.getScreenshot() != null) foundMedia.put("screenshot", mediaFiles.getScreenshot());
            if (mediaFiles.getBoxBack() != null) foundMedia.put("boxBack", mediaFiles.getBoxBack());
            if (mediaFiles.getBoxSpine() != null) foundMedia.put("boxSpine", mediaFiles.getBoxSpine());
            if (mediaFiles.getBoxFull() != null) foundMedia.put("boxFull", mediaFiles.getBoxFull());
            if (mediaFiles.getCartridge() != null) foundMedia.put("cartridge", mediaFiles.getCartridge());
            if (mediaFiles.getBezel() != null) foundMedia.put("bezel", mediaFiles.getBezel());
            if (mediaFiles.getPanel() != null) foundMedia.put("panel", mediaFiles.getPanel());
            if (mediaFiles.getCabinetLeft() != null) foundMedia.put("cabinetLeft", mediaFiles.getCabinetLeft());
            if (mediaFiles.getCabinetRight() != null) foundMedia.put("cabinetRight", mediaFiles.getCabinetRight());
            if (mediaFiles.getTile() != null) foundMedia.put("tile", mediaFiles.getTile());
            if (mediaFiles.getBanner() != null) foundMedia.put("banner", mediaFiles.getBanner());
            if (mediaFiles.getSteam() != null) foundMedia.put("steam", mediaFiles.getSteam());
            if (mediaFiles.getPoster() != null) foundMedia.put("poster", mediaFiles.getPoster());
            if (mediaFiles.getBackground() != null) foundMedia.put("background", mediaFiles.getBackground());
            if (mediaFiles.getMusic() != null) foundMedia.put("music", mediaFiles.getMusic());
            if (mediaFiles.getTitlescreen() != null) foundMedia.put("titlescreen", mediaFiles.getTitlescreen());
            if (mediaFiles.getBox3d() != null) foundMedia.put("box3d", mediaFiles.getBox3d());
            if (mediaFiles.getSteamgrid() != null) foundMedia.put("steamgrid", mediaFiles.getSteamgrid());
            if (mediaFiles.getFanart() != null) foundMedia.put("fanart", mediaFiles.getFanart());
            if (mediaFiles.getBoxtexture() != null) foundMedia.put("boxtexture", mediaFiles.getBoxtexture());
            if (mediaFiles.getSupporttexture() != null) foundMedia.put("supporttexture", mediaFiles.getSupporttexture());
            
            // 只设置数据文件中未指定或指定但不存在的媒体类型
            if (foundMedia.containsKey("boxFront") && game.getBoxFront() == null) {
                game.setImage(foundMedia.get("boxFront"));
                game.setBoxFront(foundMedia.get("boxFront"));
            }
            if (foundMedia.containsKey("video") && game.getVideo() == null) {
                game.setVideo(foundMedia.get("video"));
            }
            if (foundMedia.containsKey("logo") && game.getLogo() == null) {
                game.setMarquee(foundMedia.get("logo"));
                game.setLogo(foundMedia.get("logo"));
            }
            if (foundMedia.containsKey("screenshot") && game.getScreenshot() == null) {
                game.setThumbnail(foundMedia.get("screenshot"));
                game.setScreenshot(foundMedia.get("screenshot"));
            }
            if (foundMedia.containsKey("boxBack") && game.getBoxBack() == null) {
                game.setManual(foundMedia.get("boxBack"));
                game.setBoxBack(foundMedia.get("boxBack"));
            }
            if (foundMedia.containsKey("boxSpine") && game.getBoxSpine() == null) {
                game.setBoxSpine(foundMedia.get("boxSpine"));
            }
            if (foundMedia.containsKey("boxFull") && game.getBoxFull() == null) {
                game.setBoxFull(foundMedia.get("boxFull"));
            }
            if (foundMedia.containsKey("cartridge") && game.getCartridge() == null) {
                game.setCartridge(foundMedia.get("cartridge"));
            }
            if (foundMedia.containsKey("bezel") && game.getBezel() == null) {
                game.setBezel(foundMedia.get("bezel"));
            }
            if (foundMedia.containsKey("panel") && game.getPanel() == null) {
                game.setPanel(foundMedia.get("panel"));
            }
            if (foundMedia.containsKey("cabinetLeft") && game.getCabinetLeft() == null) {
                game.setCabinetLeft(foundMedia.get("cabinetLeft"));
            }
            if (foundMedia.containsKey("cabinetRight") && game.getCabinetRight() == null) {
                game.setCabinetRight(foundMedia.get("cabinetRight"));
            }
            if (foundMedia.containsKey("tile") && game.getTile() == null) {
                game.setTile(foundMedia.get("tile"));
            }
            if (foundMedia.containsKey("banner") && game.getBanner() == null) {
                game.setBanner(foundMedia.get("banner"));
            }
            if (foundMedia.containsKey("steam") && game.getSteam() == null) {
                game.setSteam(foundMedia.get("steam"));
            }
            if (foundMedia.containsKey("poster") && game.getPoster() == null) {
                game.setPoster(foundMedia.get("poster"));
            }
            if (foundMedia.containsKey("background") && game.getBackground() == null) {
                game.setBackground(foundMedia.get("background"));
            }
            if (foundMedia.containsKey("music") && game.getMusic() == null) {
                game.setMusic(foundMedia.get("music"));
            }
            if (foundMedia.containsKey("titlescreen") && game.getTitlescreen() == null) {
                game.setTitlescreen(foundMedia.get("titlescreen"));
            }
            if (foundMedia.containsKey("box3d") && game.getBox3d() == null) {
                game.setBox3d(foundMedia.get("box3d"));
            }
            if (foundMedia.containsKey("steamgrid") && game.getSteamgrid() == null) {
                game.setSteamgrid(foundMedia.get("steamgrid"));
            }
            if (foundMedia.containsKey("fanart") && game.getFanart() == null) {
                game.setFanart(foundMedia.get("fanart"));
            }
            if (foundMedia.containsKey("boxtexture") && game.getBoxtexture() == null) {
                game.setBoxtexture(foundMedia.get("boxtexture"));
            }
            if (foundMedia.containsKey("supporttexture") && game.getSupporttexture() == null) {
                game.setSupporttexture(foundMedia.get("supporttexture"));
            }
        }

        return game;
    }
    
    private Game convertToGameModel(GameListXml.GameXml gameXml, Long platformId, String gameListFilePath) {
        return convertToGameModel(gameXml, platformId, gameListFilePath, "generic", false, null);
    }

    private Game convertToGameModel(GameListXml.GameXml gameXml, Long platformId, String gameListFilePath, String platformType) {
        return convertToGameModel(gameXml, platformId, gameListFilePath, platformType, false, null);
    }

    private Game convertToGameModel(GameListXml.GameXml gameXml, Long platformId, String gameListFilePath, String platformType, boolean metadataOnly) {
        return convertToGameModel(gameXml, platformId, gameListFilePath, platformType, metadataOnly, null);
    }

    private Game convertToGameModel(GameListXml.GameXml gameXml, Long platformId, String gameListFilePath, String platformType, boolean metadataOnly, String platformPath) {
        Game game = new Game();
        String originalPath = gameXml.getPath();
        String path = originalPath;
        String absolutePath = null;
        Boolean exists = false;
        
        // 计算绝对路径并检查文件是否存在
        if (path != null && !path.isEmpty()) {
                File gameListFile = gameListFilePath != null ? new File(gameListFilePath) : null;
                if (gameListFile != null && gameListFile.exists()) {
                    File gameDir = gameListFile.getParentFile();
                    // 如果是相对路径（不以盘符开头或不以/开头），则转换为绝对路径
                    if (!path.matches("^[A-Za-z]:.*") && !path.startsWith("/") && !path.startsWith("\\")) {
                        // 处理路径中的./或.\前缀
                        if (path.startsWith("./") || path.startsWith(".\\")) {
                            path = path.substring(2);
                        }
                        File absolutePathFile = new File(gameDir, path);
                        absolutePath = absolutePathFile.getAbsolutePath();
                        // 检查文件是否存在
                        exists = absolutePathFile.exists();
                        logger.debug("路径转换: {} -> {}, 存在: {}", originalPath, absolutePath, exists);
                    } else {
                        // 已经是绝对路径
                        absolutePath = path;
                        File absolutePathFile = new File(absolutePath);
                        exists = absolutePathFile.exists();
                        logger.debug("绝对路径: {}, 存在: {}", absolutePath, exists);
                    }
                }
            }
            
            // 路径已经处理完毕，不再需要保留原始路径
        
        // 确保gameId不为null，优先使用<game>标签的id属性，其次使用<gameid>子标签，最后使用文件名作为备选方案
        String gameId = gameXml.getId();
        if (gameId == null || gameId.isEmpty()) {
            gameId = gameXml.getGameid();
            if (gameId == null || gameId.isEmpty()) {
                gameId = gameXml.getIdTag();
                if (gameId == null || gameId.isEmpty()) {
                    gameId = LanguageDetector.extractFileName(path);
                    // 如果文件名提取失败，使用UUID或生成一个唯一标识符
                    if (gameId == null || gameId.isEmpty()) {
                        gameId = "game_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
                    }
                }
            }
        }
        // 确保gameId长度不超过50个字符
        if (gameId.length() > 50) {
            // 截断并确保唯一性
            String timestamp = String.valueOf(System.currentTimeMillis());
            gameId = gameId.substring(0, 40) + "_" + timestamp.substring(timestamp.length() - 8);
        }
        game.setGameId(gameId);
        
        // 确保source不为null且长度不超过100
        String source = gameXml.getSource();
        if (source == null || source.isEmpty()) {
            source = platformType;
        } else if (source.length() > 100) {
            source = source.substring(0, 100);
        }
        game.setSource(source);
        
        // 确保path不为null且长度不超过1024
        if (path == null || path.isEmpty()) {
            path = "unknown_path_" + System.currentTimeMillis();
        } else if (path.length() > 1024) {
            path = path.substring(0, 1024);
        }
        game.setPath(path);
        
        // 设置platformPath字段
        if (platformPath != null && !platformPath.isEmpty()) {
            game.setPlatformPath(platformPath);
        }
        
        // 1. 验证数据文件中的媒体文件是否存在
        File gameListFile = new File(gameListFilePath);
        File gameDir = gameListFile.getParentFile();
        
        // 验证并设置数据文件中的媒体文件
        if (gameXml.getImage() != null) {
            String imagePath = gameXml.getImage();
            // 处理路径中的./或.\前缀
            if (imagePath.startsWith("./") || imagePath.startsWith(".\\")) {
                imagePath = imagePath.substring(2);
            }
            File imageFile = new File(gameDir, imagePath);
            if (imageFile.exists()) {
                game.setImage(truncateString(imagePath, 255));
            }
        }
        if (gameXml.getVideo() != null) {
            String videoPath = gameXml.getVideo();
            // 处理路径中的./或.\前缀
            if (videoPath.startsWith("./") || videoPath.startsWith(".\\")) {
                videoPath = videoPath.substring(2);
            }
            File videoFile = new File(gameDir, videoPath);
            if (videoFile.exists()) {
                game.setVideo(truncateString(videoPath, 255));
            }
        }
        if (gameXml.getMarquee() != null) {
            String marqueePath = gameXml.getMarquee();
            // 处理路径中的./或.\前缀
            if (marqueePath.startsWith("./") || marqueePath.startsWith(".\\")) {
                marqueePath = marqueePath.substring(2);
            }
            File marqueeFile = new File(gameDir, marqueePath);
            if (marqueeFile.exists()) {
                game.setMarquee(truncateString(marqueePath, 255));
            }
        }
        if (gameXml.getThumbnail() != null) {
            String thumbnailPath = gameXml.getThumbnail();
            // 处理路径中的./或.\前缀
            if (thumbnailPath.startsWith("./") || thumbnailPath.startsWith(".\\")) {
                thumbnailPath = thumbnailPath.substring(2);
            }
            File thumbnailFile = new File(gameDir, thumbnailPath);
            if (thumbnailFile.exists()) {
                game.setThumbnail(truncateString(thumbnailPath, 255));
            }
        }
        if (gameXml.getManual() != null) {
            String manualPath = gameXml.getManual();
            // 处理路径中的./或.\前缀
            if (manualPath.startsWith("./") || manualPath.startsWith(".\\")) {
                manualPath = manualPath.substring(2);
            }
            File manualFile = new File(gameDir, manualPath);
            if (manualFile.exists()) {
                game.setManual(truncateString(manualPath, 255));
            }
        }
        if (gameXml.getBoxFront() != null) {
            String boxFrontPath = gameXml.getBoxFront();
            // 处理路径中的./或.\前缀
            if (boxFrontPath.startsWith("./") || boxFrontPath.startsWith(".\\")) {
                boxFrontPath = boxFrontPath.substring(2);
            }
            File boxFrontFile = new File(gameDir, boxFrontPath);
            if (boxFrontFile.exists()) {
                game.setBoxFront(truncateString(boxFrontPath, 255));
            }
        }
        if (gameXml.getBoxBack() != null) {
            String boxBackPath = gameXml.getBoxBack();
            // 处理路径中的./或.\前缀
            if (boxBackPath.startsWith("./") || boxBackPath.startsWith(".\\")) {
                boxBackPath = boxBackPath.substring(2);
            }
            File boxBackFile = new File(gameDir, boxBackPath);
            if (boxBackFile.exists()) {
                game.setBoxBack(truncateString(boxBackPath, 255));
            }
        }
        if (gameXml.getBoxSpine() != null) {
            String boxSpinePath = gameXml.getBoxSpine();
            // 处理路径中的./或.\前缀
            if (boxSpinePath.startsWith("./") || boxSpinePath.startsWith(".\\")) {
                boxSpinePath = boxSpinePath.substring(2);
            }
            File boxSpineFile = new File(gameDir, boxSpinePath);
            if (boxSpineFile.exists()) {
                game.setBoxSpine(truncateString(boxSpinePath, 255));
            }
        }
        if (gameXml.getBoxFull() != null) {
            String boxFullPath = gameXml.getBoxFull();
            // 处理路径中的./或.\前缀
            if (boxFullPath.startsWith("./") || boxFullPath.startsWith(".\\")) {
                boxFullPath = boxFullPath.substring(2);
            }
            File boxFullFile = new File(gameDir, boxFullPath);
            if (boxFullFile.exists()) {
                game.setBoxFull(truncateString(boxFullPath, 255));
            }
        }
        if (gameXml.getCartridge() != null) {
            String cartridgePath = gameXml.getCartridge();
            // 处理路径中的./或.\前缀
            if (cartridgePath.startsWith("./") || cartridgePath.startsWith(".\\")) {
                cartridgePath = cartridgePath.substring(2);
            }
            File cartridgeFile = new File(gameDir, cartridgePath);
            if (cartridgeFile.exists()) {
                game.setCartridge(truncateString(cartridgePath, 255));
            }
        }
        if (gameXml.getLogo() != null) {
            String logoPath = gameXml.getLogo();
            // 处理路径中的./或.\前缀
            if (logoPath.startsWith("./") || logoPath.startsWith(".\\")) {
                logoPath = logoPath.substring(2);
            }
            File logoFile = new File(gameDir, logoPath);
            if (logoFile.exists()) {
                game.setLogo(truncateString(logoPath, 255));
            }
        }
        if (gameXml.getBezel() != null) {
            String bezelPath = gameXml.getBezel();
            // 处理路径中的./或.\前缀
            if (bezelPath.startsWith("./") || bezelPath.startsWith(".\\")) {
                bezelPath = bezelPath.substring(2);
            }
            File bezelFile = new File(gameDir, bezelPath);
            if (bezelFile.exists()) {
                game.setBezel(truncateString(bezelPath, 255));
            }
        }
        if (gameXml.getPanel() != null) {
            String panelPath = gameXml.getPanel();
            // 处理路径中的./或.\前缀
            if (panelPath.startsWith("./") || panelPath.startsWith(".\\")) {
                panelPath = panelPath.substring(2);
            }
            File panelFile = new File(gameDir, panelPath);
            if (panelFile.exists()) {
                game.setPanel(truncateString(panelPath, 255));
            }
        }
        if (gameXml.getCabinetLeft() != null) {
            String cabinetLeftPath = gameXml.getCabinetLeft();
            // 处理路径中的./或.\前缀
            if (cabinetLeftPath.startsWith("./") || cabinetLeftPath.startsWith(".\\")) {
                cabinetLeftPath = cabinetLeftPath.substring(2);
            }
            File cabinetLeftFile = new File(gameDir, cabinetLeftPath);
            if (cabinetLeftFile.exists()) {
                game.setCabinetLeft(truncateString(cabinetLeftPath, 255));
            }
        }
        if (gameXml.getCabinetRight() != null) {
            String cabinetRightPath = gameXml.getCabinetRight();
            // 处理路径中的./或.\前缀
            if (cabinetRightPath.startsWith("./") || cabinetRightPath.startsWith(".\\")) {
                cabinetRightPath = cabinetRightPath.substring(2);
            }
            File cabinetRightFile = new File(gameDir, cabinetRightPath);
            if (cabinetRightFile.exists()) {
                game.setCabinetRight(truncateString(cabinetRightPath, 255));
            }
        }
        if (gameXml.getTile() != null) {
            String tilePath = gameXml.getTile();
            // 处理路径中的./或.\前缀
            if (tilePath.startsWith("./") || tilePath.startsWith(".\\")) {
                tilePath = tilePath.substring(2);
            }
            File tileFile = new File(gameDir, tilePath);
            if (tileFile.exists()) {
                game.setTile(truncateString(tilePath, 255));
            }
        }
        if (gameXml.getBanner() != null) {
            String bannerPath = gameXml.getBanner();
            // 处理路径中的./或.\前缀
            if (bannerPath.startsWith("./") || bannerPath.startsWith(".\\")) {
                bannerPath = bannerPath.substring(2);
            }
            File bannerFile = new File(gameDir, bannerPath);
            if (bannerFile.exists()) {
                game.setBanner(truncateString(bannerPath, 255));
            }
        }
        if (gameXml.getSteam() != null) {
            String steamPath = gameXml.getSteam();
            // 处理路径中的./或.\前缀
            if (steamPath.startsWith("./") || steamPath.startsWith(".\\")) {
                steamPath = steamPath.substring(2);
            }
            File steamFile = new File(gameDir, steamPath);
            if (steamFile.exists()) {
                game.setSteam(truncateString(steamPath, 255));
            }
        }
        if (gameXml.getPoster() != null) {
            String posterPath = gameXml.getPoster();
            // 处理路径中的./或.\前缀
            if (posterPath.startsWith("./") || posterPath.startsWith(".\\")) {
                posterPath = posterPath.substring(2);
            }
            File posterFile = new File(gameDir, posterPath);
            if (posterFile.exists()) {
                game.setPoster(truncateString(posterPath, 255));
            }
        }
        if (gameXml.getBackground() != null) {
            String backgroundPath = gameXml.getBackground();
            // 处理路径中的./或.\前缀
            if (backgroundPath.startsWith("./") || backgroundPath.startsWith(".\\")) {
                backgroundPath = backgroundPath.substring(2);
            }
            File backgroundFile = new File(gameDir, backgroundPath);
            if (backgroundFile.exists()) {
                game.setBackground(truncateString(backgroundPath, 255));
            }
        }
        if (gameXml.getMusic() != null) {
            String musicPath = gameXml.getMusic();
            // 处理路径中的./或.\前缀
            if (musicPath.startsWith("./") || musicPath.startsWith(".\\")) {
                musicPath = musicPath.substring(2);
            }
            File musicFile = new File(gameDir, musicPath);
            if (musicFile.exists()) {
                game.setMusic(truncateString(musicPath, 255));
            }
        }
        if (gameXml.getScreenshot() != null) {
            String screenshotPath = gameXml.getScreenshot();
            // 处理路径中的./或.\前缀
            if (screenshotPath.startsWith("./") || screenshotPath.startsWith(".\\")) {
                screenshotPath = screenshotPath.substring(2);
            }
            File screenshotFile = new File(gameDir, screenshotPath);
            if (screenshotFile.exists()) {
                game.setScreenshot(truncateString(screenshotPath, 255));
            }
        }
        if (gameXml.getTitlescreen() != null) {
            String titlescreenPath = gameXml.getTitlescreen();
            // 处理路径中的./或.\前缀
            if (titlescreenPath.startsWith("./") || titlescreenPath.startsWith(".\\")) {
                titlescreenPath = titlescreenPath.substring(2);
            }
            File titlescreenFile = new File(gameDir, titlescreenPath);
            if (titlescreenFile.exists()) {
                game.setTitlescreen(truncateString(titlescreenPath, 255));
            }
        }

        // 2. 运行自动查找流程，查找其他类型的媒体文件（仅当 metadataOnly 为 false 时执行）
        if (!metadataOnly && gameDir != null) {
            String baseDir = gameDir.getAbsolutePath();
            MediaFileFinder.MediaFiles mediaFiles = MediaFileFinder.findMediaFiles(absolutePath, baseDir, null);
            Map<String, String> foundMedia = new HashMap<>();
            if (mediaFiles.getBoxFront() != null) foundMedia.put("boxFront", mediaFiles.getBoxFront());
            if (mediaFiles.getVideo() != null) foundMedia.put("video", mediaFiles.getVideo());
            if (mediaFiles.getLogo() != null) foundMedia.put("logo", mediaFiles.getLogo());
            if (mediaFiles.getScreenshot() != null) foundMedia.put("screenshot", mediaFiles.getScreenshot());
            if (mediaFiles.getBoxBack() != null) foundMedia.put("boxBack", mediaFiles.getBoxBack());
            if (mediaFiles.getBoxSpine() != null) foundMedia.put("boxSpine", mediaFiles.getBoxSpine());
            if (mediaFiles.getBoxFull() != null) foundMedia.put("boxFull", mediaFiles.getBoxFull());
            if (mediaFiles.getCartridge() != null) foundMedia.put("cartridge", mediaFiles.getCartridge());
            if (mediaFiles.getBezel() != null) foundMedia.put("bezel", mediaFiles.getBezel());
            if (mediaFiles.getPanel() != null) foundMedia.put("panel", mediaFiles.getPanel());
            if (mediaFiles.getCabinetLeft() != null) foundMedia.put("cabinetLeft", mediaFiles.getCabinetLeft());
            if (mediaFiles.getCabinetRight() != null) foundMedia.put("cabinetRight", mediaFiles.getCabinetRight());
            if (mediaFiles.getTile() != null) foundMedia.put("tile", mediaFiles.getTile());
            if (mediaFiles.getBanner() != null) foundMedia.put("banner", mediaFiles.getBanner());
            if (mediaFiles.getSteam() != null) foundMedia.put("steam", mediaFiles.getSteam());
            if (mediaFiles.getPoster() != null) foundMedia.put("poster", mediaFiles.getPoster());
            if (mediaFiles.getBackground() != null) foundMedia.put("background", mediaFiles.getBackground());
            if (mediaFiles.getMusic() != null) foundMedia.put("music", mediaFiles.getMusic());
            if (mediaFiles.getTitlescreen() != null) foundMedia.put("titlescreen", mediaFiles.getTitlescreen());
            if (mediaFiles.getBox3d() != null) foundMedia.put("box3d", mediaFiles.getBox3d());
            if (mediaFiles.getSteamgrid() != null) foundMedia.put("steamgrid", mediaFiles.getSteamgrid());
            if (mediaFiles.getFanart() != null) foundMedia.put("fanart", mediaFiles.getFanart());
            if (mediaFiles.getBoxtexture() != null) foundMedia.put("boxtexture", mediaFiles.getBoxtexture());
            if (mediaFiles.getSupporttexture() != null) foundMedia.put("supporttexture", mediaFiles.getSupporttexture());
            
            // 只设置数据文件中未指定或指定但不存在的媒体类型
            if (foundMedia.containsKey("boxFront") && game.getBoxFront() == null) {
                game.setBoxFront(foundMedia.get("boxFront"));
            }
            if (foundMedia.containsKey("video") && game.getVideo() == null) {
                game.setVideo(foundMedia.get("video"));
            }
            if (foundMedia.containsKey("logo") && game.getLogo() == null) {
                game.setLogo(foundMedia.get("logo"));
            }
            if (foundMedia.containsKey("screenshot") && game.getScreenshot() == null) {
                game.setScreenshot(foundMedia.get("screenshot"));
            }
            if (foundMedia.containsKey("boxBack") && game.getBoxBack() == null) {
                game.setBoxBack(foundMedia.get("boxBack"));
            }
            if (foundMedia.containsKey("boxSpine") && game.getBoxSpine() == null) {
                game.setBoxSpine(foundMedia.get("boxSpine"));
            }
            if (foundMedia.containsKey("boxFull") && game.getBoxFull() == null) {
                game.setBoxFull(foundMedia.get("boxFull"));
            }
            if (foundMedia.containsKey("cartridge") && game.getCartridge() == null) {
                game.setCartridge(foundMedia.get("cartridge"));
            }
            if (foundMedia.containsKey("bezel") && game.getBezel() == null) {
                game.setBezel(foundMedia.get("bezel"));
            }
            if (foundMedia.containsKey("panel") && game.getPanel() == null) {
                game.setPanel(foundMedia.get("panel"));
            }
            if (foundMedia.containsKey("cabinetLeft") && game.getCabinetLeft() == null) {
                game.setCabinetLeft(foundMedia.get("cabinetLeft"));
            }
            if (foundMedia.containsKey("cabinetRight") && game.getCabinetRight() == null) {
                game.setCabinetRight(foundMedia.get("cabinetRight"));
            }
            if (foundMedia.containsKey("tile") && game.getTile() == null) {
                game.setTile(foundMedia.get("tile"));
            }
            if (foundMedia.containsKey("banner") && game.getBanner() == null) {
                game.setBanner(foundMedia.get("banner"));
            }
            if (foundMedia.containsKey("steam") && game.getSteam() == null) {
                game.setSteam(foundMedia.get("steam"));
            }
            if (foundMedia.containsKey("poster") && game.getPoster() == null) {
                game.setPoster(foundMedia.get("poster"));
            }
            if (foundMedia.containsKey("background") && game.getBackground() == null) {
                game.setBackground(foundMedia.get("background"));
            }
            if (foundMedia.containsKey("music") && game.getMusic() == null) {
                game.setMusic(foundMedia.get("music"));
            }
            if (foundMedia.containsKey("titlescreen") && game.getTitlescreen() == null) {
                game.setTitlescreen(foundMedia.get("titlescreen"));
            }
            if (foundMedia.containsKey("box3d") && game.getBox3d() == null) {
                game.setBox3d(foundMedia.get("box3d"));
            }
            if (foundMedia.containsKey("steamgrid") && game.getSteamgrid() == null) {
                game.setSteamgrid(foundMedia.get("steamgrid"));
            }
            if (foundMedia.containsKey("fanart") && game.getFanart() == null) {
                game.setFanart(foundMedia.get("fanart"));
            }
            if (foundMedia.containsKey("boxtexture") && game.getBoxtexture() == null) {
                game.setBoxtexture(foundMedia.get("boxtexture"));
            }
            if (foundMedia.containsKey("supporttexture") && game.getSupporttexture() == null) {
                game.setSupporttexture(foundMedia.get("supporttexture"));
            }
        }
        game.setRating(gameXml.getRating());
        game.setReleasedate(truncateString(gameXml.getReleasedate(), 50));
        game.setDeveloper(truncateString(gameXml.getDeveloper(), 100));
        game.setPublisher(truncateString(gameXml.getPublisher(), 100));
        game.setGenre(truncateString(gameXml.getGenre(), 100));
        game.setPlayers(truncateString(gameXml.getPlayers(), 20));
        game.setCrc32(truncateString(gameXml.getCrc32(), 20));
        game.setMd5(truncateString(gameXml.getMd5(), 32));
        game.setLang(truncateString(gameXml.getLang(), 20));
        game.setGenreid(truncateString(gameXml.getGenreid(), 20));
        game.setHash(truncateString(gameXml.getHash(), 50));
        game.setPlatformType(platformType);
        game.setPlatformId(platformId);
        
        // 设置平台路径
        if (platformPath != null && !platformPath.isEmpty()) {
            game.setPlatformPath(platformPath);
        }
        
        // 处理平台特定字段
        java.util.Map<String, String> platformSpecificFields = new java.util.HashMap<>();
        
        // 根据平台类型添加特定字段
        if ("retrobat".equals(platformType)) {
            // RetroBat特定字段
            if (gameXml.getHash() != null) {
                platformSpecificFields.put("hash", gameXml.getHash());
            }
        }
        
        game.setPlatformSpecificFields(platformSpecificFields);
        
        // 处理游戏名称和描述
        String originalName = gameXml.getName();
        String originalDesc = gameXml.getDesc();
        
        boolean isNameChinese = LanguageDetector.containsChinese(originalName);
        boolean isDescChinese = originalDesc != null && LanguageDetector.containsChinese(originalDesc);
        
        String finalName;
        if (isNameChinese) {
            // 中文名称：name字段使用文件名，translatedName字段使用原名称
            String fileName = LanguageDetector.extractFileName(path);
            finalName = fileName != null && !fileName.isEmpty() ? fileName : "unknown_name_" + System.currentTimeMillis();
            // 确保name长度不超过255
            if (finalName.length() > 255) {
                finalName = finalName.substring(0, 255);
            }
            game.setName(finalName);
            game.setTranslatedName(truncateString(originalName, 255));
        } else {
            // 英文名称
            finalName = originalName != null && !originalName.isEmpty() ? originalName : "unknown_name_" + System.currentTimeMillis();
            // 确保name长度不超过255
            if (finalName.length() > 255) {
                finalName = finalName.substring(0, 255);
            }
            game.setName(finalName);
            
            // 如果描述是中文，那么translatedName也要写入这个英文名称
            if (isDescChinese) {
                game.setTranslatedName(finalName);
            } else {
                game.setTranslatedName(null);
            }
        }
        
        // 处理游戏描述
        if (originalDesc != null) {
            // 格式化desc字段：去除多余换行、空格
            String formattedDesc = formatDescription(originalDesc);
            if (isDescChinese) {
                // 中文描述：desc字段留空，translatedDesc字段使用格式化后的描述
                game.setDesc(null);
                game.setTranslatedDesc(formattedDesc);
            } else {
                // 英文描述：使用格式化后的描述
                game.setDesc(formattedDesc);
                game.setTranslatedDesc(null);
            }
        } else {
            game.setDesc(null);
            game.setTranslatedDesc(null);
        }
        
        // 设置绝对路径和文件存在状态
        game.setAbsolutePath(absolutePath);
        game.setExists(exists);
        
        return game;
    }

    @Override
    public int saveGame(Game game) {
        return gameMapper.insertGame(game);
    }

    @Override
    public int saveGamesBatch(List<Game> games) {
        return gameMapper.insertGamesBatch(games);
    }

    @Override
    public List<Game> getAllGames() {
        List<Game> games = gameMapper.selectAllGames();
        // 为每个游戏设置platformPath
        Map<Long, String> platformPathMap = new HashMap<>();
        for (Game game : games) {
            Long platformId = game.getPlatformId();
            if (platformId != null) {
                String platformPath = platformPathMap.get(platformId);
                if (platformPath == null) {
                    Platform platform = platformService.getPlatformById(platformId);
                    if (platform != null) {
                        platformPath = platform.getFolderPath();
                        platformPathMap.put(platformId, platformPath);
                    }
                }
                if (platformPath != null) {
                    game.setPlatformPath(platformPath);
                }
            }
        }
        return games;
    }

    public List<Game> getAllGames(String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players) {
        List<Game> games = gameMapper.selectAllGamesWithFilter(search, startDate, endDate, developers, genres, players);
        // 为每个游戏设置platformPath
        Map<Long, String> platformPathMap = new HashMap<>();
        for (Game game : games) {
            Long platformId = game.getPlatformId();
            if (platformId != null) {
                String platformPath = platformPathMap.get(platformId);
                if (platformPath == null) {
                    Platform platform = platformService.getPlatformById(platformId);
                    if (platform != null) {
                        platformPath = platform.getFolderPath();
                        platformPathMap.put(platformId, platformPath);
                    }
                }
                if (platformPath != null) {
                    game.setPlatformPath(platformPath);
                }
            }
        }
        return games;
    }

    @Override
    public List<Game> getAllGames(String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses) {
        List<Game> games = gameMapper.selectAllGamesWithFilter(search, startDate, endDate, developers, genres, players, scrapeStatuses);
        // 为每个游戏设置platformPath
        Map<Long, String> platformPathMap = new HashMap<>();
        for (Game game : games) {
            Long platformId = game.getPlatformId();
            if (platformId != null) {
                String platformPath = platformPathMap.get(platformId);
                if (platformPath == null) {
                    Platform platform = platformService.getPlatformById(platformId);
                    if (platform != null) {
                        platformPath = platform.getFolderPath();
                        platformPathMap.put(platformId, platformPath);
                    }
                }
                if (platformPath != null) {
                    game.setPlatformPath(platformPath);
                }
            }
        }
        return games;
    }

    @Override
    public List<Game> getGamesByPlatformId(Long platformId) {
        List<Game> games = gameMapper.selectGamesByPlatformId(platformId);
        // 为每个游戏设置platformPath
        Platform platform = platformService.getPlatformById(platformId);
        if (platform != null) {
            String platformPath = platform.getFolderPath();
            for (Game game : games) {
                game.setPlatformPath(platformPath);
            }
        }
        return games;
    }

    public List<Game> getGamesByPlatformId(Long platformId, String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players) {
        List<Game> games = gameMapper.selectGamesByPlatformIdWithFilter(platformId, search, startDate, endDate, developers, genres, players);
        // 为每个游戏设置platformPath
        Platform platform = platformService.getPlatformById(platformId);
        if (platform != null) {
            String platformPath = platform.getFolderPath();
            for (Game game : games) {
                game.setPlatformPath(platformPath);
            }
        }
        return games;
    }

    @Override
    public List<Game> getGamesByPlatformId(Long platformId, String search, String startDate, String endDate, List<String> developers, List<String> genres, List<String> players, List<String> scrapeStatuses) {
        List<Game> games = gameMapper.selectGamesByPlatformIdWithFilter(platformId, search, startDate, endDate, developers, genres, players, scrapeStatuses);
        // 为每个游戏设置platformPath
        Platform platform = platformService.getPlatformById(platformId);
        if (platform != null) {
            String platformPath = platform.getFolderPath();
            for (Game game : games) {
                game.setPlatformPath(platformPath);
            }
        }
        return games;
    }

    @Override
    public Game getGameById(Long id) {
        Game game = gameMapper.selectGameById(id);
        // 为游戏设置platformPath
        if (game != null) {
            Long platformId = game.getPlatformId();
            if (platformId != null) {
                Platform platform = platformService.getPlatformById(platformId);
                if (platform != null) {
                    game.setPlatformPath(platform.getFolderPath());
                }
            }
        }
        return game;
    }

    @Override
    public int deleteAllGames() {
        return gameMapper.deleteAllGames();
    }
    
    @Override
    public Statistics getOverallStatistics() {
        Statistics stats = new Statistics();
        stats.setTotalGames(gameMapper.countTotalGames());
        stats.setFullyScraped(gameMapper.countFullyScrapedGames());
        stats.setPartiallyScraped(gameMapper.countPartiallyScrapedGames());
        stats.setNotScraped(gameMapper.countNotScrapedGames());
        stats.setTotalPlatforms(platformService.getAllPlatforms().size());
        return stats;
    }
    
    @Override
    public List<PlatformStatistics> getPlatformStatistics() {
        return gameMapper.selectPlatformStatistics();
    }
    
    @Override
    public List<Game> getGamesByScrapeStatus(Long platformId, String status, int page, int size) {
        int offset = (page - 1) * size;
        List<Game> games = gameMapper.selectGamesByScrapeStatus(platformId, status, offset, size);
        // 为每个游戏设置platformPath
        if (platformId != null) {
            Platform platform = platformService.getPlatformById(platformId);
            if (platform != null) {
                String platformPath = platform.getFolderPath();
                for (Game game : games) {
                    game.setPlatformPath(platformPath);
                }
            }
        }
        return games;
    }


    @Override
    public int updateGame(Game game) {
        return gameMapper.updateGame(game);
    }
    
    /**
     * 更新指定平台下所有游戏的PLATFORM_PATH值
     * @param platformId 平台ID
     * @param platformPath 平台路径
     */
    private void updatePlatformPathForAllGames(Long platformId, String platformPath) {
        logger.info("========== 更新PLATFORM_PATH开始 ==========");
        logger.info("platformId: {}", platformId);
        logger.info("platformPath: {}", platformPath);
        if (platformId != null && platformPath != null && !platformPath.isEmpty()) {
            gameMapper.updatePlatformPathForAllGames(platformId, platformPath);
            logger.info("已更新平台ID {} 下所有游戏的PLATFORM_PATH值为: {}", platformId, platformPath);
        } else {
            logger.info("跳过更新：platformId或platformPath为null或空");
        }
        logger.info("========== 更新PLATFORM_PATH结束 ==========");
    }

    @Override
    public List<Game> getGamesByFilter(Map<String, Object> filterParams) {
        List<Game> games = gameMapper.selectGamesByFilter(filterParams);
        // 为每个游戏设置platformPath
        Long platformId = (Long) filterParams.get("platformId");
        if (platformId != null) {
            Platform platform = platformService.getPlatformById(platformId);
            if (platform != null) {
                String platformPath = platform.getFolderPath();
                for (Game game : games) {
                    game.setPlatformPath(platformPath);
                }
            }
        }
        return games;
    }
    
    @Override
    public long getGamesCountByFilter(Map<String, Object> filterParams) {
        return gameMapper.countGamesByFilter(filterParams);
    }
    
    @Override
    public ImportStatistics importGamesFromXml(String filePath) {
        ImportStatistics stats = new ImportStatistics();
        logger.info("开始从文件导入游戏: {}", filePath);
        try {
            logger.info("步骤1: 检查文件是否存在");
            // 检查文件是否存在
            File file = new File(filePath);
            if (!file.exists()) {
                logger.error("文件不存在: {}", filePath);
                throw new RuntimeException("导入失败: 文件不存在");
            }
            logger.info("文件存在，路径: {}", file.getAbsolutePath());
            logger.info("文件可读: {}", file.canRead());
            logger.info("文件大小: {} bytes", file.length());
            
            logger.info("步骤2: 开始解析XML并检测平台类型");
            GameListParser.PlatformSpecificGameList platformSpecificResult = GameListParser.parseGameListWithPlatformDetection(file);
            GameListXml gameListXml = platformSpecificResult.getGameListXml();
            String platformType = platformSpecificResult.getPlatformType();
            logger.info("XML解析完成，gameListXml: {}", gameListXml != null ? "非null" : "null");
            logger.info("识别的平台类型: {}", platformType);
            
            if (gameListXml != null) {
                logger.info("gameListXml.getProvider(): {}", gameListXml.getProvider() != null ? "非null" : "null");
                if (gameListXml.getProvider() != null) {
                    logger.info("gameListXml.getProvider().getSystem(): {}", gameListXml.getProvider().getSystem());
                }
                logger.info("gameListXml.getGame(): {}", gameListXml.getGame() != null ? "非null" : "null");
                if (gameListXml.getGame() != null) {
                    logger.info("游戏数量: {}", gameListXml.getGame().size());
                }
            }
            
            logger.info("步骤3: 获取父目录名称");
            // 安全获取父目录名称
            String parentDirName = "Unknown";
            java.io.File parentFile = file.getParentFile();
            if (parentFile != null) {
                parentDirName = parentFile.getName();
                logger.info("获取父目录名称: {}", parentDirName);
            }
            
            logger.info("步骤4: 开始导入游戏数据");
            logger.info("调用importGamesFromXml(gameListXml, parentDirName, filePath, platformType)");
            importGamesFromXml(gameListXml, parentDirName, filePath, platformType);
            logger.info("importGamesFromXml调用完成");
            
            logger.info("步骤5: 增加平台计数");
            stats.incrementPlatforms();
            logger.info("平台导入完成，增加平台计数: {}", stats.getImportedPlatforms());
            
            logger.info("步骤6: 处理游戏计数");
            // 添加空指针检查
            if (gameListXml.getGame() != null) {
                int gameCount = gameListXml.getGame().size();
                stats.addGames(gameCount);
                logger.info("游戏导入完成，增加游戏计数: {}", gameCount);
            } else {
                stats.addGames(0);
                logger.info("游戏列表为空，添加游戏计数: 0");
            }
            
            logger.info("步骤7: 导入完成");
            logger.info("导入完成，统计信息: 平台数={}, 游戏数={}, 平台类型={}", 
                    stats.getImportedPlatforms(), stats.getImportedGames(), platformType);
        } catch (Exception e) {
            logger.error("从文件导入游戏失败: {}", filePath, e);
            // 确保异常信息不为null
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            logger.error("导入失败原因: {}", errorMsg);
            logger.error("异常类型: {}", e.getClass().getName());
            // 打印完整的异常堆栈信息
            try {
                try (StringWriter sw = new StringWriter();
                     PrintWriter pw = new PrintWriter(sw)) {
                    e.printStackTrace(pw);
                    logger.error("异常堆栈信息: {}", sw.toString());
                }
            } catch (java.io.IOException ex) {
                logger.error("记录异常堆栈失败: {}", ex.getMessage());
            }
            throw new RuntimeException("导入失败: " + errorMsg, e);
        }
        return stats;
    }

    @Override
    public ImportStatistics importGamesFromXml(String filePath, boolean metadataOnly, int threadCount) {
        ImportStatistics stats = new ImportStatistics();
        logger.info("开始从文件导入游戏(带参数): {}, metadataOnly: {}, threadCount: {}", filePath, metadataOnly, threadCount);
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                logger.error("文件不存在: {}", filePath);
                throw new RuntimeException("导入失败: 文件不存在");
            }

            GameListParser.PlatformSpecificGameList platformSpecificResult = GameListParser.parseGameListWithPlatformDetection(file);
            GameListXml gameListXml = platformSpecificResult.getGameListXml();
            String platformType = platformSpecificResult.getPlatformType();

            String parentDirName = "Unknown";
            java.io.File parentFile = file.getParentFile();
            if (parentFile != null) {
                parentDirName = parentFile.getName();
            }

            importGamesFromXml(gameListXml, parentDirName, filePath, platformType, metadataOnly, threadCount);

            stats.incrementPlatforms();
            if (gameListXml.getGame() != null) {
                stats.addGames(gameListXml.getGame().size());
            }
        } catch (Exception e) {
            logger.error("从文件导入游戏失败: {}", filePath, e);
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            throw new RuntimeException("导入失败: " + errorMsg, e);
        }
        return stats;
    }

    @Override
    public ImportStatistics importGamesFromPegasusMetadata(String filePath, boolean metadataOnly, int threadCount) {
        ImportStatistics stats = new ImportStatistics();
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new RuntimeException("文件不存在: " + filePath);
            }

            List<PegasusMetadataParser.GameCollection> pegasusCollections = PegasusMetadataParser.parseMetadata(file);

            String timestamp = String.valueOf(System.currentTimeMillis());
            String importType = "pegasus";

            for (PegasusMetadataParser.GameCollection pegasusCollection : pegasusCollections) {
                String folderName = "Unknown";
                if (file != null && file.getParentFile() != null) {
                    folderName = file.getParentFile().getName();
                }

                String platformName = "export_" + timestamp + "_" + importType + "_" + folderName;

                Platform platform = new Platform();
                platform.setSystem(platformName);
                platform.setName(platformName);
                platform.setSortBy(pegasusCollection.getSortBy());
                platform.setLaunch(pegasusCollection.getLaunch());
                platform.setSoftware("WebGamelistOper");
                platform.setDatabase("Custom Database");
                platform.setWeb("http://localhost:8083");

                File metadataFile = new File(filePath);
                File parentFolder = metadataFile.getParentFile();
                if (parentFolder != null) {
                    String folderPath = parentFolder.getAbsolutePath();
                    platform.setFolderPath(folderPath);
                }

                Platform existingPlatform = platformService.getPlatformBySystem(platformName);
                if (existingPlatform == null) {
                    platformMapper.insertPlatform(platform);
                } else {
                    platform.setId(existingPlatform.getId());
                    platformMapper.updatePlatform(platform);
                }

                List<Game> games = new ArrayList<>();
                for (PegasusMetadataParser.Game pegasusGame : pegasusCollection.getGames()) {
                    List<Game> gameList = convertToGameModelsFromPegasus(pegasusGame, platform.getId(), filePath, metadataOnly, platform.getFolderPath());
                    games.addAll(gameList);
                }

                logger.info("========== 开始更新PLATFORM_PATH ==========");
                logger.info("platform.getId(): {}", platform.getId());
                logger.info("platform.getFolderPath(): {}", platform.getFolderPath());
                if (!games.isEmpty()) {
                    importGamesInBatches(games, platform, threadCount);
                }

                // 更新该平台下所有游戏的PLATFORM_PATH值
                updatePlatformPathForAllGames(platform.getId(), platform.getFolderPath());
                logger.info("========== 更新PLATFORM_PATH完成 ==========");

                stats.incrementPlatforms();
                stats.addGames(games.size());
            }
        } catch (Exception e) {
            logger.error("从文件导入Pegasus元数据失败: {}", filePath, e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
            throw new RuntimeException("导入失败: " + errorMessage, e);
        }
        return stats;
    }

    /**
     * 从指定文件路径导入游戏列表，当provider为null时使用文件夹名作为平台名
     * @param gameListXml 游戏列表XML对象
     * @param defaultPlatformName 默认平台名称
     * @param gameListFilePath gamelist.xml文件的绝对路径，用于将相对路径转换为绝对路径
     * @param platformType 平台类型
     * @param metadataOnly 只使用数据文件标签，不进行自动匹配
     * @param threadCount 线程数
     */
    public void importGamesFromXml(GameListXml gameListXml, String defaultPlatformName, String gameListFilePath, String platformType, boolean metadataOnly, int threadCount) {
        if (gameListXml == null) {
            logger.error("导入失败: gameListXml为null");
            throw new RuntimeException("导入失败: gameListXml为null");
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String importType = "gamelist";
        String standardizedPlatformName = "export_" + timestamp + "_" + importType + "_" + defaultPlatformName;

        Platform platform = null;
        try {
            platform = platformService.savePlatform(gameListXml.getProvider(), standardizedPlatformName);

            if (platform != null && gameListFilePath != null) {
                File gameListFile = new File(gameListFilePath);
                File parentFolder = gameListFile.getParentFile();
                if (parentFolder != null) {
                    String folderPath = parentFolder.getAbsolutePath();
                    platform.setFolderPath(folderPath);
                    platformService.updatePlatform(platform);
                }
            }
        } catch (Exception e) {
            logger.error("平台保存失败: {}", e.getMessage(), e);
            throw new RuntimeException("平台保存失败: " + e.getMessage(), e);
        }

        if (platform == null) {
            logger.error("导入失败: 平台保存后返回null");
            throw new RuntimeException("导入失败: 平台保存后返回null");
        }

        List<Game> games = new ArrayList<>();

        if (gameListXml.getGame() != null) {
            for (GameListXml.GameXml gameXml : gameListXml.getGame()) {
                Game game = convertToGameModel(gameXml, platform.getId(), gameListFilePath, platformType, metadataOnly, platform.getFolderPath());
                games.add(game);
            }
        }

        if (!games.isEmpty()) {
            importGamesInBatches(games, platform, threadCount);
        }

        // 更新该平台下所有游戏的PLATFORM_PATH值
        logger.info("========== 开始更新PLATFORM_PATH ==========");
        logger.info("platform.getId(): {}", platform.getId());
        logger.info("platform.getFolderPath(): {}", platform.getFolderPath());
        updatePlatformPathForAllGames(platform.getId(), platform.getFolderPath());
        logger.info("========== 更新PLATFORM_PATH完成 ==========");
    }

    /**
     * 从指定文件路径导入游戏列表，当provider为null时使用文件夹名作为平台名
     * @param gameListXml 游戏列表XML对象
     * @param defaultPlatformName 默认平台名称
     * @param gameListFilePath gamelist.xml文件的绝对路径，用于将相对路径转换为绝对路径
     * @param platformType 平台类型
     */
    public void importGamesFromXml(GameListXml gameListXml, String defaultPlatformName, String gameListFilePath, String platformType) {
        importGamesFromXml(gameListXml, defaultPlatformName, gameListFilePath, platformType, false, 1);
    }

    @Override
    public ImportStatistics importGamesFromPegasusMetadata(String filePath) {
        ImportStatistics stats = new ImportStatistics();
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new RuntimeException("文件不存在: " + filePath);
            }
            
            // 解析Pegasus元数据文件
            List<PegasusMetadataParser.GameCollection> pegasusCollections = PegasusMetadataParser.parseMetadata(file);
            
            // 生成时间戳
            String timestamp = String.valueOf(System.currentTimeMillis());
            String importType = "pegasus";
            
            // 处理每个平台数据
            for (PegasusMetadataParser.GameCollection pegasusCollection : pegasusCollections) {
                String folderName = "Unknown";
                if (file != null && file.getParentFile() != null) {
                    folderName = file.getParentFile().getName();
                }
                
                // 生成标准平台名称：export_时间戳_pegasus_文件夹名
                String platformName = "export_" + timestamp + "_" + importType + "_" + folderName;
                
                // 保存platform信息
                Platform platform = new Platform();
                platform.setSystem(platformName);
                platform.setName(platformName);
                platform.setSortBy(pegasusCollection.getSortBy());
                platform.setLaunch(pegasusCollection.getLaunch());
                platform.setSoftware("WebGamelistOper");
                platform.setDatabase("Custom Database");
                platform.setWeb("http://localhost:8083");
                
                // 设置平台的folderPath为metadata.pegasus.txt文件所在的文件夹路径
                File metadataFile = new File(filePath);
                File parentFolder = metadataFile.getParentFile();
                if (parentFolder != null) {
                    String folderPath = parentFolder.getAbsolutePath();
                    platform.setFolderPath(folderPath);
                }
                
                // 检查platform是否已存在
                Platform existingPlatform = platformService.getPlatformBySystem(platformName);
                if (existingPlatform == null) {
                    platformMapper.insertPlatform(platform);
                } else {
                    platform.setId(existingPlatform.getId());
                    platformMapper.updatePlatform(platform);
                }
                
                // 转换游戏数据并保存
                List<Game> games = new ArrayList<>();
                for (PegasusMetadataParser.Game pegasusGame : pegasusCollection.getGames()) {
                    List<Game> gameList = convertToGameModelsFromPegasus(pegasusGame, platform.getId(), filePath, false, platform.getFolderPath());
                    games.addAll(gameList);
                }
                
                // 批量保存游戏
                if (!games.isEmpty()) {
                    importGamesInBatches(games, platform);
                }
                
                stats.incrementPlatforms();
                stats.addGames(games.size());
            }
        } catch (Exception e) {
            logger.error("从文件导入Pegasus元数据失败: {}", filePath, e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
            throw new RuntimeException("导入失败: " + errorMessage, e);
        }
        return stats;
    }
    
    /**
     * 截断字符串，确保不超过指定长度
     */
    private String truncateString(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() > maxLength) {
            return value.substring(0, maxLength);
        }
        return value;
    }
    
    /**
     * 将异常堆栈转换为字符串
     */
    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.toString()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("    at ").append(element.toString()).append("\n");
            // 只显示前10行堆栈，避免信息过长
            if (sb.length() > 1000) {
                sb.append("    ... 更多堆栈信息省略 ...");
                break;
            }
        }
        return sb.toString();
    }
    
    /**
     * 格式化描述字段：去除多余换行、空格
     */
    private String formatDescription(String desc) {
        if (desc == null) {
            return null;
        }
        // 去除多余的换行和空格
        return desc.replaceAll("\\s+\\n\\s+", " ")
                   .replaceAll("\\n+", " ")
                   .replaceAll("\\s+", " ")
                   .trim();
    }
    
    @Override
    public Map<String, List<String>> getUniqueGameValues() {
        Map<String, List<String>> uniqueValues = new java.util.HashMap<>();
        uniqueValues.put("developers", gameMapper.selectUniqueDevelopers());
        uniqueValues.put("publishers", gameMapper.selectUniquePublishers());
        uniqueValues.put("genres", gameMapper.selectUniqueGenres());
        uniqueValues.put("players", gameMapper.selectUniquePlayers());
        return uniqueValues;
    }
    
    @Override
    public Map<String, Object> mergeDiscs(List<?> gameIds) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        try {
            // 验证游戏数量
            if (gameIds == null || gameIds.size() < 2) {
                result.put("success", false);
                result.put("errorMessage", "至少需要选择两个游戏进行合盘操作");
                return result;
            }
            
            // 获取选中的游戏
            java.util.List<Game> games = new java.util.ArrayList<>();
            for (Object gameIdObj : gameIds) {
                Long gameId = null;
                if (gameIdObj instanceof Integer) {
                    gameId = ((Integer) gameIdObj).longValue();
                } else if (gameIdObj instanceof Long) {
                    gameId = (Long) gameIdObj;
                } else {
                    continue; // 跳过无效的游戏ID
                }
                Game game = gameMapper.selectGameById(gameId);
                if (game != null) {
                    games.add(game);
                }
            }
            
            if (games.size() < 2) {
                result.put("success", false);
                result.put("errorMessage", "选中的游戏中至少有两个游戏不存在");
                return result;
            }
            
            // 以第一个游戏为基准创建新游戏
            Game baseGame = games.get(0);
            Game newGame = new Game();
            newGame.setName(baseGame.getName());
            newGame.setDesc(baseGame.getDesc());
            newGame.setTranslatedName(baseGame.getTranslatedName());
            newGame.setTranslatedDesc(baseGame.getTranslatedDesc());
            newGame.setDeveloper(baseGame.getDeveloper());
            newGame.setPublisher(baseGame.getPublisher());
            newGame.setGenre(baseGame.getGenre());
            newGame.setPlayers(baseGame.getPlayers());
            newGame.setReleasedate(baseGame.getReleasedate());
            newGame.setRating(baseGame.getRating());
            newGame.setLang(baseGame.getLang());
            newGame.setPlatformId(baseGame.getPlatformId());
            // 确保source字段不为null
            String source = baseGame.getSource();
            if (source == null || source.isEmpty()) {
                source = "merged";
            }
            newGame.setSource(source);
            
            // 确保gameId不为null
            String newGameId = baseGame.getGameId();
            if (newGameId == null || newGameId.isEmpty()) {
                newGameId = "game_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
            }
            // 确保gameId长度不超过50个字符
            if (newGameId.length() > 50) {
                String timestamp = String.valueOf(System.currentTimeMillis());
                newGameId = newGameId.substring(0, 40) + "_" + timestamp.substring(timestamp.length() - 8);
            }
            newGame.setGameId(newGameId);
            
            // 复制媒体文件路径
            newGame.setImage(baseGame.getImage());
            newGame.setVideo(baseGame.getVideo());
            newGame.setMarquee(baseGame.getMarquee());
            newGame.setThumbnail(baseGame.getThumbnail());
            newGame.setManual(baseGame.getManual());
            newGame.setBoxFront(baseGame.getBoxFront());
            newGame.setBoxBack(baseGame.getBoxBack());
            newGame.setBoxSpine(baseGame.getBoxSpine());
            newGame.setBoxFull(baseGame.getBoxFull());
            newGame.setCartridge(baseGame.getCartridge());
            newGame.setLogo(baseGame.getLogo());
            newGame.setBezel(baseGame.getBezel());
            newGame.setPanel(baseGame.getPanel());
            newGame.setCabinetLeft(baseGame.getCabinetLeft());
            newGame.setCabinetRight(baseGame.getCabinetRight());
            newGame.setTile(baseGame.getTile());
            newGame.setBanner(baseGame.getBanner());
            newGame.setSteam(baseGame.getSteam());
            newGame.setPoster(baseGame.getPoster());
            newGame.setBackground(baseGame.getBackground());
            newGame.setMusic(baseGame.getMusic());
            newGame.setScreenshot(baseGame.getScreenshot());
            newGame.setTitlescreen(baseGame.getTitlescreen());
            newGame.setBox3d(baseGame.getBox3d());
            newGame.setSteamgrid(baseGame.getSteamgrid());
            newGame.setFanart(baseGame.getFanart());
            newGame.setBoxtexture(baseGame.getBoxtexture());
            newGame.setSupporttexture(baseGame.getSupporttexture());
            
            // 复制其他属性
            newGame.setCrc32(baseGame.getCrc32());
            newGame.setMd5(baseGame.getMd5());
            newGame.setGenreid(baseGame.getGenreid());
            newGame.setHash(baseGame.getHash());
            newGame.setPlatformType(baseGame.getPlatformType());
            newGame.setSortBy(baseGame.getSortBy());
            newGame.setPlatformSpecificFields(baseGame.getPlatformSpecificFields());
            newGame.setScraped(baseGame.getScraped());
            newGame.setEdited(baseGame.getEdited());
            newGame.setExists(true); // 合盘成功后，exists 肯定是 true
            
            // 提取平台信息，找到gamelist.xml文件的位置
            Long platformId = baseGame.getPlatformId();
            Platform platform = platformService.getPlatformById(platformId);
            if (platform == null) {
                result.put("success", false);
                result.put("errorMessage", "无法获取平台信息");
                return result;
            }
            
            // 获取平台的folderPath，这应该是gamelist.xml所在的目录
            String platformFolderPath = platform.getFolderPath();
            if (platformFolderPath == null || platformFolderPath.isEmpty()) {
                // 如果平台没有folderPath，使用第一个游戏的父目录
                String basePath = baseGame.getAbsolutePath();
                if (basePath == null || basePath.isEmpty()) {
                    basePath = baseGame.getPath();
                }
                java.io.File baseFile = new java.io.File(basePath);
                java.io.File parentDir = baseFile.getParentFile();
                if (parentDir == null) {
                    result.put("success", false);
                    result.put("errorMessage", "无法确定游戏文件的父目录");
                    return result;
                }
                platformFolderPath = parentDir.getAbsolutePath();
            }
            
            // 输出详细调试信息
            logger.info("platformFolderPath: " + platformFolderPath);
            
            // 创建 m3u 文件名
            String gameName = baseGame.getName().replaceAll("[<>\"/\\|?*]", "_");
            logger.info("gameName 处理后: [" + gameName + "]");
            String m3uFileName = gameName + ".m3u";
            logger.info("m3uFileName: [" + m3uFileName + "]");
            java.io.File m3uFile = new java.io.File(platformFolderPath, m3uFileName);
            logger.info("m3uFile 绝对路径: " + m3uFile.getAbsolutePath());
            
            // 检查目录是否存在，如果不存在则创建
            java.io.File m3uDir = m3uFile.getParentFile();
            if (!m3uDir.exists()) {
                if (!m3uDir.mkdirs()) {
                    result.put("success", false);
                    result.put("errorMessage", "无法创建目录：" + m3uDir.getAbsolutePath());
                    return result;
                }
            }
            
            // 检查目录是否可写
            if (!m3uDir.canWrite()) {
                result.put("success", false);
                result.put("errorMessage", "目录没有写入权限：" + m3uDir.getAbsolutePath());
                return result;
            }
            
            // 检查文件是否已经存在，如果存在则先删除
            if (m3uFile.exists()) {
                if (!m3uFile.delete()) {
                    result.put("success", false);
                    result.put("errorMessage", "无法删除已存在的 m3u 文件：" + m3uFile.getAbsolutePath());
                    return result;
                }
            }
            
            // 写入 m3u 文件内容，使用相对路径
            try {
                // 输出调试信息
                logger.info("准备创建 m3u 文件，路径: " + m3uFile.getAbsolutePath());
                
                // 尝试直接创建文件
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(m3uFile);
                     java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(fos, java.nio.charset.StandardCharsets.UTF_8);
                     java.io.BufferedWriter writer = new java.io.BufferedWriter(osw)) {
                    for (int i = 0; i < games.size(); i++) {
                        Game game = games.get(i);
                        String gamePath = game.getAbsolutePath();
                        if (gamePath == null || gamePath.isEmpty()) {
                            gamePath = game.getPath();
                        }
                        
                        // 计算相对路径
                        java.io.File gameFile = new java.io.File(gamePath);
                        String relativePath = getRelativePath(m3uFile, gameFile);
                        logger.info("游戏文件: " + gameFile.getAbsolutePath());
                        logger.info("相对路径: " + relativePath);
                        
                        writer.write(relativePath);
                        writer.newLine();
                    }
                }
                logger.info("m3u 文件创建成功: " + m3uFile.getAbsolutePath());
            } catch (java.io.IOException e) {
                logger.error("创建 m3u 文件失败", e);
                result.put("success", false);
                result.put("errorMessage", "创建 m3u 文件失败：" + e.getMessage() + "，路径：" + m3uFile.getAbsolutePath());
                return result;
            }
            
            // 设置新游戏的路径为 m3u 文件的相对路径，使用 ./ 格式
            newGame.setPath("./" + m3uFileName);
            newGame.setAbsolutePath(m3uFile.getAbsolutePath());
            // 合盘成功后，exists 肯定是 true
            newGame.setExists(true);
            
            // 保存新游戏
            gameMapper.insertGame(newGame);
            
            // 删除选中的游戏条目
            for (Object gameIdObj : gameIds) {
                Long gameId = null;
                if (gameIdObj instanceof Integer) {
                    gameId = ((Integer) gameIdObj).longValue();
                } else if (gameIdObj instanceof Long) {
                    gameId = (Long) gameIdObj;
                } else {
                    continue; // 跳过无效的游戏ID
                }
                gameMapper.deleteGameById(gameId);
            }
            
            result.put("success", true);
            result.put("newGameId", newGame.getId());
            result.put("m3uFilePath", m3uFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("合盘操作失败", e);
            result.put("success", false);
            result.put("errorMessage", "合盘操作失败：" + e.getMessage());
        }
        return result;
    }
    
    /**
     * 计算两个文件之间的相对路径
     */
    private String getRelativePath(java.io.File fromFile, java.io.File toFile) {
        try {
            java.io.File fromDir = fromFile.getParentFile();
            if (fromDir == null) {
                fromDir = fromFile;
            }
            
            java.nio.file.Path fromPath = fromDir.toPath();
            java.nio.file.Path toPath = toFile.toPath();
            java.nio.file.Path relativePath = fromPath.relativize(toPath);
            
            // 将路径转换为正斜杠格式，确保兼容性
            return relativePath.toString().replace('\\', '/');
        } catch (Exception e) {
            logger.error("计算相对路径失败", e);
            // 如果计算失败，返回文件名
            return toFile.getName();
        }
    }

    /**
     * 计算两个路径字符串之间的相对路径
     */
    private String getRelativePath(String fullPath, String baseDir) {
        try {
            if (fullPath == null || baseDir == null) {
                return fullPath;
            }
            
            // 确保路径格式一致
            String normalizedFullPath = fullPath.replace('\\', '/');
            String normalizedBaseDir = baseDir.replace('\\', '/');
            
            // 确保 baseDir 以 / 结尾，以便正确处理
            if (!normalizedBaseDir.endsWith("/")) {
                normalizedBaseDir = normalizedBaseDir + "/";
            }
            
            if (normalizedFullPath.startsWith(normalizedBaseDir)) {
                String relativePath = normalizedFullPath.substring(normalizedBaseDir.length());
                return relativePath;
            }
            
            // 如果不匹配，返回原始路径
            return normalizedFullPath;
        } catch (Exception e) {
            logger.error("计算相对路径失败", e);
            return fullPath;
        }
    }
    
    // 检查字符串是否包含特殊字符
    private boolean containsSpecialCharacters(String str) {
        // 检查是否包含非 ASCII 字符或控制字符
        for (char c : str.toCharArray()) {
            if (c < 32 || c > 126) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int batchUpdateGames(List<Long> gameIds, Map<String, Object> updates) {
        if (gameIds == null || gameIds.isEmpty()) {
            logger.warn("批量更新游戏：游戏ID列表为空");
            return 0;
        }
        if (updates == null || updates.isEmpty()) {
            logger.warn("批量更新游戏：更新内容为空");
            return 0;
        }
        
        int updatedCount = 0;
        try {
            for (Long gameId : gameIds) {
                if (gameId == null) {
                    continue;
                }
                Game game = gameMapper.selectGameById(gameId);
                if (game != null) {
                    if (updates.containsKey("developer")) {
                        game.setDeveloper((String) updates.get("developer"));
                    }
                    if (updates.containsKey("publisher")) {
                        game.setPublisher((String) updates.get("publisher"));
                    }
                    if (updates.containsKey("genre")) {
                        game.setGenre((String) updates.get("genre"));
                    }
                    if (updates.containsKey("players")) {
                        game.setPlayers((String) updates.get("players"));
                    }
                    if (updates.containsKey("lang")) {
                        game.setLang((String) updates.get("lang"));
                    }
                    game.setEdited(true);
                    int result = gameMapper.updateGame(game);
                    if (result > 0) {
                        updatedCount++;
                    }
                }
            }
            logger.info("批量更新游戏：成功更新 {} 个游戏", updatedCount);
        } catch (Exception e) {
            logger.error("批量更新游戏失败：", e);
            throw e;
        }
        return updatedCount;
    }
    
    @Override
    public int migrateGames(List<Long> gameIds, Long targetPlatformId) {
        if (gameIds == null || gameIds.isEmpty()) {
            logger.warn("迁移游戏：游戏ID列表为空");
            return 0;
        }
        if (targetPlatformId == null) {
            logger.warn("迁移游戏：目标平台ID为空");
            return 0;
        }
        
        Platform targetPlatform = platformMapper.selectPlatformById(targetPlatformId);
        if (targetPlatform == null) {
            logger.warn("迁移游戏：目标平台不存在，platformId={}", targetPlatformId);
            return 0;
        }
        
        int migratedCount = 0;
        try {
            for (Long gameId : gameIds) {
                if (gameId == null) {
                    continue;
                }
                Game game = gameMapper.selectGameById(gameId);
                if (game != null) {
                    Long oldPlatformId = game.getPlatformId();
                    game.setPlatformId(targetPlatformId);
                    game.setPlatformPath(targetPlatform.getFolderPath());
                    game.setEdited(true);
                    int result = gameMapper.updateGame(game);
                    if (result > 0) {
                        migratedCount++;
                        logger.info("迁移游戏：gameId={} 从 platformId={} 迁移到 platformId={}", 
                            gameId, oldPlatformId, targetPlatformId);
                    }
                }
            }
            logger.info("迁移游戏：成功迁移 {} 个游戏到平台 {}", migratedCount, targetPlatformId);
        } catch (Exception e) {
            logger.error("迁移游戏失败：", e);
            throw e;
        }
        return migratedCount;
    }
    
    @Override
    public int addGame(Game game) {
        if (game == null) {
            logger.warn("新增游戏：游戏对象为空");
            return 0;
        }
        
        // 设置编辑标志
        game.setEdited(true);
        
        // 插入游戏记录
        int result = gameMapper.insertGame(game);
        if (result > 0) {
            logger.info("新增游戏成功，gameId={}, gameName={}", game.getId(), game.getName());
        } else {
            logger.warn("新增游戏失败，gameName={}", game.getName());
        }
        return result;
    }
    
    @Override
    public int batchDeleteGames(List<?> gameIds) {
        if (gameIds == null || gameIds.isEmpty()) {
            logger.warn("批量删除游戏：游戏ID列表为空");
            return 0;
        }

        int deletedCount = 0;
        try {
            for (Object gameIdObj : gameIds) {
                if (gameIdObj == null) {
                    continue;
                }
                Long gameId = convertToLong(gameIdObj);
                if (gameId == null) {
                    logger.warn("无法将游戏ID转换为Long类型: {}", gameIdObj);
                    continue;
                }
                int result = gameMapper.deleteGameById(gameId);
                if (result > 0) {
                    deletedCount++;
                    logger.info("删除游戏成功，gameId={}", gameId);
                }
            }
        } catch (Exception e) {
            logger.error("批量删除游戏失败", e);
            throw e;
        }

        logger.info("批量删除游戏完成，共删除 {} 个游戏", deletedCount);
        return deletedCount;
    }

    private Long convertToLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Long) {
            return (Long) obj;
        }
        if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public ImportStatistics importGamesFromXml(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount) {
        ImportStatistics stats = new ImportStatistics();
        logger.info("开始从文件导入游戏(带模板): {}, importMethod: {}, importTemplate: {}, metadataOnly: {}, threadCount: {}", 
                filePath, importMethod, importTemplate, metadataOnly, threadCount);
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                logger.error("文件不存在: {}", filePath);
                throw new RuntimeException("导入失败: 文件不存在");
            }

            GameListParser.PlatformSpecificGameList platformSpecificResult = GameListParser.parseGameListWithPlatformDetection(file);
            GameListXml gameListXml = platformSpecificResult.getGameListXml();
            String platformType = platformSpecificResult.getPlatformType();

            String parentDirName = "Unknown";
            java.io.File parentFile = file.getParentFile();
            if (parentFile != null) {
                parentDirName = parentFile.getName();
            }

            importGamesFromXml(gameListXml, parentDirName, filePath, platformType, metadataOnly, threadCount);

            stats.incrementPlatforms();
            if (gameListXml.getGame() != null) {
                stats.addGames(gameListXml.getGame().size());
            }
        } catch (Exception e) {
            logger.error("从文件导入游戏失败: {}", filePath, e);
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
            throw new RuntimeException("导入失败: " + errorMsg, e);
        }
        return stats;
    }

    @Override
    public ImportStatistics importGamesFromPegasusMetadata(String filePath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount) {
        ImportStatistics stats = new ImportStatistics();
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new RuntimeException("文件不存在: " + filePath);
            }

            List<PegasusMetadataParser.GameCollection> pegasusCollections = PegasusMetadataParser.parseMetadata(file);

            String timestamp = String.valueOf(System.currentTimeMillis());
            String importType = "pegasus";

            for (PegasusMetadataParser.GameCollection pegasusCollection : pegasusCollections) {
                String folderName = "Unknown";
                if (file != null && file.getParentFile() != null) {
                    folderName = file.getParentFile().getName();
                }

                String platformName = "export_" + timestamp + "_" + importType + "_" + folderName;

                Platform platform = new Platform();
                platform.setSystem(platformName);
                platform.setName(platformName);
                platform.setSortBy(pegasusCollection.getSortBy());
                platform.setLaunch(pegasusCollection.getLaunch());
                platform.setSoftware("WebGamelistOper");
                platform.setDatabase("Custom Database");
                platform.setWeb("http://localhost:8083");

                File metadataFile = new File(filePath);
                File parentFolder = metadataFile.getParentFile();
                if (parentFolder != null) {
                    String folderPath = parentFolder.getAbsolutePath();
                    platform.setFolderPath(folderPath);
                }

                Platform existingPlatform = platformService.getPlatformBySystem(platformName);
                if (existingPlatform == null) {
                    platformMapper.insertPlatform(platform);
                } else {
                    platform.setId(existingPlatform.getId());
                    platformMapper.updatePlatform(platform);
                }

                List<Game> games = new ArrayList<>();
                for (PegasusMetadataParser.Game pegasusGame : pegasusCollection.getGames()) {
                    List<Game> gameList = convertToGameModelsFromPegasus(pegasusGame, platform.getId(), filePath, metadataOnly, platform.getFolderPath());
                    games.addAll(gameList);
                }

                logger.info("========== 开始更新PLATFORM_PATH ==========");
                logger.info("platform.getId(): {}", platform.getId());
                logger.info("platform.getFolderPath(): {}", platform.getFolderPath());
                if (!games.isEmpty()) {
                    importGamesInBatches(games, platform, threadCount);
                }

                // 更新该平台下所有游戏的PLATFORM_PATH值
                updatePlatformPathForAllGames(platform.getId(), platform.getFolderPath());
                logger.info("========== 更新PLATFORM_PATH完成 ==========");

                stats.incrementPlatforms();
                stats.addGames(games.size());
            }
        } catch (Exception e) {
            logger.error("从文件导入Pegasus元数据失败: {}", filePath, e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
            throw new RuntimeException("导入失败: " + errorMessage, e);
        }
        return stats;
    }

    @Override
    public ImportStatistics importGamesFromGameFiles(String scanPath, String importMethod, String importTemplate, boolean metadataOnly, int threadCount) {
        ImportStatistics stats = new ImportStatistics();
        logger.info("开始从游戏文件导入: {}, importMethod: {}, importTemplate: {}, metadataOnly: {}, threadCount: {}", 
                scanPath, importMethod, importTemplate, metadataOnly, threadCount);
        try {
            File scanDir = new File(scanPath);
            if (!scanDir.exists() || !scanDir.isDirectory()) {
                logger.error("扫描路径不存在或不是目录: {}", scanPath);
                throw new RuntimeException("导入失败: 扫描路径不存在或不是目录");
            }

            // 读取导入模板
            ImportTemplate template = ImportTemplate.loadTemplate(importTemplate);
            if (template == null) {
                logger.error("无法加载导入模板: {}", importTemplate);
                throw new RuntimeException("导入失败: 无法加载导入模板");
            }

            // 扫描游戏文件
            List<File> gameFiles = scanGameFiles(scanDir, template.getGameExtensions());
            logger.info("找到 {} 个游戏文件", gameFiles.size());

            if (gameFiles.isEmpty()) {
                logger.warn("未找到游戏文件");
                return stats;
            }

            // 创建平台
            String platformName = scanDir.getName();
            String timestamp = String.valueOf(System.currentTimeMillis());
            String importType = "gamefiles";
            String standardizedPlatformName = "export_" + timestamp + "_" + importType + "_" + platformName;

            Platform platform = new Platform();
            platform.setSystem(standardizedPlatformName);
            platform.setName(standardizedPlatformName);
            platform.setFolderPath(scanDir.getAbsolutePath());
            platform.setSoftware("WebGamelistOper");
            platform.setDatabase("Custom Database");
            platform.setWeb("http://localhost:8083");

            Platform existingPlatform = platformService.getPlatformBySystem(standardizedPlatformName);
            if (existingPlatform == null) {
                platformMapper.insertPlatform(platform);
            } else {
                platform.setId(existingPlatform.getId());
                platformMapper.updatePlatform(platform);
            }

            // 处理游戏文件
            List<Game> games = new ArrayList<>();
            for (File gameFile : gameFiles) {
                Game game = createGameFromFile(gameFile, platform.getId(), scanDir.getAbsolutePath(), importMethod, template, metadataOnly);
                games.add(game);
            }

            if (!games.isEmpty()) {
                importGamesInBatches(games, platform, threadCount);
            }

            // 更新该平台下所有游戏的PLATFORM_PATH值
            updatePlatformPathForAllGames(platform.getId(), platform.getFolderPath());

            stats.incrementPlatforms();
            stats.addGames(games.size());
            logger.info("从游戏文件导入完成，平台数: {}, 游戏数: {}", stats.getImportedPlatforms(), stats.getImportedGames());
        } catch (Exception e) {
            logger.error("从游戏文件导入失败: {}", scanPath, e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
            throw new RuntimeException("导入失败: " + errorMessage, e);
        }
        return stats;
    }

    @Override
    public ImportStatistics importGamesFromFileScan(String scanPath, String fileExtensions, String importTemplate, int threadCount, Long taskId) {
        ImportStatistics stats = new ImportStatistics();
        logger.info("开始无数据文件导入: {}, 扩展名: {}, 模板: {}", scanPath, fileExtensions, importTemplate);

        try {
            File scanDir = new File(scanPath);
            if (!scanDir.exists() || !scanDir.isDirectory()) {
                logger.error("扫描路径不存在或不是目录: {}", scanPath);
                throw new RuntimeException("导入失败: 扫描路径不存在或不是目录");
            }

            // 读取导入模板
            ImportTemplate template = ImportTemplate.loadTemplate(importTemplate);
            if (template == null) {
                logger.error("无法加载导入模板: {}", importTemplate);
                throw new RuntimeException("导入失败: 无法加载导入模板");
            }

            // 解析文件扩展名
            List<String> extensionsList = new ArrayList<>();
            if (fileExtensions != null && !fileExtensions.isEmpty()) {
                String[] exts = fileExtensions.split(",");
                for (String ext : exts) {
                    String trimmed = ext.trim();
                    if (!trimmed.isEmpty()) {
                        if (trimmed.startsWith(".")) {
                            extensionsList.add(trimmed.substring(1).toLowerCase());
                        } else {
                            extensionsList.add(trimmed.toLowerCase());
                        }
                    }
                }
                logger.info("使用用户指定的扩展名: {}", extensionsList);
            }

            if (extensionsList.isEmpty()) {
                extensionsList = Arrays.asList("chd", "iso", "bin", "cue", "zip", "7z", "rar", "sfc", "nes", "gen", "md", "gb", "gba", "n64", "psx", "ps2", "psp", "wii", "wiiu", "switch", "xbox", "x360");
                logger.info("使用默认扩展名: {}", extensionsList);
            }

            // 创建平台（根目录名称作为平台名）
            String platformName = scanDir.getName();
            String standardizedPlatformName = "unknown_" + platformName;

            Platform platform = new Platform();
            platform.setSystem(standardizedPlatformName);
            platform.setName(standardizedPlatformName);
            platform.setFolderPath(scanDir.getAbsolutePath());
            platform.setSoftware("WebGamelistOper");
            platform.setDatabase("Custom Database");
            platform.setWeb("http://localhost:8083");

            Platform existingPlatform = platformService.getPlatformBySystem(standardizedPlatformName);
            if (existingPlatform == null) {
                platformMapper.insertPlatform(platform);
            } else {
                platform.setId(existingPlatform.getId());
                platformMapper.updatePlatform(platform);
            }

            // 检查根目录是否直接包含游戏文件
            int gameCount = 0;
            
            if (containsGameFiles(scanDir, extensionsList)) {
                // 检查根目录是否直接包含游戏文件
                List<File> gameFiles = findGameFilesInDirectory(scanDir, extensionsList);
                if (!gameFiles.isEmpty()) {
                    logger.info("根目录直接包含 {} 个游戏文件，为每个文件创建游戏对象", gameFiles.size());
                    List<Game> games = new ArrayList<>();
                    for (File gameFile : gameFiles) {
                        Game game = createGameFromFile(gameFile, platform.getId(), scanDir.getAbsolutePath(), template);
                        games.add(game);
                    }
                    importGamesInBatches(games, platform, threadCount);
                    gameCount = games.size();
                } else {
                    logger.info("根目录包含游戏文件但未找到具体文件");
                }
            } else {
                // 遍历根目录下的子文件夹，只将包含游戏文件的子文件夹作为游戏
                File[] subDirs = scanDir.listFiles(File::isDirectory);
                if (subDirs == null || subDirs.length == 0) {
                    logger.warn("根目录下没有子文件夹，也没有直接的游戏文件");
                    stats.incrementPlatforms();
                    return stats;
                }

                // 记录找到的子文件夹
                logger.info("找到 {} 个子文件夹:", subDirs.length);
                for (File dir : subDirs) {
                    logger.info("- {}", dir.getAbsolutePath());
                }

                List<Game> games = new ArrayList<>();
                int totalDirs = subDirs.length;
                int processed = 0;

                for (File gameDir : subDirs) {
                    try {
                        // 检查子文件夹中是否包含符合扩展名的游戏文件
                        if (containsGameFiles(gameDir, extensionsList)) {
                            Game game = createGameFromDirectory(gameDir, platform.getId(), scanDir.getAbsolutePath(), template);
                            games.add(game);
                        } else {
                            logger.debug("跳过非游戏目录: {}", gameDir.getAbsolutePath());
                        }

                        processed++;
                        if (taskId != null && processed % 10 == 0) {
                            int progress = (int) ((double) processed / totalDirs * 100);
                            logger.info("进度: {}/{} ({}%)", processed, totalDirs, progress);
                        }
                    } catch (Exception e) {
                        logger.error("处理游戏目录失败: {}", gameDir.getAbsolutePath(), e);
                    }
                }

                if (!games.isEmpty()) {
                    importGamesInBatches(games, platform, threadCount);
                    gameCount = games.size();
                }
            }

            // 更新该平台下所有游戏的PLATFORM_PATH值
            updatePlatformPathForAllGames(platform.getId(), platform.getFolderPath());

            stats.incrementPlatforms();
            stats.addGames(gameCount);
            logger.info("无数据文件导入完成，平台数: {}, 游戏数: {}", stats.getImportedPlatforms(), stats.getImportedGames());
        } catch (Exception e) {
            logger.error("无数据文件导入失败: {}", scanPath, e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
            throw new RuntimeException("导入失败: " + errorMessage, e);
        }
        return stats;
    }

    /**
     * 检查文件夹中是否包含游戏文件（递归检查）
     */
    private boolean containsGameFiles(File directory, List<String> extensions) {
        if (directory == null || !directory.isDirectory()) {
            return false;
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return false;
        }
        
        logger.debug("检查目录: {}, 扩展名: {}", directory.getAbsolutePath(), extensions);
        
        for (File file : files) {
            if (file.isFile()) {
                String fileName = file.getName().toLowerCase();
                logger.debug("检查文件: {}", fileName);
                for (String ext : extensions) {
                    if (fileName.endsWith("." + ext)) {
                        logger.info("找到匹配的游戏文件: {}", file.getAbsolutePath());
                        return true;
                    }
                }
            } else if (file.isDirectory()) {
                // 递归检查子文件夹
                logger.debug("递归检查子目录: {}", file.getAbsolutePath());
                if (containsGameFiles(file, extensions)) {
                    return true;
                }
            }
        }
        logger.debug("目录 {} 中未找到游戏文件", directory.getAbsolutePath());
        return false;
    }

    /**
     * 递归查找目录中的所有游戏文件
     */
    private List<File> findGameFilesInDirectory(File directory, List<String> extensions) {
        List<File> gameFiles = new ArrayList<>();
        if (directory == null || !directory.isDirectory()) {
            return gameFiles;
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return gameFiles;
        }
        
        for (File file : files) {
            if (file.isFile()) {
                String fileName = file.getName().toLowerCase();
                for (String ext : extensions) {
                    if (fileName.endsWith("." + ext)) {
                        gameFiles.add(file);
                        break;
                    }
                }
            } else if (file.isDirectory()) {
                // 递归检查子文件夹
                gameFiles.addAll(findGameFilesInDirectory(file, extensions));
            }
        }
        return gameFiles;
    }

    /**
     * 从游戏文件创建游戏对象
     */
    private Game createGameFromFile(File gameFile, Long platformId, String platformPath, ImportTemplate template) {
        Game game = new Game();
        String absolutePath = gameFile.getAbsolutePath();
        String relativePath = getRelativePath(new File(platformPath), gameFile);
        String fileName = gameFile.getName();
        String gameName = fileName.substring(0, fileName.lastIndexOf('.'));

        // 设置游戏基本信息
        game.setGameId("0");
        game.setSource("NoDataFile");
        game.setPath(relativePath);
        game.setAbsolutePath(absolutePath);
        game.setPlatformId(platformId);
        game.setPlatformPath(platformPath);
        game.setName(gameName);
        game.setDesc("unknown");
        game.setGenre("unknown");
        game.setDeveloper("unknown");
        game.setPublisher("unknown");
        game.setExists(true);

        // 根据模板的 mediaRules 查找媒体文件
        if (template != null && template.getMediaRules() != null) {
            Map<String, List<String>> mediaRules = template.getMediaRules();
            findMediaFilesForGame(game, gameFile.getParentFile(), gameName, mediaRules);
        }

        return game;
    }

    /**
     * 从子文件夹创建游戏对象
     */
    private Game createGameFromDirectory(File gameDir, Long platformId, String platformPath, ImportTemplate template) {
        Game game = new Game();
        String absolutePath = gameDir.getAbsolutePath();
        String relativePath = getRelativePath(new File(platformPath), gameDir);
        String gameName = gameDir.getName();

        // 设置游戏基本信息
        game.setGameId("0");
        game.setSource("NoDataFile");
        game.setPath(relativePath);
        game.setAbsolutePath(absolutePath);
        game.setPlatformId(platformId);
        game.setPlatformPath(platformPath);
        game.setName(gameName);
        game.setDesc("unknown");
        game.setGenre("unknown");
        game.setDeveloper("unknown");
        game.setPublisher("unknown");
        game.setExists(true);

        // 根据模板的 mediaRules 查找媒体文件
        if (template != null && template.getMediaRules() != null) {
            Map<String, List<String>> mediaRules = template.getMediaRules();
            findMediaFilesForGame(game, gameDir, gameName, mediaRules);
        }

        return game;
    }

    /**
     * 根据模板的 mediaRules 查找媒体文件
     */
    private void findMediaFilesForGame(Game game, File gameDir, String gameName, Map<String, List<String>> mediaRules) {
        File platformDir = new File(game.getPlatformPath());
        String platformDirPath = platformDir.getAbsolutePath();
        
        for (Map.Entry<String, List<String>> entry : mediaRules.entrySet()) {
            String mediaType = entry.getKey();
            List<String> rules = entry.getValue();

            for (String rule : rules) {
                // 替换 {filename} 和 {gameName} 占位符
                String mediaPath = rule.replace("{filename}", gameName).replace("{gameName}", gameName);

                // 处理 {ext} 占位符 - 尝试常见扩展名
                int extPlaceholderIndex = mediaPath.indexOf("{ext}");
                if (extPlaceholderIndex != -1) {
                    String basePath = mediaPath.substring(0, extPlaceholderIndex);
                    for (String ext : Arrays.asList("png", "jpg", "jpeg", "gif", "mp4", "webm", "mkv")) {
                        // 媒体文件路径是相对于平台目录的
                        File mediaFile = new File(platformDir, basePath + ext);
                        String mediaFilePath = mediaFile.getAbsolutePath();
                        logger.debug("查找媒体文件: {}", mediaFilePath);
                        if (mediaFile.exists()) {
                            String relativePath = getRelativePath(mediaFilePath, platformDirPath);
                            setGameMediaField(game, mediaType, relativePath);
                            logger.info("找到媒体文件: {} for type: {}, relative: {}", mediaFilePath, mediaType, relativePath);
                            break;
                        }
                    }
                } else {
                    // 媒体文件路径是相对于平台目录的
                    File mediaFile = new File(platformDir, mediaPath);
                    String mediaFilePath = mediaFile.getAbsolutePath();
                    logger.debug("查找媒体文件: {}", mediaFilePath);
                    if (mediaFile.exists()) {
                        String relativePath = getRelativePath(mediaFilePath, platformDirPath);
                        setGameMediaField(game, mediaType, relativePath);
                        logger.info("找到媒体文件: {} for type: {}, relative: {}", mediaFilePath, mediaType, relativePath);
                        break;
                    }
                }
            }
        }
    }

    /**
     * 设置游戏的媒体文件字段
     */
    private void setGameMediaField(Game game, String mediaType, String mediaPath) {
        if (mediaPath == null || mediaPath.isEmpty()) {
            return;
        }

        switch (mediaType) {
            case "box2dfront":
                game.setBoxFront(mediaPath);
                game.setImage(mediaPath);
                break;
            case "box2dback":
                game.setBoxBack(mediaPath);
                break;
            case "box3d":
                game.setBox3d(mediaPath);
                break;
            case "screenshot":
                game.setScreenshot(mediaPath);
                break;
            case "video":
                game.setVideo(mediaPath);
                break;
            case "wheel":
                game.setWheel(mediaPath);
                game.setThumbnail(mediaPath);
                break;
            case "marquee":
                game.setMarquee(mediaPath);
                game.setLogo(mediaPath);
                break;
            case "boxfull":
                game.setBoxFull(mediaPath);
                break;
            case "boxspine":
                game.setBoxSpine(mediaPath);
                break;
            case "cartridge":
                game.setCartridge(mediaPath);
                break;
            case "bezel":
                game.setBezel(mediaPath);
                break;
            case "panel":
                game.setPanel(mediaPath);
                break;
            case "cabinetleft":
                game.setCabinetLeft(mediaPath);
                break;
            case "cabinetright":
                game.setCabinetRight(mediaPath);
                break;
            case "tile":
                game.setTile(mediaPath);
                break;
            case "banner":
                game.setBanner(mediaPath);
                break;
            case "steam":
                game.setSteam(mediaPath);
                break;
            case "poster":
                game.setPoster(mediaPath);
                break;
            case "background":
                game.setBackground(mediaPath);
                break;
            case "music":
                game.setMusic(mediaPath);
                break;
            case "titlescreen":
                game.setTitlescreen(mediaPath);
                break;
            case "steamgrid":
                game.setSteamgrid(mediaPath);
                break;
            case "fanart":
                game.setFanart(mediaPath);
                break;
            case "boxtexture":
                game.setBoxtexture(mediaPath);
                break;
            case "supporttexture":
                game.setSupporttexture(mediaPath);
                break;
            case "manual":
                game.setManual(mediaPath);
                break;
            default:
                logger.debug("未知的媒体类型: {}", mediaType);
                break;
        }
    }

    /**
     * 扫描指定目录下的游戏文件
     */
    private List<File> scanGameFiles(File directory, List<String> extensions) {
        List<File> gameFiles = new ArrayList<>();
        if (extensions == null || extensions.isEmpty()) {
            // 默认支持的游戏扩展名
            extensions = Arrays.asList("chd", "iso", "bin", "cue", "zip", "7z", "rar", "sfc", "nes", "gen", "md", "gb", "gba", "n64", "psx", "ps2", "psp", "wii", "wiiu", "switch", "xbox", "x360");
        }
        scanGameFilesRecursive(directory, gameFiles, extensions);
        return gameFiles;
    }

    /**
     * 递归扫描游戏文件
     */
    private void scanGameFilesRecursive(File directory, List<File> gameFiles, List<String> extensions) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    scanGameFilesRecursive(file, gameFiles, extensions);
                } else {
                    String fileName = file.getName().toLowerCase();
                    for (String ext : extensions) {
                        if (fileName.endsWith("." + ext.toLowerCase())) {
                            gameFiles.add(file);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * 从游戏文件创建游戏对象
     */
    private Game createGameFromFile(File gameFile, Long platformId, String platformPath, String importMethod, ImportTemplate template, boolean metadataOnly) {
        Game game = new Game();
        String absolutePath = gameFile.getAbsolutePath();
        String relativePath = getRelativePath(new File(platformPath), gameFile);
        String gameName = LanguageDetector.extractFileName(absolutePath);

        // 设置游戏基本信息
        game.setGameId("game_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000));
        game.setSource("GameFile");
        game.setPath(relativePath);
        game.setAbsolutePath(absolutePath);
        game.setPlatformId(platformId);
        game.setPlatformPath(platformPath);
        game.setName(gameName);
        game.setExists(true);

        // 查找媒体文件
        if (!metadataOnly) {
            if ("traditional".equals(importMethod)) {
                // 传统方式：使用默认规则查找媒体文件
                MediaFileFinder.MediaFiles mediaFiles = MediaFileFinder.findMediaFiles(absolutePath, platformPath, null);
                if (mediaFiles.getBoxFront() != null) {
                    game.setImage(mediaFiles.getBoxFront());
                    game.setBoxFront(mediaFiles.getBoxFront());
                }
                if (mediaFiles.getVideo() != null) {
                    game.setVideo(mediaFiles.getVideo());
                }
                if (mediaFiles.getLogo() != null) {
                    game.setMarquee(mediaFiles.getLogo());
                    game.setLogo(mediaFiles.getLogo());
                }
                if (mediaFiles.getScreenshot() != null) {
                    game.setThumbnail(mediaFiles.getScreenshot());
                    game.setScreenshot(mediaFiles.getScreenshot());
                }
                // 其他媒体文件...
            } else {
                // 模板方式：使用模板的 mediaRules 查找媒体文件
                if (template != null && template.getMediaRules() != null) {
                    Map<String, List<String>> mediaRules = template.getMediaRules();
                    findMediaFilesForGame(game, gameFile.getParentFile(), gameName, mediaRules);
                }
            }
        }

        return game;
    }

    /**
     * 导入模板类
     */
    public static class ImportTemplate {
        private static String templatesPath = "/data/rules/import";
        
        private String frontend;
        private String name;
        private String version;
        private String description;
        private String type;
        private String dataFile;
        private String delimiter;
        private Rules rules;
        
        public static void setTemplatesPath(String path) {
            templatesPath = path;
        }

        public static ImportTemplate loadTemplate(String templateName) {
            try {
                // 只从外部路径加载模板
                File externalTemplateFile = new File(templatesPath + "/" + templateName);
                if (externalTemplateFile.exists()) {
                    logger.info("Loading template from external path: {}", externalTemplateFile.getAbsolutePath());
                    try (java.io.InputStream inputStream = new java.io.FileInputStream(externalTemplateFile)) {
                        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        return objectMapper.readValue(inputStream, ImportTemplate.class);
                    }
                }
                logger.warn("Template not found: {}", externalTemplateFile.getAbsolutePath());
                return null;
            } catch (Exception e) {
                logger.error("加载导入模板失败: {}", templateName, e);
                return null;
            }
        }

        // Getters and setters
        public String getFrontend() { return frontend; }
        public void setFrontend(String frontend) { this.frontend = frontend; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDataFile() { return dataFile; }
        public void setDataFile(String dataFile) { this.dataFile = dataFile; }
        public String getDelimiter() { return delimiter; }
        public void setDelimiter(String delimiter) { this.delimiter = delimiter; }
        public Rules getRules() { return rules; }
        public void setRules(Rules rules) { this.rules = rules; }
        
        // 兼容旧版模板 - 获取 header
        public Header getHeader() { 
            return rules != null ? rules.getHeader() : null; 
        }
        
        // 兼容旧版模板 - 获取 fieldMappings
        public Map<String, FieldMapping> getFieldMappings() { 
            return rules != null ? rules.getFieldMappings() : null; 
        }
        
        // 兼容旧版模板 - 获取 mediaRules
        public Map<String, List<String>> getMediaRules() { 
            if (rules == null || rules.getMedia() == null) {
                return null;
            }
            Map<String, List<String>> mediaRules = new java.util.HashMap<>();
            for (Map.Entry<String, MediaRule> entry : rules.getMedia().entrySet()) {
                mediaRules.put(entry.getKey(), entry.getValue().getRules());
            }
            return mediaRules;
        }
        
        // 兼容旧版模板 - 获取 extensions
        public Map<String, List<String>> getExtensions() { 
            return rules != null ? rules.getExtensions() : null; 
        }
        
        // 兼容旧版模板 - 获取 gameExtensions
        public List<String> getGameExtensions() { 
            return rules != null ? rules.getGameExtensions() : null; 
        }

        // 规则包装类
        public static class Rules {
            private Header header;
            private Map<String, FieldMapping> fieldMappings;
            private Map<String, MediaRule> media;
            private Map<String, List<String>> extensions;
            private List<String> gameExtensions;

            public Header getHeader() { return header; }
            public void setHeader(Header header) { this.header = header; }
            public Map<String, FieldMapping> getFieldMappings() { return fieldMappings; }
            public void setFieldMappings(Map<String, FieldMapping> fieldMappings) { this.fieldMappings = fieldMappings; }
            public Map<String, MediaRule> getMedia() { return media; }
            public void setMedia(Map<String, MediaRule> media) { this.media = media; }
            public Map<String, List<String>> getExtensions() { return extensions; }
            public void setExtensions(Map<String, List<String>> extensions) { this.extensions = extensions; }
            public List<String> getGameExtensions() { return gameExtensions; }
            public void setGameExtensions(List<String> gameExtensions) { this.gameExtensions = gameExtensions; }
        }

        // 媒体规则类
        public static class MediaRule {
            private String source;
            private List<String> rules;
            private TransformRule transform;

            public String getSource() { return source; }
            public void setSource(String source) { this.source = source; }
            public List<String> getRules() { return rules; }
            public void setRules(List<String> rules) { this.rules = rules; }
            public TransformRule getTransform() { return transform; }
            public void setTransform(TransformRule transform) { this.transform = transform; }
        }

        // 表头配置类
        public static class Header {
            private boolean enabled;
            private String format;
            private String startMarker;
            private String endMarker;
            private List<String> structure;
            private Map<String, FieldMapping> fields;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public String getFormat() { return format; }
            public void setFormat(String format) { this.format = format; }
            public String getStartMarker() { return startMarker; }
            public void setStartMarker(String startMarker) { this.startMarker = startMarker; }
            public String getEndMarker() { return endMarker; }
            public void setEndMarker(String endMarker) { this.endMarker = endMarker; }
            public List<String> getStructure() { return structure; }
            public void setStructure(List<String> structure) { this.structure = structure; }
            public Map<String, FieldMapping> getFields() { return fields; }
            public void setFields(Map<String, FieldMapping> fields) { this.fields = fields; }
        }

        public static class FieldMapping {
            private List<String> fields;
            @com.fasterxml.jackson.annotation.JsonProperty("isMultiValue")
            private boolean multiValue;
            private String valuePrefix;
            private TransformRule transform;

            public List<String> getFields() { return fields; }
            public void setFields(List<String> fields) { this.fields = fields; }
            public boolean isMultiValue() { return multiValue; }
            public void setMultiValue(boolean multiValue) { this.multiValue = multiValue; }
            public String getValuePrefix() { return valuePrefix; }
            public void setValuePrefix(String valuePrefix) { this.valuePrefix = valuePrefix; }
            public TransformRule getTransform() { return transform; }
            public void setTransform(TransformRule transform) { this.transform = transform; }
        }

        public static class TransformRule {
            private String path;
            private String caseType;
            private boolean trim;
            private ReplaceRule replace;

            public String getPath() { return path; }
            public void setPath(String path) { this.path = path; }
            public String getCaseType() { return caseType; }
            public void setCaseType(String caseType) { this.caseType = caseType; }
            public boolean isTrim() { return trim; }
            public void setTrim(boolean trim) { this.trim = trim; }
            public ReplaceRule getReplace() { return replace; }
            public void setReplace(ReplaceRule replace) { this.replace = replace; }
        }

        public static class ReplaceRule {
            private String from;
            private String to;

            public String getFrom() { return from; }
            public void setFrom(String from) { this.from = from; }
            public String getTo() { return to; }
            public void setTo(String to) { this.to = to; }
        }
    }
}
