package com.example.SqlQuiz.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

@Service
public class TestDatabaseInitializer {

    private final DataSource testDataSource;

    public TestDatabaseInitializer(@Qualifier("testDataSource") DataSource testDataSource) {
        this.testDataSource = testDataSource;
    }

    public void initializeTestEnvironment() {
        try (Connection connection = testDataSource.getConnection()) {
            System.out.println("连接到测试数据库: " + connection.getMetaData().getURL());

            // 创建示例表和数据
//            createStudentsTable(connection);
//            createCoursesTable(connection);
//            createEnrollmentsTable(connection);
//            insertSampleData(connection);

            System.out.println("测试数据库环境初始化完成");
        } catch (SQLException e) {
            System.err.println("测试数据库环境初始化失败: " + e.getMessage());
            throw new RuntimeException("测试环境创建失败: " + e.getMessage(), e);
        }
    }

//    // 添加验证方法，检查表是否已创建且包含数据
//    public boolean verifyTestEnvironment() {
//        try (Connection connection = testDataSource.getConnection()) {
//            System.out.println("验证测试数据库: " + connection.getMetaData().getURL());
//
//            // 检查students表是否存在
//            if (!tableExists(connection, "students")) {
//                System.out.println("students表不存在");
//                return false;
//            }
//
//            // 检查courses表是否存在
//            if (!tableExists(connection, "courses")) {
//                System.out.println("courses表不存在");
//                return false;
//            }
//
//            // 检查enrollments表是否存在
//            if (!tableExists(connection, "enrollments")) {
//                System.out.println("enrollments表不存在");
//                return false;
//            }
//
//            // 检查是否有数据
//            if (!hasData(connection, "students")) {
//                System.out.println("students表中没有数据");
//                return false;
//            }
//
//            System.out.println("测试数据库环境验证通过");
//            return true;
//        } catch (SQLException e) {
//            System.err.println("测试数据库环境验证失败: " + e.getMessage());
//            return false;
//        }
//    }
//
//    private boolean tableExists(Connection connection, String tableName) throws SQLException {
//        try (Statement statement = connection.createStatement();
//             ResultSet rs = statement.executeQuery("SHOW TABLES LIKE '" + tableName + "'")) {
//            return rs.next();
//        }
//    }
//
//    private boolean hasData(Connection connection, String tableName) throws SQLException {
//        try (Statement statement = connection.createStatement();
//             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
//            if (rs.next()) {
//                return rs.getInt(1) > 0;
//            }
//            return false;
//        }
//    }
//
//    private void createStudentsTable(Connection connection) throws SQLException {
//        try (Statement statement = connection.createStatement()) {
//            statement.execute("""
//                CREATE TABLE IF NOT EXISTS students (
//                    id INT PRIMARY KEY AUTO_INCREMENT,
//                    name VARCHAR(50) NOT NULL,
//                    age INT,
//                    grade VARCHAR(10),
//                    email VARCHAR(100)
//                )
//            """);
//        }
//    }
//
//    private void createCoursesTable(Connection connection) throws SQLException {
//        try (Statement statement = connection.createStatement()) {
//            statement.execute("""
//                CREATE TABLE IF NOT EXISTS courses (
//                    id INT PRIMARY KEY AUTO_INCREMENT,
//                    name VARCHAR(100) NOT NULL,
//                    credits INT,
//                    teacher VARCHAR(50)
//                )
//            """);
//        }
//    }
//
//    private void createEnrollmentsTable(Connection connection) throws SQLException {
//        try (Statement statement = connection.createStatement()) {
//            statement.execute("""
//                CREATE TABLE IF NOT EXISTS enrollments (
//                    id INT PRIMARY KEY AUTO_INCREMENT,
//                    student_id INT,
//                    course_id INT,
//                    score DECIMAL(5,2),
//                    FOREIGN KEY (student_id) REFERENCES students(id),
//                    FOREIGN KEY (course_id) REFERENCES courses(id)
//                )
//            """);
//        }
//    }
//
//    private void insertSampleData(Connection connection) throws SQLException {
//        // 插入学生数据
//        try (Statement statement = connection.createStatement()) {
//
//            statement.execute("""
//                INSERT IGNORE INTO students (name, age, grade, email) VALUES
//                ('张三', 20, 'A', 'zhangsan@example.com'),
//                ('李四', 21, 'B', 'lisi@example.com'),
//                ('王五', 19, 'A', 'wangwu@example.com'),
//                ('赵六', 22, 'C', 'zhaoliu@example.com')
//            """);
//            System.out.println("okkkkk");
//        }
//
//        // 插入课程数据
//        try (Statement statement = connection.createStatement()) {
//            statement.execute("""
//                INSERT IGNORE INTO courses (name, credits, teacher) VALUES
//                ('数据库原理', 4, '陈教授'),
//                ('操作系统', 3, '李教授'),
//                ('数据结构', 4, '王教授')
//            """);
//        }
//
//        // 插入选课数据
//        try (Statement statement = connection.createStatement()) {
//            statement.execute("""
//                INSERT IGNORE INTO enrollments (student_id, course_id, score) VALUES
//                (1, 1, 85.5),
//                (1, 2, 78.0),
//                (2, 1, 92.0),
//                (2, 3, 88.5),
//                (3, 2, 76.5),
//                (4, 1, 69.0)
//            """);
//        }
//    }
}