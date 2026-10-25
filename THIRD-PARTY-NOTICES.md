# 第三方组件声明 / Third-Party Notices / サードパーティ通知

---

## 中文

本软件 **Frontend Killer** 以 MIT 许可证发布，不包含任何第三方程序代码。

### chdman（MAME tools）

生成 CHD 格式镜像时，本软件通过**独立进程**调用 `chdman`。`chdman` 是 [MAME](https://www.mamedev.org) 项目的组成部分：

| 项目 | 内容 |
|---|---|
| 组件 | chdman（MAME tools） |
| 许可证 | GNU General Public License v2 或更高版本（GPL-2.0-or-later） |
| 调用方式 | 独立子进程 + 命令行参数，不链接、不修改其源码 |
| 与本软件的关系 | 仅为聚合分发，不构成衍生作品 |

**源码获取**

- MAME 官方仓库：https://github.com/mamedev/mame
- Debian 打包源码：https://sources.debian.org/src/mame/

**许可证文本**

- Docker / Linux：随包保留在 `/usr/share/doc/mame-tools/copyright`
- Windows：见本文件附录，或 MAME 仓库中的 `docs/legal/GPL-2.0`

### ffmpeg

把 APE / FLAC 音轨还原为 WAV 时，本软件同样以**独立进程**调用 `ffmpeg`。`ffmpeg` 是 [FFmpeg](https://ffmpeg.org) 项目的产物：

| 项目 | 内容 |
|---|---|
| 组件 | ffmpeg |
| 许可证 | GNU Lesser General Public License v2.1 或更高版本（LGPL-2.1-or-later，官方默认构建）；若使用 GPL 构建则为 GPL-2.0-or-later |
| 调用方式 | 独立子进程 + 命令行参数，不链接、不修改其源码 |
| 与本软件的关系 | 仅为聚合分发，不构成衍生作品 |

**源码获取**

- 官方站点：https://ffmpeg.org
- 源码仓库：https://github.com/FFmpeg/FFmpeg
- Windows 构建：https://www.gyan.dev/ffmpeg/builds/、https://github.com/BtbN/FFmpeg-Builds/releases

**许可证文本**

- Docker / Linux：随包保留在 `/usr/share/doc/ffmpeg/copyright`
- Windows：随 ffmpeg 发行包附带的 `LICENSE` / `COPYING.*` 文件

**商标声明**

MAME 是 Gregory Ember 的注册商标。使用该名称、标识或文字商标需要获得商标持有者的许可。本软件与 MAME 项目无隶属关系，也未获得其背书。

---

## English

**Frontend Killer** is released under the MIT License and contains no third-party program code.

### chdman (MAME tools)

To generate CHD disc images, this software invokes `chdman` as a **separate process**. `chdman` is part of the [MAME](https://www.mamedev.org) project:

| Item | Detail |
|---|---|
| Component | chdman (MAME tools) |
| License | GNU General Public License v2 or later (GPL-2.0-or-later) |
| Invocation | Separate subprocess with command-line arguments; not linked, source unmodified |
| Relationship | Mere aggregation only; not a derivative work |

**Source code**

- MAME repository: https://github.com/mamedev/mame
- Debian package source: https://sources.debian.org/src/mame/

**License text**

- Docker / Linux: retained at `/usr/share/doc/mame-tools/copyright`
- Windows: see the appendix of this file, or `docs/legal/GPL-2.0` in the MAME repository.

### ffmpeg

To unpack APE / FLAC audio tracks back to WAV, this software likewise invokes `ffmpeg` as a **separate process**. `ffmpeg` is a product of the [FFmpeg](https://ffmpeg.org) project:

| Item | Detail |
|---|---|
| Component | ffmpeg |
| License | GNU Lesser General Public License v2.1 or later (LGPL-2.1-or-later, default official builds); GPL-2.0-or-later if a GPL build is used |
| Invocation | Separate subprocess with command-line arguments; not linked, source unmodified |
| Relationship | Mere aggregation only; not a derivative work |

**Source code**

- Project site: https://ffmpeg.org
- Source repository: https://github.com/FFmpeg/FFmpeg
- Windows builds: https://www.gyan.dev/ffmpeg/builds/, https://github.com/BtbN/FFmpeg-Builds/releases

**License text**

- Docker / Linux: retained at `/usr/share/doc/ffmpeg/copyright`
- Windows: the `LICENSE` / `COPYING.*` files shipped with the ffmpeg distribution

**Trademark**

MAME is a registered trademark of Gregory Ember. Permission is required to use the "MAME" name, logo, or wordmark. This software is not affiliated with, or endorsed by, the MAME project.

---

## 日本語

本ソフト **Frontend Killer** は MIT ライセンスで公開されており、サードパーティのプログラムコードを含みません。

### chdman（MAME tools）

CHD 形式のディスクイメージを生成する際、本ソフトは**別プロセス**として `chdman` を呼び出します。`chdman` は [MAME](https://www.mamedev.org) プロジェクトの一部です。

| 項目 | 内容 |
|---|---|
| コンポーネント | chdman（MAME tools） |
| ライセンス | GNU General Public License v2 以降（GPL-2.0-or-later） |
| 呼び出し方法 | 独立したサブプロセスとコマンドライン引数。リンクせず、ソースも改変しません |
| 本ソフトとの関係 | 単なる集約配布であり、派生著作物を構成しません |

**ソースコード**

- MAME 公式リポジトリ：https://github.com/mamedev/mame
- Debian パッケージのソース：https://sources.debian.org/src/mame/

**ライセンス文**

- Docker / Linux：`/usr/share/doc/mame-tools/copyright` に保持されます
- Windows：本ファイルの付録、または MAME リポジトリの `docs/legal/GPL-2.0` を参照してください。

### ffmpeg

APE / FLAC 音声トラックを WAV に戻す際も、本ソフトは**別プロセス**として `ffmpeg` を呼び出します。`ffmpeg` は [FFmpeg](https://ffmpeg.org) プロジェクトの成果物です。

| 項目 | 内容 |
|---|---|
| コンポーネント | ffmpeg |
| ライセンス | GNU Lesser General Public License v2.1 以降（LGPL-2.1-or-later、公式の既定ビルド）。GPL ビルドを使用する場合は GPL-2.0-or-later |
| 呼び出し方法 | 独立したサブプロセスとコマンドライン引数。リンクせず、ソースも改変しません |
| 本ソフトとの関係 | 単なる集約配布であり、派生著作物を構成しません |

**ソースコード**

- 公式サイト：https://ffmpeg.org
- ソースリポジトリ：https://github.com/FFmpeg/FFmpeg
- Windows ビルド：https://www.gyan.dev/ffmpeg/builds/ 、https://github.com/BtbN/FFmpeg-Builds/releases

**ライセンス文**

- Docker / Linux：`/usr/share/doc/ffmpeg/copyright` に保持されます
- Windows：ffmpeg 配布物に付属する `LICENSE` / `COPYING.*` ファイル

**商標**

MAME は Gregory Ember の登録商標です。「MAME」の名称、ロゴ、ワードマークの使用には商標権者の許可が必要です。本ソフトは MAME プロジェクトと提携しておらず、承認も受けていません。

---

## 附录：使用场景说明 / Appendix: Usage note / 付録：利用について

CHD 是可选格式，与 CSO、ISO 等格式并列，由用户在副本设置中自行勾选。用户不启用 CHD 时不依赖 `chdman`，本软件完全可独立运行。APE / FLAC 音轨还原同样只在源镜像带有这类音轨时发生。

**存放位置**：Windows / JAR 部署可把 `chdman.exe`、`ffmpeg.exe` 放在程序目录下的 `tools/` 或 `data/tools/`；Docker 部署放在宿主机的 `./data/tools/`（容器内 `/data/tools/`）。也可以在「工具箱 → ROM 副本 → 外部工具」里直接指定路径，或设置 `CHDMAN` / `FFMPEG` 环境变量。

CHD is an optional format listed alongside CSO and ISO, selected by the user in variant settings. Without CHD enabled, this software does not depend on `chdman` and runs fully standalone. APE / FLAC track extraction likewise only happens when the source image actually carries those tracks.

**Where to put them**: for Windows / JAR deployments, drop `chdman.exe` and `ffmpeg.exe` into `tools/` or `data/tools/` next to the program; for Docker, into `./data/tools/` on the host (that is `/data/tools/` inside the container). You can also set the path in the toolbox (ROM variants → External tools), or use the `CHDMAN` / `FFMPEG` environment variables.

CHD はオプション形式であり、CSO や ISO などと並んで、ユーザーがバリアント設定で任意に選択します。CHD を有効にしない場合、本ソフトは `chdman` に依存せず、単独で動作します。APE / FLAC 音声トラックの変換も、元イメージに該当トラックがある場合に限られます。

**配置場所**：Windows / JAR 版では `chdman.exe` と `ffmpeg.exe` をプログラム直下の `tools/` または `data/tools/` に、Docker 版ではホスト側の `./data/tools/`（コンテナ内 `/data/tools/`）に置きます。ツールボックス（ROM バリアント → 外部ツール）でパスを直接指定するか、`CHDMAN` / `FFMPEG` 環境変数を使う方法もあります。
