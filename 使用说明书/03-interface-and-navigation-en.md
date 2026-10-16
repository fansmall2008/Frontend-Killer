# 03 · Interface & Navigation

> This chapter introduces the overall UI layout and where every page lives, so you "know where to click when you want a feature".

---

## 3.1 Overall Layout

After opening the software, the UI is divided into three areas:

```
┌──────────┬───────────────────────────────────┐
│          │  Top bar (breadcrumb · theme · bell)│
│  Left     ├───────────────────────────────────┤
│  nav      │                                    │
│ (sidebar) │        Main content (feature pages)│
│          │                                    │
└──────────┴───────────────────────────────────┘
```

- **Left navigation bar (sidebar)**: entry points to all major features, arranged in groups — where you'll click most often.
- **Top bar**: shows the current location (breadcrumb), a light/dark theme toggle, and the notification bell.
- **Main content area**: the current page's content and actions.

The sidebar bottom also has a status indicator (showing total game count), and the brand area in the lower-left shows the version number.

---

## 3.2 Sidebar Menu Map

The sidebar is divided into 4 groups with 9 main entries total:

### ▸ OVERVIEW
| Menu | Icon | Purpose | Chapter |
|---|---|---|---|
| **Home** | 🏠 | Dashboard: overall stats, system status, running tasks | `05` |

### ▸ DATA
| Menu | Icon | Purpose | Chapter |
|---|---|---|---|
| **Data Import** | 📥 | Import game data / ROMs | `09` |
| **Data Export** | 📤 | Export organized data to frontends | `10` |
| **Platform Management** | 🎮 | Manage all platforms; enter a platform to see its game list | `06` |

### ▸ TOOLS
| Menu | Icon | Purpose | Chapter |
|---|---|---|---|
| **Scraper Systems** | 🔍 | Manage the ScreenScraper system list (scrapable console categories) | `11` |
| **Media Download** | 🖼️ | Download/manage game images, videos, and other media | `12` |
| **Data Merge** | 🔀 | Merge data from multiple platforms together | `13` |
| **Task Management** | 📋 | View and control background tasks (scrape, import, export, download) | `14` |
| **Log Viewer** | 📊 | View runtime logs in real time to troubleshoot | `16` |

### ▸ SYSTEM
| Menu | Icon | Purpose | Chapter |
|---|---|---|---|
| **System Settings** | ⚙️ | Language, ScreenScraper account, theme, translation, database backup, etc. | `17` |

> Note: The Platform Management menu may show a small numeric badge (total platforms) on its right, and Media Download may show a pending-download count badge.

---

## 3.3 Sub-pages Not in the Sidebar but You'll Use

Some pages are reached by "clicking into" a main page and don't appear directly in the sidebar:

| Sub-page | Reached from | Purpose | Chapter |
|---|---|---|---|
| **Game List** | Platform Management → click a platform | View/manage all games under that platform | `07` |
| **Game Editing** | Game List → click a game | Edit a single game's details | `08` |
| **Platform Details** | Platform Management → platform stats/details | View detailed platform statistics | `06` |
| **Temp Subset Edit** | Platform Management related entry | Filter a temporary subset out of a large platform to handle separately | `15` |
| **Scraper System Edit** | Scraper Systems → click a system | Edit system info | `11` |
| **Merge Conflicts / Merge Report** | Within the Data Merge flow | Handle merge conflicts, view the merge result report | `13` |

---

## 3.4 The Top Bar Trio

The right side of the top bar has three commonly used controls:

### 1) Breadcrumb (left)
Shows your current location, e.g. `🏠 › DASHBOARD`. Click 🏠 to return home.

### 2) Light/Dark theme toggle ☀️ / 🌙
Click to switch between **dark/light** mode. Your choice is remembered for next time. (Richer theme-pack switching is in "System Settings", see `17`.)

### 3) Notification bell 🔔
- When background tasks (scrape, import, export, media download) **complete or fail**, notifications are pushed automatically and a red unread badge appears on the bell.
- Click the bell to open the notification panel to view all notifications and clear them.
- Notifications are pushed in real time (SSE-based); each expires after about 5 minutes, and the bell also does a fallback unread-count refresh every 30 seconds.

---

## 3.5 Two Windows That May Pop Up on First Open

On first use (or when the environment isn't configured), you may hit popups right on the home page. Don't panic — the software is reminding you:

### 1) "SCRAPER NOT READY" warning
If the **ScreenScraper account isn't configured** or **there's no scraper system data in the database**, the home page shows this yellow warning, listing exactly what's missing, with a "Go to configuration" button.

- **What to do**: Click "Go to configuration" to jump to System Settings, then follow Step 3 of `02 Quick Start` to configure the account and "Fetch system list".
- Once configured, this popup won't appear again.

### 2) "Enable game info auto-cache?" prompt (first time only)
On the very first run, the software asks you once whether to enable **arcade game info auto-cache**.

- When enabled: info for successfully scraped arcade games is cached, so export can automatically find the "parent ROM" for you (this uses some ScreenScraper quota).
- It pops up only once; you can change it anytime later in System Settings.
- If unsure: enable it if you play arcade (MAME/FBA etc.); choose "not now" if you don't.

---

## 3.6 Where to Switch Language

The UI supports **Chinese / English / Japanese**. Switch via **System Settings → Select language**, then save and refresh the page. The whole UI (including popups and notifications) follows the switch.

---

## 3.7 Summary

- 90% of daily operations live in the **9 entries of the left sidebar**.
- Check progress/results → **Task Management** and the top-right **notification bell**.
- Troubleshoot → **Log Viewer**.
- Configuration (language, account, theme, backup) → **System Settings**.

Next: `04 End-to-End Workflow` strings these pages into one complete usage pipeline.
