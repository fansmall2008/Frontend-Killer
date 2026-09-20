package com.gamelist.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.gamelist.mapper.GameMapper;
import com.gamelist.model.ExportRequest;
import com.gamelist.model.Platform;
import com.gamelist.model.TemplateV3;
import com.gamelist.service.ExportRuleService;
import com.gamelist.service.ExportService;
import com.gamelist.service.PlatformService;
import com.gamelist.service.TaskService;

@Service
public class ExportServiceImpl implements ExportService {
    private static final Logger logger = LoggerFactory.getLogger(ExportServiceImpl.class);

    @Autowired
    private GameMapper gameMapper;

    @Autowired
    private ExportRuleService exportRuleService;

    @Autowired
    private PlatformService platformService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ExportOrchestrator exportOrchestrator;

    // 默认线程池大小 - 根据CPU核心数设置
    private static final int DEFAULT_THREAD_POOL_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors());
    // 最大线程池大小
    private static final int MAX_THREAD_POOL_SIZE = Math.max(10, Runtime.getRuntime().availableProcessors() * 2);

    @Override
    public Map<String, Object> exportPlatform(ExportRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 记录导出请求参数
            logger.info("Starting platform export with request: {}", request);
            
            String outputPath = request.getOutputPath() != null ? request.getOutputPath() : "/data/output";
            Long platformId = request.getPlatformId();
            String frontend = request.getFrontend();
            final int threadCount;
            {
                int tempThreadCount = request.getThreadCount() > 0 ? request.getThreadCount() : DEFAULT_THREAD_POOL_SIZE;
                // 确保线程数在合理范围内
                tempThreadCount = Math.min(tempThreadCount, MAX_THREAD_POOL_SIZE);
                tempThreadCount = Math.max(tempThreadCount, 1);
                threadCount = tempThreadCount;
            }

            logger.info("Export parameters - platformId: {}, frontend: {}, outputPath: {}, threadCount: {}", 
                platformId, frontend, outputPath, threadCount);
            logger.info("Export options - copyRoms: {}, copyMedia: {}, generateDataFile: {}", 
                request.isCopyRoms(), request.isCopyMedia(), request.isGenerateDataFile());

            // 获取平台信息
            logger.info("Getting platform information for ID: {}", platformId);
            Platform platform = platformService.getPlatformById(platformId);
            if (platform == null) {
                logger.error("Platform not found: {}", platformId);
                result.put("success", false);
                result.put("error", "Platform not found");
                return result;
            }
            
            final String platformName;
            {
                String tempPlatformName = platform.getName();
                if (tempPlatformName == null || tempPlatformName.isEmpty()) {
                    tempPlatformName = "platform_" + platformId;
                    logger.warn("Platform name is empty, using default: {}", tempPlatformName);
                }
                platformName = tempPlatformName;
            }
            logger.info("Platform information: id={}, name={}, folderPath={}", 
                platform.getId(), platform.getName(), platform.getFolderPath());

            // 加载规则
            logger.info("Loading export rules");
            exportRuleService.loadRules();

            // v3 模板分发
            logger.info("Loading v3 template for frontend: {}", frontend);
            TemplateV3 v3Template = exportRuleService.getV3RuleByFrontend(frontend);
            if (v3Template == null) {
                result.put("success", false);
                result.put("error", "Export template not found for frontend: " + frontend);
                return result;
            }

            String taskDescription = "导出平台(v3): " + platformName + " (" + frontend + ")";
            final com.gamelist.model.BackgroundTask task = taskService.createTask("export", taskDescription);
            logger.info("Created v3 export task with ID: {}", task.getId());

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.submit(() -> {
                try {
                    taskService.updateTaskProgress(task.getId(), 0, "开始 v3 导出", 0, 100);
                    exportOrchestrator.executeExport(v3Template, platform, request, task);
                } catch (Exception e) {
                    logger.error("v3 export failed", e);
                    taskService.failTask(task.getId(), "v3 导出失败", e.getMessage());
                }
            });
            executorService.shutdown();

            result.put("success", true);
            result.put("message", "v3 导出任务已启动");
            result.put("taskId", task.getId());
        } catch (Exception e) {
            logger.error("Export platform failed", e);
            result.put("success", false);
            result.put("error", "Export failed: " + e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> batchExport(List<Long> platformIds, ExportRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String frontend = request.getFrontend();
            String baseOutputPath = request.getOutputPath() != null ? request.getOutputPath() : "/data/output";
            final int threadCount = request.getThreadCount() > 0 ? request.getThreadCount() : DEFAULT_THREAD_POOL_SIZE;

            // 验证所有平台 ID
            List<Platform> platforms = new ArrayList<>();
            for (Long pid : platformIds) {
                Platform p = platformService.getPlatformById(pid);
                if (p == null) {
                    logger.warn("Batch export: platform {} not found, skipping", pid);
                    continue;
                }
                platforms.add(p);
            }

            if (platforms.isEmpty()) {
                result.put("success", false);
                result.put("error", "No valid platforms found");
                return result;
            }

            // 创建单个后台任务，顺序处理所有平台
            String taskDescription = "批量导出: " + platforms.size() + " 个平台 (" + frontend + ")";
            final com.gamelist.model.BackgroundTask task = taskService.createTask("export", taskDescription);
            logger.info("Batch export: {} platforms, single task ID: {}", platforms.size(), task.getId());

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.submit(() -> {
                int completed = 0;
                int failed = 0;
                try {
                    taskService.updateTaskProgress(task.getId(), 0,
                            "开始批量导出 0/" + platforms.size(), 0, 100);

                    for (int i = 0; i < platforms.size(); i++) {
                        Platform platform = platforms.get(i);
                        try {
                            taskService.updateTaskProgress(task.getId(),
                                    (int) (i * 100.0 / platforms.size()),
                                    "导出平台 (" + (i + 1) + "/" + platforms.size() + "): " + platform.getName(),
                                    i * 100 / platforms.size(), 100);

                            // 构建该平台的导出请求
                            ExportRequest individualRequest = new ExportRequest();
                            individualRequest.setPlatformId(platform.getId());
                            individualRequest.setFrontend(frontend);
                            individualRequest.setOutputPath(baseOutputPath);
                            individualRequest.setCopyRoms(request.isCopyRoms());
                            individualRequest.setCopyMedia(request.isCopyMedia());
                            individualRequest.setGenerateDataFile(request.isGenerateDataFile());
                            individualRequest.setThreadCount(threadCount);

                            // v3 模板分发
                            TemplateV3 v3Template = exportRuleService.getV3RuleByFrontend(frontend);
                            if (v3Template == null) {
                                logger.error("v3 template not found for frontend: {}", frontend);
                                failed++;
                                continue;
                            }
                            exportOrchestrator.executeExport(v3Template, platform, individualRequest, task);
                            completed++;
                        } catch (Exception e) {
                            logger.error("Batch export: platform {} failed", platform.getName(), e);
                            failed++;
                        }
                    }

                    String finalMessage = String.format("批量导出完成: 成功 %d/%d", completed, platforms.size());
                    if (failed > 0) {
                        finalMessage += String.format(", 失败 %d", failed);
                    }
                    taskService.completeTask(task.getId(), finalMessage, "导出路径: " + baseOutputPath);
                    logger.info("Batch export completed: {}/{} success", completed, platforms.size());

                } catch (Exception e) {
                    logger.error("Batch export task failed", e);
                    taskService.failTask(task.getId(), "批量导出失败", e.getMessage());
                }
            });
            executorService.shutdown();

            result.put("success", true);
            result.put("message", "批量导出任务已启动: " + platforms.size() + " 个平台");
            result.put("taskId", task.getId());
            result.put("totalPlatforms", platforms.size());
        } catch (Exception e) {
            logger.error("Batch export failed", e);
            result.put("success", false);
            result.put("error", "Batch export failed: " + e.getMessage());
        }
        return result;
    }
}
