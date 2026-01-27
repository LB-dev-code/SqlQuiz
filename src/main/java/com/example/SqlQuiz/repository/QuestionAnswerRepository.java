package com.example.SqlQuiz.repository;

import com.example.SqlQuiz.entity.QuestionAnswer;
import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionAnswerRepository extends JpaRepository<QuestionAnswer, Long> {
    
    // 根据提交记录查找答题记录
    List<QuestionAnswer> findBySubmission(Submission submission);
    
    // 根据题目查找答题记录
    List<QuestionAnswer> findByQuestion(Question question);
    
    // 根据提交记录和题目查找答题记录
    Optional<QuestionAnswer> findBySubmissionAndQuestion(Submission submission, Question question);
    
    // 根据提交记录查找答题记录（按题目顺序排序）
    @Query("SELECT qa FROM QuestionAnswer qa JOIN qa.question q " +
           "WHERE qa.submission = :submission ORDER BY q.orderIndex ASC")
    List<QuestionAnswer> findBySubmissionOrderByQuestionOrder(@Param("submission") Submission submission);
    
    // 统计某道题目的答对人数
    @Query("SELECT COUNT(qa) FROM QuestionAnswer qa WHERE qa.question = :question AND qa.isCorrect = true")
    long countCorrectAnswersByQuestion(@Param("question") Question question);
    
    // 统计某道题目的总答题人数
    @Query("SELECT COUNT(qa) FROM QuestionAnswer qa WHERE qa.question = :question AND qa.studentSql IS NOT NULL")
    long countAnswersByQuestion(@Param("question") Question question);
    
    // 计算某道题目的平均得分
    @Query("SELECT AVG(qa.score) FROM QuestionAnswer qa WHERE qa.question = :question AND qa.studentSql IS NOT NULL")
    Double calculateAverageScoreByQuestion(@Param("question") Question question);
    
    // 查找某道题目的所有错误答案
    @Query("SELECT qa FROM QuestionAnswer qa WHERE qa.question = :question AND qa.isCorrect = false " +
           "AND qa.studentSql IS NOT NULL")
    List<QuestionAnswer> findIncorrectAnswersByQuestion(@Param("question") Question question);
    
    // 查找某道题目的所有正确答案
    @Query("SELECT qa FROM QuestionAnswer qa WHERE qa.question = :question AND qa.isCorrect = true")
    List<QuestionAnswer> findCorrectAnswersByQuestion(@Param("question") Question question);
    
    // 查找有SQL执行错误的答题记录
    @Query("SELECT qa FROM QuestionAnswer qa WHERE qa.executionError IS NOT NULL AND qa.executionError != ''")
    List<QuestionAnswer> findAnswersWithExecutionErrors();
    
    // 查找某个提交中未答题的记录
    @Query("SELECT qa FROM QuestionAnswer qa WHERE qa.submission = :submission " +
           "AND (qa.studentSql IS NULL OR qa.studentSql = '')")
    List<QuestionAnswer> findUnansweredBySubmission(@Param("submission") Submission submission);
    
    // 统计某个提交的答对题目数
    @Query("SELECT COUNT(qa) FROM QuestionAnswer qa WHERE qa.submission = :submission AND qa.isCorrect = true")
    long countCorrectAnswersBySubmission(@Param("submission") Submission submission);
    
    // 统计某个提交的已答题目数
    @Query("SELECT COUNT(qa) FROM QuestionAnswer qa WHERE qa.submission = :submission " +
           "AND qa.studentSql IS NOT NULL AND qa.studentSql != ''")
    long countAnsweredQuestionsBySubmission(@Param("submission") Submission submission);
    
    // 查找SQL执行时间最长的答题记录
    @Query("SELECT qa FROM QuestionAnswer qa WHERE qa.executionTimeMs IS NOT NULL " +
           "ORDER BY qa.executionTimeMs DESC")
    List<QuestionAnswer> findAnswersOrderByExecutionTimeDesc();
}