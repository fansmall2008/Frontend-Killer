# 导入模板文档

## 1. 功能概述

导入功能允许用户将外部前端的数据文件和媒体文件导入到系统中，支持多种前端格式的导入，如 Pegasus、EmulationStation DE (ES-DE) 等。通过导入模板，用户可以灵活配置导入规则，实现不同前端数据的快速迁移。

### 核心功能：
- 支持多种前端格式的数据文件导入
- 自动识别和处理不同格式的数据文件（XML、文本）
- 智能媒体文件匹配和关联
- 灵活的字段映射配置
- 支持表头信息提取和处理
- 多值字段处理
- 自动识别文件扩展名

## 2. 规则配置文件

### 2.1 配置文件格式

规则配置文件采用 JSON 格式，存放在 `rules/import-templates/` 目录下。

### 2.2 配置文件结构详解

配置文件包含以下主要部分：

#### 2.2.1 基本信息

```json
{
  "frontend": "esde",                // 前端标识
  "name": "ES-DE 标准模板",           // 模板名称
  "version": "1.0",                  // 模板版本
  "description": "适用于 ES-DE 前端的 XML 格式数据文件导入模板",  // 模板描述
  "type": "xml",                     // 数据文件类型（xml 或 text）
  "dataFile": "gamelist.xml",        // 数据文件名
  "delimiter": "",                   // 分隔符（text 类型使用）
  "rules": {
    // 规则配置
  }
}
```

**字段说明：**
- `frontend`: 前端标识，用于标识该模板适用于哪个前端系统
- `name`: 模板名称
- `version`: 模板版本号
- `description`: 模板描述
- `type`: 数据文件类型，支持 `xml`、`text`、`none`
- `dataFile`: 数据文件名
- `delimiter`: 分隔符，用于 text 类型的数据文件
- `rules`: 规则配置包装器

#### 2.2.2 规则配置 (`rules`)

`rules` 是规则配置的包装器，包含以下部分：

```json
"rules": {
  "header": {
    // 表头配置
  },
  "fieldMappings": {
    // 字段映射
  },
  "media": {
    // 媒体文件规则
  },
  "extensions": {
    // 扩展名配置
  },
  "gameExtensions": ["chd", "iso", ...]  // 游戏文件扩展名
}
```

#### 2.2.3 表头配置 (`rules.header`)

```json
"header": {
  "enabled": true,                 // 是否启用表头处理
  "format": "xml",                // 表头格式（xml 或 key-value）
  "startMarker": "<provider>",     // 表头开始标记
  "endMarker": "</provider>",     // 表头结束标记
  "structure": [                   // 表头结构模板
    "collection: {platform.name}",
    "sort-by: {platform.sortBy}"
  ],
  "fields": {                      // 平台信息字段映射
    "platform.name": {
      "fields": ["System", "platform"],  // 平台名称字段
      "isMultiValue": false        // 是否多值字段
    },
    "platform.software": {
      "fields": ["software"],      // 平台软件字段
      "isMultiValue": false
    }
  }
}
```

**字段说明：**
- `enabled`: 是否启用表头处理
- `format`: 表头格式，支持 `xml`、`key-value`、`none`
- `startMarker`: 表头开始标记
- `endMarker`: 表头结束标记
- `structure`: 表头结构模板列表
- `fields`: 平台信息字段映射

#### 2.2.4 字段映射 (`rules.fieldMappings`)

```json
"fieldMappings": {
  "name": {
    "fields": ["name"],            // 游戏名称字段
    "isMultiValue": false
  },
  "description": {
    "fields": ["desc"],            // 游戏描述字段
    "isMultiValue": false
  },
  "releaseDate": {
    "fields": ["releasedate"],     // 发布日期字段
    "isMultiValue": false
  },
  "files": {
    "fields": ["path"],            // 游戏文件路径字段
    "isMultiValue": false
  }
}
```

**字段说明：**
- 键名：系统内部字段名
- `fields`：外部数据文件中的字段名列表，按顺序匹配
- `isMultiValue`：是否为多值字段
- `valuePrefix`：可选，多值字段的值前缀
- `transform`：可选，字段值转换规则，支持以下选项：
  - `path`: 路径格式转换（`no`=不带`./`，`yes`=带`./`，`keep`=保持原样）
  - `trim`: 是否去除首尾空格（`true`/`false`）
  - `case`: 大小写转换（`upper`=转大写，`lower`=转小写，`none`=不转换）
  - `replace`: 字符串替换（对象包含 `from` 和 `to` 字段）

