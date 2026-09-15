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

# 显示当前时间供调试
log "当前容器时间: $(date '+%Y-%m-%d %H:%M:%S')"

if [ ! -f "/app/app.jar" ]; then
    error_exit "找不到 app.jar 文件"
fi

mkdir -p "$RULES_DIR" "$EXPORT_RULES_DIR" "$IMPORT_TEMPLATES_DIR" "$LOG_DIR" "/data/backup" "/data/input" "/data/output" "/data/scraper/system" "/data/scraper/games"

# 释放data文件夹到挂载目录（不覆盖已存在的数据库）
if [ -d "/app/seed-data" ]; then
    log "检查并释放data文件夹到挂载目录..."
    # 检查 /data/database 是否已有数据库文件（来自 volume 挂载）
    if [ -n "$(ls -A /data/database/ 2>/dev/null)" ]; then
        log "检测到 /data/database 已有数据（volume 挂载），跳过所有数据复制"
    else
        log "/data/database 为空，开始释放初始数据..."
        # 遍历/app/seed-data下的所有文件和目录
        for item in /app/seed-data/*; do
            if [ -e "$item" ]; then
                item_name=$(basename "$item")
                # 跳过database目录，避免覆盖已存在的数据库
                if [ "$item_name" != "database" ]; then
                    target_path="/data/$item_name"
                    # 如果目标不存在，才复制
                    if [ ! -e "$target_path" ]; then
                        if [ -d "$item" ]; then
                            cp -r "$item" "$target_path" 2>/dev/null || true
                        else
                            cp "$item" "$target_path" 2>/dev/null || true
                        fi
                        log "复制 $item_name 到 /data/"
                    else
                        log "跳过 $item_name（已存在）"
                    fi
                else
                    log "跳过 database 目录（保护已有数据）"
                fi
            fi
        done
    fi
    log "data文件夹释放完成"
fi

if [ -d "$DEFAULT_RULES_DIR" ]; then
    # 复制导出规则（强制覆盖以确保使用最新版本）
    log "复制默认导出规则..."
    cp -r "$DEFAULT_RULES_DIR/export/"* "$EXPORT_RULES_DIR/" 2>/dev/null || true

    # 复制导入模板（强制覆盖以确保使用最新版本）
    log "复制默认导入模板..."
    cp -r "$DEFAULT_RULES_DIR/import/"* "$IMPORT_TEMPLATES_DIR/" 2>/dev/null || true

    if [ ! -f "$TRANSLATION_CONFIG" ] && [ -f "/app/seed-data/rules/translation-config.json" ]; then
        log "翻译配置文件不存在，复制默认配置..."
        cp "/app/seed-data/rules/translation-config.json" "$TRANSLATION_CONFIG"
    fi
fi

log "规则目录初始化完成"
log "开始启动应用..."

JAVA_OPTS="${JAVA_OPTS:- -Xmx2g -Xms512m -XX:+UseG1GC}"
export JAVA_OPTS

# 添加外部静态资源目录（文件系统优先于classpath）
STATIC_OPTS="-Dspring.web.resources.static-locations=file:/app/static/,classpath:/static/"

# ---- 运行用户与权限（unRAID 等 NAS 通过 PUID/PGID 控制落盘文件归属）----
# 默认 0（root）以保持既有行为；unRAID 模板会显式传入 99/100。
PUID="${PUID:-0}"
PGID="${PGID:-0}"

if [ "$PUID" != "0" ]; then
    log "以非 root 用户运行: PUID=$PUID PGID=$PGID"
    # 尽力创建匹配的 group/user（已存在则忽略错误）
    getent group "$PGID" >/dev/null 2>&1 || groupadd -g "$PGID" appuser >/dev/null 2>&1 || true
    getent passwd "$PUID" >/dev/null 2>&1 || useradd -u "$PUID" -g "$PGID" -M -N -s /usr/sbin/nologin appuser >/dev/null 2>&1 || true
    # 修正应用管理目录归属；roms 仅修正挂载点本身（不递归，避免大目录耗时/改动用户 ROM 归属）
    chown -R "$PUID":"$PGID" /data/database /data/rules /data/logs /data/backup /data/output /data/input /data/scraper 2>/dev/null || true
    chown "$PUID":"$PGID" /data /data/roms 2>/dev/null || true
    if setpriv --reuid "$PUID" --regid "$PGID" --init-groups true 2>/dev/null; then
        exec setpriv --reuid "$PUID" --regid "$PGID" --init-groups java $JAVA_OPTS $STATIC_OPTS -jar /app/app.jar
    else
        exec setpriv --reuid "$PUID" --regid "$PGID" --clear-groups java $JAVA_OPTS $STATIC_OPTS -jar /app/app.jar
    fi
fi

exec java $JAVA_OPTS $STATIC_OPTS -jar /app/app.jar
