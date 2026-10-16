-- rom_db 添加多语言名称字段
ALTER TABLE rom_db ADD COLUMN IF NOT EXISTS game_name_jp VARCHAR(500);
ALTER TABLE rom_db ADD COLUMN IF NOT EXISTS game_name_cn VARCHAR(500);

-- 废弃 disc_serial_cache 表（统一走 rom_db 匹配）
DROP TABLE IF EXISTS disc_serial_cache;
