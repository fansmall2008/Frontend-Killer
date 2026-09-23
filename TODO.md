# 待办清单

> 记录在进行中和计划中的开发任务。已完成事项见 [MEMO.md](MEMO.md)。
> 最后更新：2026-09-22
>
> **当前焦点：第 6 条阶段 3**（范围外拷贝），第 7.5 条（国际化收尾）与第 2 条（文件夹平台）随后。

---

## 1. 数据导入：线程数选择功能

**现状**：页面上的"线程数"选择在数据库插入阶段基本无效——`importGamesInBatches` 中虽然按 threadCount 分 chunk 并行，但 `synchronized (GameServiceImpl.class)` 锁导致实际串行执行。媒体发现阶段用固定 4 线程（不受此参数控制），已经真正并行。

**想法**：
- 去掉 `synchronized` 锁，改用真正的多线程批量插入
- 或研究 H2 的批量写入 API（`PreparedStatement.addBatch` / 多值 INSERT）
- 让页面线程数选择真正生效，或干脆从 UI 上移除该选项避免误导

**来源**：用户问"线程的选择功能现在有用吗？"后的调查结论，暂缓处理。

---

## 2. 游戏信息缓存（SS manifest）+ 导出找齐方案（2026-09-22 定稿）

### 2.1 问题本质
拖一年的根因：**「一个 MAME/街机游戏由哪些文件组成、文件夹边界在哪」在纯文件系统层面不可确定**。试图靠扫目录推断边界=死结。真正要命的是**导出时一个逻辑游戏对应的多个物理文件被漏拷 → 拷到前端跑不起来**（漏拷点 `ExportOrchestrator.copySingleGameFile()` L209：非 multiFile 只拷单个 `game.path`）。

### 2.2 调查依据（SS 真实数据结论）
- SS 不按文件夹建模，按**文件哈希**逐个匹配；一条 `jeuInfos` 即返回该游戏**完整 manifest**（`roms[]` 含全部 clone/兄弟/哈希/cloneof/numsupport）。
- `romcloneof`=父 rom id，**闭合在同一 jeu 内**；父链可离线解。共享 BIOS **不在** `roms[]`（兜不了）。
- 多碟分组靠 `romnumsupport/romtotalsupport`；chd 无归组字段但**就在同 jeu 的 `roms[]` 里**（凭“jeu 成员”即可认定）。

### 2.3 核心架构：缓存每游戏的 SS manifest
**将成功刮时拿到的 `jeuInfos` 响应缓存下来，作为找齐的离线权威源。**
- **存储**：按 **SS gameId** 存一份完整 JSON（同 jeu 的 clone 共享、自然去重）。形式=内部不透明存储（DB 表 或 文件 `data/scraper/games/<systemId>/<gameId>/manifest.json`），**不进导出包/备份、不被任何接口列举**。
- **只回写三样到 game 表**：`parent_rom`（从 cloneof 投影）+ `scraped` + `cached`；**其它元数据只躺缓存，不同步 game 表**（避免覆盖用户手改）。
- **缓存优先**：数据/媒体刮削先查 manifest，命中不走接口（省线程省额度）。
- **线程受控**：manifest 取数一律经 `ThreadResourceManager`，与刮削/下载**共享同一线程池与 SS 额度**（`maxthreads=0` 拒绝时不拉、提示登录）。

### 2.4 开关与安全（为何不可让用户随意读）
- **系统设置新增“是否自动缓存游戏信息”，默认关闭**；首次运行弹窗征询。文案：不改数据、只加速刮削，但**占用线程与刮削额度**。
- 缓存内容敏感（三点）：①响应含 `ssuser` 账号身份/配额与软件标识；②防被拿去批量下载惹恼 SS；③防篡改。→ 读取时**校验**（畸形/解析失败当未命中重拉），不盲信文件。
- **自动缓存范围**：仅对 **`platform.systemId` 非空（绑定系统）且属街机类（`type.contains("arcade")`）** 的平台生效。已证 cloneof 为街机专属（PSX/DC/SNES 实测全 0），非街机平台既不缓存也不做父 rom，省额度/线程/存储。
- **cache-through 仅当缓存开关打开时生效**：未开启→导出绝不隐式打 SS，未刮削平台直接进 2.5 弹窗。

### 2.5 导出预检与弹窗（O(1)，全程不扫盘）
平台是否含未刮削游戏 = `SELECT count(*) FROM game WHERE platform_id=? AND scraped=false`（一条列查询）：
- **多平台**且任一平台有未刮削游戏 → 弹窗“xxx 平台有未刮削游戏、无法保证关联性、请单独导出”（不硬阻，给“仅导可保障平台/取消”）。
- **单平台**且有未刮削游戏 → 告知无法保障 ROM 关联性、建议拷源文件夹：选**是**→媒体+信息文件**严格按模板生成**、仅把 ROM 拷贝步换成**整目录拷贝**（源可能多文件夹，合并平台预留 List）；选**否**→完全按模板。
- **边界（务必分清）**：
  - **父 rom = 运行时“需调用的外部依赖”**（另一台机器/共享 zip，名字与本体不同、本地磁盘扫不到）→ **唯一非 SS manifest 不可**的东西 → 走本步 copy-if-missing。
  - **“本体自己就是一堆文件”= 游戏本身**（同主名 chd、`cue`+bin+音频、多碟 m3u）→ **从本地磁盘解析比走远端 SS 方便** → **属下一个讨论（cue/多文件），本步不碰**。
