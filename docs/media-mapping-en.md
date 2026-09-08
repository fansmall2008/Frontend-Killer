# Media File Mapping Documentation

---

## 1. Import Template to Database Field Mapping

### 1.1 Basic Field Mapping

| XML Field | Database Field | Description | Supported Templates |
|-----------|----------------|-------------|---------------------|
| name | name | Game Name | webgamelistoper, emuelec |
| desc / description | description | Game Description | webgamelistoper, emuelec |
| releasedate / release | releaseDate | Release Date | webgamelistoper, emuelec |
| developer / dev | developer | Developer | webgamelistoper, emuelec |
| publisher / pub | publisher | Publisher | webgamelistoper, emuelec |
| genre / category | genre | Genre | webgamelistoper, emuelec |
| players / player | players | Number of Players | webgamelistoper, emuelec |
| rating | rating | Rating | webgamelistoper, emuelec |
| hash / crc / crc32 | hash | Hash Value (CRC32) | webgamelistoper |
| path / file | files | Game File Path | webgamelistoper, emuelec |
| image | image | Game Icon | webgamelistoper, emuelec |
| video | video | Video Path | webgamelistoper, emuelec |
| thumbnail | thumbnail | Thumbnail | webgamelistoper, emuelec |
| marquee | marquee | Marquee Image | webgamelistoper, emuelec |

### 1.2 Media File Path Mapping (webgamelistoper template)

| Template Media Type | Database Field | File Path Pattern |
|---------------------|----------------|-------------------|
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

### 1.3 Media File Path Mapping (emuelec template)

| Template Media Type | Database Field | File Path Pattern |
|---------------------|----------------|-------------------|
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

## 2. Scraper Media Type to Database Field Mapping

### 2.1 Scraper Media Type Mapping Table

| Scraper Media Type | Database Field | Description |
|---------------------|----------------|-------------|
| image | image | Game Icon |
| box-2d, box2d, box-front, support-2d, boxvierge | boxFront | 2D Box Front |
| box-2d-back, boxback | boxBack | 2D Box Back |
| box-2d-side, boxspine | boxSpine | 2D Box Side/Spine |
| box-3d, support-3d | boxFull | 3D Box |
| wheel | wheel | Wheel Icon |
| wheel-carbon | wheelcarbon | Carbon Wheel |
| wheel-steel | wheelsteel | Steel Wheel |
| logo | logo | Logo |
| thumbnail, icon | thumbnail | Thumbnail |
| sstitle, titlescreen, minicon | thumbnail | Title Screen/Minicon |
| ss, screenshot, ssmap | screenshot | Screenshot |
| marquee, arcademarquee, screenmarquee | marquee | Marquee |
| screenmarqueesmall | screenmarqueesmall | Small Marquee |
| video, intro | video | Video |
| videonormalized | videonormalized | Normalized Video |
| fanart, photo, illustration, controller | fanart | Fan Art/Photo |
| background, backgrounds | background | Background |
| banner | banner | Banner |
| poster, flyer, flyer-2d | poster | Poster/Flyer |
| bezel, bezel43, bezel169 | bezel | Bezel |
| panel | panel | Panel |
| cartridge | cartridge | Cartridge |
| manual, manuel | manual | Manual |
| music | music | Music |
| steam | steam | Steam Icon |
| steamgrid | steamgrid | Steam Grid |
| boxtexture | boxtexture | Box Texture |
| supporttexture | supporttexture | Support Texture |
| figurine | figurine | Figurine |
| pictocouleur | pictocouleur | Color Icon |
| pictoliste | pictoliste | List Icon |
| pictomonochrome | pictomonochrome | Monochrome Icon |
| pictomonochromesvg | pictomonochromesvg | Monochrome SVG Icon |
| wallpaper | wallpaper | Wallpaper |
| cabinet-left | cabinetLeft | Cabinet Left |
| cabinet-right | cabinetRight | Cabinet Right |
| spine | spine | Spine |
| box-full | boxFull | Full Box |

### 2.2 One-to-Many Mapping Summary

| Database Field | Number of Mapped Types | Included Media Types |
|----------------|------------------------|----------------------|
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

## 3. Database Game Table Field List

### 3.1 Basic Information Fields

| Field Name | Type | Description |
|------------|------|-------------|
| id | Long | Primary Key ID |
| name | String | Game Name |
| description | String | Game Description |
| releaseDate | String | Release Date |
| developer | String | Developer |
| publisher | String | Publisher |
| genre | String | Genre |
| players | String | Number of Players |
| rating | Double | Rating |
| hash | String | Hash Value (CRC32) |
| files | String | Game File Path |

### 3.2 Media File Fields (Total 40)

| Field Name | Type | Description |
|------------|------|-------------|
| image | String | Game Icon |
| video | String | Video |
| marquee | String | Marquee |
| thumbnail | String | Thumbnail |
| wheel | String | Wheel Icon |
| manual | String | Manual |
| boxFront | String | 2D Box Front |
| boxBack | String | 2D Box Back |
| boxSpine | String | Spine |
| boxFull | String | 3D Box |
| cartridge | String | Cartridge |
| logo | String | Logo |
| bezel | String | Bezel |
| panel | String | Panel |
| cabinetLeft | String | Cabinet Left |
| cabinetRight | String | Cabinet Right |
| tile | String | Tile |
| banner | String | Banner |
| steam | String | Steam Icon |
| poster | String | Poster |
| background | String | Background |
| music | String | Music |
| screenshot | String | Screenshot |
| titlescreen | String | Title Screen |
| box3d | String | 3D Box |
| steamgrid | String | Steam Grid |
| fanart | String | Fan Art |
| boxtexture | String | Box Texture |
| supporttexture | String | Support Texture |
| videonormalized | String | Normalized Video |
| wheelcarbon | String | Carbon Wheel |
| wheelsteel | String | Steel Wheel |
| screenmarqueesmall | String | Small Marquee |
| boxside | String | Box Side |
| figurine | String | Figurine |
| pictoliste | String | List Icon |
| pictomonochrome | String | Monochrome Icon |
| pictomonochromesvg | String | Monochrome SVG Icon |
| pictocouleur | String | Color Icon |
| wallpaper | String | Wallpaper |

---

## 4. Mapping Consistency Notes

### 4.1 Unified Mapping for Import and Scraper

Whether media files are obtained through **import templates** or **online scraping**, they will ultimately be mapped to the same database fields.

### 4.2 Mapping Design Principles

1. **Compatibility First**: Support multiple frontend formats (EmuELEC, ESDE, Pegasus, etc.)
2. **Type Consolidation**: Synonymous or similar media types are mapped to the same field
3. **Extensibility**: Reserved commonly used fields for future expansion
4. **Naming Standardization**: Database fields use camelCase naming, consistent with Java entities

---

## 5. Supported File Extensions

### 5.1 Image Formats

| Format | Extensions |
|--------|------------|
| PNG | .png |
| JPEG | .jpg, .jpeg |
| GIF | .gif |
| WebP | .webp |
| SVG | .svg |

### 5.2 Video Formats

| Format | Extensions |
|--------|------------|
| MP4 | .mp4 |
| MKV | .mkv |
| AVI | .avi |
| WMV | .wmv |
| WebM | .webm |

### 5.3 Audio Formats

| Format | Extensions |
|--------|------------|
| MP3 | .mp3 |
| OGG | .ogg |
| WAV | .wav |

### 5.4 Document Formats

| Format | Extensions |
|--------|------------|
| PDF | .pdf |

---

**Document Version**: v1.0  
**Generated Date**: 2026-05-28  
**Applicable Version**: WebGamelistOper v1.0+