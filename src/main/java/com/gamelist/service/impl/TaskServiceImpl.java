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
import com.gamelist.service.TaskService;

@Service
public class TaskServiceImpl implements TaskService {

    private final BackgroundTaskMapper taskMapper;
    private final Map<Long, BackgroundTask> taskCache = new ConcurrentHashMap<>();
    private final AtomicLong taskIdGenerator = new AtomicLong(1);
    private volatile boolean initialized = false;
    private final ReentrantLock initLock = new ReentrantLock();

    public TaskServiceImpl(BackgroundTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
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