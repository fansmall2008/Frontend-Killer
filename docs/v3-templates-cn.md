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
  },
  "variables": [
    // 可选：执行前需用户设定的模板变量（见 3.4）
  ]
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
| `before` | `before(str, sep)` | 取分隔符之前部分；无分隔符时返回原串 | `before(path, "#")` |
| `after` | `after(str, sep)` | 取分隔符之后部分；无分隔符时返回原串 | `after(path, "#")` |
| `concat` | `concat(f1, f2, ...)` | 拼接所有参数，跳过空值 | `concat(cdn, '/', filename)` |
| `sanitize` | `sanitize(str, charset)` | 将 charset 中出现的每个字符替换为 `_` | `sanitize(name, "&*/:\\<>?\|")` |
| `matches` | `matches(str, regex)` | 正则全匹配，返回 `true`/`false` | `matches(crc32, '^[0-9A-Fa-f]{8}\\|crc$')` |

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

#### 2.2.5 方言映射函数

| 函数 | 语法 | 说明 | 示例 |
|------|------|------|------|
| `map` | `map(value[, category])` | 方言映射：命中方言映射表返回目标词（多值输入逐段映射，`-` 连接），未命中原样返回 | `map(genre, "genre")` |
| `mapFirst` | `mapFirst(value[, category])` | 方言映射，只返回第一个映射结果（适合目录名等单一值场景） | `mapFirst(genre)` |

`map()` / `mapFirst()` 基于系统的**方言映射表**（term_mapping，可在「系统设置」中维护）做确定性翻译，例如把刮削返回的英文游戏类型统一为中文：`Beat-'Em-Up` → `清版游戏`。

- `map(value)`：在全部映射分类中查找（`system_alias` 分类为平台系统匹配内部使用，不参与）；同一词命中多个分类时按分类名字典序取第一个，保证结果确定
- `map(value, category)`：仅在指定分类中查找
- 匹配前对源词做归一化（小写、去空格与标点），因此 `Beat'em Up`、`beat-em-up`、`BEAT EM UP` 视为同一个词
- 未命中时原样返回输入值，不报错

#### 2.2.6 通用语法说明

- **函数名大小写不敏感**：`mapFirst(...)` 与 `mapfirst(...)` 等价
- **字符串字面量**：双引号 `"..."` 或单引号 `'...'` 均可；函数参数中的逗号、括号在引号内不需转义
- **惰性求值**：`default`、`if`、`coalesce` 三个函数按需求值参数，未选中的分支不参与计算，避免无效求值报错
- **未知函数**：返回 null 并记录 WARN 日志，不中断导出

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

# 方言映射
map(genre, "genre")
mapFirst(genre)

# 分隔符处理（RetroArch 播放列表常用）
before(path, '#')
ext(before(path, '#'))

# 文件名清洗（RetroArch 缩略图规则）
sanitize(name, "&*/:\\<>?|")

# 正则校验
matches(crc32, '^[0-9A-Fa-f]{8}\\|crc$')

