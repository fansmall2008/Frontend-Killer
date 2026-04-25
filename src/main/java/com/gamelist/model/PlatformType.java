package com.gamelist.model;

public class PlatformType {
    public static final String RECALBOX = "recalbox";
    public static final String BATOCERA = "batocera";
    public static final String RETROBAT = "retrobat";
    public static final String ESDE = "esde";
    public static final String GENERIC = "generic";
    
    private PlatformType() {
        // 私有构造函数，防止实例化
    }
    
    /**
     * 检查是否为支持的平台类型
     * @param platformType 平台类型字符串
     * @return 是否为支持的平台类型
     */
    public static boolean isValidPlatformType(String platformType) {
        return RECALBOX.equals(platformType) ||
               BATOCERA.equals(platformType) ||
               RETROBAT.equals(platformType) ||
               ESDE.equals(platformType) ||
               GENERIC.equals(platformType);
    }
    
    /**
     * 根据平台名称识别平台类型
     * @param platformName 平台名称或描述
     * @return 识别的平台类型
     */
    public static String detectPlatformType(String platformName) {
        if (platformName == null) {
            return GENERIC;
        }
        
        String lowerName = platformName.toLowerCase();
        if (lowerName.contains("recalbox")) {
            return RECALBOX;
        } else if (lowerName.contains("batocera")) {
            return BATOCERA;
        } else if (lowerName.contains("retrobat")) {
            return RETROBAT;
        } else if (lowerName.contains("esde")) {
            return ESDE;
        } else {
            return GENERIC;
        }
    }
}
