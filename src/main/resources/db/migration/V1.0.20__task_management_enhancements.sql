-- 任务管理功能增强：添加取消支持和新增状态

-- 1. 为 background_task 表添加 cancellable 标志
ALTER TABLE background_task ADD COLUMN IF NOT EXISTS cancellable BOOLEAN DEFAULT FALSE;

-- 2. 更新现有导入/导出任务为可取消（可选，仅对新任务生效）
-- UPDATE background_task SET cancellable = TRUE WHERE type IN ('import', 'export');

-- 3. 添加索引优化查询性能
CREATE INDEX IF NOT EXISTS idx_background_task_status ON background_task(status);
CREATE INDEX IF NOT EXISTS idx_background_task_type ON background_task(type);

COMMENT ON COLUMN background_task.cancellable IS '标识任务是否支持用户主动取消（true=可取消，false=不可取消）';
