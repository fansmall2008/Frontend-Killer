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
  "fileExtensions": [".xml"],
  "rootElement": "gameList",
  "gameElement": "game",
  "fieldMappings": {
    "name": "name",
    "path": "path",
    "description": "description"
  }
}
```

## Pegasus Import Template

Example: `data/rules/import/pegasus.json`

```json
{
  "frontend": "pegasus",
  "name": "Pegasus Frontend",
  "importPath": "pegasus",
  "fileExtensions": [".xml"],
  "rootElement": "gameList",
  "gameElement": "game",
  "fieldMappings": {
    "name": "name",
    "path": "path",
    "description": "description",
    "developer": "developer",
    "publisher": "publisher",
    "genre": "genre",
    "releaseDate": "releaseYear",
    "players": "players",
    "rating": "rating",
    "image": "marquee",
    "video": "video",
    "thumbnail": "thumbnail"
  },
  "mediaMappings": {
    "boxFront": ["boxfront", "box-front", "box_front"],
    "boxBack": ["boxback", "box-back", "box_back"],
    "screenShot": ["screenshot", "screen-shots", "screenshots"],
    "banner": ["banner", "marquee"],
    "wheel": ["wheel", "wheelart"],
    "video": ["video", "video_preview"]
  },
  "platformMappings": {
    "nes": "NES",
    "snes": "SNES",
    "genesis": "Sega Genesis"
  }
}
```

## RetroBat Import Template

Example: `data/rules/import/retrobat.json`

```json
{
  "frontend": "retrobat",
  "name": "RetroBat",
  "importPath": "retrobat",
  "fileExtensions": [".xml"],
  "rootElement": "gameList",
  "gameElement": "game",
  "fieldMappings": {
    "name": "name",
    "path": "path",
    "description": "desc",
    "developer": "developer",
    "publisher": "publisher",
    "genre": "genre",
    "releaseDate": "releasedate",
    "players": "players",
    "rating": "rating",
    "image": "image",
    "video": "video",
    "thumbnail": "thumbnail"
  },
  "mediaMappings": {
    "boxFront": ["boxart", "box"],
    "boxBack": ["boxart2", "box2"],
    "screenShot": ["ss", "screenshots"],
    "wheel": ["wheel"],
    "marquee": ["marquee"]
  },
  "platformMappings": {
    "nes": "NES",
    "snes": "SNES",
    "genesis": "MEGADRIVE"
  }
}
```

## Field Mappings

The `fieldMappings` object maps XML elements to database fields:

| XML Element | Database Field | Description |
|-------------|----------------|-------------|
| `name` | Game Name | Name of the game |
| `path` | Game Path | Path to the game file |
| `description` | Description | Game description |
| `developer` | Developer | Game developer |
| `publisher` | Publisher | Game publisher |
| `genre` | Genre | Game genre |
| `releaseDate` | Release Date | Release date |
| `players` | Players | Number of players |
| `rating` | Rating | Game rating |
| `image` | Image | Main image |
| `video` | Video | Video preview |
| `thumbnail` | Thumbnail | Thumbnail image |

## Media Mappings

The `mediaMappings` object maps media types to folder/file names:

| Media Type | Possible Folder Names |
|------------|----------------------|
| `boxFront` | boxfront, box-front, box_front, boxart |
| `boxBack` | boxback, box-back, box_back, boxart2 |
| `screenShot` | screenshot, screenshots, ss |
| `banner` | banner, marquee |
| `wheel` | wheel, wheelart |
| `video` | video, video_preview |
| `thumbnail` | thumbnail, thumb |

## Platform Mappings

The `platformMappings` object maps short names to full platform names:

```json
"platformMappings": {
  "nes": "NES",
  "snes": "SNES",
  "genesis": "Sega Genesis",
  "gba": "Game Boy Advance"
}
```

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
  "importPath": "custom",
  "fileExtensions": [".xml"],
  "rootElement": "gameList",
  "gameElement": "game",
  "fieldMappings": {
    "name": "name",
    "path": "path",
    "description": "desc",
    "developer": "developer",
    "publisher": "publisher",
    "genre": "genre",
    "players": "players"
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

### rootElement
```json
"rootElement": "gameList"
```
The root XML element containing all games.

### gameElement
```json
"gameElement": "game"
```
The XML element representing a single game.

### nestedElements
For nested structures:
```json
"nestedElements": {
  "platform": {
    "element": "system",
    "nameAttribute": "name"
  }
}
```

### defaultValues
Set default values for missing fields:
```json
"defaultValues": {
  "players": "1",
  "genre": "Unknown"
}
```

## Troubleshooting

### Template Not Loading
- Ensure JSON file is valid
- Check file is in correct directory
- Verify file extension is `.json`

### Fields Not Mapping
- Check field names match XML elements exactly
- Use XML element names, not attribute names
- Enable debug logging for detailed error info