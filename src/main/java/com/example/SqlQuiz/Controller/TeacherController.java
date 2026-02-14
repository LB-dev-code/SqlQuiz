package com.example.SqlQuiz.Controller;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.repository.QuestionAnswerRepository;
import com.example.SqlQuiz.repository.SubmissionRepository;
import com.example.SqlQuiz.service.GLMService;
import com.example.SqlQuiz.service.QuizService;
import com.example.SqlQuiz.service.SetupSqlExecutorService;
import com.example.SqlQuiz.service.SqlValidationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.ArrayList;
import java.util.zip.DeflaterOutputStream;

@Controller
@RequestMapping("/teacher")
public class TeacherController {

    @Autowired
    private QuizService quizService;

    @Autowired
    private SqlValidationService sqlValidationService;

    @Autowired
    private GLMService glmService;

    @Autowired
    private QuestionAnswerRepository questionAnswerRepository;

    @Autowired
    private SubmissionRepository submissionRepository;
    
    @Autowired
    private SetupSqlExecutorService setupSqlExecutorService;

    @Autowired
    private com.example.SqlQuiz.service.QuizTableMetadataService tableMetadataService;

    @Autowired
    private com.example.SqlQuiz.service.SandboxDatabaseService sandboxService;

    // Teacher dashboard
    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        List<Quiz> quizzes = quizService.findQuizzesByTeacher(teacher);

        // Count total questions
        int totalQuestions = 0;
        for (Quiz quiz : quizzes) {
            totalQuestions += quiz.getQuestions().size();
        }

        // Count student submissions
        List<Submission> submissions = quizService.getAllSubmissionsByTeacher(teacher);
        int totalSubmissions = submissions.size();

