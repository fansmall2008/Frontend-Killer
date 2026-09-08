# Multi-stage build for Spring Boot application
# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS builder

# 设置工作目录
WORKDIR /app

# 配置国内 Maven 镜像源（阿里云）
RUN mkdir -p /root/.m2 && echo "<settings><mirrors><mirror><id>aliyunmaven</id><mirrorOf>central</mirrorOf><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>" > /root/.m2/settings.xml

# 复制 Maven 配置文件和源代码
COPY pom.xml .
COPY src ./src

# 编译项目
RUN mvn clean package spring-boot:repackage -DskipTests

# Stage 2: Run the application
FROM openjdk:27-ea-17-jdk-slim

# 设置时区为上海时间
ENV TZ=Asia/Shanghai

# 设置工作目录
WORKDIR /app

# 复制编译好的 JAR 文件
COPY --from=builder /app/target/webGamelistOper-1.0.6-beta3.jar app.jar

# 复制静态资源文件（覆盖JAR中的文件）
COPY src/main/resources/static/game-list.html /app/static/game-list.html
COPY src/main/resources/static/platform-management.html /app/static/platform-management.html

# 复制默认规则文件
COPY src/main/resources/export-rules/ /app/default-rules/export/
COPY src/main/resources/import-templates/ /app/default-rules/import/

# 复制data文件夹到容器中
COPY data/ /app/data/

# 复制 rules 目录（包含自定义模板和规则）- 必须在 data/ 之后复制，以确保不被覆盖
COPY rules/ /app/data/rules/

# 复制entrypoint脚本
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# 创建必要的目录
RUN mkdir -p /data/logs /data/database /data/backup /data/scraper/system /data/scraper/games

# 暴露端口
EXPOSE 8080

# 使用entrypoint脚本启动应用
ENTRYPOINT ["/entrypoint.sh"]