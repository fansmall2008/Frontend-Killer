package com.gamelist.config;

import java.awt.Desktop;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动完成后自动打开默认浏览器访问首页。
 * 可通过 app.auto-open-browser=false 或命令行参数 --auto-open-browser=false 关闭。
 */
@Component
public class BrowserAutoLauncher {

    private static final Logger logger = LoggerFactory.getLogger(BrowserAutoLauncher.class);

    @Value("${server.port:8080}")
    private int port;

    @Value("${app.auto-open-browser:true}")
    private boolean autoOpenBrowser;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!autoOpenBrowser) {
            logger.info("自动打开浏览器已禁用 (app.auto-open-browser=false)");
            return;
        }

        // 延迟 1 秒打开浏览器，确保 Web 服务器完全就绪
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                String url = "http://localhost:" + port;
                logger.info("正在打开浏览器: {}", url);

                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                    logger.info("浏览器已打开");
                } else {
                    // Windows 回退方案
                    String os = System.getProperty("os.name", "").toLowerCase();
                    if (os.contains("win")) {
                        Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
                        logger.info("浏览器已打开 (cmd fallback)");
                    } else if (os.contains("mac")) {
                        Runtime.getRuntime().exec(new String[]{"open", url});
                        logger.info("浏览器已打开 (open fallback)");
                    } else {
                        Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                        logger.info("浏览器已打开 (xdg-open fallback)");
                    }
                }
            } catch (Exception e) {
                logger.warn("自动打开浏览器失败: {}", e.getMessage());
            }
        }, "browser-auto-launcher").start();
    }
}
