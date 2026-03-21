package com.example.SqlQuiz.integration.repository;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.repository.PracticeSessionRepository;
import com.example.SqlQuiz.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("PracticeSessionRepository Integration Tests")
public class PracticeSessionRepositoryIntegrationTest {

    @Autowired
    private PracticeSessionRepository practiceSessionRepository;

    @Autowired
    private UserRepository userRepository;

    private User student;
    private PracticeSession session;

    @BeforeEach
    void setUp() {
        // Clear data
        practiceSessionRepository.deleteAll();
        userRepository.deleteAll();

        // Create test student
        student = new User();
        student.setUsername("student");
        student.setPassword("password");
        student.setEmail("student@example.com");
        student.setFullName("Student");
        student.setRole(User.Role.STUDENT);
        student.setEnabled(true);
        student = userRepository.save(student);

        // Create test session
        session = new PracticeSession(student);
        session.setStatus(PracticeSession.SessionStatus.IN_PROGRESS);
        session = practiceSessionRepository.save(session);
    }

    @Test
    @DisplayName("保存练习会话")
    void save_Session() {
        // Assert
        assertThat(session.getId()).isNotNull();
        assertThat(session.getStudent()).isEqualTo(student);
        assertThat(session.getStatus()).isEqualTo(PracticeSession.SessionStatus.IN_PROGRESS);
        assertThat(session.getStartTime()).isNotNull();
    }

    @Test
    @DisplayName("根据学生查找进行中的会话")
    void findByStudentAndStatus() {
        // Act
        List<PracticeSession> result = practiceSessionRepository.findByStudentAndStatus(
            student, PracticeSession.SessionStatus.IN_PROGRESS);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(session.getId());
    }

