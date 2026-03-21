package com.example.SqlQuiz.unit.entity;

import com.example.SqlQuiz.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("QuestionAnswer Entity Unit Tests")
public class QuestionAnswerEntityTest {

    private QuestionAnswer questionAnswer;
    private Question question;
    private Submission submission;

    @BeforeEach
    void setUp() {
        User student = new User();
        student.setId(1L);
        student.setRole(User.Role.STUDENT);

        User teacher = new User();
        teacher.setId(2L);
        teacher.setRole(User.Role.TEACHER);

        Quiz quiz = new Quiz();
        quiz.setId(1L);
        quiz.setTitle("Test Quiz");
        quiz.setTeacher(teacher);

        submission = new Submission(student, quiz, 1);
        submission.setId(1L);
        submission.setStatus(Submission.SubmissionStatus.IN_PROGRESS);

        question = new Question();
        question.setId(1L);
        question.setContent("Test Question");
        question.setScore(10.0);
        question.setQuiz(quiz);

        questionAnswer = new QuestionAnswer(question, submission);
        questionAnswer.setId(1L);
    }

    @Test
    @DisplayName("创建答题记录 - 带参数构造函数")
    void createWithConstructor() {
        // Assert
        assertThat(questionAnswer.getQuestion()).isEqualTo(question);
        assertThat(questionAnswer.getSubmission()).isEqualTo(submission);
    }

    @Test
    @DisplayName("设置分数")
    void setScore() {
        // Act
        questionAnswer.setScore(10.0);

        // Assert
        assertThat(questionAnswer.getScore()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("设置正确性")
    void setIsCorrect() {
        // Act
        questionAnswer.setIsCorrect(true);

        // Assert
        assertThat(questionAnswer.getIsCorrect()).isTrue();
    }

    @Test
    @DisplayName("设置自动反馈")
    void setAutoFeedback() {
        // Act
        questionAnswer.setAutoFeedback("Good job! Your answer is correct.");

        // Assert
        assertThat(questionAnswer.getAutoFeedback()).isEqualTo("Good job! Your answer is correct.");
    }

    @Test
    @DisplayName("满分与正确性关联")
    void fullScoreImpliesCorrect() {
        // Arrange
        question.setScore(10.0);

        // Act
        questionAnswer.setScore(10.0);
        questionAnswer.setIsCorrect(questionAnswer.getScore() == question.getScore());

        // Assert
        assertThat(questionAnswer.getIsCorrect()).isTrue();
    }

    @Test
    @DisplayName("部分分数表示不正确")
    void partialScoreImpliesIncorrect() {
        // Arrange
        question.setScore(10.0);

        // Act
        questionAnswer.setScore(5.0);
        questionAnswer.setIsCorrect(questionAnswer.getScore() == question.getScore());

        // Assert
        assertThat(questionAnswer.getIsCorrect()).isFalse();
    }

    @Test
    @DisplayName("零分表示不正确")
    void zeroScoreImpliesIncorrect() {
        // Arrange
        question.setScore(10.0);

        // Act
        questionAnswer.setScore(0.0);
        questionAnswer.setIsCorrect(questionAnswer.getScore() == question.getScore());

        // Assert
        assertThat(questionAnswer.getIsCorrect()).isFalse();
    }

    @Test
    @DisplayName("Getter 和 Setter 方法")
    void gettersAndSetters() {
        LocalDateTime now = LocalDateTime.now();

        // Act & Assert
        questionAnswer.setId(999L);
        assertThat(questionAnswer.getId()).isEqualTo(999L);

        questionAnswer.setStudentSql("SELECT * FROM test");
        assertThat(questionAnswer.getStudentSql()).isEqualTo("SELECT * FROM test");

        questionAnswer.setScore(8.5);
        assertThat(questionAnswer.getScore()).isEqualTo(8.5);

        questionAnswer.setIsCorrect(false);
        assertThat(questionAnswer.getIsCorrect()).isFalse();

        questionAnswer.setAutoFeedback("Try again");
        assertThat(questionAnswer.getAutoFeedback()).isEqualTo("Try again");
    }

    @Test
    @DisplayName("关联题目和提交")
    void questionAndSubmissionAssociation() {
        // Assert
        assertThat(questionAnswer.getQuestion()).isEqualTo(question);
        assertThat(questionAnswer.getSubmission()).isEqualTo(submission);

        // Verify associated objects
        assertThat(questionAnswer.getQuestion().getId()).isEqualTo(1L);
        assertThat(questionAnswer.getSubmission().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("默认构造函数")
    void defaultConstructor() {
        // Act
        QuestionAnswer empty = new QuestionAnswer();

        // Assert
        assertThat(empty).isNotNull();
        assertThat(empty.getId()).isNull();
        assertThat(empty.getStudentSql()).isNull();
        assertThat(empty.getScore()).isNull();
        assertThat(empty.getIsCorrect()).isNull();
    }

    @Test
    @DisplayName("边界值 - 分数为0")
    void boundary_ZeroScore() {
        questionAnswer.setScore(0.0);
        assertThat(questionAnswer.getScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("边界值 - 满分")
    void boundary_FullScore() {
        double fullScore = question.getScore();
        questionAnswer.setScore(fullScore);
        assertThat(questionAnswer.getScore()).isEqualTo(fullScore);
    }

    @Test
    @DisplayName("小数分数")
    void decimalScore() {
        questionAnswer.setScore(7.5);
        assertThat(questionAnswer.getScore()).isEqualTo(7.5);
    }

    @Test
    @DisplayName("SQL 注入测试字符串")
    void sqlInjectionStrings() {
        String maliciousSql = "SELECT * FROM users; DROP TABLE users; --";

        questionAnswer.submitAnswer(maliciousSql);
        assertThat(questionAnswer.getStudentSql()).isEqualTo(maliciousSql);
    }

    @Test
    @DisplayName("空 SQL 提交")
    void emptySqlSubmission() {
        questionAnswer.submitAnswer("");
        assertThat(questionAnswer.getStudentSql()).isEmpty();
    }

    @Test
    @DisplayName("Null SQL 提交")
    void nullSqlSubmission() {
        questionAnswer.submitAnswer(null);
        assertThat(questionAnswer.getStudentSql()).isNull();
    }
}
