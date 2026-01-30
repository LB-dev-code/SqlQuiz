package com.example.SqlQuiz.Controller;

import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.entity.Quiz;
import com.example.SqlQuiz.entity.QuizTableMetadata;
import com.example.SqlQuiz.entity.User;
import com.example.SqlQuiz.service.QuizService;
import com.example.SqlQuiz.service.QuizTableMetadataService;
import com.example.SqlQuiz.service.SetupSqlExecutorService;
import com.example.SqlQuiz.service.SqlValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sql-practice")
public class SqlPracticeController {

    @Autowired
    private QuizService quizService;

    @Autowired
    private QuizTableMetadataService tableMetadataService;

    @Autowired
    private SqlValidationService sqlValidationService;

    @Autowired
    @Qualifier("testDataSource")
    private DataSource testDataSource;

    @Autowired
    private com.example.SqlQuiz.service.SandboxDatabaseService sandboxService;

    /**
     * 获取可用的Quiz列表
     */
    @GetMapping("/quizzes")
    public ResponseEntity<?> getQuizzes(Authentication auth) {
        try {
            List<Quiz> quizzes;
            if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER"))) {
                User teacher = (User) auth.getPrincipal();
                quizzes = quizService.findQuizzesByTeacher(teacher);
            } else {
                quizzes = quizService.findOpenQuizzes();
            }

            List<Map<String, Object>> quizList = quizzes.stream().map(quiz -> {
                Map<String, Object> data = new HashMap<>();
                data.put("id", quiz.getId());
                data.put("title", quiz.getTitle());
                data.put("description", quiz.getDescription());
                data.put("questionCount", quiz.getQuestions().size());
                return data;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", quizList
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 获取指定Quiz的题目列表
     */
    @GetMapping("/quiz/{quizId}/questions")
    public ResponseEntity<?> getQuestions(@PathVariable Long quizId, Authentication auth) {
        try {
            Quiz quiz = quizService.findById(quizId).orElse(null);
            if (quiz == null) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "Quiz不存在"
                ));
            }

            List<Question> questions = quizService.getQuestionsByQuiz(quizId);
            List<Map<String, Object>> questionList = questions.stream().map(q -> {
                Map<String, Object> data = new HashMap<>();
                data.put("id", q.getId());
                data.put("content", q.getContent());
                data.put("questionType", q.getQuestionType() != null ? q.getQuestionType().getDisplayName() : "未分类");
                data.put("difficultyLevel", q.getDifficultyLevel() != null ? q.getDifficultyLevel().getDisplayName() : "未设置");
                data.put("score", q.getScore());
                return data;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", questionList
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 获取题目详细信息（包括表格数据）
     */
    @GetMapping("/question/{questionId}")
    public ResponseEntity<?> getQuestionDetail(@PathVariable Long questionId) {
        try {
            Optional<Question> questionOpt = quizService.getQuestionById(questionId);
            if (!questionOpt.isPresent()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "题目不存在"
                ));
            }

            Question question = questionOpt.get();

            Map<String, Object> data = new HashMap<>();
            data.put("id", question.getId());
            data.put("content", question.getContent());
            data.put("description", question.getDescription());
            data.put("expectedSql", question.getExpectedSql());
            data.put("questionType", question.getQuestionType() != null ? question.getQuestionType().getDisplayName() : "未分类");
            data.put("difficultyLevel", question.getDifficultyLevel() != null ? question.getDifficultyLevel().getDisplayName() : "未设置");
            data.put("score", question.getScore());

            // 获取题目对应的表格数据
            List<QuizTableMetadata> metadataList = tableMetadataService.getByQuestionId(questionId);
            List<Map<String, Object>> tables = new ArrayList<>();

            for (QuizTableMetadata metadata : metadataList) {
                String tablePrefix = metadata.getTablePrefix();
                List<Map<String, Object>> tableData = getTableDataByPrefix(tablePrefix);

                Map<String, Object> tableInfo = new HashMap<>();
                tableInfo.put("prefix", tablePrefix);
                tableInfo.put("data", tableData);
                tables.add(tableInfo);
            }

            data.put("tables", tables);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 执行SQL并验证答案（使用沙库环境，支持表名前缀自动映射）
     */
    @PostMapping("/question/{questionId}/validate")
    public ResponseEntity<?> validateSql(
            @PathVariable Long questionId,
            @RequestBody Map<String, String> request) {
        
        com.example.SqlQuiz.entity.SandboxContext sandbox = null;
        
        try {
            String userSql = request.get("sql");
            if (userSql == null || userSql.trim().isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "SQL语句不能为空"
                ));
            }

            Optional<com.example.SqlQuiz.entity.Question> questionOpt = quizService.getQuestionById(questionId);
            if (!questionOpt.isPresent()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "题目不存在"
                ));
            }

            com.example.SqlQuiz.entity.Question question = questionOpt.get();

            // 1. 创建教师验证沙库
            sandbox = sandboxService.createAISandbox();

            // 2. 执行setupSQL创建表和数据（如果有）
            String setupSql = question.getSetupSql();
            String tablePrefix = null;

            if (setupSql != null && !setupSql.trim().isEmpty()) {
                sandboxService.executeSetupSql(sandbox, setupSql);
                // 从setupSQL中提取表前缀
                tablePrefix = extractTablePrefixFromSetupSql(setupSql);
            } else {
                // 没有setupSQL时的处理：
                // 尝试从QuizTableMetadata获取表结构并在沙库中创建
                List<QuizTableMetadata> metadataList = tableMetadataService.getByQuestionId(questionId);
                if (!metadataList.isEmpty()) {
                    for (QuizTableMetadata metadata : metadataList) {
                        // 从主数据库复制表结构和数据到沙库
                        copyTableToSandbox(metadata.getTablePrefix(), sandbox);
                    }
                    // 使用第一个表的前缀
                    tablePrefix = metadataList.get(0).getTablePrefix();
                    // 移除表名中的时间戳部分，只保留基础前缀
                    if (tablePrefix != null && tablePrefix.matches("quiz_q_\\d+_\\d+_")) {
                        tablePrefix = tablePrefix.replaceAll("_\\d+$", "");
                    }
                }
                // 如果也没有QuizTableMetadata，允许在空沙库中执行
                // 学生可以使用CREATE TABLE等DDL语句自己创建表
            }

            // 4. 添加表前缀到用户SQL（如果有前缀）
            String actualUserSql = userSql;
            if (tablePrefix != null && !tablePrefix.isEmpty()) {
                actualUserSql = addTablePrefixToSql(userSql, tablePrefix);
            }

            // 5. 在沙库中执行用户的SQL
            com.example.SqlQuiz.service.SandboxDatabaseService.SqlExecutionResult userResult = 
                sandboxService.executeInSandbox(sandbox, actualUserSql);

            if (!userResult.isSuccess()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "SQL执行错误",
                    "errorMessage", userResult.getErrorMessage()
                ));
            }

