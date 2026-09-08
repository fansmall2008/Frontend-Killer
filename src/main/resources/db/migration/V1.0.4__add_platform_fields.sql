-- V1.0.4__add_platform_fields.sql
-- 为媒体下载任务表添加平台关联字段

-- 为 media_download_task 表添加平台ID和平台名称字段
ALTER TABLE media_download_task ADD COLUMN IF NOT EXISTS platform_id BIGINT DEFAULT NULL;
ALTER TABLE media_download_task ADD COLUMN IF NOT EXISTS platform_name VARCHAR(255) DEFAULT NULL;

-- 添加平台ID索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_media_download_task_platform_id ON media_download_task(platform_id);
