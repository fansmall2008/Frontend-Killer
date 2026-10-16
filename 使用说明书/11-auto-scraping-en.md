# 11 · Auto Scraping

> Scraping is the software's core capability — automatically pulling game data from ScreenScraper. This chapter covers how to start scraping, what to configure, why it's sometimes slow or fails, and how to use the "Scraper Systems" page.

**Entry**:
- Start scraping: the platform's Scrape button in **Platform Management**, or "Batch scrape" in the **Game List**.
- Manage the system list: sidebar **Scraper Systems** 🔍 (TOOLS group).

---

## 11.1 Three Prerequisites Before Scraping

For scraping to run, you must first satisfy:

1. **Configure a ScreenScraper account** (enter username/password in System Settings and "Test connection"). Without it you're in guest mode with a very low quota.
2. **Fetch the system list** (click "Fetch system list" in System Settings to pull the scrapable standard systems into the local database).
3. **Platform bound to a system** (see `06`). Without binding, it doesn't know which console to look up.

> The home page's "SCRAPER NOT READY" warning tells you exactly what's missing; click "Go to configuration".

---

## 11.2 Scraping Is Split into Two Steps (must understand)

Since version 1.2, "scraping" is **two independent steps** sharing one background task pool:

| Step | What it scrapes | Where |
|---|---|---|
| **① Game info scraping** | Text data: name, description, publisher, developer, date, genre, players, rating | Platform/Game List "Scrape" |
| **② Media file download** | Images/videos: box art, screenshots, videos, logos, etc. | Sidebar "Media Download" (see `12`) |

**Key**: Doing ① doesn't automatically give you images! Images require doing ② separately. Many first-time users are confused by "why no box art" — this is why.

---

## 11.3 Starting Game Info Scraping

### Single-platform scrape
1. Platform Management → find the platform → click the Scrape button to open the scrape settings dialog.
2. In the dialog confirm:
   - **Region**: which region's data to prefer (e.g., EU/US/JP).
   - **Media type scope** (if this step involves media).
3. Click "Start scraping".

### Batch scrape
- In Platform Management, check multiple platforms → batch "Scrape".
- Or in the Game List, check multiple games → "Batch scrape".

The scraping task enters the background queue; watch progress in **Task Management** 📋; when done the **notification bell** alerts you.

---

## 11.4 Task Queue & Priority

All background work is queued into a unified task pool with priorities:

**User-initiated scraping > media download > arcade auto-download**

That is, your manually started scraping runs first. Thread count dynamically follows the maximum ScreenScraper allows — you don't manage it manually.

---

## 11.5 Quota, Rate Limiting & Retry on Failure

ScreenScraper has quotas and rate limits on requests; the software has protections for this:

- **Quota**: guests get about 20,000 requests/day; logged-in accounts get a higher quota.
- **Rate limiting**: too-frequent requests get rate-limited (returning 429/430/431, etc.). The software **automatically backs off and waits** before retrying; a task slowing down or pausing then is **normal protection**, not a hang.
- **Retry on failure**: individual games that fail to scrape (no match, timeout) are recorded; you can retry them in Task Management, or manually "Search game" on the game editing page (see `08`).

> On rate limits, don't keep hammering — let the software back off automatically.

---

## 11.6 The Scraper Systems Page (🔍)

The sidebar "Scraper Systems" manages the ScreenScraper standard system list:

- **System list**: shows all scrapable systems (consoles/handhelds/arcade), including name, ID, aliases, etc.
- **Edit system**: click into a system to view/change its info (e.g., alias pool, which affects smart matching).
- This list is what platforms can pick when "binding a system", and is the source of the system matching guide's recommendations.

On first use, remember to "Fetch system list" in System Settings to populate it.

---

## 11.7 How Matching Works (FYI)

When scraping, the software must match your local games to ScreenScraper entries. It uses:
- Filenames, CRC/hash, and other identifiers;
- The smart algorithm in the system matching guide (folder name, extensions, alias pool, edit distance, etc., scored).

For games that don't match, use "Search game" on the game editing page to specify manually (see `08`).

---

## 11.8 Typical Flow

1. Configure the account in System Settings + fetch the system list.
2. Bind the platform to a system (or use the matching guide).
3. Platform Management → Scrape → choose region → Start (fills text data).
4. Go to Media Download to fill images/videos (see `12`).
5. Watch progress in Task Management; retry failures; manually search the few that don't match.

---

## 11.9 Next Steps

- Fill images/videos → `12 Media File Management`
- Task progress & retry → `14 Task Management`
- Manual fallback → `08 Game Editing`
