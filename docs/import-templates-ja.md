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
  "name": "ES-DE 標準テンプレート",        // テンプレート名
  "description": "ES-DE フロントエンド用の XML 形式データファイルインポートテンプレート",  // テンプレートの説明
  "type": "xml",                   // データファイルの種類（xml または text）
  "dataFile": "gamelist.xml",      // データファイル名
  "delimiter": "",                 // 区切り文字（text タイプで使用）
  "header": {
    // ヘッダー設定
  },
  "fieldMappings": {
    // フィールドマッピング
  },
  "mediaRules": {
    // メディアファイルルール
  },
  "extensions": {
    // 拡張子設定
  },
  "gameExtensions": ["chd", "iso", ...]  // ゲームファイルの拡張子
}
```

#### 2.2.2 ヘッダー設定 (`header`)

```json
"header": {
  "enabled": true,                 // ヘッダー処理を有効にするかどうか
  "format": "xml",                // ヘッダー形式（xml または key-value）
  "startMarker": "<provider>",     // ヘッダーの開始マーカー
  "endMarker": "</provider>",     // ヘッダーの終了マーカー
  "fieldMappings": {
    "platformName": {
      "fields": ["System", "platform"],  // プラットフォーム名フィールド
      "isMultiValue": false        // 複数値フィールドかどうか
    },
    "platformSoftware": {
      "fields": ["software"],      // プラットフォームソフトウェアフィールド
      "isMultiValue": false
    }
  }
}
```

**フィールドの説明：**
- `enabled`: ヘッダー処理を有効にするかどうか
- `format`: ヘッダー形式、`xml` と `key-value` をサポート
- `startMarker`: ヘッダーの開始マーカー
- `endMarker`: ヘッダーの終了マーカー
- `fieldMappings`: プラットフォーム情報のフィールドマッピング

#### 2.2.3 フィールドマッピング (`fieldMappings`)

```json
"fieldMappings": {
  "name": {
    "fields": ["name"],            // ゲーム名フィールド
    "isMultiValue": false
  },
  "description": {
    "fields": ["desc"],            // ゲームの説明フィールド
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
- キー名: システム内部のフィールド名
- `fields`: 外部データファイルのフィールド名のリスト、順番に一致
- `isMultiValue`: 複数値フィールドかどうか
- `valuePrefix`: オプション、複数値フィールドの値のプレフィックス

#### 2.2.4 メディアルール (`mediaRules`)

```json
"mediaRules": {
  "box2dfront": [                  // ゲームボックスの表紙
    "media/{gameName}/boxFront.{ext}",
    "media/{gameName}/box_front.{ext}",
    "media/boxFront/{gameName}.{ext}"
  ],
  "screenshot": [                  // ゲームのスクリーンショット
    "media/{gameName}/screenshot.{ext}",
    "media/screenshots/{gameName}.{ext}"
  ]
}
```

**フィールドの説明：**
- キー名: メディアの種類
- 値: メディアファイルのパステンプレートのリスト、順番に一致

#### 2.2.5 拡張子設定 (`extensions`)

```json
"extensions": {
  "image": ["png", "jpg", "jpeg", "gif", "webp"],  // 画像ファイルの拡張子
  "video": ["mp4", "mkv", "avi", "wmv", "webm"]    // 動画ファイルの拡張子
}
```

**フィールドの説明：**
- `image`: サポートされている画像ファイルの拡張子
- `video`: サポートされている動画ファイルの拡張子

#### 2.2.6 ゲームファイルの拡張子 (`gameExtensions`)

```json
"gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]
```

**フィールドの説明：**
- サポートされているゲームファイルの拡張子のリスト

### 2.3 完全な設定例

#### 2.3.1 ES-DE (XML) 設定例

```json
{
  "name": "ES-DE 標準テンプレート",                     // テンプレート名
  "description": "ES-DE フロントエンド用の XML 形式データファイルインポートテンプレート",  // テンプレートの説明
  "type": "xml",                                // データファイルの種類（xml）
  "dataFile": "gamelist.xml",                   // データファイル名
  "delimiter": "",                              // XML 形式には区切り文字は不要
  "header": {
    "enabled": true,                              // ヘッダー処理を有効にする
    "format": "xml",                             // ヘッダー形式は XML
    "startMarker": "<provider>",                 // ヘッダーの開始マーカー
    "endMarker": "</provider>",                 // ヘッダーの終了マーカー
    "fieldMappings": {                           // プラットフォーム情報のフィールドマッピング
      "platformName": {                          // プラットフォーム名
        "fields": ["System", "platform"],       // 一致させるフィールド名
        "isMultiValue": false                    // 複数値フィールドではない
      },
      "platformSoftware": {                      // プラットフォームソフトウェア
        "fields": ["software"],                  // 一致させるフィールド名
        "isMultiValue": false
      },
      "platformDatabase": {                      // プラットフォームデータベース
        "fields": ["database"],                  // 一致させるフィールド名
        "isMultiValue": false
      },
      "platformWeb": {                           // プラットフォームのウェブサイト
        "fields": ["web"],                       // 一致させるフィールド名
        "isMultiValue": false
      }
    }
  },
  "fieldMappings": {                             // ゲームフィールドのマッピング
    "name": {                                    // ゲーム名
      "fields": ["name"],                        // 一致させるフィールド名
      "isMultiValue": false
    },
    "description": {                              // ゲームの説明
      "fields": ["desc"],                        // 一致させるフィールド名
      "isMultiValue": false
    },
    "releaseDate": {                              // リリース日
      "fields": ["releasedate"],                 // 一致させるフィールド名
      "isMultiValue": false
    },
    "developer": {                                // 開発者
      "fields": ["developer"],                   // 一致させるフィールド名
      "isMultiValue": false
    },
    "publisher": {                                // 発行者
      "fields": ["publisher"],                   // 一致させるフィールド名
      "isMultiValue": false
    },
    "genre": {                                    // ゲームのジャンル
      "fields": ["genre"],                       // 一致させるフィールド名
      "isMultiValue": false
    },
    "players": {                                  // サポートされるプレイヤー数
      "fields": ["players"],                     // 一致させるフィールド名
      "isMultiValue": false
    },
    "files": {                                    // ゲームファイルパス
      "fields": ["path"],                        // 一致させるフィールド名
      "isMultiValue": false
    }
  },
  "mediaRules": {                                // メディアファイルのマッチングルール
    "box2dfront": [                              // ゲームボックスの表紙
      "media/{gameName}/boxFront.{ext}",         // パステンプレート 1
      "media/{gameName}/box_front.{ext}",        // パステンプレート 2
      "media/boxFront/{gameName}.{ext}",         // パステンプレート 3
      "media/images/{gameName}.{ext}"            // パステンプレート 4
    ],
    "box2dback": [                               // ゲームボックスの裏面
      "media/{gameName}/boxBack.{ext}",
      "media/{gameName}/box_back.{ext}",
      "media/boxBack/{gameName}.{ext}"
    ],
    "box3d": [                                   // 3D ゲームボックス
      "media/{gameName}/box3d.{ext}",
      "media/box3d/{gameName}.{ext}"
    ],
    "screenshot": [                              // ゲームのスクリーンショット
      "media/{gameName}/screenshot.{ext}",
      "media/{gameName}/screen.{ext}",
      "media/screenshots/{gameName}.{ext}",
      "media/screens/{gameName}.{ext}"
    ],
    "video": [                                   // ゲームの動画
      "media/{gameName}/video.{ext}",
      "media/{gameName}/trailer.{ext}",
      "media/videos/{gameName}.{ext}",
      "media/{gameName}.{ext}"
    ],
    "wheel": [                                   // ゲームのホイール
      "media/{gameName}/wheel.{ext}",
      "media/wheel/{gameName}.{ext}"
    ],
    "marquee": [                                 // ゲームのマーキー
      "media/{gameName}/marquee.{ext}",
      "media/{gameName}/logo.{ext}",
      "media/logos/{gameName}.{ext}",
      "media/marquees/{gameName}.{ext}"
    ],
    "fanart": [                                  // ゲームのファンアート
      "media/{gameName}/fanart.{ext}",
      "media/fanart/{gameName}.{ext}"
    ]
  },
  "extensions": {                                // ファイル拡張子の設定
    "image": ["png", "jpg", "jpeg", "gif", "webp"],  // サポートされる画像の拡張子
    "video": ["mp4", "mkv", "avi", "wmv", "webm"]    // サポートされる動画の拡張子
  },
  "gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]  // サポートされるゲームファイルの拡張子
}
```

#### 2.3.2 Pegasus (テキスト) 設定例

```json
{
  "name": "Pegasus 標準テンプレート",                    // テンプレート名
  "description": "Pegasus フロントエンド用のデータファイルとメディアファイルのインポートテンプレート",  // テンプレートの説明
  "type": "text",                               // データファイルの種類（text）
  "dataFile": "metadata.pegasus.txt",           // データファイル名
  "delimiter": ":",                             // フィールドの区切り文字
  "header": {
    "enabled": true,                              // ヘッダー処理を有効にする
    "format": "key-value",                       // ヘッダー形式はキー値
    "startMarker": "collection:",                // ヘッダーの開始マーカー
    "endMarker": "",                             // ヘッダーの終了マーカー（空は最初のゲームエントリまで）
    "fieldMappings": {                           // プラットフォーム情報のフィールドマッピング
      "platformName": {                          // プラットフォーム名
        "fields": ["name", "title", "platform", "collection"],  // 一致させるフィールド名
        "isMultiValue": false                    // 複数値フィールドではない
      },
      "platformDescription": {                   // プラットフォームの説明
        "fields": ["description", "desc"],      // 一致させるフィールド名
        "isMultiValue": false
      },
      "platformLaunchCommand": {                 // プラットフォームの起動コマンド
        "fields": ["launch", "command"],        // 一致させるフィールド名
        "isMultiValue": false
      },
      "platformSortBy": {                        // プラットフォームのソートフィールド
        "fields": ["sort-by", "sort"],          // 一致させるフィールド名
        "isMultiValue": false
      }
    }
  },
  "fieldMappings": {                             // ゲームフィールドのマッピング
    "name": {                                    // ゲーム名
      "fields": ["title", "name", "game"],     // 一致させるフィールド名（順番に）
      "isMultiValue": false
    },
    "description": {                              // ゲームの説明
      "fields": ["description", "desc"],        // 一致させるフィールド名
      "isMultiValue": false
    },
    "releaseDate": {                              // リリース日
      "fields": ["release", "releasedate", "date"],  // 一致させるフィールド名
      "isMultiValue": false
    },
    "developer": {                                // 開発者
      "fields": ["developer", "dev"],           // 一致させるフィールド名
      "isMultiValue": false
    },
    "publisher": {                                // 発行者
      "fields": ["publisher", "pub"],           // 一致させるフィールド名
      "isMultiValue": false
    },
    "genre": {                                    // ゲームのジャンル
      "fields": ["genre", "category"],          // 一致させるフィールド名
      "isMultiValue": false
    },
    "players": {                                  // サポートされるプレイヤー数
      "fields": ["players", "player"],          // 一致させるフィールド名
      "isMultiValue": false
    },
    "files": {                                    // ゲームファイルパス
      "fields": ["files", "file"],              // 一致させるフィールド名
      "isMultiValue": true,                      // 複数値フィールドである
      "valuePrefix": "  "                        // 複数値フィールドの値のプレフィックス
    }
  },
  "mediaRules": {                                // メディアファイルのマッチングルール
    "box2dfront": [                              // ゲームボックスの表紙
      "media/{gameName}/boxFront.{ext}",         // パステンプレート 1
      "media/{gameName}/box_front.{ext}",        // パステンプレート 2
      "media/boxFront/{gameName}.{ext}",         // パステンプレート 3
      "media/images/{gameName}.{ext}"            // パステンプレート 4
    ],
    "box2dback": [                               // ゲームボックスの裏面
      "media/{gameName}/boxBack.{ext}",
      "media/{gameName}/box_back.{ext}",
      "media/boxBack/{gameName}.{ext}"
    ],
    "box3d": [                                   // 3D ゲームボックス
      "media/{gameName}/box3d.{ext}",
      "media/box3d/{gameName}.{ext}"
    ],
    "screenshot": [                              // ゲームのスクリーンショット
      "media/{gameName}/screenshot.{ext}",
      "media/{gameName}/screen.{ext}",
      "media/screenshots/{gameName}.{ext}",
      "media/screens/{gameName}.{ext}"
    ],
    "video": [                                   // ゲームの動画
      "media/{gameName}/video.{ext}",
      "media/{gameName}/trailer.{ext}",
      "media/videos/{gameName}.{ext}",
      "media/{gameName}.{ext}"
    ],
    "wheel": [                                   // ゲームのホイール
      "media/{gameName}/wheel.{ext}",
      "media/wheel/{gameName}.{ext}"
    ],
    "marquee": [                                 // ゲームのマーキー
      "media/{gameName}/marquee.{ext}",
      "media/{gameName}/logo.{ext}",
      "media/logos/{gameName}.{ext}",
      "media/marquees/{gameName}.{ext}"
    ],
    "fanart": [                                  // ゲームのファンアート
      "media/{gameName}/fanart.{ext}",
      "media/fanart/{gameName}.{ext}"
    ]
  },
  "extensions": {                                // ファイル拡張子の設定
    "image": ["png", "jpg", "jpeg", "gif", "webp"],  // サポートされる画像の拡張子
    "video": ["mp4", "mkv", "avi", "wmv", "webm"]    // サポートされる動画の拡張子
  },
  "gameExtensions": ["chd", "iso", "bin", "cue", "img", "zip", "7z", "rar", "nes", "snes", "md", "gen", "n64", "psx", "ps1", "gba", "nds"]  // サポートされるゲームファイルの拡張子
}
```

### 2.4 サポートされる変数

#### 2.4.1 パス変数

- `{gameName}`: ゲーム名
- `{ext}`: ファイル拡張子

### 2.5 サポートされるフィールド

#### 2.5.1 プラットフォームフィールド

- `platformName`: プラットフォーム名
- `platformSoftware`: プラットフォームソフトウェア
- `platformDatabase`: プラットフォームデータベース
- `platformWeb`: プラットフォームのウェブサイト
- `platformLaunchCommand`: プラットフォームの起動コマンド
- `platformSortBy`: プラットフォームのソートフィールド
- `platformDescription`: プラットフォームの説明

#### 2.5.2 ゲームフィールド

- `name`: ゲーム名
- `description`: ゲームの説明
- `releaseDate`: リリース日
- `developer`: 開発者
- `publisher`: 発行者
- `genre`: ゲームのジャンル
- `players`: サポートされるプレイヤー数
- `files`: ゲームファイルパス

#### 2.5.3 メディアフィールド

- `box2dfront`: ゲームボックスの表紙
- `box2dback`: ゲームボックスの裏面
- `box3d`: 3D ゲームボックス
- `screenshot`: ゲームのスクリーンショット
- `video`: ゲームの動画
- `wheel`: ゲームのホイール
- `marquee`: ゲームのマーキー
- `fanart`: ゲームのファンアート

### 2.6 サポートされる形式

#### 2.6.1 xml 形式

XML 形式、ES-DE などのフロントエンドに適しています：

```json
{
  "type": "xml",
  "dataFile": "gamelist.xml",
  "delimiter": ""
}
```

#### 2.6.2 text 形式

テキスト形式、Pegasus などのフロントエンドに適しています：

```json
{
  "type": "text",
  "dataFile": "metadata.pegasus.txt",
  "delimiter": ":"
}
```

### 2.7 メディアファイルのマッチングメカニズム

1. **パステンプレートの解析**: システムは設定ファイルのメディアファイルパステンプレートを解析し、変数を置換します
2. **拡張子のマッチング**: `extensions` 設定に従って異なるファイル拡張子を試します
3. **順序のマッチング**: 設定された順序で異なるパステンプレートを試し、一致するファイルが見つかるまで続けます
4. **相対パスの処理**: データファイルに対する相対パスをサポート
5. **絶対パスのサポート**: 絶対パスもサポート

### 2.8 複数値のマッチングメカニズム

テンプレート設定ファイルの `fieldMappings` セクションでは、各フィールドに複数の外部フィールド名を指定でき、システムは順番に一致させ、空でない値が見つかるまで続けます。例えば：

```json
"name": {
  "fields": ["title", "name", "game"],
  "isMultiValue": false
}
```

これは、最初に `title` フィールドを使用しようとし、空の場合は `name` フィールドを使用し、さらに空の場合は `game` フィールドを使用することを意味します。

## 3. 使用ガイド

### 3.1 インポートページへのアクセス

1. ホームページから「ゲームデータのインポート」ボタンをクリックします
2. または直接 `/import.html` にアクセスします

### 3.2 インポート設定

1. **テンプレートを選択**: ドロップダウンメニューから適切なインポートテンプレートを選択します
2. **データファイルを選択**: データファイルをアップロードするか、パスを指定します
3. **メディアファイルディレクトリを選択**: メディアファイルが配置されているディレクトリを指定します
4. **ターゲットプラットフォームを選択**: インポート先のターゲットプラットフォームを選択します

### 3.3 インポートの実行

「インポート」ボタンをクリックしてインポートプロセスを開始し、システムはリアルタイムの進捗と詳細なログを表示します。

## 4. 技術的な実装

### 4.1 バックエンドの実装

- **テンプレートの読み込み**: `ImportTemplateService` はインポートテンプレートの読み込みと解析を担当します
- **インポートサービス**: `ImportService` はインポート操作の実行を担当します
- **ファイル処理**: データファイルの解析、メディアファイルのマッチング、ゲームデータのインポートをサポート
- **API インターフェース**: `ImportController` はインポート関連の REST API を提供します

### 4.2 フロントエンドの実装

- **インポート設定インターフェース**: `import.html` はユーザーフレンドリーなインポート設定インターフェースを提供します
- **進捗フィードバック**: インポートの進捗と詳細なログをリアルタイムで表示
- **レスポンシブデザイン**: 異なる画面サイズに適応

## 5. 一般的な問題と解決策

### 5.1 インポートの失敗

- **データファイルを確認**: データファイルの形式が正しいことを確認します
- **テンプレート設定を確認**: 正しいインポートテンプレートが選択されていることを確認します
- **ファイル権限を確認**: アプリケーションにデータファイルとメディアファイルにアクセスするための十分な権限があることを確認します

### 5.2 メディアファイルの不一致

- **メディアルールを確認**: テンプレートのメディアルールが実際のファイル構造と一致していることを確認します
- **ファイルパスを確認**: メディアファイルのパスが正しいことを確認します
- **ファイル拡張子を確認**: メディアファイルの拡張子が設定に含まれていることを確認します

### 5.3 フィールドマッピングエラー

- **フィールドマッピングを確認**: テンプレートのフィールドマッピングがデータファイルのフィールド名と一致していることを確認します
- **データファイル形式を確認**: データファイルの形式がテンプレートの要件を満たしていることを確認します

## 6. 拡張ガイド

### 6.1 新しいインポートテンプレートの追加

1. `rules/import-templates/` ディレクトリに新しいテンプレート設定ファイルを作成します
2. テンプレート形式に従って設定を記入します
3. アプリケーションを再起動して新しいテンプレートを読み込みます

### 6.2 メディアルールのカスタマイズ

- テンプレート設定ファイルの `mediaRules` セクションを変更します
- メディアファイルのパステンプレートを実際のファイル構造に合わせて調整します

### 6.3 パフォーマンスの最適化

- 多数のファイルをインポートする場合は、並列処理を使用することをお勧めします
- 十分なディスク容量を確保します
- インポートソースとしてネットワークストレージを避けてください（パフォーマンスに影響する可能性があります）

## 7. トラブルシューティング

### 7.1 ログの表示

- バックエンドログ: アプリケーションコンテナのログを確認します
- フロントエンドログ: ブラウザの開発者ツールのコンソール

### 7.2 一般的なエラー

- **ファイルが見つかりません**: データファイルとメディアファイルのパスが正しいことを確認します
- **権限が不足しています**: アプリケーションにファイルにアクセスするための十分な権限があることを確認します
- **メモリ不足**: 多数のファイルをインポートする場合は、アプリケーションのメモリを増やす必要がある場合があります

## 8. まとめ

インポート機能は、外部フロントエンドからのゲームデータをインポートする柔軟で拡張可能な方法を提供し、複数のフロントエンド形式をサポートして異なるユーザーのニーズを満たします。インポートテンプレートを通じて、ユーザーはインポートルールを簡単に設定し、異なるフロントエンドのデータを迅速に移行することができます。

---

**バージョン**: 1.0
**最終更新日**: 2026-04-24