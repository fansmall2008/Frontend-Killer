package com.gamelist.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 页面与静态资源禁用浏览器缓存：JAR 更新后浏览器自动加载最新版本，避免旧页面残留
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                    ModelAndView modelAndView) {
                response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
            }
        }).addPathPatterns("/**")
          .excludePathPatterns("/api/**", "/scraper/**", "/data/scraper/**", "/media/**", "/h2-console/**", "/logs/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String dataPath = com.gamelist.util.PathUtil.getDataPath();
        registry.addResourceHandler("/scraper/**")
                .addResourceLocations("file:" + dataPath + "/scraper/", "classpath:/static/", "file:./", "file:./media/", "file:./data")
                .setCacheControl(CacheControl.noCache());
        
        registry.addResourceHandler("/data/scraper/**")
                .addResourceLocations("file:" + dataPath + "/scraper/")
                .setCacheControl(CacheControl.noCache());
    }
}