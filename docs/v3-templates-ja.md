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
| `before` | `before(str, sep)` | 区切り文字より前の部分を返す。区切り文字がない場合は元の文字列を返す | `before(path, "#")` |
| `after` | `after(str, sep)` | 区切り文字より後の部分を返す。区切り文字がない場合は元の文字列を返す | `after(path, "#")` |
| `concat` | `concat(f1, f2, ...)` | すべての引数を結合（空値はスキップ） | `concat(cdn, '/', filename)` |
| `sanitize` | `sanitize(str, charset)` | charset に含まれる各文字を `_` に置換 | `sanitize(name, "&*/:\\<>?\|")` |
| `matches` | `matches(str, regex)` | 正規表現の完全一致、`true`/`false` を返す | `matches(crc32, '^[0-9A-Fa-f]{8}\\|crc$')` |

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
| `map` | `map(value[, category])` | 方言マッピング：ヒットすれば変換語を返す（複数値入力は区切り `-` で接続しつつ各セグメントを変換）、ヒットしなければ元の値を返す | `map(genre, "genre")` |
| `mapFirst` | `mapFirst(value[, category])` | 方言マッピングの最初の変換結果のみを返す（ディレクトリ名などの単一値向け） | `mapFirst(genre)` |

`map()` / `mapFirst()` はシステムの**方言マッピング表**（term_mapping、システム設定で管理）に基づく決定的な翻訳です。例：スクレイピングで取得した英語のジャンルを中国語に統一する（`Beat-'Em-Up` → `清版游戏`）。

- `map(value)`：全マッピングカテゴリを検索（`system_alias` カテゴリはプラットフォームとシステムのマッチング内部用のため対象外）。同じ語が複数カテゴリにヒットした場合はカテゴリ名の辞書順で最初のものを使用し、結果が常に決定的になる
- `map(value, category)`：指定カテゴリのみを検索
- 検索前に元の語を正規化（小文字化、スペース・句読点の除去）するため、`Beat'em Up`、`beat-em-up`、`BEAT EM UP` は同一の語として扱われる
- ヒットしなかった場合は入力値をそのまま返し、エラーにはならない

#### 2.2.6 共通構文の説明

- **関数名は大文字小文字を区別しない**：`mapFirst(...)` と `mapfirst(...)` は等価
- **文字列リテラル**：ダブルクォート `"..."` とシングルクォート `'...'` の両方をサポート。引用内のカンマ・括弧はエスケープ不要
- **遅延評価**：`default`、`if`、`coalesce` は必要に応じて引数を評価。選択されなかった分岐は評価されず、無効な評価によるエラーを回避
- **未知の関数**：null を返し WARN ログを記録、エクスポートは中断しない

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
mapFirst(genre)

# 区切り文字処理（RetroArch プレイリストで多用）
before(path, '#')
ext(before(path, '#'))

# ファイル名サニタイズ（RetroArch サムネイル規則）
sanitize(name, "&*/:\\<>?|")

# 正規表現検証
matches(crc32, '^[0-9A-Fa-f]{8}\\|crc$')

