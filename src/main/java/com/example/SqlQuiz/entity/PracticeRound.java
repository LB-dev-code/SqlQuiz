package com.example.SqlQuiz.entity;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 练习轮次实体
 * 每轮包含10道题目
 */
@Entity
@Table(name = "practice_rounds")
public class PracticeRound {

    public static final int QUESTIONS_PER_ROUND = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private PracticeSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private RoundStatus status;

    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions = QUESTIONS_PER_ROUND;

    @Column(name = "correct_count", nullable = false)
    private Integer correctCount = 0;

    @Column(name = "current_question_index", nullable = false)
    private Integer currentQuestionIndex = 0;

    @Column(name = "accuracy")
    private Double accuracy = 0.0;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PracticeAnswer> answers;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum RoundStatus {
        IN_PROGRESS("进行中"),
        COMPLETED("已完成"),
        ABANDONED("已放弃");

        private final String displayName;

        RoundStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public PracticeRound() {}

    public PracticeRound(PracticeSession session, User student, Integer roundNumber) {
        this.session = session;
        this.student = student;
        this.roundNumber = roundNumber;
        this.startTime = LocalDateTime.now();
        this.status = RoundStatus.IN_PROGRESS;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void completeRound() {
        this.status = RoundStatus.COMPLETED;
        this.endTime = LocalDateTime.now();
        recalculateStatistics();
    }

    public void abandonRound() {
        this.status = RoundStatus.ABANDONED;
        this.endTime = LocalDateTime.now();
    }

    public void recordAnswer(boolean isCorrect) {
        if (isCorrect) {
            correctCount++;
        }
        currentQuestionIndex++;
        recalculateStatistics();
    }

    public void recalculateStatistics() {
        if (currentQuestionIndex > 0) {
            accuracy = (double) correctCount / currentQuestionIndex;
        }
    }

    public boolean isCompleted() {
        return status == RoundStatus.COMPLETED;
    }

    public boolean isInProgress() {
        return status == RoundStatus.IN_PROGRESS;
    }

    public boolean hasMoreQuestions() {
        return currentQuestionIndex < totalQuestions;
    }

    public int getRemainingQuestions() {
        return totalQuestions - currentQuestionIndex;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PracticeSession getSession() { return session; }
    public void setSession(PracticeSession session) { this.session = session; }

    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    public Integer getRoundNumber() { return roundNumber; }
    public void setRoundNumber(Integer roundNumber) { this.roundNumber = roundNumber; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public RoundStatus getStatus() { return status; }
    public void setStatus(RoundStatus status) { this.status = status; }

    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }

    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }

    public Integer getCurrentQuestionIndex() { return currentQuestionIndex; }
    public void setCurrentQuestionIndex(Integer currentQuestionIndex) { this.currentQuestionIndex = currentQuestionIndex; }

    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public List<PracticeAnswer> getAnswers() { return answers; }
    public void setAnswers(List<PracticeAnswer> answers) { this.answers = answers; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
