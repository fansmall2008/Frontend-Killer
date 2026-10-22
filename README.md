<p align="center">
  <img src="unraid/icon.png" alt="Frontend-Killer" width="160">
</p>

<h1 align="center">Frontend-Killer</h1>

<p align="center">A web-based game list management tool for emulator frontend metadata. Import, manage, export, and scrape game metadata across multiple frontend formats.</p>

## What's New in 1.3.0

- **Fast Local Matching** - Identify ROMs offline against a local `rom_db` before ever calling ScreenScraper. Whitelisted systems take the millisecond-level serial / cartridge-header path; everything else falls back to whole-file CRC32. Match local game data without scraping, or auto-carry the authoritative CRC/serial into scraping for a higher hit rate and fewer wasted API calls
- **rom_db Local Matching Database** - Import libretro-database (No-Intro + Redump DAT/RDB) and ClrMamePro data. A single unified table covers cartridge (CRC32) and disc (serial) platforms with multilang names and incremental merge import (same CRC only fills empty fields, never overwrites)
- **Multi-Tier Matching Pipeline** - L0 filename serial → L1 CRC32 → L2 disc serial (SYSTEM.CNF / PARAM.SFO / IP.BIN / GameCube ID) → L3 cartridge header → name fallback; ZIP/7z use the inner ROM entry CRC
- **Black/White-List Routing** - 16 whitelist systems fast-tracked to serial/header extraction, 5 disc systems with no reliable identifier blocked from local matching, all others on CRC32; fixed order serial → CRC → filename
- **Per-Operation Method Selection** - Serial / CRC / filename checkboxes on the match modals; unchecked methods are skipped entirely with no I/O
- **Fuzzy Match + Manual Confirmation** - Still-unmatched games get a candidate list the user confirms before write-back, so near-miss titles are never force-matched
- **Manual Data-Import UI** - Copy `.dat`/`.rdb` files under `data/rom-db/`, trigger import from system settings with a live progress bar, plus a confirm-guarded “clear match database” reset
- **About & Copyright Page** - New tri-lingual (中文 / English / 日本語) page covering the development story, acknowledgments, copyright notice and code-usage policy
- **Scraper Accuracy** - The scraping pipeline now queries rom_db first and substitutes the authoritative CRC/serial before hitting ScreenScraper
- **Fixed** - `SsNameNormalizer` regex hang; rich-text i18n flattening that wiped the libretro attribution (now rendered via `data-i18n-html`)

> Local matching needs the rom_db data, which is **not** bundled in any package - download libretro-database / ClrMamePro data yourself and import it from system settings.

---

## Local Matching — Supported Systems & Formats

Local matching identifies a ROM by an **in-ROM identifier** (disc serial or cartridge header ID) rather than hashing the whole file. `RomDbMatchingConfig` routes every system through a three-tier black/white list:

- **Whitelist** → millisecond-level serial / cartridge-header extraction (the 16 systems below)
- **Default** → whole-file **CRC32** (every other cartridge / arcade system)
- **Blacklist** → local matching **blocked** (disc systems with no reliable internal serial)

The blacklist is an absolute block, checked first (it hides the UI button and rejects the API call). Within an enabled run the fixed order is **serial → CRC → filename**.

### ✅ Whitelist — 16 systems (serial / header extraction)

