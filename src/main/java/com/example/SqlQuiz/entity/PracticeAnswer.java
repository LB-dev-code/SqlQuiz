package com.example.SqlQuiz.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 练习答题记录实体
 * 记录每道练习题的答案和反馈
 */
@Entity
@Table(name = "practice_answers")
public class PracticeAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_round_id", nullable = false, referencedColumnName = "id")
    private PracticeRound round;

    @Column(name = "question_index", nullable = false)
    private Integer questionIndex; // 题目在轮次中的序号（0-9）

    @Column(name = "question_type")
    @Enumerated(EnumType.STRING)
    private Question.QuestionType questionType;

    @Column(name = "difficulty_level")
    @Enumerated(EnumType.STRING)
    private Question.DifficultyLevel difficultyLevel;

    @Column(name = "question_title", columnDefinition = "TEXT")
    private String questionTitle;

    @Column(name = "question_content", columnDefinition = "TEXT")
    private String questionContent;

    @Column(name = "database_context", columnDefinition = "TEXT")
    private String databaseContext;

    @Column(name = "expected_sql", columnDefinition = "TEXT")
    private String expectedSql;

    @Column(name = "setup_sql", columnDefinition = "TEXT")
    private String setupSql; // 建表和插入数据的SQL语句

    @Column(name = "table_prefix")
    private String tablePrefix; // 题目使用的表前缀（用于追踪testdb中的表）

    @Column(name = "student_sql", columnDefinition = "TEXT")
    private String studentSql;

    @Column(name = "execution_result", columnDefinition = "TEXT")
    private String executionResult;

    @Column(name = "execution_error", columnDefinition = "TEXT")
    private String executionError;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "score")
    private Double score = 0.0;

    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback; // AI生成的详细反馈和纠错建议

    @Column(name = "answer_time")
    private LocalDateTime answerTime;

    @Column(name = "answered")
    private Boolean answered = false; // 标记答案是否已提交

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds; // 答题耗时（秒）

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 构造函数
    public PracticeAnswer() {}

    public PracticeAnswer(PracticeRound round, Integer questionIndex) {
        this.round = round;
        this.questionIndex = questionIndex;
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

    // 业务方法
    public void setQuestionInfo(String title, String content, String dbContext, 
                                 String expectedSql, Question.QuestionType type, 
                                 Question.DifficultyLevel difficulty) {
        this.questionTitle = title;
        this.questionContent = content;
        this.databaseContext = dbContext;
        this.expectedSql = expectedSql;
        this.questionType = type;
        this.difficultyLevel = difficulty;
    }

    public void setQuestionInfoWithSetup(String title, String content, String dbContext, 
                                          String expectedSql, String setupSql, String tablePrefix,
                                          Question.QuestionType type, Question.DifficultyLevel difficulty) {
        this.questionTitle = title;
        this.questionContent = content;
        this.databaseContext = dbContext;
        this.expectedSql = expectedSql;
        this.setupSql = setupSql;
        this.tablePrefix = tablePrefix;
        this.questionType = type;
        this.difficultyLevel = difficulty;
    }

    public void submitAnswer(String sql, String result, String error, 
                              boolean correct, double score, String feedback) {
        this.studentSql = sql;
        this.executionResult = result;
        this.executionError = error;
        this.isCorrect = correct;
        this.score = score;
        this.aiFeedback = feedback;
        this.answerTime = LocalDateTime.now();
    }

    public boolean hasAnswer() {
        return studentSql != null && !studentSql.trim().isEmpty();
    }

    public boolean isExecutedSuccessfully() {
        return executionError == null || executionError.trim().isEmpty();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PracticeRound getRound() { return round; }
    public void setRound(PracticeRound round) { this.round = round; }

    public Integer getQuestionIndex() { return questionIndex; }
    public void setQuestionIndex(Integer questionIndex) { this.questionIndex = questionIndex; }

    public Question.QuestionType getQuestionType() { return questionType; }
    public void setQuestionType(Question.QuestionType questionType) { this.questionType = questionType; }

    public Question.DifficultyLevel getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(Question.DifficultyLevel difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public String getQuestionTitle() { return questionTitle; }
    public void setQuestionTitle(String questionTitle) { this.questionTitle = questionTitle; }

    public String getQuestionContent() { return questionContent; }
    public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }

    public String getDatabaseContext() { return databaseContext; }
    public void setDatabaseContext(String databaseContext) { this.databaseContext = databaseContext; }

    public String getExpectedSql() { return expectedSql; }
    public void setExpectedSql(String expectedSql) { this.expectedSql = expectedSql; }

    public String getSetupSql() { return setupSql; }
    public void setSetupSql(String setupSql) { this.setupSql = setupSql; }

    public String getTablePrefix() { return tablePrefix; }
    public void setTablePrefix(String tablePrefix) { this.tablePrefix = tablePrefix; }

    public String getStudentSql() { return studentSql; }
    public void setStudentSql(String studentSql) { this.studentSql = studentSql; }

    public String getExecutionResult() { return executionResult; }
    public void setExecutionResult(String executionResult) { this.executionResult = executionResult; }

    public String getExecutionError() { return executionError; }
    public void setExecutionError(String executionError) { this.executionError = executionError; }

    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public String getAiFeedback() { return aiFeedback; }
    public void setAiFeedback(String aiFeedback) { this.aiFeedback = aiFeedback; }

    public LocalDateTime getAnswerTime() { return answerTime; }
    public void setAnswerTime(LocalDateTime answerTime) { this.answerTime = answerTime; }

    public Boolean isAnswered() { return answered != null && answered; }
    public Boolean getAnswered() { return answered; }
    public void setAnswered(Boolean answered) { this.answered = answered; }

    public Integer getTimeSpentSeconds() { return timeSpentSeconds; }
    public void setTimeSpentSeconds(Integer timeSpentSeconds) { this.timeSpentSeconds = timeSpentSeconds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
