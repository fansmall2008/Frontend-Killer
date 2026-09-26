-- scrape_task 表新增 media_scope 列
-- 用于 GAME_INFO 任务存储媒体偏好：'*'=全部媒体, 'ss,box-2D'=指定类型, NULL=不刮媒体
ALTER TABLE scrape_task ADD COLUMN IF NOT EXISTS media_scope VARCHAR(512) DEFAULT NULL;
