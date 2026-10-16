# 待办清单

> 记录在进行中和计划中的开发任务。已完成事项见 [MEMO.md](MEMO.md)；已决定暂不实施的功能调研见 [ABANDONED.md](ABANDONED.md)。
> 最后更新：2026-10-14（GameDB/GameID 评估已移入 ABANDONED.md 归档；保留本地匹配架构现状与 NES/GB 去头 CRC 回退活跃待办）
>
> **当前焦点：v1.3 matched 字段 + 全量操作已完成，待全流程测试**
>
> **明日重点（10-12）：**
> 1. 全流程测试除虫（matched + 应用于所有搜索结果）
> 2. 游戏搜索匹配优化
> 3. GC 平台匹配机制

---

## 1. 数据导入：线程数选择功能

**现状**：页面上的"线程数"选择在数据库插入阶段基本无效——`importGamesInBatches` 中虽然按 threadCount 分 chunk 并行，但 `synchronized (GameServiceImpl.class)` 锁导致实际串行执行。媒体发现阶段用固定 4 线程（不受此参数控制），已经真正并行。

**想法**：
- 去掉 `synchronized` 锁，改用真正的多线程批量插入
- 或研究 H2 的批量写入 API（`PreparedStatement.addBatch` / 多值 INSERT）
- 让页面线程数选择真正生效，或干脆从 UI 上移除该选项避免误导

**来源**：用户问"线程的选择功能现在有用吗？"后的调查结论，暂缓处理。

---

## 2. 游戏信息缓存（SS manifest）——剩余项

> 主体已完成（game_manifest 缓存表、系统设置开关、刮削侧缓存优先、导出预检与父 rom 找齐），详见 [MEMO.md](MEMO.md) v1.2 新功能。

- ⏳ **媒体下载 cache-first**：媒体下载任务命中 manifest 缓存时跳过已下载媒体（待第一步 UX 验证确认后实施）
- ⏳ **cue/gdi 多文件第二步**：清单优先扫描 + 全 `FILE` 行解析 → 写 `multiFileContent`、被引用者不单列、导出改名时回写 N+1 条路径、匹配只认数据轨
- ❌ 明确不做（本期）：romset/DAT 解析、BIOS 名单、通用 DAT 缺件报告（仅保留父 rom 未找到窄口径报告）；无单文件锚点的文件夹式（Daphne/DOS/Win3.xx）

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

> 路径规则与已完成阶段详见 [MEMO.md](MEMO.md) v1.2 新功能

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

---

## 9. 平台未绑定系统时的系统匹配引导（预开发）

**场景**：平台 systemId 为空时，用户点开平台 → 根据平台文件夹名在系统列表中模糊匹配 → 弹窗展示候选系统供选择；匹配不到则列出全部系统；同时提供“这些都不是，我自己选”入口。

**交互规则**：
- 取消：不记忆，下次点开仍弹
- 选定系统后：先检查该系统（平台）是否已刮削，未刮削则自动发起刮削

**模糊匹配机制（需确认）**：
- 现有数据：`ScraperSystem` 已有 `name/nameEn/nameCn` 与 `nomEu/nomLaunchbox/nomHyperspin/nomRecalbox/nomRetropie/nomsCommun` 多平台别名池，可直接作为匹配词典
- 难点：“sfc”、“nintendo fc”这类缩写与带前缀的文件夹名 → **由第 10 条的映射表解决**（系统别名类映射 + 用户选定后自动记录）
- 匹配链（按优先级）：
  1. 归一化（小写、去空格/连字符/下划线/特殊符号）后查映射表（用户自定义别名，最高优先级）
  2. 系统别名池（nom*）精确匹配
  3. 包含匹配（候选系统名/别名包含文件夹名；反向包含不可靠）
  4. 编辑距离（Levenshtein ≤ 阈值）兜底排序
  5. 加分项：平台目录内文件扩展名 vs 系统 `extensions` 字段（如 .sfc 直接命中 SNES）
- **学习机制**：弹窗选定系统后自动写入“文件夹名 → 系统”映射（可勾选“记住此映射”），后续同类文件夹名直接命中
- 需确认：匹配阈值与排序权重

