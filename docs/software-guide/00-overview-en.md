# Frontend-Killer User Guide

> **Version**: v1.2 · **Updated**: 2026-09-23

Frontend-Killer (webGamelistOper) is a web-based retro game metadata management platform that transforms ROM file collections into game list data ready for use by frontend launchers.

---

## Data Processing Pipeline

```mermaid
flowchart LR
    A["🎮 ROM Files"] --> B["📥 Data Import"]
    B --> C["🗄️ H2 Database"]
    C --> D["🔍 Scraping / Search"]
    D --> E["📝 Metadata Editing"]
    E --> F["🔀 Platform Merge"]
    F --> G["📤 Data Export"]
    G --> H["📋 Frontend Format Files"]

    D -->|"Automatic"| I["🖼️ Media Download"]
    I --> H

    style A fill:#333,stroke:#00ffff,color:#fff
    style C fill:#1a1a2e,stroke:#00ffff,color:#00ffff
    style H fill:#006600,stroke:#44ff44,color:#fff
```

### Pipeline Steps

| Step | Description | Page |
|------|-------------|------|
| 1. ROM Files | Your retro game ROM file directory | — |
| 2. Data Import | Scan ROM directories and generate game records using import templates | [Data Import](03-data-import-en.md) |
| 3. H2 Database | All game data stored in embedded H2 database | [System Settings](11-system-settings-en.md) |
| 4. Scraping/Search | Fetch game metadata and media via ScreenScraper API | [Scraper System](09-scraper-en.md) |
| 5. Metadata Editing | Manually correct/supplement game names, descriptions, translations | [Game Edit](06-game-edit-en.md) |
| 6. Platform Merge | Merge multiple sub-platforms into one unified platform | [Platform Merge](07-platform-merge-en.md) |
| 7. Data Export | Output gamelist.xml and other files using target frontend templates | [Data Export](08-export-en.md) |
| 8. Media Download | Automatically download screenshots, covers, videos, etc. | [Media Download](10-media-download-en.md) |

---

## Data Production Flowchart

```mermaid
flowchart TD
    subgraph INPUT["📥 Data Input Phase"]
        I1["Select ROM Directory"] --> I2["Choose Import Template"]
        I2 --> I3["Set Scan Depth"]
        I3 --> I4["Start Scan & Import"]
        I4 --> I5["Generate Basic Game Records\n(Name, Path, ROM File Info)"]
    end

    subgraph SCRAPE["🔍 Data Scraping Phase"]
        S1["Configure ScreenScraper Account"] --> S2["Initialize Scraper Systems"]
        S2 --> S3["Assign Scraper System to Platform"]
        S3 --> S4["Execute Game Info Scraping"]
        S4 --> S5["Fetch Metadata\n(Developer/Publisher/Genre/Rating/Description…)"]
        S4 --> S6["Create Media Download Tasks\n(Screenshots/Covers/Videos…)"]
    end

    subgraph EDIT["📝 Data Editing Phase"]
        E1["Edit Individual Games"] --> E2["Correct Name/Description"]
        E1 --> E3["Add Translation Name/Description"]
        E1 --> E4["Manually Upload Media"]
        E1 --> E5["Search ScreenScraper for Supplements"]
    end

    subgraph MERGE["🔀 Data Merge Phase"]
        M1["Select Multiple Source Platforms"] --> M2["Specify New Platform Name"]
        M2 --> M3["Execute Merge"]
        M3 --> M4["Resolve Conflicts"]
    end

    subgraph EXPORT["📤 Data Output Phase"]
        X1["Select Target Platform"] --> X2["Select Frontend Template"]
        X2 --> X3["Choose Export Scope"]
        X3 --> X4["Export Data Files + Media Files"]
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

## Page Navigation Map

```mermaid
graph TD
    HOME["🏠 Home\nindex.html"]

    HOME --> IMP["📥 Data Import\ndata-import.html"]
    HOME --> PM["📋 Platform Mgmt\nplatform-management.html"]
    HOME --> MERGE["🔀 Platform Merge\nplatform-merge.html"]
    HOME --> EXP["📤 Data Export\nexport.html"]
    HOME --> SS["⚙️ System Settings\nsystem-settings.html"]

    PM --> GL["🎮 Game List\ngame-list.html"]
    GL --> GE["✏️ Game Edit\ngame-edit.html"]

    SS --> SSL["🔍 Scraper Systems\nscraper-system-list.html"]
    SSL --> SSE["📝 Scraper Edit\nscraper-system-edit.html"]

    SS --> MD["🖼️ Media Download\nmedia-download.html"]
    SS --> TM["📊 Task Management\ntask-management.html"]
    SS --> LV["📃 Log Viewer\nlog-viewer.html"]

    PM --> TSE["📦 Temp Subset Edit\ntemp-subset-edit.html"]

    MERGE --> MR["📋 Merge Reports\nmerge-reports.html"]
    MR --> MRD["📄 Report Details\nmerge-report-details.html"]
    MRD --> MC["⚠️ Resolve Conflicts\nmerge-conflicts.html"]

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