- **本步（SS manifest 轨道）找齐 = 本体 + 父链 cloneof copy-if-missing**（递归、共享父只拷一次）。仅此。
- **父 rom 处理细则**：拿到 `parent_rom` 后 →① 在源目录定位父文件；② 源找到→目标 copy-if-missing 拷；③ 源找不到→记入**窄口径缺件报告**（“X 缺父 P.zip，可能跑不了”）。
- **平台预筛**：cloneof 是街机专属（PSX/DC/SNES 实测全 0）→ 用现成 `ScraperSystem.type.contains("arcade")` 做平台级闸门，**非街机平台整个父 rom 子步骤跳过**（不查不报）。

### 2.6 “刮削过”定义 + 数据模型变更
- **`scraped=true` 仅当成功从 SS 获取到游戏信息**：自动刮削命中✓、检索命中✓、**媒体下载✗**、失败/未命中✗。
- game 新增列：`scraped`(bool) / `cached`(bool) / `parent_rom`(varchar)。
- 新增存储：`game_manifest` 表或上述内部文件（按 gameId）。
- **既有所需功能**（自动刮削/检索/单游戏刮）在成功点补写这三列（`ScraperServiceImpl` L2137 setGameId 同侧）。

### 2.7 明确不做（本期）
- ❌ romset/DAT（`mame -listxml`、FBNeo dat）解析・BIOS 名单・**通用 DAT 缺件报告**（仅保留 2.5 的“父 rom 未找到”窄口径报告）
- ❌ 除 `parent_rom` 外的元数据回写（名称/描述/媒体一律不动 game 表）

### 2.8 dossier（文件夹类游戏）——已拍板
- **放弃无单文件锚点的文件夹式**：Daphne（`.daphne` 目录）、PC Win3.xx / DOS（一堆 exe/dll）——无代表文件可锚定，不做。
- **只做有锚点扩展名**：ScummVM 认 `.scummvm/.svm`（一文件=一游戏）；Amiga CD 的 `cue/iso/wav` 归入下面的 cue/清单处理。
- **cue/gdi 多文件（含 cue+bin+多 wav/mp3）**：“清单优先扫描 + 全 `FILE` 行解析→写 `multiFileContent`、被引用者不单列、导出改名时回写 N+1 条路径、匹配只认数据轨”，属**第二步**。

---

## 3. 通知模态框类名统一

**现状**：`fragments/layout.html` 的通知模态框 fragment 仍用 `.modal` / `.modal-content` 类名（theme.css 体系）。各页面 CSS 均已补 `#notificationModal.modal` 特判样式，但 fragment 类名本身尚未统一。

**想法**：把 fragment 通知模态框改用 `.modal-overlay` + `.modal` 结构（与 index.html 一致），或统一重命名类，消除各页面散落的特判样式。

---

## 4. 音效系统与效果包

**想法**：
- 给每个动作（点击、保存、删除、刮削完成、任务结束等）增加音效
- 本地软件无需考虑网络，音效资源直接内置或从本地目录读取
- 远期：做成完整的效果包概念，把配色主题 + 音效打包，支持一键切换整套视听风格

**待讨论**：
- 音效文件来源与格式（WAV/MP3）、触发时机、音量控制开关
- 效果包结构设计（目录规范、manifest、主题 token 与音效的对应关系）

---

## 5. 其他零散想法

- 刮削模态框"选择语种"等选项与刮削结果联动验证
- 静态资源缓存问题：HTML 更新后浏览器 Ctrl+F5 可能不生效，需关标签页重开（后续可在 Spring 配置里加 no-cache 头或版本号参数）

---

## 6. 刮削媒体路径重构（阶段 3-4 待做）

**剩余实施**：
3. ⏳ 范围外拷贝（刮削范围外游戏有导入媒体时，拷贝到新路径规则目录并更新 DB）
4. ⏳ （可选）平台编辑弹窗 preferred_region 下拉

> 路径规则与已完成阶段详见 [MEMO.md — v1.2 新功能](MEMO.md#v12开发中)

---

## 7. 全站国际化收尾（约 50% 剩余）

- **进展**（2026-09-21 统计）：总量从 ~700 行降至 ~357 行
  - platform-management: 258 → **57** ｜ game-list: 191 → **185**（重点待处理）
  - media-download: 52 → **34** ｜ game-edit: 49 → **18** ｜ data-import: 30 → **1** ｜ index: 16 → **1**
  - task-management: 13 → **4** ｜ temp-subset-edit: 29 → **17** ｜ scraper-system-list: 21 → **9**
  - export: **15** ｜ log-viewer: **5** ｜ system-settings: **5** ｜ platform-details: **2**
  - scraper-system-edit: **0** ｜ platform-merge: **0** ｜ merge-* 三页: **0**
- **剩余重点**：game-list（185 行）、platform-management（57 行）、media-download（34 行）、temp-subset-edit（17 行）、export（15 行）
- **方案**：按页面分批；HTML 加 `data-i18n`、JS 动态文本用 `I18n.t()`；三语 locale 补 key

---

## 8. 已导入数据去重与删除已导出文件（需慎重讨论）

**需求来源**：用户提出希望能对已导入的信息进行去重，以及删除已导出的文件。

**需慎重考虑的点**：
- **安全**：删除类操作不可逆，必须有二次确认、防误删机制（回收站/备份/白名单）
- **审查**：去重判据如何定义（按 ROM 名？CRC？路径？显示名？多平台同名游戏是否算重复？），去重结果是否需要人工复核
- **范围**：去重是自动还是提供预览工具？删除已导出文件是单个文件还是整目录？

**状态**：暂缓，待讨论后明确方案再实施。