# 複雑な式
(name or filename) + ".jpg"
coalesce(box-2D, screenshot, image)
concat('Named_Boxarts/', sanitize(name, '&*/:\\<>?|'), '.png')
```

---

## 3. 利用可能な変数

### 3.1 ゲームフィールド変数

すべてのゲームフィールドはフィールド名で直接参照できます。3つの命名規則がサポートされています：

#### 3.1.1 基本フィールド

| フィールド | 説明 | 例 |
|-----------|------|-----|
| `name` | ゲーム名 | "Super Mario Bros" |
| `translatedName` | 翻訳済みゲーム名（エクスポート前に選択言語で注入。`coalesce` と併用推奨） | "超级马里奥兄弟" |
| `desc` | ゲーム説明 | "A classic platform game..." |
| `translatedDesc` | 翻訳済みゲーム説明 | "经典平台游戏..." |
| `releasedate` | リリース日 | "1985-09-13" |
| `developer` | 開発元 | "Nintendo" |
| `publisher` | パブリッシャー | "Nintendo" |
| `genre` | ジャンル | "Platform" |
| `players` | プレイヤー数 | "1-2" |
| `rating` | 評価 | "0.85" |
| `path` | ゲームパス（マルチディスクゲームは先頭行） | "./roms/nes/supermario.nes" |
| `lang` | 言語 | "en" |
| `sort-by` | ソートフィールド | "Mario" |
| `gameId` | ゲーム ID | "12345" |
| `hash` | ファイルハッシュ | "abc123..." |
| `crc32` | CRC32 チェックサム | "12345678" |
| `md5` | MD5 チェックサム | "abcdef..." |
| `source` | データソース | "screenscraper" |
| `platformType` | プラットフォームタイプ | "console" |
| `multiFile` | マルチディスクゲームかどうか（ブール。式中では `"true"` または空文字列） | "true" |
| `multiFileContent` | マルチディスクのファイルリストテキスト（1行1ファイル。インポート時に multiFileDetection が書き込み） | "disc1.bin\ndisc2.bin" |

#### 3.1.2 計算フィールド（読み取り専用）

| フィールド | 説明 | 計算方法 |
|-----------|------|----------|
| `filename` | 拡張子なしファイル名 | `path` から抽出 |
| `releaseYear` | リリース年 | `releasedate` の先頭4文字 |
| `m3uPath` | マルチディスクゲームの m3u ファイル名（`multiFile=true` のときのみ非空） | `stem(path 先頭行) + ".m3u"`、不正文字は `_` に置換 |

`m3uPath` は `if` と組み合わせてマルチディスクのパス書き換えによく使われます：`if(multiFile, m3uPath, path)`（ES-DE / EmuELEC / RetroBat エクスポートテンプレートの書き方）。

#### 3.1.3 フィールドエイリアス

以下のエイリアスは標準フィールド名の代わりに使用できます：

| エイリアス | マップ先 |
|-----------|---------|
| `description` | `desc` |
| `file` | `path` |
| `region` | `lang`（注意：独立した「リージョン」フィールドはなく、region の値は `lang` から取得） |
| `category` | `genre` |
| `releaseDate` | `releasedate` |
| `manuel` | `manual` |

> マッチングは大文字小文字を無視し、`-`/`_` を正規化するため、`box2dfront`、`Box-2D`、`box_2d` などはすべて同じフィールドにヒットします。

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
| `platform.sortBy` | プラットフォームソート値（Pegasus の sort-by 行） | "064" |
| `platform.extensions` | プラットフォーム拡張子リスト（Pegasus の extensions 行） | "nes\|fds" |
| `platform.ignoreFiles` | 無視ファイルリスト（Pegasus の ignore-files 行） | "\*.bak" |

### 3.3 計算変数（computedVariables 宣言注入）

インポートテンプレートは `game.computedVariables` でカスタム計算変数を宣言できます（key=変数名、value=式）。インポート時に各ゲームごとに評価され、変数テーブルに注入されます：

```json
"computedVariables": {
  "filename": "stem(path)",
  "filepath": "replace(path or '', './', '')",
  "safeName": "sanitize(label, '&*/:\\<>?|')"
}
```

- 計算変数は**後続のフィールドマッピングと mediaDiscovery 規則**でも参照できます（RetroArch インポートテンプレートは `{playlistName}`/`{safeName}` でサムネイルパスを組み立て）
- テンプレートが `computedVariables` を宣言しない場合（旧テンプレート互換）、内蔵デフォルト注入にフォールバック：

| 変数 | 説明 | 計算方法 |
|------|------|----------|
| `filename` | 拡張子なしファイル名 | `path` から抽出 |
| `filepath` | 拡張子と `./` プレフィックスなしファイルパス | `path` から抽出 |

**例**:
- `path` = `./roms/nes/supermario.nes`
- `filename` = `supermario`
- `filepath` = `roms/nes/supermario`

エクスポート方向にも内蔵変数があります：`{outputPath}`（エクスポートパス）、`{gameName}`（ゲーム名、メディア target で使用）、`{filename}`、`{ext}`（ファイル名テンプレートで使用、バージョン置換時はバージョン拡張子）、およびトップレベル `variables` で宣言されたテンプレート変数（3.4 参照）。

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
    "format": "xml|json|text",
    "dataFile": "gamelist.xml",
    "delimiter": ":"
  },
  "parsing": {
    // パーサー動作パラメータ（省略可、5.2 参照）
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
      // マルチディスク検出（省略可、5.3 参照）
    },
    "computedVariables": {
      // 計算変数宣言（省略可、3.3 参照）
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
      // メディア検出設定（省略可、5.6 参照）
    }
  }
}
```

