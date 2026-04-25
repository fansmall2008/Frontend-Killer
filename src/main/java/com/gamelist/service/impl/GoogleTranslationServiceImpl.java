package com.gamelist.service.impl;

import com.gamelist.service.TranslationException;
import com.gamelist.service.TranslationService;
import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;

public class GoogleTranslationServiceImpl implements TranslationService {
    private final String apiKey;
    private final String apiUrl = "https://translation.googleapis.com/language/translate/v2";
    private final OkHttpClient client = new OkHttpClient();
    
    public GoogleTranslationServiceImpl(String apiKey) {
        this.apiKey = apiKey;
    }
    
    @Override
    public String translate(String text, String sourceLang, String targetLang) throws TranslationException {
        try {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("q", text);
            requestBody.put("target", targetLang);
            if (sourceLang != null && !sourceLang.equals("auto")) {
                requestBody.put("source", sourceLang);
            }
            
            // 构建请求
            Request request = new Request.Builder()
                .url(apiUrl + "?key=" + apiKey)
                .post(RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json; charset=utf-8")
                ))
                .build();
            
            // 发送请求
            Response response = client.newCall(request).execute();
            
            // 解析响应
            if (!response.isSuccessful()) {
                throw new TranslationException("Google API request failed: " + response.code());
            }
            
            JSONObject responseBody = new JSONObject(response.body().string());
            JSONObject data = responseBody.getJSONObject("data");
            JSONObject translation = data.getJSONArray("translations").getJSONObject(0);
            
            return translation.getString("translatedText");
        } catch (IOException e) {
            throw new TranslationException("Network error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new TranslationException("Translation error: " + e.getMessage(), e);
        }
    }
}
