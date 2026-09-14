# v3 导入模板编写指南

本文档面向**不需要懂 Java 的模板编写者**。通过编写一份 JSON 文件，即可定义任意新前端平台的导入规则。

---

## 1. 模板总体结构

一份 v3 导入模板是一个 JSON 文件，包含以下顶层块：

```json
{
  "templateInfo": { ... },
  "parsing":      { ... },
  "system":       { ... },
  "game":         { ... }
}
```

| 块 | 用途 | 必填 |
|----|------|------|
| `templateInfo` | 模板元信息（名称、方向、数据文件类型等） | 是 |
| `parsing` | 数据文件解析器行为参数（注释符、编码、大小写等） | 否（有默认值） |
| `system` | 系统信息映射（平台名、启动命令等只出现一次的信息） | 否 |
| `game` | 游戏信息映射（字段映射、媒体映射、计算变量等） | 是 |

---

## 2. `templateInfo` — 模板元信息

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import",
    "dataFileType": "text",
    "delimiter": ":",
    "dataFile": "metadata.pegasus.txt",
    "author": "你的名字",
    "description": "模板说明"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `version` | int | 是 | 固定为 `3` |
| `direction` | string | 是 | 固定为 `"import"` |
| `dataFileType` | string | 是 | `"text"`（纯文本）或 `"data"`（XML） |
| `delimiter` | string | text型必填 | 属性名与值的分隔符，如 `":"`、`"="` |
| `dataFile` | string | 是 | 数据文件名，如 `"gamelist.xml"` |
| `author` | string | 否 | 模板作者 |
| `description` | string | 否 | 模板描述 |
| `notes` | string | 否 | 备注 |

---

## 3. `parsing` — 解析器行为配置

控制 Java 引擎如何解析数据文件。**所有选项均有默认值**，缺失时不影响工作。

### 3.1 文本型（`dataFileType: "text"`）

```json
{
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
}
```

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `delimiter` | `templateInfo.delimiter` 或 `":"` | 属性名与值的分隔符 |
| `commentChars` | `["#"]` | 注释符列表，以这些字符开头的行被跳过。支持多个，如 `["#", ";", "//"]` |
| `entrySeparator` | `"emptyLine"` | 条目分隔方式。`"emptyLine"` 表示空行分隔不同游戏 |
| `keyCase` | `"lower"` | key 大小写策略：`"lower"` 转小写、`"upper"` 转大写、`"preserve"` 保留原始 |
| `encoding` | `"UTF-8"` | 数据文件编码，如 `"UTF-8"`、`"GBK"`、`"Shift_JIS"` |
| `multiLine.enabled` | `false` | 是否支持缩进续行（如 Pegasus 的多行描述） |
| `multiLine.indentChars` | `["  ", "\t"]` | 缩进字符列表，以此开头的行视为上一属性的续行 |

### 3.2 XML 型（`dataFileType: "data"`, `format: "xml"`）

XML 型不需要 `parsing.text` 配置。XML 的解析由 `system.systemTag` 和 `game.gameStartMarker`（即 XML 标签名）控制。

---

## 4. `system` — 系统信息映射

映射数据文件中只出现一次的系统级信息（如平台名、启动命令）。

```json
{
  "system": {
    "gameStartMarker": "game:",
    "systemTag": "provider",
    "fields": {
      "platform.system": ["collection"],
      "platform.launch": ["launch", "command"]
    }
  }
}
```

| 字段 | 说明 |
|------|------|
| `gameStartMarker` | （text 型）遇到此标记之前的 key-value 归为系统信息 |
| `systemTag` | （XML 型）系统信息所在的 XML 标签名 |
| `fields` | key = 内部字段名，value = 数据文件中的候选 key 列表 |

---

## 5. `game` — 游戏信息映射（核心）

### 5.1 结构概览

