package com.gamelist.variant;

import java.util.ArrayList;
import java.util.List;

/**
 * 流水线规划结果：从源工件到目标工件的模块序列 + 是否可执行。
 * <p>
 * 规划只做拓扑选择（步数优先、代价次之），不读文件内容，也不判断输入是否真的符合声明。
 * 那一部分由每个模块的 {@link ConversionModule#verify} 负责。
 */
public record PipelinePlan(Status status,
                           ArtifactKind source,
                           ArtifactKind target,
                           List<ConversionModule.ModuleSpec> specs,
                           String pipelineId,
                           String chainText,
                           String message) {

    public enum Status {
        /** 全部模块已实现，可直接执行 */
        READY,
        /** 路径存在，但有模块尚未实现 */
        INCOMPLETE,
        /** 没有可用路径 */
        UNSUPPORTED,
        /** 源工件已经是目标格式 */
        ALREADY
    }

    public PipelinePlan {
        specs = specs == null ? List.of() : List.copyOf(specs);
        message = message == null ? "" : message;
        pipelineId = pipelineId == null ? "none" : pipelineId;
        chainText = chainText == null ? "" : chainText;
    }

    public boolean runnable() {
        return status == Status.READY;
    }

    public List<String> moduleIds() {
        List<String> ids = new ArrayList<>();
        for (ConversionModule.ModuleSpec spec : specs) {
            ids.add(spec.id());
        }
        return ids;
    }

    public String missingModuleId() {
        for (ConversionModule.ModuleSpec spec : specs) {
            if (!spec.available()) {
                return spec.id();
            }
        }
        return null;
    }

    public static PipelinePlan already(ArtifactKind kind) {
        return new PipelinePlan(Status.ALREADY, kind, kind, List.of(), "none",
                kind.display(), "源文件已经是目标格式");
    }

    public static PipelinePlan unsupported(ArtifactKind source, ArtifactKind target, String message) {
        return new PipelinePlan(Status.UNSUPPORTED, source, target, List.of(), "none", "", message);
    }

    /** 把一条已找到的路径包装成计划（是否 READY 由模块状态决定） */
    public static PipelinePlan of(ArtifactKind source, ArtifactKind target, List<ConversionModule.ModuleSpec> specs) {
        List<ConversionModule.ModuleSpec> route = List.copyOf(specs);
        String id = route.isEmpty() ? "none" : joinIds(route);
        String chain = chainText(source, route);
        String missing = null;
        for (ConversionModule.ModuleSpec spec : route) {
            if (!spec.available()) {
                missing = spec.id();
                break;
            }
        }
        if (missing != null) {
            return new PipelinePlan(Status.INCOMPLETE, source, target, route, id, chain,
                    "产线包含尚未实现的模块 " + missing);
        }
        return new PipelinePlan(Status.READY, source, target, route, id, chain,
                "产线就绪：" + route.size() + " 步");
    }

    private static String joinIds(List<ConversionModule.ModuleSpec> route) {
        StringBuilder sb = new StringBuilder();
        for (ConversionModule.ModuleSpec spec : route) {
            if (sb.length() > 0) {
                sb.append('-');
            }
            sb.append(spec.id());
        }
        return sb.toString();
    }

    /** iso ▸[iso2cso]▸ cso */
    private static String chainText(ArtifactKind source, List<ConversionModule.ModuleSpec> route) {
        StringBuilder sb = new StringBuilder(source.code());
        for (ConversionModule.ModuleSpec spec : route) {
            sb.append(" ▸[").append(spec.id()).append("]▸ ").append(spec.output().code());
        }
        return sb.toString();
    }
}
