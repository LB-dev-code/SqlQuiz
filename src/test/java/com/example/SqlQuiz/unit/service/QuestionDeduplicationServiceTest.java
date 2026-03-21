package com.example.SqlQuiz.unit.service;

import com.example.SqlQuiz.entity.Question;
import com.example.SqlQuiz.service.QuestionDeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("QuestionDeduplicationService Unit Tests")
public class QuestionDeduplicationServiceTest {

    private QuestionDeduplicationService service;

    @BeforeEach
    void setUp() {
        service = new QuestionDeduplicationService();
    }

    @Test
    @DisplayName("解析有效的JSON题目数组")
    void parseAndDeduplicate_ValidJsonArray() {
        // Arrange
        String json = """
            [
                {
                    "questionType": "SELECT_BASIC",
                    "difficulty": "EASY",
                    "title": "Query all users",
                    "description": "Select all columns from users table",
                    "databaseContext": "users(id, name, email)",
                    "setupSql": "CREATE TABLE users (id INT, name VARCHAR(100))",
                    "expectedSql": "SELECT * FROM users",
                    "hints": "Use SELECT *"
                },
                {
                    "questionType": "SELECT_JOIN",
                    "difficulty": "MEDIUM",
                    "title": "Join users and orders",
                    "description": "Get users with their orders",
                    "databaseContext": "users and orders tables",
                    "setupSql": null,
                    "expectedSql": "SELECT * FROM users u JOIN orders o ON u.id = o.user_id",
                    "hints": null
                }
            ]
            """;

        // Act
        List<QuestionDeduplicationService.GeneratedQuestion> result = service.parseAndDeduplicate(json);

        // Assert
        assertThat(result).hasSize(2);

        QuestionDeduplicationService.GeneratedQuestion q1 = result.get(0);
        assertThat(q1.questionType).isEqualTo(Question.QuestionType.SELECT_BASIC);
        assertThat(q1.difficulty).isEqualTo(Question.DifficultyLevel.EASY);
        assertThat(q1.title).isEqualTo("Query all users");
        assertThat(q1.description).isEqualTo("Select all columns from users table");
        assertThat(q1.databaseContext).isEqualTo("users(id, name, email)");
        assertThat(q1.setupSql).isEqualTo("CREATE TABLE users (id INT, name VARCHAR(100))");
        assertThat(q1.expectedSql).isEqualTo("SELECT * FROM users");
        assertThat(q1.hints).isEqualTo("Use SELECT *");

        QuestionDeduplicationService.GeneratedQuestion q2 = result.get(1);
        assertThat(q2.questionType).isEqualTo(Question.QuestionType.SELECT_JOIN);
        assertThat(q2.difficulty).isEqualTo(Question.DifficultyLevel.MEDIUM);
        assertThat(q2.title).isEqualTo("Join users and orders");
        assertThat(q2.setupSql).isNull();
        assertThat(q2.hints).isNull();
    }

