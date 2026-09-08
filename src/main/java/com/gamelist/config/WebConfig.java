package com.gamelist.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/scraper/**")
                .addResourceLocations("file:/data/scraper/", "classpath:/static/", "file:./", "file:./media/", "file:./data")
                .setCacheControl(CacheControl.noCache());
        
        registry.addResourceHandler("/data/scraper/**")
                .addResourceLocations("file:/data/scraper/")
                .setCacheControl(CacheControl.noCache());
    }
}