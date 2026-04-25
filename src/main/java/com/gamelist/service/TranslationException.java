package com.gamelist.service;

public class TranslationException extends Exception {
    public TranslationException(String message) {
        super(message);
    }
    
    public TranslationException(String message, Throwable cause) {
        super(message, cause);
    }
}
