package com.example.SqlQuiz.service;

import com.example.SqlQuiz.repository.QuizTableMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SetupSqlExecutorService {

    @Autowired
    @Qualifier("testDataSource")
    private DataSource testDataSource;

    @Autowired
    private QuizTableMetadataRepository metadataRepository;

    public DataSource getTestDataSource() {
        return testDataSource;
    }

    /**
     * 在测试数据库中执行setup SQL语句
     * @param setupSql 包含建表和插入数据的SQL语句
     * @return 执行成功后返回表格前缀
     * @throws SQLException SQL执行异常
     */
    @Transactional
    public String executeSetupSql(String setupSql) throws SQLException {
        if (setupSql == null || setupSql.trim().isEmpty()) {
            throw new IllegalArgumentException("Setup SQL 不能为空");
        }

        System.out.println("=== SetupSqlExecutorService 开始处理 ===");
        System.out.println("原始SQL: " + setupSql.substring(0, Math.min(200, setupSql.length())) + "...");

        // 清理 Markdown 转义字符（AI 生成的 SQL 可能包含 \( \) 这样的转义）
        String cleanedSql = setupSql.replace("\\(", "(").replace("\\)", ")");
        if (!cleanedSql.equals(setupSql)) {
            System.out.println("检测到 Markdown 转义字符，已清理");
        }

        // 将 INT/INTEGER 升级为 BIGINT，避免AI生成的大数据（GDP、人口、金额等）溢出
        cleanedSql = cleanedSql.replaceAll("(?i)\\bINT\\b(?!\\w)", "BIGINT");
        cleanedSql = cleanedSql.replaceAll("(?i)\\bINTEGER\\b", "BIGINT");

        // 提取所有CREATE TABLE语句中的完整表名
        List<String> allTableNames = extractAllTableNames(cleanedSql);
        System.out.println("提取到的表名: " + allTableNames);

        // 检查是否所有表名都已经有quiz_q_前缀
        boolean allHaveQuizPrefix = allTableNames.stream()
            .allMatch(name -> name.matches("quiz_q_.+"));

        String processedSql = cleanedSql;
        String tablePrefix = null;

        if (!allHaveQuizPrefix && !allTableNames.isEmpty()) {
            // 有不带前缀的表，生成新前缀并替换
            tablePrefix = generateTablePrefix(allTableNames);
            System.out.println("生成的新前缀: " + tablePrefix);

            // 只替换不带quiz_q_前缀的表名
            List<String> tablesToPrefix = allTableNames.stream()
                .filter(name -> !name.matches("quiz_q_.+"))
                .collect(java.util.stream.Collectors.toList());

            System.out.println("需要添加前缀的表: " + tablesToPrefix);
            // 使用cleanedSql（已做INT→BIGINT和Markdown转义清理），而非原始setupSql
            processedSql = addTablePrefix(cleanedSql, tablesToPrefix, tablePrefix);
        } else if (!allTableNames.isEmpty()) {
            // 所有表都已有前缀，直接使用完整表名作为前缀（去掉最后的表名部分）
            String fullTableName = allTableNames.get(0);
            tablePrefix = extractPrefixFromTableName(fullTableName);
            System.out.println("检测到已有前缀的表名: " + fullTableName);
            System.out.println("提取的前缀: " + tablePrefix);
            // 使用cleanedSql（已做INT→BIGINT和Markdown转义清理），而非原始setupSql
            processedSql = cleanedSql;
        }

        System.out.println("处理后的SQL: " + processedSql.substring(0, Math.min(200, processedSql.length())) + "...");
        System.out.println("最终使用的前缀: " + tablePrefix);

        try (Connection connection = testDataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // 分割多个SQL语句并逐个执行
            String[] sqlStatements = processedSql.split(";");

            for (String sql : sqlStatements) {
                String trimmedSql = sql.trim();
                if (!trimmedSql.isEmpty()) {
                    try {
                        statement.execute(trimmedSql);
                        System.out.println("执行SQL成功: " + trimmedSql.substring(0, Math.min(100, trimmedSql.length())) + "...");
                    } catch (SQLSyntaxErrorException e) {
                        if (e.getMessage().contains("already exists")) {
                            // 表已存在，先删除再创建
                            String tableName = extractTableNameFromCreate(trimmedSql);
                            System.out.println("表已存在，删除后重建: " + tableName);
                            String dropSql = "DROP TABLE IF EXISTS " + tableName;
                            statement.execute(dropSql);
                            statement.execute(trimmedSql);
                        } else if (e.getMessage().contains("Identifier name") && e.getMessage().contains("is too long")) {
                            // 约束名太长，移除约束名后重试
                            System.out.println("约束名太长，移除约束名后重试: " + e.getMessage());
                            String fixedSql = removeConstraintNames(trimmedSql);
                            statement.execute(fixedSql);
                            System.out.println("修复后执行成功");
                        } else {
                            System.err.println("SQL执行错误: " + e.getMessage());
                            throw e;
                        }
                    } catch (SQLException e) {
                        if (!e.getMessage().contains("Duplicate entry")) {
                            System.err.println("SQL异常: " + e.getMessage());
                            throw e;
                        }
                    }
                }
            }

            // 验证表是否创建成功
            if (tablePrefix != null) {
                String verifyQuery = "SELECT TABLE_NAME FROM information_schema.TABLES " +
                                  "WHERE TABLE_SCHEMA = 'mysql_test_db' AND TABLE_NAME LIKE '" + tablePrefix + "%'";
                try (ResultSet rs = statement.executeQuery(verifyQuery)) {
                    System.out.println("验证表创建结果:");
                    while (rs.next()) {
                        System.out.println("  - " + rs.getString("TABLE_NAME"));
                    }
                }
            }
        }

        System.out.println("=== SetupSqlExecutorService 处理完成 ===\n");
        return tablePrefix;
    }

    private List<String> extractAllTableNames(String setupSql) {
        List<String> tableNames = new ArrayList<>();
        // 提取所有表名（包括带前缀的）
        Pattern pattern = Pattern.compile("(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`']?([\\w]+)[`']?",
                                        Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(setupSql);

        while (matcher.find()) {
            String tableName = matcher.group(1);
            if (!tableNames.contains(tableName)) {
                tableNames.add(tableName);
            }
        }

        return tableNames;
    }

    private String generateTablePrefix(List<String> tableNames) {
        return "quiz_q_" + System.currentTimeMillis();
    }

    private String extractPrefixFromTableName(String tableName) {
        // 从带前缀的完整表名中提取前缀部分
        // 输入: quiz_q_1769416679715_quiz_q_a4a65176_1769416594455_world
        // 目标: 找到原始表名（world），然后返回除原始表名之外的前缀部分
        // 返回: quiz_q_1769416679715_quiz_q_a4a65176_1769416594455
        
        if (tableName == null || !tableName.matches("quiz_q_.+")) {
            return null;
        }
        
        System.out.println("[extractPrefixFromTableName] 输入完整表名: " + tableName);
        
        // 查找最后一个下划线的位置，假设下划线后面是原始表名
        int lastUnderscore = tableName.lastIndexOf('_');
        if (lastUnderscore > 7) { // 确保不是"quiz_q_"中的下划线
            String prefix = tableName.substring(0, lastUnderscore);
            System.out.println("[extractPrefixFromTableName] 提取的前缀: " + prefix);
            return prefix;
        }
        
        // 如果无法提取，返回完整表名（可能表名本身没有下划线）
        System.out.println("[extractPrefixFromTableName] 无法提取前缀，返回完整表名");
        return tableName;
    }

    private String addTablePrefix(String setupSql, List<String> tableNames, String tablePrefix) {
        String result = setupSql;

        for (String tableName : tableNames) {
            // 替换 CREATE TABLE 中的表名
            result = result.replaceAll(
                "(?i)(CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?)[`']?" +
                Pattern.quote(tableName) + "[`']?",
                "$1" + tablePrefix + "_" + tableName
            );

            // 替换 INSERT INTO 中的表名
            result = result.replaceAll(
                "(?i)(INSERT\\s+INTO\\s+)[`']?" +
                Pattern.quote(tableName) + "[`']?",
                "$1" + tablePrefix + "_" + tableName
            );
        }

        return result;
    }

    private String extractTableNameFromCreate(String createSql) {
        Pattern pattern = Pattern.compile("(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`']?([\\w]+)[`']?");
        Matcher matcher = pattern.matcher(createSql);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 移除SQL中的约束名称，让MySQL自动生成
     * 用于解决约束名过长的问题
     */
    private String removeConstraintNames(String sql) {
        // 移除 CONSTRAINT `constraint_name` 部分
        String result = sql.replaceAll("(?i)CONSTRAINT\\s+`[^`]+`\\s+FOREIGN\\s+KEY", "FOREIGN KEY");
        result = result.replaceAll("(?i)CONSTRAINT\\s+[\\w]+\\s+FOREIGN\\s+KEY", "FOREIGN KEY");
        return result;
    }

    /**
     * 清空测试数据库中的表（用于重新初始化）
     * @param tableName 表名
     * @throws SQLException SQL执行异常
     */
    public void clearTable(String tableName) throws SQLException {
        try (Connection connection = testDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM " + tableName);
        }
    }

    /**
     * 删除测试数据库中的表
     * @param tableName 表名
     * @throws SQLException SQL执行异常
     */
    public void dropTable(String tableName) throws SQLException {
        try (Connection connection = testDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + tableName);
        }
    }

    /**
     * 清理旧的quiz相关表（只清理未被元数据引用的表）
     */
    public void cleanupOldQuizTables() {
        try (Connection connection = testDataSource.getConnection()) {
            List<String> tablesToDrop = new ArrayList<>();
            
            // 获取所有以quiz_q_开头的表
            String query = "SELECT TABLE_NAME FROM information_schema.TABLES " +
                         "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'quiz_q_%'";
            
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(query)) {
                
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    // 提取表前缀
                    String prefix = extractTablePrefixFromName(tableName);
                    
                    // 检查元数据中是否存在该前缀
                    if (prefix != null && !metadataRepository.existsByTablePrefix(prefix)) {
                        tablesToDrop.add(tableName);
                    }
                }
            }

            // 删除未被引用的表
            for (String tableName : tablesToDrop) {
                try (Statement dropStatement = connection.createStatement()) {
                    System.out.println("清理未被引用的表: " + tableName);
                    dropStatement.execute("DROP TABLE IF EXISTS " + tableName);
                } catch (SQLException e) {
                    System.err.println("清理表 " + tableName + " 失败: " + e.getMessage());
                }
            }

            if (!tablesToDrop.isEmpty()) {
                System.out.println("已清理 " + tablesToDrop.size() + " 个未被引用的表");
            }
        } catch (SQLException e) {
            System.err.println("清理旧表失败: " + e.getMessage());
        }
    }

    private String extractTablePrefixFromName(String tableName) {
        if (tableName != null && tableName.matches("quiz_q_\\d+_[\\w]+")) {
            int lastUnderscore = tableName.lastIndexOf("_");
            if (lastUnderscore > 0) {
                return tableName.substring(0, lastUnderscore);
            }
        }
        return null;
    }

    /**
     * 删除指定题目相关的所有表
     * @param questionId 题目ID
     */
    @Transactional
    public void dropTablesByQuestionId(Long questionId) {
        try {
            // 先获取元数据
            List<com.example.SqlQuiz.entity.QuizTableMetadata> metadataList =
                metadataRepository.findByQuestionId(questionId);

            for (com.example.SqlQuiz.entity.QuizTableMetadata metadata : metadataList) {
                String prefix = metadata.getTablePrefix();
                dropTableByPrefix(prefix);
            }
            
            // 删除元数据记录
            metadataRepository.deleteByQuestionId(questionId);
            
        } catch (Exception e) {
            System.err.println("删除题目 " + questionId + " 相关表失败: " + e.getMessage());
        }
    }

    private void dropTableByPrefix(String tablePrefix) {
        try (Connection connection = testDataSource.getConnection()) {
            // 查找所有以该前缀开头的表
            String query = "SELECT TABLE_NAME FROM information_schema.TABLES " +
                         "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE '" + tablePrefix + "%'";
            
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(query)) {
                
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    try (Statement dropStatement = connection.createStatement()) {
                        dropStatement.execute("DROP TABLE IF EXISTS " + tableName);
                        System.out.println("删除表: " + tableName);
                    } catch (SQLException e) {
                        System.err.println("删除表 " + tableName + " 失败: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("删除前缀为 " + tablePrefix + " 的表失败: " + e.getMessage());
        }
    }

    /**
     * 从表格数据生成setupSql
     * @param tables 表格数据列表，每个包含tableName, columns, rows
     * @param tablePrefix 表格前缀
     * @return 生成的setupSql字符串
     */
    @SuppressWarnings("unchecked")
    public String generateSetupSqlFromTableData(List<Map<String, Object>> tables, String tablePrefix) {
        StringBuilder sqlBuilder = new StringBuilder();
        
        for (Map<String, Object> table : tables) {
            String originalTableName = (String) table.get("tableName");
            List<String> columns = (List<String>) table.get("columns");
            List<List<Object>> rows = (List<List<Object>>) table.get("rows");
            
            // 移除可能存在的旧前缀，获取纯表名
            String pureTableName = extractPureTableName(originalTableName);
            String fullTableName = tablePrefix + "_" + pureTableName;
            
            // 生成CREATE TABLE语句
            sqlBuilder.append("CREATE TABLE `").append(fullTableName).append("` (\n");
            
            for (int i = 0; i < columns.size(); i++) {
                String column = columns.get(i);
                String columnType = inferColumnType(rows, i);
                sqlBuilder.append("  `").append(column).append("` ").append(columnType);
                
                // 第一列作为主键
                if (i == 0) {
                    sqlBuilder.append(" PRIMARY KEY AUTO_INCREMENT");
                }
                
                if (i < columns.size() - 1) {
                    sqlBuilder.append(",\n");
                } else {
                    sqlBuilder.append("\n");
                }
            }
            
            sqlBuilder.append(");\n\n");
            
            // 生成INSERT语句
            if (rows != null && !rows.isEmpty()) {
                for (List<Object> row : rows) {
                    sqlBuilder.append("INSERT INTO `").append(fullTableName).append("` (");
                    
                    // 列名
                    for (int i = 0; i < columns.size(); i++) {
                        sqlBuilder.append("`").append(columns.get(i)).append("`");
                        if (i < columns.size() - 1) {
                            sqlBuilder.append(", ");
                        }
                    }
                    
                    sqlBuilder.append(") VALUES (");
                    
                    // 值
                    for (int i = 0; i < row.size(); i++) {
                        Object value = row.get(i);
                        sqlBuilder.append(formatSqlValue(value));
                        if (i < row.size() - 1) {
                            sqlBuilder.append(", ");
                        }
                    }
                    
                    sqlBuilder.append(");\n");
                }
                sqlBuilder.append("\n");
            }
        }
        
        return sqlBuilder.toString();
    }
    
    /**
     * 提取纯表名（去除前缀）
     * 输入: quiz_q_f433a642_1770701461518_customers 或 quiz_q_18__quiz_q_f433a642_1770701461518_customers
     * 输出: customers
     */
    private String extractPureTableName(String tableName) {
        System.out.println("[extractPureTableName] Input: " + tableName);
        
        // 如果包含quiz_q_前缀
        if (tableName.contains("quiz_q_")) {
            // 找到最后一个quiz_q_xxx_xxx_模式后面的部分
            // 匹配模式: quiz_q_[a-z0-9]+_[0-9]+_
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "quiz_q_[a-z0-9]+_[0-9]+_(.+)$",
                java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher matcher = pattern.matcher(tableName);
            
            if (matcher.find()) {
                String pureTableName = matcher.group(1);
                System.out.println("[extractPureTableName] Extracted: " + pureTableName);
                return pureTableName;
            }
            
            // 备选方案: 取最后一个下划线后的部分
            int lastUnderscore = tableName.lastIndexOf('_');
            if (lastUnderscore > 0 && lastUnderscore < tableName.length() - 1) {
                String pureTableName = tableName.substring(lastUnderscore + 1);
                System.out.println("[extractPureTableName] Fallback extracted: " + pureTableName);
                return pureTableName;
            }
        }
        
        System.out.println("[extractPureTableName] No prefix found, returning as is: " + tableName);
        return tableName;
    }
    
    /**
     * 推断列的SQL数据类型
     */
    private String inferColumnType(List<List<Object>> rows, int columnIndex) {
        if (rows == null || rows.isEmpty()) {
            return "VARCHAR(255)";
        }
        
        boolean allIntegers = true;
        boolean allDecimals = true;
        int maxLength = 0;
        
        for (List<Object> row : rows) {
            if (columnIndex >= row.size()) continue;
            
            Object value = row.get(columnIndex);
            if (value == null) continue;
            
            String strValue = value.toString();
            maxLength = Math.max(maxLength, strValue.length());
            
            // 检查是否为整数
            if (allIntegers) {
                try {
                    Long.parseLong(strValue);
                } catch (NumberFormatException e) {
                    allIntegers = false;
                }
            }
            
            // 检查是否为小数
            if (allDecimals && !allIntegers) {
                try {
                    Double.parseDouble(strValue);
                } catch (NumberFormatException e) {
                    allDecimals = false;
                }
            }
        }
        
        if (allIntegers) {
            return "BIGINT";
        } else if (allDecimals) {
            return "DECIMAL(20,2)";
        } else {
            // 字符串类型，根据长度决定
            int length = Math.max(255, maxLength * 2);
            if (length > 1000) {
                return "TEXT";
            }
            return "VARCHAR(" + length + ")";
        }
    }
    
    /**
     * 格式化SQL值（添加引号、转义等）
     */
    private String formatSqlValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        
        String strValue = value.toString();
        
        // 检查是否为数字
        try {
            Long.parseLong(strValue);
            return strValue;
        } catch (NumberFormatException e1) {
            try {
                Double.parseDouble(strValue);
                return strValue;
            } catch (NumberFormatException e2) {
                // 字符串类型，需要添加引号和转义
                return "'" + strValue.replace("'", "''") + "'";
            }
        }
    }
}