# 复杂表达式
(name or filename) + ".jpg"
coalesce(box-2D, screenshot, image)
concat('Named_Boxarts/', sanitize(name, '&*/:\\<>?|'), '.png')
```

---

## 3. 可用变量

### 3.1 游戏字段变量

所有游戏字段都可以通过字段名直接引用。支持三种命名方式：

#### 3.1.1 基础字段

| 字段名 | 说明 | 示例值 |
|--------|------|--------|
| `name` | 游戏名称 | "Super Mario Bros" |
| `translatedName` | 译文游戏名（导出前按所选语种注入，常与 `coalesce` 配合） | "超级马里奥兄弟" |
| `desc` | 游戏描述 | "A classic platform game..." |
| `translatedDesc` | 译文游戏描述 | "经典平台游戏..." |
| `releasedate` | 发布日期 | "1985-09-13" |
| `developer` | 开发者 | "Nintendo" |
| `publisher` | 发行商 | "Nintendo" |
| `genre` | 游戏类型 | "Platform" |
| `players` | 玩家数 | "1-2" |
| `rating` | 评分 | "0.85" |
| `path` | 游戏路径（多盘游戏为首行路径） | "./roms/nes/supermario.nes" |
| `lang` | 语言 | "en" |
| `sort-by` | 排序字段 | "Mario" |
| `gameId` | 游戏 ID | "12345" |
| `hash` | 文件哈希 | "abc123..." |
| `crc32` | CRC32 校验 | "12345678" |
| `md5` | MD5 校验 | "abcdef..." |
| `source` | 数据来源 | "screenscraper" |
| `platformType` | 平台类型 | "console" |
| `multiFile` | 是否多盘游戏（布尔；表达式中为 `"true"` 或空串） | "true" |
| `multiFileContent` | 多盘文件清单文本（每行一个文件，导入时由 multiFileDetection 写入） | "disc1.bin\ndisc2.bin" |

#### 3.1.2 计算字段（只读）

| 字段名 | 说明 | 计算方式 |
|--------|------|----------|
| `filename` | 文件名（不含扩展名） | 从 `path` 提取 |
| `releaseYear` | 发布年份 | 从 `releasedate` 提取前 4 位 |
| `m3uPath` | 多盘游戏的 m3u 文件名（仅 `multiFile=true` 时非空） | `stem(path 首行) + ".m3u"`，非法字符替换为 `_` |

`m3uPath` 常与 `if` 组合实现多盘游戏路径改写：`if(multiFile, m3uPath, path)`（ES-DE / EmuELEC / RetroBat 导出模板的写法）。

#### 3.1.3 字段别名

以下别名可以替代标准字段名：

| 别名 | 对应字段 |
|------|----------|
| `description` | `desc` |
| `file` | `path` |
| `region` | `lang`（注意：没有独立的“区域”字段，region 取值来自 lang） |
| `category` | `genre` |
| `releaseDate` | `releasedate` |
| `manuel` | `manual` |

> 匹配时忽略大小写，并把 `-`/`_` 归一化处理，因此 `box2dfront`、`Box-2D`、`box_2d` 等写法都能命中同一字段。

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
| `platform.sortBy` | 平台排序值（Pegasus sort-by 行） | "064" |
| `platform.extensions` | 平台扩展名列表（Pegasus extensions 行） | "nes\|fds" |
| `platform.ignoreFiles` | 忽略文件列表（Pegasus ignore-files 行） | "\*.bak" |

### 3.3 计算变量（computedVariables 声明注入）

导入模板可通过 `game.computedVariables` 声明自定义计算变量（key=变量名，value=表达式），导入时对每个游戏求值后注入变量表：

```json
"computedVariables": {
  "filename": "stem(path)",
  "filepath": "replace(path or '', './', '')",
  "safeName": "sanitize(label, '&*/:\\<>?|')"
}
```

- 计算变量在**后续字段映射和 mediaDiscovery 规则**中均可引用（如 RetroArch 导入模板用 `{playlistName}`/`{safeName}` 拼接缩略图路径）
- 模板未声明 `computedVariables` 时（旧模板兼容），回退到内置默认注入：

| 变量 | 说明 | 计算方式 |
|------|------|----------|
| `filename` | 文件名（不含扩展名） | 从 `path` 提取 |
| `filepath` | 文件路径（不含扩展名和 `./` 前缀） | 从 `path` 提取 |

**示例**：
- `path` = `./roms/nes/supermario.nes`
- `filename` = `supermario`
- `filepath` = `roms/nes/supermario`

导出方向同样有内置变量：`{outputPath}`（导出路径）、`{gameName}`（游戏名，媒体 target 中使用）、`{filename}`、`{ext}`（文件名模板中使用，版本替换时为版本扩展名）以及顶层 `variables` 声明的模板变量（见 3.4）。

### 3.4 模板自定义变量（variables 块）

有些数值在“游戏加工阶段”是不知道的，需要在执行导入/导出**动作之前**由用户设定（例如拼接 URL 的前缀、导出后 ROM 存放的文件夹名）。v3 模板提供可选的顶层 `variables` 块声明这些变量；选中含变量声明的模板后，点击导入/导出会弹出对话框，逐项显示**变量名、输入框、说明**，用户填写的值作为全局变量随请求发送到后端。

```json
"variables": [
  {
    "name": "romSubdir",
    "label": "ROM 文件夹",
    "description": "导出后游戏文件存放的子文件夹名，播放列表 path 会指向它",
    "type": "text",
    "default": "{platform.system}",
    "required": true
  }
]
```

| 字段 | 说明 |
|------|------|
| `name` | 变量名，表达式与 `{xxx}` 占位符中使用；须为标识符（字母/下划线开头），不得与内置变量重名 |
| `label` | 弹窗中显示的变量名 |
| `description` | 弹窗中显示的说明（为什么需要、作用是什么） |
| `type` | 仅 `text` \| `path` 两种（`path` 类型弹窗提供路径浏览） |
| `default` | 可选默认值，支持 `{platform.xxx}` 等占位符，执行时按平台解析 |
| `required` | 默认 `false`；为空且 `required=true` 时前端阻止提交、后端返回 400 |

**行为约定**：
- 变量值**不持久化**，每次导入/导出都需重新填写。
- 多平台批量导出只弹一次框，所有平台**共享同一份**取值；平台差异通过 `default`/表达式中的 `{platform.xxx}` 解决。
- 变量名与内置变量冲突时内置优先（该声明被忽略并打 WARN 日志），保证旧模板不受影响。
- 无 `variables` 块的模板行为完全不变。

**如何在模板中使用变量（拼接 URL / 字符串）**：注入的变量存于同一份 vars map，引擎有两条通道都会读取它，变量可直接用于拼接：

1. **`{var}` 占位符通道**：对 `output.*.directory`、`filename`、header/footer 行等做字面 `{key}→value` 替换。如 `"directory": "{outputPath}/{romSubdir}"`。
2. **表达式裸标识符通道**：变量名可直接出现在 `concat(...)`、`+` 等表达式中。如 `"image": "concat(cdnBase, '/', platform.system, '/', filename(name), '.png')"`。

变量为空时，`concat`/`+` 按现有语义跳过空值或返回 null，不影响其他字段。参考示例模板：`rules/export/retroarch-folder-v3.json`。

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
    "format": "xml|json|text",
    "dataFile": "gamelist.xml",
    "delimiter": ":"
  },
  "parsing": {
    // 解析器行为参数（可选，见 5.2）
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
      // 多盘游戏检测（可选，见 5.3）
    },
    "computedVariables": {
      // 计算变量声明（可选，见 3.3）
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
      // 媒体发现配置（可选，见 5.6）
    }
  }
}
```

