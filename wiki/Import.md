# Import Templates

Web GameList Oper supports importing game data from various frontend formats using customizable JSON templates.

## Directory Structure

Place your import templates in:
```
data/rules/import/
```

## Template Structure

Each import template is a JSON file with the following structure:

```json
{
  "frontend": "pegasus",
  "name": "Pegasus Frontend",
  "version": "1.0",
  "description": "Import template for Pegasus frontend",
  "fileExtensions": [".xml"],
  "rules": {
    "dataFile": {
      "rootElement": "gameList",
      "gameElement": "game",
      "fields": {
        "title": "name",
        "path": "path",
        "description": "description"
      }
    },
    "media": {
      "boxFront": {
        "source": "boxFront",
        "rules": ["media/boxfront/{filename}.png"]
      }
    }
  }
}
```

## EmuELEC Import Template

Example: `data/rules/import/emuelec.json`

EmuELEC supports three gamelist.xml lookup locations:
1. `roms/{platform}/gamelist.xml`
2. `roms/{platform}/.gamelist.xml`
3. `share/emuelec/configs/emulationstation/gamelists/{platform}/gamelist.xml`

```json
{
  "frontend": "emuelec",
  "name": "EmuELEC",
  "version": "1.0",
  "description": "Import template for EmuELEC frontend. Note: Users must copy downloaded_images folder to the same directory as gamelist.xml before importing.",
  "fileExtensions": [".xml"],
  "rules": {
    "dataFile": {
      "rootElement": "gameList",
      "gameElement": "game",
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
    "media": {
      "boxFront": {
        "source": "boxFront",
        "rules": ["downloaded_images/{platform}/box2dfront/{filename}.png"]
      },
      "boxBack": {
        "source": "boxBack",
        "rules": ["downloaded_images/{platform}/box2dback/{filename}.png"]
      },
      "screenshot": {
        "source": "screenshot",
        "rules": ["downloaded_images/{platform}/screenshot/{filename}.png"]
      },
      "video": {
        "source": "video",
        "rules": ["downloaded_images/{platform}/video/{filename}.mp4"]
      },
      "wheel": {
        "source": "wheel",
        "rules": ["downloaded_images/{platform}/wheel/{filename}.png"]
      },
      "fanart": {
        "source": "fanart",
        "rules": ["downloaded_images/{platform}/fanart/{filename}.png"]
      }
    }
  }
}
```

## EmulationStation DE (ES-DE) Import Template

Example: `data/rules/import/esde.json`

```json
{
  "frontend": "esde",
  "name": "EmulationStation DE",
  "version": "1.0",
  "description": "Import template for EmulationStation Desktop Edition (ES-DE). Note: Users must copy gamelists and downloaded_media folders to the same directory before importing.",
  "fileExtensions": [".xml"],
  "rules": {
    "dataFile": {
      "rootElement": "gameList",
      "gameElement": "game",
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
    "media": {
      "boxFront": {
        "source": "boxFront",
        "rules": ["downloaded_media/{platform}/boxfront/{filepath}.png"]
      },
      "boxBack": {
        "source": "boxBack",
        "rules": ["downloaded_media/{platform}/boxback/{filepath}.png"]
      },
      "screenshot": {
        "source": "screenshot",
        "rules": ["downloaded_media/{platform}/screenshot/{filepath}.png"]
      },
      "video": {
        "source": "video",
        "rules": ["downloaded_media/{platform}/video/{filepath}.mp4"]
      },
      "wheel": {
        "source": "wheel",
        "rules": ["downloaded_media/{platform}/wheel/{filepath}.png"]
      },
      "fanart": {
        "source": "fanart",
        "rules": ["downloaded_media/{platform}/fanart/{filepath}.jpg"]
      },
      "titlescreen": {
        "source": "titlescreen",
        "rules": ["downloaded_media/{platform}/titlescreen/{filepath}.png"]
      },
      "marquee": {
        "source": "marquee",
        "rules": ["downloaded_media/{platform}/marquee/{filepath}.png"]
      },
      "manual": {
        "source": "manual",
        "rules": ["downloaded_media/{platform}/manual/{filepath}.pdf"]
      }
    }
  }
}
```

