package com.example.SqlQuiz.core;

import com.example.SqlQuiz.entity.SandboxContext;
import com.example.SqlQuiz.service.GLMService;
import com.example.SqlQuiz.service.QuizTableMetadataService;
import com.example.SqlQuiz.service.SandboxDatabaseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Core Feature: AI Question Generation")
class AIQuestionGenerationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("generateQuestionWithRAG should include retrieval config, strict easy rules, and the generated prefix")
    void generateQuestionWithRagShouldBuildTheExpectedPrompt() throws Exception {
        try (GlmMockHttpServer server = new GlmMockHttpServer(
                "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\"title\\\":\\\"Employee Lookup\\\",\\\"questionType\\\":\\\"SELECT_BASIC\\\"}\\n```\"}}]}")) {

            GLMService service = new GLMService("test-key", server.baseUrl(), "glm-test");
            QuizTableMetadataService metadataService = mock(QuizTableMetadataService.class);
            when(metadataService.generateUniqueTablePrefix()).thenReturn("quiz_q_deadbeef_1738671234567");
            ReflectionTestUtils.setField(service, "tableMetadataService", metadataService);

            String result = service.generateQuestionWithRAG("SELECT_BASIC", "EASY");
            JsonNode json = objectMapper.readTree(result);

            assertThat(json.get("title").asText()).isEqualTo("Employee Lookup");
            assertThat(server.lastRequestBody()).contains("\"knowledge_id\":\"2013534505419395072\"");
            assertThat(server.lastRequestBody()).contains("STRICT RULE FOR SELECT_BASIC + EASY");
            assertThat(server.lastRequestBody()).contains("quiz_q_deadbeef_1738671234567_");
            assertThat(server.lastRequestBody()).contains("ALL generated text must be in English only");
        }
    }

    @Test
    @DisplayName("generatePracticeQuestion should return clean JSON and include practice-specific prompt constraints")
    void generatePracticeQuestionShouldReturnCleanJson() throws Exception {
        try (GlmMockHttpServer server = new GlmMockHttpServer(
                "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\"title\\\":\\\"Join Practice\\\",\\\"questionType\\\":\\\"SELECT_JOIN\\\"}\\n```\"}}]}")) {

            GLMService service = new GLMService("test-key", server.baseUrl(), "glm-test");
            QuizTableMetadataService metadataService = mock(QuizTableMetadataService.class);
            when(metadataService.generateUniqueTablePrefix()).thenReturn("quiz_q_feedface_1738671234567");
            ReflectionTestUtils.setField(service, "tableMetadataService", metadataService);

            String result = service.generatePracticeQuestion("SELECT_JOIN", "MEDIUM");
            JsonNode json = objectMapper.readTree(result);

            assertThat(json.get("title").asText()).isEqualTo("Join Practice");
            assertThat(server.lastRequestBody()).contains("Generate a practice question with these requirements");
            assertThat(server.lastRequestBody()).contains("quiz_q_feedface_1738671234567_");
            assertThat(server.lastRequestBody()).contains("Language requirement: ALL generated text must be English only");
        }
    }

    @Test
    @DisplayName("verifyQuestionInSandbox should extract the table prefix and clean up the sandbox")
    void verifyQuestionInSandboxShouldUseExtractedPrefixAndCleanup() throws Exception {
        GLMService service = new GLMService("test-key", "http://127.0.0.1:65535", "glm-test");
        SandboxDatabaseService sandboxService = mock(SandboxDatabaseService.class);
        ReflectionTestUtils.setField(service, "sandboxService", sandboxService);

        SandboxContext sandbox = new SandboxContext();
        sandbox.setDatabaseName("quiz_sb_ai_test");
        when(sandboxService.createAISandbox()).thenReturn(sandbox);

        SandboxDatabaseService.SqlExecutionResult executionResult = new SandboxDatabaseService.SqlExecutionResult();
        executionResult.setSuccess(true);
        when(sandboxService.executeInSandbox(
                eq(sandbox),
                eq("SELECT * FROM employees"),
                eq("quiz_q_deadbeef_1738671234567")))
                .thenReturn(executionResult);

        boolean verified = service.verifyQuestionInSandbox(
                "CREATE TABLE quiz_q_deadbeef_1738671234567_employees (id INT);",
                "SELECT * FROM employees");

        assertThat(verified).isTrue();
        verify(sandboxService).executeSetupSql(
                sandbox,
                "CREATE TABLE quiz_q_deadbeef_1738671234567_employees (id INT);");
        verify(sandboxService).executeInSandbox(
                sandbox,
                "SELECT * FROM employees",
                "quiz_q_deadbeef_1738671234567");
        verify(sandboxService).closeConnection(sandbox);
        verify(sandboxService).cleanupSandbox("quiz_sb_ai_test");
    }
}
