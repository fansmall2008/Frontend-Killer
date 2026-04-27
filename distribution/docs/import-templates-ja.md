# インポートテンプレートドキュメント

## 1. 機能概要

インポート機能を使用すると、ユーザーは外部フロントエンドのデータファイルとメディアファイルをシステムにインポートできます。Pegasus、EmulationStation DE (ES-DE) など、複数のフロントエンド形式に対応しています。インポートテンプレートを通じて、ユーザーはインポートルールを柔軟に設定し、異なるフロントエンドのデータを迅速に移行することができます。

### コア機能：
- 複数のフロントエンド形式のデータファイルのインポートをサポート
- 異なる形式のデータファイル（XML、テキスト）の自動認識と処理
- インテリジェントなメディアファイルのマッチングと関連付け
- 柔軟なフィールドマッピング設定
- ヘッダー情報の抽出と処理のサポート
- 複数値フィールドの処理
- ファイル拡張子の自動認識

## 2. テンプレート設定ファイル

### 2.1 設定ファイル形式

インポートテンプレート設定ファイルは JSON 形式を使用し、`rules/import-templates/` ディレクトリに格納されます。

### 2.2 設定ファイル構造の詳細

設定ファイルには以下の主要部分が含まれます：

#### 2.2.1 基本情報

```json
{
  "frontend": "esde",                // フロントエンド識別子
  "name": "ES-DE 標準テンプレート",           // テンプレート名
  "version": "1.0",                  // テンプレートバージョン
  "description": "ES-DE フロントエンド用の XML 形式データファイルインポートテンプレート",  // テンプレートの説明
  "type": "xml",                     // データファイルの種類（xml、text、none）
  "dataFile": "gamelist.xml",        // データファイル名
  "delimiter": "",                   // 区切り文字（text タイプで使用）
  "rules": {
    // ルール設定
  }
}
```

**フィールドの説明：**
- `frontend`: フロントエンド識別子、どのフロントエンドシステム用かを指定
- `name`: テンプレート名
- `version`: テンプレートバージョン番号
- `description`: テンプレートの説明
- `type`: データファイルの種類、`xml`、`text`、`none` をサポート
- `dataFile`: データファイル名
- `delimiter`: 区切り文字、text タイプ用のデータファイル
- `rules`: ルール設定ラッパー

#### 2.2.2 ルール設定 (`rules`)

`rules` はルール設定のラッパーで、以下の部分を含みます：

```json
"rules": {
  "header": {
    // ヘッダー設定
  },
  "fieldMappings": {
    // フィールドマッピング
  },
  "media": {
    // メディアファイルルール
  },
  "extensions": {
    // 拡張子設定
  },
  "gameExtensions": ["chd", "iso", ...]  // ゲームファイルの拡張子
}
```

#### 2.2.3 ヘッダー設定 (`rules.header`)

```json
"header": {
  "enabled": true,                 // ヘッダー処理を有効にするか
  "format": "xml",                // ヘッダー形式（xml または key-value）
  "startMarker": "<provider>",     // ヘッダー開始マーカー
  "endMarker": "</provider>",     // ヘッダー終了マーカー
  "structure": [                   // ヘッダー構造テンプレート
    "collection: {platform.name}",
    "sort-by: {platform.sortBy}"
  ],
  "fields": {                      // プラットフォーム情報フィールドマッピング
    "platform.name": {
      "fields": ["System", "platform"],  // プラットフォーム名フィールド
      "isMultiValue": false        // 複数値フィールドかどうか
    },
    "platform.software": {
      "fields": ["software"],      // プラットフォームソフトウェアフィールド
      "isMultiValue": false
    }
  }
}
```

**フィールドの説明：**
- `enabled`: ヘッダー処理を有効にするか
- `format`: ヘッダー形式、`xml`、`key-value`、`none` をサポート
- `startMarker`: ヘッダー開始マーカー
- `endMarker`: ヘッダー終了マーカー
- `structure`: ヘッダー構造テンプレートリスト
- `fields`: プラットフォーム情報フィールドマッピング

#### 2.2.4 フィールドマッピング (`rules.fieldMappings`)

