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
  "frontend": "esde",                // Frontend identifier
  "name": "ES-DE Standard Template",           // Template name
  "version": "1.0",                  // Template version
  "description": "XML format data file import template for ES-DE frontend",  // Template description
  "type": "xml",                     // Data file type (xml, text, or none)
  "dataFile": "gamelist.xml",        // Data file name
  "delimiter": "",                   // Delimiter (used for text type)
  "rules": {
    // Rules configuration
  }
}
```

**Field Description:**
- `frontend`: Frontend identifier, used to indicate which frontend system this template is for
- `name`: Template name
- `version`: Template version number
- `description`: Template description
- `type`: Data file type, supports `xml`, `text`, `none`
- `dataFile`: Data file name
- `delimiter`: Delimiter, used for text type data files
- `rules`: Rules configuration wrapper

#### 2.2.2 Rules Configuration (`rules`)

`rules` is the wrapper for rules configuration, containing the following parts:

```json
"rules": {
  "header": {
    // Header configuration
  },
  "fieldMappings": {
    // Field mappings
  },
  "media": {
    // Media file rules
  },
  "extensions": {
    // Extension configuration
  },
  "gameExtensions": ["chd", "iso", ...]  // Game file extensions
}
```

#### 2.2.3 Header Configuration (`rules.header`)

```json
"header": {
  "enabled": true,                 // Whether to enable header processing
  "format": "xml",                // Header format (xml or key-value)
  "startMarker": "<provider>",     // Header start marker
  "endMarker": "</provider>",     // Header end marker
  "structure": [                   // Header structure template
    "collection: {platform.name}",
    "sort-by: {platform.sortBy}"
  ],
  "fields": {                      // Platform information field mappings
    "platform.name": {
      "fields": ["System", "platform"],  // Platform name fields
      "isMultiValue": false        // Whether it's a multi-value field
    },
    "platform.software": {
      "fields": ["software"],      // Platform software fields
      "isMultiValue": false
    }
  }
}
```

**Field Description:**
- `enabled`: Whether to enable header processing
- `format`: Header format, supports `xml`, `key-value`, `none`
- `startMarker`: Header start marker
- `endMarker`: Header end marker
- `structure`: Header structure template list
- `fields`: Platform information field mappings

#### 2.2.4 Field Mappings (`rules.fieldMappings`)

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
- `transform`: Optional, field value transformation rules, supporting the following options:
  - `path`: Path format transformation (`no`=without `./`, `yes`=with `./`, `keep`=keep original)
  - `trim`: Whether to trim leading/trailing spaces (`true`/`false`)
  - `case`: Case conversion (`upper`=uppercase, `lower`=lowercase, `none`=no conversion)
  - `replace`: String replacement (object with `from` and `to` fields)

**Built-in Path Transform Rules**:

For convenience, the system provides pre-configured `transform` settings for path-related fields in all built-in import templates:
- Path fields like `files`, `image`, `video`, `thumbnail`, `marquee` are set to `path: "no"` (without `./` prefix) by default
- This ensures uniform path format during import, regardless of the original frontend format

#### 2.2.5 Media File Rules (`rules.media`)

```json
"media": {
  "boxFront": {
    "source": "boxFront",          // Media source field
    "rules": [                     // Media file path templates
      "media/{gameName}/boxFront.{ext}",
      "media/{gameName}/box_front.{ext}",
      "media/boxFront/{gameName}.{ext}"
    ]
  },
  "screenshot": {
    "source": "screenshot",
    "rules": [
      "media/{gameName}/screenshot.{ext}",
      "media/screenshots/{gameName}.{ext}"
    ]
  }
}
```

**Field Description:**
- Key name: Media type
- `source`: Media source field name
- `rules`: List of media file path templates, matched in order

**Supported Media Types:**
- `boxFront`: Game box front cover
- `boxBack`: Game box back cover
- `box3d`: 3D game box
- `boxFull`: Game box full view
- `screenshot`: Game screenshot
- `video`: Game video
- `wheel`: Game logo/wheel
- `marquee`: Game marquee
- `fanart`: Game fanart
- `titlescreen`: Game title screen
- `banner`: Game banner
- `thumbnail`: Game thumbnail
- `background`: Game background
- `music`: Game music

#### 2.2.6 Extension Configuration (`rules.extensions`)

```json
"extensions": {
  "image": ["png", "jpg", "jpeg", "gif", "webp"],  // Image file extensions
  "video": ["mp4", "mkv", "avi", "wmv", "webm"]    // Video file extensions
}
```

**Field Description:**
- `image`: Supported image file extensions
- `video`: Supported video file extensions

#### 2.2.7 Game File Extensions (`rules.gameExtensions`)

```json
"gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]
```

**Field Description:**
- List of supported game file extensions

### 2.3 Complete Configuration Examples

#### 2.3.1 ES-DE (XML) Configuration Example

```json
{
  "frontend": "esde",
  "name": "ES-DE Standard Template",
  "version": "1.0",
  "description": "XML format data file import template for EmulationStation DE frontend",
  "type": "xml",
  "dataFile": "gamelist.xml",
  "delimiter": "",
  "rules": {
    "header": {
      "enabled": true,
      "format": "xml",
      "startMarker": "<provider>",
      "endMarker": "</provider>",
      "structure": [
        "collection: {platform.name}",
        "sort-by: {platform.sortBy}"
      ],
      "fields": {
        "platform.name": {
          "fields": ["System", "platform"],
          "isMultiValue": false
        },
        "platform.software": {
          "fields": ["software"],
          "isMultiValue": false
        },
        "platform.database": {
          "fields": ["database"],
          "isMultiValue": false
        },
        "platform.web": {
          "fields": ["web"],
          "isMultiValue": false
        }
      }
    },
    "fieldMappings": {
      "name": {
        "fields": ["name"],
        "isMultiValue": false
      },
      "description": {
        "fields": ["desc", "description"],
        "isMultiValue": false
      },
      "releaseDate": {
        "fields": ["releasedate", "release"],
        "isMultiValue": false
      },
      "developer": {
        "fields": ["developer", "dev"],
        "isMultiValue": false
      },
      "publisher": {
        "fields": ["publisher", "pub"],
        "isMultiValue": false
      },
      "genre": {
        "fields": ["genre", "category"],
        "isMultiValue": false
      },
      "players": {
        "fields": ["players", "player"],
        "isMultiValue": false
      },
      "files": {
        "fields": ["path", "file"],
        "isMultiValue": false
      }
    },
    "media": {
      "boxFront": {
        "source": "boxFront",
        "rules": [
          "media/{gameName}/boxFront.{ext}",
          "media/{gameName}/box_front.{ext}",
          "media/boxFront/{gameName}.{ext}",
          "media/images/{gameName}.{ext}"
        ]
      },
      "boxBack": {
        "source": "boxBack",
        "rules": [
          "media/{gameName}/boxBack.{ext}",
          "media/{gameName}/box_back.{ext}",
          "media/boxBack/{gameName}.{ext}"
        ]
      },
      "box3d": {
        "source": "box3d",
        "rules": [
          "media/{gameName}/box3d.{ext}",
          "media/box3d/{gameName}.{ext}"
        ]
      },
      "screenshot": {
        "source": "screenshot",
        "rules": [
          "media/{gameName}/screenshot.{ext}",
          "media/{gameName}/screen.{ext}",
          "media/screenshots/{gameName}.{ext}",
          "media/screens/{gameName}.{ext}"
        ]
      },
      "video": {
        "source": "video",
        "rules": [
          "media/{gameName}/video.{ext}",
          "media/{gameName}/trailer.{ext}",
          "media/videos/{gameName}.{ext}",
          "media/{gameName}.{ext}"
        ]
      },
      "wheel": {
        "source": "wheel",
        "rules": [
          "media/{gameName}/wheel.{ext}",
          "media/wheel/{gameName}.{ext}"
        ]
      },
      "marquee": {
        "source": "marquee",
        "rules": [
          "media/{gameName}/marquee.{ext}",
          "media/{gameName}/logo.{ext}",
          "media/logos/{gameName}.{ext}",
          "media/marquees/{gameName}.{ext}"
        ]
      },
      "fanart": {
        "source": "fanart",
        "rules": [
          "media/{gameName}/fanart.{ext}",
          "media/fanart/{gameName}.{ext}"
        ]
      }
    },
    "extensions": {
      "image": ["png", "jpg", "jpeg", "gif", "webp"],
      "video": ["mp4", "mkv", "avi", "wmv", "webm"]
    },
    "gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]
  }
}
```

#### 2.3.2 Pegasus (Text) Configuration Example

```json
{
  "frontend": "pegasus",
  "name": "Pegasus Standard Template",
  "version": "1.0",
  "description": "Data file and media file import template for Pegasus frontend",
  "type": "text",
  "dataFile": "metadata.pegasus.txt",
  "delimiter": ":",
  "rules": {
    "header": {
      "enabled": true,
      "format": "key-value",
      "startMarker": "collection:",
      "endMarker": "",
      "structure": [
        "collection: {platform.name}",
        "sort-by: {platform.sortBy}",
        "launch: {platform.launch}"
      ],
      "fields": {
        "platform.name": {
          "fields": ["name", "title", "platform", "collection"],
          "isMultiValue": false
        },
        "platform.description": {
          "fields": ["description", "desc"],
          "isMultiValue": false
        },
        "platform.launch": {
          "fields": ["launch", "command"],
          "isMultiValue": false
        },
        "platform.sortBy": {
          "fields": ["sort-by", "sort"],
          "isMultiValue": false
        }
      }
    },
    "fieldMappings": {
      "name": {
        "fields": ["title", "name", "game"],
        "isMultiValue": false
      },
      "description": {
        "fields": ["description", "desc"],
        "isMultiValue": false
      },
      "releaseDate": {
        "fields": ["release", "releasedate", "date"],
        "isMultiValue": false
      },
      "developer": {
        "fields": ["developer", "dev"],
        "isMultiValue": false
      },
      "publisher": {
        "fields": ["publisher", "pub"],
        "isMultiValue": false
      },
      "genre": {
        "fields": ["genre", "category"],
        "isMultiValue": false
      },
      "players": {
        "fields": ["players", "player"],
        "isMultiValue": false
      },
      "files": {
        "fields": ["files", "file"],
        "isMultiValue": true,
        "valuePrefix": "  "
      }
    },
    "media": {
      "boxFront": {
        "source": "boxFront",
        "rules": [
          "media/{gameName}/boxFront.{ext}",
          "media/{gameName}/box_front.{ext}",
          "media/boxFront/{gameName}.{ext}",
          "media/images/{gameName}.{ext}"
        ]
      },
      "boxBack": {
        "source": "boxBack",
        "rules": [
          "media/{gameName}/boxBack.{ext}",
          "media/{gameName}/box_back.{ext}",
          "media/boxBack/{gameName}.{ext}"
        ]
      },
      "box3d": {
        "source": "box3d",
        "rules": [
          "media/{gameName}/box3d.{ext}",
          "media/box3d/{gameName}.{ext}"
        ]
      },
      "screenshot": {
        "source": "screenshot",
        "rules": [
          "media/{gameName}/screenshot.{ext}",
          "media/{gameName}/screen.{ext}",
          "media/screenshots/{gameName}.{ext}",
          "media/screens/{gameName}.{ext}"
        ]
      },
      "video": {
        "source": "video",
        "rules": [
          "media/{gameName}/video.{ext}",
          "media/{gameName}/trailer.{ext}",
          "media/videos/{gameName}.{ext}",
          "media/{gameName}.{ext}"
        ]
      },
      "wheel": {
        "source": "wheel",
        "rules": [
          "media/{gameName}/wheel.{ext}",
          "media/wheel/{gameName}.{ext}"
        ]
      },
      "marquee": {
        "source": "marquee",
        "rules": [
          "media/{gameName}/marquee.{ext}",
          "media/{gameName}/logo.{ext}",
          "media/logos/{gameName}.{ext}",
          "media/marquees/{gameName}.{ext}"
        ]
      },
      "fanart": {
        "source": "fanart",
        "rules": [
          "media/{gameName}/fanart.{ext}",
          "media/fanart/{gameName}.{ext}"
        ]
      }
    },
    "extensions": {
      "image": ["png", "jpg", "jpeg", "gif", "webp"],
      "video": ["mp4", "mkv", "avi", "wmv", "webm"]
    },
    "gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]
  }
}
```

### 2.3.3 Skraper-EmulationStation (XML) Configuration Example

```json
{
  "frontend": "skraper-es",
  "name": "Skraper-EmulationStation",
  "version": "1.0",
  "description": "Template for importing Skraper-scraped data for EmulationStation frontend with deep subdirectory media support",
  "type": "xml",
  "dataFile": "gamelist.xml",
  "delimiter": "",
  "rules": {
    "header": {
      "enabled": false
    },
    "fieldMappings": {
      "name": {
        "fields": ["name"],
        "isMultiValue": false
      },
      "description": {
        "fields": ["desc", "description"],
        "isMultiValue": false
      },
      "releaseDate": {
        "fields": ["releasedate", "release"],
        "isMultiValue": false
      },
      "developer": {
        "fields": ["developer", "dev"],
        "isMultiValue": false
      },
      "publisher": {
        "fields": ["publisher", "pub"],
        "isMultiValue": false
      },
      "genre": {
        "fields": ["genre", "category"],
        "isMultiValue": false
      },
      "players": {
        "fields": ["players", "player"],
        "isMultiValue": false
      },
      "rating": {
        "fields": ["rating"],
        "isMultiValue": false
      },
      "hash": {
        "fields": ["hash"],
        "isMultiValue": false
      },
      "files": {
        "fields": ["path", "file"],
        "isMultiValue": false,
        "transform": {
          "path": "no"
        }
      },
      "image": {
        "fields": ["image"],
        "isMultiValue": false,
        "transform": {
          "path": "no"
        }
      },
      "video": {
        "fields": ["video"],
        "isMultiValue": false,
        "transform": {
          "path": "no"
        }
      },
      "thumbnail": {
        "fields": ["thumbnail"],
        "isMultiValue": false,
        "transform": {
          "path": "no"
        }
      },
      "marquee": {
        "fields": ["marquee"],
        "isMultiValue": false,
        "transform": {
          "path": "no"
        }
      }
    },
    "media": {
      "boxFront": {
        "source": "boxFront",
        "rules": [
          "media/box2dfront/{filepath}.{ext}",
          "media/box2dfron/{filepath}.{ext}"
        ],
        "transform": {
          "path": "no"
        }
      },
      "boxBack": {
        "source": "boxBack",
        "rules": [
          "media/box2dback/{filepath}.{ext}"
        ],
        "transform": {
          "path": "no"
        }
      },
      "boxSpine": {
        "source": "boxSpine",
        "rules": [
          "media/box2dside/{filepath}.{ext}"
        ],
        "transform": {
          "path": "no"
        }
      },
      "box3d": {
        "source": "box3d",
        "rules": [
          "media/box3d/{filepath}.{ext}"
        ],
        "transform": {
          "path": "no"
        }
      },
      "boxtexture": {
        "source": "boxtexture",
        "rules": [
          "media/boxtexture/{filepath}.{ext}"
        ],
        "transform": {
          "path": "no"
        }
      },
      "fanart": {
        "source": "fanart",
        "rules": [
          "media/fanart/{filepath}.{ext}"
        ],
        "transform": {
          "path": "no"
        }
      },
      "banner": {
        "source": "banner",
        "rules": [
          "media/images/{filepath}.{ext}"
        ],
        "transform": {
          "path": "no"
        }
      },
      "marquee": {
        "source": "marquee",
        "rules": [
          "media/marquee/{filepath}.{ext}",
          "media/screenmarquee/{filepath}.{ext}",
          "media/screenmarquesmall/{filepath}.{ext}"
        ],
        "transform": {
          "path": "no"
        }
      },
      "screenshot": {
        "source": "screenshot",
        "rules": [
          "media/screenshot/{filepath}.{ext}",
          "media/screenshots/{filepath}.{ext}"
        ],
        "transform": {
          "path": "no"
        }
      },
      "titlescreen": {
        "source": "titlescreen",
        "rules": [
          "media/screenshottitle/{filepath}.{ext}"
        ],
        "transform": {
          "path": "no"
        }
      },
      "steamgrid": {
        "source": "steamgrid",
        "rules": [
          "media/steamgrid/{filepath}.{ext}"
        ],
        "transform": {
          "path": "no"
        }
      },
      "support": {
        "source": "support",
        "rules": [
          "media/support/{filepath}.{ext}",
          "media/supporttexture/{filepath}.{ext}"
        ],
        "transform": {
          "path": "no"
        }
      },
      "video": {
        "source": "video",
        "rules": [
          "media/videos/{filepath}.{ext}",
          "media/video/{filepath}.{ext}"
        ],
        "transform": {
          "path": "no"
        }
      },
      "wheel": {
        "source": "wheel",
        "rules": [
          "media/wheel/{filepath}.{ext}",
          "media/wheelcarbon/{filepath}.{ext}",
          "media/wheelsteel/{filepath}.{ext}"
        ],
        "transform": {
          "path": "no"
        }
      }
    },
    "extensions": {
      "image": ["png", "jpg", "jpeg", "gif", "webp"],
      "video": ["mp4", "mkv", "avi", "wmv", "webm"]
    },
    "gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds", "gb", "gbc", "sms", "gg", "pcengine", "pce", "tg16", "saturn", "ps2", "gamecube", "wii", "xbox", "xbox360", "ps3", "ps4", "switch", "3ds", "psp", "ds", "dreamcast", "arcade", "fba", "mame", "a26", "int"]
  }
}
```

**Skraper-EmulationStation Template Features:**

1. **Deep Subdirectory Support**: Uses `{filepath}` variable to support deep subdirectory structures for both game and media files
2. **Full Media Type Coverage**: Supports all media types generated by Skraper:
   - box2dfront, box2dback, box2dside (2D box art)
   - box3d (3D box art)
   - boxtexture, support, supporttexture (textures)
   - fanart (background images)
   - images (banners)
   - marquee, screenmarquee, screenmarquesmall (title banners)
   - screenshot, screenshottitle (screenshots)
   - steamgrid (Steam grid images)
   - videos (video previews)
   - wheel, wheelcarbon, wheelsteel (wheel icons)

3. **Example Path Matching**:
   - Game path: `./All 2600 ROMs_16MB/4 Game Series Collections/Genre/Shoot 'em Ups/Sort By - 3-D Style/3-D Genesis (USA) (Proto).a26`
   - Media path: `media/images/All 2600 ROMs_16MB/4 Game Series Collections/Genre/Shoot 'em Ups/Sort By - 3-D Style/3-D Genesis (USA) (Proto).png`

