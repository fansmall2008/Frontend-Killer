# Export Rules

Web GameList Oper supports exporting game data to various frontend formats using customizable JSON export rules.

## Directory Structure

Place your export rules in:
```
data/rules/export/
```

## Rule Structure

Each export rule is a JSON file with the following structure:

```json
{
  "frontend": "pegasus",
  "name": "Pegasus Frontend",
  "version": "1.0",
  "description": "Export template for Pegasus frontend",
  "exportOptions": {
    "gameFiles": true,
    "mediaFiles": true,
    "gameListFile": true,
    "showWarning": false
  },
  "rules": {
    "media": {
      "boxFront": {
        "source": "boxFront",
        "target": "{mediaPath}/boxfront/{filename}.png"
      }
    },
    "dataFile": {
      "filename": "gamelist.xml",
      "format": "xml",
      "fields": {
        "title": "name",
        "path": "path"
      }
    },
    "directory": {
      "roms": "{outputPath}/roms/{platform}",
      "media": "{outputPath}/media/{platform}",
      "gamelist": "{outputPath}/gamelists/{platform}"
    },
    "coreMappings": {
      "NES": { "path": "/tmp/cores/core.so", "name": "CoreName" }
    }
  }
}
```

**Note:** `coreMappings` is only used for Lakka (`.lpl` format) exports.

## Export Options

The `exportOptions` object controls which export options are available to users:

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `gameFiles` | boolean | true | Whether ROM files can be exported |
| `mediaFiles` | boolean | true | Whether media files can be exported |
| `gameListFile` | boolean | true | Whether gamelist.xml can be exported |
| `showWarning` | boolean | false | Whether to display a warning message about template limitations |

**Example for templates that should not export ROM files:**

```json
{
  "exportOptions": {
    "gameFiles": false,
    "mediaFiles": true,
    "gameListFile": true,
    "showWarning": true
  }
}
```

## EmuELEC Export Rule

Example: `data/rules/export/emuelec.json`

```json
{
  "frontend": "emuelec",
  "name": "EmuELEC",
  "version": "1.0",
  "description": "Template for Exporting Data and Media Files for EmuELEC Frontend. Note: This template only exports game metadata and media files, not ROM files.",
  "exportOptions": {
    "gameFiles": false,
    "mediaFiles": true,
    "gameListFile": true,
    "showWarning": true
  },
  "rules": {
    "media": {
      "boxFront": { "source": "boxFront", "target": "{mediaPath}/box2dfront/{filename}.png" },
      "boxBack": { "source": "boxBack", "target": "{mediaPath}/box2dback/{filename}.png" },
      "screenshot": { "source": "screenshot", "target": "{mediaPath}/screenshot/{filename}.png" },
      "video": { "source": "video", "target": "{mediaPath}/video/{filename}.mp4" },
      "wheel": { "source": "wheel", "target": "{mediaPath}/wheel/{filename}.png" },
      "fanart": { "source": "fanart", "target": "{mediaPath}/fanart/{filename}.png" }
    },
    "dataFile": {
      "filename": "gamelist.xml",
      "format": "xml",
      "pathFormat": "absoluteWithDot",
      "header": ["<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "<gameList>"],
      "footer": ["</gameList>"],
      "fields": {
        "title": "name",
        "path": "path",
        "description": "desc",
        "rating": "rating",
        "releaseDate": "releasedate",
        "developer": "developer",
        "publisher": "publisher",
        "genre": "genre",
        "players": "players"
      }
    },
    "directory": {
      "roms": "{outputPath}/roms/{platform}",
      "media": "{outputPath}/downloaded_images/{platform}",
      "gamelist": "{outputPath}/roms/{platform}"
    }
  }
}
```

## EmulationStation DE (ES-DE) Export Rule

Example: `data/rules/export/esde.json`

