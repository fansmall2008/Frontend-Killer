# v3 Template System Documentation

## 1. Overview

v3 is the unified template format version for Frontend Killer, used for importing and exporting data files. Compared to v2, v3 provides a more powerful expression engine, unified field naming system, and more flexible media discovery mechanism.

### 1.1 Key Improvements in v3

- **Unified Field Accessor**: Access the same field via nomcourt (API original value), dbColumn (database column name), or javaField (Java field name)
- **Expression Engine**: Supports function calls, operators, conditional expressions, and more
- **Computed Variable Injection**: Automatically injects computed variables like `filename`, `filepath`
- **Media Discovery Mechanism**: Supports `mediaDiscovery` rules for automatic disk-based media file lookup
- **Multi-disc Game Support**: Automatically expands multi-disc games into separate entries

### 1.2 Template Structure

v3 templates use a unified JSON structure:

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import|export",
    "dataFileType": "data|text",
    "format": "xml|text",
    "dataFile": "gamelist.xml",
    "author": "Author Name",
    "description": "Template description",
    "notes": "Notes"
  },
  "system": {
    // System/platform information configuration
  },
  "game": {
    // Game info and media info configuration
  },
  "output": {
    // Export configuration (export templates only)
  }
}
```

---

## 2. Expression Engine

The v3 template system includes a powerful expression engine that supports functions, operators, and variable references in template values.

### 2.1 Operators

#### 2.1.1 `or` Fallback Operator

Returns the first non-null value, similar to SQL COALESCE:

```
name or filename
box-2D or screenshot or image
```

**Priority**: `or` has the lowest operator priority.

#### 2.1.2 `+` String Concatenation Operator

Concatenates multiple strings:

```
name + ".jpg"
platform.system + "_" + filename
```

**Priority**: `+` has higher priority than `or`, therefore:
- `name or filename + ".jpg"` is equivalent to `name or (filename + ".jpg")`
- Use parentheses to change priority: `(name or filename) + ".jpg"`

### 2.2 Built-in Functions

#### 2.2.1 String Functions

| Function | Syntax | Description | Example |
|----------|--------|-------------|---------|
| `sub` | `sub(str, start[, end])` | Extract substring | `sub(name, 0, 7)` |
| `upper` | `upper(str)` | Convert to uppercase | `upper(name)` |
| `lower` | `lower(str)` | Convert to lowercase | `lower(name)` |
| `trim` | `trim(str)` | Trim whitespace | `trim(desc)` |
| `replace` | `replace(str, from, to)` | String replacement | `replace(path, "\", "/")` |
| `len` | `len(str)` | String length | `len(name)` |

#### 2.2.2 Path Functions

| Function | Syntax | Description | Example |
|----------|--------|-------------|---------|
| `filename` | `filename(path)` | Extract filename (with extension) | `filename(path)` → "game.zip" |
| `stem` | `stem(path)` | Remove extension | `stem(path)` → "game" |
| `ext` | `ext(path)` | Extract extension (with dot) | `ext(path)` → ".zip" |
| `dir` | `dir(path)` | Extract directory | `dir(path)` → "/roms/nes" |

#### 2.2.3 Conditional Functions

| Function | Syntax | Description | Example |
|----------|--------|-------------|---------|
| `default` | `default(field, fallback)` | Use default when field is empty | `default(desc, "No description")` |
| `if` | `if(condition, trueVal, falseVal)` | Conditional expression | `if(video, "Has video", "No video")` |
| `coalesce` | `coalesce(f1, f2, ...)` | Return first non-null value | `coalesce(box-2D, screenshot, image)` |

#### 2.2.4 Date Functions

| Function | Syntax | Description | Example |
|----------|--------|-------------|---------|
| `dateformat` | `dateformat(dateStr, pattern)` | Format date | `dateformat(releasedate, "yyyy-MM-dd")` |

**Supported input formats**:
- `yyyy-MM-dd'T'HH:mm:ss`
- `yyyy-MM-dd HH:mm:ss`
- `yyyy-MM-dd`
- `yyyyMMdd`
- `yyyy`
- `MM/dd/yyyy`
- `dd/MM/yyyy`

### 2.3 Expression Examples

```
# Basic field reference
name
desc
box-2D

# Fallback chain
box-2D or screenshot or image

# String concatenation
name + ".jpg"
platform.system + "_" + filename

# Function calls
upper(name)
replace(path, "\", "/")
stem(path)

# Nested calls
trim(replace(name, " ", "_"))

# Conditional expressions
if(video, "Has video", "No video")
default(desc, "No description")

# Complex expressions
(name or filename) + ".jpg"
coalesce(box-2D, screenshot, image)
```

---

## 3. Available Variables

### 3.1 Game Field Variables

All game fields can be referenced directly by field name. Three naming conventions are supported:

#### 3.1.1 Basic Fields

| Field | Description | Example Value |
|-------|-------------|---------------|
| `name` | Game name | "Super Mario Bros" |
| `desc` | Game description | "A classic platform game..." |
| `releasedate` | Release date | "1985-09-13" |
| `developer` | Developer | "Nintendo" |
| `publisher` | Publisher | "Nintendo" |
| `genre` | Genre | "Platform" |
| `players` | Player count | "1-2" |
| `rating` | Rating | "0.85" |
| `path` | Game path | "./roms/nes/supermario.nes" |
| `lang` | Language | "en" |
| `region` | Region | "us" |
| `sort-by` | Sort field | "Mario" |
| `gameId` | Game ID | "12345" |
| `hash` | File hash | "abc123..." |
| `crc32` | CRC32 checksum | "12345678" |
| `md5` | MD5 checksum | "abcdef..." |
| `source` | Data source | "screenscraper" |
| `platformType` | Platform type | "console" |

#### 3.1.2 Computed Fields (Read-only)

| Field | Description | Computation |
|-------|-------------|-------------|
| `filename` | Filename without extension | Extracted from `path` |
| `releaseYear` | Release year | First 4 chars of `releasedate` |

#### 3.1.3 Field Aliases

The following aliases can be used instead of standard field names:

| Alias | Maps to |
|-------|---------|
| `description` | `desc` |
| `file` | `path` |
| `region` | `lang` |
| `category` | `genre` |
| `releaseDate` | `releasedate` |
| `manuel` | `manual` |

### 3.2 Platform Variables

Access platform information via the `platform.` prefix:

| Variable | Description | Example Value |
|----------|-------------|---------------|
| `platform.system` | Platform system name | "nes" |
| `platform.name` | Platform name | "Nintendo Entertainment System" |
| `platform.launch` | Launch command | "retroarch -L nes_libretro.dll" |
| `platform.software` | Platform software | "RetroArch" |
| `platform.database` | Database name | "screenscraper" |
| `platform.web` | Website URL | "https://screenscraper.fr" |
| `platform.folderPath` | Platform folder path | "/roms/nes" |

### 3.3 Computed Variables (Auto-injected during Import)

The system automatically injects the following computed variables in import templates:

| Variable | Description | Computation |
|----------|-------------|-------------|
| `filename` | Filename without extension | Extracted from `path` |
| `filepath` | File path without extension and `./` prefix | Extracted from `path` |

**Example**:
- `path` = `./roms/nes/supermario.nes`
- `filename` = `supermario`
- `filepath` = `roms/nes/supermario`

---

## 4. Field Name Mapping Table

The v3 system supports accessing the same field via three naming conventions. Below is the complete mapping table.

### 4.1 Media Type Field Mapping Table

| nomcourt (API Original) | dbColumn (DB Column) | javaField (Java Field) | Category | Format |
|-------------------------|---------------------|------------------------|----------|--------|
| `bezel-16-9-cocktail` | `bezel_16_9_cocktail` | `bezel169Cocktail` | Bezels | image |
| `bezel-16-9` | `bezel_16_9` | `bezel169` | Bezels | image |
| `bezel-16-9-v` | `bezel_16_9_v` | `bezel169V` | Bezels | image |
| `bezel-4-3-cocktail` | `bezel_4_3_cocktail` | `bezel43Cocktail` | Bezels | image |
| `bezel-4-3` | `bezel_4_3` | `bezel43` | Bezels | image |
| `bezel-4-3-v` | `bezel_4_3_v` | `bezel43V` | Bezels | image |
| `box-3D` | `box_3d` | `box3D` | Boitiers | image |
| `box-texture` | `box_texture` | `boxTexture` | Boitiers | image |
| `box-2D-back` | `box_2d_back` | `box2dBack` | Elements Boitiers | image |
| `box-2D` | `box_2d` | `box2d` | Elements Boitiers | image |
| `box-2D-side` | `box_2d_side` | `box2dSide` | Elements Boitiers | image |
| `wheel-hd` | `wheel_hd` | `wheelHd` | Logos (Wheels) | image |
| `wheel` | `wheel` | `wheel` | Logos (Wheels) | image |
| `wheel-carbon` | `wheel_carbon` | `wheelCarbon` | Logos (Wheels) | image |
| `wheel-steel` | `wheel_steel` | `wheelSteel` | Logos (Wheels) | image |
| `marquee` | `marquee` | `marquee` | Marquee | image |
| `screenmarqueesmall` | `screenmarqueesmall` | `screenmarqueesmall` | Marquee | image |
| `screenmarquee` | `screenmarquee` | `screenmarquee` | Marquee | image |
| `fanart` | `fanart` | `fanart` | Médias | image |
| `overlay` | `overlay` | `overlay` | Médias | image |
| `ss` | `ss` | `ss` | Médias | image |
| `sstitle` | `sstitle` | `sstitle` | Médias | image |
| `steamgrid` | `steamgrid` | `steamgrid` | Médias | image |
| `video` | `video` | `video` | Médias | video |
| `video-normalized` | `video_normalized` | `videoNormalized` | Médias | video |
| `ssdmd` | `ssdmd` | `ssdmd` | Médias Pincab | image |
| `ssfronton16-9` | `ssfronton16_9` | `ssfronton169` | Médias Pincab | image |
| `ssfronton1-1` | `ssfronton1_1` | `ssfronton11` | Médias Pincab | image |
| `ssfronton4-3` | `ssfronton4_3` | `ssfronton43` | Médias Pincab | image |
| `sstable` | `sstable` | `sstable` | Médias Pincab | image |
| `sstopper` | `sstopper` | `sstopper` | Médias Pincab | image |
| `videodmd` | `videodmd` | `videodmd` | Médias Pincab | video |
| `videofronton4-3` | `videofronton4_3` | `videofronton43` | Médias Pincab | video |
| `videofronton16-9` | `videofronton16_9` | `videofronton169` | Médias Pincab | video |
| `videotable4k` | `videotable4k` | `videotable4k` | Médias Pincab | video |
| `videotable` | `videotable` | `videotable` | Médias Pincab | video |
| `videotopper` | `videotopper` | `videotopper` | Médias Pincab | video |
| `wheel-tarcisios` | `wheel_tarcisios` | `wheelTarcisios` | Médias Pincab | image |
| `figurine` | `figurine` | `figurine` | Médias Secondaires | image |
| `flyer` | `flyer` | `flyer` | Médias Secondaires | image |
| `manuel` | `manuel` | `manuel` | Médias Secondaires | doc |
| `maps` | `maps` | `maps` | Médias Secondaires | image |
| `background` | `background` | `background` | Images Secondaires | image |
| `pictoliste` | `pictoliste` | `pictoliste` | Images Secondaires | image |
| `pictomonochrome` | `pictomonochrome` | `pictomonochrome` | Images Secondaires | image |
| `pictocouleur` | `pictocouleur` | `pictocouleur` | Images Secondaires | image |
| `mixrbv1` | `mixrbv1` | `mixrbv1` | Mixes | image |
| `mixrbv2` | `mixrbv2` | `mixrbv2` | Mixes | image |
| `box-scan` | `box_scan` | `boxScan` | Sources | image |
| `support-scan` | `support_scan` | `supportScan` | Sources | image |
| `support-2D` | `support_2d` | `support2d` | Supports | image |
| `support-texture` | `support_texture` | `supportTexture` | Supports | image |
| `themehb` | `themehb` | `themehb` | Themes | theme |
| `themehs` | `themehs` | `themehs` | Themes | theme |

### 4.2 Naming Convention Rules

- **nomcourt**: ScreenScraper API original value, preserving original case and hyphens (e.g., `box-2D`)
- **dbColumn**: Database column name, replacing `-` with `_` in nomcourt, all lowercase (e.g., `box_2d`)
- **javaField**: Java field name, converting dbColumn from snake_case to camelCase (e.g., `box2d`)

### 4.3 Field Access Examples

All three approaches access the same field:

```
# Using nomcourt
box-2D

# Using dbColumn
box_2d

# Using javaField
box2d
```

The system automatically recognizes and maps them to the same field.

---

## 5. Import Template Configuration

### 5.1 Import Template Structure

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import",
    "dataFileType": "data|text",
    "format": "xml|text",
    "dataFile": "gamelist.xml",
    "delimiter": ":"
  },
  "system": {
    "systemTag": "provider",
    "fields": {
      "platform.system": ["system"],
      "platform.software": ["software"]
    }
  },
  "game": {
    "gameStartMarker": "game",
    "gameInfo": {
      "name": ["name", "sortname"],
      "desc": ["desc", "description"]
    },
    "mediaInfo": {
      "box-2D": ["boxart", "image"],
      "ss": ["screenshot"]
    },
    "mediaDiscovery": {
      // Media discovery configuration (optional)
    }
  }
}
```

### 5.2 gameInfo Field Mapping

`gameInfo` defines the mapping from data file fields to system fields:

```json
"gameInfo": {
  "name": ["name", "sortname"],
  "desc": ["desc", "description"],
  "releasedate": ["releasedate"],
  "developer": ["developer"],
  "publisher": ["publisher"],
  "genre": ["genre"],
  "players": ["players"],
  "rating": ["rating"],
  "path": ["path"],
  "lang": ["lang"],
  "region": ["region"],
  "sort-by": ["sortname"]
}
```

**Note**:
- Keys (e.g., `name`) are system field names
- Values (e.g., `["name", "sortname"]`) are lists of possible data file field names, matched by priority

### 5.3 mediaInfo Field Mapping

`mediaInfo` defines the mapping from media file paths to system media fields:

```json
"mediaInfo": {
  "box-2D": ["boxart", "image"],
  "ss": ["screenshot"],
  "video": ["video"],
  "wheel": ["wheel", "marquee"]
}
```

### 5.4 mediaDiscovery Configuration

`mediaDiscovery` is used to automatically find media files on disk:

```json
"mediaDiscovery": {
  "enabled": true,
  "baseDir": "media",
  "subDirPattern": "{filename}",
  "extensions": ["png", "jpg", "jpeg", "gif", "webp", "mp4", "mkv", "avi", "webm"],
  "rules": {
    "box-2D": [
      "{filename}/boxFront.{ext}",
      "{filename}/box_front.{ext}",
      "{filename}/box-2D.{ext}",
      "boxFront/{filename}.{ext}",
      "box2dfront/{filename}.{ext}",
      "images/{filename}.{ext}"
    ],
    "video": [
      "{filename}/video.{ext}",
      "{filename}/trailer.{ext}",
      "videos/{filename}.{ext}"
    ]
  }
}
```

**Configuration Notes**:
- `enabled`: Whether to enable media discovery
- `baseDir`: Base directory for media files
- `subDirPattern`: Subdirectory pattern, supports `{filename}` variable
- `extensions`: List of file extensions to search
- `rules`: Search rules for each media type, supports `{filename}` and `{ext}` variables

**Variable Substitution**:
- `{filename}`: Game filename without extension
- `{ext}`: Current file extension being searched

**Example**:
For game `supermario.nes`, the system searches for `box-2D` media in this order:
1. `media/supermario/boxFront.png`
2. `media/supermario/boxFront.jpg`
3. `media/supermario/box_front.png`
4. ...
5. `boxFront/supermario.png`
6. `box2dfront/supermario.png`
7. `images/supermario.png`

---

## 6. Export Template Configuration

### 6.1 Export Template Structure

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "export",
    "dataFileType": "data|text",
    "delimiter": ": ",
    "dataFile": "metadata.pegasus.txt"
  },
  "system": {
    "header": [
      "collection: {platform.system}",
      "sort-by: 064",
      "{platform.launch}",
      ""
    ],
    "fields": {
      "collection": "platform.system",
      "launch": "platform.launch"
    }
  },
  "game": {
    "gameStartMarker": "game:",
    "entrySeparator": "\n\n",
    "gameInfo": {
      "game": "name",
      "file": "path",
      "description": "desc"
    },
    "mediaInfo": {
      "assets.box_front": "box-2D",
      "assets.screenshot": "ss"
    }
  },
  "output": {
    "filename": "metadata.pegasus.txt",
    "pathFormat": "relative",
    "directory": {
      "roms": "{outputPath}/{platform.system}",
      "media": "{outputPath}/{platform.system}/media"
    },
    "media": {
      "video": {
        "source": "video",
        "target": "{mediaPath}/{gameName}/video.mp4"
      }
    }
  }
}
```

