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
  },
  "variables": [
    // Optional: template variables the user sets before execution (see 3.4)
  ]
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
| `before` | `before(str, sep)` | Return the part before the separator; returns the original string when no separator is found | `before(path, "#")` |
| `after` | `after(str, sep)` | Return the part after the separator; returns the original string when no separator is found | `after(path, "#")` |
| `concat` | `concat(f1, f2, ...)` | Concatenate all arguments, skipping empty values | `concat(cdn, '/', filename)` |
| `sanitize` | `sanitize(str, charset)` | Replace each character found in `charset` with `_` | `sanitize(name, "&*/:\\<>?\|")` |
| `matches` | `matches(str, regex)` | Full regex match, returns `true`/`false` | `matches(crc32, '^[0-9A-Fa-f]{8}\\|crc$')` |

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

#### 2.2.5 Dialect Mapping Functions

| Function | Syntax | Description | Example |
|----------|--------|-------------|---------|
| `map` | `map(value[, category])` | Dialect mapping: on hit returns the mapped target word (multi-value input maps segment by segment, joined with `-`), on miss returns the original value | `map(genre, "genre")` |
| `mapFirst` | `mapFirst(value[, category])` | Dialect mapping returning only the first mapped result (for single-value contexts such as directory names) | `mapFirst(genre)` |

`map()` / `mapFirst()` perform deterministic translation using the system's **dialect mapping table** (term_mapping, maintained in System Settings), e.g. normalizing scraped English genres to Chinese: `Beat-'Em-Up` → `清版游戏`.

- `map(value)`: searches all mapping categories (the `system_alias` category is internal to platform-system matching and is excluded); if one word hits multiple categories, the first in category-name dictionary order is used for deterministic results
- `map(value, category)`: searches only the specified category
- Source words are normalized before lookup (lowercased, spaces and punctuation stripped), so `Beat'em Up`, `beat-em-up` and `BEAT EM UP` are treated as the same word
- On miss the input value is returned unchanged, no error is raised

#### 2.2.6 General Syntax Notes

- **Function names are case-insensitive**: `mapFirst(...)` equals `mapfirst(...)`
- **String literals**: both double `"..."` and single `'...'` quotes are supported; commas and parentheses inside quoted arguments need no escaping
- **Lazy evaluation**: `default`, `if` and `coalesce` evaluate arguments on demand; branches not selected are never evaluated, avoiding spurious errors
- **Unknown functions**: return null and log a WARN, without interrupting the export

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

# Dialect mapping
map(genre, "genre")
mapFirst(genre)

# Separator handling (common in RetroArch playlists)
before(path, '#')
ext(before(path, '#'))

# Filename sanitizing (RetroArch thumbnail rules)
sanitize(name, "&*/:\\<>?|")

# Regex validation
matches(crc32, '^[0-9A-Fa-f]{8}\\|crc$')

