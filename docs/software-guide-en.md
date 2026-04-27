# Frontend Killer - Software Guide

## 1. Software Overview

### 1.1 Product Introduction

**Frontend Killer** is a professional emulator game metadata management platform designed to help users easily manage, import, export, and organize data information for various emulator games. The software supports data import and export for multiple frontend formats, including mainstream emulator frontends such as Pegasus, EmulationStation DE (ES-DE), and RetroBat.

### 1.2 Key Features

- 🕹️ **Multi-format Support**: Supports importing and exporting multiple frontend formats including Pegasus, ES-DE, and RetroBat
- 📁 **Batch Processing**: Supports batch import and export of game data and media files
- 🔄 **Platform Merging**: Supports merging multiple game platforms into a unified management system
- 🌐 **Multi-language Interface**: Supports Chinese, English, and Japanese language interfaces
- 📊 **Data Statistics**: Provides detailed data statistics and reporting functions
- 🎨 **Media Management**: Automatically manages and matches game media files (covers, screenshots, videos, etc.)
- ⚙️ **Flexible Configuration**: Supports custom rule configuration to adapt to different frontend requirements
- 💾 **Database Management**: Built-in H2 database for easy direct viewing and management of data

## 2. Page Structure Overview

### 2.1 Home Page (index.html)

The home page is the entry point for users to enter the system, featuring a game-style cyberpunk design with clear functional navigation.

**Main Function Entries:**

- **Import Module**: Quick access to data import functionality
- **Data Processing**: Access platform management and data statistics
- **Data Merging**: Access platform merging tools
- **Export Module**: Access data export functionality
- **System Settings**: Access system configuration, H2 database console, task management, etc.

**Top Functions:**

- 🌍 **Language Selector**: Supports switching interface language between Chinese, English, and Japanese
- 🔔 **Notification Bell**: Displays system task messages and notifications

### 2.2 Import Page (data-import.html)

The import functionality allows users to import data files from external frontends into the system database.

**Core Features:**

