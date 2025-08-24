@echo off
rem 设置控制台编码为UTF-8
chcp 65001 > nul

rem 设置环境变量
set MAVEN_OPTS=-Dfile.encoding=UTF-8 -Duser.language=zh -Duser.country=CN -Duser.timezone=Asia/Shanghai
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8

echo ========================================
echo RPC框架测试执行脚本 (UTF-8编码)
echo ========================================
echo.

echo 1. 基础功能测试
echo 2. 综合功能测试  
echo 3. 性能测试
echo 4. 高级功能测试
echo 5. 新增功能测试
echo 6. 全部测试
echo.

set /p choice=请选择要运行的测试 (1-6): 

if "%choice%"=="1" (
    echo.
    echo 正在运行基础功能测试...
    mvn exec:java@basic-test
) else if "%choice%"=="2" (
    echo.
    echo 正在运行综合功能测试...
    mvn exec:java@comprehensive-test
) else if "%choice%"=="3" (
    echo.
    echo 正在运行性能测试...
    mvn exec:java@performance-test
) else if "%choice%"=="4" (
    echo.
    echo 正在运行高级功能测试...
    mvn exec:java@advanced-test
) else if "%choice%"=="5" (
    echo.
    echo 正在运行新增功能测试...
    mvn exec:java@new-features-test
) else if "%choice%"=="6" (
    echo.
    echo 正在运行全部测试...
    echo.
    echo [1/5] 基础功能测试
    mvn exec:java@basic-test
    echo.
    echo [2/5] 综合功能测试
    mvn exec:java@comprehensive-test
    echo.
    echo [3/5] 性能测试
    mvn exec:java@performance-test
    echo.
    echo [4/5] 高级功能测试
    mvn exec:java@advanced-test
    echo.
    echo [5/5] 新增功能测试
    mvn exec:java@new-features-test
    echo.
    echo 全部测试完成！
) else (
    echo 无效的选择，请重新运行脚本
    pause
    exit /b 1
)

echo.
echo 测试完成！
pause
