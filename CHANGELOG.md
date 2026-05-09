# Changelog

## [1.0.6-beta2] - 2026-05-08

### Added
- Added EmuELEC import/export templates (`emuelec.json`)
  - Export template: Supports exporting game metadata and media files
  - Import template: Supports importing from EmuELEC gamelist.xml and downloaded_images
  - Supports three gamelist.xml lookup locations as per EmuELEC specification
  - Includes 15+ media types: box2dfront, box2dback, box2dside, box3d, fanart, screenshot, video, wheel, banner, manual, images, titlescreen, marquee, miximage, cartridge, bezel
- Added `exportOptions` configuration for export templates
  - `gameFiles`: Controls whether ROM files can be exported (default: true)
  - `mediaFiles`: Controls whether media files can be exported (default: true)
  - `gameListFile`: Controls whether gamelist.xml can be exported (default: true)
  - `showWarning`: Controls whether to display template limitation warnings (default: false)
- Added enhanced media file lookup logic
  - Now executes template rules regardless of whether media files exist in gamelist.xml
  - Supports filling in missing media types even when some are already present
  - Added detailed logging for media file matching paths
- Added missing media types to Skraper-EmulationStation import template
  - Added `boxFull`, `cartridge`, `manual`, `logo`, `bezel`, `panel`, `cabinetLeft`, `cabinetRight`, `tile`, `steam`, `poster`, `background`, `music`

### Fixed
- Fixed ES-DE import template not finding `downloaded_media` folder
  - Adjusted media path rules to use `../../downloaded_media/{platform}/{mediaType}/{filepath}.{ext}`
- Fixed media file matching not logging complete paths
  - Added detailed log output showing all attempted paths
- Fixed media type name case sensitivity issue
  - Added case-insensitive matching for media type names (e.g., `boxFront` vs `boxfront`)
- Fixed platform statistics page duplicate file conflict table overflow
  - Added vertical scroll (`max-height: 300px; overflow-y: auto;`)
  - Added horizontal scroll for wide tables
  - Set minimum table width for better readability
- Fixed template parameter not being passed to conversion method
  - Added `ImportTemplate template` parameter to `convertToGameModel` method

### Improved
- Improved ES-DE export template documentation
  - Added clear description about ROM file export restriction
  - Updated to English description for international compatibility
- Improved EmuELEC export template documentation
  - Added clear description about ROM file export restriction
  - Updated to English description for international compatibility
- Improved export page UI to reflect template restrictions
  - Automatically disables checkboxes based on `exportOptions` configuration
  - Shows warning message when template has export restrictions
  - Dynamically displays template description from JSON file
- Updated skraper-es.json import template with comprehensive media types
  - Now covers all standard Skraper media types
  - Added multiple path rules for better compatibility

### Known Issues
- Some text entries are still not fully translated

### Roadmap
- **1.1**: First official release
  - Import/export templates:
    - EmuDeck
    - ~~Lakka~~ ✅ Done
    - ~~EmuElec~~ ✅ Done
    - Recalbox
    - Batocera
  - Multi-file game support (e.g., DOS games) by treating entire game folders as game entries
  - Folder export configuration with configurable parent directory levels
  - Advanced game filtering options
- **1.2**: Integrate with ScreenScraper API for game scraping functionality
- **1.3**: Statistics dashboard for game collection analytics

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
