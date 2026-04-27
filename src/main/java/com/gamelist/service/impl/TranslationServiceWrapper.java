package com.gamelist.service.impl;

import com.gamelist.service.TranslationException;
import com.gamelist.service.TranslationService;

public class TranslationServiceWrapper implements TranslationService {
    private final TranslationService delegate;
    
    public TranslationServiceWrapper(TranslationService delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public String translate(String text, String sourceLang, String targetLang) throws TranslationException {
        try {
            String translation = delegate.translate(text, sourceLang, targetLang);
            
            // 处理翻译服务返回的错误信息或非简洁翻译结果
            if (translation != null && isInvalidTranslation(translation)) {
                // 返回原始文本，避免存储错误信息
                return text;
            }
            
            return translation;
        } catch (Exception e) {
            // 如果翻译失败，返回原始文本
            return text;
        }
    }
    
    private boolean isInvalidTranslation(String translation) {
        // 检查是否包含错误信息
        if (translation.contains("Sorry, I cannot provide") ||
            translation.contains("无法提供") ||
            translation.contains("cannot translate") ||
            translation.contains("error") ||
            translation.contains("Error")) {
            return true;
        }

        // 检查是否包含换行符（说明是段落式输出，不是简洁的游戏名称）
        if (translation.contains("\n") || translation.contains("\r")) {
            return true;
        }

        // 检查是否包含选项列表标记（如 "- " 或 "• "）
        if (translation.contains("- ") || translation.contains("• ") || translation.contains("* ")) {
            return true;
        }

        // 检查是否包含说明性文字（说明不是简洁的翻译结果）
        if (translation.contains("以下是") ||
            translation.contains("根据具体") ||
            translation.contains("可能还有") ||
            translation.contains("例如") ||
            translation.contains("建议") ||
            translation.contains("翻译为") ||
            translation.contains("或") ||
            translation.contains("（或") ||
            translation.contains("()")) {
            return true;
        }

        // 检查长度是否超过限制
        if (translation.length() > 255) {
            return true;
        }

        return false;
    }
}