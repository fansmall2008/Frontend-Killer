# webGamelistOper Installation Guide

## Requirements

- Docker and Docker Compose
- At least 2GB of RAM
- 50GB of available disk space

## Installation Steps

### Method 1: Docker Deployment (Recommended)

1. Extract the installation package, navigate to the `distribution` directory

2. Edit `docker-compose.yml` to configure your local paths (see Configuration section below)

3. Make sure Docker service is running

4. Execute the following command to start the application:
   ```bash
   docker-compose up -d --build
   ```

5. Wait for the container to start, then access the application:
   ```
   http://localhost:8080
   ```

### Method 2: Run JAR Package Directly

1. Ensure Java 17 or higher is installed

2. Extract the installation package, navigate to the `distribution` directory

3. Create data directories:
   ```bash
   mkdir -p /data/database
   mkdir -p /data/backup
   ```

4. Run the application:
   ```bash
   java -jar webGamelistOper-1.0-beta.jar
   ```

5. Access the application:
   ```
   http://localhost:8080
   ```

## Directory Structure

- `/data/database` - Database file storage directory
- `/data/backup` - Database backup file storage directory
- `/data/roms` - Game ROM files directory
- `/data/output` - Export output directory
- `/data/rules` - Export rules configuration directory
- `/data/input` - Input files directory

## Docker Compose Configuration

The `docker-compose.yml` file contains the following configuration parameters:

```yaml
services:
  webgamelistoper:
    build: .                # Build from Dockerfile
    ports:
      - "8080:8080"         # Web service port mapping
    volumes:
      - ./logs:/app/logs     # Application logs directory
      - /path/to/roms:/data/roms        # Game ROMs directory (required)
      - /path/to/output:/data/output     # Export output directory (required)
      - /path/to/rules:/data/rules       # Export rules directory (required)
      - /path/to/input:/data/input       # Input files directory (required)
    environment:
      - SPRING_PROFILES_ACTIVE=default   # Spring profile
      - SERVER_TOMCAT_BASEDIR=/data      # Tomcat base directory
      - SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input  # Static resources locations
    restart: unless-stopped                # Auto-restart policy
```

### Volume Configuration

| Host Path | Container Path | Description |
|-----------|---------------|-------------|
| ./logs | /app/logs | Application logs |
| /path/to/roms | /data/roms | Game ROM files directory |
| /path/to/output | /data/output | Export output directory |
| /path/to/rules | /data/rules | Export rules configuration |
| /path/to/input | /data/input | Input files directory |

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| SPRING_PROFILES_ACTIVE | Spring profile | default |
| SERVER_TOMCAT_BASEDIR | Tomcat base directory | /data |
| SPRING_RESOURCES_STATIC_LOCATIONS | Static resources locations | classpath:/static/,file:/data,... |

### Customization

Before running `docker-compose up -d --build`, modify the volume paths in `docker-compose.yml` to match your local directories:

- Change `/path/to/roms` to your actual ROMs directory
- Change `/path/to/output` to your desired output directory
- Change `/path/to/rules` to your rules configuration directory
- Change `/path/to/input` to your input files directory

### Windows Path Example

On Windows, use Windows-style paths:
```yaml
volumes:
  - D:\games:/data/roms
  - D:\output:/data/output
  - D:\rules:/data/rules
  - D:\input:/data/input
```

### Linux/Mac Path Example

On Linux or Mac, use Unix-style paths:
```yaml
volumes:
  - /home/user/games:/data/roms
  - /home/user/output:/data/output
  - /home/user/rules:/data/rules
  - /home/user/input:/data/input
```

## Common Commands

### View Container Status
```bash
docker-compose ps
```

### View Logs
```bash
docker-compose logs -f
```

### Stop Application
```bash
docker-compose down
```

### Restart Application
```bash
docker-compose restart
```

### Rebuild and Start
```bash
docker-compose up -d --build
```

## Port Information

- **8080** - Web service port
- **8081** - Adminer database management port (optional)

## Data Management

### Backup Database
Click the "Backup Database" button on the system settings page. Backup files will be saved in the `/data/backup` directory.

### Restore Database
Click the "Restore Database" button on the system settings page, then select an existing backup file to restore.

### Initialize Database
Click the "Initialize" button on the system settings page. This will recreate all table structures and clear all data.

## Troubleshooting

### Q: Container fails to start. What should I do?
A: Check if Docker is running properly and ensure ports 8080 and 8081 are not in use:
```bash
docker-compose logs
```

### Q: What if I lose data?
A: Use the backup function regularly, or mount a host directory to the container:
```yaml
volumes:
  - ./data:/data
```

### Q: How to check application status?
A: Access `http://localhost:8080` to view the web interface, or use:
```bash
docker-compose ps
```

### Q: How to customize directory paths?
A: Edit the volume mappings in `docker-compose.yml` to point to your local directories before running `docker-compose up -d --build`.

## Support

For issues or questions, please visit the project repository:
https://github.com/fansmall2008/webGamelistOper