```json
{
  "frontend": "esde",
  "name": "EmulationStation DE",
  "version": "1.0",
  "description": "Export template for EmulationStation Desktop Edition (ES-DE). Note: This template only exports game metadata and media files, not ROM files.",
  "exportOptions": {
    "gameFiles": false,
    "mediaFiles": true,
    "gameListFile": true,
    "showWarning": true
  },
  "rules": {
    "media": {
      "boxFront": { "source": "boxFront", "target": "{mediaPath}/boxfront/{filename}.png" },
      "boxBack": { "source": "boxBack", "target": "{mediaPath}/boxback/{filename}.png" },
      "screenshot": { "source": "screenshot", "target": "{mediaPath}/screenshot/{filename}.png" },
      "video": { "source": "video", "target": "{mediaPath}/video/{filename}.mp4" },
      "wheel": { "source": "wheel", "target": "{mediaPath}/wheel/{filename}.png" },
      "fanart": { "source": "fanart", "target": "{mediaPath}/fanart/{filename}.jpg" }
    },
    "dataFile": {
      "filename": "gamelist.xml",
      "format": "xml",
      "pathFormat": "relative",
      "pathPrefix": "./",
      "header": {
        "structure": ["<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "<gameList>"],
        "fields": {}
      },
      "footer": ["</gameList>"],
      "fields": {
        "title": "name",
        "path": "path",
        "description": "description",
        "rating": "rating",
        "releaseDate": "releaseYear",
        "developer": "developer",
        "publisher": "publisher",
        "genre": "genre",
        "players": "players",
        "hash": "hash"
      }
    },
    "directory": {
      "roms": "{outputPath}/roms/{platform.system}",
      "media": "{outputPath}/downloaded_media/{platform.system}",
      "gamelist": "{outputPath}/gamelists/{platform.system}"
    }
  }
}
```

## Lakka Export Rule

Example: `data/rules/export/lakka.json`

```json
{
  "frontend": "lakka",
  "name": "Lakka",
  "version": "1.0",
  "description": "Export template for Lakka .lpl playlist format",
  "rules": {
    "dataFile": {
      "filename": "{platform}.lpl",
      "format": "lpl",
      "fields": {
        "path": "path",
        "label": "name"
      }
    },
    "directory": {
      "roms": "{outputPath}/roms/{platform}",
      "gamelist": "{outputPath}/playlists"
    }
  }
}
```

### Core Mappings (Lakka-specific)

Lakka uses RetroArch emulator cores to run games. The `coreMappings` object defines the mapping between platforms and their corresponding RetroArch core paths:

```json
{
  "rules": {
    "coreMappings": {
      "NES": {
        "path": "/tmp/cores/nestopia_libretro.so",
        "name": "Nestopia"
      },
      "SNES": {
        "path": "/tmp/cores/snes9x_libretro.so",
        "name": "Snes9x"
      },
      "Genesis": {
        "path": "/tmp/cores/genesis_plus_gx_libretro.so",
        "name": "Genesis Plus GX"
      },
      "default": {
        "path": "/tmp/cores/libretro.so",
        "name": "DETECT"
      }
    }
  }
}
```

| Field | Description |
|-------|-------------|
| `path` | Absolute path to the RetroArch core library file (`.so` on Linux, `.dll` on Windows) |
| `name` | Display name of the core, shown in the playlist file |
| `default` | Fallback mapping used when a platform has no specific core defined |

The `.lpl` playlist format (6-line format):
```
/storage/roms/NES/game.nes     # Game path
Game Name                       # Game label
/tmp/cores/nestopia_libretro.so # Core path
Nestopia                        # Core name
DETECT                          # Database detect mode
NES.lpl                         # Playlist name
```

## Pegasus Export Rule

Example: `data/rules/export/pegasus.json`

```json
{
  "frontend": "pegasus",
  "name": "Pegasus Frontend",
  "version": "1.0",
  "rules": {
    "media": {
      "boxFront": { "source": "boxFront", "target": "media/boxfront/{filename}.png" },
      "boxBack": { "source": "boxBack", "target": "media/boxback/{filename}.png" },
      "screenShot": { "source": "screenShot", "target": "media/screenshots/{filename}.png" },
      "banner": { "source": "banner", "target": "media/marquees/{filename}.png" },
      "wheel": { "source": "wheel", "target": "media/wheel/{filename}.png" },
      "video": { "source": "video", "target": "media/videos/{filename}.mp4" },
      "thumbnail": { "source": "thumbnail", "target": "media/thumbs/{filename}.png" }
    },
    "dataFile": {
      "filename": "gamelist.xml",
      "format": "xml",
      "fields": {
        "title": "name",
        "path": "path",
        "description": "description",
        "developer": "developer",
        "publisher": "publisher",
        "genre": "genre",
        "releaseDate": "releaseDate",
        "players": "players",
        "rating": "rating"
      }
    },
    "directory": {
      "roms": "{outputPath}/roms/{platform}",
      "media": "{outputPath}/media",
      "gamelist": "{outputPath}"
    }
  }
}
```

