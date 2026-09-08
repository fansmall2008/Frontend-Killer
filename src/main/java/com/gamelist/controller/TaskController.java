package com.gamelist.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gamelist.mapper.BackgroundTaskMapper;
import com.gamelist.mapper.MediaDownloadTaskMapper;
import com.gamelist.model.BackgroundTask;
import com.gamelist.service.MediaDownloadService;
import com.gamelist.service.ScraperService;
import com.gamelist.service.TaskService;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private BackgroundTaskMapper backgroundTaskMapper;

    @Autowired
    private MediaDownloadTaskMapper mediaDownloadTaskMapper;

    @Autowired
    private MediaDownloadService mediaDownloadService;

    @Autowired
    private ScraperService scraperService;

    @GetMapping
    public List<BackgroundTask> getTasks() {
        return taskService.getTasks();
    }

    @GetMapping("/{id}")
    public BackgroundTask getTask(@PathVariable Long id) {
        return taskService.getTaskById(id);
    }

    @DeleteMapping("/{id}")
    public void deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        mediaDownloadTaskMapper.deleteByTaskId(id);
    }

    @DeleteMapping("/clear")
    public void clearTasks() {
        taskService.clearAllTasks();
    }

    @GetMapping("/{id}/media-tasks")
    public Map<String, Object> getMediaTasks(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();

        long total = mediaDownloadTaskMapper.countTotalByTaskId(id);
        long pending = mediaDownloadTaskMapper.countPendingByTaskId(id);
        long downloading = mediaDownloadTaskMapper.countDownloadingByTaskId(id);
        long completed = mediaDownloadTaskMapper.countCompletedByTaskId(id);
        long failed = mediaDownloadTaskMapper.countFailedByTaskId(id);

        result.put("total", total);
        result.put("pending", pending);
        result.put("downloading", downloading);
        result.put("completed", completed);
        result.put("failed", failed);

        double progress = total > 0 ? ((completed + failed) * 100.0 / total) : 0;
        result.put("progress", progress);

        return result;
    }

    @PostMapping("/{id}/start-media-download")
    public Map<String, Object> startMediaDownload(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();

        BackgroundTask task = backgroundTaskMapper.selectById(id);
        if (task == null) {
            result.put("success", false);
            result.put("message", "任务不存在");
            return result;
        }

        long totalMediaTasks = mediaDownloadTaskMapper.countTotalByTaskId(id);
        if (totalMediaTasks == 0) {
            result.put("success", false);
            result.put("message", "没有媒体任务需要下载");
            return result;
        }

        int maxThreads = scraperService.getUserMaxThreads();
        if (maxThreads <= 0) {
            result.put("success", false);
            result.put("message", "您的账户没有线程可以刮削");
            return result;
        }

        if (mediaDownloadService.isRunning()) {
            result.put("success", false);
            result.put("message", "已有媒体下载任务在运行中");
            return result;
        }

        BackgroundTask mediaTask = taskService.createTask("MEDIA_DOWNLOAD",
            "媒体下载任务 - " + task.getDescription());
        mediaTask.setTotalItems(totalMediaTasks);
        mediaTask.setStatus("RUNNING");
        backgroundTaskMapper.update(mediaTask);

        mediaDownloadService.startMediaDownloadTask(mediaTask.getId(), maxThreads);

        result.put("success", true);
        result.put("message", "媒体下载任务已启动");
        result.put("taskId", mediaTask.getId());
        result.put("totalMediaTasks", totalMediaTasks);

        return result;
    }

    @PostMapping("/stop-media-download")
    public Map<String, Object> stopMediaDownload() {
        Map<String, Object> result = new HashMap<>();

        if (!mediaDownloadService.isRunning()) {
            result.put("success", false);
            result.put("message", "没有正在运行的媒体下载任务");
            return result;
        }

        mediaDownloadService.stopMediaDownloadTask();
        result.put("success", true);
        result.put("message", "已请求停止媒体下载任务");

        return result;
    }

    @PostMapping("/pause-media-download")
    public Map<String, Object> pauseMediaDownload() {
        Map<String, Object> result = new HashMap<>();

        if (!mediaDownloadService.isRunning()) {
            result.put("success", false);
            result.put("message", "没有正在运行的媒体下载任务");
            return result;
        }

        if (mediaDownloadService.isPaused()) {
            result.put("success", false);
            result.put("message", "任务已经是暂停状态");
            return result;
        }

        mediaDownloadService.pauseMediaDownloadTask();
        result.put("success", true);
        result.put("message", "媒体下载任务已暂停");

        return result;
    }

    @PostMapping("/resume-media-download")
    public Map<String, Object> resumeMediaDownload() {
        Map<String, Object> result = new HashMap<>();

        if (!mediaDownloadService.isRunning()) {
            result.put("success", false);
            result.put("message", "没有正在运行的媒体下载任务");
            return result;
        }

        if (!mediaDownloadService.isPaused()) {
            result.put("success", false);
            result.put("message", "任务不在暂停状态");
            return result;
        }

        mediaDownloadService.resumeMediaDownloadTask();
        result.put("success", true);
        result.put("message", "媒体下载任务已恢复");

        return result;
    }

    @GetMapping("/media-download-status")
    public Map<String, Object> getMediaDownloadStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("running", mediaDownloadService.isRunning());
        result.put("paused", mediaDownloadService.isPaused());
        return result;
    }
}
