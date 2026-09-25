# v3 テンプレートシステムドキュメント

## 1. 概要

v3 は Frontend Killer の統一テンプレートフォーマットバージョンで、データファイルのインポートとエクスポートに使用されます。v2 と比較して、v3 はより強力な式エンジン、統一されたフィールド命名体系、そしてより柔軟なメディア検出メカニズムを提供します。

### 1.1 v3 の主な改善点

- **統一フィールドアクセサ**: nomcourt（API 元の値）、dbColumn（データベースカラム名）、javaField（Java フィールド名）の3つの名前で同じフィールドにアクセス可能
- **式エンジン**: 関数呼び出し、演算子、条件式などをサポート
- **計算変数注入**: `filename`、`filepath` などの計算変数を自動注入
- **メディア検出メカニズム**: `mediaDiscovery` ルールによるディスク上のメディアファイルの自動検索をサポート
- **マルチディスクゲームサポート**: マルチディスクゲームを自動的に個別エントリに展開

### 1.2 テンプレート構造

v3 テンプレートは統一された JSON 構造を使用します：

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import|export",
    "dataFileType": "data|text",
    "format": "xml|text",
    "dataFile": "gamelist.xml",
    "author": "著者名",
    "description": "テンプレートの説明",
    "notes": "備考"
  },
  "system": {
    // システム/プラットフォーム情報設定
  },
  "game": {
    // ゲーム情報とメディア情報設定
  },
  "output": {
    // エクスポート設定（エクスポートテンプレートのみ）
  },
  "variables": [
    // 省略可：実行前にユーザーが設定するテンプレート変数（3.4 参照）
  ]
}
```

---

## 2. 式エンジン

v3 テンプレートシステムには強力な式エンジンが組み込まれており、テンプレート値で関数、演算子、変数参照をサポートします。

### 2.1 演算子

#### 2.1.1 `or` フォールバック演算子

最初の null でない値を返します。SQL の COALESCE と同様です：

```
name or filename
box-2D or screenshot or image
```

**優先度**: `or` は最も低い優先度の演算子です。

#### 2.1.2 `+` 文字列結合演算子

複数の文字列を結合します：

```
name + ".jpg"
platform.system + "_" + filename
```

**優先度**: `+` は `or` より優先度が高いため：
- `name or filename + ".jpg"` は `name or (filename + ".jpg")` と同等
- 括弧で優先度を変更: `(name or filename) + ".jpg"`

### 2.2 組み込み関数

#### 2.2.1 文字列操作関数

| 関数 | 構文 | 説明 | 例 |
|------|------|------|-----|
| `sub` | `sub(str, start[, end])` | 部分文字列を抽出 | `sub(name, 0, 7)` |
| `upper` | `upper(str)` | 大文字に変換 | `upper(name)` |
| `lower` | `lower(str)` | 小文字に変換 | `lower(name)` |
| `trim` | `trim(str)` | 先頭・末尾の空白を除去 | `trim(desc)` |
| `replace` | `replace(str, from, to)` | 文字列置換 | `replace(path, "\", "/")` |
| `len` | `len(str)` | 文字列長 | `len(name)` |

#### 2.2.2 パス操作関数

| 関数 | 構文 | 説明 | 例 |
|------|------|------|-----|
| `filename` | `filename(path)` | パスからファイル名を抽出（拡張子含む） | `filename(path)` → "game.zip" |
| `stem` | `stem(path)` | 拡張子を除去 | `stem(path)` → "game" |
| `ext` | `ext(path)` | 拡張子を抽出（ドット含む） | `ext(path)` → ".zip" |
| `dir` | `dir(path)` | ディレクトリ部分を抽出 | `dir(path)` → "/roms/nes" |

#### 2.2.3 条件関数

| 関数 | 構文 | 説明 | 例 |
|------|------|------|-----|
| `default` | `default(field, fallback)` | フィールドが空の場合デフォルト値を使用 | `default(desc, "説明なし")` |
| `if` | `if(condition, trueVal, falseVal)` | 条件分岐 | `if(video, "動画あり", "動画なし")` |
| `coalesce` | `coalesce(f1, f2, ...)` | 最初の null でない値を返す | `coalesce(box-2D, screenshot, image)` |

#### 2.2.4 日付関数

| 関数 | 構文 | 説明 | 例 |
|------|------|------|-----|
| `dateformat` | `dateformat(dateStr, pattern)` | 日付をフォーマット | `dateformat(releasedate, "yyyy-MM-dd")` |

**サポートされる入力フォーマット**:
- `yyyy-MM-dd'T'HH:mm:ss`
- `yyyy-MM-dd HH:mm:ss`
- `yyyy-MM-dd`
- `yyyyMMdd`
- `yyyy`
- `MM/dd/yyyy`
- `dd/MM/yyyy`

#### 2.2.5 方言マッピング関数

| 関数 | 構文 | 説明 | 例 |
|------|------|------|-----|
| `map` | `map(value[, category])` | 方言マッピング：方言マッピング表にヒットすれば変換語を返し、ヒットしなければ元の値を返す | `map(genre, "genre")` |

`map()` はシステムの**方言マッピング表**（term_mapping、システム設定で管理）に基づく決定的な翻訳です。例：スクレイピングで取得した英語のジャンルを中国語に統一する（`Beat-'Em-Up` → `清版游戏`）。

- `map(value)`：全マッピングカテゴリを検索（`system_alias` カテゴリはプラットフォームとシステムのマッチング内部用のため対象外）。同じ語が複数カテゴリにヒットした場合はカテゴリ名の辞書順で最初のものを使用し、結果が常に決定的になる
- `map(value, category)`：指定カテゴリのみを検索
- 検索前に元の語を正規化（小文字化、スペース・句読点の除去）するため、`Beat'em Up`、`beat-em-up`、`BEAT EM UP` は同一の語として扱われる
- ヒットしなかった場合は入力値をそのまま返し、エラーにはならない

### 2.3 式の例

```
# 基本フィールド参照
name
desc
box-2D

# フォールバックチェーン
box-2D or screenshot or image

# 文字列結合
name + ".jpg"
platform.system + "_" + filename

# 関数呼び出し
upper(name)
replace(path, "\", "/")
stem(path)

# ネストした呼び出し
trim(replace(name, " ", "_"))

# 条件式
if(video, "動画あり", "動画なし")
default(desc, "説明なし")

# 方言マッピング
map(genre, "genre")
map(desc)

# 複雑な式
(name or filename) + ".jpg"
coalesce(box-2D, screenshot, image)
```

---

## 3. 利用可能な変数

### 3.1 ゲームフィールド変数

すべてのゲームフィールドはフィールド名で直接参照できます。3つの命名規則がサポートされています：

#### 3.1.1 基本フィールド

| フィールド | 説明 | 例 |
|-----------|------|-----|
| `name` | ゲーム名 | "Super Mario Bros" |
| `desc` | ゲーム説明 | "A classic platform game..." |
| `releasedate` | リリース日 | "1985-09-13" |
| `developer` | 開発元 | "Nintendo" |
| `publisher` | パブリッシャー | "Nintendo" |
| `genre` | ジャンル | "Platform" |
| `players` | プレイヤー数 | "1-2" |
| `rating` | 評価 | "0.85" |
| `path` | ゲームパス | "./roms/nes/supermario.nes" |
| `lang` | 言語 | "en" |
| `region` | リージョン | "us" |
| `sort-by` | ソートフィールド | "Mario" |
| `gameId` | ゲーム ID | "12345" |
| `hash` | ファイルハッシュ | "abc123..." |
| `crc32` | CRC32 チェックサム | "12345678" |
| `md5` | MD5 チェックサム | "abcdef..." |
| `source` | データソース | "screenscraper" |
| `platformType` | プラットフォームタイプ | "console" |

#### 3.1.2 計算フィールド（読み取り専用）

| フィールド | 説明 | 計算方法 |
|-----------|------|----------|
| `filename` | 拡張子なしファイル名 | `path` から抽出 |
| `releaseYear` | リリース年 | `releasedate` の先頭4文字 |

#### 3.1.3 フィールドエイリアス

以下のエイリアスは標準フィールド名の代わりに使用できます：

| エイリアス | マップ先 |
|-----------|---------|
| `description` | `desc` |
| `file` | `path` |
| `region` | `lang` |
| `category` | `genre` |
| `releaseDate` | `releasedate` |
| `manuel` | `manual` |

### 3.2 プラットフォーム変数

`platform.` プレフィックスでプラットフォーム情報にアクセスします：

| 変数 | 説明 | 例 |
|------|------|-----|
| `platform.system` | プラットフォームシステム名 | "nes" |
| `platform.name` | プラットフォーム名 | "Nintendo Entertainment System" |
| `platform.launch` | 起動コマンド | "retroarch -L nes_libretro.dll" |
| `platform.software` | プラットフォームソフトウェア | "RetroArch" |
| `platform.database` | データベース名 | "screenscraper" |
| `platform.web` | Web サイト URL | "https://screenscraper.fr" |
| `platform.folderPath` | プラットフォームフォルダパス | "/roms/nes" |

### 3.3 計算変数（インポート時に自動注入）

インポートテンプレートでは、以下の計算変数がシステムによって自動注入されます：

| 変数 | 説明 | 計算方法 |
|------|------|----------|
| `filename` | 拡張子なしファイル名 | `path` から抽出 |
| `filepath` | 拡張子と `./` プレフィックスなしファイルパス | `path` から抽出 |

**例**:
- `path` = `./roms/nes/supermario.nes`
- `filename` = `supermario`
- `filepath` = `roms/nes/supermario`

### 3.4 テンプレート変数（variables ブロック）

いくつかの値は「ゲーム加工段階」では未知であり、インポート/エクスポートの**アクション実行前**にユーザーが設定する必要があります（例：URL 結合用のプレフィックス、エクスポート後に ROM を格納するフォルダ名）。v3 テンプレートはこれらを宣言する省略可能なトップレベル `variables` ブロックを提供します。変数宣言のあるテンプレートを選択すると、インポート/エクスポートをクリックした際にダイアログが表示され、各**変数名・入力欄・説明**を示し、入力値はグローバル変数としてリクエストとともにバックエンドへ送信されます。

```json
"variables": [
  {
    "name": "romSubdir",
    "label": "ROM フォルダ",
    "description": "エクスポート後、ゲームファイルが格納されるサブフォルダ名。プレイリストの path がこれを指します",
    "type": "text",
    "default": "{platform.system}",
    "required": true
  }
]
```

| フィールド | 説明 |
|------|------|
| `name` | 数式と `{xxx}` プレースホルダで使用される変数名。識別子（英字/下線始まり）であり、組み込み変数と重複してはいけません |
| `label` | ダイアログに表示される変数名 |
| `description` | ダイアログに表示される説明（なぜ必要か / どう作用するか） |
| `type` | `text` \| `path` のみ（`path` はダイアログにパスブラウザを提供） |
| `default` | 省略可能なデフォルト値。`{platform.xxx}` プレースホルダをサポートし、実行時にプラットフォームごとに解決 |
| `required` | デフォルト `false`。空かつ `required=true` の場合、フロントエンドは送信を阻止し、バックエンドは 400 を返します |

**動作規約**：
- 変数値は**永続化されず**、インポート/エクスポートのたびに再入力が必要です。
- 複数プラットフォームの一括エクスポートは一度だけダイアログを表示し、全プラットフォームが**同じ値を共有**します。プラットフォーム間の差は `default`/数式内の `{platform.xxx}` で解決します。
- 変数名が組み込み変数と衝突する場合、組み込みが優先されます（その宣言は WARN ログとともに無視され、旧テンプレートに影響を与えません）。
- `variables` ブロックのないテンプレートは従来と完全に同じ動作です。

**テンプレート内での使用（URL / 文字列の結合）**：注入された変数は同じ vars map に保存され、エンジンにはそれを読み込む2つのチャネルがあるため、変数は直接結合に使用できます：

1. **`{var}` プレースホルダチャネル**：`output.*.directory`、`filename`、header/footer 行などに対して文字面 `{key}→value` 置換。例 `"directory": "{outputPath}/{romSubdir}"`。
2. **数式中の裸識別子チャネル**：変数名は `concat(...)`、`+` などの中に直接出現できます。例 `"image": "concat(cdnBase, '/', platform.system, '/', filename(name), '.png')"`。

変数が空の場合、`concat`/`+` は既存のセマンティクスに従い空値をスキップするか null を返し、他のフィールドに影響しません。サンプルテンプレート：`rules/export/retroarch-folder-v3.json`。

---

## 4. フィールド名マッピングテーブル

v3 システムでは3つの命名規則で同じフィールドにアクセスできます。以下は完全なマッピングテーブルです。

### 4.1 メディアタイプフィールドマッピングテーブル

| nomcourt（API 元の値） | dbColumn（DB カラム名） | javaField（Java フィールド名） | カテゴリ | フォーマット |
|------------------------|------------------------|-------------------------------|---------|-------------|
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

### 4.2 命名規則の説明

- **nomcourt**: ScreenScraper API の元の値。元の大文字小文字とハイフンを保持（例: `box-2D`）
- **dbColumn**: データベースカラム名。nomcourt の `-` を `_` に置換し、すべて小文字（例: `box_2d`）
- **javaField**: Java フィールド名。dbColumn の snake_case を camelCase に変換（例: `box2d`）

### 4.3 フィールドアクセスの例

以下の3つの方法で同じフィールドにアクセスします：

```
# nomcourt を使用
box-2D

# dbColumn を使用
box_2d

# javaField を使用
box2d
```

システムはこれらを自動的に認識し、同じフィールドにマッピングします。

---

## 5. インポートテンプレート設定

### 5.1 インポートテンプレート構造

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import",
    "dataFileType": "data|text",
    "format": "xml|text",
    "dataFile": "gamelist.xml",
    "delimiter": ":"
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
    "gameInfo": {
      "name": ["name", "sortname"],
      "desc": ["desc", "description"]
    },
    "mediaInfo": {
      "box-2D": ["boxart", "image"],
      "ss": ["screenshot"]
    },
    "mediaDiscovery": {
      // メディア検出設定（オプション）
    }
  }
}
```

### 5.2 gameInfo フィールドマッピング

`gameInfo` はデータファイルフィールドからシステムフィールドへのマッピングを定義します：

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

**説明**:
- キー（例: `name`）はシステムフィールド名
- 値（例: `["name", "sortname"]`）はデータファイルに表示される可能性のあるフィールド名のリスト。優先度順にマッチ

### 5.3 mediaInfo フィールドマッピング

`mediaInfo` はメディアファイルパスからシステムメディアフィールドへのマッピングを定義します：

```json
"mediaInfo": {
  "box-2D": ["boxart", "image"],
  "ss": ["screenshot"],
  "video": ["video"],
  "wheel": ["wheel", "marquee"]
}
```

### 5.4 mediaDiscovery 設定

`mediaDiscovery` はディスク上のメディアファイルを自動的に検索するために使用されます：

```json
"mediaDiscovery": {
  "enabled": true,
  "baseDir": "media",
  "subDirPattern": "{filename}",
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

**設定説明**:
- `enabled`: メディア検出を有効にするかどうか
- `baseDir`: メディアファイルのベースディレクトリ
- `subDirPattern`: サブディレクトリパターン。`{filename}` 変数をサポート
- `extensions`: 検索するファイル拡張子のリスト
- `rules`: 各メディアタイプの検索ルール。`{filename}` と `{ext}` 変数をサポート

**変数置換**:
- `{filename}`: 拡張子なしのゲームファイル名
- `{ext}`: 現在検索中のファイル拡張子

**例**:
ゲーム `supermario.nes` の場合、システムは `box-2D` メディアを以下の順序で検索します：
1. `media/supermario/boxFront.png`
2. `media/supermario/boxFront.jpg`
3. `media/supermario/box_front.png`
4. ...
5. `boxFront/supermario.png`
6. `box2dfront/supermario.png`
7. `images/supermario.png`

---

## 6. エクスポートテンプレート設定

### 6.1 エクスポートテンプレート構造

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "export",
    "dataFileType": "data|text",
    "delimiter": ": ",
    "dataFile": "metadata.pegasus.txt"
  },
  "system": {
    "header": [
      "collection: {platform.system}",
      "sort-by: 064",
      "{platform.launch}",
      ""
    ],
    "fields": {
      "collection": "platform.system",
      "launch": "platform.launch"
    }
  },
  "game": {
    "gameStartMarker": "game:",
    "entrySeparator": "\n\n",
    "gameInfo": {
      "game": "name",
      "file": "path",
      "description": "desc"
    },
    "mediaInfo": {
      "assets.box_front": "box-2D",
      "assets.screenshot": "ss"
    }
  },
  "output": {
    "filename": "metadata.pegasus.txt",
    "pathFormat": "relative",
    "directory": {
      "roms": "{outputPath}/{platform.system}",
      "media": "{outputPath}/{platform.system}/media"
    },
    "media": {
      "video": {
        "source": "video",
        "target": "{mediaPath}/{gameName}/video.mp4"
      }
    }
  }
}
```

### 6.2 system.header 設定

`header` はデータファイルのヘッダー行を定義します：

```json
"header": [
  "collection: {platform.system}",
  "sort-by: 064",
  "{platform.launch}",
  ""
]
```

**説明**:
- 各行は文字列
- `{platform.xxx}` 変数をサポート
- 空文字列は空行

### 6.3 game.gameInfo 設定

`gameInfo` はシステムフィールドからデータファイルフィールドへのマッピングを定義します：

```json
"gameInfo": {
  "game": "name",
  "file": "path",
  "sort-by": "sort-by",
  "developer": "developer",
  "description": "desc"
}
```

**説明**:
- キー（例: `game`）はデータファイルのフィールド名
- 値（例: `name`）はシステムフィールド名または式

**式サポート**:

```json
"gameInfo": {
  "title": "name or filename",
  "year": "sub(releasedate, 0, 4)",
  "path": "replace(path, '\\', '/')"
}
```

### 6.4 game.mediaInfo 設定

`mediaInfo` はデータファイルのメディアタグからシステムメディアフィールドへのマッピングを定義します：

```json
"mediaInfo": {
  "assets.box_front": "box-2D",
  "assets.screenshot": "ss",
  "assets.video": "video"
}
```

**説明**:
- キー（例: `assets.box_front`）はデータファイルのメディアタグ
- 値（例: `box-2D`）はシステムメディアフィールド名

### 6.5 output 設定

`output` はエクスポートの動作を定義します：

```json
"output": {
  "filename": "metadata.pegasus.txt",
  "pathFormat": "relative",
  "directory": {
    "roms": "{outputPath}/{platform.system}",
    "media": "{outputPath}/{platform.system}/media"
  },
  "media": {
    "video": {
      "source": "video",
      "target": "{mediaPath}/{gameName}/video.mp4"
    }
  },
  "m3u": {
    "enabled": false
  }
}
```

**設定説明**:
- `filename`: 出力データファイル名
- `pathFormat`: パスフォーマット（`relative` または `absolute`）
- `directory`: ディレクトリ構造設定
  - `roms`: ROM ファイルディレクトリ
  - `media`: メディアファイルディレクトリ
- `media`: メディアファイルコピールール
  - `source`: ソースメディアフィールド
  - `target`: ターゲットパステンプレート
- `m3u`: M3U プレイリスト処理設定

**サポートされる変数**:
- `{outputPath}`: エクスポートパス
- `{platform.system}`: プラットフォームシステム名
- `{mediaPath}`: メディアディレクトリパス
- `{gameName}`: ゲーム名

---

## 7. 完全な例

### 7.1 ES-DE インポートテンプレートの例

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import",
    "dataFileType": "data",
    "format": "xml",
    "dataFile": "gamelist.xml",
    "author": "Frontend-Killer",
    "description": "EmulationStation / ES-DE インポートテンプレート（v3 統一フォーマット）"
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
      "gameId": ["attr_id"]
    },
    "mediaInfo": {
      "box-2D": ["boxart", "image"],
      "ss": ["screenshot"],
      "video": ["video"],
      "wheel": ["wheel", "marquee"],
      "mix": ["mix", "image"],
      "marquee": ["marquee"]
    }
  }
}
```

