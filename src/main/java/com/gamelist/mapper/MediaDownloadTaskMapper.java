package com.gamelist.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.gamelist.model.MediaDownloadTask;

@Mapper
public interface MediaDownloadTaskMapper {
    int insert(MediaDownloadTask task);
    int insertBatch(List<MediaDownloadTask> tasks);
    MediaDownloadTask selectById(Long id);
    List<MediaDownloadTask> selectByTaskId(Long taskId);
    List<MediaDownloadTask> selectPendingTasks(@Param("limit") int limit);
    List<MediaDownloadTask> selectPendingTasksByTaskId(@Param("taskId") Long taskId, @Param("limit") int limit);
    int updateStatus(MediaDownloadTask task);
    int updateStatusById(@Param("id") Long id, @Param("status") String status, @Param("errorMessage") String errorMessage);
    int countByTaskId(Long taskId);
    int countByTaskIdAndStatus(@Param("taskId") Long taskId, @Param("status") String status);
    int deleteByTaskId(Long taskId);
    long countTotalByTaskId(Long taskId);
    long countCompletedByTaskId(Long taskId);
    long countFailedByTaskId(Long taskId);
    long countPendingByTaskId(Long taskId);
    long countDownloadingByTaskId(Long taskId);
    
    /**
     * 选择一个待下载的任务（用于多线程并发下载）
     */
    MediaDownloadTask selectOnePendingTask(@Param("taskId") Long taskId);
    
    /**
     * 按平台ID选择一个待下载任务（不限制 taskId，避免不同批次任务互相找不到）
     */
    MediaDownloadTask selectOnePendingTaskByPlatformId(@Param("platformId") Long platformId);
    
    /**
     * 尝试更新任务状态（乐观锁方式，防止重复下载）
     * @return 更新成功返回1，失败返回0
     */
    int tryUpdateStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);
    
    List<MediaDownloadTask> selectByPlatformId(@Param("platformId") Long platformId);
    List<MediaDownloadTask> selectPendingTasksByPlatformId(@Param("platformId") Long platformId, @Param("limit") int limit);
    List<MediaDownloadTask> selectPendingAndNotStoppedByPlatformId(@Param("platformId") Long platformId, @Param("limit") int limit);
    int deleteByPlatformId(Long platformId);
    int deleteById(Long id);
    int deleteBatch(@Param("ids") List<Long> ids);
    long countByPlatformId(Long platformId);
    long countPendingByPlatformId(Long platformId);
    long countDownloadingByPlatformId(Long platformId);
    long countCompletedByPlatformId(Long platformId);
    long countFailedByPlatformId(Long platformId);
    long countStoppedByPlatformId(Long platformId);
    int updateStatusByPlatformId(@Param("platformId") Long platformId, @Param("status") String status);
    int updateStoppedToPendingByPlatformId(@Param("platformId") Long platformId);
    int updateFailedToPendingByPlatformId(@Param("platformId") Long platformId);
    int updateStoppedToPendingByTaskId(@Param("taskId") Long taskId);
    
    /**
     * 启动时恢复：将 DOWNLOADING 状态的任务重置为 PENDING（处理断电/异常关闭的情况）
     */
    int updateDownloadingToPending();
    
    List<MediaDownloadTask> selectDownloadingByPlatformId(Long platformId);
    List<Long> selectDistinctPlatformIds();

    /**
     * 一次性汇总所有平台各状态的任务数量，返回列：platform_id, status, cnt
     * 用于替代逐平台 6 次 COUNT 查询，显著降低刷新时的数据库压力。
     */
    List<Map<String, Object>> selectPlatformStatusSummary();
    
    /**
     * 按游戏ID查询媒体下载任务（删除游戏前需先调用）
     */
    List<MediaDownloadTask> selectByGameId(@Param("gameId") Long gameId);
    
    /**
     * 按游戏ID删除媒体下载任务（删除游戏前需先调用）
     */
    int deleteByGameId(@Param("gameId") Long gameId);
    
    /**
     * 批量拾取待处理的媒体下载任务（按优先级和排序索引）
     */
    List<MediaDownloadTask> pickNextPendingMediaTasks(@Param("limit") int limit);
    
    /**
     * 乐观锁认领媒体下载任务：PENDING → DOWNLOADING
     * @return 1=成功, 0=已被其他线程认领
     */
    int tryClaimMediaTask(@Param("id") Long id);
    
    /**
     * 将指定游戏的 FAILED 状态且 download_url 非空的媒体任务重置为 PENDING。
     * 用于复用已存储的 SS 媒体 URL，避免重新调用 jeuInfos API。
     * @return 重置的任务数量
     */
    int resetFailedTasksToPending(@Param("gameId") Long gameId);
}