### 5.2 parsing 設定

`parsing` ブロックはパーサーの動作パラメータを定義し、同じエンジンで異なるフロントエンドのフォーマットを処理できるようにします。省略時のデフォルト値は後方互換を維持します。

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

| フィールド | 説明 |
|-----------|------|
| `delimiter` | 属性区切り文字（例：`":"`、`"="`） |
| `commentChars` | コメントマーカー。一致する行はスキップ |
| `entrySeparator` | エントリ分離モード。`"emptyLine"` は空行でエントリを分割 |
| `keyCase` | キーの大文字小文字戦略：`lower` / `upper` / `preserve` |
| `encoding` | ファイルエンコーディング（デフォルト UTF-8） |
| `multiLine.enabled` | インデントされた継続行をサポートするか（Pegasus の `files:` ブロックはこれに依存） |
| `multiLine.indentChars` | 継続行のインデント文字リスト |

**json 型**（RetroArch プレイリストなど）：

```json
"parsing": {
  "json": {
    "root": "items",
    "encoding": "UTF-8"
  }
}
```

| フィールド | 説明 |
|-----------|------|
| `root` | ゲームエントリ配列への JSON パス（ドット区切り）。デフォルト `"items"` |
| `encoding` | ファイルエンコーディング（デフォルト UTF-8） |

### 5.3 multiFileDetection（マルチディスク検出）

`multiFileDetection` はマルチディスク情報を Game レコードの `multiFile=true` + `multiFileContent` フィールドに書き込みます。トリガー後もゲームは**単一レコードのまま**です（複数の Game に展開されません）：

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

| フィールド | 値 | 説明 |
|-----------|-----|------|
| `source` | フィールド名 | 検出ソースフィールド（例：`path`、`files`） |
| `trigger` | `fieldExists` \| `endsWith` | トリガー条件：フィールド存在 / フィールド先頭行がパターンで終わる（大文字小文字を区別しない） |
| `pattern` | 例 `".m3u"` | `endsWith` のマッチパターン。デフォルト `".m3u"` |
| `content` | `fieldValue` \| `fileContent` | コンテンツソース：クリーニング済みフィールド値 / ディスクから読んだファイル内容（m3u） |
| `pathFrom` | `firstLine` | 省略可。`firstLine` 指定時、path はクリーニング済み先頭行になる |

**2 つの典型的モード**（内蔵インポートテンプレート間の違い）：

| モード | 設定 | テンプレート |
|--------|------|--------------|
| Pegasus マルチラインブロック | `source=files, trigger=fieldExists, content=fieldValue, pathFrom=firstLine` | pegasus-v3 |
| m3u ポインタ | `source=path, trigger=endsWith(.m3u), content=fileContent` | esde / emuelec / retrobat / skraper-es / webgamelistoper |

