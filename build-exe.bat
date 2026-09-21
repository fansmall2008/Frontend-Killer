@echo off
echo ========================================
echo  Frontend-Killer EXE Builder
echo  Version: 1.2-RC1
echo ========================================

rem Set Java environment (JDK 17 required for jpackage)
set JAVA_HOME=C:\Program Files\RedHat\java-17-openjdk-17.0.19.0.10-1
set MAVEN_HOME=D:\apache-maven-3.9.16
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

rem Verify jpackage is available
where jpackage >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] jpackage not found! Please install JDK 17.
    echo Expected path: %JAVA_HOME%\bin\jpackage.exe
    pause
    exit /b 1
)

rem Set variables
set PROJECT_DIR=%~dp0
set TARGET_DIR=%PROJECT_DIR%target
set EXE_DIR=%PROJECT_DIR%distribution\exe
set JAR_NAME=webGamelistOper-1.2-RC1.jar
set APP_NAME=Frontend-Killer

echo.
echo [1/5] Cleaning previous build...
if exist "%TARGET_DIR%" rmdir /s /q "%TARGET_DIR%"
if exist "%EXE_DIR%" rmdir /s /q "%EXE_DIR%"
mkdir "%EXE_DIR%"

echo.
echo [2/5] Building JAR with Maven...
call mvn clean package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven build failed!
    pause
    exit /b 1
)

if not exist "%TARGET_DIR%\%JAR_NAME%" (
    echo [ERROR] JAR file not found: %TARGET_DIR%\%JAR_NAME%
    pause
    exit /b 1
)

echo.
echo [3/5] Creating app-image with jpackage...
jpackage ^
    --type app-image ^
    --name "%APP_NAME%" ^
    --input "%TARGET_DIR%" ^
    --main-jar "%JAR_NAME%" ^
    --main-class org.springframework.boot.loader.launch.JarLauncher ^
    --java-options "-Xms512m" ^
    --java-options "-Xmx2g" ^
    --java-options "-XX:+UseG1GC" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --app-version 1.2.0 ^
    --vendor "Frontend-Killer" ^
    --dest "%EXE_DIR%"

if %ERRORLEVEL% neq 0 (
    echo [ERROR] jpackage failed!
    pause
    exit /b 1
)

echo.
echo [4/5] Copying data and rules...
rem Copy default data directory (exclude scraper cache to keep package small)
if exist "%PROJECT_DIR%data\database" (
    xcopy "%PROJECT_DIR%data\database\*" "%EXE_DIR%\%APP_NAME%\data\database\" /E /I /Y >nul
)
if exist "%PROJECT_DIR%data\rules" (
    xcopy "%PROJECT_DIR%data\rules\*" "%EXE_DIR%\%APP_NAME%\data\rules\" /E /I /Y >nul
)

rem Copy user rules (if exists)
if exist "%PROJECT_DIR%rules" (
    xcopy "%PROJECT_DIR%rules\*" "%EXE_DIR%\%APP_NAME%\rules\" /E /I /Y >nul
)

echo.
echo [5/5] Creating launcher script...
rem Create a start script — cd into Frontend-Killer/ so ./data resolves correctly
(
echo @echo off
echo rem Frontend-Killer Launcher
echo rem 所有路径相对于 Frontend-Killer 目录解析
echo.
echo rem 切换到 Frontend-Killer 目录
echo cd /d "%%%%~dp0%APP_NAME%"
echo.
echo set JAVA_OPTS=-Xms512m -Xmx2g -XX:+UseG1GC
echo.
echo echo Starting Frontend-Killer...
echo echo Access URL: http://localhost:8080
echo echo.
echo.
echo rem 设置 Spring Boot 数据目录和静态资源路径
echo set SERVER_TOMCAT_BASEDIR=./data
echo set SPRING_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:./,file:./data,file:./roms,file:./output
echo.
echo "%APP_NAME%.exe" %%%%JAVA_OPTS%%%%
) > "%EXE_DIR%\start.bat"

rem Create stop script
(
echo @echo off
echo rem Frontend-Killer 停止脚本
echo echo 正在关闭 Frontend-Killer...
echo taskkill /F /IM %APP_NAME%.exe ^>nul 2^>^&1
echo if %%ERRORLEVEL%% equ 0 ^(
echo     echo Frontend-Killer 已关闭。
echo ^) else ^(
echo     echo Frontend-Killer 未在运行。
echo ^)
echo pause
) > "%EXE_DIR%\stop.bat"

echo.
echo ========================================
echo  Build complete!
echo  EXE package: %EXE_DIR%\%APP_NAME%\
echo  Start:       %EXE_DIR%\start.bat
echo  Stop:        %EXE_DIR%\stop.bat
echo ========================================
echo.
echo  To distribute: zip the entire 'exe' folder
echo  Users extract and run start.bat or %APP_NAME%.exe
echo.
pause
