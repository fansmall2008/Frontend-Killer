# 第一阶段：构建应用
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# 第二阶段：运行应用
FROM eclipse-temurin:17-jre-alpine

# 安装必要的工具（用于健康检查和日志轮转）
RUN apk add --no-cache \
    bash \
    wget \
    logrotate \
    && rm -rf /var/cache/apk/*

# 设置工作目录
WORKDIR /app

# 从构建阶段复制JAR文件
COPY --from=builder /build/target/webGamelistOper-1.0-beta.jar app.jar

# 复制默认规则文件
COPY rules/ /app/default-rules/

# 复制entrypoint脚本
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# 创建必要的目录
RUN mkdir -p /data/logs /data/database /data/backup

# 暴露端口
EXPOSE 8080

# 使用entrypoint脚本启动应用
ENTRYPOINT ["/entrypoint.sh"]