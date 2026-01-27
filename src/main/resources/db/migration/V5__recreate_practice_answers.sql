-- V5: 重建practice_answers表
-- 基于PracticeAnswer实体类的完整结构

-- 删除旧表（如果存在）
DROP TABLE IF EXISTS practice_answers;

-- 创建practice_answers表
CREATE TABLE practice_answers (
    -- 主键
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    
    -- 外键：关联practice_rounds表
    practice_round_id BIGINT NOT NULL,
    
    -- 题目基本信息
    question_index INT NOT NULL COMMENT '题目在轮次中的序号（0-9）',
    question_type VARCHAR(50) COMMENT '题目类型（SELECT_BASIC, SELECT_JOIN等）',
    difficulty_level VARCHAR(20) COMMENT '难度级别（EASY, MEDIUM, HARD）',
    question_title TEXT COMMENT '题目标题',
    question_content TEXT COMMENT '题目内容描述',
    database_context TEXT COMMENT '数据库上下文（表结构信息）',
    expected_sql TEXT COMMENT '期望的正确SQL',
    
    -- 新增字段：setupSQL和表前缀
    setup_sql TEXT COMMENT '建表和插入数据的SQL语句',
    table_prefix VARCHAR(100) COMMENT '题目使用的表前缀（用于追踪testdb中的表）',
    
    -- 学生答题信息
    student_sql TEXT COMMENT '学生提交的SQL',
    execution_result TEXT COMMENT 'SQL执行结果',
    execution_error TEXT COMMENT 'SQL执行错误信息',
    is_correct BOOLEAN DEFAULT FALSE COMMENT '答案是否正确',
    score DOUBLE DEFAULT 0.0 COMMENT '得分',
    ai_feedback TEXT COMMENT 'AI生成的详细反馈和纠错建议',
    
    -- 时间相关
    answer_time TIMESTAMP NULL COMMENT '答题时间',
    time_spent_seconds INT DEFAULT 0 COMMENT '答题耗时（秒）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 外键约束
    CONSTRAINT fk_practice_answers_round 
        FOREIGN KEY (practice_round_id) 
        REFERENCES practice_rounds(id) 
        ON DELETE CASCADE,
    
    -- 索引
    INDEX idx_practice_answers_round (practice_round_id),
    INDEX idx_practice_answers_type (question_type),
    INDEX idx_practice_answers_table_prefix (table_prefix)
    
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
COMMENT='练习题目答案表 - 记录每道练习题的答案和反馈';
