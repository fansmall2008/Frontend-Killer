# 12 · Media File Management

> This step adds images, videos, and other media to games. Scraping info (text) and media download are two separate things — this chapter is dedicated to media download.

**Entry**: sidebar **Media Download** 🖼️ (TOOLS group); you can also operate from the media download panel in Task Management.

---

## 12.1 What the Media Download Page Looks Like

The page mainly contains:

- **Top controls**: `⏹️ Stop all scraping`, `🔄 Refresh status`.
- **Status badge**: shows whether it's currently "idle / downloading / queued / stopped", etc.
- **Pause/Resume/Stop buttons**: appear during download — `⏸️ Pause`, `▶️ Resume`, `⏹️ Stop`.
- **Concurrent threads**: shows/sets how many threads download simultaneously.
- **Per-platform statistics**: each platform's media download situation — pending, downloading, done, failed, stopped counts and progress.

---

## 12.2 50 Media Types — Choose As Needed

ScreenScraper defines **50 media types**. When downloading you can choose which ones you want; common ones:

| Type | Meaning |
|---|---|
| `box-2D` | Flat box art (front/back/side) |
| `box-3D` | 3D box art |
| `ss` | Screenshot |
| `video` | Video |
| `wheel` | Game logo |
| `marquee` | Arcade marquee |
| `fanart` | Background art |
| `manuel` | Manual |

> Most players only need "box art + screenshots"; add video/logo if you want them. Checking too many significantly increases download volume and time. (Which to pick is a common dilemma — we've recorded it as a potential guidance point.)

---

## 12.3 Starting a Media Download

1. Go to the Media Download page.
2. Select the platforms/scope and media types to download.
3. Start downloading. The task enters the background queue (priority lower than user-initiated scraping).
4. The page shows progress per platform; you can also see it in Task Management.

---

## 12.4 Where It's Stored

Downloaded media files are stored at:

```
data/scraper/games/{systemId}/{gameId}/
```

(For local items not matched to a system, `local/{filename}/`.) The database records **relative paths** (starting with `./`, forward slashes). On export the software arranges these media into the export directory per template rules.

---

## 12.5 Concurrency, Rate Limiting & Protection Mechanisms

Media download has a robust set of protections; understanding them avoids "thinking it's stuck" misunderstandings:

- **Concurrency limit**: downloads **3** at a time by default (independent concurrency control); you can adjust concurrent threads on the page.
- **Rate-limit protection**: requests to ScreenScraper are rate-limited (on the order of ~20 per 10 seconds) to avoid triggering their limits.
- **404 circuit breaker**: consecutive not-found (404) responses trigger a short protective backoff (on the order of ~10 per 10 seconds), avoiding pointless hammering.
- **Deduplication**: identical media isn't downloaded twice.
- **Auto-retry**: failed items can be retried.

> So slow download speed and occasional pauses are mostly the protection mechanisms at work — that's normal. Don't repeatedly restart manually.

---

## 12.6 Controlling Downloads: Pause / Resume / Stop

- **Pause ⏸️**: temporarily stops; later **Resume ▶️** continues.
- **Stop ⏹️**: terminates the current download.
- **Stop all scraping**: top button, stops all running downloads/scrapes at once.
- **Refresh status 🔄**: manually refreshes each platform's download statistics.

Retrying individual failed tasks is done in **Task Management** (see `14`).

---

## 12.7 Viewing Progress Per Platform

The page lists media download statistics per platform; common status counts:

- **Pending**: queued, not yet downloaded.
- **Downloading**: currently downloading.
- **Done**: downloaded.
- **Failed**: unsuccessful (retryable).
- **Stopped**: was stopped.

The badge on the sidebar "Media Download" menu also shows the total pending count.

---

## 12.8 Typical Flow

1. Finish game info scraping first (see `11`).
2. Come to the Media Download page, select platform + media types (e.g., box art + screenshots).
3. Start downloading, watch per-platform progress.
4. Pause if you need to yield temporarily; after downloading, check failed items and retry in Task Management.
5. On export, check "Media files" to bring the media along (see `10`).

---

## 12.9 Next Steps

- Task progress & retry on failure → `14 Task Management`
- Fine-tuning a single game's media → `08 Game Editing`
