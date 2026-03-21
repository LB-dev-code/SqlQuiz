package com.example.SqlQuiz.integration.repository;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.repository.QuizRepository;
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
@DisplayName("QuizRepository Integration Tests")
public class QuizRepositoryIntegrationTest {

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private UserRepository userRepository;

    private User testTeacher;
    private Quiz testQuiz;

    @BeforeEach
    void setUp() {
        // 清理数据
        quizRepository.deleteAll();
        userRepository.deleteAll();

        // 创建测试教师
        testTeacher = new User();
        testTeacher.setUsername("teacher");
        testTeacher.setPassword("password");
        testTeacher.setEmail("teacher@example.com");
        testTeacher.setFullName("Teacher");
        testTeacher.setRole(User.Role.TEACHER);
        testTeacher.setEnabled(true);
        testTeacher = userRepository.save(testTeacher);

        // 创建测试测验
        testQuiz = new Quiz();
        testQuiz.setTitle("Test Quiz");
        testQuiz.setDescription("Test Description");
        testQuiz.setTimeLimit(60);
        testQuiz.setMaxAttempts(3);
        testQuiz.setTeacher(testTeacher);
        testQuiz.setIsActive(true);
        testQuiz.setStartTime(LocalDateTime.now().minusHours(1));
        testQuiz.setEndTime(LocalDateTime.now().plusDays(1));
        testQuiz = quizRepository.save(testQuiz);
    }