### 2.3.4 EmulationStation DE (ES-DE) Configuration Example

```json
{
  "frontend": "esde",
  "name": "EmulationStation DE",
  "version": "1.0",
  "description": "Template for importing EmulationStation DE (ES-DE) data. Note: Please copy the 'downloaded_media' and 'gamelists' folders to the same directory before importing.",
  "type": "xml",
  "dataFile": "gamelist.xml",
  "delimiter": "",
  "rules": {
    "header": {
      "enabled": false
    },
    "fieldMappings": {
      "name": {
        "fields": ["name"],
        "isMultiValue": false
      },
      "description": {
        "fields": ["desc", "description"],
        "isMultiValue": false
      },
      "releaseDate": {
        "fields": ["releasedate", "release"],
        "isMultiValue": false
      },
      "developer": {
        "fields": ["developer", "dev"],
        "isMultiValue": false
      },
      "publisher": {
        "fields": ["publisher", "pub"],
        "isMultiValue": false
      },
      "genre": {
        "fields": ["genre", "category"],
        "isMultiValue": false
      },
      "players": {
        "fields": ["players", "player"],
        "isMultiValue": false
      },
      "rating": {
        "fields": ["rating"],
        "isMultiValue": false
      },
      "hash": {
        "fields": ["hash"],
        "isMultiValue": false
      },
      "files": {
        "fields": ["path", "file"],
        "isMultiValue": false,
        "transform": {
          "path": "no"
        }
      }
    },
    "media": {
      "boxFront": {
        "source": "boxFront",
        "rules": [
          "downloaded_media/{platform}/boxfront/{filepath}.{ext}",
          "downloaded_media/{platform}/covers/{filepath}.{ext}"
        ]
      },
      "boxBack": {
        "source": "boxBack",
        "rules": [
          "downloaded_media/{platform}/boxback/{filepath}.{ext}"
        ]
      },
      "box3d": {
        "source": "box3d",
        "rules": [
          "downloaded_media/{platform}/box3d/{filepath}.{ext}"
        ]
      },
      "screenshot": {
        "source": "screenshot",
        "rules": [
          "downloaded_media/{platform}/screenshot/{filepath}.{ext}"
        ]
      },
      "video": {
        "source": "video",
        "rules": [
          "downloaded_media/{platform}/video/{filepath}.{ext}"
        ]
      },
      "wheel": {
        "source": "wheel",
        "rules": [
          "downloaded_media/{platform}/wheel/{filepath}.{ext}"
        ]
      },
      "marquee": {
        "source": "marquee",
        "rules": [
          "downloaded_media/{platform}/marquee/{filepath}.{ext}"
        ]
      },
      "fanart": {
        "source": "fanart",
        "rules": [
          "downloaded_media/{platform}/fanart/{filepath}.{ext}"
        ]
      },
      "titlescreen": {
        "source": "titlescreen",
        "rules": [
          "downloaded_media/{platform}/titlescreen/{filepath}.{ext}"
        ]
      },
      "manual": {
        "source": "manual",
        "rules": [
          "downloaded_media/{platform}/manual/{filepath}.{ext}"
        ]
      }
    },
    "extensions": {
      "image": ["png", "jpg", "jpeg", "gif", "webp"],
      "video": ["mp4", "mkv", "avi", "wmv", "webm"],
      "manual": ["pdf"]
    },
    "gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds", "gb", "gbc", "sms", "gg", "pcengine", "pce", "tg16", "saturn", "ps2", "gamecube", "wii", "xbox", "xbox360", "ps3", "ps4", "switch", "3ds", "psp", "ds", "dreamcast", "arcade", "fba", "mame"]
  }
}
```

