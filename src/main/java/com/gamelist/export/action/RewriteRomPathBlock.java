package com.gamelist.export.action;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 路径变更积木（chain 步骤 {@code rewrite-rom-path}）：确定数据文件里要记录的 ROM 文件名。
 * <p>
 * 有交付清单（分片/转换产出）→ 取第一条（主分片/转换产物）为目标名；
 * 无交付清单（原样交付）→ 用导出主流程按文件名模板算出的 {@code desiredBaseName}。
 * 真正的 {@code game.setPath} 由导出主流程读取 {@link ExportActionContext#getPrimaryDestName()} 完成，
 * 保证与 romExportPaths 的“单一计算源”一致。
 */
@Component
public class RewriteRomPathBlock implements ExportBlock {

    @Override
    public String id() {
        return "rewrite-rom-path";
    }

    @Override
    public void apply(ExportActionContext ctx) {
        List<ExportActionContext.Deliverable> deliverables = ctx.getDeliverables();
        if (deliverables.isEmpty()) {
            ctx.setPrimaryDestName(ctx.getDesiredBaseName());
        } else {
            ctx.setPrimaryDestName(deliverables.get(0).destName());
        }
    }
}
