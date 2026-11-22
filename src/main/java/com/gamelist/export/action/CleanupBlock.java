package com.gamelist.export.action;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 清理积木（chain 步骤 {@code cleanup}）：删除暂存目录里的中间产物。
 * <p>
 * 尽力而为，失败不影响已落盘的目标文件（copy 已原子完成）。放在链尾，即使前面某块抛异常，
 * runner 也会在 finally 里调用它回收暂存。
 */
@Component
public class CleanupBlock implements ExportBlock {

    private static final Logger logger = LoggerFactory.getLogger(CleanupBlock.class);

    @Override
    public String id() {
        return "cleanup";
    }

    @Override
    public void apply(ExportActionContext ctx) {
        deleteTree(ctx.stagingDir());
        ctx.setStagingDir(null);
    }

    private static void deleteTree(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 清理失败不影响主流程
                }
            });
        } catch (IOException e) {
            logger.debug("清理暂存目录失败: {} ({})", dir, e.getMessage());
        }
    }
}
