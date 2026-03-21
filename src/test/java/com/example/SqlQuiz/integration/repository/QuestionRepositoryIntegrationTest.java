package com.example.SqlQuiz.integration.repository;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.repository.QuestionRepository;
import com.example.SqlQuiz.repository.QuizRepository;
import com.example.SqlQuiz.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("QuestionRepository Integration Tests")
public class QuestionRepositoryIntegrationTest {

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private UserRepository userRepository;

    private User testTeacher;
    private Quiz testQuiz;
    private Question testQuestion;

    @BeforeEach
    void setUp() {
        // Clear data
        questionRepository.deleteAll();
        quizRepository.deleteAll();
        userRepository.deleteAll();

        // Create test teacher
        testTeacher = new User();
        testTeacher.setUsername("teacher");
        testTeacher.setPassword("password");
        testTeacher.setEmail("teacher@example.com");
        testTeacher.setFullName("Teacher");
        testTeacher.setRole(User.Role.TEACHER);
        testTeacher.setEnabled(true);
        testTeacher = userRepository.save(testTeacher);

        // Create test quiz
        testQuiz = new Quiz();
        testQuiz.setTitle("Test Quiz");
        testQuiz.setDescription("Test Description");
        testQuiz.setTimeLimit(60);
        testQuiz.setMaxAttempts(3);
        testQuiz.setTeacher(testTeacher);
        testQuiz.setIsActive(true);
        testQuiz = quizRepository.save(testQuiz);

        // Create test question
        testQuestion = new Question();
        testQuestion.setContent("Test Question");
        testQuestion.setQuestionType(Question.QuestionType.SELECT_BASIC);
        testQuestion.setScore(10.0);
        testQuestion.setDifficultyLevel(Question.DifficultyLevel.EASY);
        testQuestion.setQuiz(testQuiz);
        testQuestion.setOrderIndex(1);
        testQuestion = questionRepository.save(testQuestion);
    }

