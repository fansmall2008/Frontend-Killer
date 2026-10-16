# 04 · End-to-End Workflow

> This chapter doesn't cover individual feature details; instead it walks you **through the complete pipeline from start to finish**, so you understand "how these features are strung together". Each stage points to its detailed chapter — jump over when you need depth.

---

## 4.1 The Main Line at a Glance

```
 ① Prepare        ② Import          ③ Bind system        ④ Scrape info
 ROMs/data   →   into platform  →  platform↔ScreenScraper → fill in text data
                                                              ↓
 ⑦ Export          ⑥ Edit & organize     ⑤ Download media
 to frontend  ←   fix/translate/merge ←   fill in images/videos
```

Seven stages:
1. **Prepare**: Get your ROMs or existing frontend data files ready.
2. **Import**: Read the data into the software, forming "platform + game list".
3. **Bind system**: Tell the software which standard ScreenScraper system this platform corresponds to.
4. **Scrape info**: Automatically fill in each game's name, description, publisher, genre, date, and other text data.
5. **Download media**: Automatically download box art, screenshots, videos, and other images/videos.
6. **Edit & organize**: Manually fix errors, translate, merge multi-disc games, batch-adjust.
7. **Export**: Export in the format required by the target frontend, ready for the emulator.

Let's go through each stage.

---

## ① Prepare: Have ROMs or Data First

You need one of the following (or both):

- **A batch of ROM files**: placed in some directory (in Docker deployments, usually the mounted `roms` directory).
- **Existing frontend data files**: e.g., game list files you previously generated with Pegasus / ES-DE / RetroBat (`metadata.pegasus.txt`, `gamelist.xml`, etc.) and media folders.

