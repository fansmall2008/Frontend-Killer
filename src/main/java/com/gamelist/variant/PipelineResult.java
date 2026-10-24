package com.gamelist.variant;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次流水线执行的结果。
 * <p>
 * 只有 {@link Status#FAILED} 是真失败（转换出错）；其余都是“结论”：
 * 没路可走、缺模块、输入被拒、数据本身不可无损转换。
 */
public record PipelineResult(Status status,
                             PipelinePlan plan,
                             DiscArtifact output,
                             Analysis analysis,
                             String failedModule,
                             String message,
                             List<String> warnings) {

    public enum Status {
        /** 产出已落盘并通过自检 */
        SUCCESS,
        /** 数据结论：输入不可安全转换（多轨、加密、索引损坏等） */
        BLOCKED,
        /** 模块拒收输入，输入不是声明的形态 */
        REJECTED,
        /** 转换过程出错 */
        FAILED
    }

    public PipelineResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        message = message == null ? "" : message;
        failedModule = failedModule == null ? "" : failedModule;
    }

    public boolean success() {
        return status == Status.SUCCESS;
    }

    /** 给用户看的完整说明：结论 + 阻断原因 + 风险提示 */
    public String describe() {
        StringBuilder sb = new StringBuilder(message);
        if (analysis != null && !analysis.blockerText().isBlank()) {
            if (sb.length() > 0) {
                sb.append("：");
            }
            sb.append(analysis.blockerText());
        }
        List<String> warn = new ArrayList<>(warnings);
        if (analysis != null) {
            warn.addAll(analysis.warnings());
        }
        if (!warn.isEmpty()) {
            sb.append("（").append(String.join("；", warn)).append("）");
        }
        return sb.toString();
    }

    public static PipelineResult success(PipelinePlan plan, DiscArtifact output, Analysis analysis,
                                         List<String> warnings) {
        return new PipelineResult(Status.SUCCESS, plan, output, analysis, "", "转换完成", warnings);
    }

    public static PipelineResult blocked(PipelinePlan plan, Analysis analysis, String message) {
        return new PipelineResult(Status.BLOCKED, plan, null, analysis, "", message,
                analysis == null ? List.of() : analysis.warnings());
    }

    public static PipelineResult rejected(PipelinePlan plan, String message) {
        return new PipelineResult(Status.REJECTED, plan, null, null, "", message, List.of());
    }

    public static PipelineResult failed(PipelinePlan plan, Analysis analysis, String moduleId, String message) {
        return new PipelineResult(Status.FAILED, plan, null, analysis, moduleId, message,
                analysis == null ? List.of() : analysis.warnings());
    }

    /** 规划阶段的结论直接转成结果，避免上层重复判断 */
    public static PipelineResult fromPlan(PipelinePlan plan) {
        switch (plan.status()) {
            case INCOMPLETE:
                return new PipelineResult(Status.BLOCKED, plan, null, null, plan.missingModuleId(),
                        "缺少模块 " + plan.missingModuleId() + "，产线尚不可用", List.of());
            case UNSUPPORTED:
                return new PipelineResult(Status.BLOCKED, plan, null, null, "", plan.message(), List.of());
            case ALREADY:
                return new PipelineResult(Status.BLOCKED, plan, null, null, "",
                        "源文件已经是目标格式，无需转换", List.of());
            default:
                return new PipelineResult(Status.BLOCKED, plan, null, null, "", plan.message(), List.of());
        }
    }
}
