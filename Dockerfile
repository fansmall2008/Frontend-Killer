# 运行应用
FROM openjdk:27-ea-17-jdk-slim

# 基础镜像已包含 bash，wget 不是必需的

# 设置工作目录
WORKDIR /app

# 复制本地构建的JAR文件
COPY target/webGamelistOper-1.0.6-beta2.jar app.jar

# 复制默认规则文件
COPY src/main/resources/export-rules/ /app/default-rules/export/
COPY src/main/resources/import-templates/ /app/default-rules/import/

# 复制data文件夹到容器中
COPY data/ /app/data/

# 复制 rules 目录（包含自定义模板和规则）- 必须在 data/ 之后复制，以确保不被覆盖
COPY rules/ /app/data/rules/

# 复制自定义文件到特定路径（可根据需要修改）
# 示例：复制config目录到/app/config/
# COPY config/ /app/config/
# 示例：复制单个文件到/app/config/
# COPY config.properties /app/config/

# 复制entrypoint脚本
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# 创建必要的目录
RUN mkdir -p /data/logs /data/database /data/backup

# 暴露端口
EXPOSE 8080

# 使用entrypoint脚本启动应用
ENTRYPOINT ["/entrypoint.sh"]