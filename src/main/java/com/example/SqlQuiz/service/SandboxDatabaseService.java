
package com.example.SqlQuiz.service;

import com.example.SqlQuiz.entity.SandboxContext;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 沙库数据库服务
 * 负责创建、管理和清理临时沙库环境
 */
@Service
public class SandboxDatabaseService {

    private static final Logger log = LoggerFactory.getLogger(SandboxDatabaseService.class);

    @Autowired
    @Qualifier("sandboxAdminDataSource")
    private DataSource adminDataSource;

    @Autowired
    @Qualifier("sandboxUserDataSource")
    private DataSource userDataSource;

    @Autowired
    @Qualifier("testDataSource")
    private DataSource testDataSource;

    // 沙库执行用户的凭证。每个沙库的库名不同，无法复用 userDataSource 的连接池，
    // 因此这里手动建连；账号密码仍从配置读取，避免写死在代码里。
    @Value("${spring.datasource.sandbox-user.username}")
    private String sandboxUserUsername;

    @Value("${spring.datasource.sandbox-user.password}")
    private String sandboxUserPassword;

    private static final String SANDBOX_PRACTICE_PREFIX = "quiz_sb_practice_";
    private static final String SANDBOX_AI_PREFIX = "quiz_sb_ai_";
    private static final int DB_NAME_MAX_LENGTH = 64;

    /**
     * 为自主练习创建沙库
     */
    public SandboxContext createPracticeSandbox(Long studentId, Long answerId) {
        String dbName = generateSandboxName(SANDBOX_PRACTICE_PREFIX, studentId, answerId);
        return createSandboxInternal(dbName, "practice");
    }

    /**
     * 为AI出题验证创建沙库
     */
    public SandboxContext createAISandbox() {
        String dbName = generateSandboxName(SANDBOX_AI_PREFIX, null, null);
        return createSandboxInternal(dbName, "ai");
    }