# Complex expressions
(name or filename) + ".jpg"
coalesce(box-2D, screenshot, image)
concat('Named_Boxarts/', sanitize(name, '&*/:\\<>?|'), '.png')
```

---

## 3. Available Variables

### 3.1 Game Field Variables

All game fields can be referenced directly by field name. Three naming conventions are supported:

#### 3.1.1 Basic Fields

| Field | Description | Example Value |
|-------|-------------|---------------|
| `name` | Game name | "Super Mario Bros" |
| `translatedName` | Translated game name (injected before export per selected language; often combined with `coalesce`) | "超级马里奥兄弟" |
| `desc` | Game description | "A classic platform game..." |
| `translatedDesc` | Translated game description | "经典平台游戏..." |
| `releasedate` | Release date | "1985-09-13" |
| `developer` | Developer | "Nintendo" |
| `publisher` | Publisher | "Nintendo" |
| `genre` | Genre | "Platform" |
| `players` | Player count | "1-2" |
| `rating` | Rating | "0.85" |
| `path` | Game path (first line for multi-disc games) | "./roms/nes/supermario.nes" |
| `lang` | Language | "en" |
| `sort-by` | Sort field | "Mario" |
| `gameId` | Game ID | "12345" |
| `hash` | File hash | "abc123..." |
| `crc32` | CRC32 checksum | "12345678" |
| `md5` | MD5 checksum | "abcdef..." |
| `source` | Data source | "screenscraper" |
| `platformType` | Platform type | "console" |
| `multiFile` | Whether the game is multi-disc (boolean; `"true"` or empty string in expressions) | "true" |
| `multiFileContent` | Multi-disc file list text (one file per line, written by multiFileDetection at import) | "disc1.bin\ndisc2.bin" |

#### 3.1.2 Computed Fields (Read-only)

| Field | Description | Computation |
|-------|-------------|-------------|
| `filename` | Filename without extension | Extracted from `path` |
| `releaseYear` | Release year | First 4 chars of `releasedate` |
| `m3uPath` | m3u filename of a multi-disc game (non-empty only when `multiFile=true`) | `stem(first line of path) + ".m3u"`, illegal characters replaced with `_` |

`m3uPath` is commonly combined with `if` to rewrite multi-disc paths: `if(multiFile, m3uPath, path)` (used by the ES-DE / EmuELEC / RetroBat export templates).

#### 3.1.3 Field Aliases

The following aliases can be used instead of standard field names:

| Alias | Maps to |
|-------|---------|
| `description` | `desc` |
| `file` | `path` |
| `region` | `lang` (note: there is no standalone "region" field; region values come from `lang`) |
| `category` | `genre` |
| `releaseDate` | `releasedate` |
| `manuel` | `manual` |

> Matching is case-insensitive with `-`/`_` normalized, so `box2dfront`, `Box-2D` and `box_2d` all resolve to the same field.

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
| `platform.sortBy` | Platform sort value (Pegasus sort-by line) | "064" |
| `platform.extensions` | Platform extension list (Pegasus extensions line) | "nes\|fds" |
| `platform.ignoreFiles` | Ignored-files list (Pegasus ignore-files line) | "*.bak" |

### 3.3 Computed Variables (computedVariables Declaration Injection)

Import templates can declare custom computed variables via `game.computedVariables` (key = variable name, value = expression); at import time each game is evaluated and the results injected into the variable table:

```json
"computedVariables": {
  "filename": "stem(path)",
  "filepath": "replace(path or '', './', '')",
  "safeName": "sanitize(label, '&*/:\\<>?|')"
}
```

- Computed variables can be referenced in **subsequent field mappings and mediaDiscovery rules** (e.g. the RetroArch import template uses `{playlistName}`/`{safeName}` to build thumbnail paths)
- When the template declares no `computedVariables` (backward compatibility), injection falls back to the built-in defaults:

| Variable | Description | Computation |
|----------|-------------|-------------|
| `filename` | Filename without extension | Extracted from `path` |
| `filepath` | File path without extension and `./` prefix | Extracted from `path` |

**Example**:
- `path` = `./roms/nes/supermario.nes`
- `filename` = `supermario`
- `filepath` = `roms/nes/supermario`

The export direction also has built-in variables: `{outputPath}` (export path), `{gameName}` (game name, used in media targets), `{filename}`, `{ext}` (used in filename templates; the version extension during version replacement), plus template variables declared in the top-level `variables` block (see 3.4).

### 3.4 Template Variables (variables block)

Some values are unknown during the "game processing stage" and must be set by the user **before** the import/export action runs (e.g. a URL prefix for concatenation, or the folder name where ROMs are placed after export). The v3 template offers an optional top-level `variables` block to declare them; when a template with variable declarations is selected, clicking import/export pops up a dialog showing each **variable name, input field, and description**, and the entered values are sent to the backend as global variables with the request.

```json
"variables": [
  {
    "name": "romSubdir",
    "label": "ROM Folder",
    "description": "Sub-folder under the output directory where ROMs are placed; the playlist path points into it",
    "type": "text",
    "default": "{platform.system}",
    "required": true
  }
]
```

| Field | Description |
|-------|-------------|
| `name` | Variable name used in expressions and `{xxx}` placeholders; must be an identifier (letter/underscore start), must not clash with a built-in variable |
| `label` | Name shown in the dialog |
| `description` | Explanation shown in the dialog (why it is needed / what it does) |
| `type` | Only `text` \| `path` (`path` provides a path browser in the dialog) |
| `default` | Optional default, supports `{platform.xxx}` placeholders, resolved per platform at execution |
| `required` | Defaults to `false`; when empty and `required=true` the frontend blocks submit and the backend returns 400 |

**Behavior**:
- Variable values are **not persisted**; they must be entered for every import/export.
- A multi-platform batch export pops up once; all platforms **share the same** values. Platform differences are handled via `{platform.xxx}` in `default`/expressions.
- When a variable name clashes with a built-in, the built-in wins (the declaration is ignored with a WARN log), keeping old templates unaffected.
- Templates without a `variables` block behave exactly as before.

**Using variables in a template (concatenating URLs / strings)**: injected variables live in the same vars map, and the engine reads it through two channels, so a variable can be concatenated directly:

1. **`{var}` placeholder channel**: literal `{key}→value` replacement for `output.*.directory`, `filename`, header/footer lines, etc. e.g. `"directory": "{outputPath}/{romSubdir}"`.
2. **Bare-identifier expression channel**: the variable name can appear directly inside `concat(...)`, `+`, etc. e.g. `"image": "concat(cdnBase, '/', platform.system, '/', filename(name), '.png')"`.

When a variable is empty, `concat`/`+` skip empty values or return null per existing semantics, without affecting other fields. See the sample template: `rules/export/retroarch-folder-v3.json`.

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
    "format": "xml|json|text",
    "dataFile": "gamelist.xml",
    "delimiter": ":"
  },
  "parsing": {
    // Parser behavior parameters (optional, see 5.2)
  },
  "system": {
    "systemTag": "provider",
    "gameStartMarker": "game:",
    "fields": {
      "platform.system": ["system"],
      "platform.software": ["software"]
    }
  },
  "game": {
    "gameStartMarker": "game",
    "multiLine": true,
    "multiFileDetection": {
      // Multi-disc detection (optional, see 5.3)
    },
    "computedVariables": {
      // Computed variable declarations (optional, see 3.3)
    },
    "gameInfo": {
      "name": ["name", "sortname"],
      "desc": ["desc", "description"]
    },
    "mediaInfo": {
      "box-2D": ["boxart", "image"],
      "ss": ["screenshot"]
    },
    "mediaDiscovery": {
      // Media discovery configuration (optional, see 5.6)
    }
  }
}
```

### 5.2 parsing Configuration

The `parsing` block defines parser behavior parameters so the same engine can handle different frontends' formats. Defaults keep backward compatibility when omitted.

**text type**:

```json
"parsing": {
  "text": {
    "delimiter": ":",
    "commentChars": ["#"],
    "entrySeparator": "emptyLine",
    "keyCase": "lower",
    "encoding": "UTF-8",
    "multiLine": {
      "enabled": true,
      "indentChars": ["  ", "\t"]
    }
  }
}
```