**クリーニングルール**（`content` を `multiFileContent` に書き込む前に適用）：
- `#` コメント行と空行を除去、各行をトリム
- 先頭の `./` または `.\` プレフィックスを除去
- **cue→bin 展開は行わない**：エントリはそのまま保持され、cue 付属ファイルの収集はエクスポート時のみ行う（6.7 roms.cue 参照）
- **ディレクトリ基準はプラットフォーム ROM ルートに統一**：`fileContent` モードではクリーニング済み行に m3u 自身のディレクトリプレフィックスは付かない。m3u がサブディレクトリにあり相対参照を使う場合、エクスポートはプラットフォームルート基準で解決するため、**サブディレクトリの相対参照は基準を失う**
- Pegasus の `files:` 継続行も同じルールでクリーニングされ `multiFileContent` に保存される

**マルチディスク展開（旧セマンティクス）**：`game.multiFile` ブロック（`enabled`/`field`/`separator`）は複数値フィールドを複数の Game レコードに分割します。`multiFileDetection` と相互排他です（検出が優先され単一レコードを維持）。新しいテンプレートは `multiFileDetection` のみを使用してください。

### 5.4 gameInfo フィールドマッピング

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

### 5.5 mediaInfo フィールドマッピング

`mediaInfo` はメディアファイルパスからシステムメディアフィールドへのマッピングを定義します：

```json
"mediaInfo": {
  "box-2D": ["boxart", "image"],
  "ss": ["screenshot"],
  "video": ["video"],
  "wheel": ["wheel", "marquee"]
}
```

### 5.6 mediaDiscovery 設定

`mediaDiscovery` はディスク上のメディアファイルを自動的に検索するために使用されます：

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

**設定説明**:
- `enabled`: メディア検出を有効にするかどうか
- `baseDir`: メディアファイルのベースディレクトリ（データファイルのディレクトリ基準。`"../thumbnails"` のような相対パスも可）
- `subDirPatterns`: サブディレクトリ命名戦略の**優先度付きリスト**（配列。順に試行。例：`["{filename}", "{name}"]` はまず ROM ファイル名、次に表示名を試す）
- `extensions`: 検索するファイル拡張子のリスト
- `rules`: メディアタイプごとの検索ルール。`{filename}`、`{ext}`、computedVariables で宣言した変数（例：`{safeName}`）をサポート

**変数置換**:
- `{filename}`: 拡張子なしのゲームファイル名
- `{name}`: ゲーム表示名
- `{ext}`: 現在検索中のファイル拡張子
- computedVariables で宣言した変数（RetroArch テンプレートの `{playlistName}`、`{safeName}` など）

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
    "format": "xml|json|text",
    "delimiter": ": ",
    "dataFile": "metadata.pegasus.txt"
  },
  "variables": [
    // 省略可：エクスポート前にダイアログで設定するテンプレート変数（3.4 参照）
  ],
  "system": {
    "header": [ /* データファイルのヘッダー行 */ ],
    "footer": [ /* データファイルのフッター行 */ ],
    "fields": { /* header/footer 行で使用するフィールドマッピング（省略可） */ }
  },
  "game": {
    "gameStartMarker": "game:",
    "entrySeparator": "\n\n",
    "gameInfo": { /* ゲームフィールド → データファイルフィールドのマッピング（値は式） */ },
    "mediaInfo": { /* データファイルのメディアタグ → ソースメディアフィールド（nomcourt） */ }
  },
  "output": {
    "exportOptions": { /* UI エクスポートオプション宣言 */ },
    "roms": { /* ROM ファイルコピー：enabled/directory/filename/m3u/cue */ },
    "media": { /* メディアファイルコピー：directory/rules */ },
    "dataFile": { /* データファイル：directory/filename/pathFormat など */ },
    "readme": { /* 省略可：使用説明 readme の生成 */ }
  }
}
```

### 6.2 output.exportOptions エクスポートオプション宣言

`exportOptions` はターゲットフロントエンドが対応するエクスポート範囲を宣言し、エクスポートフォームで利用可能なオプションとそのデフォルト値を制御します：

```json
"exportOptions": {
  "gameFiles": true,
  "mediaFiles": true,
  "gameListFile": true,
  "showWarning": false
}
```

| フィールド | 説明 |
|-----------|------|
| `gameFiles` | ROM ファイルのエクスポートを許可するか（roms ブロックの有効化を制御） |
| `mediaFiles` | メディアファイルのエクスポートを許可するか（media ブロックの有効化を制御） |
| `gameListFile` | データファイルの生成を許可するか（dataFile ブロックの有効化を制御） |
| `showWarning` | UI に制限警告を表示するか（例：「メタデータとメディアのみで、ROM はエクスポートされません」） |

エクスポートフォームでユーザーが実際にチェックした項目が、実行時にどの出力ブロックが有効になるかを決定します。

### 6.3 system.header / footer

`header` / `footer` はデータファイルのヘッダー行とフッター行を定義します：

```json
"header": [
  "collection: {platform.name}",
  "sort-by: {platform.sortBy}",
  "{platform.launch}",
  ""
]
```

