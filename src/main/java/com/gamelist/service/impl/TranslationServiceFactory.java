package com.gamelist.service.impl;

import com.gamelist.service.TranslationService;

public class TranslationServiceFactory {
    public static TranslationService createTranslationService(String serviceType, String... params) {
        TranslationService service;
        
        switch (serviceType) {
            case "google":
                if (params.length < 1) {
                    throw new IllegalArgumentException("Google translation service requires API key");
                }
                service = new GoogleTranslationServiceImpl(params[0]);
                break;
            
            case "baidu":
                if (params.length < 2) {
                    throw new IllegalArgumentException("Baidu translation service requires appId and appKey");
                }
                service = new BaiduTranslationServiceImpl(params[0], params[1]);
                break;
            
            case "youdao":
                if (params.length < 2) {
                    throw new IllegalArgumentException("Youdao translation service requires appKey and appSecret");
                }
                service = new YoudaoTranslationServiceImpl(params[0], params[1]);
                break;
            
            case "deepseek":
                if (params.length < 1) {
                    throw new IllegalArgumentException("DeepSeek translation service requires API key");
                }
                service = new DeepSeekTranslationServiceImpl(params[0]);
                break;
            
            default:
                throw new IllegalArgumentException("Unknown translation service type: " + serviceType);
        }
        
        // 返回包装后的服务，添加通用错误处理
        return new TranslationServiceWrapper(service);
    }
}
