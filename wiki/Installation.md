# Installation Guide

This guide covers multiple ways to install and run Frontend-Killer.

## Prerequisites

- Docker (recommended) or Java 17+
- Minimum 2GB RAM recommended

## Option 1: Docker (Recommended)

### Quick Start

```bash
# Pull the latest image
docker pull fansmall/frontendkiller:latest

# Create necessary directories
mkdir -p ./data ./roms

# Run the container
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

### Docker Compose

Create a `docker-compose.yml` file:

```yaml
services:
  frontend-killer:
    image: fansmall/frontendkiller:latest
    container_name: frontend-killer
    ports:
      - "8081:8080"
    volumes:
      - ./data:/data
      - /path/to/roms:/data/roms
    environment:
      - SPRING_PROFILES_ACTIVE=default
      - SERVER_TOMCAT_BASEDIR=/data
      - SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input
      - JAVA_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC
      - PUID=0
      - PGID=0
      - TZ=Asia/Shanghai
    restart: unless-stopped
```

Run with:
```bash
docker-compose up -d
```

### unRAID (Community Applications)

Frontend-Killer ships an unRAID template at `unraid/frontend-killer.xml`. Install it from Community Applications, or add it manually via Docker → Add Container with image `fansmall/frontendkiller:latest`, port `8080`, volumes `<appdata>:/data` and `<roms>:/data/roms`, and set `PUID=99` / `PGID=100` for correct ownership on unRAID shares.

## Option 2: JAR File

### Requirements

- Java 17 or higher

### Running

```bash
# Create necessary directories
mkdir -p ./data/rules/export ./data/rules/import ./output ./logs

# Run the JAR file
java -jar webGamelistOper-1.1-RC1.jar
```

### Configuration

You can override default settings using environment variables:

```bash
java -jar webGamelistOper-1.1-RC1.jar \
  --server.port=8081 \
  --app.data.path=/custom/data/path
```

## Access

Once running, access the application at:
- **Local**: http://localhost:8081

## Directory Structure

```
./
├── data/           # Application data (persisted volume, mounted at /data)
│   ├── database/   # H2 database
│   ├── rules/      # Export/import rules
│   ├── scraper/    # Scraper media cache
│   ├── input/      # Import staging
│   ├── output/     # Export output
│   ├── logs/       # Application logs
│   └── backup/     # Backup files
└── roms/           # Game ROMs (mounted at /data/roms)
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `PUID` / `PGID` | UID/GID the app runs as (use 99/100 on unRAID) | 0 / 0 |
| `TZ` | Container timezone | Asia/Shanghai |
| `JAVA_OPTS` | JVM options | -Xmx2g -Xms512m |
| `SPRING_PROFILES_ACTIVE` | Spring profile | default |
| `SERVER_TOMCAT_BASEDIR` | Tomcat base directory | /data |
| `SPRING_RESOURCES_STATIC_LOCATIONS` | Static & media resource locations | classpath:/static/,file:/data,... |