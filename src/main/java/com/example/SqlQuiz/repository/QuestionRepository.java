package com.example.SqlQuiz.repository;

import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.entity.Quiz;
import com.example.SqlQuiz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    
    // 根据测试查找题目（按顺序排列）
    List<Question> findByQuizOrderByOrderIndexAsc(Quiz quiz);
    
    // 根据测试ID查找题目（按顺序排列）
    List<Question> findByQuizIdOrderByOrderIndexAsc(Long quizId);
    
    // 根据题目类型查找题目
    List<Question> findByQuestionType(Question.QuestionType questionType);
    
    // 根据难度级别查找题目
    List<Question> findByDifficultyLevel(Question.DifficultyLevel difficultyLevel);
    
    // 根据测试和题目类型查找题目
    List<Question> findByQuizAndQuestionType(Quiz quiz, Question.QuestionType questionType);
    
    // 根据测试和难度级别查找题目
    List<Question> findByQuizAndDifficultyLevel(Quiz quiz, Question.DifficultyLevel difficultyLevel);
    
    // 统计测试中的题目数量
    long countByQuiz(Quiz quiz);
    
    // 统计测试中指定类型的题目数量
    long countByQuizAndQuestionType(Quiz quiz, Question.QuestionType questionType);
    
    // 计算测试的总分
    @Query("SELECT COALESCE(SUM(q.score), 0) FROM Question q WHERE q.quiz = :quiz")
    Double calculateTotalScoreByQuiz(@Param("quiz") Quiz quiz);
    
    // 查找测试中得分最高的题目
    @Query("SELECT q FROM Question q WHERE q.quiz = :quiz ORDER BY q.score DESC")
    List<Question> findByQuizOrderByScoreDesc(@Param("quiz") Quiz quiz);
    
    // 根据内容模糊查询
    @Query("SELECT q FROM Question q WHERE q.content LIKE %:content%")
    List<Question> findByContentContaining(@Param("content") String content);
    
    // 查找指定分数范围的题目
    @Query("SELECT q FROM Question q WHERE q.score BETWEEN :minScore AND :maxScore")
    List<Question> findByScoreBetween(@Param("minScore") Double minScore, @Param("maxScore") Double maxScore);
    
    // 获取测试中的最大顺序号
    @Query("SELECT COALESCE(MAX(q.orderIndex), 0) FROM Question q WHERE q.quiz = :quiz")
    Integer getMaxOrderIndexByQuiz(@Param("quiz") Quiz quiz);
    
    // 根据教师查找所有题目
    List<Question> findByQuiz_Teacher(User teacher);

    // 查找所有题目（用于构建黑名单，避免生成重复题目）
    @Query("SELECT q FROM Question q")
    List<Question> findAllForBlacklist();
}