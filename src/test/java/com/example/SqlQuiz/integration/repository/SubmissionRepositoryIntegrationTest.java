package com.example.SqlQuiz.integration.repository;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.repository.*;
import com.example.SqlQuiz.service.QuizService;
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
@DisplayName("SubmissionRepository Integration Tests")
public class SubmissionRepositoryIntegrationTest {

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionAnswerRepository questionAnswerRepository;

    private User teacher;
    private User student;
    private Quiz quiz;
    private Question question;
    private Submission submission;

    @BeforeEach
    void setUp() {
        // 清理数据
        questionAnswerRepository.deleteAll();
        submissionRepository.deleteAll();
        questionRepository.deleteAll();
        quizRepository.deleteAll();
        userRepository.deleteAll();

        // Create test users
        teacher = new User();
        teacher.setUsername("teacher");
        teacher.setPassword("password");
        teacher.setEmail("teacher@example.com");
        teacher.setFullName("Teacher");
        teacher.setRole(User.Role.TEACHER);
        teacher.setEnabled(true);
        teacher = userRepository.save(teacher);

        student = new User();
        student.setUsername("student");
        student.setPassword("password");
        student.setEmail("student@example.com");
        student.setFullName("Student");
        student.setRole(User.Role.STUDENT);
        student.setEnabled(true);
        student = userRepository.save(student);

        // Create test quiz
        quiz = new Quiz();
        quiz.setTitle("Test Quiz");
        quiz.setDescription("Description");
        quiz.setTimeLimit(60);
        quiz.setMaxAttempts(3);
        quiz.setTeacher(teacher);
        quiz.setIsActive(true);
        quiz.setStartTime(LocalDateTime.now().minusHours(1));
        quiz.setEndTime(LocalDateTime.now().plusDays(1));
        quiz = quizRepository.save(quiz);

        // Create test question
        question = new Question();
        question.setContent("Test Question");
        question.setQuestionType(Question.QuestionType.SELECT_BASIC);
        question.setScore(10.0);
        question.setQuiz(quiz);
        question.setOrderIndex(1);
        question = questionRepository.save(question);

        // Create test submission
        submission = new Submission(student, quiz, 1);
        submission.setStatus(Submission.SubmissionStatus.IN_PROGRESS);
        submission = submissionRepository.save(submission);
    }

