package com.example.SqlQuiz.unit.util;

import com.example.SqlQuiz.entity.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Test data management utility class
 * Used to create various entity objects in tests
 */
@SuppressWarnings("unused")
public class TestDataManager {

    private final PasswordEncoder passwordEncoder;

    public TestDataManager(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 创建测试学生用户
     */
    public User createTestStudent(String username) {
        User student = new User();
        student.setUsername(username);
        student.setPassword(passwordEncoder.encode("password123"));
        student.setEmail(username + "@example.com");
        student.setFullName("Test " + username);
        student.setRole(User.Role.STUDENT);
        student.setEnabled(true);
        return student;
    }

    /**
     * 创建测试教师用户
     */
    public User createTestTeacher(String username) {
        User teacher = new User();
        teacher.setUsername(username);
        teacher.setPassword(passwordEncoder.encode("password123"));
        teacher.setEmail(username + "@example.com");
        teacher.setFullName("Test " + username);
        teacher.setRole(User.Role.TEACHER);
        teacher.setEnabled(true);
        return teacher;
    }

    /**
     * 创建测试测验
     */
    public Quiz createTestQuiz(User teacher, String title) {
        Quiz quiz = new Quiz();
        quiz.setTitle(title);
        quiz.setDescription("Test description for " + title);
        quiz.setTimeLimit(60);
        quiz.setMaxAttempts(3);
        quiz.setTeacher(teacher);
        quiz.setIsActive(true);
        quiz.setStartTime(LocalDateTime.now().minusHours(1));
        quiz.setEndTime(LocalDateTime.now().plusDays(1));
        return quiz;
    }

    /**
     * 创建测试题目
     */
    public Question createTestQuestion(Quiz quiz, int orderIndex) {
        Question question = new Question();
        question.setContent("Test Question " + orderIndex);
        question.setQuestionType(Question.QuestionType.SELECT_BASIC);
        question.setScore(10.0);
        question.setDifficultyLevel(Question.DifficultyLevel.EASY);
        question.setQuiz(quiz);
        question.setOrderIndex(orderIndex);
        question.setDescription("Test description");
        question.setDatabaseContext("Test database context");
        question.setExpectedSql("SELECT * FROM test_table");
        return question;
    }

    /**
     * 创建测试提交记录
     */
    public Submission createTestSubmission(User student, Quiz quiz, int attemptNumber) {
        Submission submission = new Submission(student, quiz, attemptNumber);
        submission.setStatus(Submission.SubmissionStatus.IN_PROGRESS);
        return submission;
    }

    /**
     * 创建测试答题记录
     */
    public QuestionAnswer createQuestionAnswer(Question question, Submission submission) {
        QuestionAnswer answer = new QuestionAnswer(question, submission);
        answer.setStudentSql("SELECT * FROM test");
        return answer;
    }

    /**
     * 创建测试练习会话
     */
    public PracticeSession createPracticeSession(User student) {
        PracticeSession session = new PracticeSession(student);
        session.setStatus(PracticeSession.SessionStatus.IN_PROGRESS);
        return session;
    }

    /**
     * 创建测试练习轮次
     */
    public PracticeRound createPracticeRound(PracticeSession session, int roundNumber) {
        PracticeRound round = new PracticeRound();
        round.setSession(session);
        round.setRoundNumber(roundNumber);
        round.setStatus(PracticeRound.RoundStatus.IN_PROGRESS);
        round.setTotalQuestions(5);
        round.setCorrectCount(0);
        return round;
    }

    /**
     * 创建完整的测试场景
     * 包括教师、学生、测验、题目等
     */
    public TestScenario createCompleteTestScenario() {
        User teacher = createTestTeacher("test_teacher");
        User student = createTestStudent("test_student");

        Quiz quiz = createTestQuiz(teacher, "Test Quiz");

        Question q1 = createTestQuestion(quiz, 1);
        Question q2 = createTestQuestion(quiz, 2);
        q2.setQuestionType(Question.QuestionType.SELECT_JOIN);
        q2.setScore(15.0);

        Question q3 = createTestQuestion(quiz, 3);
        q3.setQuestionType(Question.QuestionType.SELECT_AGGREGATE);
        q3.setDifficultyLevel(Question.DifficultyLevel.MEDIUM);

        return new TestScenario(teacher, student, quiz, List.of(q1, q2, q3));
    }

    /**
     * 测试场景数据容器
     */
    public static class TestScenario {
        private final User teacher;
        private final User student;
        private final Quiz quiz;
        private final List<Question> questions;

        public TestScenario(User teacher, User student, Quiz quiz, List<Question> questions) {
            this.teacher = teacher;
            this.student = student;
            this.quiz = quiz;
            this.questions = questions;
        }

        public User getTeacher() { return teacher; }
        public User getStudent() { return student; }
        public Quiz getQuiz() { return quiz; }
        public List<Question> getQuestions() { return questions; }
    }
}
