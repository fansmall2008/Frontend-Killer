# Export Functionality Documentation

## 1. Functionality Overview

The export functionality allows users to copy game files, media files, and data files of selected platforms to a specified path, supporting multiple frontend rules such as Pegasus, EmulationStation DE (ES-DE), and RetroBat.

### Core Features:
- Support for multiple frontend rules
- Option to export game files, media files, and data files
- Support for custom export paths
- Real-time export progress and log feedback
- Flexible rule configuration system

## 2. Rule Configuration Files

### 2.1 Configuration File Format

Rule configuration files use JSON format and are stored in the `src/main/resources/export-rules/` directory.

### 2.2 Configuration File Structure

```json
{
  "frontend": "pegasus",
  "name": "Pegasus Frontend",
  "version": "1.0",
  "description": "Pegasus frontend export rules",
  "rules": {
    "media": {
      "box2dfront": {
        "source": "box2dfront",
        "target": "{mediaPath}/{gameName}/boxFront.png",
        "dataFileTag": "assets.boxFront"
      },
      "box2dback": {
        "source": "box2dback",
        "target": "{mediaPath}/{gameName}/boxBack.png",
        "dataFileTag": "assets.boxBack"
      }
    },
    "dataFile": {
      "filename": "metadata.pegasus.txt",
      "format": "pegasus",
      "fields": {
        "title": "name",
        "description": "description",
        "rating": "rating"
      }
    },
    "directory": {
      "roms": "{outputPath}/{platform}/roms",
      "media": "{outputPath}/{platform}/media"
    }
  }
}
```

### 2.3 Supported Variables

- `{outputPath}`: Export path
- `{platform}`: Platform name
- `{gameName}`: Game name
- `{mediaPath}`: Media file directory
- `{romsPath}`: ROM file directory

### 2.4 Supported Fields

- `name`: Game name
- `description`: Game description
- `rating`: Game rating
- `releaseYear`: Release year
- `developer`: Developer
- `publisher`: Publisher
- `genre`: Game genre
- `players`: Number of players
- `region`: Game region
- `filename`: Filename extracted from path (without extension and leading path)

### 2.5 Multi-value Matching Mechanism

In the `fields` section of the rule configuration file, you can use commas to separate multiple field names, and the system will match them in order until a non-empty value is found. For example:

```json
"title": "name,filename"
```

This means it will first try to use the `name` field, and if it is empty, it will use the `filename` field.

### 2.6 Database Field Description

The following are the database fields available in the Game class, which can be used in the `fields` section of the rule configuration file:

| Field Name | Type | Description |
|-----------|------|-------------|
| `id` | Long | Game ID |
| `gameId` | String | Game unique identifier |
| `source` | String | Game source |
| `path` | String | Game relative path |
| `name` | String | Game name |
| `desc` | String | Game description |
| `translatedName` | String | Translated game name |
| `translatedDesc` | String | Translated game description |
| `image` | String | Game image |
| `video` | String | Game video |
| `marquee` | String | Game marquee |
| `thumbnail` | String | Game thumbnail |
| `manual` | String | Game manual |
| `boxFront` | String | Game box front |
| `boxBack` | String | Game box back |
| `boxSpine` | String | Game box spine |
| `boxFull` | String | Game box full |
| `cartridge` | String | Game cartridge |
| `logo` | String | Game logo |
| `bezel` | String | Game bezel |
| `panel` | String | Game panel |
| `cabinetLeft` | String | Cabinet left |
| `cabinetRight` | String | Cabinet right |
| `tile` | String | Game tile |
| `banner` | String | Game banner |
| `steam` | String | Steam link |
| `poster` | String | Game poster |
| `background` | String | Game background |
| `music` | String | Game music |
| `screenshot` | String | Game screenshot |
| `titlescreen` | String | Game title screen |
| `box3d` | String | 3D game box |
| `steamgrid` | String | Steam grid |
| `fanart` | String | Game fan art |
| `boxtexture` | String | Game box texture |
| `supporttexture` | String | Support texture |
| `rating` | Double | Game rating |
| `releasedate` | String | Game release date |
| `developer` | String | Game developer |
| `publisher` | String | Game publisher |
| `genre` | String | Game genre |
| `players` | String | Number of players |
| `crc32` | String | Game file CRC32 checksum |
| `md5` | String | Game file MD5 checksum |
| `lang` | String | Game language |
| `genreid` | String | Game genre ID |
| `hash` | String | Game file hash value |
| `platformType` | String | Platform type |
| `platformId` | Long | Platform ID |
| `sortBy` | String | Sort field |
| `scraped` | Boolean | Whether scraped |
| `edited` | Boolean | Whether edited |
| `exists` | Boolean | Whether file exists |
| `absolutePath` | String | Game absolute path |
| `platformPath` | String | Platform path |

