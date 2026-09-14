# Frequently Asked Questions (FAQ)

This document compiles common questions and solutions when using Frontend-Killer.

---

## Import Related

### Q: The game list is empty after import?

**A:** Please check the following:
1. Whether the scan path correctly points to the directory containing ROM files
2. Whether the import template matches your gamelist file format
3. Check the [Log Viewer](13-log-viewer-en.md) for error messages
4. Confirm that ROM files actually exist at the specified path

### Q: What import formats are supported?

**A:** The following formats are currently supported for import:
- Pegasus (metadata.pegasus.txt)
- ES-DE (gamelist.xml)
- RetroBat (gamelist.xml)
- EmuELEC (gamelist.xml)
- Lakka (gamelist.xml)
- Skraper (gamelist.xml)
- WebGamelistOper (native format)
- V3 versions of all the above formats

See [Data Import](03-data-import-en.md) for details.

---

## Scraping Related

### Q: Scraping failed with a quota exceeded error?

**A:** ScreenScraper has daily request limits:
- Guest mode: approximately 20,000 requests/day
- Registered account: higher limits based on contribution level
- Please configure your ScreenScraper account in [System Settings](11-system-settings-en.md)
- Wait until the next day when the quota resets and try again

### Q: How to scrape only specific games?

**A:** Check the games you want to scrape in the Game List page, then click the "Batch Scrape" button.

### Q: Where are the scraped media files stored?

**A:** Media files are stored in the `scraper/games/` directory under the data directory, organized by game ID subdirectories.

---

## Export Related

### Q: The frontend can't recognize games after export?

**A:** Please check:
1. Whether the exported frontend template matches your frontend
2. Whether the export path is correct
3. Whether media files were copied correctly
4. See the [Export documentation](08-export-en.md) for path format requirements of each template

### Q: Some export options are grayed out and can't be selected?

**A:** Some frontend templates restrict which content types can be exported. A yellow warning will be displayed after selecting a template explaining the restrictions.

---

## Platform Management Related

### Q: How to merge games from two platforms?

**A:** Select the target platform in the Platform Management page, use the "Merge" function to select the source platform. The system will automatically detect conflicts. See [Platform Merge](07-platform-merge-en.md) for details.

### Q: How to migrate games to another platform?

**A:** Check the games in the Game List page, click the "Migrate" button, and select the target platform.

---

## System Related

### Q: How to backup the database?

**A:** Click the "Backup Database" button on the [System Settings](11-system-settings-en.md) page. Backup files are saved in the `/data/backup/` directory.

### Q: What languages are supported?

**A:** Currently supports 中文, English, and 日本語. Switch in System Settings.

### Q: How to check system status?

**A:** 
- Background tasks → [Task Management](12-task-management-en.md)
- System logs → [Log Viewer](13-log-viewer-en.md)
- Scraping progress → [Media Download](10-media-download-en.md)

---

## Next Steps

- Return to overview → [Overview](00-overview-en.md)
- Quick start → [Quick Start](01-quick-start-en.md)
