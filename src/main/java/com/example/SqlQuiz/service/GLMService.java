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
     * 使用 GLM-4V 进行图片 OCR 识别
     * @param imagePath 图片文件路径
     * @return 识别出的文本内容
     */
    public String performOCR(String imagePath) {
        try {
            // 1. 读取图片并转为Base64
            Path path = Path.of(imagePath);
            byte[] imageBytes = Files.readAllBytes(path);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            
            // 2. 构建多模态请求（图片 + OCR提示词）
            String ocrPrompt = "Please carefully extract all text content from this image. " +
                    "This is likely a SQL quiz question or database exercise. " +
                    "Return the text exactly as it appears, maintaining the structure and format. " +
                    "Include question titles, descriptions, table structures, and any SQL code if present.";
            
            // 创建多模态消息
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
            
            // 3. 调用 glm-4v 模型
            Map<String, Object> body = new HashMap<>();
            body.put("model", "glm-4v");  // 使用视觉模型
            body.put("messages", List.of(message));
            body.put("max_tokens", 2000);
            body.put("temperature", 0.1);  // 降低temperature提高识别准确性
            
            // 4. 发送请求
            String result = restClient.post()
                    .uri("/api/paas/v4/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            
            System.out.println("OCR API Response: " + result);
            
            if (result == null || result.trim().isEmpty()) {
                throw new RuntimeException("OCR API returned empty response");
            }
            
            // 5. 解析并返回识别结果
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(result);
            
            // 检查错误
            JsonNode error = jsonNode.get("error");
            if (error != null) {
                String errorMessage = error.get("message") != null ? error.get("message").asText() : error.asText();
                throw new RuntimeException("OCR API returned error: " + errorMessage);
            }
            
            // 提取识别文本
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
     * 生成题目的方法
     */
    public String chat_create_quiz (String description) throws JsonProcessingException {
        // 生成唯一标识符，确保表名不会冲突
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
                "   Use this exact format:\n" +
                "   \n" +
                "   " + tablePrefix + "_students table:\n" +
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
                "   - INSERT INTO statements: Insert 3-5 sample data records, do not specify primary key values, let database auto-generate\n" +
                "   - Ensure SQL statements can be executed directly in MySQL for initializing test database (testdb)\n" +
                "   - **Important**: Use the provided unique prefix to ensure table names do not conflict\n" +
                "5. expectedSql: Standard answer SQL statement, use the actual generated table names\n" +
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
        score_prompt = String.format(
                "You are a professional SQL teaching scoring assistant. Please score the student's SQL answer based on the following information. All your responses should be in English:\n" +
                        "**Scoring Criteria:**\n" +
                        "- Full score: %.1f points\n" +
                        "- Question description: %s\n" +
                        "- Standard answer: %s\n" +
                        "- Student answer: %s\n" +
                        "**Scoring Requirements:**\n" +
                        "1. If the student's SQL writing differs from the standard answer but achieves the same query effect and results, give full score\n" +
                        "2. If the student's answer has partial errors, give corresponding partial scores based on the error degree:\n" +
                        "   - Correct syntax but wrong logic: give 60%%~80%% score\n" +
                        "   - Main logic correct but with detail issues: give 80%%~95%% score\n" +
                        "   - Serious logic errors but with some correct thinking: give 20%%~60%% score\n" +
                        "3. If the student's answer is completely wrong or completely unreasonable, give 0 points\n" +
                        "4. As long as the student doesn't get full score, you must explain the deduction reasons in detail\n" +
                        "**Scoring Dimensions:**\n" +
                        "- SQL syntax correctness\n" +
                        "- Query logic accuracy\n" +
                        "- Result completeness\n" +
                        "- Code standardization\n" +
                        "**Response Format Requirements:**\n" +
                        "Please respond strictly in the following JSON format without adding any other content:\n" +
                        "```json\n" +
                        "{\n" +
                        "  \"score\": actual_score(number),\n" +
                        "  \"fullScore\": %.1f,\n" +
                        "  \"percentage\": score_percentage(number),\n" +
                        "  \"feedback\": \"detailed scoring feedback description\",\n" +
                        "  \"deductionReasons\": [\n" +
                        "    \"deduction_reason_1\",\n" +
                        "    \"deduction_reason_2\"\n" +
                        "  ]\n" +  // Remove trailing comma
                        "}\n" +    // Add newline
                        "```",
                score, description, expected_answer, student_answer,score // First %.1f and %s
                // Second %.1f corresponds to fullScore
        );
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", score_prompt + "\n\nPlease score this SQL question")
        );
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", 2000,
                "temperature", 0.3  // 评分需要更准确，降低温度
        );
        String result = "";
        try {
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
                        System.out.println("Extracted score content: " + contentText);
                        return contentText;
                    }
                }
            }
            
            // If unable to parse, return original response for debugging
            System.err.println("Unable to extract content from GLM scoring API response, returning original response");
            return result;
            
        } catch (JsonProcessingException e) {
            System.err.println("GLM scoring API JSON parsing error: " + e.getMessage());
            System.err.println("Response was: " + result);
            // If JSON parsing fails, try to return original response
            if (result != null && !result.isEmpty()) {
                System.err.println("Returning original scoring response for debugging");
                return result;
            }
            throw new RuntimeException("GLM scoring API JSON parsing failed: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("GLM Score API call failed: " + e.getMessage());
            System.err.println("Response was: " + result);
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
                "4. Provide 3-5 sample records in setupSql\n" +
                "5. **CRITICAL - databaseContext Format:**\n" +
                "   The databaseContext field MUST contain Markdown tables with ACTUAL SAMPLE DATA.\n" +
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
                "6. Return valid JSON only, no additional text\n" +
                "7. The answer field should include step-by-step solution explanation in Markdown\n" +
                "8. Infer appropriate questionType and difficulty from the input question";
        
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
        
        String typeMapping = "{\n" +
                "  \"SINGLE_TABLE\": \"Single table query\",\n" +
                "  \"GROUP_AGGREGATE\": \"Group by and aggregate functions\",\n" +
                "  \"MULTI_JOIN\": \"Multiple table JOIN queries\",\n" +
                "  \"SUBQUERY\": \"Subquery operations\",\n" +
                "  \"COMPREHENSIVE\": \"Comprehensive complex queries\",\n" +
                "  \"UPDATE_DELETE\": \"UPDATE and DELETE operations\"\n" +
                "}";
        
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
                "  \"expectedSql\": \"Correct answer SQL\",\n" +
                "  \"answer\": \"Detailed solution explanation in Markdown\",\n" +
                "  \"questionType\": \"" + questionType + "\",\n" +
                "  \"difficulty\": \"" + difficulty + "\"\n" +
                "}\n" +
                "\n" +
                "**CRITICAL - databaseContext Format:**\n" +
                "The databaseContext field MUST contain Markdown tables with ACTUAL SAMPLE DATA.\n" +
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
                "- Use unique prefix: `" + tablePrefix + "_`\n" +
                "- Format: `" + tablePrefix + "_[table_name]`\n" +
                "- All primary keys: AUTO_INCREMENT\n" +
                "- Insert 3-5 sample records\n" +
                "\n" +
                "Return valid JSON only, no extra text.";
        
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", ragPrompt)
        );
        
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", 4000,
                "temperature", 0.7
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
            System.err.println("Question generation with RAG failed: " + e.getMessage());
            throw new RuntimeException("Question generation with RAG failed: " + e.getMessage());
        }
    }

    private String getTypeDescription(String questionType) {
        switch (questionType) {
            case "SINGLE_TABLE": return "单表查询";
            case "GROUP_AGGREGATE": return "分组聚合";
            case "MULTI_JOIN": return "多表JOIN";
            case "SUBQUERY": return "子查询";
            case "COMPREHENSIVE": return "综合查询";
            case "UPDATE_DELETE": return "更新删除";
            case "SELECT_BASIC": return "基础查询";
            case "SELECT_JOIN": return "表连接";
            case "SELECT_SUBQUERY": return "子查询";
            case "SELECT_AGGREGATE": return "聚合函数";
            case "SELECT_COMPLEX": return "复杂查询";
            case "DML_INSERT": return "插入数据";
            case "DML_UPDATE": return "更新数据";
            case "DML_DELETE": return "删除数据";
            default: return "未知类型";
        }
    }

    /**
     * 为自主练习模式生成题目
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
                "Table naming: Use prefix `" + tablePrefix + "_`\n" +
                "All primary keys: AUTO_INCREMENT\n" +
                "Insert 3-5 sample records\n" +
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
     * 批量生成练习题目（10题）
     * 根据题型分布配置，一次AI调用生成10道题目
     *
     * @param distribution 题型分布，如 {SELECT_BASIC: 4, SELECT_JOIN: 3, SELECT_AGGREGATE: 3}
     * @param blacklist 已存在题目的黑名单（内容归一化后）
     * @return JSON数组格式的题目列表
     */
    public String generatePracticeQuestionsBatch(
            Map<Question.QuestionType, Integer> distribution,
            List<String> blacklist) throws JsonProcessingException {

        String tablePrefix = tableMetadataService.generateUniqueTablePrefix();

        // 构建题型分布描述
        StringBuilder distributionDesc = new StringBuilder();
        int totalQuestions = 0;
        for (Map.Entry<Question.QuestionType, Integer> entry : distribution.entrySet()) {
            distributionDesc.append(String.format("- %s: %d题 (%s)\n",
                    entry.getKey().name(),
                    entry.getValue(),
                    getTypeDescription(entry.getKey().name())));
            totalQuestions += entry.getValue();
        }

        // 构建黑名单提示（避免重复）
        StringBuilder blacklistDesc = new StringBuilder();
        if (blacklist != null && !blacklist.isEmpty()) {
            blacklistDesc.append("\n**IMPORTANT - AVOID THESE QUESTIONS:**\n");
            blacklistDesc.append("Do NOT generate questions similar to these existing ones:\n");
            int count = Math.min(5, blacklist.size()); // 只显示前5个作为示例
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
                "- Use prefix: `" + tablePrefix + "_`\n" +
                "- Format: `" + tablePrefix + "_[table_name]`\n" +
                "- All primary keys: AUTO_INCREMENT\n" +
                "- Insert 3-5 sample records per table\n\n" +
                "**databaseContext Format (IMPORTANT):**\n" +
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

        // 添加RAG知识库支持
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", 8000);
        body.put("temperature", 0.7);
        
        // 添加知识库ID
        Map<String, Object> tools = new HashMap<>();
        tools.put("type", "retrieval");
        tools.put("retrieval", Map.of("knowledge_id", "2013534505419395072"));
        body.put("tools", List.of(tools));

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
            throw new RuntimeException("Batch practice question generation failed: " + e.getMessage());
        }
    }

    /**
     * 为练习答案生成纠错反馈
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
}
