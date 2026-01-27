package com.example.SqlQuiz.config;

import com.example.SqlQuiz.entity.User;
import com.example.SqlQuiz.service.UserService;
import com.example.SqlQuiz.service.TestDatabaseInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UserService userService;

    @Autowired
    private TestDatabaseInitializer testDatabaseInitializer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        fixDatabaseStructure();
        initializeDefaultUsers();
        initializeTestDatabase();
    }

    /**
     * 修复数据库结构
     * 删除practice_answers表中的question_id字段，添加新字段
     */
    private void fixDatabaseStructure() {
        try {
            log.info("========== 开始检查数据库结构 ==========");
            
            // 检查practice_answers表中是否存在question_id字段
            String checkColumnSql = "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "AND TABLE_NAME = 'practice_answers' " +
                    "AND COLUMN_NAME = 'question_id'";
            
            Integer columnExists = jdbcTemplate.queryForObject(checkColumnSql, Integer.class);
            
            if (columnExists != null && columnExists > 0) {
                log.warn("检测到practice_answers表中存在question_id字段，开始删除...");
                
                // 先尝试删除外键约束
                try {
                    String dropFkSql = "ALTER TABLE practice_answers DROP FOREIGN KEY fk_practice_answers_question";
                    jdbcTemplate.execute(dropFkSql);
                    log.info("成功删除外键约束: fk_practice_answers_question");
                } catch (Exception e) {
                    log.info("外键约束不存在或已删除");
                }
                
                // 删除question_id字段
                try {
                    String dropColumnSql = "ALTER TABLE practice_answers DROP COLUMN question_id";
                    jdbcTemplate.execute(dropColumnSql);
                    log.info("✅ 成功删除question_id字段");
                } catch (Exception e) {
                    log.error("删除question_id字段失败: " + e.getMessage());
                }
            } else {
                log.info("✅ practice_answers表结构正常，无question_id字段");
            }
            
            // 确保新字段存在
            ensureColumnExists("setup_sql", "TEXT", "建表和插入数据的SQL语句");
            ensureColumnExists("table_prefix", "VARCHAR(100)", "题目使用的表前缀");
            
            // 添加索引
            try {
                String checkIndexSql = "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() " +
                        "AND TABLE_NAME = 'practice_answers' " +
                        "AND INDEX_NAME = 'idx_practice_answers_table_prefix'";
                Integer indexExists = jdbcTemplate.queryForObject(checkIndexSql, Integer.class);
                
                if (indexExists == null || indexExists == 0) {
                    jdbcTemplate.execute("CREATE INDEX idx_practice_answers_table_prefix ON practice_answers(table_prefix)");
                    log.info("✅ 创建索引: idx_practice_answers_table_prefix");
                }
            } catch (Exception e) {
                log.info("索引已存在或创建失败: " + e.getMessage());
            }
            
            log.info("========== 数据库结构检查完成 ==========");
            
        } catch (Exception e) {
            log.error("数据库结构修复过程中出错: " + e.getMessage(), e);
        }
    }
    
    private void ensureColumnExists(String columnName, String columnType, String comment) {
        try {
            String checkSql = "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "AND TABLE_NAME = 'practice_answers' " +
                    "AND COLUMN_NAME = '" + columnName + "'";
            
            Integer exists = jdbcTemplate.queryForObject(checkSql, Integer.class);
            
            if (exists == null || exists == 0) {
                String addColumnSql = "ALTER TABLE practice_answers ADD COLUMN " + columnName + " " + columnType + " COMMENT '" + comment + "'";
                jdbcTemplate.execute(addColumnSql);
                log.info("✅ 添加字段: " + columnName);
            } else {
                log.info("✅ 字段已存在: " + columnName);
            }
        } catch (Exception e) {
            log.error("处理字段 " + columnName + " 时出错: " + e.getMessage());
        }
    }

    private void initializeDefaultUsers() {
        // 创建默认教师账户
        if (!userService.findByUsername("teacher").isPresent()) {
            userService.registerUser("teacher", "123456", "teacher@example.com",
                    "默认教师", User.Role.TEACHER);
            System.out.println("默认教师账户已创建: teacher/123456");
        }

        // 创建默认学生账户
        if (!userService.findByUsername("student").isPresent()) {
            userService.registerUser("student", "123456", "student@example.com",
                    "默认学生", User.Role.STUDENT);
            System.out.println("默认学生账户已创建: student/123456");
        }
    }

    private void initializeTestDatabase() {
        try {
            testDatabaseInitializer.initializeTestEnvironment();
            // 验证测试数据库是否正确初始化
//            if (testDatabaseInitializer.verifyTestEnvironment()) {
//                System.out.println("测试数据库环境初始化并验证成功");
//            } else {
//                System.err.println("测试数据库环境初始化完成但验证失败");
//            }
        } catch (Exception e) {
            System.err.println("测试数据库环境初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}