    @Test
    @DisplayName("保存测验")
    void save_Quiz() {
        // Arrange
        Quiz newQuiz = new Quiz();
        newQuiz.setTitle("New Quiz");
        newQuiz.setDescription("New Description");
        newQuiz.setTimeLimit(90);
        newQuiz.setMaxAttempts(5);
        newQuiz.setTeacher(testTeacher);
        newQuiz.setIsActive(true);

        // Act
        Quiz saved = quizRepository.save(newQuiz);

        // Assert
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("New Quiz");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("根据ID查找测验")
    void findById() {
        // Act
        Optional<Quiz> found = quizRepository.findById(testQuiz.getId());

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Test Quiz");
    }

    @Test
    @DisplayName("查找所有活跃测验")
    void findByIsActiveTrueOrderByCreatedAtDesc() {
        // Arrange - Create inactive quiz
        Quiz inactiveQuiz = new Quiz();
        inactiveQuiz.setTitle("Inactive Quiz");
        inactiveQuiz.setDescription("Description");
        inactiveQuiz.setTimeLimit(60);
        inactiveQuiz.setMaxAttempts(3);
        inactiveQuiz.setTeacher(testTeacher);
        inactiveQuiz.setIsActive(false);
        quizRepository.save(inactiveQuiz);

        // Act
        List<Quiz> activeQuizzes = quizRepository.findByIsActiveTrueOrderByCreatedAtDesc();

        // Assert
        assertThat(activeQuizzes).isNotEmpty();
        assertThat(activeQuizzes).allMatch(Quiz::getIsActive);
        assertThat(activeQuizzes).doesNotContain(inactiveQuiz);
    }

    @Test
    @DisplayName("查找开放的测验")
    void findOpenQuizzes() {
        // Arrange - Create quizzes with different time statuses
        Quiz futureQuiz = new Quiz();
        futureQuiz.setTitle("Future Quiz");
        futureQuiz.setDescription("Description");
        futureQuiz.setTimeLimit(60);
        futureQuiz.setMaxAttempts(3);
        futureQuiz.setTeacher(testTeacher);
        futureQuiz.setIsActive(true);
        futureQuiz.setStartTime(LocalDateTime.now().plusDays(1));
        futureQuiz.setEndTime(LocalDateTime.now().plusDays(2));
        quizRepository.save(futureQuiz);

        // Act
        List<Quiz> openQuizzes = quizRepository.findOpenQuizzes(LocalDateTime.now());

        // Assert
        assertThat(openQuizzes).contains(testQuiz);
        assertThat(openQuizzes).doesNotContain(futureQuiz);
    }

    @Test
    @DisplayName("根据教师查找测验")
    void findByTeacherOrderByCreatedAtDesc() {
        // Arrange - Create quiz from other teacher
        User otherTeacher = new User();
        otherTeacher.setUsername("other_teacher");
        otherTeacher.setPassword("password");
        otherTeacher.setEmail("other@example.com");
        otherTeacher.setFullName("Other Teacher");
        otherTeacher.setRole(User.Role.TEACHER);
        otherTeacher.setEnabled(true);
        otherTeacher = userRepository.save(otherTeacher);

        Quiz otherQuiz = new Quiz();
        otherQuiz.setTitle("Other Quiz");
        otherQuiz.setDescription("Description");
        otherQuiz.setTimeLimit(60);
        otherQuiz.setMaxAttempts(3);
        otherQuiz.setTeacher(otherTeacher);
        otherQuiz.setIsActive(true);
        quizRepository.save(otherQuiz);

        // Act
        List<Quiz> teacherQuizzes = quizRepository.findByTeacherOrderByCreatedAtDesc(testTeacher);

        // Assert
        assertThat(teacherQuizzes).contains(testQuiz);
        assertThat(teacherQuizzes).doesNotContain(otherQuiz);
    }

    @Test
    @DisplayName("更新测验")
    void updateQuiz() {
        // Arrange
        testQuiz.setTitle("Updated Title");
        testQuiz.setMaxAttempts(5);

        // Act
        Quiz updated = quizRepository.save(testQuiz);

        // Assert
        assertThat(updated.getTitle()).isEqualTo("Updated Title");
        assertThat(updated.getMaxAttempts()).isEqualTo(5);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("删除测验")
    void deleteQuiz() {
        // Act
        quizRepository.delete(testQuiz);

        // Assert
        Optional<Quiz> found = quizRepository.findById(testQuiz.getId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("统计教师的测验数量")
    void countByTeacher() {
        // Arrange - Create more quizzes
        Quiz quiz2 = new Quiz();
        quiz2.setTitle("Quiz 2");
        quiz2.setDescription("Description");
        quiz2.setTimeLimit(60);
        quiz2.setMaxAttempts(3);
        quiz2.setTeacher(testTeacher);
        quiz2.setIsActive(true);
        quizRepository.save(quiz2);

        // Act
        long count = quizRepository.findAll().stream()
            .filter(q -> q.getTeacher().getId().equals(testTeacher.getId()))
            .count();

        // Assert
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("测验时间边界测试")
    void quizTimeBoundaries() {
        // Arrange - Create boundary case quizzes
        LocalDateTime now = LocalDateTime.now();

        // Quiz that just started
        Quiz justStarted = new Quiz();
        justStarted.setTitle("Just Started");
        justStarted.setDescription("Description");
        justStarted.setTimeLimit(60);
        justStarted.setMaxAttempts(3);
        justStarted.setTeacher(testTeacher);
        justStarted.setIsActive(true);
        justStarted.setStartTime(now.minusSeconds(1));
        justStarted.setEndTime(now.plusHours(1));
        quizRepository.save(justStarted);

        // Quiz that just ended
        Quiz justEnded = new Quiz();
        justEnded.setTitle("Just Ended");
        justEnded.setDescription("Description");
        justEnded.setTimeLimit(60);
        justEnded.setMaxAttempts(3);
        justEnded.setTeacher(testTeacher);
        justEnded.setIsActive(true);
        justEnded.setStartTime(now.minusHours(2));
        justEnded.setEndTime(now.minusSeconds(1));
        quizRepository.save(justEnded);

        // Act
        List<Quiz> openQuizzes = quizRepository.findOpenQuizzes(now);

        // Assert
        assertThat(openQuizzes).contains(justStarted);
        assertThat(openQuizzes).doesNotContain(justEnded);
    }
}
