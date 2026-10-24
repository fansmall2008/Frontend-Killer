package com.gamelist.service;

import java.util.List;
import java.util.Map;

import com.gamelist.model.ScraperSystem;

/**
 * 系统副本能力矩阵。
 * <p>
 * 能力由 scraper_system 的 type/rom_type/extensions 自动派生，
 * 用户在系统管理页手动调整后 source=manual，初始化器不再覆盖。
 */
public interface VariantCapabilityService {

    /** 按当前 scraper_system 重算能力矩阵（幂等；不覆盖 manual 行）。 */
    void refresh();

    /** 只返回合格系统的矩阵行。 */
    List<Map<String, Object>> getMatrix();

    /** 属性规则判定：该系统是否进入副本体系。 */
    boolean isEligible(ScraperSystem system);

    /** 系统是否支持某格式（original 恒为 true）。 */
    boolean isSupported(Integer systemId, String format);

    /** 系统支持的格式，按 bare/zip/7z 顺序返回。 */
    List<String> getSupportedFormats(Integer systemId);

    /** 上次导出选择的格式；未设置或不支持时返回 original。 */
    String getPreferredFormat(Integer systemId);

    /** 保存系统级导出偏好。 */
    void savePreference(Integer systemId, String format);

    /** 手动开启/关闭某格式（source=manual）。 */
    void updateCapability(Integer systemId, String format, boolean supported);
}
