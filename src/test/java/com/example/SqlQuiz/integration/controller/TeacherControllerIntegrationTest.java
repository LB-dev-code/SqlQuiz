package com.example.SqlQuiz.integration.controller;

import com.example.SqlQuiz.entity.User;
import com.example.SqlQuiz.repository.UserRepository;
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
@DisplayName("TeacherController Integration Tests")
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class TeacherControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testTeacher;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up existing test data
        userRepository.deleteAll();

        // Create test teacher user
        testTeacher = new User();
        testTeacher.setUsername("teacher1");
        testTeacher.setPassword(passwordEncoder.encode("password"));
        testTeacher.setEmail("teacher1@example.com");
        testTeacher.setFullName("Test Teacher");
        testTeacher.setRole(User.Role.TEACHER);
        testTeacher.setEnabled(true);
        testTeacher = userRepository.save(testTeacher);
    }

    @Test
    @WithMockUser(username = "teacher1", roles = "TEACHER")
    @DisplayName("Access teacher dashboard - Success")
    void dashboard_Success() throws Exception {
        mockMvc.perform(get("/teacher/dashboard"))
            .andExpect(status().isOk())
            .andExpect(view().name("teacher/dashboard"))
            .andExpect(model().attributeExists("teacher"))
            .andExpect(model().attributeExists("quizzes"));
    }

    @Test
    @DisplayName("Access teacher dashboard - Unauthorized")
    void dashboard_Unauthorized() throws Exception {
        mockMvc.perform(get("/teacher/dashboard"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "teacher1", roles = "TEACHER")
    @DisplayName("Access quiz management page - Success")
    void quizManagement_Success() throws Exception {
        mockMvc.perform(get("/teacher/quizzes"))
            .andExpect(status().isOk())
            .andExpect(view().name("teacher/quiz-list"))
            .andExpect(model().attributeExists("teacher"))
            .andExpect(model().attributeExists("quizzes"));
    }

    @Test
    @WithMockUser(username = "teacher1", roles = "TEACHER")
    @DisplayName("Access create quiz page - Success")
    void createQuizPage_Success() throws Exception {
        mockMvc.perform(get("/teacher/quiz/create"))
            .andExpect(status().isOk())
            .andExpect(view().name("teacher/quiz-create"))
            .andExpect(model().attributeExists("teacher"))
            .andExpect(model().attributeExists("quiz"));
    }

    @Test
    @WithMockUser(username = "teacher1", roles = "TEACHER")
    @DisplayName("Create quiz - Missing required fields")
    void createQuiz_MissingFields() throws Exception {
        mockMvc.perform(post("/teacher/quiz/create")
                .param("title", "")
                .param("timeLimit", "")
                .param("maxAttempts", "")
                .param("startTime", "")
                .param("endTime", ""))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/teacher/quiz/create"))
            .andExpect(flash().attributeExists("errors"));
    }

    @Test
    @WithMockUser(username = "teacher1", roles = "TEACHER")
    @DisplayName("Access SQL test page - Success")
    void sqlTestPage_Success() throws Exception {
        mockMvc.perform(get("/teacher/sql-test"))
            .andExpect(status().isOk())
            .andExpect(view().name("teacher/sql-test"))
            .andExpect(model().attributeExists("teacher"));
    }
}
