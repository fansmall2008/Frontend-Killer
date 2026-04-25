# Import Template Documentation

## 1. Function Overview

The import function allows users to import data files and media files from external frontends into the system, supporting multiple frontend formats such as Pegasus, EmulationStation DE (ES-DE), etc. Through import templates, users can flexibly configure import rules to achieve quick migration of data from different frontends.

### Core Features:
- Support for importing data files from multiple frontend formats
- Automatic recognition and processing of different data file formats (XML, text)
- Intelligent media file matching and association
- Flexible field mapping configuration
- Support for header information extraction and processing
- Multi-value field processing
- Automatic file extension recognition

## 2. Template Configuration Files

### 2.1 Configuration File Format

Import template configuration files use JSON format and are stored in the `rules/import-templates/` directory.

### 2.2 Configuration File Structure Details

The configuration file includes the following main parts:

#### 2.2.1 Basic Information

```json
{
  "name": "ES-DE Standard Template",        // Template name
  "description": "XML format data file import template for ES-DE frontend",  // Template description
  "type": "xml",                   // Data file type (xml or text)
  "dataFile": "gamelist.xml",      // Data file name
  "delimiter": "",                 // Delimiter (used for text type)
  "header": {
    // Header configuration
  },
  "fieldMappings": {
    // Field mappings
  },
  "mediaRules": {
    // Media file rules
  },
  "extensions": {
    // Extension configuration
  },
  "gameExtensions": ["chd", "iso", ...]  // Game file extensions
}
```

#### 2.2.2 Header Configuration (`header`)

```json
"header": {
  "enabled": true,                 // Whether to enable header processing
  "format": "xml",                // Header format (xml or key-value)
  "startMarker": "<provider>",     // Header start marker
  "endMarker": "</provider>",     // Header end marker
  "fieldMappings": {
    "platformName": {
      "fields": ["System", "platform"],  // Platform name fields
      "isMultiValue": false        // Whether it's a multi-value field
    },
    "platformSoftware": {
      "fields": ["software"],      // Platform software field
      "isMultiValue": false
    }
  }
}
```

**Field Description:**
- `enabled`: Whether to enable header processing
- `format`: Header format, supports `xml` and `key-value`
- `startMarker`: Header start marker
- `endMarker`: Header end marker
- `fieldMappings`: Platform information field mappings

#### 2.2.3 Field Mappings (`fieldMappings`)

```json
"fieldMappings": {
  "name": {
    "fields": ["name"],            // Game name fields
    "isMultiValue": false
  },
  "description": {
    "fields": ["desc"],            // Game description fields
    "isMultiValue": false
  },
  "releaseDate": {
    "fields": ["releasedate"],     // Release date fields
    "isMultiValue": false
  },
  "files": {
    "fields": ["path"],            // Game file path fields
    "isMultiValue": false
  }
}
```

**Field Description:**
- Key name: System internal field name
- `fields`: List of field names in external data files, matched in order
- `isMultiValue`: Whether it's a multi-value field
- `valuePrefix`: Optional, value prefix for multi-value fields

#### 2.2.4 Media Rules (`mediaRules`)

```json
"mediaRules": {
  "box2dfront": [                  // Game box cover
    "media/{gameName}/boxFront.{ext}",
    "media/{gameName}/box_front.{ext}",
    "media/boxFront/{gameName}.{ext}"
  ],
  "screenshot": [                  // Game screenshot
    "media/{gameName}/screenshot.{ext}",
    "media/screenshots/{gameName}.{ext}"
  ]
}
```

**Field Description:**
- Key name: Media type
- Value: List of media file path templates, matched in order

#### 2.2.5 Extension Configuration (`extensions`)

```json
"extensions": {
  "image": ["png", "jpg", "jpeg", "gif", "webp"],  // Image file extensions
  "video": ["mp4", "mkv", "avi", "wmv", "webm"]    // Video file extensions
}
```

**Field Description:**
- `image`: Supported image file extensions
- `video`: Supported video file extensions

#### 2.2.6 Game File Extensions (`gameExtensions`)

```json
"gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]
```

**Field Description:**
- List of supported game file extensions

### 2.3 Complete Configuration Examples

#### 2.3.1 ES-DE (XML) Configuration Example

