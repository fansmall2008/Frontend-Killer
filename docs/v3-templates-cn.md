# v3 模板系统文档

## 1. 概述

v3 是 Frontend Killer 的统一模板格式版本，用于导入和导出数据文件。相比 v2 版本，v3 提供了更强大的表达式引擎、统一的字段命名体系和更灵活的媒体发现机制。

### 1.1 v3 的主要改进

- **统一字段访问器**：支持通过 nomcourt（API 原值）、dbColumn（数据库列名）、javaField（Java 字段名）三种名称访问同一字段
- **表达式引擎**：支持函数调用、运算符、条件判断等复杂表达式
- **计算变量注入**：自动注入 `filename`、`filepath` 等计算变量
- **媒体发现机制**：支持 `mediaDiscovery` 规则，自动在磁盘查找媒体文件
- **多盘游戏支持**：自动展开多盘游戏为多个条目

### 1.2 模板结构

v3 模板采用统一的 JSON 结构：

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import|export",
    "dataFileType": "data|text",
    "format": "xml|text",
    "dataFile": "gamelist.xml",
    "author": "作者名",
    "description": "模板描述",
    "notes": "备注"
  },
  "system": {
    // 系统/平台信息配置
  },
  "game": {
    // 游戏信息和媒体信息配置
  },
  "output": {
    // 导出配置（仅导出模板）
  }
}
```

---

## 2. 表达式引擎

v3 模板系统内置了强大的表达式引擎，支持在模板值中使用函数、运算符和变量引用。

### 2.1 运算符

#### 2.1.1 `or` 回退运算符

取第一个非空值，类似 SQL 的 COALESCE：

```
name or filename
box-2D or screenshot or image
```

**优先级**：`or` 是最低优先级的运算符。

#### 2.1.2 `+` 字符串拼接运算符

拼接多个字符串：

```
name + ".jpg"
platform.system + "_" + filename
```

**优先级**：`+` 的优先级高于 `or`，因此：
- `name or filename + ".jpg"` 等价于 `name or (filename + ".jpg")`
- 使用括号改变优先级：`(name or filename) + ".jpg"`

### 2.2 内置函数

#### 2.2.1 字符串操作函数

| 函数 | 语法 | 说明 | 示例 |
|------|------|------|------|
| `sub` | `sub(str, start[, end])` | 截取子串 | `sub(name, 0, 7)` |
| `upper` | `upper(str)` | 转大写 | `upper(name)` |
| `lower` | `lower(str)` | 转小写 | `lower(name)` |
| `trim` | `trim(str)` | 去首尾空白 | `trim(desc)` |
| `replace` | `replace(str, from, to)` | 字符串替换 | `replace(path, "\", "/")` |
| `len` | `len(str)` | 字符串长度 | `len(name)` |

#### 2.2.2 路径操作函数

| 函数 | 语法 | 说明 | 示例 |
|------|------|------|------|
| `filename` | `filename(path)` | 从路径提取文件名（含扩展名） | `filename(path)` → "game.zip" |
| `stem` | `stem(path)` | 去扩展名 | `stem(path)` → "game" |
| `ext` | `ext(path)` | 取扩展名（含点号） | `ext(path)` → ".zip" |
| `dir` | `dir(path)` | 取目录部分 | `dir(path)` → "/roms/nes" |

#### 2.2.3 条件函数

| 函数 | 语法 | 说明 | 示例 |
|------|------|------|------|
| `default` | `default(field, fallback)` | 字段为空时使用默认值 | `default(desc, "暂无描述")` |
| `if` | `if(condition, trueVal, falseVal)` | 条件判断 | `if(video, "有视频", "无视频")` |
| `coalesce` | `coalesce(f1, f2, ...)` | 取第一个非空值 | `coalesce(box-2D, screenshot, image)` |

#### 2.2.4 日期函数

| 函数 | 语法 | 说明 | 示例 |
|------|------|------|------|
| `dateformat` | `dateformat(dateStr, pattern)` | 日期格式化 | `dateformat(releasedate, "yyyy-MM-dd")` |

**支持的输入格式**：
- `yyyy-MM-dd'T'HH:mm:ss`
- `yyyy-MM-dd HH:mm:ss`
- `yyyy-MM-dd`
- `yyyyMMdd`
- `yyyy`
- `MM/dd/yyyy`
- `dd/MM/yyyy`

### 2.3 表达式示例

```
# 基础字段引用
name
desc
box-2D

# 回退链
box-2D or screenshot or image

# 字符串拼接
name + ".jpg"
platform.system + "_" + filename

# 函数调用
upper(name)
replace(path, "\", "/")
stem(path)

# 嵌套调用
trim(replace(name, " ", "_"))

# 条件判断
if(video, "有视频", "无视频")
default(desc, "暂无描述")

# 复杂表达式
(name or filename) + ".jpg"
coalesce(box-2D, screenshot, image)
```

---

## 3. 可用变量

### 3.1 游戏字段变量

所有游戏字段都可以通过字段名直接引用。支持三种命名方式：

#### 3.1.1 基础字段

| 字段名 | 说明 | 示例值 |
|--------|------|--------|
| `name` | 游戏名称 | "Super Mario Bros" |
| `desc` | 游戏描述 | "A classic platform game..." |
| `releasedate` | 发布日期 | "1985-09-13" |
| `developer` | 开发者 | "Nintendo" |
| `publisher` | 发行商 | "Nintendo" |
| `genre` | 游戏类型 | "Platform" |
| `players` | 玩家数 | "1-2" |
| `rating` | 评分 | "0.85" |
| `path` | 游戏路径 | "./roms/nes/supermario.nes" |
| `lang` | 语言 | "en" |
| `region` | 区域 | "us" |
| `sort-by` | 排序字段 | "Mario" |
| `gameId` | 游戏 ID | "12345" |
| `hash` | 文件哈希 | "abc123..." |
| `crc32` | CRC32 校验 | "12345678" |
| `md5` | MD5 校验 | "abcdef..." |
| `source` | 数据来源 | "screenscraper" |
| `platformType` | 平台类型 | "console" |

#### 3.1.2 计算字段（只读）

| 字段名 | 说明 | 计算方式 |
|--------|------|----------|
| `filename` | 文件名（不含扩展名） | 从 `path` 提取 |
| `releaseYear` | 发布年份 | 从 `releasedate` 提取前 4 位 |

#### 3.1.3 字段别名

以下别名可以替代标准字段名：

| 别名 | 对应字段 |
|------|----------|
| `description` | `desc` |
| `file` | `path` |
| `region` | `lang` |
| `category` | `genre` |
| `releaseDate` | `releasedate` |
| `manuel` | `manual` |

### 3.2 平台变量

通过 `platform.` 前缀访问平台信息：

| 变量 | 说明 | 示例值 |
|------|------|--------|
| `platform.system` | 平台系统名 | "nes" |
| `platform.name` | 平台名称 | "Nintendo Entertainment System" |
| `platform.launch` | 启动命令 | "retroarch -L nes_libretro.dll" |
| `platform.software` | 平台软件 | "RetroArch" |
| `platform.database` | 数据库名 | "screenscraper" |
| `platform.web` | 网站地址 | "https://screenscraper.fr" |
| `platform.folderPath` | 平台文件夹路径 | "/roms/nes" |

### 3.3 计算变量（导入时自动注入）

在导入模板中，系统会自动注入以下计算变量：

| 变量 | 说明 | 计算方式 |
|------|------|----------|
| `filename` | 文件名（不含扩展名） | 从 `path` 提取 |
| `filepath` | 文件路径（不含扩展名和 `./` 前缀） | 从 `path` 提取 |

**示例**：
- `path` = `./roms/nes/supermario.nes`
- `filename` = `supermario`
- `filepath` = `roms/nes/supermario`

---

## 4. 字段名称对照表

v3 系统支持通过三种名称访问同一字段。以下是完整的对照表。

### 4.1 媒体类型字段对照表

| nomcourt (API 原值) | dbColumn (数据库列名) | javaField (Java 字段名) | 分类 | 格式 |
|---------------------|----------------------|------------------------|------|------|
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

### 4.2 命名规则说明

- **nomcourt**：ScreenScraper API 原值，保留原始大小写和连字符（如 `box-2D`）
- **dbColumn**：数据库列名，将 nomcourt 中的 `-` 替换为 `_`，全部小写（如 `box_2d`）
- **javaField**：Java 字段名，将 dbColumn 的 snake_case 转换为 camelCase（如 `box2d`）

### 4.3 字段访问示例

以下三种方式访问同一个字段：

```
# 使用 nomcourt
box-2D

# 使用 dbColumn
box_2d

# 使用 javaField
box2d
```

系统会自动识别并映射到同一个字段。

---

## 5. 导入模板配置

### 5.1 导入模板结构

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
      // 媒体发现配置（可选）
    }
  }
}
```

### 5.2 gameInfo 字段映射

`gameInfo` 定义数据文件字段到系统字段的映射：

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

**说明**：
- 键（如 `name`）是系统字段名
- 值（如 `["name", "sortname"]`）是数据文件中可能出现的字段名列表，按优先级匹配

### 5.3 mediaInfo 字段映射

`mediaInfo` 定义媒体文件路径到系统媒体字段的映射：

```json
"mediaInfo": {
  "box-2D": ["boxart", "image"],
  "ss": ["screenshot"],
  "video": ["video"],
  "wheel": ["wheel", "marquee"]
}
```

### 5.4 mediaDiscovery 配置

`mediaDiscovery` 用于自动在磁盘上查找媒体文件：

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

**配置说明**：
- `enabled`：是否启用媒体发现
- `baseDir`：媒体文件的基础目录
- `subDirPattern`：子目录模式，支持 `{filename}` 变量
- `extensions`：要搜索的文件扩展名列表
- `rules`：每种媒体类型的搜索规则，支持 `{filename}` 和 `{ext}` 变量

**变量替换**：
- `{filename}`：游戏文件名（不含扩展名）
- `{ext}`：当前搜索的文件扩展名

**示例**：
对于游戏 `supermario.nes`，系统会按以下顺序搜索 `box-2D` 媒体：
1. `media/supermario/boxFront.png`
2. `media/supermario/boxFront.jpg`
3. `media/supermario/box_front.png`
4. ...
5. `boxFront/supermario.png`
6. `box2dfront/supermario.png`
7. `images/supermario.png`

---

## 6. 导出模板配置

### 6.1 导出模板结构

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

### 6.2 system.header 配置

`header` 定义数据文件的表头行：

```json
"header": [
  "collection: {platform.system}",
  "sort-by: 064",
  "{platform.launch}",
  ""
]
```

**说明**：
- 每行是一个字符串
- 支持使用 `{platform.xxx}` 变量
- 空字符串表示空行

### 6.3 game.gameInfo 配置

`gameInfo` 定义系统字段到数据文件字段的映射：

```json
"gameInfo": {
  "game": "name",
  "file": "path",
  "sort-by": "sort-by",
  "developer": "developer",
  "description": "desc"
}
```

**说明**：
- 键（如 `game`）是数据文件中的字段名
- 值（如 `name`）是系统字段名或表达式

**支持表达式**：

```json
"gameInfo": {
  "title": "name or filename",
  "year": "sub(releasedate, 0, 4)",
  "path": "replace(path, '\\', '/')"
}
```

### 6.4 game.mediaInfo 配置

`mediaInfo` 定义数据文件媒体标签到系统媒体字段的映射：

```json
"mediaInfo": {
  "assets.box_front": "box-2D",
  "assets.screenshot": "ss",
  "assets.video": "video"
}
```

**说明**：
- 键（如 `assets.box_front`）是数据文件中的媒体标签
- 值（如 `box-2D`）是系统媒体字段名

### 6.5 output 配置

`output` 定义导出行为：

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

**配置说明**：
- `filename`：输出的数据文件名
- `pathFormat`：路径格式（`relative` 或 `absolute`）
- `directory`：目录结构配置
  - `roms`：ROM 文件目录
  - `media`：媒体文件目录
- `media`：媒体文件拷贝规则
  - `source`：源媒体字段
  - `target`：目标路径模板
- `m3u`：M3U 播放列表处理配置

**支持的变量**：
- `{outputPath}`：导出路径
- `{platform.system}`：平台系统名
- `{mediaPath}`：媒体目录路径
- `{gameName}`：游戏名称

---

## 7. 完整示例

### 7.1 ES-DE 导入模板示例

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import",
    "dataFileType": "data",
    "format": "xml",
    "dataFile": "gamelist.xml",
    "author": "Frontend-Killer",
    "description": "EmulationStation / ES-DE 导入模板（v3 统一格式）"
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

### 7.2 Pegasus 导入模板示例（含 mediaDiscovery）

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import",
    "dataFileType": "text",
    "delimiter": ":",
    "dataFile": "metadata.pegasus.txt",
    "author": "Frontend-Killer",
    "description": "Pegasus Frontend 导入模板（v3 统一格式）"
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

### 7.3 ES-DE 导出模板示例

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "export",
    "dataFileType": "data",
    "format": "xml",
    "dataFile": "gamelist.xml",
    "author": "Frontend-Killer",
    "description": "EmulationStation / ES-DE 导出模板（v3 统一格式）"
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

### 7.4 Pegasus 导出模板示例

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "export",
    "dataFileType": "text",
    "delimiter": ": ",
    "dataFile": "metadata.pegasus.txt",
    "author": "Frontend-Killer",
    "description": "Pegasus Frontend 导出模板（v3 统一格式）"
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

## 8. 最佳实践

### 8.1 字段命名建议

- **导入模板**：优先使用 nomcourt（如 `box-2D`），与 ScreenScraper API 保持一致
- **导出模板**：根据目标前端的要求选择命名
- **表达式**：使用最易读的名称，如 `box-2D` 比 `box_2d` 更清晰

### 8.2 媒体发现配置建议

- 为常用媒体类型配置多个搜索路径，提高匹配成功率
- 使用 `{filename}` 变量创建子目录结构，便于管理
- 包含常见的扩展名：`png`, `jpg`, `jpeg`, `gif`, `webp`, `mp4`

### 8.3 表达式使用建议

- 使用 `or` 运算符提供回退值：`name or filename`
- 使用 `default` 函数提供默认值：`default(desc, "暂无描述")`
- 使用 `coalesce` 处理多级回退：`coalesce(box-2D, screenshot, image)`
- 使用括号明确优先级：`(name or filename) + ".jpg"`

---

## 9. 故障排除

### 9.1 字段无法访问

**问题**：模板中使用的字段名无法获取值

**解决方案**：
1. 检查字段名是否正确（参考第 4 节对照表）
2. 尝试使用不同的命名方式（nomcourt / dbColumn / javaField）
3. 检查字段是否有值（某些字段可能为空）

### 9.2 表达式计算失败

**问题**：表达式返回 null 或错误值

**解决方案**：
1. 检查函数语法是否正确
2. 检查参数类型是否匹配
3. 使用简单的字段名测试，逐步添加复杂性

### 9.3 媒体发现失败

**问题**：mediaDiscovery 无法找到媒体文件

**解决方案**：
1. 检查 `baseDir` 路径是否正确
2. 检查 `rules` 中的路径模式是否匹配实际文件结构
3. 检查 `extensions` 是否包含目标文件的扩展名
4. 查看日志了解搜索过程

---

## 10. 附录

### 10.1 v2 与 v3 的主要区别

| 特性 | v2 | v3 |
|------|----|----|
| 字段命名 | 使用 javaField（如 `box2d`） | 支持 nomcourt / dbColumn / javaField |
| 表达式 | 简单变量替换 | 支持函数、运算符、条件 |
| 媒体发现 | 手动配置路径 | 自动 mediaDiscovery 规则 |
| 计算变量 | 无 | 自动注入 `filename`、`filepath` |
| 多盘游戏 | 不支持 | 自动展开 |

### 10.2 支持的 ScreenScraper 媒体类型总数

- **总计**：50 种官方媒体类型
- **图片**：40 种
- **视频**：8 种
- **文档**：1 种（manuel）
- **主题**：2 种（themehb, themehs）

### 10.3 版本信息

- **文档版本**：3.0
- **最后更新**：2026-09-13
- **适用系统**：Frontend Killer v1.0.5+
