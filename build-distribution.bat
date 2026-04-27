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
copy "%TARGET_DIR%\webGamelistOper-1.0.4-beta.jar" "%DISTRIBUTION_DIR%\webGamelistOper-1.0.4-beta.jar" /Y

rem 拷贝data目录内容
echo 拷贝data目录内容...
if exist "%PROJECT_DIR%\data" (
    xcopy "%PROJECT_DIR%\data\*" "%DATA_DIR%" /E /I /Y
) else (
    echo 注意: 项目根目录下的data目录不存在，将使用distribution目录下的data
)

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