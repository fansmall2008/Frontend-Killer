-- ============================================================
-- V1.0.23 游戏匹配状态字段
-- matched: 游戏身份是否已通过权威数据源确认
--   true  = SS批量刮削成功 或 rom_db精确匹配（CRC/serial）
--   false = 未匹配 / 手动操作需确认
-- ============================================================
ALTER TABLE game ADD COLUMN IF NOT EXISTS matched BOOLEAN DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_game_matched ON game(matched);
