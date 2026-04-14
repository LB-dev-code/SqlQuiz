package com.example.SqlQuiz.core;

import com.example.SqlQuiz.entity.ErrorTypeStatistics;
import com.example.SqlQuiz.entity.PracticeAnswer;
import com.example.SqlQuiz.entity.PracticeRound;
import com.example.SqlQuiz.entity.PracticeSession;
import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.entity.User;
import com.example.SqlQuiz.repository.ErrorTypeStatisticsRepository;
import com.example.SqlQuiz.repository.PracticeAnswerRepository;
import com.example.SqlQuiz.repository.PracticeRoundRepository;
import com.example.SqlQuiz.repository.PracticeSessionRepository;
import com.example.SqlQuiz.service.GLMService;
import com.example.SqlQuiz.service.PracticeService;
import com.example.SqlQuiz.service.QuestionDeduplicationService;
import com.example.SqlQuiz.service.SandboxDatabaseService;
import com.example.SqlQuiz.service.SetupSqlExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Core Feature: Student Self-Practice")
class PracticeServiceCoreTest {

    @Mock
    private PracticeSessionRepository sessionRepository;

    @Mock
    private PracticeRoundRepository roundRepository;

    @Mock
    private PracticeAnswerRepository answerRepository;

    @Mock
    private ErrorTypeStatisticsRepository statisticsRepository;

    @Mock
    private GLMService glmService;

    @Mock
    private QuestionDeduplicationService deduplicationService;

    @Mock
    private SetupSqlExecutorService setupSqlExecutorService;

    @Mock
    private SandboxDatabaseService sandboxService;

    @InjectMocks
    private PracticeService practiceService;

    private User student;

    @BeforeEach
    void setUp() {
        student = new User();
        student.setId(7L);
        student.setUsername("student");
        student.setRole(User.Role.STUDENT);
    }

