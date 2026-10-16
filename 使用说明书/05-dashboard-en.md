# 05 · Dashboard

> The home page is the first thing you see when you open the software — like a "cockpit" that shows the state of your whole game library and what the background is doing at a glance. This chapter explains what each block means.

**Entry**: sidebar **Home** 🏠 (or click 🏠 in the top breadcrumb)

---

## 5.1 Overall Structure

From top to bottom: welcome banner → 4 stat cards → scrape grading bar → system status → running tasks → footer.

---

## 5.2 Welcome Banner (Hero)

A large banner at the top reads "Welcome back, COMMANDER", with three core numbers below:

- **Platforms**: how many platforms you've created.
- **Games**: total games across all platforms.
- **Scraped**: percentage of games that have completed scraping.

> Tip: On startup, if your browser supports it, there may be a voice greeting ("Welcome back, Commander") — this is part of the theme sound effects and is normal.

---

## 5.3 Four Stat Cards

Below the banner are 4 cards with finer numbers:

| Card | Icon | Meaning |
|---|---|---|
| **Platforms** | 🎮 | Total platforms |
| **Games** | 🕹️ | Total games |
| **Scraped** | ✅ | Scraped game count + coverage percentage |
| **Tasks** | ⏳ | Current "running + pending" task count, noting how many are running |

---

## 5.4 Scrape Grading Bar (key)

This is a **stacked color bar** that visually shows the distribution of your library's "data completeness". Four colors represent four grades:

| Color | Grade | Meaning |
|---|---|---|
| 🟢 Green | **Scraped** | Complete data, scraping succeeded |
| 🔵 Blue | **Original data** | Has imported original data (e.g., brought over from an old frontend) |
| 🟡 Yellow | **Low quality** | Incomplete/poor data; recommend re-scraping or manual fill |
| 🔴 Red | **Raw ROM** | Only a ROM file, almost no data; most in need of attention |

**How to use it**: The more red and yellow, the more games still await completion. Hover over each segment to see exact counts and percentages. It's a great basis for deciding "what to scrape next".

> If the bar shows "no data", your library has no games yet — go import first (see `09`).

---

## 5.5 System Status

Two small cards showing the health of key subsystems:

### Scraper
- **Status badge**: online (green, account configured) / offline (red, account not configured).
- **Thread quota**: currently available scraping threads.
- **Account status**: configured / not configured.

> If this shows "not configured/offline", the ScreenScraper account isn't set up and scraping is unavailable. Configure it in **System Settings** (see `02` Step 3, `17`).

### Media Downloads
- **Status badge**: in progress (green) / queued (yellow) / idle (gray).
- **Pending**: number of media queued for download.
- **In progress**: number currently downloading.

---

## 5.6 Running Tasks

Lists background tasks **currently running**; each task shows:
- Task description/type
- Progress bar + percentage
- An ✕ stop button (clicking asks for confirmation, then stops the task)

If there are none, it shows "no running tasks". To see all tasks (including history and failures), go to **Task Management** 📋 (see `14`).

---

## 5.7 Startup Check Warning Popup

When you enter the home page, the software automatically runs an **environment self-check** on two things:
1. Whether the ScreenScraper account is configured.
2. Whether the database has scraper system data.

If either isn't ready, it pops a yellow **"SCRAPER NOT READY"** warning, clearly telling you what's missing with red/green items, and offering a "Go to configuration" button straight to System Settings. If both are ready, no popup.

> This is a very useful guide: newly installed users are often led right to where they need to configure.

---

## 5.8 Footer

Shows copyright info and the current version number (e.g., v1.2).

---

## 5.9 Questions the Home Page Answers

- How many games/platforms do I manage in total? → Banner and stat cards.
- Is my library's data complete? How much is left? → Scrape grading bar (lots of red/yellow = lots to fill).
- Can scraping be used? Is the account configured? → "Scraper" in system status.
- What's the background busy with now? → Running tasks.

---

## 5.10 Next Steps

- Create/manage platforms → `06 Platform Management`
- Full main line → revisit `04 End-to-End Workflow`