| Field | Description |
|-------|-------------|
| `delimiter` | Attribute separator (e.g. `":"`, `"="`) |
| `commentChars` | Comment markers; matching lines are skipped |
| `entrySeparator` | Entry separation mode; `"emptyLine"` splits entries on blank lines |
| `keyCase` | Key case strategy: `lower` / `upper` / `preserve` |
| `encoding` | File encoding (default UTF-8) |
| `multiLine.enabled` | Whether indented continuation lines are supported (Pegasus `files:` blocks rely on this) |
| `multiLine.indentChars` | List of continuation-line indent characters |

**json type** (RetroArch playlists, etc.):

```json
"parsing": {
  "json": {
    "root": "items",
    "encoding": "UTF-8"
  }
}
```

| Field | Description |
|-------|-------------|
| `root` | JSON path (dot-separated) to the game entry array, default `"items"` |
| `encoding` | File encoding (default UTF-8) |

### 5.3 multiFileDetection (Multi-disc Detection)

`multiFileDetection` writes multi-disc info into the Game record's `multiFile=true` + `multiFileContent` fields; once triggered the game **stays as a single record** (it is not expanded into multiple Games):

```json
"multiFileDetection": {
  "enabled": true,
  "source": "path",
  "trigger": "endsWith",
  "pattern": ".m3u",
  "content": "fileContent",
  "pathFrom": "firstLine"
}
```

| Field | Values | Description |
|-------|--------|-------------|
| `source` | Field name | Detection source field (e.g. `path`, `files`) |
| `trigger` | `fieldExists` \| `endsWith` | Trigger condition: field exists / field's first line ends with pattern (case-insensitive) |
| `pattern` | e.g. `".m3u"` | Match pattern for `endsWith`, default `".m3u"` |
| `content` | `fieldValue` \| `fileContent` | Content source: cleaned field value / file content read from disk (m3u) |
| `pathFrom` | `firstLine` | Optional; with `firstLine`, path becomes the first cleaned line |

**Two typical modes** (differences among built-in import templates):

| Mode | Configuration | Templates |
|------|---------------|-----------|
| Pegasus multi-line block | `source=files, trigger=fieldExists, content=fieldValue, pathFrom=firstLine` | pegasus-v3 |
| m3u pointer | `source=path, trigger=endsWith(.m3u), content=fileContent` | esde / emuelec / retrobat / skraper-es / webgamelistoper |