- **Multi-format Data File Import**
  - Supports XML format (e.g., ES-DE's gamelist.xml)
  - Supports text format (e.g., Pegasus's metadata.pegasus.txt)
  - Automatic file encoding and format recognition

- **Intelligent Field Mapping**
  - Automatically matches external fields with system fields
  - Supports multi-value field processing
  - Flexible field mapping configuration

- **Media File Import**
  - Automatically scans and imports game media files
  - Supports multiple media types (covers, screenshots, videos, etc.)
  - Intelligent media file matching rules

- **Import Template Management**
  - Preset multiple import templates (ES-DE, Pegasus, etc.)
  - Supports custom import templates
  - Template online editing and configuration

**Usage Process:**

1. Select import data source (directory or file)
2. Select import template
3. Configure import parameters
4. Execute import and view results

### 2.3 Platform Management Page (platform-management.html)

Platform management is the core data management module of the system, providing platform CRUD operations and detailed statistical data.

**Core Features:**

- **Platform Information Management**
  - Add, edit, and delete game platforms
  - Set platform basic information (name, description, icon, etc.)
  - Configure platform-specific parameters

- **Game List View**
  - View all game lists on the platform
  - Support game search and filtering
  - View game details and media files

- **Data Statistics Panel**
  - Platform game total statistics
  - Media file coverage statistics
  - Data integrity analysis
  - Visual data display

- **Game Data Editing**
  - Edit game basic information
  - Update game metadata
  - Manage game media file associations

### 2.4 Platform Details Page (platform-details.html)

Displays detailed information and game content of a single platform.

**Main Content:**

- Platform basic information display
- Platform statistics
- Game list and quick operations
- Platform configuration options

### 2.5 Game List Page (game-list.html)

Displays a list view of all games on the platform.

**Feature Highlights:**

- Game list display (card/list view switching)
- Game search and filtering
- Game quick operations (edit, delete, export, etc.)
- Batch selection and operations

### 2.6 Platform Merge Page (platform-merge.html)

A powerful platform merging tool used to integrate data from multiple platforms into one.

**Core Features:**

- **Merge Source Selection**
  - Select two or more source platforms
  - Preview game data for each platform
  - View platform statistics

- **Merge Configuration**
  - Set target platform name
  - Select merge strategy (skip/overwrite/merge)
  - Configure conflict handling rules

- **Conflict Detection and Resolution**
  - Automatically detect duplicate games
  - Display conflict details
  - Provide conflict resolution options

- **Merge Report**
  - Generate detailed merge report
  - Record operations during the merge process
  - Support merge rollback (if needed)

### 2.7 Merge Conflicts Page (merge-conflicts.html)

Displays conflict information found during the merge process.

**Conflict Types:**

- Game duplication conflicts
- Field value conflicts
- Media file conflicts

**Resolution Options:**

- Keep source platform data
- Use target platform data
- Manually merge data
- Skip conflict items

### 2.8 Merge Reports Page (merge-reports.html)

View detailed reports of historical merge operations.

**Report Content:**

- Merge operation summary
- Number of games processed
- Conflict resolution records
- Merge timeline

### 2.9 Merge Report Details Page (merge-report-details.html)

View complete detailed information of a single merge report.

### 2.10 Export Page (export.html)

Export game data from the system to various frontend formats.

**Core Features:**

- **Multi-frontend Format Support**
  - Pegasus format export
  - EmulationStation DE (ES-DE) format export
  - RetroBat format export
  - Custom format templates

- **Export Content Selection**
  - Game data file export
  - Game ROM file copy
  - Media file export (covers, screenshots, videos, etc.)

- **Export Rule Configuration**
  - Custom export rules
  - Path template configuration
  - Field mapping settings

- **Real-time Export Progress**
  - Export progress bar display
  - Detailed log output
  - Error prompts and retries

**Export Process:**

1. Select source platform
2. Select target frontend format
3. Configure export options
4. Set export path
5. Execute export and view results

### 2.11 System Settings Page (system-settings.html)

Configure and manage various system parameters.

**Configuration Items:**

- **Database Configuration**
  - Database connection settings
  - Data backup and recovery
  - Database initialization

- **System Parameters**
  - System base path settings
  - Backup path configuration
  - Temporary file management

- **Advanced Settings**
  - Debug mode switch
  - Log level configuration
  - Performance parameter adjustment

**Database Operations:**

- Backup current database
- Restore database to specified version
- Initialize database structure
- View database status

### 2.12 H2 Database Console

Provides direct database access and management interface.

**Features:**

- View all data tables
- Execute SQL queries
- Manage and modify data
- Table structure viewing

**Access Address:** `/h2-console`

**Default Configuration:**

- JDBC URL: `jdbc:h2:file:/data/database`
- Username: `sa`
- Password: (default empty)

### 2.13 Task Management Page (task-management.html)

Manage and monitor system background task execution.

**Task Types:**

- 📥 **Import Tasks**: Data import operations
- 📤 **Export Tasks**: Data export operations
- 🌐 **Translation Tasks**: Game information translation tasks
- 🔄 **Merge Tasks**: Platform merge operations

**Feature Highlights:**

- Task list display
- Task status monitoring
- Task progress tracking
- Task log viewing
- Task cancellation and retry

### 2.14 Log Viewer Page (log-viewer.html)

View system running logs.

**Features:**

- Real-time log viewing
- Log level filtering
- Log search
- Log export

### 2.15 Temporary Subset Edit Page (temp-subset-edit.html)

Used for editing temporary game subset data.

**Purpose:**

- Batch edit selected games
- Temporarily save edit results
- Preview modification effects

## 3. Functional Module Details

### 3.1 Data Import Module

#### 3.1.1 Supported Formats

| Format Type | File Format | Applicable Frontend |
|------------|------------|-------------------|
| XML Format | gamelist.xml | ES-DE, RetroBat |
| Text Format | metadata.pegasus.txt | Pegasus |
| JSON Format | Custom | Custom Templates |

#### 3.1.2 Import Process

```
Data Source Selection → Template Matching → Field Mapping → Data Validation → Import Execution → Result Feedback
```

#### 3.1.3 Media File Processing

- Automatic media directory scanning
- Intelligent media file matching
- Support for multiple image and video formats
- Media file deduplication and optimization

### 3.2 Data Processing Module

#### 3.2.1 Platform Management

- Platform CRUD operations
- Platform configuration management
- Platform statistics information

#### 3.2.2 Game Management

- Game information editing
- Batch operation support
- Media file association
- Game data validation

#### 3.2.3 Data Statistics

- Game total statistics
- Platform comparison analysis
- Media coverage analysis
- Data integrity check

### 3.3 Data Merging Module

#### 3.3.1 Merge Strategies

- **Intelligent Merge**: Automatically handle duplicate data
- **Complete Overwrite**: Completely replace old data with new data
- **Selective Merge**: Manually select which data to keep
- **Only Merge New**: Only add games that don't exist

#### 3.3.2 Conflict Handling

- Automatically detect duplicate games
- Display conflict details
- Provide resolution suggestions
- Support manual resolution

### 3.4 Data Export Module

#### 3.4.1 Export Options

- ✅ Game data files
- ✅ Game ROM files
- ✅ Media files
- ✅ Platform configuration files

#### 3.4.2 Path Template Variables

- `{platform}` - Platform name
- `{gameName}` - Game name
- `{outputPath}` - Export root path
- `{mediaPath}` - Media file path
- `{romsPath}` - ROM file path

#### 3.4.3 Data File Formats

Supports generating standard format files that meet the requirements of each frontend, including XML and plain text formats.

## 4. Usage Process Guide

### 4.1 First-time Usage Process

1. **Access System**: Open browser to access system address
2. **Language Settings**: Switch interface language as needed
3. **Configure System**: Configure database and paths in system settings
4. **Start Using**: Import or manually create platform data

### 4.2 Daily Usage Process

#### Importing New Game Data

1. Enter "Import" page
2. Select data source directory
3. Select appropriate import template
4. Configure import parameters
5. Execute import and view results

#### Managing and Editing Games

1. Enter "Data Processing" page
2. Select platform to manage
3. View game list
4. Edit game information
5. Save changes

#### Exporting Game Data

1. Enter "Export" page
2. Select source platform
3. Select target frontend format
4. Configure export options
5. Execute export

### 4.3 Advanced Operation Process

#### Merging Platforms

1. Enter "Data Merging" page
2. Select source platforms (multiple selection)
3. Configure merge options
4. Process merge conflicts
5. Execute merge and view report

#### Custom Import/Export Templates

1. Refer to template configuration documentation
2. Create new template JSON file
3. Configure field mapping rules
4. Set media file matching rules
5. Save and test template

## 5. Configuration File Description

### 5.1 Import Template Configuration

Import templates are stored in the `/data/rules/import-templates/` directory, mainly including:

- **ES-DE Template** (`esde.json`): Suitable for EmulationStation DE format
- **Pegasus Template** (`pegasus.json`): Suitable for Pegasus frontend format
- **Generic Template** (`generic.json`): Suitable for generic format

### 5.2 Export Rule Configuration

Export rules are stored in the `/data/rules/export-rules/` directory, mainly including:

- **esde.json**: ES-DE export rules
- **pegasus.json**: Pegasus export rules
- **retrobat.json**: RetroBat export rules
- **template.json**: Generic export template

### 5.3 Translation Configuration

Translation API configuration is stored in `/data/rules/translation-config.json`, used to configure machine translation services.

## 6. Directory Structure Description

### 6.1 Docker Deployment Directory Structure

```
/app/
├── app.jar                 # Application JAR package
├── default-rules/         # Default rule files
│   ├── export-rules/      # Export rules
│   ├── import-templates/  # Import templates
│   └── translation-config.json  # Translation configuration
└── logs/                  # Log directory

/data/
├── backup/               # Database backup directory
├── database/            # H2 database files
├── input/               # Input file directory
├── logs/                # Application logs
├── output/              # Export output directory
├── roms/                # Game ROM directory
└── rules/               # Rule files directory (mount point)
    ├── export-rules/
    ├── import-templates/
    └── translation-config.json
```

## 7. Technical Architecture

### 7.1 Technology Stack

- **Backend**: Java + Spring Boot
- **Frontend**: HTML5 + CSS3 + JavaScript
- **Database**: H2 Database
- **Server**: Apache Tomcat
- **Containerization**: Docker + Docker Compose

### 7.2 System Requirements

- JDK 17+
- 2GB+ RAM
- 10GB+ available disk space
- Docker (optional)

## 8. Frequently Asked Questions

### 8.1 Import Related

**Q: What if it says file format not supported during import?**
A: Please check if the file format is supported (XML, text, etc.) and ensure the file encoding is UTF-8.

**Q: What if media files are not matched correctly?**
A: Check the mediaRules configuration in the import template to ensure the path template matches the actual media file structure.

### 8.2 Export Related

**Q: What if the exported files don't meet frontend requirements?**
A: You can customize the export rule configuration file to adjust field mappings and data formats. Please refer to the export functionality documentation for details.

**Q: What if media files are not exported?**
A: Check the media rule settings in the export configuration to ensure media files exist and path configuration is correct.

### 8.3 System Related

**Q: How to backup the database?**
A: Click the "Backup Database" button on the system settings page, and the system will backup the database to the `/data/backup` directory.

**Q: How to restore the database?**
A: Select the backup file to restore on the system settings page and click the "Restore Database" button.

**Q: What if the H2 database console cannot connect?**
A: Check if the H2 console JDBC URL configuration is correct, the default should be `jdbc:h2:file:/data/database`.

## 9. Getting Help

### 9.1 Documentation Resources

- 📘 **Import Template Configuration Documentation**: `import-templates-en.md` (English)
- 📗 **Export Functionality Configuration Documentation**: `export-functionality-en.md` (English)
- 📙 **Installation and Deployment Documentation**: `INSTALL.md`

### 9.2 Technical Support

- Submit GitHub Issue
- View project Wiki
- Contact development team

## 10. Version Information

**Current Version**: 1.0.2-beta
**Last Updated**: 2026-04-26

## 11. Docker Deployment Guide

### 11.1 Deploy using docker-compose (Recommended)

```bash
# Create data directories
mkdir -p logs roms output rules input backup database

# Start services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

### 11.2 Deploy using docker run

```bash
# Create data directories
mkdir -p logs roms output rules input backup database

# Run container
docker run -d \
  --name webgamelistoper \
  -p 8080:8080 \
  -v $(pwd)/logs:/data/logs \
  -v /path/to/roms:/data/roms \
  -v $(pwd)/output:/data/output \
  -v $(pwd)/rules:/data/rules \
  -v $(pwd)/backup:/data/backup \
  -v $(pwd)/database:/data/database \
  -e SPRING_PROFILES_ACTIVE=default \
  -e SERVER_TOMCAT_BASEDIR=/data \
  -e JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC" \
  -e SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input \
  --restart unless-stopped \
  fansmall/webgamelistoper:1.0.2-beta
```

### 11.3 Common Docker Commands

```bash
# View container status
docker ps -a | grep webgamelistoper

# View logs
docker logs -f webgamelistoper

# Enter container
docker exec -it webgamelistoper bash

# Stop container
docker stop webgamelistoper

# Remove container
docker rm webgamelistoper

# Restart container
docker restart webgamelistoper
```

### 11.4 Docker Hub Image

- **Repository**: https://hub.docker.com/r/fansmall/webgamelistoper
- **Version Tag**: `fansmall/webgamelistoper:1.0.2-beta`

---

**© 2026 Frontend Killer | Emulator Game Metadata Management Platform**