## RetroBat Export Rule

Example: `data/rules/export/retrobat.json`

```json
{
  "frontend": "retrobat",
  "name": "RetroBat",
  "version": "1.0",
  "rules": {
    "media": {
      "boxFront": { "source": "boxFront", "target": "boxart/{filename}.png" },
      "screenShot": { "source": "screenShot", "target": "screenshots/{filename}.png" },
      "wheel": { "source": "wheel", "target": "wheel/{filename}.png" },
      "marquee": { "source": "marquee", "target": "marquee/{filename}.png" }
    },
    "dataFile": {
      "filename": "gamelist-retrobat.xml",
      "format": "xml",
      "fields": {
        "title": "name",
        "path": "path",
        "description": "desc",
        "developer": "developer",
        "publisher": "publisher",
        "genre": "genre",
        "releaseDate": "releaseDate",
        "players": "players",
        "rating": "rating",
        "image": "image",
        "thumbnail": "thumbnail"
      }
    },
    "directory": {
      "roms": "{outputPath}/roms/{platform}",
      "media": "{outputPath}",
      "gamelist": "{outputPath}/roms/{platform}"
    }
  }
}
```

## Field Mappings

The `fields` object maps database fields to XML elements:

| Database Field | XML Element | Description |
|----------------|-------------|-------------|
| `name` | name | Name of the game |
| `path` | path | Path to the game file |
| `description` | description | Game description |
| `developer` | developer | Game developer |
| `publisher` | publisher | Game publisher |
| `genre` | genre | Game genre |
| `releaseDate` | releaseDate | Release date |
| `players` | players | Number of players |
| `rating` | rating | Game rating |

## Media Mappings

The `media` object defines how media files are exported:

```json
"media": {
  "boxFront": {
    "source": "boxFront",
    "target": "{mediaPath}/boxfront/{filename}.png",
    "dataFileTag": "image"
  }
}
```

| Property | Description |
|----------|-------------|
| `source` | Database field name |
| `target` | Output path template |
| `dataFileTag` | Optional: XML tag name for media reference |

## Directory Configuration

The `directory` object specifies output directories:

| Property | Description |
|----------|-------------|
| `roms` | Path for exported ROM files |
| `media` | Base path for media files |
| `gamelist` | Path for gamelist.xml |

## Supported Path Variables

| Variable | Description |
|----------|-------------|
| `{outputPath}` | Base output directory |
| `{platform}` | Platform name |
| `{platform.system}` | Platform system name |
| `{filename}` | Game filename without extension |
| `{filepath}` | Full file path without extension |
| `{mediaPath}` | Media directory path |

## Creating Custom Export Rules

### Step 1: Analyze Target Format

Examine your frontend's expected XML format and directory structure.

### Step 2: Create Rule File

Create a JSON file in `data/rules/export/`:

```json
{
  "frontend": "custom",
  "name": "Custom Format",
  "version": "1.0",
  "description": "Custom export template",
  "rules": {
    "media": {
      "boxFront": { "source": "boxFront", "target": "images/boxart/{filename}.png" }
    },
    "dataFile": {
      "filename": "gamelist.xml",
      "format": "xml",
      "fields": {
        "title": "name",
        "path": "path",
        "description": "desc"
      }
    },
    "directory": {
      "roms": "{outputPath}/roms/{platform}",
      "media": "{outputPath}/media",
      "gamelist": "{outputPath}"
    }
  }
}
```

### Step 3: Test Export

1. Place your rule in `data/rules/export/`
2. Go to Export page
3. Select your export format
4. Choose platforms/games to export
5. Click Export

## Export Output Structure

After export, you'll get:

```
output/
├── gamelist.xml           # Main game list XML
└── media/
    ├── boxfront/          # Box front images
    ├── boxback/           # Box back images
    ├── screenshots/       # Screenshots
    ├── marquees/          # Banners/marquees
    ├── wheel/             # Wheel artwork
    └── videos/            # Video previews
```

## Troubleshooting

### Export Rule Not Loading
- Ensure JSON file is valid
- Check file is in correct directory
- Verify `frontend` field is unique

### Media Files Not Copied
- Check `media` rules have correct target paths
- Verify source media files exist
- Ensure output directory is writable

### XML Format Incorrect
- Verify `fields` mappings match expected format
- Check `dataFile.header` and `footer` are valid XML
- Validate pathFormat setting