**Cleaning rules** (applied before `content` is written to `multiFileContent`):
- `#` comment lines and blank lines are removed, each line trimmed
- Leading `./` or `.\` prefixes are removed
- **No cue→bin expansion**: entries are kept as-is; cue companion-file collection only happens at export time (see 6.7 roms.cue)
- **The directory base is uniformly the platform ROM root**: in `fileContent` mode the cleaned lines do not carry the m3u's own directory prefix; if the m3u lives in a subdirectory and uses relative references, export resolves entries against the platform root, so **sub-directory relative references lose their base**
- Pegasus `files:` continuation lines are cleaned by the same rules before being stored in `multiFileContent`

**Multi-disc expansion (legacy semantics)**: the `game.multiFile` block (`enabled`/`field`/`separator`) splits a multi-value field into multiple Game records; it is mutually exclusive with `multiFileDetection` (detection wins and keeps a single record). New templates should use `multiFileDetection` only.

### 5.4 gameInfo Field Mapping

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

### 5.5 mediaInfo Field Mapping

`mediaInfo` defines the mapping from media file paths to system media fields:

```json
"mediaInfo": {
  "box-2D": ["boxart", "image"],
  "ss": ["screenshot"],
  "video": ["video"],
  "wheel": ["wheel", "marquee"]
}
```

### 5.6 mediaDiscovery Configuration

`mediaDiscovery` is used to automatically find media files on disk:

```json
"mediaDiscovery": {
  "enabled": true,
  "baseDir": "media",
  "subDirPatterns": ["{filename}", "{name}"],
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
- `baseDir`: Base directory for media files (relative to the data file's directory; may be a relative path like `"../thumbnails"`)
- `subDirPatterns`: **Prioritized list** of sub-directory naming strategies (array, tried in order; e.g. `["{filename}", "{name}"]` tries the ROM filename first, then the display name)
- `extensions`: List of file extensions to search
- `rules`: Search rules per media type, supporting `{filename}`, `{ext}` and variables declared in computedVariables (e.g. `{safeName}`)

**Variable Substitution**:
- `{filename}`: Game filename without extension
- `{name}`: Game display name
- `{ext}`: Current file extension being searched
- Variables declared in computedVariables (e.g. `{playlistName}`, `{safeName}` in the RetroArch template)

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
    "format": "xml|json|text",
    "delimiter": ": ",
    "dataFile": "metadata.pegasus.txt"
  },
  "variables": [
    // Optional: template variables set via dialog before export (see 3.4)
  ],
  "system": {
    "header": [ /* data file header lines */ ],
    "footer": [ /* data file footer lines */ ],
    "fields": { /* field mappings used in header/footer lines (optional) */ }
  },
  "game": {
    "gameStartMarker": "game:",
    "entrySeparator": "\n\n",
    "gameInfo": { /* game field → data file field mappings (values are expressions) */ },
    "mediaInfo": { /* data file media tags → source media fields (nomcourt) */ }
  },
  "output": {
    "exportOptions": { /* UI export option declarations */ },
    "roms": { /* ROM file copying: enabled/directory/filename/m3u/cue */ },
    "media": { /* media file copying: directory/rules */ },
    "dataFile": { /* data file: directory/filename/pathFormat etc. */ },
    "readme": { /* optional: usage readme generation */ }
  }
}
```

### 6.2 output.exportOptions Export Option Declarations

`exportOptions` declares the export scope supported by the target frontend, controlling which options are available on the export form and their defaults:

```json
"exportOptions": {
  "gameFiles": true,
  "mediaFiles": true,
  "gameListFile": true,
  "showWarning": false
}
```

| Field | Description |
|-------|-------------|
| `gameFiles` | Whether ROM file export is allowed (controls whether the roms block takes effect) |
| `mediaFiles` | Whether media file export is allowed (controls whether the media block takes effect) |
| `gameListFile` | Whether data file generation is allowed (controls whether the dataFile block takes effect) |
| `showWarning` | Whether to show a limitation warning in the UI (e.g. "metadata and media only, ROMs are not exported") |

The user's actual checkboxes on the export form decide which output blocks take effect at runtime.

### 6.3 system.header / footer

`header` / `footer` define the header and footer lines of the data file:

```json
"header": [
  "collection: {platform.name}",
  "sort-by: {platform.sortBy}",
  "{platform.launch}",
  ""
]
```

**Note**:
- Each line is a string, supporting `{platform.xxx}` placeholders and expressions
- Empty strings represent blank lines
- `fields` is optional and declares the field mappings used in header/footer lines

### 6.4 game.gameInfo / game.mediaInfo

**gameInfo** (keys = data file fields/tags, values = expressions):

```json
"gameInfo": {
  "game": "coalesce(name, translatedName)",
  "file": "if(multiFile, '', path)",
  "files": "multiFileContent",
  "path": "if(multiFile, m3uPath, path)",
  "crc32": "if(crc32, concat(crc32, '|crc'), 'DETECT')"
}
```

- Values support the full expression syntax (Section 2)
- When an expression references `path` or `m3uPath`, the result is automatically formatted per `output.dataFile.pathFormat` (see 6.9)
- Empty values are not output
- **Multi-disc output**: when the value is multi-line text (e.g. Pegasus `files: multiFileContent`), it renders as "first line key+separator, following lines indented two spaces" — i.e. Pegasus's `files:` multi-line block

**mediaInfo** (keys = media tags in the data file, values = source media nomcourt):

```json
"mediaInfo": {
  "image": "box-2D",
  "assets.video": "video",
  "assets.boxFront": "box-2D"
}
```

- Media paths **prefer** the export-path override computed via the matching `dataFileTag` in `output.media.rules` (see 6.8); without an override the original media path from the database is used
- Media types without a `dataFileTag` in rules are copied only, not written into the data file
- rules keys are not limited to the 54 ScreenScraper nomcourt values; legacy field names (e.g. `image`, `banner`, `manual`) also work — the engine accesses the Game field the same way

### 6.5 output.roms ROM File Copying

`output.roms` defines ROM file copying behavior:

```json
"roms": {
  "enabled": true,
  "directory": "{outputPath}/{platform.system}",
  "filename": "{filename}{ext}",
  "m3u": {
    "enabled": false,
    "target": "{outputPath}/{platform.system}"
  },
  "cue": { "enabled": true, "onMissing": "warn" }
}
```

| Field | Description |
|-------|-------------|
| `enabled` | Whether to perform ROM copying; **when false, the entire ROM copy phase (including m3u generation and cue companion-file collection) is skipped, making the m3u/cue settings moot** |
| `directory` | Target directory template; supports per-game expressions (e.g. `if(genre, concat('{outputPath}/', platform.system, '/', mapFirst(genre)), '{outputPath}/{platform.system}')` to group by genre) |
| `filename` | Target filename template, down to the filename level; default `{filename}{ext}` keeps the original name; supports expressions like sanitize/concat |

> In whole-directory copy mode (choosing "whole directory copy" on the export form), per-game companion resolution is skipped; the platform source ROM directory is copied as a whole to `roms.directory`, and m3u/cue per-game logic does not participate.

### 6.6 output.roms.m3u Playlist Processing

`roms.m3u` controls M3U handling at export time, default `enabled=false`. It involves **two different m3u paths**:

**Path 1: regenerate m3u for multiFile games**
- After a multi-disc game (`multiFile=true`, content from the `multiFileContent` stored at import) has its discs copied, a `{filename}.m3u` is generated in the ROM directory
- Content = cleaned lines of `multiFileContent` (comments/blank lines/`./` prefixes removed, separators normalized to `/`)

**Path 2: parse-and-rewrite for legacy data whose path points to an .m3u**
- When a game's `path` points to an `.m3u` file (resolved to multiFile at import, or existing as an m3u file), all companion files referenced by the m3u are copied and the **m3u content is rewritten**
- Rewrite rule: each line becomes a path relative to the m3u's own directory, separators normalized to `/`; missing entry lines are kept as-is
- `m3u.target`: optional, designates a unified target directory template for referenced files (default: the target directory where the m3u itself lands)
- When an m3u entry is a `.cue`, companion files are collected per the cue rules (see 6.7); when it is an `.m3u`, it is expanded further

**Important semantics**:
- m3u content is based on the **snapshot stored at import time** (`multiFileContent` or the path recorded in the database); manually edited m3u files after import are **not** reflected at export
- When `roms.enabled=false`, the entire ROM copy phase is skipped and m3u settings do not apply
- The `filename` template variable is the stem of the path's first line in the multi-disc scenario

### 6.7 output.roms.cue CUE/BIN Companion-File Collection

`roms.cue` controls automatic collection of CUE disc-image companion files (`.bin`/`.wav` etc.); it is the default v3 export behavior:

```json
"cue": { "enabled": true, "onMissing": "warn" }
```

| Field | Default | Description |
|-------|---------|-------------|
| `enabled` | `true` | **Templates without a cue section are treated as enabled=true**, keeping behavior backward compatible; when false, FILE references inside cue files are not parsed |
| `onMissing` | `warn` | Handling of missing referenced files: `warn` (task log + completion summary list) / `skip` (silent, debug log only) / `fail` (log to task log, then abort **that game's** copying without interrupting the whole batch); unknown values fall back to warn |

**Trigger scenarios** (when cue.enabled=true):
1. A single-file game whose exported target ends with `.cue` → parse FILE references inside the cue and collect companion files
2. When a multiFile game's discs are copied and a `multiFileContent` entry is a `.cue` → collect its FILE references
3. During m3u parse-and-rewrite, when an m3u entry is a `.cue` → collect its FILE references

**Recursive expansion**:
- `FILE` lines inside a cue are parsed one by one: both quoted (spaces allowed inside quotes) and unquoted forms are supported; blank lines, `;`/`#` comments and BOM are skipped
- **Paths inside a cue resolve against the cue's own directory**; referenced files land next to the cue target preserving their relative structure (leading `../` or cross-drive paths degrade to a flat filename)
- **All multi-track bins are collected**: each FILE line corresponds to one companion file, all collected and copied
- cue referencing cue → expand further; entry is an `.m3u` → expand its entries in turn
- Nested expansion is capped at **depth 16** (m3u→cue→m3u chains), with a visited-set preventing infinite recursion
- All pending files are deduplicated by source absolute path and copied in one pass

