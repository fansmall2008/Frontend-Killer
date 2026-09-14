package com.gamelist.controller;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gamelist.mapper.GameMapper;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.service.PlatformService;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);
    private static final String MEDIA_DIR = "media";

    @PostMapping("/cache")
    public ResponseEntity<Map<String, Object>> cacheMediaFile(@RequestParam String localPath) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 确保媒体目录存在
            Path mediaDirPath = Paths.get(MEDIA_DIR);
            if (!Files.exists(mediaDirPath)) {
                Files.createDirectories(mediaDirPath);
            }
            
            // 获取源文件，处理Windows路径映射
            String processedPath = localPath;
            // 处理Windows路径映射（D:\3do -> /roms）
            if (processedPath.contains("D:\\3do")) {
                processedPath = processedPath.replace("D:\\3do", "/roms");
            }
            // 处理Windows路径分隔符
            processedPath = processedPath.replace("\\", "/");
            
            Path sourcePath = Paths.get(processedPath);
            if (!Files.exists(sourcePath)) {
                // 尝试直接使用原始路径
                sourcePath = Paths.get(localPath);
                if (!Files.exists(sourcePath)) {
                    response.put("success", false);
                    response.put("message", "源文件不存在: " + localPath);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }
            }
            
            // 生成目标文件名（使用时间戳确保唯一性，去除空格和特殊字符）
            String originalFileName = sourcePath.getFileName().toString();
            String safeFileName = System.currentTimeMillis() + "_" + originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path targetPath = mediaDirPath.resolve(safeFileName);
            
            // 复制文件
            Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            // 生成访问URL
            String mediaUrl = "/" + MEDIA_DIR + "/" + safeFileName;
            
            response.put("success", true);
            response.put("message", "媒体文件缓存成功");
            response.put("mediaUrl", mediaUrl);
            response.put("fileName", safeFileName);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "缓存媒体文件失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Autowired
    private PlatformService platformService;

    @Autowired
    private GameMapper gameMapper;

    /**
     * 媒体文件查看代理。
     * <p>
     * 解析策略（按优先级）：
     * <ol>
     *   <li>绝对路径 → 直接检查存在性</li>
     *   <li>相对路径 → 先试程序工作目录（刮削/上传路径）</li>
     *   <li>相对路径 → 再试 platform.folderPath（导入路径）</li>
     *   <li>Docker 兼容 → /roms 映射回退</li>
     *   <li>都不存在 → 404</li>
     * </ol>
     */
    @GetMapping("/view")
    public ResponseEntity<?> viewMediaFile(@RequestParam String localPath,
                                            @RequestParam(required = false) Long platformId,
                                            @RequestParam(required = false) Long gameId,
                                            @RequestParam(required = false) String platformPath) {
        try {
            logger.debug("媒体查看请求: localPath={}, platformId={}, gameId={}", localPath, platformId, gameId);

            // 1. 清理路径：统一分隔符，去掉 ./ 前缀
            String cleanPath = localPath.replace("\\", "/");
            if (cleanPath.startsWith("./")) {
                cleanPath = cleanPath.substring(2);
            }

            Path sourcePath = resolveMediaPath(cleanPath, platformId, gameId, platformPath);

            if (sourcePath == null || !Files.exists(sourcePath)) {
                logger.debug("媒体文件不存在: {}", localPath);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "文件不存在: " + localPath);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            logger.debug("媒体文件已定位: {}", sourcePath.toAbsolutePath());

            // 检测文件类型
            MediaType mediaType = detectMediaType(sourcePath.getFileName().toString());

            // 创建输入流资源
            InputStreamResource resource = new InputStreamResource(Files.newInputStream(sourcePath));

            // 响应头：内联显示 + 浏览器缓存 1 天
            HttpHeaders headers = new HttpHeaders();
            headers.setCacheControl("max-age=86400, must-revalidate");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(mediaType)
                    .body(resource);

        } catch (IOException e) {
            logger.error("读取媒体文件失败: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "读取文件失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 按优先级解析媒体路径。
     * <p>
     * 1. 绝对路径 → 直接返回
     * 2. 工作目录 + 相对路径（刮削/上传文件）
     * 3. platformPath 参数 + 相对路径（前端直传）
     * 4. gameId → game.platformPath + 相对路径
     * 5. platformId → platform.folderPath + 相对路径（导入文件）
     * 6. Docker /roms 映射回退
     */
    private Path resolveMediaPath(String cleanPath, Long platformId, Long gameId, String platformPath) {
        // 1. 绝对路径
        Path absPath = Paths.get(cleanPath);
        if (absPath.isAbsolute() && Files.exists(absPath)) {
            logger.debug("路径解析: 绝对路径命中");
            return absPath;
        }

        // 2. 工作目录 + 相对路径（刮削/上传路径，如 data/scraper/games/...）
        Path workDirPath = Paths.get(cleanPath).toAbsolutePath();
        if (Files.exists(workDirPath)) {
            logger.debug("路径解析: 工作目录命中");
            return workDirPath;
        }

        // 3. 前端直传 platformPath
        if (platformPath != null && !platformPath.isEmpty()) {
            Path p = Paths.get(platformPath, cleanPath);
            if (Files.exists(p)) {
                logger.debug("路径解析: 前端platformPath命中");
                return p;
            }
        }

        // 4. 从 gameId 获取 platformPath
        if (gameId != null && gameId > 0) {
            Game game = gameMapper.selectGameById(gameId);
            if (game != null && game.getPlatformPath() != null && !game.getPlatformPath().isEmpty()) {
                Path p = Paths.get(game.getPlatformPath(), cleanPath);
                if (Files.exists(p)) {
                    logger.debug("路径解析: game.platformPath命中");
                    return p;
                }
            }
            if (game != null && game.getPlatformId() != null && platformId == null) {
                platformId = game.getPlatformId();
            }
        }

        // 5. platformId → platform.folderPath（导入路径）
        if (platformId != null && platformId > 0) {
            Platform platform = platformService.getPlatformById(platformId);
            if (platform != null && platform.getFolderPath() != null && !platform.getFolderPath().isEmpty()) {
                Path p = Paths.get(platform.getFolderPath(), cleanPath);
                if (Files.exists(p)) {
                    logger.debug("路径解析: platform.folderPath命中");
                    return p;
                }
            }
        }

        // 6. Docker /roms 映射回退
        String romsPath = cleanPath;
        if (romsPath.contains("D:/3do")) {
            romsPath = romsPath.replace("D:/3do", "/roms");
        }
        if (!romsPath.startsWith("/roms")) {
            romsPath = "/roms/" + romsPath;
        }
        Path romsPathObj = Paths.get(romsPath);
        if (Files.exists(romsPathObj)) {
            logger.debug("路径解析: Docker /roms 映射命中");
            return romsPathObj;
        }

        logger.debug("路径解析: 所有策略均未命中");
        return null;
    }

    /**
     * 根据文件扩展名检测 MediaType。
     */
    private MediaType detectMediaType(String fileName) {
        String name = fileName.toLowerCase();
        if (name.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (name.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (name.endsWith(".svg")) return MediaType.parseMediaType("image/svg+xml");
        if (name.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (name.endsWith(".mp4")) return MediaType.parseMediaType("video/mp4");
        if (name.endsWith(".avi")) return MediaType.parseMediaType("video/avi");
        if (name.endsWith(".mkv")) return MediaType.parseMediaType("video/x-matroska");
        if (name.endsWith(".webm")) return MediaType.parseMediaType("video/webm");
        if (name.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    // ==================== 游戏编辑页 - 媒体上传/删除/状态 ====================

    /**
     * 上传媒体文件到固定路径: ./data/scraper/games/{platformName}/{gameId}/{mediaType}.{ext}
     * 如果该媒体类型已有文件，先删除旧文件再保存新文件
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam("gameId") Long gameId,
            @RequestParam("mediaType") String mediaTypeNomcourt) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 1. 验证媒体类型
            com.gamelist.model.MediaType mediaType = com.gamelist.model.MediaType.fromNomcourt(mediaTypeNomcourt);
            if (mediaType == null) {
                mediaType = com.gamelist.model.MediaType.fromNomcourtLenient(mediaTypeNomcourt);
            }
            if (mediaType == null) {
                response.put("success", false);
                response.put("message", "未知的媒体类型: " + mediaTypeNomcourt);
                return ResponseEntity.badRequest().body(response);
            }

            // 2. 获取游戏信息
            Game game = gameMapper.selectGameById(gameId);
            if (game == null) {
                response.put("success", false);
                response.put("message", "游戏不存在: " + gameId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // 3. 获取平台名称
            Platform platform = platformService.getPlatformById(game.getPlatformId());
            if (platform == null) {
                response.put("success", false);
                response.put("message", "平台不存在: " + game.getPlatformId());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            String platformName = platform.getName();

            // 4. 构建目标目录: ./data/scraper/games/{platformName}/{gameId}/
            Path gameMediaDir = Paths.get("./data/scraper/games", platformName, String.valueOf(gameId));
            Files.createDirectories(gameMediaDir);

            // 5. 删除该媒体类型的旧文件（按 mediaType nomcourt 前缀匹配）
            deleteExistingMediaFiles(gameMediaDir, mediaTypeNomcourt);

            // 6. 保存新文件
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFileName = mediaTypeNomcourt + extension;
            Path targetPath = gameMediaDir.resolve(newFileName);
            file.transferTo(targetPath.toFile());

            // 7. 构建相对路径并更新 Game 字段
            String relativePath = "./data/scraper/games/" + platformName + "/" + gameId + "/" + newFileName;
            updateGameField(game, mediaType, relativePath);

            logger.info("媒体上传成功: gameId={}, mediaType={}, path={}", gameId, mediaTypeNomcourt, relativePath);
            response.put("success", true);
            response.put("message", "上传成功");
            response.put("path", relativePath);
            response.put("mediaType", mediaTypeNomcourt);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("媒体上传失败: gameId={}, mediaType={}, error={}", gameId, mediaTypeNomcourt, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 删除指定游戏的指定媒体类型文件
     */
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteMedia(
            @RequestParam("gameId") Long gameId,
            @RequestParam("mediaType") String mediaTypeNomcourt) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 1. 验证媒体类型
            com.gamelist.model.MediaType mediaType = com.gamelist.model.MediaType.fromNomcourt(mediaTypeNomcourt);
            if (mediaType == null) {
                mediaType = com.gamelist.model.MediaType.fromNomcourtLenient(mediaTypeNomcourt);
            }
            if (mediaType == null) {
                response.put("success", false);
                response.put("message", "未知的媒体类型: " + mediaTypeNomcourt);
                return ResponseEntity.badRequest().body(response);
            }

            // 2. 获取游戏信息
            Game game = gameMapper.selectGameById(gameId);
            if (game == null) {
                response.put("success", false);
                response.put("message", "游戏不存在: " + gameId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // 3. 获取平台名称，构建目录
            Platform platform = platformService.getPlatformById(game.getPlatformId());
            if (platform != null) {
                Path gameMediaDir = Paths.get("./data/scraper/games", platform.getName(), String.valueOf(gameId));
                if (Files.exists(gameMediaDir)) {
                    deleteExistingMediaFiles(gameMediaDir, mediaTypeNomcourt);
                }
            }

            // 4. 清空 Game 字段
            clearGameField(game, mediaType);

            logger.info("媒体删除成功: gameId={}, mediaType={}", gameId, mediaTypeNomcourt);
            response.put("success", true);
            response.put("message", "删除成功");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("媒体删除失败: gameId={}, mediaType={}, error={}", gameId, mediaTypeNomcourt, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 查询指定游戏的所有媒体类型状态（文件是否存在、路径）
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMediaStatus(@RequestParam("gameId") Long gameId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Game game = gameMapper.selectGameById(gameId);
            if (game == null) {
                response.put("success", false);
                response.put("message", "游戏不存在: " + gameId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // 获取平台名称，用于构建检查路径
            Platform platform = platformService.getPlatformById(game.getPlatformId());
            String platformName = (platform != null) ? platform.getName() : null;
            Path gameMediaDir = null;
            if (platformName != null) {
                gameMediaDir = Paths.get("./data/scraper/games", platformName, String.valueOf(gameId));
            }

            // 遍历所有媒体类型，检查状态
            Map<String, Object> mediaStatusMap = new LinkedHashMap<>();
            for (com.gamelist.model.MediaType mt : com.gamelist.model.MediaType.values()) {
                Map<String, Object> status = new HashMap<>();
                
                // 通过反射获取当前字段值
                String currentValue = getGameFieldValue(game, mt);
                
                // 检查文件是否存在（使用多策略路径解析）
                boolean fileExists = false;
                String filePath = currentValue;
                
                if (currentValue != null && !currentValue.isEmpty()) {
                    // 清理路径：统一分隔符，去掉 ./ 前缀
                    String cleanPath = currentValue.replace("\\", "/");
                    if (cleanPath.startsWith("./")) {
                        cleanPath = cleanPath.substring(2);
                    }
                    
                    // 策略1：绝对路径
                    Path checkPath = Paths.get(cleanPath);
                    if (checkPath.isAbsolute() && Files.exists(checkPath)) {
                        fileExists = true;
                    }
                    
                    // 策略2：工作目录 + 相对路径（刮削/上传路径）
                    if (!fileExists) {
                        checkPath = Paths.get(cleanPath).toAbsolutePath();
                        if (Files.exists(checkPath)) {
                            fileExists = true;
                        }
                    }
                    
                    // 策略3：game.platformPath + 相对路径
                    if (!fileExists && game.getPlatformPath() != null && !game.getPlatformPath().isEmpty()) {
                        checkPath = Paths.get(game.getPlatformPath(), cleanPath);
                        if (Files.exists(checkPath)) {
                            fileExists = true;
                            filePath = cleanPath; // 使用清理后的相对路径
                        }
                    }
                    
                    // 策略4：platform.folderPath + 相对路径
                    if (!fileExists && platform != null && platform.getFolderPath() != null && !platform.getFolderPath().isEmpty()) {
                        checkPath = Paths.get(platform.getFolderPath(), cleanPath);
                        if (Files.exists(checkPath)) {
                            fileExists = true;
                            filePath = cleanPath;
                        }
                    }
                }
                
                // 如果 DB 路径不存在，检查固定目录
                if (!fileExists && gameMediaDir != null && Files.exists(gameMediaDir)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(gameMediaDir, mt.getNomcourt() + ".*")) {
                        for (Path entry : stream) {
                            fileExists = true;
                            filePath = entry.toString().replace("\\", "/");
                            if (!filePath.startsWith("./")) {
                                filePath = "./" + filePath;
                            }
                            break;
                        }
                    }
                }
                
                status.put("exists", fileExists);
                status.put("path", filePath);
                status.put("mediaFormat", mt.getMediaFormat());
                status.put("category", mt.getCategory());
                status.put("nomcourt", mt.getNomcourt());
                mediaStatusMap.put(mt.getNomcourt(), status);
            }

            response.put("success", true);
            response.put("data", mediaStatusMap);
            response.put("gameId", gameId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("查询媒体状态失败: gameId={}, error={}", gameId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ==================== 媒体上传/删除辅助方法 ====================

    /** 删除游戏目录下指定媒体类型的所有文件 */
    private void deleteExistingMediaFiles(Path gameMediaDir, String mediaTypeNomcourt) throws IOException {
        if (!Files.exists(gameMediaDir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gameMediaDir, mediaTypeNomcourt + ".*")) {
            for (Path existing : stream) {
                Files.delete(existing);
                logger.debug("删除旧媒体文件: {}", existing);
            }
        }
    }

    /** 通过反射更新 Game 的媒体字段，同时更新废弃兼容字段 */
    private void updateGameField(Game game, com.gamelist.model.MediaType mediaType, String path) {
        try {
            // 设置主字段（特殊处理 box3d → box3D 的命名差异）
            String setterName = mediaType.getSetterName();
            if ("setBox3d".equals(setterName)) setterName = "setBox3D";
            Method setter = Game.class.getMethod(setterName, String.class);
            setter.invoke(game, path);
            
            // 同步更新废弃兼容字段（双写）
            updateDeprecatedAlias(game, mediaType, path);
            
            gameMapper.updateGame(game);
        } catch (Exception e) {
            logger.warn("反射更新Game字段失败: mediaType={}, error={}", mediaType.getNomcourt(), e.getMessage());
        }
    }

    /** 通过反射清空 Game 的媒体字段 */
    private void clearGameField(Game game, com.gamelist.model.MediaType mediaType) {
        try {
            String setterName = mediaType.getSetterName();
            if ("setBox3d".equals(setterName)) setterName = "setBox3D";
            Method setter = Game.class.getMethod(setterName, String.class);
            setter.invoke(game, (String) null);
            
            // 同步清空废弃兼容字段
            clearDeprecatedAlias(game, mediaType);
            
            gameMapper.updateGame(game);
        } catch (Exception e) {
            logger.warn("反射清空Game字段失败: mediaType={}, error={}", mediaType.getNomcourt(), e.getMessage());
        }
    }

    /** 通过反射读取 Game 的媒体字段值 */
    private String getGameFieldValue(Game game, com.gamelist.model.MediaType mediaType) {
        try {
            String getterName = mediaType.getGetterName();
            if ("getBox3d".equals(getterName)) getterName = "getBox3D";
            Method getter = Game.class.getMethod(getterName);
            Object value = getter.invoke(game);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 废弃字段双写：新字段设置时同步设置旧字段 */
    private void updateDeprecatedAlias(Game game, com.gamelist.model.MediaType mediaType, String path) {
        try {
            switch (mediaType) {
                case BOX_2D:
                    game.setImage(path); game.setBoxFront(path); game.setBox2d(path);
                    break;
                case BOX_2D_BACK:
                    game.setBoxBack(path); game.setBox2dBack(path);
                    break;
                case BOX_2D_SIDE:
                    game.setBoxSpine(path); game.setBox2dSide(path);
                    break;
                case BOX_3D:
                    game.setBoxFull(path); game.setBox3D(path);
                    break;
                case SS:
                    game.setScreenshot(path); game.setSs(path);
                    break;
                case SSTITLE:
                    game.setTitlescreen(path); game.setSstitle(path);
                    break;
                case MARQUEE:
                    game.setLogo(path); game.setMarquee(path);
                    break;
                case SUPPORT_2D:
                    game.setCartridge(path); game.setSupport2d(path);
                    break;
                case BEZEL_4_3:
                    game.setBezel(path); game.setBezel43(path);
                    break;
                case BOX_TEXTURE:
                    game.setBoxtexture(path); game.setBoxTexture(path);
                    break;
                case SUPPORT_TEXTURE:
                    game.setSupporttexture(path); game.setSupportTexture(path);
                    break;
                case VIDEO_NORMALIZED:
                    game.setVideonormalized(path); game.setVideoNormalized(path);
                    break;
                case WHEEL_CARBON:
                    game.setWheelcarbon(path); game.setWheelCarbon(path);
                    break;
                case WHEEL_STEEL:
                    game.setWheelsteel(path); game.setWheelSteel(path);
                    break;
                case MANUEL:
                    game.setManual(path); game.setManuel(path);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            // 忽略废弃字段更新失败
        }
    }

    /** 废弃字段双写清空 */
    private void clearDeprecatedAlias(Game game, com.gamelist.model.MediaType mediaType) {
        updateDeprecatedAlias(game, mediaType, null);
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearMediaCache() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Path mediaDirPath = Paths.get(MEDIA_DIR);
            if (Files.exists(mediaDirPath)) {
                // 遍历并删除所有文件
                try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(mediaDirPath)) {
                    for (Path file : stream) {
                        Files.deleteIfExists(file);
                    }
                }
            }
            
            response.put("success", true);
            response.put("message", "媒体缓存已清空");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "清空媒体缓存失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
