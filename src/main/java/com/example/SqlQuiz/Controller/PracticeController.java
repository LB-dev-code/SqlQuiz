package com.example.SqlQuiz.Controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * 自主练习控制器
 * 提供学生自主练习的REST API和页面路由
 */
@Controller
@RequestMapping("/student/practice")
public class PracticeController {

    @Autowired
    private PracticeService practiceService;

    @Autowired
    private UserService userService;

    // ==================== 页面路由 ====================

    /**
     * 练习主页/仪表板
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
     * 答题页面
     */
    @GetMapping("/round/{roundId}")
    public String practiceRound(@PathVariable Long roundId, Model model, Authentication auth) {
        String username = auth.getName();
        User student = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // 获取轮次信息
        PracticeRound round = practiceService.getRound(roundId)
                .orElseThrow(() -> new RuntimeException("Round not found"));

        Optional<PracticeAnswer> currentQuestion = practiceService.getCurrentQuestion(roundId);
        if (currentQuestion.isEmpty()) {
            return "redirect:/student/practice/dashboard";
        }

        model.addAttribute("student", student);
        model.addAttribute("roundId", roundId);
        model.addAttribute("roundNumber", round.getRoundNumber());
        model.addAttribute("currentQuestion", currentQuestion.get());

        return "student/practice-round";
    }