> 依赖关系：先做第 10 条映射表基础设施，再做本条匹配引导（功能 1 复用功能 2 的映射表与 UI）

---

## 10. 方言映射（术语映射表 + 模板函数）（预开发）

**目标**：让用户可自定义字段归类/改名。例如游戏类型即使大类也有 31 种，用户定义 Beat'em Up / act / action → 清版游戏。

**设计**：
- DB 新增映射表：`source_term → target_term`；**基数：多对一函数**（多个 source 可映射同一 target；每个 source 唯一映射一个 target，保证 `map()` 确定性——不允许一个 source 对应多个 target）；唯一键建在 source_term(+category) 上，target 可重复；建议加 `category` 字段区分用途（genre 归类 / 系统别名）
- **双用途**：① 模板表达式引擎（`TemplateExpressionEngine`）新增内置函数 `map(value)` —— 导出时命中映射表返回映射值，未命中原样返回；② 系统别名映射 —— 供第 9 条文件夹名匹配（用户选定系统后自动记录“文件夹名 → 系统”）
- 映射仅作用于模板输出（导出），不改数据库原始值
- 需确认：映射表管理 UI 位置（系统设置页新增管理区？）；作用范围（全局 or 按模板/平台）；匹配是否区分大小写与空白

---

## 11. v1.3 光盘类平台刮削标识符重构

**背景**：光盘类镜像（ISO/CHD/BIN+CUE）的 CRC32 极不稳定——不同 dump 工具、格式转换会导致 CRC 完全不同，匹配率极低。

**核心方案**：本地统一标识库（H2 `disc_serial_cache` 表）+ SS 元数据查询。从光盘文件中提取 serial → 查本地库得到游戏名 → 调 SS 获取完整元数据。

### 11.1 数据库 Schema

```sql
-- 光盘标识缓存表（新表，存储 Redump + OpenVGDB + SS 三源合并数据）
CREATE TABLE disc_serial_cache (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    system_id   INT NOT NULL,          -- SS systemId
    serial      VARCHAR(50) NOT NULL,  -- 光盘编号
    game_name   VARCHAR(500) NOT NULL, -- 标准游戏名
    source      VARCHAR(20),           -- 数据来源: 'ss' / 'openvgdb' / 'redump'
    UNIQUE(system_id, serial)
);
CREATE INDEX idx_disc_serial_lookup ON disc_serial_cache(system_id, serial);

-- Game 表新增字段
ALTER TABLE game ADD COLUMN disc_serial VARCHAR(100);

-- 系统设置新增项（复用现有 system_settings 表）
-- key: 'disc_serial_enabled'    value: 'true'/'false'
-- key: 'disc_serial_strategy'   value: 'auto'/'serial_first'/'crc_first'/'serial_only'
```

**支持光盘编号的系统列表**：从 `disc_serial_cache` 动态生成（`SELECT DISTINCT system_id, COUNT(*) GROUP BY system_id`），无需单独维护。系统管理页展示各系统的 serial 数据量。

### 11.2 数据源与覆盖（11 个光盘平台）

| 平台 | systemId | SS serialnum | OpenVGDB | Redump | 覆盖方式 |
|---|---|---|---|---|---|
| PS1 | 57 | ✅ | ✅ 7,029 | ✅ | 三源保险 |
| PS2 | 58 | ✅ | — | ✅ | 双保险 |
| GameCube | 13 | ✅ | ✅ 1,789 | ✅ | 三源保险 |
| Wii U | 18 | ✅ | — | — | SS 单源 |
| PSP | 61 | — | ✅ 3,281 | ✅ | 双保险 |
| Saturn | 22 | — | ✅ 1,328 | ✅ | 双保险 |
| Mega-CD | 20 | — | ✅ 254 | ✅ | 双保险 |
| PCE-CD | 114 | — | ✅ 79 | ✅ | 双保险 |
| Dreamcast | 23 | — | — | ✅ ~1,516 | Redump 单源 |
| Wii | 16 | — | — | ✅ ~3,780 | Redump 单源 |
| Neo Geo CD | — | — | — | ✅ 111 | Redump 单源 |

