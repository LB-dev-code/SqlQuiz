package com.example.SqlQuiz.unit.service;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.repository.*;
import com.example.SqlQuiz.service.GLMService;
import com.example.SqlQuiz.service.QuizService;
import com.example.SqlQuiz.service.QuizTableMetadataService;
import com.example.SqlQuiz.service.SetupSqlExecutorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuizService Unit Tests")
public class QuizServiceTest {

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private QuestionAnswerRepository questionAnswerRepository;

    @Mock
    private GLMService glmService;

    @Mock
    private SetupSqlExecutorService setupSqlExecutorService;

    @Mock
    private QuizTableMetadataRepository quizTableMetadataRepository;

    @Mock
    private QuizTableMetadataService tableMetadataService;

    @InjectMocks
    private QuizService quizService;

    private User testTeacher;
    private User testStudent;
    private Quiz testQuiz;
    private Question testQuestion;

    @BeforeEach
    void setUp() {
        testTeacher = new User();
        testTeacher.setId(1L);
        testTeacher.setUsername("teacher");
        testTeacher.setRole(User.Role.TEACHER);

        testStudent = new User();
        testStudent.setId(2L);
        testStudent.setUsername("student");
        testStudent.setRole(User.Role.STUDENT);

        testQuiz = new Quiz();
        testQuiz.setId(1L);
        testQuiz.setTitle("Test Quiz");
        testQuiz.setDescription("Test Description");
        testQuiz.setTimeLimit(60);
        testQuiz.setMaxAttempts(3);
        testQuiz.setTeacher(testTeacher);
        testQuiz.setIsActive(true);
        testQuiz.setStartTime(LocalDateTime.now().minusDays(1));
        testQuiz.setEndTime(LocalDateTime.now().plusDays(1));

        testQuestion = new Question();
        testQuestion.setId(1L);
        testQuestion.setContent("Test Question");
        testQuestion.setQuestionType(Question.QuestionType.SELECT_BASIC);
        testQuestion.setScore(10.0);
        testQuestion.setDifficultyLevel(Question.DifficultyLevel.EASY);
        testQuestion.setQuiz(testQuiz);
    }