    @Test
    @DisplayName("保存题目")
    void save_Question() {
        // Arrange
        Question newQuestion = new Question();
        newQuestion.setContent("New Question");
        newQuestion.setQuestionType(Question.QuestionType.SELECT_JOIN);
        newQuestion.setScore(15.0);
        newQuestion.setDifficultyLevel(Question.DifficultyLevel.MEDIUM);
        newQuestion.setQuiz(testQuiz);
        newQuestion.setOrderIndex(2);

        // Act
        Question saved = questionRepository.save(newQuestion);

        // Assert
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getContent()).isEqualTo("New Question");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("根据ID查找题目")
    void findById() {
        // Act
        Optional<Question> found = questionRepository.findById(testQuestion.getId());

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getContent()).isEqualTo("Test Question");
    }

    @Test
    @DisplayName("根据测验查找题目并按顺序排序")
    void findByQuizIdOrderByOrderIndexAsc() {
        // Arrange - Create multiple questions
        Question q2 = new Question();
        q2.setContent("Question 2");
        q2.setQuestionType(Question.QuestionType.SELECT_JOIN);
        q2.setScore(10.0);
        q2.setQuiz(testQuiz);
        q2.setOrderIndex(2);
        questionRepository.save(q2);

        Question q3 = new Question();
        q3.setContent("Question 3");
        q3.setQuestionType(Question.QuestionType.SELECT_AGGREGATE);
        q3.setScore(10.0);
        q3.setQuiz(testQuiz);
        q3.setOrderIndex(3);
        questionRepository.save(q3);

        // Act
        List<Question> questions = questionRepository.findByQuizIdOrderByOrderIndexAsc(testQuiz.getId());

        // Assert
        assertThat(questions).hasSize(3);
        assertThat(questions.get(0).getOrderIndex()).isEqualTo(1);
        assertThat(questions.get(1).getOrderIndex()).isEqualTo(2);
        assertThat(questions.get(2).getOrderIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("根据测验和顺序查找题目")
    void findByQuizOrderByOrderIndexAsc() {
        // Arrange
        Question q2 = new Question();
        q2.setContent("Question 2");
        q2.setQuestionType(Question.QuestionType.SELECT_JOIN);
        q2.setScore(10.0);
        q2.setQuiz(testQuiz);
        q2.setOrderIndex(2);
        questionRepository.save(q2);

        // Act
        List<Question> questions = questionRepository.findByQuizOrderByOrderIndexAsc(testQuiz);

        // Assert
        assertThat(questions).hasSize(2);
        assertThat(questions.get(0)).isEqualTo(testQuestion);
        assertThat(questions.get(1)).isEqualTo(q2);
    }

    @Test
    @DisplayName("获取测验中题目的最大顺序号")
    void getMaxOrderIndexByQuiz() {
        // Arrange - Add more questions
        Question q2 = new Question();
        q2.setContent("Question 2");
        q2.setQuestionType(Question.QuestionType.SELECT_JOIN);
        q2.setScore(10.0);
        q2.setQuiz(testQuiz);
        q2.setOrderIndex(5);
        questionRepository.save(q2);

        // Act
        Integer maxOrder = questionRepository.getMaxOrderIndexByQuiz(testQuiz);

        // Assert
        assertThat(maxOrder).isEqualTo(5);
    }

    @Test
    @DisplayName("获取测验中题目的最大顺序号 - 空测验")
    void getMaxOrderIndexByQuiz_EmptyQuiz() {
        // Arrange - Create empty quiz
        Quiz emptyQuiz = new Quiz();
        emptyQuiz.setTitle("Empty Quiz");
        emptyQuiz.setTeacher(testTeacher);
        emptyQuiz.setTimeLimit(60);
        emptyQuiz.setMaxAttempts(3);
        emptyQuiz = quizRepository.save(emptyQuiz);

        // Act
        Integer maxOrder = questionRepository.getMaxOrderIndexByQuiz(emptyQuiz);

        // Assert
        assertThat(maxOrder).isNull();
    }

    @Test
    @DisplayName("根据教师查找题目")
    void findByQuiz_Teacher() {
        // Arrange - Create questions from other teacher
        User otherTeacher = new User();
        otherTeacher.setUsername("other_teacher");
        otherTeacher.setPassword("password");
        otherTeacher.setEmail("other@example.com");
        otherTeacher.setFullName("Other Teacher");
        otherTeacher.setRole(User.Role.TEACHER);
        otherTeacher.setEnabled(true);
        otherTeacher = userRepository.save(otherTeacher);

        Quiz otherQuiz = new Quiz();
        otherQuiz.setTitle("Other Quiz");
        otherQuiz.setTeacher(otherTeacher);
        otherQuiz.setTimeLimit(60);
        otherQuiz.setMaxAttempts(3);
        otherQuiz = quizRepository.save(otherQuiz);

        Question otherQuestion = new Question();
        otherQuestion.setContent("Other Question");
        otherQuestion.setQuestionType(Question.QuestionType.SELECT_BASIC);
        otherQuestion.setScore(10.0);
        otherQuestion.setQuiz(otherQuiz);
        questionRepository.save(otherQuestion);

        // Act
        List<Question> teacherQuestions = questionRepository.findByQuiz_Teacher(testTeacher);

        // Assert
        assertThat(teacherQuestions).contains(testQuestion);
        assertThat(teacherQuestions).doesNotContain(otherQuestion);
    }

    @Test
    @DisplayName("更新题目")
    void updateQuestion() {
        // Arrange
        testQuestion.setContent("Updated Content");
        testQuestion.setScore(15.0);

        // Act
        Question updated = questionRepository.save(testQuestion);

        // Assert
        assertThat(updated.getContent()).isEqualTo("Updated Content");
        assertThat(updated.getScore()).isEqualTo(15.0);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("删除题目")
    void deleteQuestion() {
        // Act
        questionRepository.delete(testQuestion);

        // Assert
        Optional<Question> found = questionRepository.findById(testQuestion.getId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("按题目类型查找")
    void findByQuestionType() {
        // Arrange - Create different question types
        Question joinQuestion = new Question();
        joinQuestion.setContent("Join Question");
        joinQuestion.setQuestionType(Question.QuestionType.SELECT_JOIN);
        joinQuestion.setScore(10.0);
        joinQuestion.setQuiz(testQuiz);
        questionRepository.save(joinQuestion);

        // Act
        List<Question> allQuestions = questionRepository.findAll();
        List<Question> basicQuestions = allQuestions.stream()
            .filter(q -> q.getQuestionType() == Question.QuestionType.SELECT_BASIC)
            .toList();
        List<Question> joinQuestions = allQuestions.stream()
            .filter(q -> q.getQuestionType() == Question.QuestionType.SELECT_JOIN)
            .toList();

        // Assert
        assertThat(basicQuestions).contains(testQuestion);
        assertThat(basicQuestions).doesNotContain(joinQuestion);
        assertThat(joinQuestions).contains(joinQuestion);
        assertThat(joinQuestions).doesNotContain(testQuestion);
    }

    @Test
    @DisplayName("按难度级别查找")
    void findByDifficultyLevel() {
        // Arrange
        Question hardQuestion = new Question();
        hardQuestion.setContent("Hard Question");
        hardQuestion.setQuestionType(Question.QuestionType.SELECT_COMPLEX);
        hardQuestion.setScore(20.0);
        hardQuestion.setDifficultyLevel(Question.DifficultyLevel.HARD);
        hardQuestion.setQuiz(testQuiz);
        questionRepository.save(hardQuestion);

        // Act
        List<Question> allQuestions = questionRepository.findAll();
        List<Question> easyQuestions = allQuestions.stream()
            .filter(q -> q.getDifficultyLevel() == Question.DifficultyLevel.EASY)
            .toList();
        List<Question> hardQuestions = allQuestions.stream()
            .filter(q -> q.getDifficultyLevel() == Question.DifficultyLevel.HARD)
            .toList();

        // Assert
        assertThat(easyQuestions).contains(testQuestion);
        assertThat(easyQuestions).doesNotContain(hardQuestion);
        assertThat(hardQuestions).contains(hardQuestion);
        assertThat(hardQuestions).doesNotContain(testQuestion);
    }

    @Test
    @DisplayName("统计测验的题目数量")
    void countByQuiz() {
        // Arrange
        Question q2 = new Question();
        q2.setContent("Question 2");
        q2.setQuestionType(Question.QuestionType.SELECT_JOIN);
        q2.setScore(10.0);
        q2.setQuiz(testQuiz);
        questionRepository.save(q2);

        // Act
        long count = questionRepository.findAll().stream()
            .filter(q -> q.getQuiz().getId().equals(testQuiz.getId()))
            .count();

        // Assert
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("题目顺序唯一性")
    void uniqueOrderIndexPerQuiz() {
        // Arrange - Create other quiz
        Quiz otherQuiz = new Quiz();
        otherQuiz.setTitle("Other Quiz");
        otherQuiz.setTeacher(testTeacher);
        otherQuiz.setTimeLimit(60);
        otherQuiz.setMaxAttempts(3);
        otherQuiz = quizRepository.save(otherQuiz);

        // Using same orderIndex in other quiz should be OK
        Question otherQuestion = new Question();
        otherQuestion.setContent("Other Question");
        otherQuestion.setQuestionType(Question.QuestionType.SELECT_BASIC);
        otherQuestion.setScore(10.0);
        otherQuestion.setQuiz(otherQuiz);
        otherQuestion.setOrderIndex(1); // 与 testQuestion 相同的 orderIndex
        Question saved = questionRepository.save(otherQuestion);

        // Assert
        assertThat(saved).isNotNull();
        assertThat(saved.getOrderIndex()).isEqualTo(1);
    }
}
