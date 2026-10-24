package com.gamelist.variant;

/**
 * 模块对输入的判断结论。
 * <p>
 * 判断是轻量的（只读头部与文件长度），扩展名只能作为提示，魔数与结构才是依据。
 */
public record Verdict(Result result, String reason, ArtifactKind actualKind) {

    public enum Result {
        /** 是我要的形态，可以进入分析 */
        ACCEPT,
        /** 明确不是我要的，附原因与证据 */
        REJECT,
        /** 是别的形态，附实际类型，交给调度器重新算路 */
        MISMATCH
    }

    public static Verdict accept() {
        return new Verdict(Result.ACCEPT, "", null);
    }

    public static Verdict reject(String reason) {
        return new Verdict(Result.REJECT, reason == null ? "" : reason, null);
    }

    public static Verdict mismatch(ArtifactKind actual) {
        return new Verdict(Result.MISMATCH, "实际形态是 " + (actual == null ? "未知" : actual.display()), actual);
    }

    public static Verdict mismatch(ArtifactKind actual, String reason) {
        return new Verdict(Result.MISMATCH,
                reason == null || reason.isBlank() ? "实际形态是 " + actual.display() : reason, actual);
    }

    public boolean accepted() {
        return result == Result.ACCEPT;
    }
}
