package com.example.SqlQuiz.e2e;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.repository.*;
import com.example.SqlQuiz.service.PracticeService;
import com.example.SqlQuiz.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.*;

/**
 * Self-Practice Complete Flow - End-to-End Test
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(com.example.SqlQuiz.TestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.primary.url=jdbc:h2:mem:testdb",
    "spring.datasource.primary.driver-class-name=org.h2.Driver",
    "spring.datasource.primary.username=sa",
    "spring.datasource.primary.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Self-Practice Complete Flow - End-to-End Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PracticeFlowE2ETest {

    @Autowired
    private UserService userService;

    @Autowired
    private PracticeService practiceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PracticeSessionRepository practiceSessionRepository;

    @Autowired
    private PracticeRoundRepository practiceRoundRepository;

    @Autowired
    private PracticeAnswerRepository practiceAnswerRepository;

    private static User student;
    private static PracticeSession session;
    private static PracticeRound round;

    @Test
    @Order(1)
    @Transactional
    @DisplayName("Step 1: Create Student Account")
    void step1_CreateStudent() {
        student = userService.registerUser(
            "practice_student",
            "password123",
            "practice@example.com",
            "Practice Student",
            User.Role.STUDENT
        );

        assertThat(student).isNotNull();
        assertThat(student.getId()).isNotNull();
        assertThat(student.getRole()).isEqualTo(User.Role.STUDENT);

        System.out.println("Created student ID: " + student.getId());
    }

    @Test
    @Order(2)
    @Transactional
    @DisplayName("Step 2: Start New Practice Session (Auto Mode)")
    void step2_StartPracticeSession() {
        session = practiceService.startSession(student, (Question.QuestionType) null);

        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotNull();
        assertThat(session.getStudent()).isEqualTo(student);
        assertThat(session.getStatus()).isEqualTo(PracticeSession.SessionStatus.IN_PROGRESS);
        assertThat(session.getTargetQuestionType()).isNull();

        System.out.println("Created practice session ID: " + session.getId());
    }

    @Test
    @Order(3)
    @Transactional
    @DisplayName("Step 3: Start First Practice Round")
    void step3_StartFirstRound() {
        round = practiceService.startNewRound(session.getId());

        assertThat(round).isNotNull();
        assertThat(round.getId()).isNotNull();
        assertThat(round.getSession()).isEqualTo(session);
        assertThat(round.getRoundNumber()).isEqualTo(1);
        assertThat(round.getStatus()).isEqualTo(PracticeRound.RoundStatus.IN_PROGRESS);

        System.out.println("Started practice round ID: " + round.getId());
    }

    @Test
    @Order(4)
    @Transactional
    @DisplayName("Step 4: Submit Question 1 Answer (Correct)")
    void step4_SubmitQuestion1_Correct() {
        // 假设第一题是基础查询题
        Long answerId = practiceService.submitAnswerToRound(
            round.getId(),
            0,
            "SELECT * FROM users"
        );

        assertThat(answerId).isNotNull();

        // 验证答案记录
        PracticeAnswer answer = practiceAnswerRepository.findById(answerId).orElse(null);
        assertThat(answer).isNotNull();
        assertThat(answer.getStudentSql()).isEqualTo("SELECT * FROM users");

        System.out.println("Submitted question 1 answer ID: " + answerId);
    }

    @Test
    @Order(5)
    @Transactional
    @DisplayName("Step 5: Submit Question 2 Answer (Correct)")
    void step5_SubmitQuestion2_Correct() {
        Long answerId = practiceService.submitAnswerToRound(
            round.getId(),
            1,
            "SELECT * FROM users u JOIN orders o ON u.id = o.user_id"
        );

        assertThat(answerId).isNotNull();

        // 验证轮次统计
        PracticeRound updatedRound = practiceRoundRepository.findById(round.getId()).orElse(null);
        assertThat(updatedRound).isNotNull();
        assertThat(updatedRound.getCurrentQuestionIndex()).isGreaterThan(0);

        System.out.println("Submitted question 2 answer ID: " + answerId);
    }

    @Test
    @Order(6)
    @Transactional
    @DisplayName("Step 6: Submit Question 3 Answer (Incorrect)")
    void step6_SubmitQuestion3_Incorrect() {
        Long answerId = practiceService.submitAnswerToRound(
            round.getId(),
            2,
            "WRONG SQL QUERY"
        );

        assertThat(answerId).isNotNull();

        PracticeAnswer answer = practiceAnswerRepository.findById(answerId).orElse(null);
        assertThat(answer).isNotNull();
        assertThat(answer.getStudentSql()).isEqualTo("WRONG SQL QUERY");

        System.out.println("Submitted question 3 answer ID: " + answerId);
    }

    @Test
    @Order(7)
    @Transactional
    @DisplayName("Step 7: Complete Current Round")
    void step7_CompleteRound() {
        practiceService.completeRound(round.getId());

        PracticeRound completedRound = practiceRoundRepository.findById(round.getId()).orElse(null);
        assertThat(completedRound).isNotNull();
        assertThat(completedRound.getStatus()).isEqualTo(PracticeRound.RoundStatus.COMPLETED);
        assertThat(completedRound.getEndTime()).isNotNull();

        // 验证会话统计已更新
        PracticeSession updatedSession = practiceSessionRepository.findById(session.getId()).orElse(null);
        assertThat(updatedSession).isNotNull();
        assertThat(updatedSession.getTotalRounds()).isGreaterThan(0);

        System.out.println("Completed round at: " + completedRound.getEndTime());
    }

    @Test
    @Order(8)
    @Transactional
    @DisplayName("Step 8: Start Second Round")
    void step8_StartSecondRound() {
        PracticeRound round2 = practiceService.startNewRound(session.getId());

        assertThat(round2).isNotNull();
        assertThat(round2.getRoundNumber()).isEqualTo(2);

        System.out.println("Started second round ID: " + round2.getId());
    }

    @Test
    @Order(9)
    @Transactional
    @DisplayName("Step 9: Pause Practice Session")
    void step9_PauseSession() {
        practiceService.pauseSession(session.getId());

        PracticeSession pausedSession = practiceSessionRepository.findById(session.getId()).orElse(null);
        assertThat(pausedSession).isNotNull();
        assertThat(pausedSession.getStatus()).isEqualTo(PracticeSession.SessionStatus.PAUSED);

        System.out.println("Paused session at: " + pausedSession.getEndTime());
    }

    @Test
    @Order(10)
    @Transactional
    @DisplayName("Step 10: Resume Practice Session")
    void step10_ResumeSession() {
        practiceService.resumeSession(session.getId());

        PracticeSession resumedSession = practiceSessionRepository.findById(session.getId()).orElse(null);
        assertThat(resumedSession).isNotNull();
        assertThat(resumedSession.getStatus()).isEqualTo(PracticeSession.SessionStatus.IN_PROGRESS);

        System.out.println("Resumed session successfully");
    }

    @Test
    @Order(11)
    @Transactional
    @DisplayName("Step 11: Complete Practice Session")
    void step11_CompleteSession() {
        practiceService.completeSession(session.getId());

        PracticeSession completedSession = practiceSessionRepository.findById(session.getId()).orElse(null);
        assertThat(completedSession).isNotNull();
        assertThat(completedSession.getStatus()).isEqualTo(PracticeSession.SessionStatus.COMPLETED);
        assertThat(completedSession.getEndTime()).isNotNull();

        System.out.println("Completed session at: " + completedSession.getEndTime());
        System.out.println("Total rounds: " + completedSession.getTotalRounds());
        System.out.println("Total questions: " + completedSession.getTotalQuestions());
        System.out.println("Total correct: " + completedSession.getTotalCorrect());
        System.out.println("Overall accuracy: " + completedSession.getOverallAccuracy());
    }

    @Test
    @Order(12)
    @Transactional
    @DisplayName("Step 12: Start Practice Session with Specific Question Type")
    void step12_StartSessionWithSpecificType() {
        PracticeSession joinSession = practiceService.startSession(
            student,
            Question.QuestionType.SELECT_JOIN
        );

        assertThat(joinSession).isNotNull();
        assertThat(joinSession.getTargetQuestionType()).isEqualTo(Question.QuestionType.SELECT_JOIN);

        // 清理
        practiceService.completeSession(joinSession.getId());

        System.out.println("Created JOIN-specific practice session");
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
        System.out.println("Cleanup completed");
    }
}
