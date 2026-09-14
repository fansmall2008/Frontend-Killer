package com.gamelist.util;

/**
 * ScreenScraper API 异常，携带 HTTP 状态码
 * 用于在 executeRequest 与调用方之间传递状态码信息，
 * 使调用方能区分限速(429/430/431)、认证错误(401/403)等不同情况
 */
public class ScreenScraperApiException extends Exception {

    private final int statusCode;

    public ScreenScraperApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /**
     * 是否为限额类错误（需要暂停刮削）
     */
    public boolean isLimitError() {
        return ScreenScraperStatusHandler.isRateLimitError(statusCode)
            || ScreenScraperStatusHandler.isSoftwareLimitError(statusCode)
            || ScreenScraperStatusHandler.isUserLimitError(statusCode);
    }

    /**
     * 是否为软件级限额
     */
    public boolean isSoftwareLimit() {
        return ScreenScraperStatusHandler.isSoftwareLimitError(statusCode);
    }

    /**
     * 是否为用户级限额
     */
    public boolean isUserLimit() {
        return ScreenScraperStatusHandler.isUserLimitError(statusCode);
    }

    /**
     * 获取用户友好的限额提示信息
     */
    public String getLimitWarningMessage() {
        return ScreenScraperStatusHandler.getLimitWarningMessage(statusCode);
    }
}