| System (id) | Identifier source | Supported image formats |
|---|---|---|
| PlayStation (57) | SYSTEM.CNF boot line | `.iso` `.bin` `.img` `.cso` `.pbp` |
| PlayStation 2 (58) | SYSTEM.CNF boot2 line | `.iso` `.bin` `.img` `.cso` `.pbp` |
| PlayStation 3 (59) | PARAM.SFO `TITLE_ID` | `.iso` `.bin` `.img` |
| GameCube (13) | header 6-byte game ID | `.iso` `.gcm` `.bin` |
| Sega Saturn (22) | IP.BIN `SEGA SEGASATURN` → +0x20 | `.bin` `.cue` `.iso` |
| Sega Mega-CD (20) | IP.BIN `SEGA SEGAMEGADRIVE` → +0x20 | `.bin` `.cue` `.iso` |
| Sega Dreamcast (23) | IP.BIN `SEGA SEGAKATANA` → +0x40 | `.bin` `.cue` `.iso` `.cdi` `.gdi` |
| Game Boy (9) | header `0x0134` title | raw `.gb`/`.gbc` + `.zip` (inner entry) |
| Game Boy Color (10) | header `0x0134` title | raw `.gb`/`.gbc` + `.zip` |
| Game Boy Advance (12) | header `0xAC` game code | raw `.gba` + `.zip` |
| Nintendo 64 (14) | header `0x3C` cart ID | raw `.z64`/`.n64`/`.v64` + `.zip` |
| Genesis / Mega Drive (1) | header `0x180` domestic serial | raw `.bin`/`.md`/`.gen`/`.smd` + `.zip` |
| SNES / SFC (4) | LoROM `0x7FC0` / HiROM `0xFFC0` internal title | raw `.smc`/`.sfc` + `.zip` |
| Nintendo DS (15) | header `0x0C` game code (4 bytes) | raw `.nds` + `.zip` (inner entry) |
| Nintendo 3DS (17) | NCSD header product code (e.g. `CTR-P-A3RP`) | `.3ds` `.cci` |
| Nintendo Wii (16) | disc-header game ID → 4-byte code (`.iso` @0x00 / `.wbfs` @0x200) | `.iso` `.gcm` `.wbfs` |

> Cartridge platforms also read the header from **inside `.zip`** (inner ROM entry); the Sega disc platforms do not unwrap `.zip`.

### ⛔ Blacklist — 5 systems (local matching blocked)

| System (id) | Reason |
|---|---|
| PC-FX (72) | CD-ROM with no standard filesystem — serial cannot be extracted reliably |
| PC Engine CD-ROM (114) | disc-based, filename regex unreliable |
| Neo Geo CD (70) | disc-based, filename regex unreliable |
| 3DO Interactive Multiplayer (29) | disc-based, filename regex unreliable |
| Sony PSP (61) | UMD disc, filename regex unreliable (internal-file extraction still pending) |

> **Why block them?** A whole-file CRC32 on a disc image costs 30–60s per file over NAS, and a hacked / translated ROM's CRC never matches a standard dump anyway — its metadata stays empty, so ScreenScraper is still required. Blocking these avoids pointless full-file hashing.

### 🔁 Default — every other system

Any cartridge / arcade system not listed above falls back to **whole-file CRC32** against the imported `rom_db`.

> **Not yet supported:** `.chd` disc images (needs a Java CHD hunk reader — on the roadmap). The `rom_db` data itself is **not** bundled in any package: download libretro-database / ClrMamePro data and import it from system settings.

---

## English

### Features
- Game list management (CRUD) with search and filtering
- Import from multiple frontend templates (Pegasus, RetroBat, ES-DE, EmuELEC, Lakka, Skraper, and more)
- Export to multiple frontend templates with v3 expression engine
- ScreenScraper integration for automated game metadata and media scraping
- Media file management (images, videos, manuals)
- Platform merge and data migration
- Background task management with progress tracking
- Multi-language support (Chinese, English, Japanese)

### Tech Stack
- Java 17 + Spring Boot 3.2.x
- H2 Database (embedded)
- MyBatis + Flyway
- HTML5 + JavaScript (no frontend framework)
- Docker support

### Deployment

#### Option 1: Docker (Recommended)
```bash
docker pull fansmall/frontendkiller:latest

mkdir -p ./data ./roms

docker run -d \
  --name frontend-killer \
  -p 8080:8080 \
  -v ./data:/data \
  -v ./roms:/data/roms \
  -e SPRING_PROFILES_ACTIVE=default \
  -e SERVER_TOMCAT_BASEDIR=/data \
  -e SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input \
  -e JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC" \
  -e PUID=0 -e PGID=0 -e TZ=Asia/Shanghai \
  --restart unless-stopped \
  fansmall/frontendkiller:latest
```

#### Option 2: Docker Compose
```yaml
services:
  frontend-killer:
    image: fansmall/frontendkiller:latest
    container_name: frontend-killer
    ports:
      - "8080:8080"
    volumes:
      - ./data:/data
      - ./roms:/data/roms
    environment:
      - SPRING_PROFILES_ACTIVE=default
      - SERVER_TOMCAT_BASEDIR=/data
      - SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input
      - JAVA_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC
      - PUID=0
      - PGID=0
      - TZ=Asia/Shanghai
    restart: unless-stopped
```
Run: `docker compose up -d`

