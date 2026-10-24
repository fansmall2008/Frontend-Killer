package com.gamelist.variant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 流水线执行器：按计划逐步运行模块。
 * <p>
 * 每一步都走完模块自己的四段：verify → analyze → convert → 输出回读。
 * 首段 verify 若返回 MISMATCH，说明探测和实际形态不一致，这里只重算一次路径，
 * 避免“扩展名是 .bin、内容其实是 ISO”这种情况直接失败。
 */
@Component
public class PipelineRunner {

    private static final Logger logger = LoggerFactory.getLogger(PipelineRunner.class);

    private final ModuleCatalog catalog;
    private final PipelinePlanner planner;

    public PipelineRunner(ModuleCatalog catalog, PipelinePlanner planner) {
        this.catalog = catalog;
        this.planner = planner;
    }

    /**
     * @param outputFile 最终产物路径（父目录由调用方创建）
     * @param workDir    中间产物目录，执行结束会被清理
     */
    public PipelineResult run(PipelinePlan plan, DiscArtifact input, Path outputFile, Path workDir,
                              ConversionModule.ProgressSink progress) {
        if (plan == null) {
            return PipelineResult.blocked(null, null, "没有可执行的产线");
        }
        if (plan.status() != PipelinePlan.Status.READY) {
            return PipelineResult.fromPlan(plan);
        }
        if (input == null || input.path() == null || !Files.isRegularFile(input.path())) {
            return PipelineResult.rejected(plan, "源文件不存在: " + (input == null ? "null" : input.path()));
        }
        PipelineResult result = execute(plan, input, outputFile, workDir, progress, true);
        if (result.status() != PipelineResult.Status.REJECTED || result.message().isBlank()) {
            return result;
        }
        return result;
    }

    private PipelineResult execute(PipelinePlan plan, DiscArtifact input, Path outputFile, Path workDir,
                                   ConversionModule.ProgressSink progress, boolean allowReplan) {
        List<ConversionModule.ModuleSpec> specs = plan.specs();
        List<Path> intermediates = new ArrayList<>();
        Analysis lastAnalysis = null;
        DiscArtifact cursor = input;

        try {
            for (int i = 0; i < specs.size(); i++) {
                ConversionModule.ModuleSpec spec = specs.get(i);
                ConversionModule module = catalog.implementation(spec.id());
                if (module == null || !spec.available()) {
                    return PipelineResult.blocked(plan, lastAnalysis,
                            "模块 " + spec.id() + " 尚未实现，无法完成这条产线");
                }

                Verdict verdict = module.verify(cursor);
                if (verdict == null) {
                    return PipelineResult.rejected(plan, "模块 " + spec.id() + " 未给出判断结论");
                }
                if (verdict.result() == Verdict.Result.MISMATCH && allowReplan) {
                    PipelinePlan replanned = replan(plan, cursor, verdict);
                    if (replanned == null) {
                        return PipelineResult.rejected(plan, "输入形态与产线要求不符：" + verdict.reason());
                    }
                    return execute(replanned, cursor, outputFile, workDir, progress, false);
                }
                if (!verdict.accepted()) {
                    return PipelineResult.rejected(plan,
                            "模块 " + spec.id() + " 拒收输入：" + verdict.reason());
                }

                lastAnalysis = module.analyze(cursor);
                if (lastAnalysis == null || !lastAnalysis.usable()) {
                    return PipelineResult.blocked(plan, lastAnalysis,
                            "输入不可无损转换");
                }

                boolean last = i == specs.size() - 1;
                Path stepOutput = last ? outputFile : workDir.resolve(stepName(i, spec));
                if (!last) {
                    intermediates.add(stepOutput);
                }
                logger.info("流水线执行 {} ({}): {} → {}", plan.pipelineId(), spec.id(),
                        cursor.path().getFileName(), stepOutput.getFileName());

                DiscArtifact produced = module.convert(cursor, lastAnalysis, stepOutput, subProgress(progress, i, specs.size()));
                if (produced == null || produced.path() == null || !Files.isRegularFile(produced.path())) {
                    return PipelineResult.failed(plan, lastAnalysis, spec.id(),
                            "模块 " + spec.id() + " 未产出文件");
                }
                long size = Files.size(produced.path());
                if (size <= 0) {
                    return PipelineResult.failed(plan, lastAnalysis, spec.id(),
                            "模块 " + spec.id() + " 产出的文件为空");
                }
                // 输出自证：最后一步重新探测，确认落盘物确实是声明的形态
                DiscArtifact verified = last ? verifyOutput(produced, spec) : produced;
                if (verified == null) {
                    return PipelineResult.failed(plan, lastAnalysis, spec.id(),
                            "输出文件与预期形态不符：" + produced.path().getFileName());
                }
                cursor = verified;
            }
            return PipelineResult.success(plan, cursor, lastAnalysis, List.of());
        } catch (IOException e) {
            logger.warn("流水线执行失败: {}", e.getMessage());
            deleteQuietly(outputFile);
            return PipelineResult.failed(plan, lastAnalysis, "", e.getMessage());
        } catch (RuntimeException e) {
            logger.warn("流水线执行异常", e);
            deleteQuietly(outputFile);
            return PipelineResult.failed(plan, lastAnalysis, "", String.valueOf(e.getMessage()));
        } finally {
            for (Path intermediate : intermediates) {
                deleteQuietly(intermediate);
            }
        }
    }

