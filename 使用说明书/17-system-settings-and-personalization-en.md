# 17 · System Settings & Personalization

> System Settings is the software's "control panel": language, theme, sound effects, scraping account, caching strategy, list appearance, and most importantly — database backup / restore / initialize all live here. This chapter explains each block, flagging the irreversible dangerous operations.

**Entry**: sidebar **System Settings** ⚙️ (SYSTEM group)

---

## 17.1 Page Overview

The System Settings page is divided from top to bottom into blocks:

1. Language settings
2. 🎨 Theme pack settings
3. 🔊 Sound effect settings
4. ScreenScraper settings
5. Game info caching
6. Game list display settings
7. Database backup ⚠️ (includes the dangerous initialize operation)

Each block is explained below.

---

## 17.2 Language Settings

- **Choose language**: Chinese / English / Japanese (three choices).
- After switching, **you must refresh the page for it to take effect** (the page hints at this).
- Site-wide text uses a unified i18n mechanism, covering all three languages.

> You can also quickly switch language from the top bar (see `03`).

---

## 17.3 Theme Pack Settings 🎨

- The page shows available theme packs as a **card grid**; click to switch.
- A theme pack changes the interface's **colors, fonts, and sound effects** together.
- Theme files live in the `data/themes/` directory (you can put custom themes there).

---

## 17.4 Sound Effect Settings 🔊

- **Enable UI sound effects**: master switch.
- **Master volume**: slider 0 ~ 100, default 50%.
- **Per-event sounds**: an on/off for each event; **click to preview**.
- Sound effects are synthesized by the **Web Audio API** by default — no audio files needed; custom themes can place mp3/wav in the theme directory.

---

## 17.5 ScreenScraper Settings (scraping account)

This is the **key prerequisite** for the scraping feature to work (see `11`):

| Control | Description |
|---|---|
| **Username / Password** | Your ScreenScraper account |
| **Save settings** | Save the account |
| **Test connection** | Verify the account works |
| **Fetch system list** | Pull ScreenScraper's system list (needed for binding a platform to a system) |

> **Guest mode**: you can use it without an account, but there's a limit of about **20000** requests per day, easy to hit. For stable scraping and a higher limit, register a free account at [ScreenScraper.fr](https://www.screenscraper.fr/) and fill it in here.
>
> The home page has an environment self-check: when the account or system data is missing, it pops a "scraper not ready" warning and guides you here to configure (see `05`).

---

## 17.6 Game Info Caching (arcade only)

- The **Auto-cache game info** checkbox is **off by default**.
- When enabled: successfully scraped **arcade** games cache the full info returned by ScreenScraper locally, so export can **automatically find parent ROMs** (clone dependencies) based on it.
- ⚠️ Cost: it **uses up part of your request quota**. Only recommended if you play arcade and need to handle parent/child ROM relationships.

> The first time the software runs it pops a dialog asking whether to enable this (see `03`, "Guidance Node Inventory" E3/N23); if you didn't think it through then, you can change it here later.

---

## 17.7 Game List Display Settings

- **Game list icon field**: decides which image each row in the game list shows. There are many options (~20); common ones:

  | Option | Meaning |
  |---|---|
  | Cover image (image) | Default cover |
  | Wheel icon (wheel / wheel-carbon / wheel-steel) | Game logo |
  | Box (box-2D / box-2D-back / box-3D / box-2D-side) | Box front/back/3D/side |
  | Screenshot (ss) / Title screen (sstitle) | Game screen |
  | Banner / poster / fanart | Promotional art |
  | Others | logo, thumbnail, bezel, panel, steam, steamgrid, etc. |

- After setting, **you must refresh the page for it to take effect**.
- ⚠️ Note: if a game **has no media of the selected type**, the icon slot will show **blank**. So if you pick wheel but never downloaded wheels, it'll be all empty.

---

## 17.8 Database Backup ⚠️ (key section, includes dangerous operations)

This block manages your data safety; the three buttons have very different consequences:

| Button | Function | Risk |
|---|---|---|
| **Back up database** | Package the current data as a backup (select/manage in the backup list below) | 🟢 safe, recommended often |
| **Restore database** | **Overwrite** the current data with a backup (deletes existing tables then imports) | 🟠 high — current data is replaced |
| **Run initialize** | **Rebuild the table structure and clear all data** | 🔴 very high — **irreversible**, all data gone |

The page states clearly:

> Running database initialization will rebuild the table structure and clear all data. **This operation is irreversible — proceed with caution!**

**Recommendations**:
- Before any risky big operation (platform merge, bulk delete, restore, initialize), **click "Back up database" first**.
- Think before **restoring**: it overwrites the current data with the backup; changes newer than the backup will be lost.
- **Initialize** is basically a "factory reset" — only use it when you truly want to clear and start over, and **always back up first**.

> These three operations (especially initialize/restore) are recorded high-priority guidance points ("Guidance Node Inventory" N20): in the future we may strengthen confirmation (e.g., typing a confirmation word) to prevent misclicks.

---

## 17.9 Typical Uses

- **First time**: fill in the ScreenScraper account → test connection → fetch system list (scraping prerequisite).
- **Want a change of mood**: switch theme pack / adjust sounds / change the list icon field.
- **Before a big move**: back up the database first.
- **Messed up and want to roll back**: restore from the backup list (confirm the backup is the version you want first).
- **Multilingual users**: switch language → refresh.

---

## 17.10 Next Steps

- After configuring the scraping account, how to scrape → `11 Auto Scraping`
- How backup/restore pairs with merging → `13 Platform Merge`
- How to download the media types for list icons → `12 Media File Management`
