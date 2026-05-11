#!/bin/bash
set -e

DEFAULT_RULES_DIR="/app/default-rules"
RULES_DIR="/data/rules"
EXPORT_RULES_DIR="/data/rules/export"
IMPORT_TEMPLATES_DIR="/data/rules/import"
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

# 释放data文件夹到挂载目录
if [ -d "/app/data" ]; then
    log "检查并释放data文件夹到挂载目录..."
    # 复制data目录下的所有内容到/data目录
    cp -r /app/data/* /data/ 2>/dev/null || true
    log "data文件夹释放完成"
fi

if [ -d "$DEFAULT_RULES_DIR" ]; then
    # 复制导出规则（强制覆盖以确保使用最新版本）
    log "复制默认导出规则..."
    cp -r "$DEFAULT_RULES_DIR/export/"* "$EXPORT_RULES_DIR/" 2>/dev/null || true

    # 复制导入模板（强制覆盖以确保使用最新版本）
    log "复制默认导入模板..."
    cp -r "$DEFAULT_RULES_DIR/import/"* "$IMPORT_TEMPLATES_DIR/" 2>/dev/null || true

    if [ ! -f "$TRANSLATION_CONFIG" ] && [ -f "/app/data/rules/translation-config.json" ]; then
        log "翻译配置文件不存在，复制默认配置..."
        cp "/app/data/rules/translation-config.json" "$TRANSLATION_CONFIG"
    fi
fi

log "规则目录初始化完成"
log "开始启动应用..."

JAVA_OPTS="${JAVA_OPTS:- -Xmx2g -Xms512m -XX:+UseG1GC}"
export JAVA_OPTS

exec java $JAVA_OPTS -jar /app/app.jar
