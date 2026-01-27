package com.example.SqlQuiz.repository;

import com.example.SqlQuiz.entity.Quiz;
import com.example.SqlQuiz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    
    // 根据教师查找测试
    List<Quiz> findByTeacher(User teacher);
    
    // 根据教师ID查找测试
    List<Quiz> findByTeacherId(Long teacherId);
    
    // 查找所有活跃的测试
    List<Quiz> findByIsActiveTrueOrderByCreatedAtDesc();
    
    // 查找教师创建的活跃测试
    List<Quiz> findByTeacherAndIsActiveTrueOrderByCreatedAtDesc(User teacher);
    
    // 查找当前开放的测试（在时间范围内且活跃）
    @Query("SELECT q FROM Quiz q WHERE q.isActive = true " +
           "AND (q.startTime IS NULL OR q.startTime <= :now) " +
           "AND (q.endTime IS NULL OR q.endTime >= :now) " +
           "ORDER BY q.createdAt DESC")
    List<Quiz> findOpenQuizzes(@Param("now") LocalDateTime now);
    
    // 根据标题模糊查询
    @Query("SELECT q FROM Quiz q WHERE q.title LIKE %:title% ORDER BY q.createdAt DESC")
    List<Quiz> findByTitleContaining(@Param("title") String title);
    
    // 查找教师创建的测试（按创建时间倒序）
    @Query("SELECT q FROM Quiz q WHERE q.teacher = :teacher ORDER BY q.createdAt DESC")
    List<Quiz> findByTeacherOrderByCreatedAtDesc(@Param("teacher") User teacher);
    
    // 统计教师创建的测试数量
    long countByTeacher(User teacher);
    
    // 统计活跃的测试数量
    long countByIsActiveTrue();
    
    // 查找指定时间范围内创建的测试
    @Query("SELECT q FROM Quiz q WHERE q.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY q.createdAt DESC")
    List<Quiz> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                     @Param("endDate") LocalDateTime endDate);
}