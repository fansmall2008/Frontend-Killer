package com.gamelist.controller;

import com.gamelist.mapper.MediaDownloadTaskMapper;
import com.gamelist.model.MediaDownloadTask;
import com.gamelist.service.MediaDownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/media-download")
public class MediaDownloadController {

    private static final Logger logger = LoggerFactory.getLogger(MediaDownloadController.class);

    @Autowired
    private MediaDownloadTaskMapper mediaDownloadTaskMapper;

    @Autowired
    private MediaDownloadService mediaDownloadService;

    /**
     * 获取所有媒体下载任务
     */
    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> getAllTasks() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<MediaDownloadTask> tasks = mediaDownloadTaskMapper.selectPendingTasks(1000);
            response.put("success", true);
            response.put("data", tasks);
            response.put("count", tasks.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取媒体下载任务失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取媒体下载任务失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 按平台筛选媒体下载任务
     */
    @GetMapping("/tasks/by-platform")
    public ResponseEntity<Map<String, Object>> getTasksByPlatform(@RequestParam(required = false) Long platformId) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (platformId == null) {
                response.put("success", false);
                response.put("message", "请提供平台ID");
                return ResponseEntity.badRequest().body(response);
            }
            List<MediaDownloadTask> tasks = mediaDownloadTaskMapper.selectByPlatformId(platformId);
            response.put("success", true);
            response.put("data", tasks);
            response.put("count", tasks.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("按平台筛选媒体下载任务失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "按平台筛选媒体下载任务失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 按任务ID获取媒体下载任务
     */
    @GetMapping("/tasks/by-task")
    public ResponseEntity<Map<String, Object>> getTasksByTaskId(@RequestParam(required = false) Long taskId) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (taskId == null) {
                response.put("success", false);
                response.put("message", "请提供任务ID");
                return ResponseEntity.badRequest().body(response);
            }
            List<MediaDownloadTask> tasks = mediaDownloadTaskMapper.selectByTaskId(taskId);
            response.put("success", true);
            response.put("data", tasks);
            response.put("count", tasks.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("按任务ID获取媒体下载任务失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "按任务ID获取媒体下载任务失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 获取单个媒体下载任务
     */
    @GetMapping("/tasks/{id}")
    public ResponseEntity<Map<String, Object>> getTaskById(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            MediaDownloadTask task = mediaDownloadTaskMapper.selectById(id);
            if (task == null) {
                response.put("success", false);
                response.put("message", "媒体下载任务不存在");
                return ResponseEntity.status(404).body(response);
            }
            response.put("success", true);
            response.put("data", task);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取媒体下载任务失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取媒体下载任务失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 删除单个媒体下载任务
     */
    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            MediaDownloadTask task = mediaDownloadTaskMapper.selectById(id);
            if (task == null) {
                response.put("success", false);
                response.put("message", "媒体下载任务不存在");
                return ResponseEntity.status(404).body(response);
            }
            
            mediaDownloadTaskMapper.deleteById(id);
            response.put("success", true);
            response.put("message", "删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除媒体下载任务失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "删除媒体下载任务失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 批量删除媒体下载任务
     */
    @DeleteMapping("/tasks/batch")
    public ResponseEntity<Map<String, Object>> deleteTasksBatch(@RequestBody List<Long> ids) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (ids == null || ids.isEmpty()) {
                response.put("success", false);
                response.put("message", "请选择要删除的任务");
                return ResponseEntity.badRequest().body(response);
            }
            
            mediaDownloadTaskMapper.deleteBatch(ids);
            response.put("success", true);
            response.put("message", "成功删除 " + ids.size() + " 个任务");
            response.put("deletedCount", ids.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("批量删除媒体下载任务失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "批量删除媒体下载任务失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 删除指定平台的所有媒体下载任务
     */
    @DeleteMapping("/tasks/by-platform")
    public ResponseEntity<Map<String, Object>> deleteTasksByPlatform(@RequestParam Long platformId) {
        Map<String, Object> response = new HashMap<>();
        try {
            long count = mediaDownloadTaskMapper.countByPlatformId(platformId);
            if (count == 0) {
                response.put("success", false);
                response.put("message", "该平台没有媒体下载任务");
                return ResponseEntity.status(404).body(response);
            }
            
            mediaDownloadTaskMapper.deleteByPlatformId(platformId);
            response.put("success", true);
            response.put("message", "成功删除该平台的 " + count + " 个任务");
            response.put("deletedCount", count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除平台媒体下载任务失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "删除平台媒体下载任务失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 删除指定任务ID的所有媒体下载任务
     */
    @DeleteMapping("/tasks/by-task")
    public ResponseEntity<Map<String, Object>> deleteTasksByTaskId(@RequestParam Long taskId) {
        Map<String, Object> response = new HashMap<>();
        try {
            long count = mediaDownloadTaskMapper.countTotalByTaskId(taskId);
            if (count == 0) {
                response.put("success", false);
                response.put("message", "该任务没有媒体下载任务");
                return ResponseEntity.status(404).body(response);
            }
            
            mediaDownloadTaskMapper.deleteByTaskId(taskId);
            response.put("success", true);
            response.put("message", "成功删除该任务的 " + count + " 个媒体下载任务");
            response.put("deletedCount", count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除任务的媒体下载任务失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "删除任务的媒体下载任务失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 获取媒体下载状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("isRunning", mediaDownloadService.isRunning());
            response.put("isPaused", mediaDownloadService.isPaused());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取媒体下载状态失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取媒体下载状态失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 获取媒体下载统计
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(@RequestParam(required = false) Long taskId) {
        Map<String, Object> response = new HashMap<>();
        try {
            long total, pending, completed, failed, downloading;
            
            if (taskId != null) {
                total = mediaDownloadTaskMapper.countTotalByTaskId(taskId);
                pending = mediaDownloadTaskMapper.countPendingByTaskId(taskId);
                completed = mediaDownloadTaskMapper.countCompletedByTaskId(taskId);
                failed = mediaDownloadTaskMapper.countFailedByTaskId(taskId);
                downloading = mediaDownloadTaskMapper.countDownloadingByTaskId(taskId);
            } else {
                // TODO: 如果需要全局统计，需要添加相应的Mapper方法
                response.put("success", false);
                response.put("message", "请提供taskId参数");
                return ResponseEntity.badRequest().body(response);
            }
            
            response.put("success", true);
            response.put("total", total);
            response.put("pending", pending);
            response.put("completed", completed);
            response.put("failed", failed);
            response.put("downloading", downloading);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取媒体下载统计失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取媒体下载统计失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 暂停媒体下载
     */
    @PostMapping("/pause")
    public ResponseEntity<Map<String, Object>> pauseDownload() {
        Map<String, Object> response = new HashMap<>();
        try {
            mediaDownloadService.pauseMediaDownloadTask();
            response.put("success", true);
            response.put("message", "媒体下载已暂停");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("暂停媒体下载失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "暂停媒体下载失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 恢复媒体下载
     */
    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resumeDownload() {
        Map<String, Object> response = new HashMap<>();
        try {
            mediaDownloadService.resumeMediaDownloadTask();
            response.put("success", true);
            response.put("message", "媒体下载已恢复");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("恢复媒体下载失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "恢复媒体下载失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 停止媒体下载
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopDownload() {
        Map<String, Object> response = new HashMap<>();
        try {
            mediaDownloadService.stopMediaDownloadTask();
            response.put("success", true);
            response.put("message", "媒体下载已停止");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("停止媒体下载失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "停止媒体下载失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 获取所有有媒体下载任务的平台ID列表
     */
    @GetMapping("/platforms")
    public ResponseEntity<Map<String, Object>> getPlatformsWithTasks() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Long> platformIds = mediaDownloadTaskMapper.selectDistinctPlatformIds();
            response.put("success", true);
            response.put("data", platformIds);
            response.put("count", platformIds.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取平台列表失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取平台列表失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 一次性汇总所有平台的下载统计，前端刷新时仅需一次请求。
     * 返回结构：{ success, data: [ {platformId, total, pending, downloading, completed, failed, stopped}, ... ] }
     */
    @GetMapping("/platforms/summary")
    public ResponseEntity<Map<String, Object>> getPlatformsSummary() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> rows = mediaDownloadTaskMapper.selectPlatformStatusSummary();
            Map<Long, Map<String, Object>> byPlatform = new java.util.LinkedHashMap<>();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Object pidObj = row.get("platformId");
                    Object statusObj = row.get("status");
                    Object cntObj = row.get("cnt");
                    if (pidObj == null) continue;
                    long pid = ((Number) pidObj).longValue();
                    long cnt = cntObj == null ? 0L : ((Number) cntObj).longValue();
                    String status = statusObj == null ? "" : statusObj.toString();
                    Map<String, Object> agg = byPlatform.computeIfAbsent(pid, k -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("platformId", k);
                        m.put("total", 0L);
                        m.put("pending", 0L);
                        m.put("downloading", 0L);
                        m.put("completed", 0L);
                        m.put("failed", 0L);
                        m.put("stopped", 0L);
                        return m;
                    });
                    agg.put("total", ((Number) agg.get("total")).longValue() + cnt);
                    switch (status) {
                        case "PENDING":     agg.put("pending",     ((Number) agg.get("pending")).longValue() + cnt); break;
                        case "DOWNLOADING": agg.put("downloading", ((Number) agg.get("downloading")).longValue() + cnt); break;
                        case "COMPLETED":   agg.put("completed",   ((Number) agg.get("completed")).longValue() + cnt); break;
                        case "FAILED":      agg.put("failed",      ((Number) agg.get("failed")).longValue() + cnt); break;
                        case "STOPPED":     agg.put("stopped",     ((Number) agg.get("stopped")).longValue() + cnt); break;
                        default: break;
                    }
                }
            }
            response.put("success", true);
            response.put("data", new java.util.ArrayList<>(byPlatform.values()));
            response.put("count", byPlatform.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取平台下载汇总失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取平台下载汇总失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 获取指定平台的下载统计信息
     */
    @GetMapping("/platforms/{platformId}/stats")
    public ResponseEntity<Map<String, Object>> getPlatformStats(@PathVariable Long platformId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> stats = mediaDownloadService.getPlatformDownloadStats(platformId);
            response.put("success", true);
            response.put("data", stats);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取平台统计信息失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "获取平台统计信息失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 停止指定平台的媒体下载
     */
    @PostMapping("/platforms/{platformId}/stop")
    public ResponseEntity<Map<String, Object>> stopPlatformDownload(@PathVariable Long platformId) {
        Map<String, Object> response = new HashMap<>();
        try {
            mediaDownloadService.stopPlatformDownload(platformId);
            response.put("success", true);
            response.put("message", "平台 " + platformId + " 的媒体下载已停止");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("停止平台媒体下载失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "停止平台媒体下载失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 恢复指定平台的媒体下载
     */
    @PostMapping("/platforms/{platformId}/resume")
    public ResponseEntity<Map<String, Object>> resumePlatformDownload(@PathVariable Long platformId) {
        Map<String, Object> response = new HashMap<>();
        try {
            mediaDownloadService.resumePlatformDownload(platformId);
            response.put("success", true);
            response.put("message", "平台 " + platformId + " 的媒体下载已恢复（包括失败任务）");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("恢复平台媒体下载失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "恢复平台媒体下载失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 重试指定平台的失败下载任务
     */
    @PostMapping("/platforms/{platformId}/retry-failed")
    public ResponseEntity<Map<String, Object>> retryFailedDownloads(@PathVariable Long platformId) {
        Map<String, Object> response = new HashMap<>();
        try {
            mediaDownloadService.retryFailedDownloads(platformId);
            response.put("success", true);
            response.put("message", "平台 " + platformId + " 的失败下载任务已重置为待下载状态");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("重试失败下载任务失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "重试失败下载任务失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 停止所有平台的媒体下载
     */
    @PostMapping("/platforms/stop-all")
    public ResponseEntity<Map<String, Object>> stopAllPlatformDownload() {
        Map<String, Object> response = new HashMap<>();
        try {
            mediaDownloadService.stopAllPlatformDownload();
            response.put("success", true);
            response.put("message", "所有平台的媒体下载已停止");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("停止所有平台媒体下载失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "停止所有平台媒体下载失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 删除指定平台的所有刮削任务（包括所有刮削队列）
     */
    @PostMapping("/platforms/{platformId}/delete")
    public ResponseEntity<Map<String, Object>> deletePlatformScrapeTask(@PathVariable Long platformId) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 先停止该平台的下载
            mediaDownloadService.stopPlatformDownload(platformId);
            
            // 删除该平台的所有媒体下载任务
            long count = mediaDownloadTaskMapper.countByPlatformId(platformId);
            if (count > 0) {
                mediaDownloadTaskMapper.deleteByPlatformId(platformId);
            }
            
            response.put("success", true);
            response.put("message", "成功删除该平台的 " + count + " 个刮削任务");
            response.put("deletedCount", count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除平台刮削任务失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "删除平台刮削任务失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}