```json
"fieldMappings": {
  "name": {
    "fields": ["name"],            // ゲーム名フィールド
    "isMultiValue": false
  },
  "description": {
    "fields": ["desc"],            // ゲーム説明フィールド
    "isMultiValue": false
  },
  "releaseDate": {
    "fields": ["releasedate"],     // リリース日フィールド
    "isMultiValue": false
  },
  "files": {
    "fields": ["path"],            // ゲームファイルパスフィールド
    "isMultiValue": false
  }
}
```

**フィールドの説明：**
- キー名: システム内部フィールド名
- `fields`：外部データファイル内のフィールド名リスト、順序通りにマッチング
- `isMultiValue`：複数値フィールドかどうか
- `valuePrefix`：オプション、複数値フィールドの値プレフィックス
- `transform`：オプション、フィールド値変換規則、以下のオプションをサポート：
  - `path`: パス形式変換（`no`=`./` なし、`yes`=`./` あり、`keep`=元のまま）
  - `trim`: 先頭と末尾のスペースをトリムするかどうか（`true`/`false`）
  - `case`: 大文字小文字変換（`upper`=大文字、`lower`=小文字、`none`=変換なし）
  - `replace`: 文字列置換（`from` と `to` フィールドを持つオブジェクト）

**組み込みパス変換規則について**：

便宜上、システムではすべての組み込みインポートテンプレートのパス関連フィールドに事前設定された `transform` を提供します：
- `files`、`image`、`video`、`thumbnail`、`marquee` などのパスフィールドはデフォルトで `path: "no"`（`./` プレフィックスなし）に設定されています
- これにより、元のフロントエンド形式に関係なく、インポート時のパス形式が統一されます

#### 2.2.5 メディアファイルルール (`rules.media`)

