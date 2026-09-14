# Frontend-Killer 软件说明

> **版本**：v1.1-RC1 · **更新日期**：2026-09-13

Frontend-Killer（webGamelistOper）是一款基于 Web 的模拟游戏元数据管理平台，用于将 ROM 文件集合加工为各前端启动器可直接使用的游戏列表数据。

---

## 数据加工流程

```mermaid
flowchart LR
    A["🎮 ROM 文件"] --> B["📥 数据导入"]
    B --> C["🗄️ H2 数据库"]
    C --> D["🔍 刮削 / 检索"]
    D --> E["📝 元数据编辑"]
    E --> F["🔀 平台合并"]
    F --> G["📤 数据导出"]
    G --> H["📋 前端格式文件"]

    D -->|"自动"| I["🖼️ 媒体下载"]
    I --> H

    style A fill:#333,stroke:#00ffff,color:#fff
    style C fill:#1a1a2e,stroke:#00ffff,color:#00ffff
    style H fill:#006600,stroke:#44ff44,color:#fff
```

### 流程说明

| 步骤 | 说明 | 对应页面 |
|------|------|----------|
| 1. ROM 文件 | 用户的模拟游戏 ROM 文件目录 | — |
| 2. 数据导入 | 扫描 ROM 目录，按导入模板解析生成游戏记录 | [数据导入](03-data-import-cn.md) |
| 3. H2 数据库 | 所有游戏数据存储在内嵌 H2 数据库中 | [系统设置](11-system-settings-cn.md) |
| 4. 刮削/检索 | 通过 ScreenScraper API 获取游戏元数据与媒体 | [刮削系统](09-scraper-cn.md) |
| 5. 元数据编辑 | 手动修正/补充游戏名称、描述、翻译等 | [游戏编辑](06-game-edit-cn.md) |
| 6. 平台合并 | 将多个子平台合并为一个统一平台 | [平台合并](07-platform-merge-cn.md) |
| 7. 数据导出 | 按目标前端模板输出 gamelist.xml 等文件 | [数据导出](08-export-cn.md) |
| 8. 媒体下载 | 自动下载截图、封面、视频等媒体文件 | [媒体下载](10-media-download-cn.md) |

---

## 数据生产流程图

```mermaid
flowchart TD
    subgraph INPUT["📥 数据输入阶段"]
        I1["选择 ROM 目录"] --> I2["选择导入模板"]
        I2 --> I3["设置扫描深度"]
        I3 --> I4["开始扫描并导入"]
        I4 --> I5["生成游戏基础记录\n（名称、路径、ROM文件信息）"]
    end

    subgraph SCRAPE["🔍 数据刮削阶段"]
        S1["配置 ScreenScraper 账号"] --> S2["初始化刮削系统"]
        S2 --> S3["为平台指定刮削系统"]
        S3 --> S4["执行游戏信息刮削"]
        S4 --> S5["获取元数据\n（开发商/发行商/类型/评分/描述…）"]
        S4 --> S6["创建媒体下载任务\n（截图/封面/视频…）"]
    end

    subgraph EDIT["📝 数据编辑阶段"]
        E1["逐条编辑游戏"] --> E2["修正名称/描述"]
        E1 --> E3["补充翻译名称/描述"]
        E1 --> E4["手动上传媒体"]
        E1 --> E5["检索 ScreenScraper 补充"]
    end

    subgraph MERGE["🔀 数据合并阶段"]
        M1["选择多个源平台"] --> M2["指定新平台名称"]
        M2 --> M3["执行合并"]
        M3 --> M4["处理冲突"]
    end

    subgraph EXPORT["📤 数据输出阶段"]
        X1["选择目标平台"] --> X2["选择前端模板"]
        X2 --> X3["选择导出范围"]
        X3 --> X4["导出数据文件 + 媒体文件"]
    end

    INPUT --> SCRAPE
    SCRAPE --> EDIT
    EDIT --> MERGE
    MERGE --> EXPORT

    style INPUT fill:#0a0a2e,stroke:#00ffff,color:#fff
    style SCRAPE fill:#0a0a2e,stroke:#ff00ff,color:#fff
    style EDIT fill:#0a0a2e,stroke:#ffd700,color:#fff
    style MERGE fill:#0a0a2e,stroke:#00ff88,color:#fff
    style EXPORT fill:#0a0a2e,stroke:#44ff44,color:#fff
```

---

## 页面导航图

