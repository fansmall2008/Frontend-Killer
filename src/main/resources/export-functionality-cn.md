# 导出功能文档

## 1. 功能概述

导出功能允许用户将选中平台的游戏文件、媒体文件和数据文件拷贝到指定的路径下，支持多种前端规则的导出，如 Pegasus、EmulationStation DE (ES-DE) 和 RetroBat 等。

### 核心功能：
- 支持多种前端规则的导出
- 可选择导出游戏文件、媒体文件和数据文件
- 支持自定义导出路径
- 实时导出进度和日志反馈
- 灵活的规则配置系统
- 配置驱动的数据文件生成
- 支持数据文件表头和页脚配置
- 支持 XML 格式的正确处理
- 灵活的变量替换系统

## 2. 规则配置文件

### 2.1 配置文件格式

规则配置文件采用 JSON 格式，存放在 `src/main/resources/export-rules/` 目录下。

### 2.2 配置文件结构详解

配置文件包含以下主要部分：

#### 2.2.1 基本信息

```json
{
  "frontend": "pegasus",        // 前端名称
  "name": "Pegasus Frontend",  // 前端显示名称
  "version": "1.0",             // 配置版本
  "description": "Pegasus frontend export rules",  // 配置描述
  "rules": {
    // 规则配置
  }
}
```

#### 2.2.2 媒体文件配置 (`media`)

```json
"media": {
  "box2dfront": {
    "source": "box2dfront",           // 媒体文件来源字段
    "target": "{mediaPath}/{gameName}/boxFront.png",  // 目标文件路径模板
    "dataFileTag": "assets.boxFront"  // 数据文件中的标签名（可选）
  },
  "screenshot": {
    "source": "screenshot",
    "target": "{mediaPath}/{gameName}/screenshot.png"
    // 没有dataFileTag，只复制文件不添加到数据文件
  }
}
```

**字段说明：**
- `source`: 媒体文件在游戏数据中的来源字段
- `target`: 目标文件路径模板，支持变量替换
- `dataFileTag`: 可选，数据文件中用于引用媒体文件的标签名

#### 2.2.3 数据文件配置 (`dataFile`)

```json
"dataFile": {
  "filename": "metadata.pegasus.txt",  // 数据文件名
  "format": "text",                   // 数据文件格式（text或xml）
  "fieldSeparator": ";",              // 字段分隔符（text格式使用）
  "entrySeparator": "\n\n",           // 条目分隔符（text格式使用）
  "header": {
    "structure": [                     // 表头结构
      "collection: {platform}",
      "sort-by: 064",
      "launch: \"{env.appdir}\\RetroArch\\retroarch.exe\" -L \"{env.appdir}\\RetroArch\\cores\\{core}.dll\" \"{file.path}\""
    ],
    "fields": {
      "platform": "system",           // 平台变量映射
      "core": "core"
    }
  },
  "footer": [                         // 页脚结构
    "# End of collection"
  ],
  "fields": {
    "title": "name",                 // 游戏字段映射
    "description": "description",
    "rating": "rating"
  }
}
```

**字段说明：**
- `filename`: 数据文件名
- `format`: 数据文件格式，支持 `text` 和 `xml`
- `fieldSeparator`: 字段分隔符，仅 `text` 格式使用
- `entrySeparator`: 条目分隔符，仅 `text` 格式使用
- `header`: 表头配置，包含结构和字段映射
- `footer`: 页脚配置，包含固定文本行
- `fields`: 游戏字段映射，将游戏数据映射到数据文件字段

#### 2.2.4 目录配置 (`directory`)

```json
"directory": {
  "roms": "{outputPath}/{platform}/roms",  // ROM文件目录模板
  "media": "{outputPath}/{platform}/media"  // 媒体文件目录模板
}
```

**字段说明：**
- `roms`: ROM文件目录模板
- `media`: 媒体文件目录模板

### 2.2.5 完整配置示例

#### 2.2.5.1 Pegasus 配置示例

```json
{
  "frontend": "pegasus",
  "name": "Pegasus Frontend",
  "version": "1.0",
  "description": "Pegasus frontend export rules",
  "rules": {
    "media": {
      "box2dfront": {
        "source": "box2dfront",
        "target": "{mediaPath}/{gameName}/boxFront.png",
        "dataFileTag": "assets.boxFront"
      },
      "screenshot": {
        "source": "screenshot",
        "target": "{mediaPath}/{gameName}/screenshot.png",
        "dataFileTag": "assets.screenshot"
      },
      "video": {
        "source": "video",
        "target": "{mediaPath}/{gameName}/video.mp4",
        "dataFileTag": "assets.video"
      }
    },
    "dataFile": {
      "filename": "metadata.pegasus.txt",
      "format": "text",
      "fieldSeparator": "\n",
      "entrySeparator": "\n\n",
      "header": {
        "structure": [
          "collection: {platform}",
          "sort-by: 064",
          "launch: \"{env.appdir}\\RetroArch\\retroarch.exe\" -L \"{env.appdir}\\RetroArch\\cores\\{core}.dll\" \"{file.path}\""
        ],
        "fields": {
          "platform": "system",
          "core": "core"
        }
      },
      "footer": [],
      "fields": {
        "title": "name",
        "description": "description",
        "rating": "rating"
      }
    },
    "directory": {
      "roms": "{outputPath}/{platform}/roms",
      "media": "{outputPath}/{platform}/media"
    }
  }
}
```