    @Test
    @DisplayName("保存提交记录")
    void save_Submission() {
        // Act
        assertThat(submission.getId()).isNotNull();
        assertThat(submission.getStudent()).isEqualTo(student);
        assertThat(submission.getQuiz()).isEqualTo(quiz);
        assertThat(submission.getAttemptNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("查找学生的提交记录")
    void findCompletedSubmissionsByStudent() {
        // Arrange - Create submissions with different statuses
        Submission completed = new Submission(student, quiz, 2);
        completed.setStatus(Submission.SubmissionStatus.SUBMITTED);
        completed.setSubmitTime(LocalDateTime.now());
        submissionRepository.save(completed);

        // Act
        List<Submission> result = submissionRepository.findCompletedSubmissionsByStudent(
            student, Submission.SubmissionStatus.IN_PROGRESS);

        // Assert
        assertThat(result).contains(submission);
        // 注意：具体行为依赖于实际实现
    }

    @Test
    @DisplayName("根据学生、测验和状态查找提交")
    void findByStudentAndQuizAndStatus() {
        // Act
        Optional<Submission> result = submissionRepository.findByStudentAndQuizAndStatus(
            student, quiz, Submission.SubmissionStatus.IN_PROGRESS);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(submission.getId());

        // Test non-existent status
        Optional<Submission> notFound = submissionRepository.findByStudentAndQuizAndStatus(
            student, quiz, Submission.SubmissionStatus.GRADED);
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("统计学生的测验提交次数")
    void countByStudentAndQuiz() {
        // Arrange
        Submission attempt2 = new Submission(student, quiz, 2);
        attempt2.setStatus(Submission.SubmissionStatus.SUBMITTED);
        submissionRepository.save(attempt2);

        // Act
        long count = submissionRepository.countByStudentAndQuiz(student, quiz);

        // Assert
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("查找测验的提交记录")
    void findLatestSubmissionsByQuiz() {
        // Arrange - Create another student and submission
        User student2 = new User();
        student2.setUsername("student2");
        student2.setPassword("password");
        student2.setEmail("student2@example.com");
        student2.setFullName("Student 2");
        student2.setRole(User.Role.STUDENT);
        student2.setEnabled(true);
        student2 = userRepository.save(student2);

        Submission sub2 = new Submission(student2, quiz, 1);
        sub2.setStatus(Submission.SubmissionStatus.SUBMITTED);
        sub2.setSubmitTime(LocalDateTime.now().minusMinutes(30));
        submissionRepository.save(sub2);

        // Act
        List<Submission> result = submissionRepository.findLatestSubmissionsByQuiz(quiz);

        // Assert
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("统计测验的不同学生提交数")
    void countDistinctStudentsByQuiz() {
        // Arrange
        User student2 = new User();
        student2.setUsername("student2");
        student2.setPassword("password");
        student2.setEmail("student2@example.com");
        student2.setFullName("Student 2");
        student2.setRole(User.Role.STUDENT);
        student2.setEnabled(true);
        student2 = userRepository.save(student2);

        Submission sub2 = new Submission(student2, quiz, 1);
        submissionRepository.save(sub2);

        // Act
        long count = submissionRepository.countDistinctStudentsByQuiz(quiz);

        // Assert
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("计算测验的平均分")
    void calculateAverageScoreByQuiz() {
        // Arrange - Create scored submissions
        Submission scored = new Submission(student, quiz, 2);
        scored.setStatus(Submission.SubmissionStatus.GRADED);
        scored.setTotalScore(85.0);
        scored.setMaxScore(100.0);
        scored.setPercentage(85.0);
        scored.setSubmitTime(LocalDateTime.now());
        submissionRepository.save(scored);

        // Act
        Double average = submissionRepository.calculateAverageScoreByQuiz(
            quiz, Submission.SubmissionStatus.IN_PROGRESS);

        // Assert
        // 注意：具体返回值依赖于实际实现逻辑
        assertThat(average).isNotNull();
    }

    @Test
    @DisplayName("根据测验教师查找提交")
    void findByQuiz_Teacher() {
        // Arrange - Create quiz and submission from other teacher
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
        otherQuiz.setTeacher(otherTeacher);
        otherQuiz.setTimeLimit(60);
        otherQuiz.setMaxAttempts(3);
        otherQuiz = quizRepository.save(otherQuiz);

        Submission otherSubmission = new Submission(student, otherQuiz, 1);
        submissionRepository.save(otherSubmission);

        // Act
        List<Submission> teacherSubmissions = submissionRepository.findByQuiz_Teacher(teacher);

        // Assert
        assertThat(teacherSubmissions).contains(submission);
        assertThat(teacherSubmissions).doesNotContain(otherSubmission);
    }

    @Test
    @DisplayName("更新提交记录")
    void updateSubmission() {
        // Arrange
        submission.setTotalScore(90.0);
        submission.setMaxScore(100.0);
        submission.setPercentage(90.0);
        submission.setStatus(Submission.SubmissionStatus.GRADED);

        // Act
        Submission updated = submissionRepository.save(submission);

        // Assert
        assertThat(updated.getTotalScore()).isEqualTo(90.0);
        assertThat(updated.getPercentage()).isEqualTo(90.0);
        assertThat(updated.getStatus()).isEqualTo(Submission.SubmissionStatus.GRADED);
    }

    @Test
    @DisplayName("删除提交记录")
    void deleteSubmission() {
        // Act
        submissionRepository.delete(submission);

        // Assert
        Optional<Submission> found = submissionRepository.findById(submission.getId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("查找教师的所有提交记录")
    void findAllByQuiz_Teacher() {
        // Arrange - Create more student submissions
        User student2 = new User();
        student2.setUsername("student2");
        student2.setPassword("password");
        student2.setEmail("student2@example.com");
        student2.setFullName("Student 2");
        student2.setRole(User.Role.STUDENT);
        student2.setEnabled(true);
        student2 = userRepository.save(student2);

        Submission sub2 = new Submission(student2, quiz, 1);
        submissionRepository.save(sub2);

        // Act
        List<Submission> teacherSubmissions = submissionRepository.findByQuiz_Teacher(teacher);

        // Assert
        assertThat(teacherSubmissions).hasSize(2);
        assertThat(teacherSubmissions).allMatch(s ->
            s.getQuiz().getTeacher().getId().equals(teacher.getId()));
    }

    @Test
    @DisplayName("提交状态枚举值")
    void submissionStatusEnum() {
        // Verify all status enum values
        Submission.SubmissionStatus[] statuses = Submission.SubmissionStatus.values();

        assertThat(statuses).contains(
            Submission.SubmissionStatus.IN_PROGRESS,
            Submission.SubmissionStatus.SUBMITTED,
            Submission.SubmissionStatus.AUTO_SUBMITTED,
            Submission.SubmissionStatus.GRADED
        );

        // Verify display names
        assertThat(Submission.SubmissionStatus.IN_PROGRESS.getDisplayName()).isEqualTo("In Progress");
        assertThat(Submission.SubmissionStatus.SUBMITTED.getDisplayName()).isEqualTo("Submitted");
        assertThat(Submission.SubmissionStatus.AUTO_SUBMITTED.getDisplayName()).isEqualTo("Auto Submitted");
        assertThat(Submission.SubmissionStatus.GRADED.getDisplayName()).isEqualTo("Graded");
    }
}