    /**
     * 轮次结束反馈页面
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
     * 练习历史页面
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
     * 获取错误统计和推荐题型
     */
    @GetMapping("/api/statistics")
    @ResponseBody
    public ResponseEntity<?> getStatistics(Authentication auth) {
        try {
            String username = auth.getName();
            User student = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            List<ErrorTypeStatistics> statistics = practiceService.getErrorStatistics(student);

            // 构建响应数据
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

                // 记录已掌握题型
                if (stat.getIsMastered()) {
                    masteredTypes.add(stat.getQuestionType().getDisplayName());
                }

                // 推荐高错误率且未掌握的题型
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
     * 开始练习会话（支持多选题型）
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

            // 支持多选题型
            List<Question.QuestionType> selectedTypes = null;
            boolean useMultiType = false;

            if (request != null) {
                // 检查是否使用多选题型
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
                            // 忽略无效的类型
                        }
                    }
                } else {
                    // 兼容旧的单选模式
                    String questionType = (String) request.get("questionType");
                    if (questionType != null && !questionType.isEmpty()) {
                        try {
                            selectedTypes = new ArrayList<>();
                            selectedTypes.add(Question.QuestionType.valueOf(questionType));
                        } catch (IllegalArgumentException e) {
                            // 忽略无效的类型
                        }
                    }
                }
            }

            PracticeSession session;
            if (useMultiType) {
                session = practiceService.startSession(student, selectedTypes);
            } else {
                // 兼容旧API
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
     * 开始新一轮练习
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

            PracticeRound round = practiceService.startNewRound(sessionId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("roundId", round.getId());
            response.put("roundNumber", round.getRoundNumber());
            response.put("totalQuestions", round.getTotalQuestions());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to start round: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取当前题目（支持通过索引指定）
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
                // 通过索引获取指定题目
                question = practiceService.getQuestionByIndex(roundId, index);
                System.out.println("[题目查询] roundId=" + roundId + ", index=" + index);
            } else {
                // 获取当前未回答的题目
                question = practiceService.getCurrentQuestion(roundId);
                System.out.println("[题目查询] roundId=" + roundId + ", 获取当前题目");
            }

            if (question.isEmpty()) {
                System.out.println("[题目查询] 未找到题目");
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "hasMore", false,
                        "message", "No more questions in this round"
                ));
            }

            PracticeAnswer q = question.get();
            System.out.println("[题目查询] 题目信息:");
            System.out.println("  - answerId: " + q.getId());
            System.out.println("  - questionIndex: " + q.getQuestionIndex());
            System.out.println("  - title: " + q.getQuestionTitle());
            System.out.println("  - tablePrefix: " + q.getTablePrefix());
            System.out.println("  - hasSetupSql: " + (q.getSetupSql() != null && !q.getSetupSql().trim().isEmpty()));
            if (q.getSetupSql() != null && !q.getSetupSql().trim().isEmpty()) {
                System.out.println("  - setupSql(前100字符): " + q.getSetupSql().substring(0, Math.min(100, q.getSetupSql().length())));
            }

            // 动态获取真实数据库数据来生成databaseContext（保证前端显示与实际数据一致）
            String realDatabaseContext = generateRealDatabaseContext(q);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("hasMore", true);
            response.put("answerId", q.getId());
            response.put("questionIndex", q.getQuestionIndex());
            response.put("title", q.getQuestionTitle());
            response.put("content", q.getQuestionContent());
            response.put("databaseContext", realDatabaseContext);  // 使用真实数据
            response.put("setupSql", q.getSetupSql());
            response.put("tablePrefix", q.getTablePrefix());
            response.put("studentSql", q.getStudentSql()); // 已提交的SQL
            response.put("questionType", q.getQuestionType() != null ? q.getQuestionType().name() : null);
            response.put("questionTypeDisplay", q.getQuestionType() != null ? q.getQuestionType().getDisplayName() : null);
            response.put("difficulty", q.getDifficultyLevel() != null ? q.getDifficultyLevel().name() : null);
            response.put("difficultyDisplay", q.getDifficultyLevel() != null ? q.getDifficultyLevel().getDisplayName() : null);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("[题目查询] 错误: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to get question: " + e.getMessage()
            ));
        }
    }

    /**
     * 提交答案
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

            // 解析保存的查询结果
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("isCorrect", answer.getIsCorrect());
            response.put("score", answer.getScore());
            response.put("feedback", answer.getAiFeedback());
            response.put("expectedSql", answer.getExpectedSql());

            // 从保存的executionResult中提取查询结果数据
            if (answer.getExecutionResult() != null && !answer.getExecutionResult().isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode resultNode = mapper.readTree(answer.getExecutionResult());

                    // 添加查询结果数据到响应
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
                    // 解析失败，返回空数据
                    response.put("data", List.of());
                    response.put("rowCount", 0);
                }
            } else {
                // 没有结果数据
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
     * 结束轮次
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
     * 结束练习会话
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
     * 清理未完成的会话（每次进入Dashboard时调用）
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
     * 获取练习历史
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
     * 在沙库中运行SQL（用于Run按钮，不保存答案）
     * 使用沙库机制执行SQL，保护主数据库不被修改
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

            // 获取答案信息
            PracticeAnswer answer = practiceService.getAnswerRepository().findById(answerId)
                    .orElseThrow(() -> new RuntimeException("Answer not found"));

            // 创建沙库并执行SQL
            com.example.SqlQuiz.service.SandboxDatabaseService sandboxService =
                    practiceService.getSandboxService();
            sandbox = sandboxService.createPracticeSandbox(
                    answer.getRound().getSession().getStudent().getId(),
                    answerId
            );

            // 使用setupSql初始化沙库（确保与题目描述的databaseContext一致）
            String tablePrefix = answer.getTablePrefix();
            String setupSql = answer.getSetupSql();

            if (setupSql != null && !setupSql.trim().isEmpty()) {
                // 使用题目保存时的setupSql来初始化沙库，确保数据一致
                sandboxService.executeSetupSql(sandbox, setupSql);
            } else if (tablePrefix != null && !tablePrefix.isEmpty()) {
                // 兼容旧数据：如果没有setupSql，尝试从testdb克隆
                sandboxService.cloneTablesFromTestDB(sandbox, tablePrefix);
            }

            // 获取表前缀并传递给沙库执行方法
            // setupSql 中的表名带前缀（quiz_q_123_teacher）
            // 学生输入的表名不带前缀（teacher）
            // 需要系统映射：teacher -> quiz_q_123_teacher

            // 在沙库中执行SQL，传入 tablePrefix 进行自动映射
            com.example.SqlQuiz.service.SandboxDatabaseService.SqlExecutionResult result =
                    sandboxService.executeInSandbox(sandbox, sql, tablePrefix);

            // 构建响应
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
            // 记录详细错误
            System.err.println("[runSqlInSandbox] SQL执行失败:");
            System.err.println("  异常类型: " + e.getClass().getName());
            System.err.println("  异常信息: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to run SQL: " + e.getMessage()
            ));
        } finally {
            // 清理沙库
            if (sandbox != null) {
                try {
                    com.example.SqlQuiz.service.SandboxDatabaseService sandboxService =
                            practiceService.getSandboxService();
                    sandboxService.closeConnection(sandbox);
                    sandboxService.cleanupSandbox(sandbox.getDatabaseName());
                } catch (Exception e) {
                    // 忽略清理错误
                }
            }
        }
    }

    /**
     * 从 setupSQL 动态生成真实的数据库表格展示（Markdown格式）
     * 流程：创建临时沙库 -> 执行setupSQL -> 查询数据 -> 生成Markdown -> 清理沙库
     * 这样可以保证前端显示的表格数据与学生实际执行SQL时的数据完全一致
     */
    private String generateRealDatabaseContext(PracticeAnswer answer) {
        String setupSql = answer.getSetupSql();
        String tablePrefix = answer.getTablePrefix();
        
        System.out.println("[generateRealDatabaseContext] ======== 开始生成真实数据 ========");
        System.out.println("[generateRealDatabaseContext] answerId: " + answer.getId());
        System.out.println("[generateRealDatabaseContext] tablePrefix: " + tablePrefix);
        System.out.println("[generateRealDatabaseContext] setupSql是否为空: " + (setupSql == null || setupSql.trim().isEmpty()));
        
        // 如果没有setupSQL，返回原始的databaseContext
        if (setupSql == null || setupSql.trim().isEmpty()) {
            System.out.println("[generateRealDatabaseContext] ⚠️ setupSQL为空，返回原始databaseContext");
            return answer.getDatabaseContext();
        }
        
        System.out.println("[generateRealDatabaseContext] setupSql前200字符: " + setupSql.substring(0, Math.min(200, setupSql.length())));
        
        com.example.SqlQuiz.entity.SandboxContext sandbox = null;
        try {
            // 1. 创建临时沙库
            com.example.SqlQuiz.service.SandboxDatabaseService sandboxService = 
                    practiceService.getSandboxService();
            sandbox = sandboxService.createAISandbox();
            System.out.println("[generateRealDatabaseContext] 沙库创建成功: " + sandbox.getDatabaseName());
            
            // 2. 执行setupSQL创建表和数据
            sandboxService.executeSetupSql(sandbox, setupSql);
            System.out.println("[generateRealDatabaseContext] setupSQL执行成功");
            
            // 3. 查询所有表的数据并生成Markdown
            StringBuilder markdown = new StringBuilder();
            java.sql.Connection conn = sandbox.getConnection();
            
            // 获取沙库中所有表
            java.sql.DatabaseMetaData metaData = conn.getMetaData();
            java.sql.ResultSet tables = metaData.getTables(sandbox.getDatabaseName(), null, "%", new String[]{"TABLE"});
            
            int tableCount = 0;
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                tableCount++;
                System.out.println("[generateRealDatabaseContext] 发现表: " + tableName);
                
                // 生成无前缀的显示表名（quiz_q_123_students -> students）
                String displayTableName = tableName;
                if (tablePrefix != null && tableName.startsWith(tablePrefix + "_")) {
                    displayTableName = tableName.substring(tablePrefix.length() + 1);
                }
                System.out.println("[generateRealDatabaseContext] 显示表名: " + displayTableName);
                
                markdown.append(displayTableName).append(" table:\n\n");
                
                // 查询表数据
                try (java.sql.Statement stmt = conn.createStatement();
                     java.sql.ResultSet rs = stmt.executeQuery("SELECT * FROM `" + tableName + "`")) {
                    
                    java.sql.ResultSetMetaData rsmd = rs.getMetaData();
                    int columnCount = rsmd.getColumnCount();
                    System.out.println("[generateRealDatabaseContext] 表 " + displayTableName + " 列数: " + columnCount);
                    
                    // 生成表头
                    markdown.append("|");
                    for (int i = 1; i <= columnCount; i++) {
                        String colName = rsmd.getColumnName(i);
                        markdown.append(" ").append(colName).append(" |");
                        System.out.println("[generateRealDatabaseContext]   列名: " + colName);
                    }
                    markdown.append("\n|");
                    for (int i = 1; i <= columnCount; i++) {
                        markdown.append("----|" );
                    }
                    markdown.append("\n");
                    
                    // 生成数据行
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
                    System.out.println("[generateRealDatabaseContext] 表 " + displayTableName + " 数据行数: " + rowCount);
                }
                
                markdown.append("\n");
            }
            
            tables.close();
            System.out.println("[generateRealDatabaseContext] 总表数: " + tableCount);
            
            String result = markdown.toString().trim();
            System.out.println("[generateRealDatabaseContext] ✅ 生成真实数据成功，长度: " + result.length());
            System.out.println("[generateRealDatabaseContext] 生成内容前500字符: " + result.substring(0, Math.min(500, result.length())));
            
            if (result.isEmpty()) {
                System.out.println("[generateRealDatabaseContext] ⚠️ 生成结果为空，返回原始databaseContext");
                return answer.getDatabaseContext();
            }
            
            return result;
            
        } catch (Exception e) {
            System.err.println("[generateRealDatabaseContext] ❌ 生成真实数据失败: " + e.getMessage());
            e.printStackTrace();
            // 失败时返回原始的databaseContext
            return answer.getDatabaseContext();
        } finally {
            // 4. 清理沙库
            if (sandbox != null) {
                try {
                    com.example.SqlQuiz.service.SandboxDatabaseService sandboxService = 
                            practiceService.getSandboxService();
                    sandboxService.closeConnection(sandbox);
                    sandboxService.cleanupSandbox(sandbox.getDatabaseName());
                    System.out.println("[generateRealDatabaseContext] 沙库清理完成");
                } catch (Exception e) {
                    // 忽略清理错误
                }
            }
        }
    }

}