```json
{
  "name": "ES-DE Standard Template",                     // Template name
  "description": "XML format data file import template for ES-DE frontend",  // Template description
  "type": "xml",                                // Data file type (xml)
  "dataFile": "gamelist.xml",                   // Data file name
  "delimiter": "",                              // No delimiter needed for XML format
  "header": {
    "enabled": true,                              // Enable header processing
    "format": "xml",                             // Header format is XML
    "startMarker": "<provider>",                 // Header start marker
    "endMarker": "</provider>",                 // Header end marker
    "fieldMappings": {                           // Platform information field mappings
      "platformName": {                          // Platform name
        "fields": ["System", "platform"],       // Field names to try matching
        "isMultiValue": false                    // Not a multi-value field
      },
      "platformSoftware": {                      // Platform software
        "fields": ["software"],                  // Field names to try matching
        "isMultiValue": false
      },
      "platformDatabase": {                      // Platform database
        "fields": ["database"],                  // Field names to try matching
        "isMultiValue": false
      },
      "platformWeb": {                           // Platform website
        "fields": ["web"],                       // Field names to try matching
        "isMultiValue": false
      }
    }
  },
  "fieldMappings": {                             // Game field mappings
    "name": {                                    // Game name
      "fields": ["name"],                        // Field names to try matching
      "isMultiValue": false
    },
    "description": {                              // Game description
      "fields": ["desc"],                        // Field names to try matching
      "isMultiValue": false
    },
    "releaseDate": {                              // Release date
      "fields": ["releasedate"],                 // Field names to try matching
      "isMultiValue": false
    },
    "developer": {                                // Developer
      "fields": ["developer"],                   // Field names to try matching
      "isMultiValue": false
    },
    "publisher": {                                // Publisher
      "fields": ["publisher"],                   // Field names to try matching
      "isMultiValue": false
    },
    "genre": {                                    // Game genre
      "fields": ["genre"],                       // Field names to try matching
      "isMultiValue": false
    },
    "players": {                                  // Number of supported players
      "fields": ["players"],                     // Field names to try matching
      "isMultiValue": false
    },
    "files": {                                    // Game file path
      "fields": ["path"],                        // Field names to try matching
      "isMultiValue": false
    }
  },
  "mediaRules": {                                // Media file matching rules
    "box2dfront": [                              // Game box cover
      "media/{gameName}/boxFront.{ext}",         // Path template 1
      "media/{gameName}/box_front.{ext}",        // Path template 2
      "media/boxFront/{gameName}.{ext}",         // Path template 3
      "media/images/{gameName}.{ext}"            // Path template 4
    ],
    "box2dback": [                               // Game box back
      "media/{gameName}/boxBack.{ext}",
      "media/{gameName}/box_back.{ext}",
      "media/boxBack/{gameName}.{ext}"
    ],
    "box3d": [                                   // 3D game box
      "media/{gameName}/box3d.{ext}",
      "media/box3d/{gameName}.{ext}"
    ],
    "screenshot": [                              // Game screenshot
      "media/{gameName}/screenshot.{ext}",
      "media/{gameName}/screen.{ext}",
      "media/screenshots/{gameName}.{ext}",
      "media/screens/{gameName}.{ext}"
    ],
    "video": [                                   // Game video
      "media/{gameName}/video.{ext}",
      "media/{gameName}/trailer.{ext}",
      "media/videos/{gameName}.{ext}",
      "media/{gameName}.{ext}"
    ],
    "wheel": [                                   // Game wheel
      "media/{gameName}/wheel.{ext}",
      "media/wheel/{gameName}.{ext}"
    ],
    "marquee": [                                 // Game marquee
      "media/{gameName}/marquee.{ext}",
      "media/{gameName}/logo.{ext}",
      "media/logos/{gameName}.{ext}",
      "media/marquees/{gameName}.{ext}"
    ],
    "fanart": [                                  // Game fanart
      "media/{gameName}/fanart.{ext}",
      "media/fanart/{gameName}.{ext}"
    ]
  },
  "extensions": {                                // File extension configuration
    "image": ["png", "jpg", "jpeg", "gif", "webp"],  // Supported image extensions
    "video": ["mp4", "mkv", "avi", "wmv", "webm"]    // Supported video extensions
  },
  "gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]  // Supported game file extensions
}
```

#### 2.3.2 Pegasus (Text) Configuration Example

