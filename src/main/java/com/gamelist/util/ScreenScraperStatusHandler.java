package com.gamelist.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ScreenScraper API 状态码处理工具类
 * 根据官方API文档处理不同的HTTP状态码
 */
public class ScreenScraperStatusHandler {

    private static final Logger logger = LoggerFactory.getLogger(ScreenScraperStatusHandler.class);

    private static final Set<Integer> STOP_IMMEDIATELY_CODES = Set.of(400, 401, 403, 423, 426, 429, 430, 431);
    private static final Set<Integer> NOTIFY_USER_CODES = Set.of(401, 403, 423, 426, 429, 430, 431);

    private static final int NOT_FOUND_THRESHOLD = 10;
    private static final long NOT_FOUND_WINDOW_MS = 10000;

    private static final AtomicInteger notFoundCount = new AtomicInteger(0);
    private static volatile long notFoundWindowStart = System.currentTimeMillis();

    public static final Map<Integer, StatusInfo> STATUS_MAP;

    static {
        STATUS_MAP = Map.ofEntries(
            Map.entry(200, new StatusInfo("成功", "请求成功，返回正常数据", StatusType.SUCCESS)),
            Map.entry(400, new StatusInfo("请求错误", "URL 缺少必要字段，或文件名包含路径", StatusType.CLIENT_ERROR)),
            Map.entry(401, new StatusInfo("未授权", "API 对非会员关闭，或服务器 CPU 使用率 > 60%", StatusType.AUTH_ERROR)),
            Map.entry(403, new StatusInfo("禁止访问", "开发者凭证错误", StatusType.AUTH_ERROR)),
            Map.entry(404, new StatusInfo("未找到", "游戏未找到", StatusType.NOT_FOUND)),
            Map.entry(423, new StatusInfo("锁定", "API 完全关闭，服务器有严重问题", StatusType.SERVER_ERROR)),
            Map.entry(426, new StatusInfo("需要升级", "软件被黑名单（版本过旧或不兼容）", StatusType.CLIENT_ERROR)),
            Map.entry(429, new StatusInfo("请求过多", "线程数或每分钟请求数超限", StatusType.RATE_LIMIT)),
            Map.entry(430, new StatusInfo("配额用尽", "今日刮削次数已达上限", StatusType.RATE_LIMIT)),
            Map.entry(431, new StatusInfo("无效请求过多", "今日未找到的游戏请求次数已达上限", StatusType.RATE_LIMIT))
        );
    }

    public static class StatusInfo {
        private final String type;
        private final String description;
        private final StatusType statusType;

        public StatusInfo(String type, String description, StatusType statusType) {
            this.type = type;
            this.description = description;
            this.statusType = statusType;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public StatusType getStatusType() {
            return statusType;
        }
    }

    public enum StatusType {
        SUCCESS,
        CLIENT_ERROR,
        AUTH_ERROR,
        NOT_FOUND,
        SERVER_ERROR,
        RATE_LIMIT,
        UNKNOWN
    }

    public static StatusInfo getStatusInfo(int statusCode) {
        return STATUS_MAP.getOrDefault(statusCode,
            new StatusInfo("未知错误", "响应体包含具体错误信息", StatusType.UNKNOWN));
    }

    public static String getSuggestion(int statusCode) {
        return switch (statusCode) {
            case 200 -> "正常处理";
            case 400 -> "检查请求参数，确保URL不缺少必要字段。要立刻停止刮削";
            case 401 -> "稍后重试，或登录后使用。立刻停止刮削并弹窗通知用户";
            case 403 -> "检查 devid 和 devpassword。立刻停止刮削并弹窗通知用户";
            case 404 -> "检查 ROM 文件或 CRC 值是否正确";
            case 423 -> "等待官方修复。立刻停止刮削并弹窗通知用户";
            case 426 -> "更新软件版本。立刻停止刮削并弹窗通知用户";
            case 429 -> "降低请求速度。立刻停止刮削并弹窗通知用户";
            case 430 -> "等待明天，或提高用户等级。立刻停止刮削并弹窗通知用户";
            case 431 -> "整理 ROM 文件，明天再试。立刻停止刮削并弹窗通知用户";
            default -> "根据响应体内容排查";
        };
    }

    public static boolean shouldStopImmediately(int statusCode) {
        return STOP_IMMEDIATELY_CODES.contains(statusCode);
    }

    public static boolean shouldNotifyUser(int statusCode) {
        return NOTIFY_USER_CODES.contains(statusCode);
    }

    public static synchronized boolean shouldStopDueToNotFound() {
        long now = System.currentTimeMillis();
        if (now - notFoundWindowStart > NOT_FOUND_WINDOW_MS) {
            notFoundWindowStart = now;
            notFoundCount.set(1);
            return false;
        }

        int count = notFoundCount.incrementAndGet();
        if (count >= NOT_FOUND_THRESHOLD) {
            logger.error("10秒内出现{}次404错误，停止刮削", count);
            notFoundCount.set(0);
            return true;
        }
        return false;
    }

    public static int getCurrentNotFoundCount() {
        long now = System.currentTimeMillis();
        if (now - notFoundWindowStart > NOT_FOUND_WINDOW_MS) {
            return 0;
        }
        return notFoundCount.get();
    }

