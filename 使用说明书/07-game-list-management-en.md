# 07 · Game List Management

> After entering a platform, you see the list of all its games. This is the most frequently used page for daily operations — filtering, batch processing, and entering single-game editing all start here.

**Entry**: Platform Management → click a platform name/row → enter that platform's game list (URL like `/game-list?platformId=...`)

> 💡 If this platform isn't bound to a scraper system yet, entering it automatically pops up the "system matching guide" (see `06` §6.5).

---

## 7.1 Page Structure

The game list page has roughly three parts:
1. **Top toolbar**: add, batch operation buttons, pagination.
2. **Filter area**: filter games by various criteria.
3. **Game table**: lists games row by row, with status markers, key fields, and actions.

---

## 7.2 Game Status Legend (red/yellow/green)

Each game in the list carries a **data completeness** status marker, so you can spot at a glance which need attention:

| Color | Meaning | Suggestion |
|---|---|---|
| 🔴 Red | **No info**: almost no data (only a ROM) | Most in need of scraping/completion |
| 🟡 Yellow | **Partially missing**: some data but incomplete | Can re-scrape or complete manually |
| 🟢 Green | **Complete**: full data | Usually no action needed |

> Combined with filters, you can list only the "red" games to handle them together — very efficient.

---

## 7.3 Filtering & Pagination

- **Multi-dimensional filtering**: Filter by name, status, whether media exists, whether scraped, etc., to quickly locate the games you want to handle.
- **Pagination**: "Previous/Next" at the bottom. Page size options are **100 / 300 / 500** — increase as needed for large libraries.

---

## 7.4 Batch Operation Toolbar (core)

**First check** games in the table (multi-select/select all); the previously grayed-out toolbar buttons become active. Available operations:

| Button | Purpose | Notes |
|---|---|---|
| **Add** | Manually add a game entry to the platform | Usable without selection |
| **Delete selected** | Delete the checked games | ⚠️ Irreversible, asks for confirmation |
| **Batch scrape** | Start scraping for the checked games | Scrape settings dialog; see `11` |
| **M3U merge** | Merge multiple single-file games into one m3u entry | ⚠️ Deletes original entries, may affect ROMset; see below |
| **Batch edit** | Change the same field across many games at once | Dialog shows "will modify N games" |
| **Translate** | Translate the checked games' data | See `17` |
| **Migrate** | Move/transfer the checked games to another platform | Good for misfiled games |
| **Swap translations** | Swap the checked games' original ↔ translation | ⚠️ Confirms; large impact |
| **Recheck existence** | Re-check whether game files actually exist | Updates the "file exists" status |

> Tip: High-impact or irreversible operations like batch edit, swap translations, delete, and M3U merge all pop a confirmation dialog telling you how many games are affected — read carefully before confirming.

---

## 7.5 Key Operations Explained

### M3U Merge (multi-disc into m3u)
Merges multiple files belonging to the same game (e.g., several discs) into **one m3u playlist entry**, so the frontend shows them as a single game.

- Generates the new entry **based on the first selected record**, with the game path pointing to the newly created m3u file.
- ⚠️ **The original separate entries are deleted**, and merging may break the original ROMset integrity. Before doing it, be sure the selected items are multiple discs of the same game, and that you've backed up.

### Migrate
Moves the selected games from the current platform to another. Good for games misfiled during import.

### Recheck existence
Re-verifies whether the physical file each game's path points to still exists, updating the "exists" status in the database. Use it to refresh status after moving ROMs.

### Swap translations
One-click swap of a game's "original-language content" and "translated content". For example, if you want the list to show Chinese instead of the English original name, translate first then use this to swap. ⚠️ Applies to all selected games, with a confirmation prompt.

---

## 7.6 Entering Single-Game Editing

Click a game (row or edit entry) → enter the **Game Editing** page to change that game's details and manage its media. Details in `08`.

---

## 7.7 Typical Uses

- **Complete low-quality data**: Filter red/yellow games → select all → batch scrape.
- **Organize multi-disc games**: Filter the multiple discs of the same game → check → M3U merge.
- **Correct misfiling**: Find games that don't belong to this platform → check → migrate to the correct platform.
- **Switch display language**: After batch translate → swap translations.

---

## 7.8 Next Steps

- How to edit a single game → `08 Game Editing`
- Scrape settings details → `11 Auto Scraping`