### 5.2 parsing 解析配置

`parsing` 定义数据文件的解析器行为参数，使同一套引擎能处理不同前端的格式。缺省时使用默认值保持向后兼容。

**text 型**：

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

| 字段 | 说明 |
|------|------|
| `delimiter` | 属性分隔符（如 `":"`、`"="`） |
| `commentChars` | 注释符列表，命中行跳过 |
| `entrySeparator` | 条目分隔方式，`"emptyLine"` 表示空行分条 |
| `keyCase` | key 大小写策略：`lower` / `upper` / `preserve` |
| `encoding` | 文件编码（默认 UTF-8） |
| `multiLine.enabled` | 是否支持缩进续行（Pegasus files: 多行块依赖此配置） |
| `multiLine.indentChars` | 续行缩进字符列表 |

**json 型**（RetroArch 播放列表等）：

```json
"parsing": {
  "json": {
    "root": "items",
    "encoding": "UTF-8"
  }
}
```

| 字段 | 说明 |
|------|------|
| `root` | 游戏条目数组的 JSON 路径（点号分隔），默认 `"items"` |
| `encoding` | 文件编码（默认 UTF-8） |

### 5.3 multiFileDetection 多盘游戏检测

`multiFileDetection` 把多盘信息写入 Game 记录的 `multiFile=true` + `multiFileContent` 字段，触发后**保持单条记录**（不再展开为多条 Game）：

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

