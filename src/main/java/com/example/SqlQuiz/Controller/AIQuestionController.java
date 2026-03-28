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
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/teacher/api/question")
public class AIQuestionController {
    private static final Pattern FORBIDDEN_SYSTEM_SCHEMA_PATTERN = Pattern.compile(
            "(?i)\\b(?:information_schema|mysql|performance_schema|sys)\\b\\s*\\."
    );
    private static final Pattern CJK_CHAR_PATTERN = Pattern.compile(
            "[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]"
    );
    private static final Pattern BASIC_EASY_COMPLEX_KEYWORDS_PATTERN = Pattern.compile(
            "(?i)\\b(?:join|group\\s+by|having|union|intersect|except|with\\b)\\b"
    );
    private static final Pattern SUBQUERY_PATTERN = Pattern.compile("(?i)\\(\\s*select\\b");
    private static final int GENERATION_MAX_ATTEMPTS = 3;


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
            @RequestParam(required = false) String input,
            @RequestParam(required = false) String inputType,
            @RequestParam(required = false) MultipartFile imageFile,
            Authentication auth) {
        try {
            // Handle image file if provided
            if (imageFile != null && !imageFile.isEmpty()) {
                try {
                    // Create temp directory
                    Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "sqlquiz-ocr");
                    if (!Files.exists(tempDir)) {
                        Files.createDirectories(tempDir);
                    }

                    // Save uploaded file to temp location
                    String originalFilename = imageFile.getOriginalFilename();
                    String extension = originalFilename != null && originalFilename.contains(".")
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : ".jpg";
                    String filename = UUID.randomUUID().toString() + extension;
                    Path tempFilePath = tempDir.resolve(filename);
                    imageFile.transferTo(tempFilePath.toFile());

                    // Perform OCR
                    String recognizedText = glmService.performOCR(tempFilePath.toString());

                    // Clean up temp file
                    try {
                        Files.deleteIfExists(tempFilePath);
                    } catch (IOException e) {
                        System.err.println("Failed to delete temp file: " + e.getMessage());
                    }

                    if (recognizedText != null && !recognizedText.trim().isEmpty()) {
                        input = recognizedText;
                    }
                    inputType = "IMAGE";
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "error", "OCR recognition failed: " + e.getMessage()
                    ));
                }
            }

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

            // Verify AI-normalized SQL in sandbox (prevent incorrect SQL from damaging database)
            if (jsonNode.has("setupSql") && jsonNode.has("expectedSql")) {
                String setupSql = jsonNode.get("setupSql").asText();
                String expectedSql = jsonNode.get("expectedSql").asText();

                if (containsForbiddenSystemSchemaQuery(expectedSql)) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "error", "AI-normalized SQL contains system schema query (information_schema/mysql/performance_schema/sys), which is blocked in sandbox verification"
                    ));
                }

                if (setupSql != null && !setupSql.trim().isEmpty()) {
                    boolean isValid = glmService.verifyQuestionInSandbox(setupSql, expectedSql);
                    if (!isValid) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "success", false,
                                "error", "AI-normalized SQL validation failed: setupSql or expectedSql contains errors, please re-normalize"
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

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = null;
            String lastRejectReason = "Unknown generation error";

            for (int attempt = 1; attempt <= GENERATION_MAX_ATTEMPTS; attempt++) {
                try {
                    String jsonResponse = glmService.generateQuestionWithRAG(questionType, difficulty);
                    JsonNode candidate = objectMapper.readTree(jsonResponse);
                    String validationError = validateGeneratedQuestion(candidate, questionType, difficulty);

                    if (validationError == null) {
                        jsonNode = candidate;
                        break;
                    }

                    lastRejectReason = validationError;
                    System.err.println("[AI Generate] Rejected candidate (attempt " + attempt + "): " + validationError);
                } catch (Exception e) {
                    lastRejectReason = "Model response is not valid JSON: " + e.getMessage();
                    System.err.println("[AI Generate] Parse failed (attempt " + attempt + "): " + e.getMessage());
                }
            }

            if (jsonNode == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "AI generated question did not pass internal quality checks after "
                                + GENERATION_MAX_ATTEMPTS + " attempts: " + lastRejectReason + ". Please retry."
                ));
            }

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
            
            // Debug logs
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

            // Verify AI-generated SQL in sandbox (prevent incorrect SQL from damaging database)
            SandboxContext sandbox = null;
            try {
                if (setupSql != null && !setupSql.trim().isEmpty()) {
                    if (containsForbiddenSystemSchemaQuery(expectedSql)) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "success", false,
                                "error", "Expected SQL references system schema (information_schema/mysql/performance_schema/sys). Please use business tables defined in setupSql."
                        ));
                    }

                    // Use sandbox to verify setupSql and expectedSql
                    boolean isValid = glmService.verifyQuestionInSandbox(setupSql, expectedSql);
                    if (!isValid) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "success", false,
                                "error", "AI-generated SQL validation failed: setupSql or expectedSql contains errors, please regenerate"
                        ));
                    }
                }
            } finally {
                // Sandbox already cleaned up in verifyQuestionInSandbox
            }

            String tablePrefix = null;
            if (setupSql != null && !setupSql.trim().isEmpty()) {
                tablePrefix = setupSqlExecutorService.executeSetupSql(setupSql);
            }

            // description 和 databaseContext 分开存储，不再拼接
            Question question = quizService.addQuestionToQuiz(
                    quizId,
                    title,
                    questionType,
                    description,
                    databaseContext,
                    expectedSql,
                    setupSql,  // Pass setupSql parameter
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

    private boolean containsForbiddenSystemSchemaQuery(String sql) {
        if (sql == null || sql.isBlank()) {
            return false;
        }
        String normalized = sql.toLowerCase(Locale.ROOT);
        if (normalized.contains("show tables") || normalized.contains("show databases")) {
            return true;
        }
        return FORBIDDEN_SYSTEM_SCHEMA_PATTERN.matcher(sql).find();
    }

    private String validateGeneratedQuestion(JsonNode jsonNode, String requestedType, String requestedDifficulty) {
        if (jsonNode == null) {
            return "AI response is empty";
        }

        String setupSql = jsonNode.path("setupSql").asText("");
        String expectedSql = jsonNode.path("expectedSql").asText("");
        String title = jsonNode.path("title").asText("");
        String description = jsonNode.path("description").asText("");
        String databaseContext = jsonNode.path("databaseContext").asText("");
        String answer = jsonNode.path("answer").asText("");
        String actualType = jsonNode.path("questionType").asText("");
        String actualDifficulty = jsonNode.path("difficulty").asText("");

        if (title.isBlank()) {
            return "Generated title is empty";
        }
        if (description.isBlank()) {
            return "Generated description is empty";
        }
        if (setupSql.isBlank()) {
            return "Generated setupSql is empty";
        }
        if (expectedSql.isBlank()) {
            return "Generated expectedSql is empty";
        }
        if (containsForbiddenSystemSchemaQuery(expectedSql)) {
            return "expectedSql queries system schema/table";
        }
        if (containsCjkCharacters(title) || containsCjkCharacters(description)
                || containsCjkCharacters(databaseContext) || containsCjkCharacters(answer)
                || containsCjkCharacters(setupSql) || containsCjkCharacters(expectedSql)) {
            return "Generated content must be English only (CJK characters detected)";
        }

        if ("SELECT_BASIC".equalsIgnoreCase(requestedType) && "EASY".equalsIgnoreCase(requestedDifficulty)) {
            String trimmed = expectedSql.trim();
            if (trimmed.isEmpty() || !trimmed.toLowerCase(Locale.ROOT).startsWith("select")) {
                return "SELECT_BASIC EASY expectedSql must start with SELECT";
            }

            String withoutTrailingSemicolon = trimmed.endsWith(";")
                    ? trimmed.substring(0, trimmed.length() - 1)
                    : trimmed;
            if (withoutTrailingSemicolon.contains(";")) {
                return "SELECT_BASIC EASY expectedSql must contain only one SQL statement";
            }
            if (BASIC_EASY_COMPLEX_KEYWORDS_PATTERN.matcher(expectedSql).find()) {
                return "SELECT_BASIC EASY expectedSql is too complex (JOIN/GROUP/HAVING/UNION/CTE)";
            }
            if (SUBQUERY_PATTERN.matcher(expectedSql).find()) {
                return "SELECT_BASIC EASY expectedSql must not contain subquery";
            }
        }

        if (!actualType.isBlank() && !requestedType.equalsIgnoreCase(actualType)) {
            return "Generated questionType mismatch: requested " + requestedType + ", got " + actualType;
        }
        if (!actualDifficulty.isBlank() && !requestedDifficulty.equalsIgnoreCase(actualDifficulty)) {
            return "Generated difficulty mismatch: requested " + requestedDifficulty + ", got " + actualDifficulty;
        }

        return null;
    }

    private boolean containsCjkCharacters(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return CJK_CHAR_PATTERN.matcher(text).find();
    }
}