Also confirm: your ScreenScraper account is configured in **System Settings** (otherwise stages ④⑤ can't auto-scrape). → See Step 3 of `02 Quick Start`.

---

## ② Import: Read the Data In

**Entry**: sidebar **Data Import** 📥 → details in `09 Data Import`

Two typical import methods:

- **Template import**: You already have frontend data files; pick the matching template (Pegasus / ES-DE / RetroBat / EmuELEC, etc.). The software parses the files per template rules and reads in games and media paths.
- **No-data-file import (scan ROMs)**: You only have ROMs, no data files; scan the ROM directory directly and the software creates a game entry for each ROM (data left empty for now, to be filled by scraping).

During import it may:
- Pop up a **template variable** input box (e.g., ROM folder name, URL prefix) — fill it per the instructions.
- Trigger **media discovery** (mediaDiscovery), automatically matching existing images/videos on disk to games per rules.

**Output**: New "platform" and its "games" are added to the database. Go to **Platform Management** 🎮 to see the new platform.

---

## ③ Bind System: Match Platform to ScreenScraper System

**Entry**: **Platform Management** → edit platform → select scraper system; or open the platform's game list to trigger the guide → details in `06 Platform Management`

**Why it's required**: ScreenScraper organizes data by "standard system" (PS1, Saturn, MAME…). Your platform must **bind** to a system so the software knows which console to look up in the database. **No binding = no auto-scraping.**

**The convenient part**: When you open the game list of a platform **not yet bound to a system**, the software **automatically pops up the "system matching guide"**, intelligently recommending the most likely systems based on the platform folder name, extensions, etc. One click binds it; you can also "choose myself" or "not now". After binding you can check "remember this mapping" so the same-named folder auto-matches next time.

---

## ④ Scrape Info: Fill in Text Data

**Entry**: Platform Management → the platform row's "Scrape" button; or batch scrape in the game list → details in `11 Auto Scraping`

Scraping connects to ScreenScraper and looks up, for each game: name, description, publisher, developer, release date, genre, players, rating, and other **text data**.

**Key point (a common pitfall)**: Since 1.2, "scraping" is split into **two independent steps** —
- This step only scrapes **info (text)**.
- Images/videos are the **separate next step, "media download"**.

So finding "no box art" after scraping info is normal — media must be downloaded separately (see ⑤).

The scraping task enters the background queue; check progress in **Task Management** 📋; when done, the top-right **notification bell** alerts you.

---

## ⑤ Download Media: Fill in Images & Videos

**Entry**: sidebar **Media Download** 🖼️, or the media download panel in Task Management → details in `12 Media File Management`

This step downloads **media files** such as box art (box-2D), screenshots (ss), videos (video), logos (wheel), backgrounds (fanart). ScreenScraper has **50 media types**; you can select just the few you need (commonly: box art + screenshots).

Media files download to `data/scraper/games/{system}/{game}/`; the database records relative paths.

**Notes**:
- Media download has a concurrency limit (default 3) and rate-limit protection; slow speed is normal — be patient.
- On 404 or rate limits, the software automatically backs off and retries.
- Download tasks can likewise be paused/resumed/stopped/retried in **Task Management**.

---

## ⑥ Edit & Organize: Fix As Needed

**Entry**: Platform Management → click platform → **Game List** → click game → **Game Editing** → details in `07 Game List Management`, `08 Game Editing`

Scraped data isn't necessarily 100% accurate; this step is for correcting and enhancing:

- **Single edit**: Enter the game editing page to change name/description/genre, etc., across 4 tabs (Basic Info / Metadata / Media Management / Media Preview). For games auto-scraping didn't match, use "Search game" to find them manually from ScreenScraper.
- **Batch operations**: Select multiple games in the game list to batch-edit fields, batch-translate, batch re-scrape, swap translations, M3U merge (multi-disc into m3u), etc.
- **Status at a glance**: The game list marks data completeness in red/yellow/green (red = no info, yellow = partially missing, green = complete), so you can prioritize the red and yellow ones.
- **Translation**: You can translate data into a target language (see `17`).

> ⚠️ M3U merge deletes the original separate entries and may affect ROMset integrity — think it through before doing it.

---

## ⑦ Export: For the Frontend to Use

**Entry**: sidebar **Data Export** 📤 → details in `10 Data Export`

Once organized, export the data into a format the target frontend can use:

1. Select the platform(s) to export (single or batch).
2. Choose the target frontend template (Pegasus / ES-DE / RetroBat, etc.).
3. Check what to export: **game files (ROMs) / media files / data files**.
4. Specify the output directory.
5. Before exporting, the software runs a **preflight** check and gives suggestions (export directly / split / suggest whole directory) — handle per the prompt.
6. Click export; the task enters the queue; check progress in **Task Management**.

**Output**: The output directory gets data files (e.g., `gamelist.xml`, `metadata.pegasus.txt`) plus organized ROMs and media, ready to copy to your frontend.

---

## 4.2 Two "Dashboards" That Span the Whole Process

No matter which stage you're at, these two places keep you in control of the big picture:

- **Home dashboard** 🏠 (details in `05`): total platforms, total games, scrape coverage, scrape grading (scraped/original data/low quality/raw ROM), system status, running tasks.
- **Task Management** 📋 (details in `14`): status and progress of all background tasks; you can stop, cancel, delete, retry, and control media download.

---

## 4.3 A Complete Example (Strung Together)

Suppose you have a batch of Sega Saturn ROMs and want to make an illustrated game library usable by ES-DE:

1. **Prepare**: Put ROMs in `roms/saturn/`, configure the ScreenScraper account.
2. **Import**: Data Import → choose "scan ROM directory" or the ES-DE template → generate a platform.
3. **Bind**: Open the platform's game list → matching guide pops up automatically → select "Sega Saturn" to bind (optionally check "remember this mapping").
4. **Scrape info**: Platform Management → that platform → Scrape → Start, wait for the task to finish.
5. **Download media**: Media Download → select box art + screenshots → start downloading.
6. **Edit**: In the game list, handle the red/yellow ones; manually search the few unmatched; translate if you want Chinese.
7. **Export**: Data Export → select that platform → ES-DE template → check ROMs + media + data → specify directory → export.
8. Copy the output directory to ES-DE, and on boot you'll see an illustrated game list.

---

## 4.4 Next Steps

With the main line clear, you can now dive into each feature's detailed chapter (`05`~`19`) as needed. When building import/export templates, be sure to read `18 Template System Guide`.