| 字段 | 取值 | 说明 |
|------|------|------|
| `source` | 字段名 | 检测源字段（如 `path`、`files`） |
| `trigger` | `fieldExists` \| `endsWith` | 触发条件：字段存在即触发 / 字段首行以 pattern 结尾（大小写不敏感） |
| `pattern` | 如 `".m3u"` | `endsWith` 的匹配模式，默认 `".m3u"` |
| `content` | `fieldValue` \| `fileContent` | 内容来源：源字段值清洗 / 读取文件内容（m3u） |
| `pathFrom` | `firstLine` | 可选；`firstLine` 时 path 取清洗后第一行 |

**两种典型模式**（内置导入模板的差异）：

| 模式 | 配置组合 | 适用模板 |
|------|----------|----------|
| Pegasus 多行块 | `source=files, trigger=fieldExists, content=fieldValue, pathFrom=firstLine` | pegasus-v3 |
| m3u 指向 | `source=path, trigger=endsWith(.m3u), content=fileContent` | esde / emuelec / retrobat / skraper-es / webgamelistoper |

**清洗规则**（`content` 写入 `multiFileContent` 前）：
- 去除 `#` 注释行与空行，每行 trim
- 去掉行首 `./` 或 `.\` 前缀
- **不展开 cue→bin**：条目本身保留原样，cue 关联文件收集只发生在导出阶段（见 6.7 roms.cue）
- **目录基准统一按平台 ROM 根目录**：`fileContent` 模式读入 m3u 后，清洗行不含 m3u 所在目录前缀；若 m3u 在子目录中且使用相对引用，导入后的条目在导出阶段会按平台根目录解析，**子目录相对引用会失基准**
- Pegasus `files:` 多行续行文本同样按上述规则清洗后存入 `multiFileContent`

**多盘游戏展开（旧语义，遗留）**：`game.multiFile` 块（`enabled`/`field`/`separator`）会把多值字段拆成多条独立 Game 记录，与 `multiFileDetection` 互斥（检测触发后优先保持单条记录）。新模板建议只用 `multiFileDetection`。

### 5.4 gameInfo 字段映射

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

### 5.5 mediaInfo 字段映射

`mediaInfo` 定义媒体文件路径到系统媒体字段的映射：

```json
"mediaInfo": {
  "box-2D": ["boxart", "image"],
  "ss": ["screenshot"],
  "video": ["video"],
  "wheel": ["wheel", "marquee"]
}
```

### 5.6 mediaDiscovery 配置

`mediaDiscovery` 用于自动在磁盘上查找媒体文件：

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

**配置说明**：
- `enabled`：是否启用媒体发现
- `baseDir`：媒体文件的基础目录（相对数据文件所在目录；可为 `"../thumbnails"` 等相对路径）
- `subDirPatterns`：子目录命名策略**优先级列表**（数组，按序尝试；如 `["{filename}", "{name}"]` 表示先用 ROM 文件名匹配，再用显示名匹配）
- `extensions`：要搜索的文件扩展名列表
- `rules`：每种媒体类型的搜索规则，支持 `{filename}`、`{ext}` 及 computedVariables 声明的变量（如 `{safeName}`）

**变量替换**：
- `{filename}`：游戏文件名（不含扩展名）
- `{name}`：游戏显示名
- `{ext}`：当前搜索的文件扩展名
- computedVariables 声明的变量（如 RetroArch 模板的 `{playlistName}`、`{safeName}`）

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
    "format": "xml|json|text",
    "delimiter": ": ",
    "dataFile": "metadata.pegasus.txt"
  },
  "variables": [
    // 可选：导出前弹窗设定的模板变量（见 3.4）
  ],
  "system": {
    "header": [ /* 数据文件头部行 */ ],
    "footer": [ /* 数据文件尾部行 */ ],
    "fields": { /* 头/尾行中的字段映射（可选） */ }
  },
  "game": {
    "gameStartMarker": "game:",
    "entrySeparator": "\n\n",
    "gameInfo": { /* 游戏字段 → 数据文件字段的映射（值为表达式） */ },
    "mediaInfo": { /* 数据文件媒体标签 → 源媒体字段（nomcourt） */ }
  },
  "output": {
    "exportOptions": { /* UI 导出选项声明 */ },
    "roms": { /* ROM 文件复制：enabled/directory/filename/m3u/cue */ },
    "media": { /* 媒体文件复制：directory/rules */ },
    "dataFile": { /* 数据文件：directory/filename/pathFormat 等 */ },
    "readme": { /* 可选：使用说明文件生成 */ }
  }
}
```

