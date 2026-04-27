# Translation Configuration

Web GameList Oper supports multi-language translation for game data using a JSON-based translation configuration.

## File Location

Place the translation configuration file at:
```
data/rules/translation-config.json
```

## File Structure

```json
{
  "defaultLanguage": "en",
  "supportedLanguages": ["en", "zh", "ja"],
  "translations": {
    "en": { ... },
    "zh": { ... },
    "ja": { ... }
  },
  "fieldConfig": { ... }
}
```

## Complete Example

```json
{
  "defaultLanguage": "en",
  "supportedLanguages": ["en", "zh", "ja"],
  "translations": {
    "en": {
      "game-name": "Game Name",
      "game-path": "Game Path",
      "game-description": "Description",
      "developer": "Developer",
      "publisher": "Publisher",
      "genre": "Genre",
      "release-date": "Release Date",
      "players": "Players",
      "rating": "Rating",
      "platform": "Platform",
      "import": "Import",
      "export": "Export",
      "translate": "Translate",
      "search": "Search",
      "filter": "Filter",
      "edit": "Edit",
      "delete": "Delete",
      "save": "Save",
      "cancel": "Cancel",
      "confirm": "Confirm",
      "success": "Success",
      "error": "Error",
      "loading": "Loading...",
      "no-data": "No data available",
      "select-language": "Select Language",
      "chinese": "中文",
      "english": "English",
      "japanese": "日本語"
    },
    "zh": {
      "game-name": "游戏名称",
      "game-path": "游戏路径",
      "game-description": "游戏描述",
      "developer": "开发商",
      "publisher": "发行商",
      "genre": "游戏类型",
      "release-date": "发行日期",
      "players": "玩家数量",
      "rating": "评分",
      "platform": "平台",
      "import": "导入",
      "export": "导出",
      "translate": "翻译",
      "search": "搜索",
      "filter": "筛选",
      "edit": "编辑",
      "delete": "删除",
      "save": "保存",
      "cancel": "取消",
      "confirm": "确认",
      "success": "成功",
      "error": "错误",
      "loading": "加载中...",
      "no-data": "暂无数据",
      "select-language": "选择语言",
      "chinese": "中文",
      "english": "English",
      "japanese": "日本語"
    },
    "ja": {
      "game-name": "ゲーム名",
      "game-path": "ゲームパス",
      "game-description": "説明",
      "developer": "開発者",
      "publisher": "発売元",
      "genre": "ジャンル",
      "release-date": "発売日",
      "players": "プレイヤー数",
      "rating": "評価",
      "platform": "プラットフォーム",
      "import": "インポート",
      "export": "エクスポート",
      "translate": "翻訳",
      "search": "検索",
      "filter": "フィルター",
      "edit": "編集",
      "delete": "削除",
      "save": "保存",
      "cancel": "キャンセル",
      "confirm": "確認",
      "success": "成功",
      "error": "エラー",
      "loading": "読み込み中...",
      "no-data": "データがありません",
      "select-language": "言語を選択",
      "chinese": "中文",
      "english": "English",
      "japanese": "日本語"
    }
  },
  "fieldConfig": {
    "game-name": {
      "maxLength": 255,
      "required": true
    },
    "game-path": {
      "maxLength": 500,
      "required": true
    },
    "game-description": {
      "maxLength": 2000
    }
  }
}
```

## Configuration Sections

### defaultLanguage

Specifies the default language when no translation is available:

```json
"defaultLanguage": "en"
```

### supportedLanguages

Lists all supported language codes:

```json
"supportedLanguages": ["en", "zh", "ja"]
```

### translations

Contains all translation key-value pairs for each language:

```json
"translations": {
  "en": {
    "game-name": "Game Name",
    ...
  },
  "zh": {
    "game-name": "游戏名称",
    ...
  },
  "ja": {
    "game-name": "ゲーム名",
    ...
  }
}
```

### fieldConfig

Optional configuration for validation:

```json
"fieldConfig": {
  "game-name": {
    "maxLength": 255,
    "required": true
  }
}
```

## UI Text Translations

### Navigation
```json
{
  "game-list": "Game List",
  "data-import": "Data Import",
  "export": "Export",
  "platform-management": "Platform Management",
  "system-settings": "System Settings"
}
```

### Buttons
```json
{
  "new-game": "New Game",
  "edit-game": "Edit Game",
  "delete-game": "Delete Game",
  "batch-edit": "Batch Edit",
  "batch-translate": "Batch Translate",
  "import": "Import",
  "export": "Export",
  "start-import": "Start Import",
  "start-export": "Start Export",
  "save": "Save",
  "cancel": "Cancel",
  "close": "Close",
  "confirm": "Confirm",
  "reset": "Reset"
}
```

