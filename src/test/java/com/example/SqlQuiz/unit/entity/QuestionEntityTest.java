package com.example.SqlQuiz.unit.entity;

import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.entity.Quiz;
import com.example.SqlQuiz.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Question Entity Unit Tests")
public class QuestionEntityTest {

    private Question question;
    private Quiz quiz;

    @BeforeEach
    void setUp() {
        User teacher = new User();
        teacher.setId(1L);
        teacher.setRole(User.Role.TEACHER);

        quiz = new Quiz();
        quiz.setId(1L);
        quiz.setTitle("Test Quiz");
        quiz.setTeacher(teacher);

        question = new Question();
        question.setId(1L);
        question.setContent("Test Question Content");
        question.setQuestionType(Question.QuestionType.SELECT_BASIC);
        question.setScore(10.0);
        question.setDifficultyLevel(Question.DifficultyLevel.EASY);
        question.setQuiz(quiz);
    }

    @Test
    @DisplayName("创建题目 - 带参数构造函数")
    void createQuestionWithConstructor() {
        // Act
        Question newQuestion = new Question("New Question",
            Question.QuestionType.SELECT_JOIN, 15.0, quiz);

        // Assert
        assertThat(newQuestion.getContent()).isEqualTo("New Question");
        assertThat(newQuestion.getQuestionType()).isEqualTo(Question.QuestionType.SELECT_JOIN);
        assertThat(newQuestion.getScore()).isEqualTo(15.0);
        assertThat(newQuestion.getQuiz()).isEqualTo(quiz);
    }

    @Test
    @DisplayName("题目类型枚举 - 显示名称")
    void questionTypeDisplayNames() {
        // Assert
        assertThat(Question.QuestionType.SELECT_BASIC.getDisplayName()).isEqualTo("基础查询");
        assertThat(Question.QuestionType.SELECT_BASIC.getEnglishName()).isEqualTo("Basic SELECT");

        assertThat(Question.QuestionType.SELECT_JOIN.getDisplayName()).isEqualTo("表连接");
        assertThat(Question.QuestionType.SELECT_JOIN.getEnglishName()).isEqualTo("JOIN Queries");

        assertThat(Question.QuestionType.SELECT_SUBQUERY.getDisplayName()).isEqualTo("子查询");

        assertThat(Question.QuestionType.SELECT_AGGREGATE.getDisplayName()).isEqualTo("聚合函数");

        assertThat(Question.QuestionType.SELECT_COMPLEX.getDisplayName()).isEqualTo("复杂查询");

        assertThat(Question.QuestionType.DML_INSERT.getDisplayName()).isEqualTo("插入数据");
        assertThat(Question.QuestionType.DML_UPDATE.getDisplayName()).isEqualTo("更新数据");
        assertThat(Question.QuestionType.DML_DELETE.getDisplayName()).isEqualTo("删除数据");

        assertThat(Question.QuestionType.DDL_CREATE.getDisplayName()).isEqualTo("创建表");
        assertThat(Question.QuestionType.DDL_ALTER.getDisplayName()).isEqualTo("修改表结构");
    }

    @Test
    @DisplayName("难度级别枚举 - 显示名称")
    void difficultyLevelDisplayNames() {
        // Assert
        assertThat(Question.DifficultyLevel.EASY.getDisplayName()).isEqualTo("简单");
        assertThat(Question.DifficultyLevel.EASY.getEnglishName()).isEqualTo("Easy");

        assertThat(Question.DifficultyLevel.MEDIUM.getDisplayName()).isEqualTo("中等");
        assertThat(Question.DifficultyLevel.MEDIUM.getEnglishName()).isEqualTo("Medium");

        assertThat(Question.DifficultyLevel.HARD.getDisplayName()).isEqualTo("困难");
        assertThat(Question.DifficultyLevel.HARD.getEnglishName()).isEqualTo("Hard");
    }

    @Test
    @DisplayName("toString 方法验证")
    void toStringVerification() {
        // Act
        String result = question.toString();

        // Assert
        assertThat(result).contains("1");
        assertThat(result).contains("SELECT_BASIC");
        assertThat(result).contains("10.0");
        assertThat(result).contains("EASY");
    }

    @Test
    @DisplayName("Getter 和 Setter 方法")
    void gettersAndSetters() {
        // Act & Assert
        question.setId(999L);
        assertThat(question.getId()).isEqualTo(999L);

        question.setContent("Updated Content");
        assertThat(question.getContent()).isEqualTo("Updated Content");

        question.setQuestionType(Question.QuestionType.SELECT_JOIN);
        assertThat(question.getQuestionType()).isEqualTo(Question.QuestionType.SELECT_JOIN);

        question.setDescription("Test Description");
        assertThat(question.getDescription()).isEqualTo("Test Description");

        question.setDatabaseContext("DB Context");
        assertThat(question.getDatabaseContext()).isEqualTo("DB Context");

        question.setExpectedSql("SELECT * FROM test");
        assertThat(question.getExpectedSql()).isEqualTo("SELECT * FROM test");

        question.setSetupSql("CREATE TABLE test (id INT)");
        assertThat(question.getSetupSql()).isEqualTo("CREATE TABLE test (id INT)");

        question.setTestData("{\"data\": []}");
        assertThat(question.getTestData()).isEqualTo("{\"data\": []}");

        question.setExpectedResult("{\"result\": []}");
        assertThat(question.getExpectedResult()).isEqualTo("{\"result\": []}");

        question.setScore(20.0);
        assertThat(question.getScore()).isEqualTo(20.0);

        question.setDifficultyLevel(Question.DifficultyLevel.HARD);
        assertThat(question.getDifficultyLevel()).isEqualTo(Question.DifficultyLevel.HARD);

        question.setOrderIndex(5);
        assertThat(question.getOrderIndex()).isEqualTo(5);
    }

    @Test
    @DisplayName("设置和获取关联的测验")
    void quizAssociation() {
        // Arrange
        Quiz newQuiz = new Quiz();
        newQuiz.setId(2L);

        // Act
        question.setQuiz(newQuiz);

        // Assert
        assertThat(question.getQuiz()).isEqualTo(newQuiz);
        assertThat(question.getQuiz().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("默认构造函数")
    void defaultConstructor() {
        // Act
        Question emptyQuestion = new Question();

        // Assert
        assertThat(emptyQuestion).isNotNull();
        assertThat(emptyQuestion.getId()).isNull();
        assertThat(emptyQuestion.getContent()).isNull();
        assertThat(emptyQuestion.getQuestionType()).isNull();
    }

    @Test
    @DisplayName("分数边界值测试")
    void scoreBoundaryValues() {
        // Minimum score
        question.setScore(0.0);
        assertThat(question.getScore()).isEqualTo(0.0);

        // Decimal score
        question.setScore(7.5);
        assertThat(question.getScore()).isEqualTo(7.5);

        // Large score
        question.setScore(100.0);
        assertThat(question.getScore()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("所有题目类型枚举值")
    void allQuestionTypeValues() {
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
    @DisplayName("所有难度级别枚举值")
    void allDifficultyLevelValues() {
        Question.DifficultyLevel[] levels = Question.DifficultyLevel.values();

        assertThat(levels).hasSize(3);
        assertThat(levels).contains(
            Question.DifficultyLevel.EASY,
            Question.DifficultyLevel.MEDIUM,
            Question.DifficultyLevel.HARD
        );
    }
}
