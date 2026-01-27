package com.example.SqlQuiz.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 练习会话实体
 * 记录一次完整的自主练习过程
 */
@Entity
@Table(name = "practice_sessions")
public class PracticeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "target_question_type")
    @Enumerated(EnumType.STRING)
    private Question.QuestionType targetQuestionType; // 学生选择的练习类型，null表示自动选择

    @Column(name = "selected_types", columnDefinition = "TEXT")
    private String selectedTypesJson; // 学生选择的题型列表（JSON），如["SELECT_BASIC", "SELECT_JOIN"]

    @Column(name = "question_distribution", columnDefinition = "TEXT")
    private String distributionJson; // 题型分布配置（JSON），如{"SELECT_BASIC": 4, "SELECT_JOIN": 3}

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionStatus status;

    @Column(name = "total_rounds", nullable = false)
    private Integer totalRounds = 0;

    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions = 0;

    @Column(name = "total_correct", nullable = false)
    private Integer totalCorrect = 0;

    @Column(name = "overall_accuracy")
    private Double overallAccuracy = 0.0;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PracticeRound> rounds;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum SessionStatus {
        IN_PROGRESS("进行中"),
        PAUSED("已暂停"),
        COMPLETED("已完成"),
        ABANDONED("已放弃");

        private final String displayName;

        SessionStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // 构造函数
    public PracticeSession() {}

    public PracticeSession(User student) {
        this.student = student;
        this.startTime = LocalDateTime.now();
        this.status = SessionStatus.IN_PROGRESS;
    }

    public PracticeSession(User student, Question.QuestionType targetQuestionType) {
        this(student);
        this.targetQuestionType = targetQuestionType;
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
    public void completeSession() {
        this.status = SessionStatus.COMPLETED;
        this.endTime = LocalDateTime.now();
        recalculateStatistics();
    }

    public void pauseSession() {
        this.status = SessionStatus.PAUSED;
    }

    public void resumeSession() {
        this.status = SessionStatus.IN_PROGRESS;
    }

    public void abandonSession() {
        this.status = SessionStatus.ABANDONED;
        this.endTime = LocalDateTime.now();
    }

    public void addRound(PracticeRound round) {
        this.totalRounds++;
        recalculateStatistics();
    }

    public void recalculateStatistics() {
        if (rounds != null && !rounds.isEmpty()) {
            totalQuestions = rounds.stream()
                    .mapToInt(PracticeRound::getTotalQuestions)
                    .sum();
            totalCorrect = rounds.stream()
                    .mapToInt(PracticeRound::getCorrectCount)
                    .sum();
            if (totalQuestions > 0) {
                overallAccuracy = (double) totalCorrect / totalQuestions;
            }
        }
    }

    public boolean isInProgress() {
        return status == SessionStatus.IN_PROGRESS;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    public Question.QuestionType getTargetQuestionType() { return targetQuestionType; }
    public void setTargetQuestionType(Question.QuestionType targetQuestionType) { this.targetQuestionType = targetQuestionType; }

    public String getSelectedTypesJson() { return selectedTypesJson; }
    public void setSelectedTypesJson(String selectedTypesJson) { this.selectedTypesJson = selectedTypesJson; }

    public String getDistributionJson() { return distributionJson; }
    public void setDistributionJson(String distributionJson) { this.distributionJson = distributionJson; }

    /**
     * 设置学生选择的题型列表
     */
    public void setSelectedTypes(List<Question.QuestionType> types) {
        if (types == null || types.isEmpty()) {
            this.selectedTypesJson = null;
        } else {
            try {
                List<String> typeNames = new ArrayList<>();
                for (Question.QuestionType type : types) {
                    typeNames.add(type.name());
                }
                this.selectedTypesJson = new ObjectMapper().writeValueAsString(typeNames);
            } catch (Exception e) {
                this.selectedTypesJson = null;
            }
        }
    }

    /**
     * 获取学生选择的题型列表
     */
    public List<Question.QuestionType> getSelectedTypes() {
        if (selectedTypesJson == null || selectedTypesJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<String> typeNames = new ObjectMapper().readValue(selectedTypesJson, new TypeReference<List<String>>() {});
            List<Question.QuestionType> types = new ArrayList<>();
            for (String typeName : typeNames) {
                types.add(Question.QuestionType.valueOf(typeName));
            }
            return types;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 设置题型分布配置
     */
    public void setDistribution(Map<Question.QuestionType, Integer> distribution) {
        if (distribution == null || distribution.isEmpty()) {
            this.distributionJson = null;
        } else {
            try {
                Map<String, Integer> distMap = new HashMap<>();
                for (Map.Entry<Question.QuestionType, Integer> entry : distribution.entrySet()) {
                    distMap.put(entry.getKey().name(), entry.getValue());
                }
                this.distributionJson = new ObjectMapper().writeValueAsString(distMap);
            } catch (Exception e) {
                this.distributionJson = null;
            }
        }
    }

    /**
     * 获取题型分布配置
     */
    public Map<Question.QuestionType, Integer> getDistribution() {
        if (distributionJson == null || distributionJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            Map<String, Integer> distMap = new ObjectMapper().readValue(distributionJson, new TypeReference<Map<String, Integer>>() {});
            Map<Question.QuestionType, Integer> distribution = new HashMap<>();
            for (Map.Entry<String, Integer> entry : distMap.entrySet()) {
                distribution.put(Question.QuestionType.valueOf(entry.getKey()), entry.getValue());
            }
            return distribution;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    public Integer getTotalRounds() { return totalRounds; }
    public void setTotalRounds(Integer totalRounds) { this.totalRounds = totalRounds; }

    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }

    public Integer getTotalCorrect() { return totalCorrect; }
    public void setTotalCorrect(Integer totalCorrect) { this.totalCorrect = totalCorrect; }

    public Double getOverallAccuracy() { return overallAccuracy; }
    public void setOverallAccuracy(Double overallAccuracy) { this.overallAccuracy = overallAccuracy; }

    public List<PracticeRound> getRounds() { return rounds; }
    public void setRounds(List<PracticeRound> rounds) { this.rounds = rounds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
