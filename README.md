# Web GameList Oper

⚠️ **Important Notice**: This is a **beta/test version**. We welcome bug reports and feedback to help improve the project!

---

## Recent Updates

### v1.0.5-beta (2026-04-30)
- Fixed page internationalization logic in multiple HTML files
- Corrected numerous untranslated internationalization entries
- Fixed translation keys not being applied after dynamic content updates
- Resolved language switching issues in game-list.html, task-management.html, and other pages
- Added complete multi-language support (Chinese, English, Japanese)
- Improved updateTranslations() function to properly handle dynamically generated content

### v1.0.5-beta
- Initial public release
- Game list management (CRUD)
- Import from multiple frontend templates (Pegasus, RetroBat, etc.)
- Export to multiple frontend templates
- Media file management (images, videos)
- Platform merge and data migration

---

## English

### Features
- 🎮 Game list management (CRUD)
- 📥 Import from multiple frontend templates (Pegasus, RetroBat, etc.)
- 📤 Export to multiple frontend templates
- 🌐 Multi-language support (Chinese, English, Japanese)
- 🖼️ Media file management (images, videos)
- 🔄 Platform merge and data migration

### Tech Stack
- Java 17
- Spring Boot 3.2.x
- SQLite Database
- HTML5 + JavaScript
- Docker

### Deployment

#### Option 1: Docker (Recommended)
```bash
docker pull fansmall/webgamelistoper:1.0.5-beta

mkdir -p ./data/rules/export ./data/rules/import ./output ./logs ./backup

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
  fansmall/webgamelistoper:1.0.5-beta
```

#### Option 2: Docker Compose
Create `docker-compose.yml`:
```yaml
version: '3.8'
services:
  webgamelistoper:
    image: fansmall/webgamelistoper:1.0.5-beta
    container_name: webgamelistoper
    ports:
      - "8081:8080"
    volumes:
      - /path/to/output:/data/output
      - /path/to/roms:/data/roms
      - ./logs:/app/logs
      - ./data:/data
      - ./backup:/data/backup
    environment:
      - SPRING_PROFILES_ACTIVE=default
      - SERVER_TOMCAT_BASEDIR=/data
      - SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input
      - JAVA_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC
    restart: unless-stopped
```
Run: `docker-compose up -d`

#### Option 3: JAR File
```bash
mkdir -p ./data/rules/export ./data/rules/import ./output ./logs
java -jar webGamelistOper-1.0.5-beta.jar
```

### Access
http://localhost:8081

---

## 中文

### 功能特性
- 🎮 游戏列表管理（增删改查）
- 📥 支持多种前端模板导入（Pegasus、RetroBat 等）
- 📤 支持多种前端模板导出
- 🌐 多语言国际化支持（中文、英文、日文）
- 🖼️ 媒体文件管理（图片、视频）
- 🔄 平台合并与数据迁移

### 技术栈
- Java 17
- Spring Boot 3.2.x
- SQLite 数据库
- HTML5 + JavaScript
- Docker

### 部署方式

#### 方式一：Docker 部署（推荐）
```bash
docker pull fansmall/webgamelistoper:1.0.5-beta

mkdir -p ./data/rules/export ./data/rules/import ./output ./logs ./backup

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
  fansmall/webgamelistoper:1.0.5-beta
```

#### 方式二：Docker Compose 部署
创建 `docker-compose.yml` 文件：
```yaml
version: '3.8'
services:
  webgamelistoper:
    image: fansmall/webgamelistoper:1.0.5-beta
    container_name: webgamelistoper
    ports:
      - "8081:8080"
    volumes:
      - /path/to/output:/data/output
      - /path/to/roms:/data/roms
      - ./logs:/app/logs
      - ./data:/data
      - ./backup:/data/backup
    environment:
      - SPRING_PROFILES_ACTIVE=default
      - SERVER_TOMCAT_BASEDIR=/data
      - SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input
      - JAVA_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC
    restart: unless-stopped
```
运行：`docker-compose up -d`

#### 方式三：JAR 包运行
```bash
mkdir -p ./data/rules/export ./data/rules/import ./output ./logs
java -jar webGamelistOper-1.0.5-beta.jar
```

### 访问地址
http://localhost:8081

---

## 日本語

### 機能特徴
- 🎮 ゲームリスト管理（追加、削除、更新、検索）
- 📥 複数のフロントエンドテンプレートからのインポート（Pegasus、RetroBat など）
- 📤 複数のフロントエンドテンプレートへのエクスポート
- 🌐 多言語国際化サポート（中国語、英語、日本語）
- 🖼️ メディアファイル管理（画像、動画）
- 🔄 プラットフォーム統合とデータ移行

### 技術スタック
- Java 17
- Spring Boot 3.2.x
- SQLite データベース
- HTML5 + JavaScript
- Docker

### デプロイ方法

#### オプション1：Docker（推奨）
```bash
docker pull fansmall/webgamelistoper:1.0.5-beta

mkdir -p ./data/rules/export ./data/rules/import ./output ./logs ./backup

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
  fansmall/webgamelistoper:1.0.5-beta
```

#### オプション2：Docker Compose
`docker-compose.yml` を作成：
```yaml
version: '3.8'
services:
  webgamelistoper:
    image: fansmall/webgamelistoper:1.0.5-beta
    container_name: webgamelistoper
    ports:
      - "8081:8080"
    volumes:
      - /path/to/output:/data/output
      - /path/to/roms:/data/roms
      - ./logs:/app/logs
      - ./data:/data
      - ./backup:/data/backup
    environment:
      - SPRING_PROFILES_ACTIVE=default
      - SERVER_TOMCAT_BASEDIR=/data
      - SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input
      - JAVA_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC
    restart: unless-stopped
```
実行：`docker-compose up -d`

#### オプション3：JARファイル
```bash
mkdir -p ./data/rules/export ./data/rules/import ./output ./logs
java -jar webGamelistOper-1.0.5-beta.jar
```

### アクセスアドレス
http://localhost:8081

---

**Version**: 1.0.5-beta