### 6.2 system.header Configuration

`header` defines the header lines of the data file:

```json
"header": [
  "collection: {platform.system}",
  "sort-by: 064",
  "{platform.launch}",
  ""
]
```

**Note**:
- Each line is a string
- Supports `{platform.xxx}` variables
- Empty string represents a blank line

### 6.3 game.gameInfo Configuration

`gameInfo` defines the mapping from system fields to data file fields:

```json
"gameInfo": {
  "game": "name",
  "file": "path",
  "sort-by": "sort-by",
  "developer": "developer",
  "description": "desc"
}
```

**Note**:
- Keys (e.g., `game`) are field names in the data file
- Values (e.g., `name`) are system field names or expressions

**Expression Support**:

```json
"gameInfo": {
  "title": "name or filename",
  "year": "sub(releasedate, 0, 4)",
  "path": "replace(path, '\\', '/')"
}
```

### 6.4 game.mediaInfo Configuration

`mediaInfo` defines the mapping from data file media tags to system media fields:

```json
"mediaInfo": {
  "assets.box_front": "box-2D",
  "assets.screenshot": "ss",
  "assets.video": "video"
}
```

**Note**:
- Keys (e.g., `assets.box_front`) are media tags in the data file
- Values (e.g., `box-2D`) are system media field names