## Lakka Import Template

Example: `data/rules/import/lakka.json`

```json
{
  "frontend": "lakka",
  "name": "Lakka",
  "version": "1.0",
  "description": "Import template for Lakka .lpl playlist format",
  "fileExtensions": [".lpl"],
  "rules": {
    "dataFile": {
      "format": "lpl",
      "fields": {
        "path": "path",
        "label": "label"
      }
    }
  }
}
```

## Skraper-EmulationStation Import Template

Example: `data/rules/import/skraper-es.json`

```json
{
  "frontend": "skraper-es",
  "name": "Skraper-EmulationStation",
  "version": "1.0",
  "description": "Import template for Skraper-scraped data for EmulationStation frontend",
  "fileExtensions": [".xml"],
  "rules": {
    "dataFile": {
      "rootElement": "gameList",
      "gameElement": "game",
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
    "media": {
      "boxFront": {
        "source": "boxFront",
        "rules": ["media/SKRAPER/{platform}/BOX2DFRONT/{filepath}.png"]
      },
      "boxBack": {
        "source": "boxBack",
        "rules": ["media/SKRAPER/{platform}/BOX2DBACK/{filepath}.png"]
      },
      "screenshot": {
        "source": "screenshot",
        "rules": ["media/SKRAPER/{platform}/SCREENSHOT/{filepath}.png"]
      },
      "video": {
        "source": "video",
        "rules": ["media/SKRAPER/{platform}/VIDEO/{filepath}.mp4"]
      },
      "wheel": {
        "source": "wheel",
        "rules": ["media/SKRAPER/{platform}/WHEEL/{filepath}.png"]
      },
      "fanart": {
        "source": "fanart",
        "rules": ["media/SKRAPER/{platform}/FANART/{filepath}.jpg"]
      }
    }
  }
}
```

## Pegasus Import Template

Example: `data/rules/import/pegasus.json`

```json
{
  "frontend": "pegasus",
  "name": "Pegasus Frontend",
  "version": "1.0",
  "fileExtensions": [".xml"],
  "rules": {
    "dataFile": {
      "rootElement": "gameList",
      "gameElement": "game",
      "fields": {
        "title": "name",
        "path": "path",
        "description": "description",
        "rating": "rating",
        "releaseDate": "releaseYear",
        "developer": "developer",
        "publisher": "publisher",
        "genre": "genre",
        "players": "players"
      }
    },
    "media": {
      "boxFront": {
        "source": "boxFront",
        "rules": ["media/boxfront/{filename}.png"]
      },
      "boxBack": {
        "source": "boxBack",
        "rules": ["media/boxback/{filename}.png"]
      },
      "screenShot": {
        "source": "screenShot",
        "rules": ["media/screenshots/{filename}.png"]
      },
      "banner": {
        "source": "banner",
        "rules": ["media/marquees/{filename}.png"]
      },
      "wheel": {
        "source": "wheel",
        "rules": ["media/wheel/{filename}.png"]
      },
      "video": {
        "source": "video",
        "rules": ["media/videos/{filename}.mp4"]
      },
      "thumbnail": {
        "source": "thumbnail",
        "rules": ["media/thumbs/{filename}.png"]
      }
    }
  }
}
```

## RetroBat Import Template

Example: `data/rules/import/retrobat.json`

```json
{
  "frontend": "retrobat",
  "name": "RetroBat",
  "version": "1.0",
  "fileExtensions": [".xml"],
  "rules": {
    "dataFile": {
      "rootElement": "gameList",
      "gameElement": "game",
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
    "media": {
      "boxFront": {
        "source": "boxFront",
        "rules": ["boxart/{filename}.png"]
      },
      "screenShot": {
        "source": "screenShot",
        "rules": ["screenshots/{filename}.png"]
      },
      "wheel": {
        "source": "wheel",
        "rules": ["wheel/{filename}.png"]
      },
      "marquee": {
        "source": "marquee",
        "rules": ["marquee/{filename}.png"]
      }
    }
  }
}
```