    @Test
    @DisplayName("解析带代码块标记的JSON")
    void parseAndDeduplicate_WithCodeBlockMarkers() {
        // Arrange
        String json = """
            ```json
            [
                {
                    "questionType": "SELECT_BASIC",
                    "difficulty": "EASY",
                    "title": "Test Question",
                    "description": "Test Description",
                    "databaseContext": "Test Context",
                    "setupSql": null,
                    "expectedSql": "SELECT * FROM test",
                    "hints": null
                }
            ]
            ```
            """;

        // Act
        List<QuestionDeduplicationService.GeneratedQuestion> result = service.parseAndDeduplicate(json);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title).isEqualTo("Test Question");
    }

    @Test
    @DisplayName("解析单个题目对象（非数组）")
    void parseAndDeduplicate_SingleObject() {
        // Arrange
        String json = """
            {
                "questionType": "SELECT_AGGREGATE",
                "difficulty": "HARD",
                "title": "Count users",
                "description": "Count total users",
                "databaseContext": "users table",
                "setupSql": "CREATE TABLE users (id INT)",
                "expectedSql": "SELECT COUNT(*) FROM users",
                "hints": "Use COUNT function"
            }
            """;

        // Act
        List<QuestionDeduplicationService.GeneratedQuestion> result = service.parseAndDeduplicate(json);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).questionType).isEqualTo(Question.QuestionType.SELECT_AGGREGATE);
        assertThat(result.get(0).difficulty).isEqualTo(Question.DifficultyLevel.HARD);
    }

    @Test
    @DisplayName("解析无效的JSON返回空列表")
    void parseAndDeduplicate_InvalidJson() {
        // Arrange
        String invalidJson = "this is not valid json";

        // Act
        List<QuestionDeduplicationService.GeneratedQuestion> result = service.parseAndDeduplicate(invalidJson);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("解析空JSON返回空列表")
    void parseAndDeduplicate_EmptyJson() {
        // Arrange
        String emptyJson = "[]";

        // Act
        List<QuestionDeduplicationService.GeneratedQuestion> result = service.parseAndDeduplicate(emptyJson);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("解析null返回空列表")
    void parseAndDeduplicate_NullInput() {
        // Act
        List<QuestionDeduplicationService.GeneratedQuestion> result = service.parseAndDeduplicate(null);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("处理无效的题型枚举值")
    void parseAndDeduplicate_InvalidQuestionType() {
        // Arrange
        String json = """
            [
                {
                    "questionType": "INVALID_TYPE",
                    "difficulty": "EASY",
                    "title": "Test",
                    "description": "Test",
                    "databaseContext": "Test",
                    "setupSql": null,
                    "expectedSql": "SELECT 1",
                    "hints": null
                }
            ]
            """;

        // Act
        List<QuestionDeduplicationService.GeneratedQuestion> result = service.parseAndDeduplicate(json);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).questionType).isNull(); // Invalid enum value will be set to null
    }

    @Test
    @DisplayName("处理无效的难度枚举值使用默认值")
    void parseAndDeduplicate_InvalidDifficulty() {
        // Arrange
        String json = """
            [
                {
                    "questionType": "SELECT_BASIC",
                    "difficulty": "INVALID_DIFFICULTY",
                    "title": "Test",
                    "description": "Test",
                    "databaseContext": "Test",
                    "setupSql": null,
                    "expectedSql": "SELECT 1",
                    "hints": null
                }
            ]
            """;

        // Act
        List<QuestionDeduplicationService.GeneratedQuestion> result = service.parseAndDeduplicate(json);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).difficulty).isEqualTo(Question.DifficultyLevel.MEDIUM); // Default is MEDIUM
    }

    @Test
    @DisplayName("处理缺失字段")
    void parseAndDeduplicate_MissingFields() {
        // Arrange
        String json = """
            [
                {
                    "title": "Minimal Question"
                }
            ]
            """;

        // Act
        List<QuestionDeduplicationService.GeneratedQuestion> result = service.parseAndDeduplicate(json);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title).isEqualTo("Minimal Question");
        assertThat(result.get(0).questionType).isNull();
        assertThat(result.get(0).difficulty).isNull();
        assertThat(result.get(0).description).isNull();
    }

    @Test
    @DisplayName("解析包含所有题型")
    void parseAndDeduplicate_AllQuestionTypes() {
        // Arrange
        String json = """
            [
                {"questionType": "SELECT_BASIC", "difficulty": "EASY", "title": "Basic", "description": "D", "databaseContext": "DB", "expectedSql": "SELECT 1"},
                {"questionType": "SELECT_JOIN", "difficulty": "EASY", "title": "Join", "description": "D", "databaseContext": "DB", "expectedSql": "SELECT 1"},
                {"questionType": "SELECT_SUBQUERY", "difficulty": "EASY", "title": "Subquery", "description": "D", "databaseContext": "DB", "expectedSql": "SELECT 1"},
                {"questionType": "SELECT_AGGREGATE", "difficulty": "EASY", "title": "Aggregate", "description": "D", "databaseContext": "DB", "expectedSql": "SELECT 1"},
                {"questionType": "SELECT_COMPLEX", "difficulty": "EASY", "title": "Complex", "description": "D", "databaseContext": "DB", "expectedSql": "SELECT 1"},
                {"questionType": "DML_INSERT", "difficulty": "EASY", "title": "Insert", "description": "D", "databaseContext": "DB", "expectedSql": "INSERT"},
                {"questionType": "DML_UPDATE", "difficulty": "EASY", "title": "Update", "description": "D", "databaseContext": "DB", "expectedSql": "UPDATE"},
                {"questionType": "DML_DELETE", "difficulty": "EASY", "title": "Delete", "description": "D", "databaseContext": "DB", "expectedSql": "DELETE"},
                {"questionType": "DDL_CREATE", "difficulty": "EASY", "title": "Create", "description": "D", "databaseContext": "DB", "expectedSql": "CREATE"},
                {"questionType": "DDL_ALTER", "difficulty": "EASY", "title": "Alter", "description": "D", "databaseContext": "DB", "expectedSql": "ALTER"}
            ]
            """;

        // Act
        List<QuestionDeduplicationService.GeneratedQuestion> result = service.parseAndDeduplicate(json);

        // Assert
        assertThat(result).hasSize(10);
        assertThat(result).allMatch(q -> q.questionType != null);
    }

    @Test
    @DisplayName("解析包含所有难度级别")
    void parseAndDeduplicate_AllDifficultyLevels() {
        // Arrange
        String json = """
            [
                {"questionType": "SELECT_BASIC", "difficulty": "EASY", "title": "Easy", "description": "D", "databaseContext": "DB", "expectedSql": "SELECT 1"},
                {"questionType": "SELECT_BASIC", "difficulty": "MEDIUM", "title": "Medium", "description": "D", "databaseContext": "DB", "expectedSql": "SELECT 1"},
                {"questionType": "SELECT_BASIC", "difficulty": "HARD", "title": "Hard", "description": "D", "databaseContext": "DB", "expectedSql": "SELECT 1"}
            ]
            """;

        // Act
        List<QuestionDeduplicationService.GeneratedQuestion> result = service.parseAndDeduplicate(json);

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result.get(0).difficulty).isEqualTo(Question.DifficultyLevel.EASY);
        assertThat(result.get(1).difficulty).isEqualTo(Question.DifficultyLevel.MEDIUM);
        assertThat(result.get(2).difficulty).isEqualTo(Question.DifficultyLevel.HARD);
    }

    @Test
    @DisplayName("处理混合有效和无效题目")
    void parseAndDeduplicate_MixedValidAndInvalid() {
        // Arrange
        String json = """
            [
                {
                    "questionType": "SELECT_BASIC",
                    "difficulty": "EASY",
                    "title": "Valid Question",
                    "description": "Valid Description",
                    "databaseContext": "Valid Context",
                    "expectedSql": "SELECT * FROM table1"
                },
                {
                    "title": "Incomplete Question"
                },
                {
                    "questionType": "SELECT_JOIN",
                    "difficulty": "MEDIUM",
                    "title": "Another Valid Question",
                    "description": "Another Valid Description",
                    "databaseContext": "Another Valid Context",
                    "expectedSql": "SELECT * FROM table2"
                }
            ]
            """;

        // Act
        List<QuestionDeduplicationService.GeneratedQuestion> result = service.parseAndDeduplicate(json);

        // Assert
        assertThat(result).hasSize(3); // All questions should be parsed, even with missing fields
    }
}
