package com.gamelist.service;

public interface TranslationService {
    String translate(String text, String sourceLang, String targetLang) throws TranslationException;
    
    // 同时翻译游戏名称和描述
    default TranslationResult translateGame(String gameName, String gameDescription, String sourceLang, String targetLang) throws TranslationException {
        // 默认实现：分别调用translate方法
        String translatedName = translate(gameName, sourceLang, targetLang);
        String translatedDesc = translate(gameDescription, sourceLang, targetLang);
        return new TranslationResult(translatedName, translatedDesc);
    }
    
    // 翻译结果类
    class TranslationResult {
        private final String gameName;
        private final String gameDescription;
        
        public TranslationResult(String gameName, String gameDescription) {
            this.gameName = gameName;
            this.gameDescription = gameDescription;
        }
        
        public String getGameName() {
            return gameName;
        }
        
        public String getGameDescription() {
            return gameDescription;
        }
    }
}
