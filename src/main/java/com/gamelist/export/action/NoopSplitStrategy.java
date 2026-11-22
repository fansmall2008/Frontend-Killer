package com.gamelist.export.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 预留（空）分片策略：动作在该系统上“占了位但还没实现”。
 * <p>
 * 不改动交付清单 → copy 块按原样交付单文件，仅登记一条提示。PS3 分片先挂这里，
 * 以后要实现只需新增策略 bean 并把 {@code export-actions.json} 里对应 systemId 换掉。
 */
@Component
public class NoopSplitStrategy implements SplitStrategy {

    private static final Logger logger = LoggerFactory.getLogger(NoopSplitStrategy.class);

    @Override
    public String id() {
        return "noop";
    }

    @Override
    public void split(ExportActionContext ctx) {
        String note = "该系统分片尚未实现，已按单文件导出";
        ctx.addNote(note);
        logger.info("分片预留: systemId={}, {}", ctx.getSystemId(), note);
    }
}
