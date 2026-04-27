# Installation Guide

This guide covers multiple ways to install and run Web GameList Oper.

## Prerequisites

- Docker (recommended) or Java 17+
- Minimum 2GB RAM recommended

## Option 1: Docker (Recommended)

### Quick Start

```bash
# Pull the latest image
docker pull fansmall/webgamelistoper:1.0.4-beta

# Create necessary directories
mkdir -p ./data ./output ./logs ./backup

# Run the container
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

### Docker Compose

Create a `docker-compose.yml` file:

```yaml
version: '3.8'
services:
  webgamelistoper:
    image: fansmall/webgamelistoper:1.0.4-beta
    container_name: webgamelistoper
    ports:
      - "8081:8080"
    volumes:
      - ./data:/data
      - ./output:/data/output
      - ./logs:/app/logs
      - ./backup:/data/backup
    environment:
      - SPRING_PROFILES_ACTIVE=default
      - SERVER_TOMCAT_BASEDIR=/data
      - SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input
      - JAVA_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC
    restart: unless-stopped
```

Run with:
```bash
docker-compose up -d
```

## Option 2: JAR File

### Requirements

- Java 17 or higher

### Running

```bash
# Create necessary directories
mkdir -p ./data/rules/export ./data/rules/import ./output ./logs

# Run the JAR file
java -jar webGamelistOper-1.0.4-beta.jar
```

### Configuration

You can override default settings using environment variables:

```bash
java -jar webGamelistOper-1.0.4-beta.jar \
  --server.port=8081 \
  --app.data.path=/custom/data/path
```

## Access

Once running, access the application at:
- **Local**: http://localhost:8081

## Directory Structure

```
./
├── data/           # Application data
│   ├── rules/      # Export/import rules
│   ├── roms/       # Game ROMs
│   └── database/   # SQLite database
├── output/         # Export output
├── logs/           # Application logs
└── backup/         # Backup files
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `APP_PORT` | Service port | 8080 |
| `DATA_PATH` | Data directory | /data |
| `OUTPUT_PATH` | Export output directory | /output |
| `ROMS_PATH` | Game ROMs directory | /data/roms |
| `JAVA_OPTS` | Java options | -Xmx2g -Xms512m |