**ES-DE Import Template Features:**

1. **Pre-import Preparation**: Please copy ES-DE's `downloaded_media` and `gamelists` folders to the same directory before importing
2. **Media File Matching**: Uses `{filepath}` variable to support media files with subdirectory structures
3. **Supported Media Types**: boxfront, boxback, box3d, screenshot, video, wheel, marquee, fanart, titlescreen, manual

## 3. Template Variables

Import templates support the following variables:

| Variable | Description |
|----------|-------------|
| `{gameName}` | Game name (without extension) |
| `{filename}` | Same as `{gameName}`, game name (without extension) |
| `{filepath}` | Full game file path (without extension, including subdirectories) |
| `{platform}` | Platform name |
| `{ext}` | File extension |
| `{outputPath}` | Output path |
| `{mediaPath}` | Media file path |
| `{romsPath}` | Game file path |

**Variable Description:**

- `{gameName}` / `{filename}`: Contains only the file name, without path and extension. Example: `path1/path2/game.a26` → `game`
- `{filepath}`: Contains the full path (without extension). Example: `path1/path2/game.a26` → `path1/path2/game`

**Usage Example:**

```json
"rules": {
  "media": {
    "boxFront": {
      "source": "boxFront",
      "rules": [
        "media/{gameName}/boxFront.{ext}",
        "media/{platform}/boxFront/{gameName}.{ext}"
      ]
    }
  }
}
```