#### Option 3: JAR File
Requires Java 17+.
```bash
java -jar webGamelistOper-1.3.1.jar
```

#### Option 4: Windows EXE (No Java Required)
Download the EXE package from GitHub Releases, extract and run `Frontend-Killer.exe`.

#### NAS App Stores (unRAID & fnOS) — now live
Frontend-Killer is now **published to both the unRAID Community Applications and the fnOS (飞牛) app store** — just search for **Frontend-Killer** in either store and install to get the latest version, no manual setup required.

A manual unRAID template also ships at `unraid/frontend-killer.xml`: install it from Community Applications, or via Docker → Add Container with image `fansmall/frontendkiller:latest`, port `8080`, volumes `<appdata>:/data` and `<roms>:/data/roms`, and set `PUID=99` / `PGID=100` so files land with correct ownership on unRAID shares.

### Access
http://localhost:8080

---

## 中文

### 功能特性
- 游戏列表管理（增删改查），支持搜索和过滤
- 支持多种前端模板导入（Pegasus、RetroBat、ES-DE、EmuELEC、Lakka、Skraper 等）
- 支持多种前端模板导出，v3 表达式引擎支持
- ScreenScraper 集成，自动刮削游戏元数据和媒体文件
- 媒体文件管理（图片、视频、手册）
- 平台合并与数据迁移
- 后台任务管理与进度追踪
- 多语言支持（中文、英文、日文）

### 技术栈
- Java 17 + Spring Boot 3.2.x
- H2 数据库（嵌入式）
- MyBatis + Flyway
- HTML5 + JavaScript（无前端框架）
- Docker 支持

### 部署方式

#### 方式一：Docker 部署（推荐）
```bash
docker pull fansmall/frontendkiller:latest

mkdir -p ./data ./roms

docker run -d \
  --name frontend-killer \
  -p 8080:8080 \
  -v ./data:/data \
  -v ./roms:/data/roms \
  -e SPRING_PROFILES_ACTIVE=default \
  -e SERVER_TOMCAT_BASEDIR=/data \
  -e SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input \
  -e JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC" \
  -e PUID=0 -e PGID=0 -e TZ=Asia/Shanghai \
  --restart unless-stopped \
  fansmall/frontendkiller:latest
```

#### 方式二：Docker Compose 部署
```yaml
services:
  frontend-killer:
    image: fansmall/frontendkiller:latest
    container_name: frontend-killer
    ports:
      - "8080:8080"
    volumes:
      - ./data:/data
      - ./roms:/data/roms
    environment:
      - SPRING_PROFILES_ACTIVE=default
      - SERVER_TOMCAT_BASEDIR=/data
      - SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input
      - JAVA_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC
      - PUID=0
      - PGID=0
      - TZ=Asia/Shanghai
    restart: unless-stopped
```
运行：`docker compose up -d`

#### 方式三：JAR 包运行
需要 Java 17+。
```bash
java -jar webGamelistOper-1.3.1.jar
```

#### 方式四：Windows EXE（无需 Java）
从 GitHub Releases 下载 EXE 安装包，解压后运行 `Frontend-Killer.exe`。

#### NAS 应用商店（unRAID 与 fnOS）—— 已上线
Frontend-Killer 现已**同时上架 unRAID 应用商店（Community Applications）与飞牛 fnOS 应用中心**——在任一商店中搜索 **Frontend-Killer** 即可安装使用最新版，无需手动配置。

项目也自带 unRAID 模板 `unraid/frontend-killer.xml`，可通过 Community Applications 安装，或手动：Docker → Add Container，镜像 `fansmall/frontendkiller:latest`，端口 `8080`，卷 `<appdata>:/data` 与 `<roms>:/data/roms`，并设置 `PUID=99` / `PGID=100` 以保证 unRAID 共享目录的文件归属正确。

### 访问地址
http://localhost:8080

---

## 日本語

### 機能特徴
- ゲームリスト管理（追加、削除、更新、検索）、フィルタリング対応
- 複数のフロントエンドテンプレートからのインポート（Pegasus、RetroBat、ES-DE、EmuELEC、Lakka、Skraper など）
- 複数のフロントエンドテンプレートへのエクスポート、v3 式エンジン対応
- ScreenScraper 統合、ゲームメタデータとメディアファイルの自動スクレイピング
- メディアファイル管理（画像、動画、マニュアル）
- プラットフォーム統合とデータ移行
- バックグラウンドタスク管理と進捗追跡
- 多言語サポート（中国語、英語、日本語）

