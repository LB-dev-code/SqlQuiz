package com.example.SqlQuiz.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.SqlQuiz.entity.PracticeSession;
import com.example.SqlQuiz.entity.User;

@Repository
public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    List<PracticeSession> findByStudent(User student);

    List<PracticeSession> findByStudentOrderByStartTimeDesc(User student);

    List<PracticeSession> findByStudentAndStatus(User student, PracticeSession.SessionStatus status);

    @Query("SELECT p FROM PracticeSession p WHERE p.student = :student AND p.status = 'IN_PROGRESS'")
    Optional<PracticeSession> findActiveSessionByStudent(@Param("student") User student);

    @Query("SELECT p FROM PracticeSession p WHERE p.student = :student ORDER BY p.startTime DESC")
    List<PracticeSession> findRecentSessionsByStudent(@Param("student") User student);

    @Query("SELECT COUNT(p) FROM PracticeSession p WHERE p.student = :student AND p.status = 'COMPLETED'")
    long countCompletedSessionsByStudent(@Param("student") User student);

    @Query("SELECT AVG(p.overallAccuracy) FROM PracticeSession p WHERE p.student = :student AND p.status = 'COMPLETED'")
    Double getAverageAccuracyByStudent(@Param("student") User student);
}
