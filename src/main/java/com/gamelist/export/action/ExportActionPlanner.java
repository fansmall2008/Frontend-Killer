package com.gamelist.export.action;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.gamelist.model.TemplateV3;

/**
 * 动作规划器：把“模板声明的动作绑定 + 用户填写值 + 当前系统”翻译成一次导出的执行计划，
 * 并在<b>声明期</b>给出绑定约束（供选模板校验，无论用户是否勾选）。
 */
@Component
public class ExportActionPlanner {

    private static final Logger logger = LoggerFactory.getLogger(ExportActionPlanner.class);

    /** 公共尾巴：所有交付类动作共用的收尾积木，合并时只接一次 */
    private static final Set<String> TAIL = Set.of("rewrite-rom-path", "copy", "cleanup");
    /** select 里代表“维持原样/不转换”的取值 */
    private static final Set<String> NO_CONVERSION = Set.of("", "none", "original", "off", "0");

    /** 转换积木的确定先后：先转格式，再对结果分片 */
    private static final List<String> TRANSFORM_ORDER = List.of("transcode", "split");

    private final ExportActionCatalog catalog;

    public ExportActionPlanner(ExportActionCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * 依据用户填写值构造运行时计划；无启用动作时返回空计划（导出走原路径）。
     *
     * @param template  v3 导出模板
     * @param userVars  弹窗收集的用户填写值（变量名 → 值）
     * @param systemId  当前导出平台的 systemId
     */
    public ExportActionPlan plan(TemplateV3 template, Map<String, String> userVars, Integer systemId) {
        if (template == null || systemId == null) {
            return ExportActionPlan.empty();
        }
        Map<String, String> bindings = template.getActionBindings();
        if (bindings.isEmpty()) {
            return ExportActionPlan.empty();
        }
        ExportActionPlan.ExportActionPlanBuilder builder = ExportActionPlan.builder();
        Set<String> transforms = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : bindings.entrySet()) {
            String varName = entry.getKey();
            ExportAction action = catalog.get(entry.getValue());
            if (action == null) {
                logger.warn("模板变量 {} 引用了未定义的动作 {}，忽略", varName, entry.getValue());
                continue;
            }
            if (!action.supportsSystem(systemId)) {
                continue; // 该系统不在作用范围（正常应已被绑定校验拦下）
            }
            String value = userVars != null ? userVars.get(varName) : null;
            if (isCheckbox(action)) {
                if (truthy(value)) {
                    builder.addAction(action);
                    collectTransforms(action, transforms);
                }
            } else if (isSelect(action)) {
                String format = normalizeFormat(value);
                if (!format.isEmpty()) {
                    builder.addAction(action);
                    builder.select("format", format);
                    collectTransforms(action, transforms);
                }
            }
        }
        if (transforms.isEmpty()) {
            return ExportActionPlan.empty();
        }
        List<String> steps = new ArrayList<>();
        for (String known : TRANSFORM_ORDER) {
            if (transforms.remove(known)) {
                steps.add(known);
            }
        }
        steps.addAll(transforms); // 其余未知转换积木按声明顺序补后
        steps.addAll(TAIL_ORDER);
        return builder.steps(steps).build();
    }

    private static final List<String> TAIL_ORDER = List.of("rewrite-rom-path", "copy", "cleanup");

    private void collectTransforms(ExportAction action, Set<String> out) {
        for (String step : action.chain()) {
            if (!TAIL.contains(step)) {
                out.add(step);
            }
        }
    }

    /**
     * 声明期绑定约束：只要模板引用了动作（无论用户是否勾选），就据此限制可选平台。
     */
    public ActionConstraints constraints(TemplateV3 template) {
        if (template == null) {
            return ActionConstraints.none();
        }
        Map<String, String> bindings = template.getActionBindings();
        if (bindings.isEmpty()) {
            return ActionConstraints.none();
        }
        Set<Integer> supported = null; // null = 尚不受限（全部动作 scope 为空）
        boolean single = false;
        boolean any = false;
        for (String actionId : bindings.values()) {
            ExportAction action = catalog.get(actionId);
            if (action == null) {
                continue;
            }
            any = true;
            if (action.singlePlatform()) {
                single = true;
            }
            if (!action.systemIds().isEmpty()) {
                if (supported == null) {
                    supported = new LinkedHashSet<>(action.systemIds());
                } else {
                    supported.retainAll(action.systemIds()); // 需同时支持所有动作 → 取交集
                }
            }
        }
        if (!any) {
            return ActionConstraints.none();
        }
        return new ActionConstraints(true, single, supported == null ? null : Set.copyOf(supported));
    }

    private static boolean isCheckbox(ExportAction action) {
        return "checkbox".equalsIgnoreCase(action.variableType());
    }

    private static boolean isSelect(ExportAction action) {
        return "select".equalsIgnoreCase(action.variableType());
    }

    private static String normalizeFormat(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return NO_CONVERSION.contains(v) ? "" : v;
    }

    private static boolean truthy(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return !v.isEmpty() && !v.equals("false") && !v.equals("0") && !v.equals("off") && !v.equals("no");
    }

    /**
     * 模板的动作绑定约束。
     *
     * @param hasActions            是否引用了任何动作
     * @param requiresSinglePlatform 是否强制单平台
     * @param supportedSystemIds    允许的平台 systemId 集合；{@code null}=不限系统
     */
    public record ActionConstraints(boolean hasActions, boolean requiresSinglePlatform,
                                    Set<Integer> supportedSystemIds) {

        public static ActionConstraints none() {
            return new ActionConstraints(false, false, null);
        }

        /** 该平台是否被允许使用本模板 */
        public boolean allowsSystem(int systemId) {
            return supportedSystemIds == null || supportedSystemIds.contains(systemId);
        }
    }
}