### 7.2 Pegasus インポートテンプレートの例（mediaDiscovery 付き）

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "import",
    "dataFileType": "text",
    "delimiter": ":",
    "dataFile": "metadata.pegasus.txt",
    "author": "Frontend-Killer",
    "description": "Pegasus Frontend インポートテンプレート（v3 統一フォーマット）"
  },
  "system": {
    "gameStartMarker": "game:",
    "fields": {
      "platform.system": ["collection"],
      "platform.launch": ["launch", "command"]
    }
  },
  "game": {
    "gameStartMarker": "game:",
    "multiLine": true,
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
      "ss": ["assets.screenshot"],
      "video": ["assets.video"],
      "wheel-carbon": ["assets.logo"],
      "wheel": ["assets.wheel"],
      "marquee": ["assets.marquee"],
      "fanart": ["assets.fanart"],
      "bezel-16-9": ["assets.bezel"]
    },
    "mediaDiscovery": {
      "enabled": true,
      "baseDir": "media",
      "subDirPattern": "{filename}",
      "extensions": ["png", "jpg", "jpeg", "gif", "webp", "mp4", "mkv"],
      "rules": {
        "box-2D": [
          "{filename}/boxFront.{ext}",
          "{filename}/box_front.{ext}",
          "boxFront/{filename}.{ext}"
        ],
        "video": [
          "{filename}/video.{ext}",
          "videos/{filename}.{ext}"
        ]
      }
    }
  }
}
```

### 7.3 ES-DE エクスポートテンプレートの例

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "export",
    "dataFileType": "data",
    "format": "xml",
    "dataFile": "gamelist.xml",
    "author": "Frontend-Killer",
    "description": "EmulationStation / ES-DE エクスポートテンプレート（v3 統一フォーマット）"
  },
  "system": {
    "header": [
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
      "<gameList>"
    ],
    "footer": [
      "</gameList>"
    ],
    "fields": {}
  },
  "game": {
    "gameStartMarker": "game",
    "gameInfo": {
      "name": "name",
      "sortname": "sort-by",
      "description": "desc",
      "releasedate": "releasedate",
      "developer": "developer",
      "publisher": "publisher",
      "genre": "genre",
      "players": "players",
      "rating": "rating",
      "lang": "lang",
      "region": "region"
    },
    "mediaInfo": {
      "image": "box-2D",
      "video": "video",
      "marquee": "wheel",
      "thumbnail": "ss"
    }
  },
  "output": {
    "filename": "gamelist.xml",
    "pathFormat": "relative",
    "directory": {
      "roms": "{outputPath}/roms/{platform.system}",
      "media": "{outputPath}/downloaded_media/{platform.system}",
      "gamelist": "{outputPath}/gamelists/{platform.system}"
    },
    "media": {
      "boxfront": {
        "source": "box-2D",
        "target": "{mediaPath}/boxfront/{filename}.png"
      },
      "screenshot": {
        "source": "ss",
        "target": "{mediaPath}/screenshot/{filename}.png"
      },
      "video": {
        "source": "video",
        "target": "{mediaPath}/video/{filename}.mp4"
      }
    }
  }
}
```

