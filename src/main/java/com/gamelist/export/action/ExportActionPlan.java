package com.gamelist.export.action;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamelist.model.Game;
import com.gamelist.variant.ConversionModule;

/**
 * 一次导出（单系统）的动作执行计划：把用户勾选/选中的多个动作折叠成<b>一条</b>积木链。
 * <p>
 * 为什么要合并：分片和格式转换各自的链都以“rewrite-rom-path → copy → cleanup”收尾，
 * 若分别整跑会重复交付。这里把各动作的<b>转换积木</b>（transcode / split）串起来，
 * 再接<b>一次</b>公共尾巴，得到确定顺序：{@code transcode → split → rewrite-rom-path → copy → cleanup}。
 */
public final class ExportActionPlan {

    private static final ExportActionPlan EMPTY = new ExportActionPlan();

    private final List<ExportAction> activeActions = new ArrayList<>();
    private final List<String> steps = new ArrayList<>();
    private final Map<String, String> params = new LinkedHashMap<>();
    private final Map<Integer, String> strategies = new LinkedHashMap<>();
    private final Map<String, String> selections = new LinkedHashMap<>();

    static ExportActionPlan empty() {
        return EMPTY;
    }

    static ExportActionPlanBuilder builder() {
        return new ExportActionPlanBuilder();
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public List<ExportAction> getActiveActions() {
        return List.copyOf(activeActions);
    }

    public List<String> getSteps() {
        return List.copyOf(steps);
    }

    /** 建一个已注入合并配置（params/strategies/selections）的上下文 */
    public ExportActionContext newContext(Game game, int systemId, Path source, String sourceExtension,
                                          Path targetDir, String desiredBaseName,
                                          ConversionModule.ProgressSink progress) {
        ExportActionContext ctx = new ExportActionContext(game, systemId, source, sourceExtension,
                targetDir, desiredBaseName, progress);
        ctx.getParams().putAll(params);
        ctx.getStrategies().putAll(strategies);
        ctx.getSelections().putAll(selections);
        return ctx;
    }

    /** 计划装配器 */
    static final class ExportActionPlanBuilder {
        private final ExportActionPlan plan = new ExportActionPlan();

        ExportActionPlanBuilder addAction(ExportAction action) {
            plan.activeActions.add(action);
            plan.params.putAll(action.params());
            plan.strategies.putAll(action.strategies());
            return this;
        }

        ExportActionPlanBuilder select(String key, String value) {
            if (value != null && !value.isBlank()) {
                plan.selections.put(key, value);
            }
            return this;
        }

        ExportActionPlanBuilder steps(List<String> steps) {
            plan.steps.addAll(steps);
            return this;
        }

        ExportActionPlan build() {
            return plan;
        }
    }
}
