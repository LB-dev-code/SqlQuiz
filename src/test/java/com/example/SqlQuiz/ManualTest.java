package com.example.SqlQuiz;

import java.sql.*;

/**
 * Manual test for question creation and table storage
 */
public class ManualTest {
    public static void main(String[] args) {
        // Test setupSql
        String setupSql = """
            CREATE TABLE products (
                id INT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(100) NOT NULL,
                price DECIMAL(10,2) NOT NULL,
                category VARCHAR(50)
            );
            
            INSERT INTO products (name, price, category) VALUES
            ('Laptop', 899.99, 'Electronics'),
            ('Mouse', 29.99, 'Electronics'),
            ('Desk', 249.99, 'Furniture');
        """;

        // Test database connection
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/mysql_test_db?useSSL=false&serverTimezone=Asia/Shanghai",
                "root",
                "123456"
            );

            System.out.println("=== Manual Table Creation Test ===");
            System.out.println("Original SQL:");
            System.out.println(setupSql);
            System.out.println("\n");

            // Simulate prefix generation
            String tablePrefix = "quiz_q_" + System.currentTimeMillis();
            System.out.println("Generated table prefix: " + tablePrefix);
            System.out.println("\n");

            // Process SQL - Add prefix
            String processedSql = setupSql.replaceAll(
                "(?i)(CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?)[`']?(products)[`']?",
                "$1" + tablePrefix + "_products"
            ).replaceAll(
                "(?i)(INSERT\\s+INTO\\s+)[`']?(products)[`']?",
                "$1" + tablePrefix + "_products"
            );

            System.out.println("Processed SQL:");
            System.out.println(processedSql);
            System.out.println("\n");

            // Execute SQL
            Statement stmt = conn.createStatement();
            String[] sqlStatements = processedSql.split(";");

            for (String sql : sqlStatements) {
                String trimmedSql = sql.trim();
                if (!trimmedSql.isEmpty()) {
                    try {
                        stmt.execute(trimmedSql);
                        System.out.println("✓ Execution successful: " + trimmedSql.substring(0, Math.min(80, trimmedSql.length())) + "...");
                    } catch (SQLException e) {
                        System.err.println("✗ Execution failed: " + e.getMessage());
                    }
                }
            }

            // Verify table creation
            System.out.println("\n=== Verify Table Creation ===");
            ResultSet rs = stmt.executeQuery(
                "SELECT TABLE_NAME FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = 'mysql_test_db' AND TABLE_NAME LIKE '" + tablePrefix + "%'"
            );

            boolean found = false;
            while (rs.next()) {
                System.out.println("✓ Table created: " + rs.getString("TABLE_NAME"));
                found = true;
            }

            if (!found) {
                System.err.println("✗ No tables found with prefix " + tablePrefix);
            }

            // Query table data
            rs = stmt.executeQuery("SELECT * FROM " + tablePrefix + "_products");
            System.out.println("\n=== Table Data ===");
            while (rs.next()) {
                System.out.println("ID: " + rs.getInt("id") + 
                                 ", Name: " + rs.getString("name") + 
                                 ", Price: " + rs.getBigDecimal("price") +
                                 ", Category: " + rs.getString("category"));
            }

            stmt.close();
            conn.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
