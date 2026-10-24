package com.gamelist.variant;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 流水线模块：一次转换，输入一种工件、输出另一种工件。
 * <p>
 * 模块必须自己完成四件事：
 * <ol>
 *   <li>{@link #verify} 判断输入（轻量，扩展名只作提示，魔数与结构才是依据）</li>
 *   <li>{@link #analyze} 分析输入（读出转换所需事实与风险）</li>
 *   <li>{@link #convert} 转换</li>
 *   <li>写出输出（由 {@code convert} 落盘，路径由调度器给出，必须是原子写入）</li>
 * </ol>
 * 模块只负责“怎么转”，不知道上游是谁、下游去哪；具体怎么连接由 {@link PipelinePlanner} 计算。
 * 未实现的模块不写 Java 类，只在 {@code rules/variants/pipeline-modules.json} 里声明占位（RESERVED），
 * 等实现类以同 id 出现时自动转为 AVAILABLE。
 */
public interface ConversionModule {

    ModuleSpec spec();

    /**
     * 1 输入格式判断：只读头部与文件长度，不解析内容。
     */
    Verdict verify(DiscArtifact input);

    /**
     * 2 输入格式分析：读有限字节，产出转换所需事实；{@link Analysis#usable()} 为 false 时调度器会中止。
     */
    Analysis analyze(DiscArtifact input) throws IOException;

    /**
     * 3+4 转换并写出。实现必须“写临时文件 → 校验 → 原子改名”，失败时不得留下半成品。
     *
     * @param input    输入工件
     * @param analysis {@link #analyze} 的结论，转换阶段不重复解析
     * @param output   输出文件路径（父目录已创建）
     * @param progress 进度回调，按输入字节数上报
     * @return 输出工件的描述
     */
    DiscArtifact convert(DiscArtifact input, Analysis analysis, Path output, ProgressSink progress)
            throws IOException;

    /** 进度回调 */
    @FunctionalInterface
    interface ProgressSink {
        void update(long done, long total);

        ProgressSink NOOP = (done, total) -> {
        };
    }

    /** 模块实现状态 */
    enum ModuleState {
        /** 已实现，可执行 */
        AVAILABLE,
        /** 已预留位置，尚未实现 */
        RESERVED
    }

    /**
     * 模块描述符。
     *
     * @param id          模块标识（如 iso2cso）
     * @param input       输入工件类型
     * @param output      输出工件类型
     * @param cost        成本权重，用于在多条路径中选择更省的一条
     * @param lossless    是否无损；有损模块默认不会被自动选中
     * @param state       实现状态
     * @param note        说明；预留模块在这里写清“要实现什么”
     * @param externalDeps 外部依赖（如 chdman）
     */
    record ModuleSpec(String id,
                      ArtifactKind input,
                      ArtifactKind output,
                      int cost,
                      boolean lossless,
                      ModuleState state,
                      String note,
                      List<String> externalDeps) {

        public ModuleSpec {
            externalDeps = externalDeps == null ? List.of() : List.copyOf(externalDeps);
            note = note == null ? "" : note;
        }

        public boolean available() {
            return state == ModuleState.AVAILABLE;
        }

        public String arrow() {
            return input.display() + " → " + output.display();
        }
    }
}