**Missing-file handling details**:
- `warn`: missing files are logged to the task log and summarized as "CUE missing N" in the export completion result (**up to 20 entries**, truncated with … beyond that)
- `fail`: only the current game's copying is aborted (caught by the per-game exception handler); subsequent games continue
- Missing multiFile entries / m3u entries themselves are also handled per `onMissing` (even when cue.enabled=false)

### 6.8 output.media Media Copying

`output.media` defines media file copying:

```json
"media": {
  "directory": "{outputPath}/{platform.system}/media",
  "rules": {
    "box-2D": {
      "source": "box-2D",
      "target": "boxfront/{filename}.png",
      "dataFileTag": "image"
    }
  }
}
```

| Field | Description |
|-------|-------------|
| `directory` | Media root directory template (supports `{outputPath}`, `{platform.xxx}`, `{gameName}` etc.) |
| `rules` | Output rules per media type; key = source nomcourt |
| `rules.*.source` | Source media field (same as key, kept for readability) |
| `rules.*.target` | Target path template (relative to media.directory); supports `{filename}`, `{gameName}` variables and expressions (e.g. `sanitize`, `concat`) |
| `rules.*.dataFileTag` | Corresponding tag in the data file; **when set, that media path in the data file is rewritten to the post-export path** (recomputed relative to the data file directory); when unset the file is copied without rewriting |

Media paths in the data file = full path of media.directory + target, relativized against the data file directory, then formatted per `pathFormat` (see 6.9).

### 6.9 output.dataFile Data File

`output.dataFile` defines data file output:

```json
"dataFile": {
  "directory": "{outputPath}/gamelists/{platform.system}",
  "filename": "gamelist.xml",
  "pathFormat": "relative",
  "pathPrefix": "./",
  "jsonItemsKey": "items",
  "jsonTopLevelFields": [
    {"key": "version", "value": "1.5", "type": "string"}
  ]
}
```

| Field | Description |
|-------|-------------|
| `directory` / `filename` | Data file directory and filename templates (e.g. `{platform.name}.lpl`) |
| `pathFormat` | Path format, see below |
| `pathPrefix` | Reserved field (not read by the current engine; prefix handling is done by pathFormat) |
| `jsonItemsKey` | JSON type: key of the game entry array, default `"items"` |
| `jsonTopLevelFields` | JSON type: top-level fields output before the entry array (order preserved); each item has `key`/`value`/optional `type` (`string`/`number`/`boolean`/`null`, auto-inferred by default, preventing `"1.5"` from being misread as a number) |

**Exact pathFormat semantics** (`formatExportPath` only adds/removes prefixes, **never truly relativizes**):

| Value | Behavior |
|-------|----------|
| `relative` | Strips a leading `./` prefix (default behavior; also applied for non-relative values) |
| `absoluteWithDot` | Ensures the path carries a `./` prefix (except Windows drive-letter paths and `/`-rooted absolute paths) |
| `absolute` / others | Same as relative: only strips the `./` prefix |

- **Path-type fields** (expressions referencing `path`/`m3uPath`): only prefix add/remove as above, no relativization — whatever path is written is what gets output (with pathFormat prefix handling)
- **Media paths**: first truly relativized from the data file directory → media file full path (falling back to string computation across drive letters), then prefix-processed per `pathFormat`

### 6.10 output.readme Usage Readme Generation

`output.readme` is optional; after export it generates a usage readme file in the export directory:

```json
"readme": {
  "enabled": true,
  "directory": "{outputPath}",
  "filename": "使用说明-{platform.name}.md",
  "content": [
    "# {platform.name} playlist export notes",
    "..."
  ]
}
```

- `content` is output line by line, supporting `{platform.xxx}`, `{outputPath}` and other placeholder substitutions
- Reference templates: `rules/export/retroarch-v3.json`, `rules/export/retroarch-folder-v3.json`

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
    "multiFileDetection": {
      "enabled": true,
      "source": "path",
      "trigger": "endsWith",
      "pattern": ".m3u",
      "content": "fileContent"
    },
    "computedVariables": {
      "filename": "stem(path)",
      "filepath": "replace(path or '', './', '')"
    },
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
      "gameId": ["attr_id"],
      "genreid": ["genreid"]
    },
    "mediaInfo": {
      "box-2D": ["boxart", "image"],
      "ss": ["screenshot"],
      "video": ["video"],
      "wheel": ["wheel"],
      "marquee": ["marquee"],
      "fanart": ["fanart"],
      "sstitle": ["titlescreen"],
      "box-2D-back": ["boxback"],
      "box-3D": ["box3d"],
      "bezel-16-9": ["bezel"],
      "support-2D": ["cartridge"],
      "steamgrid": ["steamgrid"],
      "manuel": ["manual"]
    }
  }
}
```

### 7.2 Pegasus Import Template Example (with multiFileDetection and mediaDiscovery)

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
  "parsing": {
    "text": {
      "delimiter": ":",
      "commentChars": ["#"],
      "entrySeparator": "emptyLine",
      "keyCase": "lower",
      "encoding": "UTF-8",
      "multiLine": {
        "enabled": true,
        "indentChars": ["  ", "\t"]
      }
    }
  },
  "system": {
    "gameStartMarker": "game:",
    "fields": {
      "platform.name": ["collection"],
      "platform.launch": ["launch", "command"],
      "platform.sortBy": ["sort-by"],
      "platform.extensions": ["extensions"],
      "platform.ignoreFiles": ["ignore-files"]
    }
  },
  "game": {
    "gameStartMarker": "game:",
    "multiLine": true,
    "multiFileDetection": {
      "enabled": true,
      "source": "files",
      "trigger": "fieldExists",
      "content": "fieldValue",
      "pathFrom": "firstLine"
    },
    "computedVariables": {
      "filename": "stem(path)",
      "filepath": "replace(path or '', './', '')"
    },
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
      "box-2D-side": ["assets.box_spine", "assets.boxspine"],
      "ss": ["assets.screenshot"],
      "video": ["assets.video"],
      "wheel-carbon": ["assets.logo"],
      "marquee": ["assets.marquee"],
      "fanart": ["assets.fanart"],
      "bezel-16-9": ["assets.bezel"]
    },
    "mediaDiscovery": {
      "enabled": true,
      "baseDir": "media",
      "subDirPatterns": ["{filename}", "{name}"],
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
    "description": "EmulationStation DE",
    "notes": "输出 gamelist.xml 格式。注意：仅导出元数据和媒体文件，不导出 ROM 文件"
  },
  "system": {
    "systemTag": "provider",
    "header": [
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
      "<gameList>",
      "  <provider>",
      "    <system>{platform.system}</system>",
      "    <software>Frontend-Killer</software>",
      "  </provider>"
    ],
    "footer": [
      "</gameList>"
    ],
    "fields": {
      "system": "platform.system"
    }
  },
  "game": {
    "gameStartMarker": "game",
    "gameInfo": {
      "path": "if(multiFile, m3uPath, path)",
      "name": "coalesce(name, translatedName)",
      "sortname": "sort-by",
      "desc": "coalesce(desc, translatedDesc)",
      "rating": "rating",
      "releasedate": "releasedate",
      "developer": "developer",
      "publisher": "publisher",
      "genre": "genre",
      "players": "players",
      "lang": "lang",
      "region": "region",
      "hash": "hash",
      "genreid": "genreid"
    },
    "mediaInfo": {
      "image": "box-2D",
      "video": "video",
      "marquee": "marquee",
      "fanart": "fanart",
      "screenshot": "ss",
      "titlescreen": "sstitle",
      "wheel": "wheel",
      "boxback": "box-2D-back",
      "box3d": "box-3D",
      "bezel": "bezel-16-9",
      "cartridge": "support-2D",
      "steamgrid": "steamgrid",
      "manual": "manuel"
    }
  },
  "output": {
    "exportOptions": {
      "gameFiles": false,
      "mediaFiles": true,
      "gameListFile": true,
      "showWarning": true
    },
    "roms": {
      "enabled": false,
      "directory": "{outputPath}/roms/{platform.system}",
      "filename": "{filename}{ext}",
      "m3u": {
        "enabled": true
      },
      "cue": { "enabled": true, "onMissing": "warn" }
    },
    "media": {
      "directory": "{outputPath}/downloaded_media/{platform.system}",
      "rules": {
        "box-2D": {
          "source": "box-2D",
          "target": "boxfront/{filename}.png",
          "dataFileTag": "image"
        },
        "box-2D-back": {
          "source": "box-2D-back",
          "target": "boxback/{filename}.png",
          "dataFileTag": "boxback"
        },
        "box-3D": {
          "source": "box-3D",
          "target": "box3d/{filename}.png",
          "dataFileTag": "box3d"
        },
        "sstitle": {
          "source": "sstitle",
          "target": "titlescreen/{filename}.png",
          "dataFileTag": "titlescreen"
        },
        "marquee": {
          "source": "marquee",
          "target": "marquee/{filename}.png",
          "dataFileTag": "marquee"
        },
        "fanart": {
          "source": "fanart",
          "target": "fanart/{filename}.jpg",
          "dataFileTag": "fanart"
        },
        "video": {
          "source": "video",
          "target": "video/{filename}.mp4",
          "dataFileTag": "video"
        },
        "manuel": {
          "source": "manuel",
          "target": "manual/{filename}.pdf",
          "dataFileTag": "manual"
        },
        "wheel": {
          "source": "wheel",
          "target": "wheel/{filename}.png",
          "dataFileTag": "wheel"
        },
        "ss": {
          "source": "ss",
          "target": "screenshot/{filename}.png",
          "dataFileTag": "screenshot"
        },
        "support-2D": {
          "source": "support-2D",
          "target": "support-2d/{filename}.png",
          "dataFileTag": "cartridge"
        },
        "bezel-16-9": {
          "source": "bezel-16-9",
          "target": "bezel/{filename}.png",
          "dataFileTag": "bezel"
        },
        "steamgrid": {
          "source": "steamgrid",
          "target": "steamgrid/{filename}.png",
          "dataFileTag": "steamgrid"
        }
      }
    },
    "dataFile": {
      "directory": "{outputPath}/gamelists/{platform.system}",
      "filename": "gamelist.xml",
      "pathFormat": "relative",
      "pathPrefix": "./"
    }
  }
}
```