**内置路径转换规则说明**：

为方便使用，系统为所有内置导入模板的路径相关字段预设了 `transform` 配置：
- `files`、`image`、`video`、`thumbnail`、`marquee` 等路径字段默认设置为 `path: "no"`（不带 `./` 前缀）
- 这样可以保证导入数据时路径格式统一，不受前端原始格式影响

#### 2.2.5 媒体文件规则 (`rules.media`)

```json
"media": {
  "boxFront": {
    "source": "boxFront",          // 媒体来源字段
    "rules": [                     // 媒体文件路径模板
      "media/{gameName}/boxFront.{ext}",
      "media/{gameName}/box_front.{ext}",
      "media/boxFront/{gameName}.{ext}"
    ]
  },
  "screenshot": {
    "source": "screenshot",
    "rules": [
      "media/{gameName}/screenshot.{ext}",
      "media/screenshots/{gameName}.{ext}"
    ]
  }
}
```

**字段说明：**
- 键名：媒体类型
- `source`: 媒体来源字段名
- `rules`: 媒体文件路径模板列表，按顺序匹配

**支持的媒体类型：**
- `boxFront`: 游戏盒封面
- `boxBack`: 游戏盒背面
- `box3d`: 3D 游戏盒
- `boxFull`: 游戏盒全景
- `screenshot`: 游戏截图
- `video`: 游戏视频
- `wheel`: 游戏标志
- `marquee`: 游戏横幅
- `fanart`: 游戏同人艺术
- `titlescreen`: 游戏标题屏幕
- `banner`: 游戏横幅
- `thumbnail`: 游戏缩略图
- `background`: 游戏背景
- `music`: 游戏音乐

#### 2.2.6 扩展名配置 (`rules.extensions`)

```json
"extensions": {
  "image": ["png", "jpg", "jpeg", "gif", "webp"],  // 图片文件扩展名
  "video": ["mp4", "mkv", "avi", "wmv", "webm"]    // 视频文件扩展名
}
```

**字段说明：**
- `image`：支持的图片文件扩展名
- `video`：支持的视频文件扩展名

#### 2.2.7 游戏文件扩展名 (`rules.gameExtensions`)

```json
"gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]
```

**字段说明：**
- 支持的游戏文件扩展名列表

### 2.3 完整配置示例

#### 2.3.1 ES-DE (XML) 配置示例

```json
{
  "frontend": "esde",
  "name": "ES-DE 标准模板",
  "version": "1.0",
  "description": "适用于 EmulationStation DE 前端的 XML 格式数据文件导入模板",
  "type": "xml",
  "dataFile": "gamelist.xml",
  "delimiter": "",
  "rules": {
    "header": {
      "enabled": true,
      "format": "xml",
      "startMarker": "<provider>",
      "endMarker": "</provider>",
      "structure": [
        "collection: {platform.name}",
        "sort-by: {platform.sortBy}"
      ],
      "fields": {
        "platform.name": {
          "fields": ["System", "platform"],
          "isMultiValue": false
        },
        "platform.software": {
          "fields": ["software"],
          "isMultiValue": false
        },
        "platform.database": {
          "fields": ["database"],
          "isMultiValue": false
        },
        "platform.web": {
          "fields": ["web"],
          "isMultiValue": false
        }
      }
    },
    "fieldMappings": {
      "name": {
        "fields": ["name"],
        "isMultiValue": false
      },
      "description": {
        "fields": ["desc", "description"],
        "isMultiValue": false
      },
      "releaseDate": {
        "fields": ["releasedate", "release"],
        "isMultiValue": false
      },
      "developer": {
        "fields": ["developer", "dev"],
        "isMultiValue": false
      },
      "publisher": {
        "fields": ["publisher", "pub"],
        "isMultiValue": false
      },
      "genre": {
        "fields": ["genre", "category"],
        "isMultiValue": false
      },
      "players": {
        "fields": ["players", "player"],
        "isMultiValue": false
      },
      "files": {
        "fields": ["path", "file"],
        "isMultiValue": false
      }
    },
    "media": {
      "boxFront": {
        "source": "boxFront",
        "rules": [
          "media/{gameName}/boxFront.{ext}",
          "media/{gameName}/box_front.{ext}",
          "media/boxFront/{gameName}.{ext}",
          "media/images/{gameName}.{ext}"
        ]
      },
      "boxBack": {
        "source": "boxBack",
        "rules": [
          "media/{gameName}/boxBack.{ext}",
          "media/{gameName}/box_back.{ext}",
          "media/boxBack/{gameName}.{ext}"
        ]
      },
      "box3d": {
        "source": "box3d",
        "rules": [
          "media/{gameName}/box3d.{ext}",
          "media/box3d/{gameName}.{ext}"
        ]
      },
      "screenshot": {
        "source": "screenshot",
        "rules": [
          "media/{gameName}/screenshot.{ext}",
          "media/{gameName}/screen.{ext}",
          "media/screenshots/{gameName}.{ext}",
          "media/screens/{gameName}.{ext}"
        ]
      },
      "video": {
        "source": "video",
        "rules": [
          "media/{gameName}/video.{ext}",
          "media/{gameName}/trailer.{ext}",
          "media/videos/{gameName}.{ext}",
          "media/{gameName}.{ext}"
        ]
      },
      "wheel": {
        "source": "wheel",
        "rules": [
          "media/{gameName}/wheel.{ext}",
          "media/wheel/{gameName}.{ext}"
        ]
      },
      "marquee": {
        "source": "marquee",
        "rules": [
          "media/{gameName}/marquee.{ext}",
          "media/{gameName}/logo.{ext}",
          "media/logos/{gameName}.{ext}",
          "media/marquees/{gameName}.{ext}"
        ]
      },
      "fanart": {
        "source": "fanart",
        "rules": [
          "media/{gameName}/fanart.{ext}",
          "media/fanart/{gameName}.{ext}"
        ]
      }
    },
    "extensions": {
      "image": ["png", "jpg", "jpeg", "gif", "webp"],
      "video": ["mp4", "mkv", "avi", "wmv", "webm"]
    },
    "gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]
  }
}
```

