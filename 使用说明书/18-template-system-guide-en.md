# 18 · Template System Guide

> When importing/exporting, the software relies on "template rules" to decide how each field gets its value, what files are named, and which directory they go into. Templates can contain **expressions** — referencing fields, concatenating strings, calling functions. This chapter is the complete reference for the template system: variables, operators, all 21 built-in functions, recipe examples, and troubleshooting.

**Applies to**: editing field mappings, filenames, and path templates in import rules (`rules/import/`) and export rules (`rules/export/`).

---

## 18.1 First Distinguish "Field Name" from "Expression"

Values you put in a template fall into two kinds:

- **Plain field name**: e.g., `name`, `desc`, `box-2D`. Takes the value of that game field directly, with no processing.
- **Expression**: as soon as it contains a **function-call parenthesis `()`**, a **plus `+`**, an **`or` operator** (with spaces around it), or **starts with a quote**, it's treated as an expression and computed.

Examples:
- `name` → plain field, takes the game name.
- `name or filename` → expression (has `or`), falls back to the filename when the name is empty.
- `stem(path) + ".jpg"` → expression (has a function and `+`), takes the filename without extension and appends `.jpg`.

**String literals** are wrapped in double or single quotes: `"hello"`, `'.jpg'`.

---

## 18.2 The Variable System

### ① Game fields (non-media)

| Variable | Meaning | Notes |
|---|---|---|
| `name` | Game name | |
| `translatedName` | Translated name | |
| `desc` | Description | alias `description` |
| `translatedDesc` | Translated description | |
| `rating` | Rating | decimal 0~1 |
| `releasedate` | Release date | alias `releaseDate` |
| `releaseYear` | Release year | **read-only**, first 4 chars of the date |
| `developer` | Developer | |
| `publisher` | Publisher | |
| `genre` | Genre | alias `category` |
| `players` | Players | |
| `lang` | Language | alias `region` |
| `path` | Game file path | alias `file` |
| `hash` / `crc32` / `md5` | Checksums | |
| `gameId` | Game ID | |
| `source` | Source | |
| `platformType` | Platform type | |
| `sortBy` | Sort key | alias `sort-by` |
| `multiFile` | Whether multi-file | maps to `"true"` or empty string, handy for `if` checks |
| `multiFileContent` | Multi-file content text | |

### ② Computed fields (read-only, auto-derived)

| Variable | Meaning |
|---|---|
| `filename` | The filename **without extension**, extracted from `path` |
| `m3uPath` | The m3u filename when exporting a multi-file game (`filename + ".m3u"`); empty for non-multi-file |
| `releaseYear` | Release year (same as above) |

### ③ Media fields (three namings all recognized)

Each media type can be referenced in three spellings, with the same value — pick any:

| Spelling | Examples |
|---|---|
| nomcourt (hyphen) | `box-2D`, `box-3D`, `ss`, `video`, `wheel` |
| Database column (underscore) | `box_2d`, `box_3d` |
| Java field (camelCase) | `box2d`, `box3d` |

Legacy aliases are also kept: `image`, `thumbnail`, `manual` (alias `manuel`), `boxFront`, `boxBack`, `logo`, `banner`, `screenshot`, `titlescreen`, `fanart`/`background`, etc. Field-name matching **ignores case, hyphens, and underscores** (`box-2D` = `box_2d` = `box2d`).

### ④ Platform fields (`platform.` prefix)

| Variable | Meaning |
|---|---|
| `platform.system` | The bound system |
| `platform.name` | Platform name |
| `platform.launch` | Launch command |
| `platform.software` | Software name |
| `platform.database` | Database name |
| `platform.web` | Website |
| `platform.folderPath` | Platform folder path |

### ⑤ Custom variables