**数据导入**：
- Redump：Docker 爬虫已开发（`d:\code\redump-scraper\`），正在本地运行，输出 TSV 文件
- OpenVGDB：从 `openvgdb.sqlite`（用户桌面）用 Java + SQLite JDBC 提取 serial→name
- SS serialnum：运行时对 PS1/PS2/GC/WiiU 调用 API 补充

### 11.3 标识提取（三级递降）

| 层级 | 方法 | 适用格式 | 速度 |
|---|---|---|---|
| L0 | 文件名正则 | 所有格式 | 极快 |
| L1 | ISO9660 PVD 卷标 | .iso / .bin | 快 |
| L2 | SYSTEM.CNF 解析 | .iso / .bin | 快 |
| — | 不提取 | .chd | 仅走 L0 |

**各平台正则**：

| 平台 | 正则模式 | 示例 |
|---|---|---|
| PS1 | `S[LCP]U[SX]-\d{5}` | SLUS-12345 |
| PS2 | `S[LCP][UJ][SX]-\d{5,6}` | SLUS-21345 |
| PSP | `U[CL][EJUS][M]-\d{5}` | UCUS-98600 |
| Saturn | `T-\d{4,5}[GH]?` | T-1234G |
| Mega-CD | `[GT]-\d{4,5}` | T-12345 |
| PCE-CD | `[A-Z]{4}CD-\d{4}` | AKCD-0001 |
| Dreamcast | `MK-\d{5}\|HDR-\d{4}\|T-\d{4}` | MK-51064 |
| Wii | `RVL-[A-Z0-9]{4}` | RVL-RJ2E |
| GameCube | `DOL-[A-Z]{4}` | DOL-GALE |
| 3DO | `FZ-S\d{4}` | FZ-SJ4452 |
| NGCD | `NGCD-\d{3,4}\|ADCD-\d{3}` | NGCD-030 |

### 11.4 匹配流程

```
光盘文件 → 提取 serial（L0 文件名 → L1 PVD → L2 SYSTEM.CNF）
    ↓
SS serialnum 直查（仅 PS1/PS2/GC/WiiU，4 平台）
    ├─ 命中 → 拿到 gameId → 刮削元数据 → ✅
    └─ 未命中/不支持 ↓
本地 disc_serial_cache 查询（serial + systemId → game_name）
    ├─ 命中 → 用 game_name + systemId 调 SS romnom → ✅
    └─ 未命中 ↓
CRC32 匹配（现有行为兜底）
    ├─ 命中 → ✅
    └─ 未命中 → 标记失败
