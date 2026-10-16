-- ============================================================
-- V1.0.22 ROM 识别数据库（统一替代 disc_serial_cache）
-- 数据来源：libretro-database (No-Intro + Redump 合并)
-- 支持卡带平台 CRC32 匹配 + 光盘平台 serial 匹配 + 模糊搜索
-- ============================================================

-- ROM 识别数据库（统一表）
CREATE TABLE IF NOT EXISTS rom_db (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_id    INT NOT NULL DEFAULT 0,      -- ScreenScraper systemId（0 = 无对应系统）
    platform     VARCHAR(100) NOT NULL,        -- libretro-database 平台名（如 "Sony - PlayStation 2"）
    crc32        VARCHAR(8),                   -- 卡带类平台用（8位十六进制）
    serial       VARCHAR(50),                  -- 光盘类平台用（如 SLUS-20751）
    game_name    VARCHAR(500) NOT NULL,        -- 标准游戏名称
    region       VARCHAR(50),                  -- 区域（Japan/USA/Europe/World 等）
    source       VARCHAR(20),                  -- 数据来源: 'redump' / 'no-intro' / 'serial'
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 卡带平台 CRC32 精确匹配索引
CREATE INDEX IF NOT EXISTS idx_rom_db_crc ON rom_db(system_id, crc32);

-- 光盘平台 serial 精确匹配索引
CREATE INDEX IF NOT EXISTS idx_rom_db_serial ON rom_db(system_id, serial);

-- 按平台查询索引
CREATE INDEX IF NOT EXISTS idx_rom_db_platform ON rom_db(platform);

-- 模糊搜索辅助索引（game_name）
CREATE INDEX IF NOT EXISTS idx_rom_db_name ON rom_db(game_name);

-- 按来源统计索引
CREATE INDEX IF NOT EXISTS idx_rom_db_source ON rom_db(source);
