---
kind: logging_system
name: 基于 Spring Boot Logback 的日志系统（应用日志 + 错误日志双通道）
category: logging_system
scope:
    - '**'
source_files:
    - src/main/resources/application.properties
    - src/main/java/com/gamelist/controller/LogController.java
    - src/main/java/com/gamelist/util/ErrorLogWriter.java
    - src/main/java/com/gamelist/config/DataDirectoryInitializer.java
    - src/main/java/com/gamelist/config/DatabaseInitializer.java
    - src/main/resources/static/log-viewer.html
    - pom.xml
---

## 1. 使用的系统与框架

本项目采用 **Spring Boot 3.2** 内置的 **Logback** 作为日志框架，通过 `spring-boot-starter-web` 自动引入。代码中统一使用 SLF4J 门面接口 `org.slf4j.Logger` + `LoggerFactory.getLogger(...)` 获取 logger，未引入 Lombok 的 `@Slf4j` 注解，而是以 `private static final Logger logger = LoggerFactory.getLogger(Xxx.class);` 的形式声明。

项目没有引入独立的 log4j2、log4j 或第三方日志库，所有日志输出均由 Spring Boot 默认配置驱动。

## 2. 关键文件与位置

- **日志配置**：`src/main/resources/application.properties`（第 43–53 行），集中定义日志级别、控制台/文件输出格式、轮转策略。
- **应用主入口**：`src/main/java/com/gamelist/Application.java`（由 `pom.xml` 指定 mainClass）。
- **运行时日志读取 API**：`src/main/java/com/gamelist/controller/LogController.java`，暴露 `/api/logs/realtime` 和 `/api/logs/status` 两个 REST 端点，用于前端实时查看应用日志。
- **业务错误日志工具**：`src/main/java/com/gamelist/util/ErrorLogWriter.java`，将导入/导出过程中的异常条目单独写入 `./logs/errolog-*.log`。
- **初始化阶段日志**：`src/main/java/com/gamelist/config/DataDirectoryInitializer.java`、`DatabaseInitializer.java`，在启动时创建数据目录并记录数据库初始化过程。
- **日志文件输出路径**：根目录下 `logs/application.log`，以及按日期归档的 `application.log.YYYY-MM-DD.0.gz`。

## 3. 架构与约定

### 3.1 日志级别策略
- 包级默认级别：`logging.level.com.gamelist=INFO`，即业务代码默认只输出 INFO 及以上级别。
- 依赖组件级别：`logging.level.org.mybatis=INFO`，MyBatis SQL 执行日志为 INFO。
- 调试开关：`DatabaseInitializer` 中使用 `logger.debug("执行SQL: ...")`，说明 DEBUG 级别可用于排查 SQL 执行细节。

### 3.2 输出格式
- 控制台与文件共用同一 pattern：`%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`，包含时间戳、线程名、级别、logger 名称（截断至 36 字符）、消息体。
- 控制台编码强制 UTF-8：`logging.charset.console=UTF-8`。

### 3.3 文件日志与轮转
- 输出目标：`logging.file.name=logs/application.log`，相对路径，位于工作目录下的 `logs/`。
- 轮转策略：单文件最大 10MB（`logging.file.max-size=10MB`），最多保留 7 个历史文件（`logging.file.max-history=7`），超出后由 Logback 自动压缩为 `.gz` 归档（从 `logs/` 下已存在的 `application.log.2026-05-21.0.gz` 可验证）。

### 3.4 双通道日志设计
项目同时维护两类日志：
1. **应用运行日志**（application.log）：由 SLF4J + Logback 输出，覆盖启动、数据库初始化、HTTP 请求等运行时事件，可通过 `LogController` 的 `/api/logs/realtime` 以最后 N 行的方式返回给前端。
2. **业务错误日志**（errolog-*.log）：由 `ErrorLogWriter` 直接通过 `java.nio.file.Files` 写入，专门记录导入/导出过程中被跳过、转换失败或缺失文件的游戏条目，按操作类型（import/export）、平台名和日期生成独立文件，便于问题定位。

### 3.5 前端集成
静态页面 `src/main/resources/static/log-viewer.html` 配合 `LogController` 提供 Web 端的日志查看能力，无需登录即可通过浏览器访问应用日志的最后若干行。

## 4. 约定与约束

- **统一使用 SLF4J**：所有 Java 类中的日志调用均通过 `org.slf4j.LoggerFactory.getLogger(...)` 获取 logger，未发现直接使用 `System.out.println`、`printStackTrace` 等方式输出日志的情况。
- **日志级别规范**：正常流程走 `info`，异常情况走 `warn`/`error`，调试信息走 `debug`；`DataDirectoryInitializer`、`DatabaseInitializer` 等启动期逻辑严格遵循该约定。
- **日志文件路径固定**：应用日志固定输出到 `logs/application.log`，错误日志固定输出到 `./logs/errolog-*.log`，由 `ErrorLogWriter` 在首次写入时自动创建 `logs/` 目录。
- **日志轮转由 Logback 管理**：不自行实现滚动/压缩逻辑，仅通过 `application.properties` 的 `max-size` 与 `max-history` 控制。
- **日志内容不含敏感信息**：现有日志消息均为中文描述性文本，未发现打印密码、密钥等敏感字段。
- **无结构化日志字段**：当前日志为纯文本格式，未使用 JSON 结构化输出或 MDC/TraceId 等上下文追踪机制。
- **日志级别可在运行时调整**：由于使用 Spring Boot 默认 Logback 配置，可通过 Actuator 或修改 `application.properties` 动态调整各包的日志级别（如将 `com.gamelist` 改为 `DEBUG`）。