### 7.4 Pegasus エクスポートテンプレートの例

```json
{
  "templateInfo": {
    "version": 3,
    "direction": "export",
    "dataFileType": "text",
    "delimiter": ": ",
    "dataFile": "metadata.pegasus.txt",
    "author": "Frontend-Killer",
    "description": "Pegasus Frontend エクスポートテンプレート（v3 統一フォーマット）"
  },
  "system": {
    "header": [
      "collection: {platform.system}",
      "sort-by: 064",
      "{platform.launch}",
      ""
    ],
    "fields": {
      "collection": "platform.system",
      "launch": "platform.launch"
    }
  },
  "game": {
    "gameStartMarker": "game:",
    "entrySeparator": "\n\n",
    "gameInfo": {
      "game": "name",
      "file": "path",
      "sort-by": "sort-by",
      "developer": "developer",
      "publisher": "publisher",
      "genre": "genre",
      "players": "players",
      "description": "desc",
      "rating": "rating",
      "release": "releasedate"
    },
    "mediaInfo": {
      "assets.box_front": "box-2D",
      "assets.box_back": "box-2D-back",
      "assets.screenshot": "ss",
      "assets.video": "video",
      "assets.logo": "wheel-carbon",
      "assets.wheel": "wheel",
      "assets.marquee": "marquee",
      "assets.fanart": "fanart",
      "assets.bezel": "bezel-16-9"
    }
  },
  "output": {
    "filename": "metadata.pegasus.txt",
    "pathFormat": "relative",
    "directory": {
      "roms": "{outputPath}/{platform.system}",
      "media": "{outputPath}/{platform.system}/media"
    },
    "media": {
      "video": {
        "source": "video",
        "target": "{mediaPath}/{gameName}/video.mp4"
      },
      "box_front": {
        "source": "box-2D",
        "target": "{mediaPath}/{gameName}/boxFront.png"
      },
      "screenshot": {
        "source": "ss",
        "target": "{mediaPath}/{gameName}/screenshot.png"
      },
      "logo": {
        "source": "wheel-carbon",
        "target": "{mediaPath}/{gameName}/logo.png"
      }
    },
    "m3u": {
      "enabled": false
    }
  }
}
```