### 6.5 output Configuration

`output` defines export behavior:

```json
"output": {
  "filename": "metadata.pegasus.txt",
  "pathFormat": "relative",
  "directory": {
    "roms": "{outputPath}/{platform.system}",
    "media": "{outputPath}/{platform.system}/media"
  },
  "media": {
    "video": {
      "source": "video",
      "target": "{mediaPath}/{gameName}/video.mp4"
    }
  },
  "m3u": {
    "enabled": false
  }
}
```

**Configuration Notes**:
- `filename`: Output data file name
- `pathFormat`: Path format (`relative` or `absolute`)
- `directory`: Directory structure configuration
  - `roms`: ROM file directory
  - `media`: Media file directory
- `media`: Media file copy rules
  - `source`: Source media field
  - `target`: Target path template
- `m3u`: M3U playlist processing configuration

**Supported Variables**:
- `{outputPath}`: Export path
- `{platform.system}`: Platform system name
- `{mediaPath}`: Media directory path
- `{gameName}`: Game name

---

## 7. Complete Examples

### 7.1 ES-DE Import Template Example

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import",
    "dataFileType": "data",
    "format": "xml",
    "dataFile": "gamelist.xml",
    "author": "Frontend-Killer",
    "description": "EmulationStation / ES-DE import template (v3 unified format)"
  },
  "system": {
    "systemTag": "provider",
    "fields": {
      "platform.system": ["system"],
      "platform.software": ["software"]
    }
  },
  "game": {
    "gameStartMarker": "game",
    "gameInfo": {
      "name": ["name", "sortname"],
      "desc": ["desc", "description"],
      "releasedate": ["releasedate"],
      "developer": ["developer"],
      "publisher": ["publisher"],
      "genre": ["genre"],
      "players": ["players"],
      "rating": ["rating"],
      "path": ["path"],
      "lang": ["lang"],
      "region": ["region"],
      "sort-by": ["sortname"],
      "gameId": ["attr_id"]
    },
    "mediaInfo": {
      "box-2D": ["boxart", "image"],
      "ss": ["screenshot"],
      "video": ["video"],
      "wheel": ["wheel", "marquee"],
      "mix": ["mix", "image"],
      "marquee": ["marquee"]
    }
  }
}
```

### 7.2 Pegasus Import Template Example (with mediaDiscovery)

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import",
    "dataFileType": "text",
    "delimiter": ":",
    "dataFile": "metadata.pegasus.txt",
    "author": "Frontend-Killer",
    "description": "Pegasus Frontend import template (v3 unified format)"
  },
  "system": {
    "gameStartMarker": "game:",
    "fields": {
      "platform.system": ["collection"],
      "platform.launch": ["launch", "command"]
    }
  },
  "game": {
    "gameStartMarker": "game:",
    "multiLine": true,
    "gameInfo": {
      "name": ["game", "title"],
      "desc": ["description", "desc", "summary"],
      "releasedate": ["release", "releasedate"],
      "developer": ["developer", "developers"],
      "publisher": ["publisher", "publishers"],
      "genre": ["genre", "genres", "tag", "tags"],
      "players": ["players"],
      "rating": ["rating"],
      "path": ["file", "files"],
      "sort-by": ["sort-by", "sort_title", "sort_name"],
      "lang": ["region"]
    },
    "mediaInfo": {
      "box-2D": ["assets.box_front", "assets.boxfront"],
      "box-2D-back": ["assets.box_back", "assets.boxback"],
      "ss": ["assets.screenshot"],
      "video": ["assets.video"],
      "wheel-carbon": ["assets.logo"],
      "wheel": ["assets.wheel"],
      "marquee": ["assets.marquee"],
      "fanart": ["assets.fanart"],
      "bezel-16-9": ["assets.bezel"]
    },
    "mediaDiscovery": {
      "enabled": true,
      "baseDir": "media",
      "subDirPattern": "{filename}",
      "extensions": ["png", "jpg", "jpeg", "gif", "webp", "mp4", "mkv"],
      "rules": {
        "box-2D": [
          "{filename}/boxFront.{ext}",
          "{filename}/box_front.{ext}",
          "boxFront/{filename}.{ext}"
        ],
        "video": [
          "{filename}/video.{ext}",
          "videos/{filename}.{ext}"
        ]
      }
    }
  }
}
```

