package com.example.SqlQuiz.integration.controller;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.repository.*;
import com.example.SqlQuiz.service.QuizService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
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
@DisplayName("StudentController Integration Tests")
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class StudentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private QuizService quizService;

    private User testStudent;
    private User testTeacher;
    private Quiz testQuiz;
    private Question testQuestion;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up existing test data
        submissionRepository.deleteAll();
        questionAnswerRepository.deleteAll();
        questionRepository.deleteAll();
        quizRepository.deleteAll();
        userRepository.deleteAll();

        // Create test student user
        testStudent = new User();
        testStudent.setUsername("student1");
        testStudent.setPassword(passwordEncoder.encode("password"));
        testStudent.setEmail("student1@example.com");
        testStudent.setFullName("Test Student");
        testStudent.setRole(User.Role.STUDENT);
        testStudent.setEnabled(true);
        testStudent = userRepository.save(testStudent);

        // Create test teacher user
        testTeacher = new User();
        testTeacher.setUsername("teacher1");
        testTeacher.setPassword(passwordEncoder.encode("password"));
        testTeacher.setEmail("teacher1@example.com");
        testTeacher.setFullName("Test Teacher");
        testTeacher.setRole(User.Role.TEACHER);
        testTeacher.setEnabled(true);
        testTeacher = userRepository.save(testTeacher);

        // Create test quiz
        testQuiz = new Quiz();
        testQuiz.setTitle("Test Quiz");
        testQuiz.setDescription("Test Description");
        testQuiz.setTimeLimit(60);
        testQuiz.setMaxAttempts(3);
        testQuiz.setTeacher(testTeacher);
        testQuiz.setIsActive(true);
        testQuiz.setStartTime(LocalDateTime.now().minusHours(1));
        testQuiz.setEndTime(LocalDateTime.now().plusDays(1));
        testQuiz = quizRepository.save(testQuiz);

        // Create test question
        testQuestion = new Question();
        testQuestion.setContent("Test Question");
        testQuestion.setQuestionType(Question.QuestionType.SELECT_BASIC);
        testQuestion.setScore(10.0);
        testQuestion.setDifficultyLevel(Question.DifficultyLevel.EASY);
        testQuestion.setQuiz(testQuiz);
        testQuestion.setOrderIndex(1);
        testQuestion = questionRepository.save(testQuestion);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Access student dashboard - Success")
    void dashboard_Success() throws Exception {
        mockMvc.perform(get("/student/dashboard"))
            .andExpect(status().isOk())
            .andExpect(view().name("student/dashboard"))
            .andExpect(model().attributeExists("student"))
            .andExpect(model().attributeExists("availableQuizzes"))
            .andExpect(model().attributeExists("submissions"));
    }

    @Test
    @DisplayName("Access student dashboard - Unauthorized")
    void dashboard_Unauthorized() throws Exception {
        mockMvc.perform(get("/student/dashboard"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Access quiz list page - Success")
    void quizList_Success() throws Exception {
        mockMvc.perform(get("/student/quizzes"))
            .andExpect(status().isOk())
            .andExpect(view().name("student/quiz-list"))
            .andExpect(model().attributeExists("student"))
            .andExpect(model().attributeExists("quizzes"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("View quiz details - Success")
    void quizDetail_Success() throws Exception {
        mockMvc.perform(get("/student/quiz/{id}", testQuiz.getId()))
            .andExpect(status().isOk())
            .andExpect(view().name("student/quiz-detail"))
            .andExpect(model().attributeExists("quiz"))
            .andExpect(model().attributeExists("canTake"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Start quiz - Success")
    @Transactional
    void startQuiz_Success() throws Exception {
        mockMvc.perform(post("/student/quiz/{id}/start", testQuiz.getId()))
            .andExpect(status().isFound())
            .andExpect(redirectedUrlPattern("/student/submission/*"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Submit answer - Success")
    @Transactional
    void submitAnswer_Success() throws Exception {
        // Start quiz first
        Submission submission = quizService.startQuiz(testQuiz.getId(), testStudent);

        mockMvc.perform(post("/student/submission/{submissionId}/answer", submission.getId())
                .param("questionId", testQuestion.getId().toString())
                .param("sql", "SELECT * FROM users"))
            .andExpect(status().isOk())
            .andExpect(content().string("success"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Submit quiz - Success")
    @Transactional
    void submitQuiz_Success() throws Exception {
        // Start quiz first
        Submission submission = quizService.startQuiz(testQuiz.getId(), testStudent);

        mockMvc.perform(post("/student/submission/{id}/submit", submission.getId()))
            .andExpect(status().isOk())
            .andExpect(content().string("success"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("View my submissions - Success")
    void mySubmissions_Success() throws Exception {
        mockMvc.perform(get("/student/submissions"))
            .andExpect(status().isOk())
            .andExpect(view().name("student/my-submissions"))
            .andExpect(model().attributeExists("student"))
            .andExpect(model().attributeExists("submissions"));
    }
}
