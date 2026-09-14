package com.gamelist.model;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ScreenScraper 官方媒体类型枚举
 * 数据来源：mediasJeuListe API（50个官方类型）
 * 
 * 命名规则：
 * - nomcourt: ScreenScraper API 原值（如 "box-2D"）
 * - dbColumn: 数据库列名 = nomcourt 中 '-' 替换为 '_'，全部小写（如 "box_2d"）
 * - javaField: Java 字段名 = dbColumn snake_case 转 camelCase（如 "box2d"）
 */
public enum MediaType {

    // ==================== Bezels (边框类) ====================
    BEZEL_16_9_COCKTAIL("bezel-16-9-cocktail", "bezel_16_9_cocktail", "bezel169Cocktail", "Bezels", "image"),
    BEZEL_16_9("bezel-16-9", "bezel_16_9", "bezel169", "Bezels", "image"),
    BEZEL_16_9_V("bezel-16-9-v", "bezel_16_9_v", "bezel169V", "Bezels", "image"),
    BEZEL_4_3_COCKTAIL("bezel-4-3-cocktail", "bezel_4_3_cocktail", "bezel43Cocktail", "Bezels", "image"),
    BEZEL_4_3("bezel-4-3", "bezel_4_3", "bezel43", "Bezels", "image"),
    BEZEL_4_3_V("bezel-4-3-v", "bezel_4_3_v", "bezel43V", "Bezels", "image"),

    // ==================== Boitiers (包装盒类) ====================
    BOX_3D("box-3D", "box_3d", "box3D", "Boitiers", "image"),
    BOX_TEXTURE("box-texture", "box_texture", "boxTexture", "Boitiers", "image"),

    // ==================== Elements Boitiers (包装盒元素类) ====================
    BOX_2D_BACK("box-2D-back", "box_2d_back", "box2dBack", "Elements Boitiers", "image"),
    BOX_2D("box-2D", "box_2d", "box2d", "Elements Boitiers", "image"),
    BOX_2D_SIDE("box-2D-side", "box_2d_side", "box2dSide", "Elements Boitiers", "image"),

    // ==================== Logos (Wheels) (轮盘类) ====================
    WHEEL_HD("wheel-hd", "wheel_hd", "wheelHd", "Logos (Wheels)", "image"),
    WHEEL("wheel", "wheel", "wheel", "Logos (Wheels)", "image"),
    WHEEL_CARBON("wheel-carbon", "wheel_carbon", "wheelCarbon", "Logos (Wheels)", "image"),
    WHEEL_STEEL("wheel-steel", "wheel_steel", "wheelSteel", "Logos (Wheels)", "image"),

    // ==================== Marquee (霓虹灯类) ====================
    MARQUEE("marquee", "marquee", "marquee", "Marquee", "image"),
    SCREENMARQUEESMALL("screenmarqueesmall", "screenmarqueesmall", "screenmarqueesmall", "Marquee", "image"),
    SCREENMARQUEE("screenmarquee", "screenmarquee", "screenmarquee", "Marquee", "image"),

    // ==================== Médias (通用媒体类) ====================
    FANART("fanart", "fanart", "fanart", "Médias", "image"),
    OVERLAY("overlay", "overlay", "overlay", "Médias", "image"),
    SS("ss", "ss", "ss", "Médias", "image"),
    SSTITLE("sstitle", "sstitle", "sstitle", "Médias", "image"),
    STEAMGRID("steamgrid", "steamgrid", "steamgrid", "Médias", "image"),
    VIDEO("video", "video", "video", "Médias", "video"),
    VIDEO_NORMALIZED("video-normalized", "video_normalized", "videoNormalized", "Médias", "video"),

    // ==================== Médias Pincab (弹球台媒体类) ====================
    SSDMD("ssdmd", "ssdmd", "ssdmd", "Médias Pincab", "image"),
    SSFRONTON16_9("ssfronton16-9", "ssfronton16_9", "ssfronton169", "Médias Pincab", "image"),
    SSFRONTON1_1("ssfronton1-1", "ssfronton1_1", "ssfronton11", "Médias Pincab", "image"),
    SSFRONTON4_3("ssfronton4-3", "ssfronton4_3", "ssfronton43", "Médias Pincab", "image"),
    SSTABLE("sstable", "sstable", "sstable", "Médias Pincab", "image"),
    STOPPER("sstopper", "sstopper", "sstopper", "Médias Pincab", "image"),
    VIDEODMD("videodmd", "videodmd", "videodmd", "Médias Pincab", "video"),
    VIDEOFRONTON4_3("videofronton4-3", "videofronton4_3", "videofronton43", "Médias Pincab", "video"),
    VIDEOFRONTON16_9("videofronton16-9", "videofronton16_9", "videofronton169", "Médias Pincab", "video"),
    VIDEOTABLE4K("videotable4k", "videotable4k", "videotable4k", "Médias Pincab", "video"),
    VIDEOTABLE("videotable", "videotable", "videotable", "Médias Pincab", "video"),
    VIDEOTOPPER("videotopper", "videotopper", "videotopper", "Médias Pincab", "video"),
    WHEEL_TARCISIOS("wheel-tarcisios", "wheel_tarcisios", "wheelTarcisios", "Médias Pincab", "image"),

    // ==================== Médias Secondaires (次要媒体类) ====================
    FIGURINE("figurine", "figurine", "figurine", "Médias Secondaires", "image"),
    FLYER("flyer", "flyer", "flyer", "Médias Secondaires", "image"),
    MANUEL("manuel", "manuel", "manuel", "Médias Secondaires", "doc"),
    MAPS("maps", "maps", "maps", "Médias Secondaires", "image"),