## Field Mappings

The `fields` object maps XML elements to database fields:

| XML Element | Database Field | Description |
|-------------|----------------|-------------|
| `name` | title | Game title |
| `path` | path | Path to the game file |
| `description` | description | Game description |
| `developer` | developer | Game developer |
| `publisher` | publisher | Game publisher |
| `genre` | genre | Game genre |
| `releaseDate` | releaseDate | Release date |
| `players` | players | Number of players |
| `rating` | rating | Game rating |

## Media Mappings

The `media` object defines how media files are imported:

```json
"media": {
  "boxFront": {
    "source": "boxFront",
    "rules": ["media/boxfront/{filename}.png", "media/boxart/{filename}.jpg"]
  }
}
```

| Property | Description |
|----------|-------------|
| `source` | Database field name |
| `rules` | Array of path patterns to search for media files |

## Media Path Variables

Import templates support variables for media file path matching:

| Variable | Description |
|----------|-------------|
| `{gameName}` | Game name (without extension) |
| `{filename}` | Same as `{gameName}` |
| `{filepath}` | Full game file path (without extension, including subdirectories) |
| `{platform}` | Platform name |
| `{ext}` | File extension |

**Example usage for subdirectory media files:**

```json
"media": {
  "screenshot": {
    "source": "screenshot",
    "rules": ["media/screenshot/{filepath}.{ext}"]
  }
}
```

For a game path `path1/path2/game.a26`, this will look for:
- `media/screenshot/path1/path2/game.png`

## Creating Custom Templates

### Step 1: Analyze Your XML Format

First, examine your game list XML file structure:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<gameList>
  <game>
    <name>Super Mario Bros.</name>
    <path>/roms/nes/smb.zip</path>
    <desc>A classic platformer</desc>
    <developer>Nintendo</developer>
    <publisher>Nintendo</publisher>
    <genre>Platformer</genre>
    <players>2</players>
  </game>
</gameList>
```

### Step 2: Create Template File

Create a JSON file in `data/rules/import/`:

```json
{
  "frontend": "custom",
  "name": "Custom Format",
  "version": "1.0",
  "description": "Custom import template",
  "fileExtensions": [".xml"],
  "rules": {
    "dataFile": {
      "rootElement": "gameList",
      "gameElement": "game",
      "fields": {
        "title": "name",
        "path": "path",
        "description": "desc",
        "developer": "developer",
        "publisher": "publisher",
        "genre": "genre",
        "players": "players"
      }
    },
    "media": {
      "boxFront": {
        "source": "boxFront",
        "rules": ["images/boxart/{filename}.png"]
      }
    }
  }
}
```

### Step 3: Test Import

1. Place your template in `data/rules/import/`
2. Go to Data Import page
3. Select your template
4. Choose your XML file
5. Click Import

## Template Configuration Options

### fileExtensions
```json
"fileExtensions": [".xml", ".gxml"]
```
Supported file extensions for import.

### dataFile.rootElement
```json
"rootElement": "gameList"
```
The root XML element containing all games.

### dataFile.gameElement
```json
"gameElement": "game"
```
The XML element representing a single game.

### dataFile.fields
```json
"fields": {
  "title": "name",
  "path": "path"
}
```
Maps XML element names to database fields.

### media
```json
"media": {
  "boxFront": {
    "source": "boxFront",
    "rules": ["path/to/media/{filename}.png"]
  }
}
```
Defines media file matching rules.

## Troubleshooting

### Template Not Loading
- Ensure JSON file is valid
- Check file is in correct directory
- Verify file extension is `.json`

### Fields Not Mapping
- Check field names match XML elements exactly
- Use XML element names, not attribute names
- Enable debug logging for detailed error info

### Media Files Not Found
- Check media path rules are correct
- Verify media files exist in the expected locations
- Use `{filepath}` variable for games in subdirectories
