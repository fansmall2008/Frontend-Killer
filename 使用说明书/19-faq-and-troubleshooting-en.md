# 19 · FAQ & Troubleshooting

> Look here first when you hit a problem. This chapter is organized by "the symptom you see", gives possible causes and fixes, and teaches you how to locate issues with logs and how to provide useful information when asking for help.

---

## 19.1 Quick Index (find by symptom)

| Symptom | See section |
|---|---|
| Page won't open / can't access | 19.2 |
| Scraping fails, no info scraped | 19.3 |
| Media download very slow / keeps pausing / stuck | 19.4 |
| Quota exhausted / 429 / 401 prompts | 19.5 |
| Import does nothing, can't find files | 19.6 |
| Export path wrong, can't export | 19.7 |
| Platform can't scrape, no system | 19.8 |
| Task stuck, cancel won't stop | 19.9 |
| Data accidentally deleted / messed up, want to roll back | 19.10 |
| Language/theme/icon didn't take effect | 19.11 |
| How to locate problems with logs | 19.12 |
| What to provide when asking for help | 19.13 |

---

## 19.2 Page Won't Open / Can't Access

**Symptom**: browsing to `http://localhost:8080` (or your deployment address) won't open, white screen, connection refused.

**Troubleshooting**:
1. **Is the software running**: confirm the jar/exe/Docker container has started and the console didn't error out.
2. **Port**: default `8080`. If occupied by another program, change `server.port` in `application.properties`, or close the program using the port.
3. **Is the address right**: on the local machine use `localhost`; for Docker/NAS deployments use the **host IP + mapped port**, not localhost.
4. **Directory permissions**: the `data`, `output`, `logs`, `backup` directories must be readable/writable (a common NAS/container pitfall).
5. Check the startup log for phrases like "Cannot connect to database", "Address already in use" (see 19.12).

---

## 19.3 Scraping Fails, No Info Scraped

**Symptom**: after starting a scrape, games got no info, or the task turned red FAILED.

**Common causes & remedies**:

| Cause | How to tell | Fix |
|---|---|---|
| **Platform not bound to a system** | Platform systemId is empty | Entering the game list pops the system-matching guide, or go to Platform Management ✏️ edit to bind (see `06`, 19.8) |
| **Scraping account not configured** | Home page pops "scraper not ready" | Fill in the ScreenScraper account in System Settings → test connection → fetch system list (see `17`) |
| **Game name too messy to match** | Name contains lots of tags/numbers | Regularize the game name first, or use manual search in Game Editing to specify (see `08`) |
| **ScreenScraper doesn't have it** | Obscure/homebrew game | Normal — fill in the info manually |
| **Network/rate limit** | Logs show Timeout, Rate limit | Retry later, see 19.5 |

> Scraping is "info (text)"; media (images/videos) is a **separate step** — go to the Media Download page to download it separately (see `12`). Scraping info doesn't mean you have images.

---

## 19.4 Media Download Very Slow / Keeps Pausing / Stuck

**Symptom**: media download progress moves slowly, pauses now and then, feels stuck.

**Mostly the protection mechanisms at work — this is normal**:
- **Concurrency is 3 by default**; it won't download at full throttle.
- **Rate-limit protection**: requests to ScreenScraper are throttled (on the order of ~20 per 10 seconds).
- **404 circuit breaker**: consecutive not-found resources trigger a short backoff (on the order of ~10 per 10 seconds).
- **Deduplication**: already-downloaded items aren't re-downloaded, so later ones "seem not to move" — actually they were skipped.

**Remedies**:
- Don't repeatedly restart manually; let it run on its own.
- You can adjust **concurrent threads** on the Media Download page to speed up somewhat (but too high easily triggers rate limiting, backfiring).
- Truly failed items → retry in **Task Management** (see `14`).
- Want to temporarily yield to other tasks → **pause** in the panel, **resume** later.

---

## 19.5 Quota Exhausted / 429 / 401 Prompts

ScreenScraper uses status codes to tell you what happened:

| Status code | Meaning | What to do |
|---|---|---|
| **401 / auth failed** | Wrong account or password, not logged in | Re-enter the correct account in System Settings → test connection (see `17`) |
| **429 / rate limit** | Requests too frequent, hit the limit | **Pause the task, wait a while**, then resume; lower concurrency |
| **Guest quota exhausted** | No account configured, ~20000/day hit | Register a free account and fill it in to raise the limit (see `17`, N27) |
| **5xx / server error** | A problem on ScreenScraper's side | Not your fault; retry after a while |

> For rate limiting, the three-step combo is "wait + lower concurrency + retry". Pushing hard just keeps getting blocked.

---

## 19.6 Import Does Nothing, Can't Find Files

**Symptom**: clicked import, no games scanned, or a "file not found" prompt.

**Troubleshooting**:
1. **Is the scan level right**: the Data Import page has level 1~4 checkboxes and **only scans the checked level**, without drilling into subfolders automatically. If ROMs are placed deep, check the corresponding level (see `09`).
2. **Is the ROM directory right**: confirm there really are files under `roms/` (or your specified path).
3. **Import method**: template method vs no-data-file import — choosing wrong means it can't read (see `09`).
4. **Media not associated**: to have import bring along media files with no records, check "match unrecorded media files" (mediaDiscovery).
5. Check Task Management and logs for errors.

