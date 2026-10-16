-- rom_db 扩展元数据字段（来源：RDB 中的 developer/publisher/releaseyear/genre）
ALTER TABLE rom_db ADD COLUMN IF NOT EXISTS developer VARCHAR(200);
ALTER TABLE rom_db ADD COLUMN IF NOT EXISTS publisher VARCHAR(200);
ALTER TABLE rom_db ADD COLUMN IF NOT EXISTS releaseyear VARCHAR(10);
ALTER TABLE rom_db ADD COLUMN IF NOT EXISTS genre VARCHAR(100);
