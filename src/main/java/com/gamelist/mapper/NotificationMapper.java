package com.gamelist.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.gamelist.model.Notification;

@Mapper
public interface NotificationMapper {
    int insert(Notification notification);
    List<Notification> selectRecent(@Param("limit") int limit);
    int countUnread();
    int markAllRead();
    int deleteAll();
    int deleteOlderThan(@Param("cutoffId") long cutoffId);
}
