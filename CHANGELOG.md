# Changelog

## [Unreleased]

## [1.3.8] - 2026-10-26

> 版本机制重构 + ScreenScraper 刮削性能优化三项（统一连接池 / manifest 缓存全覆盖 / FAILED 任务 URL 复用）。

### Added
- **Column Operations tool**: batch copy/swap/overwrite/replace game column data with preview confirmation

### 版本机制重构
- **术语统一**："副本" 更名为 "版本"（UI 文案、日志、注释）；"裸 ROM" 更名为 "原始文件"（formats.json label、UI 显示）
- **运行时 version 属性**：游戏新增 `game.version`，从文件路径扩展名自动获取（如 `.nes` → `nes`、`.iso` → `iso`），不入库
- **导入自动识别**：导入时自动识别文件格式版本，无需手动指定
- `Game.java` 新增 `getVersion()` 瞬态方法；`formats.json` bare 格式 label 改为 "原始文件（解压后）"，id 保持 `bare` 不变
- 转换管线（bare/zip/7z/iso/cso/chd）逻辑完全不变；数据库表名和 Java 类名保持不变

### ScreenScraper 刮削优化
- **P0 — 统一 OkHttpClient 连接池**：新建 `OkHttpClientConfig.java` 共享 Bean（ConnectionPool 20/5min），`ScreenScraperApiServiceImpl`、`ScraperServiceImpl`、`GameManifestService`、`MediaDownloadServiceImpl` 全部改为注入共享客户端；媒体下载从 `HttpURLConnection` 迁移到 OkHttp，复用 TCP 连接节省 TLS 握手开销
- **P1 — manifest 缓存全覆盖**：`storeManifest()` 触发条件从"手动刮削+街机"简化为"所有刮削成功的游戏"（`ssGameId != null` 即缓存），后续刮削直接复用缓存 manifest，0 次 SS API 调用
- **P2 — FAILED 任务 URL 复用**：新增 `resetFailedTasksToPending()` 方法，重试时复用已存储的加密下载 URL，避免重新调用 jeuInfos 浪费配额

## [1.3.7] - 2026-10-25

> Patch release merging 1.3.1 fixes with critical media download architecture fixes, plus a brand-new **Toolbox** (translation / DAT generator / ROM variants). Headline: **media download tasks were written to the wrong database table, causing the UI to show 0 progress despite tasks completing in the background**.

### Fixed
- **Batch scrape under "Apply to all search results" produced no work** — the game-list page sends a nested `filters` object (the same contract every other batch endpoint already reads), but the scrape backend (`ScraperRequest` / `ScraperServiceImpl`) only inspected flat fields, so `hasFilters()` was always false and the batch branch resolved to an empty game list ("no games to scrape"). `ScraperRequest` now accepts the nested `filters` and flattens it (empty arrays/blanks normalized to null to avoid an empty `IN ()` returning 0 rows), and the batch branch now falls back to the **whole platform** when apply-to-all is selected with no filter conditions.
- **Media download tasks written to wrong database table** — `enqueueMediaTasksFromGameInfo()` created `ScrapeTask` objects inserted into `scrape_task` table, but the `platform-progress` API queried `media_download_task` table for media stats, so media completed/pending/failed counts always showed 0. Fixed to create `MediaDownloadTask` objects inserted via `mediaDownloadTaskMapper.insertBatch()` into `media_download_task` table.
- **ScrapeWorkerPool unable to dispatch media tasks from `media_download_task`** — The worker pool's listener thread only polled `scrape_task` table, so new media tasks in `media_download_task` were invisible and never dispatched. Added a second listener thread (`media-download-listener`) that picks PENDING tasks from `media_download_task` with optimistic locking (PENDING → DOWNLOADING), processes via `ScraperService.executeMediaDownloadTask(MediaDownloadTask)`, and updates status (COMPLETED/FAILED) in `media_download_task`. Both listeners share the same `workerPool` with automatic slot coordination.
- **SS quota (maxThreads) not synced to ScrapeStatus for UI display** — `getUserMaxThreads()` fetched the correct value from SS API but only stored it in `cachedMaxThreads` (ScraperServiceImpl field), not syncing to `ScrapeStatus`. Since `platform-progress` API reads `ScrapeStatus.getMaxThreads()`, the UI showed the hardcoded default (4) instead of the actual quota (6). Fixed by calling `scrapeStatus.updateQuotaFromSsuser(ssuserNode)` in `getUserMaxThreads()` after parsing the response.
- **Failed task modal trigger missing after media-download page rewrite** — The `showFailedTasks()` function existed in JS but no UI element called it after the Thymeleaf template rewrite. Made failed count numbers clickable links (`<a>` tags with `onclick="showFailedTasks('GAME_INFO')"` / `showFailedTasks('MEDIA_DOWNLOAD')`) and added `.md-fail-link` CSS styling (dashed underline, hover glow effect).
- **Container never had a `/data/rom-db` or `/data/tools` folder** — the image deliberately skips `data/rom-db/` (467 MB of libretro data is not shipped), but neither the Dockerfile nor the entrypoint created the folders, so inside a container there was nowhere to put DAT/RDB files and the import only logged “directory not found”. Both now create `/data/rom-db/metadat/generated`, `/data/rom-db/rdb` and `/data/tools`, `chown` them together with the other managed folders when `PUID`/`PGID` are set, and log one hint with the exact target paths while rom-db is still empty

### Added
- **Toolbox (工具箱, `/tools`)** — one page for offline and general-purpose utilities, holding three self-contained tools (translation, DAT generator, ROM variants) followed by shortcuts to the scraper-system and database pages. The translation and DAT panels are localized in zh-CN / en-US / ja-JP; the ROM-variant panel is currently Chinese-only
- **Translation Tool** — batch-translate `game.name`, `game.desc` and `rom_db.game_name` into any of 10 target languages (en/ja/zh/fr/es/de/it/ko/pt/ru; the source side also accepts auto-detect)
  - Scope is a single platform, a set of platforms (`rom_db`, picked with a multi-select) or the whole table; results are written to the dedicated `field_translation` table (`V1.0.29__field_translation.sql`) so originals are never overwritten, and “overwrite existing” decides whether already-translated rows are retried
  - Identical texts are translated once; live progress bar, per-item log and a stop button; the run happens on its own thread and keeps the last task id for polling
  - **Engine management** — add an OpenAI-compatible engine with Base URL + Model + API Key (optionally system prompt and temperature), then edit, delete, set as default or fire a one-sentence connectivity probe. An advanced mode accepts a raw provider JSON with `${var}` placeholders; secrets stay in config variables rather than inside the service JSON
