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

WORKDIR /app

# Copy the built JAR file
COPY --from=builder /build/target/webGamelistOper-1.2.jar app.jar

# Copy default rules
COPY src/main/resources/export-rules/ /app/default-rules/export/
COPY src/main/resources/import-templates/ /app/default-rules/import/

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
RUN mkdir -p /data/logs /data/database /data/backup /data/input /data/output /data/rules/export /data/rules/import /data/scraper/system /data/scraper/games \
    && ln -sfn /data /app/data \
    && ln -sfn /data/logs /app/logs

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD bash -c "exec 3<>/dev/tcp/localhost/8080 && printf 'GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3 && grep -q 'HTTP' <&3" || exit 1

ENTRYPOINT ["/entrypoint.sh"]
