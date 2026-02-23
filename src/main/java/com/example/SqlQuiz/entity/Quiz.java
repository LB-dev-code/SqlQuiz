package com.example.SqlQuiz.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "quizzes")
public class Quiz {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    @NotBlank(message = "测试标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200字符")
    private String title;
    
    @Column(columnDefinition = "TEXT")
    @Size(max = 1000, message = "描述长度不能超过1000字符")
    private String description;
    
    @Column(name = "time_limit")
    @NotNull(message = "时间限制不能为空")
    private Integer timeLimit; // 分钟
    
    @Column(name = "max_attempts")
    @NotNull(message = "最大尝试次数不能为空")
    private Integer maxAttempts;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "start_time")
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // 创建者（教师）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;
    
    // 测试包含的题目
    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Question> questions;
    
    // 学生的提交记录
    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Submission> submissions;
    
    // 构造函数
    public Quiz() {
    }
    
    public Quiz(String title, String description, Integer timeLimit, Integer maxAttempts, User teacher) {
        this.title = title;
        this.description = description;
        this.timeLimit = timeLimit;
        this.maxAttempts = maxAttempts;
        this.teacher = teacher;
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
    public boolean isOpen() {
        LocalDateTime now = LocalDateTime.now();
        return isActive &&
               (startTime == null || !now.isBefore(startTime)) &&   // !isBefore = >= 包含边界
               (endTime == null || !now.isAfter(endTime));          // !isAfter = <= 包含边界
    }
    
    public boolean hasTimeLimit() {
        return timeLimit != null && timeLimit > 0;
    }
    
    public int getTotalQuestions() {
        return questions != null ? questions.size() : 0;
    }
    
    public double getTotalScore() {
        return questions != null ? 
               questions.stream().mapToDouble(Question::getScore).sum() : 0.0;
    }
    
    // Getter和Setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Integer getTimeLimit() {
        return timeLimit;
    }
    
    public void setTimeLimit(Integer timeLimit) {
        this.timeLimit = timeLimit;
    }
    
    public Integer getMaxAttempts() {
        return maxAttempts;
    }
    
    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
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
    
    public User getTeacher() {
        return teacher;
    }
    
    public void setTeacher(User teacher) {
        this.teacher = teacher;
    }
    
    public List<Question> getQuestions() {
        return questions;
    }
    
    public void setQuestions(List<Question> questions) {
        this.questions = questions;
    }
    
    public List<Submission> getSubmissions() {
        return submissions;
    }
    
    public void setSubmissions(List<Submission> submissions) {
        this.submissions = submissions;
    }
    
    @Override
    public String toString() {
        return "Quiz{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", timeLimit=" + timeLimit +
                ", maxAttempts=" + maxAttempts +
                ", isActive=" + isActive +
                '}';
    }
}