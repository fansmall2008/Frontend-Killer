# メディアファイルマッピングドキュメント

---

## 1. インポートテンプレートとデータベースフィールドの対応関係

### 1.1 基本フィールドマッピング

| XMLフィールド | データベースフィールド | 説明 | サポートテンプレート |
|--------------|----------------------|------|---------------------|
| name | name | ゲーム名 | webgamelistoper, emuelec |
| desc / description | description | ゲーム説明 | webgamelistoper, emuelec |
| releasedate / release | releaseDate | リリース日 | webgamelistoper, emuelec |
| developer / dev | developer | 開発元 | webgamelistoper, emuelec |
| publisher / pub | publisher | 発売元 | webgamelistoper, emuelec |
| genre / category | genre | ジャンル | webgamelistoper, emuelec |
| players / player | players | プレイ人数 | webgamelistoper, emuelec |
| rating | rating | レーティング | webgamelistoper, emuelec |
| hash / crc / crc32 | hash | ハッシュ値(CRC32) | webgamelistoper |
| path / file | files | ゲームファイルパス | webgamelistoper, emuelec |
| image | image | ゲームアイコン | webgamelistoper, emuelec |
| video | video | 動画パス | webgamelistoper, emuelec |
| thumbnail | thumbnail | サムネイル | webgamelistoper, emuelec |
| marquee | marquee | マーキー画像 | webgamelistoper, emuelec |

### 1.2 メディアファイルパスマッピング（webgamelistoperテンプレート）

| テンプレートメディアタイプ | データベースフィールド | ファイルパスパターン |
|---------------------------|----------------------|---------------------|
| image | image | `data/scraper/games/{platform}/{gameId}/{region}/image.png` |
| wheel | wheel | `data/scraper/games/{platform}/{gameId}/{region}/wheel.png` |
| wheelcarbon | wheelcarbon | `data/scraper/games/{platform}/{gameId}/{region}/wheelcarbon.png` |
| wheelsteel | wheelsteel | `data/scraper/games/{platform}/{gameId}/{region}/wheelsteel.png` |
| logo | logo | `data/scraper/games/{platform}/{gameId}/{region}/logo.png` |
| thumbnail | thumbnail | `data/scraper/games/{platform}/{gameId}/{region}/thumbnail.png` |
| boxFront | boxFront | `data/scraper/games/{platform}/{gameId}/{region}/box-2D.png` |
| boxBack | boxBack | `data/scraper/games/{platform}/{gameId}/{region}/box-2D-back.png` |
| box3d | box3d | `data/scraper/games/{platform}/{gameId}/{region}/box-3D.png` |
| boxside | boxside | `data/scraper/games/{platform}/{gameId}/{region}/box-side.png` |
| banner | banner | `data/scraper/games/{platform}/{gameId}/{region}/banner.png` |
| titlescreen | thumbnail | `data/scraper/games/{platform}/{gameId}/{region}/ss.png` |
| screenshot | screenshot | `data/scraper/games/{platform}/{gameId}/{region}/screenshot.png` |
| fanart | fanart | `data/scraper/games/{platform}/{gameId}/{region}/fanart.png` |
| poster | poster | `data/scraper/games/{platform}/{gameId}/{region}/poster.png` |
| cartridge | cartridge | `data/scraper/games/{platform}/{gameId}/{region}/cartridge.png` |
| bezel | bezel | `data/scraper/games/{platform}/{gameId}/{region}/bezel.png` |
| panel | panel | `data/scraper/games/{platform}/{gameId}/{region}/panel.png` |
| background | background | `data/scraper/games/{platform}/{gameId}/{region}/background.png` |
| steam | steam | `data/scraper/games/{platform}/{gameId}/{region}/steam.png` |
| steamgrid | steamgrid | `data/scraper/games/{platform}/{gameId}/{region}/steamgrid.png` |
| manual | manual | `data/scraper/games/{platform}/{gameId}/{region}/manual.pdf` |
| music | music | `data/scraper/games/{platform}/{gameId}/{region}/music.mp3` |
| pictocouleur | pictocouleur | `data/scraper/games/{platform}/{gameId}/{region}/pictocouleur.png` |
| supporttexture | supporttexture | `data/scraper/games/{platform}/{gameId}/{region}/supporttexture.png` |
| boxtexture | boxtexture | `data/scraper/games/{platform}/{gameId}/{region}/boxtexture.png` |
| screenmarqueesmall | screenmarqueesmall | `data/scraper/games/{platform}/{gameId}/{region}/screenmarqueesmall.png` |
| figurine | figurine | `data/scraper/games/{platform}/{gameId}/{region}/figurine.png` |
| videonormalized | videonormalized | `data/scraper/games/{platform}/{gameId}/{region}/videonormalized.mp4` |
| cabinetLeft | cabinetLeft | `data/scraper/games/{platform}/{gameId}/{region}/cabinet-left.png` |
| cabinetRight | cabinetRight | `data/scraper/games/{platform}/{gameId}/{region}/cabinet-right.png` |
| spine | spine | `data/scraper/games/{platform}/{gameId}/{region}/spine.png` |
| boxFull | boxFull | `data/scraper/games/{platform}/{gameId}/{region}/box-full.png` |
| pictoliste | pictoliste | `data/scraper/games/{platform}/{gameId}/{region}/pictoliste.png` |
| pictomonochrome | pictomonochrome | `data/scraper/games/{platform}/{gameId}/{region}/pictomonochrome.png` |
| pictomonochromesvg | pictomonochromesvg | `data/scraper/games/{platform}/{gameId}/{region}/pictomonochromesvg.svg` |
| wallpaper | wallpaper | `data/scraper/games/{platform}/{gameId}/{region}/wallpaper.png` |