**Using `{filepath}` for Subdirectory Media Files:**

When media files are organized according to the directory structure of game files, you can use the `{filepath}` variable:

```json
"rules": {
  "media": {
    "screenshot": {
      "source": "screenshot",
      "rules": [
        "media/screenshot/{filepath}.{ext}"
      ]
    },
    "boxFront": {
      "source": "boxFront",
      "rules": [
        "media/box2dfront/{filepath}.{ext}"
      ]
    },
    "video": {
      "source": "video",
      "rules": [
        "media/videos/{filepath}.{ext}"
      ]
    }
  }
}
```

**Example Explanation:**

For a game path `path1/path2/game.a26`, the system will look for:
- `media/screenshot/path1/path2/game.png`
- `media/box2dfront/path1/path2/game.png`
- `media/videos/path1/path2/game.mp4`

## 4. Media File Matching Mechanism

### 4.1 Matching Process

1. **Template Loading**: System loads the user-selected import template
2. **Data File Parsing**: Parse data files according to template configuration, extract game information
3. **Media File Scanning**: Scan for matching media files according to `media` configuration in template
4. **Extension Matching**: Try different file extensions according to `extensions` configuration
5. **Order Matching**: Try different path templates in the configured order until a matching file is found
6. **Relative Path Processing**: Support paths relative to the data file
7. **Absolute Path Support**: Also support absolute paths