### 6.2 output.exportOptions 导出选项声明

`exportOptions` 声明目标前端支持的导出范围，控制导出表单上哪些选项可用及默认值：

```json
"exportOptions": {
  "gameFiles": true,
  "mediaFiles": true,
  "gameListFile": true,
  "showWarning": false
}
```

| 字段 | 说明 |
|------|------|
| `gameFiles` | 是否允许导出 ROM 文件（控制 roms 块是否生效） |
| `mediaFiles` | 是否允许导出媒体文件（控制 media 块是否生效） |
| `gameListFile` | 是否允许生成数据文件（控制 dataFile 块是否生效） |
| `showWarning` | 是否在 UI 上显示限制警告（如“仅导出元数据和媒体，不导出 ROM”） |

用户在导出表单上的实际勾选决定运行时哪些 output 块生效。

### 6.3 system.header / footer

`header` / `footer` 定义数据文件的头部与尾部行：

```json
"header": [
  "collection: {platform.name}",
  "sort-by: {platform.sortBy}",
  "{platform.launch}",
  ""
]
```

**说明**：
- 每行是一个字符串，支持 `{platform.xxx}` 占位符与表达式
- 空字符串表示空行
- `fields` 可选，用于声明头/尾行中出现的字段映射

### 6.4 game.gameInfo / game.mediaInfo

**gameInfo**（键 = 数据文件字段/标签，值 = 表达式）：

```json
"gameInfo": {
  "game": "coalesce(name, translatedName)",
  "file": "if(multiFile, '', path)",
  "files": "multiFileContent",
  "path": "if(multiFile, m3uPath, path)",
  "crc32": "if(crc32, concat(crc32, '|crc'), 'DETECT')"
}
```

- 值支持全部表达式语法（第 2 节）
- 表达式引用 `path` 或 `m3uPath` 时，求值结果会自动按 `output.dataFile.pathFormat` 格式化（见 6.9）
- 值为空时该字段不输出
- **多盘游戏输出**：值为多行文本时（如 Pegasus `files: multiFileContent`），文本格式按“首行 key+分隔符、后续行缩进两空格”输出，即 Pegasus 的 `files:` 多行块

**mediaInfo**（键 = 数据文件中的媒体标签，值 = 源媒体字段 nomcourt）：

```json
"mediaInfo": {
  "image": "box-2D",
  "assets.video": "video",
  "assets.boxFront": "box-2D"
}
```

- 媒体路径**优先**使用 `output.media.rules` 中对应 `dataFileTag` 计算出的导出路径覆盖（见 6.8）；无覆盖时回退数据库中的原始媒体路径
- rules 中未定义 `dataFileTag` 的媒体类型只复制文件，不写入数据文件
- rules 的 key 不限于 54 种 ScreenScraper nomcourt，也可用旧版字段名（如 `image`、`banner`、`manual`），引擎按相同方式访问 Game 字段

### 6.5 output.roms ROM 复制

`output.roms` 定义 ROM 文件复制行为：

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

| 字段 | 说明 |
|------|------|
| `enabled` | 是否执行 ROM 复制；**false 时整个 ROM 复制阶段（含 m3u 生成与 cue 关联文件收集）全部跳过，m3u/cue 配置形同虚设** |
| `directory` | 目标目录模板；支持 per-game 表达式（如 `if(genre, concat('{outputPath}/', platform.system, '/', mapFirst(genre)), '{outputPath}/{platform.system}')` 实现按游戏类别分子目录） |
| `filename` | 目标文件名模板，精确到文件名级别；默认 `{filename}{ext}` 保持原名；支持 sanitize/concat 等表达式 |

