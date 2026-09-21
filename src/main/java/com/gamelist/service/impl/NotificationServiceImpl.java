package com.gamelist.service.impl;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.gamelist.mapper.NotificationMapper;
import com.gamelist.model.Notification;
import com.gamelist.service.NotificationService;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    /** 通知表保留上限：超出后清理最旧记录，避免无限增长 */
    private static final int MAX_KEEP = 500;

    /**
     * SSE 服务端超时：5 分钟自动断开，避免旧连接无上限堆积。
     * 前端 EventSource 会在连接 CLOSED 后自动重连，实现上仍是长连接，
     * 但可以清理“忘记 close”的僵尸连接。
     */
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    /** 心跳周期：15s 发一次 comment 行，防止代理/防火墙因无活动断开 */
    private static final long HEARTBEAT_PERIOD_SEC = 15L;

    private final NotificationMapper notificationMapper;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "notif-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public NotificationServiceImpl(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
        heartbeatExecutor.scheduleAtFixedRate(this::heartbeat,
                HEARTBEAT_PERIOD_SEC, HEARTBEAT_PERIOD_SEC, TimeUnit.SECONDS);
    }

    @Override
    public Notification send(String title, String message, String type, String source) {
        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type != null ? type : "info");
        notification.setSource(source);
        notification.setRead(false);
        notification.setCreatedAt(new Date());
        try {
            notificationMapper.insert(notification);
            trimOverflow();
        } catch (Exception e) {
            logger.warn("通知写入数据库失败: {} - {}", title, e.getMessage());
        }
        logger.info("发送通知: [{}] {} - {}", type, title, message);
        broadcast(notification);
        return notification;
    }

    @Override
    public List<Notification> recent(int limit) {
        return notificationMapper.selectRecent(limit > 0 ? limit : 50);
    }

    @Override
    public int unreadCount() {
        return notificationMapper.countUnread();
    }

    @Override
    public void markAllRead() {
        notificationMapper.markAllRead();
    }

    @Override
    public void clear() {
        notificationMapper.deleteAll();
    }

    @Override
    public SseEmitter subscribe() {
        // 5 分钟服务端超时，避免僵尸 SSE 堆积占用 Tomcat 连接，影响新页面建立 fetch
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> { emitters.remove(emitter); try { emitter.complete(); } catch (Exception ignore) {} });
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /** 定时向所有订阅者发送 SSE 注释行作为心跳；发送失败的连接直接剔除 */
    private void heartbeat() {
        if (emitters.isEmpty()) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    /** 将新通知推送给所有 SSE 订阅者，发送失败的连接直接移除 */
    private void broadcast(Notification notification) {
        if (emitters.isEmpty()) {
            return;
        }
        Map<String, Object> payload = toPayload(notification);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    private Map<String, Object> toPayload(Notification notification) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", notification.getId());
        map.put("title", notification.getTitle());
        map.put("message", notification.getMessage());
        map.put("type", notification.getType());
        map.put("source", notification.getSource());
        map.put("read", notification.getRead());
        map.put("createdAt", notification.getCreatedAt());
        return map;
    }

    /** 超出保留上限时清理最旧记录 */
    private void trimOverflow() {
        try {
            List<Notification> overflow = notificationMapper.selectRecent(MAX_KEEP + 1);
            if (overflow != null && overflow.size() > MAX_KEEP) {
                // selectRecent 倒序返回，最后一条即第 MAX_KEEP+1 旧的通知，以其 id 为阈值删除更旧记录
                long cutoffId = overflow.get(overflow.size() - 1).getId();
                notificationMapper.deleteOlderThan(cutoffId);
                logger.info("通知数量超过上限 {}，已清理 id <= {} 的历史通知", MAX_KEEP, cutoffId);
            }
        } catch (Exception e) {
            logger.warn("通知清理失败: {}", e.getMessage());
        }
    }
}