    public static void resetNotFoundCount() {
        notFoundCount.set(0);
        notFoundWindowStart = System.currentTimeMillis();
    }

    public static Map<String, Object> handleResponse(int statusCode, String responseBody) {
        Map<String, Object> result = new ConcurrentHashMap<>();

        if (statusCode == 200) {
            result.put("success", true);
            result.put("message", "请求成功");
            result.put("suggestion", "正常处理");
            result.put("statusType", "SUCCESS");
            return result;
        }

        StatusInfo statusInfo = getStatusInfo(statusCode);
        String suggestion = getSuggestion(statusCode);

        logger.error("ScreenScraper API 错误 - 状态码: {}, 类型: {}, 描述: {}, 建议: {}",
            statusCode, statusInfo.getType(), statusInfo.getDescription(), suggestion);

        if (responseBody != null && !responseBody.isEmpty()) {
            logger.error("响应体内容: {}", responseBody.length() > 500 ? responseBody.substring(0, 500) : responseBody);
        }

        result.put("success", false);
        result.put("statusCode", statusCode);
        result.put("message", statusInfo.getDescription());
        result.put("errorType", statusInfo.getType());
        result.put("suggestion", suggestion);
        result.put("statusType", statusInfo.getStatusType().name());
        result.put("retryAfter", getRetryAfterSeconds(statusCode));
        result.put("stopImmediately", shouldStopImmediately(statusCode));
        result.put("notifyUser", shouldNotifyUser(statusCode));

        return result;
    }

    public static int getRetryAfterSeconds(int statusCode) {
        return switch (statusCode) {
            case 401 -> 60;
            case 423 -> 300;
            case 429 -> 60;
            case 430, 431 -> 86400;
            default -> 0;
        };
    }

    public static boolean canRetry(int statusCode) {
        return switch (statusCode) {
            case 401, 423, 429 -> true;
            default -> false;
        };
    }

    public static boolean isAuthError(int statusCode) {
        return statusCode == 401 || statusCode == 403;
    }

    public static boolean isRateLimitError(int statusCode) {
        return statusCode == 429 || statusCode == 430 || statusCode == 431;
    }

    public static boolean isServerError(int statusCode) {
        return statusCode == 423 || (statusCode >= 500 && statusCode < 600);
    }

    /**
     * 是否为软件级限额（与软件版本/开发者凭证相关）
     * 429: 线程数或每分钟请求数超限
     * 426: 软件被黑名单
     * 423: API 完全关闭
     */
    public static boolean isSoftwareLimitError(int statusCode) {
        return statusCode == 429 || statusCode == 426 || statusCode == 423 || statusCode == 400;
    }

    /**
     * 是否为用户级限额（与用户账号/等级相关）
     * 430: 今日刮削次数已达上限
     * 431: 今日未找到游戏请求次数已达上限
     * 401: 非会员限制或服务器负载高
     */
    public static boolean isUserLimitError(int statusCode) {
        return statusCode == 430 || statusCode == 431 || statusCode == 401;
    }

    /**
     * 获取限额类型的用户友好提示
     */
    public static String getLimitWarningMessage(int statusCode) {
        if (isSoftwareLimitError(statusCode)) {
            return switch (statusCode) {
                case 429 -> "⚠️ 软件请求频率超限：ScreenScraper 服务器限制了当前软件的请求速度，已暂停刮削。请稍后手动恢复。";
                case 426 -> "⚠️ 软件版本受限：当前软件版本被 ScreenScraper 服务器限制（版本过旧或不兼容），已暂停刮削。请更新软件版本。";
                case 423 -> "⚠️ API 已关闭：ScreenScraper 服务器 API 暂时完全关闭，已暂停刮削。请等待官方修复。";
                case 400 -> "⚠️ 请求错误：发送给 ScreenScraper 的请求包含错误，已暂停刮削。请检查配置。";
                default -> "⚠️ 软件级限额：ScreenScraper 服务器返回限制状态 (" + statusCode + ")，已暂停刮削。";
            };
        } else if (isUserLimitError(statusCode)) {
            return switch (statusCode) {
                case 430 -> "⚠️ 用户刮削配额已满：今日刮削次数已达 ScreenScraper 上限，已暂停刮削。请等待明天或升级用户等级。";
                case 431 -> "⚠️ 用户未找到配额已满：今日未找到的游戏请求次数已达上限，已暂停刮削。请整理 ROM 文件或等待明天。";
                case 401 -> "⚠️ 用户权限受限：ScreenScraper 服务器限制当前用户状态（可能需登录或服务器负载过高），已暂停刮削。";
                default -> "⚠️ 用户级限额：ScreenScraper 服务器返回用户限制状态 (" + statusCode + ")，已暂停刮削。";
            };
        }
        return "⚠️ 刮削遇到限制 (状态码: " + statusCode + ")，已暂停。";
    }

    public static void logStatus(int statusCode, String context) {
        if (statusCode == 200) {
            logger.debug("ScreenScraper API [{}] - 成功", context);
            return;
        }

        StatusInfo statusInfo = getStatusInfo(statusCode);
        String suggestion = getSuggestion(statusCode);

        logger.warn("ScreenScraper API [{}] - 状态码: {}, 类型: {}, 描述: {}, 建议: {}",
            context, statusCode, statusInfo.getType(), statusInfo.getDescription(), suggestion);
    }
}