    @Test
    @DisplayName("创建测验 - 成功")
    void createQuiz_Success() throws JsonProcessingException {
        // Arrange
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz quiz = invocation.getArgument(0);
            quiz.setId(1L);
            return quiz;
        });

        // Act
        Quiz result = quizService.createQuiz("New Quiz", "Description", 60, 3, testTeacher);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("New Quiz");
        assertThat(result.getDescription()).isEqualTo("Description");
        assertThat(result.getTimeLimit()).isEqualTo(60);
        assertThat(result.getMaxAttempts()).isEqualTo(3);
        assertThat(result.getTeacher()).isEqualTo(testTeacher);
        assertThat(result.getIsActive()).isTrue();
        verify(quizRepository).save(any(Quiz.class));
    }

    @Test
    @DisplayName("更新测验 - 成功")
    void updateQuiz_Success() {
        // Arrange
        when(quizRepository.findById(1L)).thenReturn(Optional.of(testQuiz));
        when(quizRepository.save(any(Quiz.class))).thenReturn(testQuiz);

        LocalDateTime newStartTime = LocalDateTime.now().plusHours(1);
        LocalDateTime newEndTime = LocalDateTime.now().plusDays(2);

        // Act
        Quiz result = quizService.updateQuiz(1L, "Updated Title", "Updated Description",
            90, 5, newStartTime, newEndTime);

        // Assert
        assertThat(result).isNotNull();
        verify(quizRepository).save(any(Quiz.class));
    }

    @Test
    @DisplayName("更新测验 - 测验不存在")
    void updateQuiz_NotFound() {
        // Arrange
        when(quizRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() ->
            quizService.updateQuiz(999L, "Title", "Description", 60, 3, null, null)
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Quiz does not exist");

        verify(quizRepository, never()).save(any(Quiz.class));
    }

    @Test
    @DisplayName("查找测验 - 成功")
    void findById_Success() {
        // Arrange
        when(quizRepository.findById(1L)).thenReturn(Optional.of(testQuiz));

        // Act
        Optional<Quiz> result = quizService.findById(1L);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
        verify(quizRepository).findById(1L);
    }

    @Test
    @DisplayName("获取所有活跃测验")
    void findActiveQuizzes_Success() {
        // Arrange
        List<Quiz> quizzes = Arrays.asList(testQuiz);
        when(quizRepository.findByIsActiveTrueOrderByCreatedAtDesc()).thenReturn(quizzes);

        // Act
        List<Quiz> result = quizService.findActiveQuizzes();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsActive()).isTrue();
        verify(quizRepository).findByIsActiveTrueOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("切换测验状态 - 成功")
    void toggleQuizStatus_Success() {
        // Arrange
        when(quizRepository.findById(1L)).thenReturn(Optional.of(testQuiz));
        when(quizRepository.save(any(Quiz.class))).thenReturn(testQuiz);
        boolean originalStatus = testQuiz.getIsActive();

        // Act
        quizService.toggleQuizStatus(1L);

        // Assert
        assertThat(testQuiz.getIsActive()).isNotEqualTo(originalStatus);
        verify(quizRepository).save(testQuiz);
    }

    @Test
    @DisplayName("添加题目到测验 - 成功")
    void addQuestionToQuiz_Success() {
        // Arrange
        when(quizRepository.findById(1L)).thenReturn(Optional.of(testQuiz));
        when(questionRepository.getMaxOrderIndexByQuiz(testQuiz)).thenReturn(0);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question q = invocation.getArgument(0);
            q.setId(2L);
            return q;
        });

        // Act
        Question result = quizService.addQuestionToQuiz(1L, "New Question",
            Question.QuestionType.SELECT_JOIN, "Description", "DB Context",
            "Expected SQL", "Test Data", "Expected Result", 15.0,
            Question.DifficultyLevel.MEDIUM);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("New Question");
        assertThat(result.getQuiz()).isEqualTo(testQuiz);
        assertThat(result.getOrderIndex()).isEqualTo(1);
        verify(questionRepository).save(any(Question.class));
    }

    @Test
    @DisplayName("学生开始测验 - 成功")
    void startQuiz_Success() {
        // Arrange
        when(quizRepository.findById(1L)).thenReturn(Optional.of(testQuiz));
        when(submissionRepository.findByStudentAndQuizAndStatus(testStudent, testQuiz,
            Submission.SubmissionStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(submissionRepository.countByStudentAndQuiz(testStudent, testQuiz)).thenReturn(0L);
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission s = invocation.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(questionRepository.findByQuizOrderByOrderIndexAsc(testQuiz))
            .thenReturn(Arrays.asList(testQuestion));
        when(questionAnswerRepository.save(any(QuestionAnswer.class))).thenAnswer(invocation -> {
            QuestionAnswer qa = invocation.getArgument(0);
            qa.setId(1L);
            return qa;
        });

        // Act
        Submission result = quizService.startQuiz(1L, testStudent);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(Submission.SubmissionStatus.IN_PROGRESS);
        assertThat(result.getAttemptNumber()).isEqualTo(1);
        verify(submissionRepository).save(any(Submission.class));
        verify(questionAnswerRepository, atLeastOnce()).save(any(QuestionAnswer.class));
    }

    @Test
    @DisplayName("学生开始测验 - 测验未开放")
    void startQuiz_QuizNotOpen() {
        // Arrange
        testQuiz.setStartTime(LocalDateTime.now().plusDays(1));
        when(quizRepository.findById(1L)).thenReturn(Optional.of(testQuiz));

        // Act & Assert
        assertThatThrownBy(() -> quizService.startQuiz(1L, testStudent))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Quiz is not currently available");
    }

    @Test
    @DisplayName("学生开始测验 - 超过最大尝试次数")
    void startQuiz_MaxAttemptsReached() {
        // Arrange
        when(quizRepository.findById(1L)).thenReturn(Optional.of(testQuiz));
        when(submissionRepository.findByStudentAndQuizAndStatus(testStudent, testQuiz,
            Submission.SubmissionStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(submissionRepository.countByStudentAndQuiz(testStudent, testQuiz)).thenReturn(3L);

        // Act & Assert
        assertThatThrownBy(() -> quizService.startQuiz(1L, testStudent))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Maximum attempt limit reached");
    }

    @Test
    @DisplayName("检查学生是否可以参加测验 - 可以")
    void canStudentTakeQuiz_CanTake() {
        // Arrange
        when(quizRepository.findById(1L)).thenReturn(Optional.of(testQuiz));
        when(submissionRepository.findByStudentAndQuizAndStatus(testStudent, testQuiz,
            Submission.SubmissionStatus.IN_PROGRESS)).thenReturn(Optional.empty());
        when(submissionRepository.countByStudentAndQuiz(testStudent, testQuiz)).thenReturn(1L);

        // Act
        boolean result = quizService.canStudentTakeQuiz(1L, testStudent);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("学生提交答案 - 成功")
    void submitAnswer_Success() {
        // Arrange
        Submission submission = new Submission(testStudent, testQuiz, 1);
        submission.setId(1L);
        submission.setStatus(Submission.SubmissionStatus.IN_PROGRESS);

        QuestionAnswer questionAnswer = new QuestionAnswer(testQuestion, submission);
        questionAnswer.setId(1L);

        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(questionRepository.findById(1L)).thenReturn(Optional.of(testQuestion));
        when(questionAnswerRepository.findBySubmissionAndQuestion(submission, testQuestion))
            .thenReturn(Optional.of(questionAnswer));
        when(questionAnswerRepository.save(any(QuestionAnswer.class))).thenReturn(questionAnswer);

        // Act
        quizService.submitAnswer(1L, 1L, "SELECT * FROM users");

        // Assert
        verify(questionAnswerRepository).save(any(QuestionAnswer.class));
    }

    @Test
    @DisplayName("学生提交答案 - 提交已完成")
    void submitAnswer_AlreadySubmitted() {
        // Arrange
        Submission submission = new Submission(testStudent, testQuiz, 1);
        submission.setStatus(Submission.SubmissionStatus.SUBMITTED);

        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(questionRepository.findById(1L)).thenReturn(Optional.of(testQuestion));

        // Act & Assert
        assertThatThrownBy(() -> quizService.submitAnswer(1L, 1L, "SELECT * FROM users"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Quiz has ended");
    }

    @Test
    @DisplayName("获取测验统计信息")
    void getQuizStatistics_Success() {
        // Arrange
        when(quizRepository.findById(1L)).thenReturn(Optional.of(testQuiz));
        when(submissionRepository.countDistinctStudentsByQuiz(testQuiz)).thenReturn(10L);
        when(submissionRepository.calculateAverageScoreByQuiz(testQuiz,
            Submission.SubmissionStatus.IN_PROGRESS)).thenReturn(75.5);

        // Act
        QuizService.QuizStatistics result = quizService.getQuizStatistics(1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getParticipantCount()).isEqualTo(10);
        assertThat(result.getAverageScore()).isEqualTo(75.5);
    }
}
