package com.gamelist.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.service.SystemSettingsService;

/**
 * 主题包管理 API
 * <p>
 * 主题包存放在 data/themes/ 目录下，每个主题一个子目录，
 * 目录内包含 theme.json 描述文件，可选 sounds/ 和 fonts/ 子目录。
 */
@RestController
@RequestMapping("/api/themes")
public class ThemeController {

    private static final Logger logger = LoggerFactory.getLogger(ThemeController.class);
    private static final String THEMES_DIR = "./data/themes";
    private static final String DEFAULT_THEME_ID = "default";
    private static final String CLASSPATH_DEFAULT_THEME = "themes/default/theme.json";

    @Autowired
    private SystemSettingsService settingsService;

    /* ========== 列出所有可用主题包 ========== */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listThemes() {
        List<Map<String, Object>> themes = new ArrayList<>();
        try {
            Path themesPath = Paths.get(THEMES_DIR);
            if (!Files.exists(themesPath)) {
                Files.createDirectories(themesPath);
            }

            // 如果目录下没有任何主题，释放默认主题
            if (isThemesDirEmpty(themesPath)) {
                extractDefaultTheme();
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(themesPath, Files::isDirectory)) {
                for (Path themeDir : stream) {
                    Path themeJson = themeDir.resolve("theme.json");
                    if (Files.exists(themeJson)) {
                        String json = Files.readString(themeJson, StandardCharsets.UTF_8);
                        JSONObject obj = new JSONObject(json);
                        Map<String, Object> info = new HashMap<>();
                        info.put("id", obj.optString("id", themeDir.getFileName().toString()));
                        info.put("name", obj.optString("name", "Unknown"));
                        info.put("nameEn", obj.optString("nameEn", ""));
                        info.put("nameJa", obj.optString("nameJa", ""));
                        info.put("description", obj.optString("description", ""));
                        info.put("version", obj.optString("version", "1.0"));
                        info.put("author", obj.optString("author", ""));
                        info.put("hasSounds", obj.has("sounds"));
                        info.put("hasCustomFonts", obj.has("fonts") && !obj.optJSONObject("fonts").isEmpty());
                        themes.add(info);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("列出主题包失败", e);
        }
        return ResponseEntity.ok(themes);
    }

    /* ========== 获取指定主题包完整 JSON ========== */
    @GetMapping("/{themeId}")
    public ResponseEntity<String> getTheme(@PathVariable String themeId) {
        try {
            Path themeJson = Paths.get(THEMES_DIR, themeId, "theme.json");
            if (Files.exists(themeJson)) {
                return ResponseEntity.ok(Files.readString(themeJson, StandardCharsets.UTF_8));
            }
            // 请求默认主题时尝试从 classpath 读取
            if (DEFAULT_THEME_ID.equals(themeId)) {
                return readClasspathTheme();
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("读取主题包失败: {}", themeId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /* ========== 获取当前应用的主题 ========== */
    @GetMapping("/current")
    public ResponseEntity<String> getCurrentTheme() {
        String themeId = settingsService.get("active_theme", DEFAULT_THEME_ID);
        try {
            Path themeJson = Paths.get(THEMES_DIR, themeId, "theme.json");
            if (Files.exists(themeJson)) {
                return ResponseEntity.ok(Files.readString(themeJson, StandardCharsets.UTF_8));
            }
            if (DEFAULT_THEME_ID.equals(themeId)) {
                return readClasspathTheme();
            }
        } catch (Exception e) {
            logger.error("读取当前主题失败", e);
        }
        // 兜底返回硬编码默认主题
        return ResponseEntity.ok(buildHardcodedDefaultTheme());
    }

    /* ========== 应用主题包 ========== */
    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> applyTheme(@RequestParam String themeId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Path themeJson = Paths.get(THEMES_DIR, themeId, "theme.json");
            if (!Files.exists(themeJson) && !DEFAULT_THEME_ID.equals(themeId)) {
                resp.put("success", false);
                resp.put("message", "主题包不存在: " + themeId);
                return ResponseEntity.ok(resp);
            }
            settingsService.set("active_theme", themeId);
            resp.put("success", true);
            resp.put("message", "主题已应用");
            resp.put("themeId", themeId);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("应用主题失败", e);
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.ok(resp);
        }
    }

    /* ========== 提供主题音效文件 ========== */
    @GetMapping("/{themeId}/sounds/{fileName}")
    public ResponseEntity<byte[]> getSoundFile(
            @PathVariable String themeId,
            @PathVariable String fileName) {
        try {
            if (themeId.contains("..") || fileName.contains("..")) {
                return ResponseEntity.badRequest().build();
            }
            Path soundFile = Paths.get(THEMES_DIR, themeId, "sounds", fileName);
            if (Files.exists(soundFile)) {
                byte[] data = Files.readAllBytes(soundFile);
                String contentType = guessAudioContentType(fileName);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .body(data);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("读取音效文件失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /* ========== 提供主题字体文件 ========== */
    @GetMapping("/{themeId}/fonts/{fileName}")
    public ResponseEntity<byte[]> getFontFile(
            @PathVariable String themeId,
            @PathVariable String fileName) {
        try {
            if (themeId.contains("..") || fileName.contains("..")) {
                return ResponseEntity.badRequest().build();
            }
            Path fontFile = Paths.get(THEMES_DIR, themeId, "fonts", fileName);
            if (Files.exists(fontFile)) {
                byte[] data = Files.readAllBytes(fontFile);
                String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
                String contentType = switch (ext) {
                    case "woff2" -> "font/woff2";
                    case "woff"  -> "font/woff";
                    case "ttf"   -> "font/ttf";
                    case "otf"   -> "font/otf";
                    default      -> "application/octet-stream";
                };
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .body(data);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("读取字体文件失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ===================== 内部方法 =====================

    private boolean isThemesDirEmpty(Path themesPath) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(themesPath, Files::isDirectory)) {
            return !stream.iterator().hasNext();
        }
    }

    /**
     * 将 classpath 中的默认主题释放到 data/themes/default/
     */
    private void extractDefaultTheme() {
        try {
            Path defaultDir = Paths.get(THEMES_DIR, DEFAULT_THEME_ID);
            Files.createDirectories(defaultDir);
            ClassPathResource res = new ClassPathResource(CLASSPATH_DEFAULT_THEME);
            if (res.exists()) {
                try (InputStream is = res.getInputStream()) {
                    Files.copy(is, defaultDir.resolve("theme.json"));
                }
                logger.info("已释放默认主题包到 {}", defaultDir);
            } else {
                // classpath 也没有，直接写一份硬编码的
                Files.writeString(defaultDir.resolve("theme.json"),
                        buildHardcodedDefaultTheme(), StandardCharsets.UTF_8);
                logger.info("已写入硬编码默认主题包到 {}", defaultDir);
            }
        } catch (Exception e) {
            logger.error("释放默认主题失败", e);
        }
    }

    private ResponseEntity<String> readClasspathTheme() {
        try {
            ClassPathResource res = new ClassPathResource(CLASSPATH_DEFAULT_THEME);
            if (res.exists()) {
                try (InputStream is = res.getInputStream()) {
                    String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    return ResponseEntity.ok(json);
                }
            }
        } catch (Exception e) {
            logger.error("读取 classpath 主题失败", e);
        }
        return ResponseEntity.ok(buildHardcodedDefaultTheme());
    }

    private String guessAudioContentType(String fileName) {
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "mp3"  -> "audio/mpeg";
            case "wav"  -> "audio/wav";
            case "ogg"  -> "audio/ogg";
            case "m4a"  -> "audio/mp4";
            default     -> "application/octet-stream";
        };
    }

    /**
     * 硬编码的默认主题 JSON（兜底方案）
     */
    private String buildHardcodedDefaultTheme() {
        return """
        {
          "id": "default",
          "name": "默认",
          "nameEn": "Default",
          "nameJa": "デフォルト",
          "description": "简洁高效的默认主题，轻柔的合成音效",
          "version": "1.0",
          "author": "Frontend-Killer",
          "sounds": {
            "click":          { "type": "synth", "freq": 880,  "freqEnd": 700,  "wave": "sine",     "duration": 0.05, "volume": 0.25 },
            "success":        { "type": "synth", "freq": 523,  "freqEnd": 1047, "wave": "sine",     "duration": 0.3,  "volume": 0.35 },
            "error":          { "type": "synth", "freq": 400,  "freqEnd": 200,  "wave": "sawtooth", "duration": 0.3,  "volume": 0.25 },
            "notification":   { "type": "synth", "freq": 660,  "freqEnd": 880,  "wave": "sine",     "duration": 0.15, "volume": 0.30 },
            "task_complete":  { "type": "synth", "freq": 440,  "freqEnd": 880,  "wave": "triangle", "duration": 0.4,  "volume": 0.35 },
            "task_fail":      { "type": "synth", "freq": 350,  "freqEnd": 150,  "wave": "sawtooth", "duration": 0.4,  "volume": 0.25 },
            "warning":        { "type": "synth", "freq": 600,  "freqEnd": 400,  "wave": "square",   "duration": 0.2,  "volume": 0.20 },
            "theme_switch":   { "type": "synth", "freq": 440,  "freqEnd": 880,  "wave": "sine",     "duration": 0.5,  "volume": 0.30 }
          }
        }
        """;
    }
}