### 1.3 メディアファイルパスマッピング（emuelecテンプレート）

| テンプレートメディアタイプ | データベースフィールド | ファイルパスパターン |
|---------------------------|----------------------|---------------------|
| box2dfront | boxFront | `downloaded_images/{platform}/box2dfront/{filepath}.{ext}` |
| box2dback | boxBack | `downloaded_images/{platform}/box2dback/{filepath}.{ext}` |
| box2dside | boxside | `downloaded_images/{platform}/box2dside/{filepath}.{ext}` |
| box3d | box3d | `downloaded_images/{platform}/box3d/{filepath}.{ext}` |
| fanart | fanart | `downloaded_images/{platform}/fanart/{filepath}.{ext}` |
| screenshot | screenshot | `downloaded_images/{platform}/screenshot/{filepath}.{ext}` |
| video | video | `downloaded_images/{platform}/video/{filepath}.{ext}` |
| wheel | wheel | `downloaded_images/{platform}/wheel/{filepath}.{ext}` |
| banner | banner | `downloaded_images/{platform}/banner/{filepath}.{ext}` |
| manual | manual | `downloaded_images/{platform}/manuals/{filepath}.{ext}` |
| images | image | `downloaded_images/{platform}/images/{filepath}.{ext}` |
| titlescreen | thumbnail | `downloaded_images/{platform}/titlescreen/{filepath}.{ext}` |
| marquee | marquee | `downloaded_images/{platform}/marquee/{filepath}.{ext}` |
| miximage | screenshot | `downloaded_images/{platform}/miximage/{filepath}.{ext}` |
| cartridge | cartridge | `downloaded_images/{platform}/cartridge/{filepath}.{ext}` |
| bezel | bezel | `downloaded_images/{platform}/bezel/{filepath}.{ext}` |

---

## 2. スクレイピングメディアタイプとデータベースフィールドの対応関係

### 2.1 スクレイピングメディアタイプマッピング表

| スクレイピングメディアタイプ | データベースフィールド | 説明 |
|-----------------------------|----------------------|------|
| image | image | ゲームアイコン |
| box-2d, box2d, box-front, support-2d, boxvierge | boxFront | 2Dボックス正面 |
| box-2d-back, boxback | boxBack | 2Dボックス背面 |
| box-2d-side, boxspine | boxSpine | 2Dボックス側面/背表紙 |
| box-3d, support-3d | boxFull | 3Dボックス |
| wheel | wheel | ホイールアイコン |
| wheel-carbon | wheelcarbon | カーボンホイール |
| wheel-steel | wheelsteel | スチールホイール |
| logo | logo | ロゴ |
| thumbnail, icon | thumbnail | サムネイル |
| sstitle, titlescreen, minicon | thumbnail | タイトル画面/ミニアイコン |
| ss, screenshot, ssmap | screenshot | スクリーンショット |
| marquee, arcademarquee, screenmarquee | marquee | マーキー |
| screenmarqueesmall | screenmarqueesmall | スモールマーキー |
| video, intro | video | 動画 |
| videonormalized | videonormalized | 正規化動画 |
| fanart, photo, illustration, controller | fanart | ファンアート/写真 |
| background, backgrounds | background | 背景画像 |
| banner | banner | バナー |
| poster, flyer, flyer-2d | poster | ポスター/フライヤー |
| bezel, bezel43, bezel169 | bezel | ベゼル |
| panel | panel | パネル |
| cartridge | cartridge | カートリッジ |
| manual, manuel | manual | マニュアル |
| music | music | 音楽 |
| steam | steam | Steamアイコン |
| steamgrid | steamgrid | Steamグリッド |
| boxtexture | boxtexture | ボックステクスチャ |
| supporttexture | supporttexture | サポートテクスチャ |
| figurine | figurine | フィギュア |
| pictocouleur | pictocouleur | カラーアイコン |
| pictoliste | pictoliste | リストアイコン |
| pictomonochrome | pictomonochrome | モノクロアイコン |
| pictomonochromesvg | pictomonochromesvg | モノクロSVGアイコン |
| wallpaper | wallpaper | 壁紙 |
| cabinet-left | cabinetLeft | キャビネット左側 |
| cabinet-right | cabinetRight | キャビネット右側 |
| spine | spine | 背表紙 |
| box-full | boxFull | フルボックス |

