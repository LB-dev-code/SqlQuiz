package com.example.SqlQuiz.unit.entity;

import com.example.SqlQuiz.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PracticeAnswer Entity Unit Tests")
public class PracticeAnswerEntityTest {

    private PracticeAnswer answer;
    private PracticeRound round;
    private Question question;

    @BeforeEach
    void setUp() {
        User student = new User();
        student.setId(1L);
        student.setRole(User.Role.STUDENT);

        PracticeSession session = new PracticeSession(student);
        session.setId(1L);

        round = new PracticeRound();
        round.setId(1L);
        round.setSession(session);
        round.setStudent(student);
        round.setRoundNumber(1);
        round.setStatus(PracticeRound.RoundStatus.IN_PROGRESS);

        User teacher = new User();
        teacher.setId(2L);
        teacher.setRole(User.Role.TEACHER);

        Quiz quiz = new Quiz();
        quiz.setId(1L);
        quiz.setTeacher(teacher);

        question = new Question();
        question.setId(1L);
        question.setContent("Test Question");
        question.setQuestionType(Question.QuestionType.SELECT_BASIC);
        question.setScore(10.0);
        question.setQuiz(quiz);

        answer = new PracticeAnswer();
        answer.setId(1L);
        answer.setRound(round);
        answer.setQuestionIndex(0);
    }

    @Test
    @DisplayName("默认构造函数")
    void defaultConstructor() {
        PracticeAnswer newAnswer = new PracticeAnswer();

        assertThat(newAnswer).isNotNull();
        assertThat(newAnswer.getId()).isNull();
        assertThat(newAnswer.getStudentSql()).isNull();
        assertThat(newAnswer.getIsCorrect()).isNull();
        assertThat(newAnswer.getScore()).isEqualTo(0.0);
        assertThat(newAnswer.isAnswered()).isFalse();
    }

    @Test
    @DisplayName("带参数构造函数")
    void constructorWithParameters() {
        PracticeAnswer newAnswer = new PracticeAnswer(round, 5);

        assertThat(newAnswer.getRound()).isEqualTo(round);
        assertThat(newAnswer.getQuestionIndex()).isEqualTo(5);
    }

    @Test
    @DisplayName("设置题目信息")
    void setQuestionInfo() {
        // Act
        answer.setQuestionInfo(
            "Test Title",
            "Test Content",
            "Test DB Context",
            "SELECT * FROM test",
            Question.QuestionType.SELECT_JOIN,
            Question.DifficultyLevel.MEDIUM
        );

        // Assert
        assertThat(answer.getQuestionTitle()).isEqualTo("Test Title");
        assertThat(answer.getQuestionContent()).isEqualTo("Test Content");
        assertThat(answer.getDatabaseContext()).isEqualTo("Test DB Context");
        assertThat(answer.getExpectedSql()).isEqualTo("SELECT * FROM test");
        assertThat(answer.getQuestionType()).isEqualTo(Question.QuestionType.SELECT_JOIN);
        assertThat(answer.getDifficultyLevel()).isEqualTo(Question.DifficultyLevel.MEDIUM);
    }

    @Test
    @DisplayName("设置题目信息（包含建表SQL）")
    void setQuestionInfoWithSetup() {
        // Act
        answer.setQuestionInfoWithSetup(
            "Test Title",
            "Test Content",
            "Test DB Context",
            "SELECT * FROM test",
            "CREATE TABLE test (id INT)",
            "quiz_q_1_123456",
            Question.QuestionType.SELECT_JOIN,
            Question.DifficultyLevel.MEDIUM
        );

        // Assert
        assertThat(answer.getQuestionTitle()).isEqualTo("Test Title");
        assertThat(answer.getSetupSql()).isEqualTo("CREATE TABLE test (id INT)");
        assertThat(answer.getTablePrefix()).isEqualTo("quiz_q_1_123456");
        assertThat(answer.getQuestionType()).isEqualTo(Question.QuestionType.SELECT_JOIN);
    }

