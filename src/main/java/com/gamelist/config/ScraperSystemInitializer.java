package com.gamelist.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.gamelist.model.ScraperSystem;
import com.gamelist.service.ScraperSettingsService;
import com.gamelist.service.ScraperSystemService;
import com.gamelist.service.ScreenScraperApiService;

@Component
public class ScraperSystemInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ScraperSystemInitializer.class);

    public static final int STATUS_NOT_INIT = 0;
    public static final int STATUS_INITIALIZING = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_FAILED = 3;

    private static final AtomicBoolean initializing = new AtomicBoolean(false);
    private static final AtomicInteger initStatus = new AtomicInteger(STATUS_NOT_INIT);
    private static final AtomicInteger systemsCount = new AtomicInteger(-1);
    private static String lastError = "";

    @Autowired
    private ScraperSystemService scraperSystemService;

    @Autowired
    private ScreenScraperApiService screenScraperApiService;

    @Autowired
    private ScraperSettingsService scraperSettingsService;

    @Override
    public void run(ApplicationArguments args) {
        logger.info("=== Starting SCRAPER_SYSTEM table initialization check ===");

        try {
            if (!scraperSystemService.getAll().isEmpty()) {
                systemsCount.set(scraperSystemService.getAll().size());
                initStatus.set(STATUS_COMPLETED);
                logger.info("SCRAPER_SYSTEM table already has data, skipping init. Current system count: {}", systemsCount.get());
                return;
            }

            logger.info("SCRAPER_SYSTEM table is empty, loading from baseline JSON...");
            initStatus.set(STATUS_INITIALIZING);
            initializing.set(true);

            new Thread(() -> {
                try {
                    // 优先从内置基础数据 JSON 加载（无需 API 调用）
                    List<ScraperSystem> baselineSystems = screenScraperApiService.loadBaselineSystems();
                    
                    if (!baselineSystems.isEmpty()) {
                        int saved = scraperSystemService.batchInsert(baselineSystems);
                        systemsCount.set(saved);
                        initializing.set(false);
                        initStatus.set(STATUS_COMPLETED);
                        logger.info("Loaded {} systems from baseline JSON (no API call needed)", saved);
                    } else {
                        // 基础数据为空，回退到 API 拉取
                        logger.warn("Baseline JSON is empty, falling back to API fetch...");
                        doFetchSystems();
                    }
                } catch (Exception e) {
                    logger.error("Baseline load failed, trying API fetch", e);
                    try {
                        doFetchSystems();
                    } catch (Exception e2) {
                        logger.error("API fetch also failed", e2);
                        initializing.set(false);
                        initStatus.set(STATUS_FAILED);
                        lastError = e2.getMessage();
                    }
                }
            }, "SystemInit-Thread").start();

        } catch (Exception e) {
            logger.error("Initialization check failed", e);
            initStatus.set(STATUS_FAILED);
            lastError = e.getMessage();
        }
    }

    private void doFetchSystems() {
        try {
            Map<String, String> settings = scraperSettingsService.getSettings();
            String username = settings.getOrDefault("username", "");
            String password = settings.getOrDefault("password", "");

            logger.info("Fetching systems with username: {}", username.isEmpty() ? "(empty - guest mode)" : username);

            int count = screenScraperApiService.fetchAndSaveSystems(username, password, scraperSystemService);

            systemsCount.set(count);
            initializing.set(false);
            initStatus.set(STATUS_COMPLETED);
            logger.info("System list fetch completed, total {} systems", count);

        } catch (Exception e) {
            logger.error("Failed to fetch systems", e);
            initializing.set(false);
            initStatus.set(STATUS_FAILED);
            lastError = e.getMessage();
        }
    }

    public static boolean isInitializing() {
        return initializing.get();
    }

    public static int getInitStatus() {
        return initStatus.get();
    }

    public static int getSystemsCount() {
        return systemsCount.get();
    }

    public static String getLastError() {
        return lastError;
    }

    public static boolean isReady() {
        return initStatus.get() == STATUS_COMPLETED;
    }
}