-- 创建平台表
CREATE TABLE IF NOT EXISTS platform (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    `system` VARCHAR(100) NOT NULL,
    software VARCHAR(100) NOT NULL,
    `database` VARCHAR(100) NOT NULL,
    web VARCHAR(200) NOT NULL,
    name VARCHAR(255) NOT NULL,
    sort_by VARCHAR(50),
    launch TEXT,
    folder_path VARCHAR(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 为平台表添加刮削系统关联字段（兼容旧数据库）
ALTER TABLE platform ADD COLUMN IF NOT EXISTS system_id INT DEFAULT NULL;
ALTER TABLE platform ADD COLUMN IF NOT EXISTS system_region VARCHAR(10) DEFAULT NULL;
ALTER TABLE platform ADD COLUMN IF NOT EXISTS logo_region VARCHAR(10) DEFAULT NULL;
ALTER TABLE platform ADD COLUMN IF NOT EXISTS logo_type VARCHAR(50) DEFAULT NULL;

-- 为平台表添加 Pegasus 表头字段（扩展名 / 忽略文件，兼容旧数据库）
ALTER TABLE platform ADD COLUMN IF NOT EXISTS extensions VARCHAR(200) DEFAULT NULL;
ALTER TABLE platform ADD COLUMN IF NOT EXISTS ignore_files TEXT DEFAULT NULL;

-- 创建游戏表
CREATE TABLE IF NOT EXISTS game (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id VARCHAR(100) NOT NULL,
    source VARCHAR(100),
    `path` VARCHAR(1024) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `desc` TEXT,
    translated_name VARCHAR(255),
    translated_desc TEXT,
    image VARCHAR(255),
    video VARCHAR(255),
    marquee VARCHAR(255),
    thumbnail VARCHAR(255),
    manual VARCHAR(255),
    rating DOUBLE,
    releasedate VARCHAR(50),
    developer VARCHAR(100),
    publisher VARCHAR(100),
    genre VARCHAR(100),
    players VARCHAR(20),
    crc32 VARCHAR(20),
    md5 VARCHAR(32),
    lang VARCHAR(20),
    genreid VARCHAR(20),
    sort_by VARCHAR(50),
    platform_id BIGINT,
    scraped BOOLEAN DEFAULT false,
    edited BOOLEAN DEFAULT false,
    `exists` BOOLEAN DEFAULT false,
    absolute_path VARCHAR(2048),
    platform_path VARCHAR(2048),
    ss_game_id BIGINT,
    multi_file BOOLEAN DEFAULT false,
    multi_file_content CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (platform_id) REFERENCES platform(id),
    -- 媒体类型字段
    box_front VARCHAR(255),
    box_back VARCHAR(255),
    box_spine VARCHAR(255),
    box_full VARCHAR(255),
    cartridge VARCHAR(255),
    logo VARCHAR(255),
    bezel VARCHAR(255),
    panel VARCHAR(255),
    cabinet_left VARCHAR(255),
    cabinet_right VARCHAR(255),
    tile VARCHAR(255),
    banner VARCHAR(255),
    steam VARCHAR(255),
    poster VARCHAR(255),
    background VARCHAR(255),
    music VARCHAR(255),
    screenshot VARCHAR(255),
    titlescreen VARCHAR(255),
    box3d VARCHAR(255),
    steamgrid VARCHAR(255),
    fanart VARCHAR(255),
    boxtexture VARCHAR(255),
    supporttexture VARCHAR(255),
    videonormalized VARCHAR(255),
    wheelcarbon VARCHAR(255),
    wheelsteel VARCHAR(255),
    screenmarqueesmall VARCHAR(255),
    boxside VARCHAR(255),
    figurine VARCHAR(255),
    -- V1.0.5/V1.0.8 新增媒体类型
    pictoliste VARCHAR(255),
    pictomonochrome VARCHAR(255),
    pictomonochromesvg VARCHAR(255),
    pictocouleur VARCHAR(255),
    wallpaper VARCHAR(255),
    -- 平台类型和哈希
    platform_type VARCHAR(50),
    hash VARCHAR(50),
    -- V1.0.11 ScreenScraper 标准媒体字段
    box_2d VARCHAR(255),
    box_2d_back VARCHAR(255),
    box_2d_side VARCHAR(255),
    box_texture VARCHAR(255),
    box_scan VARCHAR(255),
    support_2d VARCHAR(255),
    support_texture VARCHAR(255),
    support_scan VARCHAR(255),
    wheel VARCHAR(255),
    wheel_hd VARCHAR(255),
    wheel_carbon VARCHAR(255),
    wheel_steel VARCHAR(255),
    screenmarquee VARCHAR(255),
    ss VARCHAR(255),
    sstitle VARCHAR(255),
    overlay VARCHAR(255),
    video_normalized VARCHAR(255),
    flyer VARCHAR(255),
    manuel VARCHAR(255),
    maps VARCHAR(255),
    bezel_4_3 VARCHAR(255),
    bezel_4_3_v VARCHAR(255),
    bezel_4_3_cocktail VARCHAR(255),
    bezel_16_9 VARCHAR(255),
    bezel_16_9_v VARCHAR(255),
    bezel_16_9_cocktail VARCHAR(255),
    mixrbv1 VARCHAR(255),
    mixrbv2 VARCHAR(255),
    themehb VARCHAR(255),
    themehs VARCHAR(255),
    ssdmd VARCHAR(255),
    ssfronton16_9 VARCHAR(255),
    ssfronton1_1 VARCHAR(255),
    ssfronton4_3 VARCHAR(255),
    sstable VARCHAR(255),
    sstopper VARCHAR(255),
    videodmd VARCHAR(255),
    videofronton4_3 VARCHAR(255),
    videofronton16_9 VARCHAR(255),
    videotable VARCHAR(255),
    videotable4k VARCHAR(255),
    videotopper VARCHAR(255),
    wheel_tarcisios VARCHAR(255)
);

-- 创建系统设置表
CREATE TABLE IF NOT EXISTS system_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value VARCHAR(2048) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 补充默认设置（只补缺、不覆盖：升级时新键自动出现，用户已修改的值保留）
INSERT INTO system_settings (setting_key, setting_value, description)
SELECT 'backup_root_directory', '.backup', 'Backup root directory' WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'backup_root_directory');
INSERT INTO system_settings (setting_key, setting_value, description)
SELECT 'game_path', '', 'Game path' WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'game_path');
INSERT INTO system_settings (setting_key, setting_value, description)
SELECT 'backup_path', '.backup', 'Backup path' WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'backup_path');
INSERT INTO system_settings (setting_key, setting_value, description)
SELECT 'google_api_key', '', 'Google Translation API Key' WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'google_api_key');
INSERT INTO system_settings (setting_key, setting_value, description)
SELECT 'baidu_app_id', '', 'Baidu Translation APP ID' WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'baidu_app_id');
INSERT INTO system_settings (setting_key, setting_value, description)
SELECT 'baidu_app_key', '', 'Baidu Translation APP Key' WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'baidu_app_key');
INSERT INTO system_settings (setting_key, setting_value, description)
SELECT 'youdao_app_key', '', 'Youdao Translation APP Key' WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'youdao_app_key');
INSERT INTO system_settings (setting_key, setting_value, description)
SELECT 'youdao_app_secret', '', 'Youdao Translation APP Secret' WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'youdao_app_secret');
INSERT INTO system_settings (setting_key, setting_value, description)
SELECT 'deepseek_api_key', '', 'DeepSeek API Key' WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'deepseek_api_key');
INSERT INTO system_settings (setting_key, setting_value, description)
SELECT 'auto_cache_game_info', 'false', 'Auto cache game manifest (arcade only, speeds up scraping but uses SS quota)' WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'auto_cache_game_info');