```mermaid
graph TD
    HOME["🏠 首页\nindex.html"]

    HOME --> IMP["📥 数据导入\ndata-import.html"]
    HOME --> PM["📋 平台管理\nplatform-management.html"]
    HOME --> MERGE["🔀 平台合并\nplatform-merge.html"]
    HOME --> EXP["📤 数据导出\nexport.html"]
    HOME --> SS["⚙️ 系统设置\nsystem-settings.html"]

    PM --> GL["🎮 游戏列表\ngame-list.html"]
    GL --> GE["✏️ 游戏编辑\ngame-edit.html"]

    SS --> SSL["🔍 刮削系统管理\nscraper-system-list.html"]
    SSL --> SSE["📝 刮削系统编辑\nscraper-system-edit.html"]

    SS --> MD["🖼️ 媒体下载管理\nmedia-download.html"]
    SS --> TM["📊 任务管理\ntask-management.html"]
    SS --> LV["📃 日志查看器\nlog-viewer.html"]

    PM --> TSE["📦 临时子集修改\ntemp-subset-edit.html"]

    MERGE --> MR["📋 合并报告\nmerge-reports.html"]
    MR --> MRD["📄 报告详情\nmerge-report-details.html"]
    MRD --> MC["⚠️ 解决冲突\nmerge-conflicts.html"]

    style HOME fill:#1a1a2e,stroke:#00ffff,color:#fff,stroke-width:3px
    style IMP fill:#0a0a2e,stroke:#0088ff,color:#fff
    style PM fill:#0a0a2e,stroke:#0088ff,color:#fff
    style MERGE fill:#0a0a2e,stroke:#0088ff,color:#fff
    style EXP fill:#0a0a2e,stroke:#0088ff,color:#fff
    style SS fill:#0a0a2e,stroke:#0088ff,color:#fff
    style GL fill:#0a0a2e,stroke:#ff00ff,color:#fff
    style GE fill:#0a0a2e,stroke:#ff00ff,color:#fff
    style SSL fill:#0a0a2e,stroke:#ffd700,color:#fff
    style SSE fill:#0a0a2e,stroke:#ffd700,color:#fff
    style MD fill:#0a0a2e,stroke:#ffd700,color:#fff
    style TM fill:#0a0a2e,stroke:#ffd700,color:#fff
    style LV fill:#0a0a2e,stroke:#ffd700,color:#fff
    style TSE fill:#0a0a2e,stroke:#00ff88,color:#fff
    style MR fill:#0a0a2e,stroke:#00ff88,color:#fff
    style MRD fill:#0a0a2e,stroke:#00ff88,color:#fff
    style MC fill:#0a0a2e,stroke:#00ff88,color:#fff
```

---

## 文档目录

| 编号 | 文档 | 说明 |
|------|------|------|
| 00 | [总览](00-overview-cn.md) | 本文 — 流程图与页面索引 |
| 01 | [快速开始](01-quick-start-cn.md) | 从零到导出的最小操作步骤 |
| 02 | [首页](02-home-page-cn.md) | 首页功能卡片与导航 |
| 03 | [数据导入](03-data-import-cn.md) | ROM 扫描与导入配置 |
| 04 | [平台管理](04-platform-mgmt-cn.md) | 平台增删改查、刮削系统绑定 |
| 05 | [游戏列表](05-game-list-cn.md) | 游戏表格、搜索、批量操作 |
| 06 | [游戏编辑](06-game-edit-cn.md) | 单条游戏编辑、媒体管理、检索 |
| 07 | [平台合并](07-platform-merge-cn.md) | 多平台合并与冲突处理 |
| 08 | [数据导出](08-export-cn.md) | 导出配置与前端格式输出 |
| 09 | [刮削系统](09-scraper-cn.md) | ScreenScraper 集成与系统管理 |
| 10 | [媒体下载](10-media-download-cn.md) | 媒体下载任务管理与配额 |
| 11 | [系统设置](11-system-settings-cn.md) | 语言、账号、数据库备份 |
| 12 | [任务管理](12-task-management-cn.md) | 后台任务监控与日志 |
| 13 | [日志查看器](13-log-viewer-cn.md) | 实时日志查看 |
| 14 | [临时子集](14-temp-subset-cn.md) | 子集分离、编辑与同步 |
| 15 | [常见问题](15-faq-cn.md) | FAQ 与故障排查 |

---

## 支持的前端格式

| 前端 | 导入模板 | 导出模板 |
|------|----------|----------|
| Pegasus | `pegasus.json` / `pegasus-v3.json` | `pegasus.json` / `pegasus-v3.json` |
| ES-DE (EmulationStation) | `esde.json` / `esde-v3.json` | `esde.json` / `esde-v3.json` |
| RetroBat | `retrobat.json` / `retrobat-v3.json` | `retrobat.json` / `retrobat-v3.json` |
| EmuELEC | `emuelec.json` / `emuelec-v3.json` | `emuelec.json` |
| Lakka | `lakka.json` | `lakka.json` |
| Skraper (ES) | `skraper-es.json` / `skraper-es-v3.json` | — |
| WebGamelistOper | `webgamelistoper.json` / `webgamelistoper-v3.json` | — |

---

## 技术栈

- **后端**：Spring Boot 3.2.x + Java 17
- **数据库**：H2 嵌入式（单文件 `data/database/database.db`）
- **前端**：纯 HTML + CSS + JavaScript（无框架依赖）
- **刮削**：ScreenScraper API（CRC32 匹配）
- **部署**：可执行 JAR / Docker