```json
{
  "name": "Pegasus Standard Template",                    // Template name
  "description": "Data file and media file import template for Pegasus frontend",  // Template description
  "type": "text",                               // Data file type (text)
  "dataFile": "metadata.pegasus.txt",           // Data file name
  "delimiter": ":",                             // Field delimiter
  "header": {
    "enabled": true,                              // Enable header processing
    "format": "key-value",                       // Header format is key-value
    "startMarker": "collection:",                // Header start marker
    "endMarker": "",                             // Header end marker (empty means until first game entry)
    "fieldMappings": {                           // Platform information field mappings
      "platformName": {                          // Platform name
        "fields": ["name", "title", "platform", "collection"],  // Field names to try matching
        "isMultiValue": false                    // Not a multi-value field
      },
      "platformDescription": {                   // Platform description
        "fields": ["description", "desc"],      // Field names to try matching
        "isMultiValue": false
      },
      "platformLaunchCommand": {                 // Platform launch command
        "fields": ["launch", "command"],        // Field names to try matching
        "isMultiValue": false
      },
      "platformSortBy": {                        // Platform sort field
        "fields": ["sort-by", "sort"],          // Field names to try matching
        "isMultiValue": false
      }
    }
  },
  "fieldMappings": {                             // Game field mappings
    "name": {                                    // Game name
      "fields": ["title", "name", "game"],     // Field names to try matching (in order)
      "isMultiValue": false
    },
    "description": {                              // Game description
      "fields": ["description", "desc"],        // Field names to try matching
      "isMultiValue": false
    },
    "releaseDate": {                              // Release date
      "fields": ["release", "releasedate", "date"],  // Field names to try matching
      "isMultiValue": false
    },
    "developer": {                                // Developer
      "fields": ["developer", "dev"],           // Field names to try matching
      "isMultiValue": false
    },
    "publisher": {                                // Publisher
      "fields": ["publisher", "pub"],           // Field names to try matching
      "isMultiValue": false
    },
    "genre": {                                    // Game genre
      "fields": ["genre", "category"],          // Field names to try matching
      "isMultiValue": false
    },
    "players": {                                  // Number of supported players
      "fields": ["players", "player"],          // Field names to try matching
      "isMultiValue": false
    },
    "files": {                                    // Game file path
      "fields": ["files", "file"],              // Field names to try matching
      "isMultiValue": true,                      // Is a multi-value field
      "valuePrefix": "  "                        // Value prefix for multi-value field
    }
  },
  "mediaRules": {                                // Media file matching rules
    "box2dfront": [                              // Game box cover
      "media/{gameName}/boxFront.{ext}",         // Path template 1
      "media/{gameName}/box_front.{ext}",        // Path template 2
      "media/boxFront/{gameName}.{ext}",         // Path template 3
      "media/images/{gameName}.{ext}"            // Path template 4
    ],
    "box2dback": [                               // Game box back
      "media/{gameName}/boxBack.{ext}",
      "media/{gameName}/box_back.{ext}",
      "media/boxBack/{gameName}.{ext}"
    ],
    "box3d": [                                   // 3D game box
      "media/{gameName}/box3d.{ext}",
      "media/box3d/{gameName}.{ext}"
    ],
    "screenshot": [                              // Game screenshot
      "media/{gameName}/screenshot.{ext}",
      "media/{gameName}/screen.{ext}",
      "media/screenshots/{gameName}.{ext}",
      "media/screens/{gameName}.{ext}"
    ],
    "video": [                                   // Game video
      "media/{gameName}/video.{ext}",
      "media/{gameName}/trailer.{ext}",
      "media/videos/{gameName}.{ext}",
      "media/{gameName}.{ext}"
    ],
    "wheel": [                                   // Game wheel
      "media/{gameName}/wheel.{ext}",
      "media/wheel/{gameName}.{ext}"
    ],
    "marquee": [                                 // Game marquee
      "media/{gameName}/marquee.{ext}",
      "media/{gameName}/logo.{ext}",
      "media/logos/{gameName}.{ext}",
      "media/marquees/{gameName}.{ext}"
    ],
    "fanart": [                                  // Game fanart
      "media/{gameName}/fanart.{ext}",
      "media/fanart/{gameName}.{ext}"
    ]
  },
  "extensions": {                                // File extension configuration
    "image": ["png", "jpg", "jpeg", "gif", "webp"],  // Supported image extensions
    "video": ["mp4", "mkv", "avi", "wmv", "webm"]    // Supported video extensions
  },
  "gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]  // Supported game file extensions
}
```

### 2.4 Supported Variables

#### 2.4.1 Path Variables

- `{gameName}`: Game name
- `{ext}`: File extension

### 2.5 Supported Fields

#### 2.5.1 Platform Fields

- `platformName`: Platform name
- `platformSoftware`: Platform software
- `platformDatabase`: Platform database
- `platformWeb`: Platform website
- `platformLaunchCommand`: Platform launch command
- `platformSortBy`: Platform sort field
- `platformDescription`: Platform description

#### 2.5.2 Game Fields

- `name`: Game name
- `description`: Game description
- `releaseDate`: Release date
- `developer`: Developer
- `publisher`: Publisher
- `genre`: Game genre
- `players`: Number of supported players
- `files`: Game file path

