package com.gamelist.util;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gamelist.model.Game;
import com.gamelist.model.MediaType;

/**
 * 统一字段访问器 — 替代 ExportServiceImpl/XmlDataFileGenerator/TextDataFileGenerator 中的 switch-case。
 * <p>
 * 支持通过以下任意名称获取/设置 Game 字段值：
 * <ul>
 *   <li>数据库列名（如 "box_2d"）</li>
 *   <li>nomcourt 值（如 "box-2D"）</li>
 *   <li>Java 字段名（如 "box2d"）</li>
 *   <li>旧版别名（如 "box2dfront"、"description"）</li>
 * </ul>
 */
public final class GameFieldAccessor {

    private static final Logger logger = LoggerFactory.getLogger(GameFieldAccessor.class);

    /** 字段别名 → getter（含媒体字段 + 非媒体字段） */
    private static final Map<String, Function<Game, String>> FIELD_GETTERS = new HashMap<>();

    /** 字段别名 → setter（含媒体字段 + 非媒体字段） */
    private static final Map<String, BiConsumer<Game, String>> FIELD_SETTERS = new HashMap<>();

    /** 所有支持的字段名集合 */
    private static final Set<String> SUPPORTED_FIELDS = new LinkedHashSet<>();

    static {
        // ==================== 非媒体字段 ====================
        registerSimpleField("name", Game::getName, Game::setName);
        registerSimpleField("translatedName", Game::getTranslatedName, Game::setTranslatedName);
        registerSimpleField("desc", Game::getDesc, Game::setDesc);
        registerAlias("description", "desc");
        registerAlias("translatedDesc_get", null); // placeholder, handled specially
        registerSimpleField("translatedDesc", Game::getTranslatedDesc, Game::setTranslatedDesc);
        registerSimpleField("rating", g -> g.getRating() != null ? g.getRating().toString() : null,
                (g, v) -> { try { g.setRating(v != null ? Double.parseDouble(v) : null); } catch (NumberFormatException e) { /* ignore */ } });
        registerSimpleField("releasedate", Game::getReleasedate, Game::setReleasedate);
        registerAlias("releaseDate", "releasedate");
        registerSimpleField("releaseYear",
                g -> { String d = g.getReleasedate(); return d != null && d.length() >= 4 ? d.substring(0, 4) : null; },
                null); // releaseYear 只读，不能直接 set
        registerSimpleField("developer", Game::getDeveloper, Game::setDeveloper);
        registerSimpleField("publisher", Game::getPublisher, Game::setPublisher);
        registerSimpleField("genre", Game::getGenre, Game::setGenre);
        registerAlias("category", "genre");
        registerSimpleField("players", Game::getPlayers, Game::setPlayers);
        registerSimpleField("lang", Game::getLang, Game::setLang);
        registerAlias("region", "lang");
        registerSimpleField("path", Game::getPath, Game::setPath);
        registerAlias("file", "path");
        registerSimpleField("hash", Game::getHash, Game::setHash);
        registerSimpleField("crc32", Game::getCrc32, Game::setCrc32);
        registerSimpleField("md5", Game::getMd5, Game::setMd5);
        registerSimpleField("gameId", Game::getGameId, Game::setGameId);
        registerSimpleField("source", Game::getSource, Game::setSource);
        registerSimpleField("platformType", Game::getPlatformType, Game::setPlatformType);
        registerSimpleField("sortBy", Game::getSortBy, Game::setSortBy);
        registerAlias("sort-by", "sortBy");

        // 特殊计算字段：filename（从 path 提取不含扩展名的文件名）
        registerSimpleField("filename", g -> {
            String p = g.getPath();
            if (p == null) return null;
            int sep = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
            String fn = sep >= 0 ? p.substring(sep + 1) : p;
            int dot = fn.lastIndexOf('.');
            return dot >= 0 ? fn.substring(0, dot) : fn;
        }, null); // 只读

        // 旧版遗留字段（@Deprecated，但导入导出仍可能用到）
        registerSimpleField("image", Game::getImage, Game::setImage);
        registerSimpleField("thumbnail", Game::getThumbnail, Game::setThumbnail);
        registerSimpleField("manual", Game::getManual, Game::setManual);
        registerAlias("manuel", "manual");
        registerSimpleField("boxFront", Game::getBoxFront, Game::setBoxFront);
        registerSimpleField("boxBack", Game::getBoxBack, Game::setBoxBack);
        registerSimpleField("boxSpine", Game::getBoxSpine, Game::setBoxSpine);
        registerSimpleField("boxFull", Game::getBoxFull, Game::setBoxFull);
        registerSimpleField("cartridge", Game::getCartridge, Game::setCartridge);
        registerSimpleField("logo", Game::getLogo, Game::setLogo);
        registerSimpleField("bezel", Game::getBezel, Game::setBezel);
        registerSimpleField("panel", Game::getPanel, Game::setPanel);
        registerSimpleField("cabinetLeft", Game::getCabinetLeft, Game::setCabinetLeft);
        registerSimpleField("cabinetRight", Game::getCabinetRight, Game::setCabinetRight);
        registerSimpleField("tile", Game::getTile, Game::setTile);
        registerSimpleField("banner", Game::getBanner, Game::setBanner);
        registerSimpleField("steam", Game::getSteam, Game::setSteam);
        registerSimpleField("poster", Game::getPoster, Game::setPoster);
        registerSimpleField("background", Game::getBackground, Game::setBackground);
        registerSimpleField("music", Game::getMusic, Game::setMusic);
        registerSimpleField("screenshot", Game::getScreenshot, Game::setScreenshot);
        registerSimpleField("titlescreen", Game::getTitlescreen, Game::setTitlescreen);

        // ==================== 媒体字段（通过 MediaType 枚举自动注册） ====================
        for (MediaType mt : MediaType.values()) {
            // 注册 nomcourt、dbColumn、javaField 三种名称
            String getterName = mt.getGetterName();
            String setterName = mt.getSetterName();

            Function<Game, String> getter = buildMediaGetter(getterName);
            BiConsumer<Game, String> setter = buildMediaSetter(setterName);

            // nomcourt（如 "box-2D"）
            registerRaw(mt.getNomcourt(), getter, setter);
            // dbColumn（如 "box_2d"）
            registerRaw(mt.getDbColumn(), getter, setter);
            // javaField（如 "box2d"）
            registerRaw(mt.getJavaField(), getter, setter);
        }
    }

