---
kind: dependency_management
name: Maven + Spring Boot 依赖管理
category: dependency_management
scope:
    - '**'
source_files:
    - pom.xml
    - src/main/resources/application.properties
    - Dockerfile
    - docker-compose.yml
    - distribution/webGamelistOper-1.0.6-beta3.jar
---

## 1. 使用的系统/方法

本项目采用 **Maven** 作为唯一的依赖管理与构建工具，基于 **Spring Boot 3.2.0** 的 `spring-boot-starter-parent` 父 POM 统一管理版本。所有第三方库通过 `<dependencies>` 声明在根目录的 `pom.xml` 中，未使用任何私有仓库、镜像或 `vendor/` 锁定策略。

- 包管理器：Maven（`mvn`）
- 依赖来源：公共 Maven Central（由 `spring-boot-starter-parent` 默认配置）
- 版本锁定：无 lockfile（如 `maven-dependency-plugin` 的 `dependency:go-offline` 产物），仅靠 `pom.xml` 中的显式版本号
- 打包方式：`spring-boot-maven-plugin` 将依赖与类文件打包为可执行 JAR（`webGamelistOper-*.jar`），见 `distribution/` 下的发布产物

## 2. 关键文件

- `pom.xml`：唯一依赖清单，定义所有直接依赖与插件版本
- `src/main/resources/application.properties`：运行时数据库与 Flyway 配置（H2/Flyway 依赖在此生效）
- `Dockerfile` / `docker-compose.yml`：容器化部署时复用已构建的 JAR，不引入额外依赖源
- `distribution/webGamelistOper-*.jar`：预构建的可分发制品，体现最终依赖快照

## 3. 架构与约定

- **BOM 继承**：通过 `<parent>org.springframework.boot:spring-boot-starter-parent:3.2.0</parent>` 继承 Spring Boot 的依赖管理 BOM，因此 `spring-boot-starter-web`、`spring-boot-starter-jdbc`、`spring-boot-starter-test` 等 starter 无需显式指定版本。
- **显式版本集中声明**：非 starter 依赖统一在 `<properties>` 或 `<dependencies>` 中以固定版本声明，例如 MyBatis (`mybatis-spring-boot-starter 3.0.3`)、OkHttp (`4.12.0`)、JSON (`20231013`)、JAXB API (`4.0.1`)、JAXB Impl (`4.0.4`)。
- **作用域控制**：仅测试用依赖标记 `<scope>test</scope>`（`spring-boot-starter-test`）；仅运行期需要的依赖标记 `<scope>runtime</scope>`（`h2`、`jaxb-impl`），编译期仅需 API 的依赖（如 `jakarta.xml.bind-api`）不加 runtime scope。
- **Flyway 多驱动支持**：同时引入 `flyway-core` 与 `flyway-mysql`，以支持 H2（开发/内嵌）与 MySQL（生产）两种迁移场景。
- **资源过滤规则**：`maven-resources-plugin` 配置 `nonFilteredFileExtensions` 排除 `html`、`json`，避免 JSON 模板与 i18n 资源被 Maven 变量替换破坏。
- **JAR 入口**：`maven-jar-plugin` 与 `spring-boot-maven-plugin` 均指定 `com.gamelist.Application` 为主类，确保 `java -jar` 可直接运行。

## 4. 约定与约束

- **Java 版本约束**：`<properties><java.version>17</java.version></java.version>`，要求 JDK 17+ 进行编译与运行。
- **无私有仓库/代理配置**：`pom.xml` 中未出现 `<repositories>`、`<mirrors>`、`<servers>` 或 `~/.m2/settings.xml` 引用，依赖全部从 Maven Central 拉取。
- **无依赖锁定机制**：项目未使用 `maven-enforcer-plugin` 强制版本范围、也未提交 `dependency-reduced-pom.xml`、`dependency-tree.txt` 等锁定产物；升级依赖需手动修改 `pom.xml` 对应 `<version>`。
- **starter 优先原则**：Web 层、JDBC、测试等能力一律通过 Spring Boot Starter 引入，而非零散引入 `spring-web`、`spring-jdbc` 等底层模块，保证与 Spring Boot 3.2 兼容。
- **运行时依赖最小化**：H2 与 JAXB 实现均标记为 `runtime`，使编译期仅依赖 API，减小依赖树复杂度。
- **构建产物即依赖快照**：`distribution/` 下按版本归档的 `webGamelistOper-*.jar` 即为依赖的最终固化形态，发布流程通过 `build-distribution.bat` / `build.bat` 触发 Maven 打包后复制产物。

综上，本项目的依赖管理是典型的“单 POM + Spring Boot BOM”模式，简单直接、无私有仓库与锁定策略，适合小型独立部署的 Java Web 应用。