- **DAT Generator** — export `rom_db` or the current game list as a standard ClrMamePro text DAT
  - Two sources: `rom_db` (grouped by platform name) and the game list (grouped by platform id). A per-platform pre-check lists total rows, exportable rows (name non-blank **and** CRC-or-serial present) and whether the ScreenScraper `systemId` resolves, so a platform that cannot be written back is visible before starting
  - Writes `{platform}.dat` into `data/rom-db/metadat/generated/` — the directory the DAT import already scans recursively — so the output can be fed straight back into `rom_db`; “import immediately after generating” does both in one run
  - Runs as a background job with progress / log / stop; generation and import are mutually exclusive so a half-written DAT is never imported
- **ROM Variants (ROM 副本)** — per-system copies of a game file in a chosen format, generated once and reused by export
  - **Capability matrix** — only qualifying systems are listed (console / handheld types; disc, optical and other ROM types and extensions excluded). Every system × format can be switched on or off and one export preference picked; state lives in `system_variant_capability` / `system_variant_preference`. The matrix is derived from `scraper_system` attributes at startup (and refreshed lazily by the API), so it is populated without manual entry
  - **Batch generation** — choose a platform, the formats and optionally force rebuild; runs as a background job with progress, per-item log and stop. Records live in `game_variant` (`V1.0.30__game_variant.sql`), files under `data/variants/` (override with `VARIANT_PATH`)
  - **Format survey** — read-only, per platform: classifies every game by extension (no file headers are read) into already present / can be added / cannot generate / needs repair, with per-format totals. Nothing is generated or written
  - **Pipeline overview** — the global module catalog (AVAILABLE / RESERVED) plus each system's declared routes and the chain the planner actually computes, so an unreachable target is visible before anyone clicks generate
