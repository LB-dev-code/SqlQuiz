@echo off
REM SQL Quiz 项目测试运行脚本 (Windows)

echo ========================================
echo SQL Quiz 测试运行脚本
echo ========================================
echo.

REM 设置默认选项
set RUN_ALL=1
set RUN_UNIT=0
set RUN_INTEGRATION=0
set RUN_E2E=0
set RUN_COVERAGE=0

REM 解析命令行参数
:parse_args
if "%1"=="--unit" set RUN_UNIT=1& set RUN_ALL=0& shift& goto parse_args
if "%1"=="--integration" set RUN_INTEGRATION=1& set RUN_ALL=0& shift& goto parse_args
if "%1"=="--e2e" set RUN_E2E=1& set RUN_ALL=0& shift& goto parse_args
if "%1"=="--coverage" set RUN_COVERAGE=1& shift& goto parse_args
if not "%1"=="" shift& goto parse_args

REM 检查Maven是否可用
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 未找到 Maven，请确保已安装并添加到 PATH
    exit /b 1
)

echo [信息] Maven 版本:
mvn -version | findstr "Apache Maven"
echo.

REM 运行测试
if %RUN_ALL%==1 (
    echo [运行] 执行所有测试...
    echo.
    mvn clean test
    goto :end
)

if %RUN_UNIT%==1 (
    echo [运行] 执行单元测试...
    echo.
    mvn test -Dtest="com.example.SqlQuiz.unit.**"
    echo.
)

if %RUN_INTEGRATION%==1 (
    echo [运行] 执行集成测试...
    echo.
    mvn test -Dtest="com.example.SqlQuiz.integration.**"
    echo.
)

if %RUN_E2E%==1 (
    echo [运行] 执行端到端测试...
    echo.
    mvn test -Dtest="com.example.SqlQuiz.e2e.**"
    echo.
)

if %RUN_COVERAGE%==1 (
    echo [运行] 生成测试覆盖率报告...
    echo.
    mvn clean test jacoco:report
    echo.
    echo [信息] 覆盖率报告已生成: target/site/jacoco/index.html
    goto :end
)

:end
echo.
echo ========================================
echo 测试执行完成！
echo ========================================
echo.
echo 使用方法:
echo   test-all.bat          - 运行所有测试
echo   test-all.bat --unit   - 仅运行单元测试
echo   test-all.bat --integration - 仅运行集成测试
echo   test-all.bat --e2e    - 仅运行端到端测试
echo   test-all.bat --coverage - 生成覆盖率报告
echo.

pause