```json
{
  "game": {
    "gameStartMarker": "game:",
    "multiLine": true,
    "multiFile": { ... },
    "computedVariables": { ... },
    "gameInfo": { ... },
    "mediaInfo": { ... },
    "mediaDiscovery": { ... }
  }
}
```

### 5.2 `gameStartMarker` / `multiLine`

| 字段 | 说明 |
|------|------|
| `gameStartMarker` | text 型：每遇到此标记开始一条新游戏记录 |
| `multiLine` | text 型：是否启用缩进续行 |

### 5.3 `multiFile` — 多文件游戏展开

当一个游戏对应多个 ROM 文件时（如多碟游戏），展开为多条独立记录。

```json
{
  "multiFile": {
    "enabled": true,
    "field": "path",
    "separator": "\n"
  }
}
```

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `enabled` | `false` | 是否启用多文件展开 |
| `field` | `"path"` | 要展开的字段名 |
| `separator` | `"\n"` | 路径之间的分隔符 |

### 5.4 `computedVariables` — 计算变量

定义在映射前自动计算的变量，可在 `gameInfo`、`mediaInfo`、`mediaDiscovery` 的表达式中引用。

```json
{
  "computedVariables": {
    "filename": "stem(path)",
    "filepath": "replace(path or '', './', '')"
  }
}
```

- **key** = 变量名（注入到 rawFields 供后续引用）
- **value** = 表达式（使用表达式引擎求值，可引用数据文件中的任何字段）

> **向后兼容**：如果模板中未定义 `computedVariables`，引擎自动注入 `filename` 和 `filepath`（行为与旧版一致）。

### 5.5 `gameInfo` — 游戏属性字段映射

key = 内部字段名（对应数据库列），value = 数据文件中的候选 key 列表（按优先级依次尝试）。

```json
{
  "gameInfo": {
    "name": ["game", "title"],
    "desc": ["description", "desc", "summary"],
    "releasedate": ["release", "releasedate"],
    "rating": ["rating"],
    "path": ["file", "files"]
  }
}
```

**映射规则**：引擎按候选列表顺序查找 rawFields 中第一个有值的 key，将其值写入对应的内部字段。候选值也可以是表达式。

### 5.6 `mediaInfo` — 媒体字段映射

与 `gameInfo` 格式相同，但 key 必须使用 **ScreenScraper nomcourt 值**（见第 7 节）。

```json
{
  "mediaInfo": {
    "box-2D": ["assets.box_front", "assets.boxfront"],
    "ss": ["assets.screenshot"],
    "video": ["assets.video"]
  }
}
```

### 5.7 `mediaDiscovery` — 媒体文件自动发现

当数据文件中未声明媒体路径，或声明的路径指向的文件不存在时，按规则在磁盘上扫描匹配。

