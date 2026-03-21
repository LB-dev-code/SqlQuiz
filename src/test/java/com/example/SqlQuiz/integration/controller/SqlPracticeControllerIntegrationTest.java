package com.example.SqlQuiz.integration.controller;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.repository.PracticeRoundRepository;
import com.example.SqlQuiz.repository.UserRepository;
import com.example.SqlQuiz.service.PracticeService;
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
@DisplayName("SqlPracticeController Integration Tests")
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class SqlPracticeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PracticeRoundRepository practiceRoundRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PracticeService practiceService;

    private User testStudent;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up existing test data
        practiceRoundRepository.deleteAll();
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
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Access practice home page - Success")
    void index_Success() throws Exception {
        mockMvc.perform(get("/sql-practice"))
            .andExpect(status().isOk())
            .andExpect(view().name("sql-practice/index"))
            .andExpect(model().attributeExists("student"));
    }

    @Test
    @DisplayName("Access practice home page - Unauthorized")
    void index_Unauthorized() throws Exception {
        mockMvc.perform(get("/sql-practice"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Access practice mode selection page - Success")
    void modeSelection_Success() throws Exception {
        mockMvc.perform(get("/sql-practice/mode"))
            .andExpect(status().isOk())
            .andExpect(view().name("sql-practice/mode-selection"))
            .andExpect(model().attributeExists("questionTypes"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Start new practice session - Auto mode")
    @Transactional
    void startSession_AutoMode() throws Exception {
        mockMvc.perform(post("/sql-practice/start")
                .param("mode", "auto"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrlPattern("/sql-practice/session/*"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Start new practice session - Selected question type")
    @Transactional
    void startSession_SelectedType() throws Exception {
        mockMvc.perform(post("/sql-practice/start")
                .param("mode", "selected")
                .param("questionType", "SELECT_JOIN"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrlPattern("/sql-practice/session/*"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Start new practice session - Mixed question types")
    @Transactional
    void startSession_MixedTypes() throws Exception {
        mockMvc.perform(post("/sql-practice/start")
                .param("mode", "mixed")
                .param("types", "SELECT_BASIC")
                .param("types", "SELECT_JOIN")
                .param("types", "SELECT_AGGREGATE"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrlPattern("/sql-practice/session/*"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Get practice question")
    @Transactional
    void getNextQuestion_Success() throws Exception {
        // Create session first
        PracticeSession session = practiceService.startSession(testStudent, (Question.QuestionType) null);

        mockMvc.perform(get("/sql-practice/session/{id}/next-question", session.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questionIndex").exists())
            .andExpect(jsonPath("$.questionContent").exists());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Submit practice answer")
    @Transactional
    void submitAnswer_Success() throws Exception {
        // Create session and round
        PracticeSession session = practiceService.startSession(testStudent, (Question.QuestionType) null);
        PracticeRound round = new PracticeRound(session, testStudent, 1);
        practiceRoundRepository.save(round);

        mockMvc.perform(post("/sql-practice/session/{sessionId}/submit", session.getId())
                .param("roundId", round.getId().toString())
                .param("questionIndex", "0")
                .param("sql", "SELECT * FROM users"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Complete practice round")
    @Transactional
    void completeRound_Success() throws Exception {
        PracticeSession session = practiceService.startSession(testStudent, (Question.QuestionType) null);
        PracticeRound round = new PracticeRound(session, testStudent, 1);
        practiceRoundRepository.save(round);

        mockMvc.perform(post("/sql-practice/round/{id}/complete", round.getId()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Pause practice session")
    @Transactional
    void pauseSession_Success() throws Exception {
        PracticeSession session = practiceService.startSession(testStudent, (Question.QuestionType) null);

        mockMvc.perform(post("/sql-practice/session/{id}/pause", session.getId()))
            .andExpect(status().isFound())
            .andExpect(flash().attributeExists("message"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Resume practice session")
    @Transactional
    void resumeSession_Success() throws Exception {
        PracticeSession session = practiceService.startSession(testStudent, (Question.QuestionType) null);
        practiceService.pauseSession(session.getId());

        mockMvc.perform(post("/sql-practice/session/{id}/resume", session.getId()))
            .andExpect(status().isFound());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("Complete practice session")
    @Transactional
    void completeSession_Success() throws Exception {
        PracticeSession session = practiceService.startSession(testStudent, (Question.QuestionType) null);

        mockMvc.perform(post("/sql-practice/session/{id}/complete", session.getId()))
            .andExpect(status().isFound());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("View practice session results")
    @Transactional
    void sessionResult_Success() throws Exception {
        PracticeSession session = practiceService.startSession(testStudent, (Question.QuestionType) null);
        practiceService.completeSession(session.getId());

        mockMvc.perform(get("/sql-practice/session/{id}/result", session.getId()))
            .andExpect(status().isOk())
            .andExpect(view().name("sql-practice/session-result"))
            .andExpect(model().attributeExists("session"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    @DisplayName("View practice history")
    void practiceHistory_Success() throws Exception {
        mockMvc.perform(get("/sql-practice/history"))
            .andExpect(status().isOk())
            .andExpect(view().name("sql-practice/history"))
            .andExpect(model().attributeExists("sessions"));
    }
}
