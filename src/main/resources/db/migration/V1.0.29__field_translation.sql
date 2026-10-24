-- 通用字段译文表：接管 game.name / game.desc / rom_db.game_name 的多语种译文存储
CREATE TABLE IF NOT EXISTS field_translation (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    src_table   VARCHAR(20)  NOT NULL,
    src_id      BIGINT       NOT NULL,
    src_field   VARCHAR(30)  NOT NULL,
    lang        VARCHAR(10)  NOT NULL,
    trans_value VARCHAR(2000),
    UNIQUE (src_table, src_id, src_field, lang)
);
CREATE INDEX IF NOT EXISTS idx_ft_lookup ON field_translation (src_table, src_field, lang);

-- 迁移旧 game.translated_name / translated_desc 为 lang='zh' 译文（game 表 PK 为 id）
INSERT INTO field_translation (src_table, src_id, src_field, lang, trans_value)
    SELECT 'game', id, 'name', 'zh', translated_name
    FROM game
    WHERE translated_name IS NOT NULL AND TRIM(translated_name) <> '';

INSERT INTO field_translation (src_table, src_id, src_field, lang, trans_value)
    SELECT 'game', id, 'desc', 'zh', translated_desc
    FROM game
    WHERE translated_desc IS NOT NULL AND TRIM(translated_desc) <> '';

-- 删除旧列（不可逆，发布前已备份快照）
ALTER TABLE game DROP COLUMN IF EXISTS translated_name;
ALTER TABLE game DROP COLUMN IF EXISTS translated_desc;
