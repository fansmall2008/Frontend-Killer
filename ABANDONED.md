# 废弃 / 搁置的功能评估

> 本文件存放**已决定暂不实施**的功能调研记录，保留结论与实测数据，方便日后"心情好再说"时不必重做功课。
> 与进行中的 `TODO.md` 分离，避免污染活跃待办。

---

## GameDB 数据源引入（2026-10-14 决定：**暂不引用，意义不大**）

### 背景

GB/GBC/SNES 头部提取的 `internal_title` 无法通过 LIKE 匹配 rom_db 的 No-Intro 名（命名体系不同：头部标题 `POKEMON RED` ≠ No-Intro 名 `Pocket Monsters - Aka (Japan)`）。libretro RDB / No-Intro DAT 均无 `internal_title` 字段（已实测）。GameDB（niemasd/GameDB-{console}）恰好提供「头部内部标题 → No-Intro 名」的映射，一度考虑接入。

### 最终评估：GameDB 的边际价值（用数据说话）

> **结论**：GameDB 不是雪中送炭，是锦上添花，而且**只给 GB/GBC/SNES 三个平台添花**；NES 及另外约 48 个平台价值为 0。

**唯一不可替代的价值**：提供 `internal_title → No-Intro 名` 索引，把软件现有"提了头部标题却匹配不上"的能力盘活。

**GB/GBC 实测**（本地 NAS `GB / GBC` 共 273 个文件）：

| 匹配层 | 命中 | 累计 |
|---|---|---|
| CRC（rom_db 现有能力） | 156 | 57.1% |
| + GameDB `title+checksum` 双键（安全，几乎无假命中） | +16 | 63.0% |
| + GameDB `title` 单键（激进，有假命中风险） | +58 | 最高 78.3% |

救回的都是 CRC 彻底对不上、但头部标题保留的汉化/改版：口袋妖怪红/绿/蓝/黄、SD高达外传、吞食天地、女神转生外传 Last Bible II、宇宙巡航机。**可信区间 +6pp，激进上限 +21pp。** SNES 结构类似（86% 有 internal_title）。

**全平台字段实测**（53 个平台 JSON，约 8.2 万条）：

| 平台 | 记录 | 有 internal_title | 有 checksum | 价值 |
|---|---|---|---|---|
| GB | 2186 | 1564 (72%) | ✓ | ★★★ |
| GBC | 2472 | 1795 (73%) | ✓ | ★★★ |
| SNES | 4147 | 3583 (86%) | ✓ | ★★★ |
| GBA | 2804 | 2801 | ✗ | ★（已有 serial） |
| Genesis | 3234 | ✗ | 1149 | ★（已有 serial） |
| NES | 6551 | ✗ (0/6551) | ✗ | ☆ 无 |
| 其余 48 平台 | - | - | - | ☆ 无 |

下载 URL：`https://github.com/niemasd/GameDB-{Console}/releases/latest/download/{Console}.data.json`（TSV 同路径）。JSON 结构统一：key=crc32，value=字段字典（`internal_title / global_checksum_expected / header_checksum_expected / release_name / title` 等），Jackson 可直接解析。

**许可证**：官方欢迎第三方使用，仅要求署名 GameDB + 数据来源（No-Intro），比 libretro 的 CC BY-SA 4.0 宽松。

### 曾计划的接入方案（若日后重启）

- 放弃"转 clrmamepro DAT"（clrmamepro 是 game→rom 两级结构，GameDB 是 per-rom 索引，语义扭曲）。
- 改为导入管线新增 `GameDbJsonParser`（Jackson 直接解析 GameDB JSON）。
- 新表 `rom_db_internal`（system_id, crc32, internal_title, checksum, game_name, region, developer_id, rom_version, rom_type, serial）。
- 匹配链：头部提取 internal_title(+checksum) → `rom_db_internal` → 拿到 No-Intro 名/crc32 → rom_db。
- 用户操作：只下载 GB/GBC/SNES 三个 `.data.json`（约 3.6MB）→ 放目录 → 导入。
- 需注意：`internal_title` 归一化（whitespace-stripped + 特殊字符转 `_`）；GB 头部 global checksum 提取需在 `CartridgeHeaderReader` 补充（偏移 0x14E-0x14F，在 ZIP 0x200 窗口内）；SNES 四元组 = `developer_ID + internal_title + rom_version + checksum`。

