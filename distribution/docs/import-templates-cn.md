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

## 2. 模板配置文件

### 2.1 配置文件格式

导入模板配置文件采用 JSON 格式，存放在 `rules/import-templates/` 目录下。

### 2.2 配置文件结构详解

配置文件包含以下主要部分：

#### 2.2.1 基本信息

```json
{
  "name": "ES-DE 标准模板",        // 模板名称
  "description": "适用于 ES-DE 前端的 XML 格式数据文件导入模板",  // 模板描述
  "type": "xml",                   // 数据文件类型（xml 或 text）
  "dataFile": "gamelist.xml",      // 数据文件名
  "delimiter": "",                 // 分隔符（text 类型使用）
  "header": {
    // 表头配置
  },
  "fieldMappings": {
    // 字段映射
  },
  "mediaRules": {
    // 媒体文件规则
  },
  "extensions": {
    // 扩展名配置
  },
  "gameExtensions": ["chd", "iso", ...]  // 游戏文件扩展名
}
```

#### 2.2.2 表头配置 (`header`)

```json
"header": {
  "enabled": true,                 // 是否启用表头处理
  "format": "xml",                // 表头格式（xml 或 key-value）
  "startMarker": "<provider>",     // 表头开始标记
  "endMarker": "</provider>",     // 表头结束标记
  "fieldMappings": {
    "platformName": {
      "fields": ["System", "platform"],  // 平台名称字段
      "isMultiValue": false        // 是否多值字段
    },
    "platformSoftware": {
      "fields": ["software"],      // 平台软件字段
      "isMultiValue": false
    }
  }
}
```

**字段说明：**
- `enabled`: 是否启用表头处理
- `format`: 表头格式，支持 `xml` 和 `key-value`
- `startMarker`: 表头开始标记
- `endMarker`: 表头结束标记
- `fieldMappings`: 平台信息字段映射

#### 2.2.3 字段映射 (`fieldMappings`)

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

#### 2.2.4 媒体规则 (`mediaRules`)

```json
"mediaRules": {
  "box2dfront": [                  // 游戏盒封面
    "media/{gameName}/boxFront.{ext}",
    "media/{gameName}/box_front.{ext}",
    "media/boxFront/{gameName}.{ext}"
  ],
  "screenshot": [                  // 游戏截图
    "media/{gameName}/screenshot.{ext}",
    "media/screenshots/{gameName}.{ext}"
  ]
}
```

**字段说明：**
- 键名：媒体类型
- 值：媒体文件路径模板列表，按顺序匹配

#### 2.2.5 扩展名配置 (`extensions`)

```json
"extensions": {
  "image": ["png", "jpg", "jpeg", "gif", "webp"],  // 图片文件扩展名
  "video": ["mp4", "mkv", "avi", "wmv", "webm"]    // 视频文件扩展名
}
```

**字段说明：**
- `image`：支持的图片文件扩展名
- `video`：支持的视频文件扩展名

#### 2.2.6 游戏文件扩展名 (`gameExtensions`)

```json
"gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]
```

**字段说明：**
- 支持的游戏文件扩展名列表

### 2.3 完整配置示例

#### 2.3.1 ES-DE (XML) 配置示例

