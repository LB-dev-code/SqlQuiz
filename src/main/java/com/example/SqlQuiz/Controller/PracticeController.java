package com.example.SqlQuiz.Controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.SqlQuiz.entity.ErrorTypeStatistics;
import com.example.SqlQuiz.entity.PracticeAnswer;
import com.example.SqlQuiz.entity.PracticeRound;
import com.example.SqlQuiz.entity.PracticeSession;
import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.entity.User;
import com.example.SqlQuiz.service.PracticeService;
import com.example.SqlQuiz.service.SandboxDatabaseService;
import com.example.SqlQuiz.service.UserService;

/**
 * Self-practice Controller
 * Provides REST API and page routing for student self-practice
 */
@Controller
@RequestMapping("/student/practice")
public class PracticeController {

    private static final Logger log = LoggerFactory.getLogger(PracticeController.class);

    @Autowired
    private PracticeService practiceService;

    @Autowired
    private UserService userService;

    // ==================== Page Routes ====================

    /**
     * Practice main page/dashboard
     */
    @GetMapping("/dashboard")
    public String practiceDashboard(Model model, Authentication auth) {
        String username = auth.getName();
        User student = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Student not found: " + username));

        // 获取错误统计
        List<ErrorTypeStatistics> statistics = practiceService.getErrorStatistics(student);
        if (statistics == null) {
            statistics = new ArrayList<>();
        }

        // 获取练习历史
        List<PracticeSession> history = practiceService.getPracticeHistory(student);
        if (history == null) {
            history = new ArrayList<>();
        }

        // 检查是否有进行中的会话
        Optional<PracticeSession> activeSession = practiceService.getActiveSession(student);

        // 获取当前活跃轮次（如果有）
        Optional<PracticeRound> activeRound = Optional.empty();
        if (activeSession.isPresent()) {
            activeRound = practiceService.getCurrentRound(activeSession.get().getId());
        }

        // 获取所有题型供选择
        Question.QuestionType[] questionTypes = Question.QuestionType.values();

        model.addAttribute("student", student);
        model.addAttribute("statistics", statistics);
        model.addAttribute("history", history);
        model.addAttribute("activeSession", activeSession.orElse(null));
        model.addAttribute("activeRound", activeRound.orElse(null));
        model.addAttribute("questionTypes", questionTypes);

        return "student/practice-dashboard";
    }

    /**
     * Answer page
     */
    @GetMapping("/round/{roundId}")
    public String practiceRound(@PathVariable Long roundId, Model model, Authentication auth) {
        String username = auth.getName();
        User student = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // Get round information
        PracticeRound round = practiceService.getRound(roundId)
                .orElseThrow(() -> new RuntimeException("Round not found"));

        model.addAttribute("student", student);
        model.addAttribute("roundId", roundId);
        model.addAttribute("roundNumber", round.getRoundNumber());


        return "student/practice-round";
    }

    /**
     * Round end feedback page
     */
    @GetMapping("/feedback/{roundId}")
    public String practiceFeedback(@PathVariable Long roundId, Model model, Authentication auth) {
        String username = auth.getName();
        User student = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        model.addAttribute("student", student);
        model.addAttribute("roundId", roundId);

        return "student/practice-feedback";
    }




    /**
     * Start practice session (supports multi-type selection)
     */
    @PostMapping("/api/start")
    @ResponseBody
    public ResponseEntity<?> startPractice(
            @RequestBody(required = false) Map<String, Object> request,
            Authentication auth) {
        try {
            String username = auth.getName();
            User student = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            // Support multi-type question selection
            List<Question.QuestionType> selectedTypes = null;
            boolean useMultiType = false;

            if (request != null) {
                // Check if using multi-type questions
                Object selectedTypesObj = request.get("selectedTypes");
                if (selectedTypesObj instanceof List) {
                    useMultiType = true;
                    @SuppressWarnings("unchecked")
                    List<String> typeStrings = (List<String>) selectedTypesObj;
                    selectedTypes = new ArrayList<>();
                    for (String typeStr : typeStrings) {
                        try {
                            if (typeStr != null && !typeStr.isEmpty()) {
                                selectedTypes.add(Question.QuestionType.valueOf(typeStr));
                            }
                        } catch (IllegalArgumentException e) {
                            // Ignore invalid types
                        }
                    }
                } else {
                    // Compatible with old single selection mode
                    String questionType = (String) request.get("questionType");
                    if (questionType != null && !questionType.isEmpty()) {
                        try {
                            selectedTypes = new ArrayList<>();
                            selectedTypes.add(Question.QuestionType.valueOf(questionType));
                        } catch (IllegalArgumentException e) {
                            // Ignore invalid types
                        }
                    }
                }
            }

            PracticeSession session;
            if (useMultiType) {
                session = practiceService.startSession(student, selectedTypes);
            } else {
                Question.QuestionType targetType = (selectedTypes != null && !selectedTypes.isEmpty())
                        ? selectedTypes.get(0) : null;
                session = practiceService.startSession(student, targetType);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sessionId", session.getId());
            response.put("message", "Practice session started");
            response.put("selectedTypes", session.getSelectedTypes().stream()
                    .map(Question.QuestionType::name)
                    .toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to start practice: " + e.getMessage()
            ));
        }
    }

