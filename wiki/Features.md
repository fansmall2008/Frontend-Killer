# Features

Web GameList Oper provides comprehensive game list management capabilities. Here are the main features:

## Game List Management

### CRUD Operations
- **Create**: Add new game entries with detailed information
- **Read**: Browse and search through your game library
- **Update**: Edit game details including name, path, developer, publisher, etc.
- **Delete**: Remove games from your library

### Game Details
- Game ID
- Game Name
- Game Path
- Platform
- Developer
- Publisher
- Genre
- Release Date
- Players count
- Rating
- Description
- Media files (images, videos)

## Import Functionality

### Supported Templates
- **Pegasus**: Import from Pegasus frontend XML format
- **RetroBat**: Import from RetroBat game list format
- **Custom**: Support for custom import templates

### Import Options
- Auto-detect platform
- Batch import from multiple files
- Duplicate detection
- Error handling and reporting

## Export Functionality

### Supported Formats
- **Pegasus**: Export to Pegasus XML format
- **RetroBat**: Export to RetroBat compatible format
- **Custom**: Support for custom export rules

### Export Options
- Select specific platforms
- Select specific games
- Include media files
- Custom output directory

## Internationalization

### Supported Languages
- 🇨🇳 Chinese (中文)
- 🇬🇧 English
- 🇯🇵 Japanese (日本語)

### Features
- Language switcher in navigation
- All UI text translated
- Dynamic language switching without page reload
- Translation configuration file support

## Media Management

### Media Types
- **Images**: Box art, screenshots
- **Videos**: Game trailers, gameplay videos
- **Manuals**: PDF manuals

### Features
- View media files in modal
- Download media files
- Media file status tracking

## Platform Management

### Features
- View platform list
- Add/Edit/Delete platforms
- Platform merge functionality
- Platform details view

## Batch Operations

### Supported Operations
- **Batch Edit**: Edit multiple games at once
- **Batch Translate**: Translate game names/descriptions
- **Batch Delete**: Remove multiple games
- **Batch Migrate**: Migrate games between platforms

## Search and Filter

### Search
- Full-text search across all game fields
- Quick search bar
- Advanced search options

### Filters
- Platform filter
- Developer filter
- Genre filter
- Release date range
- Player count filter
- Scrape status filter

## Data Backup & Restore

### Backup
- Automatic database backup
- Manual backup option
- Scheduled backup support

### Restore
- Restore from backup files
- Selective restore options

## Logging & Debugging

### Features
- Application logs
- Import/export logs
- Error tracking
- Debug mode support