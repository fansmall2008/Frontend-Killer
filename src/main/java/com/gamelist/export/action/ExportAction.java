package com.gamelist.export.action;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 导出动作描述符：一条 {@code export-actions.json} 声明的运行时视图。
 * <p>
 * 动作 = “模板可声明、用户在导出弹窗里选用的一件 ROM 后处理事情”（分片、导出时格式转换……）。
 * 本类只描述<b>存在性与约束</b>（作用系统、是否单平台、控件、积木链、参数），
 * 真正的行为由 {@link ExportBlock} 链与按 systemId 分派的 strategy 完成。
 *
 * @param id             动作标识（模板 variables 的 {@code action} 字段引用它）
 * @param label          展示名（缺省文案，模板可覆盖）
 * @param variableType   弹窗控件：{@code checkbox} | {@code select}
 * @param singlePlatform 是否强制单平台（分片/转换这类逐文件动作必须 true）
 * @param systemIds      作用系统（空=不限）；未绑定的平台选用应被阻止
 * @param chain          积木链：按顺序执行的 {@link ExportBlock#id()}
 * @param params         动作参数（如 splitSize）
 * @param strategies     systemId → 该系统的 transform strategy id（分片按系统分派）
 * @param optionsSource  select 候选来源：{@code pipeline}=按系统产线算出，空=无
 * @param note           说明（含未实现系统的降级语义）
 */
public record ExportAction(String id,
                           String label,
                           String variableType,
                           boolean singlePlatform,
                           Set<Integer> systemIds,
                           List<String> chain,
                           Map<String, String> params,
                           Map<Integer, String> strategies,
                           String optionsSource,
                           String note) {

    public ExportAction {
        systemIds = systemIds == null ? Set.of() : Set.copyOf(systemIds);
        chain = chain == null ? List.of() : List.copyOf(chain);
        params = params == null ? Map.of() : Map.copyOf(params);
        strategies = strategies == null ? Map.of() : Map.copyOf(strategies);
        note = note == null ? "" : note;
        variableType = variableType == null || variableType.isBlank() ? "checkbox"
                : variableType.trim().toLowerCase(Locale.ROOT);
        optionsSource = optionsSource == null ? "" : optionsSource.trim().toLowerCase(Locale.ROOT);
    }

    /** 该系统是否在动作作用范围内（scope 为空视为不限系统） */
    public boolean supportsSystem(int systemId) {
        return systemIds.isEmpty() || systemIds.contains(systemId);
    }

    public String param(String key, String fallback) {
        String value = params.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 该系统分派到的 strategy id；未声明返回 null（由调用方决定跳过/降级） */
    public String strategyFor(int systemId) {
        return strategies.get(systemId);
    }

    /** select 候选是否来自系统产线 */
    public boolean optionsFromPipeline() {
        return "pipeline".equals(optionsSource);
    }

    /** 便捷构造：仅 id + 可变集合，供加载器填充 */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** 可变装配器 */
    public static final class Builder {
        private final String id;
        private String label = "";
        private String variableType = "checkbox";
        private boolean singlePlatform;
        private final Set<Integer> systemIds = new LinkedHashSet<>();
        private final List<String> chain = new java.util.ArrayList<>();
        private final Map<String, String> params = new LinkedHashMap<>();
        private final Map<Integer, String> strategies = new LinkedHashMap<>();
        private String optionsSource = "";
        private String note = "";

        private Builder(String id) {
            this.id = id;
        }

        public Builder label(String v) { this.label = v; return this; }
        public Builder variableType(String v) { this.variableType = v; return this; }
        public Builder singlePlatform(boolean v) { this.singlePlatform = v; return this; }
        public Builder addSystemId(int v) { this.systemIds.add(v); return this; }
        public Builder addChainStep(String v) { this.chain.add(v); return this; }
        public Builder param(String k, String v) { this.params.put(k, v); return this; }
        public Builder strategy(int systemId, String strategyId) { this.strategies.put(systemId, strategyId); return this; }
        public Builder optionsSource(String v) { this.optionsSource = v; return this; }
        public Builder note(String v) { this.note = v; return this; }

        public ExportAction build() {
            return new ExportAction(id, label, variableType, singlePlatform, systemIds,
                    chain, params, strategies, optionsSource, note);
        }
    }
}
