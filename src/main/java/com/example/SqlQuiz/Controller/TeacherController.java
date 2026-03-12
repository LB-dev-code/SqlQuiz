package com.example.SqlQuiz.Controller;

import com.example.SqlQuiz.entity.*;
import com.example.SqlQuiz.repository.QuestionAnswerRepository;
import com.example.SqlQuiz.repository.SubmissionRepository;
import com.example.SqlQuiz.service.GLMService;
import com.example.SqlQuiz.service.QuizService;
import com.example.SqlQuiz.service.SetupSqlExecutorService;
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
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;

@Controller
@RequestMapping("/teacher")
public class TeacherController {

    @Autowired
    private QuizService quizService;


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

            // Issue 2: Validate start time must be in the future
            LocalDateTime now = LocalDateTime.now();
            if (startDateTime != null && startDateTime.isBefore(now)) {
                errors.add("Activation Date (Start Time) cannot be in the past");
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

            // Validate end time is after start time
            if (startDateTime != null && endDateTime != null && endDateTime.isBefore(startDateTime)) {
                errors.add("Termination Date (End Time) must be after Activation Date (Start Time)");
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

            // Create quiz
            User teacher = (User) auth.getPrincipal();
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
    public Map<String, Object> editQuestionPage(@PathVariable Long questionId, Authentication auth) {
        Map<String, Object> result = new HashMap<>();
        User teacher = (User) auth.getPrincipal();
        Optional<Question> questionOpt = quizService.getQuestionById(questionId);

        if (questionOpt.isPresent()) {
            Question question = questionOpt.get();
            // Check permission: ensure it's the creator of the quiz this question belongs to
            if (question.getQuiz().getTeacher().getId().equals(teacher.getId())) {
                // Return only necessary fields to avoid circular reference
                result.put("id", question.getId());
                result.put("content", question.getContent());
                result.put("description", question.getDescription());
                result.put("expectedSql", question.getExpectedSql());
                result.put("setupSql", question.getSetupSql());
                result.put("databaseContext", question.getDatabaseContext());
                result.put("score", question.getScore());
                result.put("questionType", question.getQuestionType() != null ? question.getQuestionType().name() : null);
                result.put("difficultyLevel", question.getDifficultyLevel() != null ? question.getDifficultyLevel().name() : null);
                return result;
            }
        }
        return result;
    }

    // Get table data for question
    @GetMapping("/question/{questionId}/table-data")
    @ResponseBody
    public Map<String, Object> getQuestionTableData(@PathVariable Long questionId, Authentication auth) {
        Map<String, Object> response = new HashMap<>();
        try {
            User teacher = (User) auth.getPrincipal();
            Optional<Question> questionOpt = quizService.getQuestionById(questionId);

            if (!questionOpt.isPresent()) {
                response.put("success", false);
                response.put("error", "Question does not exist");
                return response;
            }

            Question question = questionOpt.get();
            // Check permission
            if (!question.getQuiz().getTeacher().getId().equals(teacher.getId())) {
                response.put("success", false);
                response.put("error", "No permission");
                return response;
            }

            // Get table metadata by questionId
            List<com.example.SqlQuiz.entity.QuizTableMetadata> metadataList = 
                tableMetadataService.getByQuestionId(questionId);
            
            List<Map<String, Object>> tables = new ArrayList<>();
            
            if (!metadataList.isEmpty()) {
                // If metadata exists, get tables from testdb
                for (com.example.SqlQuiz.entity.QuizTableMetadata metadata : metadataList) {
                    String tablePrefix = metadata.getTablePrefix();
                    List<Map<String, Object>> tableData = getTableDataByPrefix(tablePrefix);
                    
                    for (Map<String, Object> table : tableData) {
                        tables.add(table);
                    }
                }
            } else if (question.getSetupSql() != null && !question.getSetupSql().trim().isEmpty()) {
                // If no metadata, try to extract table prefix from setupSql
                System.out.println("[getQuestionTableData] No metadata found, trying to extract from setupSql");
                String setupSql = question.getSetupSql();
                String extractedPrefix = extractTablePrefixFromSql(setupSql);
                
                if (extractedPrefix != null) {
                    System.out.println("[getQuestionTableData] Extracted prefix: " + extractedPrefix);
                    List<Map<String, Object>> tableData = getTableDataByPrefix(extractedPrefix);
                    tables.addAll(tableData);
                    
                    // Create metadata if tables found
                    if (!tableData.isEmpty()) {
                        try {
                            tableMetadataService.createMetadata(extractedPrefix, questionId, teacher.getId());
                            System.out.println("[getQuestionTableData] Created metadata for prefix: " + extractedPrefix);
                        } catch (Exception e) {
                            System.err.println("[getQuestionTableData] Failed to create metadata: " + e.getMessage());
                        }
                    }
                } else {
                    System.out.println("[getQuestionTableData] Could not extract prefix from setupSql");
                }
            }

            response.put("success", true);
            response.put("tables", tables);
            return response;

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    // Helper method to get table data by prefix
    private List<Map<String, Object>> getTableDataByPrefix(String tablePrefix) {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection connection = setupSqlExecutorService.getTestDataSource().getConnection()) {
            // Query all tables with this prefix
            String query = "SELECT TABLE_NAME FROM information_schema.TABLES " +
                         "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE '" + tablePrefix + "%'";
            
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(query)) {
                
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    Map<String, Object> tableData = getTableData(tableName);
                    if (tableData != null) {
                        result.add(tableData);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to get table data by prefix: " + e.getMessage());
        }
        return result;
    }

    // Helper method to extract table prefix from setupSql
    private String extractTablePrefixFromSql(String setupSql) {
        try {
            // Look for CREATE TABLE `quiz_q_xxx...` pattern
            // Pattern 1: CREATE TABLE `quiz_q_xxxx_timestamp_tablename`
            java.util.regex.Pattern pattern1 = java.util.regex.Pattern.compile(
                "CREATE\\s+TABLE\\s+`(quiz_q_[a-z0-9]+_[0-9]+)_",
                java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher matcher1 = pattern1.matcher(setupSql);
            
            if (matcher1.find()) {
                String prefix = matcher1.group(1);
                System.out.println("[extractTablePrefixFromSql] Found prefix (pattern1): " + prefix);
                return prefix;
            }
            
            // Pattern 2: CREATE TABLE quiz_q_xxx (without backticks)
            java.util.regex.Pattern pattern2 = java.util.regex.Pattern.compile(
                "CREATE\\s+TABLE\\s+(quiz_q_[a-z0-9]+_[0-9]+)_",
                java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher matcher2 = pattern2.matcher(setupSql);
            
            if (matcher2.find()) {
                String prefix = matcher2.group(1);
                System.out.println("[extractTablePrefixFromSql] Found prefix (pattern2): " + prefix);
                return prefix;
            }
            
            System.out.println("[extractTablePrefixFromSql] No prefix pattern matched in SQL: " + 
                setupSql.substring(0, Math.min(100, setupSql.length())));
            
        } catch (Exception e) {
            System.err.println("Failed to extract prefix from setupSql: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // Helper method to get single table data
    private Map<String, Object> getTableData(String tableName) {
        Map<String, Object> tableInfo = new HashMap<>();
        try (Connection connection = setupSqlExecutorService.getTestDataSource().getConnection()) {
            String query = "SELECT * FROM " + tableName;
            System.out.println("[getTableData] Querying table: " + tableName);
            
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(query)) {
                
                java.sql.ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                System.out.println("[getTableData] Column count: " + columnCount);
                
                // Get column names
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    String colName = metaData.getColumnName(i);
                    columns.add(colName);
                    System.out.println("[getTableData] Column " + i + ": " + colName);
                }
                tableInfo.put("tableName", tableName);
                tableInfo.put("columns", columns);
                
                // Get data rows
                List<List<Object>> rows = new ArrayList<>();
                int rowCount = 0;
                while (rs.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        Object value = rs.getObject(i);
                        row.add(value);
                    }
                    rows.add(row);
                    rowCount++;
                }
                System.out.println("[getTableData] Row count: " + rowCount);
                tableInfo.put("rows", rows);
                
                System.out.println("[getTableData] Successfully loaded table data: " + tableName + 
                    " with " + columnCount + " columns and " + rowCount + " rows");
            }
        } catch (Exception e) {
            System.err.println("Failed to get data for table " + tableName + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
        return tableInfo;
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

    // Update question with table data
    @PostMapping("/question/{questionId}/update-with-tables")
    @ResponseBody
    @Transactional
    public Map<String, Object> updateQuestionWithTables(
            @PathVariable Long questionId,
            @RequestBody Map<String, Object> payload,
            Authentication auth) {
        Map<String, Object> response = new HashMap<>();
        try {
            User teacher = (User) auth.getPrincipal();
            Optional<Question> questionOpt = quizService.getQuestionById(questionId);

            if (!questionOpt.isPresent()) {
                response.put("success", false);
                response.put("error", "Question does not exist");
                return response;
            }

            Question question = questionOpt.get();

            // Check permission
            if (!question.getQuiz().getTeacher().getId().equals(teacher.getId())) {
                response.put("success", false);
                response.put("error", "No permission");
                return response;
            }

            // Get basic question info from payload
            String content = (String) payload.get("content");
            String description = (String) payload.get("description");
            String expectedSql = (String) payload.get("expectedSql");
            
            // Get tables with proper type casting
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tables = (List<Map<String, Object>>) payload.get("tables");

            // Get or generate table prefix
            List<com.example.SqlQuiz.entity.QuizTableMetadata> metadataList = 
                tableMetadataService.getByQuestionId(questionId);
            String tablePrefix;
            
            if (!metadataList.isEmpty()) {
                // Use existing prefix
                tablePrefix = metadataList.get(0).getTablePrefix();
                // Drop old tables
                setupSqlExecutorService.dropTablesByQuestionId(questionId);
            } else {
                // Generate new prefix
                tablePrefix = tableMetadataService.generateUniqueTablePrefix();
                System.out.println("test prefix:"+tablePrefix);
            }

            // Generate setupSql from table data
            String setupSql = "";
            if (tables != null && !tables.isEmpty()) {
                setupSql = setupSqlExecutorService.generateSetupSqlFromTableData(tables, tablePrefix);
                
                // Execute setupSql to create new tables
                try {
                    setupSqlExecutorService.executeSetupSql(setupSql);
                } catch (Exception e) {
                    response.put("success", false);
                    response.put("error", "Failed to create tables: " + e.getMessage());
                    return response;
                }
                
                // Create or update metadata
                if (metadataList.isEmpty()) {
                    tableMetadataService.createMetadata(tablePrefix, questionId, teacher.getId());
                }
            }

            // Update question entity with new setupSql
            quizService.updateQuestion(
                questionId,
                content,
                question.getQuestionType(),
                description,
                question.getDatabaseContext(),
                expectedSql,
                setupSql.isEmpty() ? null : setupSql,
                question.getTestData(),
                question.getExpectedResult(),
                question.getScore(),
                question.getDifficultyLevel()
            );

            response.put("success", true);
            response.put("message", "Question and tables updated successfully");
            return response;

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("error", "Failed to update: " + e.getMessage());
            return response;
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



    // SQL test page
    @GetMapping("/sql-test")
    public String sqlTestPage(Model model, Authentication auth) {
        User teacher = (User) auth.getPrincipal();
        model.addAttribute("teacher", teacher);
        return "teacher/sql-test";
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

    @PostMapping("/api/question-answer/{qaId}/score")
    @ResponseBody
    @Transactional
    public Map<String, Object> updateQuestionAnswerScore(
            @PathVariable Long qaId,
            @RequestBody Map<String, Object> payload,
            Authentication auth) {

        Map<String, Object> response = new HashMap<>();
        try {
            User teacher = (User) auth.getPrincipal();
            double newScore = Double.parseDouble(payload.get("score").toString());

            QuestionAnswer qa = questionAnswerRepository.findById(qaId)
                    .orElseThrow(() -> new RuntimeException("QuestionAnswer not found"));

            if (!qa.getSubmission().getQuiz().getTeacher().getId().equals(teacher.getId())) {
                response.put("success", false);
                response.put("message", "No permission");
                return response;
            }

            double maxScore = qa.getQuestion().getScore();
            if (newScore < 0) newScore = 0;
            if (newScore > maxScore) newScore = maxScore;

            qa.setScore(newScore);
            qa.setIsCorrect(newScore >= maxScore);
            questionAnswerRepository.save(qa);

            quizService.calculateAndSaveTotalScore(qa.getSubmission());

            Submission updated = submissionRepository.findById(qa.getSubmission().getId()).orElse(null);

            response.put("success", true);
            response.put("score", newScore);
            response.put("isCorrect", qa.getIsCorrect());
            response.put("totalScore", updated != null ? updated.getTotalScore() : null);
            response.put("maxScore", updated != null ? updated.getMaxScore() : null);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
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

    // Delete Quiz (including questions and their tables in testdb)
    @GetMapping("/quiz/{id}/delete")
    public String deleteQuiz(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            User teacher = (User) auth.getPrincipal();
            Quiz quiz = quizService.findById(id).orElse(null);

            if (quiz == null) {
                redirectAttributes.addFlashAttribute("error", "Quiz does not exist");
                return "redirect:/teacher/quizzes";
            }

            // Check permission
            if (!quiz.getTeacher().getId().equals(teacher.getId())) {
                redirectAttributes.addFlashAttribute("error", "No permission to delete this quiz");
                return "redirect:/teacher/quizzes";
            }

            quizService.deleteQuiz(id);
            redirectAttributes.addFlashAttribute("message", "Quiz deleted successfully");
            return "redirect:/teacher/quizzes";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to delete quiz: " + e.getMessage());
            return "redirect:/teacher/quizzes";
        }
    }
}