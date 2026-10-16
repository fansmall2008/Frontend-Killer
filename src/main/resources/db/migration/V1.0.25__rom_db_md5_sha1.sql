-- rom_db 增加 MD5/SHA1 哈希字段（从 libretro-database metadat 提取）
ALTER TABLE rom_db ADD COLUMN IF NOT EXISTS md5 VARCHAR(32);
ALTER TABLE rom_db ADD COLUMN IF NOT EXISTS sha1 VARCHAR(40);