    private GameFieldAccessor() {} // 工具类禁止实例化

    // ==================== 公开 API ====================

    /**
     * 根据任意字段名（数据库列名、nomcourt、Java 字段名、旧版别名）获取 Game 字段值。
     * @return 字段值，未找到或为空时返回 null
     */
    public static String getValue(Game game, String fieldName) {
        if (game == null || fieldName == null || fieldName.isEmpty()) return null;
        Function<Game, String> getter = FIELD_GETTERS.get(fieldName);
        if (getter != null) {
            try {
                return getter.apply(game);
            } catch (Exception e) {
                logger.warn("获取字段值失败: fieldName={}, error={}", fieldName, e.getMessage());
                return null;
            }
        }
        // 尝试忽略大小写模糊匹配
        String normalized = fieldName.toLowerCase().replace("-", "").replace("_", "");
        for (Map.Entry<String, Function<Game, String>> entry : FIELD_GETTERS.entrySet()) {
            String keyNorm = entry.getKey().toLowerCase().replace("-", "").replace("_", "");
            if (keyNorm.equals(normalized)) {
                try {
                    return entry.getValue().apply(game);
                } catch (Exception e) {
                    logger.warn("模糊匹配获取字段值失败: fieldName={}, matched={}, error={}", fieldName, entry.getKey(), e.getMessage());
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 根据任意字段名设置 Game 字段值。
     */
    public static void setValue(Game game, String fieldName, String value) {
        if (game == null || fieldName == null || fieldName.isEmpty()) return;
        BiConsumer<Game, String> setter = FIELD_SETTERS.get(fieldName);
        if (setter != null) {
            try {
                setter.accept(game, value);
                return;
            } catch (Exception e) {
                logger.warn("设置字段值失败: fieldName={}, error={}", fieldName, e.getMessage());
                return;
            }
        }
        // 尝试忽略大小写模糊匹配
        String normalized = fieldName.toLowerCase().replace("-", "").replace("_", "");
        for (Map.Entry<String, BiConsumer<Game, String>> entry : FIELD_SETTERS.entrySet()) {
            String keyNorm = entry.getKey().toLowerCase().replace("-", "").replace("_", "");
            if (keyNorm.equals(normalized)) {
                try {
                    entry.getValue().accept(game, value);
                    return;
                } catch (Exception e) {
                    logger.warn("模糊匹配设置字段值失败: fieldName={}, matched={}, error={}", fieldName, entry.getKey(), e.getMessage());
                }
            }
        }
        logger.debug("未找到可设置的字段: {}", fieldName);
    }

    /**
     * 获取所有支持的字段名集合。
     */
    public static Set<String> getSupportedFields() {
        return SUPPORTED_FIELDS;
    }

    /**
     * 检查字段名是否受支持。
     */
    public static boolean isSupported(String fieldName) {
        if (fieldName == null) return false;
        if (FIELD_GETTERS.containsKey(fieldName)) return true;
        String normalized = fieldName.toLowerCase().replace("-", "").replace("_", "");
        for (String key : FIELD_GETTERS.keySet()) {
            if (key.toLowerCase().replace("-", "").replace("_", "").equals(normalized)) return true;
        }
        return false;
    }

    // ==================== 内部注册方法 ====================

    private static void registerSimpleField(String name, Function<Game, String> getter, BiConsumer<Game, String> setter) {
        FIELD_GETTERS.put(name, getter);
        if (setter != null) {
            FIELD_SETTERS.put(name, setter);
        }
        SUPPORTED_FIELDS.add(name);
    }

    private static void registerRaw(String name, Function<Game, String> getter, BiConsumer<Game, String> setter) {
        // 不覆盖已有的手动注册（手动注册优先）
        FIELD_GETTERS.putIfAbsent(name, getter);
        if (setter != null) {
            FIELD_SETTERS.putIfAbsent(name, setter);
        }
        SUPPORTED_FIELDS.add(name);
    }

    private static void registerAlias(String alias, String targetField) {
        Function<Game, String> getter = FIELD_GETTERS.get(targetField);
        BiConsumer<Game, String> setter = FIELD_SETTERS.get(targetField);
        if (getter != null) {
            FIELD_GETTERS.putIfAbsent(alias, getter);
        }
        if (setter != null) {
            FIELD_SETTERS.putIfAbsent(alias, setter);
        }
        SUPPORTED_FIELDS.add(alias);
    }

    private static Function<Game, String> buildMediaGetter(String getterName) {
        return game -> {
            try {
                Method m = Game.class.getMethod(getterName);
                Object val = m.invoke(game);
                return val != null ? val.toString() : null;
            } catch (Exception e) {
                logger.trace("反射获取媒体字段失败: getter={}", getterName);
                return null;
            }
        };
    }

    private static BiConsumer<Game, String> buildMediaSetter(String setterName) {
        return (game, value) -> {
            try {
                Method m = Game.class.getMethod(setterName, String.class);
                m.invoke(game, value);
            } catch (Exception e) {
                logger.trace("反射设置媒体字段失败: setter={}", setterName);
            }
        };
    }
}
