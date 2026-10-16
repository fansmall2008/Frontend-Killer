# 02 · Quick Start

> This chapter takes you from zero to a running instance and through the first basic configuration. Just follow along — no programming background required.

---

## 2.1 What You Need

- **A machine that can run a service**: a PC, NAS, or server (Windows / macOS / Linux all work).
- **Your game ROM directory**: the batch of game files you want to manage; where you put them is up to you.
- **A ScreenScraper account (optional but strongly recommended)**: it works without one (guest mode), but there's a daily request cap; registering and entering an account gives you a much higher quota. Sign up at `https://www.screenscraper.fr`.
- **Java 17+**: only needed if you choose the "run from JAR" method. With Docker or the Windows EXE you don't need to worry about Java.

---

## 2.2 Four Deployment Methods — Pick One

Once running, the software listens on **port 8080**; you access it with a browser. Comparison of the four methods:

| Method | Best for | Pros | Notes |
|---|---|---|---|
| **Docker (recommended)** | NAS / server / any OS | Clean isolation, easy upgrades, built-in health check | Must correctly mount `data` and `roms` directories |
| **Docker Compose** | One-command start, centralized config | Starts the service with one command | Watch port usage and paths |
| **JAR** | Machines that already have Java | No Docker dependency | You handle logs/backups/permissions yourself |
| **Windows EXE** | Windows users who don't want Java | Double-click to run | Download the matching version from the release page |

Details below. The default access URL for all of them is:

```
http://localhost:8080
```

(If you change the port mapping — say to host port 8081 — then access `http://localhost:8081`.)

---

### Method A: Docker (recommended)

**Step 1: Prepare directories**
Create two folders where you want the data:
- `./data`: application data (database, rules, logs, output, etc.)
- `./roms`: your game ROMs

```bash
mkdir -p ./data ./roms
```

**Step 2: Pull the image**
```bash
docker pull fansmall/frontendkiller:latest
```

**Step 3: Start the container**
```bash
docker run -d \
  --name frontend-killer \
  -p 8080:8080 \
  -v ./data:/data \
  -v ./roms:/data/roms \
  -e SPRING_PROFILES_ACTIVE=default \
  -e SERVER_TOMCAT_BASEDIR=/data \
  -e SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input \
  -e JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC" \
  -e PUID=0 -e PGID=0 -e TZ=Asia/Shanghai \
  --restart unless-stopped \
  fansmall/frontendkiller:latest
```

Key parameters:

| Parameter | Purpose |
|---|---|
| `-p 8080:8080` | Maps container port 8080 to host 8080 |
| `-v ./data:/data` | Data directory mount (database, rules, etc. live here — keep it!) |
| `-v ./roms:/data/roms` | Mounts your ROM directory into the container |
| `SPRING_RESOURCES_STATIC_LOCATIONS` | Allows web access to files in roms/output/input |
| `JAVA_OPTS` | Memory parameters, tune to your machine |
| `PUID`/`PGID` | Container runtime user, common on unRAID/NAS |
| `TZ` | Time zone |

**Success indicator**: `docker ps` shows `frontend-killer` with status `Up`, and opening `http://localhost:8080` in a browser shows the home page.

---

### Method B: Docker Compose

The project root already provides a `docker-compose.yml` (default container name `webgamelistoper`, port 8080, mounts `./data` and `./roms`, includes a health check).

```bash
docker compose up -d        # start
docker logs -f webgamelistoper   # view logs
```

After you see the Spring Boot startup logs exposing port 8080, visit `http://localhost:8080` in a browser.

---

### Method C: Run from JAR

Prerequisites: **Java 17+** installed, and `webGamelistOper-1.2.jar` downloaded.

```bash
java -jar webGamelistOper-1.2.jar
```

In JAR mode, data directories are relative to the "current working directory": database at `./data/database`, logs at `./logs`, rules at `./data/rules`, input/output at `./data/input` and `./data/output`.

---

### Method D: Windows EXE

1. Download the EXE installer from GitHub Releases.
2. Extract it.
3. Double-click `Frontend-Killer.exe`.
4. Once the program is up, visit `http://localhost:8080` in a browser.

