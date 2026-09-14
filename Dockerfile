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
COPY --from=builder /build/target/webGamelistOper-1.1-RC1.jar app.jar

# Copy default rules
COPY src/main/resources/export-rules/ /app/default-rules/export/
COPY src/main/resources/import-templates/ /app/default-rules/import/

# Copy data folder (initial data for first run)
COPY data/ /app/data/

# Copy rules directory (custom templates, overrides data/ rules)
COPY rules/ /app/data/rules/

# Copy entrypoint script
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Create necessary directories
RUN mkdir -p /data/logs /data/database /data/backup /data/scraper/system /data/scraper/games

EXPOSE 8080

ENTRYPOINT ["/entrypoint.sh"]
