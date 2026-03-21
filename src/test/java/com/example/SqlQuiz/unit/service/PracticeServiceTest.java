package com.example.SqlQuiz.unit.service;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.repository.*;
import com.example.SqlQuiz.service.GLMService;
import com.example.SqlQuiz.service.PracticeService;
import com.example.SqlQuiz.service.QuestionDeduplicationService;
import com.example.SqlQuiz.service.SandboxDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PracticeService Unit Tests")
public class PracticeServiceTest {

    @Mock
    private PracticeSessionRepository practiceSessionRepository;

    @Mock
    private PracticeRoundRepository practiceRoundRepository;

    @Mock
    private PracticeAnswerRepository practiceAnswerRepository;

    @Mock
    private GLMService glmService;

    @Mock
    private QuestionDeduplicationService deduplicationService;

    @Mock
    private SandboxDatabaseService sandboxDatabaseService;

    @Mock
    private ErrorTypeStatisticsRepository statisticsRepository;

    @InjectMocks
    private PracticeService practiceService;

    private User testStudent;
    private PracticeSession testSession;
    private PracticeRound testRound;
    private Question testQuestion;

    @BeforeEach
    void setUp() {
        testStudent = new User();
        testStudent.setId(1L);
        testStudent.setUsername("student");
        testStudent.setRole(User.Role.STUDENT);

        testSession = new PracticeSession(testStudent);
        testSession.setId(1L);
        testSession.setStatus(PracticeSession.SessionStatus.IN_PROGRESS);
        testSession.setTotalRounds(0);
        testSession.setTotalQuestions(0);
        testSession.setTotalCorrect(0);

        testRound = new PracticeRound();
        testRound.setId(1L);
        testRound.setSession(testSession);
        testRound.setRoundNumber(1);
        testRound.setStatus(PracticeRound.RoundStatus.IN_PROGRESS);
        testRound.setTotalQuestions(5);
        testRound.setCorrectCount(0);

        testQuestion = new Question();
        testQuestion.setId(1L);
        testQuestion.setContent("Test Question");
        testQuestion.setQuestionType(Question.QuestionType.SELECT_BASIC);
        testQuestion.setScore(10.0);
        testQuestion.setDifficultyLevel(Question.DifficultyLevel.EASY);
    }

    @Test
    @DisplayName("创建练习会话 - 成功")
    void startSession_Success() {
        // Arrange
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(invocation -> {
            PracticeSession session = invocation.getArgument(0);
            session.setId(1L);
            return session;
        });

        // Act
        PracticeSession result = practiceService.startSession(testStudent, (Question.QuestionType) null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStudent()).isEqualTo(testStudent);
        assertThat(result.getStatus()).isEqualTo(PracticeSession.SessionStatus.IN_PROGRESS);
        verify(practiceSessionRepository).save(any(PracticeSession.class));
    }

    @Test
    @DisplayName("创建练习会话 - 指定题型")
    void startSession_WithQuestionType() {
        // Arrange
        Question.QuestionType questionType = Question.QuestionType.SELECT_JOIN;
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenAnswer(invocation -> {
            PracticeSession session = invocation.getArgument(0);
            session.setId(1L);
            return session;
        });

        // Act
        PracticeSession result = practiceService.startSession(testStudent, questionType);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTargetQuestionType()).isEqualTo(questionType);
        verify(practiceSessionRepository).save(any(PracticeSession.class));
    }

    @Test
    @DisplayName("获取学生当前的练习会话")
    void getActiveSession_Success() {
        // Arrange
        when(practiceSessionRepository.findActiveSessionByStudent(testStudent))
            .thenReturn(Optional.of(testSession));

        // Act
        Optional<PracticeSession> result = practiceService.getActiveSession(testStudent);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(testSession.getId());
        verify(practiceSessionRepository).findActiveSessionByStudent(testStudent);
    }

    @Test
    @DisplayName("完成练习会话")
    void completeSession_Success() {
        // Arrange
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenReturn(testSession);

        // Act
        practiceService.completeSession(1L);

        // Assert
        assertThat(testSession.getStatus()).isEqualTo(PracticeSession.SessionStatus.COMPLETED);
        assertThat(testSession.getEndTime()).isNotNull();
        verify(practiceSessionRepository).save(testSession);
    }

    @Test
    @DisplayName("暂停练习会话")
    void pauseSession_Success() {
        // Arrange
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenReturn(testSession);

        // Act
        practiceService.pauseSession(1L);

        // Assert
        assertThat(testSession.getStatus()).isEqualTo(PracticeSession.SessionStatus.PAUSED);
        verify(practiceSessionRepository).save(testSession);
    }

    @Test
    @DisplayName("恢复练习会话")
    void resumeSession_Success() {
        // Arrange
        testSession.setStatus(PracticeSession.SessionStatus.PAUSED);
        when(practiceSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(practiceSessionRepository.save(any(PracticeSession.class))).thenReturn(testSession);

        // Act
        practiceService.resumeSession(1L);

        // Assert
        assertThat(testSession.getStatus()).isEqualTo(PracticeSession.SessionStatus.IN_PROGRESS);
        verify(practiceSessionRepository).save(testSession);
    }
}
