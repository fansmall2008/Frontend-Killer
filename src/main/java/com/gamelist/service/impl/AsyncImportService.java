package com.gamelist.service.impl;

import com.gamelist.model.BackgroundTask;
import com.gamelist.model.ImportStatistics;
import com.gamelist.service.GameService;
import com.gamelist.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * 异步导入服务
 * 独立 Bean 以解决 ScanController 中 @Async 自调用失效的问题
 */
@Service
public class AsyncImportService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncImportService.class);

    @Autowired
    private GameService gameService;

    @Autowired
    private TaskService taskService;

    @Async("taskExecutor")
    public void executeImport(Long taskId, List<String> files, String type, int threadCount,
                              String importMethod, String importTemplate, String scanPath,
                              boolean noDataFile, String fileExtensions, Long scraperSystemId,
                              boolean enableMediaDiscovery, List<Integer> levels) {
        try {
            if (noDataFile) {
                taskService.updateTaskLog(taskId, "使用无数据文件导入模式");
                taskService.updateTaskLog(taskId, "扫描路径：" + scanPath + "，扩展名：" + fileExtensions);
                taskService.updateTaskLog(taskId, "模板：" + importTemplate);
                if (scraperSystemId != null) {
                    taskService.updateTaskLog(taskId, "选中的 scraper 系统 ID：" + scraperSystemId);
                }

                ImportStatistics stats = gameService.importGamesFromFileScan(scanPath, fileExtensions, importTemplate, threadCount, taskId, scraperSystemId, levels, enableMediaDiscovery);
                taskService.updateTaskLog(taskId, "无数据文件导入完成，共导入 " + stats.getImportedGames() + " 个游戏");

                String resultMsg = "成功导入 " + stats.getImportedGames() + " 个游戏\n";
                resultMsg += "导入平台数量: " + stats.getImportedPlatforms() + "\n";
                resultMsg += "导入游戏数量: " + stats.getImportedGames() + "\n";
                taskService.completeTask(taskId, "导入完成", resultMsg);
                return;
            }

            taskService.updateTaskProgress(taskId, 0, "开始导入", 0, files.size() > 0 ? files.size() : 1);
            taskService.updateTaskLog(taskId, "开始导入任务，共 " + files.size() + " 个文件，线程数：" + threadCount);
            taskService.updateTaskLog(taskId, "导入方式：" + importMethod + "，模板：" + importTemplate);
            taskService.updateTaskLog(taskId, "匹配无记录媒体文件：" + (enableMediaDiscovery ? "是" : "否"));
            if (scraperSystemId != null) {
                taskService.updateTaskLog(taskId, "选中的 scraper 系统 ID：" + scraperSystemId);
            }

            int processed = 0;
            int importedPlatforms = 0;
            int importedGames = 0;
            StringBuilder skippedFiles = new StringBuilder();

            for (String filePath : files) {
                try {
                    File file = new File(filePath);
                    if (!file.exists()) {
                        String errorMsg = "文件不存在: " + filePath;
                        logger.warn(errorMsg);
                        skippedFiles.append(errorMsg).append("\n");
                        taskService.updateTaskLog(taskId, errorMsg);
                        continue;
                    }

                    if (filePath.endsWith("gamelist.xml")) {
                        ImportStatistics stats = gameService.importGamesFromXml(filePath, importMethod, importTemplate, false, threadCount, scraperSystemId, enableMediaDiscovery);
                        importedPlatforms += stats.getImportedPlatforms();
                        importedGames += stats.getImportedGames();
                    } else if (filePath.endsWith("metadata.pegasus.txt")) {
                        ImportStatistics stats = gameService.importGamesFromPegasusMetadata(filePath, importMethod, importTemplate, false, threadCount, scraperSystemId, enableMediaDiscovery, taskId);
                        importedPlatforms += stats.getImportedPlatforms();
                        importedGames += stats.getImportedGames();
                    } else {
                        String errorMsg = "不支持的文件类型: " + filePath;
                        logger.warn(errorMsg);
                        skippedFiles.append(errorMsg).append("\n");
                        taskService.updateTaskLog(taskId, errorMsg);
                        continue;
                    }

                    processed++;
                    int progress = (int) ((double) processed / files.size() * 100);
                    taskService.updateTaskProgress(taskId, progress, "导入中...", processed, files.size());
                    taskService.updateTaskLog(taskId, "成功导入文件: " + filePath);
                } catch (Exception e) {
                    String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
                    String errorMsg = "导入文件失败: " + filePath + "，原因: " + errorMessage;
                    logger.error(errorMsg, e);
                    try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
                        e.printStackTrace(pw);
                        String stackTrace = sw.toString();
                        logger.error("异常堆栈信息: {}", stackTrace);
                        taskService.updateTaskLog(taskId, errorMsg);
                        taskService.updateTaskLog(taskId, "详细错误: " + stackTrace.substring(0, Math.min(stackTrace.length(), 1000)));
                    }
                    skippedFiles.append(errorMsg).append("\n");
                }
            }

            StringBuilder resultMsg = new StringBuilder();
            int totalFiles = files.size() > 0 ? files.size() : 1;

            if (processed == 0 && totalFiles > 0) {
                resultMsg.append("所有文件导入失败\n");
                resultMsg.append("未导入文件: \n").append(skippedFiles.toString());
                taskService.completeTask(taskId, "导入完成", resultMsg.toString());
                taskService.updateTaskLog(taskId, "导入任务完成，所有文件导入失败");
            } else if (processed < totalFiles) {
                resultMsg.append("成功导入 " + processed + " 个文件\n");
                resultMsg.append("导入平台数量: " + importedPlatforms + "\n");
                resultMsg.append("导入游戏数量: " + importedGames + "\n");
                resultMsg.append("未导入文件: \n").append(skippedFiles.toString());
                taskService.completeTask(taskId, "导入完成", resultMsg.toString());
                taskService.updateTaskLog(taskId, "导入任务完成，共导入 " + processed + " 个文件，" + importedPlatforms + " 个平台，" + importedGames + " 个游戏，" + (totalFiles - processed) + " 个文件导入失败");
            } else {
                resultMsg.append("成功导入 " + processed + " 个文件\n");
                resultMsg.append("导入平台数量: " + importedPlatforms + "\n");
                resultMsg.append("导入游戏数量: " + importedGames + "\n");
                taskService.completeTask(taskId, "导入完成", resultMsg.toString());
                taskService.updateTaskLog(taskId, "导入任务完成，共导入 " + processed + " 个文件，" + importedPlatforms + " 个平台，" + importedGames + " 个游戏");
            }
        } catch (Exception e) {
            logger.error("导入任务失败", e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
            taskService.failTask(taskId, "导入失败", errorMessage);
            taskService.updateTaskLog(taskId, "导入任务失败: " + errorMessage);
        }
    }
}