```json
{
  "mediaDiscovery": {
    "enabled": true,
    "baseDir": "media",
    "subDirPatterns": ["{filename}", "{name}"],
    "extensions": ["png", "jpg", "jpeg", "gif", "webp"],
    "rules": {
      "box-2D": [
        "{filename}/boxFront.{ext}",
        "boxFront/{filename}.{ext}"
      ],
      "ss": [
        "{filename}/screenshot.{ext}"
      ]
    }
  }
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `enabled` | 是 | 是否启用 |
| `baseDir` | 是 | 媒体文件根目录名（如 `"media"`） |
| `subDirPatterns` | 是 | 子目录命名策略列表，按优先级尝试。`{filename}` = ROM 文件名去扩展名，`{name}` = 游戏显示名 |
| `extensions` | 是 | 尝试的文件扩展名列表 |
| `rules` | 是 | key = nomcourt，value = 路径模式列表。`{filename}`、`{name}`、`{ext}` 会被动态替换 |

> **注意**：`baseDir`、`subDirPatterns`、`extensions` 三项必须显式声明，不提供默认值。

---

## 6. 表达式引擎

`computedVariables` 和 `gameInfo`/`mediaInfo` 的候选值均支持表达式。

### 6.1 变量引用

直接写字段名即可引用数据文件中的值：

```
name            → rawFields 中 key 为 "name" 的值
path            → rawFields 中 key 为 "path" 的值
```

### 6.2 运算符

| 运算符 | 说明 | 示例 |
|--------|------|------|
| `or` | 取第一个非空值 | `region or lang` |
| `+` | 字符串拼接 | `name + ".png"` |

### 6.3 内置函数

#### 字符串操作

| 函数 | 说明 | 示例 | 结果（假设 name="Super Mario"） |
|------|------|------|------|
| `sub(str, start, end)` | 截取子串 | `sub(name, 0, 5)` | `"Super"` |
| `upper(str)` | 转大写 | `upper(name)` | `"SUPER MARIO"` |
| `lower(str)` | 转小写 | `lower(name)` | `"super mario"` |
| `trim(str)` | 去首尾空白 | `trim(name)` | `"Super Mario"` |
| `replace(str, from, to)` | 替换子串 | `replace(path, '\', '/')` | 路径分隔符替换 |
| `len(str)` | 字符串长度 | `len(name)` | `"13"` |

#### 路径操作

| 函数 | 说明 | 示例（path="./roms/snes/game.smc"） | 结果 |
|------|------|------|------|
| `filename(path)` | 提取文件名（含扩展名） | `filename(path)` | `"game.smc"` |
| `stem(path)` | 去扩展名 | `stem(path)` | `"game"` |
| `ext(path)` | 取扩展名（含点号） | `ext(path)` | `".smc"` |
| `dir(path)` | 取目录部分 | `dir(path)` | `"./roms/snes"` |

#### 条件与默认值

| 函数 | 说明 | 示例 | 结果 |
|------|------|------|------|
| `default(field, fallback)` | 空值时使用默认 | `default(desc, '暂无描述')` | 描述为空时返回"暂无描述" |
| `if(cond, yes, no)` | 条件判断 | `if(rating, '有评分', '无评分')` | rating 非空返回"有评分" |
| `coalesce(f1, f2, ...)` | 取第一个非空值 | `coalesce(title, name, '未知')` | 依次尝试 |

#### 日期操作

| 函数 | 说明 | 示例 | 结果 |
|------|------|------|------|
| `dateformat(date, pattern)` | 日期格式化 | `dateformat(releasedate, 'yyyy')` | `"2023"` |

支持的输入格式：`yyyy-MM-dd'T'HH:mm:ss`、`yyyy-MM-dd HH:mm:ss`、`yyyy-MM-dd`、`yyyyMMdd`、`yyyy`、`MM/dd/yyyy`、`dd/MM/yyyy`

#### 函数嵌套

函数可以任意嵌套：

```
trim(replace(path, '\', '/'))
stem(filename(path))
default(lower(name), 'unknown')
```

---

## 7. 游戏属性字段与数据库对应关系

### 7.1 非媒体字段（gameInfo 中使用）

| 内部字段名 | 数据库列名 | Java 类型 | 说明 |
|-----------|-----------|----------|------|
| `name` | name | String | 游戏名称 |
| `desc` | desc | String | 游戏描述 |
| `releasedate` | releasedate | String | 发行日期 |
| `developer` | developer | String | 开发商 |
| `publisher` | publisher | String | 发行商 |
| `genre` | genre | String | 游戏类型 |
| `players` | players | String | 玩家人数 |
| `rating` | rating | Double | 评分（0.0 ~ 1.0，字符串会自动转换） |
| `path` | path | String | ROM 文件路径 |
| `lang` | lang | String | 语言/区域 |
| `hash` | hash | String | 文件哈希 |
| `gameId` | gameId | String | 游戏唯一标识 |
| `sort-by` | sortBy | String | 排序名称 |
| `source` | source | String | 数据来源 |
| `crc32` | crc32 | String | CRC32 校验值 |
| `md5` | md5 | String | MD5 校验值 |

### 7.2 媒体字段（mediaInfo 中使用）

所有媒体类型使用 **ScreenScraper API 官方 nomcourt 值** 作为标识符。以下列出常用类型：

#### 包装盒类

| nomcourt | 数据库列名 | 说明 |
|----------|-----------|------|
| `box-2D` | media_box_2d | 包装盒正面 |
| `box-2D-back` | media_box_2d_back | 包装盒背面 |
| `box-2D-side` | media_box_2d_side | 包装盒侧面 |
| `box-3D` | media_box_3d | 3D 包装盒 |
| `box-texture` | media_box_texture | 包装盒纹理 |
| `box-scan` | media_box_scan | 包装盒扫描源 |

#### 支撑类

| nomcourt | 数据库列名 | 说明 |
|----------|-----------|------|
| `support-2D` | media_support_2d | 支撑图 2D |
| `support-texture` | media_support_texture | 支撑纹理 |
| `support-scan` | media_support_scan | 支撑扫描源 |

#### 轮盘/Logo 类

| nomcourt | 数据库列名 | 说明 |
|----------|-----------|------|
| `wheel` | media_wheel | 轮盘 |
| `wheel-hd` | media_wheel_hd | 高清轮盘 |
| `wheel-carbon` | media_wheel_carbon | 碳纤维轮盘 |
| `wheel-steel` | media_wheel_steel | 钢铁轮盘 |

#### 霓虹灯类

| nomcourt | 数据库列名 | 说明 |
|----------|-----------|------|
| `marquee` | media_marquee | 霓虹灯 |
| `screenmarquee` | media_screenmarquee | 屏幕霓虹灯 |
| `screenmarqueesmall` | media_screenmarqueesmall | 小屏幕霓虹灯 |

#### 通用媒体类

| nomcourt | 数据库列名 | 说明 |
|----------|-----------|------|
| `ss` | media_ss | 截图 |
| `sstitle` | media_sstitle | 标题截图 |
| `steamgrid` | media_steamgrid | Steam 网格图 |
| `fanart` | media_fanart | 粉丝艺术 |
| `overlay` | media_overlay | 覆盖层 |
| `video` | media_video | 视频 |
| `video-normalized` | media_video_normalized | 标准化视频 |

#### 次要媒体类

| nomcourt | 数据库列名 | 说明 |
|----------|-----------|------|
| `flyer` | media_flyer | 传单 |
| `manuel` | media_manuel | 手册 |
| `maps` | media_maps | 地图 |
| `figurine` | media_figurine | 手办 |

#### 边框类

| nomcourt | 数据库列名 | 说明 |
|----------|-----------|------|
| `bezel-4-3` | media_bezel_4_3 | 边框 4:3 横屏 |
| `bezel-4-3-v` | media_bezel_4_3_v | 边框 4:3 竖屏 |
| `bezel-16-9` | media_bezel_16_9 | 边框 16:9 横屏 |
| `bezel-16-9-v` | media_bezel_16_9_v | 边框 16:9 竖屏 |

#### 混合类 / 主题类

| nomcourt | 数据库列名 | 说明 |
|----------|-----------|------|
| `mixrbv1` | media_mixrbv1 | 混合图 v1 |
| `mixrbv2` | media_mixrbv2 | 混合图 v2 |
| `themehb` | media_themehb | 主题 Homebrew |
| `themehs` | media_themehs | 主题 Hyperspin |

> **提示**：`gameInfo` 和 `mediaInfo` 的 key 支持 nomcourt（如 `box-2D`）、数据库列名（如 `box_2d`）、Java 字段名（如 `box2d`）三种写法，引擎会自动识别。推荐使用 **nomcourt** 以保持一致性。

---

## 8. 完整模板示例

### 8.1 Pegasus Frontend（文本型）

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import",
    "dataFileType": "text",
    "delimiter": ":",
    "dataFile": "metadata.pegasus.txt",
    "author": "Frontend-Killer",
    "description": "Pegasus Frontend 导入模板"
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
      "platform.system": ["collection"],
      "platform.launch": ["launch"]
    }
  },
  "game": {
    "gameStartMarker": "game:",
    "multiLine": true,
    "multiFile": {
      "enabled": true,
      "field": "path",
      "separator": "\n"
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
      "genre": ["genre", "genres", "tag"],
      "players": ["players"],
      "rating": ["rating"],
      "path": ["file", "files"],
      "sort-by": ["sort-by", "sort_title"],
      "lang": ["region"]
    },
    "mediaInfo": {
      "box-2D": ["assets.box_front", "assets.boxfront"],
      "ss": ["assets.screenshot"],
      "video": ["assets.video"],
      "wheel-carbon": ["assets.logo"]
    },
    "mediaDiscovery": {
      "enabled": true,
      "baseDir": "media",
      "subDirPatterns": ["{filename}", "{name}"],
      "extensions": ["png", "jpg", "jpeg", "gif", "webp", "mp4"],
      "rules": {
        "box-2D": ["{filename}/boxFront.{ext}"],
        "ss": ["{filename}/screenshot.{ext}"],
        "video": ["{filename}/video.{ext}"],
        "wheel-carbon": ["{filename}/wheel.{ext}", "{filename}/logo.{ext}"]
      }
    }
  }
}
```

### 8.2 EmulationStation / ES-DE（XML 型）

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import",
    "dataFileType": "data",
    "format": "xml",
    "dataFile": "gamelist.xml",
    "author": "Frontend-Killer",
    "description": "EmulationStation 导入模板"
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
      "lang": ["lang"]
    },
    "mediaInfo": {
      "box-2D": ["boxart", "image"],
      "ss": ["screenshot"],
      "video": ["video"],
      "wheel": ["wheel", "marquee"]
    }
  }
}
```

