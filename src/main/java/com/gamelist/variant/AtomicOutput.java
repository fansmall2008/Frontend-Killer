package com.gamelist.variant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    // ---------------------------------------------------------------- 多文件（分片等一组产物）

    /**
     * 一组 (part → target) 的整批原子提交：先全部校验（存在、字节数、非空）再逐个改名，
     * 中途失败则把已提交的挪回各自 .part，保证“要么整组到位、要么一个都不落地”。
     * <p>
     * WBFS 分片产出的是 .wbfs + .wbf1(…) 一组文件，必须整组成功或整组回退，否则 loader 读到残缺分片。
     */
    public static void commitAll(List<Part> parts) throws IOException {
        if (parts == null || parts.isEmpty()) {
            return;
        }
        // 1. 先整体校验，不动任何文件
        for (Part p : parts) {
            if (!Files.isRegularFile(p.part())) {
                throw new IOException("临时文件不存在: " + p.part().getFileName());
            }
            long actual = Files.size(p.part());
            if (p.expectedBytes() >= 0 && actual != p.expectedBytes()) {
                throw new IOException("产出字节数不符: " + actual + "/" + p.expectedBytes()
                        + " (" + p.part().getFileName() + ")");
            }
            if (actual <= 0) {
                throw new IOException("产出文件为空: " + p.part().getFileName());
            }
        }
        // 2. 再逐个改名，记录已完成项以便回滚
        List<Part> done = new ArrayList<>();
        try {
            for (Part p : parts) {
                moveAtomic(p.part(), p.target());
                done.add(p);
            }
        } catch (IOException | RuntimeException e) {
            // 3. 回滚：已提交的挪回 .part（尽力而为）
            for (Part p : done) {
                try {
                    moveAtomic(p.target(), p.part());
                } catch (IOException ignored) {
                    // 回滚失败不掩盖原始异常
                }
            }
            throw e;
        }
    }

    /** 失败清理：删掉一组 .part */
    public static void discardAll(Collection<Path> parts) {
        if (parts == null) {
            return;
        }
        for (Path part : parts) {
            discard(part);
        }
    }

    /** 原子改名；跨文件系统时退化为普通改名 */
    private static void moveAtomic(Path from, Path to) throws IOException {
        Path parent = to.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 多文件提交单元：一个 part 落到 target。expectedBytes 小于 0 表示不校验字节数 */
    public record Part(Path part, Path target, long expectedBytes) {

        public Part(Path part, Path target) {
            this(part, target, -1);
        }
    }
}
