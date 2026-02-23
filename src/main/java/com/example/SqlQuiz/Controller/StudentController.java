package com.example.SqlQuiz.Controller;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.service.QuizService;
import com.example.SqlQuiz.service.QuizTableMetadataService;
import com.example.SqlQuiz.service.SandboxDatabaseService;
import com.example.SqlQuiz.service.SqlValidationService;
import com.example.SqlQuiz.repository.QuestionAnswerRepository;
import com.example.SqlQuiz.repository.SubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private QuizService quizService;

    @Autowired
    private SqlValidationService sqlValidationService;

    @Autowired
    private QuestionAnswerRepository questionAnswerRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private QuizTableMetadataService quizTableMetadataService;

    @Autowired
    private SandboxDatabaseService sandboxDatabaseService;

    // Student dashboard
    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        User student = (User) auth.getPrincipal();

        // Get all active quizzes (show all, regardless of time constraints)
        List<Quiz> availableQuizzes = quizService.findActiveQuizzes();

        // Get student's quiz records
        List<Submission> allSubmissions = quizService.getStudentSubmissions(student);

        // Group by quiz, keep only last submission for each quiz
        Map<Long, Submission> latestSubmissionsByQuiz = new HashMap<>();
        for (Submission submission : allSubmissions) {
            Long quizId = submission.getQuiz().getId();
            Submission currentLatest = latestSubmissionsByQuiz.get(quizId);

            // If this quiz has no record yet, or current submission is newer than existing record, update
            if (currentLatest == null ||
                (submission.getSubmitTime() != null && currentLatest.getSubmitTime() != null &&
                 submission.getSubmitTime().isAfter(currentLatest.getSubmitTime())) ||
                (submission.getSubmitTime() != null && currentLatest.getSubmitTime() == null) ||
                (submission.getStartTime() != null && currentLatest.getStartTime() != null &&
                 submission.getStartTime().isAfter(currentLatest.getStartTime()))) {
                latestSubmissionsByQuiz.put(quizId, submission);
            }
        }

        // Convert to List
        List<Submission> submissions = new ArrayList<>(latestSubmissionsByQuiz.values());
        // Sort by submission time descending (newest first)
        submissions.sort((s1, s2) -> {
            if (s1.getSubmitTime() != null && s2.getSubmitTime() != null) {
                return s2.getSubmitTime().compareTo(s1.getSubmitTime());
            } else if (s1.getSubmitTime() != null) {
                return -1;
            } else if (s2.getSubmitTime() != null) {
                return 1;
            } else {
                return s2.getStartTime().compareTo(s1.getStartTime());
            }
        });

        model.addAttribute("student", student);
        model.addAttribute("availableQuizzes", availableQuizzes);
        model.addAttribute("submissions", submissions);

        return "student/dashboard";
    }

    // Quiz list
    @GetMapping("/quizzes")
    public String quizList(Model model, Authentication auth) {
        User student = (User) auth.getPrincipal();
        // Get all active quizzes (show all, regardless of time constraints)
        List<Quiz> availableQuizzes = quizService.findActiveQuizzes();

        // Get student's quiz records
        List<Submission> submissions = quizService.getStudentSubmissions(student);

        // Calculate statistics
        int totalQuizzes = availableQuizzes.size();
        int completedQuizzes = 0;
        double totalScore = 0.0;
        double maxTotalScore = 0.0;

        for (Submission submission : submissions) {
            if (submission.isCompleted()) {
                completedQuizzes++;
                if (submission.getTotalScore() != null) {
                    totalScore += submission.getTotalScore();
                }
                if (submission.getMaxScore() != null) {
                    maxTotalScore += submission.getMaxScore();
                }
            }
        }

        double averageScore = maxTotalScore > 0 ? (totalScore / maxTotalScore) * 100 : 0.0;

        model.addAttribute("quizzes", availableQuizzes);
        model.addAttribute("student", student);
        model.addAttribute("totalQuizzes", totalQuizzes);
        model.addAttribute("completedQuizzes", completedQuizzes);
        model.addAttribute("averageScore", averageScore);
        model.addAttribute("submissions", submissions.size());

        return "student/quiz-list";
    }

    // Quiz detail
    @GetMapping("/quiz/{id}")
    public String quizDetail(@PathVariable Long id, Model model, Authentication auth, RedirectAttributes redirectAttributes) {
        User student = (User) auth.getPrincipal();
        Quiz quiz = quizService.findById(id).orElse(null);

        if (quiz == null) {
            return "redirect:/student/quizzes";
        }

        LocalDateTime now = LocalDateTime.now();

        // Check time constraints and provide English error messages
        if (quiz.getStartTime() != null && now.isBefore(quiz.getStartTime())) {
            redirectAttributes.addFlashAttribute("error", "This quiz is not yet available. Available starting from: " + quiz.getStartTime());
            model.addAttribute("quiz", quiz);
            model.addAttribute("canTake", false);
            return "student/quiz-detail";
        }

        if (quiz.getEndTime() != null && now.isAfter(quiz.getEndTime())) {
            redirectAttributes.addFlashAttribute("error", "This quiz has ended. The end time was: " + quiz.getEndTime());
            model.addAttribute("quiz", quiz);
            model.addAttribute("canTake", false);
            return "student/quiz-detail";
        }

        // Check if student can take (attempt limit, in-progress submission, etc.)
        boolean canTake = quizService.canStudentTakeQuizIgnoreTime(id, student);
        model.addAttribute("quiz", quiz);
        model.addAttribute("canTake", canTake);

        return "student/quiz-detail";
    }

    // Start quiz
    @PostMapping("/quiz/{id}/start")
    public String startQuiz(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            User student = (User) auth.getPrincipal();

            Quiz quiz = quizService.findById(id).orElse(null);
            if (quiz == null) {
                redirectAttributes.addFlashAttribute("error", "Quiz not found");
                return "redirect:/student/quizzes";
            }

            LocalDateTime now = LocalDateTime.now();

            // Check time constraints with English error messages
            if (quiz.getStartTime() != null && now.isBefore(quiz.getStartTime())) {
                redirectAttributes.addFlashAttribute("error", "This quiz is not yet available. Available starting from: " + quiz.getStartTime());
                return "redirect:/student/quiz/" + id;
            }

            if (quiz.getEndTime() != null && now.isAfter(quiz.getEndTime())) {
                redirectAttributes.addFlashAttribute("error", "This quiz has ended. The end time was: " + quiz.getEndTime());
                return "redirect:/student/quiz/" + id;
            }

            // Check other constraints (attempt limit, etc.)
            if (!quizService.canStudentTakeQuizIgnoreTime(id, student)) {
                redirectAttributes.addFlashAttribute("error", "Cannot take this quiz. Maximum attempts reached or quiz is inactive.");
                return "redirect:/student/quiz/" + id;
            }

            Submission submission = quizService.startQuiz(id, student);
            return "redirect:/student/submission/" + submission.getId();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to start quiz: " + e.getMessage());
            return "redirect:/student/quiz/" + id;
        }
    }

    // Take quiz page
    @GetMapping("/submission/{id}")
    public String takeQuiz(@PathVariable Long id, Model model, Authentication auth, RedirectAttributes redirectAttributes) {
        User student = (User) auth.getPrincipal();

        // Get submission record
        Optional<Submission> submissionOpt = submissionRepository.findById(id);
        if (!submissionOpt.isPresent()) {
            return "redirect:/student/quizzes";
        }

        Submission submission = submissionOpt.get();

        // Verify permission
        if (!submission.getStudent().getId().equals(student.getId())) {
            return "redirect:/student/quizzes";
        }

        // Check submission status
        if (!submission.isInProgress()) {
            return "redirect:/student/submission/" + id + "/result";
        }

        // Get quiz and check time constraints
        Quiz quiz = submission.getQuiz();
        LocalDateTime now = LocalDateTime.now();

        System.out.println("[takeQuiz] Checking time constraints for submission " + id);
        System.out.println("[takeQuiz] Current time: " + now);
        System.out.println("[takeQuiz] Quiz startTime: " + quiz.getStartTime());
        System.out.println("[takeQuiz] Quiz endTime: " + quiz.getEndTime());

        // Check if quiz has started (Issue 1: cannot enter before start time)
        if (quiz.getStartTime() != null && now.isBefore(quiz.getStartTime())) {
            System.out.println("[takeQuiz] BLOCKED: Quiz has not started yet");
            redirectAttributes.addFlashAttribute("error", "This quiz is not yet available. It will start at: " + quiz.getStartTime());
            return "redirect:/student/quiz/" + quiz.getId();
        }


        // Check if quiz has ended (Issue 3: cannot enter after end time)
        if (quiz.getEndTime() != null && now.isAfter(quiz.getEndTime())) {
            System.out.println("[takeQuiz] BLOCKED: Quiz has already ended");
            redirectAttributes.addFlashAttribute("error", "This quiz has ended. The end time was: " + quiz.getEndTime());
            return "redirect:/student/quiz/" + quiz.getId();
        }

        System.out.println("[takeQuiz] Time check passed, allowing access");

        // Get question information
        List<Question> questions = quizService.getQuestionsByQuiz(quiz.getId());

        // Get student's question answer records
        List<QuestionAnswer> questionAnswers = questionAnswerRepository.findBySubmission(submission);

        // Generate real databaseContext from test_db for each question
        Map<Long, String> realDatabaseContextMap = new HashMap<>();
        for (Question q : questions) {
            List<QuizTableMetadata> metadataList = quizTableMetadataService.getByQuestionId(q.getId());
            if (!metadataList.isEmpty()) {
                String tablePrefix = metadataList.get(0).getTablePrefix();
                String realMarkdown = sandboxDatabaseService.generateMarkdownFromTestDB(tablePrefix);
                if (realMarkdown != null) {
                    realDatabaseContextMap.put(q.getId(), realMarkdown);
                }
            }
        }

        // Ensure all necessary properties are not null
        model.addAttribute("submission", submission);
        model.addAttribute("quiz", quiz != null ? quiz : new Quiz());
        model.addAttribute("questions", questions != null ? questions : new ArrayList<>());
        model.addAttribute("questionAnswers", questionAnswers != null ? questionAnswers : new ArrayList<>());
        model.addAttribute("student", student);
        model.addAttribute("realDatabaseContextMap", realDatabaseContextMap);

        // Pass quiz endTime to frontend (Issue 4: auto-submit when endTime is reached)
        if (quiz.getEndTime() != null) {
            model.addAttribute("quizEndTime", quiz.getEndTime());
        }

        // Calculate remaining time (if there's a time limit)
        if (quiz.hasTimeLimit()) {
            long timeElapsed = java.time.Duration.between(submission.getStartTime(), java.time.LocalDateTime.now()).toMinutes();
            long timeRemaining = quiz.getTimeLimit() - timeElapsed;
            model.addAttribute("timeRemaining", Math.max(0, timeRemaining));
        } else {
            model.addAttribute("timeRemaining", -1); // No time limit
        }

        return "student/take-quiz";
    }

    // Submit answer
    @PostMapping("/submission/{submissionId}/answer")
    @ResponseBody
    public String submitAnswer(@PathVariable Long submissionId,
                               @RequestParam Long questionId,
                               @RequestParam String sql,
                               Authentication auth) {
        try {
            User student = (User) auth.getPrincipal();
            quizService.submitAnswer(submissionId, questionId, sql);
            return "success";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    // Review answers page
    @GetMapping("/submission/{id}/review")
    public String reviewAnswers(@PathVariable Long id, Model model, Authentication auth) {
        User student = (User) auth.getPrincipal();

        // Get submission record
        Optional<Submission> submissionOpt = submissionRepository.findById(id);
        if (!submissionOpt.isPresent()) {
            return "redirect:/student/quizzes";
        }

        Submission submission = submissionOpt.get();

        // Verify permission
        if (!submission.getStudent().getId().equals(student.getId())) {
            return "redirect:/student/quizzes";
        }

        // Check submission status
        if (!submission.isInProgress()) {
            return "redirect:/student/submission/" + id + "/result";
        }

        // Get question answer records
        List<QuestionAnswer> questionAnswers = questionAnswerRepository.findBySubmission(submission);

        // Count answered questions
        long answeredCount = questionAnswers.stream()
            .filter(qa -> qa.getStudentSql() != null && !qa.getStudentSql().trim().isEmpty())
            .count();

        model.addAttribute("submission", submission);
        model.addAttribute("questionAnswers", questionAnswers);
        model.addAttribute("answeredCount", answeredCount);
        model.addAttribute("student", student);

        return "student/quiz-review";
    }

    // Submit quiz
    @PostMapping("/submission/{id}/submit")
    @ResponseBody
    public String submitQuiz(@PathVariable Long id, Authentication auth) {
        try {
            User student = (User) auth.getPrincipal();

            // Verify permission
            Optional<Submission> submissionOpt = submissionRepository.findById(id);
            if (!submissionOpt.isPresent()) {
                return "error: Submission not found";
            }

            Submission submission = submissionOpt.get();
            if (!submission.getStudent().getId().equals(student.getId())) {
                return "error: Access denied";
            }

            quizService.submitQuiz(id);
            return "success";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    // View quiz result
    @GetMapping("/submission/{id}/result")
    public String viewResult(@PathVariable Long id, Model model, Authentication auth) {
        User student = (User) auth.getPrincipal();

        // Get submission record
        Optional<Submission> submissionOpt = submissionRepository.findById(id);
        if (!submissionOpt.isPresent()) {
            return "redirect:/student/submissions";
        }

        Submission submission = submissionOpt.get();

        // Verify permission
        if (!submission.getStudent().getId().equals(student.getId())) {
            return "redirect:/student/submissions";
        }

        // Check submission status - only completed submissions can view results
        if (submission.isInProgress()) {
            return "redirect:/student/submission/" + id;
        }

        model.addAttribute("submission", submission);
        model.addAttribute("student", student);

        return "student/quiz-result";
    }

    // My quiz records
    @GetMapping("/submissions")
    public String mySubmissions(Model model, Authentication auth) {
        User student = (User) auth.getPrincipal();
        List<Submission> submissions = quizService.getStudentSubmissions(student);
        model.addAttribute("submissions", submissions);
        model.addAttribute("student", student);
        return "student/my-submissions";
    }

    // SQL practice page (for student practice)
    @GetMapping("/sql-practice")
    public String sqlPractice() {
        return "student/sql-practice";
    }

    // Execute practice SQL
    @PostMapping("/sql-practice")
    @ResponseBody
    public SqlValidationService.SqlExecutionResult practiceSQL(@RequestParam String sql) {
        return sqlValidationService.executeSQL(sql);
    }
}