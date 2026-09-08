-- 为平台表添加刮削系统关联字段
ALTER TABLE platform ADD COLUMN system_id INT DEFAULT NULL;
ALTER TABLE platform ADD COLUMN system_region VARCHAR(10) DEFAULT NULL;

-- 添加刮削状态和是否编辑过的状态字段
ALTER TABLE game ADD COLUMN scraped BOOLEAN DEFAULT false;
ALTER TABLE game ADD COLUMN edited BOOLEAN DEFAULT false;
ALTER TABLE game ADD COLUMN platform_path VARCHAR(1024) DEFAULT NULL;

-- 为临时子集游戏表也添加相同的字段
ALTER TABLE temp_subset_game ADD COLUMN scraped BOOLEAN DEFAULT false;
ALTER TABLE temp_subset_game ADD COLUMN edited BOOLEAN DEFAULT false;
ALTER TABLE temp_subset_game ADD COLUMN platform_path VARCHAR(1024) DEFAULT NULL;

-- 添加新的媒体类型字段
ALTER TABLE game ADD COLUMN IF NOT EXISTS videonormalized VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS wheelcarbon VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS wheelsteel VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS screenmarqueesmall VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS boxside VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS figurine VARCHAR(255);

-- 为临时子集游戏表也添加新的媒体类型字段
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS videonormalized VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS wheelcarbon VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS wheelsteel VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS screenmarqueesmall VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS boxside VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS figurine VARCHAR(255);