> 整目录拷贝模式（导出表单选择“整目录拷贝”）下不逐游戏判关联，直接把平台源 ROM 目录全量拷到 `roms.directory`，m3u/cue 逐游戏逻辑不参与。

### 6.6 output.roms.m3u 播放列表处理

`roms.m3u` 控制导出阶段的 M3U 处理，默认 `enabled=false`。它涉及**两条不同的 m3u 路径**：

**路径一：multiFile 游戏重新生成 m3u**
- 多盘游戏（`multiFile=true`，内容来自导入时入库的 `multiFileContent`）复制各盘文件后，在 ROM 目录生成 `{filename}.m3u`
- 内容 = `multiFileContent` 清洗后的行（去注释/空行/`./` 前缀，分隔符归一为 `/`）

**路径二：旧数据 path 指向 .m3u 的解析重写**
- 游戏 `path` 指向 `.m3u` 文件时（导入时已解析为 multiFile 或直接以 m3u 文件存在），复制 m3u 引用的所有关联文件并**重写 m3u 内容**
- 重写规则：每行改为相对 m3u 所在目录的相对路径，分隔符归一为 `/`；缺失条目行保留原样
- `m3u.target`：可选，指定引用文件的统一目标目录模板（缺省为 m3u 本体所在目标目录）
- m3u 条目为 `.cue` 时继续按 cue 规则收集关联文件（见 6.7）；条目为 `.m3u` 时继续展开

**重要语义**：
- m3u 内容基于**导入时入库的快照**（`multiFileContent` 或数据库记录的 path）；导入后手动修改的 m3u 文件**不会**反映到导出
- `roms.enabled=false` 时整个 ROM 复制阶段跳过，m3u 配置不生效
- `filename` 模板变量在多盘场景下为 path 首行的 stem

### 6.7 output.roms.cue CUE/BIN 关联文件收集

`roms.cue` 控制 CUE 光盘镜像关联文件（`.bin`/`.wav` 等）的自动收集，是 v3 导出的默认行为：

```json
"cue": { "enabled": true, "onMissing": "warn" }
```

| 字段 | 默认 | 说明 |
|------|------|------|
| `enabled` | `true` | **旧模板无 cue 段时视为 enabled=true**，行为向后兼容；为 false 时不解析 cue 内 FILE 引用 |
| `onMissing` | `warn` | 引用文件缺失处理：`warn`（任务日志 + 完成结果汇总清单）/ `skip`（静默，仅 debug 日志）/ `fail`（写任务日志后中断**该游戏**的复制，不中断整批导出）；未知取值回退 warn |

**触发场景**（cue.enabled=true 时）：
1. 单文件游戏导出后目标文件以 `.cue` 结尾 → 解析 cue 内 FILE 引用收集关联文件
2. multiFile 游戏展开复制时，`multiFileContent` 条目为 `.cue` → 收集其 FILE 引用
3. m3u 解析重写时，m3u 条目为 `.cue` → 收集其 FILE 引用

**递归展开行为**：
- cue 内 `FILE` 行逐条解析：支持带引号（引号内可含空格路径）与不带引号两种形式；跳过空行、`;`/`#` 注释与 BOM
- **cue 内部路径以 cue 所在目录为基准解析**；引用文件按源中相对 cue 的位置落到 cue 目标旁（保持相对结构；前导 `../` 或跨盘符时降级为文件名落平）
- **多音轨多 bin 全收**：每个 FILE 行对应一个关联文件，全部收集复制
- cue 引用 cue → 继续展开；条目为 `.m3u` → 继续展开其中条目
- 嵌套展开**深度上限 16**（m3u→cue→m3u 链），配合已访问集合防无限递归
- 所有待复制文件按源绝对路径去重后统一复制

**缺件处理细节**：
- `warn`：缺件记入任务日志，并在导出完成结果中附“CUE缺件 N 个”汇总清单（**最多列 20 条**，超出以 … 截断）
- `fail`：仅中断当前游戏的复制（已被外层单游戏异常捕获），后续游戏继续
- multiFile 条目 / m3u 条目自身的缺失同样按 `onMissing` 规则处理（即使 cue.enabled=false）

