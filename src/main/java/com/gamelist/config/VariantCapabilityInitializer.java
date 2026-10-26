package com.gamelist.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.gamelist.service.VariantCapabilityService;

/**
 * 启动后按 scraper_system 属性派生版本能力矩阵。
 * <p>
 * ScraperSystemInitializer 可能在后台线程加载基础数据，因此矩阵接口也会懒刷新；
 * 这里只做一次启动预热，失败不影响应用启动。
 */
@Component
public class VariantCapabilityInitializer {

    private static final Logger logger = LoggerFactory.getLogger(VariantCapabilityInitializer.class);

    @Autowired
    private VariantCapabilityService variantCapabilityService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            variantCapabilityService.refresh();
        } catch (Exception e) {
            logger.warn("启动时刷新版本能力矩阵失败: {}", e.getMessage());
        }
    }
}
