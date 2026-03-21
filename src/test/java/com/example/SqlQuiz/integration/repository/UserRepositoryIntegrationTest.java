package com.example.SqlQuiz.integration.repository;

import com.example.SqlQuiz.entity.User;
import com.example.SqlQuiz.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("UserRepository Integration Tests")
public class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // 清理数据
        userRepository.deleteAll();

        // Create test user
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword("encoded_password");
        testUser.setEmail("test@example.com");
        testUser.setFullName("Test User");
        testUser.setRole(User.Role.STUDENT);
        testUser.setEnabled(true);
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("保存用户")
    void save_User() {
        // Arrange
        User newUser = new User();
        newUser.setUsername("newuser");
        newUser.setPassword("password");
        newUser.setEmail("new@example.com");
        newUser.setFullName("New User");
        newUser.setRole(User.Role.TEACHER);
        newUser.setEnabled(true);

        // Act
        User savedUser = userRepository.save(newUser);

        // Assert
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getUsername()).isEqualTo("newuser");
        assertThat(savedUser.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("根据用户名查找用户")
    void findByUsername() {
        // Act
        Optional<User> found = userRepository.findByUsername("testuser");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("根据用户名或邮箱查找用户")
    void findByUsernameOrEmail() {
        // Act - 使用用户名查找
        Optional<User> foundByUsername = userRepository.findByUsernameOrEmail("testuser");

        // Assert
        assertThat(foundByUsername).isPresent();
        assertThat(foundByUsername.get().getUsername()).isEqualTo("testuser");

        // Act - 使用邮箱查找
        Optional<User> foundByEmail = userRepository.findByUsernameOrEmail("test@example.com");

        // Assert
        assertThat(foundByEmail).isPresent();
        assertThat(foundByEmail.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("检查用户名是否存在")
    void existsByUsername() {
        // Assert
        assertThat(userRepository.existsByUsername("testuser")).isTrue();
        assertThat(userRepository.existsByUsername("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("检查邮箱是否存在")
    void existsByEmail() {
        // Assert
        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nonexistent@example.com")).isFalse();
    }

    @Test
    @DisplayName("查找所有学生")
    void findAllByRole() {
        // Arrange - Create more test users
        User teacher = new User();
        teacher.setUsername("teacher");
        teacher.setPassword("password");
        teacher.setEmail("teacher@example.com");
        teacher.setFullName("Teacher");
        teacher.setRole(User.Role.TEACHER);
        teacher.setEnabled(true);
        userRepository.save(teacher);

        User student2 = new User();
        student2.setUsername("student2");
        student2.setPassword("password");
        student2.setEmail("student2@example.com");
        student2.setFullName("Student 2");
        student2.setRole(User.Role.STUDENT);
        student2.setEnabled(true);
        userRepository.save(student2);

        // Act
        List<User> students = userRepository.findAll().stream()
            .filter(u -> u.getRole() == User.Role.STUDENT)
            .toList();

        // Assert
        assertThat(students).hasSize(2);
        assertThat(students).allMatch(u -> u.getRole() == User.Role.STUDENT);
    }

    @Test
    @DisplayName("更新用户信息")
    void updateUser() {
        // Arrange
        testUser.setFullName("Updated Name");

        // Act
        User updated = userRepository.save(testUser);

        // Assert
        assertThat(updated.getFullName()).isEqualTo("Updated Name");
        assertThat(updated.getUpdatedAt()).isNotEqualTo(testUser.getUpdatedAt());
    }

    @Test
    @DisplayName("删除用户")
    void deleteUser() {
        // Act
        userRepository.delete(testUser);

        // Assert
        Optional<User> found = userRepository.findById(testUser.getId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("查找启用的用户")
    void findByEnabled() {
        // Arrange
        User disabledUser = new User();
        disabledUser.setUsername("disabled");
        disabledUser.setPassword("password");
        disabledUser.setEmail("disabled@example.com");
        disabledUser.setFullName("Disabled User");
        disabledUser.setRole(User.Role.STUDENT);
        disabledUser.setEnabled(false);
        userRepository.save(disabledUser);

        // Act
        List<User> enabledUsers = userRepository.findAll().stream()
            .filter(User::getEnabled)
            .toList();

        // Assert
        assertThat(enabledUsers).contains(testUser);
        assertThat(enabledUsers).doesNotContain(disabledUser);
    }

    @Test
    @DisplayName("用户唯一性约束 - 用户名")
    void uniqueConstraint_Username() {
        // Arrange
        User duplicate = new User();
        duplicate.setUsername("testuser"); // Duplicate username
        duplicate.setPassword("password");
        duplicate.setEmail("different@example.com");
        duplicate.setFullName("Duplicate");
        duplicate.setRole(User.Role.STUDENT);
        duplicate.setEnabled(true);

        // Act & Assert - 这将抛出异常或违反约束
        assertThatThrownBy(() -> userRepository.save(duplicate))
            .isInstanceOf(Exception.class);
    }
}