```json
{
  "name": "ES-DE 标准模板",                     // 模板名称
  "description": "适用于 ES-DE 前端的 XML 格式数据文件导入模板",  // 模板描述
  "type": "xml",                                // 数据文件类型（xml）
  "dataFile": "gamelist.xml",                   // 数据文件名
  "delimiter": "",                              // XML 格式不需要分隔符
  "header": {
    "enabled": true,                              // 启用表头处理
    "format": "xml",                             // 表头格式为 XML
    "startMarker": "<provider>",                 // 表头开始标记
    "endMarker": "</provider>",                 // 表头结束标记
    "fieldMappings": {                           // 平台信息字段映射
      "platformName": {                          // 平台名称
        "fields": ["System", "platform"],       // 尝试匹配的字段名
        "isMultiValue": false                    // 不是多值字段
      },
      "platformSoftware": {                      // 平台软件
        "fields": ["software"],                  // 尝试匹配的字段名
        "isMultiValue": false
      },
      "platformDatabase": {                      // 平台数据库
        "fields": ["database"],                  // 尝试匹配的字段名
        "isMultiValue": false
      },
      "platformWeb": {                           // 平台网站
        "fields": ["web"],                       // 尝试匹配的字段名
        "isMultiValue": false
      }
    }
  },
  "fieldMappings": {                             // 游戏字段映射
    "name": {                                    // 游戏名称
      "fields": ["name"],                        // 尝试匹配的字段名
      "isMultiValue": false
    },
    "description": {                              // 游戏描述
      "fields": ["desc"],                        // 尝试匹配的字段名
      "isMultiValue": false
    },
    "releaseDate": {                              // 发布日期
      "fields": ["releasedate"],                 // 尝试匹配的字段名
      "isMultiValue": false
    },
    "developer": {                                // 开发者
      "fields": ["developer"],                   // 尝试匹配的字段名
      "isMultiValue": false
    },
    "publisher": {                                // 发行商
      "fields": ["publisher"],                   // 尝试匹配的字段名
      "isMultiValue": false
    },
    "genre": {                                    // 游戏类型
      "fields": ["genre"],                       // 尝试匹配的字段名
      "isMultiValue": false
    },
    "players": {                                  // 支持的玩家数量
      "fields": ["players"],                     // 尝试匹配的字段名
      "isMultiValue": false
    },
    "files": {                                    // 游戏文件路径
      "fields": ["path"],                        // 尝试匹配的字段名
      "isMultiValue": false
    }
  },
  "mediaRules": {                                // 媒体文件匹配规则
    "box2dfront": [                              // 游戏盒封面
      "media/{gameName}/boxFront.{ext}",         // 路径模板1
      "media/{gameName}/box_front.{ext}",        // 路径模板2
      "media/boxFront/{gameName}.{ext}",         // 路径模板3
      "media/images/{gameName}.{ext}"            // 路径模板4
    ],
    "box2dback": [                               // 游戏盒背面
      "media/{gameName}/boxBack.{ext}",
      "media/{gameName}/box_back.{ext}",
      "media/boxBack/{gameName}.{ext}"
    ],
    "box3d": [                                   // 3D 游戏盒
      "media/{gameName}/box3d.{ext}",
      "media/box3d/{gameName}.{ext}"
    ],
    "screenshot": [                              // 游戏截图
      "media/{gameName}/screenshot.{ext}",
      "media/{gameName}/screen.{ext}",
      "media/screenshots/{gameName}.{ext}",
      "media/screens/{gameName}.{ext}"
    ],
    "video": [                                   // 游戏视频
      "media/{gameName}/video.{ext}",
      "media/{gameName}/trailer.{ext}",
      "media/videos/{gameName}.{ext}",
      "media/{gameName}.{ext}"
    ],
    "wheel": [                                   // 游戏标志
      "media/{gameName}/wheel.{ext}",
      "media/wheel/{gameName}.{ext}"
    ],
    "marquee": [                                 // 游戏横幅
      "media/{gameName}/marquee.{ext}",
      "media/{gameName}/logo.{ext}",
      "media/logos/{gameName}.{ext}",
      "media/marquees/{gameName}.{ext}"
    ],
    "fanart": [                                  // 游戏同人艺术
      "media/{gameName}/fanart.{ext}",
      "media/fanart/{gameName}.{ext}"
    ]
  },
  "extensions": {                                // 文件扩展名配置
    "image": ["png", "jpg", "jpeg", "gif", "webp"],  // 支持的图片扩展名
    "video": ["mp4", "mkv", "avi", "wmv", "webm"]    // 支持的视频扩展名
  },
  "gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]  // 支持的游戏文件扩展名
}
```

#### 2.3.2 Pegasus (文本) 配置示例

