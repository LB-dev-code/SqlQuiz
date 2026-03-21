package com.example.SqlQuiz.unit.entity;

import com.example.SqlQuiz.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collection;

import static org.assertj.core.api.Assertions.*;

@DisplayName("User Entity Unit Tests")
public class UserEntityTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("encoded_password");
        user.setEmail("test@example.com");
        user.setFullName("Test User");
        user.setRole(User.Role.STUDENT);
        user.setEnabled(true);
    }

    @Test
    @DisplayName("创建教师用户")
    void createTeacherUser() {
        // Arrange
        User teacher = new User("teacher", "password", "teacher@example.com",
            "Teacher Name", User.Role.TEACHER);

        // Assert
        assertThat(teacher.getUsername()).isEqualTo("teacher");
        assertThat(teacher.getRole()).isEqualTo(User.Role.TEACHER);
        assertThat(teacher.getRole().getDisplayName()).isEqualTo("教师");
    }

    @Test
    @DisplayName("创建学生用户")
    void createStudentUser() {
        // Arrange
        User student = new User("student", "password", "student@example.com",
            "Student Name", User.Role.STUDENT);

        // Assert
        assertThat(student.getUsername()).isEqualTo("student");
        assertThat(student.getRole()).isEqualTo(User.Role.STUDENT);
        assertThat(student.getRole().getDisplayName()).isEqualTo("学生");
    }

    @Test
    @DisplayName("UserDetails 实现检查")
    void userDetailsImplementation() {
        // Assert - Verify UserDetails interface methods
        assertThat(user.getUsername()).isEqualTo("testuser");
        assertThat(user.getPassword()).isEqualTo("encoded_password");

        Collection<? extends org.springframework.security.core.GrantedAuthority> authorities =
            user.getAuthorities();
        assertThat(authorities).hasSize(1);
        assertThat(authorities.iterator().next().getAuthority()).isEqualTo("ROLE_STUDENT");

        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("教师权限验证")
    void teacherAuthorities() {
        // Arrange
        user.setRole(User.Role.TEACHER);

        // Act
        Collection<? extends org.springframework.security.core.GrantedAuthority> authorities =
            user.getAuthorities();

        // Assert
        assertThat(authorities).hasSize(1);
        assertThat(authorities.iterator().next().getAuthority()).isEqualTo("ROLE_TEACHER");
    }

    @Test
    @DisplayName("禁用用户账户")
    void disableUserAccount() {
        // Act
        user.setEnabled(false);

        // Assert
        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("toString 方法验证")
    void toStringVerification() {
        // Act
        String result = user.toString();

        // Assert
        assertThat(result).contains("testuser");
        assertThat(result).contains("test@example.com");
        assertThat(result).contains("Test User");
        assertThat(result).contains("STUDENT");
    }

    @Test
    @DisplayName("Getter 和 Setter 方法")
    void gettersAndSetters() {
        // Act & Assert
        user.setId(999L);
        assertThat(user.getId()).isEqualTo(999L);

        user.setUsername("newusername");
        assertThat(user.getUsername()).isEqualTo("newusername");

        user.setPassword("newpassword");
        assertThat(user.getPassword()).isEqualTo("newpassword");

        user.setEmail("new@example.com");
        assertThat(user.getEmail()).isEqualTo("new@example.com");

        user.setFullName("New Full Name");
        assertThat(user.getFullName()).isEqualTo("New Full Name");

        user.setRole(User.Role.TEACHER);
        assertThat(user.getRole()).isEqualTo(User.Role.TEACHER);

        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        assertThat(user.getCreatedAt()).isEqualTo(now);

        user.setUpdatedAt(now);
        assertThat(user.getUpdatedAt()).isEqualTo(now);

        user.setEnabled(false);
        assertThat(user.getEnabled()).isFalse();
    }

    @Test
    @DisplayName("默认构造函数")
    void defaultConstructor() {
        // Act
        User emptyUser = new User();

        // Assert
        assertThat(emptyUser).isNotNull();
        assertThat(emptyUser.getId()).isNull();
        assertThat(emptyUser.getUsername()).isNull();
    }

    @Test
    @DisplayName("角色枚举显示名称")
    void roleDisplayNames() {
        // Assert
        assertThat(User.Role.TEACHER.getDisplayName()).isEqualTo("教师");
        assertThat(User.Role.STUDENT.getDisplayName()).isEqualTo("学生");
    }
}
