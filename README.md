# Web GameList Oper

一个用于管理游戏列表的 Web 应用程序，支持游戏数据的导入、导出、翻译和管理。

## 功能特性

- 🎮 游戏列表管理（增删改查）
- 📥 支持多种前端模板导入（Pegasus、RetroBat 等）
- 📤 支持多种前端模板导出
- 🌐 多语言国际化支持（中文、英文、日文）
- 🖼️ 媒体文件管理（图片、视频）
- 🔄 平台合并与数据迁移

## 技术栈

- Java 17
- Spring Boot 3.2.x
- SQLite 数据库
- HTML5 + JavaScript
- Docker

## 部署方式

### 方式一：Docker 部署（推荐）

```bash
# 拉取镜像
docker pull fansmall/webgamelistoper:1.0.4-beta

# 创建数据目录
mkdir -p ./data/rules/export ./data/rules/import ./output ./logs

# 运行容器
docker run -d \
  --name webgamelistoper \
  -p 8080:8080 \
  -v $(pwd)/data:/data \
  -v $(pwd)/output:/output \
  -v $(pwd)/logs:/app/logs \
  fansmall/webgamelistoper:1.0.4-beta
```

### 方式二：Docker Compose 部署

创建 `docker-compose.yml` 文件：

```yaml
version: '3.8'
services:
  webgamelistoper:
    image: fansmall/webgamelistoper:1.0.4-beta
    container_name: webgamelistoper
    ports:
      - "8080:8080"
    volumes:
      - ./data:/data
      - ./output:/output
      - ./logs:/app/logs
    restart: unless-stopped
```

运行命令：

```bash
# 创建数据目录
mkdir -p ./data/rules/export ./data/rules/import ./output ./logs

# 启动服务
docker-compose up -d
```

### 方式三：JAR 包运行

```bash
# 下载或编译 JAR 包
# 编译命令：mvn clean package -DskipTests

# 创建数据目录
mkdir -p ./data/rules/export ./data/rules/import ./output ./logs

# 运行 JAR 包
java -jar webGamelistOper-1.0.4-beta.jar
```

## 访问地址

启动成功后，访问：http://localhost:8080

## 目录结构

```
./
├── data/                    # 数据目录
│   ├── rules/               # 规则文件目录
│   │   ├── export/          # 导出规则
│   │   └── import/          # 导入模板
│   ├── roms/                # 游戏 ROM 目录
│   └── database/            # SQLite 数据库
├── output/                  # 导出输出目录
├── logs/                    # 日志目录
└── backup/                  # 备份目录
```

## 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `APP_PORT` | 服务端口 | 8080 |
| `DATA_PATH` | 数据目录 | /data |
| `OUTPUT_PATH` | 输出目录 | /output |
| `ROMS_PATH` | ROM 目录 | /data/roms |

## 规则文件

### 导出规则

放置于 `data/rules/export/` 目录：
- pegasus.json - Pegasus 前端导出规则
- retrobat.json - RetroBat 前端导出规则

### 导入模板

放置于 `data/rules/import/` 目录：
- pegasus.json - Pegasus 前端导入模板
- retrobat.json - RetroBat 前端导入模板

## 国际化

支持三种语言：
- 中文（默认）
- English
- 日本語

在页面右上角可切换语言。

## 注意事项

1. 首次启动时，应用会自动创建必要的目录结构
2. 确保挂载的目录具有读写权限
3. 建议定期备份 `data/database/` 目录
4. 日志文件位于 `logs/` 目录，可用于排查问题

## 技术支持

如有问题，请提交 Issue 或联系开发者。

---

**版本**: 1.0.4-beta