    /** 首段探测与模块判断不一致：按模块给出的实际形态重算一次路径 */
    private PipelinePlan replan(PipelinePlan plan, DiscArtifact input, Verdict verdict) {
        ArtifactKind actual = verdict.actualKind();
        if (actual == null) {
            return null;
        }
        logger.info("输入形态修正: 探测为 {}，模块判断为 {}，重新规划", input.kind(), actual);
        PipelinePlan replanned = planner.plan(actual, plan.target());
        return replanned.status() == PipelinePlan.Status.READY ? replanned : null;
    }

    /** 输出回读：魔数必须与新形态一致，避免“写了半截”的文件被当成功 */
    private DiscArtifact verifyOutput(DiscArtifact produced, ConversionModule.ModuleSpec spec) {
        ArtifactKind expected = spec.output();
        Path path = produced.path();
        boolean ok;
        switch (expected) {
            case CSO:
                ok = ImageEvidence.isCiso(path);
                break;
            case ZSO:
                ok = ImageEvidence.isZiso(path);
                break;
            case CHD:
                ok = ImageEvidence.isChd(path);
                break;
            case PBP:
                ok = ImageEvidence.isPbp(path);
                break;
            case ISO_2048:
                // 无 PVD 的 ISO 也合法（素材盘、加密盘），只要求扇区对齐
                ok = sizeOf(path) % ImageEvidence.SECTOR_2048 == 0;
                break;
            case RAW_2352:
                ok = sizeOf(path) % ImageEvidence.SECTOR_2352 == 0;
                break;
            default:
                ok = true;
        }
        return ok ? produced : null;
    }

    private static String stepName(int index, ConversionModule.ModuleSpec spec) {
        String ext = spec.output().extension();
        return "step" + (index + 1) + (ext.isBlank() ? "" : "." + ext);
    }

    private static ConversionModule.ProgressSink subProgress(ConversionModule.ProgressSink parent,
                                                             int index, int total) {
        if (parent == null || total <= 0) {
            return ConversionModule.ProgressSink.NOOP;
        }
        return (done, all) -> {
            double fraction = all <= 0 ? 1.0 : Math.min(1.0, (double) done / all);
            parent.update((long) ((index + fraction) * 1000), total * 1000L);
        };
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1;
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.debug("删除临时文件失败: {} ({})", path, e.getMessage());
        }
    }

    /** 清理工作目录中的残留（进程异常退出时用） */
    public void cleanWorkDir(Path workDir) {
        if (workDir == null || !Files.isDirectory(workDir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(workDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(PipelineRunner::deleteQuietly);
        } catch (IOException e) {
            logger.debug("清理工作目录失败: {} ({})", workDir, e.getMessage());
        }
    }
}
