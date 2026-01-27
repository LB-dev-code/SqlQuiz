package com.example.SqlQuiz.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.SqlQuiz.entity.PracticeAnswer;
import com.example.SqlQuiz.entity.PracticeRound;

@Repository
public interface PracticeAnswerRepository extends JpaRepository<PracticeAnswer, Long> {

    List<PracticeAnswer> findByRound(PracticeRound round);

    List<PracticeAnswer> findByRoundOrderByQuestionIndexAsc(PracticeRound round);

    Optional<PracticeAnswer> findByRoundAndQuestionIndex(PracticeRound round, Integer questionIndex);

    @Query("SELECT a FROM PracticeAnswer a WHERE a.round = :round AND a.isCorrect = false")
    List<PracticeAnswer> findIncorrectByRound(@Param("round") PracticeRound round);

    @Query("SELECT a FROM PracticeAnswer a WHERE a.round = :round AND a.studentSql IS NOT NULL")
    List<PracticeAnswer> findAnsweredByRound(@Param("round") PracticeRound round);

    @Query("SELECT COUNT(a) FROM PracticeAnswer a WHERE a.round = :round AND a.isCorrect = true")
    long countCorrectByRound(@Param("round") PracticeRound round);

    @Query("SELECT a.questionType, COUNT(a), SUM(CASE WHEN a.isCorrect = true THEN 1 ELSE 0 END) " +
           "FROM PracticeAnswer a WHERE a.round.session.student.id = :studentId " +
           "GROUP BY a.questionType")
    List<Object[]> getStatisticsByStudentGroupByType(@Param("studentId") Long studentId);
}