#### 2.2.5.2 RetroBat (XML) 配置示例

```json
{
  "frontend": "retrobat",
  "name": "RetroBat Frontend",
  "version": "1.0",
  "description": "RetroBat frontend export rules",
  "rules": {
    "media": {
      "box2dfront": {
        "source": "box2dfront",
        "target": "{mediaPath}/{gameName}/boxFront.png",
        "dataFileTag": "box2dfront"
      },
      "screenshot": {
        "source": "screenshot",
        "target": "{mediaPath}/{gameName}/screenshot.png",
        "dataFileTag": "screenshot"
      }
    },
    "dataFile": {
      "filename": "gamelist.xml",
      "format": "xml",
      "header": {
        "structure": [
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
          "<gameList>",
          "  <provider>",
          "    <System>{platform.system}</System>",
          "    <software>{platform.software}</software>",
          "    <database>{platform.database}</database>",
          "    <web>{platform.web}</web>",
          "  </provider>"
        ],
        "fields": {
          "platform.system": "system",
          "platform.software": "software",
          "platform.database": "database",
          "platform.web": "web"
        }
      },
      "footer": [
        "</gameList>"
      ],
      "fields": {
        "title": "name",
        "path": "path",
        "description": "description",
        "rating": "rating",
        "releasedate": "releasedate",
        "developer": "developer",
        "publisher": "publisher",
        "genre": "genre",
        "players": "players"
      }
    },
    "directory": {
      "roms": "{outputPath}/{platform}/roms",
      "media": "{outputPath}/{platform}/media"
    }
  }
}
```

### 2.3 支持的变量

#### 2.3.1 路径变量

- `{outputPath}`: 导出路径
- `{platform}`: 平台名称
- `{gameName}`: 游戏名称
- `{mediaPath}`: 媒体文件目录
- `{romsPath}`: ROM 文件目录

#### 2.3.2 平台变量

- `{platform.system}`: 平台系统名称
- `{platform.software}`: 平台软件名称
- `{platform.database}`: 平台数据库名称
- `{platform.web}`: 平台网站地址
- `{platform.launch}`: 平台启动命令
- `{platform.name}`: 平台名称
- `{platform.sortBy}`: 平台排序字段
- `{platform.folderPath}`: 平台文件夹路径

#### 2.3.3 环境变量

- `{env.appdir}`: 应用程序目录
- `{env.home}`: 用户主目录
- `{env.user}`: 用户名

#### 2.3.4 游戏变量

- `{game.name}`: 游戏名称
- `{game.path}`: 游戏路径
- `{game.description}`: 游戏描述
- `{game.rating}`: 游戏评分
- `{game.developer}`: 游戏开发者
- `{game.publisher}`: 游戏发行商
- `{game.genre}`: 游戏类型
- `{game.players}`: 支持的玩家数量

### 2.4 支持的字段

- `name`: 游戏名称
- `description`: 游戏描述
- `rating`: 游戏评分
- `releaseYear`: 发布年份
- `developer`: 开发者
- `publisher`: 发行商
- `genre`: 游戏类型
- `players`: 支持的玩家数量
- `region`: 游戏区域
- `filename`: 从 path 中提取的文件名（去掉扩展名和前面的路径）

### 2.5 支持的格式

#### 2.5.1 text 格式

纯文本格式，支持自定义分隔符和条目分隔符：

```json
"dataFile": {
  "format": "text",
  "fieldSeparator": ";",
  "entrySeparator": "\n"
}
```

#### 2.5.2 xml 格式

XML 格式，支持正确的 XML 标签处理：

```json
"dataFile": {
  "format": "xml"
}
```

### 2.6 文件拷贝机制

#### 2.6.1 游戏文件拷贝

1. **路径解析**：系统首先解析配置文件中的 `directory.roms` 路径模板，替换其中的变量
2. **目标目录创建**：确保目标 ROM 目录存在，不存在则创建
3. **文件拷贝**：
   - 对于普通 ROM 文件：直接拷贝到目标目录
   - 对于 M3U 播放列表文件：
     - 拷贝 M3U 文件本身
     - 解析 M3U 文件内容，找到引用的所有文件
     - 拷贝所有引用的文件到相同的目标目录