### 7.3 ES-DE Export Template Example

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "export",
    "dataFileType": "data",
    "format": "xml",
    "dataFile": "gamelist.xml",
    "author": "Frontend-Killer",
    "description": "EmulationStation / ES-DE export template (v3 unified format)"
  },
  "system": {
    "header": [
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
      "<gameList>"
    ],
    "footer": [
      "</gameList>"
    ],
    "fields": {}
  },
  "game": {
    "gameStartMarker": "game",
    "gameInfo": {
      "name": "name",
      "sortname": "sort-by",
      "description": "desc",
      "releasedate": "releasedate",
      "developer": "developer",
      "publisher": "publisher",
      "genre": "genre",
      "players": "players",
      "rating": "rating",
      "lang": "lang",
      "region": "region"
    },
    "mediaInfo": {
      "image": "box-2D",
      "video": "video",
      "marquee": "wheel",
      "thumbnail": "ss"
    }
  },
  "output": {
    "filename": "gamelist.xml",
    "pathFormat": "relative",
    "directory": {
      "roms": "{outputPath}/roms/{platform.system}",
      "media": "{outputPath}/downloaded_media/{platform.system}",
      "gamelist": "{outputPath}/gamelists/{platform.system}"
    },
    "media": {
      "boxfront": {
        "source": "box-2D",
        "target": "{mediaPath}/boxfront/{filename}.png"
      },
      "screenshot": {
        "source": "ss",
        "target": "{mediaPath}/screenshot/{filename}.png"
      },
      "video": {
        "source": "video",
        "target": "{mediaPath}/video/{filename}.mp4"
      }
    }
  }
}
```

### 7.4 Pegasus Export Template Example

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "export",
    "dataFileType": "text",
    "delimiter": ": ",
    "dataFile": "metadata.pegasus.txt",
    "author": "Frontend-Killer",
    "description": "Pegasus Frontend export template (v3 unified format)"
  },
  "system": {
    "header": [
      "collection: {platform.system}",
      "sort-by: 064",
      "{platform.launch}",
      ""
    ],
    "fields": {
      "collection": "platform.system",
      "launch": "platform.launch"
    }
  },
  "game": {
    "gameStartMarker": "game:",
    "entrySeparator": "\n\n",
    "gameInfo": {
      "game": "name",
      "file": "path",
      "sort-by": "sort-by",
      "developer": "developer",
      "publisher": "publisher",
      "genre": "genre",
      "players": "players",
      "description": "desc",
      "rating": "rating",
      "release": "releasedate"
    },
    "mediaInfo": {
      "assets.box_front": "box-2D",
      "assets.box_back": "box-2D-back",
      "assets.screenshot": "ss",
      "assets.video": "video",
      "assets.logo": "wheel-carbon",
      "assets.wheel": "wheel",
      "assets.marquee": "marquee",
      "assets.fanart": "fanart",
      "assets.bezel": "bezel-16-9"
    }
  },
  "output": {
    "filename": "metadata.pegasus.txt",
    "pathFormat": "relative",
    "directory": {
      "roms": "{outputPath}/{platform.system}",
      "media": "{outputPath}/{platform.system}/media"
    },
    "media": {
      "video": {
        "source": "video",
        "target": "{mediaPath}/{gameName}/video.mp4"
      },
      "box_front": {
        "source": "box-2D",
        "target": "{mediaPath}/{gameName}/boxFront.png"
      },
      "screenshot": {
        "source": "ss",
        "target": "{mediaPath}/{gameName}/screenshot.png"
      },
      "logo": {
        "source": "wheel-carbon",
        "target": "{mediaPath}/{gameName}/logo.png"
      }
    },
    "m3u": {
      "enabled": false
    }
  }
}
```

