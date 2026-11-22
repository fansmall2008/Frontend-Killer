package com.gamelist.export.action;

/**
 * 积木块：动作链（{@code chain}）里的一个步骤。
 * <p>
 * 每块只干一件小事、只依赖 {@link ExportActionContext}，互不认识上下游：
 * 转换块改 {@code source}，分片块填 {@code deliverables}，交付块负责落盘，清理块删暂存。
 * 要加新的后处理能力（如图像缩放）＝ 新增一个 {@code @Component implements ExportBlock}
 * + 在 {@code export-actions.json} 的某条 chain 里引用其 id，导出主流程零改动。
 */
public interface ExportBlock {

    /** 块 id，与 {@code export-actions.json} 的 chain 步骤对应（如 split / transcode / copy / cleanup） */
    String id();

    /**
     * 就地改写上下文。
     *
     * @throws Exception 任一环节失败都应抛出，由 runner 决定回滚与降级；抛出前不得留下半成品到目标目录
     */
    void apply(ExportActionContext ctx) throws Exception;
}
