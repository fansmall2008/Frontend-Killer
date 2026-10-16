# 08 · Game Editing

> The game editing page is for fine-tuning a **single game's** data: edit text info, manage media, manually find data from ScreenScraper, and preview results. When auto-scraping isn't accurate enough, this is where you fill in by hand.

**Entry**: Game List → click a game (details in `07`)

---

## 8.1 Four Tabs

The top of the game editing page has 4 tabs; click to switch:

| Tab | Purpose |
|---|---|
| **Basic Info** | Name, description, publisher, developer, release date, genre, players, rating, path, and other core fields |
| **Metadata** | More complete metadata fields (various identifiers, extended info) |
| **Media Management** | Manage this game's images/videos (associate, replace, view the 50 media types) |
| **Media Preview** | Directly preview existing media |

---

## 8.2 Basic Info / Metadata

In these two tabs you can directly edit each of the game's fields:

- **Name / Sort name**: the display name, and the name used for sorting.
- **Description**: game synopsis.
- **Publisher / Developer**: the parties behind it.
- **Release date**: supports multiple date formats.
- **Genre**: game type.
- **Players / Rating**: player count and rating.
- **Path**: the game ROM file path.

> 💡 **Smart check on save**: If you changed the path, on save the software checks whether the **file at that path actually exists** and updates the "file exists" status. If the file isn't there, it may still save (e.g., you only changed the name), but it'll be marked "file does not exist" and the UI may show a hint.

Click **Save changes** when done.

---

## 8.3 Manually Searching a Game (when auto-scraping didn't match)

Sometimes auto-scraping can't find a game, or finds the wrong one. Then use the **"Search game"** feature manually:

1. On the game editing page, click "Search game" to open the search modal.
2. Enter keywords to query candidates from ScreenScraper.
3. Select the correct game from the candidates.
4. Apply its data and media to the current game.

This suits obscure games, oddly-named ones, or those where auto-matching failed. (It's the "if automation doesn't handle it, fall back to manual" mindset.)

---

## 8.4 Media Management

In the **Media Management** tab you manage all media associated with this game. ScreenScraper defines up to **50 media types**; common ones:

| Type (nomcourt) | Meaning |
|---|---|
| `box-2D` | Flat box art (front/back/side) |
| `box-3D` | 3D box art |
| `ss` | Game screenshot |
| `video` | Game video |
| `wheel` / `wheel-carbon` / `wheel-steel` | Game logo |
| `marquee` | Arcade marquee art |
| `fanart` | Background art |
| `manuel` | Manual (document) |
| `mixrbv1` / `mixrbv2` | Mixed images |
| `bezel-*` | Bezel decoration |
| …… | 50 types in total |

In Media Management you can see which media the current game has, which are missing, and associate or adjust them.

> Batch media downloading happens on the **Media Download** page (see `12`); here is mainly for viewing and fine-tuning a single game.

---

## 8.5 Media Preview

The **Media Preview** tab displays the game's images/videos directly, letting you confirm the media is correct and looks good without digging through folders.

---

## 8.6 Batch Editing (do it back in the list)

To change the same field across many games at once, don't enter them one by one — go back to the **Game List**, check multiple games, and use "Batch edit" (see `07`). The game editing page focuses on **fine-grained handling of a single game**.

---

## 8.7 Typical Uses

- Auto-scraping got the release date wrong → edit it manually in Basic Info → save.
- An obscure game wasn't scraped → use "Search game" to find it manually → apply.
- Want to confirm box art/screenshots are right → glance at Media Preview.
- ROM moved location → change the path → save (the software checks whether the file exists).

---

## 8.8 Next Steps

- Bulk data completion via scraping → `11 Auto Scraping`
- Batch media download → `12 Media File Management`
- The 50 media types and field naming reference → `18 Template System Guide`