Key points (this example matches `rules/export/esde-v3.json` exactly):

- **gameFiles: false + roms.enabled: false**: metadata and media only; the entire ROM copy phase (including m3u generation and cue collection) is skipped, and the roms settings have no effect
- **path expression `if(multiFile, m3uPath, path)`**: multi-disc games point to the generated .m3u, single-file games output the original path
- **name/desc use coalesce with translated-name fallback**: translated names (translatedName/translatedDesc) take priority
- **media.rules.dataFileTag**: after media copying, the corresponding tag (e.g. `<image>`) in the data file is rewritten to the post-export location (relativized first, then prefix-processed per pathFormat)
- **pathFormat: "relative"**: path-type fields (path/m3uPath) only get `./` prefix handling, no true relativization; media paths are relativized against the data file directory first, then prefix-processed
- **pathPrefix: "./"**: reserved field, unused in the current version

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
    "description": "Pegasus Frontend",
    "notes": "输出 metadata.pegasus.txt 格式，支持多盘游戏的 files 多行输出"
  },
  "system": {
    "header": [
      "collection: {platform.name}",
      "sort-by: {platform.sortBy}",
      "extensions: {platform.extensions}",
      "ignore-files: {platform.ignoreFiles}",
      "launch: {platform.launch}",
      ""
    ],
    "footer": [
      "# End of file"
    ],
    "fields": {
      "collection": "platform.name",
      "sort-by": "platform.sortBy",
      "extensions": "platform.extensions",
      "ignore-files": "platform.ignoreFiles",
      "launch": "platform.launch"
    }
  },
  "game": {
    "gameStartMarker": "game:",
    "entrySeparator": "\n\n",
    "gameInfo": {
      "game": "coalesce(name, translatedName)",
      "file": "if(multiFile, '', path)",
      "files": "multiFileContent",
      "sort-by": "sort-by",
      "developer": "developer",
      "publisher": "publisher",
      "genre": "genre",
      "players": "players",
      "description": "coalesce(desc, translatedDesc)",
      "rating": "rating",
      "release": "releasedate",
      "region": "region"
    },
    "mediaInfo": {
      "assets.boxFront": "box-2D",
      "assets.boxBack": "box-2D-back",
      "assets.boxSpine": "box-2D-side",
      "assets.box3d": "box-3D",
      "assets.cartridge": "support-2D",
      "assets.logo": "wheel-carbon",
      "assets.poster": "flyer",
      "assets.marquee": "marquee",
      "assets.bezel": "bezel-16-9",
      "assets.fanart": "fanart",
      "assets.steam": "steamgrid",
      "assets.background": "background",
      "assets.screenshot": "ss",
      "assets.titlescreen": "sstitle",
      "assets.video": "video"
    }
  },
  "output": {
    "exportOptions": {
      "gameFiles": true,
      "mediaFiles": true,
      "gameListFile": true,
      "showWarning": false
    },
    "roms": {
      "enabled": true,
      "directory": "if(genre, concat('{outputPath}/', platform.system, '/', mapFirst(genre)), '{outputPath}/{platform.system}')",
      "filename": "{filename}{ext}",
      "m3u": {
        "enabled": false,
        "target": "{outputPath}/{platform.system}"
      },
      "cue": { "enabled": true, "onMissing": "warn" }
    },
    "media": {
      "directory": "{outputPath}/{platform.system}/media",
      "rules": {
        "video": {
          "source": "video",
          "target": "{gameName}/video.mp4",
          "dataFileTag": "assets.video"
        },
        "box-2D": {
          "source": "box-2D",
          "target": "{gameName}/boxFront.png",
          "dataFileTag": "assets.boxFront"
        },
        "box-2D-back": {
          "source": "box-2D-back",
          "target": "{gameName}/boxBack.png",
          "dataFileTag": "assets.boxBack"
        },
        "box-3D": {
          "source": "box-3D",
          "target": "{gameName}/box3d.png",
          "dataFileTag": "assets.box3d"
        },
        "ss": {
          "source": "ss",
          "target": "{gameName}/screenshot.png",
          "dataFileTag": "assets.screenshot"
        },
        "marquee": {
          "source": "marquee",
          "target": "{gameName}/marquee.png",
          "dataFileTag": "assets.marquee"
        },
        "fanart": {
          "source": "fanart",
          "target": "{gameName}/fanart.png",
          "dataFileTag": "assets.fanart"
        },
        "bezel-16-9": {
          "source": "bezel-16-9",
          "target": "{gameName}/bezel.png",
          "dataFileTag": "assets.bezel"
        },
        "background": {
          "source": "background",
          "target": "{gameName}/background.png",
          "dataFileTag": "assets.background"
        },
        "box-2D-side": {
          "source": "box-2D-side",
          "target": "{gameName}/boxSpine.png",
          "dataFileTag": "assets.boxSpine"
        },
        "sstitle": {
          "source": "sstitle",
          "target": "{gameName}/titlescreen.png",
          "dataFileTag": "assets.titlescreen"
        },
        "wheel-carbon": {
          "source": "wheel-carbon",
          "target": "{gameName}/logo.png",
          "dataFileTag": "assets.logo"
        },
        "support-2D": {
          "source": "support-2D",
          "target": "{gameName}/cartridge.png",
          "dataFileTag": "assets.cartridge"
        },
        "flyer": {
          "source": "flyer",
          "target": "{gameName}/poster.png",
          "dataFileTag": "assets.poster"
        },
        "steamgrid": {
          "source": "steamgrid",
          "target": "{gameName}/steam.png",
          "dataFileTag": "assets.steam"
        }
      }
    },
    "dataFile": {
      "directory": "{outputPath}/{platform.system}",
      "filename": "metadata.pegasus.txt",
      "pathFormat": "relative",
      "pathPrefix": ""
    }
  }
}
```

Key points (this example matches `rules/export/pegasus-v3.json` exactly):

- **file expression `if(multiFile, '', path)`**: file is left empty for multi-disc games (disc paths are expressed by the files list), avoiding a wrong first-file path
- **files expression `multiFileContent`**: outputs a multi-line block (first line `files:`, following lines indented two spaces), parsed by the text multiLine indent rules
- **roms.m3u.enabled: false**: Pegasus uses the files multi-line list, no .m3u regeneration needed; roms.cue stays enabled (.cue references inside multiFileContent entries are still collected)
- **roms.directory groups by genre**: `mapFirst(genre)` takes the first genre as the directory name, falling back to `{outputPath}/{platform.system}` when absent
- **media.target uses {gameName}**: one directory per game (e.g. `media/Super Mario/boxFront.png`), with dataFileTag mapping to `assets.*` nested tags
- **pathFormat: "relative" + pathPrefix: ""**: path-type fields only get their `./` prefix stripped

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

### 8.4 ROM and Playlist Export Recommendations

- Multi-disc games depend on multiFileDetection recognition at import: if the template does not configure it or it is not triggered, the game is not marked multiFile and neither m3u nor cue intervenes at export
- For CUE/BIN sets keep `onMissing: "warn"`: missing files are summarized without interrupting, making it easy to re-add files later; use `fail` only when strict validation is required
- Whether to generate .m3u depends on the target frontend: ES-DE/RetroBat/EmuELEC need it and enable it; Pegasus uses the files multi-line list and disables it by default
- m3u content comes from the snapshot stored at import; manually editing the source m3u after import is not reflected at export

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

### 9.4 CUE/BIN Missing-File Notices

**Problem**: logs show "missing file" during multi-disc export, or the completion result lists missing files

**Notes**:
- `roms.cue.onMissing` controls missing-file behavior: `warn` (default; logged and summarized in the completion result) / `skip` (silent) / `fail` (aborts only that game's copying, other games unaffected)
- The completion result lists at most 20 missing entries, truncated with `…` beyond that
- `cue.enabled: false` only skips parsing FILE references inside cue files; missing m3u/multiFile entries are still handled per onMissing

### 9.5 No .m3u Generated After Multi-disc Export

**Solutions**:
1. Confirm the game was recognized as multi-disc at import (multiFileDetection triggered, multiFile=true)
2. Confirm `output.roms.enabled: true` (when false the entire ROM copy phase is skipped, and m3u/cue settings do not apply)
3. Confirm `output.roms.m3u.enabled: true`
4. The Pegasus template disables m3u by default (the files multi-line list suffices); this is expected behavior

---

## 10. Appendix

### 10.1 Key Differences between v2 and v3

| Feature | v2 | v3 |
|---------|----|----|
| Field Naming | Uses javaField (e.g., `box2d`) | Supports nomcourt / dbColumn / javaField |
| Expressions | Simple variable substitution | Supports functions, operators, conditions |
| Media Discovery | Manual path configuration | Automatic mediaDiscovery rules |
| Computed Variables | None | Declared via template `computedVariables` (legacy templates fall back to built-in `filename`/`filepath`) |
| Multi-disc Games | Not supported | multiFileDetection keeps a single record (content stored in `multiFileContent`), no splitting |

### 10.2 Total Supported ScreenScraper Media Types

- **Total**: 54 official media types
- **Images**: 43 types
- **Videos**: 8 types
- **Documents**: 1 type (manuel)
- **Themes**: 2 types (themehb, themehs)

### 10.3 Version Information

- **Document Version**: 3.1
- **Last Updated**: 2026-10-27
- **Applicable System**: Frontend Killer v1.0.5+
