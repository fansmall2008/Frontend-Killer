-- 为媒体下载任务表添加game_field_name字段，用于关联Game实体的字段
ALTER TABLE media_download_task ADD COLUMN IF NOT EXISTS game_field_name VARCHAR(50) DEFAULT NULL;