**説明**:
- 各行は文字列。`{platform.xxx}` プレースホルダと式をサポート
- 空文字列は空行
- `fields` は省略可。header/footer 行で使用するフィールドマッピングを宣言

### 6.4 game.gameInfo / game.mediaInfo

**gameInfo**（キー = データファイルのフィールド/タグ、値 = 式）：

```json
"gameInfo": {
  "game": "coalesce(name, translatedName)",
  "file": "if(multiFile, '', path)",
  "files": "multiFileContent",
  "path": "if(multiFile, m3uPath, path)",
  "crc32": "if(crc32, concat(crc32, '|crc'), 'DETECT')"
}
```

- 値は完全な式構文（第 2 節）をサポート
- 式が `path` または `m3uPath` を参照する場合、結果は `output.dataFile.pathFormat` に従って自動フォーマットされる（6.9 参照）
- 空の値は出力されない
- **マルチディスク出力**：値が複数行テキストの場合（例：Pegasus の `files: multiFileContent`）、「先頭行は key+区切り文字、後続行は 2 スペース インデント」で出力される —— すなわち Pegasus の `files:` マルチラインブロック

**mediaInfo**（キー = データファイルのメディアタグ、値 = ソースメディアの nomcourt）：

```json
"mediaInfo": {
  "image": "box-2D",
  "assets.video": "video",
  "assets.boxFront": "box-2D"
}
```

- メディアパスは `output.media.rules` の対応する `dataFileTag` で計算されたエクスポート後のパスを**優先**する（6.8 参照）。上書きがない場合はデータベースの元のメディアパスを使用
- rules に `dataFileTag` のないメディアタイプはコピーのみで、データファイルには書き込まれない
- rules のキーは 54 の ScreenScraper nomcourt 値に限られない。旧フィールド名（例：`image`、`banner`、`manual`）も動作する —— エンジンは同じ方法で Game フィールドにアクセスする

### 6.5 output.roms ROM ファイルコピー

`output.roms` は ROM ファイルのコピー動作を定義します：

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

| フィールド | 説明 |
|-----------|------|
| `enabled` | ROM コピーを実行するか。**false の場合、ROM コピー段階全体（m3u 生成と cue 付属ファイル収集を含む）がスキップされ、m3u/cue 設定は無意味になる** |
| `directory` | ターゲットディレクトリテンプレート。ゲームごとの式をサポート（例：`if(genre, concat('{outputPath}/', platform.system, '/', mapFirst(genre)), '{outputPath}/{platform.system}')` でジャンル別にグループ化） |
| `filename` | ターゲットファイル名テンプレート。ファイル名レベルまで。デフォルト `{filename}{ext}` は元の名前を保持。sanitize/concat などの式をサポート |

> ディレクトリ全体コピーモード（エクスポートフォームで「ディレクトリ全体をコピー」を選択）では、ゲームごとの付属ファイル解決はスキップされ、プラットフォームのソース ROM ディレクトリが `roms.directory` に丸ごとコピーされます。m3u/cue のゲームごとのロジックは関与しません。

### 6.6 output.roms.m3u プレイリスト処理

`roms.m3u` はエクスポート時の M3U 処理を制御します。デフォルト `enabled=false`。**2 つの異なる m3u パス**が関係します：

**パス 1：multiFile ゲームの m3u 再生成**
- マルチディスクゲーム（`multiFile=true`、内容はインポート時に保存された `multiFileContent`）のディスクコピー後、ROM ディレクトリに `{filename}.m3u` が生成される
- 内容 = `multiFileContent` のクリーニング済み行（コメント/空行/`./` プレフィックスを除去、区切り文字は `/` に正規化）

**パス 2：path が .m3u を指す旧データの解析リライト**
- ゲームの `path` が `.m3u` ファイルを指す場合（インポート時に multiFile と解決、または m3u ファイルとして存在）、m3u が参照するすべての付属ファイルがコピーされ、**m3u 内容がリライトされる**
- リライトルール：各行は m3u 自身のディレクトリからの相対パスになり、区切り文字は `/` に正規化。欠落エントリ行はそのまま保持
- `m3u.target`：省略可。参照ファイルの統一ターゲットディレクトリテンプレートを指定（デフォルト：m3u 自身が配置されるターゲットディレクトリ）
- m3u エントリが `.cue` の場合、cue ルールに従って付属ファイルを収集（6.7 参照）。`.m3u` の場合はさらに展開

