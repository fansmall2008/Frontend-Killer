# Quick Start

> Minimum steps from zero to export — ideal for first-time users.

---

## Prerequisites

1. **Java 17+** installed (or Docker environment)
2. **ROM files** ready (any directory structure)
3. (Recommended) A [ScreenScraper](https://www.screenscraper.fr/) account for higher API quotas

---

## Step 1: Launch the Application

### JAR

```bash
java -jar webGamelistOper-1.1-RC1.jar
```

### Docker

```bash
docker compose up -d
```

After startup, open **http://localhost:8080** in your browser. Seeing 5 feature cards on the home page means the app is running.

---

## Step 2: Configure ScreenScraper (Optional but Recommended)

1. Click the **"System Settings & Data"** card on the home page → **"System Settings"**
2. Enter your ScreenScraper **username** and **password**
3. Click **"Save Settings"**
4. Click **"Test Connection"** to verify
5. Click **"Get System List"** to initialize scraper system data

> 💡 You can skip this step, but the app will run in guest mode with a daily limit of ~200 requests.

---

## Step 3: Import ROM Data

1. Click the **"Import"** card on the home page
2. **Scan Path**: Enter or browse to your ROM directory
3. **Scan Depth**: Choose "All" or specify levels
4. **Select System** (optional): Pick the matching scraper system to auto-associate file extensions
5. **Import Template**: Choose a template matching your ROM directory structure
6. Check **"Match unrecorded media files"** (recommended)
7. Click **"Start Scan & Import"**

> After import completes, the page shows import result statistics.

---

## Step 4: Create a Platform & Scrape

1. Go back to the home page → click the **"Data Processing"** card (Platform Management)
2. Click **"Add Platform"**, fill in the platform name, path, and other basics
3. Find the new platform in the list, click **"Edit"**
4. In the edit dialog, **select a scraper system** (matching your game console)
5. After saving, click the **"Scrape"** button on the platform row
6. Confirm settings in the scrape dialog, click **"Start Scrape"**

> Scraping runs in the background. Check progress on the **"Task Management"** page.

---

## Step 5: Edit Games (Optional)

1. In Platform Management, click a platform name to enter the **Game List**
2. Click any game row to enter the **Game Edit** page
3. Modify name, description, translations, etc.
4. Click **"Search Game"** to look up supplementary data from ScreenScraper
5. Click **"Save Changes"** when done

---

## Step 6: Export Data

1. Go back to the home page → click the **"Export"** card
2. **Select Platform**: Choose the platform to export
3. **Select Frontend**: Choose the target frontend format (e.g., Pegasus, ES-DE)
4. **Export Scope**: Check what you need (game files / media files / data files)
5. **Export Path**: Specify the output directory
6. Click **"Export"**

> After export, check the output directory for the generated gamelist.xml and media files.

---

## Minimum Flow Cheat Sheet

```
Launch JAR → Configure SS Account → Import ROMs → Add Platform → Scrape → (Edit) → Export
```

| Step | Page | Key Button |
|------|------|------------|
| Launch | — | `java -jar` |
| Configure | System Settings | Save Settings / Test Connection / Get System List |
| Import | Data Import | Start Scan & Import |
| Create Platform | Platform Mgmt | Add Platform → Edit → Select Scraper System |
| Scrape | Platform Mgmt | Scrape button |
| Edit | Game Edit | Save Changes / Search Game |
| Export | Data Export | Export |

---

## Next Steps

- Learn detailed features of each page → [Home Page](02-home-page-en.md)
- Understand the full data processing pipeline → [Overview](00-overview-en.md)
- Running into issues? → [FAQ](15-faq-en.md)