### 2.7 Media File Source Fields

The following are the media file source fields, which can be used in the `source` field of the `media` section in the rule configuration file:

| Field Name | Description |
|-----------|-------------|
| `box2dfront` | Game box front |
| `box2dback` | Game box back |
| `box3d` | 3D game box |
| `screenshot` | Game screenshot |
| `video` | Game video |
| `wheel` | Game logo (using logo field) |
| `marquee` | Game marquee |
| `fanart` | Game fan art |

### 2.8 Supported Frontend Rules

- **Pegasus**: `pegasus.json`
- **EmulationStation DE**: `esde.json`
- **RetroBat**: `retrobat.json`

### 2.9 M3U File Processing

The system supports processing M3U format playlist files. When encountering an M3U file, it will automatically copy all files referenced in the file.

#### 2.9.1 M3U Processing Configuration

In the rule configuration file, you can configure the M3U file processing method through the `m3u` section:

```json
"m3u": {
  "enabled": true,
  "target": "{romsPath}"
}
```

- `enabled`: Whether to enable M3U processing
- `target`: Target path template for files referenced in M3U files

#### 2.9.2 Supported Path Types

- **Relative path**: Path relative to the M3U file
- **Absolute path**: Full file path

## 3. Usage Guide

### 3.1 Accessing the Export Page

1. Click the "Enter Platform Export" button from the home page
2. Or directly access `/export.html`

### 3.2 Export Configuration

1. **Select Platform**: Select the platform to export from the dropdown menu
2. **Select Frontend**: Select the target frontend rule from the dropdown menu
3. **Export Range**: Check the content to export (game files, media files, data files)
4. **Export Path**: Default path is `/data/output`, can be customized

### 3.3 Execute Export

Click the "Export" button to start the export process, and the system will display real-time progress and detailed logs.

## 4. Technical Implementation

### 4.1 Backend Implementation

- **Rule Loading**: `ExportRuleService` is responsible for loading and parsing rule configuration files
- **Export Service**: `ExportService` is responsible for executing export operations
- **File Processing**: Supports file copying, media file processing, and data file generation
- **API Interface**: `ExportController` provides export-related REST APIs

### 4.2 Frontend Implementation

- **Export Configuration Interface**: `export.html` provides a user-friendly export configuration interface
- **Progress Feedback**: Real-time display of export progress and detailed logs
- **Responsive Design**: Adapts to different screen sizes

## 5. Common Issues and Solutions

### 5.1 Export Failure

- **Check Export Path**: Ensure the export path exists and has write permissions
- **Check Platform Data**: Ensure the platform has game data
- **Check Rule Configuration**: Ensure the rule configuration file is correct

### 5.2 Media File Mismatch

- **Check Rule Configuration**: Ensure the media file mapping in the rule configuration file is correct
- **Check Source Files**: Ensure the source media files exist

### 5.3 Data File Format Error

- **Check Rule Configuration**: Ensure the data file format configuration is correct
- **Check Game Data**: Ensure the game data is complete

## 6. Extension Guide

### 6.1 Adding New Frontend Rules

1. Create a new rule configuration file in the `export-rules` directory
2. Fill in the rule configuration according to the template format
3. Restart the application to load the new rule

### 6.2 Customizing Media File Rules

- Modify the `media` section in the rule configuration file
- Adjust the `source`, `target`, and `dataFileTag` fields

### 6.3 Performance Optimization

- For exporting a large number of files, it is recommended to use parallel processing
- Ensure sufficient disk space
- Avoid network storage as the export target (may affect performance)

## 7. Troubleshooting

### 7.1 Log Viewing

- Backend logs: View the application container's logs
- Frontend logs: Browser developer tool console

### 7.2 Common Errors

- **File Not Found**: Check if the source file path is correct
- **Insufficient Permissions**: Ensure the application has sufficient permissions to access source files and target paths
- **Out of Memory**: For exporting a large number of files, you may need to increase application memory

## 8. Summary

The export functionality provides a flexible and extensible way to export game platform data, supporting multiple frontend rules to meet different user needs. Through rule configuration files, users can easily customize export behavior to adapt to different frontend requirements.

---

**Version**: 1.1
**Last Updated**: 2026-04-21