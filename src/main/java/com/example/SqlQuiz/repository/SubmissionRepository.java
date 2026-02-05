package com.example.SqlQuiz.repository;

import com.example.SqlQuiz.entity.Submission;
import com.example.SqlQuiz.entity.Quiz;
import com.example.SqlQuiz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    
    // 根据学生查找提交记录
    List<Submission> findByStudent(User student);
    
    // 根据测试查找提交记录
    List<Submission> findByQuiz(Quiz quiz);
    
    // 根据学生和测试查找提交记录
    List<Submission> findByStudentAndQuiz(User student, Quiz quiz);
    
    // 根据学生和测试查找提交记录（按尝试次数排序）
    List<Submission> findByStudentAndQuizOrderByAttemptNumberDesc(User student, Quiz quiz);
    
    // 查找学生在某个测试中的最新提交
    @Query("SELECT s FROM Submission s WHERE s.student = :student AND s.quiz = :quiz " +
           "ORDER BY s.attemptNumber DESC")
    List<Submission> findLatestSubmissionByStudentAndQuiz(@Param("student") User student, 
                                                         @Param("quiz") Quiz quiz);
    
    // 查找学生在某个测试中正在进行的提交
    Optional<Submission> findByStudentAndQuizAndStatus(User student, Quiz quiz, 
                                                      Submission.SubmissionStatus status);
    
    // 统计学生在某个测试中的尝试次数
    long countByStudentAndQuiz(User student, Quiz quiz);
    
    // 查找已完成的提交记录
    List<Submission> findByStatusIn(List<Submission.SubmissionStatus> statuses);
    
    // 根据状态查找提交记录
    List<Submission> findByStatus(Submission.SubmissionStatus status);
    
    // 查找指定时间范围内的提交记录
    @Query("SELECT s FROM Submission s WHERE s.submitTime BETWEEN :startDate AND :endDate " +
           "ORDER BY s.submitTime DESC")
    List<Submission> findBySubmitTimeBetween(@Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate);
    
    // 查找超时的正在进行中的提交
    @Query("SELECT s FROM Submission s JOIN s.quiz q WHERE s.status = :status " +
           "AND q.timeLimit IS NOT NULL " +
           "AND FUNCTION('TIMESTAMPDIFF', MINUTE, s.startTime, :now) > q.timeLimit")
    List<Submission> findOverdueSubmissions(@Param("status") Submission.SubmissionStatus status, 
                                          @Param("now") LocalDateTime now);
    
    // 统计某个测试的参与学生数量
    @Query("SELECT COUNT(DISTINCT s.student) FROM Submission s WHERE s.quiz = :quiz")
    long countDistinctStudentsByQuiz(@Param("quiz") Quiz quiz);
    
    // 计算某个测试的平均分
    @Query("SELECT AVG(s.percentage) FROM Submission s WHERE s.quiz = :quiz AND s.status != :inProgressStatus")
    Double calculateAverageScoreByQuiz(@Param("quiz") Quiz quiz, 
                                     @Param("inProgressStatus") Submission.SubmissionStatus inProgressStatus);
    
    // 查找某个测试的最高分提交
    @Query("SELECT s FROM Submission s WHERE s.quiz = :quiz AND s.status != :inProgressStatus " +
           "ORDER BY s.percentage DESC")
    List<Submission> findTopSubmissionsByQuiz(@Param("quiz") Quiz quiz, 
                                            @Param("inProgressStatus") Submission.SubmissionStatus inProgressStatus);
    
    // 查找学生的所有已完成提交（按时间倒序）
    @Query("SELECT s FROM Submission s WHERE s.student = :student " +
           "AND s.status != :inProgressStatus ORDER BY s.submitTime DESC")
    List<Submission> findCompletedSubmissionsByStudent(@Param("student") User student, 
                                                     @Param("inProgressStatus") Submission.SubmissionStatus inProgressStatus);
    
    // 根据教师查找所有提交记录
    List<Submission> findByQuiz_Teacher(User teacher);

    // 根据ID查找提交记录，并预加载关联对象（student, quiz）
    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.quiz WHERE s.id = :id")
    Optional<Submission> findByIdWithDetails(@Param("id") Long id);
}