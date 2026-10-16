# 06 · Platform Management

> Platforms are the "skeleton" of the whole software — every game lives under some platform. This chapter covers how to create, edit, bind systems to, view statistics for, and batch-process platforms.

**Entry**: sidebar **Platform Management** 🎮 (DATA group)

---

## 6.1 What Is a Platform (once more)

A platform = **a set of games + a set of import/export rules + (optionally) a bound ScreenScraper system**.

Usually a platform corresponds to a type of console or a frontend directory, e.g. "Sega Saturn", "Pegasus-NES". The software creates platforms automatically during import; you can also create them manually.

---

## 6.2 What the Platform Management Page Looks Like

The top of the page has two view tabs:
- **Platform list** (default): a table of all platforms.
- **Game subsets**: manage temporary subsets separated out of platforms (details in `15 Temporary Subset Management`).

Main columns of the platform table: platform name, system, game count, scraped count, and data-quality grading (scraped / original data / low quality / raw ROM / missing games, marked in different colors), with action buttons on the far right.

---

## 6.3 Per-Platform Actions

Each platform row has three **icon buttons** on the far right:

| Icon | Name | Purpose |
|---|---|---|
| ✏️ | **Edit** | Opens the edit dialog to change platform name, path, **bind scraper system**, etc. |
| ✂️ | **Separate** | Filters a batch of games out of the platform by criteria to create a "new platform" or a "temporary subset" |
| 📊 | **Statistics** | Opens the statistics dialog to see the platform's detailed data distribution |

**Clicking the platform name/row**: navigates to that platform's **Game List** page (`/game-list?platformId=...`) to manage its games (details in `07`).

> 💡 Important: If this platform is **not yet bound to a scraper system**, clicking into its game list makes the software **automatically pop up the "system matching guide"** to help you bind (see 6.5).

---

## 6.4 Editing a Platform & Binding a Scraper System

Click ✏️ Edit; in the dialog you can change:

- **Platform name**, **system name**, **folder path**, and other basic info.
- **Bind scraper system**: the most critical item — pick a corresponding standard system from the ScreenScraper system list. Only after binding can the platform auto-scrape.

**Why binding is required**: ScreenScraper organizes data by standard system; without binding, the software doesn't know which console to look up. No binding = no auto-scraping.

The system list comes from the "Scraper Systems" page (sidebar 🔍); on first use you must click "Fetch system list" in System Settings to pull it down (see `02` Step 3, `11`).

---

## 6.5 System Matching Guide (★ an existing smart guide in the software)

When you open the game list of a platform **not bound to a system**, the software matches automatically:

1. **Auto-detect**: Entering the game list page and finding no bound system, it automatically pops up a matching dialog.
2. **Smart recommendation**: Based on the platform's **folder name, extensions, aliases**, etc., it computes the most likely ScreenScraper systems, listing up to 5 candidates, each annotated with a **match reason** (e.g., "extension match", "exact alias match").
3. **Your choices**:
   - **Click a candidate directly** → binds that system immediately.
   - **"None of these, I'll choose myself"** → expands a searchable all-systems dropdown to pick manually.
   - **"Not now"** → closes the dialog and won't bother you again this session (it will pop up next time you enter).
   - **"Remember this mapping" checkbox** → when checked, records "this folder name → this system" so same-named folders auto-match later without choosing again.
4. **After binding**: If the platform hasn't been scraped and the account is configured, the software may automatically start scraping for you.

> This is the most mature guide design in the software. If your desired system isn't among the candidates, just use "I'll choose myself".

---

## 6.6 Batch Operations on Platforms

**Check** one or more platforms in the platform list (each row has a checkbox; the header has "select all"), and a **batch operation toolbar** appears; buttons are gray when nothing is selected. Available batch operations:

| Button | Purpose |
|---|---|
| ✨ **Scrape** | Batch-start scraping for all selected platforms (a scrape settings dialog pops up; executes in turn after confirmation) |
| 🌐 **Translate** | Batch-translate game data under the selected platforms |
| 🔁 **Swap translations** | One-click swap of "original ↔ translation" for all games under the selected platforms (⚠️ has a confirmation prompt; large impact) |
| 📦 **Scan size** | Scan and count the ROM size/data distribution of the selected platforms |
| 🗑️ **Delete** | Delete the selected platforms (dangerous, be careful) |

The toolbar shows "N platforms selected" and offers "clear selection".

> ⚠️ Batch delete and batch swap translations are high-impact operations with confirmation dialogs — read carefully before clicking.

---

## 6.7 Separate (✂️): Split Out a New Platform or Temp Subset

Clicking a platform's ✂️ Separate button lets you filter out a portion of its games **by criteria**, then:

- **Create new platform**: Make the filtered games into a standalone new platform.
- **Create temp subset**: Make the filtered games into a "temporary subset" to handle separately (without breaking the original platform) — great for "pick a batch out to scrape/export first".

Filter criteria can include scrape status (scraped/not scraped), etc. When the filtered count is 0, the "Create" button is disabled.

> Temporary subsets are an advanced but very useful feature; details in `15 Temporary Subset Management`.

---

## 6.8 Statistics (📊) and Size Scanning

- **Statistics dialog**: See a single platform's data-quality distribution (how many scraped, low quality, raw-ROM-only, missing).
- **Scan size**: Batch-refresh platform size/statistics caches so the table numbers are more accurate.

Beyond the home page's "scrape grading bar", these help you see exactly how much each platform still needs.

---

## 6.9 A Typical Walkthrough

1. After import, come to Platform Management and see the new platform.
2. Click the platform name into the game list → if no system is bound, the matching guide pops up automatically → select a system to bind (optionally check "remember this mapping").
3. Back in Platform Management, check platforms → batch "Scrape", or enter a single platform and click Scrape.
4. Want to handle a portion of games separately → ✂️ Separate into a temp subset.
5. Need to switch the whole display language → batch "Swap translations".

---

## 6.10 Next Steps

- How to manage games after entering a platform → `07 Game List Management`
- How exactly to configure scraping → `11 Auto Scraping`
- Temp subset details → `15 Temporary Subset Management`
