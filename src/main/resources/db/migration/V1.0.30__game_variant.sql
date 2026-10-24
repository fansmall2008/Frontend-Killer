-- ROM 副本机制：副本记录、系统能力矩阵、系统导出偏好。
-- 注意：本项目 spring.flyway.enabled=false，实际 schema 由 sql/init.sql 管理；
-- 此文件仅作为版本记录，若未来启用 Flyway 可直接执行。

CREATE TABLE IF NOT EXISTS game_variant (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_id         BIGINT       NOT NULL,
    format          VARCHAR(16)  NOT NULL,
    rel_path        VARCHAR(1000) NOT NULL,
    size_bytes      BIGINT,
    source_size     BIGINT,
    source_mtime    BIGINT,
    status          VARCHAR(16)  NOT NULL DEFAULT 'READY',
    recipe          VARCHAR(32),
    error_message   VARCHAR(1000),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_game_variant UNIQUE (game_id, format)
);
CREATE INDEX IF NOT EXISTS idx_game_variant_game ON game_variant(game_id);

CREATE TABLE IF NOT EXISTS system_variant_capability (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_id       INT          NOT NULL,
    format          VARCHAR(16)  NOT NULL,
    supported       BOOLEAN      NOT NULL DEFAULT TRUE,
    source          VARCHAR(16)  DEFAULT 'auto',
    note            VARCHAR(255),
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_variant UNIQUE (system_id, format)
);
CREATE INDEX IF NOT EXISTS idx_system_variant_system ON system_variant_capability(system_id);

CREATE TABLE IF NOT EXISTS system_variant_preference (
    system_id         INT PRIMARY KEY,
    preferred_format  VARCHAR(16) NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
