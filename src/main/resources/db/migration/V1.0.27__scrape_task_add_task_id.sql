-- scrape_task 表新增 task_id 列
-- 关联 background_task.id，便于按后台任务维度清理 scrape_task 记录
ALTER TABLE scrape_task ADD COLUMN IF NOT EXISTS task_id BIGINT DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_scrape_task_task_id ON scrape_task(task_id);
