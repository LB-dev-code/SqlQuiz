package com.example.SqlQuiz.service;

import com.example.SqlQuiz.dto.QuestionBlacklistItem;
import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.entity.User;
import com.example.SqlQuiz.repository.QuestionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 题目去重服务
 * 用于避免AI生成与现有题目重复的练习题
 */
@Service
public class QuestionDeduplicationService {

    @Autowired
    private QuestionRepository questionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构建题目黑名单
     * 从Question表中获取所有现有题目，归一化后作为黑名单
     *
     * @return 黑名单列表
     */
    public List<QuestionBlacklistItem> buildBlacklist() {
        List<Question> allQuestions = questionRepository.findAllForBlacklist();
        List<QuestionBlacklistItem> blacklist = new ArrayList<>();

        for (Question q : allQuestions) {
            if (q.getContent() != null && q.getExpectedSql() != null) {
                blacklist.add(new QuestionBlacklistItem(
                        q.getContent(),
                        q.getExpectedSql(),
                        q.getId()
                ));
            }
        }

        return blacklist;
    }

    /**
     * 构建指定题型的黑名单
     *
     * @param questionTypes 题型列表
     * @return 黑名单列表
     */
    public List<QuestionBlacklistItem> buildBlacklistForTypes(List<Question.QuestionType> questionTypes) {
        List<Question> allQuestions = questionRepository.findAllForBlacklist();
        List<QuestionBlacklistItem> blacklist = new ArrayList<>();

        for (Question q : allQuestions) {
            // 只包含指定类型的题目，如果类型列表为空则包含所有类型
            if (questionTypes == null || questionTypes.isEmpty() || questionTypes.contains(q.getQuestionType())) {
                if (q.getContent() != null && q.getExpectedSql() != null) {
                    blacklist.add(new QuestionBlacklistItem(
                            q.getContent(),
                            q.getExpectedSql(),
                            q.getId()
                    ));
                }
            }
        }

        return blacklist;
    }

    /**
     * 从JSON数组解析生成的题目，并进行去重
     *
     * @param batchJson AI生成的JSON数组格式题目
     * @param blacklist 黑名单
     * @return 去重后的题目列表
     */
    public List<GeneratedQuestion> parseAndDeduplicate(String batchJson, List<QuestionBlacklistItem> blacklist) {
        List<GeneratedQuestion> questions = new ArrayList<>();

        try {
            // 清理JSON响应
            String cleanedJson = cleanJsonResponse(batchJson);

            // 解析JSON数组
            JsonNode rootNode = objectMapper.readTree(cleanedJson);

            if (!rootNode.isArray()) {
                // 如果不是数组，尝试包装成单元素数组
                JsonNode wrapper = objectMapper.createArrayNode();
                ((com.fasterxml.jackson.databind.node.ArrayNode) wrapper).add(rootNode);
                rootNode = wrapper;
            }

            // 遍历生成的题目
            for (JsonNode questionNode : rootNode) {
                GeneratedQuestion q = parseQuestion(questionNode);
                if (q != null && !isDuplicate(q, blacklist)) {
                    questions.add(q);
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to parse batch questions: " + e.getMessage());
        }

        return questions;
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

    /**
     * 检查题目是否与黑名单重复
     */
    private boolean isDuplicate(GeneratedQuestion question, List<QuestionBlacklistItem> blacklist) {
        if (blacklist == null || blacklist.isEmpty()) {
            return false;
        }

        // 创建黑名单项用于比较
        QuestionBlacklistItem questionItem = new QuestionBlacklistItem(
                question.description,
                question.expectedSql
        );

        // 检查是否与任何黑名单项相似
        for (QuestionBlacklistItem blacklistItem : blacklist) {
            if (questionItem.isPossibleDuplicate(blacklistItem)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 从黑名单中提取归一化的题目内容（用于AI提示）
     */
    public List<String> extractBlacklistContent(List<QuestionBlacklistItem> blacklist) {
        return blacklist.stream()
                .map(QuestionBlacklistItem::getContent)
                .collect(Collectors.toList());
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
