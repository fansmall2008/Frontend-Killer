# 运行应用
FROM openjdk:27-ea-17-jdk-slim

# 安装必要的工具（用于健康检查和日志轮转）
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    wget \
    logrotate \
    && rm -rf /var/cache/apt/*

# 设置工作目录
WORKDIR /app

# 复制本地构建的JAR文件
COPY target/webGamelistOper-1.0.4-beta.jar app.jar

# 复制默认规则文件
COPY src/main/resources/export-rules/ /app/default-rules/export-rules/
COPY src/main/resources/import-templates/ /app/default-rules/import-templates/

# 复制自定义文件到特定路径（可根据需要修改）
# 示例：复制config目录到/app/config/
# COPY config/ /app/config/
# 示例：复制单个文件到/app/config/
# COPY config.properties /app/config/

# 复制data文件夹到容器中
COPY data/ /app/data/

# 复制entrypoint脚本
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# 创建必要的目录
RUN mkdir -p /data/logs /data/database /data/backup

# 暴露端口
EXPOSE 8080

# 使用entrypoint脚本启动应用
ENTRYPOINT ["/entrypoint.sh"]