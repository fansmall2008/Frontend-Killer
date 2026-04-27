# Quick Start Guide

Get started with Web GameList Oper in just a few minutes!

## Step 1: Start the Application

### Using Docker (Recommended)

First, create necessary directories:
```bash
mkdir -p ./data ./output ./logs ./backup
```

Then run the container:
```bash
docker run -d \
  --name webgamelistoper \
  -p 8081:8080 \
  -v /path/to/output:/data/output \
  -v /path/to/roms:/data/roms \
  -v ./logs:/app/logs \
  -v ./data:/data \
  -v ./backup:/data/backup \
  -e SPRING_PROFILES_ACTIVE=default \
  -e SERVER_TOMCAT_BASEDIR=/data \
  -e SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input \
  -e JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC" \
  --restart unless-stopped \
  fansmall/webgamelistoper:1.0.4-beta
```

**Note:** Replace `/path/to/output` and `/path/to/roms` with your actual paths.

### Using JAR File

```bash
java -jar webGamelistOper-1.0.4-beta.jar
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