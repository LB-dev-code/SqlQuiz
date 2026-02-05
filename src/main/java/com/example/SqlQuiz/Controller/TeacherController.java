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

    // 教师仪表板
    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        List<Quiz> quizzes = quizService.findQuizzesByTeacher(teacher);
        
        // 统计总题目数
        int totalQuestions = 0;
        for (Quiz quiz : quizzes) {
            totalQuestions += quiz.getQuestions().size();
        }
        
        // 统计学生提交数
        List<Submission> submissions = quizService.getAllSubmissionsByTeacher(teacher);
        int totalSubmissions = submissions.size();
        
        model.addAttribute("quizzes", quizzes);
        model.addAttribute("teacher", teacher);
        model.addAttribute("totalQuestions", totalQuestions);
        model.addAttribute("totalSubmissions", totalSubmissions);
        return "teacher/dashboard";
    }

    // 测试管理页面
    @GetMapping("/quizzes")
    public String quizManagement(Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        List<Quiz> quizzes = quizService.findQuizzesByTeacher(teacher);
        
        // 统计总题目数
        int totalQuestions = 0;
        for (Quiz quiz : quizzes) {
            totalQuestions += quiz.getQuestions().size();
        }
        
        // 统计学生提交数
        List<Submission> submissions = quizService.getAllSubmissionsByTeacher(teacher);
        int totalSubmissions = submissions.size();
        
        // 计算平均完成率
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
        System.out.println("get 结束");
        return "teacher/quiz-list";
    }

     @GetMapping("/quiz/create")
        public String createQuizPage(Model model, Authentication auth) {
            // 添加调试信息
            User teacher = (User) auth.getPrincipal();
            model.addAttribute("teacher", teacher);
            model.addAttribute("quiz", new Quiz());

            // 获取KPI数据
            List<Quiz> quizzes = quizService.findQuizzesByTeacher(teacher);
            int totalQuestions = 0;
            for (Quiz quiz : quizzes) {
                totalQuestions += quiz.getQuestions().size();
            }
            List<Submission> submissions = quizService.getAllSubmissionsByTeacher(teacher);
            int totalSubmissions = submissions.size();
            
            // 计算平均完成率
            double avgCompletionRate = 0.0;
            if (!submissions.isEmpty()) {
                long completedCount = submissions.stream()
                    .mapToLong(s -> s.getPercentage() != null && s.getPercentage() >= 100.0 ? 1 : 0)
                    .sum();
                avgCompletionRate = (double) completedCount / submissions.size();
            }
            
            model.addAttribute("quizzes", quizzes);
            model.addAttribute("totalSubmissions", totalSubmissions);
            model.addAttribute("avgCompletionRate", avgCompletionRate);
            
            System.out.println("访问创建测试页面 - 教师: " + teacher.getFullName());
            return "teacher/quiz-create";
        }


    // 处理创建测试
