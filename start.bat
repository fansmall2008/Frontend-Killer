@echo off

rem 设置JVM内存参数
set JAVA_OPTS=-Xms2G -Xmx4G -XX:MaxMetaspaceSize=512M -XX:+UseG1GC -XX:MaxGCPauseMillis=200

echo 正在启动WebGamelistOper...
echo JVM参数: %JAVA_OPTS%

echo 检查jar文件是否存在...
if exist "target\webGamelistOper-1.0-SNAPSHOT.jar" (
    echo 启动应用...
    java %JAVA_OPTS% -jar target\webGamelistOper-1.0-SNAPSHOT.jar
) else (
    echo 错误: 找不到jar文件，请先编译项目
    echo 执行: mvn clean package
    pause
    exit /b 1
)

pause