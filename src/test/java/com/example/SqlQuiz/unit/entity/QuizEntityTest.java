package com.example.SqlQuiz.unit.entity;

import com.example.SqlQuiz.entity.Quiz;
import com.example.SqlQuiz.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Quiz Entity Unit Tests")
public class QuizEntityTest {

    private Quiz quiz;
    private User teacher;

    @BeforeEach
    void setUp() {
        teacher = new User();
        teacher.setId(1L);
        teacher.setUsername("teacher");
        teacher.setRole(User.Role.TEACHER);

        quiz = new Quiz();
        quiz.setId(1L);
        quiz.setTitle("Test Quiz");
        quiz.setDescription("Test Description");
        quiz.setTimeLimit(60);
        quiz.setMaxAttempts(3);
        quiz.setTeacher(teacher);
        quiz.setIsActive(true);
    }

    @Test
    @DisplayName("创建测验 - 带参数构造函数")
    void createQuizWithConstructor() {
        // Act
        Quiz newQuiz = new Quiz("New Quiz", "New Description", 90, 5, teacher);

        // Assert
        assertThat(newQuiz.getTitle()).isEqualTo("New Quiz");
        assertThat(newQuiz.getDescription()).isEqualTo("New Description");
        assertThat(newQuiz.getTimeLimit()).isEqualTo(90);
        assertThat(newQuiz.getMaxAttempts()).isEqualTo(5);
        assertThat(newQuiz.getTeacher()).isEqualTo(teacher);
    }

    @Test
    @DisplayName("检查测验是否开放 - 时间范围内")
    void isOpen_WithinTimeRange() {
        // Arrange
        quiz.setStartTime(LocalDateTime.now().minusHours(1));
        quiz.setEndTime(LocalDateTime.now().plusHours(1));

        // Act & Assert
        assertThat(quiz.isOpen()).isTrue();
    }

    @Test
    @DisplayName("检查测验是否开放 - 未开始")
    void isOpen_NotStarted() {
        // Arrange
        quiz.setStartTime(LocalDateTime.now().plusHours(1));
        quiz.setEndTime(LocalDateTime.now().plusHours(2));

        // Act & Assert
        assertThat(quiz.isOpen()).isFalse();
    }

    @Test
    @DisplayName("检查测验是否开放 - 已结束")
    void isOpen_Ended() {
        // Arrange
        quiz.setStartTime(LocalDateTime.now().minusHours(2));
        quiz.setEndTime(LocalDateTime.now().minusHours(1));

        // Act & Assert
        assertThat(quiz.isOpen()).isFalse();
    }

    @Test
    @DisplayName("检查测验是否开放 - 无时间限制")
    void isOpen_NoTimeConstraints() {
        // Arrange
        quiz.setStartTime(null);
        quiz.setEndTime(null);

        // Act & Assert
        assertThat(quiz.isOpen()).isTrue();
    }

    @Test
    @DisplayName("检查是否有时间限制")
    void hasTimeLimit_Positive() {
        // Arrange
        quiz.setTimeLimit(60);

        // Act & Assert
        assertThat(quiz.hasTimeLimit()).isTrue();
    }

    @Test
    @DisplayName("检查是否有时间限制 - 无限制")
    void hasTimeLimit_NoLimit() {
        // Arrange
        quiz.setTimeLimit(0);

        // Act & Assert
        assertThat(quiz.hasTimeLimit()).isFalse();
    }

    @Test
    @DisplayName("检查是否有时间限制 - null")
    void hasTimeLimit_Null() {
        // Arrange
        quiz.setTimeLimit(null);

        // Act & Assert
        assertThat(quiz.hasTimeLimit()).isFalse();
    }

    @Test
    @DisplayName("toString 方法验证")
    void toStringVerification() {
        // Act
        String result = quiz.toString();

        // Assert
        assertThat(result).contains("Test Quiz");
        assertThat(result).contains("60");
        assertThat(result).contains("3");
        assertThat(result).contains("true");
    }

    @Test
    @DisplayName("Getter 和 Setter 方法")
    void gettersAndSetters() {
        LocalDateTime now = LocalDateTime.now();

        // Act & Assert
        quiz.setId(999L);
        assertThat(quiz.getId()).isEqualTo(999L);

        quiz.setTitle("Updated Title");
        assertThat(quiz.getTitle()).isEqualTo("Updated Title");

        quiz.setDescription("Updated Description");
        assertThat(quiz.getDescription()).isEqualTo("Updated Description");

        quiz.setTimeLimit(120);
        assertThat(quiz.getTimeLimit()).isEqualTo(120);

        quiz.setMaxAttempts(5);
        assertThat(quiz.getMaxAttempts()).isEqualTo(5);

        quiz.setIsActive(false);
        assertThat(quiz.getIsActive()).isFalse();

        quiz.setStartTime(now);
        assertThat(quiz.getStartTime()).isEqualTo(now);

        quiz.setEndTime(now.plusDays(1));
        assertThat(quiz.getEndTime()).isEqualTo(now.plusDays(1));

        quiz.setCreatedAt(now);
        assertThat(quiz.getCreatedAt()).isEqualTo(now);

        quiz.setUpdatedAt(now);
        assertThat(quiz.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("边界值测试 - 开始时间等于当前时间")
    void isOpen_StartTimeEqualsNow() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        quiz.setStartTime(now);
        quiz.setEndTime(now.plusHours(1));

        // Act & Assert - isOpen 使用 !isBefore 和 !isAfter，包含边界
        assertThat(quiz.isOpen()).isTrue();
    }

    @Test
    @DisplayName("边界值测试 - 结束时间等于当前时间")
    void isOpen_EndTimeEqualsNow() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        quiz.setStartTime(now.minusHours(1));
        quiz.setEndTime(now);

        // Act & Assert - isOpen 使用 !isAfter，包含边界
        assertThat(quiz.isOpen()).isTrue();
    }

    @Test
    @DisplayName("活跃状态切换")
    void toggleActiveStatus() {
        // Arrange
        boolean originalStatus = quiz.getIsActive();

        // Act
        quiz.setIsActive(!originalStatus);

        // Assert
        assertThat(quiz.getIsActive()).isNotEqualTo(originalStatus);
    }
}
