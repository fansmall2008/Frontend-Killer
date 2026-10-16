-- ============================================================
-- V1.0.21 光盘标识缓存表 + 游戏光盘编号字段
-- 用于本地统一标识库：serial → 游戏标准名称 → SS romnom 搜索
-- 数据来源：ScreenScraper serialnum / OpenVGDB / Redump
-- ============================================================

-- 光盘标识缓存表（三源合并：SS + OpenVGDB + Redump）
CREATE TABLE IF NOT EXISTS disc_serial_cache (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    system_id   INT NOT NULL,              -- ScreenScraper systemId
    serial      VARCHAR(50) NOT NULL,      -- 光盘编号（如 SLUS-12345, NGCD-030）
    game_name   VARCHAR(500) NOT NULL,     -- 标准游戏名称（英文）
    source      VARCHAR(20),               -- 数据来源: 'ss' / 'openvgdb' / 'redump'
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 按 system_id + serial 唯一约束（同 serial 取最高优先级来源，由导入逻辑保证）
CREATE UNIQUE INDEX IF NOT EXISTS idx_disc_serial_unique ON disc_serial_cache(system_id, serial);

-- 刮削时快速查找索引
CREATE INDEX IF NOT EXISTS idx_disc_serial_lookup ON disc_serial_cache(system_id, serial);

-- 按数据来源统计索引（系统管理页展示各系统数据量）
CREATE INDEX IF NOT EXISTS idx_disc_serial_system ON disc_serial_cache(system_id);

-- Game 表新增 disc_serial 字段（存储提取到的光盘编号，可追溯可手动修正）
ALTER TABLE game ADD COLUMN IF NOT EXISTS disc_serial VARCHAR(100);