---

## 19.7 Export Path Wrong, Can't Export

**Symptom**: can't find files after export, or a path problem prompt.

**Troubleshooting**:
1. **Export path**: default `/data/output` (Docker) or your configured outputPath. Confirm this directory **exists and is writable**.
2. **Platform folder path**: merge/export often require the platform to have a folder path; if missing, the preflight will hint at it (see `10`, `13`).
3. **Preflight hints**: before export it checks and may offer proceed / split / suggest_whole_dir options — follow the hints.
4. **Export scope**: which of "game files / media files / data files" did you check? If media isn't checked, no images are brought along (see `10`).
5. **Frontend template restrictions**: some frontends restrict fields/structure; the export page shows yellow hints.

---

## 19.8 Platform Can't Scrape, No System

**Symptom**: platform can't scrape, or entering the game list immediately pops a box to choose a system.

**Explanation**: this is the **system-matching guide** (existing mechanism E1) — it pops automatically when a platform isn't bound to a ScreenScraper system, letting you choose from candidates.

**Remedies**:
- Choose one from the candidates, or click "choose myself" to pick manually, or "remember this mapping" to auto-match next time.
- If you don't want to deal with it now, click "don't bind for now" (won't pop again this session).
- You can also bind the system manually in Platform Management ✏️ edit (see `06`).
- **Prerequisite**: you've already "fetched the system list" in System Settings, otherwise there are no candidates (see `17`).

---

## 19.9 Task Stuck, Cancel Won't Stop

**Symptom**: task stays RUNNING; you clicked cancel but it's still running.

**Explanation**: currently "cancel" **marks the task as cancelled**; a long-running loop doesn't necessarily stop immediately (it needs checkpoints in the loop to truly interrupt), so it may run a bit longer after cancelling.

**Remedies**:
- Wait a moment to see if the status turns CANCELLED.
- For media downloads use the panel's **Stop** button.
- After an abnormal shutdown and restart, the software does **task recovery**: marks leftover RUNNING as FAILED, resets DOWNLOADING to PENDING (see `14`). Seeing tasks turn red / return to the queue after restart is normal — just retry.

---

## 19.10 Data Accidentally Deleted / Messed Up, Want to Roll Back

**Symptom**: bulk-deleted wrong, merge got tangled, initialize cleared the data.

**Remedies**:
- **Have a backup**: System Settings → database backup area → pick a backup and **restore** (it overwrites current data with the backup, see `17`).
- **No backup**: initialize/delete are **irreversible** and very hard to recover. This is exactly why we repeatedly stress — **back up before big operations** (before merge, bulk delete, restore, initialize).
- Build the habit: before touching big data each time, click "Back up database" first.

> ⚠️ "Run initialize" rebuilds the table structure and clears all data, irreversibly. Unless you truly want to clear and start over, don't click it (see `17`, N20).

---

## 19.11 Language / Theme / Icon Didn't Take Effect

| Symptom | Cause | Fix |
|---|---|---|
| Switched language, no change | Language needs a page refresh to take effect | Refresh the page (see `17`) |
| Switched the list icon field, all blank | That game has no media of the selected type | Download that media type first, or switch back to image (see `17`, N26) |
| Theme didn't change | Didn't click the theme card / needs refresh | Re-select the theme pack, refresh |

---

## 19.12 How to Locate Problems with Logs

Logs are the first-hand clue for troubleshooting (details in `16`):

1. Open the **Log Viewer** 📊, or look at `logs/application.log`.
2. Prioritize finding **ERROR / WARN** lines.
3. Common keywords: `ERROR`, `Timeout`, `Connection refused`, `Rate limit`, `Authentication failed`, `Cannot connect to database`, `expression evaluation failed`.
4. Locate the erroring lines, then scroll up through INFO to see what operation it was doing before.
5. The page has no built-in search — use the browser's Ctrl+F or open the log file directly to search.

---

## 19.13 What to Provide When Asking for Help

When reporting a problem to others (community/issue), including these greatly speeds up diagnosis:

- **What operation you did**: e.g., "started a scrape on platform XX", "exported to /data/output".
- **The symptom/error text you saw**: copy it verbatim; don't just say "it failed".
- **Relevant log excerpt**: a few lines around the ERROR + the time.
- **Environment**: deployment method (jar/exe/Docker/NAS), version (v1.2), operating system.
- **Whether a ScreenScraper account is configured** (when scraping is involved).

> A bare "scraping is broken" is hard to help with; "scraping the PS2 platform, task FAILED, log reports 429 Rate limit" pinpoints it immediately.

---

## 19.14 Still Not Working?

- Go back to the relevant feature chapter and re-read the operation flow (`05`~`18`).
- Confirm prerequisites: scraping needs an account + system list; export needs a folder path; import needs the scan level.
- To be safe, **back up the database** before troubleshooting, so you don't make things worse.

---

*This chapter works together with the "Guidance Node Inventory": many "points where users get stuck" are recorded as potential guidance points, which may become proactive in-app prompts in the future.*