**重要なセマンティクス**：
- m3u 内容は**インポート時に保存されたスナップショット**に基づく（`multiFileContent` またはデータベースに記録されたパス）。インポート後に手動編集した m3u ファイルはエクスポートに**反映されない**
- `roms.enabled=false` の場合、ROM コピー段階全体がスキップされ、m3u 設定は適用されない
- `filename` テンプレート変数はマルチディスクシナリオでは path 先頭行の stem になる

### 6.7 output.roms.cue CUE/BIN 付属ファイル収集

`roms.cue` は CUE ディスクイメージの付属ファイル（`.bin`/`.wav` など）の自動収集を制御します。v3 エクスポートのデフォルト動作です：

```json
"cue": { "enabled": true, "onMissing": "warn" }
```

| フィールド | デフォルト | 説明 |
|-----------|-----------|------|
| `enabled` | `true` | **cue セクションのないテンプレートは enabled=true として扱われ**、後方互換を維持。false の場合、cue ファイル内の FILE 参照は解析されない |
| `onMissing` | `warn` | 参照ファイルの欠落処理：`warn`（タスクログ + 完了結果のサマリーリスト）/ `skip`（静默、デバッグログのみ）/ `fail`（タスクログに記録後、**そのゲームの**コピーを中断、バッチ全体は中断しない）。未知の値は warn にフォールバック |

**トリガーシナリオ**（cue.enabled=true の場合）：
1. 単一ファイルゲームのエクスポートターゲットが `.cue` で終わる → cue 内の FILE 参照を解析し付属ファイルを収集
2. multiFile ゲームのディスクコピー時、`multiFileContent` エントリが `.cue` → その FILE 参照を収集
3. m3u 解析リライト中、m3u エントリが `.cue` → その FILE 参照を収集

**再帰展開**：
- cue 内の `FILE` 行を順に解析：引用符付き（引用内にスペース可）と引用符なしの両形式をサポート。空行、`;`/`#` コメント、BOM はスキップ
- **cue 内のパスは cue 自身のディレクトリを基準に解決**。参照ファイルは cue ターゲットの隣に相対構造を保持して配置される（先頭 `../` やドライブレターを跨ぐパスはフラットなファイル名に劣化）
- **マルチトラックの bin はすべて収集**：各 FILE 行が 1 つの付属ファイルに対応し、すべて収集してコピー
- cue が cue を参照 → さらに展開。エントリが `.m3u` → そのエントリを順に展開
- ネスト展開は**深さ 16** で上限（m3u→cue→m3u チェーン）、visited セットで無限再帰を防止
- 保留中のすべてのファイルはソース絶対パスで重複排除され、一度にコピー

**欠落ファイル処理の詳細**：
- `warn`：欠落ファイルはタスクログに記録され、エクスポート完了結果に「CUE 欠落 N 件」としてサマリーされる（**最大 20 件**、超過分は … で切り捨て）
- `fail`：現在のゲームのコピーのみ中断（ゲームごとの例外ハンドラーで捕捉）。後続のゲームは継続
- multiFile エントリ / m3u エントリ自体の欠落も `onMissing` に従って処理される（cue.enabled=false の場合でも）

### 6.8 output.media メディアファイルコピー

`output.media` はメディアファイルのコピーを定義します：

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

| フィールド | 説明 |
|-----------|------|
| `directory` | メディアルートディレクトリテンプレート（`{outputPath}`、`{platform.xxx}`、`{gameName}` などをサポート） |
| `rules` | メディアタイプごとの出力ルール。キー = ソース nomcourt |
| `rules.*.source` | ソースメディアフィールド（キーと同じ。可読性のため保持） |
| `rules.*.target` | ターゲットパステンプレート（media.directory からの相対）。`{filename}`、`{gameName}` 変数と式（`sanitize`、`concat` など）をサポート |
| `rules.*.dataFileTag` | データファイル内の対応タグ。**設定した場合、データファイル内のそのメディアパスはエクスポート後のパスにリライトされる**（データファイルディレクトリからの相対で再計算）。未設定の場合はリライトなしでコピーのみ |

