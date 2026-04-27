# Configuration

This guide explains how to configure Web GameList Oper.

## Configuration Files

### application.properties

Main application configuration file located at `src/main/resources/application.properties`.

#### Server Settings
```properties
server.port=8080
server.tomcat.basedir=/data
```

#### Data Paths
```properties
app.data.path=${DATA_PATH:/data}
app.output.path=${OUTPUT_PATH:/output}
app.roms.path=${ROMS_PATH:/data/roms}
```

#### Database Settings
```properties
spring.datasource.url=jdbc:sqlite:${app.data.path}/database/example_db.sqlite
spring.datasource.driver-class-name=org.sqlite.JDBC
spring.datasource.username=admin
spring.datasource.password=password
```

#### Static Resources
```properties
spring.resources.static-locations=classpath:/static/,file:${app.data.path},file:${app.roms.path},file:${app.output.path}
```

## Environment Variables

| Variable | Description | Default Value |
|----------|-------------|---------------|
| `APP_PORT` | Service port | 8080 |
| `DATA_PATH` | Data directory | /data |
| `OUTPUT_PATH` | Export output directory | /output |
| `ROMS_PATH` | Game ROMs directory | /data/roms |
| `SPRING_PROFILES_ACTIVE` | Active profile | default |
| `SERVER_TOMCAT_BASEDIR` | Tomcat base directory | /data |
| `JAVA_OPTS` | Java VM options | -Xmx2g -Xms512m |

## Rules Configuration

### Directory Structure
```
data/rules/
├── export/          # Export rules
│   ├── pegasus.json
│   └── retrobat.json
├── import/          # Import templates
│   ├── pegasus.json
│   └── retrobat.json
└── translation-config.json  # Translation configuration
```

### Export Rules Format

Example `pegasus.json`:
```json
{
  "frontend": "pegasus",
  "name": "Pegasus Frontend",
  "exportPath": "pegasus",
  "gameListFile": "game_list.xml",
  "mediaDirectories": {
    "boxFront": "media/boxfront",
    "boxBack": "media/boxback",
    "screenShot": "media/screenshots",
    "video": "media/videos"
  }
}
```

### Translation Configuration

The `translation-config.json` file contains language translations:

```json
{
  "zh": {
    "game-name": "游戏名称",
    "game-path": "游戏路径"
  },
  "en": {
    "game-name": "Game Name",
    "game-path": "Game Path"
  },
  "ja": {
    "game-name": "ゲーム名",
    "game-path": "ゲームパス"
  }
}
```

## Docker Specific Configuration

### Volume Mounts
- `/data` - Application data (rules, database, etc.)
- `/data/output` - Export output directory
- `/data/roms` - Game ROMs directory
- `/app/logs` - Application logs
- `/data/backup` - Backup files

### Docker Compose Example

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
      - JAVA_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC
    restart: unless-stopped
```

## JAR File Configuration

### Command Line Arguments

```bash
java -jar webGamelistOper-1.0.4-beta.jar \
  --server.port=8081 \
  --app.data.path=/custom/data \
  --app.output.path=/custom/output
```

### System Properties

```bash
java -Dserver.port=8081 \
     -Dapp.data.path=/custom/data \
     -jar webGamelistOper-1.0.4-beta.jar
```

## Logging Configuration

### Log Levels
- DEBUG
- INFO
- WARN
- ERROR

### Log File Location
Logs are stored in:
- Docker: `/app/logs/`
- Local: `./logs/`

### Log Format
```
[timestamp] [thread] [level] [class] - message
```

## Advanced Configuration

### Memory Settings
```bash
java -Xmx2g -Xms512m -jar webGamelistOper-1.0.4-beta.jar
```

### G1GC Garbage Collector
```bash
java -XX:+UseG1GC -jar webGamelistOper-1.0.4-beta.jar
```

### Remote Debugging
```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -jar webGamelistOper-1.0.4-beta.jar
```