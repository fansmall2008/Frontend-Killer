package com.gamelist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "screenscraper")
public class ScreenScraperConfig {
    
    private String devPseudo = "fansmall";
    private String devPassword = "5YyX6PiHn7S";
    private String debugPassword = "LKhOfSgNBrm";
    private String baseUrl = "https://www.screenscraper.fr";
    
    public String getDevPseudo() {
        return devPseudo;
    }
    
    public void setDevPseudo(String devPseudo) {
        this.devPseudo = devPseudo;
    }
    
    public String getDevPassword() {
        return devPassword;
    }
    
    public void setDevPassword(String devPassword) {
        this.devPassword = devPassword;
    }
    
    public String getDebugPassword() {
        return debugPassword;
    }
    
    public void setDebugPassword(String debugPassword) {
        this.debugPassword = debugPassword;
    }
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}