Custom variables can be passed in during import/export (e.g., values you're prompted to fill in a dialog); reference them in templates directly by variable name.

---

## 18.3 Operators

There are only two operators, **listed from lowest to highest precedence**:

| Operator | Meaning | Precedence |
|---|---|---|
| `or` | **Fallback** — take the first non-empty value (like SQL COALESCE) | low |
| `+` | **String concatenation** | high |

Because `+` has higher precedence than `or`:

- `name or filename + ".jpg"` equals `name or (filename + ".jpg")` — if name has a value, use name (without .jpg); otherwise use filename+".jpg".
- To make both carry the suffix, add parentheses: `(name or filename) + ".jpg"`.

The `or` keyword **must have spaces around it** (or be at the start/end), otherwise it's not treated as an operator — this way field names containing "or" like `color`, `order` aren't misjudged.

Multi-level fallback example: `box-2D or screenshot or image` (finds the first media that exists, in order).

---

## 18.4 All 21 Built-in Functions

Listed by category. In signatures, `str`/`path` denote expressions that are evaluated first.

### A. String operations (11)

| Function | Signature | Description |
|---|---|---|
| `sub` | `sub(str, start[, end])` | Substring. start is 0-based; omit end to slice to the end. Out-of-range is auto-clamped |
| `upper` | `upper(str)` | To uppercase |
| `lower` | `lower(str)` | To lowercase |
| `trim` | `trim(str)` | Strip leading/trailing whitespace |
| `replace` | `replace(str, from, to)` | Replace all occurrences of from with to |
| `before` | `before(str, sep)` | Take the part **before** the separator; if no separator, return the original string |
| `after` | `after(str, sep)` | Take the part **after** the separator; if no separator, return the original string |
| `concat` | `concat(f1, f2, ...)` | Concatenate all arguments, **skipping empty values** |
| `sanitize` | `sanitize(str, charset)` | Replace each character in charset with `_` (for cleaning illegal filename chars) |
| `matches` | `matches(str, regex)` | Regex **full match**, returns `"true"`/`"false"` |
| `len` | `len(str)` | Returns the string length (as a numeric string) |

Examples:
- `sub(name, 0, 7)` → take the first 7 characters of the name.
- `upper(stem(path))` → filename (without extension) in uppercase.
- `trim(replace(name, " ", "_"))` → replace spaces with underscores then strip whitespace (nesting works).
- `before(name, "(")` → take the part before the first `(` (often used before stripping suffixes like "(USA)").
- `after(path, "/")` → take the part after the first `/`.
- `sanitize(name, "<>\"/\\|?*")` → replace all illegal Windows filename chars with `_`.
- `if(matches(genre, ".*Racing.*"), "Racing", "Other")` → combine with if for conditions.

### B. Path operations (4)

| Function | Signature | Description |
|---|---|---|
| `filename` | `filename(path)` | Take the **filename (with extension)** from a path |
| `stem` | `stem(path)` | Take the filename and **remove the extension** |
| `ext` | `ext(path)` | Take the **extension (with the dot)**, e.g., `.zip`; empty if none |
| `dir` | `dir(path)` | Take the **directory part** (parent path) |

Examples: `stem(path) + ext(path)` reconstructs the full filename; `dir(path)` gets the containing directory.

> Note the distinction: the path function `filename(path)` returns the filename **with extension**; whereas the computed field `filename` (no parentheses) returns the filename **without extension**. They differ — don't mix them up.

### C. Conditions / defaults (3, lazy evaluation)

| Function | Signature | Description |
|---|---|---|
| `default` | `default(field, fallback)` | Use fallback when field is empty |
| `if` | `if(cond, trueVal[, falseVal])` | cond is true if non-empty and not `"false"`; returns trueVal, else falseVal |
| `coalesce` | `coalesce(f1, f2, ...)` | Return the first non-empty value (function form of an `or` chain) |

These three use **lazy evaluation**: only the needed branch is computed, avoiding unnecessary overhead/errors.

Examples:
- `default(desc, "No description")`.
- `if(multiFile, m3uPath, filename(path))` → use the m3u name for multi-file, otherwise the filename.
- `coalesce(translatedName, name, filename)` → prefer translated name, then original name, then filename.

> `if` truthiness convention: the string `"false"` (case-insensitive) and empty string are false; other non-empty values are true — which lines up exactly with `matches`' return value.

### D. Date operations (1)

| Function | Signature | Description |
|---|---|---|
| `dateformat` | `dateformat(dateStr, pattern)` | Reformat a date per pattern |

- Auto-recognizes several input formats: `yyyy-MM-dd'T'HH:mm:ss`, `yyyy-MM-dd HH:mm:ss`, `yyyy-MM-dd`, `yyyyMMdd`, `yyyy`, `MM/dd/yyyy`, `dd/MM/yyyy`.
- When it can't parse, it **returns the input as-is** — no error.
- pattern uses Java `SimpleDateFormat` syntax (`yyyy` year, `MM` month, `dd` day).

Examples: `dateformat(releasedate, "yyyy")` → just the year; `dateformat(releasedate, "yyyy-MM")`.

### E. Dialect mapping (2)

Used to map ScreenScraper's raw terms (English genre, system names, etc.) into the localized/custom terms you want. The mapping table is `term_mapping` (maintained in `rules/translation-config.json`, etc.), split into categories like `genre`, `system_alias`.

| Function | Signature | Description |
|---|---|---|
| `map` | `map(value[, category])` | Look up the mapping table; return the target term if hit, **return as-is if not** |
| `mapFirst` | `mapFirst(value[, category])` | Same, but for multi-value input **returns only the first** mapping result |

Rule details:
- Without category: searches **all categories except `system_alias`** (system_alias is for internal system matching); when a value hits multiple categories, the first by category name in lexicographic order is taken (guarantees a deterministic result).
- With category: searches only that category, e.g., `map(genre, "genre")`.
- The value is **normalized** before lookup (lowercased, whitespace and punctuation removed), so `"Beat'em Up"` / `"beat em up"` / `"beatemup"` are equivalent.
- **Multi-value handling** (value contains commas, e.g., `"Racing, Driving"`):
  - `map`: maps each segment, joining all results with `-`.
  - `mapFirst`: tries whole-value mapping first, and if that fails splits and takes the first valid result — good for directory names and other single-value scenarios.

Examples: `map(genre)` converts English genre to your language; `mapFirst(genre)` keeps just the main genre.

---

## 18.5 Combined Recipe Examples

**Safe filename**
```
sanitize(stem(path), "<>\"/\\|?*")
```
Remove the extension + clean illegal characters, yielding a string safe to use as a filename.

**Media multi-level fallback + suffix**
```
(box-2D or screenshot or image) + ".png"
```

**Export directory layered by platform/year**
```
platform.name + "/" + dateformat(releasedate, "yyyy") + "/" + name
```

**Genre localization**
```
map(genre, "genre")
```

**Special handling for multi-file games**
```
if(multiFile, m3uPath, filename(path))
```

**Take the main name when the name has a parenthetical suffix**
```
trim(before(name, "("))
```

**Empty-value safety net**
```
coalesce(translatedName, name, filename, "Unknown Game")
```

---

## 18.6 Common Errors & Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Expression output as-is / no value | Should have been an expression but lacked a trigger (no `()`/`+`/`or`/quote), so it was treated as a plain field name | Check for a missing function parenthesis or quote |
| `or` didn't work | No spaces around `or`, or the field name itself contains "or" | Put spaces on both sides of `or` |
| Concatenation/fallback result not as expected | `+` has higher precedence than `or` | Use parentheses to make the order explicit `(a or b) + ".ext"` |
| `filename` result is wrong | Confused the function `filename(path)` (with extension) and the field `filename` (without extension) | Choose as needed; to drop the extension use `stem` |
| `map` didn't map | Value didn't hit after normalization / wrong category / not in the table | Confirm `term_mapping` has the entry; specify category if needed |
| `dateformat` returned as-is | Input date format isn't in the recognizable list | Regularize the date format first, or change the pattern |
| Whole expression failed, returns empty | Syntax error (unbalanced parens/quotes, etc.); the engine logs a warn and returns null | Check the log viewer for the "expression evaluation failed" warn (see `16`) |

> The engine is **fault-tolerant** on expression evaluation failure: it doesn't throw, it just logs a warn and returns an empty value. So when a field mysteriously becomes empty, first check the logs for an expression warning.

---

## 18.7 Relationship with Import/Export

- **Import rules** (`rules/import/`): define how to read fields from data files into games.
- **Export rules** (`rules/export/`): define how filenames, directories, and data-file fields are generated on export.
- Different frontends (Pegasus, EmulationStation, etc.) have their own template restrictions; the export page shows yellow hints (see `10`).

Template expressions are the "compute core" of these rules; import and export share the same engine with identical syntax.

---

## 18.8 Next Steps

- How import uses rules → `09 Data Import`
- How export uses rules → `10 Data Export`
- Dialect mapping / translation feature → `06 Platform Management`, `07 Game List Management`