    @Test
    @DisplayName("提交答案")
    void submitAnswer() {
        // Act
        answer.submitAnswer(
            "SELECT * FROM users",
            "[{\"id\":1,\"name\":\"Test\"}]",
            null,
            true,
            10.0,
            "Perfect answer!"
        );

        // Assert
        assertThat(answer.getStudentSql()).isEqualTo("SELECT * FROM users");
        assertThat(answer.getExecutionResult()).isEqualTo("[{\"id\":1,\"name\":\"Test\"}]");
        assertThat(answer.getExecutionError()).isNull();
        assertThat(answer.getIsCorrect()).isTrue();
        assertThat(answer.getScore()).isEqualTo(10.0);
        assertThat(answer.getAiFeedback()).isEqualTo("Perfect answer!");
        assertThat(answer.getAnswerTime()).isNotNull();
        assertThat(answer.isAnswered()).isTrue();
    }

    @Test
    @DisplayName("提交错误答案")
    void submitAnswer_WithError() {
        // Act
        answer.submitAnswer(
            "WRONG SQL",
            null,
            "Table 'users' doesn't exist",
            false,
            0.0,
            "Try again"
        );

        // Assert
        assertThat(answer.getStudentSql()).isEqualTo("WRONG SQL");
        assertThat(answer.getExecutionResult()).isNull();
        assertThat(answer.getExecutionError()).isEqualTo("Table 'users' doesn't exist");
        assertThat(answer.getIsCorrect()).isFalse();
        assertThat(answer.getScore()).isEqualTo(0.0);
        assertThat(answer.getAiFeedback()).isEqualTo("Try again");
    }

    @Test
    @DisplayName("检查是否有答案")
    void hasAnswer() {
        // Assert - 初始状态
        assertThat(answer.hasAnswer()).isFalse();

        // Act
        answer.setStudentSql("SELECT * FROM users");

        // Assert
        assertThat(answer.hasAnswer()).isTrue();
    }

    @Test
    @DisplayName("检查是否有答案 - 仅空格")
    void hasAnswer_OnlyWhitespace() {
        answer.setStudentSql("   ");

        assertThat(answer.hasAnswer()).isFalse();
    }

    @Test
    @DisplayName("检查是否执行成功")
    void isExecutedSuccessfully() {
        // Assert - 初始状态（无错误）
        assertThat(answer.isExecutedSuccessfully()).isTrue();

        // Act - 设置错误
        answer.setExecutionError("SQL Error");

        // Assert
        assertThat(answer.isExecutedSuccessfully()).isFalse();
    }

    @Test
    @DisplayName("检查是否已回答")
    void isAnswered() {
        // Assert - 初始状态
        assertThat(answer.isAnswered()).isFalse();

        // Act
        answer.setAnswered(true);

        // Assert
        assertThat(answer.isAnswered()).isTrue();
    }

