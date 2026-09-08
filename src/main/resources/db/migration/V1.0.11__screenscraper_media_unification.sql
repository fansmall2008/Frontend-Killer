-- V1.0.11__screenscraper_media_unification.sql
-- ScreenScraper 媒体类型命名统一：以官方 mediasJeuListe 的 50 个 nomcourt 为标准
-- 命名规则：DB列名 = nomcourt 中 '-' 替换为 '_'，全部小写
-- 旧列保留不删除，仅新增标准列并迁移数据

-- ============================================================
-- 第一部分：game 表 - 新增 ScreenScraper 标准列
-- ============================================================

-- 包装盒类 (Boitiers / Elements Boitiers)
ALTER TABLE game ADD COLUMN IF NOT EXISTS box_2d VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS box_2d_back VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS box_2d_side VARCHAR(255);
-- box_3d 已存在，无需添加
ALTER TABLE game ADD COLUMN IF NOT EXISTS box_texture VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS box_scan VARCHAR(255);

-- 支撑类 (Supports / Sources)
ALTER TABLE game ADD COLUMN IF NOT EXISTS support_2d VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS support_texture VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS support_scan VARCHAR(255);

-- 轮盘类 (Logos/Wheels)
ALTER TABLE game ADD COLUMN IF NOT EXISTS wheel VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS wheel_hd VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS wheel_carbon VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS wheel_steel VARCHAR(255);

-- 霓虹灯类 (Marquee)
-- marquee 已存在（baseline），无需添加
ALTER TABLE game ADD COLUMN IF NOT EXISTS screenmarquee VARCHAR(255);
-- screenmarqueesmall 已存在，无需添加

-- 截图与通用媒体类 (Médias)
ALTER TABLE game ADD COLUMN IF NOT EXISTS ss VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS sstitle VARCHAR(255);
-- steamgrid 已存在，无需添加
-- fanart 已存在，无需添加
ALTER TABLE game ADD COLUMN IF NOT EXISTS overlay VARCHAR(255);
-- video 已存在，无需添加
ALTER TABLE game ADD COLUMN IF NOT EXISTS video_normalized VARCHAR(255);

-- 次要媒体类 (Médias Secondaires)
ALTER TABLE game ADD COLUMN IF NOT EXISTS flyer VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS manuel VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS maps VARCHAR(255);
-- figurine 已存在，无需添加

-- 边框类 (Bezels)
ALTER TABLE game ADD COLUMN IF NOT EXISTS bezel_4_3 VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS bezel_4_3_v VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS bezel_4_3_cocktail VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS bezel_16_9 VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS bezel_16_9_v VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS bezel_16_9_cocktail VARCHAR(255);

-- 混合类 (Mixes)
ALTER TABLE game ADD COLUMN IF NOT EXISTS mixrbv1 VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS mixrbv2 VARCHAR(255);

-- 主题类 (Themes)
ALTER TABLE game ADD COLUMN IF NOT EXISTS themehb VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS themehs VARCHAR(255);

-- 弹球台专用 (Médias Pincab)
ALTER TABLE game ADD COLUMN IF NOT EXISTS ssdmd VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS ssfronton16_9 VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS ssfronton1_1 VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS ssfronton4_3 VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS sstable VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS sstopper VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS videodmd VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS videofronton4_3 VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS videofronton16_9 VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS videotable VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS videotable4k VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS videotopper VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS wheel_tarcisios VARCHAR(255);

-- ============================================================
-- 第二部分：game 表 - 数据迁移（旧列 -> 新标准列）
-- ============================================================

