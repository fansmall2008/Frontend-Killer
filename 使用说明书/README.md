# Frontend-Killer 使用说明书 / User Manual / 取扱説明書

> 本说明书面向**普通使用者**，只讲"怎么用"——界面在哪、按钮点哪、流程怎么走，不涉及代码、API、数据库结构等开发细节。
> This manual is for **end users**: it explains how to use the software through its interface and workflows, without any code, API, or database internals.
> 本取扱説明書は**一般利用者**向けです。画面操作と手順のみを説明し、コード・API・データベース内部には触れません。

---

## 📖 三语说明 / Languages / 言語について

本说明书提供**中文、English、日本語**三个语言版本，内容完全一致。
This manual is available in **Chinese, English, and Japanese**, with identical content.
本取扱説明書は**中国語・英語・日本語**の3言語で提供され、内容は同一です。

文件命名规则 / File naming / ファイル命名規則：`<序号>-<主题>-<语言>.md`

- 语言后缀 / Language suffix / 言語サフィックス：`cn` = 中文，`en` = English，`ja` = 日本語
- 例 / Example / 例：`06-平台管理-cn.md`、`06-platform-management-en.md`、`06-プラットフォーム管理-ja.md`

---

## 🗂️ 目录 / Table of Contents / 目次

### 第一部分 · 入门 / Part 1 · Getting Started / パート1 · 入門

| # | 中文 | English | 日本語 |
|---|------|---------|--------|
| 01 | 认识软件 | Getting to Know the Software | ソフトウェアの紹介 |
| 02 | 快速上手 | Quick Start | クイックスタート |
| 03 | 界面与导航 | Interface & Navigation | 画面とナビゲーション |
| 04 | 端到端流程 | End-to-End Workflow | エンドツーエンドの流れ |

### 第二部分 · 核心功能 / Part 2 · Core Features / パート2 · コア機能

| # | 中文 | English | 日本語 |
|---|------|---------|--------|
| 05 | 首页仪表盘 | Dashboard | ダッシュボード |
| 06 | 平台管理 | Platform Management | プラットフォーム管理 |
| 07 | 游戏列表管理 | Game List Management | ゲームリスト管理 |
| 08 | 游戏编辑 | Game Editing | ゲーム編集 |
| 09 | 数据导入 | Data Import | データインポート |
| 10 | 数据导出 | Data Export | データエクスポート |
| 11 | 自动刮削 | Auto Scraping | 自動スクレイピング |
| 12 | 媒体文件管理 | Media File Management | メディアファイル管理 |
| 13 | 平台合并 | Platform Merge | プラットフォーム統合 |

### 第三部分 · 辅助功能 / Part 3 · Auxiliary Features / パート3 · 補助機能

| # | 中文 | English | 日本語 |
|---|------|---------|--------|
| 14 | 任务管理 | Task Management | タスク管理 |
| 15 | 临时子集管理 | Temporary Subset Management | 一時サブセット管理 |
| 16 | 日志查看器 | Log Viewer | ログビューア |
| 17 | 系统设置与个性化 | System Settings & Personalization | システム設定とパーソナライズ |

### 第四部分 · 进阶与排错 / Part 4 · Advanced & Troubleshooting / パート4 · 応用とトラブル対処

| # | 中文 | English | 日本語 |
|---|------|---------|--------|
| 18 | 模板系统详解 | Template System Guide | テンプレートシステム詳解 |
| 19 | 常见问题与故障排除 | FAQ & Troubleshooting | よくある質問とトラブル対処 |

---

## 🚀 建议阅读路径 / Suggested Reading Path / おすすめの読み方

**第一次使用 / First time / はじめての方**
1. `01 认识软件` → 了解它能帮你做什么 / Learn what it does / 何ができるか把握
2. `02 快速上手` → 装好并跑起来 / Install and run / インストールして起動
3. `03 界面与导航` → 认识每个页面在哪 / Know where every page is / 各画面の場所を把握
4. `04 端到端流程` → 走一遍完整主线 / Walk the full pipeline / 全体の流れを通す

**日常查用 / Daily reference / 日常の参照**
- 直接用上面的目录跳到对应功能章节 / Jump to the feature chapter from the table above / 上の目次から該当機能の章へ

**做导入导出模板 / Building import-export templates / インポート・エクスポートテンプレート作成**
- 必读 `18 模板系统详解`（含全部函数、变量、运算符、示例）/ Must read `18 Template System Guide` / 必読 `18 テンプレートシステム詳解`

**遇到问题 / When something goes wrong / 問題が起きたら**
- 先查 `19 常见问题与故障排除` / Check `19 FAQ & Troubleshooting` first / まず `19 よくある質問とトラブル対処`

---

## 🧭 术语速查 / Glossary / 用語早見

| 术语 / Term / 用語 | 含义 / Meaning / 意味 |
|---|---|
| 平台 Platform | 一组 ROM 加一套导入导出模板的集合（如 Pegasus、RetroBat、ES-DE）/ A set of ROMs plus an import-export template |
| 系统 System | ScreenScraper 侧的标准系统（如 PS1、Saturn），平台绑定它才能刮削 / A ScreenScraper standard system a platform binds to for scraping |
| 刮削 Scrape | 从 ScreenScraper 拉取游戏元数据与媒体 / Fetching metadata & media from ScreenScraper |
| 媒体 Media | 与游戏关联的图片、视频、手册等（50 种类型）/ Images, videos, manuals linked to games |
| 合盘 M3U merge | 把多个单文件游戏合并成一个 m3u 条目 / Merge multi-file games into one m3u entry |
| 方言映射 Dialect mapping | 把同义词归一化（如英文类型统一成中文）/ Normalize synonyms across languages/frontends |
| 主题包 Theme | 可切换的界面外观（配色/字体/音效）/ Switchable UI appearance |

---

## 📌 关于《引导节点梳理》/ About the Guidance Nodes Doc / 「誘導ノード整理」について

同目录下的 `引导节点梳理.md` 是一份**内部讨论用**的候选清单，记录"用户在哪些环节可能卡住、将来可能需要在软件里做引导"的点。它**不是**面向使用者的说明书章节，仅供团队日后设计引导系统时参考。
The `引导节点梳理.md` in this folder is an **internal discussion** candidate list of points where users may get stuck and may need in-app guidance in the future. It is not a user-facing manual chapter.