---

## 8. ベストプラクティス

### 8.1 フィールド命名の推奨

- **インポートテンプレート**: ScreenScraper API との一貫性のため nomcourt（例: `box-2D`）を優先
- **エクスポートテンプレート**: ターゲットフロントエンドの要件に基づいて命名を選択
- **式**: 最も読みやすい名前を使用。例: `box-2D` は `box_2d` より明確

### 8.2 メディア検出設定の推奨

- よく使用されるメディアタイプには複数の検索パスを設定し、マッチ率を向上
- `{filename}` 変数を使用してサブディレクトリ構造を作成し、管理を容易に
- 一般的な拡張子を含める: `png`, `jpg`, `jpeg`, `gif`, `webp`, `mp4`

### 8.3 式使用の推奨

- `or` 演算子でフォールバック値を提供: `name or filename`
- `default` 関数でデフォルト値を提供: `default(desc, "説明なし")`
- `coalesce` で多段階フォールバックを処理: `coalesce(box-2D, screenshot, image)`
- 括弧で優先度を明示: `(name or filename) + ".jpg"`

---

## 9. トラブルシューティング

### 9.1 フィールドにアクセスできない

**問題**: テンプレートで使用されているフィールド名が値を取得できない

**解決策**:
1. フィールド名が正しいか確認（第4節のマッピングテーブルを参照）
2. 異なる命名規則を試す（nomcourt / dbColumn / javaField）
3. フィールドに値があるか確認（一部のフィールドは空の可能性がある）

