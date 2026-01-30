package com.example.SqlQuiz.Controller;

import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.entity.Quiz;
import com.example.SqlQuiz.entity.SandboxContext;
import com.example.SqlQuiz.entity.User;
import com.example.SqlQuiz.service.GLMService;
import com.example.SqlQuiz.service.QuizService;
import com.example.SqlQuiz.service.QuizTableMetadataService;
import com.example.SqlQuiz.service.SandboxDatabaseService;
import com.example.SqlQuiz.service.SetupSqlExecutorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/teacher/api/question")
public class AIQuestionController {

    @Autowired
    private GLMService glmService;

    @Autowired
    private QuizService quizService;

    @Autowired
    private QuizTableMetadataService tableMetadataService;

    @Autowired
    private SetupSqlExecutorService setupSqlExecutorService;

    @Autowired
    private SandboxDatabaseService sandboxService;

    @PostMapping("/normalize")
    public ResponseEntity<?> normalizeQuestion(
            @RequestBody Map<String, String> request,
            Authentication auth) {
        try {
            String input = request.get("input");
            String inputType = request.get("inputType");

            if (input == null || input.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Input cannot be empty"
                ));
            }

            if (inputType == null) {
                inputType = "TEXT";
            }

            String jsonResponse = glmService.normalizeQuestion(input, inputType);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);

            // 在沙库中验证AI规范后的SQL（防止错误的SQL破坏数据库）
            if (jsonNode.has("setupSql") && jsonNode.has("expectedSql")) {
                String setupSql = jsonNode.get("setupSql").asText();
                String expectedSql = jsonNode.get("expectedSql").asText();

                if (setupSql != null && !setupSql.trim().isEmpty()) {
                    boolean isValid = glmService.verifyQuestionInSandbox(setupSql, expectedSql);
                    if (!isValid) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "success", false,
                                "error", "AI规范后的SQL验证失败：setupSql或expectedSql存在错误，请重新规范"
                        ));
                    }
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", objectMapper.convertValue(jsonNode, Map.class));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Question normalization failed: " + e.getMessage()
            ));
        }
    }

    /**
     * 图片上传和OCR识别接口
     */
    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        try {
            // 验证文件
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "File is empty"
                ));
            }

            // 验证文件类型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Only image files are allowed"
                ));
            }

            // 验证文件大小（限制5MB）
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "File size exceeds 5MB limit"
                ));
            }

            // 创建临时目录
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "sqlquiz-ocr");
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
            String filename = UUID.randomUUID().toString() + extension;
            Path filePath = tempDir.resolve(filename);

            // 保存文件
            file.transferTo(filePath.toFile());

            // 调用OCR识别
            String recognizedText = glmService.performOCR(filePath.toString());

            // 删除临时文件
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                System.err.println("Failed to delete temp file: " + e.getMessage());
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "text", recognizedText != null ? recognizedText : "",
                    "message", "OCR recognition completed"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "OCR recognition failed: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateQuestion(
            @RequestBody Map<String, String> request,
            Authentication auth) {
        try {
            String questionType = request.get("questionType");
            String difficulty = request.get("difficulty");

            if (questionType == null || questionType.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Question type is required"
                ));
            }

            if (difficulty == null || difficulty.trim().isEmpty()) {
                difficulty = "MEDIUM";
            }

            String jsonResponse = glmService.generateQuestionWithRAG(questionType, difficulty);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", objectMapper.convertValue(jsonNode, Map.class));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Question generation failed: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmQuestion(
            @RequestBody Map<String, Object> request,
            Authentication auth) {
        try {
            User teacher = (User) auth.getPrincipal();

            Object quizIdObj = request.get("quizId");
            Long quizId;
            if (quizIdObj instanceof String) {
                quizId = Long.parseLong((String) quizIdObj);
            } else if (quizIdObj instanceof Number) {
                quizId = ((Number) quizIdObj).longValue();
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Invalid quizId format"
                ));
            }

            String title = (String) request.get("title");
            String description = (String) request.get("description");
            String databaseContext = (String) request.get("databaseContext");
            String setupSql = (String) request.get("setupSql");
            String expectedSql = (String) request.get("expectedSql");
            String answer = (String) request.get("answer");
            String questionTypeStr = (String) request.get("questionType");
            String difficultyStr = (String) request.get("difficulty");
            
            // 调试日志
            System.out.println("[AI Question] Received questionType: " + questionTypeStr);
            System.out.println("[AI Question] Received difficulty: " + difficultyStr);
            
            Object scoreObj = request.get("score");
            Double score;
            if (scoreObj instanceof String) {
                score = Double.parseDouble((String) scoreObj);
            } else if (scoreObj instanceof Number) {
                score = ((Number) scoreObj).doubleValue();
            } else {
                score = 10.0;
            }

            Optional<Quiz> quizOpt = quizService.findById(quizId);
            if (!quizOpt.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Quiz not found"
                ));
            }

            Quiz quiz = quizOpt.get();
            if (!quiz.getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "error", "No permission to add questions to this quiz"
                ));
            }

            Question.QuestionType questionType = null;
            if (questionTypeStr != null && !questionTypeStr.trim().isEmpty()) {
                try {
                    questionType = Question.QuestionType.valueOf(questionTypeStr);
                    System.out.println("[AI Question] Parsed questionType: " + questionType);
                } catch (IllegalArgumentException e) {
                    System.err.println("[AI Question] Failed to parse questionType: " + questionTypeStr);
                    System.err.println("[AI Question] Error: " + e.getMessage());
                }
            } else {
                System.err.println("[AI Question] questionType is null or empty");
            }

            Question.DifficultyLevel difficulty = null;
            if (difficultyStr != null && !difficultyStr.trim().isEmpty()) {
                try {
                    difficulty = Question.DifficultyLevel.valueOf(difficultyStr);
                    System.out.println("[AI Question] Parsed difficulty: " + difficulty);
                } catch (IllegalArgumentException e) {
                    System.err.println("[AI Question] Failed to parse difficulty: " + difficultyStr);
                    System.err.println("[AI Question] Error: " + e.getMessage());
                }
            } else {
                System.err.println("[AI Question] difficulty is null or empty");
            }

            // 在沙库中验证AI生成的SQL（防止错误的SQL破坏数据库）
            SandboxContext sandbox = null;
            try {
                if (setupSql != null && !setupSql.trim().isEmpty()) {
                    // 使用沙库验证setupSql和expectedSql
                    boolean isValid = glmService.verifyQuestionInSandbox(setupSql, expectedSql);
                    if (!isValid) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "success", false,
                                "error", "AI生成的SQL验证失败：setupSql或expectedSql存在错误，请重新生成"
                        ));
                    }
                }
            } finally {
                // 沙库已在verifyQuestionInSandbox中清理
            }

            String tablePrefix = null;
            if (setupSql != null && !setupSql.trim().isEmpty()) {
                tablePrefix = setupSqlExecutorService.executeSetupSql(setupSql);
            }

            String fullDescription = description;
            if (databaseContext != null && !databaseContext.trim().isEmpty()) {
                fullDescription = description + "\n\nDatabase Context:\n" + databaseContext;
            }

            Question question = quizService.addQuestionToQuiz(
                    quizId,
                    title,
                    questionType,
                    fullDescription,
                    databaseContext,
                    expectedSql,
                    setupSql,  // 传递setupSql参数
                    null,
                    answer,
                    score,
                    difficulty
            );

            if (tablePrefix != null) {
                tableMetadataService.createMetadata(tablePrefix, question.getId(), teacher.getId());
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Question created successfully",
                    "questionId", question.getId(),
                    "tablePrefix", tablePrefix
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Failed to confirm question: " + e.getMessage()
            ));
        }
    }
}
