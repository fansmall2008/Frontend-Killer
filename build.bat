@echo off
echo ========================================
echo 构建 WebGamelistOper
echo ========================================

echo.
echo [1/4] 设置Java环境...
set JAVA_HOME=C:\Program Files\Java\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%

echo.
echo [2/4] 清理旧的构建产物...
if exist target rmdir /s /q target

echo.
echo [3/4] 执行Maven构建（包含repackage）...
call mvn clean package spring-boot:repackage -DskipTests
if errorlevel 1 (
    echo.
    echo [ERROR] Maven构建失败！
    pause
    exit /b 1
)

echo.
echo [4/4] 验证JAR文件...
if not exist "target\webGamelistOper-1.1-RC1.jar" (
    echo [ERROR] JAR文件未找到！
    pause
    exit /b 1
)

echo.
echo ========================================
echo 构建成功！
echo JAR文件: target\webGamelistOper-1.1-RC1.jar
echo ========================================
echo.
echo 现在可以执行 docker build -t fansmall/webgamelistoper:dev .
echo.
pause
