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

        // 题目可能还在异步生成中，前端会通过轮询API等待
        // 不再检查 currentQuestion 是否存在，直接加载页面
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
     * Practice history page
     */
    @GetMapping("/history")
    public String practiceHistory(Model model, Authentication auth) {
        String username = auth.getName();
        User student = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        List<PracticeSession> history = practiceService.getPracticeHistory(student);

        model.addAttribute("student", student);
        model.addAttribute("history", history);

        return "student/practice-history";
    }

    // ==================== REST API ====================

    /**
     * Get error statistics and recommended question types
     */
    @GetMapping("/api/statistics")
    @ResponseBody
    public ResponseEntity<?> getStatistics(Authentication auth) {
        try {
            String username = auth.getName();
            User student = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            List<ErrorTypeStatistics> statistics = practiceService.getErrorStatistics(student);

            // Build response data
            List<Map<String, Object>> statisticsData = new ArrayList<>();
            List<String> masteredTypes = new ArrayList<>();
            List<String> recommendedTypes = new ArrayList<>();

            for (ErrorTypeStatistics stat : statistics) {
                Map<String, Object> statData = new HashMap<>();
                statData.put("questionType", stat.getQuestionType().name());
                statData.put("questionTypeDisplay", stat.getQuestionType().getDisplayName());
                statData.put("totalCount", stat.getTotalCount());
                statData.put("correctCount", stat.getCorrectCount());
                statData.put("errorCount", stat.getErrorCount());
                statData.put("accuracy", stat.getAccuracy());
                statData.put("isMastered", stat.getIsMastered());
                statData.put("errorFrequency", stat.getErrorFrequency());

                statisticsData.add(statData);

                // Record mastered question types
                if (stat.getIsMastered()) {
                    masteredTypes.add(stat.getQuestionType().getDisplayName());
                }

                // Recommend high error rate and not mastered question types
                if (!stat.getIsMastered() && stat.getErrorFrequency() >= 0.3) {
                    recommendedTypes.add(stat.getQuestionType().getDisplayName());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("statistics", statisticsData);
            response.put("masteredTypes", masteredTypes);
            response.put("recommendedTypes", recommendedTypes);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to get statistics: " + e.getMessage()
            ));
        }
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
                // Compatible with old API
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
                // 判断是“题目还在生成中”还是“轮次真正完成”
                PracticeRound round = practiceService.getRound(roundId).orElse(null);
                if (round != null) {
                    // 检查当前轮次是否有任何已生成的题目
                    List<PracticeAnswer> existingAnswers = practiceService.getAnswersByRound(roundId);
                    boolean isStillGenerating = existingAnswers.isEmpty() && round.isInProgress();
                    
                    if (isStillGenerating) {
                        System.out.println("[Question Query] 题目还在生成中...");
                        return ResponseEntity.ok(Map.of(
                                "success", true,
                                "hasMore", false,
                                "generating", true,
                                "message", "Questions are still being generated"
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
     * Submit answer (old API - deprecated, kept for compatibility)
     */
    @PostMapping("/api/answer")
    @ResponseBody
    public ResponseEntity<?> submitAnswer(
            @RequestBody Map<String, Object> request,
            Authentication auth) {
        try {
            Long answerId = Long.valueOf(request.get("answerId").toString());
            String sql = (String) request.get("sql");

            if (sql == null || sql.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "SQL cannot be empty"
                ));
            }

            PracticeAnswer answer = practiceService.submitAnswer(answerId, sql);

            // Parse saved query results
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("isCorrect", answer.getIsCorrect());
            response.put("score", answer.getScore());
            response.put("feedback", answer.getAiFeedback());
            response.put("expectedSql", answer.getExpectedSql());

            // Extract query result data from saved executionResult
            if (answer.getExecutionResult() != null && !answer.getExecutionResult().isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode resultNode = mapper.readTree(answer.getExecutionResult());

                    // Add query result data to response
                    if (resultNode.has("resultData")) {
                        response.put("data", mapper.convertValue(resultNode.get("resultData"), List.class));
                    } else {
                        response.put("data", List.of());
                    }

                    if (resultNode.has("rowCount")) {
                        response.put("rowCount", resultNode.get("rowCount").asInt());
                    } else {
                        response.put("rowCount", 0);
                    }

                    if (resultNode.has("executionTimeMs")) {
                        response.put("executionTimeMs", resultNode.get("executionTimeMs").asLong());
                    }

                    if (resultNode.has("errorMessage") && !resultNode.get("errorMessage").isNull()) {
                        response.put("error", resultNode.get("errorMessage").asText());
                    }
                } catch (Exception e) {
                    // Parse failed, return empty data
                    response.put("data", List.of());
                    response.put("rowCount", 0);
                }
            } else {
                // No result data
                response.put("data", List.of());
                response.put("rowCount", 0);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to submit answer: " + e.getMessage()
            ));
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
     * End practice session
     */
    @PostMapping("/api/end")
    @ResponseBody
    public ResponseEntity<?> endSession(
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

            PracticeSession session = practiceService.endSession(sessionId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalRounds", session.getTotalRounds());
            response.put("totalQuestions", session.getTotalQuestions());
            response.put("totalCorrect", session.getTotalCorrect());
            response.put("overallAccuracy", session.getOverallAccuracy());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to end session: " + e.getMessage()
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
     * Get practice history
     */
    @GetMapping("/api/history")
    @ResponseBody
    public ResponseEntity<?> getHistory(Authentication auth) {
        try {
            String username = auth.getName();
            User student = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Student not found"));
            List<PracticeSession> history = practiceService.getPracticeHistory(student);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("history", history.stream().map(s -> Map.of(
                    "id", s.getId(),
                    "startTime", s.getStartTime().toString(),
                    "endTime", s.getEndTime() != null ? s.getEndTime().toString() : null,
                    "status", s.getStatus().name(),
                    "totalRounds", s.getTotalRounds(),
                    "totalQuestions", s.getTotalQuestions(),
                    "totalCorrect", s.getTotalCorrect(),
                    "overallAccuracy", s.getOverallAccuracy()
            )).toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to get history: " + e.getMessage()
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

            if (setupSql != null && !setupSql.trim().isEmpty()) {
                // Use setupSql saved with the question to initialize sandbox, ensuring data consistency
                sandboxService.executeSetupSql(sandbox, setupSql);
            } else if (tablePrefix != null && !tablePrefix.isEmpty()) {
                // Compatible with old data: if no setupSql, try cloning from testdb
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
     * Generate real database table display from setupSQL (Markdown format)
     * Flow: Create temporary sandbox -> Execute setupSQL -> Query data -> Generate Markdown -> Cleanup sandbox
     * This ensures frontend displayed table data is completely consistent with student's actual SQL execution data
     */
    private String generateRealDatabaseContext(PracticeAnswer answer) {
        String setupSql = answer.getSetupSql();
        String tablePrefix = answer.getTablePrefix();

        System.out.println("[generateRealDatabaseContext] ======== Starting real data generation ========");
        System.out.println("[generateRealDatabaseContext] answerId: " + answer.getId());
        System.out.println("[generateRealDatabaseContext] tablePrefix: " + tablePrefix);
        System.out.println("[generateRealDatabaseContext] setupSql is empty: " + (setupSql == null || setupSql.trim().isEmpty()));

        // If no setupSQL, return original databaseContext
        if (setupSql == null || setupSql.trim().isEmpty()) {
            System.out.println("[generateRealDatabaseContext] ⚠️ setupSQL is empty, returning original databaseContext");
            return answer.getDatabaseContext();
        }

        System.out.println("[generateRealDatabaseContext] setupSql first 200 chars: " + setupSql.substring(0, Math.min(200, setupSql.length())));

        com.example.SqlQuiz.entity.SandboxContext sandbox = null;
        try {
            // 1. Create temporary sandbox
            com.example.SqlQuiz.service.SandboxDatabaseService sandboxService =
                    practiceService.getSandboxService();
            sandbox = sandboxService.createAISandbox();
            System.out.println("[generateRealDatabaseContext] Sandbox created successfully: " + sandbox.getDatabaseName());

            // 2. Execute setupSQL to create tables and data
            // 预处理：将 INT 升级为 BIGINT，避免AI生成的大数据（如GDP、人口等）溢出
            String processedSql = setupSql
                .replaceAll("(?i)\\bINT\\b(?!\\w)", "BIGINT")
                .replaceAll("(?i)\\bINTEGER\\b", "BIGINT");
            sandboxService.executeSetupSql(sandbox, processedSql);
            System.out.println("[generateRealDatabaseContext] setupSQL executed successfully");

            // 3. Query all table data and generate Markdown
            StringBuilder markdown = new StringBuilder();
            java.sql.Connection conn = sandbox.getConnection();

            // Get all tables in sandbox
            java.sql.DatabaseMetaData metaData = conn.getMetaData();
            java.sql.ResultSet tables = metaData.getTables(sandbox.getDatabaseName(), null, "%", new String[]{"TABLE"});

            int tableCount = 0;
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                tableCount++;
                System.out.println("[generateRealDatabaseContext] Found table: " + tableName);

                // Generate display table name without prefix (quiz_q_123_students -> students)
                String displayTableName = tableName;
                if (tablePrefix != null && tableName.startsWith(tablePrefix + "_")) {
                    displayTableName = tableName.substring(tablePrefix.length() + 1);
                }
                System.out.println("[generateRealDatabaseContext] Display table name: " + displayTableName);

                markdown.append(displayTableName).append(" table:\n\n");

                // Query table data
                try (java.sql.Statement stmt = conn.createStatement();
                     java.sql.ResultSet rs = stmt.executeQuery("SELECT * FROM `" + tableName + "`")) {

                    java.sql.ResultSetMetaData rsmd = rs.getMetaData();
                    int columnCount = rsmd.getColumnCount();
                    System.out.println("[generateRealDatabaseContext] Table " + displayTableName + " column count: " + columnCount);

                    // Generate table header
                    markdown.append("|");
                    for (int i = 1; i <= columnCount; i++) {
                        String colName = rsmd.getColumnName(i);
                        markdown.append(" ").append(colName).append(" |");
                        System.out.println("[generateRealDatabaseContext]   Column name: " + colName);
                    }
                    markdown.append("\n|");
                    for (int i = 1; i <= columnCount; i++) {
                        markdown.append("----|");
                    }
                    markdown.append("\n");

                    // Generate data rows
                    int rowCount = 0;
                    while (rs.next()) {
                        markdown.append("|");
                        for (int i = 1; i <= columnCount; i++) {
                            Object value = rs.getObject(i);
                            markdown.append(" ").append(value != null ? value.toString() : "NULL").append(" |");
                        }
                        markdown.append("\n");
                        rowCount++;
                    }
                    System.out.println("[generateRealDatabaseContext] Table " + displayTableName + " data rows: " + rowCount);
                }

                markdown.append("\n");
            }

            tables.close();
            System.out.println("[generateRealDatabaseContext] Total tables: " + tableCount);

            String result = markdown.toString().trim();
            System.out.println("[generateRealDatabaseContext] ✅ Real data generation successful, length: " + result.length());
            System.out.println("[generateRealDatabaseContext] Generated content first 500 chars: " + result.substring(0, Math.min(500, result.length())));

            if (result.isEmpty()) {
                System.out.println("[generateRealDatabaseContext] ⚠️ Generation result is empty, returning original databaseContext");
                return answer.getDatabaseContext();
            }

            return result;

        } catch (Exception e) {
            System.err.println("[generateRealDatabaseContext] ❌ Real data generation failed: " + e.getMessage());
            e.printStackTrace();
            // Return original databaseContext on failure
            return answer.getDatabaseContext();
        } finally {
            // 4. Cleanup sandbox
            if (sandbox != null) {
                try {
                    com.example.SqlQuiz.service.SandboxDatabaseService sandboxService =
                            practiceService.getSandboxService();
                    sandboxService.closeConnection(sandbox);
                    sandboxService.cleanupSandbox(sandbox.getDatabaseName());
                    System.out.println("[generateRealDatabaseContext] Sandbox cleanup completed");
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

}
