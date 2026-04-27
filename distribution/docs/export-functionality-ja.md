# エクスポート機能ドキュメント

## 1. 機能概要

エクスポート機能は、選択したプラットフォームのゲームファイル、メディアファイル、データファイルを指定されたパスにコピーすることができ、Pegasus、EmulationStation DE (ES-DE)、RetroBatなどの複数のフロントエンドルールをサポートしています。

### コア機能：
- 複数のフロントエンドルールのサポート
- ゲームファイル、メディアファイル、データファイルのエクスポートを選択可能
- カスタムエクスポートパスのサポート
- リアルタイムのエクスポート進捗とログフィードバック
- 柔軟なルール設定システム

## 2. ルール設定ファイル

### 2.1 設定ファイル形式

ルール設定ファイルはJSON形式を採用し、`src/main/resources/export-rules/`ディレクトリに保存されています。

### 2.2 設定ファイル構造

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
      "box2dback": {
        "source": "box2dback",
        "target": "{mediaPath}/{gameName}/boxBack.png",
        "dataFileTag": "assets.boxBack"
      }
    },
    "dataFile": {
      "filename": "metadata.pegasus.txt",
      "format": "pegasus",
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

### 2.3 サポートされている変数

- `{outputPath}`: エクスポートパス
- `{platform}`: プラットフォーム名
- `{gameName}`: ゲーム名
- `{mediaPath}`: メディアファイルディレクトリ
- `{romsPath}`: ROMファイルディレクトリ

### 2.4 サポートされているフィールド

- `name`: ゲーム名
- `description`: ゲームの説明
- `rating`: ゲームの評価
- `releaseYear`: リリース年
- `developer`: 開発者
- `publisher`: 発行者
- `genre`: ゲームのジャンル
- `players`: プレイヤー数
- `region`: ゲームのリージョン
- `filename`: パスから抽出されたファイル名（拡張子と先頭のパスを除く）

### 2.5 多値マッチングメカニズム

ルール設定ファイルの`fields`セクションでは、コンマで複数のフィールド名を区切ることができ、システムは空でない値が見つかるまで順にマッチングします。例えば：

```json
"title": "name,filename"
```

これは、最初に`name`フィールドを使用しようとし、空の場合に`filename`フィールドを使用することを意味します。

### 2.6 データベースフィールドの説明

以下はGameクラスで使用可能なデータベースフィールドであり、ルール設定ファイルの`fields`セクションで使用することができます：

| フィールド名 | タイプ | 説明 |
|-------------|--------|------|
| `id` | Long | ゲームID |
| `gameId` | String | ゲームの一意の識別子 |
| `source` | String | ゲームのソース |
| `path` | String | ゲームの相対パス |
| `name` | String | ゲーム名 |
| `desc` | String | ゲームの説明 |
| `translatedName` | String | 翻訳されたゲーム名 |
| `translatedDesc` | String | 翻訳されたゲームの説明 |
| `image` | String | ゲームの画像 |
| `video` | String | ゲームの動画 |
| `marquee` | String | ゲームのマーキー |
| `thumbnail` | String | ゲームのサムネイル |
| `manual` | String | ゲームのマニュアル |
| `boxFront` | String | ゲームのボックスフロント |
| `boxBack` | String | ゲームのボックスバック |
| `boxSpine` | String | ゲームのボックススパイン |
| `boxFull` | String | ゲームのボックスフル |
| `cartridge` | String | ゲームのカートリッジ |
| `logo` | String | ゲームのロゴ |
| `bezel` | String | ゲームのベゼル |
| `panel` | String | ゲームのパネル |
| `cabinetLeft` | String | キャビネットの左側 |
| `cabinetRight` | String | キャビネットの右側 |
| `tile` | String | ゲームのタイル |
| `banner` | String | ゲームのバナー |
| `steam` | String | Steamリンク |
| `poster` | String | ゲームのポスター |
| `background` | String | ゲームの背景 |
| `music` | String | ゲームの音楽 |
| `screenshot` | String | ゲームのスクリーンショット |
| `titlescreen` | String | ゲームのタイトル画面 |
| `box3d` | String | 3Dゲームボックス |
| `steamgrid` | String | Steamグリッド |
| `fanart` | String | ゲームのファンアート |
| `boxtexture` | String | ゲームのボックステクスチャ |
| `supporttexture` | String | サポートテクスチャ |
| `rating` | Double | ゲームの評価 |
| `releasedate` | String | ゲームのリリース日 |
| `developer` | String | ゲームの開発者 |
| `publisher` | String | ゲームの発行者 |
| `genre` | String | ゲームのジャンル |
| `players` | String | プレイヤー数 |
| `crc32` | String | ゲームファイルのCRC32チェックサム |
| `md5` | String | ゲームファイルのMD5チェックサム |
| `lang` | String | ゲームの言語 |
| `genreid` | String | ゲームのジャンルID |
| `hash` | String | ゲームファイルのハッシュ値 |
| `platformType` | String | プラットフォームのタイプ |
| `platformId` | Long | プラットフォームID |
| `sortBy` | String | ソートフィールド |
| `scraped` | Boolean | スクレイプされたかどうか |
| `edited` | Boolean | 編集されたかどうか |
| `exists` | Boolean | ファイルが存在するかどうか |
| `absolutePath` | String | ゲームの絶対パス |
| `platformPath` | String | プラットフォームのパス |

### 2.7 メディアファイルのソースフィールド

以下はメディアファイルのソースフィールドであり、ルール設定ファイルの`media`セクションの`source`フィールドで使用することができます：

| フィールド名 | 説明 |
|-------------|------|
| `box2dfront` | ゲームのボックスフロント |
| `box2dback` | ゲームのボックスバック |
| `box3d` | 3Dゲームボックス |
| `screenshot` | ゲームのスクリーンショット |
| `video` | ゲームの動画 |
| `wheel` | ゲームのロゴ（logoフィールドを使用） |
| `marquee` | ゲームのマーキー |
| `fanart` | ゲームのファンアート |

### 2.8 サポートされているフロントエンドルール

- **Pegasus**: `pegasus.json`
- **EmulationStation DE**: `esde.json`
- **RetroBat**: `retrobat.json`

### 2.8 ゲームファイルのリネーム設定（`gameFile`）

ルール設定ファイルでは、`gameFile`セクションを介してエクスポート時のゲームファイルのリネーム規則を設定できます：

```json
"gameFile": {
  "enabled": true,
  "template": "{platform}_{filename}"
}
```

- `enabled`: ゲームファイルのリネームを有効にするかどうか（デフォルト `false`）
- `template`: ファイル名テンプレート。以下の変数をサポート：

| 変数 | 説明 | 例 |
|------|------|-----|
| `{platform}` | プラットフォーム名 | nes, snes, genesis |
| `{filename}` | 拡張子なしの元のファイル名 | supermario |
| `{name}` | ゲーム名（不正な文字は自動フィルタ） | Super Mario Bros |
| `{ext}` | ファイル拡張子 | nes, sfc, md |

**使用例**：

1. 「プラットフォーム_ファイル名」形式にリネーム：
```json
"gameFile": {
  "enabled": true,
  "template": "{platform}_{filename}"
}
```
結果：`nes_supermario.nes`

2. ゲーム名にリネーム：
```json
"gameFile": {
  "enabled": true,
  "template": "{name}"
}
```
結果：`Super Mario Bros.nes`

3. プレフィックスとサフィックスを追加：
```json
"gameFile": {
  "enabled": true,
  "template": "{platform}_{filename}_usa"
}
```
結果：`nes_supermario_usa.nes`

### 2.9 フィールド変換設定（`fieldTransforms`）

ルール設定ファイルの `dataFile` セクションでは、`fieldTransforms` を介してエクスポート時のフィールド値変換規則を設定できます：

```json
"dataFile": {
  "fields": {
    "files": "path",
    "title": "name"
  },
  "fieldTransforms": {
    "files": {
      "path": "no"
    },
    "title": {
      "trim": true,
      "case": "upper",
      "replace": {
        "from": " ",
        "to": "_"
      }
    }
  }
}
```

**サポートされる変換オプション**：

| オプション | 説明 | 値 |
|------------|------|-----|
| `path` | パス形式変換 | `no`（`./` なし）、`yes`（`./` あり）、`keep`（元のまま） |
| `trim` | 先頭と末尾のスペースをトリムするかどうか | `true`、`false` |
| `case` | 大文字小文字変換 | `upper`（大文字）、`lower`（小文字）、`none`（変換なし） |
| `replace` | 文字列置換 | `from` と `to` フィールドを持つオブジェクト |

**使用例**：

1. パス変換（`./` プレフィックスを削除）：
```json
"fieldTransforms": {
  "files": {
    "path": "no"
  }
}
```
結果：`roms/supermario.nes`

2. パス変換（`./` プレフィックスを追加）：
```json
"fieldTransforms": {
  "path": {
    "path": "yes"
  }
}
```
結果：`./roms/supermario.nes`

3. 文字列置換和大文字小文字変換：
```json
"fieldTransforms": {
  "title": {
    "trim": true,
    "case": "upper",
    "replace": {
      "from": " ",
      "to": "_"
    }
  }
}
```
結果：`SUPER_MARIO_BROS`

**組み込みエクスポートテンプレートのパス形式設定**：

システムでは、各フロントエンドに対して異なるパス形式を事前設定しています：
- **Pegasus**: パス出力時に `./` プレフィックスなし（`path: "no"`）
- **RetroBat / ESDE**: パス出力時に `./` プレフィックスあり（`path: "yes"`）

これにより、エクスポートされたデータファイルが各フロントエンドの形式要件に準拠することが保証されます。

### 2.9.1 M3U処理の設定

システムはM3U形式のプレイリストファイルの処理をサポートしています。M3Uファイルに遭遇すると、ファイル内で参照されているすべてのファイルが自動的にコピーされます。

#### 2.9.1 M3U処理の設定

ルール設定ファイルでは、`m3u`セクションを介してM3Uファイルの処理方法を設定することができます：

```json
"m3u": {
  "enabled": true,
  "target": "{romsPath}"
}
```

- `enabled`: M3U処理を有効にするかどうか
- `target`: M3Uファイル内で参照されているファイルのターゲットパステンプレート

#### 2.9.2 サポートされているパスタイプ

- **相対パス**: M3Uファイルに対する相対パス
- **絶対パス**: 完全なファイルパス

## 3. 使用ガイド

### 3.1 エクスポートページへのアクセス

1. ホームページから「プラットフォームエクスポートに入る」ボタンをクリックします
2. または、直接 `/export.html` にアクセスします

### 3.2 エクスポート設定

1. **プラットフォームの選択**: ドロップダウンメニューからエクスポートするプラットフォームを選択します
2. **フロントエンドの選択**: ドロップダウンメニューからターゲットフロントエンドルールを選択します
3. **エクスポート範囲**: エクスポートするコンテンツ（ゲームファイル、メディアファイル、データファイル）をチェックします
4. **エクスポートパス**: デフォルトパスは `/data/output` で、カスタマイズすることができます

### 3.3 エクスポートの実行

「エクスポート」ボタンをクリックしてエクスポートプロセスを開始し、システムはリアルタイムの進捗と詳細なログを表示します。

## 4. 技術的な実装

### 4.1 バックエンドの実装

- **ルールのロード**: `ExportRuleService` はルール設定ファイルのロードと解析を担当します
- **エクスポートサービス**: `ExportService` はエクスポート操作の実行を担当します
- **ファイル処理**: ファイルのコピー、メディアファイルの処理、データファイルの生成をサポートします
- **APIインターフェース**: `ExportController` はエクスポート関連のREST APIを提供します

### 4.2 フロントエンドの実装

- **エクスポート設定インターフェース**: `export.html` はユーザーフレンドリーなエクスポート設定インターフェースを提供します
- **進捗フィードバック**: リアルタイムのエクスポート進捗と詳細なログを表示します
- **レスポンシブデザイン**: 異なる画面サイズに適応します

## 5. よくある問題と解決策

### 5.1 エクスポートの失敗

- **エクスポートパスを確認**: エクスポートパスが存在し、書き込み権限があることを確認します
- **プラットフォームデータを確認**: プラットフォームにゲームデータがあることを確認します
- **ルール設定を確認**: ルール設定ファイルが正しいことを確認します

### 5.2 メディアファイルの不一致

- **ルール設定を確認**: ルール設定ファイルのメディアファイルのマッピングが正しいことを確認します
- **ソースファイルを確認**: ソースメディアファイルが存在することを確認します

### 5.3 データファイルの形式エラー

- **ルール設定を確認**: データファイルの形式設定が正しいことを確認します
- **ゲームデータを確認**: ゲームデータが完全であることを確認します

## 6. 拡張ガイド

### 6.1 新しいフロントエンドルールの追加

1. `export-rules` ディレクトリに新しいルール設定ファイルを作成します
2. テンプレート形式に従ってルール設定を記入します
3. 新しいルールをロードするためにアプリケーションを再起動します

### 6.2 メディアファイルルールのカスタマイズ

- ルール設定ファイルの `media` セクションを変更します
- `source`、`target`、`dataFileTag` フィールドを調整します

### 6.3 パフォーマンスの最適化

- 大量のファイルをエクスポートする場合は、並列処理を使用することをお勧めします
- 十分なディスクスペースを確保します
- エクスポートターゲットとしてネットワークストレージを使用しないでください（パフォーマンスに影響する可能性があります）

## 7. トラブルシューティング

### 7.1 ログの表示

- バックエンドログ: アプリケーションコンテナのログを表示します
- フロントエンドログ: ブラウザの開発者ツールのコンソール

### 7.2 よくあるエラー

- **ファイルが見つからない**: ソースファイルパスが正しいかどうかを確認します
- **権限が不足している**: アプリケーションがソースファイルとターゲットパスにアクセスするための十分な権限を持っていることを確認します
- **メモリ不足**: 大量のファイルをエクスポートする場合、アプリケーションのメモリを増やす必要がある場合があります

## 8. まとめ

エクスポート機能は、複数のフロントエンドルールをサポートし、異なるユーザーのニーズを満たすための柔軟で拡張可能な方法でゲームプラットフォームデータをエクスポートする手段を提供します。ルール設定ファイルを通じて、ユーザーは異なるフロントエンドの要件に適応するためにエクスポート動作を簡単にカスタマイズすることができます。

---

**バージョン**: 1.1
**最終更新**: 2026-04-21