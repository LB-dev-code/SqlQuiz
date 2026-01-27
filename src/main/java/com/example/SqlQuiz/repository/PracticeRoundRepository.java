package com.example.SqlQuiz.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.SqlQuiz.entity.PracticeRound;
import com.example.SqlQuiz.entity.PracticeSession;

@Repository
public interface PracticeRoundRepository extends JpaRepository<PracticeRound, Long> {

    List<PracticeRound> findBySession(PracticeSession session);

    List<PracticeRound> findBySessionOrderByRoundNumberDesc(PracticeSession session);

    @Query("SELECT r FROM PracticeRound r WHERE r.session = :session AND r.status = 'IN_PROGRESS'")
    Optional<PracticeRound> findActiveRoundBySession(@Param("session") PracticeSession session);

    @Query("SELECT MAX(r.roundNumber) FROM PracticeRound r WHERE r.session = :session")
    Integer findMaxRoundNumberBySession(@Param("session") PracticeSession session);

    @Query("SELECT COUNT(r) FROM PracticeRound r WHERE r.session = :session AND r.status = 'COMPLETED'")
    long countCompletedRoundsBySession(@Param("session") PracticeSession session);

    @Query("SELECT AVG(r.accuracy) FROM PracticeRound r WHERE r.session = :session AND r.status = 'COMPLETED'")
    Double getAverageAccuracyBySession(@Param("session") PracticeSession session);
}