### 技術スタック
- Java 17 + Spring Boot 3.2.x
- H2 データベース（組み込み）
- MyBatis + Flyway
- HTML5 + JavaScript（フロントエンドフレームワークなし）
- Docker サポート

### デプロイ方法

#### オプション1：Docker（推奨）
```bash
docker pull fansmall/frontendkiller:latest

mkdir -p ./data ./roms

docker run -d \
  --name frontend-killer \
  -p 8080:8080 \
  -v ./data:/data \
  -v ./roms:/data/roms \
  -e SPRING_PROFILES_ACTIVE=default \
  -e SERVER_TOMCAT_BASEDIR=/data \
  -e SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input \
  -e JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC" \
  -e PUID=0 -e PGID=0 -e TZ=Asia/Shanghai \
  --restart unless-stopped \
  fansmall/frontendkiller:latest
```

#### オプション2：Docker Compose
```yaml
services:
  frontend-killer:
    image: fansmall/frontendkiller:latest
    container_name: frontend-killer
    ports:
      - "8080:8080"
    volumes:
      - ./data:/data
      - ./roms:/data/roms
    environment:
      - SPRING_PROFILES_ACTIVE=default
      - SERVER_TOMCAT_BASEDIR=/data
      - SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input
      - JAVA_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC
      - PUID=0
      - PGID=0
      - TZ=Asia/Shanghai
    restart: unless-stopped
```
実行：`docker compose up -d`

#### オプション3：JARファイル
Java 17+ が必要です。
```bash
java -jar webGamelistOper-1.3.1.jar
```

#### オプション4：Windows EXE（Java不要）
GitHub Releases から EXE パッケージをダウンロードし、解凍して `Frontend-Killer.exe` を実行してください。

#### NAS アプリストア（unRAID と fnOS）—— リリース済み
Frontend-Killer は現在 **unRAID の Community Applications と fnOS（飞牛）アプリストアの両方に公開**されています。いずれかのストアで **Frontend-Killer** を検索してインストールするだけで最新バージョンを利用でき、手動設定は不要です。

unRAID 用テンプレート `unraid/frontend-killer.xml` も同梱しています。Community Applications から導入するか、手動で Docker → Add Container にイメージ `fansmall/frontendkiller:latest`、ポート `8080`、ボリューム `<appdata>:/data` と `<roms>:/data/roms` を設定し、`PUID=99` / `PGID=100` で unRAID 共有のファイル所有権を合わせてください。

### アクセスアドレス
http://localhost:8080

---

## Roadmap

### Completed
- [x] Import/export templates: Pegasus, RetroBat, ES-DE, EmuELEC, Lakka, Skraper
- [x] ScreenScraper API integration for game scraping
- [x] v3 template system with expression engine
- [x] Native Windows EXE distribution (jpackage)
- [x] Multi-language support (Chinese, English, Japanese)
- [x] File existence verification
- [x] Background task recovery
- [x] Published to unRAID Community Applications & fnOS app stores

### Planned
- [ ] Folder export configuration with configurable parent directory levels
- [ ] Advanced game filtering options
- [ ] Statistics dashboard for game collection analytics
- [ ] Additional frontend templates: EmuDeck, Recalbox, Batocera

---

## ✅ New in 1.3

- **🔍 Local ROM Matching (rom_db)** - Import libretro-database (DAT/RDB) and ClrMamePro data to identify ROMs offline before ever hitting ScreenScraper, with incremental merge import and a unified multi-tier matching pipeline
- **⚡ Fast Local Matching** - Whitelisted systems with a reliable in-ROM identifier take the millisecond-level serial / cartridge-header path; everything else falls back to whole-file CRC32, and blacklist systems are blocked to avoid pointless hashing
- **💿 Disc-Based Platform Scraping Overhaul** - Stable identifiers (SYSTEM.CNF, PARAM.SFO, IP.BIN, cartridge headers) instead of fragile whole-file CRC32 for PS1/PS2/PS3, GameCube, Saturn/Mega-CD/Dreamcast, GB/GBC/GBA/N64, NDS/3DS/Wii and more
- **🎯 Fuzzy Match + Manual Confirmation** - Near-miss titles get a candidate list the user confirms before write-back, so nothing is force-matched
- **🐛 Bug Fixes from 1.2** - Including the SsNameNormalizer hang and rich-text i18n flattening that wiped the libretro attribution