### 4.2 Multi-value Matching Mechanism

In the `fieldMappings` section, you can specify multiple external field names for each field, and the system will match them in order until a non-empty value is found. For example:

```json
"name": {
  "fields": ["title", "name", "game"],
  "isMultiValue": false
}
```

This means it will first try to use the `title` field, if empty then use the `name` field, and if empty then use the `game` field.

## 5. Usage Guide

### 5.1 Accessing the Import Page

1. Click the "Import Game Data" button from the home page
2. Or directly access `/import.html`

### 5.2 Import Configuration

1. **Select Template**: Choose the appropriate import template from the dropdown menu
2. **Select Data File**: Upload or specify the data file path
3. **Select Media File Directory**: Specify the directory where media files are located
4. **Select Target Platform**: Choose the target platform to import to

### 5.3 Execute Import

Click the "Import" button to start the import process, and the system will display real-time progress and detailed logs.

## 6. Technical Implementation

### 6.1 Backend Implementation

- **Template Loading**: `ImportTemplateService` is responsible for loading and parsing import templates
- **Import Service**: `ImportService` is responsible for executing import operations
- **File Processing**: Supports data file parsing, media file matching, and game data import
- **API Interface**: `ImportController` provides REST API for import-related operations

### 6.2 Frontend Implementation

