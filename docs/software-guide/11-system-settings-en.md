# System Settings

> Page file: `system-settings.html`

The System Settings page configures system parameters including language, ScreenScraper credentials, game list display, and database backup.

---

## Page Header

| Element | Description |
|---------|-------------|
| **"Back to Home"** button | Returns to the home page |
| **🔔 Notification Bell** | Shows unread task message count, click to open notification panel |

---

## Language Settings

| Element | Description |
|---------|-------------|
| **Select Language** | Dropdown: 中文 / English / 日本語 |

> 💡 Language settings take effect after page refresh.

---

## ScreenScraper Settings

| Field | Description |
|-------|-------------|
| **Username** | ScreenScraper account username |
| **Password** | ScreenScraper account password |

| Button | Description |
|--------|-------------|
| **"Save Settings"** | Save ScreenScraper credentials |
| **"Test Connection"** | Test whether the current credentials can connect to the ScreenScraper API |
| **"Fetch System List"** | Pull all system data from ScreenScraper and import into the database (requires confirmation) |

> 💡 Leaving username/password blank will use guest mode (approximately 20,000 requests per day limit). For higher limits, register an account at [ScreenScraper.fr](https://www.screenscraper.fr/).

---

## Game List Display Settings

| Field | Description |
|-------|-------------|
| **Game List Icon Field** | Dropdown to select the media type displayed as the icon in the game list |

Available icon types (21 options):

| Option | Description |
|--------|-------------|
| **Cover Image (image)** | Default option |
| **Wheel (wheel)** | Wheel Logo |
| **Wheel Carbon (wheel-carbon)** | Carbon-style wheel |
| **Wheel Steel (wheel-steel)** | Steel-style wheel |
| **Logo (logo)** | Generic Logo |
| **Thumbnail (thumbnail)** | Thumbnail image |
| **Box 2D Front (box-2D)** | 2D box front cover |
| **Box 2D Back (box-2D-back)** | 2D box back cover |
| **Box 3D (box-3D)** | 3D box |
| **Box 2D Side (box-2D-side)** | 2D box side |
| **Banner (banner)** | Banner image |
| **Title Screen (sstitle)** | Title screenshot |
| **Screenshot (ss)** | Game screenshot |
| **Fan Art (fanart)** | Fan art image |
| **Poster (poster)** | Poster image |
| **Support 2D (support-2D)** | 2D cartridge/media |
| **Bezel (bezel)** | Screen bezel |
| **Panel (panel)** | Control panel image |
| **Background (background)** | Background image |
| **Steam (steam)** | Steam-style image |
| **Steam Grid (steamgrid)** | Steam Grid image |

| Button | Description |
|--------|-------------|
| **"Save Settings"** | Save icon display settings (stored in browser local storage, takes effect after page refresh) |

> 💡 If a game doesn't have a media file of the selected type, the icon position will display blank.

---

## Database Backup

| Button | Description |
|--------|-------------|
| **"Backup Database"** | Export the current database as a SQL backup file |
| **"Restore Database"** | Restore the database from a backup file (requires selecting a backup file) |
| **"Execute Initialization"** | Rebuild table structure and clear all data (⚠️ Irreversible operation) |

### Backup File List

Displays all existing database backup files, each with a "Restore Database" button.

---

## Notification System

| Element | Description |
|---------|-------------|
| **Notification Bell 🔔** | Fixed in the top-right corner, red badge shows unread message count |
| **Notification Popup** | Colored notification bar in the top-right corner, auto-dismisses after 3 seconds |
| **Notification Panel** | Modal opened by clicking the bell, displays all historical task messages |

---

## Next Steps

- View task progress → [Task Management](12-task-management-en.md)
- View system logs → [Log Viewer](13-log-viewer-en.md)
