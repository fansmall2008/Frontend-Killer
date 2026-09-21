package com.gamelist.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.gamelist.service.NotificationService;

/**
 * 通知中心接口：铃铛角标、通知面板与 SSE 实时推送。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 最近通知列表（倒序） */
    @GetMapping
    public ResponseEntity<Object> list(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        try {
            return ResponseEntity.ok(notificationService.recent(limit));
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /** 未读数量（铃铛角标） */
    @GetMapping("/unread-count")
    public ResponseEntity<Object> unreadCount() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            result.put("count", notificationService.unreadCount());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("count", 0);
            return ResponseEntity.ok(result);
        }
    }

    /** 全部标记已读 */
    @PutMapping("/read")
    public ResponseEntity<Object> markAllRead() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            notificationService.markAllRead();
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /** 清空通知 */
    @DeleteMapping
    public ResponseEntity<Object> clear() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            notificationService.clear();
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /** SSE 事件流：新通知实时推送 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return notificationService.subscribe();
    }
}