---

## 8. Best Practices

### 8.1 Field Naming Recommendations

- **Import templates**: Prefer nomcourt (e.g., `box-2D`) for consistency with ScreenScraper API
- **Export templates**: Choose naming based on target frontend requirements
- **Expressions**: Use the most readable name, e.g., `box-2D` is clearer than `box_2d`

### 8.2 Media Discovery Configuration Recommendations

- Configure multiple search paths for commonly used media types to improve match rates
- Use `{filename}` variable to create subdirectory structures for better organization
- Include common extensions: `png`, `jpg`, `jpeg`, `gif`, `webp`, `mp4`

### 8.3 Expression Usage Recommendations

- Use `or` operator to provide fallback values: `name or filename`
- Use `default` function to provide defaults: `default(desc, "No description")`
- Use `coalesce` for multi-level fallbacks: `coalesce(box-2D, screenshot, image)`
- Use parentheses to clarify priority: `(name or filename) + ".jpg"`

---

## 9. Troubleshooting

### 9.1 Field Not Accessible

**Problem**: Field name used in template cannot retrieve value

**Solutions**:
1. Check if the field name is correct (refer to Section 4 mapping table)
2. Try different naming conventions (nomcourt / dbColumn / javaField)
3. Check if the field has a value (some fields may be empty)