## 🔮 Coming in 1.4–1.5

- **🧭 Full-Workflow Onboarding Guide** - A step-by-step walkthrough of the import → match → scrape → media → export pipeline for first-time users
- **❓ Button / Feature Help** - Inline explanations for every action button and dialog, making the feature set self-documenting
- **📦 Possible: Archive Extraction & Format Conversion** - Decompress packed ROMs and convert between image formats (e.g. ISO ↔ CHD, GDI/BIN ↔ CHD); scope still under evaluation

---

## Development Story

This story begins in 2023. It started as a small script just to quickly convert game collections between Pegasus and ES-DE; but conversion time and automation issues kept driving me crazy, so I turned it into a Java web project deployable on a NAS — a side benefit being a chance to practice Struts. That was the prototype of the software. All that remains today is the idea; the code has been rewritten from scratch many times over.

In 2024, AI became popular. With AI's help I built the second major version, and the software was officially named **webGamelistOper** — the original idea was modest: simply edit and convert game lists across platforms. Features at the time were limited to importing lists for editing and M3U merging.

In 2025, AI-assisted programming went mainstream. After evaluating the options, I used Trae to migrate the codebase entirely to Spring Boot, and the software slowly evolved into a retro game management platform: this period added extensive platform and game editing features, brought ScreenScraper API scraping onto the agenda, and introduced multilingual support with AI translation.

From late 2025 to early 2026, to give users full control over their own import/export needs, we modularized both the import and export pipelines and added customizable templates: we built our own template system supporting parameters, operations, and constants, iterating it all the way to v3 — every field mapping, expression, and output rule now lives in JSON templates while Java only executes them, covering data import/export needs across frontends, devices, and user setups such as Pegasus, ES-DE, RetroBat, EmuELEC, Lakka, and Skraper.

That same year, I kept polishing the ScreenScraper matching, but was never quite satisfied with its speed and hit rate — not a shortcoming of ScreenScraper, but of reality: ROMs are far too poorly standardized. So I added local matching, letting users import libretro-database and ClrMamePro data, and used every trick I could to raise the hit rate. Meanwhile, I switched my development tool to Qoder — no particular reason, it simply gave me the coolest UI proposals. The software therefore went through its second great migration and was renamed **Frontend-Killer**.

I have always strived to improve readability and guidance, but the features have grown truly convoluted; version 1.4 plans a full re-evaluation and simplification of this logic.

### Project Scale & Tech Stack

After three rewrites and migrations, as of version 1.2 the codebase looks roughly like this:

| Category | Files | Lines of Code (approx.) |
|---|---|---|
| Java backend sources | 165 | 41,000 |
| HTML / Thymeleaf pages | 20 | 16,700 |
| CSS stylesheets | 18 | 7,700 |
| SQL (schema / migrations / seed data) | 25 | 1,400 |
| Standalone JS scripts | 4 | 600 |
| v3 import/export templates & rules (JSON) | 40+ | — |

Core technologies and tools:

- **Backend**: Java 17, Spring Boot 3.2.x, MyBatis, Flyway, embedded H2 database
- **Frontend**: Thymeleaf server-side rendering + vanilla HTML/CSS/JavaScript (no frontend framework)
- **Integrations**: OkHttp (ScreenScraper API), commons-compress / xz (7z, LZMA media extraction), SSE real-time notifications
- **Build & Distribution**: Maven; five release forms — Docker image, executable JAR, jpackage native Windows EXE, fnOS fpk, and unRAID template
- **Development Tools**: Trae (2025 Spring Boot migration) → Qoder (2026 Frontend-Killer rewrite)

### Acknowledgments

Thanks for tolerating this clumsy, convoluted little piece of software. Thanks to ScreenScraper, still dedicated to collecting retro game information; to the libretro team, still curating and updating their database; to the frontend authors, building ever more user-friendly interfaces; and to the players who still love retro games.

Without your effort and support, this idea would never have existed, let alone come this far. One last thing — when am I ever going to find time to organize my own game collection?

