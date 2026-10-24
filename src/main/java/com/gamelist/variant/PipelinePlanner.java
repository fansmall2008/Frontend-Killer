package com.gamelist.variant;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 流水线规划器：在模块图上找“最简路径”。
 * <p>
 * “最简”按字典序比较 <b>步数 → 总代价</b>：少一步永远比省一点代价更重要，
 * 因为每一步都要落盘一次临时文件。
 * <p>
 * 先在“全部模块可用”的子图上找；找不到再放宽到含预留模块的全图，
 * 这样界面能区分“做不到”和“还没做”。
 */
@Component
public class PipelinePlanner {

    private final ModuleCatalog catalog;

    public PipelinePlanner(ModuleCatalog catalog) {
        this.catalog = catalog;
    }

    public PipelinePlan plan(ArtifactKind source, ArtifactKind target) {
        if (source == null || target == null
                || source == ArtifactKind.UNKNOWN || target == ArtifactKind.UNKNOWN) {
            return PipelinePlan.unsupported(source, target,
                    source == ArtifactKind.UNKNOWN ? "源文件形态无法识别，无法规划产线" : "目标格式无法识别");
        }
        if (source == target) {
            return PipelinePlan.already(source);
        }

        List<ConversionModule.ModuleSpec> ready = shortest(source, target, true);
        if (!ready.isEmpty()) {
            return PipelinePlan.of(source, target, ready);
        }
        List<ConversionModule.ModuleSpec> partial = shortest(source, target, false);
        if (!partial.isEmpty()) {
            return PipelinePlan.of(source, target, partial);
        }
        return PipelinePlan.unsupported(source, target,
                "没有从 " + source.display() + " 到 " + target.display() + " 的产线");
    }

    /**
     * 单个模块的可达性：某些格式系统声明允许，但图里根本没有模块，规划结果就是 UNSUPPORTED。
     */
    public List<ConversionModule.ModuleSpec> route(ArtifactKind source, ArtifactKind target) {
        PipelinePlan plan = plan(source, target);
        return plan.specs();
    }

    /**
     * 松弛求最短路。节点数很少（枚举规模），用有限轮松弛比堆 Dijkstra 更短，也天然拒绝环。
     */
    private List<ConversionModule.ModuleSpec> shortest(ArtifactKind source, ArtifactKind target,
                                                       boolean availableOnly) {
        Map<ArtifactKind, Node> best = new EnumMap<>(ArtifactKind.class);
        best.put(source, new Node(0, 0L, null, null));

        List<ConversionModule.ModuleSpec> edges = availableOnly
                ? availableEdges()
                : catalog.specs();

        int rounds = ArtifactKind.values().length + 1;
        for (int round = 0; round < rounds; round++) {
            boolean changed = false;
            for (ConversionModule.ModuleSpec edge : edges) {
                if (edge == null || edge.input() == null || edge.output() == null) {
                    continue;
                }
                Node from = best.get(edge.input());
                if (from == null) {
                    continue;
                }
                Node candidate = new Node(from.steps + 1, from.cost + Math.max(edge.cost(), 0), edge, from);
                Node existing = best.get(edge.output());
                if (existing == null || better(candidate, existing)) {
                    best.put(edge.output(), candidate);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        Node end = best.get(target);
        if (end == null || end.prev == null) {
            return List.of();
        }
        List<ConversionModule.ModuleSpec> route = new ArrayList<>();
        for (Node cursor = end; cursor != null && cursor.edge != null; cursor = cursor.prev) {
            route.add(0, cursor.edge);
        }
        return route;
    }

    private List<ConversionModule.ModuleSpec> availableEdges() {
        List<ConversionModule.ModuleSpec> result = new ArrayList<>();
        for (ConversionModule.ModuleSpec spec : catalog.specs()) {
            if (spec.available()) {
                result.add(spec);
            }
        }
        return result;
    }

    /** 步数优先，其次总代价 */
    private static boolean better(Node candidate, Node existing) {
        if (candidate.steps != existing.steps) {
            return candidate.steps < existing.steps;
        }
        return candidate.cost < existing.cost;
    }

    private static final class Node {
        private final int steps;
        private final long cost;
        private final ConversionModule.ModuleSpec edge;
        private final Node prev;

        private Node(int steps, long cost, ConversionModule.ModuleSpec edge, Node prev) {
            this.steps = steps;
            this.cost = cost;
            this.edge = edge;
            this.prev = prev;
        }
    }
}
