package com.gamelist.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.gamelist.model.BackgroundTask;
import com.gamelist.service.TaskService;

@Service
public class TaskServiceImpl implements TaskService {
    private final Map<Long, BackgroundTask> tasks = new ConcurrentHashMap<>();
    private final AtomicLong taskIdGenerator = new AtomicLong(1);
    
    @Override
    public BackgroundTask createTask(String type, String description) {
        BackgroundTask task = new BackgroundTask();
        task.setId(taskIdGenerator.getAndIncrement());
        task.setType(type);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setMessage("任务已创建");
        task.setProcessedItems(0);
        task.setTotalItems(0);
        task.setStartTime(new Date());
        task.setDescription(description);
        task.setLog("" + new Date() + " - 任务已创建\n");
        
        tasks.put(task.getId(), task);
        return task;
    }
    
    @Override
    public void updateTaskProgress(Long taskId, int progress, String message, long processedItems, long totalItems) {
        BackgroundTask task = tasks.get(taskId);
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
        }
    }
    
    @Override
    public void updateTaskLog(Long taskId, String logMessage) {
        BackgroundTask task = tasks.get(taskId);
        if (task != null) {
            String currentLog = task.getLog();
            String newLog = currentLog + "" + new Date() + " - " + logMessage + "\n";
            task.setLog(newLog);
        }
    }
    
    @Override
    public void completeTask(Long taskId, String message, String result) {
        BackgroundTask task = tasks.get(taskId);
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
        }
    }
    
    @Override
    public void failTask(Long taskId, String message, String errorMessage) {
        BackgroundTask task = tasks.get(taskId);
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
        }
    }
    
    @Override
    public List<BackgroundTask> getTasks() {
        return new ArrayList<>(tasks.values());
    }
    
    @Override
    public BackgroundTask getTaskById(Long taskId) {
        return tasks.get(taskId);
    }
    
    @Override
    public void deleteTask(Long taskId) {
        tasks.remove(taskId);
    }
    
    @Override
    public void clearAllTasks() {
        tasks.clear();
    }
}