#### 2.3.2 Pegasus (文本) 配置示例

```json
{
  "frontend": "pegasus",
  "name": "Pegasus 标准模板",
  "version": "1.0",
  "description": "适用于 Pegasus 前端的数据文件和媒体文件导入模板",
  "type": "text",
  "dataFile": "metadata.pegasus.txt",
  "delimiter": ":",
  "rules": {
    "header": {
      "enabled": true,
      "format": "key-value",
      "startMarker": "collection:",
      "endMarker": "",
      "structure": [
        "collection: {platform.name}",
        "sort-by: {platform.sortBy}",
        "launch: {platform.launch}"
      ],
      "fields": {
        "platform.name": {
          "fields": ["name", "title", "platform", "collection"],
          "isMultiValue": false
        },
        "platform.description": {
          "fields": ["description", "desc"],
          "isMultiValue": false
        },
        "platform.launch": {
          "fields": ["launch", "command"],
          "isMultiValue": false
        },
        "platform.sortBy": {
          "fields": ["sort-by", "sort"],
          "isMultiValue": false
        }
      }
    },
    "fieldMappings": {
      "name": {
        "fields": ["title", "name", "game"],
        "isMultiValue": false
      },
      "description": {
        "fields": ["description", "desc"],
        "isMultiValue": false
      },
      "releaseDate": {
        "fields": ["release", "releasedate", "date"],
        "isMultiValue": false
      },
      "developer": {
        "fields": ["developer", "dev"],
        "isMultiValue": false
      },
      "publisher": {
        "fields": ["publisher", "pub"],
        "isMultiValue": false
      },
      "genre": {
        "fields": ["genre", "category"],
        "isMultiValue": false
      },
      "players": {
        "fields": ["players", "player"],
        "isMultiValue": false
      },
      "files": {
        "fields": ["files", "file"],
        "isMultiValue": true,
        "valuePrefix": "  "
      }
    },
    "media": {
      "boxFront": {
        "source": "boxFront",
        "rules": [
          "media/{gameName}/boxFront.{ext}",
          "media/{gameName}/box_front.{ext}",
          "media/boxFront/{gameName}.{ext}",
          "media/images/{gameName}.{ext}"
        ]
      },
      "boxBack": {
        "source": "boxBack",
        "rules": [
          "media/{gameName}/boxBack.{ext}",
          "media/{gameName}/box_back.{ext}",
          "media/boxBack/{gameName}.{ext}"
        ]
      },
      "box3d": {
        "source": "box3d",
        "rules": [
          "media/{gameName}/box3d.{ext}",
          "media/box3d/{gameName}.{ext}"
        ]
      },
      "screenshot": {
        "source": "screenshot",
        "rules": [
          "media/{gameName}/screenshot.{ext}",
          "media/{gameName}/screen.{ext}",
          "media/screenshots/{gameName}.{ext}",
          "media/screens/{gameName}.{ext}"
        ]
      },
      "video": {
        "source": "video",
        "rules": [
          "media/{gameName}/video.{ext}",
          "media/{gameName}/trailer.{ext}",
          "media/videos/{gameName}.{ext}",
          "media/{gameName}.{ext}"
        ]
      },
      "wheel": {
        "source": "wheel",
        "rules": [
          "media/{gameName}/wheel.{ext}",
          "media/wheel/{gameName}.{ext}"
        ]
      },
      "marquee": {
        "source": "marquee",
        "rules": [
          "media/{gameName}/marquee.{ext}",
          "media/{gameName}/logo.{ext}",
          "media/logos/{gameName}.{ext}",
          "media/marquees/{gameName}.{ext}"
        ]
      },
      "fanart": {
        "source": "fanart",
        "rules": [
          "media/{gameName}/fanart.{ext}",
          "media/fanart/{gameName}.{ext}"
        ]
      }
    },
    "extensions": {
      "image": ["png", "jpg", "jpeg", "gif", "webp"],
      "video": ["mp4", "mkv", "avi", "wmv", "webm"]
    },
    "gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]
  }
}
```