    /**
     * 内部创建沙库方法
     */
    private SandboxContext createSandboxInternal(String dbName, String type) {
        try (Connection conn = adminDataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 创建数据库
            stmt.execute("CREATE DATABASE `" + dbName + "`");
            log.info("Created sandbox database: {} (type: {})", dbName, type);

        } catch (SQLException e) {
            log.error("Failed to create sandbox: " + dbName, e);
            throw new RuntimeException("Failed to create sandbox: " + dbName, e);
        }

        // 创建用户连接
        try {
            Connection userConnection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/" + dbName + "?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                sandboxUserUsername,
                sandboxUserPassword
            );

            SandboxContext context = new SandboxContext();
            context.setDatabaseName(dbName);
            context.setConnection(userConnection);
            context.setType(type);
            context.setCreatedAt(System.currentTimeMillis());
            return context;

        } catch (SQLException e) {
            // 清理已创建的数据库
            cleanupSandbox(dbName);
            log.error("Failed to connect to sandbox: " + dbName, e);
            throw new RuntimeException("Failed to connect to sandbox: " + dbName, e);
        }
    }

    /**
     * 从 test_db 克隆指定题目的表到沙库（只克隆 setupSql 中涉及的表）
     * 确保沙库数据与前端展示的 test_db 数据完全一致
     */
    public void cloneSpecificTablesFromTestDB(SandboxContext context, String tablePrefix, String setupSql) throws SQLException {
        // 从 setupSql 中提取具体表名（自动补前缀）
        List<String> specificTables = extractTableNamesFromSetupSql(setupSql, tablePrefix);
        log.info("[cloneSpecificTables] Tables from setupSql: {}", specificTables);

        if (specificTables.isEmpty()) {
            // 无法解析时回退到克隆所有前缀表
            log.warn("[cloneSpecificTables] Could not extract table names, falling back to prefix clone");
            cloneTablesFromTestDB(context, tablePrefix);
            return;
        }

        log.info("========== 从testdb克隆指定表到沙库 ==========");
        log.info("沙库: {}", context.getDatabaseName());
        log.info("需要克隆的表: {}", specificTables);

        try (Connection testConn = testDataSource.getConnection();
             Statement sandboxStmt = context.getConnection().createStatement()) {

            for (String tableName : specificTables) {
                log.info("克隆表: {}", tableName);

                try {
                    // 从testdb获取表结构
                    String createTableSql = getCreateTableSql(testConn, tableName);
                    if (createTableSql == null || createTableSql.isEmpty()) {
                        log.warn("  - 表不存在或无法获取结构，跳过: {}", tableName);
                        continue;
                    }

                    // 在沙库中创建表
                    sandboxStmt.execute(createTableSql);
                    log.info("  - 表结构创建成功");

                    // 从testdb复制数据到沙库
                    String insertDataSql = generateInsertFromTestDB(testConn, context, tableName);
                    if (insertDataSql != null && !insertDataSql.isEmpty()) {
                        int copiedRows = sandboxStmt.executeUpdate(insertDataSql);
                        log.info("  - 数据复制成功，复制 {} 行", copiedRows);
                    } else {
                        log.info("  - 表无数据，跳过数据复制");
                    }
                } catch (SQLException e) {
                    log.error("  - 克隆表 {} 失败: {}", tableName, e.getMessage());
                    throw e;
                }
            }
        }

        // 验证沙库中的表
        log.info("========== 验证沙库克隆表 ==========");
        try (Statement checkStmt = context.getConnection().createStatement();
             ResultSet rs = checkStmt.executeQuery("SHOW TABLES")) {
            while (rs.next()) {
                log.info("沙库中的表: {}", rs.getString(1));
            }
        }
        log.info("========== 克隆完成 ==========");
    }

    /**
     * 从testdb克隆表结构和数据到沙库
     * 这是新的推荐方式，确保沙库与testdb完全一致
     * 使用两步法避免权限问题：
     * 1. 从testdb读取表结构和数据（使用testDataSource）
     * 2. 在沙库中创建表并插入数据（使用沙库连接）
     * @param context 沙库上下文
     * @param tablePrefix 表前缀（如 quiz_q_123）
     */
    public void cloneTablesFromTestDB(SandboxContext context, String tablePrefix) throws SQLException {
        if (tablePrefix == null || tablePrefix.isEmpty()) {
            throw new IllegalArgumentException("Table prefix cannot be null or empty");
        }


        // 1. 从testdb获取所有以该前缀开头的表
        List<String> tablesToClone = getTablesByPrefix(tablePrefix);
        log.info("找到 {} 个表需要克隆", tablesToClone.size());

        if (tablesToClone.isEmpty()) {
            log.warn("没有找到前缀为 {} 的表", tablePrefix);
            return;
        }

        // 2. 使用两步法克隆每个表
        try (Connection testConn = testDataSource.getConnection();
             Statement sandboxStmt = context.getConnection().createStatement()) {

            for (String tableName : tablesToClone) {
                log.info("克隆表: {}", tableName);

                try {
                    // 2.1 从testdb获取表结构（CREATE TABLE语句）
                    String createTableSql = getCreateTableSql(testConn, tableName);
                    if (createTableSql == null || createTableSql.isEmpty()) {
                        log.warn("  - 无法获取表结构，跳过: {}", tableName);
                        continue;
                    }

                    // 2.2 在沙库中创建表
                    log.debug("执行: {}", createTableSql);
                    sandboxStmt.execute(createTableSql);
                    log.info("  - 表结构创建成功");

                    // 2.3 从testdb读取数据并在沙库中插入
                    String insertDataSql = generateInsertFromTestDB(testConn, context, tableName);
                    if (insertDataSql != null && !insertDataSql.isEmpty()) {
                        log.debug("执行: {}", insertDataSql);
                        int copiedRows = sandboxStmt.executeUpdate(insertDataSql);
                        log.info("  - 数据复制成功，复制 {} 行", copiedRows);
                    } else {
                        log.info("  - 表无数据，跳过数据复制");
                    }
                } catch (SQLException e) {
                    log.error("  - 克隆表 {} 失败: {}", tableName, e.getMessage());
                    throw e; // 重新抛出异常
                }
            }
        } catch (SQLException e) {
            log.error("克隆表失败，整个操作中止", e);
            throw e; // 重新抛出异常
        }

        // 3. 验证沙库中的表
        log.info("========== 验证沙库中的克隆表 ==========");
        try (Statement checkStmt = context.getConnection().createStatement();
             ResultSet rs = checkStmt.executeQuery("SHOW TABLES")) {
            while (rs.next()) {
                String tableName = rs.getString(1);
                log.info("沙库中的表: {}", tableName);

                // 查询每个表的行数
                try (Statement countStmt = context.getConnection().createStatement();
                     ResultSet countRs = countStmt.executeQuery("SELECT COUNT(*) FROM `" + tableName + "`")) {
                    if (countRs.next()) {
                        log.info("  - 行数: {}", countRs.getInt(1));
                    }
                }
            }
        }
        log.info("========== 克隆完成 ==========\n");
    }

    /**
     * 完整克隆testdb的所有表到沙库（1:1克隆）
     * 用于教师端SQL验证，确保沙库与testdb完全一致
     *
     * @param context 沙库上下文
     * @throws SQLException SQL异常
     */
    public void cloneEntireTestDB(SandboxContext context) throws SQLException {
        log.info("========== 完整克隆testdb到沙库 ==========");
        log.info("沙库数据库名: {}", context.getDatabaseName());

        // 1. 从testdb获取所有表
        List<String> tablesToClone = getAllTablesFromTestDB();
        log.info("找到 {} 个表需要克隆", tablesToClone.size());

        if (tablesToClone.isEmpty()) {
            log.warn("testdb中没有找到任何表");
            return;
        }

        // 2. 使用两步法克隆每个表
        try (Connection testConn = testDataSource.getConnection();
             Statement sandboxStmt = context.getConnection().createStatement()) {

            for (String tableName : tablesToClone) {
                log.info("克隆表: {}", tableName);

                try {
                    // 2.1 从testdb获取表结构（CREATE TABLE语句）
                    String createTableSql = getCreateTableSql(testConn, tableName);
                    if (createTableSql == null || createTableSql.isEmpty()) {
                        log.warn("  - 无法获取表结构，跳过: {}", tableName);
                        continue;
                    }

                    // 2.2 在沙库中创建表
                    log.debug("执行: {}", createTableSql);
                    sandboxStmt.execute(createTableSql);
                    log.info("  - 表结构创建成功");

                    // 2.3 从testdb读取数据并在沙库中插入
                    String insertDataSql = generateInsertFromTestDB(testConn, context, tableName);
                    if (insertDataSql != null && !insertDataSql.isEmpty()) {
                        log.debug("执行: {}", insertDataSql);
                        int copiedRows = sandboxStmt.executeUpdate(insertDataSql);
                        log.info("  - 数据复制成功，复制 {} 行", copiedRows);
                    } else {
                        log.info("  - 表无数据，跳过数据复制");
                    }
                } catch (SQLException e) {
                    log.error("  - 克隆表 {} 失败: {}", tableName, e.getMessage());
                    throw e;
                }
            }
        } catch (SQLException e) {
            log.error("克隆表失败，整个操作中止", e);
            throw e;
        }

        // 3. 验证沙库中的表
        log.info("========== 验证沙库中的克隆表 ==========");
        try (Statement checkStmt = context.getConnection().createStatement();
             ResultSet rs = checkStmt.executeQuery("SHOW TABLES")) {
            while (rs.next()) {
                String tableName = rs.getString(1);
                log.info("沙库中的表: {}", tableName);

                // 查询每个表的行数
                try (Statement countStmt = context.getConnection().createStatement();
                     ResultSet countRs = countStmt.executeQuery("SELECT COUNT(*) FROM `" + tableName + "`")) {
                    if (countRs.next()) {
                        log.info("  - 行数: {}", countRs.getInt(1));
                    }
                }
            }
        }
        log.info("========== 完整克隆完成 ==========\n");
    }

    /**
     * 从testdb获取所有表名
     * @return 所有表名列表
     */
    private List<String> getAllTablesFromTestDB() throws SQLException {
        List<String> tables = new ArrayList<>();

        try (Connection conn = testDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT TABLE_NAME FROM information_schema.TABLES " +
                 "WHERE TABLE_SCHEMA = 'mysql_test_db' " +
                 "ORDER BY TABLE_NAME"
             )) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        } catch (SQLException e) {
            log.error("Failed to get all tables from testdb", e);
            throw e;
        }

        return tables;
    }

    /**
     * 从testdb获取表的CREATE TABLE语句
     */
    private String getCreateTableSql(Connection testConn, String tableName) throws SQLException {
        try (Statement stmt = testConn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW CREATE TABLE `mysql_test_db`.`" + tableName + "`")) {
            if (rs.next()) {
                String createTableSql = rs.getString(2);
                // 移除数据库名前缀，使其可以在沙库中执行
                // 输入: CREATE TABLE `mysql_test_db`.`quiz_q_123_world` (...)
                // 输出: CREATE TABLE `quiz_q_123_world` (...)
                createTableSql = createTableSql.replaceAll(
                    "CREATE TABLE\\s+`mysql_test_db`\\.",
                    "CREATE TABLE `"
                );
                // 处理可能的表名引用
                createTableSql = createTableSql.replaceAll(
                    "`mysql_test_db`\\.`",
                    "`"
                );
                return createTableSql;
            }
        }
        return null;
    }

    /**
     * 从testdb读取数据并生成INSERT语句
     * 批量插入以提高性能
     */
    private String generateInsertFromTestDB(Connection testConn, SandboxContext context,
                                           String tableName) throws SQLException {
        StringBuilder insertBuilder = new StringBuilder();

        // 查询testdb中的所有数据
        try (Statement stmt = testConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM `mysql_test_db`.`" + tableName + "`")) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // 收集所有行数据
            List<List<String>> allRows = new ArrayList<>();
            while (rs.next()) {
                List<String> rowValues = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    Object value = rs.getObject(i);
                    if (value == null) {
                        rowValues.add("NULL");
                    } else if (value instanceof String) {
                        // 转义单引号
                        String strVal = value.toString().replace("'", "''");
                        rowValues.add("'" + strVal + "'");
                    } else if (value instanceof java.sql.Date ||
                               value instanceof java.sql.Time ||
                               value instanceof java.sql.Timestamp) {
                        // 日期时间类型：使用getString()获取MySQL标准格式
                        rowValues.add("'" + rs.getString(i) + "'");
                    } else if (value instanceof Number ||
                               value instanceof Boolean) {
                        // 数字和布尔类型：直接输出，不加引号
                        rowValues.add(value.toString());
                    } else {
                        // 其他类型：作为字符串处理，加引号
                        String strVal = value.toString().replace("'", "''");
                        rowValues.add("'" + strVal + "'");
                    }
                }
                allRows.add(rowValues);
            }

            // 如果没有数据，返回null
            if (allRows.isEmpty()) {
                return null;
            }

            // 构建列名部分
            StringBuilder columns = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) columns.append(", ");
                columns.append("`").append(metaData.getColumnName(i)).append("`");
            }

            // 构建批量INSERT语句
            insertBuilder.append("INSERT INTO `").append(tableName).append("` (")
                        .append(columns).append(") VALUES ");

            for (int i = 0; i < allRows.size(); i++) {
                if (i > 0) insertBuilder.append(", ");
                List<String> rowValues = allRows.get(i);
                insertBuilder.append("(");
                for (int j = 0; j < rowValues.size(); j++) {
                    if (j > 0) insertBuilder.append(", ");
                    insertBuilder.append(rowValues.get(j));
                }
                insertBuilder.append(")");
            }
        }

        return insertBuilder.toString();
    }

    /**
     * 从testdb获取所有以指定前缀开头的表名
     * @param tablePrefix 表前缀
     * @return 表名列表
     */
    private List<String> getTablesByPrefix(String tablePrefix) throws SQLException {
        List<String> tables = new ArrayList<>();

        try (Connection conn = testDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT TABLE_NAME FROM information_schema.TABLES " +
                 "WHERE TABLE_SCHEMA = 'mysql_test_db' " +
                 "AND TABLE_NAME LIKE '" + tablePrefix + "%' " +
                 "ORDER BY TABLE_NAME"
             )) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        } catch (SQLException e) {
            log.error("Failed to get tables from testdb with prefix: {}", tablePrefix, e);
            throw e;
        }

        return tables;
    }

    /**
     * 执行setupSql初始化沙库
     * setupSql 中包含带前缀的表名（如 quiz_q_123_teacher），在沙库中创建这些表
     * @deprecated 推荐使用 cloneTablesFromTestDB() 方法，确保与testdb完全一致
     */
    public void executeSetupSql(SandboxContext context, String setupSql) throws SQLException {
        if (setupSql == null || setupSql.trim().isEmpty()) {
            return;
        }

        try (Statement stmt = context.getConnection().createStatement()) {
            // 清理 Markdown 转义字符
            String cleanedSql = setupSql.replace("\\(", "(").replace("\\)", ")");

            // ========== 调试信息 ==========
            log.info("========== 沙库 SetupSQL 执行 ==========");
            log.info("沙库数据库名: {}", context.getDatabaseName());
            log.info("原始 setupSql 长度: {}", setupSql.length());
            log.info("清理后 setupSql: {}", cleanedSql.substring(0, Math.min(300, cleanedSql.length())));
            // =====================================

            // 支持多条SQL语句，直接创建带前缀的表
            String[] sqlStatements = cleanedSql.split(";");
            for (String sql : sqlStatements) {
                String trimmedSql = sql.trim();
                if (!trimmedSql.isEmpty()) {
                    stmt.execute(trimmedSql);
                    log.info("执行 SQL: {}", trimmedSql.substring(0, Math.min(100, trimmedSql.length())));
                }
            }

            // ========== 验证沙库中的表 ==========
            log.info("========== 验证沙库中的表 ==========");
            try (Statement checkStmt = context.getConnection().createStatement();
                 ResultSet rs = checkStmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    String tableName = rs.getString(1);
                    log.info("沙库中的表: {}", tableName);

                    // 查询每个表的数据
                    try (Statement dataStmt = context.getConnection().createStatement();
                         ResultSet dataRs = dataStmt.executeQuery("SELECT * FROM `" + tableName + "` LIMIT 5")) {
                        int columnCount = dataRs.getMetaData().getColumnCount();
                        StringBuilder header = new StringBuilder("  列: ");
                        for (int i = 1; i <= columnCount; i++) {
                            header.append(dataRs.getMetaData().getColumnName(i));
                            if (i < columnCount) header.append(", ");
                        }
                        log.info(header.toString());

                        int rowCount = 0;
                        while (dataRs.next() && rowCount < 3) {
                            StringBuilder row = new StringBuilder("  数据: ");
                            for (int i = 1; i <= columnCount; i++) {
                                Object value = dataRs.getObject(i);
                                row.append(value != null ? value.toString() : "NULL");
                                if (i < columnCount) row.append(", ");
                            }
                            log.info(row.toString());
                            rowCount++;
                        }
                    }
                }
            }
            log.info("========== 沙库验证完成 ==========\n");
            // =====================================

            log.debug("Executed setupSql in sandbox: {}", context.getDatabaseName());
        } catch (SQLException e) {
            log.error("Failed to execute setupSql in sandbox: {}", context.getDatabaseName(), e);
            throw e;
        }
    }

    /**
     * 在沙库中执行SQL
     * @param context 沙库上下文
     * @param sql 学生的SQL语句
     * @param tablePrefix 表前缀（用于映射无前缀表名到带前缀表名）
     */
    public SqlExecutionResult executeInSandbox(SandboxContext context, String sql, String tablePrefix) {
        SqlExecutionResult result = new SqlExecutionResult();
        result.setSuccess(false);

        if (sql == null || sql.trim().isEmpty()) {
            result.setErrorMessage("SQL语句不能为空");
            return result;
        }

        // ========== 调试信息 ==========
        log.info("========== 学生执行 SQL ==========");
        log.info("沙库数据库名: {}", context.getDatabaseName());
        log.info("学生输入的 SQL: {}", sql);
        log.info("表前缀: {}", tablePrefix);
        // ================================

        // 添加表前缀映射：将无前缀表名映射到带前缀表名
        String actualSql = sql;
        if (tablePrefix != null && !tablePrefix.isEmpty()) {
            actualSql = addTablePrefixToSql(sql, tablePrefix);
            if (!actualSql.equals(sql)) {
                log.info("映射后 SQL: {}", actualSql);
            }
        }

        // 显示沙库中的所有表
        try {
            try (Statement checkStmt = context.getConnection().createStatement();
                 ResultSet rs = checkStmt.executeQuery("SHOW TABLES")) {
                log.info("沙库中的表:");
                while (rs.next()) {
                    log.info("  - {}", rs.getString(1));
                }
            }
        } catch (Exception e) {
            log.warn("无法查询沙库表列表: {}", e.getMessage());
        }
        // ================================

        try {
            boolean isSelect = isSelectQuery(actualSql);
            log.info("SQL类型判断: isSelectQuery={} | actualSql前缀={}", isSelect, actualSql.trim().substring(0, Math.min(50, actualSql.trim().length())).toUpperCase());

            if (isSelect) {
                log.info("执行 SELECT 查询");
                return executeSelectQuery(context, actualSql);
            } else {
                log.info("执行 UPDATE/INSERT/DELETE");
                return executeUpdate(context, actualSql);
            }
        } catch (SQLException e) {
            result.setErrorMessage("SQL执行错误: " + e.getMessage());
            log.error("SQL execution failed in sandbox: {}", context.getDatabaseName(), e);
        }

        return result;
    }

    /**
     * 在沙库中执行SQL（无前缀映射的版本，保持向后兼容）
     */
    public SqlExecutionResult executeInSandbox(SandboxContext context, String sql) {
        return executeInSandbox(context, sql, null);
    }

    /**
     * 执行SELECT查询
     */
    private SqlExecutionResult executeSelectQuery(SandboxContext context, String sql) throws SQLException {
        SqlExecutionResult result = new SqlExecutionResult();
        long startTime = System.currentTimeMillis();

        try (PreparedStatement stmt = context.getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            long endTime = System.currentTimeMillis();
            result.setExecutionTimeMs(endTime - startTime);

            // 处理结果集
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            List<Map<String, Object>> data = new ArrayList<>();
            int rowCount = 0;
            final int MAX_ROWS = 1000;

            while (rs.next() && rowCount < MAX_ROWS) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnLabel(i), rs.getObject(i));
                }
                data.add(row);
                rowCount++;
            }

            result.setResultData(data);
            result.setRowCount(rowCount);
            result.setSuccess(true);
        }

        return result;
    }

    /**
     * 执行UPDATE/INSERT/DELETE
     */
    private SqlExecutionResult executeUpdate(SandboxContext context, String sql) throws SQLException {
        SqlExecutionResult result = new SqlExecutionResult();
        long startTime = System.currentTimeMillis();

        try (PreparedStatement stmt = context.getConnection().prepareStatement(sql)) {
            int affectedRows = stmt.executeUpdate();

            long endTime = System.currentTimeMillis();
            result.setExecutionTimeMs(endTime - startTime);
            result.setRowCount(affectedRows);
            result.setSuccess(true);
        }

        return result;
    }

    /**
     * 从 mysql_test_db 查询真实表数据，生成 Markdown 格式
     * 用于前端展示，确保与学生实际执行SQL时看到的数据完全一致
     * @param tablePrefix 表前缀（如 quiz_q_xxx_timestamp）
     * @return Markdown 格式的表格数据，查不到返回 null
     */
    public String generateMarkdownFromTestDB(String tablePrefix) {
        if (tablePrefix == null || tablePrefix.trim().isEmpty()) {
            log.warn("[generateMarkdownFromTestDB] tablePrefix is null or empty");
            return null;
        }

        log.info("[generateMarkdownFromTestDB] Querying test_db for prefix: {}", tablePrefix);

        try (Connection conn = testDataSource.getConnection()) {
            // 转义 LIKE 通配符（_ 和 %），确保精确匹配前缀
            // MySQL 默认使用 \ 作为 LIKE 转义字符
            String escapedPrefix = tablePrefix.replace("%", "\\%").replace("_", "\\_");
            String query = "SELECT TABLE_NAME FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = 'mysql_test_db' AND TABLE_NAME LIKE ?";
            List<String> tableNames = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(query)) {
                // 使用前缀_作为匹配模式，确保只匹配该前缀的表
                String pattern = escapedPrefix + "\\_%";
                ps.setString(1, pattern);
                log.info("[generateMarkdownFromTestDB] LIKE pattern: {}", pattern);
                log.info("[generateMarkdownFromTestDB] Escaped prefix: {}", escapedPrefix);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tableNames.add(rs.getString("TABLE_NAME"));
                    }
                }
            }

            log.info("[generateMarkdownFromTestDB] Tables found: {} for prefix: {}", tableNames.size(), tablePrefix);
            if (!tableNames.isEmpty()) {
                tableNames.forEach(t -> log.info("[generateMarkdownFromTestDB]   - {}", t));
            }

            if (tableNames.isEmpty()) {
                return null;
            }

            StringBuilder markdown = new StringBuilder();
            for (String tableName : tableNames) {
                // 去前缀生成显示名（quiz_q_xxx_timestamp_employees -> employees）
                String displayName = tableName;
                if (tableName.startsWith(tablePrefix + "_")) {
                    displayName = tableName.substring(tablePrefix.length() + 1);
                }

                markdown.append(displayName).append(" table:\n\n");

                // 查询表数据
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM `" + tableName + "`")) {

                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    // 表头
                    markdown.append("|");
                    for (int i = 1; i <= colCount; i++) {
                        markdown.append(" ").append(meta.getColumnName(i)).append(" |");
                    }
                    markdown.append("\n|");
                    for (int i = 1; i <= colCount; i++) {
                        markdown.append("----|");
                    }
                    markdown.append("\n");

                    // 数据行
                    while (rs.next()) {
                        markdown.append("|");
                        for (int i = 1; i <= colCount; i++) {
                            Object val = rs.getObject(i);
                            markdown.append(" ").append(val != null ? val.toString() : "NULL").append(" |");
                        }
                        markdown.append("\n");
                    }
                }
                markdown.append("\n");
            }

            String result = markdown.toString().trim();
            log.info("[generateMarkdownFromTestDB] Generated markdown length: {}, prefix: {}", result.length(), tablePrefix);
            return result.isEmpty() ? null : result;

        } catch (Exception e) {
            log.error("[generateMarkdownFromTestDB] Failed for prefix {}: {}", tablePrefix, e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 从 mysql_test_db 查询指定表的真实数据，生成 Markdown 格式
     * 通过解析 setupSql 提取该题目创建的具体表名，只展示这些表
     * @param tablePrefix 表前缀
     * @param setupSql 该题目的建表SQL（用于提取具体表名）
     * @return Markdown 格式的表格数据
     */
    public String generateMarkdownFromTestDB(String tablePrefix, String setupSql) {
        if (tablePrefix == null || tablePrefix.trim().isEmpty()) {
            log.warn("[generateMarkdownFromTestDB] tablePrefix is null or empty");
            return null;
        }

        // 从 setupSql 中提取 CREATE TABLE 的具体表名（自动补前缀）
        List<String> specificTables = extractTableNamesFromSetupSql(setupSql, tablePrefix);
        log.info("[generateMarkdownFromTestDB] Specific tables from setupSql: {}", specificTables);

        if (specificTables.isEmpty()) {
            // 无法解析时回退到按前缀查询
            return generateMarkdownFromTestDB(tablePrefix);
        }

        try (Connection conn = testDataSource.getConnection()) {
            StringBuilder markdown = new StringBuilder();

            for (String tableName : specificTables) {
                // 验证表存在
                String checkQuery = "SELECT TABLE_NAME FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA = 'mysql_test_db' AND TABLE_NAME = ?";
                boolean exists = false;
                try (PreparedStatement ps = conn.prepareStatement(checkQuery)) {
                    ps.setString(1, tableName);
                    try (ResultSet rs = ps.executeQuery()) {
                        exists = rs.next();
                    }
                }

                if (!exists) {
                    log.warn("[generateMarkdownFromTestDB] Table not found in test_db: {}", tableName);
                    continue;
                }

                // 去前缀生成显示名
                String displayName = tableName;
                if (tableName.startsWith(tablePrefix + "_")) {
                    displayName = tableName.substring(tablePrefix.length() + 1);
                }

                markdown.append(displayName).append(" table:\n\n");

                // 查询表数据
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM `" + tableName + "`")) {

                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    markdown.append("|");
                    for (int i = 1; i <= colCount; i++) {
                        markdown.append(" ").append(meta.getColumnName(i)).append(" |");
                    }
                    markdown.append("\n|");
                    for (int i = 1; i <= colCount; i++) {
                        markdown.append("----|");
                    }
                    markdown.append("\n");

                    while (rs.next()) {
                        markdown.append("|");
                        for (int i = 1; i <= colCount; i++) {
                            Object val = rs.getObject(i);
                            markdown.append(" ").append(val != null ? val.toString() : "NULL").append(" |");
                        }
                        markdown.append("\n");
                    }
                }
                markdown.append("\n");
            }

            String result = markdown.toString().trim();
            log.info("[generateMarkdownFromTestDB] Generated markdown for {} table(s), length: {}",
                    specificTables.size(), result.length());
            return result.isEmpty() ? null : result;

        } catch (Exception e) {
            log.error("[generateMarkdownFromTestDB] Failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 setupSql 中提取 CREATE TABLE 语句的完整表名
     * 如果提取的表名没有 tablePrefix 前缀，自动补上
     * 兼容两种情况：
     * - 新数据：setupSql 使用简单表名（如 world） → 自动补前缀为 quiz_q_123_world
     * - 旧数据：setupSql 已包含前缀（如 quiz_q_123_world） → 直接使用
     */
    private List<String> extractTableNamesFromSetupSql(String setupSql, String tablePrefix) {
        List<String> rawNames = extractTableNamesFromSetupSql(setupSql);
        if (tablePrefix == null || tablePrefix.trim().isEmpty()) {
            return rawNames;
        }

        List<String> resolvedNames = new ArrayList<>();
        for (String name : rawNames) {
            if (name.startsWith(tablePrefix + "_") || name.startsWith(tablePrefix)) {
                // 已有前缀，直接使用
                resolvedNames.add(name);
            } else {
                // 无前缀，自动补上
                resolvedNames.add(tablePrefix + "_" + name);
            }
        }
        log.info("[extractTableNamesFromSetupSql] Raw: {} -> Resolved: {}", rawNames, resolvedNames);
        return resolvedNames;
    }

    /**
     * 从 setupSql 中提取 CREATE TABLE 语句的原始表名（不做前缀处理）
     */
    private List<String> extractTableNamesFromSetupSql(String setupSql) {
        List<String> names = new ArrayList<>();
        if (setupSql == null || setupSql.trim().isEmpty()) return names;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`'\"]?([\\w]+)[`'\"]?");
        java.util.regex.Matcher matcher = pattern.matcher(setupSql);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 清理沙库
     */
    public void cleanupSandbox(String databaseName) {
        try (Connection conn = adminDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP DATABASE IF EXISTS `" + databaseName + "`");
            log.info("Cleaned up sandbox: {}", databaseName);
        } catch (Exception e) {
            log.error("Failed to cleanup sandbox: " + databaseName, e);
        }
    }

    /**
     * 关闭沙库连接
     */
    public void closeConnection(SandboxContext context) {
        if (context != null && context.getConnection() != null) {
            try {
                context.getConnection().close();
                log.debug("Closed connection for sandbox: {}", context.getDatabaseName());
            } catch (SQLException e) {
                log.error("Failed to close sandbox connection", e);
            }
        }
    }

    /**
     * 生成沙库名称
     */
    private String generateSandboxName(String prefix, Long studentId, Long answerId) {
        StringBuilder sb = new StringBuilder(prefix);

        if (studentId != null) {
            sb.append(studentId).append("_");
        }
        if (answerId != null) {
            sb.append(answerId).append("_");
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String base = sb.toString();

        // 确保不超过MySQL数据库名长度限制（64字符）
        int maxSuffixLength = DB_NAME_MAX_LENGTH - base.length() - 5; // 5 for underscore and random suffix
        if (timestamp.length() > maxSuffixLength) {
            timestamp = timestamp.substring(timestamp.length() - maxSuffixLength);
        }

        return base + timestamp + "_" + RandomStringUtils.randomAlphanumeric(4).toLowerCase();
    }

    /**
     * 判断是否为SELECT查询
     * 会跳过SQL注释来检测实际的SQL语句类型
     */
    private boolean isSelectQuery(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }

        // 移除单行注释 -- ...
        String cleaned = sql.replaceAll("--[^\\n\\r]*", "");

        // 移除多行注释 /* ... */
        cleaned = cleaned.replaceAll("/\\*[\\s\\S]*?\\*/", " ");

        // 移除前导空白
        cleaned = cleaned.trim();

        // 检查第一个有效的SQL关键字
        return cleaned.toUpperCase().startsWith("SELECT");
    }

    /**
     * 为SQL添加表前缀映射
     * 将无前缀的表名（如teacher）映射到带前缀的表名（如quiz_q_123_teacher）
     * 防止重复添加前缀：如果表名已包含前缀，则跳过
     */
    private String addTablePrefixToSql(String sql, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return sql;
        }

        String result = sql;
        // 转义前缀中的特殊字符（用于正则表达式）
        String escapedPrefix = prefix.replace("_", "_");

        // 按顺序匹配并替换表名，更具体的模式要先匹配
        // 负向先行断言 (?!...) 确保表名不以该前缀开头，避免重复添加前缀

        // DROP TABLE table_name
        result = result.replaceAll(
                "(?i)\\b(DROP\\s+TABLE)\\s+(?!" + escapedPrefix + "_)([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "$1 " + prefix + "_$2"
        );
        // ALTER TABLE table_name
        result = result.replaceAll(
                "(?i)\\b(ALTER\\s+TABLE)\\s+(?!" + escapedPrefix + "_)([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "$1 " + prefix + "_$2"
        );
        // TRUNCATE TABLE table_name
        result = result.replaceAll(
                "(?i)\\b(TRUNCATE\\s+TABLE)\\s+(?!" + escapedPrefix + "_)([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "$1 " + prefix + "_$2"
        );
        // INSERT INTO table_name
        result = result.replaceAll(
                "(?i)\\b(INSERT\\s+INTO)\\s+(?!" + escapedPrefix + "_)([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "$1 " + prefix + "_$2"
        );
        // UPDATE table_name
        result = result.replaceAll(
                "(?i)\\bUPDATE\\s+(?!" + escapedPrefix + "_)([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "UPDATE " + prefix + "_$1"
        );
        // DELETE FROM table_name
        result = result.replaceAll(
                "(?i)\\b(DELETE\\s+FROM)\\s+(?!" + escapedPrefix + "_)([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "$1 " + prefix + "_$2"
        );
        // FROM table_name
        result = result.replaceAll(
                "(?i)\\b(FROM)\\s+(?!" + escapedPrefix + "_)([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "$1 " + prefix + "_$2"
        );
        // JOIN table_name
        result = result.replaceAll(
                "(?i)\\b(JOIN)\\s+(?!" + escapedPrefix + "_)([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "$1 " + prefix + "_$2"
        );

        return result;
    }

    /**
     * SQL执行结果类
     */
    public static class SqlExecutionResult {
        private boolean success;
        private String errorMessage;
        private List<Map<String, Object>> resultData;
        private int rowCount;
        private long executionTimeMs;

        public SqlExecutionResult() {
            this.success = false;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public List<Map<String, Object>> getResultData() {
            return resultData;
        }

        public void setResultData(List<Map<String, Object>> resultData) {
            this.resultData = resultData;
        }

        public int getRowCount() {
            return rowCount;
        }

        public void setRowCount(int rowCount) {
            this.rowCount = rowCount;
        }

        public long getExecutionTimeMs() {
            return executionTimeMs;
        }

        public void setExecutionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
        }
    }
}
