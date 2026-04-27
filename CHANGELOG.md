# Web GameList Oper v1.0.4-beta Changelog

## Release Date
2026-04-28

## Major Updates

### 🎯 Core Functionality Improvements
- ✅ **Internationalization Support**
  - Added complete multi-language support for all `game-list.html` pages
  - Added internationalization for media files viewing modal
  - Fixed `getI18nText` function global access issue
  - Supports Chinese, English, and Japanese language switching

- ✅ **Unified Path Configuration**
  - Unified `export/import` directory structure
  - Implemented Docker and local environment adaptation via `PathUtil` utility class
  - Fixed `translation-config.json` path loading issue

### 🐳 Docker Deployment Optimization
- ✅ **Image Build Optimization**
  - Updated base image to `openjdk:27-ea-17-jdk-slim`
  - Cleaned logs and database directories during build
  - Set default export path to `/output`
  - Set game ROM path to `/roms`

- ✅ **Container Configuration Updates**
  - Added `SPRING_RESOURCES_STATIC_LOCATIONS` environment variable
  - Added `JAVA_OPTS` memory configuration (-Xmx2g -Xms512m)
  - Updated port mapping to `8081:8080`
  - Added auto-restart policy `--restart unless-stopped`

### 📦 Distribution Package Optimization
- ✅ **Complete Distribution Package**
  - Includes executable JAR file
  - Includes Dockerfile, docker-compose.yml
  - Includes startup scripts (start.bat, entrypoint.sh)
  - Includes multi-language documentation (README.md, INSTALL.md, etc.)
  - Includes complete rules templates

- ✅ **Data Directory Mapping**
  - `/data` - Data directory
  - `/data/roms` - Game ROM directory
  - `/data/output` - Export directory
  - `/data/backup` - Backup directory

### 🔧 Bug Fixes & Improvements
- ✅ **Template Import Garbled Characters**
  - Use UTF-8 encoding when reading template files
  - Fixed garbled characters caused by default `FileReader` encoding

- ✅ **Debug Log Enhancement**
  - Added detailed logging in `ExportRuleServiceImpl`
  - Added debugging information in `GameListParser`
  - Facilitates locating rule file loading issues

- ✅ **Media Files Functionality**
  - Improved internationalization for media files viewing
  - Added multi-language text for images, videos, and manuals

### 📚 Documentation Updates
- ✅ **Multi-language README.md**
  - English version
  - Chinese version
  - Japanese version

- ✅ **Deployment Documentation Updates**
  - Updated Docker deployment commands
  - Updated Docker Compose configuration
  - Updated JAR file running instructions

## File Changes

### New Files
- 📄 `README.md` - Multi-language project documentation
- 📄 `CHANGELOG.md` - Changelog (this file)

### Modified Files
- 📄 `src/main/java/com/gamelist/service/impl/ExportRuleServiceImpl.java` - Added debug logging
- 📄 `src/main/java/com/gamelist/util/PathUtil.java` - Path utility class
- 📄 `src/main/java/com/gamelist/xml/GameListParser.java` - Added UTF-8 encoding handling
- 📄 `src/main/resources/static/game-list.html` - Improved internationalization
- 📄 `Dockerfile` - Image build configuration optimization
- 📄 `distribution/Dockerfile` - Distribution Docker configuration
- 📄 `distribution/entrypoint.sh` - Container startup script
- 📄 `pom.xml` - Project version update

### Configuration Changes
- ⚙️ `.gitignore` - Added new exclusion rules
- ⚙️ Internationalization config `translation-config.json` - Added new entries

## Known Issues
- No known issues

## Deployment Instructions

### Docker Quick Deployment
```bash
docker pull fansmall/webgamelistoper:1.0.4-beta
mkdir -p ./data ./output ./logs ./backup
docker run -d \
  --name webgamelistoper \
  -p 8081:8080 \
  -v ./data:/data \
  -v ./output:/data/output \
  -v ./logs:/app/logs \
  -v ./backup:/data/backup \
  -e SPRING_PROFILES_ACTIVE=default \
  -e SERVER_TOMCAT_BASEDIR=/data \
  -e SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input \
  -e JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC" \
  --restart unless-stopped \
  fansmall/webgamelistoper:1.0.4-beta
```

### Access URL
- Local: http://localhost:8081

## Project Links
- GitHub: https://github.com/fansmall2008/Frontend-Killer
- Docker Hub: https://hub.docker.com/r/fansmall/webgamelistoper

---

**Version:** 1.0.4-beta  
**Author:** fansmall2008