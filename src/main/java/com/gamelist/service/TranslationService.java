package com.gamelist.service;

public interface TranslationService {
    String translate(String text, String sourceLang, String targetLang) throws TranslationException;
}