            // 6. 执行期望SQL（如果有）
            String expectedSql = question.getExpectedSql();
            boolean isCorrect = false;
            List<Map<String, Object>> expectedData = null;

            if (expectedSql != null && !expectedSql.trim().isEmpty()) {
                String actualExpectedSql = expectedSql;
                if (tablePrefix != null && !tablePrefix.isEmpty()) {
                    actualExpectedSql = addTablePrefixToSql(expectedSql, tablePrefix);
                }
                
                com.example.SqlQuiz.service.SandboxDatabaseService.SqlExecutionResult expectedResult = 
                    sandboxService.executeInSandbox(sandbox, actualExpectedSql);
                    
                if (expectedResult.isSuccess()) {
                    expectedData = expectedResult.getResultData();
                    // 比较结果
                    isCorrect = compareResults(userResult.getResultData(), expectedData);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("isCorrect", isCorrect);
            response.put("userResult", userResult.getResultData());
            response.put("rowCount", userResult.getRowCount());
            response.put("executionTime", userResult.getExecutionTimeMs());

            if (!isCorrect && expectedData != null) {
                response.put("expectedResult", expectedData);
                response.put("message", "语句错误，请查看结果差异");
            } else if (isCorrect) {
                response.put("message", "✓ 语句正确！");
            } else {
                response.put("message", "SQL执行成功");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", "执行失败: " + e.getMessage()
            ));
        } finally {
            // 7. 清理沙库
            if (sandbox != null) {
                try {
                    sandboxService.closeConnection(sandbox);
                    sandboxService.cleanupSandbox(sandbox.getDatabaseName());
                } catch (Exception e) {
                    System.err.println("Failed to cleanup sandbox: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 从setupSQL中提取表前缀（quiz_q_数字_）
     */
    private String extractTablePrefixFromSetupSql(String setupSql) {
        if (setupSql == null || setupSql.trim().isEmpty()) {
            return null;
        }

        // 匹配 CREATE TABLE quiz_q_123_tablename 模式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "CREATE\\s+TABLE\\s+(quiz_q_\\d+)_",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(setupSql);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * 从主数据库复制表结构和数据到沙库
     */
    private void copyTableToSandbox(String tablePrefix, com.example.SqlQuiz.entity.SandboxContext sandbox) {
        try (Connection mainDbConnection = testDataSource.getConnection()) {
            // 查找所有以该前缀开头的表
            String query = "SELECT TABLE_NAME FROM information_schema.TABLES " +
                         "WHERE TABLE_SCHEMA = 'mysql_test_db' AND TABLE_NAME LIKE '" + tablePrefix + "%'";

            try (Statement statement = mainDbConnection.createStatement();
                 ResultSet rs = statement.executeQuery(query)) {

                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    copySingleTableToSandbox(tableName, sandbox, mainDbConnection);
                }
            }
        } catch (Exception e) {
            System.err.println("复制表到沙库失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 复制单个表到沙库（包括结构和数据）
     */
    private void copySingleTableToSandbox(String sourceTableName,
                                          com.example.SqlQuiz.entity.SandboxContext sandbox,
                                          Connection mainDbConnection) {
        try {
            // 获取建表语句
            String showCreateTableSql = "SHOW CREATE TABLE " + sourceTableName;
            String createTableSql = null;

            try (Statement stmt = mainDbConnection.createStatement();
                 ResultSet rs = stmt.executeQuery(showCreateTableSql)) {
                if (rs.next()) {
                    createTableSql = rs.getString(2);
                }
            }

            if (createTableSql != null) {
                // 在沙库中创建表（去掉数据库名前缀，只保留表名）
                String simplifiedTableName = sourceTableName;
                if (sourceTableName.contains(".")) {
                    simplifiedTableName = sourceTableName.substring(sourceTableName.lastIndexOf('.') + 1);
                }

                // 替换建表语句中的表名为简化表名
                createTableSql = createTableSql.replaceAll(
                    "CREATE\\s+TABLE\\s+`?" + sourceTableName + "`?",
                    "CREATE TABLE `" + simplifiedTableName + "`"
                );

                // 在沙库中执行建表语句
                try (Statement sandboxStmt = sandbox.getConnection().createStatement()) {
                    sandboxStmt.execute(createTableSql);
                }

                // 复制数据
                String insertSql = "INSERT INTO `" + simplifiedTableName + "` SELECT * FROM " + sourceTableName;
                try (Statement sandboxStmt = sandbox.getConnection().createStatement()) {
                    sandboxStmt.execute(insertSql);
                }

                System.out.println("成功复制表 " + sourceTableName + " 到沙库");
            }
        } catch (Exception e) {
            System.err.println("复制单个表失败 " + sourceTableName + ": " + e.getMessage());
        }
    }

    /**
     * 为SQL语句中的表名添加前缀
     */
    private String addTablePrefixToSql(String sql, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return sql;
        }

        String result = sql;

        // 匹配各种SQL语句中的表名
        // 注意：这是一个简化版本，可能不能处理所有复杂的SQL语句
        String[] patterns = {
            "\\b(DROP\\s+TABLE)\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
            "\\b(ALTER\\s+TABLE)\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
            "\\b(TRUNCATE\\s+TABLE)\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
            "\\b(INSERT\\s+INTO)\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
            "\\bUPDATE\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
            "\\b(DELETE\\s+FROM)\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
            "\\b(FROM)\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
            "\\b(JOIN)\\s+([a-zA-Z_][a-zA-Z0-9_]*)"
        };

        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(result);
            StringBuffer sb = new StringBuffer();
            
            while (m.find()) {
                String keyword = m.group(1);
                String tableName = m.group(m.groupCount());
                
                // 如果表名已经有前缀，不再添加
                if (!tableName.startsWith(prefix + "_")) {
                    String replacement = (m.groupCount() == 2) 
                        ? keyword + " " + prefix + "_" + tableName
                        : keyword.toUpperCase() + " " + prefix + "_" + tableName;
                    m.appendReplacement(sb, replacement);
                } else {
                    m.appendReplacement(sb, m.group(0));
                }
            }
            m.appendTail(sb);
            result = sb.toString();
        }

        return result;
    }

    /**
     * 根据表前缀获取表格数据
     */
    private List<Map<String, Object>> getTableDataByPrefix(String tablePrefix) {
        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection connection = testDataSource.getConnection()) {
            // 查找所有以该前缀开头的表
            String query = "SELECT TABLE_NAME FROM information_schema.TABLES " +
                         "WHERE TABLE_SCHEMA = 'mysql_test_db' AND TABLE_NAME LIKE '" + tablePrefix + "%'";

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(query)) {

                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    Map<String, Object> tableData = getTableData(tableName);
                    if (tableData != null) {
                        result.add(tableData);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("获取表格数据失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取单个表的数据
     */
    private Map<String, Object> getTableData(String tableName) {
        Map<String, Object> tableInfo = new HashMap<>();

        try (Connection connection = testDataSource.getConnection()) {
            // 获取表结构和数据
            String query = "SELECT * FROM " + tableName;

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(query)) {

                java.sql.ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // 列名
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(metaData.getColumnName(i));
                }
                tableInfo.put("tableName", tableName);
                tableInfo.put("columns", columns);

                // 数据行
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(metaData.getColumnName(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                tableInfo.put("rows", rows);
            }
        } catch (Exception e) {
            System.err.println("获取表 " + tableName + " 数据失败: " + e.getMessage());
            return null;
        }

        return tableInfo;
    }

    /**
     * 比较两个查询结果是否相同
     */
    private boolean compareResults(List<Map<String, Object>> result1, List<Map<String, Object>> result2) {
        if (result1 == null && result2 == null) return true;
        if (result1 == null || result2 == null) return false;
        if (result1.size() != result2.size()) return false;

        // 简单比较：行数和列数相同
        // 可以根据需要实现更复杂的比较逻辑
        return true;
    }
}
