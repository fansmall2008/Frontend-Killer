package com.gamelist.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

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

import com.gamelist.mapper.GameMapper;
import com.gamelist.model.Game;
import com.gamelist.model.Platform;
import com.gamelist.service.PlatformService;

@RestController
@RequestMapping("/api/media")
public class MediaController {

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
            // 处理Windows路径映射（D:\3do -> /data/roms）
            if (processedPath.startsWith("D:\\3do")) {
                processedPath = processedPath.replace("D:\\3do", "/data/roms");
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

    @GetMapping("/view")
    public ResponseEntity<?> viewMediaFile(@RequestParam String localPath, @RequestParam(required = false) Long platformId, @RequestParam(required = false) Long gameId, @RequestParam(required = false) String platformPath) {
        try {
            System.out.println("=== 媒体文件查看请求 ===");
            System.out.println("原始路径: " + localPath);
            System.out.println("平台ID: " + platformId);
            System.out.println("游戏ID: " + gameId);
            System.out.println("平台路径: " + platformPath);
            
            // 处理Windows路径分隔符
            String processedPath = localPath.replace("\\", "/");
            
            Path sourcePath = null;
            
            // 优先使用前端传递的platformPath
            if (platformPath != null && !platformPath.isEmpty()) {
                // 移除相对路径前缀 ./
                String mediaPath = processedPath.startsWith("./") ? processedPath.substring(2) : processedPath;
                // 构建完整路径
                String fullPath = platformPath + (mediaPath.startsWith("/") ? "" : "/") + mediaPath;
                sourcePath = Paths.get(fullPath);
                System.out.println("使用前端传递的platformPath构建的路径: " + sourcePath.toAbsolutePath());
                System.out.println("文件是否存在: " + Files.exists(sourcePath));
            }
            // 如果前端没有传递platformPath，尝试从游戏数据获取
            else if (gameId != null && gameId > 0) {
                Game game = gameMapper.selectGameById(gameId);
                if (game != null && game.getPlatformPath() != null && !game.getPlatformPath().isEmpty()) {
                    // 移除相对路径前缀 ./
                    String mediaPath = processedPath.startsWith("./") ? processedPath.substring(2) : processedPath;
                    // 构建完整路径
                    String fullPath = game.getPlatformPath() + (mediaPath.startsWith("/") ? "" : "/") + mediaPath;
                    sourcePath = Paths.get(fullPath);
                    System.out.println("使用游戏的platformPath构建的路径: " + sourcePath.toAbsolutePath());
                    System.out.println("文件是否存在: " + Files.exists(sourcePath));
                } else if (game != null && game.getPlatformId() != null) {
                    // 如果游戏的platformPath为空，尝试从平台获取
                    platformId = game.getPlatformId();
                    System.out.println("从游戏数据获取到 platformId: " + platformId);
                }
            }
            
            // 如果提供了平台ID，使用平台的folderPath构建完整路径
            if (sourcePath == null && platformId != null && platformId > 0) {
                Platform platform = platformService.getPlatformById(platformId);
                if (platform != null && platform.getFolderPath() != null) {
                    // 移除相对路径前缀 ./
                    String mediaPath = processedPath.startsWith("./") ? processedPath.substring(2) : processedPath;
                    // 构建完整路径
                    String fullPath = platform.getFolderPath() + (mediaPath.startsWith("/") ? "" : "/") + mediaPath;
                    sourcePath = Paths.get(fullPath);
                    System.out.println("使用平台folderPath构建的路径: " + sourcePath.toAbsolutePath());
                    System.out.println("文件是否存在: " + Files.exists(sourcePath));
                }
            }
            
            // 如果没有平台ID或平台路径不存在，尝试其他路径
            if (sourcePath == null || !Files.exists(sourcePath)) {
                // 处理Windows路径映射（D:\3do -> /data/roms）
                if (processedPath.startsWith("D:/3do")) {
                    processedPath = processedPath.replace("D:/3do", "/data/roms");
                    System.out.println("映射后路径: " + processedPath);
                }
                
                // 确保路径以 /data/roms 开头
                if (!processedPath.startsWith("/data/roms")) {
                    // 尝试直接添加 /data/roms 前缀
                    String testPath = "/data/roms" + (processedPath.startsWith("/") ? "" : "/") + processedPath;
                    Path testPathObj = Paths.get(testPath);
                    if (Files.exists(testPathObj)) {
                        processedPath = testPath;
                        System.out.println("添加 /data/roms 前缀: " + processedPath);
                    } else {
                        // 尝试移除所有前缀，直接使用媒体文件路径
                        int mediaIndex = processedPath.lastIndexOf("/media/");
                        if (mediaIndex > 0) {
                            String mediaPath = processedPath.substring(mediaIndex);
                            String directPath = "/data/roms" + mediaPath;
                            Path directPathObj = Paths.get(directPath);
                            if (Files.exists(directPathObj)) {
                                processedPath = directPath;
                                System.out.println("使用直接媒体路径: " + processedPath);
                            }
                        }
                    }
                }
                
                System.out.println("标准化路径: " + processedPath);
                sourcePath = Paths.get(processedPath);
                System.out.println("最终路径: " + sourcePath.toAbsolutePath());
                System.out.println("文件是否存在: " + Files.exists(sourcePath));
            }
            
            if (!Files.exists(sourcePath)) {
                // 尝试直接使用原始路径
                sourcePath = Paths.get(localPath);
                System.out.println("尝试原始路径: " + sourcePath.toAbsolutePath());
                System.out.println("原始路径是否存在: " + Files.exists(sourcePath));
                
                if (!Files.exists(sourcePath)) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "文件不存在: " + localPath);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }
            }
            
            // 检测文件类型
            String fileName = sourcePath.getFileName().toString().toLowerCase();
            MediaType mediaType;
            
            if (fileName.endsWith(".png")) {
                mediaType = MediaType.IMAGE_PNG;
            } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                mediaType = MediaType.IMAGE_JPEG;
            } else if (fileName.endsWith(".gif")) {
                mediaType = MediaType.IMAGE_GIF;
            } else if (fileName.endsWith(".mp4")) {
                mediaType = MediaType.parseMediaType("video/mp4");
            } else if (fileName.endsWith(".avi")) {
                mediaType = MediaType.parseMediaType("video/avi");
            } else if (fileName.endsWith(".mkv")) {
                mediaType = MediaType.parseMediaType("video/x-matroska");
            } else if (fileName.endsWith(".pdf")) {
                mediaType = MediaType.APPLICATION_PDF;
            } else {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
            
            // 创建输入流资源
            InputStreamResource resource = new InputStreamResource(Files.newInputStream(sourcePath));
            
            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("attachment", sourcePath.getFileName().toString());
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(mediaType)
                    .body(resource);
            
        } catch (IOException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "读取文件失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
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