### 2.2 多対1マッピングの概要

| データベースフィールド | マップされたタイプの数 | 含まれるメディアタイプ |
|----------------------|------------------------|----------------------|
| boxFront | 5 | box-2d, box2d, box-front, support-2d, boxvierge |
| thumbnail | 3 | sstitle, titlescreen, minicon |
| screenshot | 3 | ss, screenshot, ssmap |
| marquee | 3 | marquee, arcademarquee, screenmarquee |
| video | 2 | video, intro |
| fanart | 4 | fanart, photo, illustration, controller |
| background | 2 | background, backgrounds |
| poster | 3 | poster, flyer, flyer-2d |
| bezel | 3 | bezel, bezel43, bezel169 |
| manual | 2 | manuel, manual |

---

## 3. データベース Game テーブルフィールドリスト

### 3.1 基本情報フィールド

| フィールド名 | タイプ | 説明 |
|-------------|--------|------|
| id | Long | 主キーID |
| name | String | ゲーム名 |
| description | String | ゲーム説明 |
| releaseDate | String | リリース日 |
| developer | String | 開発元 |
| publisher | String | 発売元 |
| genre | String | ジャンル |
| players | String | プレイ人数 |
| rating | Double | レーティング |
| hash | String | ハッシュ値(CRC32) |
| files | String | ゲームファイルパス |

### 3.2 メディアファイルフィールド（計40個）

| フィールド名 | タイプ | 説明 |
|-------------|--------|------|
| image | String | ゲームアイコン |
| video | String | 動画 |
| marquee | String | マーキー |
| thumbnail | String | サムネイル |
| wheel | String | ホイールアイコン |
| manual | String | マニュアル |
| boxFront | String | 2Dボックス正面 |
| boxBack | String | 2Dボックス背面 |
| boxSpine | String | 背表紙 |
| boxFull | String | 3Dボックス |
| cartridge | String | カートリッジ |
| logo | String | ロゴ |
| bezel | String | ベゼル |
| panel | String | パネル |
| cabinetLeft | String | キャビネット左側 |
| cabinetRight | String | キャビネット右側 |
| tile | String | タイル |
| banner | String | バナー |
| steam | String | Steamアイコン |
| poster | String | ポスター |
| background | String | 背景画像 |
| music | String | 音楽 |
| screenshot | String | スクリーンショット |
| titlescreen | String | タイトル画面 |
| box3d | String | 3Dボックス |
| steamgrid | String | Steamグリッド |
| fanart | String | ファンアート |
| boxtexture | String | ボックステクスチャ |
| supporttexture | String | サポートテクスチャ |
| videonormalized | String | 正規化動画 |
| wheelcarbon | String | カーボンホイール |
| wheelsteel | String | スチールホイール |
| screenmarqueesmall | String | スモールマーキー |
| boxside | String | ボックス側面 |
| figurine | String | フィギュア |
| pictoliste | String | リストアイコン |
| pictomonochrome | String | モノクロアイコン |
| pictomonochromesvg | String | モノクロSVGアイコン |
| pictocouleur | String | カラーアイコン |
| wallpaper | String | 壁紙 |

---

## 4. マッピングの整合性に関する注意事項

### 4.1 インポートとスクレイピングの統一マッピング

**インポートテンプレート**または**オンラインスクレイピング**を介してメディアファイルを取得する場合でも、最終的には同じデータベースフィールドにマップされます。

### 4.2 マッピング設計の原則

1. **互換性優先**: 複数のフロントエンドフォーマット（EmuELEC、ESDE、Pegasusなど）をサポート
2. **タイプ統合**: 同義または類似のメディアタイプを同じフィールドに統合
3. **拡張性**: 将来の拡張のためによく使用されるフィールドを予約
4. **命名標準化**: データベースフィールドはキャメルケースを使用し、Javaエンティティと一致

---

## 5. サポートされるファイル拡張子

### 5.1 画像フォーマット

| フォーマット | 拡張子 |
|-------------|--------|
| PNG | .png |
| JPEG | .jpg, .jpeg |
| GIF | .gif |
| WebP | .webp |
| SVG | .svg |

### 5.2 動画フォーマット

| フォーマット | 拡張子 |
|-------------|--------|
| MP4 | .mp4 |
| MKV | .mkv |
| AVI | .avi |
| WMV | .wmv |
| WebM | .webm |

### 5.3 音声フォーマット

| フォーマット | 拡張子 |
|-------------|--------|
| MP3 | .mp3 |
| OGG | .ogg |
| WAV | .wav |

### 5.4 ドキュメントフォーマット

| フォーマット | 拡張子 |
|-------------|--------|
| PDF | .pdf |

---

**ドキュメントバージョン**: v1.0  
**生成日**: 2026-05-28  
**適用バージョン**: WebGamelistOper v1.0+