package com.gamelist.service;

import java.util.List;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.gamelist.model.Notification;

/**
 * 通知中心服务：持久化所有后台事件并通过 SSE 实时推送。
 */
public interface NotificationService {

    /**
     * 发送通知：写入数据库并广播给所有 SSE 订阅者。
     *
     * @param title   通知标题
     * @param message 通知内容
     * @param type    类型：success / warning / error / info
     * @param source  事件来源：task / scraper / media-download 等
     */
    Notification send(String title, String message, String type, String source);

    /** 最近的通知列表（倒序） */
    List<Notification> recent(int limit);

    /** 未读数量 */
    int unreadCount();

    /** 全部标记已读 */
    void markAllRead();

    /** 清空通知 */
    void clear();

    /** 订阅 SSE 事件流 */
    SseEmitter subscribe();
}