## Documentation Index

| No. | Document | Description |
|-----|----------|-------------|
| 00 | [Overview](00-overview-en.md) | This page — flowcharts and page index |
| 01 | [Quick Start](01-quick-start-en.md) | Minimum steps from zero to export |
| 02 | [Home Page](02-home-page-en.md) | Home page feature cards and navigation |
| 03 | [Data Import](03-data-import-en.md) | ROM scanning and import configuration |
| 04 | [Platform Management](04-platform-mgmt-en.md) | Platform CRUD, scraper system binding |
| 05 | [Game List](05-game-list-en.md) | Game table, search, batch operations |
| 06 | [Game Edit](06-game-edit-en.md) | Single game editing, media management, search |
| 07 | [Platform Merge](07-platform-merge-en.md) | Multi-platform merge and conflict handling |
| 08 | [Data Export](08-export-en.md) | Export configuration and frontend format output |
| 09 | [Scraper System](09-scraper-en.md) | ScreenScraper integration and system management |
| 10 | [Media Download](10-media-download-en.md) | Media download task management and quotas |
| 11 | [System Settings](11-system-settings-en.md) | Language, account, database backup |
| 12 | [Task Management](12-task-management-en.md) | Background task monitoring and logs |
| 13 | [Log Viewer](13-log-viewer-en.md) | Real-time log viewing |
| 14 | [Temp Subset](14-temp-subset-en.md) | Subset separation, editing, and sync |
| 15 | [FAQ](15-faq-en.md) | FAQ and troubleshooting |

---

## Supported Frontend Formats

| Frontend | Import Template | Export Template |
|----------|----------------|-----------------|
| Pegasus | `pegasus.json` / `pegasus-v3.json` | `pegasus.json` / `pegasus-v3.json` |
| ES-DE (EmulationStation) | `esde.json` / `esde-v3.json` | `esde.json` / `esde-v3.json` |
| RetroBat | `retrobat.json` / `retrobat-v3.json` | `retrobat.json` / `retrobat-v3.json` |
| EmuELEC | `emuelec.json` / `emuelec-v3.json` | `emuelec.json` |
| Lakka | `lakka.json` | `lakka.json` |
| Skraper (ES) | `skraper-es.json` / `skraper-es-v3.json` | — |
| WebGamelistOper | `webgamelistoper.json` / `webgamelistoper-v3.json` | — |

---

## Tech Stack

- **Backend**: Spring Boot 3.2.x + Java 17
- **Database**: H2 Embedded (single file `data/database/database.db`)
- **Frontend**: Pure HTML + CSS + JavaScript (no framework dependencies)
- **Scraping**: ScreenScraper API (CRC32 matching)
- **Deployment**: Executable JAR / Docker
