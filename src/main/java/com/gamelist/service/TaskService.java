package com.gamelist.service;

import java.util.List;

import com.gamelist.model.BackgroundTask;

public interface TaskService {
    /**
     * 创建新任务
     * @param type 任务类型
     * @param description 任务描述
     * @return 创建的任务
     */
    BackgroundTask createTask(String type, String description);
    
    /**
     * 创建可取消的任务
     * @param type 任务类型
     * @param description 任务描述
     * @param cancellable 是否支持用户取消
     * @return 创建的任务
     */
    BackgroundTask createTask(String type, String description, boolean cancellable);
    
    /**
     * 更新任务进度
     * @param taskId 任务ID
     * @param progress 进度百分比
     * @param message 任务消息
     * @param processedItems 已处理项目数
     * @param totalItems 总项目数
     */
    void updateTaskProgress(Long taskId, int progress, String message, long processedItems, long totalItems);
    
    /**
     * 完成任务
     * @param taskId 任务ID
     * @param message 完成消息
     * @param result 任务结果
     */
    void completeTask(Long taskId, String message, String result);

    /**
     * 完成任务，并指定通知类型。
     * <p>
     * 结果全是“跳过”时不该报绿色成功（例如：目标格式没产线、源文件是压缩包），
     * 用 warning 让用户看见原因。
     *
     * @param notificationType success / warning
     */
    void completeTask(Long taskId, String message, String result, String notificationType);
    
    /**
     * 失败任务
     * @param taskId 任务ID
     * @param message 失败消息
     * @param errorMessage 错误详情
     */
    void failTask(Long taskId, String message, String errorMessage);
    
    /**
     * 获取所有任务
     * @return 任务列表
     */
    List<BackgroundTask> getTasks();
    
    /**
     * 根据ID获取任务
     * @param taskId 任务ID
     * @return 任务对象
     */
    BackgroundTask getTaskById(Long taskId);
    
    /**
     * 删除任务（仅允许删除非运行中任务）
     * @param taskId 任务ID
     * @throws IllegalStateException 如果任务正在运行
     */
    void deleteTask(Long taskId);
    
    /**
     * 批量删除任务
     * @param taskIds 任务ID列表
     * @return 成功删除的数量
     */
    int deleteTasks(List<Long> taskIds);
    
    /**
     * 取消任务（将 RUNNING 任务标记为 CANCELLED）
     * @param taskId 任务ID
     * @return 是否成功取消
     */
    boolean cancelTask(Long taskId);
    
    /**
     * 清空所有任务
     */
    void clearAllTasks();
    
    /**
     * 更新任务日志
     * @param taskId 任务ID
     * @param logMessage 日志消息
     */
    void updateTaskLog(Long taskId, String logMessage);
}