# webGamelistOper 安装指南

## 环境要求

- Docker 和 Docker Compose
- 至少 2GB 内存
- 50GB 可用磁盘空间

## 安装步骤

### 方式一：Docker 部署（推荐）

1. 解压安装包，进入 `distribution` 目录

2. 编辑 `docker-compose.yml` 配置本地路径（详见配置说明章节）

3. 确保 Docker 服务正在运行

4. 执行以下命令启动应用：
   ```bash
   docker-compose up -d --build
   ```

5. 等待容器启动完成后，访问应用：
   ```
   http://localhost:8080
   ```

### 方式二：直接运行 JAR 包

1. 确保已安装 Java 17 或更高版本

2. 解压安装包，进入 `distribution` 目录

3. 创建数据目录：
   ```bash
   mkdir -p /data/database
   mkdir -p /data/backup
   ```

4. 运行应用：
   ```bash
   java -jar webGamelistOper-1.0-beta.jar
   ```

5. 访问应用：
   ```
   http://localhost:8080
   ```

## 目录说明

- `/data/database` - 数据库文件存储目录
- `/data/backup` - 数据库备份文件存储目录
- `/data/roms` - 游戏 ROM 文件目录
- `/data/output` - 导出输出目录
- `/data/rules` - 导出规则配置目录
- `/data/input` - 输入文件目录

## Docker Compose 配置说明

`docker-compose.yml` 文件包含以下配置参数：

```yaml
services:
  webgamelistoper:
    build: .                # 从 Dockerfile 构建
    ports:
      - "8080:8080"         # Web 服务端口映射
    volumes:
      - ./logs:/app/logs     # 应用日志目录
      - /path/to/roms:/data/roms        # 游戏 ROM 目录（必需）
      - /path/to/output:/data/output   # 导出输出目录（必需）
      - /path/to/rules:/data/rules      # 导出规则目录（必需）
      - /path/to/input:/data/input     # 输入文件目录（必需）
    environment:
      - SPRING_PROFILES_ACTIVE=default   # Spring 配置环境
      - SERVER_TOMCAT_BASEDIR=/data      # Tomcat 基础目录
      - SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/data,file:/data/roms,file:/data/output,file:/data/input  # 静态资源位置
    restart: unless-stopped                # 自动重启策略
```

### 卷配置（Volume）

| 主机路径 | 容器路径 | 说明 |
|---------|---------|------|
| ./logs | /app/logs | 应用日志 |
| /path/to/roms | /data/roms | 游戏 ROM 文件目录 |
| /path/to/output | /data/output | 导出输出目录 |
| /path/to/rules | /data/rules | 导出规则配置目录 |
| /path/to/input | /data/input | 输入文件目录 |

### 环境变量说明

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| SPRING_PROFILES_ACTIVE | Spring 配置环境 | default |
| SERVER_TOMCAT_BASEDIR | Tomcat 基础目录 | /data |
| SPRING_RESOURCES_STATIC_LOCATIONS | 静态资源位置 | classpath:/static/,file:/data,... |

### 自定义配置

在运行 `docker-compose up -d --build` 之前，请修改 `docker-compose.yml` 中的卷路径以匹配您的本地目录：

- 将 `/path/to/roms` 更改为您实际的 ROM 目录
- 将 `/path/to/output` 更改为您想要的输出目录
- 将 `/path/to/rules` 更改为您规则配置目录
- 将 `/path/to/input` 更改为您输入文件目录

### Windows 路径示例

在 Windows 上，使用 Windows 风格的路径：
```yaml
volumes:
  - D:\games:/data/roms
  - D:\output:/data/output
  - D:\rules:/data/rules
  - D:\input:/data/input
```

### Linux/Mac 路径示例

在 Linux 或 Mac 上，使用 Unix 风格的路径：
```yaml
volumes:
  - /home/user/games:/data/roms
  - /home/user/output:/data/output
  - /home/user/rules:/data/rules
  - /home/user/input:/data/input
```

## 常用命令

### 查看容器状态
```bash
docker-compose ps
```

### 查看日志
```bash
docker-compose logs -f
```

### 停止应用
```bash
docker-compose down
```

### 重启应用
```bash
docker-compose restart
```

### 重新构建并启动
```bash
docker-compose up -d --build
```

## 端口说明

- **8080** - Web 服务端口
- **8081** - Adminer 数据库管理端口（可选）

## 数据管理

### 备份数据库
在系统设置页面点击"备份数据库"按钮，备份文件将保存在 `/data/backup` 目录。

### 恢复数据库
在系统设置页面点击"恢复数据库"按钮，选择已有的备份文件进行恢复。

### 初始化数据库
在系统设置页面点击"初始化"按钮，将重新创建所有表结构并清空数据。

## 常见问题

### Q: 容器启动失败怎么办？
A: 检查 Docker 是否正常运行，确保端口 8080 和 8081 未被占用：
```bash
docker-compose logs
```

### Q: 数据丢失怎么办？
A: 定期使用备份功能，或挂载主机目录到容器：
```yaml
volumes:
  - ./data:/data
```

### Q: 如何查看应用状态？
A: 访问 `http://localhost:8080` 查看 Web 界面，或使用：
```bash
docker-compose ps
```

### Q: 如何自定义目录路径？
A: 在运行 `docker-compose up -d --build` 之前，编辑 `docker-compose.yml` 中的卷映射，指向您的本地目录。

## 技术支持

如有问题，请访问项目仓库：
https://github.com/fansmall2008/webGamelistOper