### 6.8 output.media 媒体复制

`output.media` 定义媒体文件复制：

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

| 字段 | 说明 |
|------|------|
| `directory` | 媒体根目录模板（支持 `{outputPath}`、`{platform.xxx}`、`{gameName}` 等变量） |
| `rules` | 每种媒体类型的输出规则；key = 源 nomcourt |
| `rules.*.source` | 源媒体字段（与 key 相同，冗余便于阅读） |
| `rules.*.target` | 目标路径模板（相对 media.directory）；支持 `{filename}`、`{gameName}` 变量与表达式（如 `sanitize`、`concat`） |
| `rules.*.dataFileTag` | 数据文件中对应的标签；**有值时数据文件中的该媒体路径改写为导出后路径**（相对数据文件目录重新计算）；无值时只复制文件不改写 |

数据文件中的媒体路径 = 先计算 media.directory + target 的完整路径，再按数据文件目录做相对化，最后按 `pathFormat` 决定输出格式（见 6.9）。

### 6.9 output.dataFile 数据文件

`output.dataFile` 定义数据文件的输出：

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

| 字段 | 说明 |
|------|------|
| `directory` / `filename` | 数据文件目录与文件名模板（如 `{platform.name}.lpl`） |
| `pathFormat` | 路径格式，见下 |
| `pathPrefix` | 预留字段（当前引擎不读取，前缀增删由 pathFormat 完成） |
| `jsonItemsKey` | JSON 型：游戏条目数组的键名，默认 `"items"` |
| `jsonTopLevelFields` | JSON 型：条目数组之前输出的顶层字段（保序）；每项含 `key`/`value`/可选 `type`（`string`/`number`/`boolean`/`null`，缺省 auto 推断，可防 `"1.5"` 被误判为数字） |

**pathFormat 精确语义**（`formatExportPath` 只做前缀增删，**不做真正 relativize**）：

| 取值 | 行为 |
|------|------|
| `relative` | 去掉路径开头的 `./` 前缀（默认行为，非 relative 也如此） |
| `absoluteWithDot` | 确保路径带 `./` 前缀（Windows 盘符路径与 `/` 开头的绝对路径除外） |
| `absolute` / 其他 | 同 relative：仅去 `./` 前缀 |

- **path 类字段**（表达式引用 `path`/`m3uPath`）：只做上述前缀增删，不做相对化——写什么路径就输出什么路径（含 `pathFormat` 前缀处理）
- **媒体路径**：先按数据文件目录 → 媒体文件完整路径做真正的相对化（跨盘符回退字符串计算），再按 `pathFormat` 处理前缀

### 6.10 output.readme 使用说明生成

`output.readme` 可选，导出完成后在导出目录生成使用说明文件：

```json
"readme": {
  "enabled": true,
  "directory": "{outputPath}",
  "filename": "使用说明-{platform.name}.md",
  "content": [
    "# {platform.name} 播放列表导出说明",
    "..."
  ]
}
```

- `content` 逐行输出，支持 `{platform.xxx}`、`{outputPath}` 等占位符替换
- 参考模板：`rules/export/retroarch-v3.json`、`rules/export/retroarch-folder-v3.json`

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

### 7.2 Pegasus 导入模板示例（含 multiFileDetection 与 mediaDiscovery）

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

要点说明（该示例与 `rules/export/esde-v3.json` 完全一致）：

- **gameFiles: false + roms.enabled: false**：只导出元数据与媒体，整个 ROM 复制阶段（含 m3u 生成、cue 收集）跳过，roms 段配置不生效
- **path 表达式 `if(multiFile, m3uPath, path)`**：多盘游戏输出指向生成的 .m3u 文件，普通游戏输出原始 path
- **name/desc 用 coalesce 回退译名**：优先中文译名（translatedName/translatedDesc）
- **media.rules.dataFileTag**：媒体复制完成后，数据文件中对应标签（如 `<image>`）的路径改写为导出后位置（先相对化，再按 pathFormat 处理）
- **pathFormat: "relative"**：path 类字段（path/m3uPath）只做 `./` 前缀处理，不做真正的 relativize；媒体路径先按数据文件目录相对化再套用前缀规则
- **pathPrefix: "./"**：预留字段，当前版本未使用

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