```json
"media": {
  "boxFront": {
    "source": "boxFront",          // メディアソースフィールド
    "rules": [                     // メディアファイルパステンプレート
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

**フィールドの説明：**
- キー名: メディアタイプ
- `source`: メディアソースフィールド名
- `rules`: メディアファイルパステンプレートリスト、順序通りにマッチング

**サポートされているメディアタイプ：**
- `boxFront`: ゲームボックスフロントカバー
- `boxBack`: ゲームボックスバックカバー
- `box3d`: 3D ゲームボックス
- `boxFull`: ゲームボックス全景
- `screenshot`: ゲームスクリーンショット
- `video`: ゲーム動画
- `wheel`: ゲームロゴ/ホイール
- `marquee`: ゲームマーキー
- `fanart`: ゲームファンアート
- `titlescreen`: ゲームタイトルスクリーン
- `banner`: ゲームバナー
- `thumbnail`: ゲームサムネイル
- `background`: ゲーム背景
- `music`: ゲーム音楽

#### 2.2.6 拡張子設定 (`rules.extensions`)

```json
"extensions": {
  "image": ["png", "jpg", "jpeg", "gif", "webp"],  // 画像ファイル拡張子
  "video": ["mp4", "mkv", "avi", "wmv", "webm"]    // 動画ファイル拡張子
}
```

**フィールドの説明：**
- `image`：サポートされている画像ファイル拡張子
- `video`：サポートされている動画ファイル拡張子

#### 2.2.7 ゲームファイル拡張子 (`rules.gameExtensions`)

```json
"gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]
```

**フィールドの説明：**
- サポートされているゲームファイル拡張子のリスト

### 2.3 完全な設定例

#### 2.3.1 ES-DE (XML) 設定例

```json
{
  "frontend": "esde",
  "name": "ES-DE 標準テンプレート",
  "version": "1.0",
  "description": "EmulationStation DE フロントエンド用の XML 形式データファイルインポートテンプレート",
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

#### 2.3.2 Pegasus (テキスト) 設定例

```json
{
  "frontend": "pegasus",
  "name": "Pegasus 標準テンプレート",
  "version": "1.0",
  "description": "Pegasus フロントエンド用のデータファイルとメディアファイルインポートテンプレート",
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

## 3. テンプレート変数

インポートテンプレートは以下の変数をサポートしています：

| 変数名 | 説明 |
|--------|------|
| `{gameName}` | ゲーム名（拡張子なし） |
| `{platform}` | プラットフォーム名 |
| `{ext}` | ファイル拡張子 |
| `{outputPath}` | 出力パス |
| `{mediaPath}` | メディアファイルパス |
| `{romsPath}` | ゲームファイルパス |

**使用例：**

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

## 4. メディアファイルマッチングメカニズム

### 4.1 マッチングプロセス

1. **テンプレート読み込み**: システムはユーザー選択されたインポートテンプレートを読み込みます
2. **データファイル解析**: テンプレート設定に従ってデータファイルを解析し、ゲーム情報を抽出します
3. **メディアファイルスキャン**: テンプレートの `media` 設定に従って、一致するメディアファイルをスキャンします
4. **拡張子マッチング**: `extensions` 設定に従って、異なるファイル拡張子を試行します
5. **順序マッチング**: 設定された順序で異なるパステンプレートを試行し、一致するファイルが見つかるまで続けます
6. **相対パス処理**: データファイルからの相対パスをサポート
7. **絶対パスサポート**: 絶対パスもサポート

### 4.2 複数値マッチングメカニズム

`fieldMappings` 部分では、各フィールドに複数の外部フィールド名を指定でき、システムは順序通りにマッチングし、空でない値が見つかるまで続けます。例：

```json
"name": {
  "fields": ["title", "name", "game"],
  "isMultiValue": false
}
```

これは最初に `title` フィールドを使用しようとし、空の場合は `name` フィールドを、さらに空の場合は `game` フィールドを使用します。

## 5. 使用ガイド

### 5.1 インポートページへのアクセス

1. ホームページから「ゲームデータインポート」ボタンをクリック
2. または `/import.html` に直接アクセス

### 5.2 インポート設定

1. **テンプレートを選択**: ドロップダウンメニューから適切なインポートテンプレートを選択
2. **データファイルを選択**: データファイルパスをアップロードまたは指定
3. **メディアファイルディレクトリを選択**: メディアファイルがあるディレクトリを指定
4. **ターゲットプラットフォームを選択**: インポート先のターゲットプラットフォームを選択

### 5.3 インポート実行

「インポート」ボタンをクリックしてインポートプロセスを開始すると、システムはリアルタイムの進行状況と詳細なログを表示します。

## 6. 技術実装

### 6.1 バックエンド実装

- **テンプレート読み込み**: `ImportTemplateService` はインポートテンプレートの読み込みと解析を担当
- **インポートサービス**: `ImportService` はインポート操作の実行を担当
- **ファイル処理**: データファイル解析、メディアファイルマッチング、ゲームデータインポートをサポート
- **API インターフェース**: `ImportController` はインポート関連の REST API を提供

### 6.2 フロントエンド実装

- **インポート設定インターフェース**: `import.html` はユーザーフレンドリーなインポート設定インターフェースを提供
- **進捗フィードバック**: インポート進行状況と詳細ログのリアルタイム表示
- **レスポンシブデザイン**: 異なる画面サイズに対応

## 7. 一般的な問題と解決策

### 7.1 インポート失敗

- **データファイルを確認**: データファイル形式が正しいことを確認
- **テンプレート設定を確認**: 正しいインポートテンプレートが選択されていることを確認
- **ファイル権限を確認**: アプリケーションがデータファイルとメディアファイルにアクセスするための十分な権限を持っていることを確認

### 7.2 メディアファイル不一致

- **メディアルールを確認**: テンプレートのメディアルールが実際のファイル構造と一致していることを確認
- **ファイルパス坂認**: メディアファイルパスが正しいことを確認
- **ファイル拡張子を確認**: メディアファイル拡張子が設定範囲内であることを確認

### 7.3 フィールドマッピングエラー

- **フィールドマッピングを確認**: テンプレートのフィールドマッピングがデータファイルのフィールド名と一致していることを確認
- **データファイル形式を確認**: データファイル形式がテンプレートの要件を満たしていることを確認

## 8. 拡張ガイド

### 8.1 新しいインポートテンプレートの追加

1. `rules/import-templates/` ディレクトリに新しい JSON ファイルを作成
2. このドキュメントの構造に従ってテンプレート設定を作成
3. フロントエンドのテンプレート選択ドロップダウンで新しいテンプレートを選択

### 8.2 メディアルールのカスタマイズ

実際のファイル構造に従って、`media` 部分でメディアタイプとパステンプレートを追加または変更します。

### 8.3 新しいフロントエンド形式のサポート

1. 新しいフロントエンドのデータファイル形式を分析
2. 対応するインポートテンプレートを作成
3. フィールドマッピングとメディアルールを設定

## 9. データベースフィールド対応

### 9.1 ゲームテーブル (`Game`) フィールド

| フィールド名 | 説明 | インポートサポート | エクスポートサポート |
|--------------|------|-------------------|---------------------|
| `gameId` | ゲーム一意識別子 | ✅ | ✅ |
| `source` | ゲームソース | ✅ | ✅ |
| `path` | ゲーム相対パス | ✅ | ✅ |
| `name` | ゲーム名 | ✅ | ✅ |
| `description` | ゲーム説明 | ✅ | ✅ |
| `translatedName` | 翻訳されたゲーム名 | ✅ | ✅ |
| `translatedDesc` | 翻訳されたゲーム説明 | ✅ | ✅ |
| `image` | ゲーム画像 | ✅ | ✅ |
| `video` | ゲーム動画 | ✅ | ✅ |
| `marquee` | ゲームマーキー | ✅ | ✅ |
| `thumbnail` | ゲームサムネイル | ✅ | ✅ |
| `manual` | ゲームマニュアル | ✅ | ✅ |
| `boxFront` | ゲームボックスフロント | ✅ | ✅ |
| `boxBack` | ゲームボックスバック | ✅ | ✅ |
| `boxSpine` | ゲームボックススピーン | ✅ | ✅ |
| `boxFull` | ゲームボックス全景 | ✅ | ✅ |
| `cartridge` | ゲームカセット | ✅ | ✅ |
| `logo` | ゲームロゴ | ✅ | ✅ |
| `rating` | ゲーム評価 | ✅ | ✅ |
| `releasedate` | ゲームリリース日 | ✅ | ✅ |
| `developer` | ゲーム開発者 | ✅ | ✅ |
| `publisher` | ゲーム発売元 | ✅ | ✅ |
| `genre` | ゲームジャンル | ✅ | ✅ |
| `players` | プレイヤー数 | ✅ | ✅ |
| `platformType` | プラットフォームタイプ | ✅ | ✅ |
| `platformId` | プラットフォーム ID | ✅ | ✅ |
| `scraped` | スクレイピング済み | ✅ | ✅ |
| `edited` | 編集済み | ✅ | ✅ |
| `absolutePath` | ゲーム絶対パス | ✅ | ✅ |
| `platformPath` |  플랫폼パス | ✅ | ✅ |

### 9.2 プラットフォームテーブル (`Platform`) フィールド

| フィールド名 | 説明 | インポートサポート | エクスポートサポート |
|--------------|------|-------------------|---------------------|
| `name` | プラットフォーム名 | ✅ | ✅ |
| `description` | プラットフォーム説明 | ✅ | ✅ |
| `system` | プラットフォームシステム名 | ✅ | ✅ |
| `software` | プラットフォームソフトウェア | ✅ | ✅ |
| `database` | プラットフォームデータベース | ✅ | ✅ |
| `web` | プラットフォームウェブサイト | ✅ | ✅ |
| `launch` | プラットフォーム起動コマンド | ✅ | ✅ |
| `sortBy` | ソートフィールド | ✅ | ✅ |
| `folderPath` | プラットフォームフォルダパス | ✅ | ✅ |
| `icon` | プラットフォームアイコン | ✅ | ✅ |
| `image` | プラットフォーム画像 | ✅ | ✅ |

## 10. まとめ

インポート機能は、外部フロントエンドからゲームデータをインポートするための柔軟で拡張可能な方法を提供し、複数のフロントエンド形式をサポートして різних ユーザーのニーズに応えます。インポートテンプレートを通じて、ユーザーは異なるフロントエンドのデータを迅速に移行するためのインポートルールを 쉽게 구성할 수 있습니다.

---

**バージョン**: 1.1
**最終更新**: 2026-04-26