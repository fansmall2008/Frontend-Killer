-- ============================================================
-- V1.0.18: 统一刮削任务池表
-- 替代分散的并发控制机制（ThreadResourceManager + Semaphore）
-- 支持游戏信息刮削、媒体下载、街机自动下载三种任务类型
-- 通过 priority 字段实现优先级调度
-- ============================================================

CREATE TABLE IF NOT EXISTS scrape_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_type VARCHAR(20) NOT NULL,           -- 'GAME_INFO' | 'MEDIA_DOWNLOAD'
    game_id BIGINT,                           -- 关联游戏
    platform_id BIGINT,                       -- 关联平台
    -- GAME_INFO 专用字段
    rom_filename VARCHAR(500),                -- ROM 文件名（用于 SS API 搜索）
    system_id INT,                            -- SS 系统 ID
    ss_game_id BIGINT,                        -- SS 游戏 ID（街机自动下载/导出缓存场景）
    -- MEDIA_DOWNLOAD 专用字段
    media_type VARCHAR(50),                   -- 媒体类型（nomcourt 值）
    media_region VARCHAR(10),                 -- 媒体区域（wor/us/eu/jp）
    download_url VARCHAR(1024),               -- 下载 URL
    local_path VARCHAR(1024),                 -- 本地存储路径
    file_size BIGINT DEFAULT 0,               -- 文件大小
    -- 通用字段
    status VARCHAR(20) DEFAULT 'PENDING',     -- PENDING/RUNNING/COMPLETED/FAILED/STOPPED
    priority INT DEFAULT 0,                   -- 用户刮削=0, 媒体下载=10, 街机自动=50
    order_index BIGINT DEFAULT 0,             -- 同优先级内的排序
    retry_count INT DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引：任务拾取加速（按优先级取 PENDING 任务）
CREATE INDEX IF NOT EXISTS idx_scrape_task_status_priority ON scrape_task(status, priority, order_index);
-- 索引：平台级统计
CREATE INDEX IF NOT EXISTS idx_scrape_task_platform_status ON scrape_task(platform_id, status);
-- 索引：游戏关联查询
CREATE INDEX IF NOT EXISTS idx_scrape_task_game_id ON scrape_task(game_id);
