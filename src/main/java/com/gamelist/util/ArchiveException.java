package com.gamelist.util;

/**
 * 压缩包读写异常。
 * <p>
 * reason 用于上层区分处理策略（例如加密包直接提示重新打包，不重试也不调用外部进程），
 * message 已经是可直接展示给用户的中文说明。
 */
public class ArchiveException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** 不是 zip/7z 文件 */
        NOT_ARCHIVE,
        /** 压缩包为空或没有可用条目 */
        EMPTY_ARCHIVE,
        /** 指定条目不存在 */
        ENTRY_NOT_FOUND,
        /** 条目内容为空 */
        EMPTY_ENTRY,
        /** 加密包（加密头或加密内容） */
        ENCRYPTED,
        /** 分卷压缩包 */
        MULTI_VOLUME,
        /** 不支持的压缩算法（例如 PPMd、Deflate64） */
        UNSUPPORTED_METHOD,
        /** 解压后体积超过限制 */
        TOO_LARGE,
        /** 条目路径不安全（绝对路径、..、符号链接） */
        UNSAFE_ENTRY,
        /** 其他 IO 错误 */
        IO
    }

    private final Reason reason;

    public ArchiveException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ArchiveException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