- **Import Configuration Interface**: `import.html` provides a user-friendly import configuration interface
- **Progress Feedback**: Real-time display of import progress and detailed logs
- **Responsive Design**: Adapts to different screen sizes

## 7. Common Issues and Solutions

### 7.1 Import Failure

- **Check Data File**: Ensure the data file format is correct
- **Check Template Configuration**: Ensure the correct import template is selected
- **Check File Permissions**: Ensure the application has sufficient permissions to access data files and media files

### 7.2 Media File Mismatch

- **Check Media Rules**: Ensure the media rules in the template match the actual file structure
- **Check File Paths**: Ensure media file paths are correct
- **Check File Extensions**: Ensure media file extensions are in the configuration

### 7.3 Field Mapping Error

- **Check Field Mappings**: Ensure field mappings in the template match field names in the data file
- **Check Data File Format**: Ensure the data file format meets template requirements

## 8. Extension Guide

### 8.1 Adding New Import Templates

1. Create a new JSON file in the `rules/import-templates/` directory
2. Write the template configuration according to the structure in this document
3. Select the new template in the frontend template dropdown

### 8.2 Customizing Media Rules

According to the actual file structure, add or modify media types and path templates in the `media` section.

### 8.3 Supporting New Frontend Formats

1. Analyze the data file format of the new frontend
2. Create a corresponding import template
3. Configure field mappings and media rules

