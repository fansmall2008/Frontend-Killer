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
  "exportPath": "pegasus",
  "gameListFile": "gamelist.xml",
  "format": "xml",
  "encoding": "UTF-8",
  "fieldMappings": {
    "name": "name",
    "path": "path"
  },
  "mediaDirectories": {
    "boxFront": "media/boxfront",
    "boxBack": "media/boxback"
  }
}
```

## Pegasus Export Rule

Example: `data/rules/export/pegasus.json`

```json
{
  "frontend": "pegasus",
  "name": "Pegasus Frontend",
  "exportPath": "pegasus",
  "gameListFile": "gamelist.xml",
  "format": "xml",
  "encoding": "UTF-8",
  "xmlHeader": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
  "rootElement": "gameList",
  "gameElement": "game",
  "fieldMappings": {
    "name": "name",
    "path": "path",
    "description": "description",
    "developer": "developer",
    "publisher": "publisher",
    "genre": "genre",
    "releaseDate": "releaseDate",
    "players": "players",
    "rating": "rating"
  },
  "mediaMappings": {
    "boxFront": "boxFront",
    "boxBack": "boxBack",
    "screenShot": "screenShot",
    "banner": "marquee",
    "wheel": "wheel",
    "video": "video",
    "thumbnail": "thumbnail"
  },
  "mediaDirectories": {
    "boxFront": "media/boxfront",
    "boxBack": "media/boxback",
    "screenShot": "media/screenshots",
    "banner": "media/marquees",
    "wheel": "media/wheel",
    "video": "media/videos",
    "thumbnail": "media/thumbs"
  },
  "xmlStructure": {
    "indent": "  ",
    "newline": "\n"
  }
}
```

## RetroBat Export Rule

Example: `data/rules/export/retrobat.json`

```json
{
  "frontend": "retrobat",
  "name": "RetroBat",
  "exportPath": "retrobat",
  "gameListFile": "gamelist-retrobat.xml",
  "format": "xml",
  "encoding": "UTF-8",
  "xmlHeader": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
  "rootElement": "gameList",
  "gameElement": "game",
  "fieldMappings": {
    "name": "name",
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
  },
  "mediaMappings": {
    "boxFront": "image",
    "screenShot": "ss",
    "wheel": "wheel",
    "marquee": "marquee"
  },
  "mediaDirectories": {
    "boxFront": "boxart",
    "screenShot": "screenshots",
    "wheel": "wheel",
    "marquee": "marquee"
  }
}
```

## Field Mappings

The `fieldMappings` object maps database fields to XML elements:

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

The `mediaMappings` object maps database media fields to XML elements:

| Database Field | XML Element | Description |
|----------------|-------------|-------------|
| `boxFront` | boxFront | Front box art |
| `boxBack` | boxBack | Back box art |
| `screenShot` | screenshot | Screenshot images |
| `banner` | marquee | Banner/marquee |
| `wheel` | wheel | Wheel artwork |
| `video` | video | Video preview |
| `thumbnail` | thumbnail | Thumbnail image |

## Media Directories

The `mediaDirectories` object specifies output directories for media files:

```json
"mediaDirectories": {
  "boxFront": "media/boxfront",
  "boxBack": "media/boxback",
  "screenShot": "media/screenshots",
  "banner": "media/marquees",
  "wheel": "media/wheel",
  "video": "media/videos",
  "thumbnail": "media/thumbs"
}
```

## XML Structure Options

### Basic XML Generation

```json
"xmlStructure": {
  "indent": "  ",
  "newline": "\n",
  "selfClosingTags": false
}
```

### Custom Element Transformation

```json
"elementTransform": {
  "name": "toUpperCase",
  "path": "toLowerCase"
}
```

## Creating Custom Export Rules

### Step 1: Analyze Target Format

Examine your frontend's expected XML format:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<gameList>
  <game>
    <name>Super Mario Bros.</name>
    <path>/roms/nes/smb.zip</path>
    <desc>A classic platformer</desc>
    <developer>Nintendo</developer>
  </game>
</gameList>
```

### Step 2: Create Rule File

Create a JSON file in `data/rules/export/`:

```json
{
  "frontend": "custom",
  "name": "Custom Format",
  "exportPath": "custom",
  "gameListFile": "gamelist-custom.xml",
  "format": "xml",
  "encoding": "UTF-8",
  "rootElement": "gameList",
  "gameElement": "game",
  "fieldMappings": {
    "name": "name",
    "path": "path",
    "description": "desc",
    "developer": "developer"
  }
}
```

### Step 3: Configure Media Directories

```json
"mediaDirectories": {
  "boxFront": "images/boxart",
  "screenShot": "images/screenshots"
}
```

### Step 4: Test Export

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

## Rule Configuration Options

### format
```json
"format": "xml"
```
Output format (currently supports XML).

### encoding
```json
"encoding": "UTF-8"
```
XML file encoding.

### xmlHeader
```json
"xmlHeader": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
```
Custom XML header.

### indent
```json
"indent": "  "
```
Indentation for pretty-printing (2 spaces).

### conditionalElements
Export elements only when they have values:
```json
"conditionalElements": {
  "developer": true,
  "publisher": true
}
```

### valueTransform
Transform values during export:
```json
"valueTransform": {
  "releaseDate": "formatDate",
  "rating": "toStars"
}
```

## Troubleshooting

### Export Rule Not Loading
- Ensure JSON file is valid
- Check file is in correct directory
- Verify `frontend` field is unique

### Media Files Not Copied
- Check `mediaDirectories` paths are correct
- Verify source media files exist
- Ensure output directory is writable

### XML Format Incorrect
- Verify `fieldMappings` match expected format
- Check `xmlHeader` is valid XML
- Validate `rootElement` and `gameElement` names