---

## 2.3 Seven Basic Configuration Steps After First Launch

Once the service runs and the browser opens the home page, go through the following in order to complete the journey from zero to usable. (Each feature has a dedicated chapter later; here we just walk the main line.)

### Step 1: Visit the home page
Open `http://localhost:8080` in a browser. You'll see the dashboard with several feature cards (Import, Platform Management, Export, Task Management, etc.), and possibly a notification bell in the top-right.

### Step 2: Set the language
Go to **System Settings**, choose 中文 / English / 日本語 under "Select language", save, then refresh the page for it to take effect.

### Step 3: Configure ScreenScraper (critical)
This is the prerequisite for automatic scraping — **strongly configure it now**.

1. Go to **System Settings** and find "ScreenScraper Settings".
2. Enter your **username** and **password** (your ScreenScraper account).
3. Click "Save settings".
4. Click "Test connection" to confirm the credentials work.
5. Click "Fetch system list" to pull down the scrapable console/handheld/arcade system data.

> It works without an account (guest mode), but the daily request cap is low and scraping many games easily hits rate limits.
> ⚠️ If you click scrape without configured credentials, the system silently skips it — you might wonder "why nothing happened". So configure this first.

### Step 4: Import game data
1. Go to **Data Import**.
2. Choose an import template (Pegasus, RetroBat, ES-DE, etc. — pick the one matching your data source).
3. Select a game list file, or scan a ROM directory directly.
4. Optionally check "match unrecorded media files".
5. Click "Start scan and import".

After import, statistics are shown, and you'll see the new platform in "Platform Management".

> Not sure which template? See `09 Data Import` and `18 Template System Guide`.

### Step 5: Bind a system and scrape
1. Go to **Platform Management**.
2. Find your platform, click "Edit", **pick a matching ScreenScraper system**, and save.
3. Click the platform row's "Scrape" button, confirm the media scope in the dialog, and click "Start scraping".
4. The scraping task enters the background queue — check progress on the **Task Management** page.

> Tip: When you open a platform's game list and it isn't bound to a system yet, the software automatically pops up the "system matching guide" to help you choose (see `06 Platform Management`).

### Step 6: Edit games (optional)
1. In Platform Management, click a platform name to enter its **Game List**.
2. Click a game to enter **Game Editing**.
3. Change name, description, translation, etc.; you can also click "Search game" to manually supplement ScreenScraper data.
4. Click "Save changes".

### Step 7: Export data
1. Go to **Data Export**.
2. Select the platform(s) to export.
3. Choose the target frontend format (Pegasus, ES-DE, etc.).
4. Check what to export: game files / media files / data files.
5. Specify the output directory and click "Export".

After export, the output directory will contain data files (e.g., `gamelist.xml`) and related media, ready for the frontend. Check task status in **Task Management**.

---

## 2.4 Won't Start? Self-Check These First

| Symptom | Common cause | What to do |
|---|---|---|
| Browser can't open `localhost:8080` | Port occupied / wrong mapping / container not up | `docker ps` for status; `docker logs frontend-killer` for errors; on port conflict use `-p 8081:8080` and access 8081 |
| No data/rules after Docker starts | On first launch `/data` is empty and initial data is auto-released | Check logs to confirm the entrypoint copied rules; verify `/data` exists |
| ScreenScraper connection fails | Wrong credentials / no network / quota exhausted | Re-enter credentials → test connection; confirm you can reach screenscraper.fr; register an account to raise quota if needed |
| Can't find ROMs/media after import | ROM dir not mounted / template doesn't match directory structure | Confirm `-v ./roms:/data/roms`; confirm the right template; confirm static resource paths include the roms dir |
| Database corrupted / want to reset | —— | System Settings offers "backup/restore/initialize". **Initialize wipes data and is irreversible — always back up first** |

For more complete troubleshooting, see `19 FAQ & Troubleshooting`.

---

## 2.5 Next Steps

- Get to know each page → `03 Interface & Navigation`
- Walk the full main line → `04 End-to-End Workflow`
