package com.example.SqlQuiz.unit.entity;

import com.example.SqlQuiz.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PracticeRound Entity Unit Tests")
public class PracticeRoundEntityTest {

    private PracticeRound round;
    private PracticeSession session;
    private User student;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId(1L);
        student.setUsername("student");
        student.setRole(User.Role.STUDENT);

        session = new PracticeSession(student);
        session.setId(1L);
        session.setStatus(PracticeSession.SessionStatus.IN_PROGRESS);

        round = new PracticeRound();
        round.setId(1L);
        round.setSession(session);
        round.setStudent(student);
        round.setRoundNumber(1);
        round.setStatus(PracticeRound.RoundStatus.IN_PROGRESS);
        round.setTotalQuestions(10);
        round.setCorrectCount(0);
        round.setCurrentQuestionIndex(0);
    }

    @Test
    @DisplayName("创建练习轮次 - 带参数构造函数")
    void createPracticeRound_WithConstructor() {
        // Act
        PracticeRound newRound = new PracticeRound(session, student, 2);

        // Assert
        assertThat(newRound.getSession()).isEqualTo(session);
        assertThat(newRound.getStudent()).isEqualTo(student);
        assertThat(newRound.getRoundNumber()).isEqualTo(2);
        assertThat(newRound.getStatus()).isEqualTo(PracticeRound.RoundStatus.IN_PROGRESS);
        assertThat(newRound.getStartTime()).isNotNull();
    }

    @Test
    @DisplayName("创建练习轮次 - 默认构造函数")
    void createPracticeRound_DefaultConstructor() {
        // Act
        PracticeRound newRound = new PracticeRound();

        // Assert
        assertThat(newRound).isNotNull();
        assertThat(newRound.getTotalQuestions()).isEqualTo(PracticeRound.QUESTIONS_PER_ROUND);
        assertThat(newRound.getCorrectCount()).isEqualTo(0);
        assertThat(newRound.getCurrentQuestionIndex()).isEqualTo(0);
        assertThat(newRound.getAccuracy()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("完成轮次")
    void completeRound() {
        // Act
        round.completeRound();

        // Assert
        assertThat(round.getStatus()).isEqualTo(PracticeRound.RoundStatus.COMPLETED);
        assertThat(round.getEndTime()).isNotNull();
    }

    @Test
    @DisplayName("放弃轮次")
    void abandonRound() {
        // Act
        round.abandonRound();

        // Assert
        assertThat(round.getStatus()).isEqualTo(PracticeRound.RoundStatus.ABANDONED);
        assertThat(round.getEndTime()).isNotNull();
    }

    @Test
    @DisplayName("记录正确答案")
    void recordAnswer_Correct() {
        // Arrange
        int initialCorrect = round.getCorrectCount();
        int initialIndex = round.getCurrentQuestionIndex();

        // Act
        round.recordAnswer(true);

        // Assert
        assertThat(round.getCorrectCount()).isEqualTo(initialCorrect + 1);
        assertThat(round.getCurrentQuestionIndex()).isEqualTo(initialIndex + 1);
    }

    @Test
    @DisplayName("记录错误答案")
    void recordAnswer_Incorrect() {
        // Arrange
        int initialCorrect = round.getCorrectCount();
        int initialIndex = round.getCurrentQuestionIndex();

        // Act
        round.recordAnswer(false);

        // Assert
        assertThat(round.getCorrectCount()).isEqualTo(initialCorrect); // Unchanged
        assertThat(round.getCurrentQuestionIndex()).isEqualTo(initialIndex + 1);
    }

    @Test
    @DisplayName("重新计算统计信息")
    void recalculateStatistics() {
        // Arrange
        round.setCorrectCount(7);
        round.setCurrentQuestionIndex(10);

        // Act
        round.recalculateStatistics();

        // Assert
        assertThat(round.getAccuracy()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("重新计算统计信息 - 无题目")
    void recalculateStatistics_NoQuestions() {
        // Arrange
        round.setCorrectCount(0);
        round.setCurrentQuestionIndex(0);

        // Act
        round.recalculateStatistics();

        // Assert
        assertThat(round.getAccuracy()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("检查是否进行中")
    void isInProgress() {
        // Assert
        assertThat(round.isInProgress()).isTrue();

        round.setStatus(PracticeRound.RoundStatus.COMPLETED);
        assertThat(round.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("检查是否已完成")
    void isCompleted() {
        // Assert
        assertThat(round.isCompleted()).isFalse();

        round.setStatus(PracticeRound.RoundStatus.COMPLETED);
        assertThat(round.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("检查是否还有更多题目")
    void hasMoreQuestions() {
        // Assert - Initial state
        assertThat(round.hasMoreQuestions()).isTrue();

        // Complete all questions
        round.setCurrentQuestionIndex(10);
        assertThat(round.hasMoreQuestions()).isFalse();
    }

    @Test
    @DisplayName("获取剩余题目数")
    void getRemainingQuestions() {
        // Assert - Initial state
        assertThat(round.getRemainingQuestions()).isEqualTo(10);

        // Completed 3 questions
        round.setCurrentQuestionIndex(3);
        assertThat(round.getRemainingQuestions()).isEqualTo(7);
    }

    @Test
    @DisplayName("轮次状态枚举")
    void roundStatusValues() {
        PracticeRound.RoundStatus[] statuses = PracticeRound.RoundStatus.values();

        assertThat(statuses).contains(
            PracticeRound.RoundStatus.IN_PROGRESS,
            PracticeRound.RoundStatus.COMPLETED,
            PracticeRound.RoundStatus.ABANDONED
        );
    }

    @Test
    @DisplayName("轮次状态枚举 - 显示名称")
    void roundStatusDisplayNames() {
        assertThat(PracticeRound.RoundStatus.IN_PROGRESS.getDisplayName()).isEqualTo("进行中");
        assertThat(PracticeRound.RoundStatus.COMPLETED.getDisplayName()).isEqualTo("已完成");
        assertThat(PracticeRound.RoundStatus.ABANDONED.getDisplayName()).isEqualTo("已放弃");
    }

    @Test
    @DisplayName("Getter 和 Setter 方法")
    void gettersAndSetters() {
        LocalDateTime now = LocalDateTime.now();

        // Act & Assert
        round.setId(999L);
        assertThat(round.getId()).isEqualTo(999L);

        round.setRoundNumber(5);
        assertThat(round.getRoundNumber()).isEqualTo(5);

        round.setStartTime(now);
        assertThat(round.getStartTime()).isEqualTo(now);

        round.setEndTime(now.plusMinutes(30));
        assertThat(round.getEndTime()).isEqualTo(now.plusMinutes(30));

        round.setTotalQuestions(20);
        assertThat(round.getTotalQuestions()).isEqualTo(20);

        round.setCorrectCount(15);
        assertThat(round.getCorrectCount()).isEqualTo(15);

        round.setCurrentQuestionIndex(15);
        assertThat(round.getCurrentQuestionIndex()).isEqualTo(15);

        round.setAccuracy(0.75);
        assertThat(round.getAccuracy()).isEqualTo(0.75);

        round.setFeedback("Good job!");
        assertThat(round.getFeedback()).isEqualTo("Good job!");
    }

    @Test
    @DisplayName("边界值测试 - 全部正确")
    void boundary_AllCorrect() {
        round.setTotalQuestions(10);
        round.setCorrectCount(10);
        round.setCurrentQuestionIndex(10);
        round.recalculateStatistics();

        assertThat(round.getAccuracy()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("边界值测试 - 全部错误")
    void boundary_NoneCorrect() {
        round.setTotalQuestions(10);
        round.setCorrectCount(0);
        round.setCurrentQuestionIndex(10);
        round.recalculateStatistics();

        assertThat(round.getAccuracy()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("关联练习会话")
    void sessionAssociation() {
        PracticeSession newSession = new PracticeSession(student);
        newSession.setId(2L);

        round.setSession(newSession);

        assertThat(round.getSession()).isEqualTo(newSession);
        assertThat(round.getSession().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("关联学生")
    void studentAssociation() {
        User newStudent = new User();
        newStudent.setId(2L);
        newStudent.setUsername("newstudent");
        newStudent.setRole(User.Role.STUDENT);

        round.setStudent(newStudent);

        assertThat(round.getStudent()).isEqualTo(newStudent);
        assertThat(round.getStudent().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("每轮题目数常量")
    void questionsPerRound_Constant() {
        assertThat(PracticeRound.QUESTIONS_PER_ROUND).isEqualTo(10);
    }

    @Test
    @DisplayName("记录答案流程 - 完整一轮")
    void recordAnswer_FullRound() {
        // Record answers for 10 questions, 7 correct
        for (int i = 0; i < 10; i++) {
            round.recordAnswer(i < 7); // First 7 are correct
        }

        // Assert
        assertThat(round.getCorrectCount()).isEqualTo(7);
        assertThat(round.getCurrentQuestionIndex()).isEqualTo(10);
        assertThat(round.getAccuracy()).isEqualTo(0.7);
        assertThat(round.hasMoreQuestions()).isFalse();
        assertThat(round.getRemainingQuestions()).isEqualTo(0);
    }

    @Test
    @DisplayName("默认构造函数创建的轮次状态")
    void defaultRoundStatus() {
        PracticeRound newRound = new PracticeRound();

        assertThat(newRound.getStatus()).isNull(); // Default is null, needs to be set manually
        assertThat(newRound.getAccuracy()).isEqualTo(0.0);
        assertThat(newRound.getTotalQuestions()).isEqualTo(10); // Use constant default value
    }
}
