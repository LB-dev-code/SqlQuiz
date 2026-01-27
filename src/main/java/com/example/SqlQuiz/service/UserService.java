package com.example.SqlQuiz.service;

import com.example.SqlQuiz.entity.User;
import com.example.SqlQuiz.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService implements UserDetailsService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    // Spring Security UserDetailsService 实现
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsernameOrEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
    }
    
    // 用户注册
    public User registerUser(String username, String password, String email, String fullName, User.Role role) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("邮箱已存在");
        }
        
        // 创建新用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role);
        user.setEnabled(true);
        
        return userRepository.save(user);
    }
    
    // 用户登录验证
    public Optional<User> authenticateUser(String credential, String password) {
        Optional<User> userOpt = userRepository.findByUsernameOrEmail(credential);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPassword()) && user.isEnabled()) {
                return Optional.of(user);
            }
        }
        
        return Optional.empty();
    }
    
    // 根据ID查找用户
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }
    
    // 根据用户名查找用户
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    // 根据邮箱查找用户
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    // 获取所有用户
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }
    
    // 根据角色查找用户
    public List<User> findUsersByRole(User.Role role) {
        return userRepository.findByRoleAndEnabledTrue(role);
    }
    
    // 获取所有教师
    public List<User> findAllTeachers() {
        return userRepository.findByRoleAndEnabledTrue(User.Role.TEACHER);
    }
    
    // 获取所有学生
    public List<User> findAllStudents() {
        return userRepository.findByRoleAndEnabledTrue(User.Role.STUDENT);
    }
    
    // 更新用户信息
    public User updateUser(User user) {
        return userRepository.save(user);
    }
    
    // 更新用户密码
    public void updatePassword(Long userId, String newPassword) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
        } else {
            throw new RuntimeException("用户不存在");
        }
    }
    
    // 启用/禁用用户
    public void toggleUserStatus(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setEnabled(!user.getEnabled());
            userRepository.save(user);
        } else {
            throw new RuntimeException("用户不存在");
        }
    }
    
    // 删除用户
    public void deleteUser(Long userId) {
        if (userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
        } else {
            throw new RuntimeException("用户不存在");
        }
    }
    
    // 检查用户名是否可用
    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }
    
    // 检查邮箱是否可用
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }
    
    // 根据姓名模糊查询用户
    public List<User> searchUsersByName(String name) {
        return userRepository.findByFullNameContaining(name);
    }
    
    // 统计用户数量
    public long getTotalUserCount() {
        return userRepository.count();
    }
    
    // 统计教师数量
    public long getTeacherCount() {
        return userRepository.countByRoleAndEnabled(User.Role.TEACHER);
    }
    
    // 统计学生数量
    public long getStudentCount() {
        return userRepository.countByRoleAndEnabled(User.Role.STUDENT);
    }
    
    // 验证当前密码
    public boolean validateCurrentPassword(Long userId, String currentPassword) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            return passwordEncoder.matches(currentPassword, user.getPassword());
        }
        return false;
    }
    
    // 更新用户基本信息
    public User updateUserProfile(Long userId, String fullName, String email) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            // 检查邮箱是否被其他用户使用
            if (!user.getEmail().equals(email) && userRepository.existsByEmail(email)) {
                throw new RuntimeException("邮箱已被其他用户使用");
            }
            
            user.setFullName(fullName);
            user.setEmail(email);
            return userRepository.save(user);
        } else {
            throw new RuntimeException("用户不存在");
        }
    }
    
    // 检查用户是否为教师
    public boolean isTeacher(User user) {
        return user != null && user.getRole() == User.Role.TEACHER;
    }
    
    // 检查用户是否为学生
    public boolean isStudent(User user) {
        return user != null && user.getRole() == User.Role.STUDENT;
    }
}