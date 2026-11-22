package com.gamelist.export.action;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.gamelist.variant.ArtifactKind;
import com.gamelist.variant.DiscArtifact;
import com.gamelist.variant.DiscImageProbe;
import com.gamelist.variant.PipelinePlan;
import com.gamelist.variant.PipelinePlanner;
import com.gamelist.variant.PipelineResult;
import com.gamelist.variant.PipelineRunner;
import com.gamelist.variant.SystemPipelineLibrary;

/**
 * 导出时格式转换积木（chain 步骤 {@code transcode}）：把当前源文件转成用户选定的目标格式。
 * <p>
 * 完全复用既有产线：{@link DiscImageProbe} 认形态 → {@link SystemPipelineLibrary} 校验该系统
 * 允许这条路由 → {@link PipelinePlanner} 规划 → {@link PipelineRunner} 执行。产物落暂存目录并登记为
 * 交付项；任一环不满足（不支持/已是目标格式/缺模块/转换失败）都只记提示、不改源，导出按原文件继续——
 * 转换是“锦上添花”，绝不因为转不动就让整次导出失败。
 */
@Component
public class TranscodeBlock implements ExportBlock {

    private static final Logger logger = LoggerFactory.getLogger(TranscodeBlock.class);

    private final DiscImageProbe probe;
    private final SystemPipelineLibrary pipelineLibrary;
    private final PipelinePlanner planner;
    private final PipelineRunner runner;

    public TranscodeBlock(DiscImageProbe probe, SystemPipelineLibrary pipelineLibrary,
                          PipelinePlanner planner, PipelineRunner runner) {
        this.probe = probe;
        this.pipelineLibrary = pipelineLibrary;
        this.planner = planner;
        this.runner = runner;
    }

    @Override
    public String id() {
        return "transcode";
    }

    @Override
    public void apply(ExportActionContext ctx) throws Exception {
        String format = ctx.getSelection("format");
        if (format.isEmpty()) {
            return; // 用户选了“不转换”
        }
        ArtifactKind targetKind = SystemPipelineLibrary.targetKind(format);
        if (targetKind == null) {
            ctx.addNote("目标格式无法识别（" + format + "），按原格式导出");
            return;
        }
        Path source = ctx.getSource();
        if (source == null || !Files.isRegularFile(source)) {
            ctx.addNote("源文件不存在，跳过格式转换");
            return;
        }
        DiscArtifact input = probe.probe(source);
        if (input.kind() == targetKind) {
            return; // 已经是目标格式
        }
        if (!pipelineLibrary.allows(ctx.getSystemId(), input.kind(), format)) {
            ctx.addNote("系统产线不支持 " + input.kind().display() + " → " + format + "，按原格式导出");
            return;
        }
        PipelinePlan plan = planner.plan(input.kind(), targetKind);
        if (plan.status() != PipelinePlan.Status.READY) {
            ctx.addNote("无可用产线（" + plan.message() + "），按原格式导出");
            return;
        }
        if (ctx.stagingDir() == null) {
            ctx.setStagingDir(Files.createTempDirectory("fk-convert-"));
        }
        Path out = ctx.staged("out." + targetKind.extension());
        PipelineResult result = runner.run(plan, input, out, ctx.stagingDir(), ctx.getProgress());
        if (!result.success() || out == null || !Files.isRegularFile(out) || Files.size(out) <= 0) {
            ctx.addNote("格式转换未完成（" + result.describe() + "），按原格式导出");
            return;
        }
        ctx.setSource(out);
        ctx.setSourceExtension("." + targetKind.extension());
        ctx.clearDeliverables();
        ctx.addDeliverable(out, ctx.desiredStem() + "." + targetKind.extension());
        logger.info("导出时格式转换: {} → {}（{}）", input.kind().display(), targetKind.display(), format);
    }
}
