# 14 · Task Management

> All background work — import, export, scraping, translation, media download — is viewed and controlled together in "Task Management". This chapter covers how to see progress, stop/cancel/delete tasks, handle failed tasks, and the media download panel.

**Entry**: sidebar **Task Management** 📋 (TOOLS group)

---

## 14.1 Page Structure

- **Task list**: task cards showing type, description, status, progress bar, processed/total.
- **Media download panel**: dedicated control to start/stop/pause/resume media download tasks.
- **Auto-refresh**: the page refreshes task status every **5 seconds** automatically — no manual refresh needed.

---

## 14.2 Task Status

Each task has a status:

| Status | Meaning |
|---|---|
| **PENDING** | Queued, waiting |
| **RUNNING** | Executing |
| **COMPLETED** | Done |
| **FAILED** | Failed (with error message) |
| **CANCELLED** | Cancelled |

Task cards also show progress percentage, processed/total counts, a message, and can be **expanded to view logs**.

---

## 14.3 Task Types

Common types:
- **IMPORT**: import
- **EXPORT**: export
- **TRANSLATE**: translation
- **Scrape / Media download**: scraping info and downloading media

Import and export tasks are marked **cancellable**.

---

## 14.4 Controlling Tasks

### Delete
- **Delete single**: delete one task record. ⚠️ **Running tasks can't be deleted** (stop/cancel first).
- **Batch delete**: check several to delete together (running ones are skipped).
- **Clear all**: clear all task records at once.

### Cancel
- Clicking "Cancel" on a **cancellable, running** task changes its status to CANCELLED.
- ⚠️ **Important note**: Currently "cancel" **marks the task as cancelled**; a long-running loop may not stop immediately (it needs checkpoints in the loop to truly interrupt). So it may keep running a bit after cancelling.

### Stop
- The home page "Running tasks" and the media panel have stop buttons to terminate tasks.

---

## 14.5 Failed Tasks & Retry

- When a task is FAILED, the card shows an **error message**; expand the log for details.
- Failed tasks can be **retried** (re-initiated).
- To know exactly why it failed, expand the task log, or go to the **Log Viewer** 📊 for fuller runtime logs (see `16`).

> Common failure causes: network/rate-limiting (scrape download), wrong paths (import/export), no ScreenScraper match. Remedies in `19`.

---

## 14.6 Media Download Panel

Task Management integrates a media download control panel:

- Shows media download task statistics: total, waiting, downloading, done, failed, overall progress.
- **Start media download**: initiate media download for a task (max threads configurable).
- **Stop / Pause / Resume**: control ongoing media downloads.
- Query media download running/paused status.

(Full media download description in `12`.)

---

## 14.7 Task Recovery After Abnormal Shutdown

If the software shuts down abnormally (power loss, crash), on next startup it does **task recovery**:
- Resets tasks still marked RUNNING to **FAILED** (they were actually interrupted).
- Resets DOWNLOADING media tasks to **PENDING** (re-downloadable).

So seeing some tasks turn red / return to the queue after restart is normal recovery behavior — just retry.

---

## 14.8 Typical Uses

- After starting scrape/import/export, come here to see how far the progress bar has gone.
- Want to abort a task that's going wrong → cancel/stop.
- Task turned red → expand to see the error → fix the cause → retry.
- Media download needs to yield temporarily → pause in the panel.
- Piled up old done/failed records → batch delete or clear.

---

## 14.9 Next Steps

- Lower-level runtime logs → `16 Log Viewer`
- Media download details → `12 Media File Management`
