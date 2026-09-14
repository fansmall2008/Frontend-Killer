package com.gamelist.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.gamelist.model.BackgroundTask;

@Mapper
public interface BackgroundTaskMapper {
    int insert(BackgroundTask task);
    int update(BackgroundTask task);
    BackgroundTask selectById(Long id);
    List<BackgroundTask> selectAll();
    List<BackgroundTask> selectByType(@Param("type") String type);
    int deleteById(Long id);
    int deleteAll();
    int updateStatus(@Param("id") Long id, @Param("status") String status);
    int updateProgress(@Param("id") Long id, @Param("progress") int progress, @Param("processedItems") long processedItems);
    
    /**
     * 启动时恢复：将 RUNNING 状态的任务标记为 FAILED（处理断电/异常关闭的情况）
     */
    int resetRunningTasks();
}