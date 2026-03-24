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
    private GLMService glmService;

    @Autowired
    private SetupSqlExecutorService setupSqlExecutorService;

    @Autowired
    private QuizTableMetadataRepository quizTableMetadataRepository;

    @Autowired
    private QuizTableMetadataService tableMetadataService;
    // Create quiz
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

    // Update quiz
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
            throw new RuntimeException("Quiz does not exist");
        }
    }

    // Find quiz by ID
    public Optional<Quiz> findById(Long id) {
        return quizRepository.findById(id);
    }

    // Get all quizzes
    public List<Quiz> findAllQuizzes() {
        return quizRepository.findAll();
    }

    // Get active quizzes
    public List<Quiz> findActiveQuizzes() {
        return quizRepository.findByIsActiveTrueOrderByCreatedAtDesc();
    }

    // Get currently open quizzes
    public List<Quiz> findOpenQuizzes() {
        return quizRepository.findOpenQuizzes(LocalDateTime.now());
    }

    // Find quizzes by teacher
    public List<Quiz> findQuizzesByTeacher(User teacher) {
        return quizRepository.findByTeacherOrderByCreatedAtDesc(teacher);
    }

    // Enable/disable quiz
    public void toggleQuizStatus(Long quizId) {
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);
        if (quizOpt.isPresent()) {
            Quiz quiz = quizOpt.get();
            quiz.setIsActive(!quiz.getIsActive());
            quizRepository.save(quiz);
        } else {
            throw new RuntimeException("Quiz does not exist");
        }
    }

    // Delete quiz (including questions and their tables in testdb)
    @Transactional
    public void deleteQuiz(Long quizId) {
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);
        if (!quizOpt.isPresent()) {
            throw new RuntimeException("Quiz does not exist");
        }

        Quiz quiz = quizOpt.get();
        List<Question> questions = quiz.getQuestions();

        // Delete tables in testdb for each question
        if (questions != null) {
            for (Question question : questions) {
                try {
                    setupSqlExecutorService.dropTablesByQuestionId(question.getId());
                } catch (Exception e) {
                    System.err.println("Failed to drop tables for question " + question.getId() + ": " + e.getMessage());
                }
            }
        }

        // Delete quiz (JPA cascade will delete questions, submissions, and question answers)
        quizRepository.delete(quiz);
    }

    // Add question to quiz
    public Question addQuestionToQuiz(Long quizId, String content, Question.QuestionType questionType,
                                      String description, String databaseContext, String expectedSql,
                                      String testData, String expectedResult, Double score,
                                      Question.DifficultyLevel difficultyLevel) {
        return addQuestionToQuiz(quizId, content, questionType, description, databaseContext,
                expectedSql, null, testData, expectedResult, score, difficultyLevel);
    }

    // Add question to quiz (including setupSql)
    public Question addQuestionToQuiz(Long quizId, String content, Question.QuestionType questionType,
                                      String description, String databaseContext, String expectedSql,
                                      String setupSql, String testData, String expectedResult, Double score,
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
            question.setSetupSql(setupSql);  // Set setupSql
            question.setTestData(testData);
            question.setExpectedResult(expectedResult);
            question.setScore(score);
            question.setDifficultyLevel(difficultyLevel);
            question.setQuiz(quiz);

            // Set question order
            Integer maxOrder = questionRepository.getMaxOrderIndexByQuiz(quiz);
            question.setOrderIndex(maxOrder + 1);

            return questionRepository.save(question);
        } else {
            throw new RuntimeException("Quiz does not exist");
        }
    }

    // Update question
    public Question updateQuestion(Long questionId, String content, Question.QuestionType questionType,
                                   String description, String databaseContext, String expectedSql,
                                   String testData, String expectedResult, Double score,
                                   Question.DifficultyLevel difficultyLevel) {
        return updateQuestion(questionId, content, questionType, description, databaseContext,
                expectedSql, null, testData, expectedResult, score, difficultyLevel);
    }

    // Update question (including setupSql)
    public Question updateQuestion(Long questionId, String content, Question.QuestionType questionType,
                                   String description, String databaseContext, String expectedSql,
                                   String setupSql, String testData, String expectedResult, Double score,
                                   Question.DifficultyLevel difficultyLevel) {
        Optional<Question> questionOpt = questionRepository.findById(questionId);
        if (questionOpt.isPresent()) {
            Question question = questionOpt.get();
            question.setContent(content);
            question.setQuestionType(questionType);
            question.setDescription(description);
            question.setDatabaseContext(databaseContext);
            question.setExpectedSql(expectedSql);
            if (setupSql != null) {
                question.setSetupSql(setupSql);
            }
            question.setTestData(testData);
            question.setExpectedResult(expectedResult);
            question.setScore(score);
            question.setDifficultyLevel(difficultyLevel);

            return questionRepository.save(question);
        } else {
            throw new RuntimeException("Question does not exist");
        }
    }

    // Delete question
    public void deleteQuestion(Long questionId) {
        if (questionRepository.existsById(questionId)) {
            questionRepository.deleteById(questionId);
        } else {
            throw new RuntimeException("Question does not exist");
        }
    }

    // Get all questions for quiz
    public List<Question> getQuestionsByQuiz(Long quizId) {
        return questionRepository.findByQuizIdOrderByOrderIndexAsc(quizId);
    }

    // Get question by ID
    public Optional<Question> getQuestionById(Long questionId) {
        return questionRepository.findById(questionId);
    }

    // Start quiz (student)
    public Submission startQuiz(Long quizId, User student) {
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);
        if (!quizOpt.isPresent()) {
            throw new RuntimeException("Quiz does not exist");
        }

        Quiz quiz = quizOpt.get();

        // Check if quiz is open
        if (!quiz.isOpen()) {
            throw new RuntimeException("Quiz is not currently available");
        }

        // Check if student has in-progress submission
        Optional<Submission> inProgressSubmission = submissionRepository
                .findByStudentAndQuizAndStatus(student, quiz, Submission.SubmissionStatus.IN_PROGRESS);
        if (inProgressSubmission.isPresent()) {
            System.out.println("[StartQuiz] Found existing IN_PROGRESS submission: " + inProgressSubmission.get().getId());
            return inProgressSubmission.get();
        }

        // Check attempt limit
        long attemptCount = submissionRepository.countByStudentAndQuiz(student, quiz);
        if (attemptCount >= quiz.getMaxAttempts()) {
            throw new RuntimeException("Maximum attempt limit reached");
        }

        // Create new submission record
        Submission submission = new Submission(student, quiz, (int)(attemptCount + 1));
        submission = submissionRepository.save(submission);
        System.out.println("[StartQuiz] Created new submission: " + submission.getId());

        // Create question answer records for each question
        List<Question> questions = questionRepository.findByQuizOrderByOrderIndexAsc(quiz);
        System.out.println("[StartQuiz] Creating QuestionAnswer records for " + questions.size() + " questions");
        
        for (Question question : questions) {
            QuestionAnswer questionAnswer = new QuestionAnswer(question, submission);
            questionAnswer = questionAnswerRepository.save(questionAnswer);
            System.out.println("[StartQuiz] Created QuestionAnswer id=" + questionAnswer.getId() + " for question=" + question.getId());
        }

        return submission;
    }

    // Submit answer
    public void submitAnswer(Long submissionId, Long questionId, String sql) {
        System.out.println("[SubmitAnswer] submissionId:" + submissionId + ", questionId:" + questionId);

        Optional<Submission> submissionOpt = submissionRepository.findById(submissionId);
        Optional<Question> questionOpt = questionRepository.findById(questionId);

        if (!submissionOpt.isPresent()) {
            System.err.println("[SubmitAnswer] ERROR: Submission not found: " + submissionId);
            throw new RuntimeException("Submission record does not exist");
        }

        if (!questionOpt.isPresent()) {
            System.err.println("[SubmitAnswer] ERROR: Question not found: " + questionId);
            throw new RuntimeException("Question does not exist");
        }

        Submission submission = submissionOpt.get();
        Question question = questionOpt.get();

        // Check submission status
        if (!submission.isInProgress()) {
            System.err.println("[SubmitAnswer] ERROR: Submission is not IN_PROGRESS, status: " + submission.getStatus());
            throw new RuntimeException("Quiz has ended, cannot submit answer");
        }

        // Find corresponding question answer record
        Optional<QuestionAnswer> qaOpt = questionAnswerRepository
                .findBySubmissionAndQuestion(submission, question);

        if (qaOpt.isPresent()) {
            QuestionAnswer questionAnswer = qaOpt.get();
            System.out.println("[SubmitAnswer] Found QuestionAnswer id=" + questionAnswer.getId() + ", updating...");
            questionAnswer.submitAnswer(sql);
            questionAnswerRepository.save(questionAnswer);
            System.out.println("[SubmitAnswer] Successfully saved answer for QuestionAnswer id=" + questionAnswer.getId());
        } else {
            System.err.println("[SubmitAnswer] ERROR: QuestionAnswer not found for submission=" + submissionId + ", question=" + questionId);

            // List all QuestionAnswers for current submission
            List<QuestionAnswer> allQAs = questionAnswerRepository.findBySubmission(submission);
            System.err.println("[SubmitAnswer] Available QuestionAnswers for this submission:");
            for (QuestionAnswer qa : allQAs) {
                System.err.println("  - QuestionAnswer id=" + qa.getId() + ", questionId=" + qa.getQuestion().getId());
            }

            throw new RuntimeException("Question answer record does not exist");
        }
    }

    // Complete quiz submission
    public Submission submitQuiz(Long submissionId) {
        Optional<Submission> submissionOpt = submissionRepository.findById(submissionId);
        if (submissionOpt.isPresent()) {
            Submission submission = submissionOpt.get();

            if (!submission.isInProgress()) {
                throw new RuntimeException("Quiz has already been submitted");
            }

            submission.submit();
            return submissionRepository.save(submission);
        } else {
            throw new RuntimeException("Submission record does not exist");
        }
    }

    // Get student's quiz records
    public List<Submission> getStudentSubmissions(User student) {
        return submissionRepository.findCompletedSubmissionsByStudent(
                student, Submission.SubmissionStatus.IN_PROGRESS);
    }

    // Get all submission records for quiz (only latest submission per student)
    public List<Submission> getQuizSubmissions(Long quizId) {
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);
        if (quizOpt.isPresent()) {
            return submissionRepository.findLatestSubmissionsByQuiz(quizOpt.get());
        } else {
            throw new RuntimeException("Quiz does not exist");
        }
    }

    // Check if student can take quiz (ignoring time constraints)
    public boolean canStudentTakeQuizIgnoreTime(Long quizId, User student) {
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);
        if (!quizOpt.isPresent()) {
            return false;
        }

        Quiz quiz = quizOpt.get();

        // Check if quiz is active
        if (!quiz.getIsActive()) {
            return false;
        }

        // Check if there's an in-progress submission
        Optional<Submission> inProgressSubmission = submissionRepository
                .findByStudentAndQuizAndStatus(student, quiz, Submission.SubmissionStatus.IN_PROGRESS);
        if (inProgressSubmission.isPresent()) {
            return true; // Can continue in-progress quiz
        }

        // Check attempt limit
        long attemptCount = submissionRepository.countByStudentAndQuiz(student, quiz);
        return attemptCount < quiz.getMaxAttempts();
    }

    // Check if student can take quiz
    public boolean canStudentTakeQuiz(Long quizId, User student) {
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);
        if (!quizOpt.isPresent()) {
            System.out.println("[canStudentTakeQuiz] Quiz not found: " + quizId);
            return false;
        }

        Quiz quiz = quizOpt.get();
        LocalDateTime now = LocalDateTime.now();

        System.out.println("[canStudentTakeQuiz] Checking quiz " + quizId + " for student " + student.getUsername());
        System.out.println("[canStudentTakeQuiz] Current time: " + now);
        System.out.println("[canStudentTakeQuiz] Quiz startTime: " + quiz.getStartTime());
        System.out.println("[canStudentTakeQuiz] Quiz endTime: " + quiz.getEndTime());
        System.out.println("[canStudentTakeQuiz] Quiz isOpen: " + quiz.isOpen());

        // Check if quiz is open (time constraints must always be checked)
        if (!quiz.isOpen()) {
            System.out.println("[canStudentTakeQuiz] BLOCKED: Quiz is not open");
            return false;
        }

        // Check if there's an in-progress submission
        Optional<Submission> inProgressSubmission = submissionRepository
                .findByStudentAndQuizAndStatus(student, quiz, Submission.SubmissionStatus.IN_PROGRESS);
        if (inProgressSubmission.isPresent()) {
            System.out.println("[canStudentTakeQuiz] Found IN_PROGRESS submission: " + inProgressSubmission.get().getId());
            return true; // Can continue in-progress quiz (only if quiz is still open, checked above)
        }

        // Check attempt limit
        long attemptCount = submissionRepository.countByStudentAndQuiz(student, quiz);
        boolean canTake = attemptCount < quiz.getMaxAttempts();
        System.out.println("[canStudentTakeQuiz] Attempt count: " + attemptCount + ", max attempts: " + quiz.getMaxAttempts() + ", can take: " + canTake);
        return canTake;
    }

    // Get quiz statistics
    public QuizStatistics getQuizStatistics(Long quizId) {
        Optional<Quiz> quizOpt = quizRepository.findById(quizId);
        if (!quizOpt.isPresent()) {
            throw new RuntimeException("Quiz does not exist");
        }

        Quiz quiz = quizOpt.get();

        long participantCount = submissionRepository.countDistinctStudentsByQuiz(quiz);
        Double averageScore = submissionRepository.calculateAverageScoreByQuiz(
                quiz, Submission.SubmissionStatus.IN_PROGRESS);

        return new QuizStatistics(participantCount, averageScore != null ? averageScore : 0.0);
    }

    // Inner class: Quiz statistics
    public static class QuizStatistics {
        private long participantCount;
        private double averageScore;

        public QuizStatistics(long participantCount, double averageScore) {
            this.participantCount = participantCount;
            this.averageScore = averageScore;
        }

        // Getters
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
                    // Get table prefix for this question
                    String tablePrefix = null;
                    List<QuizTableMetadata> metadataList = tableMetadataService.getByQuestionId(question.getId());
                    if (!metadataList.isEmpty()) {
                        tablePrefix = metadataList.get(0).getTablePrefix();
                    }

                    // Use new scoring method with result validation
                    score_feedback = glmService.scoreAnswerWithValidation(
                            question.getScore(),
                            question.getDescription(),
                            question.getExpectedSql(),
                            student_answer.getStudentSql(),
                            question.getSetupSql(),
                            tablePrefix
                    );
                    System.out.println(score_feedback);
                try {
                    // Remove possible code block markers
                    String cleanJson = score_feedback.replace("```json", "").replace("```", "").trim();

                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonNode = objectMapper.readTree(cleanJson);

                    // Extract score
                    double aiScore = jsonNode.get("score").asDouble();
                    String feedback = jsonNode.get("feedback").asText();

                    // Save score and feedback to database
                    student_answer.setScore(aiScore);
                    student_answer.setIsCorrect(aiScore == question.getScore());
                    student_answer.setAutoFeedback(feedback);
                    questionAnswerRepository.save(student_answer);
                } catch (Exception e) {
                    System.err.println("Error parsing AI scoring feedback: " + e.getMessage());
                    // 打印原始响应以便调试
                    if (score_feedback != null && !score_feedback.isEmpty()) {
                        System.err.println("Raw AI response (first 500 chars): " + score_feedback.substring(0, Math.min(500, score_feedback.length())));
                        if (score_feedback.length() > 500) {
                            System.err.println("Raw AI response (last 200 chars): ..." + score_feedback.substring(score_feedback.length() - Math.min(200, score_feedback.length())));
                        }
                    }
                    // If parsing fails, give 0 score and log error
                    student_answer.setScore(0.0);
                    student_answer.setIsCorrect(false);
                    student_answer.setAutoFeedback("Score parsing failed: " + e.getMessage());
                    questionAnswerRepository.save(student_answer);
                }
                } // end of else block for null answer check

            }
            if (student_answer.getScore()==question.getScore())    student_answer.setIsCorrect(true);
            else     student_answer.setIsCorrect(false);

        };
        
        // Calculate total score and update submission status after grading
        calculateAndSaveTotalScore(submission);
        
        return score_feedback;
    }
    
    /**
     * Calculate and save total score for submission record
     */
    @Transactional
    public void calculateAndSaveTotalScore(Submission submission) {
        //
        List<QuestionAnswer> questionAnswers = questionAnswerRepository.findBySubmission(submission);
        submission.setQuestionAnswers(questionAnswers);

        // Calculate total score
        submission.calculateScore();

        // Update status to GRADED
        submission.setStatus(Submission.SubmissionStatus.GRADED);

        // Save updated submission record
        submissionRepository.save(submission);
    }

    /**
     * Calculate and save total score by submission ID (for external calls)
     */
    @Transactional
    public void calculateAndSaveTotalScore(Long submissionId) {
        Optional<Submission> submissionOpt = submissionRepository.findById(submissionId);
        submissionOpt.ifPresent(this::calculateAndSaveTotalScore);
    }

    // Get all questions for teacher
    public List<Question> getAllQuestionsByTeacher(User teacher) {
        return questionRepository.findByQuiz_Teacher(teacher);
    }

    // Get all submission records for teacher
    public List<Submission> getAllSubmissionsByTeacher(User teacher) {
        //多级查询
        return submissionRepository.findByQuiz_Teacher(teacher);
    }
}