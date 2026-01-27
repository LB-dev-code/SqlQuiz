package com.example.SqlQuiz.repository;

import com.example.SqlQuiz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // 根据用户名查找用户
    Optional<User> findByUsername(String username);
    
    // 根据邮箱查找用户
    Optional<User> findByEmail(String email);
    
    // 根据用户名或邮箱查找用户（用于登录）
    @Query("SELECT u FROM User u WHERE u.username = :credential OR u.email = :credential")
    Optional<User> findByUsernameOrEmail(@Param("credential") String credential);
    
    // 检查用户名是否存在
    boolean existsByUsername(String username);
    
    // 检查邮箱是否存在
    boolean existsByEmail(String email);
    
    // 根据角色查找用户
    List<User> findByRole(User.Role role);
    
    // 查找所有启用的用户
    List<User> findByEnabledTrue();
    
    // 根据角色查找启用的用户
    List<User> findByRoleAndEnabledTrue(User.Role role);
    
    // 根据姓名模糊查询
    @Query("SELECT u FROM User u WHERE u.fullName LIKE %:name%")
    List<User> findByFullNameContaining(@Param("name") String name);
    
    // 统计不同角色的用户数量
    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.enabled = true")
    long countByRoleAndEnabled(@Param("role") User.Role role);
}