データファイル内のメディアパス = media.directory + target の完全パスを、データファイルディレクトリからの相対に変換し、`pathFormat` に従ってフォーマットしたもの（6.9 参照）。

### 6.9 output.dataFile データファイル

`output.dataFile` はデータファイルの出力を定義します：

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

| フィールド | 説明 |
|-----------|------|
| `directory` / `filename` | データファイルのディレクトリとファイル名テンプレート（例：`{platform.name}.lpl`） |
| `pathFormat` | パスフォーマット。下記参照 |
| `pathPrefix` | 予約フィールド（現在のエンジンは読まない。プレフィックス処理は pathFormat が担当） |
| `jsonItemsKey` | JSON 型：ゲームエントリ配列のキー。デフォルト `"items"` |
| `jsonTopLevelFields` | JSON 型：エントリ配列の前に出力するトップレベルフィールド（順序保持）。各項目は `key`/`value`/省略可 `type`（`string`/`number`/`boolean`/`null`、デフォルトは自動推論。`"1.5"` が数値と誤認されるのを防止） |

**pathFormat の正確なセマンティクス**（`formatExportPath` はプレフィックスの追加/除去のみを行い、**真の relativize は行わない**）：

| 値 | 動作 |
|-----|------|
| `relative` | 先頭の `./` プレフィックスを除去（デフォルト動作。非相対値にも適用） |
| `absoluteWithDot` | パスに `./` プレフィックスを付与（Windows ドライブレターパスと `/` ルートの絶対パスを除く） |
| `absolute` / その他 | relative と同じ：`./` プレフィックスの除去のみ |

- **パス型フィールド**（`path`/`m3uPath` を参照する式）：上記のプレフィックス追加/除去のみで、relativize なし —— 書かれたパスがそのまま出力される（pathFormat のプレフィックス処理付き）
- **メディアパス**：まずデータファイルディレクトリ → メディアファイル完全パスで真に相対化（ドライブレター跨ぎは文字列計算にフォールバック）、その後 `pathFormat` に従ってプレフィックス処理

### 6.10 output.readme 使用説明 readme の生成

`output.readme` は省略可。エクスポート後、エクスポートディレクトリに使用説明ファイルを生成します：

```json
"readme": {
  "enabled": true,
  "directory": "{outputPath}",
  "filename": "使用説明-{platform.name}.md",
  "content": [
    "# {platform.name} プレイリストエクスポートに関する説明",
    "..."
  ]
}
```

- `content` は行ごとに出力。`{platform.xxx}`、`{outputPath}` などのプレースホルダ置換をサポート
- 参照テンプレート：`rules/export/retroarch-v3.json`、`rules/export/retroarch-folder-v3.json`

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

### 7.2 Pegasus インポートテンプレートの例（multiFileDetection と mediaDiscovery 付き）

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

要点（この例は `rules/export/esde-v3.json` と完全に一致）：

- **gameFiles: false + roms.enabled: false**：メタデータとメディアのみ。ROM コピー段階全体（m3u 生成と cue 収集を含む）がスキップされ、roms 設定は無効
- **path 式 `if(multiFile, m3uPath, path)`**：マルチディスクゲームは生成された .m3u を指し、単一ファイルゲームは元のパスを出力
- **name/desc は coalesce で翻訳名フォールバック**：翻訳名（translatedName/translatedDesc）を優先
- **media.rules.dataFileTag**：メディアコピー後、データファイル内の対応タグ（例：`<image>`）はエクスポート後の場所にリライトされる（先に相対化、その後 pathFormat でプレフィックス処理）
- **pathFormat: "relative"**：パス型フィールド（path/m3uPath）は `./` プレフィックス処理のみで、真の相対化なし。メディアパスは先にデータファイルディレクトリ基準で相対化してからプレフィックス処理
- **pathPrefix: "./"**：予約フィールド、現バージョンでは未使用

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

要点（この例は `rules/export/pegasus-v3.json` と完全に一致）：

