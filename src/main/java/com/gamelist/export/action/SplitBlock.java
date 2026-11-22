package com.gamelist.export.action;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 分片积木（chain 步骤 {@code split}）：按 systemId 从动作声明里取 strategy id 分派。
 * <p>
 * Wii→{@link WitSplitStrategy}，PS3→{@link NoopSplitStrategy}；找不到策略时退化为不改动
 * （按原样交付）并记提示，保证“预留系统”不影响导出成功。
 */
@Component
public class SplitBlock implements ExportBlock {

    private static final Logger logger = LoggerFactory.getLogger(SplitBlock.class);

    private final Map<String, SplitStrategy> strategies = new LinkedHashMap<>();

    public SplitBlock(List<SplitStrategy> beans) {
        if (beans != null) {
            for (SplitStrategy strategy : beans) {
                if (strategy != null && strategy.id() != null) {
                    strategies.put(strategy.id().trim().toLowerCase(Locale.ROOT), strategy);
                }
            }
        }
    }

    @Override
    public String id() {
        return "split";
    }

    @Override
    public void apply(ExportActionContext ctx) throws Exception {
        String strategyId = ctx.getStrategyFor(ctx.getSystemId());
        if (strategyId == null || strategyId.isBlank()) {
            ctx.addNote("该系统未配置分片策略，按单文件导出");
            return;
        }
        SplitStrategy strategy = strategies.get(strategyId.trim().toLowerCase(Locale.ROOT));
        if (strategy == null) {
            logger.warn("未找到分片策略 bean: id={}（systemId={}）", strategyId, ctx.getSystemId());
            ctx.addNote("分片策略未就绪（" + strategyId + "），按单文件导出");
            return;
        }
        strategy.split(ctx);
    }
}
