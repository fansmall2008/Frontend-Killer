package com.gamelist.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.model.BackgroundTask;
import com.gamelist.service.TaskService;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    
    @Autowired
    private TaskService taskService;
    
    /**
     * 获取所有任务
     */
    @GetMapping
    public List<BackgroundTask> getTasks() {
        return taskService.getTasks();
    }
    
    /**
     * 根据ID获取任务
     */
    @GetMapping("/{id}")
    public BackgroundTask getTask(@PathVariable Long id) {
        return taskService.getTaskById(id);
    }
    
    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public void deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
    }
    
    /**
     * 清空所有任务
     */
    @DeleteMapping("/clear")
    public void clearTasks() {
        taskService.clearAllTasks();
    }
}