- **file 式 `if(multiFile, '', path)`**：マルチディスクゲームでは file を空にする（ディスクパスは files リストで表現し、誤った先頭ファイルパスを避ける）
- **files 式 `multiFileContent`**：マルチラインブロックを出力（先頭行 `files:`、後続行は 2 スペース インデント）、text の multiLine インデントルールで解析される
- **roms.m3u.enabled: false**：Pegasus は files マルチラインリストを使用するため .m3u 再生成は不要。roms.cue は有効のまま（multiFileContent エントリ内の .cue 参照は収集される）
- **roms.directory はジャンル別グループ化**：`mapFirst(genre)` が最初のジャンルをディレクトリ名として取得し、ない場合は `{outputPath}/{platform.system}` にフォールバック
- **media.target は {gameName} を使用**：ゲームごとに 1 ディレクトリ（例：`media/Super Mario/boxFront.png`）。dataFileTag は `assets.*` ネストタグにマッピング
- **pathFormat: "relative" + pathPrefix: ""**：パス型フィールドは `./` プレフィックスの除去のみ

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

### 8.4 ROM とプレイリストのエクスポートに関する推奨

- マルチディスクゲームはインポート時の multiFileDetection 認識に依存：テンプレートが設定していないか未トリガーの場合、ゲームは multiFile とマークされず、エクスポート時に m3u も cue も関与しない
- CUE/BIN セットは `onMissing: "warn"` を維持：欠落ファイルは中断なしでサマリーされ、後でファイルを再追加しやすい。厳格な検証が必要な場合のみ `fail` を使用
- .m3u を生成するかはターゲットフロントエンド次第：ES-DE/RetroBat/EmuELEC は必要とするため有効化。Pegasus は files マルチラインリストを使用するためデフォルトで無効
- m3u 内容はインポート時に保存されたスナップショット由来。インポート後にソース m3u を手動編集してもエクスポートには反映されない

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

### 9.4 CUE/BIN 欠落ファイルの通知

**問題**: マルチディスクエクスポート中にログに「欠落ファイル」が表示される、または完了結果に欠落ファイルがリストされる

**説明**:
- `roms.cue.onMissing` が欠落ファイルの動作を制御：`warn`（デフォルト。ログに記録され完了結果にサマリー）/ `skip`（静默）/ `fail`（そのゲームのコピーのみ中断、他のゲームに影響なし）
- 完了結果は最大 20 件の欠落エントリをリストし、超過分は `…` で切り捨て
- `cue.enabled: false` は cue ファイル内の FILE 参照の解析をスキップするだけ。m3u/multiFile エントリの欠落は onMissing に従って処理される

### 9.5 マルチディスクエクスポート後に .m3u が生成されない

**解決策**:
1. ゲームがインポート時にマルチディスクと認識されたか確認（multiFileDetection がトリガー、multiFile=true）
2. `output.roms.enabled: true` を確認（false の場合 ROM コピー段階全体がスキップされ、m3u/cue 設定は適用されない）
3. `output.roms.m3u.enabled: true` を確認
4. Pegasus テンプレートはデフォルトで m3u を無効化（files マルチラインリストで十分）。これは期待された動作

---

## 10. 付録

### 10.1 v2 と v3 の主な違い

| 機能 | v2 | v3 |
|------|----|----|
| フィールド命名 | javaField を使用（例: `box2d`） | nomcourt / dbColumn / javaField をサポート |
| 式 | 単純な変数置換 | 関数、演算子、条件をサポート |
| メディア検出 | 手動パス設定 | 自動 mediaDiscovery ルール |
| 計算変数 | なし | テンプレートの `computedVariables` で宣言（旧テンプレートは内蔵 `filename`/`filepath` にフォールバック） |
| マルチディスクゲーム | サポートなし | multiFileDetection が単一レコードを維持（内容は `multiFileContent` に保存）、分割なし |

### 10.2 サポートされる ScreenScraper メディアタイプの総数

- **合計**: 54 の公式メディアタイプ
- **画像**: 43 タイプ
- **動画**: 8 タイプ
- **ドキュメント**: 1 タイプ（manuel）
- **テーマ**: 2 タイプ（themehb, themehs）

### 10.3 バージョン情報

- **ドキュメントバージョン**: 3.1
- **最終更新日**: 2026-10-27
- **対象システム**: Frontend Killer v1.0.5+