---

## 开发故事

这个故事要从 2023 年讲起。最初只是想写个小脚本，快速转换 Pegasus 和 ES-DE 平台的游戏集合；但转换的耗时与自动化问题实在让人抓狂，干脆把它改成了一个可部署在 NAS 上的 Java Web 项目——顺便还能练练 Struts。这便是软件的雏形。虽然如今只剩下理念，代码早已全部重写。

2024 年，AI 开始流行。我借助 AI 建立了第二个大版本，软件正式命名为 **webGamelistOper**——最初的想法很朴素，只是编辑并转换各平台的游戏列表。当时的功能也仅限于导入列表进行编辑、合盘等。

2025 年，AI 编程逐渐普及。我评估后借助 Trae 将代码彻底迁移到 Spring Boot 架构，软件也慢慢演变成一个复古游戏管理平台：这一时期加入了大量平台与游戏的修改功能，ScreenScraper 接口刮削被正式列入议程，还新增了多语种支持与 AI 翻译。

2025 年末到 2026 年上半年，为了解决用户自定义导入导出的需求，我们把导入和导出功能全部模块化，加入了模板自定义功能：自建了一套支持参数、操作、常量的可定制模板系统，并一路迭代到 v3 版本——所有字段映射、表达式和落盘规则都写在 JSON 模板里，Java 只负责执行，如今已能覆盖 Pegasus、ES-DE、RetroBat、EmuELEC、Lakka、Skraper 等各种前端与设备的数据导入导出需求。

也是在这一年，我一方面持续打磨 ScreenScraper 接口的匹配，但对速度和命中率始终不太满意——这不是 ScreenScraper 的问题，而是 ROM 标准化程度过低等现实所致的——于是增加了本地匹配功能，允许用户导入 libretro-database 以及 ClrMamePro 数据，并用各种手段提升匹配命中率。另一方面，我把开发工具换成了 Qoder：没什么特别针对性的考量，只是它给出的 UI 方案最酷。软件也因此进行了第二次大迁移，名字改成了 **Frontend-Killer**。

我一直致力于增加软件的可读性和引导，但功能实在太过繁杂，计划在 1.4 版对这一状况进行全面评估并简化逻辑。

### 项目规模与技术栈

历经三次重写与迁移，截至 1.2 版本，代码规模大致如下：

| 类别 | 文件数 | 代码行数（约） |
|---|---|---|
| Java 后端源码 | 165 | 41,000 |
| HTML / Thymeleaf 页面 | 20 | 16,700 |
| CSS 样式 | 18 | 7,700 |
| SQL（Schema / 迁移 / 种子数据） | 25 | 1,400 |
| 独立 JS 脚本 | 4 | 600 |
| v3 导入导出模板与规则（JSON） | 40+ | — |

核心技术与工具：

- **后端**：Java 17、Spring Boot 3.2.x、MyBatis、Flyway、H2 嵌入式数据库
- **前端**：Thymeleaf 服务端渲染 + 原生 HTML/CSS/JavaScript（无前端框架）
- **集成**：OkHttp（ScreenScraper API）、commons-compress / xz（7z、LZMA 媒体解压）、SSE 实时通知
- **构建与分发**：Maven；Docker 镜像、可执行 JAR、jpackage 原生 Windows EXE、飞牛 fnOS fpk、unRAID 模板五种形态
- **开发工具**：Trae（2025 Spring Boot 迁移期）→ Qoder（2026 Frontend-Killer 重构期）

### 致谢

感谢大家能容忍我这个蹩脚又繁杂的小软件。感谢仍然致力于复古游戏信息收集的 ScreenScraper，依然在做数据整合更新的 libretro 小组，致力于做出各种方便用户的前端的作者们，以及依然对复古游戏怀有热爱的玩家们。

没有你们的付出与支持，我们不会有这个想法，更走不到今天这一步。最后我就想问一句，我自己的游戏啥时候能整理一下啊。


---

## 開発ストーリー

この物語は2023年に始まりました。最初はPegasusとES-DEのゲームコレクションを素早く変換する小さなスクリプトを書くつもりでしたが、変換時間と自動化の問題に耐えかねて、NASにデプロイできるJava Webプロジェクトに作り替えました——ついでにStrutsの練習にもなりました。これがソフトウェアの雛形です。今日残っているのは理念だけで、コードはすでに完全に書き直されています。

