# 开发备忘录 — 已完成事项归档

> 按版本记录已完成的功能改造与 bug 修复，发布时作为 CHANGELOG 编写参考。

---

## v1.2（2026-09-23 发布）

### 新功能

- **通知中心**：`notification` 表 + REST API（`/api/notifications`、`/unread-count`、`/read`、`/stream` SSE）+ `NotificationServiceImpl`（5 分钟超时 + 15 秒心跳）；`TaskServiceImpl.completeTask/failTask` 自动写入通知；前端 `layout.html` EventSource 实时推送 + `pagehide` 主动 close
- **刮削媒体类型从模板预选**：`GET /api/export/rules` 返回 `mediaTypes` 数组；platform-management 与 game-list 刮削弹窗增加"从模板预选"工具行（模板下拉 + 应用 + 清空），自动勾选对应 checkbox
- **媒体路径重构（阶段 1-2）**：`PathResolver.resolveGameMediaDir` 统一新路径规则 `data/scraper/games/{ssSystemId}/{ssGameId}/{type}.{ext}`；本地复用检查跳过已下载媒体；local→ssGameId 迁移；scope=media 兜底写回 ss_game_id
  - ⚠️ **破坏性变更**：目录结构从旧 `{platformName}/{本地id}/{region}/` 变为 `{ssSystemId}/{ssGameId}/`，原有已刮削的媒体文件不能直接复用，用户需重新刮削或等待后续兼容迁移
- **批量聚合端点**：`GET /api/media-download/platforms/summary`（GROUP BY 一次返回所有平台统计）；`GET /api/gamelist/statistics/platforms/{platformId}`（单平台统计替代全库扫描）
- **游戏信息缓存（SS manifest）+ 导出找齐**：成功刮削时把 `jeuInfos` 完整 manifest 按 SS gameId 缓存到 `game_manifest` 表（内部不透明存储，不进导出包/不被接口列举）；game 表回写 `scraped`/`cached`/`parent_rom` 三列（其余元数据只躺缓存不覆盖手改）；系统设置新增“自动缓存游戏信息”开关（默认关、仅街机类平台生效、读取时校验防篡改）；刮削侧缓存优先命中不走 SS 接口；导出预检 O(1) 弹窗（未刮削平台单独导出/拷源目录）+ 父 rom 链 copy-if-missing 找齐 + 窄口径缺件报告
- **检索区多选下拉**：开发商/发行商/游戏类型由平铺复选框改为可搜索多选下拉；类型两级分组（顶级+子级）支持级联勾选；新增 `GET /api/gamelist/filter-options`（开发商/发行商/类型树）与 `publishers` 筛选参数；内置 `ss-genres.json`（ScreenScraper 159 类型父子关系，零运行时请求）

### 改造与优化

- **Thymeleaf 全站迁移**：18 个页面全部从 static HTML 迁移至 Thymeleaf 模板 + layout fragment（侧边栏/头部/通知模态框/公共脚本统一复用）
- **页面风格统一**：紫色系设计令牌（`--accent-primary: #8b5cf6`）、`.pg-container` 容器、按钮体系规范化、弹窗遮罩统一
- **导入性能**：茎名索引 HashMap + 预构建索引共享 + 4 线程并行媒体路径；548 游戏从 10+ 分钟 → 1-2 分钟
- **前端轮询优化**：媒体下载页每轮 3 请求（原 3+3N）；重入保护 + visibilitychange 暂停；刷新间隔 15s
- **数据库索引**：`idx_mdt_platform_status`、`idx_mdt_status_order`、`idx_game_platform_id`、`idx_game_platform_scraped`、`idx_game_name`、`idx_game_path` 六个复合索引
- **平台管理操作列**：改为图标按钮（`action-icon-btn` + emoji + `data-i18n-title` tooltip），尺寸一致不受语言影响
- **平台管理行点击**：tbody click 事件委托恢复"点击行=查看游戏列表"，平台名 td 触发内联编辑
- **模板说明增强**：后端返回 `notes` 字段，前端拼接 `description` + `notes` 展示
- **国际化收尾（约 50%）**：全站未翻译中文行从 ~700 降至 ~357；重点页面 platform-management 258→57、game-edit 49→18、data-import 30→1、index 16→1

### Bug 修复

| 问题 | 根因 | 修复 |
|------|------|------|
| 媒体下载页导致系统变慢、其它页无数据 | N+1 请求风暴（每轮 3+3N HTTP + 12N COUNT SQL） | GROUP BY 批量端点 + 前端重入保护 |
| 平台详情页卡死 | `/statistics/platforms` 全库 SUM CASE 扫描 | 单平台端点 + 复合索引 |
| 媒体下载↔平台管理来回点转圈 | SSE 僵尸连接耗尽 HTTP/1.1 6 连接限制 | 客户端 `pagehide` close + 服务端 5min 超时 + 15s 心跳 |
| 刮削结果始终 0 成功 0 失败 | JAR 缺 `org.tukaani:xz` → `NoClassDefFoundError` 绕过 `catch(Exception)` | 加 xz 依赖 + `catch(Throwable)` |
| 媒体下载页"暂无任务"但实际有任务 | H2 未引号别名转大写，`row.get("platformId")` 永远 null | SQL 别名加双引号 `AS "platformId"` |
| temp-subset-edit 通知未读数永不递减 | 前端已读逻辑缺失 | 标记已读后刷新 unreadCount |
| platform-details 删除 banner 后 JS 空引用 | 残留 DOM 引用 | 移除无用代码 |
| 游戏类型筛选对多类型游戏失效 | `genre IN` 整字段精确匹配 vs 逗号分隔多值存储 | genreid 逗号边界 LIKE 匹配，顶级自动展开全部子级 id |
| 刮削状态筛选无效 | 前端传 `fully/partially/not`，SQL 只认 `scraped/original/poor_quality/raw` | 检索区改为与平台管理页一致的四档 |

### 迁移经验留档

- 页面自有 CSS 类名要与 theme.css 隔离，避免通用类名冲突（用页面前缀如 `pm-`）
- 模态框结构：遮罩层 `overflow: hidden` + 内容层 `overflow-y: auto` + `margin: 5vh auto` 居中
- 模态框打开时锁定 body 滚动（MutationObserver 监听），关闭时恢复
- 导出模板 `output.media.rules` 的 key 即 nomcourt 标准，可直接与刮削 checkbox value 匹配

---

## v1.1-RC1（2026-09-14 发布）

> 详见 [CHANGELOG.md](CHANGELOG.md#11-rc1---2026-09-14)

主要交付：ScreenScraper 全量集成、v3 模板系统、游戏编辑独立页面、文件存在性校验、后台任务恢复、Windows EXE 分发、SS 标准字段迁移、Docker 改进。