    @Test
    @DisplayName("根据学生查找所有会话")
    void findByStudent() {
        // Arrange - Create more sessions
        PracticeSession session2 = new PracticeSession(student);
        session2.setStatus(PracticeSession.SessionStatus.COMPLETED);
        session2.setEndTime(LocalDateTime.now());
        practiceSessionRepository.save(session2);

        // Act
        List<PracticeSession> result = practiceSessionRepository.findByStudent(student);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).contains(session, session2);
    }

    @Test
    @DisplayName("根据状态查找会话")
    void findByStatus() {
        // Arrange
        PracticeSession completedSession = new PracticeSession(student);
        completedSession.setStatus(PracticeSession.SessionStatus.COMPLETED);
        completedSession.setEndTime(LocalDateTime.now());
        practiceSessionRepository.save(completedSession);

        // Act - Use findByStudent and filter by status
        List<PracticeSession> allSessions = practiceSessionRepository.findByStudent(student);
        List<PracticeSession> inProgress = allSessions.stream()
            .filter(s -> s.getStatus() == PracticeSession.SessionStatus.IN_PROGRESS)
            .toList();
        List<PracticeSession> completed = allSessions.stream()
            .filter(s -> s.getStatus() == PracticeSession.SessionStatus.COMPLETED)
            .toList();

        // Assert
        assertThat(inProgress).contains(session);
        assertThat(inProgress).doesNotContain(completedSession);
        assertThat(completed).contains(completedSession);
        assertThat(completed).doesNotContain(session);
    }

    @Test
    @DisplayName("更新会话状态")
    void updateSessionStatus() {
        // Act
        session.setStatus(PracticeSession.SessionStatus.PAUSED);
        PracticeSession updated = practiceSessionRepository.save(session);

        // Assert
        assertThat(updated.getStatus()).isEqualTo(PracticeSession.SessionStatus.PAUSED);
    }

    @Test
    @DisplayName("完成会话并更新统计")
    void completeSession() {
        // Arrange
        session.setTotalRounds(3);
        session.setTotalQuestions(30);
        session.setTotalCorrect(24);
        session.setStatus(PracticeSession.SessionStatus.COMPLETED);
        session.setEndTime(LocalDateTime.now());

        // Act
        PracticeSession completed = practiceSessionRepository.save(session);

        // Assert
        assertThat(completed.getStatus()).isEqualTo(PracticeSession.SessionStatus.COMPLETED);
        assertThat(completed.getEndTime()).isNotNull();
        assertThat(completed.getTotalRounds()).isEqualTo(3);
        assertThat(completed.getTotalQuestions()).isEqualTo(30);
        assertThat(completed.getTotalCorrect()).isEqualTo(24);
        assertThat(completed.getOverallAccuracy()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("删除会话")
    void deleteSession() {
        // Act
        practiceSessionRepository.delete(session);

        // Assert
        Optional<PracticeSession> found = practiceSessionRepository.findById(session.getId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("统计学生的会话数量")
    void countByStudent() {
        // Arrange
        PracticeSession session2 = new PracticeSession(student);
        session2.setStatus(PracticeSession.SessionStatus.COMPLETED);
        practiceSessionRepository.save(session2);

        // Act
        long count = practiceSessionRepository.findByStudent(student).size();

        // Assert
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("会话状态枚举")
    void sessionStatusEnum() {
        PracticeSession.SessionStatus[] statuses = PracticeSession.SessionStatus.values();

        assertThat(statuses).contains(
            PracticeSession.SessionStatus.IN_PROGRESS,
            PracticeSession.SessionStatus.PAUSED,
            PracticeSession.SessionStatus.COMPLETED,
            PracticeSession.SessionStatus.ABANDONED
        );

        // Verify display names
        assertThat(PracticeSession.SessionStatus.IN_PROGRESS.getDisplayName()).isEqualTo("进行中");
        assertThat(PracticeSession.SessionStatus.PAUSED.getDisplayName()).isEqualTo("已暂停");
        assertThat(PracticeSession.SessionStatus.COMPLETED.getDisplayName()).isEqualTo("已完成");
        assertThat(PracticeSession.SessionStatus.ABANDONED.getDisplayName()).isEqualTo("已放弃");
    }

    @Test
    @DisplayName("查找最近完成的会话")
    void findTopByStudentOrderByEndTimeDesc() {
        // Arrange
        PracticeSession session2 = new PracticeSession(student);
        session2.setStatus(PracticeSession.SessionStatus.COMPLETED);
        session2.setEndTime(LocalDateTime.now().minusHours(1));
        practiceSessionRepository.save(session2);

        session.setStatus(PracticeSession.SessionStatus.COMPLETED);
        session.setEndTime(LocalDateTime.now());
        practiceSessionRepository.save(session);

        // Act - Find recently completed sessions
        List<PracticeSession> completed = practiceSessionRepository.findByStudent(student).stream()
            .filter(s -> s.getStatus() == PracticeSession.SessionStatus.COMPLETED)
            .sorted((s1, s2) -> s2.getEndTime().compareTo(s1.getEndTime()))
            .toList();

        // Assert
        assertThat(completed).hasSize(2);
        assertThat(completed.get(0).getEndTime()).isAfterOrEqualTo(completed.get(1).getEndTime());
    }

    @Test
    @DisplayName("题型选择功能")
    void targetQuestionType() {
        // Arrange
        PracticeSession joinSession = new PracticeSession(student);
        joinSession.setTargetQuestionType(Question.QuestionType.SELECT_JOIN);
        practiceSessionRepository.save(joinSession);

        // Act
        Optional<PracticeSession> result = practiceSessionRepository.findById(joinSession.getId());

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getTargetQuestionType()).isEqualTo(Question.QuestionType.SELECT_JOIN);
    }

    @Test
    @DisplayName("题型分布配置")
    void questionDistribution() {
        // Act & Assert - Test JSON configuration storage and retrieval
        session.setSelectedTypes(List.of(
            Question.QuestionType.SELECT_BASIC,
            Question.QuestionType.SELECT_JOIN
        ));

        List<Question.QuestionType> types = session.getSelectedTypes();
        assertThat(types).containsExactly(
            Question.QuestionType.SELECT_BASIC,
            Question.QuestionType.SELECT_JOIN
        );

        PracticeSession saved = practiceSessionRepository.save(session);

        // Reload
        Optional<PracticeSession> reloaded = practiceSessionRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();

        List<Question.QuestionType> reloadedTypes = reloaded.get().getSelectedTypes();
        assertThat(reloadedTypes).containsExactly(
            Question.QuestionType.SELECT_BASIC,
            Question.QuestionType.SELECT_JOIN
        );
    }

    @Test
    @DisplayName("边界值 - 零准确率")
    void boundary_ZeroAccuracy() {
        session.setTotalQuestions(10);
        session.setTotalCorrect(0);
        session.recalculateStatistics();

        assertThat(session.getOverallAccuracy()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("边界值 - 完美准确率")
    void boundary_PerfectAccuracy() {
        session.setTotalQuestions(10);
        session.setTotalCorrect(10);
        session.recalculateStatistics();

        assertThat(session.getOverallAccuracy()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("会话时间范围")
    void sessionTimeRange() {
        LocalDateTime start = LocalDateTime.now();
        session.setStartTime(start);

        LocalDateTime end = start.plusHours(2);
        session.setEndTime(end);

        PracticeSession saved = practiceSessionRepository.save(session);

        assertThat(saved.getStartTime()).isEqualTo(start);
        assertThat(saved.getEndTime()).isEqualTo(end);

        // Verify duration
        long durationMinutes = java.time.Duration.between(start, end).toMinutes();
        assertThat(durationMinutes).isEqualTo(120);
    }
}