### 9.2 Expression Calculation Failure

**Problem**: Expression returns null or incorrect value

**Solutions**:
1. Check function syntax
2. Check parameter types
3. Test with simple field names first, then gradually add complexity

### 9.3 Media Discovery Failure

**Problem**: mediaDiscovery cannot find media files

**Solutions**:
1. Check if `baseDir` path is correct
2. Check if path patterns in `rules` match actual file structure
3. Check if `extensions` includes the target file extensions
4. Check logs to understand the search process

---

## 10. Appendix

### 10.1 Key Differences between v2 and v3

| Feature | v2 | v3 |
|---------|----|----|
| Field Naming | Uses javaField (e.g., `box2d`) | Supports nomcourt / dbColumn / javaField |
| Expressions | Simple variable substitution | Supports functions, operators, conditions |
| Media Discovery | Manual path configuration | Automatic mediaDiscovery rules |
| Computed Variables | None | Auto-injects `filename`, `filepath` |
| Multi-disc Games | Not supported | Auto-expansion |

### 10.2 Total Supported ScreenScraper Media Types

- **Total**: 50 official media types
- **Images**: 40 types
- **Videos**: 8 types
- **Documents**: 1 type (manuel)
- **Themes**: 2 types (themehb, themehs)

### 10.3 Version Information

- **Document Version**: 3.0
- **Last Updated**: 2026-09-13
- **Applicable System**: Frontend Killer v1.0.5+