```json
{
  "name": "Pegasus 标准模板",                    // 模板名称
  "description": "适用于 Pegasus 前端的数据文件和媒体文件导入模板",  // 模板描述
  "type": "text",                               // 数据文件类型（text）
  "dataFile": "metadata.pegasus.txt",           // 数据文件名
  "delimiter": ":",                             // 字段分隔符
  "header": {
    "enabled": true,                              // 启用表头处理
    "format": "key-value",                       // 表头格式为键值对
    "startMarker": "collection:",                // 表头开始标记
    "endMarker": "",                             // 表头结束标记（空表示到第一个游戏条目）
    "fieldMappings": {                           // 平台信息字段映射
      "platformName": {                          // 平台名称
        "fields": ["name", "title", "platform", "collection"],  // 尝试匹配的字段名
        "isMultiValue": false                    // 不是多值字段
      },
      "platformDescription": {                   // 平台描述
        "fields": ["description", "desc"],      // 尝试匹配的字段名
        "isMultiValue": false
      },
      "platformLaunchCommand": {                 // 平台启动命令
        "fields": ["launch", "command"],        // 尝试匹配的字段名
        "isMultiValue": false
      },
      "platformSortBy": {                        // 平台排序字段
        "fields": ["sort-by", "sort"],          // 尝试匹配的字段名
        "isMultiValue": false
      }
    }
  },
  "fieldMappings": {                             // 游戏字段映射
    "name": {                                    // 游戏名称
      "fields": ["title", "name", "game"],     // 尝试匹配的字段名（按顺序）
      "isMultiValue": false
    },
    "description": {                              // 游戏描述
      "fields": ["description", "desc"],        // 尝试匹配的字段名
      "isMultiValue": false
    },
    "releaseDate": {                              // 发布日期
      "fields": ["release", "releasedate", "date"],  // 尝试匹配的字段名
      "isMultiValue": false
    },
    "developer": {                                // 开发者
      "fields": ["developer", "dev"],           // 尝试匹配的字段名
      "isMultiValue": false
    },
    "publisher": {                                // 发行商
      "fields": ["publisher", "pub"],           // 尝试匹配的字段名
      "isMultiValue": false
    },
    "genre": {                                    // 游戏类型
      "fields": ["genre", "category"],          // 尝试匹配的字段名
      "isMultiValue": false
    },
    "players": {                                  // 支持的玩家数量
      "fields": ["players", "player"],          // 尝试匹配的字段名
      "isMultiValue": false
    },
    "files": {                                    // 游戏文件路径
      "fields": ["files", "file"],              // 尝试匹配的字段名
      "isMultiValue": true,                      // 是多值字段
      "valuePrefix": "  "                        // 多值字段的值前缀
    }
  },
  "mediaRules": {                                // 媒体文件匹配规则
    "box2dfront": [                              // 游戏盒封面
      "media/{gameName}/boxFront.{ext}",         // 路径模板1
      "media/{gameName}/box_front.{ext}",        // 路径模板2
      "media/boxFront/{gameName}.{ext}",         // 路径模板3
      "media/images/{gameName}.{ext}"            // 路径模板4
    ],
    "box2dback": [                               // 游戏盒背面
      "media/{gameName}/boxBack.{ext}",
      "media/{gameName}/box_back.{ext}",
      "media/boxBack/{gameName}.{ext}"
    ],
    "box3d": [                                   // 3D 游戏盒
      "media/{gameName}/box3d.{ext}",
      "media/box3d/{gameName}.{ext}"
    ],
    "screenshot": [                              // 游戏截图
      "media/{gameName}/screenshot.{ext}",
      "media/{gameName}/screen.{ext}",
      "media/screenshots/{gameName}.{ext}",
      "media/screens/{gameName}.{ext}"
    ],
    "video": [                                   // 游戏视频
      "media/{gameName}/video.{ext}",
      "media/{gameName}/trailer.{ext}",
      "media/videos/{gameName}.{ext}",
      "media/{gameName}.{ext}"
    ],
    "wheel": [                                   // 游戏标志
      "media/{gameName}/wheel.{ext}",
      "media/wheel/{gameName}.{ext}"
    ],
    "marquee": [                                 // 游戏横幅
      "media/{gameName}/marquee.{ext}",
      "media/{gameName}/logo.{ext}",
      "media/logos/{gameName}.{ext}",
      "media/marquees/{gameName}.{ext}"
    ],
    "fanart": [                                  // 游戏同人艺术
      "media/{gameName}/fanart.{ext}",
      "media/fanart/{gameName}.{ext}"
    ]
  },
  "extensions": {                                // 文件扩展名配置
    "image": ["png", "jpg", "jpeg", "gif", "webp"],  // 支持的图片扩展名
    "video": ["mp4", "mkv", "avi", "wmv", "webm"]    // 支持的视频扩展名
  },
  "gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]  // 支持的游戏文件扩展名
}
```

### 2.4 支持的变量

#### 2.4.1 路径变量

- `{gameName}`: 游戏名称
- `{ext}`: 文件扩展名

### 2.5 支持的字段

#### 2.5.1 平台字段

- `platformName`: 平台名称
- `platformSoftware`: 平台软件
- `platformDatabase`: 平台数据库
- `platformWeb`: 平台网站
- `platformLaunchCommand`: 平台启动命令
- `platformSortBy`: 平台排序字段
- `platformDescription`: 平台描述

#### 2.5.2 游戏字段

- `name`: 游戏名称
- `description`: 游戏描述
- `releaseDate`: 发布日期
- `developer`: 开发者
- `publisher`: 发行商
- `genre`: 游戏类型
- `players`: 支持的玩家数量
- `files`: 游戏文件路径

#### 2.5.3 媒体字段