## 9. Database Field Mapping

### 9.1 Game Table (`Game`) Fields

| Field Name | Description | Import Support | Export Support |
|------------|-------------|----------------|-----------------|
| `gameId` | Game unique identifier | ✅ | ✅ |
| `source` | Game source | ✅ | ✅ |
| `path` | Game relative path | ✅ | ✅ |
| `name` | Game name | ✅ | ✅ |
| `description` | Game description | ✅ | ✅ |
| `translatedName` | Translated game name | ✅ | ✅ |
| `translatedDesc` | Translated game description | ✅ | ✅ |
| `image` | Game image | ✅ | ✅ |
| `video` | Game video | ✅ | ✅ |
| `marquee` | Game marquee | ✅ | ✅ |
| `thumbnail` | Game thumbnail | ✅ | ✅ |
| `manual` | Game manual | ✅ | ✅ |
| `boxFront` | Game box front | ✅ | ✅ |
| `boxBack` | Game box back | ✅ | ✅ |
| `boxSpine` | Game box spine | ✅ | ✅ |
| `boxFull` | Game box full view | ✅ | ✅ |
| `cartridge` | Game cartridge | ✅ | ✅ |
| `logo` | Game logo | ✅ | ✅ |
| `rating` | Game rating | ✅ | ✅ |
| `releasedate` | Game release date | ✅ | ✅ |
| `developer` | Game developer | ✅ | ✅ |
| `publisher` | Game publisher | ✅ | ✅ |
| `genre` | Game genre | ✅ | ✅ |
| `players` | Number of players | ✅ | ✅ |
| `platformType` | Platform type | ✅ | ✅ |
| `platformId` | Platform ID | ✅ | ✅ |
| `scraped` | Is scraped | ✅ | ✅ |
| `edited` | Is edited | ✅ | ✅ |
| `absolutePath` | Game absolute path | ✅ | ✅ |
| `platformPath` | Platform path | ✅ | ✅ |