-- 创建临时子集表
CREATE TABLE IF NOT EXISTS temp_subset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    creator_id BIGINT DEFAULT 1,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建临时子集游戏表
CREATE TABLE IF NOT EXISTS temp_subset_game (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subset_id BIGINT NOT NULL,
    original_game_id BIGINT NOT NULL,
    game_id VARCHAR(100) NOT NULL,
    source VARCHAR(100),
    `path` VARCHAR(1024) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `desc` TEXT,
    translated_name VARCHAR(255),
    translated_desc TEXT,
    image VARCHAR(255),
    video VARCHAR(255),
    marquee VARCHAR(255),
    thumbnail VARCHAR(255),
    manual VARCHAR(255),
    rating DOUBLE,
    releasedate VARCHAR(50),
    developer VARCHAR(100),
    publisher VARCHAR(100),
    genre VARCHAR(100),
    players VARCHAR(20),
    crc32 VARCHAR(20),
    md5 VARCHAR(32),
    lang VARCHAR(20),
    genreid VARCHAR(20),
    sort_by VARCHAR(50),
    platform_id BIGINT,
    scraped BOOLEAN DEFAULT false,
    edited BOOLEAN DEFAULT false,
    `exists` BOOLEAN DEFAULT false,
    absolute_path VARCHAR(2048),
    platform_path VARCHAR(2048),
    ss_game_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (subset_id) REFERENCES temp_subset(id),
    FOREIGN KEY (original_game_id) REFERENCES game(id),
    FOREIGN KEY (platform_id) REFERENCES platform(id),
    -- 媒体类型字段
    box_front VARCHAR(255),
    box_back VARCHAR(255),
    box_spine VARCHAR(255),
    box_full VARCHAR(255),
    cartridge VARCHAR(255),
    logo VARCHAR(255),
    bezel VARCHAR(255),
    panel VARCHAR(255),
    cabinet_left VARCHAR(255),
    cabinet_right VARCHAR(255),
    tile VARCHAR(255),
    banner VARCHAR(255),
    steam VARCHAR(255),
    poster VARCHAR(255),
    background VARCHAR(255),
    music VARCHAR(255),
    screenshot VARCHAR(255),
    titlescreen VARCHAR(255),
    box3d VARCHAR(255),
    steamgrid VARCHAR(255),
    fanart VARCHAR(255),
    boxtexture VARCHAR(255),
    supporttexture VARCHAR(255),
    videonormalized VARCHAR(255),
    wheelcarbon VARCHAR(255),
    wheelsteel VARCHAR(255),
    screenmarqueesmall VARCHAR(255),
    boxside VARCHAR(255),
    figurine VARCHAR(255),
    -- V1.0.5/V1.0.8 新增媒体类型
    pictoliste VARCHAR(255),
    pictomonochrome VARCHAR(255),
    pictomonochromesvg VARCHAR(255),
    pictocouleur VARCHAR(255),
    wallpaper VARCHAR(255),
    -- 平台类型和哈希
    platform_type VARCHAR(50),
    hash VARCHAR(50),
    -- V1.0.11 ScreenScraper 标准媒体字段
    box_2d VARCHAR(255),
    box_2d_back VARCHAR(255),
    box_2d_side VARCHAR(255),
    box_texture VARCHAR(255),
    box_scan VARCHAR(255),
    support_2d VARCHAR(255),
    support_texture VARCHAR(255),
    support_scan VARCHAR(255),
    wheel VARCHAR(255),
    wheel_hd VARCHAR(255),
    wheel_carbon VARCHAR(255),
    wheel_steel VARCHAR(255),
    screenmarquee VARCHAR(255),
    ss VARCHAR(255),
    sstitle VARCHAR(255),
    overlay VARCHAR(255),
    video_normalized VARCHAR(255),
    flyer VARCHAR(255),
    manuel VARCHAR(255),
    maps VARCHAR(255),
    bezel_4_3 VARCHAR(255),
    bezel_4_3_v VARCHAR(255),
    bezel_4_3_cocktail VARCHAR(255),
    bezel_16_9 VARCHAR(255),
    bezel_16_9_v VARCHAR(255),
    bezel_16_9_cocktail VARCHAR(255),
    mixrbv1 VARCHAR(255),
    mixrbv2 VARCHAR(255),
    themehb VARCHAR(255),
    themehs VARCHAR(255),
    ssdmd VARCHAR(255),
    ssfronton16_9 VARCHAR(255),
    ssfronton1_1 VARCHAR(255),
    ssfronton4_3 VARCHAR(255),
    sstable VARCHAR(255),
    sstopper VARCHAR(255),
    videodmd VARCHAR(255),
    videofronton4_3 VARCHAR(255),
    videofronton16_9 VARCHAR(255),
    videotable VARCHAR(255),
    videotable4k VARCHAR(255),
    videotopper VARCHAR(255),
    wheel_tarcisios VARCHAR(255)
);

