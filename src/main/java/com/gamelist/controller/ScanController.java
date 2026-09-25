package com.gamelist.controller;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.gamelist.service.impl.AsyncImportService;

@RestController
@RequestMapping("/api/scan")
public class ScanController {
    private static final Logger logger = LoggerFactory.getLogger(ScanController.class);
    
    @Autowired
    private GameService gameService;
    
    @Autowired
    private TaskService taskService;

    @Autowired
    private AsyncImportService asyncImportService;
    
    /**
     * 扫描参数
     */
    public static class ScanRequest {
        private String path;
        private List<Integer> levels; // 勾选的扫描层级集合，第1层为根目录本身
        private String importMethod; // template 或 noDataFile
        private String importTemplate; // 导入模板文件名
        private boolean noDataFile; // 无数据文件导入模式
        private String fileExtensions; // 文件扩展名（逗号分隔）
        private String scraperSystemId; // 选中的 scraper 系统 ID
        private boolean enableMediaDiscovery = true; // 是否执行 mediaDiscovery 规则扫描

        public String getPath() {
            return path;
        }
        public void setPath(String path) {
            this.path = path;
        }
        public List<Integer> getLevels() {
            return levels;
        }
        public void setLevels(List<Integer> levels) {
            this.levels = levels;
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
        public String getScraperSystemId() {
            return scraperSystemId;
        }
        public void setScraperSystemId(String scraperSystemId) {
            this.scraperSystemId = scraperSystemId;
        }
        public boolean isEnableMediaDiscovery() {
            return enableMediaDiscovery;
        }
        public void setEnableMediaDiscovery(boolean enableMediaDiscovery) {
            this.enableMediaDiscovery = enableMediaDiscovery;
        }
    }

    /**
     * 导入参数
     */
    public static class ImportRequest {
        private List<String> files;
        private String type; // gamelist.xml 或 metadata.pegasus.txt 或 none
        private int threadCount; // 导入线程数
        private String importMethod; // template 或 noDataFile
        private String importTemplate; // 导入模板文件名
        private String scanPath; // 扫描路径
        private boolean noDataFile; // 无数据文件导入模式
        private String fileExtensions; // 文件扩展名（逗号分隔）
        private String scraperSystemId; // 选中的 scraper 系统 ID
        private boolean enableMediaDiscovery = true; // 是否执行 mediaDiscovery 规则扫描
        private List<Integer> levels; // 勾选的扫描层级集合（noDataFile 模式使用）
        private Map<String, String> templateVariables; // 模板声明变量的用户填写值

        public Map<String, String> getTemplateVariables() {
            return templateVariables;
        }
        public void setTemplateVariables(Map<String, String> templateVariables) {
            this.templateVariables = templateVariables;
        }

        public List<Integer> getLevels() {
            return levels;
        }
        public void setLevels(List<Integer> levels) {
            this.levels = levels;
        }

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
        public String getScraperSystemId() {
            return scraperSystemId;
        }
        public void setScraperSystemId(String scraperSystemId) {
            this.scraperSystemId = scraperSystemId;
        }
        public boolean isEnableMediaDiscovery() {
            return enableMediaDiscovery;
        }
        public void setEnableMediaDiscovery(boolean enableMediaDiscovery) {
            this.enableMediaDiscovery = enableMediaDiscovery;
        }
    }
    
    /**
     * 扫描文件系统，返回元数据文件列表
     */
    @PostMapping("/scan")
    public ScanResult scan(@RequestBody ScanRequest request) {
        logger.info("开始扫描，路径: {}, 层级: {}, 导入方式: {}, 模板: {}", 
                    request.getPath(), request.getLevels(), request.getImportMethod(), request.getImportTemplate());
        
        ScanResult result = new ScanResult();
        List<ScanResult.Detail> details = new ArrayList<>();
        
        File rootDir = new File(request.getPath());
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            result.setSuccess(false);
            result.setMessage("指定的路径不存在或不是目录");
            result.setFoundFiles(0);
            return result;
        }
        
        // 确定要扫描的数据文件类型
        String targetDataFile = null;
        if ("template".equals(request.getImportMethod()) && request.getImportTemplate() != null && !request.getImportTemplate().isEmpty()) {
            // 尝试加载 v3 模板
            try {
                File v3TemplateFile = new File(com.gamelist.util.PathUtil.getRulesPath() + "/import/" + request.getImportTemplate());
                if (v3TemplateFile.exists()) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> templateData = mapper.readValue(v3TemplateFile, java.util.Map.class);
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> v3Info = (java.util.Map<String, Object>) templateData.get("templateInfo");
                    if (v3Info != null && v3Info.containsKey("version") && 
                            Integer.parseInt(v3Info.get("version").toString()) == 3) {
                        targetDataFile = (String) v3Info.get("dataFile");
                        logger.info("使用 v3 模板 {}，指定的数据文件类型: {}", request.getImportTemplate(), targetDataFile);
                    }
                }
            } catch (Exception e) {
                logger.debug("v3 模板检测失败: {}", e.getMessage());
            }
        }
        