    @Test
    @DisplayName("startSession should initialize history-based error statistics and persist selected types")
    void startSessionShouldInitializeStatisticsAndSaveSelectedTypes() {
        when(sessionRepository.findActiveSessionByStudent(student)).thenReturn(Optional.empty());
        when(statisticsRepository.findByStudent(student)).thenReturn(List.of());
        when(answerRepository.getStatisticsByStudentGroupByType(student.getId())).thenReturn(List.of(
                new Object[] {Question.QuestionType.SELECT_JOIN, 4L, 1L},
                new Object[] {Question.QuestionType.SELECT_BASIC, 2L, 2L}
        ));
        when(sessionRepository.save(any(PracticeSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PracticeSession session = practiceService.startSession(
                student,
                List.of(Question.QuestionType.SELECT_JOIN, Question.QuestionType.SELECT_BASIC));

        ArgumentCaptor<ErrorTypeStatistics> statsCaptor = ArgumentCaptor.forClass(ErrorTypeStatistics.class);

        assertThat(session.getStudent()).isEqualTo(student);
        assertThat(session.getStatus()).isEqualTo(PracticeSession.SessionStatus.IN_PROGRESS);
        assertThat(session.getSelectedTypes()).containsExactly(
                Question.QuestionType.SELECT_JOIN,
                Question.QuestionType.SELECT_BASIC);

        verify(statisticsRepository, times(2)).save(statsCaptor.capture());
        verify(sessionRepository).save(any(PracticeSession.class));

        List<ErrorTypeStatistics> savedStats = statsCaptor.getAllValues();
        assertThat(savedStats)
                .extracting(ErrorTypeStatistics::getQuestionType)
                .containsExactly(Question.QuestionType.SELECT_JOIN, Question.QuestionType.SELECT_BASIC);
        assertThat(savedStats.get(0).getErrorCount()).isEqualTo(3);
        assertThat(savedStats.get(0).getAccuracy()).isEqualTo(0.25);
        assertThat(savedStats.get(1).getErrorCount()).isZero();
        assertThat(savedStats.get(1).getIsMastered()).isTrue();
    }

    @Test
    @DisplayName("startSession should return an existing active session instead of creating a new one")
    void startSessionShouldReuseExistingActiveSession() {
        PracticeSession existing = new PracticeSession(student);
        existing.setId(99L);
        when(sessionRepository.findActiveSessionByStudent(student)).thenReturn(Optional.of(existing));

        PracticeSession result = practiceService.startSession(student, List.of(Question.QuestionType.SELECT_JOIN));

        assertThat(result).isSameAs(existing);
        verify(sessionRepository, never()).save(any(PracticeSession.class));
        verify(statisticsRepository, never()).findByStudent(student);
    }

    @Test
    @DisplayName("updateErrorStatistics should increment counts and recalculate accuracy")
    void updateErrorStatisticsShouldRecordAnswers() {
        ErrorTypeStatistics stat = new ErrorTypeStatistics(student, Question.QuestionType.SELECT_JOIN);
        stat.setTotalCount(4);
        stat.setCorrectCount(2);
        stat.setErrorCount(2);
        stat.recalculateAccuracy();

        when(statisticsRepository.findByStudentAndQuestionType(student, Question.QuestionType.SELECT_JOIN))
                .thenReturn(Optional.of(stat));

        practiceService.updateErrorStatistics(student, Question.QuestionType.SELECT_JOIN, true);

        assertThat(stat.getTotalCount()).isEqualTo(5);
        assertThat(stat.getCorrectCount()).isEqualTo(3);
        assertThat(stat.getErrorCount()).isEqualTo(2);
        assertThat(stat.getAccuracy()).isEqualTo(0.6);
        assertThat(stat.getLastPracticedAt()).isNotNull();
        verify(statisticsRepository).save(stat);
    }

    @Test
    @DisplayName("cleanupIncompleteSessions should complete every in-progress session for the student")
    void cleanupIncompleteSessionsShouldCompleteActiveSessions() {
        PracticeSession first = new PracticeSession(student);
        first.setId(1L);
        PracticeSession second = new PracticeSession(student);
        second.setId(2L);

        when(sessionRepository.findByStudentAndStatus(student, PracticeSession.SessionStatus.IN_PROGRESS))
                .thenReturn(List.of(first, second));
        when(sessionRepository.save(any(PracticeSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int cleaned = practiceService.cleanupIncompleteSessions(student);

        assertThat(cleaned).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(PracticeSession.SessionStatus.COMPLETED);
        assertThat(second.getStatus()).isEqualTo(PracticeSession.SessionStatus.COMPLETED);
        assertThat(first.getEndTime()).isNotNull();
        assertThat(second.getEndTime()).isNotNull();
        verify(sessionRepository, times(2)).save(any(PracticeSession.class));
    }

    @Test
    @DisplayName("generateRoundFeedback should group incorrect answers by type and include AI feedback")
    void generateRoundFeedbackShouldSummarizeIncorrectAnswers() {
        PracticeRound round = new PracticeRound();
        round.setRoundNumber(3);
        round.setCorrectCount(1);
        round.setTotalQuestions(3);

        PracticeAnswer joinAnswer = new PracticeAnswer(round, 0);
        joinAnswer.setQuestionType(Question.QuestionType.SELECT_JOIN);
        joinAnswer.setAiFeedback("Remember the JOIN condition.");

        PracticeAnswer aggregateAnswer = new PracticeAnswer(round, 1);
        aggregateAnswer.setQuestionType(Question.QuestionType.SELECT_AGGREGATE);
        aggregateAnswer.setAiFeedback("Use GROUP BY before HAVING.");

        when(answerRepository.findIncorrectByRound(round)).thenReturn(List.of(joinAnswer, aggregateAnswer));

        String feedback = ReflectionTestUtils.invokeMethod(practiceService, "generateRoundFeedback", round);

        assertThat(feedback).contains("Round 3 Summary");
        assertThat(feedback).contains("**Score:** 1/3");
        assertThat(feedback).contains("JOIN Queries");
        assertThat(feedback).contains("Aggregate Functions");
        assertThat(feedback).contains("Q1");
        assertThat(feedback).contains("Q2");
        assertThat(feedback).contains("Remember the JOIN condition.");
        assertThat(feedback).contains("Use GROUP BY before HAVING.");
    }

    @Test
    @DisplayName("isTypeMastered should reflect the stored mastery flag")
    void isTypeMasteredShouldReadRepositoryState() {
        ErrorTypeStatistics stat = new ErrorTypeStatistics(student, Question.QuestionType.SELECT_BASIC);
        stat.setIsMastered(true);

        when(statisticsRepository.findByStudentAndQuestionType(student, Question.QuestionType.SELECT_BASIC))
                .thenReturn(Optional.of(stat));

        boolean mastered = practiceService.isTypeMastered(student, Question.QuestionType.SELECT_BASIC);

        assertThat(mastered).isTrue();
    }
}
