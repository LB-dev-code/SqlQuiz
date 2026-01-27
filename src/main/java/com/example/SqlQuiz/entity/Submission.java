package com.example.SqlQuiz.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "submissions")
public class Submission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;
    
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;
    
    @Column(name = "submit_time")
    private LocalDateTime submitTime;
    
    @Column(name = "time_spent") // 耗时（分钟）
    private Integer timeSpent;
    
    @Column(name = "total_score")
    @DecimalMin(value = "0.0", message = "总分不能为负数")
    private Double totalScore = 0.0;
    
    @Column(name = "max_score")
    @DecimalMin(value = "0.0", message = "满分不能为负数")
    private Double maxScore;
    
    @Column(name = "percentage")
    @DecimalMin(value = "0.0", message = "百分比不能为负数")
    @DecimalMax(value = "100.0", message = "百分比不能超过100%")
    private Double percentage;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private SubmissionStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String feedback; // 自动生成的反馈
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // 提交的学生
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    
    // 参加的测试
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;
    
    // 每道题的答题记录
    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<QuestionAnswer> questionAnswers;
    
    public enum SubmissionStatus {
        IN_PROGRESS("进行中"),
        SUBMITTED("已提交"),
        AUTO_SUBMITTED("自动提交"),
        GRADED("已评分");
        
        private final String displayName;
        
        SubmissionStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // 构造函数
    public Submission() {
    }
    
    public Submission(User student, Quiz quiz, Integer attemptNumber) {
        this.student = student;
        this.quiz = quiz;
        this.attemptNumber = attemptNumber;
        this.startTime = LocalDateTime.now();
        this.status = SubmissionStatus.IN_PROGRESS;
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
    public void calculateScore() {
        if (questionAnswers != null && !questionAnswers.isEmpty()) {
            totalScore = questionAnswers.stream()
                    .mapToDouble(QuestionAnswer::getScore)
                    .sum();
            
            maxScore = questionAnswers.stream()
                    .mapToDouble(qa -> qa.getQuestion().getScore())
                    .sum();
            
            percentage = maxScore > 0 ? (totalScore / maxScore) * 100 : 0.0;
        }
    }
    
    public void submit() {
        this.submitTime = LocalDateTime.now();
        this.status = SubmissionStatus.SUBMITTED;
        
        if (startTime != null) {
            long minutes = java.time.Duration.between(startTime, submitTime).toMinutes();
            this.timeSpent = (int) minutes;
        }
        
        //calculateScore();
    }
    
    public boolean isInProgress() {
        return status == SubmissionStatus.IN_PROGRESS;
    }
    
    public boolean isCompleted() {
        return status == SubmissionStatus.SUBMITTED || 
               status == SubmissionStatus.AUTO_SUBMITTED || 
               status == SubmissionStatus.GRADED;
    }
    
    public String getGrade() {
        if (percentage == null) return "未评分";
        
        if (percentage >= 90) return "优秀";
        else if (percentage >= 80) return "良好";
        else if (percentage >= 70) return "中等";
        else if (percentage >= 60) return "及格";
        else return "不及格";
    }
    
    // Getter和Setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Integer getAttemptNumber() {
        return attemptNumber;
    }
    
    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public LocalDateTime getSubmitTime() {
        return submitTime;
    }
    
    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
    }
    
    public Integer getTimeSpent() {
        return timeSpent;
    }
    
    public void setTimeSpent(Integer timeSpent) {
        this.timeSpent = timeSpent;
    }
    
    public Double getTotalScore() {
        return totalScore;
    }
    
    public void setTotalScore(Double totalScore) {
        this.totalScore = totalScore;
    }
    
    public Double getMaxScore() {
        return maxScore;
    }
    
    public void setMaxScore(Double maxScore) {
        this.maxScore = maxScore;
    }
    
    public Double getPercentage() {
        return percentage;
    }
    
    public void setPercentage(Double percentage) {
        this.percentage = percentage;
    }
    
    public SubmissionStatus getStatus() {
        return status;
    }
    
    public void setStatus(SubmissionStatus status) {
        this.status = status;
    }
    
    public String getFeedback() {
        return feedback;
    }
    
    public void setFeedback(String feedback) {
        this.feedback = feedback;
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
    
    public User getStudent() {
        return student;
    }
    
    public void setStudent(User student) {
        this.student = student;
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
        return "Submission{" +
                "id=" + id +
                ", attemptNumber=" + attemptNumber +
                ", status=" + status +
                ", totalScore=" + totalScore +
                ", percentage=" + percentage +
                '}';
    }
}