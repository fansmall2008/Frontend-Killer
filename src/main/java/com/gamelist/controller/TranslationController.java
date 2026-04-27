package com.gamelist.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.mapper.GameMapper;
import com.gamelist.mapper.PlatformMapper;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.service.GameService;
import com.gamelist.service.PlatformService;
import com.gamelist.service.TaskService;
import com.gamelist.service.TranslationException;
import com.gamelist.service.TranslationService;
import com.gamelist.service.impl.TranslationServiceFactory;
import com.gamelist.service.impl.TranslationServiceManager;

@RestController
@RequestMapping("/api/translation")
public class TranslationController {
    
    @Autowired
    private GameMapper gameMapper;
    
    @Autowired
    private GameService gameService;
    
    @Autowired
    private PlatformMapper platformMapper;
    
    @Autowired
    private PlatformService platformService;
    
    @Autowired
    private TaskService taskService;
    
    // 翻译进度存储
    private final Map<String, Map<String, Object>> translationProgress = new HashMap<>();
    // 翻译错误记录存储
    private final Map<String, List<Map<String, Object>>> translationErrors = new HashMap<>();
    // 任务ID映射
    private final Map<String, Long> taskIdMap = new HashMap<>();
    
    /**
     * 获取所有平台
     */
    @GetMapping("/platforms")
    public List<Platform> getPlatforms() {
        return platformService.getAllPlatforms();
    }
    
