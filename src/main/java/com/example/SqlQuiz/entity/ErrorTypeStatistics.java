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
import jakarta.persistence.UniqueConstraint;

/**
 * 错误类型统计实体
 * 追踪学生在各题型上的正确率
 */
@Entity
@Table(name = "error_type_statistics", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "question_type"}))
public class ErrorTypeStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "question_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Question.QuestionType questionType;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount = 0;

    @Column(name = "correct_count", nullable = false)
    private Integer correctCount = 0;

    @Column(name = "error_count", nullable = false)
    private Integer errorCount = 0;

    @Column(name = "accuracy", nullable = false)
    private Double accuracy = 0.0;

    @Column(name = "last_practiced_at")
    private LocalDateTime lastPracticedAt;

    @Column(name = "is_mastered", nullable = false)
    private Boolean isMastered = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 构造函数
    public ErrorTypeStatistics() {}

    public ErrorTypeStatistics(User student, Question.QuestionType questionType) {
        this.student = student;
        this.questionType = questionType;
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
    public void recordAnswer(boolean isCorrect) {
        totalCount++;
        if (isCorrect) {
            correctCount++;
        } else {
            errorCount++;
        }
        recalculateAccuracy();
        lastPracticedAt = LocalDateTime.now();
    }

    public void recalculateAccuracy() {
        if (totalCount > 0) {
            accuracy = (double) correctCount / totalCount;
            isMastered = accuracy >= 0.90;
        }
    }

    public double getErrorFrequency() {
        if (totalCount == 0) return 0.0;
        return (double) errorCount / totalCount;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    public Question.QuestionType getQuestionType() { return questionType; }
    public void setQuestionType(Question.QuestionType questionType) { this.questionType = questionType; }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }

    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }

    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }

    public LocalDateTime getLastPracticedAt() { return lastPracticedAt; }
    public void setLastPracticedAt(LocalDateTime lastPracticedAt) { this.lastPracticedAt = lastPracticedAt; }

    public Boolean getIsMastered() { return isMastered; }
    public void setIsMastered(Boolean isMastered) { this.isMastered = isMastered; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
