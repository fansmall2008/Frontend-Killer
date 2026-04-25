-- 添加刮削状态和是否编辑过的状态字段
ALTER TABLE game ADD COLUMN scraped BOOLEAN DEFAULT false;
ALTER TABLE game ADD COLUMN edited BOOLEAN DEFAULT false;
ALTER TABLE game ADD COLUMN platform_path VARCHAR(1024) DEFAULT NULL;

-- 为临时子集游戏表也添加相同的字段
ALTER TABLE temp_subset_game ADD COLUMN scraped BOOLEAN DEFAULT false;
ALTER TABLE temp_subset_game ADD COLUMN edited BOOLEAN DEFAULT false;
ALTER TABLE temp_subset_game ADD COLUMN platform_path VARCHAR(1024) DEFAULT NULL;