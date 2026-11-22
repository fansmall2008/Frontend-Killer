package com.gamelist.export.action;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.gamelist.variant.module.Wit;

/**
 * Wii WBFS 分片策略：调用 {@link Wit#splitWbfs} 把已有 WBFS 按上限切分，登记整组交付项。
 * <p>
 * 命名（不依赖 wit 后缀细节，自己重排）：主分片（含 WBFS 头）= {@code <stem>.wbfs}，
 * 兄弟分片 = {@code <stem>.wbf1}、{@code <stem>.wbf2}…（USB Loader GX / Nintendont 认这套）。
 * 只产出一个文件说明本就没超过上限 → 不分片，按原样交付并提示。
 */
@Component
public class WitSplitStrategy implements SplitStrategy {

    private static final Logger logger = LoggerFactory.getLogger(WitSplitStrategy.class);
    private static final byte[] WBFS_MAGIC = {'W', 'B', 'F', 'S'};

    private final Wit wit;

    public WitSplitStrategy(Wit wit) {
        this.wit = wit;
    }

    @Override
    public String id() {
        return "wit-split";
    }

    @Override
    public void split(ExportActionContext ctx) throws Exception {
        Path source = ctx.getSource();
        if (source == null || !Files.isRegularFile(source)) {
            ctx.addNote("源文件不存在，跳过分片");
            return;
        }
        if (!isWbfs(source)) {
            ctx.addNote("该游戏不是 WBFS 文件，未分片");
            return;
        }
        if (!wit.available()) {
            ctx.addNote("未检测到 wit，无法分片，按单文件导出");
            return;
        }
        if (ctx.stagingDir() == null) {
            ctx.setStagingDir(Files.createTempDirectory("fk-split-"));
        }
        String splitSize = ctx.getParam("splitSize", "4000M");
        List<Path> produced = wit.splitWbfs(source, ctx.stagingDir(), splitSize, ctx.getProgress());
        if (produced.size() <= 1) {
            ctx.addNote("未超过分片上限，按单文件导出");
            return;
        }
        // 主分片 = 带 WBFS 头的那个；其余按名字排序当兄弟分片
        List<Path> parts = new ArrayList<>(produced);
        Path primary = null;
        for (Path p : parts) {
            if (isWbfs(p)) {
                primary = p;
                break;
            }
        }
        if (primary == null) {
            primary = parts.get(0);
        }
        parts.remove(primary);
        parts.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));

        String stem = ctx.desiredStem();
        ctx.addDeliverable(primary, stem + ".wbfs");
        for (int i = 0; i < parts.size(); i++) {
            ctx.addDeliverable(parts.get(i), stem + ".wbf" + (i + 1));
        }
        logger.info("WBFS 分片: {} → {} 个分片（上限 {}）", source.getFileName(), produced.size(), splitSize);
    }

    /** 扩展名是 .wbfs/.wbf，或头 4 字节是 "WBFS" 魔数 */
    private static boolean isWbfs(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".wbfs") || name.endsWith(".wbf")) {
            return true;
        }
        try (InputStream in = Files.newInputStream(path)) {
            byte[] head = in.readNBytes(4);
            if (head.length < 4) {
                return false;
            }
            for (int i = 0; i < 4; i++) {
                if (head[i] != WBFS_MAGIC[i]) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