### Labels
```json
{
  "game-name": "Game Name",
  "game-path": "Game Path",
  "platform": "Platform",
  "developer": "Developer",
  "publisher": "Publisher",
  "genre": "Genre",
  "release-date": "Release Date",
  "players": "Players",
  "rating": "Rating",
  "description": "Description",
  "image": "Image",
  "video": "Video",
  "thumbnail": "Thumbnail"
}
```

### Messages
```json
{
  "success": "Success",
  "error": "Error",
  "warning": "Warning",
  "info": "Information",
  "loading": "Loading...",
  "saving": "Saving...",
  "deleting": "Deleting...",
  "importing": "Importing...",
  "exporting": "Exporting...",
  "translating": "Translating...",
  "no-data": "No data available",
  "confirm-delete": "Are you sure you want to delete?",
  "operation-success": "Operation completed successfully",
  "operation-failed": "Operation failed"
}
```

## Media File Translations

```json
{
  "media-files": "Media Files",
  "box-front": "Box Front",
  "box-back": "Box Back",
  "screenshot": "Screenshot",
  "banner": "Banner",
  "wheel": "Wheel",
  "video": "Video",
  "thumbnail": "Thumbnail",
  "manual": "Manual",
  "no-media-files": "No media files found",
  "view-media": "View Media Files",
  "download-media": "Download Media"
}
```

## Language Codes

| Code | Language | 中文 |
|------|----------|------|
| `en` | English | 英语 |
| `zh` | Chinese (Simplified) | 中文 |
| `ja` | Japanese | 日语 |
| `ko` | Korean | 韩语 |
| `fr` | French | 法语 |
| `de` | German | 德语 |
| `es` | Spanish | 西班牙语 |
| `pt` | Portuguese | 葡萄牙语 |
| `it` | Italian | 意大利语 |
| `ru` | Russian | 俄语 |

## Adding New Languages

### Step 1: Add Language Code

Add the new language code to `supportedLanguages`:

```json
"supportedLanguages": ["en", "zh", "ja", "ko"]
```

### Step 2: Add Translations

Add a new section in `translations`:

```json
"translations": {
  "ko": {
    "game-name": "게임 이름",
    "game-path": "게임 경로",
    ...
  }
}
```

## Dynamic Translations

### Game Names Translation

For game-specific translations, use the `gameTranslations` section:

```json
{
  "gameTranslations": {
    "Super Mario Bros.": {
      "zh": "超级马里奥兄弟",
      "ja": "スーパーマリオブラザーズ"
    },
    "The Legend of Zelda": {
      "zh": "塞尔达传说",
      "ja": "ゼルダの伝説"
    }
  }
}
```

### Genre Translation

```json
{
  "genreTranslations": {
    "Action": { "zh": "动作", "ja": "アクション" },
    "Adventure": { "zh": "冒险", "ja": "アドベンチャー" },
    "RPG": { "zh": "角色扮演", "ja": "RPG" },
    "Sports": { "zh": "体育", "ja": "スポーツ" },
    "Puzzle": { "zh": "益智", "ja": "パズル" }
  }
}
```

## Using Translations in Code

### JavaScript

```javascript
// Get translated text
const text = translations[currentLanguage][key];

// Example
const gameNameLabel = translations['zh']['game-name'];
// Returns: "游戏名称"
```

### HTML

```html
<!-- Using data-i18n attribute -->
<label data-i18n="game-name">Game Name</label>

<!-- JavaScript will replace with translated text -->
```

### Java

```java
// Get translation
String text = translationService.get(key, language);

// Example
String gameName = translationService.get("game-name", "zh");
// Returns: "游戏名称"
```

## Best Practices

### Key Naming
- Use lowercase with hyphens: `game-name`
- Be descriptive: `game-description` not `desc`
- Be consistent across all languages

### Translation Quality
- Keep translations accurate
- Maintain consistent tone
- Consider cultural context

### Maintenance
- Update all languages when adding keys
- Remove unused keys periodically
- Keep the file organized

## Troubleshooting

### Translation Not Loading
- Ensure `translation-config.json` is valid JSON
- Check file is in correct directory
- Verify `translations` object is properly structured

### Missing Translations
- Check all required keys exist in each language
- Use `defaultLanguage` fallback
- Add missing translations

### Language Switch Not Working
- Verify `supportedLanguages` includes the language
- Check browser console for errors
- Ensure translations object is properly loaded