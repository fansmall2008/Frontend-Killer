# 13 · Platform Merge

> When you have several platforms that actually belong to the same category (e.g., batches of same-console games imported separately), you can **merge them into one new platform**. This chapter covers the merge flow, how conflicts are handled, and how to read the merge report.

**Entry**: sidebar **Data Merge** 🔀 (TOOLS group)

---

## 13.1 What Merge Does

It consolidates the game data of **multiple source platforms** into **one newly created platform**. Suitable for:
- Games of the same console split across several platforms that you want combined into one.
- Data imported in batches that you want unified.

Merging generates a **new platform**; it does not overwrite the original platforms.

---

## 13.2 The Merge Form

The Data Merge page is a form:

| Field | Description |
|---|---|
| **Source platforms (multi-select)** | The platforms to merge. Hold **Ctrl** to multi-select, **Shift** to select a range |
| **New platform name** | What the merged new platform is called |
| **New platform folder path** | The new platform's folder path. A **prerequisite for export**, not required (but recommended if you want to export) |

Fill them in and click **Start merge**.

---

## 13.3 Conflict Detection

During merge the software checks for **conflicts** between source platforms (the same game appearing in multiple platforms, etc.). Conflicts fall into a few types:

| Conflict type | Meaning |
|---|---|
| **Name conflict** | Same-named games from different sources |
| **File conflict** | Pointing to the same game file |
| **Both conflict** | Both name and file collide |

Once conflicts are detected, you enter the **conflict handling** page to decide what to do, one by one or in batch.

---

## 13.4 Conflict Handling

For each conflict, you can choose:

- **Add**: also add this one into the merge result (may cause duplicates, but preserves data).
- **Skip**: don't merge this conflicting item.

After handling conflicts, the merge continues to completion.

> ⚠️ Merging consolidates multi-platform data; when handling conflicts, think carefully about which one to keep. It's recommended to **back up the database before merging** (done in System Settings, see `17`).

---

## 13.5 Merge Report

After the merge completes, a **merge report** is generated, viewable on the report page:

- **Merge report list**: records of past merges.
- **Report details**: the specific result of a merge — how many merged, how many conflicts, how they were handled, which were skipped.

The report helps you verify the merge met expectations and lets you trace back when problems occur.

---

## 13.6 Typical Flow

1. (Recommended) Back up the database in System Settings first.
2. Data Merge → select multiple source platforms (Ctrl multi-select).
3. Fill in the new platform name + folder path.
4. Start merge → if there are conflicts, enter conflict handling and "Add/Skip" each one.
5. Done → review the merge report to verify results.
6. Go to Platform Management to confirm the new platform; bind a system, scrape, and export as needed.

---

## 13.7 Next Steps

- Back up the database → `17 System Settings & Personalization`
- Export after merging → `10 Data Export`
