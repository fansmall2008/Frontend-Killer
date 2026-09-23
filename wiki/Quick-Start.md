# Quick Start Guide

Get started with Frontend-Killer in just a few minutes!

## Step 1: Start the Application

### Using Docker (Recommended)

First, create necessary directories:
```bash
mkdir -p ./data ./roms
```

Then run the container:
```bash
docker run -d \
  --name frontend-killer \
  -p 8081:8080 \
  -v ./data:/data \
  -v /path/to/roms:/data/roms \
  -e SPRING_PROFILES_ACTIVE=default \
  -e SERVER_TOMCAT_BASEDIR=/data \
  -e SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input \
  -e JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC" \
  -e PUID=0 -e PGID=0 -e TZ=Asia/Shanghai \
  --restart unless-stopped \
  fansmall/frontendkiller:latest
```

**Note:** Replace `/path/to/output` and `/path/to/roms` with your actual paths.

### Using JAR File

```bash
java -jar webGamelistOper-1.2.jar
```

## Step 2: Access the Application

Open your browser and navigate to:
```
http://localhost:8081
```

## Step 3: Import Game Data

1. Click **Data Import** from the navigation menu
2. Select the import template (Pegasus, RetroBat, etc.)
3. Choose your game list XML file
4. Click **Start Import**

## Step 4: Manage Games

Once imported, you can:

- **View Games**: Browse your game library
- **Edit Games**: Click the edit button to modify game details
- **Add Games**: Click the "+ New Game" button
- **Delete Games**: Select games and click delete

## Step 5: Export Game Data

1. Click **Export** from the navigation menu
2. Select the export format (Pegasus, RetroBat, etc.)
3. Choose the platforms/games to export
4. Click **Start Export**
5. Find exported files in the `output` directory

## Step 6: Translate Games (Optional)

1. Select games from the game list
2. Click **Batch Translate**
3. Choose target language
4. Click **Translate**

## Quick Tips

- **Language Switch**: Use the dropdown in the top-right corner to switch languages
- **Search**: Use the search bar to quickly find games
- **Filters**: Use filters to narrow down your game list
- **Media Files**: Click "View Media Files" to see game images/videos

## Next Steps

- [Features](Features) - Learn about all features
- [Configuration](Configuration) - Customize settings
- [Troubleshooting](Troubleshooting) - Fix common issues