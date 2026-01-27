package com.example.SqlQuiz.service;

import com.example.SqlQuiz.entity.QuizTableMetadata;
import com.example.SqlQuiz.repository.QuizTableMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Service
public class TableMetadataTestService {

    @Autowired
    private QuizTableMetadataRepository metadataRepository;

    @Autowired
    private SetupSqlExecutorService setupSqlExecutorService;

    public List<String> verifyTableMetadata() {
        List<String> results = new ArrayList<>();

        try (Connection connection = setupSqlExecutorService.getTestDataSource().getConnection()) {
            // 获取所有quiz表
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(
                "SELECT TABLE_NAME FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = 'mysql_test_db' AND TABLE_NAME LIKE 'quiz_q_%'"
            );

            List<String> tablesInDb = new ArrayList<>();
            while (rs.next()) {
                tablesInDb.add(rs.getString("TABLE_NAME"));
            }

            // 获取所有元数据记录
            List<QuizTableMetadata> metadataList = metadataRepository.findAll();

            results.add("=== 数据库中的quiz表 ===");
            for (String table : tablesInDb) {
                results.add(table);
            }

            results.add("\n=== 元数据记录 ===");
            for (QuizTableMetadata metadata : metadataList) {
                results.add("前缀: " + metadata.getTablePrefix() + 
                          ", 题目ID: " + metadata.getQuestionId() +
                          ", 创建者: " + metadata.getCreatorId() +
                          ", 活跃: " + metadata.getIsActive());
            }

            // 验证对应关系
            results.add("\n=== 验证结果 ===");
            int matchedCount = 0;
            int orphanTables = 0;
            int orphanMetadata = 0;

            for (String table : tablesInDb) {
                String prefix = extractPrefix(table);
                boolean found = false;
                for (QuizTableMetadata metadata : metadataList) {
                    if (metadata.getTablePrefix().equals(prefix)) {
                        found = true;
                        matchedCount++;
                        break;
                    }
                }
                if (!found) {
                    results.add("孤立表(无元数据): " + table);
                    orphanTables++;
                }
            }

            for (QuizTableMetadata metadata : metadataList) {
                boolean found = false;
                for (String table : tablesInDb) {
                    String prefix = extractPrefix(table);
                    if (prefix.equals(metadata.getTablePrefix())) {
                        found = true;
                        break;
                    }
                }
                if (!found && metadata.getIsActive()) {
                    results.add("孤立元数据(无表): " + metadata.getTablePrefix());
                    orphanMetadata++;
                }
            }

            results.add("\n匹配: " + matchedCount + ", 孤立表: " + orphanTables + ", 孤立元数据: " + orphanMetadata);

        } catch (Exception e) {
            results.add("验证失败: " + e.getMessage());
        }

        return results;
    }

    private String extractPrefix(String tableName) {
        if (tableName.matches("quiz_q_\\d+_[\\w]+")) {
            int lastUnderscore = tableName.lastIndexOf("_");
            if (lastUnderscore > 0) {
                return tableName.substring(0, lastUnderscore);
            }
        }
        return null;
    }
}