    /**
     * Start new practice round
     * 先提交事务创建round，然后在事务外调用AI生成题目
     */
    @PostMapping("/api/round/start")
    @ResponseBody
    public ResponseEntity<?> startRound(
            @RequestBody Map<String, Long> request,
            Authentication auth) {
        try {
            Long sessionId = request.get("sessionId");
            if (sessionId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Session ID is required"
                ));
            }

            // 步骤1: 创建round（事务内快速完成）
            PracticeRound round = practiceService.startNewRound(sessionId);

            // 步骤2: 在事务外异步生成题目（避免长时间持有数据库锁）
            User student = userService.findByUsername(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            // 获取题型选择
            List<Question.QuestionType> selectedTypes = round.getSession().getSelectedTypes();
            if (selectedTypes == null || selectedTypes.isEmpty()) {
                selectedTypes = new ArrayList<>();
                if (round.getSession().getTargetQuestionType() != null) {
                    selectedTypes.add(round.getSession().getTargetQuestionType());
                }
            }

            // 在新线程中生成题目（事务外）
            final Long roundId = round.getId();
            final List<Question.QuestionType> finalSelectedTypes = selectedTypes;
            Thread genThread = new Thread(() -> {
                try {
                    practiceService.generateQuestionsForRoundAfterCommit(roundId, student, finalSelectedTypes);
                } catch (Exception e) {
                    System.err.println("[异步生成题目] 失败 - roundId: " + roundId + ", error: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            genThread.setName("QuestionGen-" + roundId);
            genThread.start();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("roundId", round.getId());
            response.put("roundNumber", round.getRoundNumber());
            response.put("totalQuestions", round.getTotalQuestions());
            response.put("message", "Round created, questions are being generated...");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to start round: " + e.getMessage()
            ));
        }
    }

    /**
     * Get current question (supports specifying by index)
     */
    @GetMapping("/api/question/{roundId}")
    @ResponseBody
    public ResponseEntity<?> getCurrentQuestion(
            @PathVariable Long roundId,
            @RequestParam(required = false) Integer index,
            Authentication auth) {
        try {
            Optional<PracticeAnswer> question;

            if (index != null) {
                // Get specified question by index
                question = practiceService.getQuestionByIndex(roundId, index);
                System.out.println("[Question Query] roundId=" + roundId + ", index=" + index);
            } else {
                // Get current unanswered question
                question = practiceService.getCurrentQuestion(roundId);
                System.out.println("[Question Query] roundId=" + roundId + ", getting current question");
            }

            if (question.isEmpty()) {
                // 判断是"题目还在生成中"还是"轮次真正完成"
                PracticeRound round = practiceService.getRound(roundId).orElse(null);
                if (round != null) {
                    // 检查当前轮次已生成的题目数量
                    List<PracticeAnswer> existingAnswers = practiceService.getAnswersByRound(roundId);
                    int generatedCount = existingAnswers.size();
                    int expectedCount = PracticeRound.QUESTIONS_PER_ROUND; // 10

                    System.out.println("[Question Query] 已生成题目数: " + generatedCount + ", 预期: " + expectedCount);

                    // 如果已生成数量不足10题，说明还在生成中
                    if (generatedCount < expectedCount && round.isInProgress()) {
                        System.out.println("[Question Query] 题目还在生成中...");
                        return ResponseEntity.ok(Map.of(
                                "success", true,
                                "hasMore", false,
                                "generating", true,
                                "message", "Questions are still being generated (" + generatedCount + "/" + expectedCount + ")"
                        ));
                    }
                }

                System.out.println("[Question Query] 轮次完成，没有更多题目");
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "hasMore", false,
                        "generating", false,
                        "message", "No more questions in this round"
                ));
            }

            PracticeAnswer q = question.get();
            System.out.println("[Question Query] Question info:");
            System.out.println("  - answerId: " + q.getId());
            System.out.println("  - questionIndex: " + q.getQuestionIndex());
            System.out.println("  - title: " + q.getQuestionTitle());
            System.out.println("  - tablePrefix: " + q.getTablePrefix());
            System.out.println("  - hasSetupSql: " + (q.getSetupSql() != null && !q.getSetupSql().trim().isEmpty()));
            if (q.getSetupSql() != null && !q.getSetupSql().trim().isEmpty()) {
                System.out.println("  - setupSql(first 100 chars): " + q.getSetupSql().substring(0, Math.min(100, q.getSetupSql().length())));
            }

            // Dynamically get real database data to generate databaseContext (ensures frontend display matches actual data)
            String realDatabaseContext = generateRealDatabaseContext(q);
            System.out.println("[Question Query] realDatabaseContext source: " + 
                (realDatabaseContext != null && !realDatabaseContext.equals(q.getDatabaseContext()) ? "TEST_DB (real)" : "AI_GENERATED (fallback)"));
            System.out.println("[Question Query] databaseContext length: " + (realDatabaseContext != null ? realDatabaseContext.length() : 0));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("hasMore", true);
            response.put("answerId", q.getId());
            response.put("questionIndex", q.getQuestionIndex());
            response.put("title", q.getQuestionTitle());
            response.put("content", q.getQuestionContent());
            response.put("databaseContext", realDatabaseContext);  // Use real data
            response.put("setupSql", q.getSetupSql());
            response.put("tablePrefix", q.getTablePrefix());
            response.put("studentSql", q.getStudentSql()); // Submitted SQL
            response.put("questionType", q.getQuestionType() != null ? q.getQuestionType().name() : null);
            response.put("questionTypeDisplay", q.getQuestionType() != null ? q.getQuestionType().getDisplayName() : null);
            response.put("difficulty", q.getDifficultyLevel() != null ? q.getDifficultyLevel().name() : null);
            response.put("difficultyDisplay", q.getDifficultyLevel() != null ? q.getDifficultyLevel().getDisplayName() : null);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("[Question Query] Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to get question: " + e.getMessage()
            ));
        }
    }

    /**
     * Save answer (without scoring)
     */
    @PostMapping("/api/save-answer")
    public ResponseEntity<Map<String, Object>> saveAnswer(@RequestBody Map<String, Object> request, Authentication auth) {
        try {
            Long answerId = Long.valueOf(request.get("answerId").toString());
            String sql = (String) request.get("sql");

            practiceService.saveAnswerOnly(answerId, sql);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Answer saved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[保存答案] Error saving answer", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Finish round and score all answers
     */
    @PostMapping("/api/finish-round")
    public ResponseEntity<Map<String, Object>> finishRound(@RequestBody Map<String, Object> request, Authentication auth) {
        try {
            Long roundId = Long.valueOf(request.get("roundId").toString());
            log.info("[完成轮次] 收到请求 - roundId={}, user={}", roundId, auth.getName());
            practiceService.scoreAllAnswersInRound(roundId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Round completed and scored");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[完成轮次] Error finishing round", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }


    /**
     * End round
     */
    @PostMapping("/api/round/end")
    @ResponseBody
    public ResponseEntity<?> endRound(
            @RequestBody Map<String, Long> request,
            Authentication auth) {
        try {
            Long roundId = request.get("roundId");
            if (roundId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Round ID is required"
                ));
            }

            PracticeRound round = practiceService.endRound(roundId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("correctCount", round.getCorrectCount());
            response.put("totalQuestions", round.getTotalQuestions());
            response.put("accuracy", round.getAccuracy());
            response.put("feedback", round.getFeedback());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to end round: " + e.getMessage()
            ));
        }
    }



    /**
     * Cleanup incomplete sessions (called every time Dashboard is entered)
     */
    @PostMapping("/api/cleanup")
    @ResponseBody
    public ResponseEntity<?> cleanupSessions(Authentication auth) {
        try {
            String username = auth.getName();
            User student = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            int cleaned = practiceService.cleanupIncompleteSessions(student);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "cleaned", cleaned
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to cleanup sessions: " + e.getMessage()
            ));
        }
    }



    /**
     * Run SQL in sandbox (for Run button, does not save answer)
     * Uses sandbox mechanism to execute SQL, protecting main database from modifications
     */
    @PostMapping("/api/run-sql")
    @ResponseBody
    public ResponseEntity<?> runSqlInSandbox(
            @RequestBody Map<String, Object> request,
            Authentication auth) {
        com.example.SqlQuiz.entity.SandboxContext sandbox = null;
        try {
            Long answerId = Long.valueOf(request.get("answerId").toString());
            String sql = (String) request.get("sql");

            if (sql == null || sql.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "SQL cannot be empty"
                ));
            }

            // Get answer information
            PracticeAnswer answer = practiceService.getAnswerRepository().findById(answerId)
                    .orElseThrow(() -> new RuntimeException("Answer not found"));

            // Create sandbox and execute SQL
            com.example.SqlQuiz.service.SandboxDatabaseService sandboxService =
                    practiceService.getSandboxService();
            sandbox = sandboxService.createPracticeSandbox(
                    answer.getRound().getSession().getStudent().getId(),
                    answerId
            );

            // Use setupSql to initialize sandbox (ensure consistent with question's databaseContext)
            String tablePrefix = answer.getTablePrefix();
            String setupSql = answer.getSetupSql();

            if (tablePrefix != null && !tablePrefix.isEmpty() && setupSql != null && !setupSql.trim().isEmpty()) {
                // Clone only this question's specific tables from test_db (not all tables with the prefix)
                // This ensures sandbox data is identical to what's displayed on the frontend
                sandboxService.cloneSpecificTablesFromTestDB(sandbox, tablePrefix, setupSql);
            } else if (tablePrefix != null && !tablePrefix.isEmpty()) {
                // Compatible with old data: if no setupSql, clone all tables with prefix
                sandboxService.cloneTablesFromTestDB(sandbox, tablePrefix);
            }

            // Get table prefix and pass to sandbox execution method
            // Table names in setupSql have prefix (quiz_q_123_teacher)
            // Student input table names have no prefix (teacher)
            // Need system mapping: teacher -> quiz_q_123_teacher

            // Execute SQL in sandbox, passing tablePrefix for automatic mapping
            com.example.SqlQuiz.service.SandboxDatabaseService.SqlExecutionResult result =
                    sandboxService.executeInSandbox(sandbox, sql, tablePrefix);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("data", result.getResultData());
            response.put("rowCount", result.getRowCount());
            response.put("executionTimeMs", result.getExecutionTimeMs());
            if (result.getErrorMessage() != null) {
                response.put("error", result.getErrorMessage());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            // Log detailed error
            System.err.println("[runSqlInSandbox] SQL execution failed:");
            System.err.println("  Exception type: " + e.getClass().getName());
            System.err.println("  Exception message: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to run SQL: " + e.getMessage()
            ));
        } finally {
            // Cleanup sandbox
            if (sandbox != null) {
                try {
                    com.example.SqlQuiz.service.SandboxDatabaseService sandboxService =
                            practiceService.getSandboxService();
                    sandboxService.closeConnection(sandbox);
                    sandboxService.cleanupSandbox(sandbox.getDatabaseName());
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    /**
     * Generate real database table display from mysql_test_db (Markdown format)
     * Directly queries test database using tablePrefix, no sandbox needed.
     * This ensures frontend displayed table data is completely consistent with student's actual SQL execution data.
     */
    private String generateRealDatabaseContext(PracticeAnswer answer) {
        String tablePrefix = answer.getTablePrefix();
        String setupSql = answer.getSetupSql();

        // If no tablePrefix, return original databaseContext
        if (tablePrefix == null || tablePrefix.trim().isEmpty()) {
            return answer.getDatabaseContext();
        }

        // Query real data from mysql_test_db, filtered by this question's specific tables
        SandboxDatabaseService sandboxService = practiceService.getSandboxService();
        String realMarkdown = (setupSql != null && !setupSql.trim().isEmpty())
                ? sandboxService.generateMarkdownFromTestDB(tablePrefix, setupSql)
                : sandboxService.generateMarkdownFromTestDB(tablePrefix);

        // Fallback to AI-generated databaseContext if no real data found
        return realMarkdown != null ? realMarkdown : answer.getDatabaseContext();
    }

}
