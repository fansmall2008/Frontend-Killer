package com.gamelist.service.impl;

import com.gamelist.service.TranslationService;

public class TranslationServiceFactory {
    public static TranslationService createTranslationService(String serviceType, String... params) {
        switch (serviceType) {
            case "google":
                if (params.length < 1) {
                    throw new IllegalArgumentException("Google translation service requires API key");
                }
                return new GoogleTranslationServiceImpl(params[0]);
            
            case "baidu":
                if (params.length < 2) {
                    throw new IllegalArgumentException("Baidu translation service requires appId and appKey");
                }
                return new BaiduTranslationServiceImpl(params[0], params[1]);
            
            case "youdao":
                if (params.length < 2) {
                    throw new IllegalArgumentException("Youdao translation service requires appKey and appSecret");
                }
                return new YoudaoTranslationServiceImpl(params[0], params[1]);
            
            case "deepseek":
                if (params.length < 1) {
                    throw new IllegalArgumentException("DeepSeek translation service requires API key");
                }
                return new DeepSeekTranslationServiceImpl(params[0]);
            
            default:
                throw new IllegalArgumentException("Unknown translation service type: " + serviceType);
        }
    }
}
