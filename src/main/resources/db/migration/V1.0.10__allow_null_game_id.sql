-- V1.0.10__allow_null_game_id.sql
-- 修改 media_download_task 表的 game_id 字段，允许为 NULL
-- H2 数据库语法

ALTER TABLE media_download_task ALTER COLUMN game_id SET NULL;
