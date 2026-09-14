-- 为 scraper_system 表添加媒体刮削状态字段
-- 用于追踪该系统的媒体文件（icon、logo 等）是否已从 ScreenScraper 下载
ALTER TABLE scraper_system ADD COLUMN IF NOT EXISTS media_scraped BOOLEAN DEFAULT FALSE;
