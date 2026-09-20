package com.gamelist.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.mapper.PlatformStatsCacheMapper;
import com.gamelist.model.PlatformStatsCache;
import com.gamelist.service.PlatformStatsScanner;

/**
 * 平台体量统计 REST 接口
 * <p>
 * 提供导出前的数据量预估能力：
 * <ul>
 *   <li>触发平台扫描（异步）</li>
 *   <li>查询单个/全部平台的扫描快照</li>
 *   <li>查询扫描运行状态</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsController.class);

    @Autowired
    private PlatformStatsScanner scanner;

    @Autowired
    private PlatformStatsCacheMapper cacheMapper;

    /**
     * 触发指定平台的体量扫描（异步）。
     * <p>
     * 幂等：若该平台已在扫描中，返回现有任务 ID。
     */
    @PostMapping("/scan/{platformId}")
    public ResponseEntity<Map<String, Object>> scanPlatform(@PathVariable Long platformId) {
        Map<String, Object> body = new HashMap<>();
        try {
            Long taskId = scanner.scanAsync(platformId);
            body.put("success", true);
            body.put("taskId", taskId);
            body.put("platformId", platformId);
            body.put("message", "扫描任务已启动");
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            body.put("success", false);
            body.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        } catch (Exception e) {
            logger.error("触发平台扫描失败: platformId={}", platformId, e);
            body.put("success", false);
            body.put("error", "触发扫描失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    /**
     * 获取指定平台的扫描快照。
     * <p>
     * 若从未扫描过返回 404，前端可据此提示用户"请先扫描"。
     */
    @GetMapping("/cache/{platformId}")
    public ResponseEntity<?> getCache(@PathVariable Long platformId) {
        try {
            PlatformStatsCache cache = scanner.getCache(platformId);
            if (cache == null) {
                Map<String, Object> body = new HashMap<>();
                body.put("success", false);
                body.put("scanned", false);
                body.put("message", "该平台尚未扫描");
                body.put("scanning", scanner.isScanning(platformId));
                return ResponseEntity.ok(body);
            }
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("scanned", true);
            body.put("scanning", scanner.isScanning(platformId));
            body.put("data", cache);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.error("获取平台扫描快照失败: platformId={}", platformId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * 获取所有平台的扫描快照列表（用于导出页/统计页批量展示）。
     */
    @GetMapping("/cache")
    public ResponseEntity<?> getAllCache() {
        try {
            List<PlatformStatsCache> list = cacheMapper.selectAll();
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            logger.error("获取全部平台扫描快照失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * 查询指定平台的扫描运行状态（前端轮询用）。
     */
    @GetMapping("/scan/{platformId}/status")
    public ResponseEntity<?> getScanStatus(@PathVariable Long platformId) {
        Map<String, Object> body = new HashMap<>();
        body.put("platformId", platformId);
        body.put("scanning", scanner.isScanning(platformId));
        return ResponseEntity.ok(body);
    }
}
