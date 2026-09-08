---
kind: error_handling
name: 基于局部 try-catch + 业务异常与错误日志文件的错误处理模式
category: error_handling
scope:
    - '**'
source_files:
    - src/main/java/com/gamelist/filter/InitStatusFilter.java
    - src/main/java/com/gamelist/service/TranslationException.java
    - src/main/java/com/gamelist/util/ErrorLogWriter.java
    - src/main/java/com/gamelist/controller/GameListController.java
    - src/main/java/com/gamelist/service/impl/GameServiceImpl.java
    - src/main/java/com/gamelist/service/impl/BaiduTranslationServiceImpl.java
    - src/main/java/com/gamelist/service/impl/DeepSeekTranslationServiceImpl.java
    - src/main/java/com/gamelist/config/DatabaseInitializer.java
    - src/main/java/com/gamelist/config/FlywayConfig.java
    - src/main/java/com/gamelist/config/ScraperSystemInitializer.java
---

## 1. 整体方案
本仓库是一个 Spring Boot 3.2 + MyBatis + H2/Flyway 的 ROM 游戏清单管理应用。代码库中没有全局统一的全局异常处理器（未使用 @ControllerAdvice/@ExceptionHandler），也没有统一的错误码枚举或响应包装类型。错误处理主要采用以下三种方式组合：
- 控制器层局部 try-catch：每个 Controller 方法自行捕获异常，打印堆栈并返回 500 的 JSON 或字符串。
- 领域级业务异常：在翻译子系统中定义并抛出 TranslationException（checked Exception），由调用方决定如何处理。
- 结构化错误日志文件：通过 ErrorLogWriter 将导入/导出过程中的“跳过、失败、缺失”等错误写入 ./logs/errolog-*.log 文件，供后续排查。

此外，项目还通过一个高优先级的 InitStatusFilter 在服务未完成初始化时主动拦截请求，返回带业务错误码的 JSON 响应。

## 2. 关键文件与位置
- src/main/java/com/gamelist/filter/InitStatusFilter.java：全局过滤器，按白名单路径放行，否则检查 Scraper 系统初始化状态，未就绪时返回 503 Service Unavailable + 统一 JSON {success, errorCode, message, initStatus, systemsCount}。
- src/main/java/com/gamelist/service/TranslationException.java：自定义 checked 异常，用于翻译 API 调用失败、参数缺失、网络错误等场景。
- src/main/java/com/gamelist/util/ErrorLogWriter.java：工具类，提供 writeImportErrorLog / writeExportErrorLog，将问题条目以人类可读格式追加到 ./logs/errolog-{import|export}-{平台名}-{日期}.log。
- src/main/java/com/gamelist/controller/GameListController.java：大量方法使用 try { ... } catch (Exception e) { e.printStackTrace(); return ResponseEntity.status(500).body(...); } 的模式。
- src/main/java/com/gamelist/config/{DatabaseInitializer,FlywayConfig,DataDirectoryInitializer,ScraperSystemInitializer}.java：启动阶段初始化逻辑，内部捕获异常后记录日志或抛 RuntimeException。
- src/main/java/com/gamelist/service/impl/{BaiduTranslationServiceImpl,DeepSeekTranslationServiceImpl,GoogleTranslationServiceImpl,YoudaoTranslationServiceImpl}.java：各翻译实现统一抛出 TranslationException。
- src/main/java/com/gamelist/service/impl/GameServiceImpl.java：导入/批量保存等核心流程抛出 RuntimeException，并在失败时调用 ErrorLogWriter.writeImportErrorLog 输出详细错误列表。

## 3. 架构与约定
### 3.1 无全局异常处理器
全仓搜索未发现 @ControllerAdvice、@RestControllerAdvice、GlobalException、GlobalError 等字样，因此不存在统一的异常→HTTP 响应映射。每个 Controller 方法自行决定如何把异常转成 HTTP 响应，导致不同接口返回体结构不一致：有的返回纯字符串 "导入失败：xxx"，有的返回 ScanResult/Statistics 等模型但标记 success=false，有的返回 Collections.singletonMap("error", "...")。

### 3.2 控制器层 try-catch 模式
典型模式（见 GameListController）：
try {
    return ResponseEntity.ok(service.doSomething());
} catch (Exception e) {
    e.printStackTrace();
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("扫描过程中发生错误：" + e.getMessage());
}
- 异常信息直接拼入响应体，未做脱敏。
- 多数方法仅 e.printStackTrace()，未走结构化日志框架。
- 部分方法使用 logger.error(...) 记录（如获取统计接口），但其余仍用 printStackTrace。

### 3.3 业务异常分层
- 通用业务异常：GameServiceImpl、SettingController 等在数据/IO 层面抛出 RuntimeException，消息形如 "导入失败: gameListXml为null"、"创建根备份目录失败：..."。
- 领域异常：TranslationException 继承 Exception（checked），被各翻译实现抛出，调用方需显式处理。
- 参数校验：TranslationController 对不支持的 API 类型抛出 IllegalArgumentException。

### 3.4 错误日志文件策略
ErrorLogWriter 是本项目最接近“集中式错误记录”的组件：
- 输入：平台名 + 三类问题列表（被跳过/转换失败/路径为空 或 导出失败/缺失文件）。
- 输出：./logs/errolog-import-export-{platform}-{yyyyMMdd}.log，UTF-8 编码，包含时间戳和分类标题。
- 空结果不写文件（if (skippedGames.isEmpty() && ...) return;）。
- 该工具由 GameServiceImpl 在导入失败路径调用，用于向用户暴露“哪些游戏没成功”。

### 3.5 初始化阶段拦截
InitStatusFilter 使用 @Order(Ordered.HIGHEST_PRECEDENCE) 最高优先级拦截所有非白名单请求，根据 ScraperSystemInitializer.isReady() 返回不同的业务错误码：
- INITIALIZING：正在获取系统信息
- NOT_INITIALIZED：系统未初始化
- INIT_FAILED：初始化失败，附带最后错误
- 正常时放行

## 4. 约定与约束
- 无强制规范文档：仓库中没有发现关于错误处理的独立规范文档；现有模式是实践中自然形成的。
- 控制器必须自行捕获异常：由于没有全局异常处理器，新增 Controller 方法需要自行包裹 try-catch，否则未捕获异常会交给 Spring Boot 默认错误页 /error（该路径在 InitStatusFilter 中被加入白名单放行）。
- 翻译 API 必须抛 TranslationException：所有翻译实现（百度、DeepSeek、Google、有道）都遵循此约定，便于上层统一处理。
- 导入/导出失败必须落盘：GameServiceImpl 在导入失败时调用 ErrorLogWriter.writeImportErrorLog，约定了错误信息应进入 ./logs/errolog-*.log 而非仅控制台输出。
- 初始化未完成禁止访问业务接口：InitStatusFilter 强制要求前端先轮询 /api/init/status，在未就绪时一律返回 503 + 业务错误码，不允许绕过。
- 日志级别不统一：部分接口使用 logger.error，更多使用 e.printStackTrace()；ErrorLogWriter 则输出独立文件。仓库未配置统一的日志框架（如 Logback/SLF4J）进行集中收集，仅依赖 Spring Boot 默认的 application.log 与自定义错误日志文件并存。