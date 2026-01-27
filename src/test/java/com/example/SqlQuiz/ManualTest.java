package com.example.SqlQuiz;

import java.sql.*;

/**
 * 手动测试题目创建和表格存储
 */
public class ManualTest {
    public static void main(String[] args) {
        // 测试setupSql
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

        // 测试数据库连接
        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/mysql_test_db?useSSL=false&serverTimezone=Asia/Shanghai",
                "root",
                "123456"
            );

            System.out.println("=== 手动测试表格创建 ===");
            System.out.println("原始SQL:");
            System.out.println(setupSql);
            System.out.println("\n");

            // 模拟前缀生成
            String tablePrefix = "quiz_q_" + System.currentTimeMillis();
            System.out.println("生成的表前缀: " + tablePrefix);
            System.out.println("\n");

            // 处理SQL - 添加前缀
            String processedSql = setupSql.replaceAll(
                "(?i)(CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?)[`']?(products)[`']?",
                "$1" + tablePrefix + "_products"
            ).replaceAll(
                "(?i)(INSERT\\s+INTO\\s+)[`']?(products)[`']?",
                "$1" + tablePrefix + "_products"
            );

            System.out.println("处理后的SQL:");
            System.out.println(processedSql);
            System.out.println("\n");

            // 执行SQL
            Statement stmt = conn.createStatement();
            String[] sqlStatements = processedSql.split(";");

            for (String sql : sqlStatements) {
                String trimmedSql = sql.trim();
                if (!trimmedSql.isEmpty()) {
                    try {
                        stmt.execute(trimmedSql);
                        System.out.println("✓ 执行成功: " + trimmedSql.substring(0, Math.min(80, trimmedSql.length())) + "...");
                    } catch (SQLException e) {
                        System.err.println("✗ 执行失败: " + e.getMessage());
                    }
                }
            }

            // 验证表是否创建
            System.out.println("\n=== 验证表创建 ===");
            ResultSet rs = stmt.executeQuery(
                "SELECT TABLE_NAME FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = 'mysql_test_db' AND TABLE_NAME LIKE '" + tablePrefix + "%'"
            );

            boolean found = false;
            while (rs.next()) {
                System.out.println("✓ 表已创建: " + rs.getString("TABLE_NAME"));
                found = true;
            }

            if (!found) {
                System.err.println("✗ 未找到以 " + tablePrefix + " 开头的表");
            }

            // 查询表数据
            rs = stmt.executeQuery("SELECT * FROM " + tablePrefix + "_products");
            System.out.println("\n=== 表数据 ===");
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
