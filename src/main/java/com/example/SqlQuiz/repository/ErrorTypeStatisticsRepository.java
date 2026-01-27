package com.example.SqlQuiz.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.SqlQuiz.entity.ErrorTypeStatistics;
import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.entity.User;

@Repository
public interface ErrorTypeStatisticsRepository extends JpaRepository<ErrorTypeStatistics, Long> {

    Optional<ErrorTypeStatistics> findByStudentAndQuestionType(User student, Question.QuestionType questionType);

    List<ErrorTypeStatistics> findByStudent(User student);

    List<ErrorTypeStatistics> findByStudentOrderByErrorCountDesc(User student);

    @Query("SELECT e FROM ErrorTypeStatistics e WHERE e.student = :student AND e.isMastered = false ORDER BY e.errorCount DESC")
    List<ErrorTypeStatistics> findUnmasteredByStudent(@Param("student") User student);

    @Query("SELECT e FROM ErrorTypeStatistics e WHERE e.student = :student AND e.accuracy < 0.9 ORDER BY e.errorCount DESC")
    List<ErrorTypeStatistics> findLowAccuracyByStudent(@Param("student") User student);

    @Query("SELECT e FROM ErrorTypeStatistics e WHERE e.student = :student ORDER BY " +
           "(CASE WHEN e.accuracy < 0.5 THEN 0 ELSE 1 END), " +
           "e.errorCount DESC")
    List<ErrorTypeStatistics> findByStudentOrderByPriority(@Param("student") User student);

    @Query("SELECT COUNT(e) FROM ErrorTypeStatistics e WHERE e.student = :student AND e.isMastered = true")
    long countMasteredByStudent(@Param("student") User student);

    @Query("SELECT AVG(e.accuracy) FROM ErrorTypeStatistics e WHERE e.student = :student")
    Double getAverageAccuracyByStudent(@Param("student") User student);
}
