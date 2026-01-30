package com.example.SqlQuiz.entity;

import java.sql.Connection;

/**
 * 沙库上下文
 * 用于管理临时沙库的连接和元数据
 */
public class SandboxContext {
    
    /**
     * 沙库数据库名称
     */
    private String databaseName;
    
    /**
     * 数据库连接
     */
    private Connection connection;
    
    /**
     * 创建时间戳
     */
    private long createdAt;
    
    /**
     * 沙库类型 (practice/ai)
     */
    private String type;
    
    public SandboxContext() {
        this.createdAt = System.currentTimeMillis();
    }
    
    public SandboxContext(String databaseName, Connection connection, String type) {
        this.databaseName = databaseName;
        this.connection = connection;
        this.type = type;
        this.createdAt = System.currentTimeMillis();
    }
    
    public String getDatabaseName() {
        return databaseName;
    }
    
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }
    
    public Connection getConnection() {
        return connection;
    }
    
    public void setConnection(Connection connection) {
        this.connection = connection;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    @Override
    public String toString() {
        return "SandboxContext{" +
                "databaseName='" + databaseName + '\'' +
                ", type='" + type + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
