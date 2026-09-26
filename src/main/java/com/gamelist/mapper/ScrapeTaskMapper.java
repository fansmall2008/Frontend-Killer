package com.gamelist.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.gamelist.model.ScrapeTask;

/**
 * 统一刮削任务池 Mapper
 * 支持优先级任务拾取、乐观锁抢占、批量插入等操作
 */
@Mapper
public interface ScrapeTaskMapper {
    
    /**
     * 插入单条任务
     */
    int insert(ScrapeTask task);
    
    /**
     * 批量插入任务
     */
    int batchInsert(@Param("list") List<ScrapeTask> tasks);
    
    /**
     * 按优先级取下一个待处理任务（PENDING 状态）
     * ORDER BY priority ASC, order_index ASC
     */
    ScrapeTask pickNextPendingTask();
    
    /**
     * 乐观锁抢占：只有 status='PENDING' 时才更新为 RUNNING
     * @return 成功返回 1，被其他线程抢走返回 0
     */
    int tryClaimTask(@Param("id") long id);
    
    /**
     * 更新任务状态
     * @return 更新成功返回 1，状态已变更返回 0
     */
    int updateStatus(@Param("id") long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);
    
    /**
     * 更新任务状态（带错误信息）
     */
    int updateStatusWithError(@Param("id") long id, @Param("fromStatus") String fromStatus, 
                              @Param("toStatus") String toStatus, @Param("errorMessage") String errorMessage);
    
    /**
     * 重置所有 RUNNING 任务为 PENDING（应用重启时调用）
     */
    int resetRunningToPending();
    
    /**
     * 按类型和状态统计任务数
     */
    int countByTypeAndStatus(@Param("type") String type, @Param("status") String status);
    
    /**
     * 按状态统计任务数（不限类型）
     */
    int countByStatus(@Param("status") String status);
    
    /**
     * 按平台ID和状态统计任务数
     */
    int countByPlatformIdAndStatus(@Param("platformId") long platformId, @Param("status") String status);
    
    /**
     * 停止指定平台的所有 PENDING 任务
     */
    int stopPendingTasksByPlatformId(@Param("platformId") long platformId);
    
    /**
     * 删除指定平台的所有任务
     */
    int deleteByPlatformId(@Param("platformId") long platformId);
    
    /**
     * 按游戏ID查找任务
     */
    List<ScrapeTask> selectByGameId(@Param("gameId") long gameId);
    
    /**
     * 获取下一个 order_index 值（用于批量插入时排序）
     */
    Long getNextOrderIndex();
    
    /**
     * 查询失败的任务（关联游戏名称和平台名称）
     * 按 updated_at DESC 排序，最近的失败在前
     */
    List<java.util.Map<String, Object>> selectFailedTasks(
        @Param("taskType") String taskType, @Param("limit") int limit);
    
    /**
     * 将失败的任务重置为 PENDING（重试）
     */
    int retryFailedTask(@Param("id") long id);
}