        // 解析勾选的层级集合（第1层为根目录本身），默认至少扫第1层
        Set<Integer> levelSet = new HashSet<>();
        if (request.getLevels() != null && !request.getLevels().isEmpty()) {
            levelSet.addAll(request.getLevels());
        } else {
            levelSet.add(1);
        }
        int maxLevel = Collections.max(levelSet);

        Map<String, List<File>> foundFiles;
        if (targetDataFile != null) {
            // 根据模板指定的数据文件类型进行扫描
            foundFiles = scanSpecificFileType(rootDir, levelSet, maxLevel, targetDataFile);
        } else {
            // 同时扫描两种文件类型（默认行为）
            foundFiles = scanBothFileTypes(rootDir, levelSet, maxLevel);
        }
        
        int totalFiles = 0;
        for (List<File> files : foundFiles.values()) {
            totalFiles += files.size();
        }
        result.setFoundFiles(totalFiles);
        
        // 添加找到的文件
        for (Map.Entry<String, List<File>> entry : foundFiles.entrySet()) {
            String fileType = entry.getKey();
            for (File file : entry.getValue()) {
                ScanResult.Detail detail = new ScanResult.Detail();
                detail.setFilePath(file.getAbsolutePath());
                detail.setType(fileType);
                detail.setMessage("找到" + fileType + "文件");
                detail.setSuccess(true);
                details.add(detail);
            }
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
    public org.springframework.http.ResponseEntity<Object> importFiles(@RequestBody ImportRequest request) {
        // 校验模板声明的必填变量
        String missingErr = validateImportTemplateVariables(request.getImportTemplate(), request.getTemplateVariables());
        if (missingErr != null) {
            return org.springframework.http.ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", missingErr
            ));
        }

        BackgroundTask task = taskService.createTask("IMPORT", "导入游戏数据");

        int threadCount = request.getThreadCount();
        if (threadCount <= 0) {
            threadCount = 4;
        } else if (threadCount > 10) {
            threadCount = 10;
        }

        // 解析 scraperSystemId
        Long scraperSystemIdLong = null;
        if (request.getScraperSystemId() != null && !request.getScraperSystemId().isEmpty()) {
            try {
                scraperSystemIdLong = Long.parseLong(request.getScraperSystemId());
            } catch (NumberFormatException e) {
                logger.warn("Invalid scraperSystemId: " + request.getScraperSystemId());
            }
        }

        // 异步执行导入（委托给独立 Bean 以使 @Async 生效）
        asyncImportService.executeImport(task.getId(), request.getFiles(), request.getType(), threadCount,
                         request.getImportMethod(), request.getImportTemplate(), request.getScanPath(),
                         request.isNoDataFile(), request.getFileExtensions(), scraperSystemIdLong,
                         request.isEnableMediaDiscovery(), request.getLevels(), request.getTemplateVariables());

