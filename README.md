<p align="center">
  <img src="unraid/icon.png" alt="Frontend-Killer" width="160">
</p>

<h1 align="center">Frontend-Killer</h1>

<p align="center">A web-based game list management tool for emulator frontend metadata. Import, manage, export, and scrape game metadata across multiple frontend formats.</p>

## What's New in 1.2

- **Theme Package System** - Customizable UI themes with `data/themes/{themeId}/theme.json` configuration; supports colors (dark/light mode), custom fonts, and per-theme sound effects; theme grid UI in system settings with live preview cards
- **Scraper Stability Fix** - Fixed scraping hang/deadlock caused by `ThreadResourceManager` counter corruption; media downloads now use an independent `Semaphore` decoupled from the shared game-info thread pool
- **Media Download Performance** - Media download throughput improved with 64KB buffer (was 8KB) and independent concurrency control (3 parallel downloads) that no longer competes with game info scraping threads
- **Template Variables (v3 `variables` block)** - Optional global variables declared in templates, set by the user before import/export; shared across `{var}` placeholders and expressions
- **Searchable Multi-Select Filters** - Developer, publisher and genre (two-level with sub-genres) filters in the game list now use searchable multi-select dropdowns; genre filtering now works correctly for multi-genre games and scrape-status filtering offers four states
- **Notification Center** - Real-time task notifications via SSE push, with unread badge and auto-created alerts on task completion/failure
- **Scraper Media Path Restructure** - New storage layout keyed by ScreenScraper system/game ID (`data/scraper/games/{ssSystemId}/{ssGameId}/`), with local-file reuse to skip already-downloaded media
- **Batch Aggregation Endpoints** - Single GROUP BY queries replace N+1 COUNT/statistics calls for media-download summaries and platform stats
- **Template Media-Type Preset** - Scrape modals can preset media types directly from an export template
- **Thymeleaf Migration & UI Polish** - Unified server-side layout, icon-based action buttons, and row-click editing across pages
- **Genre Dialect Mapping** - Term mapping system (`term_mapping` table) with 261 seed entries normalizes genre synonyms across languages and frontends (e.g. "Beat'em Up" / "act" → unified target); `map()` and `mapFirst()` expression functions available in v3 export templates for genre subdirectory generation; Racing/Driving dialect mapping added
- **Export & Filter Fixes** - `poor_quality` scrape-status filter redefined to check metadata completeness (desc/developer/publisher/genre/releasedate) instead of media presence
- **Performance & Stability Fixes** - Fixed import performance, SSE zombie connections, missing xz dependency, and H2 summary bugs
- **Internationalization** - Continued Chinese / English / Japanese coverage across pages

> Upgrading from 1.1-RC1? The scraper media directory structure changed, so previously scraped media files may not be reused directly and may require re-scraping.

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
java -jar webGamelistOper-1.2.jar
```

#### Option 4: Windows EXE (No Java Required)
Download the EXE package from GitHub Releases, extract and run `Frontend-Killer.exe`.

#### unRAID / NAS (Community Applications)
Frontend-Killer ships an unRAID template at `unraid/frontend-killer.xml`. Install it from Community Applications, or manually via Docker → Add Container with image `fansmall/frontendkiller:latest`, port `8080`, volumes `<appdata>:/data` and `<roms>:/data/roms`, and set `PUID=99` / `PGID=100` so files land with correct ownership on unRAID shares.

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
java -jar webGamelistOper-1.2.jar
```

#### 方式四：Windows EXE（无需 Java）
从 GitHub Releases 下载 EXE 安装包，解压后运行 `Frontend-Killer.exe`。

#### unRAID / NAS（Community Applications）
项目自带 unRAID 模板 `unraid/frontend-killer.xml`。可通过 Community Applications 安装，或手动：Docker → Add Container，镜像 `fansmall/frontendkiller:latest`，端口 `8080`，卷 `<appdata>:/data` 与 `<roms>:/data/roms`，并设置 `PUID=99` / `PGID=100` 以保证 unRAID 共享目录的文件归属正确。

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
java -jar webGamelistOper-1.2.jar
```

#### オプション4：Windows EXE（Java不要）
GitHub Releases から EXE パッケージをダウンロードし、解凍して `Frontend-Killer.exe` を実行してください。

#### unRAID / NAS（Community Applications）
unRAID 用テンプレート `unraid/frontend-killer.xml` を同梱しています。Community Applications から導入するか、手動で Docker → Add Container にイメージ `fansmall/frontendkiller:latest`、ポート `8080`、ボリューム `<appdata>:/data` と `<roms>:/data/roms` を設定し、`PUID=99` / `PGID=100` で unRAID 共有のファイル所有権を合わせてください。

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

### Planned
- [ ] Multi-file game support (e.g., DOS games) - treat entire game folders as entries
- [ ] Folder export configuration with configurable parent directory levels
- [ ] Advanced game filtering options
- [ ] Statistics dashboard for game collection analytics
- [ ] Additional frontend templates: EmuDeck, Recalbox, Batocera

---

**Version**: 1.2
