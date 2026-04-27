package com.gamelist.model;

import java.util.List;
import java.util.Map;

public class ExportRule {
    private String frontend;
    private String name;
    private String version;
    private String description;
    private Rules rules;

    public static class Rules {
        private Map<String, MediaRule> media;
        private DataFileRule dataFile;
        private DirectoryRule directory;
        private M3URule m3u;
        private GameFileRule gameFile;

        public Map<String, MediaRule> getMedia() {
            return media;
        }

        public void setMedia(Map<String, MediaRule> media) {
            this.media = media;
        }

        public DataFileRule getDataFile() {
            return dataFile;
        }

        public void setDataFile(DataFileRule dataFile) {
            this.dataFile = dataFile;
        }

        public DirectoryRule getDirectory() {
            return directory;
        }

        public void setDirectory(DirectoryRule directory) {
            this.directory = directory;
        }

        public M3URule getM3u() {
            return m3u;
        }

        public void setM3u(M3URule m3u) {
            this.m3u = m3u;
        }

        public GameFileRule getGameFile() {
            return gameFile;
        }

        public void setGameFile(GameFileRule gameFile) {
            this.gameFile = gameFile;
        }
    }

    public static class MediaRule {
        private String source;
        private String target;
        private String dataFileTag;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getDataFileTag() {
            return dataFileTag;
        }

        public void setDataFileTag(String dataFileTag) {
            this.dataFileTag = dataFileTag;
        }
    }

    public static class DataFileRule {
        private String filename;
        private String format;
        private Map<String, String> fields;
        private HeaderRule header;
        private List<String> footer;
        private String fieldSeparator;
        private String entrySeparator;
        private String pathFormat;
        private Map<String, TransformRule> fieldTransforms;

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public Map<String, String> getFields() {
            return fields;
        }

        public void setFields(Map<String, String> fields) {
            this.fields = fields;
        }

        public HeaderRule getHeader() {
            return header;
        }

        public void setHeader(HeaderRule header) {
            this.header = header;
        }

        public List<String> getFooter() {
            return footer;
        }

        public void setFooter(List<String> footer) {
            this.footer = footer;
        }

        public String getFieldSeparator() {
            return fieldSeparator;
        }

        public void setFieldSeparator(String fieldSeparator) {
            this.fieldSeparator = fieldSeparator;
        }

        public String getEntrySeparator() {
            return entrySeparator;
        }

        public void setEntrySeparator(String entrySeparator) {
            this.entrySeparator = entrySeparator;
        }

        public String getPathFormat() {
            return pathFormat;
        }

        public void setPathFormat(String pathFormat) {
            this.pathFormat = pathFormat;
        }

        public Map<String, TransformRule> getFieldTransforms() {
            return fieldTransforms;
        }

        public void setFieldTransforms(Map<String, TransformRule> fieldTransforms) {
            this.fieldTransforms = fieldTransforms;
        }
    }

    public static class TransformRule {
        private String path;
        private String caseType;
        private boolean trim;
        private ReplaceRule replace;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getCaseType() { return caseType; }
        public void setCaseType(String caseType) { this.caseType = caseType; }
        public boolean isTrim() { return trim; }
        public void setTrim(boolean trim) { this.trim = trim; }
        public ReplaceRule getReplace() { return replace; }
        public void setReplace(ReplaceRule replace) { this.replace = replace; }
    }

    public static class ReplaceRule {
        private String from;
        private String to;

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
    }

    public static class HeaderRule {
        private List<String> structure;
        private Map<String, String> fields;

        public List<String> getStructure() {
            return structure;
        }

        public void setStructure(List<String> structure) {
            this.structure = structure;
        }

        public Map<String, String> getFields() {
            return fields;
        }

        public void setFields(Map<String, String> fields) {
            this.fields = fields;
        }
    }

    public static class DirectoryRule {
        private String roms;
        private String media;

        public String getRoms() {
            return roms;
        }

        public void setRoms(String roms) {
            this.roms = roms;
        }

        public String getMedia() {
            return media;
        }

        public void setMedia(String media) {
            this.media = media;
        }
    }

    public static class M3URule {
        private boolean enabled;
        private String target;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }
    }

    public static class GameFileRule {
        private boolean enabled;
        private String template;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTemplate() {
            return template;
        }

        public void setTemplate(String template) {
            this.template = template;
        }
    }

    public String getFrontend() {
        return frontend;
    }

    public void setFrontend(String frontend) {
        this.frontend = frontend;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Rules getRules() {
        return rules;
    }

    public void setRules(Rules rules) {
        this.rules = rules;
    }
}
