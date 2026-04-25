# 导入模板说明

## 概述

导入模板是一种配置文件，用于定义如何从不同格式的元数据文件和媒体文件中导入游戏数据。通过使用导入模板，您可以自定义导入规则，以适应不同前端和文件结构的需求。

## 模板结构

导入模板是一个JSON格式的文件，包含以下主要字段：

```json
{
  "name": "模板名称",
  "description": "模板描述",
  "type": "数据文件类型",
  "dataFile": "数据文件名称",
  "delimiter": "分隔符",
  "header": {
    "enabled": true/false,
    "format": "header格式",
    "startMarker": "开始标记",
    "endMarker": "结束标记",
    "fieldMappings": {
      "平台字段": {
        "fields": ["数据文件字段1", "数据文件字段2"],
        "isMultiValue": true/false,
        "valuePrefix": "值前缀"
      }
    }
  },
  "fieldMappings": {
    "游戏字段": {
      "fields": ["数据文件字段1", "数据文件字段2"],
      "isMultiValue": true/false,
      "valuePrefix": "值前缀"
    }
  },
  "mediaRules": {
    "媒体类型": [
      "路径规则1",
      "路径规则2"
    ]
  },
  "extensions": {
    "image": ["图片扩展名"],
    "video": ["视频扩展名"]
  },
  "gameExtensions": ["游戏文件扩展名"]
}
```

## 字段说明

### 基本信息

- **name**: 模板名称，用于在导入界面中显示
- **description**: 模板描述，说明模板的用途和适用场景
- **type**: 数据文件类型，支持 "xml" 和 "text" 两种类型
- **dataFile**: 数据文件名称，如 "gamelist.xml" 或 "metadata.pegasus.txt"
- **delimiter**: 分隔符，仅用于 text 类型的数据文件，如 ":"

### 表头配置

- **header.enabled**: 是否启用表头解析
- **header.format**: 表头格式，支持 "key-value" 和 "xml" 两种格式
- **header.startMarker**: 表头开始标记，如 "collection:"
- **header.endMarker**: 表头结束标记，留空表示到文件末尾
- **header.fieldMappings**: 表头字段映射，定义如何从表头中提取平台信息

### 字段映射

- **fieldMappings**: 游戏字段映射，定义如何从数据文件中提取游戏信息
- **fields**: 数据文件中可能的字段名称列表，按优先级排序
- **isMultiValue**: 是否为多值字段
- **valuePrefix**: 多值字段的值前缀，用于识别多个值

### 媒体规则

- **mediaRules**: 媒体文件路径规则，定义如何查找媒体文件
- **媒体类型**: 支持的媒体类型包括：boxFront、video、logo、screenshot、boxBack、box3d、wheel、marquee、fanart
- **路径规则**: 媒体文件的路径模式，支持变量替换：
  - {game}: 游戏名称（不含扩展名）
  - {ext}: 媒体文件扩展名

### 扩展名配置

- **extensions.image**: 支持的图片扩展名列表
- **extensions.video**: 支持的视频扩展名列表
- **gameExtensions**: 支持的游戏文件扩展名列表

## 变量替换

在媒体规则的路径模式中，支持以下变量：

- **{game}**: 替换为游戏名称（不含扩展名）
- **{ext}**: 替换为媒体文件扩展名

## 示例模板

### Pegasus 标准模板

```json
{
  "name": "Pegasus 标准模板",
  "description": "适用于 Pegasus 前端的数据文件和媒体文件导入模板",
  "type": "text",
  "dataFile": "metadata.pegasus.txt",
  "delimiter": ":",
  "header": {
    "enabled": true,
    "format": "key-value",
    "startMarker": "collection:",
    "endMarker": "",
    "fieldMappings": {
      "platformName": {
        "fields": ["name", "title", "platform", "collection"],
        "isMultiValue": false
      },
      "platformLaunchCommand": {
        "fields": ["launch", "command"],
        "isMultiValue": false
      }
    }
  },
  "fieldMappings": {
    "name": {
      "fields": ["title", "name", "game"],
      "isMultiValue": false
    },
    "files": {
      "fields": ["files", "file"],
      "isMultiValue": true,
      "valuePrefix": "  "
    }
  },
  "mediaRules": {
    "boxFront": [
      "media/{game}/boxFront.{ext}",
      "media/{game}/box_front.{ext}"
    ],
    "video": [
      "media/{game}/video.{ext}",
      "media/videos/{game}.{ext}"
    ]
  },
  "extensions": {
    "image": ["png", "jpg", "jpeg"],
    "video": ["mp4", "mkv"]
  },
  "gameExtensions": ["chd", "iso", "bin"]
}
```

### ES-DE 标准模板

```json
{
  "name": "ES-DE 标准模板",
  "description": "适用于 ES-DE 前端的 XML 格式数据文件导入模板",
  "type": "xml",
  "dataFile": "gamelist.xml",
  "delimiter": "",
  "header": {
    "enabled": true,
    "format": "xml",
    "startMarker": "<provider>",
    "endMarker": "</provider>",
    "fieldMappings": {
      "platformName": {
        "fields": ["System"],
        "isMultiValue": false
      },
      "platformSoftware": {
        "fields": ["software"],
        "isMultiValue": false
      }
    }
  },
  "fieldMappings": {
    "name": {
      "fields": ["name"],
      "isMultiValue": false
    },
    "path": {
      "fields": ["path"],
      "isMultiValue": false
    }
  },
  "mediaRules": {
    "boxFront": [
      "images/{game}-boxfront.{ext}",
      "media/{game}/boxFront.{ext}"
    ],
    "video": [
      "videos/{game}.{ext}",
      "media/{game}/video.{ext}"
    ]
  },
  "extensions": {
    "image": ["png", "jpg", "jpeg"],
    "video": ["mp4", "mkv"]
  },
  "gameExtensions": ["chd", "iso", "bin"]
}
```

### 通用模板

```json
{
  "name": "通用模板",
  "description": "适用于无数据文件导入场景的通用模板",
  "type": "text",
  "dataFile": "",
  "delimiter": "",
  "header": {
    "enabled": false,
    "format": "key-value",
    "startMarker": "",
    "endMarker": "",
    "fieldMappings": {}
  },
  "fieldMappings": {},
  "mediaRules": {
    "boxFront": [
      "media/{game}/boxFront.{ext}",
      "media/{game}/cover.{ext}",
      "media/covers/{game}.{ext}"
    ],
    "video": [
      "media/{game}/video.{ext}",
      "media/videos/{game}.{ext}"
    ]
  },
  "extensions": {
    "image": ["png", "jpg", "jpeg"],
    "video": ["mp4", "mkv"]
  },
  "gameExtensions": ["chd", "iso", "bin", "cue", "zip", "7z"]
}
```

## 使用方法

1. 在导入界面中选择 "模板方式" 作为导入方式
2. 从下拉菜单中选择适合您的导入模板
3. 点击 "开始扫描并导入" 按钮开始导入过程

## 自定义模板

您可以根据自己的需求创建自定义导入模板：

1. 复制现有的模板文件
2. 修改模板名称、描述和配置
3. 将修改后的模板文件保存到 `import-templates` 目录
4. 重启应用程序，新模板将出现在导入界面的下拉菜单中

## 注意事项

- 模板文件必须是有效的 JSON 格式
- 媒体规则的路径模式按顺序尝试，找到第一个匹配的文件后停止
- 变量替换仅在媒体规则的路径模式中生效
- 多值字段需要设置 `isMultiValue` 为 true，并指定 `valuePrefix`
- 对于 XML 格式的数据文件，字段名称应与 XML 标签名一致
