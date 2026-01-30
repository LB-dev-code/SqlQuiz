package com.example.SqlQuiz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 沙库清理服务
 * 定时清理超时的沙库，防止数据库堆积
 */
@Service
@EnableScheduling
public class SandboxCleanupService {

    private static final Logger log = LoggerFactory.getLogger(SandboxCleanupService.class);

    @Autowired
    @Qualifier("sandboxAdminDataSource")
    private DataSource adminDataSource;

    // 每10分钟清理一次超过1小时的沙库
    @Scheduled(cron = "0 */10 * * * ?")
    public void cleanupOldSandboxes() {
        long cleanupTime = System.currentTimeMillis() - (60 * 60 * 1000); // 1小时前

        try (Connection conn = adminDataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 查找所有沙库
            ResultSet rs = stmt.executeQuery(
                "SELECT schema_name FROM information_schema.schemata " +
                "WHERE schema_name LIKE 'quiz\\_sb\\_%'"
            );

            int cleanedCount = 0;
            while (rs.next()) {
                String dbName = rs.getString("schema_name");

                // 从数据库名中提取时间戳判断是否需要清理
                try {
                    String timestampStr = extractTimestampFromDbName(dbName);
                    long dbCreatedTime = Long.parseLong(timestampStr);

                    if (dbCreatedTime < cleanupTime) {
                        stmt.execute("DROP DATABASE `" + dbName + "`");
                        log.info("Cleaned up old sandbox: {}", dbName);
                        cleanedCount++;
                    }
                } catch (Exception e) {
                    // 如果无法解析时间戳，跳过
                    log.warn("Unable to parse timestamp from database name: {}", dbName);
                }
            }

            if (cleanedCount > 0) {
                log.info("Sandbox cleanup completed: {} databases removed", cleanedCount);
            }

        } catch (Exception e) {
            log.error("Sandbox cleanup failed", e);
        }
    }

    /**
     * 从数据库名中提取时间戳
     * 数据库名格式: quiz_sb_practice_123_456_1234567890_abcd
     * 或: quiz_sb_ai_1234567890_abcd
     */
    private String extractTimestampFromDbName(String dbName) {
        // 分割数据库名
        String[] parts = dbName.split("_");
        
        // 时间戳通常在倒数第二部分
        if (parts.length >= 2) {
            return parts[parts.length - 2];
        }
        
        throw new IllegalArgumentException("Invalid sandbox database name format: " + dbName);
    }

    /**
     * 手动触发清理（用于测试或紧急清理）
     */
    public int manualCleanup() {
        log.info("Manual sandbox cleanup triggered");
        
        int cleanedCount = 0;
        try (Connection conn = adminDataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 查找所有沙库
            ResultSet rs = stmt.executeQuery(
                "SELECT schema_name FROM information_schema.schemata " +
                "WHERE schema_name LIKE 'quiz\\_sb\\_%'"
            );

            while (rs.next()) {
                String dbName = rs.getString("schema_name");
                stmt.execute("DROP DATABASE `" + dbName + "`");
                log.info("Manually cleaned up sandbox: {}", dbName);
                cleanedCount++;
            }

            log.info("Manual cleanup completed: {} databases removed", cleanedCount);

        } catch (Exception e) {
            log.error("Manual cleanup failed", e);
        }
        
        return cleanedCount;
    }
}
