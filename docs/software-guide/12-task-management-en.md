# Task Management

> Page file: `task-management.html`

The Task Management page monitors the execution status of all background tasks in real time, including export, scraping, and import tasks.

---

## Page Header

| Element | Description |
|---------|-------------|
| **"Back to Home"** button | Returns to the home page |
| **"Log Viewer"** button | Navigate to the Log Viewer page |

---

## Task List

### Task Cards

Each task is displayed as a card containing the following information:

| Element | Description |
|---------|-------------|
| **Task Description** | Task name or type description |
| **Status Badge** | RUNNING (cyan) / COMPLETED (green) / FAILED (red) / PENDING (yellow) |
| **Start Time** | Time when the task started |
| **End Time** | Time when the task completed/failed (not shown when incomplete) |
| **Progress Bar** | Visual representation of task completion percentage |
| **Progress Percentage** | Current completion percentage |
| **Status Message** | Current status description of the task |
| **Processing Progress** | Processed count / Total count (only shown for running tasks) |
| **Error Message** | Error details when the task fails (only shown when failed) |
| **Result Info** | Result summary when the task completes (only shown when completed) |
| **Detailed Log** | Expandable/collapsible task execution log |

### Task Actions

| Button | Description |
|--------|-------------|
| **"Delete"** | Delete this task record |
| **"Clear All Tasks"** | Delete all task records (requires confirmation) |
| **"Expand/Collapse"** | Expand or collapse the task's detailed log |

---

## Media Download Task Panel

When scraping tasks exist, a summary of media download tasks is displayed at the top of the page:

| Element | Description |
|---------|-------------|
| **Total Count** | Total number of media download tasks |
| **Completed** | Number of downloads completed (green) |
| **Failed** | Number of downloads failed (red) |
| **Pending** | Number waiting to download (yellow) |
| **Downloading** | Number currently downloading (blue) |
| **Progress Bar** | Visual representation of download completion percentage |

| Button | Description |
|--------|-------------|
| **"Start Download"** | Start the media download task |
| **"Stop Download"** | Stop the ongoing media download |

---

## Auto Refresh

- Task list auto-refreshes every **5 seconds**
- Media download status refreshes every **3 seconds**
- Automatic popup notifications on task status changes (completed/failed)
- Prominent notification displayed when scrape quota warnings are detected

---

## Cross-Page Notifications

The Task Management page broadcasts task completion/failure notifications to other open pages via BroadcastChannel, ensuring users are promptly informed of task status changes regardless of which page they are on.

---

## Next Steps

- View system logs → [Log Viewer](13-log-viewer-en.md)
- Configure system parameters → [System Settings](11-system-settings-en.md)
