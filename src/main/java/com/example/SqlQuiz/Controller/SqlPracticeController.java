package com.example.SqlQuiz.Controller;

import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.entity.Quiz;
import com.example.SqlQuiz.entity.QuizTableMetadata;
import com.example.SqlQuiz.entity.User;
import com.example.SqlQuiz.service.QuizService;
import com.example.SqlQuiz.service.QuizTableMetadataService;
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
    @Qualifier("testDataSource")
    private DataSource testDataSource;

    @Autowired
    private com.example.SqlQuiz.service.SandboxDatabaseService sandboxService;

    /**
     * Get available quiz list
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
     * Get question list for specified quiz
     */
    @GetMapping("/quiz/{quizId}/questions")
    public ResponseEntity<?> getQuestions(@PathVariable Long quizId, Authentication auth) {
        try {
            Quiz quiz = quizService.findById(quizId).orElse(null);
            if (quiz == null) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "Quiz does not exist"
                ));
            }

            List<Question> questions = quizService.getQuestionsByQuiz(quizId);
            List<Map<String, Object>> questionList = questions.stream().map(q -> {
                Map<String, Object> data = new HashMap<>();
                data.put("id", q.getId());
                data.put("content", q.getContent());
                data.put("questionType", q.getQuestionType() != null ? q.getQuestionType().getDisplayName() : "Uncategorized");
                data.put("difficultyLevel", q.getDifficultyLevel() != null ? q.getDifficultyLevel().getDisplayName() : "Not set");
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
     * Get question detail (including table data)
     */
    @GetMapping("/question/{questionId}")
    public ResponseEntity<?> getQuestionDetail(@PathVariable Long questionId) {
        try {
            Optional<Question> questionOpt = quizService.getQuestionById(questionId);
            if (!questionOpt.isPresent()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "Question does not exist"
                ));
            }

            Question question = questionOpt.get();

            Map<String, Object> data = new HashMap<>();
            data.put("id", question.getId());
            data.put("content", question.getContent());
            data.put("description", question.getDescription());
            data.put("databaseContext", question.getDatabaseContext());
            data.put("expectedSql", question.getExpectedSql());
            data.put("setupSql", question.getSetupSql());
            data.put("questionType", question.getQuestionType() != null ? question.getQuestionType().getDisplayName() : "Uncategorized");
            data.put("difficultyLevel", question.getDifficultyLevel() != null ? question.getDifficultyLevel().getDisplayName() : "Not set");
            data.put("score", question.getScore());

            // Get table data for the question
            List<QuizTableMetadata> metadataList = tableMetadataService.getByQuestionId(questionId);
            List<Map<String, Object>> tables = new ArrayList<>();

            System.out.println("[getQuestionDetail] Question ID: " + questionId);
            System.out.println("[getQuestionDetail] Metadata records found: " + metadataList.size());

            for (QuizTableMetadata metadata : metadataList) {
                String tablePrefix = metadata.getTablePrefix();
                System.out.println("[getQuestionDetail] Processing metadata with prefix: " + tablePrefix);

                List<Map<String, Object>> tableData = getTableDataByPrefix(tablePrefix);
                System.out.println("[getQuestionDetail] Tables found for prefix " + tablePrefix + ": " + tableData.size());

                Map<String, Object> tableInfo = new HashMap<>();
                tableInfo.put("prefix", tablePrefix);
                tableInfo.put("data", tableData);
                tables.add(tableInfo);
            }

            // If no metadata found, try to extract prefix from setupSql
            if (metadataList.isEmpty() && question.getSetupSql() != null && !question.getSetupSql().trim().isEmpty()) {
                System.out.println("[getQuestionDetail] No metadata found, trying to extract from setupSql");
                String tablePrefix = extractTablePrefixFromSetupSql(question.getSetupSql());
                if (tablePrefix != null && !tablePrefix.isEmpty()) {
                    System.out.println("[getQuestionDetail] Extracted prefix from setupSql: " + tablePrefix);
                    List<Map<String, Object>> tableData = getTableDataByPrefix(tablePrefix);
                    System.out.println("[getQuestionDetail] Tables found: " + tableData.size());

                    if (!tableData.isEmpty()) {
                        Map<String, Object> tableInfo = new HashMap<>();
                        tableInfo.put("prefix", tablePrefix);
                        tableInfo.put("data", tableData);
                        tables.add(tableInfo);
                    }
                }
            }

            data.put("tables", tables);
            System.out.println("[getQuestionDetail] Total table groups returned: " + tables.size());

            // Generate real databaseContext from test_db
            String realDatabaseContext = null;
            if (!metadataList.isEmpty()) {
                realDatabaseContext = sandboxService.generateMarkdownFromTestDB(metadataList.get(0).getTablePrefix());
            }
            data.put("realDatabaseContext", realDatabaseContext != null ? realDatabaseContext : question.getDatabaseContext());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
            ));
        } catch (Exception e) {
            System.err.println("[getQuestionDetail] Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Execute SQL (using sandbox environment, supports automatic table name prefix mapping)
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
                    "error", "SQL statement cannot be empty"
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

            // 1. Create teacher verification sandbox
            sandbox = sandboxService.createAISandbox();
            System.out.println("[SQL Validation] Sandbox created successfully: " + sandbox.getDatabaseName());

            // 2. Fully clone testdb to sandbox (1:1 clone)
            System.out.println("[SQL Validation] Starting full clone of testdb to sandbox...");
            try {
                sandboxService.cloneEntireTestDB(sandbox);
                System.out.println("[SQL Validation] testdb clone completed");
            } catch (Exception e) {
                System.err.println("[SQL Validation] testdb clone failed: " + e.getMessage());
                e.printStackTrace();
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "Failed to clone testdb",
                    "errorMessage", e.getMessage()
                ));
            }

            // 3. Get table prefix (for SQL conversion)
            String tablePrefix = null;
            String setupSql = question.getSetupSql();

            // Prefer extracting prefix from setupSql first
            if (setupSql != null && !setupSql.trim().isEmpty()) {
                tablePrefix = extractTablePrefixFromSetupSql(setupSql);
                System.out.println("[SQL Validation] tablePrefix extracted from setupSql: " + tablePrefix);
            }

            // If setupSql has no prefix, try getting from QuizTableMetadata
            if (tablePrefix == null || tablePrefix.isEmpty()) {
                List<QuizTableMetadata> metadataList = tableMetadataService.getByQuestionId(questionId);
                if (!metadataList.isEmpty()) {
                    tablePrefix = metadataList.get(0).getTablePrefix();
                    System.out.println("[SQL Validation] tablePrefix from Metadata: " + tablePrefix);
                }
            }

            // 4. Add table prefix to user SQL (if prefix exists)
            String actualUserSql = userSql;
            if (tablePrefix != null && !tablePrefix.isEmpty()) {
                actualUserSql = addTablePrefixToSql(userSql, tablePrefix);
                System.out.println("[SQL Validation] User SQL conversion:");
                System.out.println("  Original: " + userSql);
                System.out.println("  tablePrefix: " + tablePrefix);
                System.out.println("  Converted: " + actualUserSql);
            }

            // 5. Execute user's SQL
            System.out.println("[SQL Validation] Executing user SQL...");
            com.example.SqlQuiz.service.SandboxDatabaseService.SqlExecutionResult userResult =
                sandboxService.executeInSandbox(sandbox, actualUserSql);

            if (!userResult.isSuccess()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "SQL execution error",
                    "errorMessage", userResult.getErrorMessage()
                ));
            }

            // 6. Return execution result
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", userResult.getResultData());
            response.put("rowCount", userResult.getRowCount());
            response.put("executionTime", userResult.getExecutionTimeMs());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", "Execution failed: " + e.getMessage()
            ));
        } finally {
            // Cleanup sandbox
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
     * Extract table prefix from setupSQL (quiz_q_randomID_)
     * Supports alphanumeric IDs, e.g., quiz_q_8b8c9a12_1739580071811_
     */
    private String extractTablePrefixFromSetupSql(String setupSql) {
        if (setupSql == null || setupSql.trim().isEmpty()) {
            return null;
        }

        // Match CREATE TABLE quiz_q_xxx_tablename pattern
        // xxx can be alphanumeric combination, may be followed by underscore and numbers
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "CREATE\\s+TABLE\\s+`?(quiz_q_[a-zA-Z0-9]+_[0-9]+)_",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(setupSql);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }



    /**
     * Add prefix to table names in SQL statement
     * If table name already contains prefix, do not add again
     */
    private String addTablePrefixToSql(String sql, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return sql;
        }

        String result = sql;
        // Full prefix pattern: quiz_q_xxx_timestamp_
        String fullPrefixPattern = prefix + "_";

        // Match table names in various SQL statements
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

                // Check if table name already contains full prefix (quiz_q_xxx_timestamp_)
                if (tableName.startsWith(fullPrefixPattern)) {
                    // Table name already has prefix, do not add again
                    m.appendReplacement(sb, m.group(0));
                } else if (tableName.startsWith(prefix)) {
                    // Table name starts with base prefix (quiz_q_), but no full prefix
                    // This case also needs checking whether to supplement
                    m.appendReplacement(sb, m.group(0));
                } else {
                    // Table name has no prefix, add prefix
                    String replacement = (m.groupCount() == 2)
                        ? keyword + " " + prefix + "_" + tableName
                        : keyword.toUpperCase() + " " + prefix + "_" + tableName;
                    m.appendReplacement(sb, replacement);
                }
            }
            m.appendTail(sb);
            result = sb.toString();
        }

        return result;
    }

    /**
     * Get table data by table prefix
     */
    private List<Map<String, Object>> getTableDataByPrefix(String tablePrefix) {
        List<Map<String, Object>> result = new ArrayList<>();

                    try (Connection connection = testDataSource.getConnection()) {
                        // Find all tables starting with this prefix
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
            System.err.println("Failed to get table data: " + e.getMessage());
        }

        return result;
    }

    /**
     * Get data for single table
     */
    private Map<String, Object> getTableData(String tableName) {
        Map<String, Object> tableInfo = new HashMap<>();

        try (Connection connection = testDataSource.getConnection()) {
            // Get table structure and data
            String query = "SELECT * FROM " + tableName;

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(query)) {

                java.sql.ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // Column names
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(metaData.getColumnName(i));
                }
                tableInfo.put("tableName", tableName);
                tableInfo.put("columns", columns);

                // Data rows
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
            System.err.println("Failed to get data for table " + tableName + ": " + e.getMessage());
            return null;
        }

        return tableInfo;
    }


}
