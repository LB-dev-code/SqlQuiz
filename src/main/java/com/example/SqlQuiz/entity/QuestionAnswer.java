package com.example.SqlQuiz.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;

import java.time.LocalDateTime;

@Entity
@Table(name = "question_answers")
public class QuestionAnswer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "student_sql", columnDefinition = "TEXT")
    private String studentSql; // 学生提交的SQL语句
    
    @Column(name = "execution_result", columnDefinition = "TEXT")
    private String executionResult; // SQL执行结果（JSON格式）
    
    @Column(name = "execution_error", columnDefinition = "TEXT")
    private String executionError; // SQL执行错误信息
    
    @Column(name = "is_correct")
    private Boolean isCorrect = null; // 答案是否正确
    
    @Column(name = "score")
    @DecimalMin(value = "0.0", message = "得分不能为负数")
    private Double score = 0.0; // 获得的分数
    
    @Column(name = "auto_feedback", columnDefinition = "TEXT")
    private String autoFeedback; // 自动生成的反馈
    
    @Column(name = "manual_feedback", columnDefinition = "TEXT")
    private String manualFeedback; // 教师手动反馈
    
    @Column(name = "answer_time")
    private LocalDateTime answerTime; // 答题时间
    
    @Column(name = "execution_time_ms")
    private Long executionTimeMs; // SQL执行耗时（毫秒）
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // 所属题目
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;
    
    // 所属提交记录
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;
    
    // 构造函数
    public QuestionAnswer() {
    }
    
    public QuestionAnswer(Question question, Submission submission) {
        this.question = question;
        this.submission = submission;
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
    public void submitAnswer(String sql) {
        this.studentSql = sql;
        this.answerTime = LocalDateTime.now();
    }
    
    public void setExecutionResult(String result, Long executionTime) {
        this.executionResult = result;
        this.executionTimeMs = executionTime;
        this.executionError = null;
    }
    
    public void setExecutionError(String error) {
        this.executionError = error;
        this.executionResult = null;
        this.isCorrect = false;
        this.score = 0.0;
    }
    
    public void grade(boolean correct, double score, String feedback) {
        this.isCorrect = correct;
        this.score = score;
        this.autoFeedback = feedback;
    }
    
    public boolean hasAnswer() {
        return studentSql != null && !studentSql.trim().isEmpty();
    }
    
    public boolean isExecutedSuccessfully() {
        return executionError == null || executionError.trim().isEmpty();
    }
    
    public String getFinalFeedback() {
        StringBuilder feedback = new StringBuilder();
        
        if (autoFeedback != null && !autoFeedback.trim().isEmpty()) {
            feedback.append(autoFeedback);
        }
        
        if (manualFeedback != null && !manualFeedback.trim().isEmpty()) {
            if (feedback.length() > 0) {
                feedback.append("\n\n教师点评：");
            }
            feedback.append(manualFeedback);
        }
        
        return feedback.toString();
    }
    
    // Getter和Setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getStudentSql() {
        return studentSql;
    }
    
    public void setStudentSql(String studentSql) {
        this.studentSql = studentSql;
    }
    
    public String getExecutionResult() {
        return executionResult;
    }
    
    public void setExecutionResult(String executionResult) {
        this.executionResult = executionResult;
    }
    
    public String getExecutionError() {
        return executionError;
    }
    
    public Boolean getIsCorrect() {
        return isCorrect;
    }
    
    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }
    
    public Double getScore() {
        return score;
    }
    
    public void setScore(Double score) {
        this.score = score;
    }
    
    public String getAutoFeedback() {
        return autoFeedback;
    }
    
    public void setAutoFeedback(String autoFeedback) {
        this.autoFeedback = autoFeedback;
    }
    
    public String getManualFeedback() {
        return manualFeedback;
    }
    
    public void setManualFeedback(String manualFeedback) {
        this.manualFeedback = manualFeedback;
    }
    
    public LocalDateTime getAnswerTime() {
        return answerTime;
    }
    
    public void setAnswerTime(LocalDateTime answerTime) {
        this.answerTime = answerTime;
    }
    
    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }
    
    public void setExecutionTimeMs(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
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
    
    public Question getQuestion() {
        return question;
    }
    
    public void setQuestion(Question question) {
        this.question = question;
    }
    
    public Submission getSubmission() {
        return submission;
    }
    
    public void setSubmission(Submission submission) {
        this.submission = submission;
    }
    
    @Override
    public String toString() {
        return "QuestionAnswer{" +
                "id=" + id +
                ", isCorrect=" + isCorrect +
                ", score=" + score +
                ", hasAnswer=" + hasAnswer() +
                '}';
    }
}