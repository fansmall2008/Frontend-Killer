@echo off

rem 设置变量
set PROJECT_DIR=%~dp0
set TARGET_DIR=%PROJECT_DIR%target
set DISTRIBUTION_DIR=%PROJECT_DIR%distribution
set DATA_DIR=%DISTRIBUTION_DIR%\data

rem 停止所有Java进程
echo 停止所有Java进程...
taskkill /F /IM java.exe /T >nul 2>&1

rem 清理之前的构建
echo 清理之前的构建...
if exist "%TARGET_DIR%" rd /s /q "%TARGET_DIR%"
mkdir "%TARGET_DIR%"

rem 构建项目
echo 构建项目...
mvn clean package -DskipTests

if %ERRORLEVEL% neq 0 (
    echo 构建失败！
    pause
    exit /b 1
)

rem 创建发行包目录结构
echo 创建发行包目录结构...
if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"
if not exist "%DATA_DIR%\rules" mkdir "%DATA_DIR%\rules"
if not exist "%DATA_DIR%\rules\export" mkdir "%DATA_DIR%\rules\export"
if not exist "%DATA_DIR%\rules\import" mkdir "%DATA_DIR%\rules\import"
if not exist "%DATA_DIR%\database" mkdir "%DATA_DIR%\database"

rem 拷贝jar文件
echo 拷贝jar文件...
copy "%TARGET_DIR%\webGamelistOper-1.4.0-PREVIEW.jar" "%DISTRIBUTION_DIR%\webGamelistOper-1.4.0-PREVIEW.jar" /Y

rem 拷贝data目录（排除 scraper 缓存、rom-db 用户数据和 variants 运行时数据）
echo 拷贝data目录内容...
if exist "%PROJECT_DIR%\data\database" xcopy "%PROJECT_DIR%\data\database\*" "%DATA_DIR%\database\" /E /I /Y >nul
if exist "%PROJECT_DIR%\data\rules" xcopy "%PROJECT_DIR%\data\rules\*" "%DATA_DIR%\rules\" /E /I /Y >nul
if exist "%PROJECT_DIR%\data\themes" xcopy "%PROJECT_DIR%\data\themes\*" "%DATA_DIR%\themes\" /E /I /Y >nul
rem rom-db 只创建空目录结构，用户自行导入 DAT/RDB
if not exist "%DATA_DIR%\rom-db\metadat" mkdir "%DATA_DIR%\rom-db\metadat"
if not exist "%DATA_DIR%\rom-db\rdb" mkdir "%DATA_DIR%\rom-db\rdb"
if not exist "%DATA_DIR%\input" mkdir "%DATA_DIR%\input"
if not exist "%DATA_DIR%\work" mkdir "%DATA_DIR%\work"

rem 拷贝rules目录内容
echo 拷贝rules目录内容...
if not exist "%DATA_DIR%\rules\export" mkdir "%DATA_DIR%\rules\export"
if not exist "%DATA_DIR%\rules\import" mkdir "%DATA_DIR%\rules\import"
xcopy "%PROJECT_DIR%\src\main\resources\export-rules\*" "%DATA_DIR%\rules\export" /E /I /Y
xcopy "%PROJECT_DIR%\src\main\resources\import-templates\*" "%DATA_DIR%\rules\import" /E /I /Y

rem 拷贝文档
echo 拷贝文档...
if exist "%PROJECT_DIR%\distribution\docs" (
    xcopy "%PROJECT_DIR%\distribution\docs\*" "%DISTRIBUTION_DIR%\docs" /E /I /Y
) else (
    echo 注意: distribution/docs目录不存在
)

rem 拷贝第三方组件声明
echo 拷贝第三方组件声明...
if exist "%PROJECT_DIR%\THIRD-PARTY-NOTICES.md" (
    copy "%PROJECT_DIR%\THIRD-PARTY-NOTICES.md" "%DISTRIBUTION_DIR%" /Y
)

rem 拷贝安装说明
echo 拷贝安装说明...
if exist "%PROJECT_DIR%\distribution\INSTALL.md" (
    copy "%PROJECT_DIR%\distribution\INSTALL.md" "%DISTRIBUTION_DIR%" /Y
)
if exist "%PROJECT_DIR%\distribution\INSTALL_zh.md" (
    copy "%PROJECT_DIR%\distribution\INSTALL_zh.md" "%DISTRIBUTION_DIR%" /Y
)

rem 拷贝Docker相关文件
echo 拷贝Docker相关文件...
if exist "%PROJECT_DIR%\distribution\Dockerfile" (
    copy "%PROJECT_DIR%\distribution\Dockerfile" "%DISTRIBUTION_DIR%" /Y
)
if exist "%PROJECT_DIR%\distribution\docker-compose.yml" (
    copy "%PROJECT_DIR%\distribution\docker-compose.yml" "%DISTRIBUTION_DIR%" /Y
)
if exist "%PROJECT_DIR%\distribution\entrypoint.sh" (
    copy "%PROJECT_DIR%\distribution\entrypoint.sh" "%DISTRIBUTION_DIR%" /Y
)

rem 创建用户规则目录（发行包中为空，用户可自行添加）
echo 创建用户规则目录...
if not exist "%DISTRIBUTION_DIR%\rules\export" mkdir "%DISTRIBUTION_DIR%\rules\export"
if not exist "%DISTRIBUTION_DIR%\rules\import" mkdir "%DISTRIBUTION_DIR%\rules\import"

rem 创建空的日志目录
echo 创建日志目录...
if not exist "%DISTRIBUTION_DIR%\logs" mkdir "%DISTRIBUTION_DIR%\logs"

echo 构建完成！发行包已准备在 %DISTRIBUTION_DIR% 目录

echo 发行包内容:
dir "%DISTRIBUTION_DIR"

echo 数据目录内容:
dir "%DATA_DIR"

echo 规则目录内容:
dir "%DATA_DIR%\rules"

echo 导出规则目录内容:
dir "%DATA_DIR%\rules\export"

echo 导入规则目录内容:
dir "%DATA_DIR%\rules\import"

pause