- **Conversion pipeline (`com.gamelist.variant`)** — a planner returns the cheapest chain (fewest steps first, then total cost) and never routes through an unimplemented module
  - Pure-Java modules: `ecm2bin` (ECM decoder for record types 0–3 — literal, MODE1, MODE2 Form1/Form2 — regenerating each sector's EDC/ECC) and `cso2iso` / `iso2cso` (CISO v1 / zlib)
  - `chdman` modules: `bin2chd`, `iso2chd`, `multitrack2chd`, `gdi2chd`, `nrg2chd`, `toc2chd`; cue+bin, GDI, NRG and TOC all keep their track tables, so the CHD is a real multi-track disc rather than an empty shell
  - `ffmpeg` restores APE / FLAC CD audio to canonical 44.1 kHz / 16-bit WAV before `chdman` runs. ECM payloads are unpacked to a full 2352-sector image in a staging directory first, so the analysis card reports `payload=…` and warns that a whole-disc worth of free temp space is required
  - Still RESERVED (declared but not implemented, skipped by the planner): `bin2iso`, `iso2zso`, `iso2pbp`, `cso2bin`. `ECM → BIN` and `ECM → CHD` are READY; `ECM → ISO / CSO` reports INCOMPLETE because they both need `bin2iso`
  - `chdman` and `ffmpeg` are resolved in the same order: the path saved in the toolbox, then `gamelist.chdman.path` / `gamelist.ffmpeg.path` (i.e. `CHDMAN_PATH` / `GAMELIST_FFMPEG_PATH`), then a `CHDMAN` / `FFMPEG` environment variable, `PATH`, and `tools/`, `data/tools/` + `bin/` beside the program. The Docker image ships both `mame-tools` and `ffmpeg`, so neither needs installing in a container; JAR / EXE builds need them dropped into `data/tools/`, pointed at from the toolbox, or found on `PATH`
- **JSON-driven format and route tables** — `rules/variants/formats.json` (bare / ZIP / 7z generic; ISO and CSO scoped to PlayStation 57 and PSP 61; CHD scoped to 57/20/22/23/29/70/72/114/117), `capability-rules.json`, `system-pipelines.json` and `pipeline-modules.json`. `/api/variants/formats` feeds labels and column order to the UI, so adding or rescoping a format only touches JSON
- **Export integration** — the export page gained a per-system “ROM variant format” selector (`/api/variants/options`). `VariantResolver` resolves request → system preference → original, and falls back to the main file when a copy is missing, failed or stale (source size/mtime changed), recording the reason instead of silently shipping an outdated copy
- **Game-list variant indicator** — a fourth status icon (stacked layers): grey = none, blue = ready, yellow = stale, red = failed, with counts in the tooltip. Clicking it opens a per-game dialog carrying a live diagnosis (DIRECT / READY / SAME / INCOMPLETE / UNSUPPORTED / BLOCKED plus the chain text), the variant list, and generate-missing / force-rebuild / delete-all actions
- **External tool paths in the UI** — the ROM-variant panel gained an “External tools” (外部工具) sub-tab: one card each for `chdman` and `ffmpeg` showing available / unavailable, the resolved path, where it came from (UI setting / `gamelist.*.path` / environment variable / `PATH` / program directory) and the reported version, with a path field saved to `system_settings` (`chdman.path`, `ffmpeg.path`, `GET`+`POST /api/variants/tools`). Saving re-probes both tools immediately, no restart. An empty field returns to auto-discovery; a typed path that does not resolve is reported as unavailable rather than silently falling back to another binary on `PATH`
- **ffmpeg bundled in the Docker image** — `Dockerfile` / `Dockerfile.cn` now install `ffmpeg` alongside `mame-tools`, so APE/FLAC track extraction works in a container without mounting a binary
- **Third-party notices and install guides cover the external tools** — `THIRD-PARTY-NOTICES.md` gained a full ffmpeg section in all three languages (LGPL-2.1-or-later for official builds, GPL-2.0-or-later otherwise, source links, license-text location, separate-process statement). `INSTALL.md`, `INSTALL_zh.md` and the fnOS wizard now document the rom-db folders, where to drop `chdman` / `ffmpeg` (working-directory `data/tools/`, or `./data/tools/` on the Docker host), which builds to grab, the full resolution order, and how extra ROM folders (roms2 / roms3) are mapped into the single ROM root
- **Full-system filename matching** — `rom_db` now stores the real ROM filename from the DAT (`rom_file`, plus a normalized, indexed `rom_file_norm`), captured for every system (not just arcade) during DAT import. `filename_exact` matching resolves against `rom_file_norm`; content-level hits (crc/serial) stay confirmed (🟢) while name-level hits are flagged suspect (🟡) so they never masquerade as verified matches.
- **Dual-listener architecture in ScrapeWorkerPool** — `scrape-listener` continues handling `scrape_task` (GAME_INFO + legacy MEDIA_DOWNLOAD), while `media-download-listener` exclusively handles `media_download_task`. Both share the same `ThreadPoolExecutor` with backpressure via `availableSlots` calculation.
- **MediaDownloadTaskMapper optimistic lock methods** — `pickNextPendingMediaTasks(limit)` and `tryClaimMediaTask(id)` for safe concurrent task claiming from `media_download_task` table.

### Changed
- **Translations moved into a unified `field_translation` table** — `game.translated_name` / `translated_desc` are gone (`V1.0.29__field_translation.sql`). On the first start after upgrading, `FieldTranslationMigrationInitializer` copies any existing `translated_name` / `translated_desc` values into `field_translation` as `lang=zh`, then drops both columns. The guard checks whether the legacy columns still exist, so later restarts are no-ops
  - Export output is unchanged: `ExportRequest.translationLang` now injects the transient `translatedName` / `translatedDesc` that `-translated` fields and `coalesce(...)` already consumed, sourced from `field_translation` for the chosen language (empty = no injection, templates fall back to the originals)
  - Chinese names and synopses obtained for free during scraping are written to the same table (`lang=zh`) instead of the removed columns; `temp_subset_game` keeps its own copy of the two columns and is untouched
- ⚠️ **Breaking for anything reading the dropped columns** — custom SQL, external scripts or hand-written reports that query `game.translated_name` / `translated_desc` directly must switch to `field_translation` (`src_table='game'`, `src_field='name'|'desc'`, `lang='zh'`). The drop is irreversible, so back up the database before upgrading

## [1.3.0] - 2026-10-17

> Headline theme: **Local ROM matching** — import libretro-database (DAT/RDB) and ClrMamePro data to identify ROMs offline, speeding up scraping and improving match accuracy before ever hitting the ScreenScraper API.

### Added
- **Fast Local Matching** — identify ROMs offline against the local `rom_db` before ever calling ScreenScraper. Whitelisted systems (those exposing a reliable in-ROM identifier) take the millisecond-level serial / cartridge-header path and return instantly; every other system falls back to whole-file CRC32. It serves two purposes: users who don't want to scrape can quickly match local game data (basic info only), and successfully identified games automatically carry the authoritative CRC/serial into scraping — higher hit rate, fewer wasted API calls.
- **rom_db local matching database** (`V1.0.22__rom_db.sql`, `V1.0.24__rom_db_metadata.sql`, `V1.0.25__rom_db_md5_sha1.sql`, `V1.0.26__romdb_multilang_name.sql`)
  - Single `rom_db` table unifying cartridge platforms (CRC32) and disc platforms (serial), with standard EN/JP/CN game names, region, developer, publisher, release year, genre, MD5/SHA1
  - Data sources: libretro-database No-Intro + Redump DAT + RDB, and ClrMamePro DAT (`ClrMameProDatParser`)
  - Two-phase in-memory merge import: recursive DAT scan (auto-detects redump/no-intro/serial sub-dirs) deduped by `systemId:CRC` with complementary field fill, then RDB supplement for richer metadata
  - **Incremental merge mode** — same CRC only fills empty fields, never overwrites existing data; safe to import multiple sources repeatedly
- **Manual data-import UI in system settings**
  - Copy `.dat` files to `data/rom-db/metadat/` and `.rdb` files to `data/rom-db/rdb/`, then trigger import with a live progress bar (percent/count/detail)
  - Separate DAT and RDB import actions plus a “clear match database” (清零匹配库) destructive reset with confirmation
- **Unified multi-tier matching pipeline** (`RomDbServiceImpl`)
  - L0 filename serial regex → L1 CRC32 (all platforms) → L2 disc serial extraction (whitelist systems) → L3 cartridge header ID → name fallback for hacked/modified ROMs
  - Disc identifier extraction via `DiscSerialExtractor`: SYSTEM.CNF (PS1/PS2), PARAM.SFO (PS3), IP.BIN (Saturn/MegaCD/Dreamcast), NGC 6-byte game ID
  - Cartridge header reader via `CartridgeHeaderReader`: GB/GBC/GBA/N64/etc. millisecond-level header ID lookup (far faster than full-file CRC32)
  - ZIP/7z handling computes the **inner ROM entry CRC** (No-Intro convention) rather than the container CRC
- **Special (non-CRC) identifier matching — supported systems & formats**
  Local matching extracts an in-ROM identifier (serial / header ID) instead of whole-file CRC32 for the systems below; all other systems fall back to CRC32 (and blacklist systems are blocked).

  | System (id) | Identifier source | Supported image formats |
  |---|---|---|
  | PlayStation (57) | SYSTEM.CNF boot line | `.iso` `.bin` `.img` `.cso` `.pbp` |
  | PlayStation 2 (58) | SYSTEM.CNF boot2 line | `.iso` `.bin` `.img` `.cso` `.pbp` |
  | PlayStation 3 (59) | PARAM.SFO TITLE_ID | `.iso` `.bin` `.img` |
  | GameCube (13) | header 6-byte game ID | `.iso` `.gcm` `.bin` |
  | Sega Saturn (22) | IP.BIN `SEGA SEGASATURN` → +0x20 | `.bin` `.cue` `.iso` |
  | Sega Mega-CD (20) | IP.BIN `SEGA SEGAMEGADRIVE` → +0x20 | `.bin` `.cue` `.iso` |
  | Sega Dreamcast (23) | IP.BIN `SEGA SEGAKATANA` → +0x40 | `.bin` `.cue` `.iso` `.cdi` `.gdi` |
  | Game Boy (9) | header 0x0134 title | raw `.gb/.gbc` + `.zip` (inner entry) |
  | Game Boy Color (10) | header 0x0134 title | raw `.gb/.gbc` + `.zip` |
  | Game Boy Advance (12) | header 0xAC game code | raw `.gba` + `.zip` |
  | Nintendo 64 (14) | header 0x3C cart ID | raw `.z64/.n64/.v64` + `.zip` |
  | Genesis / Mega Drive (1) | header 0x180 domestic serial | raw `.bin/.md/.gen/.smd` + `.zip` |
  | SNES / SFC (4) | LoROM 0x7FC0 / HiROM 0xFFC0 internal title | raw `.smc/.sfc` + `.zip` |
  | Nintendo DS (15) | header 0x0C game code (4 bytes) | raw `.nds` + `.zip` (inner entry) |
  | Nintendo 3DS (17) | NCSD header product code (e.g. `CTR-P-A3RP`, scanned in first 64 KB) | `.3ds` `.cci` |
  | Nintendo Wii (16) | disc header game ID → 4-byte code (`.iso` @0x00 / `.wbfs` @0x200) | `.iso` `.gcm` `.wbfs` |

  - Cartridge platforms additionally read headers from inside `.zip` (inner ROM entry); Sega disc platforms do not unwrap `.zip`.
  - **New in 1.3:** Dreamcast `.cdi` (DiscJuggler images are uncompressed but IP.BIN sits at an arbitrary depth up to hundreds of MB → full-file magic scan) and `.gdi` (GD-ROM text index parsed, every data-track `.bin` scanned for the magic); Dreamcast serial offset corrected to `magic+0x40` (Saturn/MegaCD remain `+0x20`). Nintendo handheld/disc **NDS (15)** / **3DS (17)** / **Wii (16)** added: NDS reads the 4-byte game code at 0x0C, 3DS scans the first 64 KB for the NCSD product code, Wii reads the disc-header game ID (raw `.iso` at 0x00, or `.wbfs` at 0x200 behind the 512-byte WBFS header) and matches on the 4-byte code — verified against real NAS libraries (NDS 15/15, Wii-WBFS 15/15 matched; 3DS misses only where the title is absent from the local rom_db, not an extraction failure). Wii (16) moved from blacklist to whitelist.
  - **Not yet supported:** `.chd` disc images — planned (needs a Java CHD hunk reader; see roadmap). Blacklist (still blocked from local matching): PC-FX (72), PCE-CD (114), Neo Geo CD (70), 3DO (29), PSP (61).
- **Blacklist / whitelist matching configuration** (`RomDbMatchingConfig`) — a three-tier routing table that decides *how* (or whether) each system is matched locally
  - **Why a black/white list exists:** a whole-file CRC32 on a disc image costs 30–60s per file over NAS, and a hacked/translated ROM's CRC never matches a standard dump anyway (its metadata stays empty, so ScreenScraper is still required). Systems that expose a reliable in-ROM identifier are therefore fast-tracked to the millisecond-level serial/header path (**whitelist**), while disc systems with *no* reliable internal identifier are blocked from local matching entirely (**blacklist**) to avoid pointless full-file hashing.
  - **Whitelist (16 systems — serial / header extraction path):**
    - Disc: PlayStation (57, SYSTEM.CNF), PlayStation 2 (58, SYSTEM.CNF), PlayStation 3 (59, PARAM.SFO), GameCube (13, 6-byte header game ID)
    - Sega disc (IP.BIN magic): Saturn (22, magic+0x20), Mega-CD (20, magic+0x20), Dreamcast (23, magic+0x40; also `.cdi`/`.gdi`)
    - Cartridge (header ID): Game Boy (9), Game Boy Color (10), Game Boy Advance (12), Nintendo 64 (14), Genesis / Mega Drive (1), SNES / SFC (4)
    - Nintendo handheld / disc (new in 1.3): Nintendo DS (15, game code @0x0C), Nintendo 3DS (17, NCSD product code), Wii (16, disc-header game ID; `.iso` @0x00 / `.wbfs` @0x200)
  - **Blacklist (5 systems — local matching blocked):** PC-FX (72), PC Engine CD-ROM (114), Neo Geo CD (70), 3DO (29), PSP (61) — all disc-based with no reliable internal serial extraction
  - **Default:** every other cartridge / arcade system falls back to whole-file CRC32
  - **Strategy & precedence:** the blacklist is an absolute block (checked first — hides the UI button and rejects the API call); the whitelist routes to serial/header extraction; all others use CRC32. Within an enabled run the fixed order is serial → CRC → filename.
- **Platform mapping layer** (`RomDbPlatformMapping`): normalizes libretro-database platform names to ScreenScraper `systemId`
- **REST API** (`RomDbController`): `/api/rom-db/lookup` (unified serial→CRC with 5-level fallback), `/lookup/serial`, `/search`, `/check/{systemId}` (support/blacklist/whitelist status), `/import-dat`, and async batch match by platform
- **Front-end “本地匹配” (Local Match) action** on game-list: per-system support check, button tooltip distinguishes serial vs CRC matching; batch match runs as a background task (`ROM_DB_MATCH`, viewable in task management)
- `matched` flag on game (`V1.0.23__game_matched.sql`) records local-match results; `scrape_task.task_id` (`V1.0.27__scrape_task_add_task_id.sql`) links match tasks to the background task queue
- **Fuzzy match + manual confirmation** (`/api/rom-db/fuzzy-candidates`, `/api/rom-db/fuzzy-confirm`): after exact matching, still-unmatched games can be run through a candidate list (cleaned filename → `searchBySystemAndName`, up to 10 candidates each, batch capped at 30 games); the user picks the correct entry in `fuzzyMatchModal` before it is written back — no silent auto-fill, so near-miss titles (e.g. Contra vs Super Contra) are never force-matched. The filename-cleanup rule (strip extension/brackets, underscore→space, lowercase) is shared as a common utility.
- **Per-operation matching-method selection** for local match (game-list + platform-management modals)
  - Three checkboxes: serial extraction / CRC / filename match; unchecked methods are skipped entirely (no I/O at all)
  - Fixed execution order serial → CRC → filename applies only among checked methods; filename serial regex extraction moved into the filename branch
  - Modal explains the trade-off up front: serial is millisecond-fast but does not verify ROM integrity; CRC is exact but requires a slow full-file hash; filename only extracts a serial or does fuzzy name search
  - Serial checkbox auto-disabled and greyed out for non-whitelisted systems; API accepts a `methods` array (legacy `fileNameExtract` still supported)

### Changed
- Scraping pipeline now queries rom_db first and substitutes the authoritative CRC/serial before calling ScreenScraper (`ScraperServiceImpl`, “v1.3 ROM 数据库匹配 + CRC 管线”), improving SS hit rate and cutting wasted API calls

### Fixed
- `SsNameNormalizer` regex typo caused `ExceptionInInitializerError`, hanging ScreenScraper scraping for ~2 weeks; fixed the character-class pattern so class initialization succeeds again
- Rich-text i18n entries were being flattened to a single line: `I18n.updatePage()` assigned `textContent`, wiping all `<br/>`/`<b>`/`<a>` markup — the ROM-DB import tip lost its DAT/RDB path instructions and the libretro-database attribution link. Added a `data-i18n-html` attribute rendered via `innerHTML`, restored the full tri-lingual tip, and expanded the acknowledgment (libretro for data integration + No-Intro / Redump / TOSEC / ClrMamePro / MAME for the data sources)

### Planned for 1.4–1.5
- [ ] **Full-workflow onboarding / interactive guide** — a step-by-step walkthrough of the import → match → scrape → media → export pipeline for first-time users
- [ ] **Button / feature help** — inline explanations for every action button and dialog, making the (admittedly convoluted) feature set self-documenting
- [ ] **Possible: archive extraction & format conversion** — decompress packed ROMs and convert between image formats (e.g. ISO ↔ CHD, GDI/BIN ↔ CHD); scope still under evaluation

## [1.2] - 2026-09-28

### Added
- Removed export multi-threading option
  - Thread count selector removed from export page UI (no performance benefit on single-disk I/O)
  - ROM and media file copying now runs in single-threaded mode for simplicity and correctness
  - `ExportRequest.threadCount` field and related constants removed from backend
  - Eliminates misleading UI that suggested parallel speedup where none exists
- Batch platform operations on platform-management page
  - Checkbox column with select-all for each platform row
  - Always-visible batch toolbar with 5 actions: Scrape, Translate, Swap Translations, Scan Stats, Delete
  - Buttons disabled until platforms are selected; toolbar shows selected count
  - Batch scrape/translate open existing modals and apply to all selected platforms
  - Batch swap/scan fire API calls in parallel with confirmation dialog
  - `DELETE /api/platforms/batch` endpoint for batch platform deletion
- Failed task viewer modal
  - Click failed count on media-download page opens a styled modal listing failed tasks
  - Each row shows game name (clickable link to game-edit page), platform, error message, and retry button
  - Separate views for game info failures and media download failures
  - Full i18n support (zh-CN, en-US, ja-JP)
- Separate game info / media download progress cards on media-download page
  - `getStatus()` API returns categorized counts (gameInfoPending/Running/Completed/Failed + mediaPending/Running/Completed/Failed)
  - Two independent progress cards: "🎮 游戏信息刮削" and "🖼️ 媒体文件下载"
- `media_scope` field on `scrape_task` table persists media preferences so game info tasks produce media download tasks on completion
  - Flyway migration `V1.0.19__scrape_task_media_scope.sql`
  - `enqueueGameInfoTasks()` stores media scope; `executeGameInfoTask()` restores it
- Unified scraping task pool with dynamic worker threads
  - New `scrape_task` database table replaces in-memory thread management with persistent task queue
  - `ScrapeWorkerPool` with `ThreadPoolExecutor` supports runtime dynamic thread resizing (no restart needed when SS maxThreads changes)
  - Three-level priority scheduling: User scrape (0) > Media download (10) > Arcade auto-download (50)
  - JSON game info tasks automatically produce media download tasks in the same queue
  - SS quota validation on every API response; auto-adjusts thread pool when maxThreads changes
  - Periodic quota refresh every 100 media tasks when no JSON tasks are pending
  - `ScrapeStatus` replaces `ThreadResourceManager` for quota storage and real-time monitoring
  - App restart auto-recovers interrupted tasks (RUNNING → PENDING on startup)
- Theme package system
  - `ThemeController` REST API (`GET /api/themes`, `/api/themes/current`, `POST /api/themes/apply`) for listing, querying and switching themes
  - `ThemeManager` JS module loads theme config, injects CSS variable overrides and dynamically loads custom fonts
  - `SoundEngine` JS module for per-theme sound effect support
  - Theme grid UI in system-settings page with live preview cards and active indicator
  - Theme storage: `data/themes/{themeId}/theme.json` with colors (dark/light mode), fonts and sounds sections
  - Default theme (`data/themes/default/theme.json`) ships with the application
  - Active theme persisted in `localStorage` and applied on every page load via `theme-manager.js`
- Scraper media download resource isolation
  - Media download threads now use an independent `Semaphore` (3 concurrent permits) instead of sharing `ThreadResourceManager` with game info threads
  - Eliminates resource pool contention that caused scraping hangs and slow media downloads
  - Applied to both `ScraperServiceImpl` (in-scrape media download) and `MediaDownloadServiceImpl` (standalone media download)
- Template variables collection mechanism (v3 `variables` block)
  - Optional top-level `variables` block in v3 templates declares global variables the user sets before an import/export runs (e.g. a URL prefix, the ROM output folder)
  - `TemplateV3.TemplateVariable` model + `getValidVariables()`/`getDeclaredVariableNames()`/`buildEffectiveVariables()` helpers; names clashing with built-in variables are ignored (built-in wins)
  - `ExportRequest.templateVariables` and import request bodies carry user values; injected into the shared vars map so variables work in both `{var}` placeholders and `concat(...)`/`+` expressions (URL/string concatenation)
  - `ExportOrchestrator.applyTemplateVariables` resolves per-platform `default` expressions; `TemplateV3ImportService.setGlobalVariables` injects into `rawFields` via `putIfAbsent` (game-parsed fields win)
  - Server-side `required` validation on export/import entry points (400 with missing variable list)
  - `GET /api/export/rules` and `/api/import/templates` expose each template's `variables` declarations
  - Shared frontend modal `js/template-vars.js` (`tv-` prefixed, i18n keys `tv_*`) wired into `export.html` and `data-import.html`; multi-platform batch pops up once
  - Sample template `rules/export/retroarch-folder-v3.json` (`romSubdir` + `cdnBase`)
- Notification center with SSE real-time push
  - `notification` table + REST API (`/api/notifications`, `/unread-count`, `/read`, `/stream`)
  - `NotificationServiceImpl` with 5-minute timeout + 15-second heartbeat
  - `TaskServiceImpl.completeTask/failTask` auto-creates notifications
  - Frontend EventSource with `pagehide` graceful close
- Scrape media type preset from export template
  - `GET /api/export/rules` returns `mediaTypes` array per template
  - Scrape modals in platform-management and game-list add "Preset from template" row (dropdown + apply + clear)
- Batch aggregation endpoints for performance
  - `GET /api/media-download/platforms/summary` — single GROUP BY replaces N+1 COUNT queries
  - `GET /api/gamelist/statistics/platforms/{platformId}` — single-platform stats replaces full-table scan
- Scraper media path restructure (phase 1-2)
  - New path rule: `data/scraper/games/{ssSystemId}/{ssGameId}/{type}.{ext}`
  - Local reuse check skips already-downloaded media
  - `ss_game_id` column added to game and temp_subset_game tables
  - `PathResolver.resolveGameMediaDir` centralizes directory logic
  - DB indexes: `idx_mdt_platform_status`, `idx_mdt_status_order`, `idx_game_platform_id`, `idx_game_platform_scraped`, `idx_game_name`, `idx_game_path`
- Search area multi-select dropdowns
  - Developer, publisher and genre (two-level: top-level + sub-genre) filters upgraded from flat checkboxes to searchable multi-select dropdowns
  - `GET /api/gamelist/filter-options` returns developers, publishers and a genre tree (top-level with children)
  - `publishers` filter parameter added to game list and platform game endpoints
  - Built-in `ss-genres.json` caches ScreenScraper's 159 genres with parent-child relations (zero runtime API calls)
- Game list search state persistence
  - `sessionStorage` saves search term, filter selections, page number before navigating to game-edit page
  - `restoreSearchState()` auto-restores all search conditions on return (keyword, filters, pagination, search area visibility)
  - State keyed by platformId so different platforms maintain independent search contexts
  - User-initiated "Reset" clears saved state
- Game list icon type selector
  - Toolbar dropdown lets users choose icon display type: Auto (boxart priority), 2D Box, Screenshot, Logo (Wheel), Steam Grid, Fanart, Original Image
  - Default "Auto" mode prioritizes consistent boxart media (`box2d > steamgrid > image > boxFront > poster > ss > fanart`) instead of mixing logos/screenshots
  - Selection persisted in `localStorage` and applied instantly with re-render
- Fixed table scroll layout with sticky headers
  - Game list and platform management pages now scroll tables within fixed-height containers instead of whole-page scrolling
  - Toolbar, search area, and pagination controls remain visible at all times
  - Table headers (`th`) use `position: sticky` to stay visible when scrolling through data
  - Viewport locked with `.app-layout { height: 100vh }` to prevent page-level scroll
  - Search area has `max-height: 50vh` with internal scroll when expanded
- Empty field filters in game list search
  - New "Empty Field Filter" checkbox group in search area: game name, description, genre, players, publisher, rating
  - When checked, fetches all data and filters client-side for fields that are null/empty
  - Supports pagination on filtered results; state persisted in sessionStorage
- Empty/not-empty global toggle for field filters
  - Both game-list and platform-management pages now offer a global toggle: "为空" (empty) / "不为空" (not empty)
  - When "not empty" is selected, checked fields filter for games that HAVE values (IS NOT NULL AND not blank)
  - Client-side cache key includes the mode, so switching modes invalidates cached results
  - Session storage persists the selected mode across page navigations
- Separation modal filter UI restructure
  - Developer/publisher/genre dropdowns now have search input boxes for quick filtering
  - Language dropdown expanded to 12 languages (en/zh/ja/fr/de/es/it/pt/ko/ar/nl/ru)
  - New empty field filter section in separation modal (6 checkboxes: name/desc/genre/players/publisher/rating)
  - Scrape status checkboxes aligned to four-tier values: scraped/original/poor_quality/raw
  - Multi-select support for developers, publishers, genres, and players in backend SQL (OR LIKE matching)
- Game edit page search auto-fill with filename
  - "Search Game" modal now pre-fills search box with game filename (without extension) when game name is empty
  - Extracts filename from game path, strips extension for better ScreenScraper matching
- Genre dialect mapping (term mapping system)
  - `term_mapping` table with many-to-one structure: multiple source terms → single normalized target, keyed by `(source_term, category)`
  - 261 seed entries across 2 categories (`genre` for genre normalization, `system_alias` for platform matching)
  - `map(value)` and `mapFirst(value)` expression functions in v3 export templates for runtime genre lookup
  - `map()` returns all matching targets (for multi-value scenarios); `mapFirst()` returns single value (for directory names)
  - Racing/Driving dialect mapping added to unify synonymous genre terms
  - System alias `ss` → Sega Saturn (systemId 22) added for smart platform matching
- fnOS fpk v1.2.0 package
  - Rebuilt fnpack with detailed Chinese description and app metadata
  - Install wizard adds optional ROM directory 2 & 3 fields (`fk_roms2_path`, `fk_roms3_path`); leaving empty skips mounting
  - Docker compose mounts additional ROM paths to `/tmp/fk-romsN` transit paths
  - `entrypoint.sh` conditionally creates symlinks in `/data/roms/` only when mounted directories are non-empty

### Changed
- Platform management action column simplified: removed per-row scrape/translate/swap/delete/scan/view buttons, kept only edit/separation/statistics; all batch operations moved to toolbar
- Failed task modal redesigned with card-style grid layout, backdrop blur, and CSS design tokens (replaced inline styles)
- Full Thymeleaf migration: all 18 pages converted from static HTML to Thymeleaf templates with shared layout fragments
- Unified purple design system across all pages (`--accent-primary: #8b5cf6`, `.pg-container`, normalized button classes)
- Platform management action column: replaced text buttons with icon buttons (`action-icon-btn` + emoji + i18n tooltip)
- Platform management row click delegation restored (click row = view games, click name = inline edit)
- Import template description now shows `notes` field for richer context
- Frontend polling optimized: 3 requests per cycle (was 3+3N), reentry guard, visibilitychange pause
- Internationalization: ~50% of remaining untranslated Chinese text resolved (700→357 lines)
- Column sorting on platform-management and game-list tables (click header to sort, click again to reverse)

### Fixed
- Sortable table headers not staying fixed during scroll (`.game-table th.sortable` and `#platformTable th.sortable` had `position: relative` overriding `position: sticky`; changed to `position: sticky`)
- `changePageSize()` losing search conditions when changing page size (now preserves search term and filters)
- Circular dependency crash: `ScrapeWorkerPool → ScrapeStatus → ScraperServiceImpl → ScrapeWorkerPool` fixed with `@Lazy` on ScrapeWorkerPool fields
- Media download tasks never created: `executeGameInfoTask()` sent empty `ScraperRequest` without `mediaScope`, so `enqueueMediaTasksFromGameInfo()` never produced tasks; fixed by persisting and restoring media preferences
- Game edit URL in failed task modal: `/game-edit/{id}` → `/game-edit?id={id}` (page uses query parameter, not path variable)
- Notification bell playing multiple sounds simultaneously: MutationObserver now skips elements inside `#notificationModal` to prevent batch audio triggers
- Scraper hang / deadlock caused by `ThreadResourceManager` counter corruption
  - `MediaDownloadServiceImpl.finally` called `reset()` before `shutdownNow()`, but worker threads still called `releaseForMedia()` after reset, driving `mediaActive` to -6 and `availableThreads` to 12
  - Subsequent scraping tasks inherited corrupted state (12 game info threads running instead of max 6)
  - Fix: scraper now calls `reset()` at the start of each scraping task to ensure clean state
- Media downloads extremely slow due to shared resource pool contention with game info threads
  - Game info threads (high priority, 120s readTimeout) occupied all 6 resource slots, starving media download threads (low priority, 5s timeout)
  - Fix: media downloads use independent `Semaphore` with 3 permits, completely decoupled from `ThreadResourceManager`
- Media download throughput improved: buffer size increased from 8KB to 64KB in both `ScraperServiceImpl.downloadMediaFile` and `MediaDownloadServiceImpl.downloadMediaFileWithStatus`
- Media download page causing system-wide slowdown (N+1 request storm → batch GROUP BY)
- Platform details page freezing (full-table SUM CASE scan → single-platform query + index)
- Browser spinner on page navigation (SSE zombie connections exhausting HTTP/1.1 6-connection limit)
- Scrape results always showing 0 success 0 failure (missing `org.tukaani:xz` causing `NoClassDefFoundError` bypassing `catch(Exception)`; added xz dependency + `catch(Throwable)`)
- Media download page showing "no tasks" despite tasks existing (H2 unquoted SQL alias folded to uppercase, `row.get("platformId")` always null; fixed with `AS "platformId"`)
- temp-subset-edit notification unread count never decrementing
- platform-details JS null reference after banner removal
- Genre filtering broken for multi-genre games (`genre IN` exact match vs comma-separated values; now matches `genreid` with comma-boundary LIKE)
- Scrape-status filter not working (frontend sent `fully/partially/not` while SQL expected `scraped/original/poor_quality/raw`; search area now offers the same four statuses as platform management)
- Export genre subdirectory fragmentation: `map()` returned all matching dialect combos producing directory names like `ACT-AVG` instead of a single folder; switched to `mapFirst()` for single-value result
- `poor_quality` scrape-status filter too broad: original SQL matched any scraped game missing a single media type (box_2d/ss/wheel all null for every scraped game), making it indistinguishable from `scraped`; redefined to check metadata completeness (desc/developer/publisher/genre/releasedate)
- Notification modal positioning broken on data-import page (missing `position: fixed` CSS; moved `#notificationModal` positioning to global `theme.css` so all pages share correct fixed overlay + centering)
- `TermMappingInitializer` seed data bug: `hasSeedData()` checked total row count but `seedGenreMappings()` ran first inserting genre rows, causing `system_alias` seeds to be skipped; fixed by filtering on `category = 'system_alias'`
- Game list search state lost after editing a game: second `loadGames` function definition (line 1975) overrode the first but didn't fall back to global `currentSearchTerm`/`currentSearchParams` when called without parameters; pagination and `restoreSearchState()` calls lost search conditions and showed all games
- Empty field filter pagination causing 500 error: clicking "next page" re-requested server with `pageSize=99999`; added client-side cache (`window._emptyFieldCache`) so filtered results are fetched once and paginated locally
- Separation modal TypeError crash: JS referenced 6 non-existent element IDs (`yearMin/yearMax/developer/publisher/genre/minPlayers` instead of `startDate/endDate/developerSelect/publisherSelect/genreSelect/playersSelect`), causing null.value TypeError on submit
- Separation modal scrape status values mismatch: form sent `fully/partially/not` but SQL expected `scraped/original/poor_quality/raw`; now uses correct four-tier values
- `gameNameRegex` filter missing from `selectGamesByFilter` SQL (only existed in `countGamesByFilter`), causing regex filter to have no effect on separation results

### Breaking Changes
- ⚠️ Scraper media directory structure changed from `{platformName}/{localId}/{region}/` to `{ssSystemId}/{ssGameId}/`. Previously scraped media files are not directly reusable; users need to re-scrape or wait for a future compatibility migration.

### Removed
- Legacy v2 template documentation (import-templates, export-functionality, media-mapping docs)

## [1.1-RC1] - 2026-09-14

### Added
- ScreenScraper full integration
  - Game metadata and media scraping via ScreenScraper API v2
  - CRC32 calculation for accurate ROM identification
  - Real-time quota monitoring and thread resource management
  - Batch scraping with configurable concurrency
  - Media download task queue with pause/resume support
- v3 Template System
  - New generation import/export templates with expression engine
  - Template variables: {filename}, {filepath}, {stem}, {ext}, {dir}, {sub}, etc.
  - Built-in functions: upper(), lower(), trim(), replace(), if(), default()
  - mediaDiscovery configuration for intelligent media file matching
  - Support for Pegasus, ES-DE, RetroBat, EmuELEC, EmuELEC, Skraper-ES v3 templates
- Game Edit Page (game-edit.html)
  - Dedicated standalone page for game editing
  - Full field support with media preview
  - Path field enhancement with absolute path display
- File existence verification during import
  - Validates game files exist on filesystem
  - Visual indicators (red font) for missing files in game list
  - File status filtering in search and platform separation
- Background task recovery
  - Auto-resume interrupted media download tasks on startup
  - Auto-resume background tasks after application restart
- Windows EXE distribution
  - jpackage-based app-image packaging with bundled JRE
  - No Java installation required for end users

### Changed
- SS Standard Field Migration
  - Unified MediaType enum with 50 media types aligned to ScreenScraper nomcourt standard
  - Database schema updated with all SS standard columns
  - Frontend UI fields aligned with database schema
  - Backward compatibility maintained for legacy field names
- H2 Database schema completion
  - Added all missing columns via ALTER TABLE ... ADD COLUMN IF NOT EXISTS
  - init.sql now serves as single source of truth for schema
- Docker improvements
  - Runtime base image changed from openjdk:27-ea-17-jdk-slim to eclipse-temurin:17-jre (stable)
  - Health check no longer depends on actuator endpoint
  - Consolidated to 2 Dockerfile variants (standard + China mirror)
- Import media discovery moved to template-driven matching
  - Media file lookup now follows template rules instead of hardcoded paths
  - ROM filename vs display name priority strategy for media matching
- Pegasus v3 import handles multi-file entries via files field expansion

### Fixed
- Fixed Spring Boot circular dependency between ScraperService and MediaDownloadServiceImpl (@Lazy)
- Fixed H2 database FOR UPDATE SKIP LOCKED compatibility
- Fixed v3 import missing absolutePath causing scraper API 400 errors
- Fixed frontend relative path rendering for media files
- Fixed template parameter not passed to conversion method
- Fixed media type name case sensitivity
- Fixed platform statistics table overflow
- Fixed Flyway disabled causing init.sql and database schema inconsistency

### Improved
- Enhanced game filtering and search with file status options
- Improved XML parsing for better frontend template compatibility
- Added ErrorLogWriter for centralized error logging
- Template expression engine with comprehensive built-in function set
- Import media matcher with detailed logging for path resolution

## [1.0.6-beta3] - 2026-05-11

### Added
- Added file existence verification during game import
  - Supports traditional import and template import methods
  - Validates game files exist on the filesystem
- Added file status filtering in game list search
  - Filter games by "File Exists" or "File Not Exists" status
- Added file status filtering in platform separation modal
  - Filter games by file existence before separation
- Added internationalization support for file status UI elements
  - Added `file-status`, `file_status`, `file_exists`, `file_not_exists` keys
  - Supports Chinese, English, and Japanese languages

### Changed
- Game list displays games with missing files in red font
  - Game names shown in bold red (#ff4444) when file does not exist
  - Game paths shown in red (#ff6666) when file does not exist
- Updated Docker image tag to `dev` for development builds

### Fixed
- Fixed file existence verification logic not being properly set during import

### Improved
- Enhanced game-list.html search functionality with file status filter options
- Enhanced platform-management.html separation functionality with file status filter

## [1.0.6-beta2] - 2026-05-08

### Added
- Added EmuELEC import/export templates (`emuelec.json`)
  - Export template: Supports exporting game metadata and media files
  - Import template: Supports importing from EmuELEC gamelist.xml and downloaded_images
  - Supports three gamelist.xml lookup locations as per EmuELEC specification
  - Includes 15+ media types: box2dfront, box2dback, box2dside, box3d, fanart, screenshot, video, wheel, banner, manual, images, titlescreen, marquee, miximage, cartridge, bezel
- Added `exportOptions` configuration for export templates
  - `gameFiles`: Controls whether ROM files can be exported (default: true)
  - `mediaFiles`: Controls whether media files can be exported (default: true)
  - `gameListFile`: Controls whether gamelist.xml can be exported (default: true)
  - `showWarning`: Controls whether to display template limitation warnings (default: false)
- Added enhanced media file lookup logic
  - Now executes template rules regardless of whether media files exist in gamelist.xml
  - Supports filling in missing media types even when some are already present
  - Added detailed logging for media file matching paths
- Added missing media types to Skraper-EmulationStation import template
  - Added `boxFull`, `cartridge`, `manual`, `logo`, `bezel`, `panel`, `cabinetLeft`, `cabinetRight`, `tile`, `steam`, `poster`, `background`, `music`

### Fixed
- Fixed ES-DE import template not finding `downloaded_media` folder
  - Adjusted media path rules to use `../../downloaded_media/{platform}/{mediaType}/{filepath}.{ext}`
- Fixed media file matching not logging complete paths
  - Added detailed log output showing all attempted paths
- Fixed media type name case sensitivity issue
  - Added case-insensitive matching for media type names (e.g., `boxFront` vs `boxfront`)
- Fixed platform statistics page duplicate file conflict table overflow
  - Added vertical scroll (`max-height: 300px; overflow-y: auto;`)
  - Added horizontal scroll for wide tables
  - Set minimum table width for better readability
- Fixed template parameter not being passed to conversion method
  - Added `ImportTemplate template` parameter to `convertToGameModel` method

### Improved
- Improved ES-DE export template documentation
  - Added clear description about ROM file export restriction
  - Updated to English description for international compatibility
- Improved EmuELEC export template documentation
  - Added clear description about ROM file export restriction
  - Updated to English description for international compatibility
- Improved export page UI to reflect template restrictions
  - Automatically disables checkboxes based on `exportOptions` configuration
  - Shows warning message when template has export restrictions
  - Dynamically displays template description from JSON file
- Updated skraper-es.json import template with comprehensive media types
  - Now covers all standard Skraper media types
  - Added multiple path rules for better compatibility

### Known Issues
- Some text entries are still not fully translated

### Roadmap
- **1.1**: First official release
  - Import/export templates:
    - EmuDeck
    - ~~Lakka~~ ✅ Done
    - ~~EmuElec~~ ✅ Done
    - Recalbox
    - Batocera
  - Multi-file game support (e.g., DOS games) by treating entire game folders as game entries
  - Folder export configuration with configurable parent directory levels
  - Advanced game filtering options
  - Native Windows support with standalone EXE distribution
- **1.2**: Integrate with ScreenScraper API for game scraping functionality
- **1.3**: Statistics dashboard for game collection analytics

## [1.0.6-beta] - 2026-05-01

### Added
- Added Lakka .lpl playlist format import/export support
  - Support for 5-line and 6-line .lpl formats
  - Automatic format detection
  - Support for DETECT placeholder handling
  - ZIP ROM path parsing with `#` separator
- Added `{filepath}` template variable for media file matching
  - Supports media files organized in subdirectories
  - For example: `media/screenshot/{filepath}.{ext}` → `media/screenshot/path1/path2/game.png`
- Added Skraper-EmulationStation import template (`skraper-es.json`)
  - Supports Skraper-scraped data for EmulationStation frontend
  - Full support for deep subdirectory media file paths
  - Includes all Skraper media types: box2dfront, box2dback, box2dside, box3d, boxtexture, fanart, images, marquee, screenmarquee, screenmarquesmall, screenshot, screenshottitle, steamgrid, support, supporttexture, videos, wheel, wheelcarbon, wheelsteel
- Added EmulationStation DE (ES-DE) export template (`esde.json`)
  - Follows ES-DE official directory structure
  - gamelist.xml exported to `gamelists/{platform}/` folder
  - Media files exported to `downloaded_media/{platform}/` folder
  - Supports 15+ media types: boxfront, boxback, cartridge, titlescreen, miximage, marquee, fanart, video, manual, wheel, screenshot, banner, box3d, bezel, steamgrid
  - Automatic `./` path prefix for gamelist.xml entries
- Added EmulationStation DE (ES-DE) import template (`esde.json`)
  - Supports importing ES-DE gamelist.xml and media files
  - Note: Users must copy `downloaded_media` and `gamelists` folders to the same directory before importing
  - Uses `{filepath}` variable for subdirectory media file support
  - Supports 10+ media types: boxfront, boxback, box3d, screenshot, video, wheel, marquee, fanart, titlescreen, manual

### Fixed
- Fixed template description display: Now dynamically reads description from template JSON files instead of hardcoded values
- Fixed .lpl template file not found issue in Docker container
- Fixed game_id NULL constraint error during Lakka import
- Fixed frontend filter not recognizing .lpl file type

### Improved
- Improved XML parsing logic for better compatibility with various frontend templates
- Added ErrorLogWriter utility for centralized error logging
- Enhanced game filtering and search functionality
- Added FilterResult model for improved data processing
- Updated import template documentation with new {filepath} variable

## [1.0.5-beta] - 2026-04-30

### Fixed
- Fixed page internationalization logic in multiple HTML files
- Corrected numerous untranslated internationalization entries
- Fixed translation keys not being applied after dynamic content updates
- Resolved language switching issues in game-list.html, task-management.html, and other pages
- Fixed syntax errors and undefined variable errors (translation, currentLanguage)

### Improved
- Added complete multi-language support (Chinese, English, Japanese)
- Enhanced translation loading mechanism
- Improved updateTranslations() function to properly handle dynamically generated content
