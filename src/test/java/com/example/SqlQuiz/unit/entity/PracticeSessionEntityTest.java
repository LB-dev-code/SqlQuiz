package com.example.SqlQuiz.unit.entity;

import com.example.SqlQuiz.entity.PracticeRound;
import com.example.SqlQuiz.entity.PracticeSession;
import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PracticeSession Entity Unit Tests")
public class PracticeSessionEntityTest {

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
    }

    @Test
    @DisplayName("创建练习会话 - 带学生参数")
    void createSessionWithStudent() {
        // Act
        PracticeSession newSession = new PracticeSession(student);

        // Assert
        assertThat(newSession.getStudent()).isEqualTo(student);
        assertThat(newSession.getStatus()).isEqualTo(PracticeSession.SessionStatus.IN_PROGRESS);
        assertThat(newSession.getStartTime()).isNotNull();
        assertThat(newSession.getTotalRounds()).isEqualTo(0);
        assertThat(newSession.getTotalQuestions()).isEqualTo(0);
        assertThat(newSession.getTotalCorrect()).isEqualTo(0);
        assertThat(newSession.getOverallAccuracy()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("创建练习会话 - 带题型参数")
    void createSessionWithQuestionType() {
        // Act
        PracticeSession newSession = new PracticeSession(student, Question.QuestionType.SELECT_JOIN);

        // Assert
        assertThat(newSession.getStudent()).isEqualTo(student);
        assertThat(newSession.getTargetQuestionType()).isEqualTo(Question.QuestionType.SELECT_JOIN);
    }

    @Test
    @DisplayName("完成练习会话")
    void completeSession() {
        // Arrange
        PracticeRound round = new PracticeRound();
        round.setSession(session);
        round.setTotalQuestions(10);
        round.setCorrectCount(8);

        List<PracticeRound> rounds = new ArrayList<>();
        rounds.add(round);
        session.setRounds(rounds);

        // Act
        session.completeSession();

        // Assert
        assertThat(session.getStatus()).isEqualTo(PracticeSession.SessionStatus.COMPLETED);
        assertThat(session.getEndTime()).isNotNull();
        assertThat(session.getTotalQuestions()).isEqualTo(10);
        assertThat(session.getTotalCorrect()).isEqualTo(8);
        assertThat(session.getOverallAccuracy()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("暂停练习会话")
    void pauseSession() {
        // Act
        session.pauseSession();

        // Assert
        assertThat(session.getStatus()).isEqualTo(PracticeSession.SessionStatus.PAUSED);
    }

    @Test
    @DisplayName("恢复练习会话")
    void resumeSession() {
        // Arrange
        session.setStatus(PracticeSession.SessionStatus.PAUSED);

        // Act
        session.resumeSession();

        // Assert
        assertThat(session.getStatus()).isEqualTo(PracticeSession.SessionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("放弃练习会话")
    void abandonSession() {
        // Act
        session.abandonSession();

        // Assert
        assertThat(session.getStatus()).isEqualTo(PracticeSession.SessionStatus.ABANDONED);
        assertThat(session.getEndTime()).isNotNull();
    }

    @Test
    @DisplayName("检查是否进行中")
    void isInProgress() {
        // Assert
        assertThat(session.isInProgress()).isTrue();

        session.setStatus(PracticeSession.SessionStatus.PAUSED);
        assertThat(session.isInProgress()).isFalse();

        session.setStatus(PracticeSession.SessionStatus.COMPLETED);
        assertThat(session.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("添加练习轮次")
    void addRound() {
        // Arrange
        PracticeRound round = new PracticeRound();
        round.setSession(session);
        round.setTotalQuestions(5);
        round.setCorrectCount(3);

        // Act
        session.addRound(round);

        // Assert
        assertThat(session.getTotalRounds()).isEqualTo(1);
    }

    @Test
    @DisplayName("重新计算统计信息")
    void recalculateStatistics() {
        // Arrange
        PracticeRound round1 = new PracticeRound();
        round1.setTotalQuestions(10);
        round1.setCorrectCount(8);

        PracticeRound round2 = new PracticeRound();
        round2.setTotalQuestions(10);
        round2.setCorrectCount(6);

        List<PracticeRound> rounds = List.of(round1, round2);
        session.setRounds(rounds);

        // Act
        session.recalculateStatistics();

        // Assert
        assertThat(session.getTotalQuestions()).isEqualTo(20);
        assertThat(session.getTotalCorrect()).isEqualTo(14);
        assertThat(session.getOverallAccuracy()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("设置和获取选中的题型列表")
    void selectedTypes() {
        // Arrange
        List<Question.QuestionType> types = List.of(
            Question.QuestionType.SELECT_BASIC,
            Question.QuestionType.SELECT_JOIN
        );

        // Act
        session.setSelectedTypes(types);
        List<Question.QuestionType> result = session.getSelectedTypes();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).contains(Question.QuestionType.SELECT_BASIC);
        assertThat(result).contains(Question.QuestionType.SELECT_JOIN);
    }

    @Test
    @DisplayName("设置和获取题型分布")
    void distribution() {
        // Arrange
        Map<Question.QuestionType, Integer> distribution = new HashMap<>();
        distribution.put(Question.QuestionType.SELECT_BASIC, 4);
        distribution.put(Question.QuestionType.SELECT_JOIN, 3);

        // Act
        session.setDistribution(distribution);
        Map<Question.QuestionType, Integer> result = session.getDistribution();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(Question.QuestionType.SELECT_BASIC)).isEqualTo(4);
        assertThat(result.get(Question.QuestionType.SELECT_JOIN)).isEqualTo(3);
    }

    @Test
    @DisplayName("会话状态枚举 - 显示名称")
    void sessionStatusDisplayNames() {
        assertThat(PracticeSession.SessionStatus.IN_PROGRESS.getDisplayName()).isEqualTo("进行中");
        assertThat(PracticeSession.SessionStatus.PAUSED.getDisplayName()).isEqualTo("已暂停");
        assertThat(PracticeSession.SessionStatus.COMPLETED.getDisplayName()).isEqualTo("已完成");
        assertThat(PracticeSession.SessionStatus.ABANDONED.getDisplayName()).isEqualTo("已放弃");
    }

    @Test
    @DisplayName("Getter 和 Setter 方法")
    void gettersAndSetters() {
        LocalDateTime now = LocalDateTime.now();

        // Act & Assert
        session.setId(999L);
        assertThat(session.getId()).isEqualTo(999L);

        session.setTargetQuestionType(Question.QuestionType.SELECT_AGGREGATE);
        assertThat(session.getTargetQuestionType()).isEqualTo(Question.QuestionType.SELECT_AGGREGATE);

        session.setStartTime(now);
        assertThat(session.getStartTime()).isEqualTo(now);

        session.setEndTime(now.plusHours(1));
        assertThat(session.getEndTime()).isEqualTo(now.plusHours(1));

        session.setStatus(PracticeSession.SessionStatus.COMPLETED);
        assertThat(session.getStatus()).isEqualTo(PracticeSession.SessionStatus.COMPLETED);

        session.setTotalRounds(5);
        assertThat(session.getTotalRounds()).isEqualTo(5);

        session.setTotalQuestions(50);
        assertThat(session.getTotalQuestions()).isEqualTo(50);

        session.setTotalCorrect(40);
        assertThat(session.getTotalCorrect()).isEqualTo(40);

        session.setOverallAccuracy(0.8);
        assertThat(session.getOverallAccuracy()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("空题目列表重新计算统计")
    void recalculateStatistics_EmptyList() {
        // Arrange
        session.setRounds(new ArrayList<>());

        // Act
        session.recalculateStatistics();

        // Assert
        assertThat(session.getTotalQuestions()).isEqualTo(0);
        assertThat(session.getTotalCorrect()).isEqualTo(0);
        assertThat(session.getOverallAccuracy()).isEqualTo(0.0);
    }
}
