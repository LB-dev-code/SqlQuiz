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
@DisplayName("AuthController Integration Tests")
public class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // Clear data
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Access login page - Success")
    void loginPage_Success() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Access registration page - Success")
    void registerPage_Success() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Register new user - Success")
    @Transactional
    void register_Success() throws Exception {
        mockMvc.perform(post("/register")
                .param("username", "newuser")
                .param("password", "password123")
                .param("email", "newuser@example.com")
                .param("fullName", "New User")
                .param("role", "STUDENT"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/login"));

        // Verify user has been created
        User user = userRepository.findByUsername("newuser").orElse(null);
        assertThat(user).isNotNull();
        assertThat(user.getEmail()).isEqualTo("newuser@example.com");
        assertThat(user.getRole()).isEqualTo(User.Role.STUDENT);
    }

    @Test
    @DisplayName("Register user - Username already exists")
    @Transactional
    void register_UsernameExists() throws Exception {
        // Create an existing user first
        User existingUser = new User();
        existingUser.setUsername("existinguser");
        existingUser.setPassword(passwordEncoder.encode("password"));
        existingUser.setEmail("existing@example.com");
        existingUser.setFullName("Existing User");
        existingUser.setRole(User.Role.STUDENT);
        existingUser.setEnabled(true);
        userRepository.save(existingUser);

        // Try to register with the same username
        mockMvc.perform(post("/register")
                .param("username", "existinguser")
                .param("password", "password123")
                .param("email", "different@example.com")
                .param("fullName", "Different User")
                .param("role", "STUDENT"))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("error"));
    }

    @Test
    @DisplayName("Register user - Email already exists")
    @Transactional
    void register_EmailExists() throws Exception {
        // Create an existing user first
        User existingUser = new User();
        existingUser.setUsername("existinguser");
        existingUser.setPassword(passwordEncoder.encode("password"));
        existingUser.setEmail("existing@example.com");
        existingUser.setFullName("Existing User");
        existingUser.setRole(User.Role.STUDENT);
        existingUser.setEnabled(true);
        userRepository.save(existingUser);

        // Try to register with the same email
        mockMvc.perform(post("/register")
                .param("username", "newuser")
                .param("password", "password123")
                .param("email", "existing@example.com")
                .param("fullName", "New User")
                .param("role", "STUDENT"))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("error"));
    }

    @Test
    @DisplayName("Register user - Password too short")
    void register_PasswordTooShort() throws Exception {
        mockMvc.perform(post("/register")
                .param("username", "newuser")
                .param("password", "12345") // Less than 6 characters
                .param("email", "newuser@example.com")
                .param("fullName", "New User")
                .param("role", "STUDENT"))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("error"));
    }

    @Test
    @DisplayName("Register user - Invalid email format")
    void register_InvalidEmail() throws Exception {
        mockMvc.perform(post("/register")
                .param("username", "newuser")
                .param("password", "password123")
                .param("email", "invalid-email") // Invalid email format
                .param("fullName", "New User")
                .param("role", "STUDENT"))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("error"));
    }

    @Test
    @DisplayName("Register teacher user")
    @Transactional
    void register_Teacher() throws Exception {
        mockMvc.perform(post("/register")
                .param("username", "teacher")
                .param("password", "password123")
                .param("email", "teacher@example.com")
                .param("fullName", "Teacher User")
                .param("role", "TEACHER"))
            .andExpect(status().isFound());

        // Verify teacher role
        User user = userRepository.findByUsername("teacher").orElse(null);
        assertThat(user).isNotNull();
        assertThat(user.getRole()).isEqualTo(User.Role.TEACHER);
    }
}