## 3. 模板变量

导入模板支持以下变量：

| 变量名 | 说明 |
|-------|------|
| `{gameName}` | 游戏名称（不含扩展名） |
| `{platform}` | 平台名称 |
| `{ext}` | 文件扩展名 |
| `{outputPath}` | 输出路径 |
| `{mediaPath}` | 媒体文件路径 |
| `{romsPath}` | 游戏文件路径 |

**使用示例：**

```json
"rules": {
  "media": {
    "boxFront": {
      "source": "boxFront",
      "rules": [
        "media/{gameName}/boxFront.{ext}",
        "media/{platform}/boxFront/{gameName}.{ext}"
      ]
    }
  }
}
```

## 4. 媒体文件匹配机制

### 4.1 匹配流程

1. **模板加载**：系统加载用户选择的导入模板
2. **数据文件解析**：根据模板配置解析数据文件，提取游戏信息
3. **媒体文件扫描**：根据模板中的 `media` 配置，扫描匹配的媒体文件
4. **扩展名匹配**：根据 `extensions` 配置尝试不同的文件扩展名
5. **顺序匹配**：按照配置的顺序尝试不同的路径模板，直到找到匹配的文件
6. **相对路径处理**：支持相对于数据文件的路径
7. **绝对路径支持**：也支持绝对路径

### 4.2 多值匹配机制

在 `fieldMappings` 部分，可以为每个字段指定多个外部字段名，系统会按照顺序匹配，直到找到非空值。例如：

```json
"name": {
  "fields": ["title", "name", "game"],
  "isMultiValue": false
}
```

这表示首先尝试使用 `title` 字段，如果为空则使用 `name` 字段，再为空则使用 `game` 字段。

## 5. 使用指南

### 5.1 访问导入页面

1. 从首页点击 "导入游戏数据" 按钮
2. 或直接访问 `/import.html`

### 5.2 导入配置

1. **选择模板**：从下拉菜单中选择适合的导入模板
2. **选择数据文件**：上传或指定数据文件路径
3. **选择媒体文件目录**：指定媒体文件所在目录
4. **选择目标平台**：选择要导入到的目标平台

### 5.3 执行导入

点击 "导入" 按钮开始导入过程，系统会显示实时进度和详细日志。

## 6. 技术实现

### 6.1 后端实现

- **模板加载**：`ImportTemplateService` 负责加载和解析导入模板
- **导入服务**：`ImportService` 负责执行导入操作
- **文件处理**：支持数据文件解析、媒体文件匹配和游戏数据导入
- **API 接口**：`ImportController` 提供导入相关的 REST API

### 6.2 前端实现

- **导入配置界面**：`import.html` 提供用户友好的导入配置界面
- **进度反馈**：实时显示导入进度和详细日志
- **响应式设计**：适应不同屏幕尺寸

## 7. 常见问题及解决方案

### 7.1 导入失败

- **检查数据文件**：确保数据文件格式正确
- **检查模板配置**：确保选择了正确的导入模板
- **检查文件权限**：确保应用有足够的权限访问数据文件和媒体文件

