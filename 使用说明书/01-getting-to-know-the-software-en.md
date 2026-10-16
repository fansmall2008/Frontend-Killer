# 01 · Getting to Know the Software

> This chapter helps you understand — before you start clicking — what this software is, what it can do for you, and which concepts you must grasp first. By the end you'll have a clear picture of the whole thing.

---

## 1.1 What Is This Software

**Frontend-Killer** (internal codename webGamelistOper) is a **web-based emulator game library management tool**.

In plain terms: if you play emulators (RetroArch, Pegasus, EmulationStation, RetroBat, etc.) and have a big pile of ROM game files, those games often lack nice box art, screenshots, descriptions, and release info. Frontend-Killer helps you organize this "game metadata" in one place, fill it in automatically, and export it for use by various emulator frontends.

It runs **in your browser** — the software itself runs a service in the background, and you operate it by opening a URL in a browser. No complex client to install.

### The core things it does for you

1. **Import**: Read in game data from your existing frontend (Pegasus, ES-DE, RetroBat, etc.), or simply scan in a pile of ROM files.
2. **Fill in metadata (Scraping)**: Connect to the international game database **ScreenScraper** to automatically look up each game's name, description, publisher, release date, genre, and rating, and download media files such as box art, screenshots, and videos.
3. **Edit & organize**: Manually correct data, batch-edit, translate, merge multi-disc games, and more.
4. **Export**: Export the organized data in the format (template) required by your target frontend, ready for your emulator to use.

---

## 1.2 Who It's For

- People with a batch of emulator ROMs who want their game list to become "rich with images and complete in information".
- People migrating a game library between different emulator frontends who need to convert data formats.
- People who want to batch-fill/translate game metadata without looking up each one by hand.

If you only want to play a single game and don't manage a library, this software won't mean much to you.

---

## 1.3 Concepts You Must Understand First

The following terms will appear repeatedly in later chapters. Just get familiar with them for now — no need to memorize; come back and look them up when needed. (The README also has a glossary.)

### Platform
A "platform" is **a set of games + a set of import/export rules**. It usually corresponds to an emulator frontend or a type of console. For example, you can create a "Nintendo FC" platform or a "Pegasus frontend" platform. Every game lives under some platform.

### System
A "system" is a **standard console/system defined on the ScreenScraper side**, such as PS1, PS2, Sega Saturn, MAME arcade, etc. Each system has a unique ID (systemId).

**Key point**: Your "platform" must be **bound** to a ScreenScraper "system" so the software knows which console to look up in the database. Without a bound system, automatic scraping won't work.

> Analogy: a platform is your own folder; a system is a standard category in the international database. Binding = telling the software "which category in the database does my folder correspond to".

### Scrape
"Scraping" is the process of **automatically fetching game metadata from ScreenScraper**. Note that since version 1.2, scraping is split into two independent steps:

- **Game info scraping**: fetches text data (name, description, publisher, genre, date, etc.).
- **Media file download**: fetches images/videos (box art, screenshots, videos, etc.).

These two steps **run separately, each with its own progress**, but share the same background task queue. Many first-time users assume one scrape gets everything — in fact you do them separately. (This is one of the potential guidance points we've recorded.)

### Media
Images, videos, manuals, and other assets linked to a game. ScreenScraper defines up to **50 media types** (such as `box-2D` flat box art, `ss` screenshot, `video`, `wheel` logo, `fanart` background, etc.). You can choose which ones you want when downloading.

### Dialect Mapping
Different databases/frontends name the same concept differently (e.g., for genre, some say "Beat-'Em-Up", others say a localized term). Dialect mapping is a "synonym lookup table" that helps you **normalize these names** into a unified form. There are two categories: `genre` and `system_alias`. You can invoke it in export templates with the `map()` function.

### M3U Merge
Some games are "multi-disc/multi-file" (e.g., several discs). "Merging" combines multiple single-file game entries **into one m3u playlist entry**, so the frontend treats them as one game. ⚠️ Note: merging deletes the original separate entries — the operation has a cost, so be careful.

### Theme
A switchable UI appearance, including colors, fonts, and even sound effects. Themes live under `data/themes/{theme-name}/`; the default theme is `default`. You can switch them in System Settings.

### Notification Center
The bell icon in the top-right. When background tasks (scraping, import, export, download) complete or fail, notifications are pushed automatically. Based on real-time push (SSE); each notification expires after about 5 minutes.

### Task Pool
All background work (scraping, downloading, import, export) is queued into a single unified queue for execution. There are priorities: **user-initiated scraping > media download > arcade auto-download**. You can view and control them on the "Task Management" page.

---

## 1.4 How the Software Works Overall

No need to remember technical details — understanding this main line is enough:

```
Your ROMs/data  →  Import into a platform  →  Bind platform to a ScreenScraper system
                                                        ↓
                                          Auto scrape (info + media)
                                                        ↓
                                    Manual edit / translate / merge (fix as needed)
                                                        ↓
                              Export by template for the target frontend to use
```

Later, `04 End-to-End Workflow` walks this line step by step.

---

## 1.5 Where Data Is Stored

- **Database**: The software uses an embedded small database (H2) to store all platform and game data; the file is under `data/database/`. You don't manage it manually.
- **Media files**: Downloaded box art, screenshots, etc. are stored under `data/scraper/games/`, in folders by system and game.
- **Templates/rules**: Import/export templates are in `data/rules/` (two subdirectories: `import/` and `export/`).
- **Themes**: In `data/themes/`.
- **Logs**: Runtime logs are in `logs/application.log`.
- **Backups**: Database backups default to `data/backup/`.

> Tip: Regularly use the backup feature in "System Settings" to back up the database, especially before big operations (merge, initialize).

---

## 1.6 Version & Roadmap (FYI only)

- **Current version**: 1.2
- Main changes in 1.2: scraping split into "info scraping + media download" two steps; unified task pool; adjusted media storage paths; v2 templates fully deprecated in favor of the **v3 template system**.
- **Future plans** (roadmap, for reference only):
  - 1.3: disc-based game scraping rework, **brand-new guidance features**, multi-threading optimization.
  - 1.4: smart system matching and auto-binding, multi-file game (DOS, multi-disc) support.

> Note: This manual is written for version 1.2. If you use another version, some screens may differ slightly.

---

## 1.7 Next Steps

- Not installed yet? → See `02 Quick Start`
- Already able to open it? → See `03 Interface & Navigation` to get to know each page
- Want to jump straight into the full workflow? → See `04 End-to-End Workflow`
