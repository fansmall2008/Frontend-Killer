-- V1.0.16__add_game_manifest_cache.sql
-- 游戏信息缓存（SS manifest）+ 导出找齐第一步
--   game 新增 cached / parent_rom 两列（scraped 列已存在，复用之）
--   新增 game_manifest 表：按 SS gameId 缓存完整 jeuInfos 响应，作为导出找齐的离线权威源
--     · 同一条 jeu 的多个 clone 共享一份（按 ss_game_id 去重）
--     · 内部不透明存储：不进导出包/备份、不被任何接口列举

ALTER TABLE game ADD COLUMN IF NOT EXISTS cached BOOLEAN DEFAULT false;
ALTER TABLE game ADD COLUMN IF NOT EXISTS parent_rom VARCHAR(512) DEFAULT NULL;

CREATE TABLE IF NOT EXISTS game_manifest (
    ss_game_id  BIGINT    PRIMARY KEY,             -- ScreenScraper 全局游戏ID（同 jeu 的 clone 共享一份）
    manifest    CLOB,                              -- 完整 jeuInfos 响应 JSON（内部不透明）
    fetched_at  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP, -- 本次拉取时间（用于失效判断）
    created_at  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP
);
