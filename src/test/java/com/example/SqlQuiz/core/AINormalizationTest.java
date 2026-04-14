package com.example.SqlQuiz.core;

import com.example.SqlQuiz.service.GLMService;
import com.example.SqlQuiz.service.QuizTableMetadataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Core Feature: AI Question Normalization")
class AINormalizationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("normalizeQuestion should inject the generated table prefix and return clean JSON")
    void normalizeQuestionShouldInjectPrefixAndReturnCleanJson() throws Exception {
        try (GlmMockHttpServer server = new GlmMockHttpServer(
                "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\"title\\\":\\\"Orders\\\",\\\"questionType\\\":\\\"SELECT_JOIN\\\",\\\"difficulty\\\":\\\"MEDIUM\\\"}\\n```\"}}]}")) {

            GLMService service = new GLMService("test-key", server.baseUrl(), "glm-test");
            QuizTableMetadataService metadataService = mock(QuizTableMetadataService.class);
            when(metadataService.generateUniqueTablePrefix()).thenReturn("quiz_q_deadbeef_1738671234567");
            ReflectionTestUtils.setField(service, "tableMetadataService", metadataService);

            String result = service.normalizeQuestion("Find orders with customer names", "text");
            JsonNode json = objectMapper.readTree(result);

            assertThat(json.get("title").asText()).isEqualTo("Orders");
            assertThat(json.get("questionType").asText()).isEqualTo("SELECT_JOIN");
            assertThat(server.lastRequestBody()).contains("quiz_q_deadbeef_1738671234567_[table_name]");
            assertThat(server.lastRequestBody()).contains("User Input Type: text");
            assertThat(server.lastRequestBody()).contains("Find orders with customer names");
        }
    }

    @Test
    @DisplayName("normalizeQuestion should throw a useful error when GLM responds with an API error")
    void normalizeQuestionShouldExposeApiErrors() {
        try (GlmMockHttpServer server = new GlmMockHttpServer(
                "{\"error\":{\"message\":\"normalization rejected\"}}")) {

            GLMService service = new GLMService("test-key", server.baseUrl(), "glm-test");
            QuizTableMetadataService metadataService = mock(QuizTableMetadataService.class);
            when(metadataService.generateUniqueTablePrefix()).thenReturn("quiz_q_deadbeef_1738671234567");
            ReflectionTestUtils.setField(service, "tableMetadataService", metadataService);

            assertThatThrownBy(() -> service.normalizeQuestion("input", "text"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("normalization rejected");
        }
    }

    @Test
    @DisplayName("performOCR should send a vision request with the uploaded image content")
    void performOcrShouldUseVisionPayload() throws Exception {
        Path image = tempDir.resolve("question.jpg");
        byte[] imageBytes = new byte[] {1, 2, 3, 4, 5};
        Files.write(image, imageBytes);

        try (GlmMockHttpServer server = new GlmMockHttpServer(
                "{\"choices\":[{\"message\":{\"content\":\"SELECT * FROM employees;\"}}]}")) {

            GLMService service = new GLMService("test-key", server.baseUrl(), "glm-test");

            String text = service.performOCR(image.toString());

            String expectedBase64 = Base64.getEncoder().encodeToString(imageBytes);
            assertThat(text).isEqualTo("SELECT * FROM employees;");
            assertThat(server.lastRequestBody()).contains("\"model\":\"glm-4v\"");
            assertThat(server.lastRequestBody()).contains("data:image/jpeg;base64," + expectedBase64);
            assertThat(server.lastRequestBody()).contains("Please carefully extract all text content from this image");
        }
    }
}
