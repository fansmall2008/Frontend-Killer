package com.gamelist.variant;

/**
 * 工件类型：流水线节点。
 * <p>
 * 节点描述的是“镜像的实际形态”（扇区大小、轨道布局、容器），而不是扩展名。
 * 扩展名不足以判断形态，例如 .bin 可能是 2352 扇区原始镜像，也可能是 2048 扇区重打包。
 */
public enum ArtifactKind {

    /** 单数据轨、2048 字节扇区的 ISO 镜像 */
    ISO_2048("iso", "ISO（2048 扇区）", "iso"),
    /** 单数据轨、2352 字节扇区的原始镜像（cue+bin 的 bin） */
    RAW_2352("bin", "BIN（2352 扇区）", "bin"),
    /** 多轨或含音轨的原始镜像，块压缩容器无法承载 */
    RAW_2352_MULTI("bin-multi", "BIN/CUE（多轨或含音轨）", "bin"),
    /** Dreamcast GD-ROM：.gdi 轨道索引 + 多个 bin/raw */
    GDI("gdi", "GDI（GD-ROM 镜像）", "gdi"),
    /** Nero 光盘镜像，单文件自带轨道表 */
    NRG("nrg", "NRG（Nero 镜像）", "nrg"),
    /** cdrdao 光盘镜像，单文件自带轨道表 */
    TOC("toc", "TOC（cdrdao 镜像）", "toc"),
    /** 块压缩 ISO（CISO v1，zlib deflate） */
    CSO("cso", "CSO（块压缩 ISO）", "cso"),
    /** LZ4 块压缩 ISO（预留） */
    ZSO("zso", "ZSO（LZ4 块压缩 ISO）", "zso"),
    /**
     * ECM（Error Code Modeler）：把每个扇区里能算出来的 EDC/ECC 挖掉再补回来的容器。
     * <p>
     * 它不压缩数据，只是省掉冗余字节，所以解包可以容错地还原成 2352 原始镜像，
     * 但解包前读不到卷描述符，也不能直接交给模拟器。
     */
    ECM("ecm", "ECM（错误码建模压缩）", "ecm"),
    /** MAME CHD（预留） */
    CHD("chd", "CHD（MAME 压缩镜像）", "chd"),
    /** PBP 打包镜像（预留） */
    PBP("pbp", "PBP（打包镜像）", "pbp"),
    /** 压缩包：不是镜像形态，任何模块都不以它为输入 */
    ARCHIVE("archive", "压缩包（非镜像）", "zip"),
    /** 无法识别的文件 */
    UNKNOWN("unknown", "未知文件", "");

    private final String code;
    private final String display;
    private final String extension;

    ArtifactKind(String code, String display, String extension) {
        this.code = code;
        this.display = display;
        this.extension = extension;
    }

    /** 稳定短名，用于系统产线声明与流水线 id */
    public String code() {
        return code;
    }

    public String display() {
        return display;
    }

    /** 该形态的默认落盘扩展名 */
    public String extension() {
        return extension;
    }

    /** 是否是可以直接交给模拟器的最终产物 */
    public boolean playable() {
        return this == ISO_2048 || this == CSO || this == ZSO || this == CHD || this == PBP;
    }

    /** 是否是压缩容器：要先解包才能读到扇区内容 */
    public boolean compressed() {
        return this == ECM || this == CSO || this == ZSO || this == CHD || this == PBP || this == ARCHIVE;
    }

    /** 是否自带完整轨道表，外部工具可以直接读（cue/gdi/toc/nrg） */
    public boolean hasTrackTable() {
        return this == RAW_2352 || this == RAW_2352_MULTI || this == GDI || this == NRG || this == TOC;
    }

    public static ArtifactKind byCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toLowerCase();
        for (ArtifactKind kind : values()) {
            if (kind.code.equals(normalized) || kind.name().equalsIgnoreCase(normalized)) {
                return kind;
            }
        }
        return null;
    }
}
