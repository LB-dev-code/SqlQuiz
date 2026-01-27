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
     * 执行SQL并验证答案
     */
    @PostMapping("/question/{questionId}/validate")
    public ResponseEntity<?> validateSql(
            @PathVariable Long questionId,
            @RequestBody Map<String, String> request) {
        try {
            String userSql = request.get("sql");
            if (userSql == null || userSql.trim().isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "SQL语句不能为空"
                ));
            }

            Optional<Question> questionOpt = quizService.getQuestionById(questionId);
            if (!questionOpt.isPresent()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "题目不存在"
                ));
            }

            Question question = questionOpt.get();

            // 在testdb中执行用户的SQL
            SqlValidationService.SqlExecutionResult userResult = sqlValidationService.executeSQL(userSql);

            if (!userResult.isSuccess()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "SQL执行错误",
                    "errorMessage", userResult.getError()
                ));
            }

            // 获取期望的答案并执行
            String expectedSql = question.getExpectedSql();
            boolean isCorrect = false;
            List<Map<String, Object>> expectedData = null;

            if (expectedSql != null && !expectedSql.trim().isEmpty()) {
                SqlValidationService.SqlExecutionResult expectedResult = sqlValidationService.executeSQL(expectedSql);
                if (expectedResult.isSuccess()) {
                    expectedData = expectedResult.getData();
                    // 比较结果
                    isCorrect = compareResults(userResult.getData(), expectedData);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("isCorrect", isCorrect);
            response.put("userResult", userResult.getData());
            response.put("executionTime", userResult.getExecutionTimeMs());

            if (!isCorrect && expectedData != null) {
                response.put("expectedResult", expectedData);
                response.put("message", "语句错误，请查看结果差异");
            } else if (isCorrect) {
                response.put("message", "✓ 语句正确！");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
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
