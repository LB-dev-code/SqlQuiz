package com.example.SqlQuiz.service;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class QuizService {

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private QuestionAnswerRepository questionAnswerRepository;

    @Autowired
    private  GLMService glmService;
    // 创建测试
    public Quiz createQuiz(String title, String description, Integer timeLimit,
                           Integer maxAttempts, User teacher) throws JsonProcessingException {
        Quiz quiz = new Quiz();
        quiz.setTitle(title);
        quiz.setDescription(description);
        quiz.setTimeLimit(timeLimit);
        quiz.setMaxAttempts(maxAttempts);
        quiz.setTeacher(teacher);
        quiz.setIsActive(true);

        return quizRepository.save(quiz);
    }

    // 更新测试
    public Quiz updateQuiz(Long quizId, String title, String description,
                           Integer timeLimit, Integer maxAttempts,
                           LocalDateTime startTime, LocalDateTime endTime) {
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);
        if (quizOpt.isPresent()) {
            Quiz quiz = quizOpt.get();
            quiz.setTitle(title);
            quiz.setDescription(description);
            quiz.setTimeLimit(timeLimit);
            quiz.setMaxAttempts(maxAttempts);
            quiz.setStartTime(startTime);
            quiz.setEndTime(endTime);

            return quizRepository.save(quiz);
        } else {
            throw new RuntimeException("测试不存在");
        }
    }

    // 根据ID查找测试
    public Optional<Quiz> findById(Long id) {
        return quizRepository.findById(id);
    }

    // 获取所有测试
    public List<Quiz> findAllQuizzes() {
        return quizRepository.findAll();
    }

    // 获取活跃的测试
    public List<Quiz> findActiveQuizzes() {
        return quizRepository.findByIsActiveTrueOrderByCreatedAtDesc();
    }

    // 获取当前开放的测试
    public List<Quiz> findOpenQuizzes() {
        return quizRepository.findOpenQuizzes(LocalDateTime.now());
    }

    // 根据教师查找测试
    public List<Quiz> findQuizzesByTeacher(User teacher) {
        return quizRepository.findByTeacherOrderByCreatedAtDesc(teacher);
    }

    // 启用/禁用测试
    public void toggleQuizStatus(Long quizId) {
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);
        if (quizOpt.isPresent()) {
            Quiz quiz = quizOpt.get();
            quiz.setIsActive(!quiz.getIsActive());
            quizRepository.save(quiz);
        } else {
            throw new RuntimeException("测试不存在");
        }
    }

    // 删除测试
    public void deleteQuiz(Long quizId) {
        if (quizRepository.existsById(quizId)) {
            quizRepository.deleteById(quizId);
        } else {
            throw new RuntimeException("测试不存在");
        }
    }

    // 添加题目到测试
    public Question addQuestionToQuiz(Long quizId, String content, Question.QuestionType questionType,
                                      String description, String databaseContext, String expectedSql,
                                      String testData, String expectedResult, Double score,
                                      Question.DifficultyLevel difficultyLevel) {
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);
        if (quizOpt.isPresent()) {
            Quiz quiz = quizOpt.get();

            Question question = new Question();
            question.setContent(content);
            question.setQuestionType(questionType);
            question.setDescription(description);
            question.setDatabaseContext(databaseContext);
            question.setExpectedSql(expectedSql);
            question.setTestData(testData);
            question.setExpectedResult(expectedResult);
            question.setScore(score);
            question.setDifficultyLevel(difficultyLevel);
            question.setQuiz(quiz);

            // 设置题目顺序
            Integer maxOrder = questionRepository.getMaxOrderIndexByQuiz(quiz);
            question.setOrderIndex(maxOrder + 1);

            return questionRepository.save(question);
        } else {
            throw new RuntimeException("测试不存在");
        }
    }

    // 更新题目
    public Question updateQuestion(Long questionId, String content, Question.QuestionType questionType,
                                   String description, String databaseContext, String expectedSql,
                                   String testData, String expectedResult, Double score,
                                   Question.DifficultyLevel difficultyLevel) {
        Optional<Question> questionOpt = questionRepository.findById(questionId);
        if (questionOpt.isPresent()) {
            Question question = questionOpt.get();
            question.setContent(content);
            question.setQuestionType(questionType);
            question.setDescription(description);
            question.setDatabaseContext(databaseContext);
            question.setExpectedSql(expectedSql);
            question.setTestData(testData);
            question.setExpectedResult(expectedResult);
            question.setScore(score);
            question.setDifficultyLevel(difficultyLevel);

            return questionRepository.save(question);
        } else {
            throw new RuntimeException("题目不存在");
        }
    }

    // 删除题目
    public void deleteQuestion(Long questionId) {
        if (questionRepository.existsById(questionId)) {
            questionRepository.deleteById(questionId);
        } else {
            throw new RuntimeException("题目不存在");
        }
    }

    // 获取测试的所有题目
    public List<Question> getQuestionsByQuiz(Long quizId) {
        return questionRepository.findByQuizIdOrderByOrderIndexAsc(quizId);
    }

    // 根据ID获取题目
    public Optional<Question> getQuestionById(Long questionId) {
        return questionRepository.findById(questionId);
    }

    // 开始测试（学生）
    public Submission startQuiz(Long quizId, User student) {
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);
        if (!quizOpt.isPresent()) {
            throw new RuntimeException("测试不存在");
        }

        Quiz quiz = quizOpt.get();

        // 检查测试是否开放
        if (!quiz.isOpen()) {
            throw new RuntimeException("测试当前不可用");
        }

        // 检查学生是否有正在进行的提交
        Optional<Submission> inProgressSubmission = submissionRepository
                .findByStudentAndQuizAndStatus(student, quiz, Submission.SubmissionStatus.IN_PROGRESS);
        if (inProgressSubmission.isPresent()) {
            return inProgressSubmission.get();
        }

        // 检查尝试次数限制
        long attemptCount = submissionRepository.countByStudentAndQuiz(student, quiz);
        if (attemptCount >= quiz.getMaxAttempts()) {
            throw new RuntimeException("已达到最大尝试次数限制");
        }

        // 创建新的提交记录
        Submission submission = new Submission(student, quiz, (int)(attemptCount + 1));
        submission = submissionRepository.save(submission);

        // 为每道题目创建答题记录
        List<Question> questions = questionRepository.findByQuizOrderByOrderIndexAsc(quiz);
        for (Question question : questions) {
            QuestionAnswer questionAnswer = new QuestionAnswer(question, submission);
            questionAnswerRepository.save(questionAnswer);
        }

        return submission;
    }

    // 提交答案
    public void submitAnswer(Long submissionId, Long questionId, String sql) {
        System.out.println("subID:"+submissionId);
        System.out.println("QSID:"+questionId);
        Optional<Submission> submissionOpt = submissionRepository.findById(submissionId);
        Optional<Question> questionOpt = questionRepository.findById(questionId);

        if (!submissionOpt.isPresent()) {
            throw new RuntimeException("提交记录不存在");
        }

        if (!questionOpt.isPresent()) {
            throw new RuntimeException("题目不存在");
        }

        Submission submission = submissionOpt.get();
        Question question = questionOpt.get();

        // 检查提交状态
        if (!submission.isInProgress()) {
            throw new RuntimeException("测试已结束，无法提交答案");
        }

        // 查找对应的答题记录
        Optional<QuestionAnswer> qaOpt = questionAnswerRepository
                .findBySubmissionAndQuestion(submission, question);

        if (qaOpt.isPresent()) {
            QuestionAnswer questionAnswer = qaOpt.get();
            questionAnswer.submitAnswer(sql);
            questionAnswerRepository.save(questionAnswer);
        } else {
            throw new RuntimeException("答题记录不存在");
        }
    }

    // 完成测试提交
    public Submission submitQuiz(Long submissionId) {
        Optional<Submission> submissionOpt = submissionRepository.findById(submissionId);
        if (submissionOpt.isPresent()) {
            Submission submission = submissionOpt.get();

            if (!submission.isInProgress()) {
                throw new RuntimeException("测试已经提交过了");
            }

            submission.submit();
            return submissionRepository.save(submission);
        } else {
            throw new RuntimeException("提交记录不存在");
        }
    }

    // 获取学生的测试记录
    public List<Submission> getStudentSubmissions(User student) {
        return submissionRepository.findCompletedSubmissionsByStudent(
                student, Submission.SubmissionStatus.IN_PROGRESS);
    }

    // 获取测试的所有提交记录
    public List<Submission> getQuizSubmissions(Long quizId) {
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);
        if (quizOpt.isPresent()) {
            return submissionRepository.findByQuiz(quizOpt.get());
        } else {
            throw new RuntimeException("测试不存在");
        }
    }

    // 检查学生是否可以参加测试
    public boolean canStudentTakeQuiz(Long quizId, User student) {
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);
        if (!quizOpt.isPresent()) {
            return false;
        }

        Quiz quiz = quizOpt.get();

        // 检查测试是否开放
        if (!quiz.isOpen()) {
            return false;
        }

        // 检查是否有正在进行的提交
        Optional<Submission> inProgressSubmission = submissionRepository
                .findByStudentAndQuizAndStatus(student, quiz, Submission.SubmissionStatus.IN_PROGRESS);
        if (inProgressSubmission.isPresent()) {
            return true; // 可以继续进行中的测试
        }

        // 检查尝试次数限制
        long attemptCount = submissionRepository.countByStudentAndQuiz(student, quiz);
        return attemptCount < quiz.getMaxAttempts();
    }

    // 获取测试统计信息
    public QuizStatistics getQuizStatistics(Long quizId) {
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);
        if (!quizOpt.isPresent()) {
            throw new RuntimeException("测试不存在");
        }

        Quiz quiz = quizOpt.get();

        long participantCount = submissionRepository.countDistinctStudentsByQuiz(quiz);
        Double averageScore = submissionRepository.calculateAverageScoreByQuiz(
                quiz, Submission.SubmissionStatus.IN_PROGRESS);

        return new QuizStatistics(participantCount, averageScore != null ? averageScore : 0.0);
    }

    // 内部类：测试统计信息
    public static class QuizStatistics {
        private long participantCount;
        private double averageScore;

        public QuizStatistics(long participantCount, double averageScore) {
            this.participantCount = participantCount;
            this.averageScore = averageScore;
        }

        // getters
        public long getParticipantCount() { return participantCount; }
        public double getAverageScore() { return averageScore; }
    }

    @Transactional
    public String scoreQuiz(Long submission_id ) throws JsonProcessingException {
        String score_feedback = "";
        Optional<Submission> submissionOpt = submissionRepository.findById(submission_id);
        
        if (!submissionOpt.isPresent()) {
            throw new RuntimeException("提交记录不存在");
        }
        
        Submission submission = submissionOpt.get();
        List<QuestionAnswer> student_answers = questionAnswerRepository.findBySubmission(submission);

        for(QuestionAnswer student_answer:student_answers){
            String answer = student_answer.getStudentSql();
            Question question =student_answer.getQuestion();
            String expected_answer = question.getExpectedSql();
            if (answer != null && answer.equals(expected_answer)) {
                student_answer.setScore(question.getScore());

            }
            else {
                // If student answer is null, give 0 score and set feedback
                if (answer == null) {
                    student_answer.setScore(0.0);
                    student_answer.setIsCorrect(false);
                    student_answer.setAutoFeedback("No answer provided");
                    questionAnswerRepository.save(student_answer);
                } else {
              score_feedback = glmService.score_answer(question.getScore(),question.getDescription(),question.getExpectedSql(),student_answer.getStudentSql());
                System.out.println(score_feedback);
                try {
                    // 移除可能的代码块标记
                    String cleanJson = score_feedback.replace("```json", "").replace("```", "").trim();

                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonNode = objectMapper.readTree(cleanJson);

                    // 提取分数
                    double aiScore = jsonNode.get("score").asDouble();
                    String feedback = jsonNode.get("feedback").asText();

                    // 保存分数和反馈到数据库
                    student_answer.setScore(aiScore);
                    student_answer.setIsCorrect(aiScore == question.getScore());
                    student_answer.setAutoFeedback(feedback);
                    questionAnswerRepository.save(student_answer);
                } catch (Exception e) {
                    System.err.println("解析AI评分反馈时出错: " + e.getMessage());
                    // 如果解析失败，给0分并记录错误
                    student_answer.setScore(0.0);
                    student_answer.setIsCorrect(false);
                    student_answer.setAutoFeedback("评分解析失败: " + e.getMessage());
                    questionAnswerRepository.save(student_answer);
                }
                } // end of else block for null answer check

            }
            if (student_answer.getScore()==question.getScore())    student_answer.setIsCorrect(true);
            else     student_answer.setIsCorrect(false);

        };
        
        // 评分完成后计算总分并更新提交状态
        calculateAndSaveTotalScore(submission);
        
        return score_feedback;
    }
    
    /**
     * 计算并保存提交记录的总分
     */
    @Transactional
    public void calculateAndSaveTotalScore(Submission submission) {
        // 重新获取答题记录，确保获取到最新的分数
        List<QuestionAnswer> questionAnswers = questionAnswerRepository.findBySubmission(submission);
        submission.setQuestionAnswers(questionAnswers);
        
        // 计算总分
        submission.calculateScore();
        
        // 更新状态为已评分
        submission.setStatus(Submission.SubmissionStatus.GRADED);
        
        // 保存更新后的提交记录
        submissionRepository.save(submission);
    }
    
    /**
     * 根据提交ID计算并保存总分（对外部调用）
     */
    @Transactional
    public void calculateAndSaveTotalScore(Long submissionId) {
        Optional<Submission> submissionOpt = submissionRepository.findById(submissionId);
        submissionOpt.ifPresent(this::calculateAndSaveTotalScore);
    }

    // 获取教师的所有题目
    public List<Question> getAllQuestionsByTeacher(User teacher) {
        return questionRepository.findByQuiz_Teacher(teacher);
    }

    // 获取教师的所有提交记录
    public List<Submission> getAllSubmissionsByTeacher(User teacher) {
        return submissionRepository.findByQuiz_Teacher(teacher);
    }
}