要点说明（该示例与 `rules/export/pegasus-v3.json` 完全一致）：

- **file 表达式 `if(multiFile, '', path)`**：多盘游戏 file 留空（多盘路径由 files 列表表达），避免输出错误的首文件路径
- **files 表达式 `multiFileContent`**：输出多行块（首行 `files:`，后续行缩进两空格），由 text multiLine 缩进规则解析
- **roms.m3u.enabled: false**：Pegasus 用 files 多行列表，无需重新生成 .m3u；roms.cue 仍启用（multiFileContent 条目中的 .cue 引用照常收集）
- **roms.directory 按 genre 分子目录**：`mapFirst(genre)` 取第一个类别映射为目录名，无类别时回退 `{outputPath}/{platform.system}`
- **media.target 使用 {gameName}**：每个游戏独立目录（如 `media/超级马里奥/boxFront.png`），dataFileTag 对应 `assets.*` 嵌套标签
- **pathFormat: "relative" + pathPrefix: ""**：path 类字段仅去除 `./` 前缀

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

### 8.4 ROM 与播放列表导出建议

- 多盘游戏依赖导入时的 multiFileDetection 识别：模板未配置或未触发时，游戏不会标记 multiFile，导出端 m3u/cue 均不会介入
- CUE/BIN 集合建议保持 `onMissing: "warn"`：缺件只汇总不中断，便于事后补文件；需要严格校验时才用 `fail`
- 是否生成 .m3u 取决于目标前端：ES-DE/RetroBat/EmuELEC 需要且已启用；Pegasus 用 files 多行列表，默认关闭
- m3u 内容来自导入时入库的快照，导入后手动改动源 m3u 不会反映到导出

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

### 9.4 CUE/BIN 缺件提示

**问题**：导出多盘游戏时日志出现"缺少文件"，或完成结果中列出缺件清单

**说明**：
- `roms.cue.onMissing` 控制缺件行为：`warn`（默认，记录日志并在完成结果汇总）/ `skip`（静默跳过）/ `fail`（仅中断该游戏的复制，不影响其他游戏）
- 完成结果最多列出 20 条缺件，超出部分以 `…` 截断
- `cue.enabled: false` 仅跳过 .cue 内部 FILE 引用解析；m3u/multiFile 条目缺失仍按 onMissing 处理

### 9.5 多盘游戏导出后没有 .m3u

**解决方案**：
1. 确认游戏在导入时已被识别为多盘游戏（multiFileDetection 触发，multiFile=true）
2. 确认 `output.roms.enabled: true`（false 时整个 ROM 复制阶段跳过，m3u/cue 配置均不生效）
3. 确认 `output.roms.m3u.enabled: true`
4. Pegasus 模板默认关闭 m3u（files 多行列表已足够），属预期行为

---

## 10. 附录

### 10.1 v2 与 v3 的主要区别

| 特性 | v2 | v3 |
|------|----|----|
| 字段命名 | 使用 javaField（如 `box2d`） | 支持 nomcourt / dbColumn / javaField |
| 表达式 | 简单变量替换 | 支持函数、运算符、条件 |
| 媒体发现 | 手动配置路径 | 自动 mediaDiscovery 规则 |
| 计算变量 | 无 | 模板 `computedVariables` 声明注入（旧模板回退内置 `filename`/`filepath`） |
| 多盘游戏 | 不支持 | multiFileDetection 识别为单记录（内容存 `multiFileContent`），不拆分展开 |

### 10.2 支持的 ScreenScraper 媒体类型总数

- **总计**：54 种官方媒体类型
- **图片**：43 种
- **视频**：8 种
- **文档**：1 种（manuel）
- **主题**：2 种（themehb, themehs）

### 10.3 版本信息

- **文档版本**：3.1
- **最后更新**：2026-10-27
- **适用系统**：Frontend Killer v1.0.5+
