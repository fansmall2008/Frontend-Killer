# 媒体文件映射关系文档

---

## 一、导入模板与数据库字段对应关系

### 1.1 基础字段映射

| XML字段 | 数据库字段 | 说明 | 支持模板 |
|---------|-----------|------|---------|
| name | name | 游戏名称 | webgamelistoper, emuelec |
| desc / description | description | 游戏描述 | webgamelistoper, emuelec |
| releasedate / release | releaseDate | 发布日期 | webgamelistoper, emuelec |
| developer / dev | developer | 开发商 | webgamelistoper, emuelec |
| publisher / pub | publisher | 发行商 | webgamelistoper, emuelec |
| genre / category | genre | 游戏类型 | webgamelistoper, emuelec |
| players / player | players | 玩家数量 | webgamelistoper, emuelec |
| rating | rating | 评分 | webgamelistoper, emuelec |
| hash / crc / crc32 | hash | 哈希值(CRC32) | webgamelistoper |
| path / file | files | 游戏文件路径 | webgamelistoper, emuelec |
| image | image | 游戏图标 | webgamelistoper, emuelec |
| video | video | 视频路径 | webgamelistoper, emuelec |
| thumbnail | thumbnail | 缩略图 | webgamelistoper, emuelec |
| marquee | marquee | 跑马灯图 | webgamelistoper, emuelec |

### 1.2 媒体文件路径映射（webgamelistoper模板）

| 模板媒体类型 | 数据库字段 | 文件路径规则 |
|-------------|-----------|-------------|
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

### 1.3 媒体文件路径映射（emuelec模板）

| 模板媒体类型 | 数据库字段 | 文件路径规则 |
|-------------|-----------|-------------|
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

## 二、刮削媒体类型与数据库字段对应关系

### 2.1 刮削媒体类型映射表

| 刮削媒体类型 | 数据库字段 | 说明 |
|-------------|-----------|------|
| image | image | 游戏图标 |
| box-2d, box2d, box-front, support-2d, boxvierge | boxFront | 2D封面 |
| box-2d-back, boxback | boxBack | 2D封底 |
| box-2d-side, boxspine | boxSpine | 2D侧边/书脊 |
| box-3d, support-3d | boxFull | 3D封面 |
| wheel | wheel | 轮子图标 |
| wheel-carbon | wheelcarbon | 碳纹理轮子 |
| wheel-steel | wheelsteel | 钢纹理轮子 |
| logo | logo | Logo |
| thumbnail, icon | thumbnail | 缩略图 |
| sstitle, titlescreen, minicon | thumbnail | 标题画面/小图标 |
| ss, screenshot, ssmap | screenshot | 截图 |
| marquee, arcademarquee, screenmarquee | marquee | 跑马灯 |
| screenmarqueesmall | screenmarqueesmall | 小型跑马灯 |
| video, intro | video | 视频 |
| videonormalized | videonormalized | 标准化视频 |
| fanart, photo, illustration, controller | fanart | 粉丝艺术/照片 |
| background, backgrounds | background | 背景图 |
| banner | banner | 横幅图 |
| poster, flyer, flyer-2d | poster | 海报/传单 |
| bezel, bezel43, bezel169 | bezel | 边框 |
| panel | panel | 面板图 |
| cartridge | cartridge | 卡带图 |
| manual, manuel | manual | 手册 |
| music | music | 音乐 |
| steam | steam | Steam图标 |
| steamgrid | steamgrid | Steam网格图 |
| boxtexture | boxtexture | 盒子纹理 |
| supporttexture | supporttexture | 支架纹理 |
| figurine | figurine | 手办图 |
| pictocouleur | pictocouleur | 彩色图标 |
| pictoliste | pictoliste | 列表图标 |
| pictomonochrome | pictomonochrome | 单色图标 |
| pictomonochromesvg | pictomonochromesvg | 单色SVG图标 |
| wallpaper | wallpaper | 壁纸 |
| cabinet-left | cabinetLeft | 机柜左侧 |
| cabinet-right | cabinetRight | 机柜右侧 |
| spine | spine | 书脊 |
| box-full | boxFull | 完整盒子图 |