        model.addAttribute("quizzes", quizzes);
        model.addAttribute("teacher", teacher);
        model.addAttribute("totalQuestions", totalQuestions);
        model.addAttribute("totalSubmissions", totalSubmissions);
        return "teacher/dashboard";
    }

    // Quiz management page
    @GetMapping("/quizzes")
    public String quizManagement(Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        List<Quiz> quizzes = quizService.findQuizzesByTeacher(teacher);

        // Count total questions
        int totalQuestions = 0;
        for (Quiz quiz : quizzes) {
            totalQuestions += quiz.getQuestions().size();
        }

        // Count student submissions
        List<Submission> submissions = quizService.getAllSubmissionsByTeacher(teacher);
        int totalSubmissions = submissions.size();

        // Calculate average completion rate
        double avgCompletionRate = 0.0;
        if (!submissions.isEmpty()) {
            long completedCount = submissions.stream()
                .mapToLong(s -> s.getPercentage() != null && s.getPercentage() >= 100.0 ? 1 : 0)
                .sum();
            avgCompletionRate = (double) completedCount / submissions.size();
        }

        model.addAttribute("quizzes", quizzes);
        model.addAttribute("teacher", teacher);
        model.addAttribute("totalQuestions", totalQuestions);
        model.addAttribute("totalSubmissions", totalSubmissions);
        model.addAttribute("avgCompletionRate", avgCompletionRate);
        System.out.println("GET request completed");
        return "teacher/quiz-list";
    }

     @GetMapping("/quiz/create")
        public String createQuizPage(Model model, Authentication auth) {
            // Add debug info
            User teacher = (User) auth.getPrincipal();
            model.addAttribute("teacher", teacher);
            model.addAttribute("quiz", new Quiz());
            return "teacher/quiz-create";
        }


    @PostMapping("/quiz/create")
    public String createQuiz(@RequestParam(required = false) String title,
                             @RequestParam(required = false) String description,
                             @RequestParam(required = false) Integer timeLimit,
                             @RequestParam(required = false) Integer maxAttempts,
                             @RequestParam(required = false) String startTime,
                             @RequestParam(required = false) String endTime,
                             @RequestParam(required = false) Boolean isActive,
                             Authentication auth,
                             RedirectAttributes redirectAttributes) {
        // Validate required fields
        List<String> errors = new ArrayList<>();

        if (title == null || title.trim().isEmpty()) {
            errors.add("Module Designation (Title) is required");
        }
        if (timeLimit == null) {
            errors.add("Time Limit (Minutes) is required");
        }
        if (maxAttempts == null) {
            errors.add("Max Attempts is required");
        }
        if (startTime == null || startTime.trim().isEmpty()) {
            errors.add("Activation Date is required");
        }
        if (endTime == null || endTime.trim().isEmpty()) {
            errors.add("Termination Date is required");
        }

        if (!errors.isEmpty()) {
            redirectAttributes.addFlashAttribute("errors", errors);
            redirectAttributes.addFlashAttribute("inputData", Map.of(
                "title", title != null ? title : "",
                "description", description != null ? description : "",
                "timeLimit", timeLimit != null ? timeLimit.toString() : "",
                "maxAttempts", maxAttempts != null ? maxAttempts.toString() : "",
                "startTime", startTime != null ? startTime : "",
                "endTime", endTime != null ? endTime : "",
                "isActive", isActive != null ? isActive : false
            ));
            return "redirect:/teacher/quiz/create";
        }

        try {
            User teacher = (User) auth.getPrincipal();

            // Parse start time and end time
            LocalDateTime startDateTime = null;
            LocalDateTime endDateTime = null;

            if (startTime != null && !startTime.isEmpty()) {
                try {
                    // Parse HTML datetime-local format (yyyy-MM-ddTHH:mm)
                    startDateTime = LocalDateTime.parse(startTime);
                } catch (Exception e) {
                    System.err.println("Failed to parse start time: " + e.getMessage());
                }
            }

            if (endTime != null && !endTime.isEmpty()) {
                try {
                    // Parse HTML datetime-local format (yyyy-MM-ddTHH:mm)
                    endDateTime = LocalDateTime.parse(endTime);
                } catch (Exception e) {
                    System.err.println("Failed to parse end time: " + e.getMessage());
                }
            }

            // Create quiz
            Quiz quiz = quizService.createQuiz(title, description, timeLimit, maxAttempts, teacher);

            // Update quiz start time and end time
            quizService.updateQuiz(quiz.getId(), title, description, timeLimit, maxAttempts, startDateTime, endDateTime);

            redirectAttributes.addFlashAttribute("message", "Quiz created successfully!");
            return "redirect:/teacher/quiz/" + quiz.getId() + "/questions";
        } catch (Exception e) {
            e.printStackTrace();
            errors.add("Failed to create quiz: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errors", errors);
            System.out.println("POST request completed");
            return "redirect:/teacher/quiz/create";
        }
    }
    // Question management page
    @GetMapping("/quiz/{id}/questions")
    public String questionManagement(@PathVariable Long id, Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        Quiz quiz = quizService.findById(id).orElse(null);

        if (quiz == null || !quiz.getTeacher().getId().equals(teacher.getId())) {
            return "redirect:/teacher/quizzes";
        }

        List<Question> questions = quizService.getQuestionsByQuiz(id);
        model.addAttribute("quiz", quiz);
        model.addAttribute("questions", questions);
        model.addAttribute("questionTypes", Question.QuestionType.values());
        model.addAttribute("difficultyLevels", Question.DifficultyLevel.values());
        model.addAttribute("teacher", teacher);

        return "teacher/question-list";
    }

    // Edit question page
    @GetMapping("/question/{questionId}/edit")
    @ResponseBody
    public Question editQuestionPage(@PathVariable Long questionId, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        Optional<Question> questionOpt = quizService.getQuestionById(questionId);

        if (questionOpt.isPresent()) {
            Question question = questionOpt.get();
            // Check permission: ensure it's the creator of the quiz this question belongs to
            if (question.getQuiz().getTeacher().getId().equals(teacher.getId())) {
                return question;
            }
        }
        return null;
    }

    // Process edit question
    @PostMapping("/question/{questionId}/edit")
    public String updateQuestion(@PathVariable Long questionId,
                                 @RequestParam String content,
                                 @RequestParam(required = false) String description,
                                 @RequestParam(required = false) String expectedSql,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        try {
            User teacher = (User) auth.getPrincipal();
            Optional<Question> questionOpt = quizService.getQuestionById(questionId);

            if (!questionOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Question does not exist");
                return "redirect:/teacher/quizzes";
            }

            Question question = questionOpt.get();
            Long quizId = question.getQuiz().getId();

            // Check permission
            if (!question.getQuiz().getTeacher().getId().equals(teacher.getId())) {
                redirectAttributes.addFlashAttribute("error", "No permission to operate this question");
                return "redirect:/teacher/quizzes";
            }

            // Update question
            quizService.updateQuestion(questionId, content, question.getQuestionType(),
                    description, question.getDatabaseContext(), expectedSql,
                    question.getTestData(), question.getExpectedResult(),
                    question.getScore(), question.getDifficultyLevel());

            redirectAttributes.addFlashAttribute("message", "Question updated successfully!");
            return "redirect:/teacher/quiz/" + quizId + "/questions";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to update question: " + e.getMessage());
            return "redirect:/teacher/quizzes";
        }
    }

    // Delete question
    @PostMapping("/question/{questionId}/delete")
    public String deleteQuestion(@PathVariable Long questionId,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        try {
            User teacher = (User) auth.getPrincipal();
            Optional<Question> questionOpt = quizService.getQuestionById(questionId);

            if (!questionOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Question does not exist");
                return "redirect:/teacher/quizzes";
            }

            Question question = questionOpt.get();
            Long quizId = question.getQuiz().getId();

            // Check permission
            if (!question.getQuiz().getTeacher().getId().equals(teacher.getId())) {
                redirectAttributes.addFlashAttribute("error", "No permission to operate this question");
                return "redirect:/teacher/quizzes";
            }

            // Delete question
            quizService.deleteQuestion(questionId);

            // Delete corresponding tables in testdb
            setupSqlExecutorService.dropTablesByQuestionId(questionId);

            redirectAttributes.addFlashAttribute("message", "Question deleted successfully!");
            return "redirect:/teacher/quiz/" + quizId + "/questions";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to delete question: " + e.getMessage());
            return "redirect:/teacher/quizzes";
        }
    }

    // Quiz statistics page
    @GetMapping("/quiz/{id}/statistics")
    public String quizStatistics(@PathVariable Long id, Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        Quiz quiz = quizService.findById(id).orElse(null);

        if (quiz == null || !quiz.getTeacher().getId().equals(teacher.getId())) {
            return "redirect:/teacher/quizzes";
        }

        List<Submission> allSubmissions = quizService.getQuizSubmissions(id);

        // Group by student, keep only latest submission for each student
        Map<Long, Submission> latestSubmissionsByStudent = new HashMap<>();
        for (Submission submission : allSubmissions) {
            Long studentId = submission.getStudent().getId();
            Submission currentLatest = latestSubmissionsByStudent.get(studentId);

            // If current student has no latest submission record, or current submission is newer than existing record, update
            if (currentLatest == null ||
                (submission.getSubmitTime() != null && currentLatest.getSubmitTime() != null &&
                 submission.getSubmitTime().isAfter(currentLatest.getSubmitTime())) ||
                (submission.getSubmitTime() != null && currentLatest.getSubmitTime() == null)) {
                latestSubmissionsByStudent.put(studentId, submission);
            }
        }

        List<Submission> submissions = new ArrayList<>(latestSubmissionsByStudent.values());
        model.addAttribute("quiz", quiz);
        model.addAttribute("submissions", submissions);
        model.addAttribute("teacher", teacher);

        return "teacher/quiz-statistics";
    }

    // Generic question list page
    @GetMapping("/question-list")
    public String questionList(Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        List<Question> questions = quizService.getAllQuestionsByTeacher(teacher);
        model.addAttribute("questions", questions);
        model.addAttribute("questionTypes", Question.QuestionType.values());
        model.addAttribute("difficultyLevels", Question.DifficultyLevel.values());
        model.addAttribute("teacher", teacher);

        return "teacher/question-list";
    }

    // Generic question creation page
    @GetMapping("/question-create")
    public String questionCreate(Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        List<Quiz> quizzes = quizService.findQuizzesByTeacher(teacher);
        model.addAttribute("quizzes", quizzes);
        model.addAttribute("questionTypes", Question.QuestionType.values());
        model.addAttribute("difficultyLevels", Question.DifficultyLevel.values());
        model.addAttribute("teacher", teacher);

        return "teacher/question-create";
    }

    // Process generic question creation
    @PostMapping("/question-create")
    public String questionCreate(@RequestParam String content,
                                  @RequestParam(required = false) String questionType,
                                  @RequestParam(required = false) String description,
                                  @RequestParam(required = false) String expectedSql,
                                  @RequestParam Double score,
                                  @RequestParam(required = false) String difficultyLevel,
                                  @RequestParam Long quizId,
                                  Authentication auth,
                                  RedirectAttributes redirectAttributes) {
        try {
            User teacher = (User) auth.getPrincipal();
            Quiz quiz = quizService.findById(quizId).orElse(null);

            if (quiz == null || !quiz.getTeacher().getId().equals(teacher.getId())) {
                redirectAttributes.addFlashAttribute("error", "No permission to operate this quiz");
                return "redirect:/teacher/question-create";
            }

            // Handle optional enum types
            Question.QuestionType qType = null;
            if (questionType != null && !questionType.trim().isEmpty()) {
                qType = Question.QuestionType.valueOf(questionType);
            }

            Question.DifficultyLevel difficulty = null;
            if (difficultyLevel != null && !difficultyLevel.trim().isEmpty()) {
                difficulty = Question.DifficultyLevel.valueOf(difficultyLevel);
            }

            // Create question
            quizService.addQuestionToQuiz(quizId, content, qType, description,
                    null, expectedSql, null, null, score, difficulty);

            redirectAttributes.addFlashAttribute("message", "Question added successfully!");
            return "redirect:/teacher/question-list";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to add question: " + e.getMessage());
            return "redirect:/teacher/question-create";
        }
    }

    // Generic statistics page
    @GetMapping("/quiz-statistics")
    public String quizStatistics(Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        List<Quiz> quizzes = quizService.findQuizzesByTeacher(teacher);
        List<Submission> allSubmissions = quizService.getAllSubmissionsByTeacher(teacher);

        model.addAttribute("quizzes", quizzes);
        model.addAttribute("submissions", allSubmissions);
        model.addAttribute("teacher", teacher);

        return "teacher/quiz-statistics";
    }

    // Generic submission detail page
    @GetMapping("/submission-detail")
    @Transactional(readOnly = true)
    public String submissionDetail(@RequestParam(required = false) Long submission_id,
                                   @RequestParam(required = false) Long quiz_id,
                                   Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();

        if (submission_id != null) {
            // Show specific submission detail - use preloaded query to avoid lazy loading issues
            Optional<Submission> submissionOpt = submissionRepository.findByIdWithDetails(submission_id);
            if (!submissionOpt.isPresent()) {
                return "redirect:/teacher/quiz-statistics";
            }

            Submission submission = submissionOpt.get();
            if (!submission.getQuiz().getTeacher().getId().equals(teacher.getId())) {
                return "redirect:/teacher/quiz-statistics";
            }

            List<QuestionAnswer> questionAnswers = questionAnswerRepository.findBySubmission(submission);
            model.addAttribute("submission", submission);
            model.addAttribute("questionAnswers", questionAnswers);
        } else if (quiz_id != null) {
            // Show all submissions for specific quiz
            Quiz quiz = quizService.findById(quiz_id).orElse(null);
            if (quiz == null || !quiz.getTeacher().getId().equals(teacher.getId())) {
                return "redirect:/teacher/quiz-statistics";
            }

            List<Submission> submissions = quizService.getQuizSubmissions(quiz_id);
            model.addAttribute("quiz", quiz);
            model.addAttribute("submissions", submissions);
        } else {
            // Show all submissions
            List<Submission> allSubmissions = quizService.getAllSubmissionsByTeacher(teacher);
            model.addAttribute("submissions", allSubmissions);
        }

        model.addAttribute("teacher", teacher);
        return "teacher/submission-detail";
    }

    // SQL test page
    @GetMapping("/sql-test")
    public String sqlTestPage(Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        model.addAttribute("teacher", teacher);
        return "teacher/sql-test";
    }

    // Test SQL execution
    @PostMapping("/sql-test")
    @ResponseBody
    public Map<String, Object> testSql(@RequestParam String sql, Authentication auth) {
        Map<String, Object> response = new HashMap<>();

        if (sql == null || sql.trim().isEmpty()) {
            response.put("success", false);
            response.put("error", "SQL statement cannot be empty");
            return response;
        }

        User teacher = (User) auth.getPrincipal();
        com.example.SqlQuiz.entity.SandboxContext sandbox = null;

        try {
            // Create teacher test-specific sandbox
            sandbox = sandboxService.createTeacherTestSandbox(teacher.getId());

            // Execute SQL in sandbox
            com.example.SqlQuiz.service.SandboxDatabaseService.SqlExecutionResult result =
                sandboxService.executeInSandbox(sandbox, sql);

            response.put("success", result.isSuccess());
            response.put("data", result.getResultData());
            response.put("rowCount", result.getRowCount());
            response.put("executionTimeMs", result.getExecutionTimeMs());
            response.put("error", result.getErrorMessage());

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "SQL execution error: " + e.getMessage());
        } finally {
            // Cleanup sandbox
            if (sandbox != null) {
                try {
                    sandboxService.closeConnection(sandbox);
                    sandboxService.cleanupSandbox(sandbox.getDatabaseName());
                } catch (Exception e) {
                    System.err.println("Failed to cleanup teacher test sandbox: " + e.getMessage());
                }
            }
        }

        return response;
    }

    // View student submission detail page
    @GetMapping("/quiz/statistics/submission/{submission_id}")
    public String submissionDetail(@PathVariable Long submission_id, Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();

        // Get submission record
        Optional<Submission> submissionOpt = submissionRepository.findById(submission_id);
        if (!submissionOpt.isPresent()) {
            return "redirect:/teacher/quizzes";
        }

        Submission submission = submissionOpt.get();

        // Verify permission: ensure it's the creator of the quiz
        if (!submission.getQuiz().getTeacher().getId().equals(teacher.getId())) {
            return "redirect:/teacher/quizzes";
        }

        // Get question answer records
        List<QuestionAnswer> questionAnswers = questionAnswerRepository.findBySubmission(submission);

        model.addAttribute("submission", submission);
        model.addAttribute("questionAnswers", questionAnswers);
        model.addAttribute("teacher", teacher);

        return "teacher/submission-detail";
    }

    // AI intelligent scoring
    @PostMapping("/quiz/statistics/submission/{submission_id}")
    public String scoreSubmissions(@PathVariable Long submission_id, Model model, Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            User teacher = (User) auth.getPrincipal();

            // Verify permission
            Optional<Submission> submissionOpt = submissionRepository.findById(submission_id);
            if (!submissionOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Submission record does not exist");
                return "redirect:/teacher/quizzes";
            }

            Submission submission = submissionOpt.get();
            if (!submission.getQuiz().getTeacher().getId().equals(teacher.getId())) {
                redirectAttributes.addFlashAttribute("error", "No permission to operate this submission record");
                return "redirect:/teacher/quizzes";
            }

            // Call AI scoring
            String feedback = quizService.scoreQuiz(submission_id);
            System.out.println("AI scoring feedback: " + feedback);

            // Parse JSON returned by AI, extract only feedback field
            String feedbackContent = "";
            try {
                // Remove possible ``json```` format
                String cleanJson = feedback.replace("```json", "").replace("```", "").trim();

                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(cleanJson);

                // Extract feedback field
                if (jsonNode.has("feedback")) {
                    feedbackContent = jsonNode.get("feedback").asText();
                } else {
                    feedbackContent = feedback; // If no feedback field, use original feedback
                }
            } catch (Exception e) {
                System.err.println("Error parsing AI scoring feedback: " + e.getMessage());
                feedbackContent = feedback; // If parsing fails, use original feedback
            }

            // Re-fetch submission record and question answer records to ensure latest score and total score
            submissionOpt = submissionRepository.findById(submission_id);
            if (submissionOpt.isPresent()) {
                submission = submissionOpt.get();
                List<QuestionAnswer> questionAnswers = questionAnswerRepository.findBySubmission(submission);
                model.addAttribute("submission", submission);
                model.addAttribute("questionAnswers", questionAnswers);
            }

            // Get calculated total score info for confirming scoring result
            if (submissionOpt.isPresent()) {
                Submission updatedSubmission = submissionOpt.get();
                System.out.println("AI scoring completed - Total score: " + updatedSubmission.getTotalScore() +
                                 ", Max score: " + updatedSubmission.getMaxScore() +
                                 ", Percentage: " + updatedSubmission.getPercentage() + "%");
            }

            redirectAttributes.addFlashAttribute("message", "AI scoring completed! Total score updated");
            redirectAttributes.addFlashAttribute("feedback", feedbackContent);

            return "redirect:/teacher/quiz/statistics/submission/" + submission_id;

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "AI scoring failed: " + e.getMessage());
            return "redirect:/teacher/quiz/statistics/submission/" + submission_id;
        }
    }

    // Batch AI scoring
    @PostMapping("/quiz/{id}/score-all")
    @ResponseBody
    @Transactional
    public Map<String, Object> scoreAllSubmissions(@PathVariable Long id, Authentication auth) {
        Map<String, Object> response = new HashMap<>();
        try {
            User teacher = (User) auth.getPrincipal();
            Quiz quiz = quizService.findById(id).orElse(null);

            if (quiz == null) {
                response.put("success", false);
                response.put("error", "Quiz does not exist");
                return response;
            }

            // Check permission
            if (!quiz.getTeacher().getId().equals(teacher.getId())) {
                response.put("success", false);
                response.put("error", "No permission to operate this quiz");
                return response;
            }

            // Get all submission records for this quiz
            List<Submission> submissions = quizService.getQuizSubmissions(id);

            // Group by student, keep only latest submission for each student
            Map<Long, Submission> latestSubmissionsByStudent = new HashMap<>();
            for (Submission submission : submissions) {
                Long studentId = submission.getStudent().getId();
                Submission currentLatest = latestSubmissionsByStudent.get(studentId);

                // If current student has no latest submission, or current submission is newer than existing, update
                if (currentLatest == null ||
                    (submission.getSubmitTime() != null && currentLatest.getSubmitTime() != null &&
                     submission.getSubmitTime().isAfter(currentLatest.getSubmitTime())) ||
                    (submission.getSubmitTime() != null && currentLatest.getSubmitTime() == null)) {
                    latestSubmissionsByStudent.put(studentId, submission);
                }
            }

            List<Submission> latestSubmissions = new ArrayList<>(latestSubmissionsByStudent.values());
            int scoredCount = 0;
            List<String> errors = new ArrayList<>();
            int totalSubmissions = latestSubmissions.size();

            System.out.println("Starting batch scoring, found " + submissions.size() + " submission records, of which " + totalSubmissions + " are the latest submissions for each student");

            // Perform AI scoring on each student's latest submission
            for (Submission submission : latestSubmissions) {
                try {
                    System.out.println("Checking submission ID: " + submission.getId() +
                                     ", Status: " + submission.getStatus() +
                                     ", Total score: " + submission.getTotalScore());

                    // Re-fetch latest submission status from database
                    Optional<Submission> freshSubmissionOpt = submissionRepository.findById(submission.getId());
                    if (!freshSubmissionOpt.isPresent()) {
                        errors.add("Submission record " + submission.getId() + ": Cannot find record");
                        continue;
                    }

                    Submission freshSubmission = freshSubmissionOpt.get();

                    // Get latest question answer records
                    List<QuestionAnswer> questionAnswers = questionAnswerRepository.findBySubmission(freshSubmission);
                    freshSubmission.setQuestionAnswers(questionAnswers);

                    // Only score submitted but ungraded submissions
                    boolean needsScoring = (freshSubmission.getStatus().name().equals("SUBMITTED") ||
                                           freshSubmission.getStatus().name().equals("AUTO_SUBMITTED")) &&
                                          (freshSubmission.getTotalScore() == null ||
                                           freshSubmission.getTotalScore() <= 0);

                    System.out.println("Submission " + freshSubmission.getId() + " needs scoring: " + needsScoring);

                    if (needsScoring) {
                        System.out.println("Starting AI scoring for submission " + freshSubmission.getId());

                        // Directly call AI scoring to ensure each submission is processed independently
                        String feedback = quizService.scoreQuiz(freshSubmission.getId());
                        System.out.println("AI scoring completed, feedback length: " + (feedback != null ? feedback.length() : 0));

                        scoredCount++;
                        System.out.println("Successfully scored submission record " + freshSubmission.getId());

                        // Verify scoring result
                        Optional<Submission> updatedSubmissionOpt = submissionRepository.findById(freshSubmission.getId());
                        if (updatedSubmissionOpt.isPresent()) {
                            Submission updated = updatedSubmissionOpt.get();
                            System.out.println("After scoring - Status: " + updated.getStatus() +
                                             ", Total score: " + updated.getTotalScore() +
                                             ", Percentage: " + updated.getPercentage());
                        }
                    } else {
                        System.out.println("Skipping submission record " + freshSubmission.getId() + " (Status: " +
                                         freshSubmission.getStatus() + ", Score: " +
                                         freshSubmission.getTotalScore() + ")");
                    }
                } catch (Exception e) {
                    System.err.println("Failed to score submission record, ID: " + submission.getId() + ", Error: " + e.getMessage());
                    e.printStackTrace();
                    errors.add("Submission record " + submission.getId() + ": " + e.getMessage());
                }
            }

            System.out.println("Batch scoring completed, successfully scored: " + scoredCount + ", failed: " + errors.size());

            response.put("success", true);
            response.put("count", scoredCount);
            response.put("total", totalSubmissions);
            response.put("errors", errors.size());
            if (!errors.isEmpty()) {
                response.put("errorDetails", errors);
            }

            return response;

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("error", "Batch scoring failed: " + e.getMessage());
            return response;
        }
    }

    // Delete quiz
    @PostMapping("/quiz/delete/{id}")
    @ResponseBody
    public String deleteQuiz(@PathVariable Long id, Authentication auth) {
        try {
            User teacher = (User) auth.getPrincipal();
            Quiz quiz = quizService.findById(id).orElse(null);

            if (quiz == null) {
                return "Quiz does not exist";
            }

            // Check permission
            if (!quiz.getTeacher().getId().equals(teacher.getId())) {
                return "No permission to delete this quiz";
            }

            // Delete quiz
            quizService.deleteQuiz(id);

            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to delete quiz: " + e.getMessage();
        }
    }

    @GetMapping("/question/ai-normalize")
    public String aiNormalizePage(Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        List<Quiz> quizzes = quizService.findQuizzesByTeacher(teacher);
        model.addAttribute("quizzes", quizzes);
        model.addAttribute("teacher", teacher);
        return "teacher/question-ai-normalize";
    }

    @GetMapping("/question/ai-generate")
    public String aiGeneratePage(Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        List<Quiz> quizzes = quizService.findQuizzesByTeacher(teacher);
        model.addAttribute("quizzes", quizzes);
        model.addAttribute("teacher", teacher);
        return "teacher/question-ai-generate";
    }

    // Toggle Quiz status
    @GetMapping("/quiz/{id}/toggle-status")
    public String toggleQuizStatus(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            User teacher = (User) auth.getPrincipal();
            Quiz quiz = quizService.findById(id).orElse(null);

            if (quiz == null) {
                redirectAttributes.addFlashAttribute("error", "Quiz does not exist");
                return "redirect:/teacher/quizzes";
            }

            // Check permission
            if (!quiz.getTeacher().getId().equals(teacher.getId())) {
                redirectAttributes.addFlashAttribute("error", "No permission to operate this quiz");
                return "redirect:/teacher/quizzes";
            }

            quizService.toggleQuizStatus(id);
            redirectAttributes.addFlashAttribute("message", "Quiz status toggled successfully");
            return "redirect:/teacher/quizzes";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to toggle quiz status: " + e.getMessage());
            return "redirect:/teacher/quizzes";
        }
    }
}