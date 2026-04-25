package com.gamelist.controller;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.model.BackgroundTask;
import com.gamelist.model.ImportStatistics;
import com.gamelist.model.ScanResult;
import com.gamelist.service.GameService;
import com.gamelist.service.TaskService;

@RestController
@RequestMapping("/api/scan")
public class ScanController {
    private static final Logger logger = LoggerFactory.getLogger(ScanController.class);
    
    @Autowired
    private GameService gameService;
    
    @Autowired
    private TaskService taskService;
    
    /**
     * 扫描参数
     */
    public static class ScanRequest {
        private String path;
        private int depth; // -1: 递归所有子目录, 0: 当前目录, 1-3: 指定深度
        private String importMethod; // traditional 或 template 或 noDataFile
        private String importTemplate; // 导入模板文件名
        private boolean noDataFile; // 无数据文件导入模式
        private String fileExtensions; // 文件扩展名（逗号分隔）

        public String getPath() {
            return path;
        }
        public void setPath(String path) {
            this.path = path;
        }
        public int getDepth() {
            return depth;
        }
        public void setDepth(int depth) {
            this.depth = depth;
        }
        public String getImportMethod() {
            return importMethod;
        }
        public void setImportMethod(String importMethod) {
            this.importMethod = importMethod;
        }
        public String getImportTemplate() {
            return importTemplate;
        }
        public void setImportTemplate(String importTemplate) {
            this.importTemplate = importTemplate;
        }
        public boolean isNoDataFile() {
            return noDataFile;
        }
        public void setNoDataFile(boolean noDataFile) {
            this.noDataFile = noDataFile;
        }
        public String getFileExtensions() {
            return fileExtensions;
        }
        public void setFileExtensions(String fileExtensions) {
            this.fileExtensions = fileExtensions;
        }
    }

    /**
     * 导入参数
     */
    public static class ImportRequest {
        private List<String> files;
        private String type; // gamelist.xml 或 metadata.pegasus.txt 或 none
        private boolean metadataOnly; // 只使用数据文件标签，不进行自动匹配
        private int threadCount; // 导入线程数
        private String importMethod; // traditional 或 template 或 noDataFile
        private String importTemplate; // 导入模板文件名
        private String scanPath; // 扫描路径
        private boolean noDataFile; // 无数据文件导入模式
        private String fileExtensions; // 文件扩展名（逗号分隔）