### 2.2 多对1映射汇总

| 数据库字段 | 映射的媒体类型数量 | 包含的媒体类型 |
|-----------|-------------------|--------------|
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

## 三、数据库 Game 表字段清单

### 3.1 基础信息字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | Long | 主键ID |
| name | String | 游戏名称 |
| description | String | 游戏描述 |
| releaseDate | String | 发布日期 |
| developer | String | 开发商 |
| publisher | String | 发行商 |
| genre | String | 游戏类型 |
| players | String | 玩家数量 |
| rating | Double | 评分 |
| hash | String | 哈希值(CRC32) |
| files | String | 游戏文件路径 |

### 3.2 媒体文件字段（共40个）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| image | String | 游戏图标 |
| video | String | 视频 |
| marquee | String | 跑马灯 |
| thumbnail | String | 缩略图 |
| wheel | String | 轮子图标 |
| manual | String | 手册 |
| boxFront | String | 2D封面 |
| boxBack | String | 2D封底 |
| boxSpine | String | 书脊 |
| boxFull | String | 3D封面 |
| cartridge | String | 卡带图 |
| logo | String | Logo |
| bezel | String | 边框 |
| panel | String | 面板图 |
| cabinetLeft | String | 机柜左侧 |
| cabinetRight | String | 机柜右侧 |
| tile | String | 瓦片图 |
| banner | String | 横幅图 |
| steam | String | Steam图标 |
| poster | String | 海报 |
| background | String | 背景图 |
| music | String | 音乐 |
| screenshot | String | 截图 |
| titlescreen | String | 标题画面 |
| box3d | String | 3D盒子 |
| steamgrid | String | Steam网格 |
| fanart | String | 粉丝艺术 |
| boxtexture | String | 盒子纹理 |
| supporttexture | String | 支架纹理 |
| videonormalized | String | 标准化视频 |
| wheelcarbon | String | 碳纹理轮子 |
| wheelsteel | String | 钢纹理轮子 |
| screenmarqueesmall | String | 小型跑马灯 |
| boxside | String | 盒子侧边 |
| figurine | String | 手办图 |
| pictoliste | String | 列表图标 |
| pictomonochrome | String | 单色图标 |
| pictomonochromesvg | String | 单色SVG图标 |
| pictocouleur | String | 彩色图标 |
| wallpaper | String | 壁纸 |

---

## 四、映射一致性说明

### 4.1 导入与刮削的统一映射

无论通过**导入模板**还是**在线刮削**获取媒体文件，最终都会映射到相同的数据库字段。

### 4.2 映射设计原则

1. **兼容性优先**：支持多种前端格式（EmuELEC、ESDE、Pegasus等）
2. **同类型归并**：同义或相似的媒体类型统一映射到同一字段
3. **扩展性预留**：保留常用字段，便于未来扩展
4. **命名规范化**：数据库字段采用驼峰命名，与Java实体保持一致

---

## 五、文件扩展名支持

### 5.1 图片格式

| 格式 | 扩展名 |
|------|--------|
| PNG | .png |
| JPEG | .jpg, .jpeg |
| GIF | .gif |
| WebP | .webp |
| SVG | .svg |

### 5.2 视频格式

| 格式 | 扩展名 |
|------|--------|
| MP4 | .mp4 |
| MKV | .mkv |
| AVI | .avi |
| WMV | .wmv |
| WebM | .webm |

### 5.3 音频格式

| 格式 | 扩展名 |
|------|--------|
| MP3 | .mp3 |
| OGG | .ogg |
| WAV | .wav |

### 5.4 文档格式

| 格式 | 扩展名 |
|------|--------|
| PDF | .pdf |

---

**文档版本**: v1.0  
**生成日期**: 2026-05-28  
**适用版本**: WebGamelistOper v1.0+