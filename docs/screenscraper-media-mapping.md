# ScreenScraper 媒体类型映射表

## 概述
本文档定义了 ScreenScraper API 返回的媒体类型与 Game 实体类字段之间的完整映射关系。

---

## 1. 封面/包装盒类

| ScreenScraper 类型 | Game 字段 | 说明 | 别名 |
|:---|:---|:---|:---|
| `box-2d` | `boxFront` | 2D 盒装封面，最常用的游戏封面 | `box2d`, `box-front` |
| `box-2d-back` | `boxBack` | 2D 盒装封底 | `boxback` |
| `box-2d-side` | `boxSpine` | 2D 盒装侧脊 | `boxspine` |
| `box-3d` | `boxFull` | 3D 立体封面渲染图 | `support-3d` |
| `box-3d-side` | `boxside` | 3D 立体封面的侧视角 | `box3dside` |
| `box-texture` | `boxtexture` | 包装盒纹理贴图素材 | - |

---

## 2. 游戏介质类（卡带/光盘）

| ScreenScraper 类型 | Game 字段 | 说明 | 别名 |
|:---|:---|:---|:---|
| `support-2d` | `cartridge` | 游戏介质（卡带/光盘）2D 正面图 | - |
| `support-3d` | **[待添加字段]** | 游戏介质 3D 渲染图 | - |
| `support-texture` | `supporttexture` | 介质纹理贴图素材 | `support` |

---

## 3. Logo / Wheel 类（按风格）

| ScreenScraper 类型 | Game 字段 | 说明 | 风格特点 |
|:---|:---|:---|:---|
| `wheel` | `wheel` | 标准彩色游戏 Logo | 标准彩色 |
| `wheel-carbon` | `wheelcarbon` | 碳纤维纹理风格 Logo | 碳纤维纹理 |
| `wheel-steel` | `wheelsteel` | 金属拉丝质感风格 Logo | 金属拉丝 |
| `wheel-icon` | `wheel` | 图标格式的 Wheel | - |

---

## 4. 截图/画面类

| ScreenScraper 类型 | Game 字段 | 说明 | 别名 |
|:---|:---|:---|:---|
| `ss` | `screenshot` | 游戏实机画面截图 | `screenshot` |
| `sstitle` | `thumbnail` | 游戏标题画面截图 | `titlescreen` |
| `video` | `video` | 游戏预览视频 | - |
| `video-normalized` | `videonormalized` | 标准化视频 | - |
| `fanart` | `fanart` | 高清背景图/艺术图 | `photo`, `illustration`, `controller` |
| `background` | `background` | 通用背景图 | `backgrounds` |

---

## 5. 界面装饰类

| ScreenScraper 类型 | Game 字段 | 说明 | 别名 |
|:---|:---|:---|:---|
| `marquee` | `marquee` | 街机顶部横条图 | `arcademarquee` |
| `screenmarquee` | `marquee` | 现代前端用横条图 | - |
| `screenmarqueesmall` | `screenmarqueesmall` | 小尺寸横条图 | - |
| `flyer` | `poster` | 街机宣传海报 | `flyer-2d` |
| `steamgrid` | `steamgrid` | Steam 风格的网格封面 | - |

---

## 6. 图标系列（Picto）

| ScreenScraper 类型 | Game 字段 | 尺寸要求 | 说明 |
|:---|:---|:---|:---|
| `pictoliste` | `pictoliste` | 32×32px | 列表图标，官方自动生成 |
| `pictomonochrome` | `pictomonochrome` | 512×512px | 单色 Logo（黑色，透明背景，PNG） |
| `pictomonochromesvg` | `pictomonochromesvg` | 矢量 | 矢量单色 Logo（SVG 格式） |
| `pictocouleur` | `pictocouleur` | 512×512px | 彩色 Logo（透明背景，PNG） |
| `wallpaper` | `wallpaper` | 1920×1080px | 壁纸 |
| `figurine` | `figurine` | - | 角色/吉祥物小图标 |

---

## 7. 其他特殊类型

| ScreenScraper 类型 | Game 字段 | 说明 | 别名 |
|:---|:---|:---|:---|
| `manuel` | `manual` | PDF 格式游戏手册 | `manual` |
| `bezel` | `bezel` | 游戏边框（街机模拟器用） | - |
| `bezel43` | `bezel` | 4:3 比例边框 | - |
| `bezel169` | `bezel` | 16:9 比例边框 | - |
| `bezel-16-9` | `bezel` | 16:9 比例边框 | - |
| `panel` | `panel` | 街机控制面板图 | - |
| `logo` | `logo` | 游戏 Logo（矢量或独立文件） | - |

