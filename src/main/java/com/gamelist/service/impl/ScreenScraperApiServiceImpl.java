package com.gamelist.service.impl;

import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelist.config.ScreenScraperConfig;
import com.gamelist.model.ScraperSystem;
import com.gamelist.service.ScreenScraperApiService;
import com.gamelist.util.ScreenScraperStatusHandler;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.TlsVersion;

@Service
public class ScreenScraperApiServiceImpl implements ScreenScraperApiService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScreenScraperApiServiceImpl.class);
    
    private final ScreenScraperConfig scraperConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public ScreenScraperApiServiceImpl(ScreenScraperConfig scraperConfig) {
        this.scraperConfig = scraperConfig;
        this.httpClient = createOkHttpClient();
        this.objectMapper = new ObjectMapper();
    }
    
    private OkHttpClient createOkHttpClient() {
        System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2,TLSv1.3");
        System.setProperty("jdk.tls.client.protocols", "TLSv1,TLSv1.1,TLSv1.2,TLSv1.3");
        System.setProperty("jdk.tls.ephemeralDHKeySize", "2048");
        
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, new SecureRandom());
            
            logger.info("SSL Context initialized with protocol: {}", sslContext.getProtocol());
            
            ConnectionSpec modernSpec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                    .allEnabledCipherSuites()
                    .build();
            
            ConnectionSpec compatibleSpec = new ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
                    .allEnabledCipherSuites()
                    .build();
            
            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    })
                    .hostnameVerifier((hostname, session) -> true)
                    .connectionSpecs(java.util.Arrays.asList(modernSpec, compatibleSpec, ConnectionSpec.CLEARTEXT))
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .retryOnConnectionFailure(true)
                    .connectionPool(new okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
                    .eventListener(new okhttp3.EventListener() {
                        @Override
                        public void secureConnectStart(okhttp3.Call call) {
                            logger.debug("SSL handshake starting...");
                        }
                        @Override
                        public void secureConnectEnd(okhttp3.Call call, okhttp3.Handshake handshake) {
                            logger.info("SSL handshake completed with TLS version: {}", handshake != null ? handshake.tlsVersion() : "N/A");
                        }
                    })
                    .build();
        } catch (Exception e) {
            logger.warn("Failed to create custom SSL context: {}, using default", e.getMessage());
            return new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
        }
    }
    
    @Override
    public List<ScraperSystem> fetchSystems(String username, String password) {
        List<ScraperSystem> systems = new ArrayList<>();
        int page = 0;
        boolean hasMorePages = true;
        
        try {
            while (hasMorePages) {
                String url = buildSystemsListUrl(username, password, page);
                logger.info("Fetching systems from ScreenScraper - page: {}", page);
                logger.info("Request URL: {}", url);
                
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "FrontendKiller/1.0")
                        .header("Accept", "application/json")
                        .get()
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    int statusCode = response.code();
                    logger.info("HTTP Status Code: {}", statusCode);
                    logger.info("Content-Type: {}", response.header("Content-Type"));
                    logger.info("TLS Version: {}", response.handshake() != null ? response.handshake().tlsVersion() : "N/A");
                    
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No body";
                        ScreenScraperStatusHandler.logStatus(statusCode, "fetchSystems");
                        Map<String, Object> errorResult = ScreenScraperStatusHandler.handleResponse(statusCode, errorBody);
                        String message = (String) errorResult.get("message");
                        String suggestion = (String) errorResult.get("suggestion");
                        logger.error("ScreenScraper API request failed: {} - {}\nError Body: {}\n建议: {}", 
                                statusCode, message, errorBody, suggestion);
                        
                        if (ScreenScraperStatusHandler.isRateLimitError(statusCode)) {
                            throw new RuntimeException("API配额限制: " + message + " - " + suggestion);
                        } else if (ScreenScraperStatusHandler.isAuthError(statusCode)) {
                            throw new RuntimeException("认证失败: " + message + " - " + suggestion);
                        } else {
                            throw new RuntimeException("ScreenScraper API request failed: " + statusCode + " - " + message);
                        }
                    }
                    
                    String responseBody = response.body().string();
                    logger.info("API Response length: {} bytes", responseBody.length());
                    
                    String contentType = response.header("Content-Type", "");
                    if (!contentType.contains("json")) {
                        logger.error("Response is not JSON. Content-Type: {}", contentType);
                        logger.error("Response (first 500 chars): {}", 
                                responseBody.length() > 500 ? responseBody.substring(0, 500) : responseBody);
                        throw new RuntimeException("ScreenScraper API returned non-JSON response");
                    }
                    
                    if (responseBody.startsWith("<")) {
                        logger.error("ERROR: ScreenScraper returned HTML instead of JSON!");
                        logger.error("Response (first 1000 chars): {}", 
                                responseBody.length() > 1000 ? responseBody.substring(0, 1000) : responseBody);
                        throw new RuntimeException("ScreenScraper API returned HTML instead of JSON");
                    }
                    
                    JsonNode rootNode = objectMapper.readTree(responseBody);
                    
                    if (rootNode.has("response")) {
                        JsonNode responseNode = rootNode.get("response");
                        
                        if (responseNode.has("total")) {
                            logger.info("Total systems available: {}", responseNode.get("total").asText());
                        }
                        
                        if (responseNode.has("systemes")) {
                            JsonNode systemsNode = responseNode.get("systemes");
                            
                            if (systemsNode.isArray()) {
                                int countOnPage = systemsNode.size();
                                logger.info("Found {} systems on page {}", countOnPage, page);
                                
                                if (countOnPage > 0) {
                                    for (JsonNode systemNode : systemsNode) {
                                        ScraperSystem system = parseSystem(systemNode);
                                        if (system != null) {
                                            systems.add(system);
                                        }
                                    }
                                    page++;
                                    
                                    if (countOnPage < 20) {
                                        hasMorePages = false;
                                        logger.info("Last page reached (less than 20 systems)");
                                    }
                                } else {
                                    hasMorePages = false;
                                    logger.info("No systems found on page {}, stopping", page);
                                }
                            } else {
                                hasMorePages = false;
                                logger.warn("Systemes is not an array");
                            }
                        } else if (responseNode.has("systems")) {
                            JsonNode systemsNode = responseNode.get("systems");
                            
                            if (systemsNode.isArray()) {
                                int countOnPage = systemsNode.size();
                                logger.info("Found {} systems on page (systems field) {}", countOnPage, page);
                                
                                if (countOnPage > 0) {
                                    for (JsonNode systemNode : systemsNode) {
                                        ScraperSystem system = parseSystem(systemNode);
                                        if (system != null) {
                                            systems.add(system);
                                        }
                                    }
                                    page++;
                                    
                                    if (countOnPage < 20) {
                                        hasMorePages = false;
                                        logger.info("Last page reached (less than 20 systems)");
                                    }
                                } else {
                                    hasMorePages = false;
                                    logger.info("No systems found on page {}, stopping", page);
                                }
                            }
                        } else {
                            hasMorePages = false;
                            logger.warn("No systemes or systems field in response");
                        }
                    } else {
                        hasMorePages = false;
                        logger.warn("No response field in API response");
                    }
                }
                
                if (page >= 50) {
                    hasMorePages = false;
                    logger.warn("Reached maximum page limit (50)");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to fetch systems from ScreenScraper: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch systems from ScreenScraper: " + e.getMessage(), e);
        }
        
        logger.info("Total systems fetched: {}", systems.size());
        return systems;
    }
    
    @Override
    public boolean testConnection(String username, String password) {
        try {
            String url = buildTestUrl(username, password);
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "FrontendKiller/1.0")
                    .header("Accept", "application/json")
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return false;
                }
                
                String responseBody = response.body().string();
                String contentType = response.header("Content-Type", "");
                return contentType.contains("json") && !responseBody.startsWith("<");
            }
        } catch (Exception e) {
            logger.error("Test connection failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public int fetchAndSaveSystems(String username, String password, com.gamelist.service.ScraperSystemService systemService) {
        int totalSaved = 0;
        int page = 0;
        boolean hasMorePages = true;
        
        try {
            while (hasMorePages) {
                String url = buildSystemsListUrl(username, password, page);
                logger.info("Fetching systems from ScreenScraper - page: {}", page);
                logger.info("Request URL: {}", url);
                
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "FrontendKiller/1.0")
                        .header("Accept", "application/json")
                        .get()
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    int statusCode = response.code();
                    logger.info("HTTP Status Code: {}", statusCode);
                    
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No body";
                        ScreenScraperStatusHandler.logStatus(statusCode, "fetchAndSaveSystems");
                        Map<String, Object> errorResult = ScreenScraperStatusHandler.handleResponse(statusCode, errorBody);
                        String message = (String) errorResult.get("message");
                        String suggestion = (String) errorResult.get("suggestion");
                        logger.error("ScreenScraper API request failed: {} - {}\nError Body: {}\n建议: {}", 
                                statusCode, message, errorBody, suggestion);
                        
                        if (ScreenScraperStatusHandler.isRateLimitError(statusCode)) {
                            throw new RuntimeException("API配额限制: " + message + " - " + suggestion);
                        } else if (ScreenScraperStatusHandler.isAuthError(statusCode)) {
                            throw new RuntimeException("认证失败: " + message + " - " + suggestion);
                        } else {
                            throw new RuntimeException("ScreenScraper API request failed: " + statusCode + " - " + message);
                        }
                    }
                    
                    String responseBody = response.body().string();
                    logger.info("API Response length: {} bytes", responseBody.length());
                    
                    String contentType = response.header("Content-Type", "");
                    if (!contentType.contains("json")) {
                        logger.error("Response is not JSON. Content-Type: {}", contentType);
                        throw new RuntimeException("ScreenScraper API returned non-JSON response");
                    }
                    
                    if (responseBody.startsWith("<")) {
                        logger.error("ERROR: ScreenScraper returned HTML instead of JSON!");
                        throw new RuntimeException("ScreenScraper API returned HTML instead of JSON");
                    }
                    
                    JsonNode rootNode = objectMapper.readTree(responseBody);
                    List<ScraperSystem> pageSystems = new ArrayList<>();
                    
                    if (rootNode.has("response")) {
                        JsonNode responseNode = rootNode.get("response");
                        
                        if (responseNode.has("total")) {
                            logger.info("Total systems available: {}", responseNode.get("total").asText());
                        }
                        
                        JsonNode systemsNode = null;
                        if (responseNode.has("systemes")) {
                            systemsNode = responseNode.get("systemes");
                        } else if (responseNode.has("systems")) {
                            systemsNode = responseNode.get("systems");
                        }
                        
                        if (systemsNode != null && systemsNode.isArray()) {
                            int countOnPage = systemsNode.size();
                            logger.info("Found {} systems on page {}", countOnPage, page);
                            
                            if (countOnPage > 0) {
                                // 打印第一个系统的完整JSON结构用于调试
                                JsonNode firstSystem = systemsNode.get(0);
                                String firstSystemStr = firstSystem.toString();
                                logger.info("First system raw JSON (first 800 chars): {}", 
                                        firstSystemStr.length() > 800 ? firstSystemStr.substring(0, 800) : firstSystemStr);
                                StringBuilder fieldNames = new StringBuilder();
                                Iterator<String> fields = firstSystem.fieldNames();
                                while (fields.hasNext()) {
                                    if (fieldNames.length() > 0) fieldNames.append(", ");
                                    fieldNames.append(fields.next());
                                }
                                logger.info("First system field names: {}", fieldNames.toString());
                                
                                for (JsonNode systemNode : systemsNode) {
                                    ScraperSystem system = parseSystem(systemNode);
                                    if (system != null) {
                                        pageSystems.add(system);
                                    }
                                }
                                
                                if (!pageSystems.isEmpty()) {
                                    int saved = systemService.batchInsert(pageSystems);
                                    totalSaved += saved;
                                    logger.info("Saved {} systems from page {} to database, total: {}", 
                                            saved, page, totalSaved);
                                }
                                
                                page++;
                                hasMorePages = countOnPage >= 20;
                            } else {
                                hasMorePages = false;
                                logger.info("No systems found on page {}, stopping", page);
                            }
                        } else {
                            hasMorePages = false;
                            logger.warn("No systemes or systems field in response");
                        }
                    } else {
                        hasMorePages = false;
                        logger.warn("No response field in API response");
                    }
                }
                
                if (page >= 50) {
                    hasMorePages = false;
                    logger.warn("Reached maximum page limit (50)");
                }
            }
        } catch (javax.net.ssl.SSLHandshakeException e) {
            logger.error("SSL Handshake failed: {}", e.getMessage());
            logger.error("SSL Exception details:");
            Throwable cause = e.getCause();
            int depth = 0;
            while (cause != null && depth < 5) {
                logger.error("  Cause {}: {} - {}", depth, cause.getClass().getName(), cause.getMessage());
                cause = cause.getCause();
                depth++;
            }
            logger.error("System properties - https.protocols: {}", System.getProperty("https.protocols"));
            throw new RuntimeException("SSL Handshake failed: " + e.getMessage(), e);
        } catch (java.net.SocketTimeoutException e) {
            logger.error("Connection timeout: {}", e.getMessage());
            throw new RuntimeException("Connection timeout: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to fetch and save systems: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch and save systems: " + e.getMessage(), e);
        }
        
        logger.info("Total systems fetched and saved: {}", totalSaved);
        return totalSaved;
    }
    
    private String buildSystemsListUrl(String username, String password, int page) {
        okhttp3.HttpUrl.Builder urlBuilder = okhttp3.HttpUrl.parse(scraperConfig.getBaseUrl() + "/api2/systemesListe.php").newBuilder();
        
        urlBuilder.addQueryParameter("devid", scraperConfig.getDevPseudo());
        urlBuilder.addQueryParameter("devpassword", scraperConfig.getDevPassword());
        urlBuilder.addQueryParameter("softname", "FrontendKiller");
        urlBuilder.addQueryParameter("output", "json");
        urlBuilder.addQueryParameter("numpage", String.valueOf(page));
        
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            urlBuilder.addQueryParameter("ssid", username);
            urlBuilder.addQueryParameter("sspassword", password);
        }
        
        return urlBuilder.build().toString();
    }
    
    private String buildTestUrl(String username, String password) {
        okhttp3.HttpUrl.Builder urlBuilder = okhttp3.HttpUrl.parse(scraperConfig.getBaseUrl() + "/api2/jeuInfos.php").newBuilder();
        
        urlBuilder.addQueryParameter("devid", scraperConfig.getDevPseudo());
        urlBuilder.addQueryParameter("devpassword", scraperConfig.getDevPassword());
        urlBuilder.addQueryParameter("softname", "FrontendKiller");
        urlBuilder.addQueryParameter("output", "json");
        urlBuilder.addQueryParameter("gameid", "1");
        
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            urlBuilder.addQueryParameter("ssid", username);
            urlBuilder.addQueryParameter("sspassword", password);
        }
        
        return urlBuilder.build().toString();
    }
    
    private ScraperSystem parseSystem(JsonNode systemNode) {
        try {
            ScraperSystem system = new ScraperSystem();
            
            if (systemNode.has("id")) {
                system.setSystemId(systemNode.get("id").asInt());
            }
            
            // 处理noms对象（ScreenScraper实际返回的格式）
            if (systemNode.has("noms")) {
                JsonNode nomsNode = systemNode.get("noms");
                if (nomsNode.isObject()) {
                    // noms是一个对象，包含多个字段如 nom_eu, nom_launchbox, nom_hyperspin, noms_commun 等
                    // 先存储所有noms字段
                    if (nomsNode.has("nom_eu")) {
                        system.setNomEu(nomsNode.get("nom_eu").asText());
                    }
                    if (nomsNode.has("nom_launchbox")) {
                        system.setNomLaunchbox(nomsNode.get("nom_launchbox").asText());
                    }
                    if (nomsNode.has("nom_hyperspin")) {
                        system.setNomHyperspin(nomsNode.get("nom_hyperspin").asText());
                    }
                    if (nomsNode.has("nom_recalbox")) {
                        system.setNomRecalbox(nomsNode.get("nom_recalbox").asText());
                    }
                    if (nomsNode.has("nom_retropie")) {
                        system.setNomRetropie(nomsNode.get("nom_retropie").asText());
                    }
                    if (nomsNode.has("noms_commun")) {
                        system.setNomsCommun(nomsNode.get("noms_commun").asText());
                    }
                    
                    // 然后确定主名称
                    String name = null;
                    if (system.getNomLaunchbox() != null && !system.getNomLaunchbox().isEmpty()) {
                        name = system.getNomLaunchbox();
                    } else if (system.getNomHyperspin() != null && !system.getNomHyperspin().isEmpty()) {
                        name = system.getNomHyperspin();
                    } else if (system.getNomEu() != null && !system.getNomEu().isEmpty()) {
                        name = system.getNomEu();
                    } else if (system.getNomsCommun() != null && !system.getNomsCommun().isEmpty()) {
                        String[] parts = system.getNomsCommun().split(",");
                        if (parts.length > 0 && !parts[0].isEmpty()) {
                            name = parts[0].trim();
                        }
                    }
                    if (name != null) {
                        system.setName(name);
                    }
                } else if (nomsNode.isArray()) {
                    // 旧格式：数组形式
                    for (JsonNode nomNode : nomsNode) {
                        if (nomNode.isObject()) {
                            String lang = nomNode.has("langue") ? nomNode.get("langue").asText() : "";
                            String text = nomNode.has("text") ? nomNode.get("text").asText() : "";
                            
                            if (!text.isEmpty()) {
                                if (system.getName() == null || system.getName().isEmpty()) {
                                    system.setName(text);
                                }
                                
                                if ("en".equalsIgnoreCase(lang)) {
                                    system.setNameEn(text);
                                } else if ("fr".equalsIgnoreCase(lang)) {
                                    system.setNameFr(text);
                                } else if ("jp".equalsIgnoreCase(lang) || "ja".equalsIgnoreCase(lang)) {
                                    system.setNameJp(text);
                                } else if ("cn".equalsIgnoreCase(lang) || "zh".equalsIgnoreCase(lang)) {
                                    system.setNameCn(text);
                                }
                            }
                        } else {
                            String text = nomNode.asText();
                            if (!text.isEmpty() && (system.getName() == null || system.getName().isEmpty())) {
                                system.setName(text);
                            }
                        }
                    }
                } else if (!nomsNode.isNull()) {
                    String text = nomsNode.asText();
                    if (!text.isEmpty() && (system.getName() == null || system.getName().isEmpty())) {
                        system.setName(text);
                    }
                }
            }
            
            // 单独的nom字段
            if (systemNode.has("nom") && (system.getName() == null || system.getName().isEmpty())) {
                system.setName(systemNode.get("nom").asText());
            }
            
            // 多语言名称字段
            if (systemNode.has("nom_en") || systemNode.has("name_en")) {
                system.setNameEn(systemNode.has("nom_en") ? systemNode.get("nom_en").asText() : systemNode.get("name_en").asText());
            }
            if (systemNode.has("nom_fr") || systemNode.has("name_fr")) {
                system.setNameFr(systemNode.has("nom_fr") ? systemNode.get("nom_fr").asText() : systemNode.get("name_fr").asText());
            }
            if (systemNode.has("nom_jp") || systemNode.has("name_jp")) {
                system.setNameJp(systemNode.has("nom_jp") ? systemNode.get("nom_jp").asText() : systemNode.get("name_jp").asText());
            }
            if (systemNode.has("nom_cn") || systemNode.has("name_cn")) {
                system.setNameCn(systemNode.has("nom_cn") ? systemNode.get("nom_cn").asText() : systemNode.get("name_cn").asText());
            }
            
            // 公司字段 - 使用compagnie而非constructeur
            if (systemNode.has("compagnie")) {
                system.setCompany(systemNode.get("compagnie").asText());
            } else if (systemNode.has("constructeur")) {
                system.setCompany(systemNode.get("constructeur").asText());
            } else if (systemNode.has("company")) {
                system.setCompany(systemNode.get("company").asText());
            }
            
            if (systemNode.has("type")) {
                system.setType(systemNode.get("type").asText());
            }
            
            if (systemNode.has("datedebut")) {
                try {
                    String dateStr = systemNode.get("datedebut").asText();
                    if (dateStr != null && !dateStr.isEmpty() && dateStr.length() >= 4) {
                        system.setReleaseYear(Integer.parseInt(dateStr.substring(0, 4)));
                    }
                } catch (Exception e) {
                }
            } else if (systemNode.has("releaseyear")) {
                try {
                    system.setReleaseYear(systemNode.get("releaseyear").asInt());
                } catch (Exception e) {
                }
            }
            
            // 结束年份
            if (systemNode.has("datefin")) {
                try {
                    String dateStr = systemNode.get("datefin").asText();
                    if (dateStr != null && !dateStr.isEmpty() && !"?".equals(dateStr) && dateStr.length() >= 4) {
                        system.setEndYear(Integer.parseInt(dateStr.substring(0, 4)));
                    }
                } catch (Exception e) {
                }
            }
            
            // 父系统ID
            if (systemNode.has("parentid")) {
                try {
                    system.setParentId(systemNode.get("parentid").asInt());
                } catch (Exception e) {
                }
            }
            
            // ROM类型
            if (systemNode.has("romtype")) {
                system.setRomType(systemNode.get("romtype").asText());
            }
            
            // 支持类型
            if (systemNode.has("supporttype")) {
                system.setSupportType(systemNode.get("supporttype").asText());
            }
            
            // 扩展名
            if (systemNode.has("extensions")) {
                system.setExtensions(systemNode.get("extensions").asText());
            }
            
            if (systemNode.has("icone")) {
                system.setIconUrl(systemNode.get("icone").asText());
            } else if (systemNode.has("icon")) {
                system.setIconUrl(systemNode.get("icon").asText());
            }
            
            if (system.getName() == null || system.getName().isEmpty()) {
                logger.warn("System has no name, systemId: {}", system.getSystemId());
                logger.warn("Raw node: {}", systemNode.toString().length() > 500 ? systemNode.toString().substring(0, 500) : systemNode.toString());
            }
            
            return system;
        } catch (Exception e) {
            logger.error("Failed to parse system: {}", e.getMessage());
            return null;
        }
    }
    
    @Override
    public Map<String, Object> fetchSystemMedia(Integer systemId, String region, List<String> mediaTypes, String username, String password) {
        Map<String, Object> result = new java.util.HashMap<>();
        java.util.List<Map<String, String>> mediaUrls = new java.util.ArrayList<>();

        try {
            logger.info("Building system media URLs: systemId={}, region={}, mediaTypes={}", systemId, region, mediaTypes);

            for (String mediaType : mediaTypes) {
                String mediaUrl = buildMediaSystemeUrl(systemId, region, mediaType, username, password);

                Map<String, String> mediaInfo = new java.util.HashMap<>();
                mediaInfo.put("type", mediaType);
                mediaInfo.put("region", region);
                mediaInfo.put("url", mediaUrl);
                mediaUrls.add(mediaInfo);

                logger.info("Built media URL: type={}, region={}, url={}", mediaType, region, mediaUrl);
            }

            result.put("success", true);
            result.put("mediaUrls", mediaUrls);
            result.put("count", mediaUrls.size());
            logger.info("Built {} media URLs", mediaUrls.size());
        } catch (Exception e) {
            logger.error("Failed to build system media URLs: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Failed to build system media URLs: " + e.getMessage());
        }

        return result;
    }

    private String buildMediaSystemeUrl(Integer systemId, String region, String mediaType, String username, String password) {
        okhttp3.HttpUrl.Builder urlBuilder = okhttp3.HttpUrl.parse(scraperConfig.getBaseUrl() + "/api2/mediaSysteme.php").newBuilder();

        urlBuilder.addQueryParameter("devid", scraperConfig.getDevPseudo());
        urlBuilder.addQueryParameter("devpassword", scraperConfig.getDevPassword());
        urlBuilder.addQueryParameter("softname", "FrontendKiller");
        urlBuilder.addQueryParameter("systemeid", String.valueOf(systemId));
        urlBuilder.addQueryParameter("media", mediaType + "(" + region + ")");

        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            urlBuilder.addQueryParameter("ssid", username);
            urlBuilder.addQueryParameter("sspassword", password);
        }

        return urlBuilder.build().toString();
    }
    
    @Override
    public Map<String, Object> fetchSystemDetails(Integer systemId, String username, String password) {
        Map<String, Object> result = new java.util.HashMap<>();
        List<Map<String, String>> mediaList = new java.util.ArrayList<>();
        
        try {
            logger.info("Fetching system details: systemId={}", systemId);
            
            okhttp3.HttpUrl.Builder urlBuilder = okhttp3.HttpUrl.parse(scraperConfig.getBaseUrl() + "/api2/systemesListe.php").newBuilder();
            urlBuilder.addQueryParameter("devid", scraperConfig.getDevPseudo());
            urlBuilder.addQueryParameter("devpassword", scraperConfig.getDevPassword());
            urlBuilder.addQueryParameter("softname", "FrontendKiller");
            urlBuilder.addQueryParameter("output", "json");
            
            if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                urlBuilder.addQueryParameter("ssid", username);
                urlBuilder.addQueryParameter("sspassword", password);
            }
            
            String fullUrl = urlBuilder.build().toString();
            logger.info("Calling ScreenScraper API: {}", fullUrl);
            
            Request request = new Request.Builder().url(fullUrl).build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                int statusCode = response.code();
                
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    ScreenScraperStatusHandler.logStatus(statusCode, "fetchSystemDetails");
                    Map<String, Object> errorResult = ScreenScraperStatusHandler.handleResponse(statusCode, errorBody);
                    
                    result.put("success", false);
                    result.put("statusCode", statusCode);
                    result.put("message", errorResult.get("message"));
                    result.put("errorType", errorResult.get("errorType"));
                    result.put("suggestion", errorResult.get("suggestion"));
                    result.put("statusType", errorResult.get("statusType"));
                    result.put("retryAfter", errorResult.get("retryAfter"));
                    
                    logger.error("Failed to fetch system details: HTTP {} - {}", statusCode, errorResult.get("message"));
                    return result;
                }
                
                String responseBody = response.body() != null ? response.body().string() : "";
                JsonNode rootNode = objectMapper.readTree(responseBody);
                
                JsonNode responseNode = rootNode.path("response");
                JsonNode systemesNode = responseNode.path("systemes");
                
                if (systemesNode.isMissingNode() || systemesNode.isNull() || !systemesNode.isArray()) {
                    logger.warn("No systems found in response");
                    result.put("success", false);
                    result.put("message", "No systems found");
                    return result;
                }
                
                JsonNode systemeNode = null;
                for (JsonNode node : systemesNode) {
                    int id = node.path("id").asInt(-1);
                    if (id == systemId) {
                        systemeNode = node;
                        break;
                    }
                }
                
                if (systemeNode == null) {
                    logger.warn("System not found: systemId={}", systemId);
                    result.put("success", false);
                    result.put("message", "System not found");
                    return result;
                }
                
                JsonNode mediasNode = systemeNode.path("medias");
                if (!mediasNode.isMissingNode() && mediasNode.isArray()) {
                    for (JsonNode mediaNode : mediasNode) {
                        String url = mediaNode.path("url").asText();
                        // 跳过无效的媒体（没有URL或URL为空）
                        if (url == null || url.isEmpty()) {
                            continue;
                        }
                        
                        Map<String, String> mediaInfo = new java.util.HashMap<>();
                        mediaInfo.put("type", mediaNode.path("type").asText());
                        // region字段可能缺失，处理为空字符串的情况
                        JsonNode regionNode = mediaNode.path("region");
                        mediaInfo.put("region", regionNode.isMissingNode() ? "" : regionNode.asText());
                        mediaInfo.put("url", url);
                        mediaInfo.put("format", mediaNode.path("format").asText());
                        mediaList.add(mediaInfo);
                    }
                    logger.info("Found {} valid media files for system {}", mediaList.size(), systemId);
                } else {
                    logger.info("No media files found for system {}", systemId);
                }
                
                result.put("success", true);
                result.put("mediaList", mediaList);
                result.put("count", mediaList.size());
            }
        } catch (Exception e) {
            logger.error("Failed to fetch system details: systemId={}, error={}", systemId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Failed to fetch system details: " + e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public List<ScraperSystem> loadBaselineSystems() {
        List<ScraperSystem> systems = new ArrayList<>();
        
        try (InputStream is = getClass().getResourceAsStream("/data/system-baseline.json")) {
            if (is == null) {
                logger.warn("Baseline system file not found: /data/system-baseline.json");
                return systems;
            }
            
            JsonNode rootNode = objectMapper.readTree(is);
            JsonNode responseNode = rootNode.path("response");
            JsonNode systemsNode = responseNode.path("systemes");
            
            if (systemsNode.isMissingNode() || !systemsNode.isArray()) {
                systemsNode = responseNode.path("systems");
            }
            
            if (systemsNode.isArray()) {
                for (JsonNode systemNode : systemsNode) {
                    ScraperSystem system = parseSystem(systemNode);
                    if (system != null) {
                        systems.add(system);
                    }
                }
            }
            
            logger.info("Loaded {} systems from baseline JSON", systems.size());
        } catch (Exception e) {
            logger.error("Failed to load baseline systems: {}", e.getMessage(), e);
        }
        
        return systems;
    }
}