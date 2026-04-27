# Web GameList Oper v1.0.4-beta 更新日志

## 发布日期
2026-04-28

## 主要更新

### 🎯 核心功能优化
- ✅ **完善国际化支持**
  - 为 `game-list.html` 所有页面添加完整的多语言支持
  - 新增媒体文件查看模态框的国际化
  - 修复 `getI18nText` 函数全局访问问题
  - 支持中文、英文、日语三种语言切换

- ✅ **统一路径配置**
  - 统一使用 `export/import` 目录结构
  - 通过 `PathUtil` 工具类实现Docker和本地环境自适应
  - 修复 `translation-config.json` 路径加载问题

### 🐳 Docker 部署优化
- ✅ **镜像构建优化**
  - 基础镜像升级至 `openjdk:27-ea-17-jdk-slim`
  - 清理构建时的 logs 和 database 目录
  - 默认导出路径设置为 `/output`
  - 游戏ROM路径设置为 `/roms`

- ✅ **容器配置更新**
  - 新增 `SPRING_RESOURCES_STATIC_LOCATIONS` 环境变量
  - 新增 `JAVA_OPTS` 内存配置（-Xmx2g -Xms512m）
  - 端口映射更新为 `8081:8080`
  - 添加自动重启策略 `--restart unless-stopped`

### 📦 发行版打包优化
- ✅ **完整的发行包**
  - 包含可执行 JAR 包
  - 包含 Dockerfile、docker-compose.yml
  - 包含启动脚本（start.bat、entrypoint.sh）
  - 包含多语言文档（README.md、INSTALL.md等）
  - 包含完整的 rules 模板文件

- ✅ **数据目录映射**
  - `/data` - 数据目录
  - `/data/roms` - 游戏ROM目录
  - `/data/output` - 导出目录
  - `/data/backup` - 备份目录

### 🔧 修复与改进
- ✅ **模板导入乱码问题**
  - 使用 UTF-8 编码读取模板文件
  - 修复 `FileReader` 默认编码导致的乱码

- ✅ **调试日志增强**
  - 在 `ExportRuleServiceImpl` 中添加详细日志
  - 在 `GameListParser` 中添加调试信息
  - 便于定位规则文件加载问题

- ✅ **媒体文件功能**
  - 完善媒体文件查看功能的国际化
  - 新增图片、视频、手册的多语言文本

### 📚 文档更新
- ✅ **多语言 README.md**
  - English 版本
  - 中文版本
  - 日本語版本

- ✅ **部署文档更新**
  - 更新 Docker 部署命令
  - 更新 Docker Compose 配置
  - 更新 JAR 包运行说明

## 文件变更

### 新增文件
- 📄 `README.md` - 多语言项目说明文档
- 📄 `CHANGELOG.md` - 更新日志（本文件）

### 修改文件
- 📄 `src/main/java/com/gamelist/service/impl/ExportRuleServiceImpl.java` - 添加调试日志
- 📄 `src/main/java/com/gamelist/util/PathUtil.java` - 路径工具类
- 📄 `src/main/java/com/gamelist/xml/GameListParser.java` - 添加UTF-8编码处理
- 📄 `src/main/resources/static/game-list.html` - 完善国际化
- 📄 `Dockerfile` - 镜像构建配置优化
- 📄 `distribution/Dockerfile` - 发行版Docker配置
- 📄 `distribution/entrypoint.sh` - 容器启动脚本
- 📄 `pom.xml` - 项目版本更新

### 配置变更
- ⚙️ `.gitignore` - 新增排除规则
- ⚙️ 国际化配置 `translation-config.json` 新增条目

## 已知问题
- 暂无已知问题

## 部署说明

### Docker 快速部署
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

### 访问地址
- 本地访问：http://localhost:8081

## 项目链接
- GitHub: https://github.com/fansmall2008/Frontend-Killer
- Docker Hub: https://hub.docker.com/r/fansmall/webgamelistoper

---

**版本：** 1.0.4-beta  
**发布者：** fansmall2008