---

## Game 表字段状态

### 有用字段（ScreenScraper 有对应来源）

| Game 字段 | ScreenScraper 类型来源 |
|:---|:---|
| `boxFront` | `box-2d`, `box2d`, `box-front` |
| `boxBack` | `box-2d-back`, `boxback` |
| `boxSpine` | `box-2d-side`, `boxspine` |
| `boxFull` | `box-3d`, `support-3d` |
| `boxside` | `box-3d-side`, `box3dside` |
| `boxtexture` | `box-texture` |
| `cartridge` | `support-2d` |
| `supporttexture` | `support-texture`, `support` |
| `wheel` | `wheel`, `wheel-icon` |
| `wheelcarbon` | `wheel-carbon` |
| `wheelsteel` | `wheel-steel` |
| `thumbnail` | `sstitle`, `titlescreen` |
| `screenshot` | `ss`, `screenshot` |
| `marquee` | `marquee`, `arcademarquee`, `screenmarquee` |
| `screenmarqueesmall` | `screenmarqueesmall` |
| `background` | `background`, `backgrounds` |
| `fanart` | `fanart`, `photo`, `illustration`, `controller` |
| `poster` | `flyer`, `flyer-2d` |
| `bezel` | `bezel`, `bezel43`, `bezel169`, `bezel-16-9` |
| `panel` | `panel` |
| `video` | `video` |
| `videonormalized` | `video-normalized` |
| `manual` | `manuel`, `manual` |
| `logo` | `logo` |
| `pictoliste` | `pictoliste` |
| `pictomonochrome` | `pictomonochrome` |
| `pictomonochromesvg` | `pictomonochromesvg` |
| `pictocouleur` | `pictocouleur` |
| `wallpaper` | `wallpaper` |
| `steamgrid` | `steamgrid` |
| `figurine` | `figurine` |

---

### 缺失字段（ScreenScraper 有但 Game 表未实现）

| ScreenScraper 类型 | 说明 |
|:---|:---|
| `support-3d` | 游戏介质 3D 渲染图 |

---

### 废弃/无用字段（ScreenScraper 无对应来源）

| Game 字段 | 说明 | 状态 |
|:---|:---|:---|
| `image` | 历史遗留字段，无来源 | **废弃** |
| `cabinetLeft` | 机柜左侧图，无来源 | **废弃** |
| `cabinetRight` | 机柜右侧图，无来源 | **废弃** |
| `tile` | 磁贴图，无来源 | **废弃** |
| `banner` | 横幅图，无来源 | **废弃** |
| `steam` | Steam 相关，无来源 | **废弃** |
| `music` | 音乐文件，无来源 | **废弃** |
| `titlescreen` | 已被 `thumbnail` 替代 | **废弃** |
| `box3d` | 已被 `boxFull` 替代 | **废弃** |

---

## 代码实现位置

### 1. ScraperServiceImpl.java

- `mapMediaTypeToGameField()` 方法：[ScraperServiceImpl.java#L1544](file:///d:/code/webGamelistOper/src/main/java/com/gamelist/service/impl/ScraperServiceImpl.java#L1544)
- `updateGameMediaPath()` 方法：[ScraperServiceImpl.java#L1594](file:///d:/code/webGamelistOper/src/main/java/com/gamelist/service/impl/ScraperServiceImpl.java#L1594)

### 2. MediaDownloadServiceImpl.java

- `mapMediaTypeToGameField()` 方法：与 ScraperServiceImpl 中的对应关系需保持一致

### 3. GameServiceImpl.java

- `setGameMediaField()` 方法：[GameServiceImpl.java#L3950](file:///d:/code/webGamelistOper/src/main/java/com/gamelist/service/impl/GameServiceImpl.java#L3950)

---

## 注意事项

1. **三种模式保持一致**：刮削模式、传统导入模式、模板导入模式的字段映射关系必须完全一致
2. **映射更新**：ScreenScraper 新增媒体类型时，需同步更新本文档及相关代码
3. **字段废弃**：废弃字段不再使用新数据写入，但旧数据仍保留
4. **别名处理**：代码中需要兼容同类型的多个别名

---

## 最后更新

- 更新日期：2026-05-31
- 维护者：项目开发团队

