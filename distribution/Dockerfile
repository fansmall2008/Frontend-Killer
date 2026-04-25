# 运行应用
FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache \
    bash \
    wget \
    logrotate \
    && rm -rf /var/cache/apk/*

WORKDIR /app

COPY webGamelistOper-1.0.2-beta.jar app.jar

COPY rules/ /app/default-rules/

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

RUN mkdir -p /data/logs /data/database /data/backup

EXPOSE 8080

ENTRYPOINT ["/entrypoint.sh"]