    /**
     * 获取有效的翻译服务列表
     */
    @GetMapping("/services")
    public Map<String, Object> getValidServices() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> services = TranslationServiceManager.getInstance().getValidServices();
            result.put("success", true);
            result.put("services", services);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取翻译服务列表失败: " + e.getMessage());
        }
        return result;
    }
    
    /**
     * 翻译平台游戏
     */
    @PostMapping("/translate")
    public Map<String, Object> translateGames(
            @RequestParam Long platformId,
            @RequestParam String apiType,
            @RequestParam String sourceLang,
            @RequestParam String targetLang,
            @RequestParam String translateType,
            @RequestParam String apiKey,
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String appSecret
    ) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取平台信息
            Platform platform = platformService.getPlatformById(platformId);
            if (platform == null) {
                result.put("success", false);
                result.put("message", "平台不存在");
                return result;
            }
            
            // 获取平台下的所有游戏
            List<Game> games = gameService.getGamesByPlatformId(platformId);
            if (games.isEmpty()) {
                result.put("success", false);
                result.put("message", "该平台下无游戏");
                return result;
            }
            
            // 创建翻译服务
            TranslationService translationService = createTranslationService(apiType, apiKey, appId, appSecret);
            
            // 生成任务ID
            String taskId = "task_" + System.currentTimeMillis();
            
            // 初始化进度
            Map<String, Object> progressInfo = new HashMap<>();
            progressInfo.put("total", games.size());
            progressInfo.put("completed", 0);
            progressInfo.put("success", 0);
            progressInfo.put("failed", 0);
            progressInfo.put("status", "processing");
            translationProgress.put(taskId, progressInfo);
            
            // 初始化错误记录列表
            List<Map<String, Object>> errorList = new ArrayList<>();
            translationErrors.put(taskId, errorList);
            
            // 创建后台任务
            String taskDescription = "翻译平台: " + platform.getName() + " (" + games.size() + " 个游戏)";
            Long backgroundTaskId = taskService.createTask("TRANSLATE", taskDescription).getId();
            taskIdMap.put(taskId, backgroundTaskId);
            
            // 异步执行翻译
            new Thread(() -> {
                AtomicInteger completed = new AtomicInteger(0);
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger failedCount = new AtomicInteger(0);
                
                for (Game game : games) {
                    try {
                        // 翻译游戏名称和描述
                        if (translateType.equals("name")) {
                            if (game.getName() != null && !game.getName().isEmpty()) {
                                String translatedName = translationService.translate(
                                        game.getName(), sourceLang, targetLang
                                );
                                game.setTranslatedName(translatedName);
                            }
                        } else if (translateType.equals("desc")) {
                            if (game.getDesc() != null && !game.getDesc().isEmpty()) {
                                String translatedDesc = translationService.translate(
                                        game.getDesc(), sourceLang, targetLang
                                );
                                game.setTranslatedDesc(translatedDesc);
                            }
                        } else if (translateType.equals("both")) {
                            // 使用translateGame方法同时翻译游戏名称和描述
                            TranslationService.TranslationResult translationResult = translationService.translateGame(
                                    game.getName(), game.getDesc(), sourceLang, targetLang
                            );
                            if (translationResult.getGameName() != null) {
                                game.setTranslatedName(translationResult.getGameName());
                            }
                            if (translationResult.getGameDescription() != null) {
                                game.setTranslatedDesc(translationResult.getGameDescription());
                            }
                        }
                        
                        // 更新游戏
                        gameService.updateGame(game);
                        successCount.incrementAndGet();
                    } catch (TranslationException e) {
                        System.err.println("翻译失败: " + e.getMessage());
                        failedCount.incrementAndGet();
                        // 记录错误信息
                        Map<String, Object> errorInfo = new HashMap<>();
                        errorInfo.put("gameId", game.getGameId());
                        errorInfo.put("gameName", game.getName());
                        errorInfo.put("errorType", "translation");
                        errorInfo.put("errorMessage", e.getMessage());
                        errorList.add(errorInfo);
                    } catch (Exception e) {
                        System.err.println("更新游戏失败: " + e.getMessage());
                        failedCount.incrementAndGet();
                        // 记录错误信息
                        Map<String, Object> errorInfo = new HashMap<>();
                        errorInfo.put("gameId", game.getGameId());
                        errorInfo.put("gameName", game.getName());
                        errorInfo.put("errorType", "update");
                        errorInfo.put("errorMessage", e.getMessage());
                        errorList.add(errorInfo);
                    } finally {
                        int current = completed.incrementAndGet();
                        // 更新进度
                        synchronized (progressInfo) {
                            progressInfo.put("completed", current);
                            progressInfo.put("success", successCount.get());
                            progressInfo.put("failed", failedCount.get());
                            
                            // 更新后台任务进度
                            int percentProgress = (int) ((current * 100.0) / games.size());
                            taskService.updateTaskProgress(backgroundTaskId, percentProgress, "翻译游戏: " + game.getName(), current, games.size());
                            
                            if (current >= games.size()) {
                                progressInfo.put("status", "completed");
                                // 任务完成时，将错误信息添加到进度信息中
                                progressInfo.put("errors", errorList);
                                
                                // 完成后台任务
                                String taskResult = "翻译完成: " + successCount.get() + " 成功, " + failedCount.get() + " 失败";
                                taskService.completeTask(backgroundTaskId, taskResult, taskResult);
                            }
                        }
                    }
                }
            }).start();
            
            result.put("success", true);
            result.put("taskId", taskId);
            result.put("message", "翻译任务已启动");
            result.put("totalGames", games.size());
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "翻译任务启动失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * 翻译单个游戏
     */
    @PostMapping("/translate/game")
    public Map<String, Object> translateGame(
            @RequestParam Long gameId,
            @RequestParam String apiType,
            @RequestParam String sourceLang,
            @RequestParam String targetLang,
            @RequestParam String translateType,
            @RequestParam String apiKey,
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String appSecret
    ) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取游戏信息
            Game game = gameService.getGameById(gameId);
            if (game == null) {
                result.put("success", false);
                result.put("message", "游戏不存在");
                return result;
            }
            
            // 创建翻译服务
            TranslationService translationService = createTranslationService(apiType, apiKey, appId, appSecret);
            
            // 翻译游戏名称
            if (translateType.equals("name") || translateType.equals("both")) {
                if (game.getName() != null && !game.getName().isEmpty()) {
                    String translatedName = translationService.translate(
                            game.getName(), sourceLang, targetLang
                    );
                    game.setTranslatedName(translatedName);
                }
            }
            
            // 翻译游戏描述
            if (translateType.equals("desc") || translateType.equals("both")) {
                if (game.getDesc() != null && !game.getDesc().isEmpty()) {
                    String translatedDesc = translationService.translate(
                            game.getDesc(), sourceLang, targetLang
                    );
                    game.setTranslatedDesc(translatedDesc);
                }
            }
            
            // 更新游戏
            gameService.updateGame(game);
            
            result.put("success", true);
            result.put("message", "游戏翻译成功");
            result.put("game", game);
            
        } catch (TranslationException e) {
            result.put("success", false);
            result.put("message", "翻译失败: " + e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "翻译任务启动失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * 批量翻译游戏
     */
    @PostMapping("/translate/games")
    public Map<String, Object> translateGames(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        try {
            Object gameIdsObj = request.get("gameIds");
            List<Game> games = new ArrayList<>();

            if (gameIdsObj instanceof List) {
                List<?> gameIdsList = (List<?>) gameIdsObj;
                for (Object gameIdObj : gameIdsList) {
                    try {
                        Long gameId = convertToLong(gameIdObj);
                        if (gameId != null) {
                            Game game = gameService.getGameById(gameId);
                            if (game != null) {
                                games.add(game);
                            }
                        }
                    } catch (Exception e) {
                        // 忽略无效的游戏ID
                    }
                }
            } else if (gameIdsObj instanceof String) {
                String[] gameIdArray = ((String) gameIdsObj).split(",");
                for (String gameIdStr : gameIdArray) {
                    try {
                        Long gameId = Long.parseLong(gameIdStr.trim());
                        Game game = gameService.getGameById(gameId);
                        if (game != null) {
                            games.add(game);
                        }
                    } catch (NumberFormatException e) {
                        // 忽略无效的游戏ID
                    }
                }
            }

            if (games.isEmpty()) {
                result.put("success", false);
                result.put("message", "未找到有效的游戏");
                return result;
            }

            String apiType = (String) request.get("apiType");
            String sourceLang = (String) request.get("sourceLang");
            String targetLang = (String) request.get("targetLang");
            String translateType = (String) request.get("translateType");
            String apiKey = (String) request.get("apiKey");
            String appId = request.get("appId") != null ? (String) request.get("appId") : null;
            String appSecret = request.get("appSecret") != null ? (String) request.get("appSecret") : null;

            // 创建翻译服务
            TranslationService translationService = createTranslationService(apiType, apiKey, appId, appSecret);

            // 生成任务ID
            String taskId = "task_" + System.currentTimeMillis();

            // 初始化进度
            Map<String, Object> progressInfo = new HashMap<>();
            progressInfo.put("total", games.size());
            progressInfo.put("completed", 0);
            progressInfo.put("success", 0);
            progressInfo.put("failed", 0);
            progressInfo.put("status", "processing");
            translationProgress.put(taskId, progressInfo);

            // 初始化错误记录列表
            List<Map<String, Object>> errorList = new ArrayList<>();
            translationErrors.put(taskId, errorList);

            // 创建后台任务
            String taskDescription = "批量翻译游戏: " + games.size() + " 个游戏";
            Long backgroundTaskId = taskService.createTask("TRANSLATE", taskDescription).getId();
            taskIdMap.put(taskId, backgroundTaskId);

            // 异步执行翻译
            new Thread(() -> {
                AtomicInteger completed = new AtomicInteger(0);
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger failedCount = new AtomicInteger(0);

                for (Game game : games) {
                    try {
                        // 翻译游戏名称和描述
                        if (translateType.equals("name")) {
                            if (game.getName() != null && !game.getName().isEmpty()) {
                                String translatedName = translationService.translate(
                                        game.getName(), sourceLang, targetLang
                                );
                                game.setTranslatedName(translatedName);
                            }
                        } else if (translateType.equals("desc")) {
                            if (game.getDesc() != null && !game.getDesc().isEmpty()) {
                                String translatedDesc = translationService.translate(
                                        game.getDesc(), sourceLang, targetLang
                                );
                                game.setTranslatedDesc(translatedDesc);
                            }
                        } else if (translateType.equals("both")) {
                            // 使用translateGame方法同时翻译游戏名称和描述
                            TranslationService.TranslationResult translationResult = translationService.translateGame(
                                    game.getName(), game.getDesc(), sourceLang, targetLang
                            );
                            if (translationResult.getGameName() != null) {
                                game.setTranslatedName(translationResult.getGameName());
                            }
                            if (translationResult.getGameDescription() != null) {
                                game.setTranslatedDesc(translationResult.getGameDescription());
                            }
                        }

                        // 更新游戏
                        gameService.updateGame(game);
                        successCount.incrementAndGet();
                    } catch (TranslationException e) {
                        System.err.println("翻译失败: " + e.getMessage());
                        failedCount.incrementAndGet();
                        // 记录错误信息
                        Map<String, Object> errorInfo = new HashMap<>();
                        errorInfo.put("gameId", game.getGameId());
                        errorInfo.put("gameName", game.getName());
                        errorInfo.put("errorType", "translation");
                        errorInfo.put("errorMessage", e.getMessage());
                        errorList.add(errorInfo);
                    } catch (Exception e) {
                        System.err.println("更新游戏失败: " + e.getMessage());
                        failedCount.incrementAndGet();
                        // 记录错误信息
                        Map<String, Object> errorInfo = new HashMap<>();
                        errorInfo.put("gameId", game.getGameId());
                        errorInfo.put("gameName", game.getName());
                        errorInfo.put("errorType", "update");
                        errorInfo.put("errorMessage", e.getMessage());
                        errorList.add(errorInfo);
                    } finally {
                        int current = completed.incrementAndGet();
                        // 更新进度
                        synchronized (progressInfo) {
                            progressInfo.put("completed", current);
                            progressInfo.put("success", successCount.get());
                            progressInfo.put("failed", failedCount.get());
                            
                            // 更新后台任务进度
                            int percentProgress = (int) ((current * 100.0) / games.size());
                            taskService.updateTaskProgress(backgroundTaskId, percentProgress, "翻译游戏: " + game.getName(), current, games.size());
                            
                            if (current >= games.size()) {
                                progressInfo.put("status", "completed");
                                // 任务完成时，将错误信息添加到进度信息中
                                progressInfo.put("errors", errorList);
                                
                                // 完成后台任务
                                String taskResult = "翻译完成: " + successCount.get() + " 成功, " + failedCount.get() + " 失败";
                                taskService.completeTask(backgroundTaskId, taskResult, taskResult);
                            }
                        }
                    }
                }
            }).start();

            result.put("success", true);
            result.put("taskId", taskId);
            result.put("message", "翻译任务已启动");
            result.put("totalGames", games.size());

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "翻译任务启动失败: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
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
    
    /**
     * 获取翻译进度
     */
    @GetMapping("/progress/{taskId}")
    public Map<String, Object> getTranslationProgress(@PathVariable String taskId) {
        Map<String, Object> progress = translationProgress.get(taskId);
        if (progress == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "任务不存在");
            return result;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("progress", progress);
        
        // 检查是否有错误记录
        List<Map<String, Object>> errors = translationErrors.get(taskId);
        if (errors != null && !errors.isEmpty()) {
            result.put("errors", errors);
        }
        
        // 任务完成后保留一段时间，让前端有足够时间获取完整信息
        // 这里不立即移除，而是由定时任务或其他机制处理
        
        return result;
    }
    
    /**
     * 创建包含翻译错误条目的新平台
     */
    @PostMapping("/create-error-subset/{taskId}")
    public Map<String, Object> createErrorSubset(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取错误记录
            List<Map<String, Object>> errors = translationErrors.get(taskId);
            if (errors == null || errors.isEmpty()) {
                result.put("success", false);
                result.put("message", "无错误记录");
                return result;
            }
            
            // 生成新平台名称：translat_faild_平台名_时间戳
            String timestamp = String.valueOf(System.currentTimeMillis());
            String platformName = "translat_faild_" + timestamp;
            
            // 创建新平台
            Platform platform = new Platform();
            platform.setSystem(platformName);
            platform.setName(platformName);
            platform.setSoftware("WebGamelistOper");
            platform.setDatabase("Custom Database");
            platform.setWeb("http://localhost:8083");
            platformMapper.insertPlatform(platform);
            
            // 从错误记录中获取游戏并添加到新平台
            int errorCount = 0;
            for (Map<String, Object> error : errors) {
                String gameId = (String) error.get("gameId");
                if (gameId != null) {
                    // 查找原始游戏
                    List<Game> games = gameMapper.selectGamesByFilter(new HashMap<>());
                    for (Game game : games) {
                        if (game.getGameId().equals(gameId)) {
                            // 创建新游戏实例，保持gameId不变
                            Game newGame = new Game();
                            newGame.setGameId(game.getGameId());
                            newGame.setSource(game.getSource());
                            newGame.setPath(game.getPath());
                            newGame.setName(game.getName());
                            newGame.setDesc(game.getDesc());
                            newGame.setTranslatedName(game.getTranslatedName());
                            newGame.setTranslatedDesc(game.getTranslatedDesc());
                            newGame.setImage(game.getImage());
                            newGame.setVideo(game.getVideo());
                            newGame.setMarquee(game.getMarquee());
                            newGame.setThumbnail(game.getThumbnail());
                            newGame.setManual(game.getManual());
                            newGame.setRating(game.getRating());
                            newGame.setReleasedate(game.getReleasedate());
                            newGame.setDeveloper(game.getDeveloper());
                            newGame.setPublisher(game.getPublisher());
                            newGame.setGenre(game.getGenre());
                            newGame.setPlayers(game.getPlayers());
                            newGame.setCrc32(game.getCrc32());
                            newGame.setLang(game.getLang());
                            newGame.setGenreid(game.getGenreid());
                            newGame.setPlatformId(platform.getId());
                            newGame.setSortBy(game.getSortBy());
                            
                            gameMapper.insertGame(newGame);
                            errorCount++;
                            break;
                        }
                    }
                }
            }
            
            result.put("success", true);
            result.put("platformName", platform.getName());
            result.put("platformId", platform.getId());
            result.put("errorCount", errorCount);
            result.put("message", "错误条目子集分离成功");
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "创建错误子集失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * 创建翻译服务
     */
    private TranslationService createTranslationService(String apiType, String apiKey, String appId, String appSecret) {
        // 尝试使用新的通用翻译服务
        TranslationService service = TranslationServiceManager.getInstance().getTranslationService(apiType);
        if (service != null) {
            return service;
        }
        
        // 回退到旧的翻译服务
        switch (apiType) {
            case "google":
                return TranslationServiceFactory.createTranslationService("google", apiKey);
            case "baidu":
                return TranslationServiceFactory.createTranslationService("baidu", appId, apiKey);
            case "youdao":
                return TranslationServiceFactory.createTranslationService("youdao", apiKey, appSecret);
            case "deepseek":
                return TranslationServiceFactory.createTranslationService("deepseek", apiKey);
            default:
                throw new IllegalArgumentException("不支持的翻译API类型: " + apiType);
        }
    }
}