#### 2.5.3 Media Fields

- `box2dfront`: Game box cover
- `box2dback`: Game box back
- `box3d`: 3D game box
- `screenshot`: Game screenshot
- `video`: Game video
- `wheel`: Game wheel
- `marquee`: Game marquee
- `fanart`: Game fanart

### 2.6 Supported Formats

#### 2.6.1 xml Format

XML format, suitable for ES-DE and other frontends:

```json
{
  "type": "xml",
  "dataFile": "gamelist.xml",
  "delimiter": ""
}
```

#### 2.6.2 text Format

Text format, suitable for Pegasus and other frontends:

```json
{
  "type": "text",
  "dataFile": "metadata.pegasus.txt",
  "delimiter": ":"
}
```

### 2.7 Media File Matching Mechanism

1. **Path Template Parsing**: The system parses the media file path templates in the configuration file, replacing variables
2. **Extension Matching**: Tries different file extensions according to `extensions` configuration
3. **Order Matching**: Tries different path templates in the configured order until a matching file is found
4. **Relative Path Processing**: Supports paths relative to the data file
5. **Absolute Path Support**: Also supports absolute paths

### 2.8 Multi-value Matching Mechanism

In the `fieldMappings` section of the template configuration file, you can specify multiple external field names for each field, and the system will match them in order until a non-empty value is found. For example:

```json
"name": {
  "fields": ["title", "name", "game"],
  "isMultiValue": false
}
```

This means it will first try to use the `title` field, if empty then use the `name` field, and if empty then use the `game` field.

## 3. Usage Guide

### 3.1 Accessing the Import Page

1. Click the "Import Game Data" button from the home page
2. Or directly access `/import.html`

### 3.2 Import Configuration

1. **Select Template**: Choose the appropriate import template from the dropdown menu
2. **Select Data File**: Upload or specify the data file path
3. **Select Media File Directory**: Specify the directory where media files are located
4. **Select Target Platform**: Choose the target platform to import to

### 3.3 Execute Import

Click the "Import" button to start the import process, and the system will display real-time progress and detailed logs.

## 4. Technical Implementation

### 4.1 Backend Implementation

- **Template Loading**: `ImportTemplateService` is responsible for loading and parsing import templates
- **Import Service**: `ImportService` is responsible for executing import operations
- **File Processing**: Supports data file parsing, media file matching, and game data import
- **API Interface**: `ImportController` provides REST API for import-related operations

### 4.2 Frontend Implementation

- **Import Configuration Interface**: `import.html` provides a user-friendly import configuration interface
- **Progress Feedback**: Real-time display of import progress and detailed logs
- **Responsive Design**: Adapts to different screen sizes

## 5. Common Issues and Solutions

### 5.1 Import Failure

- **Check Data File**: Ensure the data file format is correct
- **Check Template Configuration**: Ensure the correct import template is selected
- **Check File Permissions**: Ensure the application has sufficient permissions to access data files and media files

### 5.2 Media File Mismatch

- **Check Media Rules**: Ensure the media rules in the template match the actual file structure
- **Check File Paths**: Ensure media file paths are correct
- **Check File Extensions**: Ensure media file extensions are in the configuration

### 5.3 Field Mapping Error

- **Check Field Mappings**: Ensure field mappings in the template match field names in the data file
- **Check Data File Format**: Ensure the data file format meets template requirements

## 6. Extension Guide

### 6.1 Adding New Import Templates

1. Create a new template configuration file in the `rules/import-templates/` directory
2. Fill in the configuration according to the template format
3. Restart the application to load the new template

### 6.2 Customizing Media Rules

- Modify the `mediaRules` section in the template configuration file
- Adjust media file path templates to match the actual file structure

### 6.3 Performance Optimization

- For importing a large number of files, it is recommended to use parallel processing
- Ensure sufficient disk space
- Avoid network storage as import source (may affect performance)

## 7. Troubleshooting

### 7.1 Log Viewing

- Backend logs: Check the application container logs
- Frontend logs: Browser developer tools console

### 7.2 Common Errors

- **File Not Found**: Check if data file and media file paths are correct
- **Insufficient Permissions**: Ensure the application has sufficient permissions to access files
- **Out of Memory**: For importing a large number of files, you may need to increase application memory

## 8. Summary

The import function provides a flexible and extensible way to import game data from external frontends, supporting multiple frontend formats to meet different user needs. Through import templates, users can easily configure import rules to achieve quick migration of data from different frontends.

---

**Version**: 1.0
**Last Updated**: 2026-04-24