package com.gamelist.service.impl;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelist.service.TranslationException;
import com.gamelist.service.TranslationService;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GenericTranslationService implements TranslationService {
    private final OkHttpClient client;
    private final JsonNode serviceConfig;
    private final Map<String, String> variables;
    private final ObjectMapper objectMapper;

    public GenericTranslationService(JsonNode serviceConfig, Map<String, String> variables) {
        this.serviceConfig = serviceConfig;
        this.variables = variables;
        this.objectMapper = new ObjectMapper();

        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) throws TranslationException {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        try {
            Request request = buildRequest(text, sourceLang, targetLang);

            System.out.println("Translation request: Service=" + serviceConfig.get("id").asText() + ", Text=" + text + ", SourceLang=" + sourceLang + ", TargetLang=" + targetLang);
            System.out.println("Request URL: " + request.url());
            System.out.println("Request Method: " + request.method());
            if (request.body() != null) {
                okio.Buffer buffer = new okio.Buffer();
                request.body().writeTo(buffer);
                String requestBody = buffer.readUtf8();
                System.out.println("Request Body: " + requestBody);
            }

            Response response = client.newCall(request).execute();

            System.out.println("Translation response: Status=" + response.code() + ", Message=" + response.message());

            String responseBody = response.body() != null ? response.body().string() : "No response body";
            System.out.println("Response Body: " + responseBody);

            if (!response.isSuccessful()) {
                throw new TranslationException("API request failed: " + response.code() + " " + response.message() + ". Response: " + responseBody);
            }

            return extractResult(responseBody);

        } catch (IOException e) {
            System.err.println("Network error during translation: " + e.getMessage());
            e.printStackTrace();
            throw new TranslationException("Network error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Translation error: " + e.getMessage());
            e.printStackTrace();
            throw new TranslationException("Translation error: " + e.getMessage());
        }
    }

    private Request buildRequest(String text, String sourceLang, String targetLang) throws Exception {
        String url = replaceVariables(serviceConfig.get("url").asText());
        String method = serviceConfig.get("method").asText().toUpperCase();

        Request.Builder requestBuilder = new Request.Builder();

        if (serviceConfig.has("headers")) {
            JsonNode headersNode = serviceConfig.get("headers");
            headersNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                String value = replaceVariablesAndParams(entry.getValue().asText(), text, sourceLang, targetLang);
                requestBuilder.header(key, value);
            });
        }

        System.out.println("Service config has params: " + serviceConfig.has("params"));
        if (serviceConfig.has("params")) {
            System.out.println("Params node: " + serviceConfig.get("params"));
            url = buildUrlWithParams(url, serviceConfig.get("params"), text, sourceLang, targetLang);
            System.out.println("URL after adding params: " + url);
        }

        if (method.equals("POST")) {
            if (serviceConfig.has("requestBody")) {
                String requestBody = buildRequestBody(serviceConfig.get("requestBody"), text, sourceLang, targetLang);
                MediaType mediaType = MediaType.parse("application/json");
                RequestBody body = RequestBody.create(requestBody, mediaType);
                requestBuilder.post(body);
            }
        }

        requestBuilder.url(url);

        return requestBuilder.build();
    }

    private String buildRequestBody(JsonNode requestBodyNode, String text, String sourceLang, String targetLang) throws Exception {
        if (requestBodyNode.isObject()) {
            Map<String, Object> requestBodyMap = new HashMap<>();
            requestBodyNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();
                requestBodyMap.put(key, processValue(valueNode, text, sourceLang, targetLang));
            });
            return objectMapper.writeValueAsString(requestBodyMap);
        } else if (requestBodyNode.isArray()) {
            return objectMapper.writeValueAsString(processArray(requestBodyNode, text, sourceLang, targetLang));
        } else {
            return replaceVariablesAndParams(requestBodyNode.asText(), text, sourceLang, targetLang);
        }
    }

    private String buildUrlWithParams(String baseUrl, JsonNode paramsNode, String text, String sourceLang, String targetLang) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        final boolean[] firstParam = { !baseUrl.contains("?") };

        paramsNode.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            String value = replaceVariablesAndParams(entry.getValue().asText(), text, sourceLang, targetLang);
            if (firstParam[0]) {
                urlBuilder.append("?");
                firstParam[0] = false;
            } else {
                urlBuilder.append("&");
            }
            urlBuilder.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
            urlBuilder.append("=");
            urlBuilder.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });

        return urlBuilder.toString();
    }

    private Object processValue(JsonNode valueNode, String text, String sourceLang, String targetLang) {
        if (valueNode.isObject()) {
            Map<String, Object> map = new HashMap<>();
            valueNode.fields().forEachRemaining(entry -> {
                map.put(entry.getKey(), processValue(entry.getValue(), text, sourceLang, targetLang));
            });
            return map;
        } else if (valueNode.isArray()) {
            return processArray(valueNode, text, sourceLang, targetLang);
        } else if (valueNode.isBoolean()) {
            return valueNode.asBoolean();
        } else if (valueNode.isNumber()) {
            return valueNode.asDouble();
        } else {
            return replaceVariablesAndParams(valueNode.asText(), text, sourceLang, targetLang);
        }
    }

    private Object[] processArray(JsonNode arrayNode, String text, String sourceLang, String targetLang) {
        Object[] result = new Object[arrayNode.size()];
        for (int i = 0; i < arrayNode.size(); i++) {
            result[i] = processValue(arrayNode.get(i), text, sourceLang, targetLang);
        }
        return result;
    }

    private String replaceVariablesAndParams(String input, String text, String sourceLang, String targetLang) {
        input = replaceVariables(input);
        input = input.replace("{text}", text);
        input = input.replace("{sourceLang}", sourceLang);
        input = input.replace("{targetLang}", targetLang);
        return input;
    }

    private String replaceVariables(String input) {
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            input = input.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return input;
    }

    private String extractResult(String responseBody) throws Exception {
        if (serviceConfig.has("responsePath")) {
            String responsePath = serviceConfig.get("responsePath").asText();
            return extractValueFromPath(responseBody, responsePath);
        }
        return responseBody;
    }

    private String extractValueFromPath(String json, String path) throws Exception {
        JsonNode rootNode = objectMapper.readTree(json);
        String[] pathSegments = path.split("\\.");

        JsonNode currentNode = rootNode;
        for (String segment : pathSegments) {
            if (segment.startsWith("[")) {
                int index = Integer.parseInt(segment.substring(1, segment.indexOf("]")));
                currentNode = currentNode.get(index);
            } else if (segment.contains("[")) {
                String arrayName = segment.substring(0, segment.indexOf("["));
                int index = Integer.parseInt(segment.substring(segment.indexOf("[") + 1, segment.indexOf("]")));
                currentNode = currentNode.get(arrayName).get(index);
            } else {
                currentNode = currentNode.get(segment);
            }

            if (currentNode == null) {
                throw new Exception("Path not found: " + path);
            }
        }

        return currentNode.asText();
    }
}
