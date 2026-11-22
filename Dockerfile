# Multi-stage build for Frontend-Killer
# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy pom.xml first to leverage Docker layer caching for dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Run the application
FROM eclipse-temurin:17-jre

# Set timezone
ENV TZ=Asia/Shanghai

# External tools used by the variant pipeline. All are invoked as separate
# processes, so none becomes part of this MIT-licensed application:
#   chdman  - CHD compression, part of MAME tools      (GPL-2.0-or-later)
#   ffmpeg  - APE/FLAC track decoding to WAV           (LGPL-2.1-or-later in Debian)
#   wit     - Wii ISO -> WBFS conversion (see block below) (GPLv2+)
# See THIRD-PARTY-NOTICES.md. Do not remove /usr/share/doc/mame-tools or
# /usr/share/doc/ffmpeg.
RUN set -e; ok=0; \
    for i in 1 2 3 4 5 6; do \
      if apt-get -o Acquire::Retries=10 -o Acquire::http::Timeout=60 update \
         && apt-get -o Acquire::Retries=10 -o Acquire::http::Timeout=60 install -y --no-install-recommends mame-tools ffmpeg ca-certificates curl; then \
        ok=1; break; \
      fi; \
      echo "apt attempt $i failed, retrying in 5s..."; sleep 5; \
    done; \
    [ "$ok" = "1" ] || { echo "apt install failed after retries"; exit 1; }; \
    rm -rf /var/lib/apt/lists/*

# wit (Wiimms ISO Tools): Wii ISO -> WBFS for the variant pipeline (GPLv2+, run
# as a separate process). Ubuntu has no wit package, so we ship the official
# precompiled Linux/x86_64 build. A small wrapper puts it on PATH and points
# LD_LIBRARY_PATH at any bundled libs, so Wit.locate() auto-detects it and the
# WBFS line works out of the box with no user configuration.
# (Fallback if this download host is unreachable: build from
#  https://github.com/Wiimm/wiimms-iso-tools in a toolchain stage.)
RUN set -eux; \
    ok=0; \
    for i in 1 2 3 4 5 6 7 8; do \
      if curl -fsSL --retry-all-errors --retry 5 --retry-delay 3 --connect-timeout 30 -o /tmp/wit.tar.gz \
        "https://wit.wiimm.de/download/wit-v3.05a-r8638-x86_64.tar.gz"; then ok=1; break; fi; \
      echo "wit download attempt $i failed, retrying in 5s..."; sleep 5; \
    done; \
    [ "$ok" = "1" ] || { echo "wit download failed after retries"; exit 1; }; \
    mkdir -p /opt/wit; \
    tar -xzf /tmp/wit.tar.gz -C /opt/wit; \
    REAL="$(find /opt/wit -type f -name wit | head -n1)"; \
    test -n "$REAL"; \
    LIBDIR="$(dirname "$REAL")/../lib"; \
    [ -d "$LIBDIR" ] && LIBDIR="$(cd "$LIBDIR" && pwd):" || LIBDIR=""; \
    printf '#!/bin/sh\nLD_LIBRARY_PATH="%s${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" exec "%s" "$@"\n' \
      "$LIBDIR" "$REAL" > /usr/local/bin/wit; \
    chmod +x /usr/local/bin/wit; \
    rm -rf /tmp/wit.tar.gz; \
    /usr/local/bin/wit --version

WORKDIR /app

# Copy the built JAR file
COPY --from=builder /build/target/webGamelistOper-1.4.1.jar app.jar

# Copy default rules (from rules/ directory, which contains all v2+v3 templates)
COPY rules/export/ /app/default-rules/export/
COPY rules/import/ /app/default-rules/import/

# Copy seed data (released to /data on first run by entrypoint)
# NOTE: placed in /app/seed-data (not /app/data) because /app/data is a symlink to /data (see below)
COPY data/ /app/seed-data/

# Copy rules directory (custom templates, overrides seed rules)
COPY rules/ /app/seed-data/rules/

# Copy entrypoint script
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Create persistent directories, then link the app's relative paths (./data, ./logs)
# to the persistent /data volume so data survives container recreation (unRAID/Docker updates)
RUN mkdir -p /data/logs /data/database /data/backup /data/input /data/output /data/rules/export /data/rules/import /data/scraper/system /data/scraper/games /data/themes /data/roms2 /data/roms3 \
    /data/rom-db/metadat/generated /data/rom-db/rdb /data/tools \
    && ln -sfn /data /app/data \
    && ln -sfn /data/logs /app/logs

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD bash -c "exec 3<>/dev/tcp/localhost/8080 && printf 'GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3 && grep -q 'HTTP' <&3" || exit 1

ENTRYPOINT ["/entrypoint.sh"]
