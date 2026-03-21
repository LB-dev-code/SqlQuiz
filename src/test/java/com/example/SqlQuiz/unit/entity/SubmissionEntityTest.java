package com.example.SqlQuiz.unit.entity;

import com.example.SqlQuiz.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Submission Entity Unit Tests")
public class SubmissionEntityTest {

    private Submission submission;
    private User student;
    private Quiz quiz;
    private Question question;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId(1L);
        student.setUsername("student");
        student.setRole(User.Role.STUDENT);

        quiz = new Quiz();
        quiz.setId(1L);
        quiz.setTitle("Test Quiz");
        quiz.setTeacher(new User());

        question = new Question();
        question.setId(1L);
        question.setContent("Test Question");
        question.setScore(10.0);
        question.setQuiz(quiz);

        submission = new Submission(student, quiz, 1);
    }

    @Test
    @DisplayName("创建提交记录 - 带参数构造函数")
    void createSubmissionWithConstructor() {
        // Assert
        assertThat(submission.getStudent()).isEqualTo(student);
        assertThat(submission.getQuiz()).isEqualTo(quiz);
        assertThat(submission.getAttemptNumber()).isEqualTo(1);
        assertThat(submission.getStatus()).isEqualTo(Submission.SubmissionStatus.IN_PROGRESS);
        assertThat(submission.getStartTime()).isNotNull();
    }

    @Test
    @DisplayName("提交测验")
    void submit() {
        // Arrange
        submission.setStartTime(LocalDateTime.now().minusMinutes(30));

        // Act
        submission.submit();

        // Assert
        assertThat(submission.getStatus()).isEqualTo(Submission.SubmissionStatus.SUBMITTED);
        assertThat(submission.getSubmitTime()).isNotNull();
        assertThat(submission.getTimeSpent()).isNotNull();
        assertThat(submission.getTimeSpent()).isLessThanOrEqualTo(30); // Allow some tolerance
    }

    @Test
    @DisplayName("检查是否进行中")
    void isInProgress() {
        // Assert
        assertThat(submission.isInProgress()).isTrue();

        submission.setStatus(Submission.SubmissionStatus.SUBMITTED);
        assertThat(submission.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("检查是否已完成")
    void isCompleted() {
        // Assert - Initial state is IN_PROGRESS
        assertThat(submission.isCompleted()).isFalse();

        // Test various completed statuses
        submission.setStatus(Submission.SubmissionStatus.SUBMITTED);
        assertThat(submission.isCompleted()).isTrue();

        submission.setStatus(Submission.SubmissionStatus.AUTO_SUBMITTED);
        assertThat(submission.isCompleted()).isTrue();

        submission.setStatus(Submission.SubmissionStatus.GRADED);
        assertThat(submission.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("计算总分 - 简单场景")
    void calculateScore_Simple() {
        // Arrange
        QuestionAnswer qa1 = createQuestionAnswer(10.0, 10.0);
        QuestionAnswer qa2 = createQuestionAnswer(8.0, 10.0);
        QuestionAnswer qa3 = createQuestionAnswer(5.0, 10.0);

        List<QuestionAnswer> answers = List.of(qa1, qa2, qa3);
        submission.setQuestionAnswers(answers);

        // Act
        submission.calculateScore();

        // Assert
        assertThat(submission.getTotalScore()).isEqualTo(23.0);
        assertThat(submission.getMaxScore()).isEqualTo(30.0);
        assertThat(submission.getPercentage()).isEqualTo(23.0 / 30.0 * 100, within(0.01));
    }

    @Test
    @DisplayName("计算总分 - 空答案列表")
    void calculateScore_EmptyList() {
        // Arrange
        submission.setQuestionAnswers(new ArrayList<>());

        // Act
        submission.calculateScore();

        // Assert
        assertThat(submission.getTotalScore()).isEqualTo(0.0);
        assertThat(submission.getMaxScore()).isEqualTo(0.0);
        assertThat(submission.getPercentage()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("获取等级 - 优秀")
    void getGrade_Excellent() {
        // Arrange
        submission.setPercentage(95.0);

        // Act
        String grade = submission.getGrade();

        // Assert
        assertThat(grade).isEqualTo("优秀");
    }

    @Test
    @DisplayName("获取等级 - 良好")
    void getGrade_Good() {
        // Arrange
        submission.setPercentage(85.0);

        // Act
        String grade = submission.getGrade();

        // Assert
        assertThat(grade).isEqualTo("良好");
    }

    @Test
    @DisplayName("获取等级 - 中等")
    void getGrade_Average() {
        // Arrange
        submission.setPercentage(75.0);

        // Act
        String grade = submission.getGrade();

        // Assert
        assertThat(grade).isEqualTo("中等");
    }

    @Test
    @DisplayName("获取等级 - 及格")
    void getGrade_Pass() {
        // Arrange
        submission.setPercentage(65.0);

        // Act
        String grade = submission.getGrade();

        // Assert
        assertThat(grade).isEqualTo("及格");
    }

    @Test
    @DisplayName("获取等级 - 不及格")
    void getGrade_Fail() {
        // Arrange
        submission.setPercentage(55.0);

        // Act
        String grade = submission.getGrade();

        // Assert
        assertThat(grade).isEqualTo("不及格");
    }

    @Test
    @DisplayName("获取等级 - 未评分")
    void getGrade_NotGraded() {
        // Arrange
        submission.setPercentage(null);

        // Act
        String grade = submission.getGrade();

        // Assert
        assertThat(grade).isEqualTo("未评分");
    }

    @Test
    @DisplayName("边界值测试 - 90分正好优秀")
    void getGrade_Boundary_Excellent() {
        submission.setPercentage(90.0);
        assertThat(submission.getGrade()).isEqualTo("优秀");
    }

    @Test
    @DisplayName("边界值测试 - 89.99分为良好")
    void getGrade_Boundary_Good() {
        submission.setPercentage(89.99);
        assertThat(submission.getGrade()).isEqualTo("良好");
    }

    @Test
    @DisplayName("边界值测试 - 60分正好及格")
    void getGrade_Boundary_Pass() {
        submission.setPercentage(60.0);
        assertThat(submission.getGrade()).isEqualTo("及格");
    }

    @Test
    @DisplayName("边界值测试 - 59.99分为不及格")
    void getGrade_Boundary_Fail() {
        submission.setPercentage(59.99);
        assertThat(submission.getGrade()).isEqualTo("不及格");
    }

    @Test
    @DisplayName("提交状态枚举显示名称")
    void submissionStatusDisplayNames() {
        assertThat(Submission.SubmissionStatus.IN_PROGRESS.getDisplayName()).isEqualTo("In Progress");
        assertThat(Submission.SubmissionStatus.SUBMITTED.getDisplayName()).isEqualTo("Submitted");
        assertThat(Submission.SubmissionStatus.AUTO_SUBMITTED.getDisplayName()).isEqualTo("Auto Submitted");
        assertThat(Submission.SubmissionStatus.GRADED.getDisplayName()).isEqualTo("Graded");
    }

    @Test
    @DisplayName("toString 方法验证")
    void toStringVerification() {
        // Arrange
        submission.setId(1L);
        submission.setStatus(Submission.SubmissionStatus.SUBMITTED);
        submission.setTotalScore(85.0);
        submission.setPercentage(85.0);

        // Act
        String result = submission.toString();

        // Assert
        assertThat(result).contains("1");
        assertThat(result).contains("1");
        assertThat(result).contains("SUBMITTED");
        assertThat(result).contains("85.0");
    }

    @Test
    @DisplayName("Getter 和 Setter 方法")
    void gettersAndSetters() {
        LocalDateTime now = LocalDateTime.now();

        // Act & Assert
        submission.setId(999L);
        assertThat(submission.getId()).isEqualTo(999L);

        submission.setAttemptNumber(2);
        assertThat(submission.getAttemptNumber()).isEqualTo(2);

        submission.setStartTime(now);
        assertThat(submission.getStartTime()).isEqualTo(now);

        submission.setSubmitTime(now.plusHours(1));
        assertThat(submission.getSubmitTime()).isEqualTo(now.plusHours(1));

        submission.setTimeSpent(60);
        assertThat(submission.getTimeSpent()).isEqualTo(60);

        submission.setTotalScore(100.0);
        assertThat(submission.getTotalScore()).isEqualTo(100.0);

        submission.setMaxScore(100.0);
        assertThat(submission.getMaxScore()).isEqualTo(100.0);

        submission.setPercentage(100.0);
        assertThat(submission.getPercentage()).isEqualTo(100.0);

        submission.setStatus(Submission.SubmissionStatus.GRADED);
        assertThat(submission.getStatus()).isEqualTo(Submission.SubmissionStatus.GRADED);

        submission.setFeedback("Good job!");
        assertThat(submission.getFeedback()).isEqualTo("Good job!");
    }

    /**
     * Helper method: Create QuestionAnswer
     */
    private QuestionAnswer createQuestionAnswer(double score, double maxScore) {
        QuestionAnswer qa = new QuestionAnswer();
        qa.setQuestion(question);
        qa.setScore(score);
        return qa;
    }
}
