package com.gamelist.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 异步处理配置
 * 启用Spring的异步处理功能
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // 启用异步处理
}