```

**刮削策略选择**（用户可在刮削弹窗/系统设置中选择）：
- **自动**（默认）：光盘编号 → CRC → 游戏名
- **光盘编号优先**：先走 serial 路径
- **CRC 优先**：现有行为
- **仅光盘编号**：不尝试 CRC

### 11.5 UI 交互设计

**系统设置**：
- 全局开关：☑ 启用本地光盘编号匹配
- 匹配策略选择下拉

**系统管理页**：
- 显示各系统 serial 缓存数据量（从 disc_serial_cache 动态统计）
- 仅缓存中有数据的系统才显示光盘编号相关信息

**游戏列表页**：
- 工具栏新增按钮「本地匹配光盘编号」（tooltip：从 ISO/BIN 文件中读取光盘编号，可能需要一些时间）
- 未勾选游戏 → 匹配当前平台所有游戏
- 已勾选游戏 → 仅匹配选中游戏
- 条件显示：仅当平台绑定的 systemId 在 disc_serial_cache 中存在时显示

**平台管理页**：
- 每个平台操作区新增「本地匹配光盘编号」按钮
- 条件显示同上

**游戏编辑页**：
- 新增 disc_serial 字段展示/编辑（条件显示）

### 11.6 开发任务拆分

- [x] **11.6.1 DB Schema + Flyway 迁移** ✅
  - V1.0.21 迁移脚本 + init.sql 同步
  - disc_serial_cache 表 + game.disc_serial 字段
  - DiscSerialCache 模型 + Mapper（MERGE 语义 insertIgnore）
  - system_settings 新增 disc_serial_enabled / disc_serial_strategy（待补）

- [x] **11.6.2 数据导入：Redump TSV → disc_serial_cache** ✅
  - Redump 爬虫已完成（d:\code\redump-scraper\），4 平台共 5761 条
  - JSON 文件打包到 classpath: data/disc-serial/{dc,wii,ngcd,3do}.json
  - DiscSerialDataInitializer 启动时自动灌入（幂等，已有数据则跳过）
  - 修正 systemId: DC=23, Wii=16, NGCD=70, 3DO=29

- [x] **11.6.3 数据导入：OpenVGDB SQLite → disc_serial_cache** ✅
  - ExportOpenVGDB.java 从 SQLite 导出 serial→name 为 JSON
  - PSP: 3204, Saturn: 1097, Mega-CD: 223, PCE-CD: 73
  - JSON 文件打包到 classpath，DiscSerialDataInitializer 启动时自动灌入
  - source 标记为 'openvgdb'（区分 Redump）

- [x] **11.6.4 文件名正则提取器（L0）** ✅
  - DiscSerialExtractor 工具类，覆盖 PS1/PS2/PSP/Saturn/MegaCD/PCECD/DC/Wii/GC/NGCD
  - 从 ROM 文件名提取 serial，支持按 systemId 选择正则

- [ ] **11.6.5 ISO PVD 卷标读取器（L1）**
  - 读 ISO sector 16（偏移 0x8000），提取卷标
  - 支持 .iso / .bin+.cue / .bin

- [ ] **11.6.6 SYSTEM.CNF 解析器（L2）**
  - PS1/PS2 专用，从镜像提取 SYSTEM.CNF 解析 BOOT 字段

- [x] **11.6.7 匹配引擎 DiscSerialMatchService** ✅
  - serial → disc_serial_cache 查询 → game_name（含归一化回退）
  - 批量匹配 batchMatch()（游戏列表页按钮用）
  - importOpenVGDB / importRedump 数据导入接口
  - DiscSerialController REST API（/api/disc-serial/*）

- [x] **11.6.8 刮削流程改造 ScraperServiceImpl** ✅（基础集成）
  - searchGame() 优先从文件名提取 serial → 查缓存库 → 用标准游戏名作为 romnom
  - 匹配成功时 discSerial 写回 game 记录
  - 未命中时回退到原有行为（CRC + 文件名）
  - 策略选择 / 刮削弹窗选项（待补）

- [x] **11.6.9 「本地匹配光盘编号」按钮（后端）** ✅（API 已建）
  - POST /api/disc-serial/match（gameIds + systemId）
  - GET /api/disc-serial/stats（各系统缓存量统计）
  - GET /api/disc-serial/check/{systemId}（检查系统是否支持）
  - POST /api/disc-serial/import/openvgdb + /import/redump（数据导入）
  - 异步任务化 / 复用 scrape_task 任务池（待补）

- [x] **11.6.10 「本地匹配光盘编号」按钮（前端）** ✅（game-list 已完成）
  - game-list.html 工具栏按钮（条件显示：系统有缓存数据时）
  - 选中联动：勾选游戏后启用
  - 表格新增「光盘编号」列（条件显示）
  - i18n 三语支持
  - platform-management.html 操作按钮（待补）

- [x] **11.6.11 系统管理页 serial 数据展示** ✅
  - scraper-system-list.html 新增「光盘编号库」列
  - 加载 /api/disc-serial/stats 显示各系统缓存条数
  - i18n 三语支持

- [x] **11.6.12 游戏编辑页 disc_serial 字段** ✅
  - game-edit.html 新增「光盘编号」输入框（条件显示：有值时）
  - 支持手动编辑修正
  - i18n 三语支持

### 注意事项
- `serialnum` 搜索时仍需传 `systemeid`
- PSP 的 `romsupporttype` 被 SS 错误标记为 `cartouche`
- Saturn 在 SS 中是非标准纯数字格式，OpenVGDB 中有标准 `T-xxxxx` 格式
- SS 论坛回复帖已写好：`ss_forum_reply.md`
- Redump 爬虫已开发并正在运行：`d:\code\redump-scraper\`

> 参考文件：`ss_ps2_sample.json`、`ss_psp_sample.json`、`ss_forum_reply.md`、`openvgdb.sqlite`（用户桌面）、`d:\code\redump-scraper\`

---

## 12. ROM 识别数据库（rom_db）——统一匹配库重构

> 最后更新：2026-10-10
> 状态：**核心代码已完成，编译通过**，待用户导入数据后实测

### 目标
构建统一的 ROM 识别数据库，替代现有 `disc_serial_cache`，支持：
- **卡带/单文件平台**：通过 CRC32 快速匹配游戏名
- **光盘平台**：通过 serial 编号快速匹配游戏名
- **通用**：游戏名模糊搜索

### 已完成项 ✅
- [x] 数据源确认：libretro-database metadat（No-Intro + Redump 合并，CC BY-SA 4.0）
- [x] 数据加载方式：用户手动复制 `.dat` 文件到 `data/rom-db/{redump,no-intro,serial}/`
- [x] DB Schema + Flyway 迁移（V1.0.22）+ init.sql 同步
- [x] RomDb 模型 + RomDbMapper + XML
- [x] RomDbPlatformMapping：libretro 平台名 → ScreenScraper systemId（覆盖 40+ 平台）
- [x] ClrMameProDatParser：解析 clrmamepro DAT 格式
- [x] RomDbDataInitializer：启动时扫描目录增量导入（幂等）
- [x] RomDbService + Impl：CRC/serial/模糊搜索/批量匹配
- [x] RomDbController：REST API（stats/check/match/search/reimport）
- [x] 集成到 ScraperServiceImpl：刮削时优先查 rom_db，回退 disc_serial_cache
- [x] 前端更新：“本地匹配”按钮（统一光盘/卡带）、游戏编辑页查 rom_db

### 待完成
- [ ] 用户导入数据后实测（复制 libretro-database metadat 到 data/rom-db/）
- [ ] 日文名/中文名支持（当前只有 game_name，需扩展多语言字段）
- [ ] 清理旧代码（disc_serial_cache、OpenVGDB 导入、Redump 爬虫）——保留向后兼容，待稳定后移除
- [ ] 数据管理页面（查看/搜索 rom_db 内容）
- [ ] **目录结构方案决策**（暂缓，两个备选）：
  - 方案 A（当前）：保持 `redump/` `no-intro/` `serial/` 子目录，用户整体复制
  - 方案 B（待评估）：单目录 + 自动检测格式，简化用户操作，但同名文件冲突难处理

### 架构说明
```
用户复制 .dat 文件 → data/rom-db/{redump,no-intro,serial}/
                          ↓
              RomDbDataInitializer（启动时扫描）
                          ↓
              ClrMameProDatParser（解析 clrmamepro 格式）
                          ↓
              RomDbPlatformMapping（平台名 → systemId）
                          ↓
              rom_db 表（H2 嵌入式数据库）
                          ↓
     ┌────────────┼────────────┐
     ↓               ↓               ↓
 光盘平台       卡带平台       模糊搜索
 serial匹配    CRC32匹配     游戏名LIKE
