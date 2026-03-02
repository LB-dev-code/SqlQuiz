package com.example.SqlQuiz.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.SqlQuiz.dto.QuestionBlacklistItem;
import com.example.SqlQuiz.entity.ErrorTypeStatistics;
import com.example.SqlQuiz.entity.PracticeAnswer;
import com.example.SqlQuiz.entity.PracticeRound;
import com.example.SqlQuiz.entity.PracticeSession;
import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.entity.SandboxContext;
import com.example.SqlQuiz.entity.User;
import com.example.SqlQuiz.repository.ErrorTypeStatisticsRepository;
import com.example.SqlQuiz.repository.PracticeAnswerRepository;
import com.example.SqlQuiz.repository.PracticeRoundRepository;
import com.example.SqlQuiz.repository.PracticeSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 自主练习服务
 * 核心业务逻辑：错题分析、出题优先级、会话管理、答题评分
 */
@Service
public class PracticeService {

    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);

    @Autowired
    private PracticeSessionRepository sessionRepository;

    @Autowired
    private PracticeRoundRepository roundRepository;

    @Autowired
    private PracticeAnswerRepository answerRepository;

    @Autowired
    private ErrorTypeStatisticsRepository statisticsRepository;

    @Autowired
    private GLMService glmService;

    @Autowired
    private SqlValidationService sqlValidationService;

    @Autowired
    private QuestionDeduplicationService deduplicationService;

    @Autowired
    private SetupSqlExecutorService setupSqlExecutorService;

    @Autowired
    private SandboxDatabaseService sandboxService;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 会话管理 ====================

    /**
     * 开始新的练习会话（支持多选题型）
     */
    @Transactional
    public PracticeSession startSession(User student, List<Question.QuestionType> selectedTypes) {
        // 检查是否有进行中的会话
        Optional<PracticeSession> activeSession = sessionRepository.findActiveSessionByStudent(student);
        if (activeSession.isPresent()) {
            return activeSession.get();
        }

        // 初始化错误统计（如果不存在）
        initializeErrorStatisticsFromHistory(student);

        // 创建新会话
        PracticeSession session = new PracticeSession(student);

        // 设置选择的题型（多选）
        if (selectedTypes != null && !selectedTypes.isEmpty()) {
            session.setSelectedTypes(selectedTypes);
        }

        return sessionRepository.save(session);
    }

    /**
     * 开始新的练习会话（单选题型，向后兼容）
     */
    @Transactional
    public PracticeSession startSession(User student, Question.QuestionType targetType) {
        List<Question.QuestionType> selectedTypes = new ArrayList<>();
        if (targetType != null) {
            selectedTypes.add(targetType);
        }
        return startSession(student, selectedTypes);
    }

    /**
     * 获取或创建活跃会话
     */
    public Optional<PracticeSession> getActiveSession(User student) {
        return sessionRepository.findActiveSessionByStudent(student);
    }

    /**
     * 结束练习会话
     */
    @Transactional
    public PracticeSession endSession(Long sessionId) {
        PracticeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        session.completeSession();
        return sessionRepository.save(session);
    }

    // ==================== 轮次管理 ====================

    /**
     * 开始新一轮练习
     * 注意：AI调用移出事务，避免长时间持有数据库锁
     */
    @Transactional
    public PracticeRound startNewRound(Long sessionId) {
        long startTime = System.currentTimeMillis();
        log.info("========== 开始新一轮练习 ==========");
        log.info("[步骤1] 验证会话状态 - sessionId: {}", sessionId);

        PracticeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        log.info("[步骤1.1] 会话已找到 - status: {}", session.getStatus());

        if (!session.isInProgress()) {
            log.error("[步骤1.2] 会话状态不是进行中 - currentStatus: {}", session.getStatus());
            throw new RuntimeException("Session is not in progress");
        }
        log.info("[步骤1.2] 会话状态验证通过 - 进行中");

        // 检查是否有进行中的轮次
        Optional<PracticeRound> activeRound = roundRepository.findActiveRoundBySession(session);
        if (activeRound.isPresent()) {
            log.info("[步骤1.3] 发现已存在的活跃轮次 - roundId: {}", activeRound.get().getId());
            return activeRound.get();
        }
        log.info("[步骤1.3] 无活跃轮次，需要创建新轮次");

        // 获取下一轮次号
        Integer maxRoundNumber = roundRepository.findMaxRoundNumberBySession(session);
        int nextRoundNumber = (maxRoundNumber == null ? 0 : maxRoundNumber) + 1;
        log.info("[步骤1.4] 轮次号 - nextRoundNumber: {}", nextRoundNumber);

        // 获取题型选择（在事务外使用）
        List<Question.QuestionType> selectedTypes = session.getSelectedTypes();
        if (selectedTypes == null || selectedTypes.isEmpty()) {
            selectedTypes = new ArrayList<>();
            if (session.getTargetQuestionType() != null) {
                selectedTypes.add(session.getTargetQuestionType());
            }
        }
        log.info("[步骤2] 题型选择 - selectedTypes: {}", selectedTypes);

        log.info("[步骤3] 保存轮次到数据库 - 开始保存...");
        // 创建新轮次
        PracticeRound round = new PracticeRound(session, session.getStudent(), nextRoundNumber);
        // 使用 saveAndFlush 确保立即持久化，获取ID
        round = roundRepository.saveAndFlush(round);
        log.info("[步骤3.1] saveAndFlush完成 - roundId: {}", round.getId());

        // 显式刷新EntityManager以确保round实体已同步到数据库
        entityManager.flush();
        log.info("[步骤3.2] entityManager.flush完成");

        // 验证round有ID
        if (round.getId() == null) {
            log.error("[步骤3.3] Round ID为null - 保存失败");
            throw new IllegalStateException("Round ID is null after save");
        }
        log.info("[步骤3.3] Round ID验证通过 - roundId: {}", round.getId());
        log.info("[步骤3] 保存轮次完成 - 耗时: {}ms", System.currentTimeMillis() - startTime);

        session.addRound(round);
        sessionRepository.save(session);
        log.info("[步骤4] 会话更新完成");

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("========== 轮次创建完成（事务结束） - 耗时: {}ms ==========", totalTime);

        return round;
    }

    /**
     * 生成题目（非事务，在事务外调用）
     * 应在startNewRound返回后由Controller调用
     */
    public void generateQuestionsForRoundAfterCommit(Long roundId, User student, List<Question.QuestionType> selectedTypes) {
        long startTime = System.currentTimeMillis();
        log.info("========== 开始生成题目（事务外） ========== roundId: {}", roundId);

        PracticeRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> new RuntimeException("Round not found"));

        long questionGenStart = System.currentTimeMillis();
        log.info("[步骤1] 调用批量生成（事务外）...");
        generateQuestionsForRoundBatch(round, student, selectedTypes);
        log.info("[步骤1] 题目生成完成 - 耗时: {}ms", System.currentTimeMillis() - questionGenStart);

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("========== 题目生成完成 - 总耗时: {}ms ==========", totalTime);
    }

    /**
     * 获取当前轮次
     */
    public Optional<PracticeRound> getCurrentRound(Long sessionId) {
        PracticeSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) return Optional.empty();
        return roundRepository.findActiveRoundBySession(session);
    }

    /**
     * 结束当前轮次并生成反馈
     */
    @Transactional
    public PracticeRound endRound(Long roundId) {
        PracticeRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> new RuntimeException("Round not found"));

        // 从已评分的答案中重新计算统计数据
        List<PracticeAnswer> answers = answerRepository.findByRoundOrderByQuestionIndexAsc(round);
        int answeredCount = 0;
        int correctCount = 0;
        for (PracticeAnswer answer : answers) {
            if (answer.isAnswered()) {
                answeredCount++;
                if (answer.getIsCorrect() != null && answer.getIsCorrect()) {
                    correctCount++;
                }
            }
        }
        round.setCurrentQuestionIndex(answeredCount);
        round.setCorrectCount(correctCount);
        log.info("[结束轮次] roundId={}, 已答={}, 正确={}", roundId, answeredCount, correctCount);

        round.completeRound();

        // 生成轮次反馈
        String feedback = generateRoundFeedback(round);
        round.setFeedback(feedback);

        // 更新会话统计
        PracticeSession session = round.getSession();
        session.recalculateStatistics();
        sessionRepository.save(session);

        return roundRepository.save(round);
    }

    // ==================== 题目生成 ====================

    /**
     * 为轮次生成题目（核心出题算法）
     * 注意：非事务方法，AI调用不应在事务内执行
     */
    public void generateQuestionsForRound(PracticeRound round, User student, Question.QuestionType targetType) {
        List<QuestionPriority> priorities = calculateQuestionPriorities(student, targetType);

        for (int i = 0; i < PracticeRound.QUESTIONS_PER_ROUND; i++) {
            PracticeAnswer answer = new PracticeAnswer(round, i);

            // 选择题型
            QuestionPriority selected = selectQuestionType(priorities, i);

            try {
                // 使用AI生成题目
                String questionJson = glmService.generatePracticeQuestion(
                        selected.questionType.name(),
                        selected.difficulty.name()
                );

                // 解析题目信息
                JsonNode questionNode = objectMapper.readTree(cleanJsonResponse(questionJson));

                // 提取setupSql（空字符串转为null）
                String setupSql = getJsonFieldOrNull(questionNode, "setupSql");
                log.info("[题目生成] AI返回的JSON - index: {}, hasSetupSql: {}, setupSql长度: {}",
                        i, setupSql != null, setupSql != null ? setupSql.length() : 0);

                // 执行setupSQL并获取表前缀（与步骤7逻辑保持一致）
                String tablePrefix = null;
                if (setupSql != null && !setupSql.trim().isEmpty()) {
                    try {
                        log.info("[题目生成-批量] 执行setupSQL - questionIndex: {}", i);
                        tablePrefix = setupSqlExecutorService.executeSetupSql(setupSql);
                        log.info("[题目生成-批量] setupSQL执行成功 - tablePrefix: {}", tablePrefix);
                    } catch (Exception ex) {
                        log.error("[题目生成-批量] setupSQL执行失败 - questionIndex: {}, error: {}", i, ex.getMessage());
                        // 继续保存题目，但没有表前缀
                    }
                }

                // 使用setQuestionInfoWithSetup来保存setupSql和tablePrefix
                answer.setQuestionInfoWithSetup(
                        getJsonField(questionNode, "title"),
                        getJsonField(questionNode, "description"),
                        getJsonField(questionNode, "databaseContext"),
                        getJsonField(questionNode, "expectedSql"),
                        setupSql,
                        tablePrefix,  // 使用执行setupSQL后获取的tablePrefix
                        selected.questionType,
                        selected.difficulty
                );

            } catch (Exception e) {
                // 生成失败时使用默认题目（没有setupSql）
                answer.setQuestionInfoWithSetup(
                        "SQL Practice Question " + (i + 1),
                        "Practice question for " + selected.questionType.getDisplayName(),
                        "Please write the correct SQL query.",
                        "SELECT * FROM table",
                        null, // setupSql
                        null, // tablePrefix
                        selected.questionType,
                        selected.difficulty
                );
            }

            saveAnswerInNewTransaction(answer);
        }
    }

    /**
     * 计算题型优先级（核心算法）
     * 规则：学生选择 > 错误频率高+难度低 > 错误频率低+难度高
     */
    public List<QuestionPriority> calculateQuestionPriorities(User student, Question.QuestionType targetType) {
        List<QuestionPriority> priorities = new ArrayList<>();

        // 获取学生的错误统计
        List<ErrorTypeStatistics> stats = statisticsRepository.findByStudent(student);
        Map<Question.QuestionType, ErrorTypeStatistics> statsMap = stats.stream()
                .collect(Collectors.toMap(ErrorTypeStatistics::getQuestionType, s -> s));

        // 遍历所有题型
        for (Question.QuestionType type : Question.QuestionType.values()) {
            // 跳过已掌握的题型（正确率>=90%）
            ErrorTypeStatistics stat = statsMap.get(type);
            if (stat != null && stat.getIsMastered()) {
                continue;
            }

            for (Question.DifficultyLevel difficulty : Question.DifficultyLevel.values()) {
                double priorityScore = calculatePriorityScore(type, difficulty, stat, targetType);
                priorities.add(new QuestionPriority(type, difficulty, priorityScore));
            }
        }

        // 按优先级降序排序
        priorities.sort((a, b) -> Double.compare(b.priorityScore, a.priorityScore));

        return priorities;
    }

    /**
     * 计算单个题型的优先级分数
     * 优先级规则：学生选择 > 错误频率高 > 考点基础 > 考点中等 > 考点复杂 > 错误频率低
     */
    private double calculatePriorityScore(Question.QuestionType type, Question.DifficultyLevel difficulty,
                                          ErrorTypeStatistics stat, Question.QuestionType targetType) {
        double score = 0.0;

        // 1. 用户选择加权（绝对最高优先级）
        if (targetType != null && type == targetType) {
            score += 10000;
        }

        // 2. 错误频率权重（高错误频率优先）
        if (stat != null) {
            double errorFrequency = stat.getErrorFrequency();
            if (errorFrequency >= 0.5) {
                // 高错误频率：+1000分
                score += 1000;
            } else if (errorFrequency < 0.3) {
                // 低错误频率：+50分（最低优先级）
                score += 50;
            } else {
                // 中等错误频率：+300分
                score += 300;
            }
        } else {
            // 没有历史记录，给予中等优先级
            score += 300;
        }

        // 3. 难度权重（基础=500, 中等=300, 复杂=100）
        switch (difficulty) {
            case EASY:
                score += 500;
                break;
            case MEDIUM:
                score += 300;
                break;
            case HARD:
                score += 100;
                break;
        }

        return score;
    }

    /**
     * 根据优先级选择题型
     */
    private QuestionPriority selectQuestionType(List<QuestionPriority> priorities, int questionIndex) {
        if (priorities.isEmpty()) {
            // 默认返回基础查询
            return new QuestionPriority(Question.QuestionType.SELECT_BASIC, Question.DifficultyLevel.EASY, 0);
        }

        // 前5题选择高优先级，后5题增加一些随机性
        if (questionIndex < 5) {
            // 选择前几个高优先级的题型
            int index = Math.min(questionIndex, priorities.size() - 1);
            return priorities.get(index);
        } else {
            // 增加随机性，从前10个中随机选择
            int maxIndex = Math.min(10, priorities.size());
            int randomIndex = new Random().nextInt(maxIndex);
            return priorities.get(randomIndex);
        }
    }

    // ==================== 批量题目生成（新功能）====================

    /**
     * 批量为轮次生成题目（一次AI调用生成10题）
     * 注意：round必须是已持久化的实体（有有效ID）
     * 注意：非事务方法，AI调用不应在事务内执行
     */
    public void generateQuestionsForRoundBatch(PracticeRound round, User student, List<Question.QuestionType> selectedTypes) {
        long batchStartTime = System.currentTimeMillis();
        log.info("[题目生成] ========== 开始批量生成题目 ==========");
        log.info("[题目生成] roundId: {}, student: {}, selectedTypes: {}",
                round.getId(), student.getUsername(), selectedTypes);

        // 验证round已持久化
        if (round.getId() == null) {
            log.error("[题目生成] Round ID为null，无法生成题目");
            throw new IllegalStateException("Round must be persisted before generating questions");
        }
        log.info("[题目生成] Round ID验证通过: {}", round.getId());

        try {
            // 步骤4: 计算题型分布
            long distStart = System.currentTimeMillis();
            log.info("[步骤4] 计算题型分布 - 开始...");
            Map<Question.QuestionType, Integer> distribution = calculateDistribution(selectedTypes, student);
            log.info("[步骤4] 题型分布计算完成 - distribution: {}, 耗时: {}ms",
                    distribution, System.currentTimeMillis() - distStart);

            // 步骤5: 使用AI批量生成题目（已移除黑名单筛选）
            long aiStart = System.currentTimeMillis();
            log.info("[步骤5] AI批量生成题目 - 开始调用GLM-4-Plus...");
            log.info("[步骤5] 分布配置: {}", distribution);
            try {
                String batchJson = glmService.generatePracticeQuestionsBatch(distribution, null);
                log.info("[步骤5] AI生成完成 - 返回长度: {}字符, 耗时: {}ms",
                        batchJson != null ? batchJson.length() : 0, System.currentTimeMillis() - aiStart);

                // 增加详细日志：输出AI返回的原始JSON（前500字符）
                if (batchJson != null && !batchJson.isEmpty()) {
                    log.info("[步骤5] AI返回JSON预览(前500字符): {}",
                            batchJson.substring(0, Math.min(500, batchJson.length())));
                } else {
                    log.warn("[步骤5] AI返回的JSON为空!");
                }

                // 步骤6: 解析题目（已移除去重逻辑）
                long parseStart = System.currentTimeMillis();
                log.info("[步骤6] 解析题目 - 开始解析JSON...");
                List<QuestionDeduplicationService.GeneratedQuestion> generatedQuestions =
                        deduplicationService.parseAndDeduplicate(batchJson, null);
                log.info("[步骤6] 解析完成 - 生成题目数: {}, 耗时: {}ms",
                        generatedQuestions.size(), System.currentTimeMillis() - parseStart);

                // 增加详细日志：检查每个题目的setupSql是否存在
                for (int i = 0; i < generatedQuestions.size(); i++) {
                    QuestionDeduplicationService.GeneratedQuestion gq = generatedQuestions.get(i);
                    log.info("[步骤6] 题目[{}] - title: {}, hasSetupSql: {}, hasDbContext: {}",
                            i, gq.title,
                            (gq.setupSql != null && !gq.setupSql.trim().isEmpty()),
                            (gq.databaseContext != null && !gq.databaseContext.trim().isEmpty()));
                }

                // 步骤7: 按题型分配题号并保存
                int questionIndex = 0;
                Map<Question.QuestionType, Integer> typeCounters = new HashMap<>();
                for (Question.QuestionType type : selectedTypes) {
                    typeCounters.put(type, 0);
                }

                // 根据分布分配题目
                List<QuestionAssignment> assignments = assignQuestionsByDistribution(
                        generatedQuestions,
                        distribution,
                        typeCounters
                );
                log.info("[步骤7] 题目分配完成 - 分配数: {}", assignments.size());

                // 保存题目
                long saveStart = System.currentTimeMillis();
                log.info("[步骤7] 保存题目到数据库 - 开始保存...");

                for (QuestionAssignment assignment : assignments) {
                    if (questionIndex >= PracticeRound.QUESTIONS_PER_ROUND) break;

                    QuestionDeduplicationService.GeneratedQuestion gq = assignment.question;

                    // 验证必要字段不为空
                    if (gq.description == null || gq.description.trim().isEmpty()) {
                        log.warn("[步骤7] 跳过空题目 - index: {}", questionIndex);
                        continue;
                    }

                    PracticeAnswer answer = new PracticeAnswer(round, questionIndex);

                    // 验证round有ID（用于调试）
                    if (round.getId() == null) {
                        log.error("[步骤7] Round ID为null - questionIndex: {}", questionIndex);
                        throw new IllegalStateException("Round ID is null when creating answer at index " + questionIndex);
                    }

                    // 执行setupSQL并获取表前缀
                    String tablePrefix = null;
                    if (gq.setupSql != null && !gq.setupSql.trim().isEmpty()) {
                        try {
                            log.info("[步骤7] 执行setupSQL - questionIndex: {}", questionIndex);
                            log.info("[步骤7] setupSQL内容: {}", gq.setupSql.substring(0, Math.min(200, gq.setupSql.length())));
                            tablePrefix = setupSqlExecutorService.executeSetupSql(gq.setupSql);
                            log.info("[步骤7] setupSQL执行成功 - tablePrefix: {}", tablePrefix);

                            // 验证表是否真的创建成功
                            if (tablePrefix != null) {
                                log.info("[步骤7] 验证表前缀: {}", tablePrefix);
                            } else {
                                log.warn("[步骤7] setupSQL执行返回null前缀");
                            }
                        } catch (Exception e) {
                            log.error("[步骤7] setupSQL执行失败 - questionIndex: {}, error: {}, stacktrace: {}",
                                    questionIndex, e.getMessage(), e);
                            // 继续保存题目，但没有表前缀
                        }
                    } else {
                        log.warn("[步骤7] 无setupSQL - questionIndex: {}", questionIndex);
                    }

                    answer.setQuestionInfoWithSetup(
                            gq.title != null ? gq.title : "SQL Practice Question " + (questionIndex + 1),
                            gq.description,
                            gq.databaseContext,
                            gq.expectedSql,
                            gq.setupSql,
                            tablePrefix,
                            gq.questionType != null ? gq.questionType : assignment.assignedType,
                            gq.difficulty != null ? gq.difficulty : Question.DifficultyLevel.MEDIUM
                    );

                    log.info("[步骤7] 题目信息 - index: {}, title: {}, tablePrefix: {}, hasSetupSql: {}",
                            questionIndex, answer.getQuestionTitle(), tablePrefix,
                            (gq.setupSql != null && !gq.setupSql.trim().isEmpty()));

                    saveAnswerInNewTransaction(answer);
                    log.debug("[步骤7] 保存题目成功 - index: {}, type: {}, roundId: {}, answerId: {}",
                            questionIndex, answer.getQuestionType(), round.getId(), answer.getId());
                    questionIndex++;
                }

                log.info("[步骤7] 已保存题目数: {}, 耗时: {}ms", questionIndex, System.currentTimeMillis() - saveStart);

                // 步骤8: 如果生成的题目不够，用旧方法补齐
                if (questionIndex < PracticeRound.QUESTIONS_PER_ROUND) {
                    log.warn("[步骤8] 题目数量不足，开始补齐 - 需要: {}, 已有: {}",
                            PracticeRound.QUESTIONS_PER_ROUND, questionIndex);

                    while (questionIndex < PracticeRound.QUESTIONS_PER_ROUND) {
                        PracticeAnswer answer = new PracticeAnswer(round, questionIndex);
                        Question.QuestionType fallbackType = selectedTypes.isEmpty() ?
                                Question.QuestionType.SELECT_BASIC : selectedTypes.get(0);

                        try {
                            log.info("[步骤8-补齐] 开始单题生成 - index: {}, type: {}", questionIndex, fallbackType);
                            String questionJson = glmService.generatePracticeQuestion(
                                    fallbackType.name(),
                                    Question.DifficultyLevel.MEDIUM.name()
                            );
                            log.info("[步骤8-补齐] 单题生成返回 - 长度: {}", questionJson != null ? questionJson.length() : 0);
                            if (questionJson != null && !questionJson.isEmpty()) {
                                log.info("[步骤8-补齐] 单题JSON预览: {}", questionJson.substring(0, Math.min(300, questionJson.length())));
                            }

                            JsonNode questionNode = objectMapper.readTree(cleanJsonResponse(questionJson));
                            String setupSql = getJsonFieldOrNull(questionNode, "setupSql");
                            log.info("[步骤8-补齐] 解析完成 - hasSetupSql: {}", setupSql != null);

                            // 执行setupSql并获取表前缀
                            String tablePrefix = null;
                            if (setupSql != null && !setupSql.trim().isEmpty()) {
                                try {
                                    log.info("[步骤8-补齐] 执行setupSQL...");
                                    tablePrefix = setupSqlExecutorService.executeSetupSql(setupSql);
                                    log.info("[步骤8-补齐] setupSQL执行成功 - tablePrefix: {}", tablePrefix);
                                } catch (Exception ex) {
                                    log.error("[步骤8-补齐] setupSQL执行失败: {}", ex.getMessage());
                                }
                            }

                            answer.setQuestionInfoWithSetup(
                                    getJsonField(questionNode, "title"),
                                    getJsonField(questionNode, "description"),
                                    getJsonField(questionNode, "databaseContext"),
                                    getJsonField(questionNode, "expectedSql"),
                                    setupSql,
                                    tablePrefix, // 使用执行setupSQL后获取的tablePrefix
                                    fallbackType,
                                    Question.DifficultyLevel.MEDIUM
                            );

                        } catch (Exception e) {
                            log.warn("[步骤8] 单题生成失败，使用默认题目 - index: {}, error: {}",
                                    questionIndex, e.getMessage());
                            answer.setQuestionInfoWithSetup(
                                    "SQL Practice Question " + (questionIndex + 1),
                                    "Practice question for " + fallbackType.getDisplayName(),
                                    "Please write the correct SQL query.",
                                    "SELECT * FROM table",
                                    null, // setupSql
                                    null, // tablePrefix
                                    fallbackType,
                                    Question.DifficultyLevel.MEDIUM
                            );
                        }

                        answerRepository.saveAndFlush(answer);
                        questionIndex++;
                    }
                    log.info("[步骤8] 补齐完成 - 最终题目数: {}", questionIndex);
                }

            } catch (Exception aiException) {
                log.error("[步骤5] AI生成失败 - error: {}", aiException.getMessage(), aiException);
                throw aiException;
            }

            log.info("[题目生成] ========== 批量生成成功 - 总耗时: {}ms ==========",
                    System.currentTimeMillis() - batchStartTime);

        } catch (Exception e) {
            log.error("[题目生成] ========== 批量生成失败 ==========");
            log.error("[题目生成] 错误信息: {}", e.getMessage(), e);
            log.info("[题目生成] 开始清理已创建的数据...");
            // 清理可能已创建的数据
            try {
                cleanupAnswersForRound(round);
            } catch (Exception cleanupException) {
                log.error("[题目生成] 清理失败 - error: {}", cleanupException.getMessage());
            }

            log.info("[题目生成] 降级到单题生成模式...");
            // 降级到单题生成模式
            Question.QuestionType fallbackType = selectedTypes.isEmpty() ?
                    null : selectedTypes.get(0);
            generateQuestionsForRound(round, student, fallbackType);
            log.info("[题目生成] ========== 单题生成完成 ==========");
        }
    }

    /**
     * 计算题型分布
     * 根据学生选择的题型和历史错误统计数据，计算本轮10题的题型分配
     */
    private Map<Question.QuestionType, Integer> calculateDistribution(
            List<Question.QuestionType> selectedTypes,
            User student) {

        Map<Question.QuestionType, Integer> distribution = new LinkedHashMap<>();
        int totalQuestions = PracticeRound.QUESTIONS_PER_ROUND;

        if (selectedTypes == null || selectedTypes.isEmpty()) {
            // 未选择题型，使用默认分布
            distribution.put(Question.QuestionType.SELECT_BASIC, 3);
            distribution.put(Question.QuestionType.SELECT_JOIN, 2);
            distribution.put(Question.QuestionType.SELECT_AGGREGATE, 2);
            distribution.put(Question.QuestionType.SELECT_SUBQUERY, 2);
            distribution.put(Question.QuestionType.DML_UPDATE, 1);
            return distribution;
        }

        // 获取学生的错误统计
        List<ErrorTypeStatistics> stats = statisticsRepository.findByStudent(student);
        Map<Question.QuestionType, ErrorTypeStatistics> statsMap = stats.stream()
                .collect(Collectors.toMap(ErrorTypeStatistics::getQuestionType, s -> s));

        // 根据错误频率排序选中的题型
        List<Question.QuestionType> sortedTypes = new ArrayList<>(selectedTypes);
        sortedTypes.sort((a, b) -> {
            ErrorTypeStatistics statA = statsMap.get(a);
            ErrorTypeStatistics statB = statsMap.get(b);
            double errorA = statA != null ? statA.getErrorFrequency() : 0.5;
            double errorB = statB != null ? statB.getErrorFrequency() : 0.5;
            return Double.compare(errorB, errorA); // 降序，错误频率高的优先
        });

        // 分配题目数量
        int remaining = totalQuestions;
        int typeCount = sortedTypes.size();

        for (int i = 0; i < sortedTypes.size(); i++) {
            Question.QuestionType type = sortedTypes.get(i);
            ErrorTypeStatistics stat = statsMap.get(type);

            int count;
            if (stat != null) {
                // 根据错误频率分配更多题目
                double errorRatio = stat.getErrorFrequency();
                if (errorRatio >= 0.5) {
                    count = (int) Math.ceil(totalQuestions * 0.4); // 40%
                } else if (errorRatio >= 0.3) {
                    count = (int) Math.ceil(totalQuestions * 0.25); // 25%
                } else {
                    count = (int) Math.ceil(totalQuestions * 0.15); // 15%
                }
            } else {
                // 没有历史记录，分配基础数量
                count = (int) Math.ceil((double) totalQuestions / typeCount);
            }

            // 限制数量不超过剩余
            count = Math.min(count, remaining);
            distribution.put(type, count);
            remaining -= count;

            // 最后一个类型分配剩余所有题目
            if (i == sortedTypes.size() - 1 && remaining > 0) {
                distribution.put(type, distribution.get(type) + remaining);
            }
        }

        return distribution;
    }

    /**
     * 根据分布分配题目
     */
    private List<QuestionAssignment> assignQuestionsByDistribution(
            List<QuestionDeduplicationService.GeneratedQuestion> questions,
            Map<Question.QuestionType, Integer> distribution,
            Map<Question.QuestionType, Integer> typeCounters) {

        List<QuestionAssignment> assignments = new ArrayList<>();

        for (QuestionDeduplicationService.GeneratedQuestion q : questions) {
            Question.QuestionType qType = q.questionType;

            // 检查该类型是否还需要题目
            if (distribution.containsKey(qType)) {
                int required = distribution.get(qType);
                int current = typeCounters.getOrDefault(qType, 0);

                if (current < required) {
                    assignments.add(new QuestionAssignment(q, qType));
                    typeCounters.put(qType, current + 1);
                }
            }
        }

        return assignments;
    }

    // 题目分配辅助类
    private static class QuestionAssignment {
        final QuestionDeduplicationService.GeneratedQuestion question;
        final Question.QuestionType assignedType;

        QuestionAssignment(QuestionDeduplicationService.GeneratedQuestion question, Question.QuestionType assignedType) {
            this.question = question;
            this.assignedType = assignedType;
        }
    }

    // ==================== 答题处理 ====================

    /**
     * 仅保存答案，不执行也不评分
     */
    @Transactional
    public void saveAnswerOnly(Long answerId, String studentSql) {
        PracticeAnswer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new RuntimeException("Answer not found"));

        if (answer.isAnswered()) {
            log.warn("[保存答案] 答案已提交，覆盖保存: answerId={}", answerId);
        }

        // 只保存SQL，不执行不评分
        answer.setStudentSql(studentSql);
        answer.setAnswered(true);
        answer.setAnswerTime(LocalDateTime.now());
        answerRepository.save(answer);

        log.info("[保存答案] 答案已保存: answerId={}, sqlLength={}", answerId, studentSql.length());
    }

    /**
     * 批量评分一个轮次的所有答案
     * 使用@Transactional确保懒加载正常工作
     * 在方法开头将所有数据加载到POJO中，后续评分不再依赖JPA实体
     */
    @Transactional
    public void scoreAllAnswersInRound(Long roundId) {
        // === 第一步：加载所有必要数据到POJO（利用事务内的懒加载） ===
        PracticeRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> new RuntimeException("Round not found"));

        Long studentId = round.getSession().getStudent().getId();
        List<PracticeAnswer> answers = answerRepository.findByRoundOrderByQuestionIndexAsc(round);

        log.info("[批量评分] 开始 - roundId={}, studentId={}, 题目数={}", roundId, studentId, answers.size());

        List<ScoringContext.AnswerData> answerDataList = new ArrayList<>();
        for (PracticeAnswer answer : answers) {
            ScoringContext.AnswerData ad = new ScoringContext.AnswerData();
            ad.answerId = answer.getId();
            ad.questionIndex = answer.getQuestionIndex();
            ad.answered = answer.isAnswered();
            ad.studentSql = answer.getStudentSql();
            ad.expectedSql = answer.getExpectedSql();
            ad.questionTitle = answer.getQuestionTitle();
            ad.questionContent = answer.getQuestionContent();
            ad.tablePrefix = answer.getTablePrefix();
            ad.setupSql = answer.getSetupSql();
            answerDataList.add(ad);
        }

        // === 第二步：逐题评分（使用POJO数据，不再依赖JPA懒加载） ===
        int scoredCount = 0;
        int skippedCount = 0;
        for (ScoringContext.AnswerData ad : answerDataList) {
            if (!ad.answered || ad.studentSql == null || ad.studentSql.trim().isEmpty()) {
                log.warn("[批量评分] 跳过未回答的题目: answerId={}, index={}", ad.answerId, ad.questionIndex);
                skippedCount++;
                continue;
            }

            try {
                scoreAnswer(ad, studentId);
                scoredCount++;
            } catch (Exception e) {
                log.error("[批量评分] 评分失败: answerId=" + ad.answerId, e);
            }
        }

        log.info("[批量评分] 评分完成: roundId={}, 成功={}, 跳过={}, 总数={}", roundId, scoredCount, skippedCount, answerDataList.size());
    }

    /**
     * 评分上下文（存储所有需要的数据，避免懒加载）
     */
    static class ScoringContext {
        Long studentId;
        List<AnswerData> answers;

        static class AnswerData {
            Long answerId;
            Integer questionIndex;
            Boolean answered;
            String studentSql;
            String expectedSql;
            String questionTitle;
            String questionContent;
            String tablePrefix;
            String setupSql;
        }
    }

    /**
     * 对单个答案进行评分（纯AI评分，不需要沙库执行）
     */
    private void scoreAnswer(ScoringContext.AnswerData ad, Long studentId) {
        try {
            log.info("[评分] ====== 开始评分 answerId={}, questionIndex={} ======", ad.answerId, ad.questionIndex);
            log.info("[评分] 题目: {}", ad.questionTitle);
            log.info("[评分] 学生SQL: {}", ad.studentSql);
            log.info("[评分] 预期SQL: {}", ad.expectedSql);

            boolean isCorrect = false;
            double score = 0.0;
            String aiFeedback = "";
            double fullScore = 10.0;

            // 直接使用AI评分（与教师评分相同的方式）
            try {
                String aiResponse = glmService.score_answer(
                        fullScore,
                        ad.questionContent != null ? ad.questionContent : ad.questionTitle,
                        ad.expectedSql != null ? ad.expectedSql : "N/A",
                        ad.studentSql
                );

                if (aiResponse != null) {
                    String cleanJson = aiResponse.replace("```json", "").replace("```", "").trim();
                    JsonNode jsonNode = objectMapper.readTree(cleanJson);

                    score = jsonNode.get("score").asDouble();
                    isCorrect = jsonNode.has("isCorrect") ? jsonNode.get("isCorrect").asBoolean() : (score >= fullScore * 0.6);
                    aiFeedback = jsonNode.has("feedback") ? jsonNode.get("feedback").asText() : "";

                    log.info("[评分] AI评分完成 - answerId={}, score={}, isCorrect={}", ad.answerId, score, isCorrect);
                }
            } catch (Exception aiException) {
                log.warn("[评分] AI评分失败: {}", aiException.getMessage());
                aiFeedback = "AI scoring failed: " + aiException.getMessage();
            }

            // 保存评分结果
            PracticeAnswer answer = answerRepository.findById(ad.answerId)
                    .orElseThrow(() -> new RuntimeException("Answer not found: " + ad.answerId));
            answer.setExecutionResult(null);
            answer.setExecutionError(null);
            answer.setIsCorrect(isCorrect);
            answer.setScore(score);
            answer.setAiFeedback(aiFeedback);
            answerRepository.save(answer);

            log.info("[评分] ====== 评分完成 answerId={}, score={}/{} ======", ad.answerId, score, fullScore);

        } catch (Exception e) {
            log.error("[评分] 评分失败: answerId=" + ad.answerId, e);
            throw new RuntimeException("Scoring failed: " + e.getMessage(), e);
        }
    }

    /**
     * 提交答案（使用沙库执行，进行AI评分）
     * @deprecated 改为使用 saveAnswerOnly + scoreAllAnswersInRound
     */
    @Transactional
    public PracticeAnswer submitAnswer(Long answerId, String studentSql) {
        PracticeAnswer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new RuntimeException("Answer not found"));

        SandboxContext sandbox = null;
        try {
            // 1. 创建沙库
            sandbox = sandboxService.createPracticeSandbox(
                answer.getRound().getSession().getStudent().getId(),
                answerId
            );

            // 2. 从testdb克隆表结构和数据到沙库（新方案：确保100%一致）
            String tablePrefix = answer.getTablePrefix();
            if (tablePrefix != null && !tablePrefix.isEmpty()) {
                sandboxService.cloneTablesFromTestDB(sandbox, tablePrefix);
            } else {
                // 兼容旧数据：如果没有tablePrefix，降级使用setupSql
                String setupSql = answer.getSetupSql();
                if (setupSql != null && !setupSql.isEmpty()) {
                    sandboxService.executeSetupSql(sandbox, setupSql);
                }
            }

            // 3. 在沙库中执行学生SQL
            SandboxDatabaseService.SqlExecutionResult studentResult =
                sandboxService.executeInSandbox(sandbox, studentSql, tablePrefix);

            // 4. 如果有预期SQL，也执行预期SQL获取结果用于AI评分
            SandboxDatabaseService.SqlExecutionResult expectedResult = null;
            String expectedResultJson = null;
            if (answer.getExpectedSql() != null && !answer.getExpectedSql().trim().isEmpty()) {
                try {
                    expectedResult = sandboxService.executeInSandbox(sandbox, answer.getExpectedSql(), tablePrefix);
                    if (expectedResult.isSuccess()) {
                        expectedResultJson = objectMapper.writeValueAsString(expectedResult);
                    }
                } catch (Exception e) {
                    log.warn("[学生答题] 执行预期SQL失败: {}", e.getMessage());
                }
            }

            // 5. 调用AI评分
            boolean isCorrect = false;
            double score = 0.0;
            String aiFeedback = "";
            double fullScore = 10.0;

            try {
                String studentResultJson = studentResult.isSuccess()
                    ? objectMapper.writeValueAsString(studentResult)
                    : null;

                String aiResponse = glmService.scorePracticeAnswer(
                        answer.getQuestionTitle(),
                        answer.getQuestionContent(),
                        answer.getExpectedSql() != null ? answer.getExpectedSql() : "N/A",
                        studentSql,
                        studentResultJson,
                        expectedResultJson,
                        fullScore
                );

                // 解析AI评分结果
                if (aiResponse != null) {
                    String cleanJson = aiResponse.replace("```json", "").replace("```", "").trim();
                    JsonNode jsonNode = objectMapper.readTree(cleanJson);

                    score = jsonNode.get("score").asDouble();
                    isCorrect = jsonNode.get("isCorrect").asBoolean();
                    aiFeedback = jsonNode.get("feedback").asText();

                    log.info("[学生答题] AI评分完成 - score: {}, isCorrect: {}", score, isCorrect);
                }
            } catch (Exception aiException) {
                log.warn("[学生答题] AI评分失败，降级到简单评分: {}", aiException.getMessage());
                // 降级：简单判断是否正确（基于执行结果）
                isCorrect = studentResult.isSuccess() &&
                    (studentResult.getErrorMessage() == null || studentResult.getErrorMessage().isEmpty());
                score = isCorrect ? fullScore : 0.0;
                aiFeedback = isCorrect ? "Correct!" : "Incorrect. Please try again.";
            }

            // 6. 保存答案和评分结果
            try {
                answer.submitAnswer(
                        studentSql, // 保存原始SQL
                        studentResult.isSuccess() ? objectMapper.writeValueAsString(studentResult) : null,
                        studentResult.getErrorMessage(),
                        isCorrect,
                        score,
                        aiFeedback
                );
            } catch (Exception e) {
                answer.submitAnswer(
                        studentSql,
                        null,
                        studentResult.getErrorMessage(),
                        isCorrect,
                        score,
                        aiFeedback
                );
            }
            answer = answerRepository.save(answer);

            // 7. 更新轮次统计
            PracticeRound round = answer.getRound();
            round.recordAnswer(isCorrect);
            roundRepository.save(round);

            // 8. 更新错误类型统计
            updateErrorStatistics(round.getSession().getStudent(), answer.getQuestionType(), isCorrect);

            return answer;

        } catch (Exception e) {
            log.error("Practice answer submission failed", e);
            throw new RuntimeException("答题失败: " + e.getMessage());
        } finally {
            // 9. 清理沙库
            if (sandbox != null) {
                sandboxService.closeConnection(sandbox);
                sandboxService.cleanupSandbox(sandbox.getDatabaseName());
            }
        }
    }

    /**
     * 为SQL添加表前缀
     * 将学生输入的原始表名（如employees）映射到数据库中的带前缀表名（如quiz_q_1234_employees）
     * 支持的SQL语句类型：
     * - SELECT ... FROM table
     * - JOIN table
     * - INSERT INTO table
     * - UPDATE table
     * - DELETE FROM table
     * - DROP TABLE table
     * - ALTER TABLE table
     * - TRUNCATE TABLE table
     */
    public String addTablePrefixToSql(String sql, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return sql;
        }

        log.debug("表名映射 - 原始SQL: {}", sql);
        log.debug("表名映射 - 使用前缀: {}", prefix);

        String result = sql;

        // 按顺序匹配并替换表名，更具体的模式先匹配
        // DROP TABLE table_name
        result = result.replaceAll(
                "(?i)\\b(DROP\\s+TABLE)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "$1 " + prefix + "_$2"
        );
        // ALTER TABLE table_name
        result = result.replaceAll(
                "(?i)\\b(ALTER\\s+TABLE)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "$1 " + prefix + "_$2"
        );
        // TRUNCATE TABLE table_name
        result = result.replaceAll(
                "(?i)\\b(TRUNCATE\\s+TABLE)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "$1 " + prefix + "_$2"
        );
        // INSERT INTO table_name
        result = result.replaceAll(
                "(?i)\\b(INSERT\\s+INTO)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "$1 " + prefix + "_$2"
        );
        // UPDATE table_name
        result = result.replaceAll(
                "(?i)\\bUPDATE\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "UPDATE " + prefix + "_$1"
        );
        // DELETE FROM table_name
        result = result.replaceAll(
                "(?i)\\b(DELETE\\s+FROM)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "$1 " + prefix + "_$2"
        );
        // FROM table_name
        result = result.replaceAll(
                "(?i)\\b(FROM)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "$1 " + prefix + "_$2"
        );
        // JOIN table_name
        result = result.replaceAll(
                "(?i)\\b(JOIN)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\b",
                "$1 " + prefix + "_$2"
        );

        log.debug("表名映射 - 映射后SQL: {}", result);
        return result;
    }

    /**
     * 获取当前题目
     */
    public Optional<PracticeAnswer> getCurrentQuestion(Long roundId) {
        PracticeRound round = roundRepository.findById(roundId).orElse(null);
        if (round == null) return Optional.empty();
        return answerRepository.findByRoundAndQuestionIndex(round, round.getCurrentQuestionIndex());
    }

    /**
     * 通过索引获取题目
     */
    public Optional<PracticeAnswer> getQuestionByIndex(Long roundId, Integer index) {
        PracticeRound round = roundRepository.findById(roundId).orElse(null);
        if (round == null) return Optional.empty();
        return answerRepository.findByRoundAndQuestionIndex(round, index);
    }

    /**
     * 获取轮次信息
     */
    public Optional<PracticeRound> getRound(Long roundId) {
        return roundRepository.findById(roundId);
    }

    /**
     * 获取轮次的所有题目
     */
    public List<PracticeAnswer> getAnswersByRound(Long roundId) {
        PracticeRound round = roundRepository.findById(roundId).orElse(null);
        if (round == null) return new ArrayList<>();
        return answerRepository.findByRoundOrderByQuestionIndexAsc(round);
    }

    // ==================== 统计与反馈 ====================

    /**
     * 从历史Quiz记录初始化错误统计
     */
    @Transactional
    public void initializeErrorStatisticsFromHistory(User student) {
        // 检查是否已初始化
        if (!statisticsRepository.findByStudent(student).isEmpty()) {
            return;
        }

        // 获取学生的所有历史答题记录
        List<Object[]> historyStats = answerRepository.getStatisticsByStudentGroupByType(student.getId());

        for (Object[] row : historyStats) {
            Question.QuestionType type = (Question.QuestionType) row[0];
            Long total = (Long) row[1];
            Long correct = (Long) row[2];

            ErrorTypeStatistics stat = new ErrorTypeStatistics(student, type);
            stat.setTotalCount(total.intValue());
            stat.setCorrectCount(correct.intValue());
            stat.setErrorCount(total.intValue() - correct.intValue());
            stat.recalculateAccuracy();

            statisticsRepository.save(stat);
        }
    }

    /**
     * 更新错误类型统计
     */
    @Transactional
    public void updateErrorStatistics(User student, Question.QuestionType type, boolean isCorrect) {
        ErrorTypeStatistics stat = statisticsRepository.findByStudentAndQuestionType(student, type)
                .orElse(new ErrorTypeStatistics(student, type));

        stat.recordAnswer(isCorrect);
        statisticsRepository.save(stat);
    }

    /**
     * 清理未完成的会话（每次进入Dashboard时调用）
     * 自动结束所有进行中但未完成的会话
     */
    @Transactional
    public int cleanupIncompleteSessions(User student) {
        List<PracticeSession> activeSessions = sessionRepository.findByStudentAndStatus(
                student, PracticeSession.SessionStatus.IN_PROGRESS);
        
        int cleaned = 0;
        for (PracticeSession session : activeSessions) {
            // 结束会话
            session.completeSession();
            sessionRepository.save(session);
            cleaned++;
            log.info("Cleaned up incomplete session: {} for student: {}", 
                    session.getId(), student.getUsername());
        }
        
        return cleaned;
    }

    /**
     * 获取学生的错误统计
     */
    public List<ErrorTypeStatistics> getErrorStatistics(User student) {
        return statisticsRepository.findByStudentOrderByErrorCountDesc(student);
    }

    /**
     * 检查题型是否已掌握
     */
    public boolean isTypeMastered(User student, Question.QuestionType type) {
        return statisticsRepository.findByStudentAndQuestionType(student, type)
                .map(ErrorTypeStatistics::getIsMastered)
                .orElse(false);
    }

    /**
     * 生成轮次反馈
     */
    private String generateRoundFeedback(PracticeRound round) {
        List<PracticeAnswer> incorrectAnswers = answerRepository.findIncorrectByRound(round);

        if (incorrectAnswers.isEmpty()) {
            return "Excellent! You got all questions correct in this round!";
        }

        StringBuilder feedback = new StringBuilder();
        feedback.append("## Round ").append(round.getRoundNumber()).append(" Summary\n\n");
        feedback.append("**Score:** ").append(round.getCorrectCount())
                .append("/").append(round.getTotalQuestions()).append("\n\n");
        feedback.append("### Areas for Improvement:\n\n");

        // 按题型分组错误
        Map<Question.QuestionType, List<PracticeAnswer>> errorsByType = incorrectAnswers.stream()
                .filter(a -> a.getQuestionType() != null)
                .collect(Collectors.groupingBy(PracticeAnswer::getQuestionType));

        for (Map.Entry<Question.QuestionType, List<PracticeAnswer>> entry : errorsByType.entrySet()) {
            feedback.append("#### ").append(entry.getKey().getEnglishName()).append("\n");
            for (PracticeAnswer answer : entry.getValue()) {
                feedback.append("- **Q").append(answer.getQuestionIndex() + 1).append(":** ");
                if (answer.getAiFeedback() != null) {
                    feedback.append(answer.getAiFeedback());
                }
                feedback.append("\n");
            }
            feedback.append("\n");
        }

        return feedback.toString();
    }

    /**
     * 获取练习历史
     */
    public List<PracticeSession> getPracticeHistory(User student) {
        return sessionRepository.findByStudentOrderByStartTimeDesc(student);
    }

    // ==================== 辅助方法 ====================

    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private String getJsonField(JsonNode node, String field) {
        if (node == null || !node.has(field)) return "";
        JsonNode fieldNode = node.get(field);
        if (fieldNode.isNull()) return "";
        return fieldNode.asText();
    }

    /**
     * 获取JSON字段，空字符串返回null（用于可选字段如setupSql）
     */
    private String getJsonFieldOrNull(JsonNode node, String field) {
        String value = getJsonField(node, field);
        return (value == null || value.trim().isEmpty()) ? null : value;
    }

    // 优先级数据类
    public static class QuestionPriority {
        public final Question.QuestionType questionType;
        public final Question.DifficultyLevel difficulty;
        public final double priorityScore;

        public QuestionPriority(Question.QuestionType questionType, Question.DifficultyLevel difficulty, double priorityScore) {
            this.questionType = questionType;
            this.difficulty = difficulty;
            this.priorityScore = priorityScore;
        }
    }

    /**
     * 获取沙库服务（供Controller调用）
     */
    public SandboxDatabaseService getSandboxService() {
        return sandboxService;
    }

    /**
     * 获取答案仓库（供Controller调用）
     */
    public PracticeAnswerRepository getAnswerRepository() {
        return answerRepository;
    }

    /**
     * 保存单个答案（独立事务，用于异步线程中调用）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PracticeAnswer saveAnswerInNewTransaction(PracticeAnswer answer) {
        return answerRepository.saveAndFlush(answer);
    }

    /**
     * 清理轮次的答案（独立事务，用于异步线程中调用）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupAnswersForRound(PracticeRound round) {
        List<PracticeAnswer> existingAnswers = answerRepository.findByRound(round);
        log.info("[题目生成] 找到已创建的答案数: {}", existingAnswers.size());
        for (PracticeAnswer ans : existingAnswers) {
            answerRepository.delete(ans);
        }
        answerRepository.flush();
        log.info("[题目生成] 清理完成");
    }
}
