package com.gamelist.variant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 原子落盘：先写 .part，校验通过后再改名。
 * <p>
 * 任何失败都只留下 .part（由调用方清理），目标文件要么不存在，要么是完整产物，
 * 不会出现“看起来成功、实际写了半截”的镜像。
 */
public final class AtomicOutput {

    private AtomicOutput() {
    }

    public static Path partOf(Path target) {
        return target.resolveSibling(target.getFileName().toString() + ".part");
    }

    /**
     * 校验 .part 后原子改名到目标路径。
     *
     * @param expectedBytes 期望字节数，小于 0 表示不校验
     */
    public static void commit(Path part, Path target, long expectedBytes) throws IOException {
        if (!Files.isRegularFile(part)) {
            throw new IOException("临时文件不存在: " + part.getFileName());
        }
        long actual = Files.size(part);
        if (expectedBytes >= 0 && actual != expectedBytes) {
            throw new IOException("产出字节数不符: " + actual + "/" + expectedBytes);
        }
        if (actual <= 0) {
            throw new IOException("产出文件为空: " + target.getFileName());
        }
        try {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 失败清理：删掉 .part，避免下次误判 */
    public static void discard(Path part) {
        if (part == null) {
            return;
        }
        try {
            Files.deleteIfExists(part);
        } catch (IOException ignored) {
            // 清理失败不影响主流程
        }
    }
}
