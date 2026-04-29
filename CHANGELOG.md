# Changelog

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

### Known Issues
- Some text entries are still not fully translated
- International users should be able to understand most of the interface now
- Working diligently to resolve remaining internationalization issues

### Roadmap
- **1.0.6**: Focus on UI, aim to completely solve internationalization issues
- **1.1**: First official release, target import/export templates for:
  - EmuDeck
  - Lakka
  - EmuElec
  - Recalbox
  - Batocera
- **1.2**: Integrate with ScreenScraper API for game scraping functionality