### 9.2 式の計算に失敗

**問題**: 式が null または不正な値を返す

**解決策**:
1. 関数の構文が正しいか確認
2. パラメータの型が一致しているか確認
3. 単純なフィールド名でテストし、段階的に複雑さを追加

### 9.3 メディア検出に失敗

**問題**: mediaDiscovery がメディアファイルを見つけられない

**解決策**:
1. `baseDir` パスが正しいか確認
2. `rules` のパスパターンが実際のファイル構造と一致しているか確認
3. `extensions` にターゲットファイルの拡張子が含まれているか確認
4. ログを確認して検索プロセスを把握

---

## 10. 付録

### 10.1 v2 と v3 の主な違い

| 機能 | v2 | v3 |
|------|----|----|
| フィールド命名 | javaField を使用（例: `box2d`） | nomcourt / dbColumn / javaField をサポート |
| 式 | 単純な変数置換 | 関数、演算子、条件をサポート |
| メディア検出 | 手動パス設定 | 自動 mediaDiscovery ルール |
| 計算変数 | なし | `filename`、`filepath` を自動注入 |
| マルチディスクゲーム | サポートなし | 自動展開 |

### 10.2 サポートされる ScreenScraper メディアタイプの総数

- **合計**: 50 の公式メディアタイプ
- **画像**: 40 タイプ
- **動画**: 8 タイプ
- **ドキュメント**: 1 タイプ（manuel）
- **テーマ**: 2 タイプ（themehb, themehs）

### 10.3 バージョン情報

- **ドキュメントバージョン**: 3.0
- **最終更新日**: 2026-09-13
- **対象システム**: Frontend Killer v1.0.5+
