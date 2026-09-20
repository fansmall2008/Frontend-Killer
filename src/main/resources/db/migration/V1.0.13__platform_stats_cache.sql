-- V1.0.13__platform_stats_cache.sql
-- 平台体量统计缓存表：用于导出前预估数据量（ROM/媒体文件数量与占用空间）
-- 由 PlatformStatsScanner 服务按需扫描后写入，纯查询快照，不参与业务逻辑

CREATE TABLE IF NOT EXISTS platform_stats_cache (
    platform_id           BIGINT PRIMARY KEY,
    total_games           INT           DEFAULT 0,   -- 游戏记录数（Pegasus 多文件已展开）
    total_rom_count       INT           DEFAULT 0,   -- ROM 文件数（磁盘存在）
    rom_missing_count     INT           DEFAULT 0,   -- 数据库有路径但磁盘缺失的 ROM 数
    total_rom_size        BIGINT        DEFAULT 0,   -- ROM 总字节
    total_media_count     INT           DEFAULT 0,   -- 媒体文件数（磁盘存在）
    media_missing_count   INT           DEFAULT 0,   -- 数据库有路径但磁盘缺失的媒体数
    total_media_size      BIGINT        DEFAULT 0,   -- 媒体总字节
    media_count_by_type   VARCHAR(4000),             -- JSON: {"box-2D":800,"video":200,...}
    media_size_by_type    VARCHAR(4000),             -- JSON: {"box-2D":8e8,"video":6.5e9,...}
    last_scanned_at       TIMESTAMP,                 -- 上次扫描完成时间
    scan_duration_ms      BIGINT        DEFAULT 0,   -- 上次扫描耗时
    last_task_id          BIGINT,                    -- 关联的 BackgroundTask ID
    created_at            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (platform_id) REFERENCES platform(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_psc_last_scanned ON platform_stats_cache(last_scanned_at);
