package com.example.SqlQuiz.service;

import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.entity.QuestionAnswer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Service
public class SqlValidationService {

    @Autowired
    @Qualifier("testDataSource")
    private DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // SQL执行结果类
    public static class SqlExecutionResult {
        private boolean success;
        private String error;
        private List<Map<String, Object>> data;
        private long executionTimeMs;
        private int rowCount;

        public SqlExecutionResult(boolean success) {
            this.success = success;
            this.data = new ArrayList<>();
        }

        // getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public List<Map<String, Object>> getData() { return data; }
        public void setData(List<Map<String, Object>> data) { this.data = data; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
        public int getRowCount() { return rowCount; }
        public void setRowCount(int rowCount) { this.rowCount = rowCount; }
    }

    // 执行SQL语句
    public SqlExecutionResult executeSQL(String sql) {
        SqlExecutionResult result = new SqlExecutionResult(false);

        if (sql == null || sql.trim().isEmpty()) {
            result.setError("SQL语句不能为空");
            return result;
        }

        // 安全检查：只允许SELECT语句
//        String trimmedSql = sql.trim().toLowerCase();
//        if (!trimmedSql.startsWith("select")) {
//            result.setError("只允许执行SELECT查询语句");
//            return result;
//        }

        // 检查危险关键字
//        if (containsDangerousKeywords(trimmedSql)) {
//            result.setError("SQL语句包含不允许的操作");
//            return result;
//        }

        try (Connection connection = dataSource.getConnection()) {
            long startTime = System.currentTimeMillis();

            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                long endTime = System.currentTimeMillis();
                result.setExecutionTimeMs(endTime - startTime);

                // 获取结果集元数据
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();

                List<Map<String, Object>> data = new ArrayList<>();
                int rowCount = 0;

                // 限制返回的行数，避免内存溢出
                final int MAX_ROWS = 1000;

                while (resultSet.next() && rowCount < MAX_ROWS) {
                    Map<String, Object> row = new LinkedHashMap<>();

                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnLabel(i);
                        Object value = resultSet.getObject(i);
                        row.put(columnName, value);
                    }

                    data.add(row);
                    rowCount++;
                }

                result.setData(data);
                result.setRowCount(rowCount);
                result.setSuccess(true);

            }
        } catch (SQLException e) {
            result.setError("SQL执行错误: " + e.getMessage());
        } catch (Exception e) {
            result.setError("执行过程中发生错误: " + e.getMessage());
        }

        return result;
    }

    // 验证学生答案
    public void validateAnswer(QuestionAnswer questionAnswer) {
        String studentSql = questionAnswer.getStudentSql();
        Question question = questionAnswer.getQuestion();

        if (studentSql == null || studentSql.trim().isEmpty()) {
            questionAnswer.setExecutionError("未提交答案");
            questionAnswer.setIsCorrect(false);
            questionAnswer.setScore(0.0);
            return;
        }

        // 执行学生的SQL
        SqlExecutionResult studentResult = executeSQL(studentSql);

        if (!studentResult.isSuccess()) {
            questionAnswer.setExecutionError(studentResult.getError());
            questionAnswer.setIsCorrect(false);
            questionAnswer.setScore(0.0);
            questionAnswer.setAutoFeedback("SQL语法错误或执行失败");
            return;
        }

        // 记录执行结果
        try {
            String resultJson = objectMapper.writeValueAsString(studentResult.getData());
            questionAnswer.setExecutionResult(resultJson);
            questionAnswer.setExecutionTimeMs(studentResult.getExecutionTimeMs());
        } catch (Exception e) {
            questionAnswer.setExecutionError("结果序列化失败: " + e.getMessage());
            return;
        }

        // 如果有期望结果，进行比较
        String expectedResult = question.getExpectedResult();
        if (expectedResult != null && !expectedResult.trim().isEmpty()) {
            try {
                List<Map<String, Object>> expectedData = objectMapper.readValue(
                        expectedResult, objectMapper.getTypeFactory().constructCollectionType(
                                List.class, Map.class));

                boolean isCorrect = compareResults(studentResult.getData(), expectedData);
                questionAnswer.setIsCorrect(isCorrect);

                if (isCorrect) {
                    questionAnswer.setScore(question.getScore());
                    questionAnswer.setAutoFeedback("答案正确！");
                } else {
                    questionAnswer.setScore(0.0);
                    questionAnswer.setAutoFeedback("查询结果与期望结果不匹配");
                }

            } catch (Exception e) {
                // 如果期望结果格式有问题，给予部分分数
                questionAnswer.setIsCorrect(false);
                questionAnswer.setScore(question.getScore() * 0.5); // 给一半分数
                questionAnswer.setAutoFeedback("SQL执行成功，但无法验证结果正确性");
            }
        } else {
            // 没有期望结果，只要能执行成功就给分
            questionAnswer.setIsCorrect(true);
            questionAnswer.setScore(question.getScore());
            questionAnswer.setAutoFeedback("SQL执行成功");
        }
    }

    // 比较查询结果
    private boolean compareResults(List<Map<String, Object>> studentData,
                                   List<Map<String, Object>> expectedData) {
        if (studentData.size() != expectedData.size()) {
            return false;
        }

        // 对结果进行排序，确保比较的一致性
        studentData.sort(this::compareRowsForSort);
        expectedData.sort(this::compareRowsForSort);

        for (int i = 0; i < studentData.size(); i++) {
            Map<String, Object> studentRow = studentData.get(i);
            Map<String, Object> expectedRow = expectedData.get(i);

            if (!compareRows(studentRow, expectedRow)) {
                return false;
            }
        }

        return true;
    }

    // 比较两行数据
    private boolean compareRows(Map<String, Object> row1, Map<String, Object> row2) {
        if (row1.size() != row2.size()) {
            return false;
        }

        for (Map.Entry<String, Object> entry : row1.entrySet()) {
            String key = entry.getKey();
            Object value1 = entry.getValue();
            Object value2 = row2.get(key);

            if (!Objects.equals(value1, value2)) {
                return false;
            }
        }

        return true;
    }

    // 行比较器（用于排序）
    private int compareRowsForSort(Map<String, Object> row1, Map<String, Object> row2) {
        // 简单的字符串比较
        String str1 = row1.toString();
        String str2 = row2.toString();
        return str1.compareTo(str2);
    }

    // 检查SQL是否包含危险关键字
    private boolean containsDangerousKeywords(String sql) {
        String[] dangerousKeywords = {
                "insert", "update", "delete", "drop", "create", "alter",
                "truncate", "grant", "revoke", "commit", "rollback",
                "call", "exec", "execute", "declare", "set", "use"
        };

        for (String keyword : dangerousKeywords) {
            if (sql.contains(keyword)) {
                return true;
            }
        }

        return false;
    }
}