### 8.3 新平台模板最小示例

假设某前端 "MyFrontend" 使用 JSON 数据文件，每个游戏有 `title`、`rom_path`、`cover_url` 等字段：

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import",
    "dataFileType": "text",
    "delimiter": "=",
    "dataFile": "gamedb.txt",
    "description": "MyFrontend 导入模板"
  },
  "parsing": {
    "text": {
      "delimiter": "=",
      "commentChars": [";", "//"],
      "entrySeparator": "emptyLine",
      "keyCase": "lower",
      "encoding": "UTF-8"
    }
  },
  "game": {
    "gameStartMarker": "game:",
    "computedVariables": {
      "filename": "stem(path)"
    },
    "gameInfo": {
      "name": ["title"],
      "desc": ["description"],
      "releasedate": ["year"],
      "genre": ["category"],
      "path": ["rom_path"]
    },
    "mediaInfo": {
      "box-2D": ["cover_url", "cover"]
    }
  }
}
```

将此文件保存为 `rules/import/myfrontend-v3.json` 即可在界面中选择使用。

---

## 9. 编写要点速查

1. **新建模板**：复制上述最小示例，修改 `templateInfo` 和字段映射即可
2. **文件位置**：`rules/import/你的前端名-v3.json`
3. **候选列表**：`gameInfo` 和 `mediaInfo` 的 value 是数组，按优先级依次尝试
4. **表达式**：候选值可以是表达式字符串，如 `"stem(name)"` 或 `"replace(path, '\\', '/')"`
5. **媒体类型**：`mediaInfo` 的 key 推荐使用 ScreenScraper nomcourt（如 `box-2D` 而非 `box2dfront`）
6. **计算变量**：`computedVariables` 中定义的变量可在 `gameInfo`/`mediaInfo` 的表达式中引用
7. **多文件游戏**：启用 `multiFile` 后，含分隔符的字段值会被展开为多条记录
8. **媒体发现**：`mediaDiscovery` 的 `baseDir`、`subDirPatterns`、`extensions` 必须显式声明
9. **向后兼容**：不含 `parsing`/`computedVariables`/`multiFile` 的旧 v3 模板仍可正常工作
