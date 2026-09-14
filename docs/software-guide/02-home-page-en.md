# Home Page

> Page file: `index.html`

The home page is the application's navigation center, providing 5 feature card entries and a system status overview.

---

## Page Layout

```
┌─────────────────────────────────────┐
│        Frontend-Killer Title         │
├──────────┬──────────┬───────────────┤
│  Import  │   Data   │    Data       │
│          │Processing│    Merge      │
├──────────┼──────────┼───────────────┤
│  Export  │  System Settings & Data   │
├──────────┴──────────┴───────────────┤
│            Footer Info               │
└─────────────────────────────────────┘
```

---

## Feature Cards

### 1. Import

- **Link target**: `data-import.html`
- **Description**: Scan ROM directories and import game data using templates
- **See also**: [Data Import](03-data-import-en.md)

### 2. Data Processing

- **Link target**: `platform-management.html`
- **Description**: Manage platform list, edit game data, execute scraping
- **See also**: [Platform Management](04-platform-mgmt-en.md)

### 3. Data Merge

- **Link target**: `platform-merge.html`
- **Description**: Merge multiple platforms into a new platform
- **See also**: [Platform Merge](07-platform-merge-en.md)

### 4. Export

- **Link target**: `export.html`
- **Description**: Export game lists and media files using frontend templates
- **See also**: [Data Export](08-export-en.md)

### 5. System Settings & Data

This card contains multiple sub-entries:

| Sub-card | Link Target | Description |
|----------|-------------|-------------|
| System Settings | `system-settings.html` | Language, SS account, database management |
| Scraper System Mgmt | `scraper-system-list.html` | Manage ScreenScraper system data |
| Media Download Mgmt | `media-download.html` | View and control media download tasks |
| H2 Database Console | `/h2-console` | Direct H2 database queries |
| Task Management | `task-management.html` | View background task status and logs |

---

## Notification Bell 🔔

- **Position**: Top-right corner of the page
- **Function**: Click to expand the notification panel showing recent task messages
- **Badge**: Red number indicates unread message count
- **Close**: Click the × button in the modal or click outside the modal to close

---

## Scraper Environment Check

When first entering the home page, the system automatically checks:

1. Whether a **ScreenScraper account** is configured
2. Whether **scraper system data** is initialized

If either condition is not met, a warning dialog appears:

| Button | Function |
|--------|----------|
| **"Go to Configure"** | Navigate to System Settings to configure SS account |
| **"Got It"** | Dismiss the warning and continue |

> 💡 You can still use the app without configuring an SS account, but scraping will run in guest mode with a lower daily request limit.

---

## Next Steps

- Start importing data → [Data Import](03-data-import-en.md)
- See all pages → [Overview](00-overview-en.md)