### 9.2 Platform Table (`Platform`) Fields

| Field Name | Description | Import Support | Export Support |
|------------|-------------|----------------|-----------------|
| `name` | Platform name | ✅ | ✅ |
| `description` | Platform description | ✅ | ✅ |
| `system` | Platform system name | ✅ | ✅ |
| `software` | Platform software | ✅ | ✅ |
| `database` | Platform database | ✅ | ✅ |
| `web` | Platform website | ✅ | ✅ |
| `launch` | Platform launch command | ✅ | ✅ |
| `sortBy` | Sort field | ✅ | ✅ |
| `folderPath` | Platform folder path | ✅ | ✅ |
| `icon` | Platform icon | ✅ | ✅ |
| `image` | Platform image | ✅ | ✅ |

## 10. Summary

The import function provides a flexible and extensible way to import game data from external frontends, supporting multiple frontend formats to meet different user needs. Through import templates, users can easily configure import rules to achieve quick migration of data from different frontends.

---

**Version**: 1.2
**Last Updated**: 2026-05-07

### Changelog

#### v1.2 (2026-05-07)
- Added `{filepath}` template variable for media file matching
  - Supports media files organized in subdirectories
  - For example: `media/screenshot/{filepath}.{ext}` → `media/screenshot/path1/path2/game.png`