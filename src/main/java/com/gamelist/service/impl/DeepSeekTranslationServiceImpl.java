package com.gamelist.service.impl;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gamelist.service.TranslationException;
import com.gamelist.service.TranslationService;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DeepSeekTranslationServiceImpl implements TranslationService {
    private final String apiKey;
    private final String apiUrl = "https://api.deepseek.com/chat/completions";
    private final OkHttpClient client = new OkHttpClient();

    public DeepSeekTranslationServiceImpl(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) throws TranslationException {
        try {
            // 验证API密钥
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new TranslationException("DeepSeek API key is empty");
            }

            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "deepseek-chat");

            // 构建消息数组
            JSONArray messages = new JSONArray();

            // 系统消息 - 明确要求只输出简洁的游戏名称翻译
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "你是一个专业的游戏名称翻译助手。只输出翻译后的游戏名称，不要添加任何解释、选项、标点符号、书名号或其他额外信息。游戏名称翻译应该简洁、流畅，符合目标语言的表达习惯。如果无法翻译或不确定，请直接返回原始文本。");
            messages.put(systemMessage);

            // 用户消息
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", text);
            messages.put(userMessage);

            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.1);
            requestBody.put("max_tokens", 50);

            // 构建请求
            Request request = new Request.Builder()
                .url(apiUrl)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey.trim())
                .post(RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json; charset=utf-8")
                ))
                .build();

            // 发送请求
            Response response = client.newCall(request).execute();

            // 解析响应
            if (!response.isSuccessful()) {
                // 读取错误响应内容
                String errorBody = response.body() != null ? response.body().string() : "No response body";
                throw new TranslationException("DeepSeek API request failed: " + response.code() + " " + response.message() + ". Response: " + errorBody);
            }

            JSONObject responseBody = new JSONObject(response.body().string());

            // 检查错误
            if (responseBody.has("error")) {
                throw new TranslationException("DeepSeek API error: " + responseBody.getJSONObject("error").getString("message"));
            }

            // 获取翻译结果
            JSONObject choice = responseBody.getJSONArray("choices").getJSONObject(0);
            return choice.getJSONObject("message").getString("content").trim();
        } catch (IOException e) {
            throw new TranslationException("Network error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new TranslationException("Translation error: " + e.getMessage(), e);
        }
    }

    private String getLanguageName(String langCode) {
        switch (langCode) {
            case "en":
                return "英语";
            case "zh":
            case "zh-CN":
                return "中文";
            case "ja":
                return "日语";
            case "ko":
                return "韩语";
            case "fr":
                return "法语";
            case "de":
                return "德语";
            case "es":
                return "西班牙语";
            case "ru":
                return "俄语";
            default:
                return langCode;
        }
    }
}