---
kind: configuration_system
name: Spring Boot 配置体系：application.properties + 运行时 JSON 配置 + 目录初始化
category: configuration_system
scope:
    - '**'
source_files:
    - src/main/resources/application.properties
    - src/main/java/com/gamelist/config/ScreenScraperConfig.java
    - src/main/java/com/gamelist/config/FlywayConfig.java
    - src/main/java/com/gamelist/config/DataDirectoryInitializer.java
    - src/main/java/com/gamelist/util/PathUtil.java
    - src/main/java/com/gamelist/service/impl/ConfigManager.java
    - src/main/java/com/gamelist/config/WebConfig.java
    - src/main/java/com/gamelist/config/ScraperSystemInitializer.java
---

## 1. 使用的系统与框架

本项目基于 **Spring Boot** 的 `application.properties` 作为核心配置文件，结合自定义 Java 配置类与运行时 JSON 文件，构成三层配置体系：
- **应用级配置**：`src/main/resources/application.properties`（端口、编码、静态资源、数据库、日志、上传限制、业务路径等）。
- **Spring Bean 级配置**：通过 `@ConfigurationProperties(prefix = "...")` 将配置绑定到 POJO（如 `ScreenScraperConfig`），并通过 `@ConditionalOnProperty` 条件加载。
- **运行时可编辑配置**：以 JSON 文件形式持久化在数据目录中（如 `./data/rules/translation-config.json`），由 `ConfigManager` 单例按需读取并支持热重载。

## 2. 关键文件与包

| 文件 | 作用 |
|---|---|
| `src/main/resources/application.properties` | Spring Boot 主配置：服务器、H2/HikariCP、MyBatis、Flyway、日志、上传、业务路径 (`app.*`) |
| `src/main/java/com/gamelist/config/ScreenScraperConfig.java` | 使用 `@ConfigurationProperties(prefix="screenscraper")` 绑定外部服务地址与凭据 |
| `src/main/java/com/gamelist/config/FlywayConfig.java` | 自定义 Flyway 初始化，带失败迁移自动修复逻辑，受 `spring.flyway.enabled` 控制 |
| `src/main/java/com/gamelist/config/DataDirectoryInitializer.java` | 实现 `ApplicationListener<ApplicationEnvironmentPreparedEvent>`，在环境准备阶段创建 `./data`、`./data/database`、`./data/rules`、`./data/input`、`./data/output`、`./logs` 等目录 |
| `src/main/java/com/gamelist/util/PathUtil.java` | 统一解析运行路径：优先 Docker 挂载路径 `/data`、`/roms`、`/output`，回退到本地 `./data` |
| `src/main/java/com/gamelist/service/impl/ConfigManager.java` | 单例管理 `translation-config.json`，维护 `variables` 映射，提供 `reloadConfig()` 热重载 |
| `src/main/java/com/gamelist/config/WebConfig.java` | 注册 `/scraper/**` 与 `/data/scraper/**` 静态资源映射，禁用缓存 |
| `src/main/java/com/gamelist/config/ScraperSystemInitializer.java` | `ApplicationRunner`，启动时异步拉取 Scraper 系统列表并写入数据库 |

## 3. 架构与设计约定

### 3.1 配置来源分层
1. **编译期默认值**：Java 类中的字段默认值（如 `ScreenScraperConfig` 中 `baseUrl`、`devPseudo` 等）。
2. **部署期覆盖**：`application.properties` 中 `spring.*` 与 `app.*` 前缀属性覆盖默认值。
3. **运行时可编辑**：`ConfigManager` 从 `PathUtil.getRulesPath() + "/translation-config.json"` 读取翻译服务配置，若不存在则生成包含 Google/Microsoft/DeepSeek 三种服务的默认 JSON，变量通过 `${...}` 占位符注入。

### 3.2 路径策略（Docker vs 本地）
`PathUtil` 通过检测 `/data`、`/roms`、`/output` 是否存在且可读/可写，动态选择 Docker 挂载路径或本地 `./data` 子路径。所有模块应通过该工具类获取路径，而非硬编码字符串。

### 3.3 数据目录自初始化
`DataDirectoryInitializer` 在 `ApplicationEnvironmentPreparedEvent` 中执行，从 `spring.datasource.url` 解析 H2 数据库路径并创建其父目录；同时确保 `./data`、`./data/rules`、`./data/rules/import`、`./data/rules/export`、`./data/input`、`./data/output`、`./logs` 存在。这保证了应用在无预置目录环境下也能启动。

### 3.4 数据库迁移配置
- `application.properties` 中 `spring.flyway.enabled=false`，但 `FlywayConfig` 通过 `@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)` 仍可在未显式关闭时启用。
- 使用 `baseline-on-migrate=true`、`baseline-version=1.0.2`，并设置 `validateOnMigrate(false)` 以容忍历史不一致。
- 启动时扫描 `schema_history`，发现 `FAILED` 状态则调用 `flyway.repair()` 再执行 `migrate()`。
- 另有 `spring.sql.init.schema-locations=classpath:sql/init.sql` 与 `mode=always`，在 Flyway 之前执行基础建表。

### 3.5 条件装配
- `FlywayConfig` 仅在 `spring.flyway.enabled=true`（或未设置）时生效。
- `ScreenScraperConfig` 通过 `@ConfigurationProperties(prefix="screenscraper")` 暴露 `screenscraper.base-url`、`screenscraper.dev-pseudo` 等键供外部覆盖。

## 4. 约定与约束

- **路径必须通过 `PathUtil` 访问**：避免直接拼接 `./data` 或 `/data`，以保证 Docker 挂载场景下行为一致。
- **数据目录必须在启动早期创建**：`DataDirectoryInitializer` 在环境准备阶段执行，后续组件不应假设目录已存在。
- **运行时配置变更需调用 `ConfigManager.getInstance().reloadConfig()`**：JSON 配置修改后不会自动热加载，需显式触发重新读取。
- **Flyway 迁移脚本命名遵循 `Vx.y.z__description.sql`**：位于 `src/main/resources/db/migration/`，版本从 `1.0.2` 基线开始递增。
- **业务路径通过 `app.*` 前缀暴露**：`app.rules.directory`、`app.import.templates.path`、`app.export.rules.path`、`app.input.directory`、`app.output.directory` 集中定义于 `application.properties`，便于部署时调整。
- **静态资源映射禁止缓存**：`WebConfig` 对 `/scraper/**` 和 `/data/scraper/**` 使用 `CacheControl.noCache()`，保证规则/媒体更新即时可见。
- **H2 控制台仅用于调试**：`spring.h2.console.enabled=true` 且 `web-allow-others=true`，生产环境应通过环境变量或覆盖属性关闭。
- **日志输出至 `logs/application.log`**：按大小轮转（10MB）、保留 7 份，控制台与文件均使用 UTF-8 编码。