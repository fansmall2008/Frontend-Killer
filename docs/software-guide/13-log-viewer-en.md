# Log Viewer

> Page file: `log-viewer.html`

The Log Viewer page displays real-time system logs for troubleshooting and monitoring system status.

---

## Page Header

| Element | Description |
|---------|-------------|
| **"Back to Home"** link | Returns to the home page |
| **"Task Management"** link | Navigate to the Task Management page |
| **🔔 Notification Bell** | Shows unread message count, click to open notification panel |

---

## Real-time Log Controls

| Element | Description |
|---------|-------------|
| **Display Lines** | Number input to set the number of log lines loaded each time (default 100, range 10~1000) |
| **"Refresh"** button | Manually refresh log content |
| **"Auto Refresh"** checkbox | When checked, automatically loads the latest logs every 5 seconds (enabled by default) |

---

## Log Content Area

Displays system logs in real time, with automatic coloring by log level:

| Level | Color | Description |
|-------|-------|-------------|
| **ERROR** | 🔴 Red | Error messages requiring attention |
| **WARN** | 🟡 Yellow | Warning messages indicating potential issues |
| **INFO** | 🔵 Cyan | General information, normal operation records |
| **DEBUG** | 🟢 Green | Debug information, detailed runtime data |

The log content area has a maximum height of 600px and is scrollable when content exceeds this limit.

---

## Notification System

Same notification functionality as the System Settings page:

| Element | Description |
|---------|-------------|
| **Notification Bell 🔔** | Fixed in the top-right corner, red badge shows unread count |
| **Notification Popup** | Colored notification bar in the top-right corner, auto-dismisses after 3 seconds |
| **Notification Panel** | Modal opened by clicking the bell, displays historical messages |

---

## Next Steps

- View background tasks → [Task Management](12-task-management-en.md)
- Configure system parameters → [System Settings](11-system-settings-en.md)
