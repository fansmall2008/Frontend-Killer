---
kind: build_system
name: Maven + Docker 多阶段构建与发行打包体系
category: build_system
scope:
    - '**'
source_files:
    - pom.xml
    - build.bat
    - build-distribution.bat
    - Dockerfile
    - Dockerfile.multi-stage
    - distribution/Dockerfile
    - docker-compose.yml
    - distribution/docker-compose.yml
    - entrypoint.sh
    - distribution/entrypoint.sh
---

## 1. 构建系统概览

该项目采用 **Maven**（Spring Boot Parent 3.2.0）作为核心构建工具，使用 **Docker 多阶段构建**完成镜像打包，并通过 Windows 批处理脚本提供本地一键构建/分发流程。Java 编译目标为 JDK 17，运行镜像基于 `openjdk:27-ea-17-jdk-slim` 或 `eclipse-temurin:17-jre`。

## 2. 关键文件与职责

- **`pom.xml`**：项目依赖与 Maven 插件配置。定义主类 `com.gamelist.Application`，通过 `spring-boot-maven-plugin` 执行 `repackage` 生成可执行 JAR；`maven-resources-plugin` 将 `html`、`json` 标记为非过滤资源以避免被转义；H2/Flyway/MyBatis/OkHttp/JAXB 等依赖集中声明。
- **`build.bat`**：Windows 本地开发构建脚本，设置 `JAVA_HOME=C:\Program Files\Java\jdk-17`，执行 `mvn clean package spring-boot:repackage -DskipTests`，并校验产物 `target/webGamelistOper-1.0.6-beta3.jar` 是否存在。
- **`build-distribution.bat`**：完整发行包构建脚本。先停止所有 Java 进程 → 清理 `target/` → `mvn clean package -DskipTests` → 创建 `distribution/` 目录结构 → 拷贝 JAR、`data/`、`src/main/resources/export-rules/`、`src/main/resources/import-templates/`、`distribution/docs/`、安装说明与 Docker 相关文件到 `distribution/`，形成可直接分发的文件夹。
- **`Dockerfile` / `Dockerfile.multi-stage` / `Dockerfile.cn` / `Dockerfile.local`**：多种 Docker 构建入口。根级 `Dockerfile` 使用阿里云 Maven 镜像源（`aliyunmaven`），在 builder 阶段执行 `mvn clean package spring-boot:repackage -DskipTests`，运行阶段复制 `app.jar`、静态 HTML、默认规则、`data/`、`rules/` 及 `entrypoint.sh`，暴露 8080 端口。
- **`distribution/Dockerfile`**：面向已构建产物的轻量镜像，直接 `COPY target/webGamelistOper-1.0.4-beta.jar app.jar`，额外安装 `bash/wget/logrotate`。
- **`docker-compose.yml` / `distribution/docker-compose.yml`**：定义服务 `webgamelistoper`，映射端口 8080，挂载 `logs/roms/output/rules/backup/database` 到 `/data/*`，注入环境变量 `SPRING_PROFILES_ACTIVE=default`、`SERVER_TOMCAT_BASEDIR=/data`、`SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,...`、`JAVA_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC`，并通过 `wget --spider` 检查 `/actuator/health` 健康端点。
- **`entrypoint.sh`**（根级与 `distribution/` 各一份）：容器启动入口。创建 `/data/rules`、`/data/logs`、`/data/scraper/system`、`/data/scraper/games` 等目录；若 `/data/database` 为空则从镜像内 `/app/data` 释放初始数据（跳过 database 以保护 volume 中的已有数据库）；将 `/app/default-rules` 下的导出规则与导入模板覆盖复制到 `/data/rules/export`、`/data/rules/import`；最后 `exec java $JAVA_OPTS $STATIC_OPTS -jar /app/app.jar`。

## 3. 架构与约定

- **版本管理**：版本号集中在 `pom.xml` 的 `<version>` 字段（当前 `1.0.6-beta3`），但多个脚本硬编码了不同版本（如 `build-distribution.bat` 拷贝 `1.0.4-beta.jar`、`Dockerfile` 复制 `1.0.6-beta3.jar`、`distribution/Dockerfile` 引用 `1.0.4-beta.jar`），存在版本不一致风险。
- **构建产物路径**：Maven 输出位于 `target/webGamelistOper-<version>.jar`；发行包位于 `distribution/`，包含 JAR、`data/`、`docs/`、`rules/`、`INSTALL*.md`、`Dockerfile`、`docker-compose.yml`、`entrypoint.sh`。
- **资源打包策略**：`resources/static/` 中的前端 HTML/JS/CSS 随 JAR 一起打包；运行时可通过 `-Dspring.web.resources.static-locations=file:/app/static/,classpath:/static/` 让文件系统覆盖 classpath 中的静态资源，便于热更新。
- **数据持久化约定**：所有可变数据（日志、ROM、输出、规则、备份、数据库）统一挂载到容器的 `/data/*` 目录，通过 compose volume 映射到宿主机，保证数据不丢失。
- **Flyway 迁移**：数据库变更通过 `src/main/resources/db/migration/V*.sql` 命名规范（如 `V1.0.3__baseline_migration.sql`）进行版本化管理，由 Flyway 自动执行。

## 4. 约定与约束

- **构建命令**：本地开发使用 `build.bat` 或 `mvn clean package spring-boot:repackage -DskipTests`；容器构建使用 `docker build -t fansmall/webgamelistoper:dev .`。
- **测试跳过**：所有构建脚本均带 `-DskipTests`，生产构建不执行单元测试。
- **JDK 版本锁定**：源码编译要求 JDK 17（`java.version=17`），运行镜像使用 `openjdk:27-ea-17-jdk-slim`（即 JDK 17 Early Access 27）。
- **Maven 镜像加速**：根级 `Dockerfile` 内置阿里云 Maven 镜像配置，加速依赖下载。
- **规则初始化顺序**：`entrypoint.sh` 强制先复制默认规则到 `/data/rules/export` 与 `/data/rules/import`，确保用户自定义规则不会覆盖内置模板；同时保护 `/data/database` 不被覆盖。
- **健康检查**：compose 通过 `wget --spider http://localhost:8080/actuator/health` 探测应用就绪，重试 3 次、间隔 30s、启动宽限期 60s。
- **资源编码**：`maven-resources-plugin` 显式设置 UTF-8 编码，并将 `.html`、`.json` 加入 `nonFilteredFileExtensions`，防止中文内容被 Maven 转义。
- **无 CI 流水线**：仓库中未发现 GitHub Actions / Jenkins / GitLab CI 等持续集成配置文件，构建与发布目前依赖本地脚本与手动操作。