```

### 注意事项
- 现有 `disc_serial_cache` 保留，rom_db 未命中时自动回退
- Redump 数据（已爬 5,761 + PS3 3,303 条）仍可通过旧链路使用
- OpenVGDB 数据不可靠，应被 Redump/No-Intro 替代
- 部分 ISO 文件名与实际内容不匹配（如 DOA2 问题），这是数据源问题非代码问题

---

## 13. matched 字段与“应用于所有搜索结果”全量操作 ✅ 代码完成，待测试

### 已完成
- [x] DB: game 表 `matched BOOLEAN DEFAULT FALSE`（Flyway V1.0.23）
- [x] Game 模型 + GameMapper.xml + GameService + Controller 全链路
- [x] 自动标记：SS platform 刮削 + rom_db 精确匹配 → matched=true
- [x] game-list 筛选区：匹配状态筛选（已匹配/未匹配）
- [x] game-edit 页面：已确认匹配勾选框
- [x] 工具栏：应用于所有搜索结果勾选框 + 匹配总数显示
- [x] 所有 8 个批量操作适配全量模式（刮削/rom_db匹配/删除/编辑/翻译/迁转/切换译文）
- [x] 后端 resolveGameIdsFromFilters() 辅助方法 + 5 个端点支持 filters
- [x] ScraperRequest 筛选字段 + hasFilters() + folderPath 修复
- [x] 三语 i18n

### 待测试
- [ ] 筛选功能验证（匹配/未匹配组合查询）
- [ ] 编辑页勾选保存验证
- [ ] 自动标记验证（SS 刮削/rom_db 匹配后 matched=true）
- [ ] 全量批量操作验证（勾选后操作所有搜索结果）
- [ ] 边界情况（空筛选+全量模式 = 操作整个平台）

---

## 14. 第三方数据源署名/Attribution（已决策：简洁推荐 + 致谢）

> 2026-10-13 决策：**不列整页数据来源列表**。

### 决策依据

- 我们**不打包、不嵌入**任何第三方的项目代码或数据文件——数据文件由用户自行下载、放入目录导入
- 软件只做**格式兼容**（clrmamepro DAT / RDB 两种通用格式），数据从哪来是用户自己的事
- 因此署名义务最轻：只需**推荐导入来源 + 一句感谢**，不需要逐条列出 No-Intro/Redump/TOSEC/MAME/评级机构等十几个组织

### UI 最终文案（一句推荐 + 一句感谢）

> 数据来源（推荐）：libretro-database，或任何 clrmamepro / RDB 格式数据。
> 感谢 No-Intro、Redump、TOSEC 等游戏数据社区的无偿贡献。

### 例外边界（未来如果改变策略才需要升级）
- 若未来**打包数据进发行包**：GameDB 仅要求一句 attribution；libretro 系需遵守 CC BY-SA 4.0（署名 + 相同方式共享）
- 若未来**内置自动下载数据**：才需要完整来源列表

---

## 15. 本地匹配架构现状与 GameID 项目对比（2026-10-13）

### 15.1 我们软件的本地匹配详细情况

#### 匹配入口

**触发方式**：游戏列表页 / 平台管理页的「本地匹配」按钮（手动触发，非自动）

**分流机制**：根据平台类型自动选择匹配路径

| 路径 | 适用平台 | 匹配字段 | 数据来源 |
|---|---|---|---|
| **L1 CRC32** | 所有卡带/掌机平台（默认） | crc32 → rom_db.crc32 | No-Intro dat |
| **L2 光盘 serial** | 白名单光盘平台 | serial → rom_db.serial | Redump dat |
| **L3 卡带头部** | GB/GBC/GBA/N64/Genesis/SNES | 头部提取的 ID → rom_db 名称/serial | ROM 内部数据 |

#### 各平台当前实现详情

| 平台 | systemId | 匹配方式 | 实现类 | 方法 | 备注 |
|---|---|---|---|---|---|
| PS1 | 57 | L2 serial | SystemCnfReader | 解析 SYSTEM.CNF 的 BOOT 行 | 100% 可靠 |
| PS2 | 58 | L2 serial | SystemCnfReader | 解析 SYSTEM.CNF 的 BOOT2 行 | 100% 可靠 |
| PS3 | 59 | L2 serial | ParamSfoReader | 解析 PS3_GAME/PARAM.SFO 的 TITLE_ID | 100% 可靠 |
| NGC | 13 | L2 serial | NgcHeaderReader | 镜像头部 6 字节 game ID | 100% 可靠 |
| Saturn | 22 | L2 serial | CartridgeHeaderReader | 搜索 "SEGA SEGASATURN" magic → +0x20 读 10 字节 | 仅 BIN/CUE |
| MegaCD | 20 | L2 serial | CartridgeHeaderReader | 搜索 "SEGA SEGAMEGADRIVE" magic → +0x20 读 10 字节 | 仅 BIN/CUE |
| Dreamcast | 23 | L2 serial | CartridgeHeaderReader | 搜索 "SEGA SEGAKATANA" magic → +0x20 读 10 字节 | 仅 BIN/CUE |
| GB | 9 | L3 头部 | CartridgeHeaderReader | 偏移 0x0134，16 字节 title | ZIP 内可读 |
| GBC | 10 | L3 头部 | CartridgeHeaderReader | 偏移 0x0134，16 字节 title | ZIP 内可读 |
| GBA | 12 | L3 头部 | CartridgeHeaderReader | 偏移 0xAC，4 字节 game code | ZIP 内可读 |
| N64 | 14 | L3 头部 | CartridgeHeaderReader | 偏移 0x3C，2+1 字节 cart ID | 支持大/小端/字节交换 |
| Genesis | 1 | L3 头部 | CartridgeHeaderReader | 偏移 0x180，12 字节 domestic serial | hack ROM 命中率高 |
| **SNES** | **4** | **L1 CRC32 + L3 头部** | **CartridgeHeaderReader** | **LoROM 0x7FC0 / HiROM 0xFFC0，21 字节 internal title** | **⚠️ 刚添加，未测试** |
| NES | 3 | L1 CRC32 | 默认路径 | **无头部提取**，编号不存 ROM 中 | ⚠️ Hack/汉化版 CRC 会变 |
| **黑名单** | | | | | |
| PC-FX | 72 | 阻止匹配 | RomDbMatchingConfig | 光盘无标准文件系统 | CRC 全扫耗时长 |
| PCECD | 114 | 阻止匹配 | RomDbMatchingConfig | 文件名正则不可靠 | |
| Wii | 16 | 阻止匹配 | RomDbMatchingConfig | 文件名正则不可靠 | |
| NGCD | 70 | 阻止匹配 | RomDbMatchingConfig | 文件名正则不可靠 | |
| 3DO | 29 | 阻止匹配 | RomDbMatchingConfig | 文件名正则不可靠 | |
| PSP | 61 | 阻止匹配 | RomDbMatchingConfig | UMD 无内部文件提取 | |

#### CRC32 计算规则（`calculateCrc32` / `calculateArchiveCrc`）

- **单文件**：直接读文件内容 → `java.util.zip.CRC32` 计算
- **ZIP 包**：使用 Apache Commons Compress + 多编码回退（cp437→GBK→UTF-8），找到同名或最大的 ROM entry，**解压后实际读取数据并计算 CRC32**
  - 2026-10-13 修复：之前用 `entry.getCrc()` 读 ZIP 元数据，现在实际读取数据计算
- **7z 包**：类似逻辑

#### matchBySerial 五级回退链

1. 精确匹配 serial
2. 去连字符匹配
3. DB 端 REPLACE 去连字符匹配
4. 前缀匹配（GB/GBC：game code → CGB-XXXX-REGION）
5. Game code 匹配（Genesis："MK1054" → "MK-1054-00"）

#### matchByNameFallback（名称回退）

CRC 匹配失败时，清理文件名后尝试模糊匹配：
- 移除 `[...]` 和 `(...)` 标签
- 移除 hack/ch/unl/beta/proto 后缀
- 按 `searchBySystemAndName` 查 rom_db

---

### 15.3 待办清单（SNES 测试相关）

- [ ] **SNES 测试**：验证新增的 LoROM/HiROM 头部提取是否正确
  - [ ] 验证 checksum + complement 校验逻辑
  - [ ] 验证 512 字节头部移除逻辑
  - [ ] 验证提取的 internal_title 能否通过名称匹配到 rom_db
  - [ ] ZIP 内的 SNES 目前无法走 L3 头部路径（超出 0x200 字节范围），依赖 L1 CRC32
- [ ] **NES/FC 去头 CRC 回退**（免费收益，不依赖任何新数据源）：完整文件 CRC 未命中时，剥离前 16 字节 iNES 头重算 CRC32 再查 rom_db（rom_db 已含 No-Intro `.unh` 去头条目）；本地实测 +25.7pp（244/950）。待评估 FDS/512B header 平台需处理 trainer 偏移。
- [ ] **SNES 是否加入刮削白名单**：当前已加入 `RomDbMatchingConfig.WHITELISTED_SERIAL_SYSTEMS`

---

## 16. （已搁置）GameDB 数据源评估

> 2026-10-14 决定：**GameDB 暂不引用，意义不大**。评估结论（仅 GB/GBC/SNES 有边际价值、可信区间 +6pp、NES 价值 0）、曾计划的接入方案（`GameDbJsonParser` + `rom_db_internal` 表）、GameID 项目对比、以及 ROM 带头/去头跨平台知识，已全部移入 [`ABANDONED.md`](./ABANDONED.md) 归档，日后需重启再看。
>
> 本节唯一沉淀出的**活跃免费收益**（不依赖 GameDB）已上提至 §15.3：NES/FC 去头 CRC 回退（本地实测 +25.7pp）。