4. **路径更新**：更新游戏数据中的路径为相对路径，以便前端能够正确识别

#### 2.6.2 媒体文件拷贝

1. **路径解析**：系统解析配置文件中的 `directory.media` 路径模板和各媒体文件的 `target` 路径模板
2. **目标目录创建**：确保目标媒体文件目录存在，不存在则创建
3. **文件拷贝**：
   - 对于每个配置的媒体类型，检查游戏数据中是否存在对应文件
   - 如果存在，拷贝到目标路径
   - 生成相对路径用于数据文件引用
4. **数据文件引用**：
   - 如果媒体配置中包含 `dataFileTag`，将相对路径添加到数据文件中
   - 如果没有 `dataFileTag`，只拷贝文件不添加到数据文件

#### 2.6.3 数据文件生成

1. **文件创建**：在目标目录中创建数据文件
2. **表头生成**：根据配置生成表头内容，替换变量
3. **游戏条目生成**：
   - 对每个游戏，生成对应的条目
   - 替换游戏字段和媒体文件路径
   - 处理特殊格式要求（如 XML 标签）
4. **页脚生成**：根据配置生成页脚内容
5. **文件写入**：将生成的内容写入数据文件

#### 2.6.4 变量替换流程

1. **路径变量**：首先替换与路径相关的变量，如 `{outputPath}`, `{platform}` 等
2. **平台变量**：替换与平台相关的变量，如 `{platform.system}`, `{platform.launch}` 等
3. **环境变量**：替换环境变量，如 `{env.appdir}`, `{env.home}` 等
4. **游戏变量**：替换与游戏相关的变量，如 `{game.name}`, `{game.path}` 等

### 2.7 多值匹配机制

在规则配置文件的 `fields` 部分，可以使用逗号分隔多个字段名，系统会按照顺序匹配，直到找到非空值。例如：

```json
"title": "name,filename"
```

这表示首先尝试使用 `name` 字段，如果为空则使用 `filename` 字段。

### 2.6 数据库字段说明

以下是 Game 类中可用的数据库字段，这些字段可以在规则配置文件的 `fields` 部分使用：

| 字段名 | 类型 | 描述 |
|-------|------|------|
| `id` | Long | 游戏ID |
| `gameId` | String | 游戏唯一标识 |
| `source` | String | 游戏来源 |
| `path` | String | 游戏相对路径 |
| `name` | String | 游戏名称 |
| `desc` | String | 游戏描述 |
| `translatedName` | String | 翻译后的游戏名称 |
| `translatedDesc` | String | 翻译后的游戏描述 |
| `image` | String | 游戏图片 |
| `video` | String | 游戏视频 |
| `marquee` | String | 游戏横幅 |
| `thumbnail` | String | 游戏缩略图 |
| `manual` | String | 游戏手册 |
| `boxFront` | String | 游戏盒封面 |
| `boxBack` | String | 游戏盒背面 |
| `boxSpine` | String | 游戏盒侧边 |
| `boxFull` | String | 游戏盒全景 |
| `cartridge` | String | 游戏卡带 |
| `logo` | String | 游戏标志 |
| `bezel` | String | 游戏边框 |
| `panel` | String | 游戏面板 |
| `cabinetLeft` | String | 机柜左侧 |
| `cabinetRight` | String | 机柜右侧 |
| `tile` | String | 游戏磁贴 |
| `banner` | String | 游戏横幅 |
| `steam` | String | Steam 链接 |
| `poster` | String | 游戏海报 |
| `background` | String | 游戏背景 |
| `music` | String | 游戏音乐 |
| `screenshot` | String | 游戏截图 |
| `titlescreen` | String | 游戏标题屏幕 |
| `box3d` | String | 3D 游戏盒 |
| `steamgrid` | String | Steam 网格 |
| `fanart` | String | 游戏同人艺术 |
| `boxtexture` | String | 游戏盒纹理 |
| `supporttexture` | String | 支持纹理 |
| `rating` | Double | 游戏评分 |
| `releasedate` | String | 游戏发布日期 |
| `developer` | String | 游戏开发者 |
| `publisher` | String | 游戏发行商 |
| `genre` | String | 游戏类型 |
| `players` | String | 支持的玩家数量 |
| `crc32` | String | 游戏文件 CRC32 校验和 |
| `md5` | String | 游戏文件 MD5 校验和 |
| `lang` | String | 游戏语言 |
| `genreid` | String | 游戏类型 ID |
| `hash` | String | 游戏文件哈希值 |
| `platformType` | String | 平台类型 |
| `platformId` | Long | 平台 ID |
| `sortBy` | String | 排序字段 |
| `scraped` | Boolean | 是否已抓取 |
| `edited` | Boolean | 是否已编辑 |
| `exists` | Boolean | 文件是否存在 |
| `absolutePath` | String | 游戏绝对路径 |
| `platformPath` | String | 平台路径 |

