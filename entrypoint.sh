#!/bin/bash
set -e

DEFAULT_RULES_DIR="/app/default-rules"
RULES_DIR="/data/rules"
EXPORT_RULES_DIR="/data/rules/export-rules"
IMPORT_TEMPLATES_DIR="/data/rules/import-templates"
TRANSLATION_CONFIG="/data/rules/translation-config.json"
LOG_DIR="/data/logs"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

error_exit() {
    log "ERROR: $1"
    exit 1
}

log "=========================================="
log "WebGamelistOper 启动中..."
log "=========================================="

if [ ! -f "/app/app.jar" ]; then
    error_exit "找不到 app.jar 文件"
fi

mkdir -p "$RULES_DIR" "$EXPORT_RULES_DIR" "$IMPORT_TEMPLATES_DIR" "$LOG_DIR"

if [ -d "$DEFAULT_RULES_DIR" ]; then
    if [ -z "$(ls -A "$EXPORT_RULES_DIR" 2>/dev/null)" ]; then
        log "导出规则目录为空，复制默认规则..."
        cp -r "$DEFAULT_RULES_DIR/"* "$RULES_DIR/" 2>/dev/null || true
    fi

    if [ ! -f "$TRANSLATION_CONFIG" ] && [ -f "$DEFAULT_RULES_DIR/translation-config.json" ]; then
        log "翻译配置文件不存在，复制默认配置..."
        cp "$DEFAULT_RULES_DIR/translation-config.json" "$TRANSLATION_CONFIG"
    fi
fi

log "规则目录初始化完成"
log "开始启动应用..."

JAVA_OPTS="${JAVA_OPTS:- -Xmx2g -Xms512m -XX:+UseG1GC}"
export JAVA_OPTS

exec java $JAVA_OPTS -jar /app/app.jar
