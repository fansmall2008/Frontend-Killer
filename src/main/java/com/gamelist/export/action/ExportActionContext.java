package com.gamelist.export.action;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamelist.model.Game;
import com.gamelist.variant.ConversionModule;

/**
 * 动作链的执行上下文：一条 ROM 在导出后处理期间被各 {@link ExportBlock} 逐步改写的可变状态。
 * <p>
 * 语义约定（积木之间唯一的契约）：
 * <ul>
 *   <li>{@link #getSource()} —— 当前待交付的单个源文件；转换块会把它替换成产物。</li>
 *   <li>{@link #getDeliverables()} —— 交付清单：一组 (暂存文件 → 目标文件名)。为空表示“原样交付源文件”。</li>
 *   <li>{@link #getPrimaryDestName()} —— 数据文件里 {@code game.path} 应记录的那个文件名（分片时=主分片）。</li>
 *   <li>{@link #isTakenOver()} —— 动作是否已接管交付；接管后导出主流程不再自行 copyFile。</li>
 * </ul>
 * 非线程共享：每个游戏一个实例，顺序跑链。
 */
public final class ExportActionContext {

    /** 一条交付项：暂存目录里的实际文件 → 目标目录下应落成的文件名 */
    public record Deliverable(Path staged, String destName) {
    }

    private final Game game;
    private final int systemId;
    private final Path targetDir;
    private final String desiredBaseName;
    private final ConversionModule.ProgressSink progress;

    /** 惰性创建的暂存目录（转换/分片产物先落这里，cleanup 块整体删除） */
    private Path stagingDir;
    private Path source;
    private String sourceExtension;
    private final List<Deliverable> deliverables = new ArrayList<>();
    private String primaryDestName;
    private final Map<String, String> selections = new LinkedHashMap<>();
    /** 多个启用动作合并后的参数（如 splitSize），积木从上下文取而非从具体 action 取 */
    private final Map<String, String> params = new LinkedHashMap<>();
    /** systemId → 分片 strategy id（合并自启用动作），SplitBlock 据此分派 */
    private final Map<Integer, String> strategies = new LinkedHashMap<>();
    private boolean takenOver;
    private final List<String> notes = new ArrayList<>();

    public ExportActionContext(Game game, int systemId,
                               Path source, String sourceExtension,
                               Path targetDir, String desiredBaseName,
                               ConversionModule.ProgressSink progress) {
        this.game = game;
        this.systemId = systemId;
        this.source = source;
        this.sourceExtension = sourceExtension == null ? "" : sourceExtension;
        this.targetDir = targetDir;
        this.desiredBaseName = desiredBaseName;
        this.progress = progress == null ? ConversionModule.ProgressSink.NOOP : progress;
    }

    /** 取（必要时创建）暂存目录 */
    public Path stagingDir() {
        return stagingDir;
    }

    public void setStagingDir(Path stagingDir) {
        this.stagingDir = stagingDir;
    }

    /** 暂存一个目录内的相对名（纯 ASCII 安全名由调用方保证） */
    public Path staged(String name) {
        return stagingDir == null ? null : stagingDir.resolve(name);
    }

    public Game getGame() { return game; }
    public int getSystemId() { return systemId; }
    public Path getTargetDir() { return targetDir; }
    public String getDesiredBaseName() { return desiredBaseName; }
    public ConversionModule.ProgressSink getProgress() { return progress; }

    /** 合并参数：读取动作声明的参数（如 splitSize），缺失回退 fallback */
    public Map<String, String> getParams() { return params; }
    public String getParam(String key, String fallback) {
        String value = params.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 合并的分片策略表：按 systemId 取 strategy id */
    public Map<Integer, String> getStrategies() { return strategies; }
    public String getStrategyFor(int systemId) { return strategies.get(systemId); }

    public Path getSource() { return source; }
    public void setSource(Path source) { this.source = source; }

    public String getSourceExtension() { return sourceExtension; }
    public void setSourceExtension(String sourceExtension) {
        this.sourceExtension = sourceExtension == null ? "" : sourceExtension;
    }

    public List<Deliverable> getDeliverables() { return deliverables; }
    public void addDeliverable(Path staged, String destName) {
        deliverables.add(new Deliverable(staged, destName));
    }
    public void clearDeliverables() { deliverables.clear(); }

    public String getPrimaryDestName() { return primaryDestName; }
    public void setPrimaryDestName(String primaryDestName) { this.primaryDestName = primaryDestName; }

    public boolean isTakenOver() { return takenOver; }
    public void setTakenOver(boolean takenOver) { this.takenOver = takenOver; }

    public List<String> getNotes() { return notes; }

    /** 运行时用户选择（如格式转换的 targetFormat），由导出主流程注入 */
    public Map<String, String> getSelections() { return selections; }
    public void setSelection(String key, String value) {
        if (key != null && value != null && !value.isBlank()) {
            selections.put(key, value);
        }
    }
    public String getSelection(String key) {
        String value = selections.get(key);
        return value == null ? "" : value.trim();
    }
    public void addNote(String note) {
        if (note != null && !note.isBlank()) {
            notes.add(note);
        }
    }

    /** 从 desiredBaseName 去掉扩展名，得到分片兄弟文件命名用的主干 */
    public String desiredStem() {
        String name = desiredBaseName == null ? "rom" : desiredBaseName;
        int dot = name.lastIndexOf('.');
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return (dot > slash) ? name.substring(0, dot) : name;
    }
}