2024年、AIが流行し始めました。AIの助けを借りて第二のメジャーバージョンを構築し、ソフトウェアは正式に**webGamelistOper**と名付けられました——当初のアイデアはシンプルで、各プラットフォームのゲームリストを編集・変換することだけでした。機能もリストのインポート編集やM3Uマージ程度のものでした。

2025年、AIプログラミングが普及し始めました。検討の末、Traeを使ってコードをSpring Bootアーキテクチャへ全面的に移行し、ソフトウェアは徐々にレトロゲーム管理プラットフォームへと進化していきました：この時期にプラットフォームとゲームの編集機能が大幅に追加され、ScreenScraper APIによるスクレイピングが正式に計画に組み込まれ、多言語対応とAI翻訳も追加されました。

2025年末から2026年前半にかけて、ユーザー自身のインポート・エクスポート需求に応えるため、インポートとエクスポートの両機能をモジュール化し、テンプレートのカスタマイズ機能を追加しました：パラメータ・演算・定数をサポートする独自のカスタマイズ可能なテンプレートシステムを構築し、v3まで進化させました——すべてのフィールドマッピング、式、出力ルールはJSONテンプレートに記述し、Javaは実行のみを担当します。現在ではPegasus、ES-DE、RetroBat、EmuELEC、Lakka、Skraperなど、さまざまなフロントエンド・デバイス・ユーザー環境のデータインポート・エクスポートをカバーしています。

同年、ScreenScraperのマッチングを継続して磨いていましたが、速度と命中率にはいま一つ納得できませんでした——これはScreenScraperの問題ではなく、ROMの標準化度が低すぎるという現実に起因するものです——そこでローカルマッチング機能を追加し、libretro-databaseやClrMameProのデータをインポートできるようにし、さまざまな手段で命中率を高めました。一方で、開発ツールをQoderに切り替えました：特に戦略的な理由はなく、提示されたUI案が一番かっこよかったからです。ソフトウェアはこれにより二度目の大移行を行い、名前は**Frontend-Killer**に変更されました。

可読性とガイドの向上にずっと努力してきましたが、機能はあまりに複雑になりすぎており、1.4版でこの状況を全面的に見直し、ロジックを簡素化する計画です。

### プロジェクト規模と技術スタック

三度の書き直しと移行を経て、1.2時点のコード規模はおよそ下の表のとおりです：

| 種別 | ファイル数 | コード行数（約） |
|---|---|---|
| Java バックエンドソース | 165 | 41,000 |
| HTML / Thymeleaf ページ | 20 | 16,700 |
| CSS スタイル | 18 | 7,700 |
| SQL（スキーマ / マイグレーション / シードデータ） | 25 | 1,400 |
| 単独JSスクリプト | 4 | 600 |
| v3 インポート・エクスポートテンプレートとルール（JSON） | 40+ | — |

主要な技術とツール：

- **バックエンド**：Java 17、Spring Boot 3.2.x、MyBatis、Flyway、H2組み込みデータベース
- **フロントエンド**：Thymeleaf サーバーサイドレンダリング + ネイティブ HTML/CSS/JavaScript（フロントエンドフレームワークなし）
- **統合**：OkHttp（ScreenScraper API）、commons-compress / xz（7z、LZMAメディア展開）、SSEリアルタイム通知
- **ビルドと配布**：Maven。Dockerイメージ、実行可能JAR、jpackageネイティブWindows EXE、fnOS fpk、unRAIDテンプレートの5形態
- **開発ツール**：Trae（2025年 Spring Boot移行期）→ Qoder（2026年 Frontend-Killer再構築期）

### 謝辞

この不格好で入り組んだ小さなソフトウェアを容忍していただき、ありがとうございます。レトロゲーム情報の収集に今も取り組むScreenScraper、データの統合更新を続けるlibretroチーム、使いやすいフロントエンドを作り続ける作者たち、そしてレトロゲームを愛し続けるプレイヤーのみなさんに感謝します。

みなさんの尽力と支援がなければ、このアイデアは生まれず、今日までの道もありませんでした。最後にひとことだけ——自分のゲームコレクションはいつ整理できるのだろう？

---

**Version**: 1.3.1