-- 创建合并报告表
CREATE TABLE IF NOT EXISTS merge_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_name VARCHAR(255) NOT NULL,
    new_platform_id BIGINT NOT NULL,
    source_platforms VARCHAR(1024) NOT NULL,
    total_games INT DEFAULT 0,
    added_games INT DEFAULT 0,
    conflict_games INT DEFAULT 0,
    status VARCHAR(50) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    FOREIGN KEY (new_platform_id) REFERENCES platform(id)
);

-- 创建合并冲突表
CREATE TABLE IF NOT EXISTS merge_conflict (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    source_game_id BIGINT NULL,
    existing_game_id BIGINT NULL,
    source_game_name VARCHAR(255) NOT NULL,
    existing_game_name VARCHAR(255) NOT NULL,
    source_file_name VARCHAR(1024) NOT NULL,
    existing_file_name VARCHAR(1024) NOT NULL,
    conflict_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'pending',
    FOREIGN KEY (report_id) REFERENCES merge_report(id),
    FOREIGN KEY (source_game_id) REFERENCES game(id),
    FOREIGN KEY (existing_game_id) REFERENCES game(id)
);

-- 创建刮削系统表
CREATE TABLE IF NOT EXISTS scraper_system (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_id INT UNIQUE NOT NULL,
    parent_id INT,
    name VARCHAR(255) NOT NULL,
    name_en VARCHAR(255),
    name_fr VARCHAR(255),
    name_jp VARCHAR(255),
    name_cn VARCHAR(255),
    nom_eu VARCHAR(255),
    nom_us VARCHAR(255),
    nom_launchbox VARCHAR(255),
    nom_hyperspin VARCHAR(255),
    nom_recalbox VARCHAR(255),
    nom_retropie VARCHAR(255),
    noms_commun TEXT,
    company VARCHAR(255),
    type VARCHAR(50),
    release_year INT,
    end_year INT,
    rom_type VARCHAR(50),
    support_type VARCHAR(50),
    extensions VARCHAR(200),
    icon_url VARCHAR(500),
    media_scraped BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 为 scraper_system 表添加媒体刮削状态字段（兼容旧数据库）
ALTER TABLE scraper_system ADD COLUMN IF NOT EXISTS media_scraped BOOLEAN DEFAULT FALSE;

-- 创建后台任务表
CREATE TABLE IF NOT EXISTS background_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    progress INT DEFAULT 0,
    message VARCHAR(500),
    total_items BIGINT DEFAULT 0,
    processed_items BIGINT DEFAULT 0,
    start_time TIMESTAMP NULL,
    end_time TIMESTAMP NULL,
    error_message TEXT,
    description VARCHAR(500),
    result TEXT,
    `log` TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 创建媒体下载任务表
CREATE TABLE IF NOT EXISTS media_download_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    game_id BIGINT,
    game_name VARCHAR(255),
    game_field_name VARCHAR(50) DEFAULT NULL,
    platform_id BIGINT DEFAULT NULL,
    platform_name VARCHAR(255) DEFAULT NULL,
    media_type VARCHAR(50) NOT NULL,
    download_url VARCHAR(1024) NOT NULL,
    local_path VARCHAR(1024) NOT NULL,
    file_size BIGINT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PENDING',
    priority INT DEFAULT 0,
    retry_count INT DEFAULT 0,
    error_message TEXT,
    order_index BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (task_id) REFERENCES background_task(id),
    FOREIGN KEY (game_id) REFERENCES game(id)
);

