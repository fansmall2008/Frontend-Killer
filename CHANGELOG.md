# Changelog

## [1.0.6-beta] - 2026-05-01

### Added
- Added Lakka .lpl playlist format import/export support
  - Support for 5-line and 6-line .lpl formats
  - Automatic format detection
  - Support for DETECT placeholder handling
  - ZIP ROM path parsing with `#` separator
- Added `{filepath}` template variable for media file matching
  - Supports media files organized in subdirectories
  - For example: `media/screenshot/{filepath}.{ext}` → `media/screenshot/path1/path2/game.png`
- Added Skraper-EmulationStation import template (`skraper-es.json`)
  - Supports Skraper-scraped data for EmulationStation frontend
  - Full support for deep subdirectory media file paths
  - Includes all Skraper media types: box2dfront, box2dback, box2dside, box3d, boxtexture, fanart, images, marquee, screenmarquee, screenmarquesmall, screenshot, screenshottitle, steamgrid, support, supporttexture, videos, wheel, wheelcarbon, wheelsteel
- Added EmulationStation DE (ES-DE) export template (`esde.json`)
  - Follows ES-DE official directory structure
  - gamelist.xml exported to `gamelists/{platform}/` folder
  - Media files exported to `downloaded_media/{platform}/` folder
  - Supports 15+ media types: boxfront, boxback, cartridge, titlescreen, miximage, marquee, fanart, video, manual, wheel, screenshot, banner, box3d, bezel, steamgrid
  - Automatic `./` path prefix for gamelist.xml entries
- Added EmulationStation DE (ES-DE) import template (`esde.json`)
  - Supports importing ES-DE gamelist.xml and media files
  - Note: Users must copy `downloaded_media` and `gamelists` folders to the same directory before importing
  - Uses `{filepath}` variable for subdirectory media file support
  - Supports 10+ media types: boxfront, boxback, box3d, screenshot, video, wheel, marquee, fanart, titlescreen, manual

### Fixed
- Fixed template description display: Now dynamically reads description from template JSON files instead of hardcoded values
- Fixed .lpl template file not found issue in Docker container
- Fixed game_id NULL constraint error during Lakka import
- Fixed frontend filter not recognizing .lpl file type

### Improved
- Improved XML parsing logic for better compatibility with various frontend templates
- Added ErrorLogWriter utility for centralized error logging
- Enhanced game filtering and search functionality
- Added FilterResult model for improved data processing
- Updated import template documentation with new {filepath} variable

### Known Issues
- Some text entries are still not fully translated

### Roadmap
- **1.1**: First official release, target import/export templates for:
  - EmuDeck
  - ~~Lakka~~ ✅ Done
  - EmuElec
  - Recalbox
  - Batocera
- **1.2**: Integrate with ScreenScraper API for game scraping functionality

## [1.0.5-beta] - 2026-04-30

### Fixed
- Fixed page internationalization logic in multiple HTML files
- Corrected numerous untranslated internationalization entries
- Fixed translation keys not being applied after dynamic content updates
- Resolved language switching issues in game-list.html, task-management.html, and other pages
- Fixed syntax errors and undefined variable errors (translation, currentLanguage)

### Improved
- Added complete multi-language support (Chinese, English, Japanese)
- Enhanced translation loading mechanism
- Improved updateTranslations() function to properly handle dynamically generated content