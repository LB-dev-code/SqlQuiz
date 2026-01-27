package com.example.SqlQuiz.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_table_metadata", uniqueConstraints = {
    @UniqueConstraint(columnNames = "table_prefix")
})
public class QuizTableMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_prefix", nullable = false, unique = true, length = 50)
    private String tablePrefix;

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
    }

    public QuizTableMetadata() {
    }

    public QuizTableMetadata(String tablePrefix, Long questionId, Long creatorId) {
        this.tablePrefix = tablePrefix;
        this.questionId = questionId;
        this.creatorId = creatorId;
        this.isActive = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    @Override
    public String toString() {
        return "QuizTableMetadata{" +
                "id=" + id +
                ", tablePrefix='" + tablePrefix + '\'' +
                ", questionId=" + questionId +
                ", createdAt=" + createdAt +
                ", creatorId=" + creatorId +
                ", isActive=" + isActive +
                '}';
    }
}
