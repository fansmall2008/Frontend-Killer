# Changelog

## [1.0.6-beta] - 2026-05-01

### Fixed
- Fixed template description display: Now dynamically reads description from template JSON files instead of hardcoded values

### Improved
- Improved XML parsing logic for better compatibility with various frontend templates
- Added ErrorLogWriter utility for centralized error logging
- Enhanced game filtering and search functionality
- Added FilterResult model for improved data processing

### Known Issues
- Some text entries are still not fully translated

### Roadmap
- **1.1**: First official release, target import/export templates for:
  - EmuDeck
  - Lakka
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