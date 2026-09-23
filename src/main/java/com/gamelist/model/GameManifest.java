package com.gamelist.model;

import java.sql.Timestamp;

/**
 * 游戏信息缓存（SS manifest）
 *
 * 按 ScreenScraper 全局游戏ID（ss_game_id）缓存一份完整 jeuInfos 响应的 jeu 节点 JSON，
 * 作为导出找齐（父 rom copy-if-missing）的离线权威源。
 *
 * 安全约束：内部不透明存储，不进导出包/备份、不被任何接口列举；读取时校验，畸形当未命中重拉。
 */
public class GameManifest {

    /** ScreenScraper 全局游戏ID（主键；同一条 jeu 的多个 clone 共享一份） */
    private Long ssGameId;

    /** 完整 jeu 节点 JSON（内部不透明） */
    private String manifest;

    /** 本次拉取时间（用于失效判断） */
    private Timestamp fetchedAt;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    // ==================== Getter / Setter ====================

    public Long getSsGameId() { return ssGameId; }
    public void setSsGameId(Long ssGameId) { this.ssGameId = ssGameId; }

    public String getManifest() { return manifest; }
    public void setManifest(String manifest) { this.manifest = manifest; }

    public Timestamp getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Timestamp fetchedAt) { this.fetchedAt = fetchedAt; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
