package com.gamelist.service;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelist.config.ScreenScraperConfig;
import com.gamelist.mapper.GameManifestMapper;
import com.gamelist.model.GameManifest;
import com.gamelist.util.ScreenScraperApiException;
import com.gamelist.util.ScreenScraperStatusHandler;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 游戏信息缓存（SS manifest）读写层。
 *
 * 职责：
 *  - cache-first：先查本地 game_manifest，命中且可解析则不走接口；
 *  - 受控取数：miss 时经 {@link ThreadResourceManager} 申请"游戏信息"线程再调 jeuInfos.php，
 *    与刮削/下载共享并发与配额；拿不到线程（超时/额度为 0）则返回未命中，交由上层降级。
 *
 * 安全：manifest 为内部不透明存储，仅供本服务与导出找齐读取，不经任何接口列举、不进导出包。
 */
@Service
public class GameManifestService {

    private static final Logger logger = LoggerFactory.getLogger(GameManifestService.class);

    @Autowired
    private GameManifestMapper gameManifestMapper;

    @Autowired
    private ThreadResourceManager threadResourceManager;

    @Autowired
    private ScreenScraperConfig scraperConfig;

    @Autowired
    private ScraperSettingsService scraperSettingsService;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 是否已缓存该 SS 游戏的 manifest（仅判断 DB 行存在，不解析）。
     */
    public boolean isCached(Long ssGameId) {
        if (ssGameId == null) {
            return false;
        }
        return gameManifestMapper.selectBySsGameId(ssGameId) != null;
    }

    /**
     * cache-first 读取：命中且能解析为合法 jeu 节点则返回，否则返回 null（不触发网络）。
     */
    public JsonNode getCachedGame(Long ssGameId) {
        if (ssGameId == null) {
            return null;
        }
        GameManifest row = gameManifestMapper.selectBySsGameId(ssGameId);
        if (row == null || row.getManifest() == null || row.getManifest().isEmpty()) {
            return null;
        }
        try {
            JsonNode jeu = objectMapper.readTree(row.getManifest());
            // 校验：必须是对象且带 id，否则视为畸形（当未命中）
            if (jeu.isObject() && jeu.has("id")) {
                return jeu;
            }
            logger.warn("manifest 校验失败（缺 id/非对象），按未命中处理: ssGameId={}", ssGameId);
            return null;
        } catch (Exception e) {
            // 畸形/被篡改：不盲信，按未命中处理
            logger.warn("manifest 解析失败，按未命中处理: ssGameId={}, err={}", ssGameId, e.getMessage());
            return null;
        }
    }

    /**
     * 取 manifest（cache-first）：命中直接返回；未命中且 allowFetch=true 时经线程管理器现拉一次并缓存。
     *
     * @param ssGameId   SS 全局游戏ID
     * @param systemId   绑定的 SS 系统ID（可为 null，仅按 gameid 取）
     * @param allowFetch 是否允许出网拉取（自动缓存开关/cache-through 由上层决定）
     * @return jeu 节点；未命中且不可/拉取失败时返回 null
     */
    public JsonNode getOrFetchGame(Long ssGameId, Integer systemId, boolean allowFetch) {
        JsonNode cached = getCachedGame(ssGameId);
        if (cached != null) {
            return cached;
        }
        if (!allowFetch) {
            return null;
        }
        return fetchAndCache(ssGameId, systemId);
    }

