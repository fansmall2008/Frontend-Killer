package com.gamelist.variant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 输入分析结果：转换所需的事实 + 风险提示。
 * <p>
 * 只读、不落库。{@code blockers} 非空表示这一份输入无法安全转换，
 * 与“模块没实现”是两回事，前者是数据结论，后者是能力缺失。
 */
public record Analysis(ArtifactKind confirmedKind,
                       long totalBytes,
                       int sectorSize,
                       String mode,
                       int dataTracks,
                       int audioTracks,
                       String summary,
                       List<String> warnings,
                       List<String> blockers,
                       Map<String, String> facts) {

    public Analysis {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        facts = facts == null ? Map.of() : Map.copyOf(facts);
        summary = summary == null ? "" : summary;
        mode = mode == null ? "" : mode;
    }

    /** 是否可以继续转换 */
    public boolean usable() {
        return blockers.isEmpty();
    }

    public String blockerText() {
        return String.join("；", blockers);
    }

    public static Builder builder(ArtifactKind kind) {
        return new Builder(kind);
    }

    public static final class Builder {
        private final ArtifactKind kind;
        private long totalBytes;
        private int sectorSize;
        private String mode = "";
        private int dataTracks;
        private int audioTracks;
        private String summary = "";
        private final List<String> warnings = new ArrayList<>();
        private final List<String> blockers = new ArrayList<>();
        private final Map<String, String> facts = new LinkedHashMap<>();

        private Builder(ArtifactKind kind) {
            this.kind = kind;
        }

        public Builder totalBytes(long value) {
            this.totalBytes = value;
            return this;
        }

        public Builder sectorSize(int value) {
            this.sectorSize = value;
            return this;
        }

        public Builder mode(String value) {
            this.mode = value;
            return this;
        }

        public Builder tracks(int data, int audio) {
            this.dataTracks = data;
            this.audioTracks = audio;
            return this;
        }

        public Builder summary(String value) {
            this.summary = value;
            return this;
        }

        public Builder warn(String message) {
            if (message != null && !message.isBlank()) {
                warnings.add(message);
            }
            return this;
        }

        public Builder block(String message) {
            if (message != null && !message.isBlank()) {
                blockers.add(message);
            }
            return this;
        }

        public Builder fact(String key, String value) {
            if (key != null && value != null) {
                facts.put(key, value);
            }
            return this;
        }

        public Analysis build() {
            return new Analysis(kind, totalBytes, sectorSize, mode, dataTracks, audioTracks,
                    summary, warnings, blockers, facts);
        }
    }
}
