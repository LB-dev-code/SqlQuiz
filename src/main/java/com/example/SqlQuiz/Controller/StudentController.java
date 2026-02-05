package com.example.SqlQuiz.Controller;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.service.QuizService;
import com.example.SqlQuiz.service.SqlValidationService;
import com.example.SqlQuiz.repository.QuestionAnswerRepository;
import com.example.SqlQuiz.repository.SubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

    // 学生仪表板
    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        User student = (User) auth.getPrincipal();

        // 获取可参加的测试
        List<Quiz> availableQuizzes = quizService.findOpenQuizzes();

        // 获取学生的测试记录
        List<Submission> allSubmissions = quizService.getStudentSubmissions(student);
        
        // 按quiz分组，只保留每个quiz的最后一次提交
        Map<Long, Submission> latestSubmissionsByQuiz = new HashMap<>();
        for (Submission submission : allSubmissions) {
            Long quizId = submission.getQuiz().getId();
            Submission currentLatest = latestSubmissionsByQuiz.get(quizId);
            
            // 如果这个quiz还没有记录，或者当前提交比已有记录更新，则更新
            if (currentLatest == null || 
                (submission.getSubmitTime() != null && currentLatest.getSubmitTime() != null &&
                 submission.getSubmitTime().isAfter(currentLatest.getSubmitTime())) ||
                (submission.getSubmitTime() != null && currentLatest.getSubmitTime() == null) ||
                (submission.getStartTime() != null && currentLatest.getStartTime() != null &&
                 submission.getStartTime().isAfter(currentLatest.getStartTime()))) {
                latestSubmissionsByQuiz.put(quizId, submission);
            }
        }
        
        // 转换为List
        List<Submission> submissions = new ArrayList<>(latestSubmissionsByQuiz.values());
        // 按提交时间降序排序（最新的在前）
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

    // 测试列表
    @GetMapping("/quizzes")
    public String quizList(Model model, Authentication auth) {
        User student = (User) auth.getPrincipal();
        List<Quiz> availableQuizzes = quizService.findOpenQuizzes();
        
        // 获取学生的测试记录
        List<Submission> submissions = quizService.getStudentSubmissions(student);
        
        // 计算统计数据
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

    // 测试详情
    @GetMapping("/quiz/{id}")
    public String quizDetail(@PathVariable Long id, Model model, Authentication auth) {
        User student = (User) auth.getPrincipal();
        Quiz quiz = quizService.findById(id).orElse(null);

        if (quiz == null) {
            return "redirect:/student/quizzes";
        }

        boolean canTake = quizService.canStudentTakeQuiz(id, student);
        model.addAttribute("quiz", quiz);
        model.addAttribute("canTake", canTake);

        return "student/quiz-detail";
    }

    // 开始测试
    @PostMapping("/quiz/{id}/start")
    public String startQuiz(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            User student = (User) auth.getPrincipal();

            if (!quizService.canStudentTakeQuiz(id, student)) {
                redirectAttributes.addFlashAttribute("error", "无法参加此测试");
                return "redirect:/student/quiz/" + id;
            }

            Submission submission = quizService.startQuiz(id, student);
            return "redirect:/student/submission/" + submission.getId();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "开始测试失败: " + e.getMessage());
            return "redirect:/student/quiz/" + id;
        }
    }

    // 答题页面
    @GetMapping("/submission/{id}")
    public String takeQuiz(@PathVariable Long id, Model model, Authentication auth) {
        User student = (User) auth.getPrincipal();

        // 获取提交记录
        Optional<Submission> submissionOpt = submissionRepository.findById(id);
        if (!submissionOpt.isPresent()) {
            return "redirect:/student/quizzes";
        }

        Submission submission = submissionOpt.get();

        // 验证权限
        if (!submission.getStudent().getId().equals(student.getId())) {
            return "redirect:/student/quizzes";
        }

        // 检查提交状态
        if (!submission.isInProgress()) {
            return "redirect:/student/submission/" + id + "/result";
        }

        // 获取测试和题目信息
        Quiz quiz = submission.getQuiz();
        List<Question> questions = quizService.getQuestionsByQuiz(quiz.getId());

        // 获取学生的答题记录
        List<QuestionAnswer> questionAnswers = questionAnswerRepository.findBySubmission(submission);

        // 确保所有必要属性不为null
        model.addAttribute("submission", submission);
        model.addAttribute("quiz", quiz != null ? quiz : new Quiz());
        model.addAttribute("questions", questions != null ? questions : new ArrayList<>());
        model.addAttribute("questionAnswers", questionAnswers != null ? questionAnswers : new ArrayList<>());
        model.addAttribute("student", student);

        // 计算剩余时间（如果有时间限制）
        if (quiz.hasTimeLimit()) {
            long timeElapsed = java.time.Duration.between(submission.getStartTime(), java.time.LocalDateTime.now()).toMinutes();
            long timeRemaining = quiz.getTimeLimit() - timeElapsed;
            model.addAttribute("timeRemaining", Math.max(0, timeRemaining));
        } else {
            model.addAttribute("timeRemaining", -1); // 无时间限制
        }

        return "student/take-quiz";
    }

    // 提交答案
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

    // 答案检查页面
    @GetMapping("/submission/{id}/review")
    public String reviewAnswers(@PathVariable Long id, Model model, Authentication auth) {
        User student = (User) auth.getPrincipal();

        // 获取提交记录
        Optional<Submission> submissionOpt = submissionRepository.findById(id);
        if (!submissionOpt.isPresent()) {
            return "redirect:/student/quizzes";
        }

        Submission submission = submissionOpt.get();

        // 验证权限
        if (!submission.getStudent().getId().equals(student.getId())) {
            return "redirect:/student/quizzes";
        }

        // 检查提交状态
        if (!submission.isInProgress()) {
            return "redirect:/student/submission/" + id + "/result";
        }

        // 获取答题记录
        List<QuestionAnswer> questionAnswers = questionAnswerRepository.findBySubmission(submission);
        
        // 统计已答题数量
        long answeredCount = questionAnswers.stream()
            .filter(qa -> qa.getStudentSql() != null && !qa.getStudentSql().trim().isEmpty())
            .count();

        model.addAttribute("submission", submission);
        model.addAttribute("questionAnswers", questionAnswers);
        model.addAttribute("answeredCount", answeredCount);
        model.addAttribute("student", student);

        return "student/quiz-review";
    }

    // 提交测试
    @PostMapping("/submission/{id}/submit")
    @ResponseBody
    public String submitQuiz(@PathVariable Long id, Authentication auth) {
        try {
            User student = (User) auth.getPrincipal();
            
            // 验证权限
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

    // 查看测试结果
    @GetMapping("/submission/{id}/result")
    public String viewResult(@PathVariable Long id, Model model, Authentication auth) {
        User student = (User) auth.getPrincipal();

        // 获取提交记录
        Optional<Submission> submissionOpt = submissionRepository.findById(id);
        if (!submissionOpt.isPresent()) {
            return "redirect:/student/submissions";
        }

        Submission submission = submissionOpt.get();

        // 验证权限
        if (!submission.getStudent().getId().equals(student.getId())) {
            return "redirect:/student/submissions";
        }

        // 检查提交状态 - 只有已完成的提交才能查看结果
        if (submission.isInProgress()) {
            return "redirect:/student/submission/" + id;
        }

        model.addAttribute("submission", submission);
        model.addAttribute("student", student);

        return "student/quiz-result";
    }

    // 我的测试记录
    @GetMapping("/submissions")
    public String mySubmissions(Model model, Authentication auth) {
        User student = (User) auth.getPrincipal();
        List<Submission> submissions = quizService.getStudentSubmissions(student);
        model.addAttribute("submissions", submissions);
        model.addAttribute("student", student);
        return "student/my-submissions";
    }

    // SQL测试页面（学生练习用）
    @GetMapping("/sql-practice")
    public String sqlPractice() {
        return "student/sql-practice";
    }

    // 执行练习SQL
    @PostMapping("/sql-practice")
    @ResponseBody
    public SqlValidationService.SqlExecutionResult practiceSQL(@RequestParam String sql) {
        return sqlValidationService.executeSQL(sql);
    }
}