        public List<String> getFiles() {
            return files;
        }
        public void setFiles(List<String> files) {
            this.files = files;
        }
        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }
        public boolean isMetadataOnly() {
            return metadataOnly;
        }
        public void setMetadataOnly(boolean metadataOnly) {
            this.metadataOnly = metadataOnly;
        }
        public int getThreadCount() {
            return threadCount;
        }
        public void setThreadCount(int threadCount) {
            this.threadCount = threadCount;
        }
        public String getImportMethod() {
            return importMethod;
        }
        public void setImportMethod(String importMethod) {
            this.importMethod = importMethod;
        }
        public String getImportTemplate() {
            return importTemplate;
        }
        public void setImportTemplate(String importTemplate) {
            this.importTemplate = importTemplate;
        }
        public String getScanPath() {
            return scanPath;
        }
        public void setScanPath(String scanPath) {
            this.scanPath = scanPath;
        }
        public boolean isNoDataFile() {
            return noDataFile;
        }
        public void setNoDataFile(boolean noDataFile) {
            this.noDataFile = noDataFile;
        }
        public String getFileExtensions() {
            return fileExtensions;
        }
        public void setFileExtensions(String fileExtensions) {
            this.fileExtensions = fileExtensions;
        }
    }
    
    /**
     * 扫描文件系统，返回元数据文件列表
     */
    @PostMapping("/scan")
    public ScanResult scan(@RequestBody ScanRequest request) {
        logger.info("开始扫描，路径: {}, 深度: {}", request.getPath(), request.getDepth());
        
        ScanResult result = new ScanResult();
        List<ScanResult.Detail> details = new ArrayList<>();
        
        File rootDir = new File(request.getPath());
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            result.setSuccess(false);
            result.setMessage("指定的路径不存在或不是目录");
            result.setFoundFiles(0);
            return result;
        }
        
        // 同时扫描两种文件类型
        Map<String, List<File>> foundFiles = scanBothFileTypes(rootDir, request.getDepth());
        
        int totalFiles = foundFiles.get("gamelist.xml").size() + foundFiles.get("metadata.pegasus.txt").size();
        result.setFoundFiles(totalFiles);
        
        // 添加gamelist.xml文件
        for (File file : foundFiles.get("gamelist.xml")) {
            ScanResult.Detail detail = new ScanResult.Detail();
            detail.setFilePath(file.getAbsolutePath());
            detail.setType("gamelist.xml");
            detail.setMessage("找到gamelist.xml文件");
            detail.setSuccess(true);
            details.add(detail);
        }
        
        // 添加metadata.pegasus.txt文件
        for (File file : foundFiles.get("metadata.pegasus.txt")) {
            ScanResult.Detail detail = new ScanResult.Detail();
            detail.setFilePath(file.getAbsolutePath());
            detail.setType("metadata.pegasus.txt");
            detail.setMessage("找到metadata.pegasus.txt文件");
            detail.setSuccess(true);
            details.add(detail);
        }
        
        result.setDetails(details);
        result.setSuccess(true);
        result.setMessage("扫描完成");
        
        logger.info("扫描完成，找到 {} 个文件", totalFiles);
        return result;
    }
    
    /**
     * 异步执行导入任务
     */
    @PostMapping("/import")
    public BackgroundTask importFiles(@RequestBody ImportRequest request) {
        BackgroundTask task = taskService.createTask("IMPORT", "导入游戏数据");

        int threadCount = request.getThreadCount();
        if (threadCount <= 0) {
            threadCount = 4;
        } else if (threadCount > 10) {
            threadCount = 10;
        }

        // 异步执行导入
        importFilesAsync(task.getId(), request.getFiles(), request.getType(), request.isMetadataOnly(), threadCount,
                         request.getImportMethod(), request.getImportTemplate(), request.getScanPath(),
                         request.isNoDataFile(), request.getFileExtensions());

        return task;
    }

    /**
     * 异步导入文件
     */
    @Async
    public Future<Void> importFilesAsync(Long taskId, List<String> files, String type, boolean metadataOnly, int threadCount,
                                         String importMethod, String importTemplate, String scanPath,
                                         boolean noDataFile, String fileExtensions) {
        try {
            if (noDataFile) {
                taskService.updateTaskLog(taskId, "使用无数据文件导入模式");
                taskService.updateTaskLog(taskId, "扫描路径：" + scanPath + "，扩展名：" + fileExtensions);
                taskService.updateTaskLog(taskId, "模板：" + importTemplate);

                ImportStatistics stats = gameService.importGamesFromFileScan(scanPath, fileExtensions, importTemplate, threadCount, taskId);
                taskService.updateTaskLog(taskId, "无数据文件导入完成，共导入 " + stats.getImportedGames() + " 个游戏");

                String resultMsg = "成功导入 " + stats.getImportedGames() + " 个游戏\n";
                resultMsg += "导入平台数量: " + stats.getImportedPlatforms() + "\n";
                resultMsg += "导入游戏数量: " + stats.getImportedGames() + "\n";
                taskService.completeTask(taskId, "导入完成", resultMsg);
                return new AsyncResult<>(null);
            }

            boolean effectiveMetadataOnly = metadataOnly;

            if ("template".equals(importMethod)) {
                taskService.updateTaskLog(taskId, "使用模板模式，忽略metadataOnly参数的值");
                effectiveMetadataOnly = false;
            }

            taskService.updateTaskProgress(taskId, 0, "开始导入", 0, files.size() > 0 ? files.size() : 1);
            taskService.updateTaskLog(taskId, "开始导入任务，共 " + files.size() + " 个文件，线程数：" + threadCount + "，metadataOnly：" + effectiveMetadataOnly);
            taskService.updateTaskLog(taskId, "导入方式：" + importMethod + "，模板：" + importTemplate);

            int processed = 0;
            int importedPlatforms = 0;
            int importedGames = 0;
            StringBuilder skippedFiles = new StringBuilder();

            // 有数据文件导入
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

                    // 导入文件并收集统计信息
                    if ("gamelist.xml".equals(type)) {
                        // 导入gamelist.xml
                        ImportStatistics stats = gameService.importGamesFromXml(filePath, importMethod, importTemplate, effectiveMetadataOnly, threadCount);
                        importedPlatforms += stats.getImportedPlatforms();
                        importedGames += stats.getImportedGames();
                    } else if ("metadata.pegasus.txt".equals(type)) {
                        // 导入metadata.pegasus.txt
                        ImportStatistics stats = gameService.importGamesFromPegasusMetadata(filePath, importMethod, importTemplate, effectiveMetadataOnly, threadCount);
                        importedPlatforms += stats.getImportedPlatforms();
                        importedGames += stats.getImportedGames();
                    }

                    processed++;
                    int progress = (int) ((double) processed / files.size() * 100);
                    taskService.updateTaskProgress(taskId, progress, "导入中...", processed, files.size());
                    taskService.updateTaskLog(taskId, "成功导入文件: " + filePath);
                } catch (Exception e) {
                        String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
                        String errorMsg = "导入文件失败: " + filePath + "，原因: " + errorMessage;
                        logger.error(errorMsg, e);
                        // 添加详细的异常堆栈信息到日志
                        try (java.io.StringWriter sw = new java.io.StringWriter();
                             java.io.PrintWriter pw = new java.io.PrintWriter(sw)) {
                            e.printStackTrace(pw);
                            String stackTrace = sw.toString();
                            logger.error("异常堆栈信息: {}", stackTrace);
                            taskService.updateTaskLog(taskId, errorMsg);
                            taskService.updateTaskLog(taskId, "详细错误: " + stackTrace.substring(0, Math.min(stackTrace.length(), 1000)));
                        }
                        skippedFiles.append(errorMsg).append("\n");
                    }
            }
            
            // 构建详细的完成消息
            StringBuilder resultMsg = new StringBuilder();
            int totalFiles = files.size() > 0 ? files.size() : 1;
            
            if (processed == 0 && totalFiles > 0) {
                // 所有文件都导入失败
                resultMsg.append("所有文件导入失败\n");
                resultMsg.append("未导入文件: \n").append(skippedFiles.toString());
                taskService.completeTask(taskId, "导入完成", resultMsg.toString());
                taskService.updateTaskLog(taskId, "导入任务完成，所有文件导入失败");
            } else if (processed < totalFiles) {
                // 部分文件导入失败
                resultMsg.append("成功导入 " + processed + " 个文件\n");
                resultMsg.append("导入平台数量: " + importedPlatforms + "\n");
                resultMsg.append("导入游戏数量: " + importedGames + "\n");
                resultMsg.append("未导入文件: \n").append(skippedFiles.toString());
                taskService.completeTask(taskId, "导入完成", resultMsg.toString());
                taskService.updateTaskLog(taskId, "导入任务完成，共导入 " + processed + " 个文件，" + importedPlatforms + " 个平台，" + importedGames + " 个游戏，" + (totalFiles - processed) + " 个文件导入失败");
            } else {
                // 所有文件导入成功
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
        
        return new AsyncResult<>(null);
    }
    
    /**
     * 同时扫描gamelist.xml和metadata.pegasus.txt文件
     */
    private Map<String, List<File>> scanBothFileTypes(File directory, int maxDepth) {
        Map<String, List<File>> resultMap = new java.util.HashMap<>();
        resultMap.put("gamelist.xml", new ArrayList<>());
        resultMap.put("metadata.pegasus.txt", new ArrayList<>());
        
        scanBothFileTypesRecursive(directory, resultMap, 0, maxDepth);
        return resultMap;
    }
    
    /**
     * 递归扫描目录，同时检查两种文件类型
     */
    private void scanBothFileTypesRecursive(File directory, Map<String, List<File>> resultMap, int currentDepth, int maxDepth) {
        // 检查深度限制
        if (maxDepth != -1 && currentDepth > maxDepth) {
            return;
        }
        
        // 同时检查两种文件类型
        File gamelistFile = new File(directory, "gamelist.xml");
        if (gamelistFile.exists() && gamelistFile.isFile()) {
            resultMap.get("gamelist.xml").add(gamelistFile);
            logger.info("找到gamelist.xml: {}", gamelistFile.getAbsolutePath());
        }
        
        File metadataFile = new File(directory, "metadata.pegasus.txt");
        if (metadataFile.exists() && metadataFile.isFile()) {
            resultMap.get("metadata.pegasus.txt").add(metadataFile);
            logger.info("找到metadata.pegasus.txt: {}", metadataFile.getAbsolutePath());
        }
        
        // 遍历子目录
        if (maxDepth == -1 || currentDepth < maxDepth) {
            File[] subDirectories = directory.listFiles(File::isDirectory);
            if (subDirectories != null && subDirectories.length > 0) {
                for (File subDir : subDirectories) {
                    scanBothFileTypesRecursive(subDir, resultMap, currentDepth + 1, maxDepth);
                }
            }
        }
    }
    
    /**
     * 浏览目录请求参数
     */
    public static class BrowseRequest {
        private String path;
        
        public String getPath() {
            return path;
        }
        public void setPath(String path) {
            this.path = path;
        }
    }
    
    /**
     * 浏览目录响应
     */
    public static class BrowseResponse {
        private boolean success;
        private String message;
        private List<String> directories;
        
        public BrowseResponse(boolean success, String message, List<String> directories) {
            this.success = success;
            this.message = message;
            this.directories = directories;
        }
        
        public boolean isSuccess() {
            return success;
        }
        public void setSuccess(boolean success) {
            this.success = success;
        }
        public String getMessage() {
            return message;
        }
        public void setMessage(String message) {
            this.message = message;
        }
        public List<String> getDirectories() {
            return directories;
        }
        public void setDirectories(List<String> directories) {
            this.directories = directories;
        }
    }
    
    /**
     * 浏览目录，返回子目录列表
     */
    @PostMapping("/browse")
    public BrowseResponse browse(@RequestBody BrowseRequest request) {
        logger.info("开始浏览目录，路径: {}", request.getPath());
        
        File directory = new File(request.getPath());
        if (!directory.exists() || !directory.isDirectory()) {
            return new BrowseResponse(false, "指定的路径不存在或不是目录", null);
        }
        
        List<String> directories = new ArrayList<>();
        File[] subDirectories = directory.listFiles(File::isDirectory);
        if (subDirectories != null && subDirectories.length > 0) {
            for (File subDir : subDirectories) {
                directories.add(subDir.getAbsolutePath());
            }
        }
        
        logger.info("浏览完成，找到 {} 个子目录", directories.size());
        return new BrowseResponse(true, "浏览成功", directories);
    }
}