-- ============================================================
-- 增量补全已有数据库缺失的列（ALTER TABLE IF NOT EXISTS）
-- 对于已存在的表，CREATE TABLE IF NOT EXISTS 不会添加新列，
-- 因此通过 ALTER TABLE ADD COLUMN IF NOT EXISTS 确保升级兼容。
-- ============================================================

-- V1.0.5/V1.0.8 新增媒体类型
ALTER TABLE game ADD COLUMN IF NOT EXISTS pictoliste VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS pictomonochrome VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS pictomonochromesvg VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS pictocouleur VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS wallpaper VARCHAR(255);

-- V1.0.11 ScreenScraper 标准媒体字段
ALTER TABLE game ADD COLUMN IF NOT EXISTS box_2d VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS box_2d_back VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS box_2d_side VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS box_texture VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS box_scan VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS support_2d VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS support_texture VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS support_scan VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS wheel VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS wheel_hd VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS wheel_carbon VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS wheel_steel VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS screenmarquee VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS ss VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS sstitle VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS overlay VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS video_normalized VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS flyer VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS manuel VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS maps VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS bezel_4_3 VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS bezel_4_3_v VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS bezel_4_3_cocktail VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS bezel_16_9 VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS bezel_16_9_v VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS bezel_16_9_cocktail VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS mixrbv1 VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS mixrbv2 VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS themehb VARCHAR(255);
ALTER TABLE game ADD COLUMN IF NOT EXISTS themehs VARCHAR(255);
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

-- temp_subset_game 表同步补全
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS pictoliste VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS pictomonochrome VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS pictomonochromesvg VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS pictocouleur VARCHAR(255);
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS wallpaper VARCHAR(255);
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

-- V1.0.4 media_download_task 新增字段
ALTER TABLE media_download_task ADD COLUMN IF NOT EXISTS platform_id BIGINT DEFAULT NULL;
ALTER TABLE media_download_task ADD COLUMN IF NOT EXISTS platform_name VARCHAR(255) DEFAULT NULL;

-- V1.0.9 media_download_task 新增字段
ALTER TABLE media_download_task ADD COLUMN IF NOT EXISTS game_field_name VARCHAR(50) DEFAULT NULL;

