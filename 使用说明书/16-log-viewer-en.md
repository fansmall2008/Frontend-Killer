# 16 · Log Viewer

> Everything the software does while running is written to logs. When you hit a problem, logs are the first-hand clue for troubleshooting. This chapter covers how to view real-time logs, what the log-level colors mean, and the capability limits of this tool.

**Entry**: sidebar **Log Viewer** 📊 (TOOLS group)

---

## 16.1 Page Structure

The log viewer page is very simple, split into two blocks:

**① Top toolbar**
| Control | Description |
|---|---|
| **Lines to show** | Input box, default **100**, range **10 ~ 1000**. Controls how many of the latest lines to load at once |
| **Refresh** | Manually pull the latest logs |
| **Auto-refresh** | Checkbox, **on** by default, refreshes every **5 seconds** |

**② Log content area**
Shows the latest log text (`<pre>` monospace layout), auto-colored by log level.

---

## 16.2 Log Levels & Colors

Level keywords in log lines are auto-highlighted so you can spot problems at a glance:

| Level | Meaning | Attention |
|---|---|---|
| **ERROR** | Error — something went wrong | 🔴 highest, focus here |
| **WARN** | Warning — a risk but no crash | 🟡 worth noting |
| **INFO** | Routine info — normal flow records | 🟢 general |
| **DEBUG** | Debug detail — the most verbose | ⚪ read closely only when troubleshooting |

> Troubleshooting approach: first look only at **ERROR / WARN**, locate the few lines with the problem, then scroll up through INFO to see what it was doing before.

---

## 16.3 How to Use

**See the latest situation**
1. Open the log viewer — by default it has loaded the latest 100 lines and auto-refreshes every 5 seconds.
2. Want more history → increase "Lines to show" (e.g., 500, 1000), click Refresh.
3. Want to stop scrolling and stare at a section → uncheck "Auto-refresh".

**Troubleshoot alongside tasks**
- A task FAILED: first expand that task's log in Task Management for the error summary (see `14`), then come to the log viewer for fuller context.
- Scraping / media download isn't moving: check for ERROR (network, rate limit, 404 circuit breaker) or WARN.

---

## 16.4 Where Logs Live & How They Rotate

- **File location**: `logs/application.log`.
- **Rotation policy**: rotates roughly every **10MB** per file, keeping at most **7** history files (older ones are removed). History files look like `application.log.2026-09-28.0.gz` (compressed).

> The log viewer page reads the **real-time latest** portion. To look at records from long ago, go to the `logs/` directory and find the corresponding history file.

---

## 16.5 Capability Limits (important)

The current log viewer is a **lightweight read-only viewer**. It does **not** have:

- ❌ Built-in keyword **search**
- ❌ **Filtering** by level/time
- ❌ **Exporting** logs to a file

To search precisely within a large chunk of logs, current options are:

- Use the browser's **Ctrl+F** to search within already-loaded content (only lines shown on the page);
- Or open `logs/application.log` directly and search with system tools (e.g., Notepad, `grep`).

> This is a known capability gap; we've recorded it as a potential guidance point (see "Guidance Node Inventory" N19): in the future we may add built-in filtering, or hint on the page "use external tools to search".

---

## 16.6 Typical Uses

- An operation errored / the page reported failure → look at the log's ERROR lines, which usually state the cause directly.
- Want to confirm a background task is really running → see whether INFO lines show ongoing progress output.
- Reporting a problem to someone else → note the few lines around the ERROR + the time; more useful than a screenshot.

---

## 16.7 Next Steps

- Task-level errors & retry → `14 Task Management`
- Common error lookups & solutions → `19 FAQ & Troubleshooting`
