# 10 · Data Export

> The last step for an organized game library is exporting it into a format your target frontend can use directly. This chapter covers how to choose platforms, frontends, and export content, and how to handle the "preflight" prompt before exporting.

**Entry**: sidebar **Data Export** 📤 (DATA group)

---

## 10.1 The Four Elements of the Export Form

The export page is a form with four blocks top to bottom:

1. **Select platforms** (multi-select)
2. **Select frontend** (determines export format)
3. **Export scope** (check what to export)
4. **Export path** (where to export)

Fill them in and click **Export**.

---

## 10.2 Select Platforms (multi-select supported)

- This is a **searchable multi-select dropdown**: type keywords to filter platforms, check multiple, with "select all" and a selected count (e.g., 3/10).
- **When multiple are selected**: export **generates a separate subdirectory per platform**, without interference.
- To export just one platform, select one.

---

## 10.3 Select Frontend (determines format)

- Pick the target frontend from the "Select frontend" dropdown (Pegasus, ES-DE, RetroBat, EmuELEC, RetroArch, etc.).
- After selecting, a **description** of that frontend/template appears below to help you confirm the choice.
- If the template has **limitations** (e.g., doesn't support certain fields/media), a yellow **"⚠️ template limitation notice"** shows what content will be restricted on export.

> Choosing a frontend is essentially choosing an **export template**, which defines the data file format, directory structure, and how media is arranged. Details in `18 Template System Guide`.

---

## 10.4 Export Scope (check what to export)

Three checkboxes, **all checked by default**:

| Option | Meaning |
|---|---|
| **Game files** | Copy ROM files to the export directory |
| **Media files** | Copy over box art, screenshots, videos, etc. |
| **Data files** | Generate the data file the frontend uses (e.g., `gamelist.xml`, `metadata.pegasus.txt`) |

Combine as needed:
- Only want data files, with ROMs and media already elsewhere → check only "Data files".
- Want a complete, portable library → check all three.

> After selecting certain frontends, an **export option notice** may appear below, explaining the template's special handling of these items — take note.

---

## 10.5 Export Path

- Enter the output directory; default `/data/output`.
- Export results go here (one subdirectory per platform when multiple).

---

## 10.6 The "Preflight" Check Before Export

After clicking export, the software may first run a **preflight** check, verifying export conditions and suggesting an action. Common suggestion types:

| Suggestion | Meaning | What you should do |
|---|---|---|
| **proceed** | No problem, export directly | Confirm and continue |
| **split** | Lots of content / complex structure; suggests splitting the export | Split per the prompt |
| **suggest_whole_dir** | Suggests exporting as a whole directory | Switch to whole-directory per the prompt |

Preflight exists to avoid export results that don't meet frontend expectations. **Just follow the prompt**; when unsure, choose the more conservative option.

> The preflight wording may not be plain enough for beginners — a potential guidance point we've recorded.

---

## 10.7 Template Variable Dialog

As with import, if the export template declares **required variables** (e.g., ROM subfolder name, URL prefix), clicking export pops a dialog for you to fill.

- Those marked "required" can't be empty, or submission fails (the backend also blocks it and errors).
- **Multi-platform batch export pops the dialog only once**; all platforms **share the same values**; per-platform differences are resolved automatically by `{platform.xxx}` placeholders in the template.
- Variable values aren't saved; re-enter each export.

---

## 10.8 Start Export & View Results

- After clicking "Export", the task enters the background queue; watch progress in **Task Management** 📋 (see `14`); export tasks are usually cancellable.
- When done, check the export path: there should be data files + (if checked) ROMs and media, arranged per platform/template rules.
- Copy the output directory to your target frontend to use.

---

## 10.9 Common Issues

| Symptom | Possible cause | What to do |
|---|---|---|
| No data file exported | Didn't check "Data files" | Check it and re-export |
| Media not exported | Didn't check "Media files" / game has no media | Check it; first confirm media is downloaded (`12`) |
| Wrong path / frontend doesn't recognize | Wrong template, directory structure mismatch | Use the right frontend template; read the limitation notice |
| Required-variable error | Variable box left empty | Fill in all required variables |

More in `19`.

---

## 10.10 Next Steps

- The underlying rules of export formats → `18 Template System Guide`
- Export task control → `14 Task Management`
