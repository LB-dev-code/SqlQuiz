package com.example.SqlQuiz.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.entity.SandboxContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GLMService {

    private final RestClient restClient;
    private double temperature = 0.6;
    private String model;
    //private  String description = "";
    private  String create_promtp ;

    private String score_prompt ;

    // Scoring criteria knowledge base ID
    private static final String SCORING_KNOWLEDGE_ID = "2019315389405835264";

    @Autowired
    private SandboxDatabaseService sandboxService;

//    @Autowired
//    private SetupSqlExecutorService setupSqlExecutorService;

    public GLMService(@Value("${glm.api-key}") String apiKey,
                       @Value("${glm.base-url}") String baseUrl,
                       @Value("${glm.model}") String model) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    /**
     * Perform image OCR recognition using GLM-4V
     * @param imagePath Image file path
     * @return Recognized text content
     */
    public String performOCR(String imagePath) {
        try {
            // 1. Read image and convert to Base64
            Path path = Path.of(imagePath);
            byte[] imageBytes = Files.readAllBytes(path);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            
            // 2. Build multimodal request (image + OCR prompt)
            String ocrPrompt = "Please carefully extract all text content from this image. " +
                    "This is likely a SQL quiz question or database exercise. " +
                    "Return the text exactly as it appears, maintaining the structure and format. " +
                    "Include question titles, descriptions, table structures, and any SQL code if present.";
            
            // Create multimodal message
            Map<String, Object> imageContent = new HashMap<>();
            imageContent.put("type", "image_url");
            Map<String, String> imageUrl = new HashMap<>();
            imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
            imageContent.put("image_url", imageUrl);
            
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", ocrPrompt);
            
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", List.of(textContent, imageContent));
            
            // 3. Call glm-4v model
            Map<String, Object> body = new HashMap<>();
            body.put("model", "glm-4v");  // Use visual model
            body.put("messages", List.of(message));
            body.put("max_tokens", 2000);
            body.put("temperature", 0.1);  // Lower temperature for better recognition accuracy
            
            // 4. Send request
            String result = restClient.post()
                    .uri("/api/paas/v4/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            
            System.out.println("OCR API Response: " + result);
            
            if (result == null || result.trim().isEmpty()) {
                throw new RuntimeException("OCR API returned empty response");
            }
            
            // 5. Parse and return recognition result
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(result);
            
            // Check for errors
            JsonNode error = jsonNode.get("error");
            if (error != null) {
                String errorMessage = error.get("message") != null ? error.get("message").asText() : error.asText();
                throw new RuntimeException("OCR API returned error: " + errorMessage);
            }
            
            // Extract recognized text
            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode messageNode = choices.get(0).get("message");
                if (messageNode != null) {
                    JsonNode content = messageNode.get("content");
                    if (content != null) {
                        String recognizedText = content.asText();
                        System.out.println("OCR recognized text: " + recognizedText);
                        return recognizedText;
                    }
                }
            }
            
            throw new RuntimeException("Unable to extract OCR result from API response");
            
        } catch (IOException e) {
            System.err.println("Failed to read image file: " + e.getMessage());
            throw new RuntimeException("Failed to read image file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("OCR API call failed: " + e.getMessage());
            throw new RuntimeException("OCR API call failed: " + e.getMessage());
        }
    }

    /**
     * Method to generate questions
     */
    public String chat_create_quiz (String description) throws JsonProcessingException {
        // Generate unique identifier to ensure table names don't conflict
        String uniqueId = String.valueOf(System.currentTimeMillis());
        String randomSuffix = String.valueOf((int)(Math.random() * 1000));
        String tablePrefix = "quiz_q_" + uniqueId.substring(uniqueId.length() - 6) + "_" + randomSuffix;
        
        create_promtp = "You are a database teaching expert. Please generate an English-described question for a MySQL testing platform. I will provide the question type and difficulty level, please return data in standard JSON format.\n" +
                "\n" +
                "Required JSON format:\n" +
                "{\n" +
                "  \"questionTitle\": \"Question title\",\n" +
                "  \"questionDescription\": \"Detailed question description and requirements\",\n" +
                "  \"databaseContext\": \"Database table structure with SAMPLE DATA in Markdown table format\",\n" +
                "  \"expectedSql\": \"Correct answer SQL statement\",\n" +
                "  \"setupSql\": \"SQL statements for creating table structure and inserting sample data\",\n" +
                "  \"hints\": \"Optional tips for students\"\n" +
                "}\n" +
                "\n" +
                "Question requirements:\n" +
                "1. questionTitle: Concise question title\n" +
                "2. questionDescription: Detailed description of the query task\n" +
                "3. **CRITICAL - databaseContext Format:**\n" +
                "   The databaseContext field MUST contain Markdown tables with ACTUAL SAMPLE DATA.\n" +
                "   **IMPORTANT**: Display table names WITHOUT the prefix (use simple table names only).\n" +
                "   Use this exact format:\n" +
                "   \n" +
                "   students table:\n" +
                "   \n" +
                "   | id | name | age | score |\n" +
                "   |----|------|-----|-------|\n" +
                "   | 1  | John | 20  | 85.5  |\n" +
                "   | 2  | Mary | 21  | 92.0  |\n" +
                "   | 3  | Bob  | 19  | 78.5  |\n" +
                "   \n" +
                "   The sample data should match the INSERT statements in setupSql.\n" +
                "4. setupSql: **IMPORTANT** Please provide complete SQL statements for creating table structure and inserting sample data, including:\n" +
                "   - CREATE TABLE statements: Create required table structure for the question, use the following unique prefix: `" + tablePrefix + "_`\n" +
                "   - Table name format: `" + tablePrefix + "_[table_name]`, e.g., `" + tablePrefix + "_students`, `" + tablePrefix + "_orders`\n" +
                "   - **Key**: All table primary keys must use AUTO_INCREMENT to ensure no duplicate primary key values\n" +
                "   - INSERT INTO statements: Insert 5-8 sample data records with APPROPRIATE DISTRACTOR DATA\n" +
                "   - **CRITICAL - Distractor Data**: The table must include records that test different scenarios:\n" +
                "     * Some records that MATCH the query criteria (correct answers)\n" +
                "     * Some records that DON'T match the criteria (distractors/false answers)\n" +
                "     * Edge cases: NULL values, boundary values, similar but not matching values\n" +
                "     * Example: If asking for names containing 'United', include 'United States', 'United Kingdom' (match) AND 'Germany', 'France' (distractors)\n" +
                "   - Ensure SQL statements can be executed directly in MySQL for initializing test database (testdb)\n" +
                "   - **Important**: Use the provided unique prefix to ensure table names do not conflict\n" +
                "5. expectedSql: **IMPORTANT** Standard answer SQL statement, use simple table names WITHOUT prefix (e.g., SELECT * FROM students, NOT SELECT * FROM " + tablePrefix + "_students)\n" +
                "6. hints: Optional solving tips\n" +
                "\n" +
                "Ensure you return standard JSON format without any additional text.\n";
        List<Map<String, String>> message_creat_quiz = List.of(
                Map.of("role", "user", "content", create_promtp + "\n\n" + description)
        );
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", message_creat_quiz,
                "max_tokens", 4000,
                "temperature", temperature
        );
        
        String result = "";
        try {
            result = restClient.post()
                    .uri("/api/paas/v4/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            System.out.println("GLM API Response: " + result);
            
            if (result == null || result.trim().isEmpty()) {
                throw new RuntimeException("GLM API returned empty response");
            }
            
            // Remove possible BOM markers and leading/trailing whitespace
            result = result.trim();
            if (result.startsWith("\uFEFF")) {
                result = result.substring(1);
            }
            
            // Check if response starts with error code
            if (result.startsWith("error:") || result.startsWith("Error:")) {
                throw new RuntimeException("GLM API returned error: " + result);
            }
            
            System.out.println("Attempting to parse JSON: " + result.substring(0, Math.min(200, result.length())));
            
            // Parse JSON response
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(result);
            
            // Check for error messages
            JsonNode error = jsonNode.get("error");
            if (error != null) {
                String errorMessage = error.get("message") != null ? error.get("message").asText() : error.asText();
                throw new RuntimeException("GLM API returned error: " + errorMessage);
            }
            
            JsonNode choices = jsonNode.get("choices");
            
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        String contentText = content.asText();
                        System.out.println("Extracted content: " + contentText);
                        return contentText;
                    }
                }
            }
            
            // If unable to parse, return original response for debugging
            System.err.println("Unable to extract content from GLM API response, returning original response");
            return result;
            
        } catch (JsonProcessingException e) {
            System.err.println("JSON parsing error: " + e.getMessage());
            System.err.println("Response was: " + result);
            // If JSON parsing fails, try to return original response
            if (result != null && !result.isEmpty()) {
                System.err.println("Returning original response for debugging");
                return result;
            }
            throw new RuntimeException("JSON parsing failed: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("GLM API call failed: " + e.getMessage());
            System.err.println("Response was: " + result);
            throw new RuntimeException("GLM API call failed: " + e.getMessage());
        }
    }


    public String score_answer(Double score, String description, String expected_answer, String student_answer) throws JsonProcessingException {
        // *** CRITICAL: Preprocess SQL BEFORE sending to AI to prevent students from misleading with extra text ***
        String preprocessedExpected = preprocessSql(expected_answer);
        String preprocessedStudent = preprocessSql(student_answer);

        System.out.println("[AI Score] ========== SQL Preprocessing ==========");
        System.out.println("[AI Score] Original expected answer: " + expected_answer);
        System.out.println("[AI Score] Preprocessed expected answer: " + preprocessedExpected);
        System.out.println("[AI Score] Original student answer: " + student_answer);
        System.out.println("[AI Score] Preprocessed student answer: " + preprocessedStudent);
        System.out.println("[AI Score] ========================================");

        score_prompt = String.format(
                "You are a SQL assignment grading system. You must strictly follow the grading process from the 'SQL Assignment AI Grading System Behavior Specification Document'.\n\n" +
                        "**Core Requirement: Grading must be deterministic; identical inputs must produce identical outputs.**\n\n" +
                        "**IMPORTANT: SQL Preprocessing Already Completed**\n" +
                        "The student's answer and expected answer have ALREADY been preprocessed to remove:\n" +
                        "- All SQL comments (-- comments, /* block comments */, # inline comments)\n" +
                        "- All non-SQL content (explanations, notes, natural language text)\n" +
                        "You will receive ONLY pure SQL statements. Grade based on the SQL content you receive.\n\n" +
                        "**Input Information:**\n" +
                        "- Full score: %.1f points\n" +
                        "- Question description: %s\n" +
                        "- Expected answer (preprocessed): %s\n" +
                        "- Student answer (preprocessed): %s\n\n" +
                        "**Grading Process (Strictly Follow):**\n" +
                        "1. Input preprocessing: Convert to lowercase, trim whitespace, normalize spaces\n" +
                        "2. Exact match: After normalization, if identical = full score\n" +
                        "3. Semantic equivalence: Same logic but different syntax = full score\n" +
                        "4. Partial credit: Calculate token edit distance, use formula Score = M * (1 - D_min/T)\n" +
                        "5. Empty answer or non-SQL = 0 points\n\n" +
                        "**Output Format (Return JSON only):**\n" +
                        "{\n" +
                        "  \"score\": number(to one decimal place),\n" +
                        "  \"fullScore\": %.1f,\n" +
                        "  \"isCorrect\": boolean,\n" +
                        "  \"matchType\": \"EXACT|SEMANTIC|PARTIAL|ZERO\",\n" +
                        "  \"editDistance\": number(token edit distance),\n" +
                        "  \"feedback\": \"Brief grading explanation in English\",\n" +
                        "  \"scoringRule\": \"Detailed explanation of which scoring rule was applied and why this score was given\",\n" +
                        "  \"editDistanceDetails\": \"Detailed breakdown of the edit distance calculation - what specific tokens needed to be changed to transform student's answer into the correct answer\"\n" +
                        "}",
                score, description, preprocessedExpected, preprocessedStudent, score
        );

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content",
                        "You are a SQL grading expert. You must strictly follow all requirements from the 'SQL Assignment AI Grading System Behavior Specification Document' in the knowledge base. " +
                        "Your grading must be based entirely on calculated edit distance and preset formulas, without introducing personal reasoning or feelings. " +
                        "Identical inputs must produce identical outputs. All feedback must be in English. " +
                        "**IMPORTANT:** The SQL answers have been preprocessed to remove all comments and non-SQL content. Grade only the pure SQL statements you receive."),
                Map.of("role", "user", "content", score_prompt)
        );

        // 使用知识库 + temperature=0 确保确定性输出
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", 1000);
        body.put("temperature", 0);  // 0 = 完全确定性
        body.put("top_p", 0.1);       // 进一步限制随机性
        
        // 添加评分准则知识库
        Map<String, Object> tools = new HashMap<>();
        tools.put("type", "retrieval");
        tools.put("retrieval", Map.of(
                "knowledge_id", SCORING_KNOWLEDGE_ID,
                "prompt_template", "Find the answer to question\n\"\"\"\n{{question}}\n\"\"\"\nfrom the document\n\"\"\"\n{{knowledge}}\n\"\"\"\nAfter finding the answer, use only the scoring rules from the document for grading."
        ));
        body.put("tools", List.of(tools));

        String result = "";
        try {
            System.out.println("[AI Score] ========== Starting AI scoring (using knowledge base: " + SCORING_KNOWLEDGE_ID + ") ==========");
            System.out.println("[AI Score] Question full score: " + score);
            System.out.println("[AI Score] Question description: " + description);
            System.out.println("[AI Score] Expected answer: " + expected_answer);
            System.out.println("[AI Score] Student answer: " + student_answer);

            result = restClient.post()
                    .uri("/api/paas/v4/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            System.out.println("GLM Score API Response: " + result);

            if (result == null || result.trim().isEmpty()) {
                throw new RuntimeException("GLM scoring API returned empty response");
            }

            // Remove possible BOM markers and leading/trailing whitespace
            result = result.trim();
            if (result.startsWith("\uFEFF")) {
                result = result.substring(1);
            }

            // Check if response starts with error code
            if (result.startsWith("error:") || result.startsWith("Error:")) {
                throw new RuntimeException("GLM scoring API returned error: " + result);
            }

            // Parse JSON response
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(result);

            // Check for error messages
            JsonNode error = jsonNode.get("error");
            if (error != null) {
                String errorMessage = error.get("message") != null ? error.get("message").asText() : error.asText();
                throw new RuntimeException("GLM scoring API returned error: " + errorMessage);
            }

            JsonNode choices = jsonNode.get("choices");

            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        String contentText = content.asText();
                        System.out.println("[AI Score] Extracted score result: " + contentText);

                        // 解析JSON并打印调试信息到控制台
                        try {
                            JsonNode resultJson = objectMapper.readTree(contentText);
                            System.out.println("==================== AI 评分调试信息 ====================");
                            System.out.println("[AI评分规则] " + (resultJson.has("scoringRule") ? resultJson.get("scoringRule").asText() : "N/A"));
                            System.out.println("[编辑距离详情] " + (resultJson.has("editDistanceDetails") ? resultJson.get("editDistanceDetails").asText() : "N/A"));
                            System.out.println("[匹配类型] " + (resultJson.has("matchType") ? resultJson.get("matchType").asText() : "N/A"));
                            System.out.println("[编辑距离] " + (resultJson.has("editDistance") ? resultJson.get("editDistance").asText() : "N/A"));
                            System.out.println("[得分] " + (resultJson.has("score") ? resultJson.get("score").asText() : "N/A") + "/" + score);
                            System.out.println("======================================================");
                        } catch (Exception parseEx) {
                            System.err.println("[AI Score] 解析调试信息失败: " + parseEx.getMessage());
                        }

                        return contentText;
                    }
                }
            }

            // If unable to parse, return original response for debugging
            System.err.println("[AI Score] Unable to extract content from GLM scoring API response, returning original response");
            return result;

        } catch (JsonProcessingException e) {
            System.err.println("[AI Score] JSON parsing error: " + e.getMessage());
            System.err.println("[AI Score] Response was: " + result);
            // If JSON parsing fails, try to return original response
            if (result != null && !result.isEmpty()) {
                System.err.println("[AI Score] Returning original scoring response for debugging");
                return result;
            }
            throw new RuntimeException("GLM scoring API JSON parsing failed: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[AI Score] API call failed: " + e.getMessage());
            System.err.println("[AI Score] Response was: " + result);
            throw new RuntimeException("GLM scoring API call failed: " + e.getMessage());
        }
    }

    @Autowired
    private QuizTableMetadataService tableMetadataService;

    public String normalizeQuestion(String input, String inputType) throws JsonProcessingException {
        String tablePrefix = tableMetadataService.generateUniqueTablePrefix();
        
        String normalizePrompt = "You are a SQL question normalization expert. Please normalize the user's input into a standard MySQL quiz question format.\n" +
                "\n" +
                "User Input Type: " + inputType + "\n" +
                "User Input: " + input + "\n" +
                "\n" +
                "Please analyze and reorganize this question into the following JSON format:\n" +
                "{\n" +
                "  \"title\": \"Question Title\",\n" +
                "  \"description\": \"Detailed question description\",\n" +
                "  \"databaseContext\": \"Database table structure with SAMPLE DATA in Markdown table format\",\n" +
                "  \"setupSql\": \"Complete SQL statements for creating tables and inserting sample data\",\n" +
                "  \"expectedSql\": \"Standard answer SQL statement\",\n" +
                "  \"answer\": \"Detailed answer explanation in Markdown format\",\n" +
                "  \"questionType\": \"One of: SELECT_BASIC, SELECT_JOIN, SELECT_SUBQUERY, SELECT_AGGREGATE, SELECT_COMPLEX, DML_INSERT, DML_UPDATE, DML_DELETE\",\n" +
                "  \"difficulty\": \"One of: EASY, MEDIUM, HARD\"\n" +
                "}\n" +
                "\n" +
                "Important requirements:\n" +
                "1. Use this unique table prefix for all tables: `" + tablePrefix + "_`\n" +
                "2. All table names must follow format: `" + tablePrefix + "_[table_name]`\n" +
                "3. All primary keys must use AUTO_INCREMENT\n" +
                "4. Provide 5-8 sample records in setupSql with APPROPRIATE DISTRACTOR DATA\n" +
                "5. **CRITICAL - Distractor Data**: The table must include:\n" +
                "   * Records that MATCH the query criteria (correct answers)\n" +
                "   * Records that DON'T match the criteria (distractors)\n" +
                "   * Edge cases: NULL values, boundary values, similar-but-not-matching values\n" +
                "   * Example: For 'names containing United', include 'United States', 'United Kingdom' AND 'Germany', 'France'\n" +
                "6. **CRITICAL - databaseContext Format:**\n" +
                "   The databaseContext field MUST contain Markdown tables with ACTUAL SAMPLE DATA.\n" +
                "   **IMPORTANT**: Display table names WITHOUT the prefix (use simple table names only).\n" +
                "   Use this exact format:\n" +
                "   \n" +
                "   employees table:\n" +
                "   \n" +
                "   | id | name | department | salary |\n" +
                "   |----|------|------------|--------|\n" +
                "   | 1  | John | IT         | 5000   |\n" +
                "   | 2  | Mary | HR         | 4500   |\n" +
                "   \n" +
                "   The sample data should match the INSERT statements in setupSql.\n" +
                "7. setupSql: **IMPORTANT** Use table names WITH prefix: `" + tablePrefix + "_[table_name]`\n" +
                "   - CREATE TABLE format: CREATE TABLE `" + tablePrefix + "_[table_name]` (...)\n" +
                "   - **Do NOT use FOREIGN KEY constraints** (sandbox user doesn't have REFERENCES permission)\n" +
                "   - INSERT INTO format: INSERT INTO `" + tablePrefix + "_[table_name]` (...)\n" +
                "8. expectedSql: **IMPORTANT** Use simple table names WITHOUT prefix (e.g., SELECT * FROM employees, NOT SELECT * FROM " + tablePrefix + "_employees)\n" +
                "9. Return valid JSON only, no additional text\n" +
                "10. The answer field should include step-by-step solution explanation in Markdown\n" +
                "11. Infer appropriate questionType and difficulty from the input question";
        
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", normalizePrompt)
        );
        
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", 4000,
                "temperature", 0.6
        );
        
        String result = "";
        try {
            result = restClient.post()
                    .uri("/api/paas/v4/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
                    
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(result);
            
            JsonNode error = jsonNode.get("error");
            if (error != null) {
                String errorMessage = error.get("message") != null ? error.get("message").asText() : error.asText();
                throw new RuntimeException("GLM API returned error: " + errorMessage);
            }
            
            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        String contentText = content.asText();
                        
                        String cleanResponse = contentText.trim();
                        if (cleanResponse.startsWith("```json")) {
                            cleanResponse = cleanResponse.substring(7);
                        }
                        if (cleanResponse.startsWith("```")) {
                            cleanResponse = cleanResponse.substring(3);
                        }
                        if (cleanResponse.endsWith("```")) {
                            cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3);
                        }
                        
                        JsonNode responseJson = objectMapper.readTree(cleanResponse.trim());
                        
                        String responseWithPrefix = objectMapper.writeValueAsString(responseJson);
                        
                        return responseWithPrefix;
                    }
                }
            }
            
            return result;
            
        } catch (Exception e) {
            System.err.println("Question normalization failed: " + e.getMessage());
            throw new RuntimeException("Question normalization failed: " + e.getMessage());
        }
    }

    private static final String KNOWLEDGE_BASE_ID = "2013534505419395072";

    public String generateQuestionWithRAG(String questionType, String difficulty) throws JsonProcessingException {
        String tablePrefix = tableMetadataService.generateUniqueTablePrefix();
        // 题目类型映射
        String typeMapping = "{\n" +
                "  \"SINGLE_TABLE\": \"Single table query\",\n" +
                "  \"GROUP_AGGREGATE\": \"Group by and aggregate functions\",\n" +
                "  \"MULTI_JOIN\": \"Multiple table JOIN queries\",\n" +
                "  \"SUBQUERY\": \"Subquery operations\",\n" +
                "  \"COMPREHENSIVE\": \"Comprehensive complex queries\",\n" +
                "  \"UPDATE_DELETE\": \"UPDATE and DELETE operations\"\n" +
                "}";

        // 构建RAG Prompt
        String ragPrompt = "You are a MySQL quiz question generation expert with access to a knowledge base of SQL problems.\n" +
                "\n" +
                "Knowledge Base ID: " + KNOWLEDGE_BASE_ID + "\n" +
                "\n" +
                "Please generate a NEW and ORIGINAL question based on the following requirements:\n" +
                "Question Type: " + questionType + " (" + getTypeDescription(questionType) + ")\n" +
                "Difficulty: " + difficulty + "\n" +
                "\n" +
                "Question Type Mapping:\n" + typeMapping + "\n" +
                "\n" +
                "Requirements:\n" +
                "1. Reference the style and structure from knowledge base, but create a completely NEW scenario\n" +
                "2. Use different business domain, table names, and data from knowledge base examples\n" +
                "3. Generate in this JSON format:\n" +
                "{\n" +
                "  \"title\": \"Question title\",\n" +
                "  \"description\": \"Detailed question description\",\n" +
                "  \"databaseContext\": \"Table structure with SAMPLE DATA in Markdown table format\",\n" +
                "  \"setupSql\": \"CREATE TABLE and INSERT statements\",\n" +
                "  \"expectedSql\": \"Correct SQL answer\",\n" +
                "  \"answer\": \"Detailed solution explanation in Markdown\",\n" +
                "  \"questionType\": \"" + questionType + "\",\n" +
                "Difficulty: " + difficulty + "\n"
                + "Please strictly refer to the difficulty level definitions in the knowledge base to ensure the generated question matches the expected complexity for " + difficulty + " level questions.\n"
                + "\n" +
                "\n" +
                "**CRITICAL - databaseContext Format:**\n" +
                "The databaseContext field MUST contain Markdown tables with ACTUAL SAMPLE DATA.\n" +
                "**IMPORTANT**: Display table names WITHOUT the prefix (use simple table names only).\n" +
                "Use this exact format:\n" +
                "\n" +
                "employees table:\n" +
                "\n" +
                "| id | name | department | salary |\n" +
                "|----|------|------------|--------|\n" +
                "| 1  | John | IT         | 5000   |\n" +
                "| 2  | Mary | HR         | 4500   |\n" +
                "| 3  | Bob  | IT         | 5500   |\n" +
                "\n" +
                "The sample data in databaseContext should match the INSERT statements in setupSql.\n" +
                "\n" +
                "Table naming requirements:\n" +
                "- setupSql: Use unique prefix: `" + tablePrefix + "_`\n" +
                "- setupSql format: CREATE TABLE `" + tablePrefix + "_[table_name]` (...)\n" +
                "- **Do NOT use FOREIGN KEY constraints** (sandbox user doesn't have REFERENCES permission)\n" +
                "- **CRITICAL - INSERT FORMAT**: Every INSERT statement MUST include values for ALL columns defined in CREATE TABLE\n" +
                "  * Use format: INSERT INTO `" + tablePrefix + "_[table_name]` (col1, col2, col3, ...) VALUES (val1, val2, val3, ...)\n" +
                "  * **NEVER omit any column** - always explicitly list all columns in the INSERT statement\n" +
                "  * For AUTO_INCREMENT columns: either include the value OR set to 0/NULL (but still list the column)\n" +
                "  * This ensures no column has NULL values across all rows\n" +
                "- expectedSql: **IMPORTANT** Use simple table names WITHOUT prefix (e.g., SELECT * FROM employees, NOT SELECT * FROM " + tablePrefix + "_employees)\n" +
                "- All primary keys: AUTO_INCREMENT\n" +
                "- **CRITICAL - Distractor Data**: Insert 5-8 sample records with:\n" +
                "  * Records matching the query criteria (correct answers)\n" +
                "  * Records NOT matching the criteria (distractors)\n" +
                "  * Edge cases: boundary values, similar-but-not-matching values\n" +
                "  * Example: For 'names containing United', include 'United States', 'United Kingdom' AND 'Germany', 'France'\n" +
                "  * **IMPORTANT**: Every column should have meaningful data in every row - avoid empty NULL values\n" +
                "\n" +
                "Return valid JSON only, no extra text.";

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", ragPrompt)
        );

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", 4000);
        body.put("temperature", 0.7);

        // 添加RAG知识库支持
        Map<String, Object> tools = new HashMap<>();
        tools.put("type", "retrieval");
        tools.put("retrieval", Map.of("knowledge_id", KNOWLEDGE_BASE_ID));
        body.put("tools", List.of(tools));

        String result = "";
        try {
            // ========== RAG调试信息开始 ==========
            System.out.println("==================== RAG题目生成调试信息 ====================");
            System.out.println("[RAG Config] 知识库ID: " + KNOWLEDGE_BASE_ID);
            System.out.println("[RAG Config] 题目类型: " + questionType);
            System.out.println("[RAG Config] 难度级别: " + difficulty);
            System.out.println("[RAG Config] 表前缀: " + tablePrefix);
            System.out.println("[RAG Request] 使用RAG检索工具: true");
            System.out.println("============================================================");

            result = restClient.post()
                    .uri("/api/paas/v4/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(result);

            // 打印RAG检索相关信息
            System.out.println("==================== RAG响应分析 ====================");
            System.out.println("[RAG Response] 完整响应长度: " + result.length() + " 字符");
            System.out.println("[RAG Response] 响应预览: " + result.substring(0, Math.min(500, result.length())));

            // 检查智谱 RAG 检索结果格式：data 数组
            if (jsonNode.has("data")) {
                JsonNode dataArray = jsonNode.get("data");
                System.out.println("==================== RAG检索结果 ====================");
                System.out.println("[RAG Data] 检索到 " + dataArray.size() + " 个文档切片");

                if (dataArray.isArray()) {
                    for (int i = 0; i < dataArray.size() && i < 5; i++) {  // 最多显示5个
                        JsonNode item = dataArray.get(i);
                        System.out.println("\n--- 切片 " + (i + 1) + " ---");

                        // 切片内容
                        if (item.has("text")) {
                            String text = item.get("text").asText();
                            System.out.println("[Content] " + (text.length() > 200 ? text.substring(0, 200) + "..." : text));
                        }

                        // 相似度分数
                        if (item.has("score")) {
                            System.out.println("[Score] 相似度: " + item.get("score").asDouble());
                        }

                        // 元数据
                        if (item.has("metadata")) {
                            JsonNode metadata = item.get("metadata");
                            System.out.println("[Metadata]");
                            if (metadata.has("doc_name")) {
                                System.out.println("  文档: " + metadata.get("doc_name").asText());
                            }
                            if (metadata.has("knowledge_id")) {
                                System.out.println("  知识库ID: " + metadata.get("knowledge_id").asText());
                            }
                            if (metadata.has("doc_id")) {
                                System.out.println("  文档ID: " + metadata.get("doc_id").asText());
                            }
                            if (metadata.has("contextual_text") && !metadata.get("contextual_text").isNull()) {
                                String ctxText = metadata.get("contextual_text").asText();
                                System.out.println("  上下文增强: " + (ctxText.length() > 100 ? ctxText.substring(0, 100) + "..." : ctxText));
                            }
                        }
                    }
                }
                System.out.println("=====================================================");
            } else {
                System.out.println("[RAG] 响应中没有 data 字段（检索结果可能在后台处理）");
            }

            // 检查其他可能的检索信息字段
            if (jsonNode.has("retrieval_info")) {
                JsonNode retrievalInfo = jsonNode.get("retrieval_info");
                System.out.println("[RAG Retrieved] 检索信息: " + retrievalInfo.toPrettyString());
            }
            if (jsonNode.has("context")) {
                JsonNode context = jsonNode.get("context");
                System.out.println("[RAG Context] 上下文: " + context.toPrettyString());
            }

            // 检查choices中的检索相关信息
            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    // 检查tool_calls（可能包含检索结果）
                    if (message.has("tool_calls")) {
                        JsonNode toolCalls = message.get("tool_calls");
                        System.out.println("[RAG Tool Calls] 工具调用: " + toolCalls.toPrettyString());
                    }
                    // 检查context字段
                    if (message.has("context")) {
                        JsonNode context = message.get("context");
                        System.out.println("[RAG Message Context] 消息上下文: " + context.toPrettyString());
                    }
                    // 检查retrieval_info字段
                    if (message.has("retrieval_info")) {
                        JsonNode retrievalInfo = message.get("retrieval_info");
                        System.out.println("[RAG Retrieved Info] 检索到的信息: " + retrievalInfo.toPrettyString());
                    }
                    // 检查role字段（可能有retrieval角色）
                    if (message.has("role")) {
                        System.out.println("[RAG Message Role] 消息角色: " + message.get("role").asText());
                    }
                }
            }
            System.out.println("=====================================================");

            JsonNode error = jsonNode.get("error");
            if (error != null) {
                String errorMessage = error.get("message") != null ? error.get("message").asText() : error.asText();
                throw new RuntimeException("GLM API returned error: " + errorMessage);
            }

            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        String contentText = content.asText();

                        // 清理JSON
                        String cleanResponse = contentText.trim();
                        if (cleanResponse.startsWith("```json")) {
                            cleanResponse = cleanResponse.substring(7);
                        }
                        if (cleanResponse.startsWith("```")) {
                            cleanResponse = cleanResponse.substring(3);
                        }
                        if (cleanResponse.endsWith("```")) {
                            cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3);
                        }

                        JsonNode responseJson = objectMapper.readTree(cleanResponse.trim());

                        String responseWithPrefix = objectMapper.writeValueAsString(responseJson);

                        return responseWithPrefix;
                    }
                }
            }

            return result;

        } catch (Exception e) {
            System.err.println("[RAG Error] RAG题目生成失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("RAG题目生成失败: " + e.getMessage());
        }
    }

    private String getTypeDescription(String questionType) {
        switch (questionType) {
            case "SINGLE_TABLE": return "Single table query";
            case "GROUP_AGGREGATE": return "Group by and aggregate";
            case "MULTI_JOIN": return "Multiple table JOIN";
            case "SUBQUERY": return "Subquery";
            case "COMPREHENSIVE": return "Comprehensive query";
            case "UPDATE_DELETE": return "Update and delete";
            case "SELECT_BASIC": return "Basic SELECT";
            case "SELECT_JOIN": return "Table JOIN";
            case "SELECT_SUBQUERY": return "Subquery";
            case "SELECT_AGGREGATE": return "Aggregate functions";
            case "SELECT_COMPLEX": return "Complex query";
            case "DML_INSERT": return "INSERT data";
            case "DML_UPDATE": return "UPDATE data";
            case "DML_DELETE": return "DELETE data";
            default: return "Unknown type";
        }
    }

    /**
     * Generate question for self-practice mode
     */
    public String generatePracticeQuestion(String questionType, String difficulty) throws JsonProcessingException {
        String tablePrefix = tableMetadataService.generateUniqueTablePrefix();

        String practicePrompt = "You are a MySQL practice question generator for student self-study.\n\n" +
                "Generate a practice question with these requirements:\n" +
                "Question Type: " + questionType + " (" + getTypeDescription(questionType) + ")\n" +
                "Difficulty: " + difficulty + "\n\n" +
                "Return JSON format:\n" +
                "{\n" +
                "  \"title\": \"Brief question title\",\n" +
                "  \"description\": \"Detailed question description\",\n" +
                "  \"databaseContext\": \"Table structure with SAMPLE DATA in Markdown table format\",\n" +
                "  \"setupSql\": \"CREATE TABLE and INSERT statements\",\n" +
                "  \"expectedSql\": \"Correct SQL answer\",\n" +
                "  \"hints\": \"Optional hints for students\"\n" +
                "}\n\n" +
                "**CRITICAL - databaseContext Format:**\n" +
                "The databaseContext field MUST contain Markdown tables with ACTUAL SAMPLE DATA.\n" +
                "**IMPORTANT**: Display table names WITHOUT the prefix (use simple table names only).\n" +
                "Use this exact format:\n" +
                "\n" +
                "employees table:\n" +
                "\n" +
                "| id | name | department | salary |\n" +
                "|----|------|------------|--------|\n" +
                "| 1  | John | IT         | 5000   |\n" +
                "| 2  | Mary | HR         | 4500   |\n" +
                "\n" +
                "The sample data should match the INSERT statements in setupSql.\n\n" +
                "Table naming requirements:\n" +
                "- setupSql: Use prefix `" + tablePrefix + "_`\n" +
                "- setupSql format: CREATE TABLE `" + tablePrefix + "_[table_name]` (...)\n" +
                "- **Do NOT use FOREIGN KEY constraints** (sandbox user doesn't have REFERENCES permission)\n" +
                "- setupSql format: INSERT INTO `" + tablePrefix + "_[table_name]` (...)\n" +
                "- expectedSql: **IMPORTANT** Use simple table names WITHOUT prefix (e.g., SELECT * FROM employees, NOT SELECT * FROM " + tablePrefix + "_employees)\n" +
                "- All primary keys: AUTO_INCREMENT\n" +
                "- **CRITICAL - Distractor Data**: Insert 5-8 sample records with:\n" +
                "  * Records matching the query criteria (correct answers)\n" +
                "  * Records NOT matching the criteria (distractors)\n" +
                "  * Edge cases: NULL values, boundary values, similar-but-not-matching values\n" +
                "Return valid JSON only.";

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", practicePrompt)
        );

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", 3000,
                "temperature", 0.7
        );

        try {
            String result = restClient.post()
                    .uri("/api/paas/v4/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(result);

            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        String contentText = content.asText().trim();
                        if (contentText.startsWith("```json")) {
                            contentText = contentText.substring(7);
                        }
                        if (contentText.startsWith("```")) {
                            contentText = contentText.substring(3);
                        }
                        if (contentText.endsWith("```")) {
                            contentText = contentText.substring(0, contentText.length() - 3);
                        }
                        return contentText.trim();
                    }
                }
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Practice question generation failed: " + e.getMessage());
        }
    }

    /**
     * Batch generate practice questions (10 questions)
     * Generate 10 questions in one AI call based on question type distribution
     *
     * @param distribution Question type distribution, e.g., {SELECT_BASIC: 4, SELECT_JOIN: 3, SELECT_AGGREGATE: 3}
     * @param blacklist Blacklist of existing questions (after content normalization)
     * @return Question list in JSON array format
     */
    public String generatePracticeQuestionsBatch(
            Map<Question.QuestionType, Integer> distribution,
            List<String> blacklist) throws JsonProcessingException {

        // 不再为整个批次生成共享前缀
        // 每道题的 setupSql 使用简单表名，executeSetupSql 会为每道题自动生成唯一前缀
        // 这样避免了同一批次中多道题使用相同表名时的数据覆盖问题

        // Build question type distribution description
        StringBuilder distributionDesc = new StringBuilder();
        int totalQuestions = 0;
        for (Map.Entry<Question.QuestionType, Integer> entry : distribution.entrySet()) {
            distributionDesc.append(String.format("- %s: %d questions (%s)\n",
                    entry.getKey().name(),
                    entry.getValue(),
                    getTypeDescription(entry.getKey().name())));
            totalQuestions += entry.getValue();
        }

        // Build blacklist prompt (avoid duplicates)
        StringBuilder blacklistDesc = new StringBuilder();
        if (blacklist != null && !blacklist.isEmpty()) {
            blacklistDesc.append("\n**IMPORTANT - AVOID THESE QUESTIONS:**\n");
            blacklistDesc.append("Do NOT generate questions similar to these existing ones:\n");
            int count = Math.min(5, blacklist.size()); // Only show first 5 as examples
            for (int i = 0; i < count; i++) {
                blacklistDesc.append(i + 1).append(". ").append(blacklist.get(i)).append("\n");
            }
            if (blacklist.size() > 5) {
                blacklistDesc.append("... and ").append(blacklist.size() - 5).append(" more\n");
            }
        }

        String batchPrompt = "You are a MySQL practice question generator. Generate " + totalQuestions + " UNIQUE practice questions.\n\n" +
                "**Question Distribution:**\n" + distributionDesc.toString() + "\n" +
                "**Requirements:**\n" +
                "1. Generate exactly " + totalQuestions + " unique questions\n" +
                "2. Each question must be DIFFERENT in scenario, table names, and query logic\n" +
                "3. Use the specified question types and counts\n" +
                "4. Return a JSON ARRAY of questions\n\n" +
                blacklistDesc.toString() + "\n" +
                "**Return Format (JSON ARRAY):**\n" +
                "[\n" +
                "  {\n" +
                "    \"questionType\": \"SELECT_BASIC\",\n" +
                "    \"difficulty\": \"EASY\",\n" +
                "    \"title\": \"Brief title\",\n" +
                "    \"description\": \"Question description\",\n" +
                "    \"databaseContext\": \"Table structure: Show actual table with sample data in Markdown table format\",\n" +
                "    \"setupSql\": \"CREATE TABLE and INSERT statements\",\n" +
                "    \"expectedSql\": \"Correct SQL\",\n" +
                "    \"hints\": \"Optional hints\"\n" +
                "  },\n" +
                "  ...\n" +
                "]\n\n" +
                "**Table Naming:**\n" +
                "- setupSql: Use simple, descriptive table names WITHOUT any prefix (e.g., CREATE TABLE employees (...), INSERT INTO employees (...))\n" +
                "- **CRITICAL**: Each question MUST use UNIQUE table names that are different from all other questions in this batch\n" +
                "- Do NOT reuse the same table name across different questions (e.g., if question 1 uses 'world', question 2 must NOT use 'world')\n" +
                "- **Do NOT use FOREIGN KEY constraints** (sandbox user doesn't have REFERENCES permission)\n" +
                "- expectedSql: Use the same simple table names as in setupSql (e.g., SELECT * FROM employees)\n" +
                "- All primary keys: AUTO_INCREMENT\n" +
                "- **CRITICAL - Distractor Data**: Insert 5-8 sample records per table with:\n" +
                "  * Records matching the query criteria (correct answers)\n" +
                "  * Records NOT matching the criteria (distractors)\n" +
                "  * Edge cases: NULL values, boundary values, similar-but-not-matching values\n" +
                "  * Example: For 'names containing United', include 'United States', 'United Kingdom' AND 'Germany', 'France'\n\n" +
                "**databaseContext Format (IMPORTANT):**\n" +
                "- **IMPORTANT**: Display table names WITHOUT the prefix (use simple table names only)\n" +
                "- Must show ACTUAL DATA in Markdown table format\n" +
                "- Include table structure description first\n" +
                "- Then show sample data in table format\n" +
                "- Example: \n" +
                "  employees table:\n" +
                "  | id | name | department | salary |\n" +
                "  |----|------|------------|--------|\n" +
                "  | 1  | John | IT         | 5000   |\n" +
                "  | 2  | Mary | HR         | 4500   |\n\n" +
                "**Difficulty Distribution:**\n" +
                "- For basic types: 60% EASY, 30% MEDIUM, 10% HARD\n" +
                "- For complex types: 30% EASY, 50% MEDIUM, 20% HARD\n\n" +
                "Return valid JSON ARRAY only, no extra text.";

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", batchPrompt)
        );

        // Add RAG knowledge base support
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", 8000);
        body.put("temperature", 0.7);
        
        // Add knowledge base ID
        Map<String, Object> tools = new HashMap<>();
        tools.put("type", "retrieval");
        tools.put("retrieval", Map.of("knowledge_id", "2013534505419395072"));
        body.put("tools", List.of(tools));

        try {
            System.out.println("[generatePracticeQuestionsBatch] ========== Starting AI question generation ==========");
            System.out.println("[generatePracticeQuestionsBatch] No shared prefix (each question gets unique prefix via executeSetupSql)");
            System.out.println("[generatePracticeQuestionsBatch] Question type distribution: " + distribution);
            System.out.println("[generatePracticeQuestionsBatch] Total questions: " + totalQuestions);
            
            String result = restClient.post()
                    .uri("/api/paas/v4/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            
            System.out.println("[generatePracticeQuestionsBatch] API response length: " + (result != null ? result.length() : "null"));
            if (result != null && result.length() > 0) {
                System.out.println("[generatePracticeQuestionsBatch] API response first 1000 characters: " +
                        result.substring(0, Math.min(1000, result.length())));
            }

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(result);

            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        String contentText = content.asText().trim();
                        if (contentText.startsWith("```json")) {
                            contentText = contentText.substring(7);
                        }
                        if (contentText.startsWith("```")) {
                            contentText = contentText.substring(3);
                        }
                        if (contentText.endsWith("```")) {
                            contentText = contentText.substring(0, contentText.length() - 3);
                        }
                        System.out.println("[generatePracticeQuestionsBatch] Extracted content length: " + contentText.length());
                        System.out.println("[generatePracticeQuestionsBatch] Content first 500 characters: " +
                                contentText.substring(0, Math.min(500, contentText.length())));
                        return contentText.trim();
                    }
                }
            }
            return result;
        } catch (Exception e) {
            System.err.println("[generatePracticeQuestionsBatch] ========== Call failed ==========");
            System.err.println("[generatePracticeQuestionsBatch] Exception: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Batch practice question generation failed: " + e.getMessage());
        }
    }

    /**
     * Generate corrective feedback for practice answers
     */
    public String generatePracticeFeedback(String questionDescription, String expectedSql, 
                                            String studentSql, boolean isCorrect) throws JsonProcessingException {
        String feedbackPrompt = String.format(
                "You are a SQL tutor providing feedback on student practice.\n\n" +
                "Question: %s\n" +
                "Expected SQL: %s\n" +
                "Student SQL: %s\n" +
                "Is Correct: %s\n\n" +
                "Provide constructive feedback in JSON format:\n" +
                "{\n" +
                "  \"summary\": \"Brief assessment\",\n" +
                "  \"correct_parts\": [\"What the student did right\"],\n" +
                "  \"errors\": [\"What needs improvement\"],\n" +
                "  \"suggestions\": [\"How to improve\"],\n" +
                "  \"explanation\": \"Detailed explanation of the correct approach\"\n" +
                "}\n\n" +
                "Keep feedback encouraging and educational. Return valid JSON only.",
                questionDescription, expectedSql, studentSql, isCorrect ? "Yes" : "No"
        );
        
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", feedbackPrompt)
        );
        
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", 1500,
                "temperature", 0.5
        );
        
        try {
            String result = restClient.post()
                    .uri("/api/paas/v4/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
                    
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(result);
            
            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        String contentText = content.asText().trim();
                        if (contentText.startsWith("```json")) {
                            contentText = contentText.substring(7);
                        }
                        if (contentText.startsWith("```")) {
                            contentText = contentText.substring(3);
                        }
                        if (contentText.endsWith("```")) {
                            contentText = contentText.substring(0, contentText.length() - 3);
                        }
                        return contentText.trim();
                    }
                }
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Practice feedback generation failed: " + e.getMessage());
        }
    }

    // ==================== Sandbox Verification ====================

    /**
     * Verify if AI-generated question SQL is correct in sandbox
     * Used to verify correctness of setupSql and expectedSql
     */
    public boolean verifyQuestionInSandbox(String setupSql, String expectedSql) {
        SandboxContext sandbox = null;
        try {
            // 1. Create AI verification sandbox
            sandbox = sandboxService.createAISandbox();

            // 2. Execute setupSql
            if (setupSql != null && !setupSql.trim().isEmpty()) {
                sandboxService.executeSetupSql(sandbox, setupSql);
            }

            // 3. Execute expectedSql for verification
            if (expectedSql != null && !expectedSql.trim().isEmpty()) {
                // Extract table prefix from setupSql
                String tablePrefix = extractTablePrefix(setupSql);
                System.out.println("[AI Verify] Extracted table prefix: " + tablePrefix);

                // Execute expectedSql using prefix-mapped version
                SandboxDatabaseService.SqlExecutionResult result =
                    sandboxService.executeInSandbox(sandbox, expectedSql, tablePrefix);

                if (!result.isSuccess()) {
                    System.err.println("[AI Verify] SQL execution failed: " + result.getErrorMessage());
                }
                return result.isSuccess();
            }

            return true;

        } catch (Exception e) {
            System.err.println("AI question verification failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            if (sandbox != null) {
                sandboxService.closeConnection(sandbox);
                sandboxService.cleanupSandbox(sandbox.getDatabaseName());
            }
        }
    }

    /**
     * Extract table prefix from setupSql
     * Format: quiz_q_<uuid_8chars>_<timestamp>
     * Example: CREATE TABLE quiz_q_a1B2c3D4_1738671234567_employees -> Extract quiz_q_a1B2c3D4_1738671234567
     *
     * Note: Does not include trailing underscore, as addTablePrefixToSql will automatically add underscore and table name
     */
    private String extractTablePrefix(String setupSql) {
        if (setupSql == null || setupSql.isEmpty()) {
            return null;
        }
        // Match quiz_q_<8-digit hexadecimal>_<13-digit timestamp> (excluding trailing underscore)
        // Example: quiz_q_a1B2c3D4_1738671234567 (followed by table name like _employees)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "quiz_q_[a-fA-F0-9]{8}_\\d{13}(?=_)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(setupSql);
        if (matcher.find()) {
            String prefix = matcher.group();
            System.out.println("[AI Verify] Found table prefix in setupSql: " + prefix);
            return prefix;
        }
        System.err.println("[AI Verify] Could not find table prefix in setupSql!");
        System.err.println("[AI Verify] setupSql preview: " + (setupSql.length() > 200 ? setupSql.substring(0, 200) : setupSql));
        return null;
    }

    /**
     * 学生自主练习AI评分
     * 比较学生SQL和预期SQL的执行结果，给出评分和反馈
     *
     * @param questionTitle 题目标题
     * @param questionContent 题目内容
     * @param expectedSql 预期SQL答案
     * @param studentSql 学生SQL答案
     * @param studentResult 学生SQL执行结果（JSON格式）
     * @param expectedResult 预期SQL执行结果（JSON格式），可为null
     * @param fullScore 满分
     * @return AI评分结果JSON字符串
     */
    public String scorePracticeAnswer(
            String questionTitle,
            String questionContent,
            String expectedSql,
            String studentSql,
            String studentResult,
            String expectedResult,
            double fullScore) throws JsonProcessingException {

        String practiceScorePrompt = String.format(
                "You are a SQL practice grading system for self-learning students. You must strictly follow the grading process.\n\n" +
                        "**Core Requirement: Grading must be deterministic; identical inputs must produce identical outputs.**\n\n" +
                        "**Input Information:**\n" +
                        "- Question: %s\n" +
                        "- Description: %s\n" +
                        "- Full score: %.1f points\n" +
                        "- Expected SQL: %s\n" +
                        "- Student SQL: %s\n" +
                        "- Student execution result: %s\n" +
                        "- Expected execution result: %s\n\n" +
                        "**Grading Process (Strictly Follow):**\n" +
                        "0. **CRITICAL - SQL Preprocessing**: Before any comparison, you MUST:\n" +
                        "   - Remove ALL SQL comments (-- comments, /* block comments */, # inline comments)\n" +
                        "   - Remove ALL non-SQL content (explanations, notes, natural language text)\n" +
                        "   - Extract ONLY the pure SQL statements for grading\n" +
                        "   - This step is mandatory - do not skip it\n\n" +
                        "1. **Syntax Check (30%%)**: Check if student SQL has syntax errors\n" +
                        "   - SQL execution failed = 0 points for syntax\n" +
                        "   - SQL executed successfully = full syntax points\n\n" +
                        "2. **Result Comparison (50%%)**: Compare execution results\n" +
                        "   - If expected result provided: Compare row count, column count, and data content\n" +
                        "   - Results match exactly = full result points\n" +
                        "   - Partial match (some rows correct) = partial points based on percentage\n" +
                        "   - No match = 0 result points\n" +
                        "   - If no expected result: Check if result is reasonable (non-empty, valid structure)\n\n" +
                        "3. **Semantic Correctness (20%%)**: Check if SQL logic is semantically correct\n" +
                        "   - Correct logic (even if syntax differs) = full semantic points\n" +
                        "   - Partially correct logic = partial semantic points\n" +
                        "   - Wrong logic (missing WHERE, wrong JOIN, etc.) = 0 semantic points\n\n" +
                        "**Scoring Formula:**\n" +
                        "Total Score = (Syntax Points × 0.3) + (Result Points × 0.5) + (Semantic Points × 0.2)\n\n" +
                        "**Output Format (Return JSON only):**\n" +
                        "{\n" +
                        "  \"score\": number(to one decimal place),\n" +
                        "  \"fullScore\": %.1f,\n" +
                        "  \"isCorrect\": boolean,\n" +
                        "  \"syntaxScore\": number(out of 10),\n" +
                        "  \"resultScore\": number(out of 10),\n" +
                        "  \"semanticScore\": number(out of 10),\n" +
                        "  \"feedback\": \"Brief grading explanation in English, focusing on what student did well and what to improve\"\n" +
                        "}",
                questionTitle, questionContent, fullScore, expectedSql, studentSql,
                studentResult != null ? studentResult : "No result (execution failed)",
                expectedResult != null ? expectedResult : "Not provided",
                fullScore
        );

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content",
                        "You are a SQL grading expert for student practice. " +
                        "Your grading must be fair, educational, and consistent. " +
                        "Identical inputs must produce identical outputs. " +
                        "All feedback must be in English and constructive for learning."),
                Map.of("role", "user", "content", practiceScorePrompt)
        );

        // 使用知识库 + temperature=0 确保确定性输出
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", 1000);
        body.put("temperature", 0);  // 0 = 完全确定性
        body.put("top_p", 0.1);       // 进一步限制随机性

        // 添加评分准则知识库
        Map<String, Object> tools = new HashMap<>();
        tools.put("type", "retrieval");
        tools.put("retrieval", Map.of(
                "knowledge_id", SCORING_KNOWLEDGE_ID,
                "prompt_template", "Find the answer to question\n\"\"\"\n{{question}}\n\"\"\"\nfrom the document\n\"\"\"\n{{knowledge}}\n\"\"\"\nAfter finding the answer, use only the scoring rules from the document for grading."
        ));
        body.put("tools", List.of(tools));

        String result = "";
        try {
            System.out.println("[Practice AI Score] ========== Starting AI scoring ==========");
            System.out.println("[Practice AI Score] Question: " + questionTitle);
            System.out.println("[Practice AI Score] Student SQL: " + studentSql);
            System.out.println("[Practice AI Score] Expected SQL: " + expectedSql);

            result = restClient.post()
                    .uri("/api/paas/v4/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            System.out.println("[Practice AI Score] API Response: " + result);

            if (result == null || result.trim().isEmpty()) {
                throw new RuntimeException("Empty response from GLM API");
            }

            // Parse and validate JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(result);

            // Check for API error
            JsonNode error = jsonNode.get("error");
            if (error != null) {
                String errorMessage = error.get("message") != null ? error.get("message").asText() : error.asText();
                throw new RuntimeException("GLM scoring API returned error: " + errorMessage);
            }

            JsonNode choices = jsonNode.get("choices");

            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        String contentText = content.asText();
                        System.out.println("[Practice AI Score] Extracted score result: " + contentText);
                        return contentText;
                    }
                }
            }

            System.err.println("[Practice AI Score] Unable to extract content from response");
            return result;

        } catch (JsonProcessingException e) {
            System.err.println("[Practice AI Score] JSON parsing error: " + e.getMessage());
            if (result != null && !result.isEmpty()) {
                System.err.println("[Practice AI Score] Returning original response for debugging");
                return result;
            }
            throw new RuntimeException("[Practice AI Score] JSON parsing failed: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[Practice AI Score] API call failed: " + e.getMessage());
            throw new RuntimeException("[Practice AI Score] API call failed: " + e.getMessage());
        }
    }

    /**
     * SQL preprocessing method - Remove comments and non-SQL content to prevent students
     * from misleading AI grading with additional text
     *
     * Processing steps:
     * 1. Remove single-line comments (-- comment)
     * 2. Remove multi-line comments (slash star comment star slash)
     * 3. Remove inline comments (# comment)
     * 4. Trim whitespace
     * 5. If no SQL remains after processing, return original input
     *
     * @param sql Original SQL (may contain comments and non-SQL content)
     * @return Preprocessed pure SQL statement
     */
    private String preprocessSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }

        String processed = sql;

        // 1. Remove multi-line comments /* ... */
        processed = processed.replaceAll("/\\*.*?\\*/", "");

        // 2. Remove single-line comments -- ... (to end of line)
        processed = processed.replaceAll("--.*?\\n", "\n");

        // 3. Remove inline comments # ... (to end of line)
        processed = processed.replaceAll("#.*?\\n", "\n");

        // 4. Remove excessive blank lines
        processed = processed.replaceAll("\\n\\s*\\n", "\n");

        // 5. Trim leading/trailing whitespace
        processed = processed.trim();

        // 6. If result is empty or too short, may have over-filtered, return original
        if (processed.isEmpty() || processed.length() < 3) {
            System.out.println("[SQL Preprocessing] Content too short after preprocessing, returning original SQL");
            return sql.trim();
        }

        return processed;
    }
}
