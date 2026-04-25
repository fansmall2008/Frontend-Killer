package com.gamelist.service.impl;

import com.gamelist.service.TranslationException;
import com.gamelist.service.TranslationService;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class BaiduTranslationServiceImpl implements TranslationService {
    private final String appId;
    private final String appKey;
    private final String apiUrl = "https://fanyi-api.baidu.com/api/trans/vip/translate";
    private final OkHttpClient client = new OkHttpClient();
    private final Random random = new Random();
    
    public BaiduTranslationServiceImpl(String appId, String appKey) {
        this.appId = appId;
        this.appKey = appKey;
    }
    
    @Override
    public String translate(String text, String sourceLang, String targetLang) throws TranslationException {
        try {
            // 验证参数
            if (appId == null || appId.trim().isEmpty()) {
                throw new TranslationException("Baidu appId is empty");
            }
            if (appKey == null || appKey.trim().isEmpty()) {
                throw new TranslationException("Baidu appKey is empty");
            }
            
            // 生成随机数
            String salt = String.valueOf(random.nextInt(1000000));
            
            // 生成签名
            String sign = generateSign(appId, text, salt, appKey);
            
            // 构建请求URL
            String url = apiUrl + "?q=" + text + "&from=" + sourceLang + "&to=" + targetLang + "&appid=" + appId + "&salt=" + salt + "&sign=" + sign;
            
            // 输出请求信息
            System.out.println("Baidu Translation Request:");
            System.out.println("URL: " + apiUrl);
            System.out.println("AppId: " + appId);
            System.out.println("Source Lang: " + sourceLang);
            System.out.println("Target Lang: " + targetLang);
            System.out.println("Text length: " + (text != null ? text.length() : 0));
            System.out.println("Salt: " + salt);
            System.out.println("Sign: " + sign);
            System.out.println("Full URL: " + url);
            
            // 构建请求
            Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
            
            // 发送请求
            Response response = client.newCall(request).execute();
            
            // 解析响应
            if (!response.isSuccessful()) {
                throw new TranslationException("Baidu API request failed: " + response.code());
            }
            
            JSONObject responseBody = new JSONObject(response.body().string());
            
            // 检查错误
            if (responseBody.has("error_code")) {
                throw new TranslationException("Baidu API error: " + responseBody.getString("error_msg"));
            }
            
            JSONArray transResult = responseBody.getJSONArray("trans_result");
            JSONObject result = transResult.getJSONObject(0);
            
            return result.getString("dst");
        } catch (IOException e) {
            throw new TranslationException("Network error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new TranslationException("Translation error: " + e.getMessage(), e);
        }
    }
    
    private String generateSign(String appId, String text, String salt, String appKey) throws NoSuchAlgorithmException {
        String str = appId + text + salt + appKey;
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] bytes = md.digest(str.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