        return org.springframework.http.ResponseEntity.ok(task);
    }

    /**
     * 校验导入模板声明的必填变量是否已由用户填写。
     * @return 缺失时返回错误信息，否则 null
     */
    private String validateImportTemplateVariables(String importTemplate, Map<String, String> userVars) {
        if (importTemplate == null || importTemplate.isEmpty()) return null;
        try {
            java.io.File tplFile = new java.io.File(com.gamelist.util.PathUtil.getRulesPath() + "/import/" + importTemplate);
            if (!tplFile.exists()) return null;
            com.gamelist.model.TemplateV3 v3 = com.gamelist.model.TemplateV3.loadFromFile(tplFile);
            if (v3 == null) return null;
            List<String> missing = new ArrayList<>();
            for (com.gamelist.model.TemplateV3.TemplateVariable var : v3.getValidVariables()) {
                if (!var.isRequired()) continue;
                String value = userVars != null ? userVars.get(var.getName()) : null;
                if (value == null || value.trim().isEmpty()) {
                    missing.add(var.getLabel() != null && !var.getLabel().isEmpty() ? var.getLabel() : var.getName());
                }
            }
            if (!missing.isEmpty()) {
                return "请先填写必需的模板变量: " + String.join(", ", missing);
            }
        } catch (Exception e) {
            logger.warn("导入模板变量校验失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 异步导入文件
     */
    @Async
    public Future<Void> importFilesAsync(Long taskId, List<String> files, String type, int threadCount,
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
                return new AsyncResult<>(null);
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
                    // 根据文件扩展名动态选择解析方式
                    if (filePath.endsWith("gamelist.xml")) {
                        // 导入gamelist.xml
                        ImportStatistics stats = gameService.importGamesFromXml(filePath, importMethod, importTemplate, false, threadCount, scraperSystemId, enableMediaDiscovery);
                        importedPlatforms += stats.getImportedPlatforms();
                        importedGames += stats.getImportedGames();
                    } else if (filePath.endsWith("metadata.pegasus.txt")) {
                        // 导入metadata.pegasus.txt
                        ImportStatistics stats = gameService.importGamesFromPegasusMetadata(filePath, importMethod, importTemplate, false, threadCount, scraperSystemId, enableMediaDiscovery);
                        importedPlatforms += stats.getImportedPlatforms();
                        importedGames += stats.getImportedGames();
                    } else {
                        // 模板驱动的数据文件分发：按 v3 导入模板的 templateInfo.dataFile 模式匹配
                        // （如 "*.lpl" → retroarch-v3.json），替代扩展名硬编码；
                        // 用户显式选择的模板优先，否则自动扫描匹配
                        String matchedTemplate = (importTemplate != null && !importTemplate.isEmpty())
                                ? importTemplate : gameService.findImportTemplateForFile(file.getName());
                        if (matchedTemplate != null && !matchedTemplate.isEmpty()) {
                            ImportStatistics stats = gameService.importGamesFromTemplate(filePath, matchedTemplate, threadCount, scraperSystemId, enableMediaDiscovery);
                            importedPlatforms += stats.getImportedPlatforms();
                            importedGames += stats.getImportedGames();
                        } else {
                            String errorMsg = "不支持的文件类型: " + filePath;
                            logger.warn(errorMsg);
                            skippedFiles.append(errorMsg).append("\n");
                            taskService.updateTaskLog(taskId, errorMsg);
                            continue;
                        }
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
     * 根据指定的数据文件类型扫描
     * @param directory 根目录
     * @param maxDepth 最大扫描深度
     * @param dataFile 指定的数据文件名（如 "gamelist.xml" 或 "metadata.pegasus.txt"）
     */
    private Map<String, List<File>> scanSpecificFileType(File directory, Set<Integer> levels, int maxLevel, String dataFile) {
        Map<String, List<File>> resultMap = new java.util.HashMap<>();
        resultMap.put(dataFile, new ArrayList<>());
        
        scanSpecificFileTypeRecursive(directory, resultMap, 1, levels, maxLevel, dataFile);
        return resultMap;
    }
    
    /**
     * 递归扫描目录，只检查指定类型的文件
     */
    private void scanSpecificFileTypeRecursive(File directory, Map<String, List<File>> resultMap, int currentLevel, Set<Integer> levels, int maxLevel, String dataFile) {
        // 仅在当前层被勾选时收集目标文件
        if (levels.contains(currentLevel)) {
            if (dataFile.contains("*")) {
                // 支持通配符模式匹配（如 "*.xml"）
                String extension = dataFile.replace("*.", "");
                File[] matchingFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith("." + extension));
                if (matchingFiles != null) {
                    for (File file : matchingFiles) {
                        if (file.isFile()) {
                            resultMap.get(dataFile).add(file);
                            logger.info("找到{}: {}", dataFile, file.getAbsolutePath());
                        }
                    }
                }
            } else {
                // 精确文件名匹配（如 "gamelist.xml"）
                File targetFile = new File(directory, dataFile);
                if (targetFile.exists() && targetFile.isFile()) {
                    resultMap.get(dataFile).add(targetFile);
                    logger.info("找到{}: {}", dataFile, targetFile.getAbsolutePath());
                }
            }
        }
        
        // 未达最深勾选层时继续下钻（穿过未勾选的中间层）
        if (currentLevel < maxLevel) {
            File[] subDirectories = directory.listFiles(File::isDirectory);
            if (subDirectories != null && subDirectories.length > 0) {
                Arrays.stream(subDirectories).forEach(subDir -> {
                    scanSpecificFileTypeRecursive(subDir, resultMap, currentLevel + 1, levels, maxLevel, dataFile);
                });
            }
        }
    }
    
    /**
     * 同时扫描gamelist.xml和metadata.pegasus.txt文件
     */
    private Map<String, List<File>> scanBothFileTypes(File directory, Set<Integer> levels, int maxLevel) {
        Map<String, List<File>> resultMap = new java.util.HashMap<>();
        resultMap.put("gamelist.xml", new ArrayList<>());
        resultMap.put("metadata.pegasus.txt", new ArrayList<>());
        
        scanBothFileTypesRecursive(directory, resultMap, 1, levels, maxLevel);
        return resultMap;
    }
    
    /**
     * 递归扫描目录，同时检查两种文件类型
     */
    private void scanBothFileTypesRecursive(File directory, Map<String, List<File>> resultMap, int currentLevel, Set<Integer> levels, int maxLevel) {
        // 仅在当前层被勾选时收集数据文件
        if (levels.contains(currentLevel)) {
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
        }
        
        // 未达最深勾选层时继续下钻（穿过未勾选的中间层）
        if (currentLevel < maxLevel) {
            File[] subDirectories = directory.listFiles(File::isDirectory);
            if (subDirectories != null && subDirectories.length > 0) {
                for (File subDir : subDirectories) {
                    scanBothFileTypesRecursive(subDir, resultMap, currentLevel + 1, levels, maxLevel);
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