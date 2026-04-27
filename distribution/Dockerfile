# 运行应用
FROM openjdk:27-ea-17-jdk-slim

RUN apt-get update && apt-get install -y \
    bash \
    wget \
    logrotate \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY webGamelistOper-1.0.4-beta.jar app.jar

COPY rules/export/ /app/default-rules/export/
COPY rules/import/ /app/default-rules/import/
COPY rules/translation-config.json /app/default-rules/translation-config.json

# 复制文档到镜像中
COPY docs/ /app/default-docs/

# 复制自定义文件到特定路径（可根据需要修改）
# 示例：复制config目录到/app/config/
# COPY config/ /app/config/
# 示例：复制单个文件到/app/config/
# COPY config.properties /app/config/

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

RUN mkdir -p  /data/database /data/backup /output /roms && rm -rf /data/logs && mkdir -p /data/logs
EXPOSE 8080

ENTRYPOINT ["/entrypoint.sh"]