### 7.2 媒体文件不匹配

- **检查媒体规则**：确保模板中的媒体规则与实际文件结构匹配
- **检查文件路径**：确保媒体文件路径正确
- **检查文件扩展名**：确保媒体文件扩展名在配置范围内

### 7.3 字段映射错误

- **检查字段映射**：确保模板中的字段映射与数据文件中的字段名匹配
- **检查数据文件格式**：确保数据文件格式符合模板要求

## 8. 扩展指南

### 8.1 添加新的导入模板

1. 在 `rules/import-templates/` 目录下创建新的 JSON 文件
2. 按照本文档的结构编写模板配置
3. 在前端模板选择下拉框中选择新模板

### 8.2 自定义媒体规则

根据实际文件结构，在 `media` 部分添加或修改媒体类型和路径模板。

### 8.3 支持新的前端格式

1. 分析新前端的数据文件格式
2. 创建相应的导入模板
3. 配置字段映射和媒体规则

## 9. 数据库字段对应

### 9.1 游戏表 (`Game`) 字段

| 字段名 | 说明 | 导入支持 | 导出支持 |
|-------|------|---------|---------|
| `gameId` | 游戏唯一标识 | ✅ | ✅ |
| `source` | 游戏来源 | ✅ | ✅ |
| `path` | 游戏相对路径 | ✅ | ✅ |
| `name` | 游戏名称 | ✅ | ✅ |
| `description` | 游戏描述 | ✅ | ✅ |
| `translatedName` | 翻译后的游戏名称 | ✅ | ✅ |
| `translatedDesc` | 翻译后的游戏描述 | ✅ | ✅ |
| `image` | 游戏图片 | ✅ | ✅ |
| `video` | 游戏视频 | ✅ | ✅ |
| `marquee` | 游戏横幅 | ✅ | ✅ |
| `thumbnail` | 游戏缩略图 | ✅ | ✅ |
| `manual` | 游戏手册 | ✅ | ✅ |
| `boxFront` | 游戏盒封面 | ✅ | ✅ |
| `boxBack` | 游戏盒背面 | ✅ | ✅ |
| `boxSpine` | 游戏盒侧边 | ✅ | ✅ |
| `boxFull` | 游戏盒全景 | ✅ | ✅ |
| `cartridge` | 游戏卡带 | ✅ | ✅ |
| `logo` | 游戏标志 | ✅ | ✅ |
| `rating` | 游戏评分 | ✅ | ✅ |
| `releasedate` | 游戏发布日期 | ✅ | ✅ |
| `developer` | 游戏开发者 | ✅ | ✅ |
| `publisher` | 游戏发行商 | ✅ | ✅ |
| `genre` | 游戏类型 | ✅ | ✅ |
| `players` | 支持的玩家数量 | ✅ | ✅ |
| `platformType` | 平台类型 | ✅ | ✅ |
| `platformId` | 平台 ID | ✅ | ✅ |
| `scraped` | 是否已抓取 | ✅ | ✅ |
| `edited` | 是否已编辑 | ✅ | ✅ |
| `absolutePath` | 游戏绝对路径 | ✅ | ✅ |
| `platformPath` | 平台路径 | ✅ | ✅ |

### 9.2 平台表 (`Platform`) 字段

| 字段名 | 说明 | 导入支持 | 导出支持 |
|-------|------|---------|---------|
| `name` | 平台名称 | ✅ | ✅ |
| `description` | 平台描述 | ✅ | ✅ |
| `system` | 平台系统名称 | ✅ | ✅ |
| `software` | 平台软件 | ✅ | ✅ |
| `database` | 平台数据库 | ✅ | ✅ |
| `web` | 平台网站 | ✅ | ✅ |
| `launch` | 平台启动命令 | ✅ | ✅ |
| `sortBy` | 排序字段 | ✅ | ✅ |
| `folderPath` | 平台文件夹路径 | ✅ | ✅ |
| `icon` | 平台图标 | ✅ | ✅ |
| `image` | 平台图片 | ✅ | ✅ |

## 10. 总结

导入功能提供了一种灵活、可扩展的方式来导入外部前端的游戏数据，支持多种前端格式，满足不同用户的需求。通过导入模板，用户可以轻松配置导入规则，实现不同前端数据的快速迁移。

---

**版本**: 1.1
**最后更新**: 2026-04-26