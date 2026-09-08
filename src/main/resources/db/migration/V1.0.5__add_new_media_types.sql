-- 添加新的媒体类型字段（ScreenScraper API支持）
ALTER TABLE game ADD COLUMN IF NOT EXISTS pictoliste VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS pictomonochrome VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS pictomonochromesvg VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS pictocouleur VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS wallpaper VARCHAR(255);

-- 为临时子集游戏表也添加新的媒体类型字段
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS pictoliste VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS pictomonochrome VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS pictomonochromesvg VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS pictocouleur VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS wallpaper VARCHAR(255);