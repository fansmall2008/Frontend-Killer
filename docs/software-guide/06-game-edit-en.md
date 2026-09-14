# Game Edit

> Page file: `game-edit.html`

The Game Edit page allows you to modify a game's metadata, manage media files, and retrieve supplementary data from ScreenScraper.

---

## Page Header

| Element | Description |
|---------|-------------|
| **"← Back to List"** button | Returns to the Game List page |
| **Game Name** | Displays the name of the current game |
| **"Search Game"** button | Opens the ScreenScraper search modal |

---

## Tab Navigation

The page organizes content into 4 tabs:

| Tab | Description |
|-----|-------------|
| **Basic Info** | Core game field editing |
| **Metadata** | System-level metadata (ID, CRC32, source, etc.) |
| **Media Management** | Upload/delete files across 50 media types in 14 categories |
| **Media Preview** | Gallery view of all existing media files |

---

## Tab 1: Basic Info

| Field | Description |
|-------|-------------|
| **Name (name)** | Original game name |
| **Path (path)** | ROM file path |
| **Description (desc)** | Game description text |
| **Translated Name (translatedName)** | Translated game name |
| **Translated Desc (translatedDesc)** | Translated description |
| **Developer (developer)** | Game developer |
| **Publisher (publisher)** | Game publisher |
| **Genre (genre)** | Game genre |
| **Players (players)** | Number of supported players |
| **Release Date (releasedate)** | Release date |
| **Rating (rating)** | Game rating |
| **Language (lang)** | Game language |
| **Filename (filename)** | Read-only; extracted from the path |
| **Platform Path (platformPath)** | Parent platform path |
| **Absolute Path (absolutePath)** | ROM file absolute path |

### Swap Translation Button

| Button | Description |
|--------|-------------|
| **"🔄 Swap Translation"** | Swaps original name/description with translated name/description |

---

## Tab 2: Metadata

| Field | Description |
|-------|-------------|
| **Game ID (gameId)** | Read-only; ScreenScraper game ID |
| **Source (source)** | Data source |
| **CRC32 (crc32)** | Read-only; ROM file CRC32 checksum |
| **Genre ID (genreid)** | Read-only; ScreenScraper genre ID |
| **Platform ID (platformId)** | Read-only; parent platform ID |
| **Platform Path (platformPath)** | Parent platform path |
| **Sort By (sortBy)** | Game sorting field |

---

## Tab 3: Media Management

Displays 50 media types organized by category, each as a card:

### Media Categories

| Category | Included Types |
|----------|---------------|
| 📦 Box Art (Elements Boitiers) | 2D front, 2D back, 2D side |
| 🎁 3D Cases (Boitiers) | 3D box, box texture |
| 📋 Scan Sources | Box scan |
| 💿 Media (Supports) | 2D media, media texture |
| 🔍 Media Scans (Supports-scan) | Media scan |
| 🎯 Logo (Wheels) | Wheel, HD wheel, carbon wheel, steel wheel, etc. |
| 💡 Marquee | Marquee, screen marquee |
| 🖼️ Artwork (Médias) | Screenshot, title screenshot, Steam Grid, fan art, video, etc. |
| 🖥️ Bezels | 4:3/16:9 bezels in all orientations |
| 📄 Secondary Media | Flyers, manuals, maps, figurines |
| 🎨 Mixes | mixrbv1, mixrbv2 |
| 🎭 Themes | Main screen theme, widescreen theme |
| 🎰 Pinball Media | DMD, front, table, top video, etc. |
| 🗃️ Secondary Images | Background, list icon, color icon, mono icon |

### Each Media Card

| Element | Description |
|---------|-------------|
| **Media type name** | Displays the current media type label |
| **Preview area** | Shows existing file preview (image/video) or empty state |
| **"Upload"** button | Select a local file to upload for this media type |
| **"Delete"** button | Delete the existing file for this media type (only shown when file exists) |

---

## Tab 4: Media Preview

Gallery view displaying all existing media files for the current game:

- Image types: shown as thumbnails
- Video types: shown with a video player
- Other types: shown with a "View/Download file" link

---

## Bottom Action Bar

| Button | Description |
|--------|-------------|
| **"Save Changes"** | Save all currently edited fields |
| **"Search Game"** | Open the ScreenScraper search modal |
| **"Cancel"** | Return to the game list |

---

## Search Game Modal

| Element | Description |
|---------|-------------|
| **Search input** | Enter game name, press Enter or click search |
| **"Search"** button | Execute ScreenScraper API search |
| **"☁️ Auto-download media"** checkbox | Automatically download corresponding media when a search result is selected |
| **Search results list** | Shows matching games with thumbnail, year, genre, players, rating, developer, publisher |

### Search Result Actions

After clicking a search result:

1. **Auto-fill**: Populates name, description, developer, publisher, genre, players, year, and rating into the edit form
2. **Media download** (if checked): Adds the search result's media files to the download queue

---

## Next Steps

- Return to game list → [Game List](05-game-list-en.md)
- Learn about scraper systems → [Scraper Systems](09-scraper-en.md)
- Learn about media downloads → [Media Download](10-media-download-en.md)