    /**
     * 经线程管理器出网拉取完整 jeuInfos，成功则缓存并返回 jeu 节点；失败/拿不到线程返回 null。
     */
    public JsonNode fetchAndCache(Long ssGameId, Integer systemId) {
        if (ssGameId == null) {
            return null;
        }
        boolean acquired = false;
        try {
            acquired = threadResourceManager.acquireForGameInfo(60000);
            if (!acquired) {
                logger.warn("manifest 取数未获取到线程资源（并发满/额度为0），按未命中处理: ssGameId={}", ssGameId);
                return null;
            }

            String url = buildJeuInfosUrl(ssGameId, systemId);
            String response = executeRequest(url);
            if (response == null || response.isEmpty()) {
                logger.warn("manifest 拉取返回空响应: ssGameId={}", ssGameId);
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode responseNode = root.has("response") ? root.get("response") : null;
            if (responseNode == null) {
                return null;
            }

            // 顺带更新配额（与其他刮削路径一致）
            if (responseNode.has("ssuser")) {
                updateQuotaFromSsuser(responseNode.get("ssuser"));
            }

            if (!responseNode.has("jeu") || responseNode.get("jeu").isNull()) {
                logger.info("manifest 拉取无 jeu 节点: ssGameId={}", ssGameId);
                return null;
            }
            JsonNode jeu = responseNode.get("jeu");

            // 缓存完整 jeu 节点
            try {
                GameManifest row = new GameManifest();
                row.setSsGameId(ssGameId);
                row.setManifest(objectMapper.writeValueAsString(jeu));
                row.setFetchedAt(new Timestamp(System.currentTimeMillis()));
                gameManifestMapper.upsert(row);
            } catch (Exception e) {
                logger.error("manifest 落库失败: ssGameId={}, err={}", ssGameId, e.getMessage());
            }
            return jeu;
        } catch (ScreenScraperApiException e) {
            logger.error("manifest 拉取遇到 API 状态码: {} - {}", e.getStatusCode(), e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("manifest 拉取失败: ssGameId={}, err={}", ssGameId, e.getMessage());
            return null;
        } finally {
            if (acquired) {
                threadResourceManager.releaseForGameInfo();
            }
        }
    }

    // ==================== 零额外请求落缓存 + 父 rom 投影 ====================

    /**
     * 直接把已成功抓到的完整 jeu 节点写入缓存（刮削成功时手里已有 jeu，零额外请求）。
     *
     * @return true=已成功落库；false=参数无效或写入失败（调用方据此决定 cached 标记）
     */
    public boolean storeManifest(Long ssGameId, JsonNode jeu) {
        if (ssGameId == null || jeu == null || jeu.isNull()) {
            return false;
        }
        try {
            GameManifest row = new GameManifest();
            row.setSsGameId(ssGameId);
            row.setManifest(objectMapper.writeValueAsString(jeu));
            row.setFetchedAt(new Timestamp(System.currentTimeMillis()));
            gameManifestMapper.upsert(row);
            return true;
        } catch (Exception e) {
            logger.error("manifest 落库失败: ssGameId={}, err={}", ssGameId, e.getMessage());
            return false;
        }
    }

    /**
     * 从完整 jeu 的 {@code roms[]} 投影出本体的父 rom 文件名（仅街机 clone 有意义）。
     *
     * 数据驱动、不写死：按本体 crc 在 roms[] 命中一条 → 读其 romcloneof →
     * 若 >0 再在同 jeu 内按 id 找到父条目 → 返回其 romfilename。
     * 任一环节缺失/无 cloneof → 返回 null（不写）。
     */
    public String projectParentRom(JsonNode jeu, String crc32) {
        if (jeu == null || crc32 == null || crc32.isEmpty()) {
            return null;
        }
        List<JsonNode> roms = collectRomEntries(jeu.get("roms"));
        if (roms.isEmpty()) {
            return null;
        }
        String wantCrc = normalizeCrc(crc32);
        if (wantCrc.isEmpty()) {
            return null;
        }
        String cloneOfId = null;
        for (JsonNode rom : roms) {
            String rc = normalizeCrc(text(rom, "romcrc"));
            if (!rc.isEmpty() && rc.equals(wantCrc)) {
                String co = text(rom, "romcloneof");
                if (co != null && !co.isEmpty() && !co.equals("0")) {
                    cloneOfId = co.trim();
                }
                break;
            }
        }
        if (cloneOfId == null) {
            return null;
        }
        for (JsonNode rom : roms) {
            if (cloneOfId.equals(text(rom, "id").trim())) {
                String fn = text(rom, "romfilename");
                return (fn == null || fn.isEmpty()) ? null : fn;
            }
        }
        return null;
    }

    /** roms 节点可能是数组，也可能是以 rom id 为键的对象——两种都收。 */
    private List<JsonNode> collectRomEntries(JsonNode roms) {
        List<JsonNode> list = new ArrayList<>();
        if (roms == null || roms.isNull()) {
            return list;
        }
        if (roms.isArray()) {
            for (JsonNode r : roms) {
                if (r != null && r.isObject()) list.add(r);
            }
        } else if (roms.isObject()) {
            Iterator<JsonNode> it = roms.elements();
            while (it.hasNext()) {
                JsonNode r = it.next();
                if (r != null && r.isObject()) list.add(r);
            }
        }
        return list;
    }

    /** 归一化 CRC：去空白/去 0x 前缀/去前导零/转大写，便于跨来源比较。 */
    private String normalizeCrc(String crc) {
        if (crc == null) {
            return "";
        }
        String s = crc.trim();
        if (s.toLowerCase().startsWith("0x")) {
            s = s.substring(2);
        }
        s = s.toUpperCase();
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') {
            i++;
        }
        return s.substring(i);
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? "" : v.asText("");
    }

    // ==================== 内部工具 ====================

    private String buildJeuInfosUrl(Long ssGameId, Integer systemId) {
        okhttp3.HttpUrl.Builder urlBuilder = okhttp3.HttpUrl
                .parse(scraperConfig.getBaseUrl() + "/api2/jeuInfos.php").newBuilder();
        urlBuilder.addQueryParameter("devid", scraperConfig.getDevPseudo());
        urlBuilder.addQueryParameter("devpassword", scraperConfig.getDevPassword());
        urlBuilder.addQueryParameter("softname", "FrontendKiller");
        urlBuilder.addQueryParameter("output", "json");
        urlBuilder.addQueryParameter("gameid", String.valueOf(ssGameId));
        if (systemId != null && systemId > 0) {
            urlBuilder.addQueryParameter("systemeid", systemId.toString());
        }
        Map<String, String> settings = scraperSettingsService.getSettings();
        if (settings.containsKey("username") && settings.containsKey("password")) {
            String u = settings.get("username");
            String p = settings.get("password");
            if (u != null && !u.isEmpty() && p != null && !p.isEmpty()) {
                urlBuilder.addQueryParameter("ssid", u);
                urlBuilder.addQueryParameter("sspassword", p);
            }
        }
        return urlBuilder.build().toString();
    }

    private void updateQuotaFromSsuser(JsonNode ssuserNode) {
        try {
            if (ssuserNode.has("maxthreads")) {
                threadResourceManager.updateFromServerResponse(ssuserNode.get("maxthreads").asInt());
            }
            int requestsToday = ssuserNode.has("requeststoday") ? ssuserNode.get("requeststoday").asInt(0) : 0;
            int maxRequestsPerDay = ssuserNode.has("maxrequestsperday") ? ssuserNode.get("maxrequestsperday").asInt(0) : 0;
            int maxRequestsPerMin = ssuserNode.has("maxrequestspermin") ? ssuserNode.get("maxrequestspermin").asInt(0) : 0;
            int maxDownloadSpeed = ssuserNode.has("maxdownloadspeed") ? ssuserNode.get("maxdownloadspeed").asInt(0) : 0;
            int requestsKoToday = ssuserNode.has("requestskotoday") ? ssuserNode.get("requestskotoday").asInt(0) : 0;
            String niveau = ssuserNode.has("niveau") ? ssuserNode.get("niveau").asText("") : "";
            String contribution = ssuserNode.has("contribution") ? ssuserNode.get("contribution").asText("") : "";
            threadResourceManager.updateQuotaFromServer(
                    requestsToday, maxRequestsPerDay, maxRequestsPerMin,
                    maxDownloadSpeed, requestsKoToday, niveau, contribution);
        } catch (Exception e) {
            logger.debug("更新配额信息失败（忽略）: {}", e.getMessage());
        }
    }

    private String executeRequest(String url) throws ScreenScraperApiException {
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    int statusCode = response.code();
                    String statusDesc = ScreenScraperStatusHandler.getStatusInfo(statusCode).getDescription();
                    throw new ScreenScraperApiException(statusCode, statusDesc);
                }
                return response.body() != null ? response.body().string() : null;
            }
        } catch (ScreenScraperApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ScreenScraperApiException(-1, "网络请求失败: " + e.getMessage());
        }
    }
}