- `box2dfront`: 游戏盒封面
- `box2dback`: 游戏盒背面
- `box3d`: 3D 游戏盒
- `screenshot`: 游戏截图
- `video`: 游戏视频
- `wheel`: 游戏标志
- `marquee`: 游戏横幅
- `fanart`: 游戏同人艺术

### 2.6 支持的格式

#### 2.6.1 xml 格式

XML 格式，适用于 ES-DE 等前端：

```json
{
  "type": "xml",
  "dataFile": "gamelist.xml",
  "delimiter": ""
}
```

#### 2.6.2 text 格式

文本格式，适用于 Pegasus 等前端：

```json
{
  "type": "text",
  "dataFile": "metadata.pegasus.txt",
  "delimiter": ":"
}
```

### 2.7 媒体文件匹配机制

1. **路径模板解析**：系统解析配置文件中的媒体文件路径模板，替换其中的变量
2. **扩展名匹配**：根据 `extensions` 配置尝试不同的文件扩展名
3. **顺序匹配**：按照配置的顺序尝试不同的路径模板，直到找到匹配的文件
4. **相对路径处理**：支持相对于数据文件的路径
5. **绝对路径支持**：也支持绝对路径

### 2.8 多值匹配机制

在模板配置文件的 `fieldMappings` 部分，可以为每个字段指定多个外部字段名，系统会按照顺序匹配，直到找到非空值。例如：

```json
"name": {
  "fields": ["title", "name", "game"],
  "isMultiValue": false
}
```

这表示首先尝试使用 `title` 字段，如果为空则使用 `name` 字段，再为空则使用 `game` 字段。

## 3. 使用指南

### 3.1 访问导入页面

1. 从首页点击 "导入游戏数据" 按钮
2. 或直接访问 `/import.html`

### 3.2 导入配置

1. **选择模板**：从下拉菜单中选择适合的导入模板
2. **选择数据文件**：上传或指定数据文件路径
3. **选择媒体文件目录**：指定媒体文件所在目录
4. **选择目标平台**：选择要导入到的目标平台

### 3.3 执行导入

点击 "导入" 按钮开始导入过程，系统会显示实时进度和详细日志。

## 4. 技术实现

### 4.1 后端实现

- **模板加载**：`ImportTemplateService` 负责加载和解析导入模板
- **导入服务**：`ImportService` 负责执行导入操作
- **文件处理**：支持数据文件解析、媒体文件匹配和游戏数据导入
- **API 接口**：`ImportController` 提供导入相关的 REST API

### 4.2 前端实现

- **导入配置界面**：`import.html` 提供用户友好的导入配置界面
- **进度反馈**：实时显示导入进度和详细日志
- **响应式设计**：适配不同屏幕尺寸

## 5. 常见问题和解决方案

### 5.1 导入失败

- **检查数据文件**：确保数据文件格式正确
- **检查模板配置**：确保选择了正确的导入模板
- **检查文件权限**：确保应用有足够的权限访问数据文件和媒体文件

### 5.2 媒体文件不匹配

- **检查媒体规则**：确保模板中的媒体规则与实际文件结构匹配
- **检查文件路径**：确保媒体文件路径正确
- **检查文件扩展名**：确保媒体文件扩展名在配置中

### 5.3 字段映射错误

- **检查字段映射**：确保模板中的字段映射与数据文件中的字段名匹配
- **检查数据文件格式**：确保数据文件格式符合模板要求

## 6. 扩展指南

### 6.1 添加新的导入模板

1. 在 `rules/import-templates/` 目录下创建新的模板配置文件
2. 按照模板格式填写配置
3. 重启应用以加载新模板

### 6.2 自定义媒体规则

- 修改模板配置文件中的 `mediaRules` 部分
- 调整媒体文件路径模板以匹配实际文件结构

### 6.3 性能优化

- 对于大量文件的导入，建议使用并行处理
- 确保磁盘空间充足
- 避免网络存储作为导入源（可能影响性能）

## 7. 故障排除

### 7.1 日志查看

- 后端日志: 查看应用容器的日志
- 前端日志: 浏览器开发者工具的控制台

### 7.2 常见错误

- **文件不存在**: 检查数据文件和媒体文件路径是否正确
- **权限不足**: 确保应用有足够的权限访问文件
- **内存不足**: 对于大量文件的导入，可能需要增加应用内存

## 8. 总结

导入功能提供了一种灵活、可扩展的方式来导入外部前端的游戏数据，支持多种前端格式，满足不同用户的需求。通过导入模板，用户可以轻松配置导入规则，实现不同前端数据的快速迁移。

---

**版本**: 1.0
**最后更新**: 2026-04-24