    // ==================== Images Secondaires (补充图片类) ====================
    BACKGROUND("background", "background", "background", "Images Secondaires", "image"),
    PICTOLISTE("pictoliste", "pictoliste", "pictoliste", "Images Secondaires", "image"),
    PICTOMONOCHROME("pictomonochrome", "pictomonochrome", "pictomonochrome", "Images Secondaires", "image"),
    PICTOCOULEUR("pictocouleur", "pictocouleur", "pictocouleur", "Images Secondaires", "image"),

    // ==================== Mixes (混合类) ====================
    MIXRBV1("mixrbv1", "mixrbv1", "mixrbv1", "Mixes", "image"),
    MIXRBV2("mixrbv2", "mixrbv2", "mixrbv2", "Mixes", "image"),

    // ==================== Sources (源类) ====================
    BOX_SCAN("box-scan", "box_scan", "boxScan", "Sources", "image"),
    SUPPORT_SCAN("support-scan", "support_scan", "supportScan", "Sources", "image"),

    // ==================== Supports (支撑类) ====================
    SUPPORT_2D("support-2D", "support_2d", "support2d", "Supports", "image"),
    SUPPORT_TEXTURE("support-texture", "support_texture", "supportTexture", "Supports", "image"),

    // ==================== Themes (主题类) ====================
    THEMEHB("themehb", "themehb", "themehb", "Themes", "theme"),
    THEMEHS("themehs", "themehs", "themehs", "Themes", "theme");

    // ==================== 字段定义 ====================

    /** ScreenScraper API 原值（如 "box-2D"） */
    private final String nomcourt;

    /** 数据库列名（如 "box_2d"） */
    private final String dbColumn;

    /** Java 字段名（如 "box2d"） */
    private final String javaField;

    /** 官方分类 */
    private final String category;

    /** 媒体格式：image / video / doc / theme */
    private final String mediaFormat;

    // ==================== 查找索引 ====================

    private static final Map<String, MediaType> BY_NOMCOURT = Arrays.stream(values())
            .collect(Collectors.toMap(MediaType::getNomcourt, Function.identity()));

    private static final Map<String, MediaType> BY_DB_COLUMN = Arrays.stream(values())
            .collect(Collectors.toMap(MediaType::getDbColumn, Function.identity()));

    private static final Map<String, MediaType> BY_JAVA_FIELD = Arrays.stream(values())
            .collect(Collectors.toMap(MediaType::getJavaField, Function.identity()));

    // ==================== 构造方法 ====================

    MediaType(String nomcourt, String dbColumn, String javaField, String category, String mediaFormat) {
        this.nomcourt = nomcourt;
        this.dbColumn = dbColumn;
        this.javaField = javaField;
        this.category = category;
        this.mediaFormat = mediaFormat;
    }

    // ==================== Getter ====================

    public String getNomcourt() { return nomcourt; }
    public String getDbColumn() { return dbColumn; }
    public String getJavaField() { return javaField; }
    public String getCategory() { return category; }
    public String getMediaFormat() { return mediaFormat; }

    /** 获取 Java getter 方法名（如 "getBox2d"） */
    public String getGetterName() {
        return "get" + Character.toUpperCase(javaField.charAt(0)) + javaField.substring(1);
    }

    /** 获取 Java setter 方法名（如 "setBox2d"） */
    public String getSetterName() {
        return "set" + Character.toUpperCase(javaField.charAt(0)) + javaField.substring(1);
    }

    /** 是否为图片类型 */
    public boolean isImage() { return "image".equals(mediaFormat); }

    /** 是否为视频类型 */
    public boolean isVideo() { return "video".equals(mediaFormat); }

    /** 是否为文档类型 */
    public boolean isDoc() { return "doc".equals(mediaFormat); }

    /** 是否为可忽略类型（mix/theme 等通常不需要下载） */
    public boolean isIgnorable() { return "theme".equals(mediaFormat) || this == MIXRBV1 || this == MIXRBV2; }

    // ==================== 静态查找方法 ====================

    /** 根据 ScreenScraper nomcourt 查找（如 "box-2D"） */
    public static MediaType fromNomcourt(String nomcourt) {
        return BY_NOMCOURT.get(nomcourt);
    }

    /** 根据数据库列名查找（如 "box_2d"） */
    public static MediaType fromDbColumn(String dbColumn) {
        return BY_DB_COLUMN.get(dbColumn);
    }

    /** 根据 Java 字段名查找（如 "box2d"） */
    public static MediaType fromJavaField(String javaField) {
        return BY_JAVA_FIELD.get(javaField);
    }

    /**
     * 根据 ScreenScraper nomcourt 模糊查找（忽略大小写和连字符）
     * 用于兼容旧代码中 "box2d", "Box2D", "BOX-2D" 等变体
     */
    public static MediaType fromNomcourtLenient(String input) {
        if (input == null) return null;
        String normalized = input.toLowerCase().replace("-", "").replace("_", "");
        for (MediaType mt : values()) {
            String mtNorm = mt.nomcourt.toLowerCase().replace("-", "").replace("_", "");
            if (mtNorm.equals(normalized)) {
                return mt;
            }
        }
        return null;
    }

    /** 获取所有可下载的媒体类型（排除 theme 和 mix） */
    public static MediaType[] getDownloadableTypes() {
        return Arrays.stream(values())
                .filter(mt -> !mt.isIgnorable())
                .toArray(MediaType[]::new);
    }

    /** 按分类获取媒体类型 */
    public static MediaType[] getByCategory(String category) {
        return Arrays.stream(values())
                .filter(mt -> mt.category.equals(category))
                .toArray(MediaType[]::new);
    }

    /** 获取所有分类名称 */
    public static String[] getCategories() {
        return Arrays.stream(values())
                .map(MediaType::getCategory)
                .distinct()
                .toArray(String[]::new);
    }
}
