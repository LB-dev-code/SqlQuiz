package com.example.SqlQuiz.core;

import com.example.SqlQuiz.entity.SandboxContext;
import com.example.SqlQuiz.service.SandboxDatabaseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Core Feature: Built-in SQL Validator")
class SqlValidatorTest {

    @Test
    @DisplayName("executeSetupSql should clean escaped parentheses and execute each statement")
    void executeSetupSqlShouldCleanAndExecuteStatements() throws Exception {
        SandboxDatabaseService service = new SandboxDatabaseService();
        Connection connection = mock(Connection.class);
        Statement executeStmt = mock(Statement.class);
        Statement showTablesStmt = mock(Statement.class);
        Statement dataStmt = mock(Statement.class);
        ResultSet tables = mock(ResultSet.class);
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);

        SandboxContext context = new SandboxContext();
        context.setDatabaseName("quiz_sb_ai_test");
        context.setConnection(connection);

        when(connection.createStatement()).thenReturn(executeStmt, showTablesStmt, dataStmt);
        when(showTablesStmt.executeQuery("SHOW TABLES")).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString(1)).thenReturn("employees");
        when(dataStmt.executeQuery("SELECT * FROM `employees` LIMIT 5")).thenReturn(rows);
        when(rows.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(2);
        when(metaData.getColumnName(1)).thenReturn("id");
        when(metaData.getColumnName(2)).thenReturn("name");
        when(rows.next()).thenReturn(true, false);
        when(rows.getObject(1)).thenReturn(1);
        when(rows.getObject(2)).thenReturn("Alice");

        service.executeSetupSql(
                context,
                "CREATE TABLE employees \\(id INT, name VARCHAR(20)\\); INSERT INTO employees VALUES \\(1, 'Alice'\\);");

        verify(executeStmt).execute("CREATE TABLE employees (id INT, name VARCHAR(20))");
        verify(executeStmt).execute("INSERT INTO employees VALUES (1, 'Alice')");
    }

    @Test
    @DisplayName("executeInSandbox should reject blank SQL before touching JDBC")
    void executeInSandboxShouldRejectBlankSql() {
        SandboxDatabaseService service = new SandboxDatabaseService();
        SandboxContext context = new SandboxContext();
        context.setConnection(mock(Connection.class));

        SandboxDatabaseService.SqlExecutionResult result = service.executeInSandbox(
                context,
                "   ",
                "quiz_q_deadbeef_1738671234567");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("不能为空");
    }

    @Test
    @DisplayName("executeInSandbox should map table prefixes and return select rows")
    void executeInSandboxShouldMapPrefixAndReturnRows() throws Exception {
        SandboxDatabaseService service = new SandboxDatabaseService();
        Connection connection = mock(Connection.class);
        Statement showTablesStmt = mock(Statement.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet tables = mock(ResultSet.class);
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);

        SandboxContext context = new SandboxContext();
        context.setConnection(connection);
        context.setDatabaseName("quiz_sb_ai_test");

        String prefix = "quiz_q_deadbeef_1738671234567";
        String mappedSql = "SELECT name FROM " + prefix + "_employees WHERE id = 1";

        when(connection.createStatement()).thenReturn(showTablesStmt);
        when(showTablesStmt.executeQuery("SHOW TABLES")).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString(1)).thenReturn(prefix + "_employees");
        when(connection.prepareStatement(mappedSql)).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(rows);
        when(rows.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnLabel(1)).thenReturn("name");
        when(rows.next()).thenReturn(true, false);
        when(rows.getObject(1)).thenReturn("Alice");

        SandboxDatabaseService.SqlExecutionResult result = service.executeInSandbox(
                context,
                "SELECT name FROM employees WHERE id = 1",
                prefix);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRowCount()).isEqualTo(1);
        assertThat(result.getResultData()).containsExactly(Map.of("name", "Alice"));
        verify(connection).prepareStatement(mappedSql);
    }

    @Test
    @DisplayName("executeInSandbox should use the update path for non-select SQL")
    void executeInSandboxShouldHandleUpdates() throws Exception {
        SandboxDatabaseService service = new SandboxDatabaseService();
        Connection connection = mock(Connection.class);
        Statement showTablesStmt = mock(Statement.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet tables = mock(ResultSet.class);

        SandboxContext context = new SandboxContext();
        context.setConnection(connection);
        context.setDatabaseName("quiz_sb_ai_test");

        String prefix = "quiz_q_deadbeef_1738671234567";
        String mappedSql = "UPDATE " + prefix + "_employees SET salary = salary + 1";

        when(connection.createStatement()).thenReturn(showTablesStmt);
        when(showTablesStmt.executeQuery("SHOW TABLES")).thenReturn(tables);
        when(tables.next()).thenReturn(false);
        when(connection.prepareStatement(mappedSql)).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(3);

        SandboxDatabaseService.SqlExecutionResult result = service.executeInSandbox(
                context,
                "UPDATE employees SET salary = salary + 1",
                prefix);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRowCount()).isEqualTo(3);
        verify(connection).prepareStatement(mappedSql);
    }
}
