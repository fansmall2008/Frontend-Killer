package com.gamelist.export.action;

/**
 * 分片策略：同一“分片”动作在不同系统上的具体实现。
 * <p>
 * {@code export-actions.json} 里 {@code strategies} 把 systemId 映射到策略 id，
 * {@code SplitBlock} 据此分派。Wii 走 wit，PS3 先挂 {@code noop}（预留位、按单文件导出并提示），
 * 以后要真做 PS3 分片只加一个策略 bean + 改一行 JSON。
 */
public interface SplitStrategy {

    /** 策略 id，与 catalog 的 strategies 值对应（如 wit-split / noop） */
    String id();

    /**
     * 对 {@code ctx.getSource()} 执行分片，把整组产物登记到 {@code ctx} 的交付清单。
     * <p>
     * 约定：产物先落 {@code ctx.stagingDir()}，通过 {@code ctx.addDeliverable(...)} 交出；
     * 未产生实际分片（如文件本就小于上限）时可留空 deliverables，由 copy 块按原样交付。
     */
    void split(ExportActionContext ctx) throws Exception;
}
