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


}