#!/bin/bash
# SQL Quiz 项目测试运行脚本 (Linux/Mac)

echo "========================================"
echo "SQL Quiz 测试运行脚本"
echo "========================================"
echo ""

# 默认选项
RUN_ALL=1
RUN_UNIT=0
RUN_INTEGRATION=0
RUN_E2E=0
RUN_COVERAGE=0

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --unit)
            RUN_UNIT=1
            RUN_ALL=0
            shift
            ;;
        --integration)
            RUN_INTEGRATION=1
            RUN_ALL=0
            shift
            ;;
        --e2e)
            RUN_E2E=1
            RUN_ALL=0
            shift
            ;;
        --coverage)
            RUN_COVERAGE=1
            shift
            ;;
        *)
            shift
            ;;
    esac
done

# 检查Maven是否可用
if ! command -v mvn &> /dev/null; then
    echo "[错误] 未找到 Maven，请确保已安装并添加到 PATH"
    exit 1
fi

echo "[信息] Maven 版本:"
mvn -version | head -n 1
echo ""

# 运行测试
if [ $RUN_ALL -eq 1 ]; then
    echo "[运行] 执行所有测试..."
    echo ""
    mvn clean test
    exit $?
fi

if [ $RUN_UNIT -eq 1 ]; then
    echo "[运行] 执行单元测试..."
    echo ""
    mvn test -Dtest="com.example.SqlQuiz.unit.**"
    echo ""
fi

if [ $RUN_INTEGRATION -eq 1 ]; then
    echo "[运行] 执行集成测试..."
    echo ""
    mvn test -Dtest="com.example.SqlQuiz.integration.**"
    echo ""
fi

if [ $RUN_E2E -eq 1 ]; then
    echo "[运行] 执行端到端测试..."
    echo ""
    mvn test -Dtest="com.example.SqlQuiz.e2e.**"
    echo ""
fi

if [ $RUN_COVERAGE -eq 1 ]; then
    echo "[运行] 生成测试覆盖率报告..."
    echo ""
    mvn clean test jacoco:report
    echo ""
    echo "[信息] 覆盖率报告已生成: target/site/jacoco/index.html"
    exit $?
fi

echo ""
echo "========================================"
echo "测试执行完成！"
echo "========================================"
echo ""
echo "使用方法:"
echo "  ./test-all.sh          - 运行所有测试"
echo "  ./test-all.sh --unit   - 仅运行单元测试"
echo "  ./test-all.sh --integration - 仅运行集成测试"
echo "  ./test-all.sh --e2e    - 仅运行端到端测试"
echo "  ./test-all.sh --coverage - 生成覆盖率报告"
echo ""