//    @PostMapping("/quiz/create")
//    public String createQuiz(@RequestParam String title,
//                             @RequestParam String description,
//                             @RequestParam Integer timeLimit,
//                             @RequestParam Integer maxAttempts,
//                             Authentication auth,
//                             RedirectAttributes redirectAttributes) {
//        try {
//            User teacher = (User) auth.getPrincipal();
//            Quiz quiz = quizService.createQuiz(title, description, timeLimit, maxAttempts, teacher);
//            redirectAttributes.addFlashAttribute("message", "Quiz created successfully!");
//            return "redirect:/teacher/quiz/" + quiz.getId() + "/questions";
//        } catch (Exception e) {
//            redirectAttributes.addFlashAttribute("error", "Failed to create quiz: " + e.getMessage());
//            return "redirect:/teacher/quiz/create";
//        }
//    }
    @PostMapping("/quiz/create")
    public String createQuiz(@RequestParam String title,
                             @RequestParam String description,
                             @RequestParam Integer timeLimit,
                             @RequestParam Integer maxAttempts,
                             @RequestParam(required = false) String startTime,
                             @RequestParam(required = false) String endTime,
                             Authentication auth,
                             RedirectAttributes redirectAttributes) {
        try {
            User teacher = (User) auth.getPrincipal();

            // 解析开始时间和结束时间
            LocalDateTime startDateTime = null;
            LocalDateTime endDateTime = null;

            if (startTime != null && !startTime.isEmpty()) {
                try {
                    // 解析HTML datetime-local格式 (yyyy-MM-ddTHH:mm)
                    startDateTime = LocalDateTime.parse(startTime);
                } catch (Exception e) {
                    System.err.println("开始时间解析失败: " + e.getMessage());
                }
            }

            if (endTime != null && !endTime.isEmpty()) {
                try {
                    // 解析HTML datetime-local格式 (yyyy-MM-ddTHH:mm)
                    endDateTime = LocalDateTime.parse(endTime);
                } catch (Exception e) {
                    System.err.println("结束时间解析失败: " + e.getMessage());
                }
            }

            // 创建测试
            Quiz quiz = quizService.createQuiz(title, description, timeLimit, maxAttempts, teacher);

            // 更新测试的开始时间和结束时间
            quizService.updateQuiz(quiz.getId(), title, description, timeLimit, maxAttempts, startDateTime, endDateTime);

            redirectAttributes.addFlashAttribute("message", "Quiz created successfully!");
            return "redirect:/teacher/quiz/" + quiz.getId() + "/questions";
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to create quiz: " + e.getMessage());
            System.out.println("post结束");
            return "redirect:/teacher/quiz/create";
        }
    }
    // 题目管理页面
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

    // 添加题目页面
    @GetMapping("/quiz/{quizId}/question/create")
    public String createQuestionPage(@PathVariable Long quizId, Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        Quiz quiz = quizService.findById(quizId).orElse(null);

        if (quiz == null || !quiz.getTeacher().getId().equals(teacher.getId())) {
            return "redirect:/teacher/quizzes";
        }

        model.addAttribute("quiz", quiz);
        model.addAttribute("questionTypes", Question.QuestionType.values());
        model.addAttribute("difficultyLevels", Question.DifficultyLevel.values());
        model.addAttribute("teacher", teacher);

        return "teacher/question-create";
    }

    // 处理添加题目
    @PostMapping("/quiz/{quizId}/question/create")
    public String createQuestion(@PathVariable Long quizId,
                                 @RequestParam String content,
                                 @RequestParam(required = false) String questionType,
                                 @RequestParam(required = false) String description,
                                 @RequestParam(required = false) String expectedSql,
                                 @RequestParam Double score,
                                 @RequestParam(required = false) String difficultyLevel,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        try {
            User teacher = (User) auth.getPrincipal();
            Quiz quiz = quizService.findById(quizId).orElse(null);

            if (quiz == null || !quiz.getTeacher().getId().equals(teacher.getId())) {
                redirectAttributes.addFlashAttribute("error", "No permission to operate this quiz");
                return "redirect:/teacher/quizzes";
            }

            // 处理可选的枚举类型
            Question.QuestionType qType = null;
            if (questionType != null && !questionType.trim().isEmpty()) {
                qType = Question.QuestionType.valueOf(questionType);
            }

            Question.DifficultyLevel difficulty = null;
            if (difficultyLevel != null && !difficultyLevel.trim().isEmpty()) {
                difficulty = Question.DifficultyLevel.valueOf(difficultyLevel);
            }

            // 使用AI生成题目内容
            String typeInfo = qType != null ? qType.getDisplayName() : "未指定";
            String difficultyInfo = difficulty != null ? difficulty.getDisplayName() : "未指定";
            String glmResponse = glmService.chat_create_quiz(description + "题目类型：" + typeInfo + "题目难度：" + difficultyInfo);
            System.out.println("GLM给的:" + glmResponse);

            // 清理响应文本，移除可能的代码块标记
            String cleanResponse = glmResponse.trim();
            
            // 移除可能的代码块标记 ```json 和 ```
            if (cleanResponse.startsWith("```json")) {
                cleanResponse = cleanResponse.substring(7).trim();
            }
            if (cleanResponse.startsWith("```")) {
                cleanResponse = cleanResponse.substring(3).trim();
            }
            if (cleanResponse.endsWith("```")) {
                cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3).trim();
            }
            
            // 移除可能的前后反引号
            if (cleanResponse.startsWith("`") && cleanResponse.endsWith("`")) {
                cleanResponse = cleanResponse.substring(1, cleanResponse.length() - 1).trim();
            }
            
            System.out.println("清理后的响应: " + cleanResponse);

            // 解析JSON响应
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode;
            
            try {
                jsonNode = objectMapper.readTree(cleanResponse);
            } catch (JsonProcessingException e) {
                System.err.println("JSON解析失败，尝试修复格式: " + e.getMessage());
                // 尝试简单的格式修复
                cleanResponse = cleanResponse.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "");
                jsonNode = objectMapper.readTree(cleanResponse);
            }

            // 从JSON中提取数据
            String questionTitle = jsonNode.has("questionTitle") ? jsonNode.get("questionTitle").asText() : content;
            String questionDescription = jsonNode.has("questionDescription") ? jsonNode.get("questionDescription").asText() : description;
            String databaseContext = jsonNode.has("databaseContext") ? jsonNode.get("databaseContext").asText() : null;
            String sampleData = jsonNode.has("sampleData") ? jsonNode.get("sampleData").asText() : null;
            String aiExpectedSql = jsonNode.has("expectedSql") ? jsonNode.get("expectedSql").asText() : expectedSql;
            String hints = jsonNode.has("hints") ? jsonNode.get("hints").asText() : null;
            String setupSql = jsonNode.get("setupSql").asText();

            // 在testdb中执行setup SQL并获取表前缀
            String tablePrefix = null;
            if (setupSql != null && !setupSql.trim().isEmpty()) {
                tablePrefix = setupSqlExecutorService.executeSetupSql(setupSql);
            }
            // 组合完整的描述信息
            StringBuilder fullDescription = new StringBuilder();
            fullDescription.append(questionDescription);
            if (databaseContext != null) {
                fullDescription.append("\n\n数据库表结构：\n").append(databaseContext);
            }
            if (sampleData != null) {
                fullDescription.append("\n\n示例数据：\n").append(sampleData);
            }
            if (hints != null) {
                fullDescription.append("\n\n提示：\n").append(hints);
            }

            // 创建题目
            Question question = quizService.addQuestionToQuiz(quizId, questionTitle, qType, fullDescription.toString(),
                    databaseContext, aiExpectedSql != null ? aiExpectedSql : expectedSql, null,
                    null, score, difficulty);

            // 保存表元数据
            if (tablePrefix != null) {
                tableMetadataService.createMetadata(tablePrefix, question.getId(), teacher.getId());
            }

            redirectAttributes.addFlashAttribute("message", "Question added successfully!");
            return "redirect:/teacher/quiz/" + quizId + "/questions";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to add question: " + e.getMessage());
            return "redirect:/teacher/quiz/" + quizId + "/question/create";
        }
    }

    // 编辑题目页面
    @GetMapping("/question/{questionId}/edit")
    @ResponseBody
    public Question editQuestionPage(@PathVariable Long questionId, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        Optional<Question> questionOpt = quizService.getQuestionById(questionId);

        if (questionOpt.isPresent()) {
            Question question = questionOpt.get();
            // 检查权限：确保是该题目所属测试的创建者
            if (question.getQuiz().getTeacher().getId().equals(teacher.getId())) {
                return question;
            }
        }
        return null;
    }

    // 处理编辑题目
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

            // 检查权限
            if (!question.getQuiz().getTeacher().getId().equals(teacher.getId())) {
                redirectAttributes.addFlashAttribute("error", "No permission to operate this question");
                return "redirect:/teacher/quizzes";
            }

            // 更新题目
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

    // 删除题目
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

            // 检查权限
            if (!question.getQuiz().getTeacher().getId().equals(teacher.getId())) {
                redirectAttributes.addFlashAttribute("error", "No permission to operate this question");
                return "redirect:/teacher/quizzes";
            }

            // 删除题目
            quizService.deleteQuestion(questionId);

            // 删除testdb中对应的表格
            setupSqlExecutorService.dropTablesByQuestionId(questionId);

            redirectAttributes.addFlashAttribute("message", "Question deleted successfully!");
            return "redirect:/teacher/quiz/" + quizId + "/questions";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to delete question: " + e.getMessage());
            return "redirect:/teacher/quizzes";
        }
    }

    // 测试统计页面
    @GetMapping("/quiz/{id}/statistics")
    public String quizStatistics(@PathVariable Long id, Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        Quiz quiz = quizService.findById(id).orElse(null);

        if (quiz == null || !quiz.getTeacher().getId().equals(teacher.getId())) {
            return "redirect:/teacher/quizzes";
        }

        List<Submission> allSubmissions = quizService.getQuizSubmissions(id);
        
        // 按学生分组，只保留每个学生的最新提交记录
        Map<Long, Submission> latestSubmissionsByStudent = new HashMap<>();
        for (Submission submission : allSubmissions) {
            Long studentId = submission.getStudent().getId();
            Submission currentLatest = latestSubmissionsByStudent.get(studentId);
            
            // 如果当前学生还没有最新提交记录，或者当前提交比已有记录更新，则更新
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

    // 通用题目列表页面
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

    // 通用题目创建页面
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

    // 处理通用题目创建
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

            // 处理可选的枚举类型
            Question.QuestionType qType = null;
            if (questionType != null && !questionType.trim().isEmpty()) {
                qType = Question.QuestionType.valueOf(questionType);
            }

            Question.DifficultyLevel difficulty = null;
            if (difficultyLevel != null && !difficultyLevel.trim().isEmpty()) {
                difficulty = Question.DifficultyLevel.valueOf(difficultyLevel);
            }

            // 创建题目
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

    // 通用统计页面
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

    // 通用提交详情页面
    @GetMapping("/submission-detail")
    @Transactional(readOnly = true)
    public String submissionDetail(@RequestParam(required = false) Long submission_id,
                                   @RequestParam(required = false) Long quiz_id,
                                   Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();

        if (submission_id != null) {
            // 显示特定提交详情 - 使用预加载查询避免懒加载问题
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
            // 显示特定测试的所有提交
            Quiz quiz = quizService.findById(quiz_id).orElse(null);
            if (quiz == null || !quiz.getTeacher().getId().equals(teacher.getId())) {
                return "redirect:/teacher/quiz-statistics";
            }

            List<Submission> submissions = quizService.getQuizSubmissions(quiz_id);
            model.addAttribute("quiz", quiz);
            model.addAttribute("submissions", submissions);
        } else {
            // 显示所有提交
            List<Submission> allSubmissions = quizService.getAllSubmissionsByTeacher(teacher);
            model.addAttribute("submissions", allSubmissions);
        }

        model.addAttribute("teacher", teacher);
        return "teacher/submission-detail";
    }

    // SQL测试页面
    @GetMapping("/sql-test")
    public String sqlTestPage(Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        model.addAttribute("teacher", teacher);
        return "teacher/sql-test";
    }

    // 测试SQL执行
    @PostMapping("/sql-test")
    @ResponseBody
    public Map<String, Object> testSql(@RequestParam String sql, Authentication auth) {
        Map<String, Object> response = new HashMap<>();

        if (sql == null || sql.trim().isEmpty()) {
            response.put("success", false);
            response.put("error", "SQL语句不能为空");
            return response;
        }

        User teacher = (User) auth.getPrincipal();
        com.example.SqlQuiz.entity.SandboxContext sandbox = null;

        try {
            // 创建教师测试专用沙库
            sandbox = sandboxService.createTeacherTestSandbox(teacher.getId());

            // 在沙库中执行SQL
            com.example.SqlQuiz.service.SandboxDatabaseService.SqlExecutionResult result =
                sandboxService.executeInSandbox(sandbox, sql);

            response.put("success", result.isSuccess());
            response.put("data", result.getResultData());
            response.put("rowCount", result.getRowCount());
            response.put("executionTimeMs", result.getExecutionTimeMs());
            response.put("error", result.getErrorMessage());

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "SQL执行错误: " + e.getMessage());
        } finally {
            // 清理沙库
            if (sandbox != null) {
                try {
                    sandboxService.closeConnection(sandbox);
                    sandboxService.cleanupSandbox(sandbox.getDatabaseName());
                } catch (Exception e) {
                    System.err.println("清理教师测试沙库失败: " + e.getMessage());
                }
            }
        }

        return response;
    }

    // 查看学生提交详情页面
    @GetMapping("/quiz/statistics/submission/{submission_id}")
    public String submissionDetail(@PathVariable Long submission_id, Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();

        // 获取提交记录
        Optional<Submission> submissionOpt = submissionRepository.findById(submission_id);
        if (!submissionOpt.isPresent()) {
            return "redirect:/teacher/quizzes";
        }

        Submission submission = submissionOpt.get();

        // 验证权限：确保是该测试的创建者
        if (!submission.getQuiz().getTeacher().getId().equals(teacher.getId())) {
            return "redirect:/teacher/quizzes";
        }

        // 获取答题记录
        List<QuestionAnswer> questionAnswers = questionAnswerRepository.findBySubmission(submission);

        model.addAttribute("submission", submission);
        model.addAttribute("questionAnswers", questionAnswers);
        model.addAttribute("teacher", teacher);

        return "teacher/submission-detail";
    }

    // AI智能评分
    @PostMapping("/quiz/statistics/submission/{submission_id}")
    public String scoreSubmissions(@PathVariable Long submission_id, Model model, Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            User teacher = (User) auth.getPrincipal();

            // 验证权限
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

            // 调用AI评分
            String feedback = quizService.scoreQuiz(submission_id);
            System.out.println("AI评分反馈：" + feedback);

            // 解析AI返回的JSON，只提取feedback字段
            String feedbackContent = "";
            try {
                // 移除可能的``json````格式
                String cleanJson = feedback.replace("```json", "").replace("```", "").trim();

                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(cleanJson);

                // 提取feedback字段
                if (jsonNode.has("feedback")) {
                    feedbackContent = jsonNode.get("feedback").asText();
                } else {
                    feedbackContent = feedback; // 如果没有feedback字段，使用原始反馈
                }
            } catch (Exception e) {
                System.err.println("解析AI评分反馈时出错: " + e.getMessage());
                feedbackContent = feedback; // 如果解析失败，使用原始反馈
            }

            // 重新获取提交记录和答题记录，确保获取到最新的评分信息和总分
            submissionOpt = submissionRepository.findById(submission_id);
            if (submissionOpt.isPresent()) {
                submission = submissionOpt.get();
                List<QuestionAnswer> questionAnswers = questionAnswerRepository.findBySubmission(submission);
                model.addAttribute("submission", submission);
                model.addAttribute("questionAnswers", questionAnswers);
            }

            // 获取计算后的总分信息，用于确认评分结果
            if (submissionOpt.isPresent()) {
                Submission updatedSubmission = submissionOpt.get();
                System.out.println("AI评分完成 - 总分: " + updatedSubmission.getTotalScore() + 
                                 ", 满分: " + updatedSubmission.getMaxScore() + 
                                 ", 百分比: " + updatedSubmission.getPercentage() + "%");
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

    // 批量AI评分
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
                response.put("error", "测验不存在");
                return response;
            }

            // 检查权限
            if (!quiz.getTeacher().getId().equals(teacher.getId())) {
                response.put("success", false);
                response.put("error", "无权限操作此测验");
                return response;
            }

            // 获取该测验的所有提交记录
            List<Submission> submissions = quizService.getQuizSubmissions(id);
            
            // 按学生分组，只保留每个学生的最新提交记录
            Map<Long, Submission> latestSubmissionsByStudent = new HashMap<>();
            for (Submission submission : submissions) {
                Long studentId = submission.getStudent().getId();
                Submission currentLatest = latestSubmissionsByStudent.get(studentId);
                
                // 如果当前学生还没有最新提交记录，或者当前提交比已有记录更新，则更新
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

            System.out.println("开始批量评分，找到 " + submissions.size() + " 份提交记录，其中 " + totalSubmissions + " 份为各学生的最新提交");

            // 对每个学生的最新提交记录进行AI评分
            for (Submission submission : latestSubmissions) {
                try {
                    System.out.println("检查提交记录 ID: " + submission.getId() + 
                                     ", 状态: " + submission.getStatus() + 
                                     ", 总分: " + submission.getTotalScore());
                    
                    // 重新从数据库获取最新的提交状态
                    Optional<Submission> freshSubmissionOpt = submissionRepository.findById(submission.getId());
                    if (!freshSubmissionOpt.isPresent()) {
                        errors.add("提交记录 " + submission.getId() + ": 无法找到记录");
                        continue;
                    }
                    
                    Submission freshSubmission = freshSubmissionOpt.get();
                    
                    // 获取最新的答题记录
                    List<QuestionAnswer> questionAnswers = questionAnswerRepository.findBySubmission(freshSubmission);
                    freshSubmission.setQuestionAnswers(questionAnswers);
                    
                    // 只对已提交但未评分的进行评分
                    boolean needsScoring = (freshSubmission.getStatus().name().equals("SUBMITTED") || 
                                           freshSubmission.getStatus().name().equals("AUTO_SUBMITTED")) &&
                                          (freshSubmission.getTotalScore() == null || 
                                           freshSubmission.getTotalScore() <= 0);
                    
                    System.out.println("提交记录 " + freshSubmission.getId() + " 是否需要评分: " + needsScoring);
                    
                    if (needsScoring) {
                        System.out.println("开始对提交记录 " + freshSubmission.getId() + " 进行AI评分");
                        
                        // 直接调用AI评分，确保每个提交都被独立处理
                        String feedback = quizService.scoreQuiz(freshSubmission.getId());
                        System.out.println("AI评分完成，反馈长度: " + (feedback != null ? feedback.length() : 0));
                        
                        scoredCount++;
                        System.out.println("成功评分提交记录 " + freshSubmission.getId());
                        
                        // 验证评分结果
                        Optional<Submission> updatedSubmissionOpt = submissionRepository.findById(freshSubmission.getId());
                        if (updatedSubmissionOpt.isPresent()) {
                            Submission updated = updatedSubmissionOpt.get();
                            System.out.println("评分后 - 状态: " + updated.getStatus() + 
                                             ", 总分: " + updated.getTotalScore() + 
                                             ", 百分比: " + updated.getPercentage());
                        }
                    } else {
                        System.out.println("跳过提交记录 " + freshSubmission.getId() + " (状态: " + 
                                         freshSubmission.getStatus() + ", 分数: " + 
                                         freshSubmission.getTotalScore() + ")");
                    }
                } catch (Exception e) {
                    System.err.println("评分提交记录失败，ID: " + submission.getId() + ", 错误: " + e.getMessage());
                    e.printStackTrace();
                    errors.add("提交记录 " + submission.getId() + ": " + e.getMessage());
                }
            }

            System.out.println("批量评分完成，成功评分: " + scoredCount + " 份，失败: " + errors.size() + " 份");

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
            response.put("error", "批量评分失败: " + e.getMessage());
            return response;
        }
    }

    // 删除测验
    @PostMapping("/quiz/delete/{id}")
    @ResponseBody
    public String deleteQuiz(@PathVariable Long id, Authentication auth) {
        try {
            User teacher = (User) auth.getPrincipal();
            Quiz quiz = quizService.findById(id).orElse(null);

            if (quiz == null) {
                return "Quiz does not exist";
            }

            // 检查权限
            if (!quiz.getTeacher().getId().equals(teacher.getId())) {
                return "No permission to delete this quiz";
            }

            // 删除测验
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

    // 切换Quiz状态
    @GetMapping("/quiz/{id}/toggle-status")
    public String toggleQuizStatus(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            User teacher = (User) auth.getPrincipal();
            Quiz quiz = quizService.findById(id).orElse(null);

            if (quiz == null) {
                redirectAttributes.addFlashAttribute("error", "Quiz does not exist");
                return "redirect:/teacher/quizzes";
            }

            // 检查权限
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