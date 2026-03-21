package com.example.SqlQuiz.e2e;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.repository.*;
import com.example.SqlQuiz.service.QuizService;
import com.example.SqlQuiz.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Complete Quiz Flow - End-to-End Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class QuizFlowE2ETest {

    @Autowired
    private UserService userService;

    @Autowired
    private QuizService quizService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private QuestionAnswerRepository questionAnswerRepository;

    private static User teacher;
    private static User student;
    private static Quiz quiz;
    private static Submission submission;

    @Test
    @Order(1)
    @Transactional
    @DisplayName("Step 1: Create Teacher and Student Accounts")
    void step1_CreateUsers() {
        // Create teacher
        teacher = userService.registerUser(
            "teacher_e2e",
            "password123",
            "teacher_e2e@example.com",
            "E2E Teacher",
            User.Role.TEACHER
        );
        assertThat(teacher).isNotNull();
        assertThat(teacher.getId()).isNotNull();

        // Create student
        student = userService.registerUser(
            "student_e2e",
            "password123",
            "student_e2e@example.com",
            "E2E Student",
            User.Role.STUDENT
        );
        assertThat(student).isNotNull();
        assertThat(student.getId()).isNotNull();

        System.out.println("Created teacher ID: " + teacher.getId());
        System.out.println("Created student ID: " + student.getId());
    }

    @Test
    @Order(2)
    @Transactional
    @DisplayName("Step 2: Teacher Creates Quiz")
    void step2_CreateQuiz() throws Exception {
        // Create quiz
        quiz = quizService.createQuiz(
            "E2E Test Quiz",
            "This is a quiz for end-to-end testing",
            60, // 60 minutes time limit
            3,  // Maximum 3 attempts
            teacher
        );

        assertThat(quiz).isNotNull();
        assertThat(quiz.getId()).isNotNull();

        // Set start and end time
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(10);
        LocalDateTime endTime = LocalDateTime.now().plusDays(1);

        quiz = quizService.updateQuiz(
            quiz.getId(),
            "E2E Test Quiz",
            "This is a quiz for end-to-end testing",
            60,
            3,
            startTime,
            endTime
        );

        assertThat(quiz.getStartTime()).isNotNull();
        assertThat(quiz.getEndTime()).isNotNull();

        System.out.println("Created quiz ID: " + quiz.getId());
    }

    @Test
    @Order(3)
    @Transactional
    @DisplayName("Step 3: Teacher Adds Questions")
    void step3_AddQuestions() {
        // Add first question - basic SELECT
        Question q1 = quizService.addQuestionToQuiz(
            quiz.getId(),
            "查询所有学生的信息",
            Question.QuestionType.SELECT_BASIC,
            "使用 SELECT * 语句查询学生表",
            "学生表包含: id, name, age, grade",
            "SELECT * FROM students",
            null,
            null,
            null,
            10.0,
            Question.DifficultyLevel.EASY
        );
        assertThat(q1).isNotNull();
        assertThat(q1.getId()).isNotNull();

        // Add second question - JOIN query
        Question q2 = quizService.addQuestionToQuiz(
            quiz.getId(),
            "查询学生及其对应班级信息",
            Question.QuestionType.SELECT_JOIN,
            "使用 INNER JOIN 连接学生表和班级表",
            "学生表(students)和班级表(classes)通过 class_id 关联",
            "SELECT s.*, c.class_name FROM students s INNER JOIN classes c ON s.class_id = c.id",
            null,
            null,
            null,
            15.0,
            Question.DifficultyLevel.MEDIUM
        );
        assertThat(q2).isNotNull();

        // Add third question - aggregate function
        Question q3 = quizService.addQuestionToQuiz(
            quiz.getId(),
            "统计每个班级的学生人数",
            Question.QuestionType.SELECT_AGGREGATE,
            "使用 COUNT 和 GROUP BY",
            "按班级分组统计学生数量",
            "SELECT class_id, COUNT(*) as student_count FROM students GROUP BY class_id",
            null,
            null,
            null,
            15.0,
            Question.DifficultyLevel.MEDIUM
        );
        assertThat(q3).isNotNull();

        // Verify question count
        List<Question> questions = quizService.getQuestionsByQuiz(quiz.getId());
        assertThat(questions).hasSize(3);

        System.out.println("Added 3 questions to quiz");
    }

    @Test
    @Order(4)
    @Transactional
    @DisplayName("Step 4: Student Checks if Quiz Can Be Taken")
    void step4_CheckCanTakeQuiz() {
        boolean canTake = quizService.canStudentTakeQuiz(quiz.getId(), student);
        assertThat(canTake).isTrue();

        // Verify quiz is open
        Optional<Quiz> quizOpt = quizService.findById(quiz.getId());
        assertThat(quizOpt).isPresent();
        assertThat(quizOpt.get().isOpen()).isTrue();
    }

    @Test
    @Order(5)
    @Transactional
    @DisplayName("Step 5: Student Starts Quiz")
    void step5_StartQuiz() {
        submission = quizService.startQuiz(quiz.getId(), student);

        assertThat(submission).isNotNull();
        assertThat(submission.getId()).isNotNull();
        assertThat(submission.getStatus()).isEqualTo(Submission.SubmissionStatus.IN_PROGRESS);
        assertThat(submission.getAttemptNumber()).isEqualTo(1);

        // Verify answer records created
        List<QuestionAnswer> answers = questionAnswerRepository.findBySubmission(submission);
        assertThat(answers).hasSize(3);

        System.out.println("Started quiz, submission ID: " + submission.getId());
    }

    @Test
    @Order(6)
    @Transactional
    @DisplayName("Step 6: Student Submits Question 1 Answer")
    void step6_SubmitAnswer1() {
        List<Question> questions = quizService.getQuestionsByQuiz(quiz.getId());
        Question q1 = questions.get(0);

        quizService.submitAnswer(submission.getId(), q1.getId(), "SELECT * FROM students");

        // Verify answer saved
        List<QuestionAnswer> answers = questionAnswerRepository.findBySubmission(submission);
        QuestionAnswer qa1 = answers.stream()
            .filter(qa -> qa.getQuestion().getId().equals(q1.getId()))
            .findFirst()
            .orElse(null);

        assertThat(qa1).isNotNull();
        assertThat(qa1.getStudentSql()).isEqualTo("SELECT * FROM students");

        System.out.println("Submitted answer for question 1");
    }

    @Test
    @Order(7)
    @Transactional
    @DisplayName("Step 7: Student Submits Question 2 Answer")
    void step7_SubmitAnswer2() {
        List<Question> questions = quizService.getQuestionsByQuiz(quiz.getId());
        Question q2 = questions.get(1);

        quizService.submitAnswer(submission.getId(), q2.getId(),
            "SELECT s.*, c.class_name FROM students s INNER JOIN classes c ON s.class_id = c.id");

        System.out.println("Submitted answer for question 2");
    }

    @Test
    @Order(8)
    @Transactional
    @DisplayName("Step 8: Student Submits Question 3 Answer")
    void step8_SubmitAnswer3() {
        List<Question> questions = quizService.getQuestionsByQuiz(quiz.getId());
        Question q3 = questions.get(2);

        quizService.submitAnswer(submission.getId(), q3.getId(),
            "SELECT class_id, COUNT(*) as student_count FROM students GROUP BY class_id");

        System.out.println("Submitted answer for question 3");
    }

    @Test
    @Order(9)
    @Transactional
    @DisplayName("Step 9: Student Submits Quiz")
    void step9_SubmitQuiz() {
        Submission result = quizService.submitQuiz(submission.getId());

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Submission.SubmissionStatus.SUBMITTED);
        assertThat(result.getSubmitTime()).isNotNull();

        System.out.println("Quiz submitted at: " + result.getSubmitTime());
    }

    @Test
    @Order(10)
    @Transactional
    @DisplayName("Step 10: Verify Submission")
    void step10_VerifySubmission() {
        Optional<Submission> subOpt = submissionRepository.findById(submission.getId());
        assertThat(subOpt).isPresent();

        Submission savedSubmission = subOpt.get();
        assertThat(savedSubmission.getStatus()).isEqualTo(Submission.SubmissionStatus.SUBMITTED);
        assertThat(savedSubmission.getStudent().getId()).isEqualTo(student.getId());
        assertThat(savedSubmission.getQuiz().getId()).isEqualTo(quiz.getId());

        // Verify all answers submitted
        List<QuestionAnswer> answers = questionAnswerRepository.findBySubmission(savedSubmission);
        assertThat(answers).hasSize(3);
        assertThat(answers).allMatch(qa -> qa.getStudentSql() != null);

        System.out.println("Submission verified successfully");
    }

    @Test
    @Order(11)
    @Transactional
    @DisplayName("Step 11: Student Retakes Quiz")
    void step11_RetakeQuiz() {
        // First attempt submitted, should be able to start second attempt
        Submission submission2 = quizService.startQuiz(quiz.getId(), student);

        assertThat(submission2).isNotNull();
        assertThat(submission2.getId()).isNotEqualTo(submission.getId());
        assertThat(submission2.getAttemptNumber()).isEqualTo(2);

        System.out.println("Started second attempt, submission ID: " + submission2.getId());

        // Submit second attempt immediately
        quizService.submitQuiz(submission2.getId());
    }

    @Test
    @Order(12)
    @Transactional
    @DisplayName("Step 12: Verify Attempt Limit")
    void step12_VerifyAttemptLimit() {
        // Used 2 attempts, can try 1 more time (3 total)
        boolean canTake = quizService.canStudentTakeQuiz(quiz.getId(), student);
        assertThat(canTake).isTrue();

        // Start third attempt
        Submission submission3 = quizService.startQuiz(quiz.getId(), student);
        assertThat(submission3.getAttemptNumber()).isEqualTo(3);

        // Submit third attempt
        quizService.submitQuiz(submission3.getId());

        // Now should not be able to try again
        canTake = quizService.canStudentTakeQuiz(quiz.getId(), student);
        assertThat(canTake).isFalse();

        System.out.println("Attempt limit verified successfully");
    }

    @AfterAll
    @DisplayName("Clean up test data")
    static void cleanup(
        @Autowired UserRepository userRepository
    ) {
        // Clean up test data
        if (student != null) {
            userRepository.delete(student);
        }
        if (teacher != null) {
            userRepository.delete(teacher);
        }
        // Quiz will be automatically cleaned up by cascade delete
        System.out.println("Cleanup completed");
    }
}