UPDATE game SET box_2d = box_front WHERE box_front IS NOT NULL AND box_2d IS NULL;
UPDATE game SET box_2d_back = box_back WHERE box_back IS NOT NULL AND box_2d_back IS NULL;
UPDATE game SET box_2d_side = COALESCE(boxside, box_spine) WHERE box_2d_side IS NULL;
UPDATE game SET box_texture = boxtexture WHERE boxtexture IS NOT NULL AND box_texture IS NULL;
UPDATE game SET support_2d = cartridge WHERE cartridge IS NOT NULL AND support_2d IS NULL;
UPDATE game SET support_texture = supporttexture WHERE supporttexture IS NOT NULL AND support_texture IS NULL;
UPDATE game SET wheel_carbon = wheelcarbon WHERE wheelcarbon IS NOT NULL AND wheel_carbon IS NULL;
UPDATE game SET wheel_steel = wheelsteel WHERE wheelsteel IS NOT NULL AND wheel_steel IS NULL;
UPDATE game SET screenmarquee = marquee WHERE marquee IS NOT NULL AND screenmarquee IS NULL;
UPDATE game SET ss = screenshot WHERE screenshot IS NOT NULL AND ss IS NULL;
UPDATE game SET sstitle = titlescreen WHERE titlescreen IS NOT NULL AND sstitle IS NULL;
UPDATE game SET video_normalized = videonormalized WHERE videonormalized IS NOT NULL AND video_normalized IS NULL;
UPDATE game SET manuel = manual WHERE manual IS NOT NULL AND manuel IS NULL;
UPDATE game SET bezel_4_3 = bezel WHERE bezel IS NOT NULL AND bezel_4_3 IS NULL;

-- ============================================================
-- 第三部分：temp_subset_game 表 - 新增 ScreenScraper 标准列
-- ============================================================

ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS box_2d VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS box_2d_back VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS box_2d_side VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS box_texture VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS box_scan VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS support_2d VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS support_texture VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS support_scan VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS wheel VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS wheel_hd VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS wheel_carbon VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS wheel_steel VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS screenmarquee VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS ss VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS sstitle VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS overlay VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS video_normalized VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS flyer VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS manuel VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS maps VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS bezel_4_3 VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS bezel_4_3_v VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS bezel_4_3_cocktail VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS bezel_16_9 VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS bezel_16_9_v VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS bezel_16_9_cocktail VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS mixrbv1 VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS mixrbv2 VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS themehb VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS themehs VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS ssdmd VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS ssfronton16_9 VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS ssfronton1_1 VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS ssfronton4_3 VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS sstable VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS sstopper VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS videodmd VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS videofronton4_3 VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS videofronton16_9 VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS videotable VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS videotable4k VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS videotopper VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS wheel_tarcisios VARCHAR(255);

-- ============================================================
-- 第四部分：temp_subset_game 表 - 数据迁移
-- ============================================================

UPDATE temp_subset_game SET box_2d = box_front WHERE box_front IS NOT NULL AND box_2d IS NULL;
UPDATE temp_subset_game SET box_2d_back = box_back WHERE box_back IS NOT NULL AND box_2d_back IS NULL;
UPDATE temp_subset_game SET box_2d_side = COALESCE(boxside, box_spine) WHERE box_2d_side IS NULL;
UPDATE temp_subset_game SET box_texture = boxtexture WHERE boxtexture IS NOT NULL AND box_texture IS NULL;
UPDATE temp_subset_game SET support_2d = cartridge WHERE cartridge IS NOT NULL AND support_2d IS NULL;
UPDATE temp_subset_game SET support_texture = supporttexture WHERE supporttexture IS NOT NULL AND support_texture IS NULL;
UPDATE temp_subset_game SET wheel_carbon = wheelcarbon WHERE wheelcarbon IS NOT NULL AND wheel_carbon IS NULL;
UPDATE temp_subset_game SET wheel_steel = wheelsteel WHERE wheelsteel IS NOT NULL AND wheel_steel IS NULL;
UPDATE temp_subset_game SET screenmarquee = marquee WHERE marquee IS NOT NULL AND screenmarquee IS NULL;
UPDATE temp_subset_game SET ss = screenshot WHERE screenshot IS NOT NULL AND ss IS NULL;
UPDATE temp_subset_game SET sstitle = titlescreen WHERE titlescreen IS NOT NULL AND sstitle IS NULL;
UPDATE temp_subset_game SET video_normalized = videonormalized WHERE videonormalized IS NOT NULL AND video_normalized IS NULL;
UPDATE temp_subset_game SET manuel = manual WHERE manual IS NOT NULL AND manuel IS NULL;
UPDATE temp_subset_game SET bezel_4_3 = bezel WHERE bezel IS NOT NULL AND bezel_4_3 IS NULL;
