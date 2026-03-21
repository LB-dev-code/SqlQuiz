package com.example.SqlQuiz.unit.service;

import com.example.SqlQuiz.entity.User;
import com.example.SqlQuiz.repository.UserRepository;
import com.example.SqlQuiz.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setFullName("Test User");
        testUser.setRole(User.Role.STUDENT);
    }

    @Test
    @DisplayName("注册新用户 - 成功")
    void registerUser_Success() {
        // Arrange
        String username = "newuser";
        String password = "password123";
        String email = "new@example.com";
        String fullName = "New User";
        User.Role role = User.Role.STUDENT;

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(2L);
            return user;
        });

        // Act
        User result = userService.registerUser(username, password, email, fullName, role);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(username);
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getFullName()).isEqualTo(fullName);
        assertThat(result.getRole()).isEqualTo(role);
        assertThat(result.getEnabled()).isTrue();
        verify(userRepository).existsByUsername(username);
        verify(userRepository).existsByEmail(email);
        verify(passwordEncoder).encode(password);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("注册用户 - 用户名已存在")
    void registerUser_UsernameExists() {
        // Arrange
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() ->
            userService.registerUser("existinguser", "password123", "test@example.com", "Test", User.Role.STUDENT)
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Username already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("注册用户 - 邮箱已存在")
    void registerUser_EmailExists() {
        // Arrange
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() ->
            userService.registerUser("newuser", "password123", "existing@example.com", "Test", User.Role.STUDENT)
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Email already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("根据用户名查找用户 - 成功")
    void findByUsername_Success() {
        // Arrange
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // Act
        Optional<User> result = userService.findByUsername("testuser");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("testuser");
        verify(userRepository).findByUsername("testuser");
    }

    @Test
    @DisplayName("根据用户名查找用户 - 不存在")
    void findByUsername_NotFound() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act
        Optional<User> result = userService.findByUsername("nonexistent");

        // Assert
        assertThat(result).isEmpty();
        verify(userRepository).findByUsername("nonexistent");
    }

    @Test
    @DisplayName("加载用户详情 - 成功")
    void loadUserByUsername_Success() {
        // Arrange
        when(userRepository.findByUsernameOrEmail("testuser")).thenReturn(Optional.of(testUser));

        // Act
        org.springframework.security.core.userdetails.UserDetails result =
            userService.loadUserByUsername("testuser");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getAuthorities()).hasSize(1);
        assertThat(result.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_STUDENT");
        verify(userRepository).findByUsernameOrEmail("testuser");
    }

    @Test
    @DisplayName("加载用户详情 - 用户不存在")
    void loadUserByUsername_NotFound() {
        // Arrange
        when(userRepository.findByUsernameOrEmail("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.loadUserByUsername("nonexistent"))
            .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class)
            .hasMessageContaining("User does not exist");
    }
}
