package com.gamelist.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.gamelist.mapper.BackgroundTaskMapper;
import com.gamelist.mapper.MediaDownloadTaskMapper;

/**
 * 应用启动时的任务恢复初始化器
 * 
 * 处理断电、异常关闭等情况下遗留的任务状态：
 * 1. 将 DOWNLOADING 状态的媒体下载任务重置为 PENDING（以便用户手动恢复下载）
 * 2. 将 RUNNING 状态的后台任务标记为 FAILED（因为应用重启后它们不可能继续）
 * 3. 统计有 PENDING 任务的平台数量，在日志中提示用户手动恢复
 * 
 * 注意：不自动启动下载，因为 API 额度可能已耗尽，需由用户在媒体下载页面手动决定恢复时机
 */
@Component
@Order(10) // 在数据库初始化之后执行
public class TaskRecoveryInitializer implements ApplicationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(TaskRecoveryInitializer.class);
    
    private final BackgroundTaskMapper backgroundTaskMapper;
    private final MediaDownloadTaskMapper mediaDownloadTaskMapper;
    
    public TaskRecoveryInitializer(BackgroundTaskMapper backgroundTaskMapper,
                                   MediaDownloadTaskMapper mediaDownloadTaskMapper) {
        this.backgroundTaskMapper = backgroundTaskMapper;
        this.mediaDownloadTaskMapper = mediaDownloadTaskMapper;
    }
    
    @Override
    public void run(ApplicationArguments args) {
        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        logger.info("  🔧 启动任务恢复检查...");
        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        try {
            // 1. 恢复媒体下载任务：DOWNLOADING → PENDING
            int downloadingReset = mediaDownloadTaskMapper.updateDownloadingToPending();
            if (downloadingReset > 0) {
                logger.info("  ✅ 已恢复 {} 个中断的媒体下载任务 (DOWNLOADING → PENDING)", downloadingReset);
            } else {
                logger.info("  ✅ 没有中断的媒体下载任务需要恢复");
            }
            
            // 2. 标记后台任务：RUNNING → FAILED
            int runningReset = backgroundTaskMapper.resetRunningTasks();
            if (runningReset > 0) {
                logger.info("  ✅ 已标记 {} 个中断的后台任务为失败 (RUNNING → FAILED)", runningReset);
            } else {
                logger.info("  ✅ 没有中断的后台任务需要处理");
            }
            
            // 3. 统计有 PENDING 任务的平台，提示用户手动恢复
            reportPendingPlatforms();
            
        } catch (Exception e) {
            logger.error("任务恢复检查失败: {}", e.getMessage(), e);
        }
        
        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        logger.info("  ✅ 任务恢复检查完成");
        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
    
    /**
     * 统计有 PENDING 任务的平台并在日志中提示用户
     * 不自动启动下载，避免在 API 额度耗尽时反复触发错误
     */
    private void reportPendingPlatforms() {
        try {
            List<Long> platformIds = mediaDownloadTaskMapper.selectDistinctPlatformIds();
            if (platformIds == null || platformIds.isEmpty()) {
                return;
            }
            
            int platformsWithPending = 0;
            long totalPending = 0;
            for (Long platformId : platformIds) {
                long pendingCount = mediaDownloadTaskMapper.countPendingByPlatformId(platformId);
                if (pendingCount > 0) {
                    platformsWithPending++;
                    totalPending += pendingCount;
                    logger.info("  📋 平台 {} 有 {} 个待下载任务，可在媒体下载页面手动恢复", platformId, pendingCount);
                }
            }
            
            if (platformsWithPending > 0) {
                logger.info("  💡 共 {} 个平台有 {} 个待下载任务，请前往媒体下载页面点击「恢复」按钮开始下载", 
                           platformsWithPending, totalPending);
            }
        } catch (Exception e) {
            logger.error("统计待下载平台失败: {}", e.getMessage(), e);
        }
    }
}
