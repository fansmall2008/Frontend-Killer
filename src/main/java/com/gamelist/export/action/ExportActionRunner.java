package com.gamelist.export.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 动作链执行器：按 {@link ExportAction#chain()} 顺序驱动 {@link ExportBlock}，改写同一个上下文。
 * <p>
 * 未知 block id 只 WARN 跳过（允许 JSON 先声明、Java 后补实现，和模块目录同一哲学）；
 * 无论正常收尾还是中途抛异常，finally 都兜底回收暂存目录，不留临时文件。
 */
@Component
public class ExportActionRunner {

    private static final Logger logger = LoggerFactory.getLogger(ExportActionRunner.class);

    private final Map<String, ExportBlock> blocks = new LinkedHashMap<>();

    public ExportActionRunner(List<ExportBlock> beans) {
        if (beans != null) {
            for (ExportBlock block : beans) {
                if (block != null && block.id() != null) {
                    blocks.put(block.id().trim().toLowerCase(Locale.ROOT), block);
                }
            }
        }
    }

    public void run(ExportAction action, ExportActionContext ctx) throws Exception {
        if (action == null || ctx == null) {
            return;
        }
        runSteps(action.chain(), ctx);
    }

    /** 按合并后的步骤序列驱动积木（多动作折叠成一条链时用） */
    public void runSteps(List<String> steps, ExportActionContext ctx) throws Exception {
        if (ctx == null || steps == null || steps.isEmpty()) {
            return;
        }
        try {
            for (String step : steps) {
                ExportBlock block = blocks.get(lower(step));
                if (block == null) {
                    logger.warn("动作链引用了未实现的积木: {}（跳过）", step);
                    continue;
                }
                block.apply(ctx);
            }
        } finally {
            ExportBlock cleanup = blocks.get("cleanup");
            if (cleanup != null && ctx.stagingDir() != null) {
                try {
                    cleanup.apply(ctx);
                } catch (Exception e) {
                    logger.debug("兜底清理暂存失败: {}", e.getMessage());
                }
            }
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
