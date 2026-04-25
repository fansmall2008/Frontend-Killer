package com.gamelist.service.impl;

import com.gamelist.service.TranslationException;
import com.gamelist.service.TranslationService;
import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class YoudaoTranslationServiceImpl implements TranslationService {
    private final String appKey;
    private final String appSecret;
    private final String apiUrl = "https://openapi.youdao.com/api";
    private final OkHttpClient client = new OkHttpClient();
    private final Random random = new Random();
    
    public YoudaoTranslationServiceImpl(String appKey, String appSecret) {
        this.appKey = appKey;
        this.appSecret = appSecret;
    }
    
    @Override
    public String translate(String text, String sourceLang, String targetLang) throws TranslationException {
        try {
            // 生成随机数
            String salt = String.valueOf(random.nextInt(1000000));
            
            // 生成签名
            String sign = generateSign(appKey, text, salt, appSecret);
            
            // 构建请求体
            FormBody formBody = new FormBody.Builder()
                .add("q", text)
                .add("from", sourceLang)
                .add("to", targetLang)
                .add("appKey", appKey)
                .add("salt", salt)
                .add("sign", sign)
                .add("signType", "v3")
                .build();
            
            // 构建请求
            Request request = new Request.Builder()
                .url(apiUrl)
                .post(formBody)
                .build();
            
            // 发送请求
            Response response = client.newCall(request).execute();
            
            // 解析响应
            if (!response.isSuccessful()) {
                throw new TranslationException("Youdao API request failed: " + response.code());
            }
            
            JSONObject responseBody = new JSONObject(response.body().string());
            
            // 检查错误
            String errorCode = responseBody.getString("errorCode");
            if (!errorCode.equals("0")) {
                throw new TranslationException("Youdao API error: " + errorCode);
            }
            
            // 获取翻译结果
            return responseBody.getJSONArray("translation").getString(0);
        } catch (IOException e) {
            throw new TranslationException("Network error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new TranslationException("Translation error: " + e.getMessage(), e);
        }
    }
    
    private String generateSign(String appKey, String text, String salt, String appSecret) throws NoSuchAlgorithmException {
        String str = appKey + truncateText(text) + salt + System.currentTimeMillis() + appSecret;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] bytes = md.digest(str.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    private String truncateText(String text) {
        if (text.length() <= 20) {
            return text;
        }
        return text.substring(0, 10) + text.length() + text.substring(text.length() - 10);
    }
}