-- V1.0.14 game 多文件游戏字段（多盘/合盘）
ALTER TABLE game ADD COLUMN IF NOT EXISTS multi_file BOOLEAN DEFAULT false;
ALTER TABLE game ADD COLUMN IF NOT EXISTS multi_file_content CLOB;

-- V1.0.15 ScreenScraper 游戏ID（媒体目录稳定键，跨平台复用）
ALTER TABLE game ADD COLUMN IF NOT EXISTS ss_game_id BIGINT DEFAULT NULL;
ALTER TABLE temp_subset_game ADD COLUMN IF NOT EXISTS ss_game_id BIGINT DEFAULT NULL;

-- V1.0.16 游戏信息缓存（SS manifest）+ 导出找齐第一步
--   game 新增 cached / parent_rom 两列（scraped 列已存在，复用之）
ALTER TABLE game ADD COLUMN IF NOT EXISTS cached BOOLEAN DEFAULT false;
ALTER TABLE game ADD COLUMN IF NOT EXISTS parent_rom VARCHAR(512) DEFAULT NULL;

-- game_manifest：按 SS gameId 缓存完整 jeuInfos 响应，导出找齐的离线权威源
--   · 同一条 jeu 的多个 clone 共享一份（按 ss_game_id 去重）
--   · 内部不透明存储：不进导出包/备份、不被任何接口列举
CREATE TABLE IF NOT EXISTS game_manifest (
    ss_game_id  BIGINT    PRIMARY KEY,
    manifest    CLOB,
    fetched_at  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    created_at  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- V1.0.13 平台体量统计缓存表
-- 用于导出前预估数据量（ROM/媒体文件数量与占用空间）
-- 由 PlatformStatsScanner 服务按需扫描后写入，纯查询快照，不参与业务逻辑
-- ============================================================
CREATE TABLE IF NOT EXISTS platform_stats_cache (
    platform_id           BIGINT PRIMARY KEY,
    total_games           INT           DEFAULT 0,
    total_rom_count       INT           DEFAULT 0,
    rom_missing_count     INT           DEFAULT 0,
    total_rom_size        BIGINT        DEFAULT 0,
    total_media_count     INT           DEFAULT 0,
    media_missing_count   INT           DEFAULT 0,
    total_media_size      BIGINT        DEFAULT 0,
    media_count_by_type   VARCHAR(4000),
    media_size_by_type    VARCHAR(4000),
    last_scanned_at       TIMESTAMP,
    scan_duration_ms      BIGINT        DEFAULT 0,
    last_task_id          BIGINT,
    created_at            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (platform_id) REFERENCES platform(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_psc_last_scanned ON platform_stats_cache(last_scanned_at);

-- ============================================================
-- V1.1 通知中心表
-- 持久化所有后台事件（任务创建/完成/失败、刮削、媒体下载、配额警告等）
-- 铃铛角标与通知面板读取本表，SSE 实时推送新事件
-- ============================================================
CREATE TABLE IF NOT EXISTS notification (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    title         VARCHAR(255)  NOT NULL,
    message       VARCHAR(2000),
    type          VARCHAR(20)   DEFAULT 'info',
    source        VARCHAR(50)   DEFAULT NULL,
    is_read       BOOLEAN       DEFAULT FALSE,
    created_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notification_created ON notification(created_at);
CREATE INDEX IF NOT EXISTS idx_notification_read ON notification(is_read);

-- ============================================================
-- media_download_task 复合索引：优化按 (platform_id, status) 的 COUNT / 拉取
-- 前端媒体下载页面需频繁按平台+状态统计，旧有单列索引 idx_media_download_task_platform_id
-- 仍需对 status 二次过滤；此复合索引可直接命中，显著降低扫描行数。
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_mdt_platform_status ON media_download_task(platform_id, status);
CREATE INDEX IF NOT EXISTS idx_mdt_status_order ON media_download_task(status, order_index);

-- ============================================================
-- game 表索引：平台详情页与列表页高频使用 platform_id + scraped 过滤，
-- 及 ORDER BY name 排序；没索引时每页都会全表扫描 + filesort，在大库下会
-- 直接导致平台详情页卡死。另外 path 列在导入去重时频繁命中。
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_game_platform_id      ON game(platform_id);
CREATE INDEX IF NOT EXISTS idx_game_platform_scraped ON game(platform_id, scraped);
CREATE INDEX IF NOT EXISTS idx_game_name             ON game(name);
CREATE INDEX IF NOT EXISTS idx_game_path             ON game(path);
