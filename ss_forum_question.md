# Recherche de jeux CD-ROM par numéro de série — API v2

Bonjour à toute l'équipe ScreenScraper,

Je suis le développeur de **Frontend-Killer**, un outil open-source de gestion de bibliothèque de jeux rétro (GitHub : `fansmall/Frontend-Killer`). Notre logiciel permet aux utilisateurs d'importer leurs listes de jeux depuis divers frontends (EmulationStation, RetroArch, Pegasus, etc.), de récupérer les métadonnées et médias via votre API, puis de réexporter les données enrichies.

Nous utilisons votre API v2 depuis la première version avec beaucoup de satisfaction — merci pour ce travail remarquable !

---

## Problème : les jeux CD-ROM ne correspondent pas

Nous rencontrons un problème récurrent avec le **scraping des jeux sur CD-ROM** (ISO, CHD, BIN+CUE, etc.) : un grand nombre de jeux ne trouvent pas de correspondance.

**Cause racine : le CRC32 est instable pour les CD-ROM.**

Notre logiciel calcule le CRC32 du fichier local et l'envoie à `jeuInfos.php` via le paramètre `crc`. Pour les cartouches, cela fonctionne parfaitement. Mais pour les CD-ROM, le même jeu peut avoir des CRC totalement différents selon :
- l'outil de dump utilisé,
- le format (ISO / CHD / BIN+CUE / CSO),
- la version du logiciel de conversion.

Un seul octet de différence suffit à changer complètement le CRC. Résultat : le CRC envoyé ne correspond à aucune entrée dans votre base, et le jeu n'est pas trouvé.

La recherche par `romnom` (nom de fichier) ne compense pas bien non plus, car les utilisateurs renomment souvent leurs fichiers avec des espaces, abréviations ou caractères spéciaux.

---

## Notre question

Nous avons remarqué que votre base de données contient déjà des **numéros de série** (`serials`) pour de nombreuses plateformes — par exemple `SCUS-97102` pour Gran Turismo 3 sur PS2, ou `SLES-01370` pour Metal Gear Solid sur PS1. Ces numéros sont **stables et uniques** : ils ne changent jamais, quel que soit le format ou l'outil de dump.

Notre logiciel serait capable d'extraire ces numéros de série depuis les fichiers ISO (Volume Label ISO9660, fichier `SYSTEM.CNF`) ou depuis les noms de fichiers au format standard (`SLUS-20001.iso`).

**Existe-t-il actuellement un moyen, via votre API, de rechercher un jeu à partir de son numéro de série ?** Si ce n'est pas le cas, auriez-vous une recommandation ou une piste pour améliorer la correspondance des jeux CD-ROM ?

Nous serions très intéressés par vos conseils — vous connaissez mieux que quiconque les capacités de votre base de données.

---

Merci d'avance pour votre retour !

Cordialement,
Développeur de Frontend-Killer

---
---

# Searching CD-ROM Games by Serial Number — API v2

Hello ScreenScraper team,

I'm the developer of **Frontend-Killer**, an open-source retro game library management tool (GitHub: `fansmall/Frontend-Killer`). Our software allows users to import their game libraries from various frontends (EmulationStation, RetroArch, Pegasus, etc.), retrieve metadata and media through your API, and then re-export the enriched data.

We've been using your API v2 since the very first version and are very satisfied — thank you for your outstanding work!

---

## Problem: CD-ROM games don't match

We're encountering a recurring issue with **scraping CD-ROM games** (ISO, CHD, BIN+CUE, etc.): a large number of games fail to find a match.

**Root cause: CRC32 is unstable for CD-ROMs.**

Our software calculates the CRC32 of the local file and sends it to `jeuInfos.php` via the `crc` parameter. For cartridge-based games, this works perfectly. But for CD-ROMs, the same game can have completely different CRCs depending on:
- the dump tool used,
- the file format (ISO / CHD / BIN+CUE / CSO),
- the version of the conversion software.

A single byte of difference is enough to completely change the CRC. As a result, the CRC we send doesn't match any entry in your database, and the game goes unfound.

Searching by `romnom` (filename) doesn't compensate well either, as users often rename their files with spaces, abbreviations, or special characters.

---

## Our question

We noticed that your database already contains **serial numbers** (`serials`) for many platforms — for example `SCUS-97102` for Gran Turismo 3 on PS2, or `SLES-01370` for Metal Gear Solid on PS1. These serials are **stable and unique**: they never change, regardless of format or dump tool.

Our software would be able to extract these serial numbers from ISO files (ISO9660 Volume Label, `SYSTEM.CNF` file) or from filenames in standard format (`SLUS-20001.iso`).

**Is there currently a way, through your API, to search for a game by its serial number?** If not, would you have any recommendation or lead for improving CD-ROM game matching?

We would be very interested in your advice — you know the capabilities of your database better than anyone.

---

Thank you in advance for your feedback!

Best regards,
Developer of Frontend-Killer

---
---

# 中文对照（仅供参考，不发布）

# 通过序列号搜索 CD-ROM 游戏 — API v2

ScreenScraper 团队你们好，

我是 **Frontend-Killer** 的开发者，这是一个开源的复古游戏库管理工具（GitHub：`fansmall/Frontend-Killer`）。我们的软件允许用户从各种前端（EmulationStation、RetroArch、Pegasus 等）导入游戏列表，通过你们的 API 获取元数据和媒体文件，然后将丰富后的数据重新导出。

我们从第一版开始就一直使用你们的 API v2，非常满意——感谢你们出色的工作！

---

## 问题：CD-ROM 游戏无法匹配

我们在 **CD-ROM 游戏刮削**（ISO、CHD、BIN+CUE 等）方面遇到了一个反复出现的问题：大量游戏找不到匹配。

**根本原因：CD-ROM 的 CRC32 不稳定。**

我们的软件计算本地文件的 CRC32 并通过 `crc` 参数发送到 `jeuInfos.php`。对于卡带类游戏，这工作得很好。但对于 CD-ROM，同一个游戏可能产生完全不同的 CRC，取决于：
- 使用的 dump 工具，
- 文件格式（ISO / CHD / BIN+CUE / CSO），
- 转换软件的版本。

仅仅一个字节的不同就能彻底改变 CRC。结果就是：发送的 CRC 在你们数据库中找不到对应条目，游戏就无法被识别。

通过 `romnom`（文件名）搜索也不能很好地弥补，因为用户经常用空格、缩写或特殊字符重命名文件。

---

## 我们的问题

我们注意到你们的数据库已经包含了许多平台的 **游戏序列号**（`serials`）——例如 PS2 上 Gran Turismo 3 的 `SCUS-97102`，或 PS1 上 Metal Gear Solid 的 `SLES-01370`。这些编号是 **稳定且唯一的**：无论什么格式或 dump 工具，都不会改变。

我们的软件有能力从 ISO 文件中提取这些序列号（ISO9660 卷标、`SYSTEM.CNF` 文件），或从标准格式的文件名中提取（如 `SLUS-20001.iso`）。

**目前你们的 API 是否已经有一种方法可以通过序列号来搜索游戏？** 如果暂时没有，你们有没有什么建议或思路来改善 CD-ROM 游戏的匹配？

我们非常期待你们的建议——没有人比你们更了解自己数据库的能力。

---

提前感谢你们的回复！

此致，
Frontend-Killer 开发者
