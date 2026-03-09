package com.example.SqlQuiz.service;

import com.example.SqlQuiz.entity.Question;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 题目解析服务
 * 用于解析AI生成的JSON格式题目
 */
@Service
public class QuestionDeduplicationService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从JSON数组解析生成的题目
     *
     * @param batchJson AI生成的JSON数组格式题目
     * @return 解析后的题目列表
     */
    public List<GeneratedQuestion> parseAndDeduplicate(String batchJson) {
        List<GeneratedQuestion> questions = new ArrayList<>();

        System.out.println("[parseAndDeduplicate] ========== 开始解析AI返回的JSON ==========");
        System.out.println("[parseAndDeduplicate] batchJson长度: " + (batchJson != null ? batchJson.length() : "null"));
        if (batchJson != null && batchJson.length() > 0) {
            System.out.println("[parseAndDeduplicate] batchJson前500字符: " +
                    batchJson.substring(0, Math.min(500, batchJson.length())));
        }

        try {
            // 清理JSON响应
            String cleanedJson = cleanJsonResponse(batchJson);
            System.out.println("[parseAndDeduplicate] 清理后JSON长度: " + cleanedJson.length());

            // 解析JSON数组
            JsonNode rootNode = objectMapper.readTree(cleanedJson);
            System.out.println("[parseAndDeduplicate] JSON解析成功 - isArray: " + rootNode.isArray() +
                    ", size: " + rootNode.size());

            if (!rootNode.isArray()) {
                // 如果不是数组，尝试包装成单元素数组
                System.out.println("[parseAndDeduplicate] JSON不是数组，尝试包装...");
                JsonNode wrapper = objectMapper.createArrayNode();
                ((com.fasterxml.jackson.databind.node.ArrayNode) wrapper).add(rootNode);
                rootNode = wrapper;
            }

            // 遍历生成的题目
            int index = 0;
            for (JsonNode questionNode : rootNode) {
                System.out.println("[parseAndDeduplicate] 解析题目 #" + index);
                System.out.println("[parseAndDeduplicate]   - 节点字段: " + getNodeFieldNames(questionNode));
                System.out.println("[parseAndDeduplicate]   - hasSetupSql: " + questionNode.has("setupSql"));
                if (questionNode.has("setupSql")) {
                    JsonNode setupSqlNode = questionNode.get("setupSql");
                    System.out.println("[parseAndDeduplicate]   - setupSql isNull: " + setupSqlNode.isNull());
                    if (!setupSqlNode.isNull()) {
                        String setupSql = setupSqlNode.asText();
                        System.out.println("[parseAndDeduplicate]   - setupSql长度: " + setupSql.length());
                        System.out.println("[parseAndDeduplicate]   - setupSql前100字符: " +
                                setupSql.substring(0, Math.min(100, setupSql.length())));
                    }
                }

                GeneratedQuestion q = parseQuestion(questionNode);
                if (q != null) {
                    System.out.println("[parseAndDeduplicate]   - 解析成功: title=" + q.title +
                            ", hasSetupSql=" + (q.setupSql != null && !q.setupSql.isEmpty()));
                    questions.add(q);
                    System.out.println("[parseAndDeduplicate]   - 添加到结果列表");
                } else {
                    System.out.println("[parseAndDeduplicate]   - 解析返回null");
                }
                index++;
            }

        } catch (Exception e) {
            System.err.println("[parseAndDeduplicate] ========== 解析失败 ==========");
            System.err.println("[parseAndDeduplicate] 异常类型: " + e.getClass().getName());
            System.err.println("[parseAndDeduplicate] 异常信息: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[parseAndDeduplicate] ========== 解析完成 - 返回题目数: " + questions.size() + " ==========");
        return questions;
    }
    
    /**
     * 获取JSON节点的所有字段名（用于调试）
     */
    private String getNodeFieldNames(JsonNode node) {
        if (node == null || !node.isObject()) return "(not object)";
        StringBuilder sb = new StringBuilder("[");
        java.util.Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            sb.append(it.next());
            if (it.hasNext()) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 解析单个题目
     */
    private GeneratedQuestion parseQuestion(JsonNode node) {
        try {
            String questionTypeStr = getJsonField(node, "questionType");
            String difficultyStr = getJsonField(node, "difficulty");
            String title = getJsonField(node, "title");
            String description = getJsonField(node, "description");
            String databaseContext = getJsonField(node, "databaseContext");
            String setupSql = getJsonField(node, "setupSql");
            String expectedSql = getJsonField(node, "expectedSql");
            String hints = getJsonField(node, "hints");

            // 转换枚举
            Question.QuestionType questionType = null;
            if (questionTypeStr != null && !questionTypeStr.isEmpty()) {
                try {
                    questionType = Question.QuestionType.valueOf(questionTypeStr);
                } catch (IllegalArgumentException e) {
                    // 无效的题型，使用默认值
                }
            }

            Question.DifficultyLevel difficulty = null;
            if (difficultyStr != null && !difficultyStr.isEmpty()) {
                try {
                    difficulty = Question.DifficultyLevel.valueOf(difficultyStr);
                } catch (IllegalArgumentException e) {
                    difficulty = Question.DifficultyLevel.MEDIUM;
                }
            }

            return new GeneratedQuestion(
                    questionType,
                    difficulty,
                    title,
                    description,
                    databaseContext,
                    setupSql,
                    expectedSql,
                    hints
            );

        } catch (Exception e) {
            System.err.println("Failed to parse question: " + e.getMessage());
            return null;
        }
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "[]";
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private String getJsonField(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode fieldNode = node.get(field);
        return fieldNode.isNull() ? null : fieldNode.asText();
    }

    /**
     * 生成的题目数据类
     */
    public static class GeneratedQuestion {
        public final Question.QuestionType questionType;
        public final Question.DifficultyLevel difficulty;
        public final String title;
        public final String description;
        public final String databaseContext;
        public final String setupSql;
        public final String expectedSql;
        public final String hints;

        public GeneratedQuestion(
                Question.QuestionType questionType,
                Question.DifficultyLevel difficulty,
                String title,
                String description,
                String databaseContext,
                String setupSql,
                String expectedSql,
                String hints) {
            this.questionType = questionType;
            this.difficulty = difficulty;
            this.title = title;
            this.description = description;
            this.databaseContext = databaseContext;
            this.setupSql = setupSql;
            this.expectedSql = expectedSql;
            this.hints = hints;
        }
    }
}
