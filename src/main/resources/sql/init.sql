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
    -- 平台类型和哈希
    platform_type VARCHAR(50),
    hash VARCHAR(50)
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

-- 清空系统设置表
DELETE FROM system_settings;

-- 插入默认设置
INSERT INTO system_settings (setting_key, setting_value, description) VALUES
('backup_root_directory', '.backup', 'Backup root directory'),
('game_path', '', 'Game path'),
('backup_path', '.backup', 'Backup path'),
('google_api_key', '', 'Google Translation API Key'),
('baidu_app_id', '', 'Baidu Translation APP ID'),
('baidu_app_key', '', 'Baidu Translation APP Key'),
('youdao_app_key', '', 'Youdao Translation APP Key'),
('youdao_app_secret', '', 'Youdao Translation APP Secret'),
('deepseek_api_key', '', 'DeepSeek API Key')
;

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
    -- 平台类型和哈希
    platform_type VARCHAR(50),
    hash VARCHAR(50)
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


