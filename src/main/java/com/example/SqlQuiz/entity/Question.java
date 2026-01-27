package com.example.SqlQuiz.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    @NotBlank(message = "题目内容不能为空")
    private String content;

    @Column(name = "question_type")
    @Enumerated(EnumType.STRING)
    private QuestionType questionType;

    @Column(columnDefinition = "TEXT")
    private String description; // 题目说明或提示

    @Column(name = "database_context", columnDefinition = "TEXT")
    private String databaseContext; // 数据库表结构描述

    @Column(name = "expected_sql", columnDefinition = "TEXT")
    private String expectedSql; // 期望的SQL语句（用于参考）

    @Column(name = "setup_sql", columnDefinition = "TEXT")
    private String setupSql; // 用于创建表结构和插入示例数据的SQL语句

    @Column(name = "test_data", columnDefinition = "TEXT")
    private String testData; // 测试数据（JSON格式）

    @Column(name = "expected_result", columnDefinition = "TEXT")
    private String expectedResult; // 期望的查询结果（JSON格式）

    @Column(nullable = false)
    @NotNull(message = "分数不能为空")
    @DecimalMin(value = "0.0", message = "分数不能为负数")
    private Double score;

    @Column(name = "difficulty_level")
    @Enumerated(EnumType.STRING)
    private DifficultyLevel difficultyLevel;

    @Column(name = "order_index")
    private Integer orderIndex; // 题目在测试中的顺序

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 所属测试
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    // 学生的答题记录
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<QuestionAnswer> questionAnswers;

    public enum QuestionType {
        SELECT_BASIC("基础查询", "Basic SELECT"),
        SELECT_JOIN("表连接", "JOIN Queries"),
        SELECT_SUBQUERY("子查询", "Subqueries"),
        SELECT_AGGREGATE("聚合函数", "Aggregate Functions"),
        SELECT_COMPLEX("复杂查询", "Complex Queries"),
        DML_INSERT("插入数据", "INSERT Statements"),
        DML_UPDATE("更新数据", "UPDATE Statements"),
        DML_DELETE("删除数据", "DELETE Statements"),
        DDL_CREATE("创建表", "CREATE TABLE"),
        DDL_ALTER("修改表结构", "ALTER TABLE");

        private final String displayName;
        private final String englishName;

        QuestionType(String displayName, String englishName) {
            this.displayName = displayName;
            this.englishName = englishName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getEnglishName() {
            return englishName;
        }
    }

    public enum DifficultyLevel {
        EASY("简单", "Easy"),
        MEDIUM("中等", "Medium"),
        HARD("困难", "Hard");

        private final String displayName;
        private final String englishName;

        DifficultyLevel(String displayName, String englishName) {
            this.displayName = displayName;
            this.englishName = englishName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getEnglishName() {
            return englishName;
        }
    }

    // 构造函数
    public Question() {
    }

    public Question(String content, QuestionType questionType, Double score, Quiz quiz) {
        this.content = content;
        this.questionType = questionType;
        this.score = score;
        this.quiz = quiz;
    }

    // JPA生命周期回调
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getter和Setter方法
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDatabaseContext() {
        return databaseContext;
    }

    public void setDatabaseContext(String databaseContext) {
        this.databaseContext = databaseContext;
    }

    public String getExpectedSql() {
        return expectedSql;
    }

    public void setExpectedSql(String expectedSql) {
        this.expectedSql = expectedSql;
    }

    public String getSetupSql() {
        return setupSql;
    }

    public void setSetupSql(String setupSql) {
        this.setupSql = setupSql;
    }

    public String getTestData() {
        return testData;
    }

    public void setTestData(String testData) {
        this.testData = testData;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public void setExpectedResult(String expectedResult) {
        this.expectedResult = expectedResult;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public DifficultyLevel getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(DifficultyLevel difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public void setQuiz(Quiz quiz) {
        this.quiz = quiz;
    }

    public List<QuestionAnswer> getQuestionAnswers() {
        return questionAnswers;
    }

    public void setQuestionAnswers(List<QuestionAnswer> questionAnswers) {
        this.questionAnswers = questionAnswers;
    }

    @Override
    public String toString() {
        return "Question{" +
                "id=" + id +
                ", questionType=" + questionType +
                ", score=" + score +
                ", difficultyLevel=" + difficultyLevel +
                '}';
    }
}