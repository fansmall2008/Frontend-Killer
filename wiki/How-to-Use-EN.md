# User Guide: Data Processing Pipeline

This guide introduces the complete workflow from data acquisition to export, based on a **data pipeline** approach.

---

## 📥 Stage 1: Data Acquisition

### 1.1 Three Ways to Import Games

The software supports three methods to import games. Choose the most suitable one based on your situation.

#### Method 1: Traditional Scan

**Supported Formats**: Pegasus, gamelist.xml

**Media File Matching**: Uses hardcoded rules

**Features**: Slower speed, fixed rules

---

#### Method 2: JSON Template Scan (Recommended)

**Supported Formats**: Custom templates for multiple platforms

**Media File Matching**: Uses template-defined rules, flexible and configurable

**Features**:
- Highly flexible
- Templates can be custom-written
- Supports complex matching rules

> 📎 [Template Guide](Template-Guide)

---

#### Method 3: Import without Data File

**Use Case**: Game collections without data files (gamelist.xml)

**Media Files**: Media files can be matched via template rules

**Limitations**: Only basic information like game names can be imported due to missing data files.

---

**Note**: You don't need to create a platform before importing. The import action will automatically create the platform.

---

## 🔧 Stage 2: Data Processing

### 2.1 Edit Game Information

#### Platform Editing

**Recommended Action**: After importing a platform, first click the platform name to modify its information.

**Platform Operations**:

| Action | Description |
|--------|-------------|
| View | View platform details and game list |
| Edit | Modify platform name, path, etc. |
| Delete | Delete platform and all its games (use with caution) |
| Translate | Translate **all game information within the platform** (not platform itself) |
| Split | Split platform into new platforms or game subsets |

---

#### Split Function Details

**Purpose**: Separate games from current platform based on conditions

**Split Options**:
1. **Split to New Platform**: Create an independent new platform
2. **Split to Game Subset**: Create a temporary game subset

> ⚠️ **Note**: The split operation **does not delete games from the original platform**, it only copies the games to be split.

**Game Subset Features**:
- Edit game data freely within the subset
- Sync edited data back to the original platform when done
- Ideal for testing and processing without affecting original data

---

#### Translation Function

**Description**: Translates all game information (names, descriptions, etc.) within a platform

**Notes**:
- Translation scope: All game data within the platform
- Translation API settings must be configured first
- See [Translation API Settings](Translation-API-Settings) for detailed configuration

### 2.2 Media Files (Feature Not Yet Developed)

### 2.3 Data Cleaning

**Remove Invalid Data**:
1. Filter invalid games (e.g., missing files)
2. Select and delete

**Data Standardization**:
- Use search function to find duplicate games
- Standardize game naming conventions

### 2.4 Translate Game Information

**Batch Translation**:
1. Select games to translate
2. Click "Batch Translate"
3. Select target language
4. Click "Start Translation"

---

## 📤 Stage 3: Data Export

### 3.1 Select Export Template

Choose the appropriate template based on your target device:

| Template | Target Device | Description |
|----------|--------------|-------------|
| ES-DE | PC/Linux | Most features, supports multiple media types |
| Pegasus | Multi-platform | Lightweight, cross-platform |
| RetroBat | Windows | Integrated with RetroArch |
| Lakka | Embedded | Compact gaming system |
| EmuELEC | Embedded | Multi-emulator support |

### 3.2 Configure Export Options

1. Click "Export" in the left menu
2. **Select Platforms**: Check platforms to export (multiple selection supported)
3. **Select Template**: Choose target frontend from dropdown menu
4. **Set Output Path**: Specify export directory
5. **Select Export Content**:
   - ✅ Game list file (gamelist.xml)
   - ✅ Media files (covers, screenshots, videos)
   - ⬜ ROM files (if needed)
6. **Advanced Settings**:
   - Thread count (affects export speed)
   - Media type filter (export only selected types)

### 3.3 Execute Export

1. Click "Start Export"
2. Monitor export progress
3. After completion:
   - Verify file integrity
   - Check export logs

---

## ⚙️ Stage 4: Software Settings

### 4.1 Data Backup

1. Go to "System Settings" → "Data Backup"
2. **Backup**: Backup current game data
3. **Restore**: Restore from backup
4. **Initialize**: Initialize database

### 4.2 Language Settings

1. Use the dropdown menu
2. Select target language (中文/English)
3. Page will refresh automatically

---

## 🔄 Complete Pipeline Example

```
Data Acquisition → Data Processing → Data Export → Deployment
     ↓               ↓               ↓
  Scan Games    Edit Info     Select Template
  Import List   Add Covers    Set Path
  Manual Add    Batch Translate  Execute Export
```

## 📚 Related Documentation

- [Installation Guide](Installation) - Detailed installation instructions
- [Configuration Guide](Configuration) - Advanced configuration options
- [Troubleshooting](Troubleshooting) - Solutions to common issues