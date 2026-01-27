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
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("hasMore", true);
            response.put("answerId", q.getId());
            response.put("questionIndex", q.getQuestionIndex());
            response.put("title", q.getQuestionTitle());
            response.put("content", q.getQuestionContent());
            response.put("databaseContext", q.getDatabaseContext());
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

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("isCorrect", answer.getIsCorrect());
            response.put("score", answer.getScore());
            response.put("feedback", answer.getAiFeedback());
            response.put("expectedSql", answer.getExpectedSql());

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
}