    @Test
    @DisplayName("Getter 和 Setter 方法")
    void gettersAndSetters() {
        LocalDateTime now = LocalDateTime.now();

        // Act & Assert
        answer.setId(999L);
        assertThat(answer.getId()).isEqualTo(999L);

        answer.setQuestionIndex(5);
        assertThat(answer.getQuestionIndex()).isEqualTo(5);

        answer.setQuestionType(Question.QuestionType.SELECT_AGGREGATE);
        assertThat(answer.getQuestionType()).isEqualTo(Question.QuestionType.SELECT_AGGREGATE);

        answer.setDifficultyLevel(Question.DifficultyLevel.HARD);
        assertThat(answer.getDifficultyLevel()).isEqualTo(Question.DifficultyLevel.HARD);

        answer.setQuestionTitle("New Title");
        assertThat(answer.getQuestionTitle()).isEqualTo("New Title");

        answer.setQuestionContent("New Content");
        assertThat(answer.getQuestionContent()).isEqualTo("New Content");

        answer.setDatabaseContext("New Context");
        assertThat(answer.getDatabaseContext()).isEqualTo("New Context");

        answer.setExpectedSql("SELECT 1");
        assertThat(answer.getExpectedSql()).isEqualTo("SELECT 1");

        answer.setSetupSql("CREATE TABLE test (id INT)");
        assertThat(answer.getSetupSql()).isEqualTo("CREATE TABLE test (id INT)");

        answer.setTablePrefix("quiz_q_1_123");
        assertThat(answer.getTablePrefix()).isEqualTo("quiz_q_1_123");

        answer.setStudentSql("SELECT * FROM test");
        assertThat(answer.getStudentSql()).isEqualTo("SELECT * FROM test");

        answer.setExecutionResult("result");
        assertThat(answer.getExecutionResult()).isEqualTo("result");

        answer.setExecutionError("error");
        assertThat(answer.getExecutionError()).isEqualTo("error");

        answer.setIsCorrect(true);
        assertThat(answer.getIsCorrect()).isTrue();

        answer.setScore(8.5);
        assertThat(answer.getScore()).isEqualTo(8.5);

        answer.setAiFeedback("feedback");
        assertThat(answer.getAiFeedback()).isEqualTo("feedback");

        answer.setAnswerTime(now);
        assertThat(answer.getAnswerTime()).isEqualTo(now);

        answer.setAnswered(true);
        assertThat(answer.isAnswered()).isTrue();
        assertThat(answer.getAnswered()).isTrue();

        answer.setTimeSpentSeconds(120);
        assertThat(answer.getTimeSpentSeconds()).isEqualTo(120);
    }

    @Test
    @DisplayName("关联轮次")
    void roundAssociation() {
        PracticeRound newRound = new PracticeRound();
        newRound.setId(2L);

        answer.setRound(newRound);

        assertThat(answer.getRound()).isEqualTo(newRound);
        assertThat(answer.getRound().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("边界值 - 题目索引")
    void boundary_QuestionIndex() {
        answer.setQuestionIndex(0);
        assertThat(answer.getQuestionIndex()).isEqualTo(0);

        answer.setQuestionIndex(9);
        assertThat(answer.getQuestionIndex()).isEqualTo(9);
    }

    @Test
    @DisplayName("边界值 - 答题耗时")
    void boundary_TimeSpent() {
        answer.setTimeSpentSeconds(0);
        assertThat(answer.getTimeSpentSeconds()).isEqualTo(0);

        answer.setTimeSpentSeconds(3600); // 1 hour
        assertThat(answer.getTimeSpentSeconds()).isEqualTo(3600);
    }

    @Test
    @DisplayName("边界值 - 分数")
    void boundary_Score() {
        answer.setScore(0.0);
        assertThat(answer.getScore()).isEqualTo(0.0);

        answer.setScore(10.0);
        assertThat(answer.getScore()).isEqualTo(10.0);

        answer.setScore(5.5);
        assertThat(answer.getScore()).isEqualTo(5.5);
    }

    @Test
    @DisplayName("全部题型枚举值")
    void allQuestionTypes() {
        Question.QuestionType[] types = Question.QuestionType.values();

        assertThat(types).hasSize(10);
        assertThat(types).contains(
            Question.QuestionType.SELECT_BASIC,
            Question.QuestionType.SELECT_JOIN,
            Question.QuestionType.SELECT_SUBQUERY,
            Question.QuestionType.SELECT_AGGREGATE,
            Question.QuestionType.SELECT_COMPLEX,
            Question.QuestionType.DML_INSERT,
            Question.QuestionType.DML_UPDATE,
            Question.QuestionType.DML_DELETE,
            Question.QuestionType.DDL_CREATE,
            Question.QuestionType.DDL_ALTER
        );
    }

    @Test
    @DisplayName("全部难度级别枚举值")
    void allDifficultyLevels() {
        Question.DifficultyLevel[] levels = Question.DifficultyLevel.values();

        assertThat(levels).hasSize(3);
        assertThat(levels).contains(
            Question.DifficultyLevel.EASY,
            Question.DifficultyLevel.MEDIUM,
            Question.DifficultyLevel.HARD
        );
    }
}
