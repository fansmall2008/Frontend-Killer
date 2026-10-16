# 15 · Temporary Subset Management

> A temporary subset (game subset) is a temporary collection of "a handful of games filtered out from a platform". You can circle a batch of games by conditions, edit them one by one, and then decide whether to sync back to the main set or save them as a new platform. This chapter covers what it is, how to create it, how to edit it, and how to dispose of it.

**Entry**: Platform Management page → **Game Subsets** tab; or create it by choosing the "Game subset" type in the split dialog.

---

## 15.1 What a Temporary Subset Is For

Imagine you have a very large platform with hundreds or thousands of games. You only want to do something with **a certain kind of game**, for example:

- Pick out games "rated below 0.5", fix them in bulk, then decide their fate;
- Pull out games from "a certain developer" separately to make a new platform;
- Circle games whose "file doesn't exist" to verify.

Instead of hunting through the main list, use a **temporary subset**: filter a batch of games by conditions into an **independent small collection**. In this small collection you can edit and verify at leisure, then decide:

| Disposal | Effect |
|---|---|
| **Sync to main set** | Write the subset's changes back to the original games (modifies the official data of the same batch of games) |
| **Create new platform** | Start a brand-new platform from the subset's games |
| **Delete subset** | Just discard this temporary collection; the original games are unaffected |

> Key understanding: a temporary subset is **draft / staging** in nature. It isn't an official platform by itself — after editing you either "sync to main set" to make it effective, "create new platform" to land it, or just delete it.

---

## 15.2 How to Create a Temporary Subset

Temporary subsets are created via the platform's **Split** feature (split = break out part of a platform's games by conditions):

1. Go to **Platform Management**, find the target platform, and click **✂️ Split** in the row actions.
2. In the split dialog, set **Split type** to **"Game subset"** (the other option is "New platform", which splits directly into a new platform, see `06`).
3. Set the filter conditions (see below) to circle which games you want.
4. Confirm to generate a temporary subset.

### Available filter conditions

The split dialog offers multi-dimensional filters that can be combined:

| Filter dimension | Options |
|---|---|
| **Scrape status** | scraped / has original data / poor quality / raw ROM (multi-select) |
| **File status** | file exists / file not exists |
| **Minimum rating** | no limit / 0.0 ~ 1.0 |
| **Release year range** | start year ~ end year |
| **Developer** | searchable, Ctrl multi-select |
| **Publisher** | searchable, Ctrl multi-select |

> Combination example: `poor quality` + `file exists` + `min rating 0.5` = pick out games that "have files but poor info quality", to rework in bulk.

---

## 15.3 Editing Games in the Subset

Enter **Platform Management → Game Subsets** tab, or the temporary subset editing page. The interface has several blocks:

1. **Select temporary subset**: lists all created subsets with name, game count, description, creation time. Click one to select it.
2. **Games in the subset**: after selecting a subset, lists the games it contains (name, filename, description excerpt, ID). Click a game to edit it.
3. **Edit game**: a form appears where you can change these fields:

   | Field | Meaning |
   |---|---|
   | Game name | name |
   | Game path | path |
   | Game description | desc |
   | Translated name | translatedName |
   | Translated description | translatedDesc |
   | Developer | developer |
   | Publisher | publisher |
   | Genre | genre |
   | Players | players |
   | Release date | releasedate |
   | Rating | rating |
   | Language | lang |

   When done, click **Save changes**.

---

## 15.4 Disposing of the Subset: Subset Actions

After selecting a subset, the "Subset actions" area has three actions:

- **Sync to main set**: write the subset's game changes **back to the original platform's official game data**. Use when "I just want to fix these games themselves".
- **Create new platform**: first fill in a name under "New platform name", then **generate a brand-new platform** from the subset's games. Use when "I want this batch to become its own platform".
- **Delete subset** ⚠️: delete the temporary collection itself. A confirmation dialog appears. **It deletes the subset container, not the original games** (unless you've already synced).

Action results show a success/failure message in the result area at the bottom of the page (auto-dismisses after ~3 seconds).

---

## 15.5 Typical Flows

**Scenario A: bulk-rework low-quality games**
1. Platform Management → target platform ✂️ Split → type "Game subset" → filter "poor quality" → create.
2. Game Subsets tab → select the subset → edit each game's info → save.
3. When satisfied → **Sync to main set** (changes take effect on the original platform).
4. When no longer needed → **Delete subset**.

**Scenario B: split out a new platform**
1. Split → type "Game subset" → circle by developer or other conditions → create.
2. Verify / fine-tune games in the subset.
3. Fill in the new platform name → **Create new platform**.
4. Go to Platform Management to bind a system, scrape, and export for the new platform.

---

## 15.6 Temporary Subset vs Platform Merge vs Split into New Platform

Easy to confuse — here's the breakdown:

| Feature | Direction | Use |
|---|---|---|
| **Temporary subset** | one platform → filter out a batch (staged) | circle first, edit slowly, decide the destination later |
| **Split into new platform** | one platform → split out a new platform (lands directly) | you clearly want to split, one step |
| **Platform merge** | multiple platforms → combine into one new platform | gather scattered same-kind platforms (see `13`) |

The unique value of a temporary subset is "**staging + editable + multiple exits**" — it's the only tool that lets you "circle them out, edit to see, then decide what to do".

---

## 15.7 Next Steps

- The full split feature → `06 Platform Management`
- What game fields mean → `08 Game Editing`
- How to export the split-out new platform → `10 Data Export`
