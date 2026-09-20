package com.gamelist.model;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * v3 统一模板模型 — 同时支持导入和导出。
 * <p>
 * 模板结构分为四个块：
 * <ol>
 *   <li><b>templateInfo</b> — 模板说明（方向、文件类型、分隔符等元数据）</li>
 *   <li><b>system</b> — 系统信息映射（平台名、启动命令等只出现一次的信息）</li>
 *   <li><b>game</b> — 游戏信息映射，分为 gameInfo（属性字段）和 mediaInfo（媒体字段）</li>
 *   <li><b>output</b> — 导出用：输出配置（ROM/媒体/数据文件的目录、文件名、路径格式等）</li>
 * </ol>
 * <p>
 * 导入时：field 值为候选数组 ["game", "title", "name"]，引擎依次尝试匹配数据文件中的 key。<br>
 * 导出时：field 值为表达式字符串 "name" 或 "dateformat(releasedate, 'yyyy-MM-dd')"。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateV3 {

    private TemplateInfo templateInfo;
    private SystemMapping system;
    private GameMapping game;
    private ParsingConfig parsing;  // 数据文件解析配置（注释符、分隔符、编码等）
    private OutputConfig output;    // 导出用：输出配置（ROM/媒体/数据文件的目录、文件名、路径格式等）

    // ==================== 内部类 ====================

    /**
     * 块1：模板说明
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TemplateInfo {
        private String direction;      // "import" | "export"
        private String dataFileType;   // "text" | "data" (XML/JSON)
        private String format;         // "xml" | "json" | "text" (可选，细化 data 型)
        private String delimiter;      // text 型的属性分隔符（如 ":"）
        private String dataFile;       // 数据文件名（如 "gamelist.xml"、"metadata.pegasus.txt"）
        private String author;
        private String description;
        private String notes;
        private int version = 3;

        // Getters / Setters
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }

        public String getDataFileType() { return dataFileType; }
        public void setDataFileType(String dataFileType) { this.dataFileType = dataFileType; }

        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }

        public String getDelimiter() { return delimiter; }
        public void setDelimiter(String delimiter) { this.delimiter = delimiter; }

        public String getDataFile() { return dataFile; }
        public void setDataFile(String dataFile) { this.dataFile = dataFile; }

        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }

        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }

        public boolean isImport() { return "import".equalsIgnoreCase(direction); }
        public boolean isExport() { return "export".equalsIgnoreCase(direction); }
        public boolean isTextType() { return "text".equalsIgnoreCase(dataFileType); }
        public boolean isDataType() { return "data".equalsIgnoreCase(dataFileType); }
    }

    /**
     * 块2：系统信息映射
     * <p>
     * gameStartMarker 用于 text 型：遇到该标记之前的 key-value 对归为系统信息。<br>
     * fields 的 key = 数据库列名，value = 候选字段名（导入）或表达式（导出）。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SystemMapping {
        private String gameStartMarker;
        private String systemTag;  // XML 型：系统信息所在的标签名（如 "provider"）
        private List<String> header;  // 导出用：头部模板行
        private List<String> footer;  // 导出用：尾部模板行
        private Map<String, Object> fields = new LinkedHashMap<>();

        public String getGameStartMarker() { return gameStartMarker; }
        public void setGameStartMarker(String gameStartMarker) { this.gameStartMarker = gameStartMarker; }

        public String getSystemTag() { return systemTag; }
        public void setSystemTag(String systemTag) { this.systemTag = systemTag; }

        public List<String> getHeader() { return header; }
        public void setHeader(List<String> header) { this.header = header; }

        public List<String> getFooter() { return footer; }
        public void setFooter(List<String> footer) { this.footer = footer; }

        public Map<String, Object> getFields() { return fields; }
        public void setFields(Map<String, Object> fields) { this.fields = fields; }
    }

    /**
     * 块3：游戏信息映射
     * <p>
     * gameStartMarker 用于 text 型：每遇到该标记开始一条新游戏。<br>
     * multiLine 控制是否支持缩进续行。<br>
     * gameInfo / mediaInfo 的 key = 数据库列名，value = 候选字段名（导入）或表达式（导出）。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GameMapping {
        private String gameStartMarker;
        private boolean multiLine = false;
        private String entrySeparator;  // 导出用：条目之间的分隔符
        private Map<String, Object> gameInfo = new LinkedHashMap<>();
        private Map<String, Object> mediaInfo = new LinkedHashMap<>();
        private MediaDiscovery mediaDiscovery;
        private Map<String, String> computedVariables;  // 计算变量：key=变量名, value=表达式
        private MultiFileConfig multiFile;              // 多文件游戏展开配置（旧语义）
        private MultiFileDetectionConfig multiFileDetection;  // 多文件检测配置：将多盘信息写入 multiFile/multiFileContent 字段

        public String getGameStartMarker() { return gameStartMarker; }
        public void setGameStartMarker(String gameStartMarker) { this.gameStartMarker = gameStartMarker; }

        public boolean isMultiLine() { return multiLine; }
        public void setMultiLine(boolean multiLine) { this.multiLine = multiLine; }

        public String getEntrySeparator() { return entrySeparator; }
        public void setEntrySeparator(String entrySeparator) { this.entrySeparator = entrySeparator; }

        public Map<String, Object> getGameInfo() { return gameInfo; }
        public void setGameInfo(Map<String, Object> gameInfo) { this.gameInfo = gameInfo; }

        public Map<String, Object> getMediaInfo() { return mediaInfo; }
        public void setMediaInfo(Map<String, Object> mediaInfo) { this.mediaInfo = mediaInfo; }

        public MediaDiscovery getMediaDiscovery() { return mediaDiscovery; }
        public void setMediaDiscovery(MediaDiscovery mediaDiscovery) { this.mediaDiscovery = mediaDiscovery; }

        public Map<String, String> getComputedVariables() { return computedVariables; }
        public void setComputedVariables(Map<String, String> computedVariables) { this.computedVariables = computedVariables; }

        public MultiFileConfig getMultiFile() { return multiFile; }
        public void setMultiFile(MultiFileConfig multiFile) { this.multiFile = multiFile; }

        public MultiFileDetectionConfig getMultiFileDetection() { return multiFileDetection; }
        public void setMultiFileDetection(MultiFileDetectionConfig multiFileDetection) { this.multiFileDetection = multiFileDetection; }
    }

    /**
     * 数据文件解析配置。
     * <p>
     * 定义解析器的行为参数，使同一套 Java 引擎能处理不同前端的文件格式。
     * 当该块缺失时，引擎使用默认值保持向后兼容。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParsingConfig {
        private TextParsingConfig text;

        public TextParsingConfig getText() { return text; }
        public void setText(TextParsingConfig text) { this.text = text; }
    }

    /**
     * 文本型数据文件的解析参数。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TextParsingConfig {
        private String delimiter;                    // 属性分隔符（如 ":"、"="）
        private List<String> commentChars;           // 注释符列表（如 ["#"]、[";", "//"]）
        private String entrySeparator;               // 条目分隔方式："emptyLine" 或具体字符串
        private String keyCase;                      // key 大小写策略："lower" / "upper" / "preserve"
        private String encoding;                     // 文件编码（如 "UTF-8"、"GBK"）
        private MultiLineConfig multiLine;           // 多行续行配置

        public String getDelimiter() { return delimiter; }
        public void setDelimiter(String delimiter) { this.delimiter = delimiter; }

        public List<String> getCommentChars() { return commentChars; }
        public void setCommentChars(List<String> commentChars) { this.commentChars = commentChars; }

        public String getEntrySeparator() { return entrySeparator; }
        public void setEntrySeparator(String entrySeparator) { this.entrySeparator = entrySeparator; }

        public String getKeyCase() { return keyCase; }
        public void setKeyCase(String keyCase) { this.keyCase = keyCase; }

        public String getEncoding() { return encoding; }
        public void setEncoding(String encoding) { this.encoding = encoding; }

        public MultiLineConfig getMultiLine() { return multiLine; }
        public void setMultiLine(MultiLineConfig multiLine) { this.multiLine = multiLine; }
    }

    /**
     * 多行续行配置。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MultiLineConfig {
        private boolean enabled = false;
        private List<String> indentChars;  // 缩进字符列表（如 ["  ", "\t"]）

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public List<String> getIndentChars() { return indentChars; }
        public void setIndentChars(List<String> indentChars) { this.indentChars = indentChars; }
    }

    /**
     * 多文件游戏展开配置。
     * <p>
     * 当一个游戏对应多个 ROM 文件时（如 Pegasus files: 语法），
     * 按指定分隔符拆分为多条独立的 Game 记录。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MultiFileConfig {
        private boolean enabled = false;
        private String field = "path";       // 要展开的字段
        private String separator = "\n";     // 分隔符

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getSeparator() { return separator; }
        public void setSeparator(String separator) { this.separator = separator; }
    }

    /**
     * 多文件检测配置（把多盘信息写入 multiFile / multiFileContent 字段）。
     * <p>
     * 触发条件与内容来源均由模板声明：
     * <ul>
     *   <li>trigger=fieldExists — source 字段存在且值为多行（如 Pegasus files: 多行续行）</li>
     *   <li>trigger=endsWith — source 字段值（首行）以 pattern 结尾（如 path 指向 .m3u）</li>
     * </ul>
     * 内容：content=fieldValue（取字段值清洗）或 content=fileContent（读文件内容，如 m3u）。
     * pathFrom=firstLine 时，path 取清洗后的第一行（files 多行场景）。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MultiFileDetectionConfig {
        private boolean enabled = false;
        private String source = "path";          // 检测源字段
        private String trigger = "fieldExists";  // fieldExists | endsWith
        private String pattern = ".m3u";         // endsWith 模式（大小写不敏感）
        private String content = "fieldValue";   // fieldValue | fileContent
        private String pathFrom;                  // firstLine：path 取清洗后第一行

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getTrigger() { return trigger; }
        public void setTrigger(String trigger) { this.trigger = trigger; }

        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getPathFrom() { return pathFrom; }
        public void setPathFrom(String pathFrom) { this.pathFrom = pathFrom; }
    }

    /**
     * 媒体文件自动发现配置。
     * <p>
     * 当数据文件中的媒体路径指向的文件不存在时，
     * 按 rules 中定义的路径模式在磁盘上逐一尝试，找到则写入。
     * <p>
     * subDirPatterns 定义子目录命名策略的优先级列表，按顺序尝试：
     * 例如 ["{filename}", "{name}"] 表示先用 ROM 文件名匹配，找不到再用显示名匹配。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaDiscovery {
        private boolean enabled = false;
        private String baseDir = "media";
        private List<String> subDirPatterns = new ArrayList<>();
        private List<String> extensions = new ArrayList<>();
        private Map<String, List<String>> rules = new LinkedHashMap<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getBaseDir() { return baseDir; }
        public void setBaseDir(String baseDir) { this.baseDir = baseDir; }

        public List<String> getSubDirPatterns() { return subDirPatterns; }
        public void setSubDirPatterns(List<String> subDirPatterns) { this.subDirPatterns = subDirPatterns; }

        public List<String> getExtensions() { return extensions; }
        public void setExtensions(List<String> extensions) { this.extensions = extensions; }

        public Map<String, List<String>> getRules() { return rules; }
        public void setRules(Map<String, List<String>> rules) { this.rules = rules; }
    }

    /**
     * 导出输出配置（强类型）。
     * <p>
     * 定义 ROM 文件、媒体文件、数据文件的输出目录、文件名模板、路径格式等。
     * 所有路径均为模板字符串，支持 {outputPath}、{platform.system}、{filename} 等变量。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OutputConfig {
        private ExportOptions exportOptions;   // UI 选项声明（哪些 checkbox 可用/默认值）
        private RomOutput roms;                // ROM 文件输出规则
        private MediaOutput media;             // 媒体文件输出规则
        private DataFileOutput dataFile;       // 数据文件输出规则

        public ExportOptions getExportOptions() { return exportOptions; }
        public void setExportOptions(ExportOptions exportOptions) { this.exportOptions = exportOptions; }

        public RomOutput getRoms() { return roms; }
        public void setRoms(RomOutput roms) { this.roms = roms; }

        public MediaOutput getMedia() { return media; }
        public void setMedia(MediaOutput media) { this.media = media; }

        public DataFileOutput getDataFile() { return dataFile; }
        public void setDataFile(DataFileOutput dataFile) { this.dataFile = dataFile; }
    }

    /**
     * 导出选项声明 — 控制 UI 上哪些 checkbox 可用及其默认值。
     * <p>
     * 模板作者可声明本前端支持/不支持的导出范围，
     * 用户在 UI 上的实际勾选决定运行时哪些 output 块生效。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExportOptions {
        private boolean gameFiles = true;      // 是否允许导出 ROM 文件
        private boolean mediaFiles = true;     // 是否允许导出媒体文件
        private boolean gameListFile = true;   // 是否允许生成数据文件
        private boolean showWarning = false;   // 是否在 UI 上显示限制警告

        public boolean isGameFiles() { return gameFiles; }
        public void setGameFiles(boolean gameFiles) { this.gameFiles = gameFiles; }

        public boolean isMediaFiles() { return mediaFiles; }
        public void setMediaFiles(boolean mediaFiles) { this.mediaFiles = mediaFiles; }

        public boolean isGameListFile() { return gameListFile; }
        public void setGameListFile(boolean gameListFile) { this.gameListFile = gameListFile; }

        public boolean isShowWarning() { return showWarning; }
        public void setShowWarning(boolean showWarning) { this.showWarning = showWarning; }
    }

    /**
     * ROM 文件输出规则。
     * <p>
     * directory 定义目标目录模板，filename 定义目标文件名模板（精确到文件名级别）。
     * 例如：directory="{outputPath}/{platform.system}", filename="{filename}{ext}" 表示保持原名。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RomOutput {
        private boolean enabled = true;        // 是否启用 ROM 导出
        private String directory;              // 目录模板
        private String filename;               // 文件名模板（支持表达式引擎）
        private M3uConfig m3u;                 // M3U 处理配置

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }

        public M3uConfig getM3u() { return m3u; }
        public void setM3u(M3uConfig m3u) { this.m3u = m3u; }
    }

    /**
     * M3U 文件处理配置。
     * <p>
     * 当 ROM 文件为 .m3u 格式时，控制是否同时复制 M3U 引用的文件。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class M3uConfig {
        private boolean enabled = false;       // 是否启用 M3U 引用文件复制
        private String target;                 // M3U 引用文件的目标路径模板（如 "{romsPath}"）

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
    }

    /**
     * 媒体文件输出规则。
     * <p>
     * directory 定义媒体根目录模板，rules 定义每种媒体类型的输出规则。
     * rules 的 key = 源 nomcourt 值（如 "box-2D"、"ss"、"video"），
     * 通过 GameFieldAccessor 从 Game 对象获取媒体相对路径。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaOutput {
        private String directory;                          // 媒体根目录模板
        private Map<String, MediaOutputRule> rules;        // key=源 nomcourt

        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }

        public Map<String, MediaOutputRule> getRules() { return rules; }
        public void setRules(Map<String, MediaOutputRule> rules) { this.rules = rules; }
    }

    /**
     * 单种媒体类型的输出规则。
     * <p>
     * target 定义目标路径模板（精确到文件名），如 "{gameName}/boxFront.png"。
     * dataFileTag 定义数据文件中对应的标签名，如 "assets.boxFront"。
     * 当 dataFileTag 不为空时，数据文件中的媒体路径将使用 target 求值结果。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MediaOutputRule {
        private String source;                 // 源 nomcourt（冗余，与 Map key 相同，方便读取）
        private String target;                 // 目标路径模板（精确到文件名）
        private String dataFileTag;            // 数据文件中的标签（如 "assets.boxFront"、"image"）

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }

        public String getDataFileTag() { return dataFileTag; }
        public void setDataFileTag(String dataFileTag) { this.dataFileTag = dataFileTag; }
    }

    /**
     * 数据文件输出规则。
     * <p>
     * 定义数据文件（如 gamelist.xml、metadata.pegasus.txt）的输出目录、文件名、路径格式。
     * pathFormat 控制数据文件中 ROM/媒体路径的输出方式：
     * <ul>
     *   <li>"relative" — 相对路径（如 "./game.rom"）</li>
     *   <li>"absolute" — 绝对路径</li>
     *   <li>"absoluteWithDot" — 带 ./ 前缀的绝对路径</li>
     * </ul>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataFileOutput {
        private String directory;              // 数据文件目录模板
        private String filename;               // 数据文件名
        private String pathFormat;             // 路径格式
        private String pathPrefix;             // 路径前缀（如 "./"）

        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }

        public String getPathFormat() { return pathFormat; }
        public void setPathFormat(String pathFormat) { this.pathFormat = pathFormat; }

        public String getPathPrefix() { return pathPrefix; }
        public void setPathPrefix(String pathPrefix) { this.pathPrefix = pathPrefix; }
    }

    // ==================== 工具方法 ====================

    /**
     * 将字段值统一转为候选列表（导入方向使用）。
     * <p>
     * 如果 value 是 List → 直接返回；如果是 String → 包装为单元素列表。
     */
    @SuppressWarnings("unchecked")
    public static List<String> toCandidateList(Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) result.add(item.toString());
            }
            return result;
        } else if (value instanceof String) {
            List<String> result = new ArrayList<>();
            result.add((String) value);
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * 将字段值统一转为表达式字符串（导出方向使用）。
     * <p>
     * 如果 value 是 String → 直接返回；如果是 List → 取第一个元素。
     */
    @SuppressWarnings("unchecked")
    public static String toExpression(Object value) {
        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.isEmpty() ? null : list.get(0).toString();
        }
        return null;
    }

    /**
     * 从文件路径加载 v3 模板。
     */
    public static TemplateV3 loadFromFile(File file) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            TemplateV3 template = mapper.readValue(file, TemplateV3.class);
            if (template.getTemplateInfo() != null && template.getTemplateInfo().getVersion() == 3) {
                return template;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 classpath 资源加载 v3 模板。
     */
    public static TemplateV3 loadFromClasspath(String resourcePath) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            TemplateV3 template = mapper.readValue(
                TemplateV3.class.getResourceAsStream(resourcePath), TemplateV3.class);
            if (template.getTemplateInfo() != null && template.getTemplateInfo().getVersion() == 3) {
                return template;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== Getters / Setters ====================

    public TemplateInfo getTemplateInfo() { return templateInfo; }
    public void setTemplateInfo(TemplateInfo templateInfo) { this.templateInfo = templateInfo; }

    public SystemMapping getSystem() { return system; }
    public void setSystem(SystemMapping system) { this.system = system; }

    public GameMapping getGame() { return game; }
    public void setGame(GameMapping game) { this.game = game; }

    public ParsingConfig getParsing() { return parsing; }
    public void setParsing(ParsingConfig parsing) { this.parsing = parsing; }

    public OutputConfig getOutput() { return output; }
    public void setOutput(OutputConfig output) { this.output = output; }
}
