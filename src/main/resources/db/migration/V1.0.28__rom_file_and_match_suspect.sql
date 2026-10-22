-- ============================================================
-- V1.0.28 街机文件名精准匹配 + 存疑状态
-- rom_db.rom_file : 存归一化后的 ROM 压缩包文件名（去扩展名、小写），
--                   供街机按 zip 短名做精准本地匹配（clrMAME Pro DAT 的 rom name）。
-- game.match_suspect : 名字级命中（文件名/标题推断，未校验内容完整性）标记，
--                   与 matched 正交：matched=true 为权威(绿)，match_suspect=true 为存疑(黄)。
-- game.match_method  : 记录命中来源（crc/serial/filename_exact/name_serial/name_fuzzy），
--                   供前端 tooltip 与统计用。
-- ============================================================

-- rom_db 增加文件名列 + 按 (system_id, rom_file) 精确匹配索引
ALTER TABLE rom_db ADD COLUMN IF NOT EXISTS rom_file VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_rom_db_romfile ON rom_db(system_id, rom_file);

-- game 增加存疑标记与命中来源
ALTER TABLE game ADD COLUMN IF NOT EXISTS match_suspect BOOLEAN DEFAULT FALSE;
ALTER TABLE game ADD COLUMN IF NOT EXISTS match_method VARCHAR(20);
