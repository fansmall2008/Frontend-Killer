# Game List

> Page file: `game-list.html`

The Game List page displays all games under a specific platform, supporting search, pagination, and batch operations.

---

## Page Header

| Element | Description |
|---------|-------------|
| **"Back to Platform Mgmt"** button | Returns to Platform Management |
| **Platform Name** | Shows the current platform name |
| **"Search"** button | Toggle the search area |
| **Total Games** | Shows total game count for the current platform |

---

## Search Area

Toggle open/close with the **"Search"** button. Contains these filters:

| Field | Type | Description |
|-------|------|-------------|
| **Keyword Search** | Text input | Search game name or path |
| **Release Date Range** | Year range | Start year ~ End year |
| **Developer** | Checkbox list | Dynamically loaded developers for this platform; multi-select |
| **Genre** | Checkbox list | All genres; multi-select |
| **Players** | Checkbox list | All player counts; multi-select |
| **Scrape Status** | Checkbox list | Fully scraped / Partially scraped / Not scraped |
| **File Status** | Checkbox list | File exists / File not exists |
| **Folder Path** | Text input | Filter by folder name (supports any-level matching) |

| Button | Description |
|--------|-------------|
| **"Search"** | Execute search |
| **"Reset"** | Clear all search criteria |

---

## Toolbar

Located on the left side of the pagination area:

| Element | Type | Description |
|---------|------|-------------|
| **Page Size** | Dropdown | Items per page: 20 / 50 / 100 |
| **"Add"** button | Toolbar button | Opens the Add Game modal |
| **"Delete Selected"** button | Toolbar button | Delete checked games (must check first) |
| **"Batch Scrape"** button | Toolbar button | Batch scrape checked games |
| **"Merge Discs"** button | Toolbar button | Merge checked multi-disc games into one record |
| **"Batch Edit"** button | Toolbar button | Batch edit developer/publisher/genre etc. for checked games |
| **"Translate"** button | Toolbar button | Translate checked games |
| **"Migrate"** button | Toolbar button | Migrate checked games to another platform |
| **"Swap Translation"** button | Toolbar button | Swap original and translated text for checked games |

> 💡 Except for "Add", all batch operation buttons require game rows to be checked first.

---

## Pagination Controls

Located on the right side of the toolbar:

| Element | Description |
|---------|-------------|
| **"Previous"** button | Go to previous page |
| **Page Info** | Shows "Page X of Y" |
| **"Next"** button | Go to next page |

---

## Status Legend

Displayed above the table, showing color meanings:

| Color | Meaning |
|-------|---------|
| 🔴 Red | No info — game is missing key metadata |
| 🟡 Yellow | Partial — game has some metadata but incomplete |
| 🟢 Green | Complete — game metadata is complete |

---

## Game Table

| Column | Description |
|--------|-------------|
| **☑** | Select all / individual checkbox |
| **Status** | Red/Yellow/Green dot indicating data completeness |
| **ID** | Game database ID |
| **Icon** | Configured icon field thumbnail (configurable in System Settings) |
| **Game Name** | Game name |
| **Game Path** | ROM file path |
| **Platform Path** | Parent platform path |
| **Description** | Game description text |
| **Translated Name** | Translated game name |
| **Translated Desc** | Translated description |
| **Developer** | Game developer |
| **Publisher** | Game publisher |
| **Genre** | Game genre |
| **Players** | Number of supported players |
| **Release Date** | Release date |
| **Rating** | Game rating |
| **Language** | Game language |
| **File Status** | Whether the ROM file exists |

**Row action**: Click any game row to enter the [Game Edit](06-game-edit-en.md) page.

---

## Batch Edit Modal

Opens when clicking **"Batch Edit"**:

| Field | Description |
|-------|-------------|
| **Developer** | Leave blank to skip |
| **Publisher** | Leave blank to skip |
| **Genre** | Leave blank to skip |
| **Players** | Leave blank to skip |
| **Language** | Leave blank to skip |

| Button | Description |
|--------|-------------|
| **"Confirm"** | Apply changes to all checked games |
| **"Cancel"** | Close the modal |

---

## Batch Scrape Modal

Opens when clicking **"Batch Scrape"**, with the same configuration as the scrape modal in Platform Management:

- Scrape scope, game info fields, media file types, language tags

---

## Add Game Modal

Opens when clicking **"Add"**, fill in basic game info to create a new record.

---

## Next Steps

- Edit individual games → [Game Edit](06-game-edit-en.md)
- Back to platform management → [Platform Management](04-platform-mgmt-en.md)