### 2.7 媒体文件来源字段

以下是媒体文件的来源字段，这些字段可以在规则配置文件的 `media` 部分的 `source` 字段中使用：

| 字段名 | 描述 |
|-------|------|
| `box2dfront` | 游戏盒封面 |
| `box2dback` | 游戏盒背面 |
| `box3d` | 3D 游戏盒 |
| `screenshot` | 游戏截图 |
| `video` | 游戏视频 |
| `wheel` | 游戏标志（使用 logo 字段） |
| `marquee` | 游戏横幅 |
| `fanart` | 游戏同人艺术 |

### 2.8 支持的前端规则

- **Pegasus**: `pegasus.json`
- **EmulationStation DE**: `esde.json`
- **RetroBat**: `retrobat.json`

### 2.9 M3U 文件处理

系统支持处理 M3U 格式的播放列表文件，当遇到 M3U 文件时，会自动复制文件中引用的所有文件。

#### 2.9.1 M3U 处理配置

在规则配置文件中，可以通过 `m3u` 部分配置 M3U 文件的处理方式：

```json
"m3u": {
  "enabled": true,
  "target": "{romsPath}"
}
```

- `enabled`: 是否启用 M3U 处理
- `target`: M3U 文件中引用的文件的目标路径模板

#### 2.9.2 支持的路径类型

- **相对路径**: 相对于 M3U 文件的路径
- **绝对路径**: 完整的文件路径

## 3. 使用指南

### 3.1 访问导出页面

1. 从首页点击 "进入平台导出" 按钮
2. 或直接访问 `/export.html`

### 3.2 导出配置

1. **选择平台**: 从下拉菜单中选择要导出的平台
2. **选择前端**: 从下拉菜单中选择目标前端规则
3. **导出范围**: 勾选要导出的内容（游戏文件、媒体文件、数据文件）
4. **导出路径**: 默认路径为 `/data/output`，可自定义

### 3.3 执行导出

点击 "导出" 按钮开始导出过程，系统会显示实时进度和详细日志。

## 4. 技术实现

### 4.1 后端实现

- **规则加载**: `ExportRuleService` 负责加载和解析规则配置文件
- **导出服务**: `ExportService` 负责执行导出操作
- **文件处理**: 支持文件复制、媒体文件处理和数据文件生成
- **API 接口**: `ExportController` 提供导出相关的 REST API

### 4.2 前端实现

- **导出配置界面**: `export.html` 提供用户友好的导出配置界面
- **进度反馈**: 实时显示导出进度和详细日志
- **响应式设计**: 适配不同屏幕尺寸

## 5. 常见问题和解决方案

### 5.1 导出失败

- **检查导出路径**: 确保导出路径存在且有写入权限
- **检查平台数据**: 确保平台有游戏数据
- **检查规则配置**: 确保规则配置文件正确

### 5.2 媒体文件不匹配

- **检查规则配置**: 确保规则配置文件中的媒体文件映射正确
- **检查源文件**: 确保源媒体文件存在

### 5.3 数据文件格式错误

- **检查规则配置**: 确保数据文件格式配置正确
- **检查游戏数据**: 确保游戏数据完整

## 6. 扩展指南

### 6.1 添加新的前端规则

1. 在 `export-rules` 目录下创建新的规则配置文件
2. 按照模板格式填写规则配置
3. 重启应用以加载新规则

### 6.2 自定义媒体文件规则

- 修改规则配置文件中的 `media` 部分
- 调整 `source`、`target` 和 `dataFileTag` 字段

### 6.3 性能优化

- 对于大量文件的导出，建议使用并行处理
- 确保磁盘空间充足
- 避免网络存储作为导出目标（可能影响性能）

## 7. 故障排除

### 7.1 日志查看

- 后端日志: 查看应用容器的日志
- 前端日志: 浏览器开发者工具的控制台

### 7.2 常见错误

- **文件不存在**: 检查源文件路径是否正确
- **权限不足**: 确保应用有足够的权限访问源文件和目标路径
- **内存不足**: 对于大量文件的导出，可能需要增加应用内存

## 8. 总结

导出功能提供了一种灵活、可扩展的方式来导出游戏平台数据，支持多种前端规则，满足不同用户的需求。通过规则配置文件，用户可以轻松自定义导出行为，适应不同的前端要求。

---

**版本**: 1.3
**最后更新**: 2026-04-22