### 为何搁置

- 收益窄：只提升 3 个平台，可信区间仅 +6pp。
- 存在更划算的免费方案：NES/GB「去头 CRC 回退」是纯代码、零新数据源、零用户操作（见 TODO.md §15.3，NES 实测 +25.7pp），优先级远高于接入 GameDB。
- 接入成本不低：新解析器 + 新表 + 归一化 + 假命中消歧 + 用户手动下载 + 每月刷新维护。

---

## 附：GameID 项目（niemasd/GameID）识别方法参考（2026-10-14 一并归档）

> 项目地址：https://github.com/niemasd/GameID ｜ Go 移植版：https://github.com/ZaparooProject/go-gameid

各平台识别方法与数据库 key：

| 平台 | 识别方法 | 数据库 key |
|---|---|---|
| NES | 整个 .nes 文件 CRC32 | `int(crc32,16)` |
| SNES | 移除 512B 头 → LoROM(0x7FC0)/HiROM(0xFFC0) → 验证 checksum+complement==0xFFFF → 提取四元组 | (developer_ID, internal_title_hex, rom_version, checksum) |
| GB/GBC | internal_title + global_checksum_expected | (title, checksum) |
| GBA | game_code | game code |
| N64 | cart_ID（自动判大/小端/字节交换） | cart ID |
| Genesis | domestic_serial（去空格/连字符） | cleaned serial |
| Saturn/SegaCD | IP.BIN product_code（去空格/连字符） | cleaned serial |
| NeoGeoCD | UUID + volume_ID | (UUID, volume_ID) |
| GC | game_ID（DOL-XXXX-XXX 的 XXXX） | middle of serial |
| PSX/PS2 | SYSTEM.CNF（"-"→"_"） | normalized serial |
| PSP | DISC.TXT / ULISTBL.BIN | serial |
| 3DO | header serial | serial |

数据库来源：GameDB-{平台} 各仓库（No-Intro 卡带 + Redump 光盘 + 专有），打包为 `db.pkl.gz`，由 `scripts/build_db.py` 生成。

与我们的差异：NES/GC/Saturn/PS1/PS2 思路相同；GB/GBC 我们用 title 前缀匹配、GameID 用 title+checksum；SNES 我们用 CRC32+头部名称匹配、GameID 用四元组精确匹配。

---

## 附：ROM「带头 / 去头」形态与跨平台头差异（通用知识，长期有效）

> 这段虽由 GameDB 调研引出，但属于长期有效的通用结论，留存备查。

**1）同一份 ROM 本体可打包成"带头"与"去头"两种存档**：No-Intro 对同款常同时收录两行，如 `Duck Hunt (World).nes size 24592 crc E9F806A9`（带头）与 `Duck Hunt (World).unh size 24576 crc 24598791`（去头，正好差 16 字节）。

**2）各平台"头"含义不同，不能一概而论**：

| 平台 | 头情况 | CRC 影响 |
|---|---|---|
| NES/FC | 文件必须带 16 字节 iNES 头（mapper 号），几乎无无头文件；头内字节常被改 | 带头 CRC 全崩、去头才对上 |
| SNES/SFC | 真有物理两种：`.sfc` 无附加头 / `.smc` 多 512B 拷贝器头 | 需按 512B 偏移分别处理 |
| GB/GBC | 原生无"文件头"，标题/checksum 在 ROM 内 0x134 | 整个文件直算 CRC 即中 |
| FDS/512B 头类 | 有可选头 | 需另处理偏移 |

**3）核心结论**：NES 本地集合匹配 0 命中的真因是"头字节被改、本体干净"，不是内容篡改。修复=剥 16 字节头只比本体，命中 rom_db 里 No-Intro 的 `.unh` 记录。边界：去头只对"只改头、本体未动"有效；`Contra (CN)` 这类连本体都改的真汉化/扩容去头也对不上。
