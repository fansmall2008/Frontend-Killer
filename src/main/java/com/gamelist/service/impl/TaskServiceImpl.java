package com.gamelist.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;

import com.gamelist.mapper.BackgroundTaskMapper;
import com.gamelist.model.BackgroundTask;
import com.gamelist.service.NotificationService;
import com.gamelist.service.TaskService;

@Service
public class TaskServiceImpl implements TaskService {

    private final BackgroundTaskMapper taskMapper;
    private final NotificationService notificationService;
    private final Map<Long, BackgroundTask> taskCache = new ConcurrentHashMap<>();
    private final AtomicLong taskIdGenerator = new AtomicLong(1);
    private volatile boolean initialized = false;
    private final ReentrantLock initLock = new ReentrantLock();

    public TaskServiceImpl(BackgroundTaskMapper taskMapper, NotificationService notificationService) {
        this.taskMapper = taskMapper;
        this.notificationService = notificationService;
        // 延迟初始化，不在构造函数中调用数据库
    }

    private void initializeTaskIdGenerator() {
        if (initialized) {
            return;
        }
        initLock.lock();
        try {
            if (initialized) {
                return;
            }
            try {
                List<BackgroundTask> existingTasks = taskMapper.selectAll();
                if (existingTasks != null && !existingTasks.isEmpty()) {
                    long maxId = existingTasks.stream()
                            .mapToLong(BackgroundTask::getId)
                            .max()
                            .orElse(0);
                    taskIdGenerator.set(maxId + 1);
                }
            } catch (Exception e) {
                // 如果表还不存在，使用默认值1
                // Flyway迁移完成后会自动初始化
            }
            initialized = true;
        } finally {
            initLock.unlock();
        }
    }

    @Override
    public BackgroundTask createTask(String type, String description) {
        initializeTaskIdGenerator();
        BackgroundTask task = new BackgroundTask();
        task.setType(type);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setMessage("任务已创建");
        task.setProcessedItems(0);
        task.setTotalItems(0);
        task.setStartTime(new Date());
        task.setDescription(description);
        task.setLog("" + new Date() + " - 任务已创建\n");

        taskMapper.insert(task);
        taskCache.put(task.getId(), task);
        // 任务创建事件进入通知中心
        try {
            notificationService.send(
                    description != null && !description.isEmpty() ? description : type,
                    "后台任务已创建（" + type + "）", "info", "task");
        } catch (Exception e) {
            // 通知失败不影响任务创建
        }
        return task;
    }

    @Override
    public void updateTaskProgress(Long taskId, int progress, String message, long processedItems, long totalItems) {
        BackgroundTask task = getTaskFromCacheOrDb(taskId);
        if (task != null) {
            task.setProgress(progress);
            task.setMessage(message);
            task.setProcessedItems(processedItems);
            task.setTotalItems(totalItems);
            if (!"RUNNING".equals(task.getStatus())) {
                task.setStatus("RUNNING");
                updateTaskLog(taskId, "任务状态变更为 RUNNING");
            }
            updateTaskLog(taskId, message + " (" + processedItems + "/" + totalItems + ")");
            taskMapper.update(task);
        }
    }

    @Override
    public void updateTaskLog(Long taskId, String logMessage) {
        BackgroundTask task = getTaskFromCacheOrDb(taskId);
        if (task != null) {
            String currentLog = task.getLog();
            String newLog = currentLog + "" + new Date() + " - " + logMessage + "\n";
            task.setLog(newLog);
            taskMapper.update(task);
        }
    }

    @Override
    public void completeTask(Long taskId, String message, String result) {
        BackgroundTask task = getTaskFromCacheOrDb(taskId);
        if (task != null) {
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setMessage(message);
            task.setResult(result);
            task.setEndTime(new Date());
            updateTaskLog(taskId, "任务状态变更为 COMPLETED");
            updateTaskLog(taskId, message);
            if (result != null && !result.isEmpty()) {
                updateTaskLog(taskId, "结果: " + result);
            }
            taskMapper.update(task);
            taskCache.remove(taskId);
            // 任务完成事件进入通知中心
            try {
                notificationService.send(
                        task.getDescription() != null && !task.getDescription().isEmpty() ? task.getDescription() : task.getType(),
                        message, "success", "task");
            } catch (Exception e) {
                // 通知失败不影响任务状态
            }
        }
    }

    @Override
    public void failTask(Long taskId, String message, String errorMessage) {
        BackgroundTask task = getTaskFromCacheOrDb(taskId);
        if (task != null) {
            task.setStatus("FAILED");
            task.setMessage(message);
            task.setErrorMessage(errorMessage);
            task.setEndTime(new Date());
            updateTaskLog(taskId, "任务状态变更为 FAILED");
            updateTaskLog(taskId, message);
            if (errorMessage != null && !errorMessage.isEmpty()) {
                updateTaskLog(taskId, "错误: " + errorMessage);
            }
            taskMapper.update(task);
            taskCache.remove(taskId);
            // 任务失败事件进入通知中心
            try {
                String detail = message;
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    detail = message + "：" + errorMessage;
                }
                notificationService.send(
                        task.getDescription() != null && !task.getDescription().isEmpty() ? task.getDescription() : task.getType(),
                        detail, "error", "task");
            } catch (Exception e) {
                // 通知失败不影响任务状态
            }
        }
    }

    @Override
    public List<BackgroundTask> getTasks() {
        return taskMapper.selectAll();
    }

    @Override
    public BackgroundTask getTaskById(Long taskId) {
        return getTaskFromCacheOrDb(taskId);
    }

    @Override
    public void deleteTask(Long taskId) {
        taskMapper.deleteById(taskId);
        taskCache.remove(taskId);
    }

    @Override
    public void clearAllTasks() {
        taskMapper.deleteAll();
        taskCache.clear();
    }

    private BackgroundTask getTaskFromCacheOrDb(Long taskId) {
        BackgroundTask task = taskCache.get(taskId);
        if (task == null) {
            task = taskMapper.selectById(taskId);
            if (task != null) {
                taskCache.put(taskId, task);
            }
        }
        return task;
    }
}