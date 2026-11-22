package com.gamelist.export.action;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.gamelist.variant.AtomicOutput;

/**
 * 交付积木（chain 步骤 {@code copy}）：把整组产物原子落到目标目录。
 * <p>
 * 有交付清单则整组（主分片 + 兄弟分片 / 转换产物）一起落；无清单则把源文件原样落成
 * {@code primaryDestName}。用 {@link AtomicOutput#commitAll} 保证“要么整组到位、要么一个不落”，
 * 避免 loader 读到残缺分片。落盘成功后标记 {@code takenOver}，导出主流程不再自行 copyFile。
 */
@Component
public class CopyBlock implements ExportBlock {

    private static final Logger logger = LoggerFactory.getLogger(CopyBlock.class);

    @Override
    public String id() {
        return "copy";
    }

    @Override
    public void apply(ExportActionContext ctx) throws Exception {
        List<ExportActionContext.Deliverable> deliverables = ctx.getDeliverables();
        if (deliverables.isEmpty()) {
            // 无实际产物（未达分片条件 / 系统未实现 / 用户选了不转换）→ 不接管，交回导出主流程按原样拷贝
            return;
        }
        Path targetDir = ctx.getTargetDir();
        if (targetDir == null) {
            throw new IllegalStateException("缺少目标目录，无法交付 ROM");
        }
        Files.createDirectories(targetDir);

        // 1. 先把每个产物拷到目标旁的 .part（暂存目录与目标可能跨盘，用 copy 而非 move）
        List<AtomicOutput.Part> parts = new ArrayList<>();
        List<Path> partPaths = new ArrayList<>();
        try {
            for (ExportActionContext.Deliverable d : deliverables) {
                Path target = targetDir.resolve(d.destName());
                Path part = AtomicOutput.partOf(target);
                Files.copy(d.staged(), part, StandardCopyOption.REPLACE_EXISTING);
                parts.add(new AtomicOutput.Part(part, target));
                partPaths.add(part);
            }
            // 2. 整组原子提交
            AtomicOutput.commitAll(parts);
        } catch (Exception e) {
            AtomicOutput.discardAll(partPaths);
            throw e;
        }

        ctx.setTakenOver(true);
        if (deliverables.size() > 1) {
            logger.info("动作交付 {} 个分片 → {}", deliverables.size(), targetDir);
        }
    }
}
