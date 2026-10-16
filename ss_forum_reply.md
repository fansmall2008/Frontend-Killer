# Réponse — Suite de notre recherche sur les numéros de série

Bonjour à toute l'équipe,

Merci pour votre travail ! De notre côté, nous avons avancé dans nos recherches.

## Ce que nous avons trouvé

En étudiant la documentation de l'API v2, nous avons découvert le paramètre **`serialnum`** dans `jeuInfos.php`. Nous l'avons testé intensivement et les résultats sont excellents pour certaines plateformes :

| Plateforme | systemId | Format du numéro | Résultat |
|---|---|---|---|
| **PS1** | 57 | `SLUS/SLES/SLPS-xxxxx` | ✅ Fonctionne parfaitement |
| **PS2** | 58 | `SCUS/SLUS/SLPM-xxxxx` | ✅ Fonctionne parfaitement |
| **GameCube** | 13 | `DL-DOL-XXXX-REGION` | ✅ Fonctionne parfaitement |
| **Wii U** | 18 | Codes courts (ex. `ARDE01`) | ✅ Fonctionne parfaitement |

Pour ces 4 plateformes, `serialnum` + `systemeid` suffit à trouver le jeu avec précision, sans avoir besoin du CRC. C'est exactement ce qu'il nous fallait !

## Le problème restante

Cependant, pour d'autres plateformes CD-ROM, le champ `serials` dans votre base de données semble **vide** :

| Plateforme | systemId | Problème |
|---|---|---|
| **PSP** | 61 | `serials` vide |
| **Saturn** | 22 | Format non standard (numéros purs comme `81009`) |
| **Dreamcast** | 23 | `serials` vide |
| **Mega-CD** | 20 | `serials` vide |
| **PCE-CD** | 114 | `serials` vide |
| **Wii** | 16 | `serials` vide |

Pourtant, ces plateformes ont aussi des numéros de série ou des identifiants uniques sur leurs disques :
- **PSP** : codes `UCUS-xxxxx` / `UCES-xxxxx` sur les UMD
- **Saturn** : codes `T-xxxxx` sur les disques
- **Dreamcast** : codes `MK-xxxxx` sur les GD-ROM
- **Mega-CD** : codes `MK-xxxxx` sur les CD
- **Wii** : codes `Rxxx` sur les disques

## Notre question

**Y a-t-il un moyen d'améliorer la correspondance pour ces plateformes ?** Par exemple :
- Un autre paramètre API que nous aurions manqué ?
- Une méthode alternative pour extraire un identifiant stable depuis ces disques ?
- Ou avez-vous prévu d'ajouter les numéros de série pour ces plateformes dans votre base ?

Nous continuons bien sûr à utiliser le CRC et le nom de fichier comme solution de repli, mais pour les jeux CD-ROM, comme nous l'avons expliqué, le CRC est très instable.

Merci d'avance pour vos conseils !

Cordialement,
Développeur de Frontend-Killer

---
---

# Reply — Follow-up on our serial number research

Hello team,

Thank you for your work! On our side, we've made progress in our research.

## What we found

While studying the API v2 documentation, we discovered the **`serialnum`** parameter in `jeuInfos.php`. We tested it extensively and the results are excellent for some platforms:

| Platform | systemId | Serial format | Result |
|---|---|---|---|
| **PS1** | 57 | `SLUS/SLES/SLPS-xxxxx` | ✅ Works perfectly |
| **PS2** | 58 | `SCUS/SLUS/SLPM-xxxxx` | ✅ Works perfectly |
| **GameCube** | 13 | `DL-DOL-XXXX-REGION` | ✅ Works perfectly |
| **Wii U** | 18 | Short codes (e.g. `ARDE01`) | ✅ Works perfectly |

For these 4 platforms, `serialnum` + `systemeid` is enough to find the game precisely, without needing CRC. This is exactly what we needed!

## The remaining problem

However, for other CD-ROM platforms, the `serials` field in your database appears to be **empty**:

| Platform | systemId | Problem |
|---|---|---|
| **PSP** | 61 | `serials` empty |
| **Saturn** | 22 | Non-standard format (pure numbers like `81009`) |
| **Dreamcast** | 23 | `serials` empty |
| **Mega-CD** | 20 | `serials` empty |
| **PCE-CD** | 114 | `serials` empty |
| **Wii** | 16 | `serials` empty |

Yet these platforms also have serial numbers or unique identifiers on their discs:
- **PSP**: `UCUS-xxxxx` / `UCES-xxxxx` codes on UMDs
- **Saturn**: `T-xxxxx` codes on discs
- **Dreamcast**: `MK-xxxxx` codes on GD-ROMs
- **Mega-CD**: `MK-xxxxx` codes on CDs
- **Wii**: `Rxxx` codes on discs

## Our question

**Is there a way to improve matching for these platforms?** For example:
- Another API parameter we might have missed?
- An alternative method to extract a stable identifier from these discs?
- Or are you planning to add serial numbers for these platforms in your database?

We're of course still using CRC and filename as fallback, but as we explained, CRC is very unstable for CD-ROM games.

Thank you in advance for your advice!

Best regards,
Developer of Frontend-Killer

---
---

# 中文对照（仅供参考，不发布）

# 回复 — 关于序列号研究的后续

团队你们好，

感谢你们的工作！我们这边也有了新的进展。

## 我们的发现

在研究 API v2 文档时，我们发现了 `jeuInfos.php` 中的 **`serialnum`** 参数。经过大量测试，以下平台效果非常好：

| 平台 | systemId | 编号格式 | 结果 |
|---|---|---|---|
| **PS1** | 57 | `SLUS/SLES/SLPS-xxxxx` | ✅ 完美运行 |
| **PS2** | 58 | `SCUS/SLUS/SLPM-xxxxx` | ✅ 完美运行 |
| **GameCube** | 13 | `DL-DOL-XXXX-REGION` | ✅ 完美运行 |
| **Wii U** | 18 | 短码（如 `ARDE01`） | ✅ 完美运行 |

对于这 4 个平台，`serialnum` + `systemeid` 就足以精确找到游戏，不需要 CRC。这正是我们需要的！

## 仍然存在的问题

但是，对于其他光盘类平台，你们数据库中的 `serials` 字段似乎是**空的**：

| 平台 | systemId | 问题 |
|---|---|---|
| **PSP** | 61 | `serials` 为空 |
| **Saturn** | 22 | 非标准格式（纯数字如 `81009`） |
| **Dreamcast** | 23 | `serials` 为空 |
| **Mega-CD** | 20 | `serials` 为空 |
| **PCE-CD** | 114 | `serials` 为空 |
| **Wii** | 16 | `serials` 为空 |

然而这些平台的光盘上实际上也有序列号或唯一标识：
- **PSP**：UMD 上的 `UCUS-xxxxx` / `UCES-xxxxx`
- **Saturn**：光盘上的 `T-xxxxx`
- **Dreamcast**：GD-ROM 上的 `MK-xxxxx`
- **Mega-CD**：CD 上的 `MK-xxxxx`
- **Wii**：光盘上的 `Rxxx`

## 我们的问题

**有没有办法改善这些平台的匹配？** 比如：
- 是否有我们遗漏的其他 API 参数？
- 是否有替代方法可以从这些光盘中提取稳定的标识符？
- 或者你们有计划在数据库中添加这些平台的序列号吗？

我们当然也在继续使用 CRC 和文件名作为后备方案，但正如我们之前所说，CRC 对光盘类游戏非常不稳定。

提前感谢你们的建议！

此致，
Frontend-Killer 开发者
