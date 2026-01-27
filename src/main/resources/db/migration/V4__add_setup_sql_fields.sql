-- V4: 为自主练习添加setupSQL和tablePrefix字段
-- 用于支持AI生成题目的表结构创建和管理

-- 确保question_id字段不存在（如果V3没有执行成功）
ALTER TABLE practice_answers DROP FOREIGN KEY IF EXISTS fk_practice_answers_question;
ALTER TABLE practice_answers DROP COLUMN IF EXISTS question_id;

-- 添加setupSql字段，用于存储建表和插入数据的SQL语句
ALTER TABLE practice_answers ADD COLUMN IF NOT EXISTS setup_sql TEXT COMMENT '建表和插入数据的SQL语句';

-- 添加tablePrefix字段，用于追踪题目在testdb中使用的表前缀
ALTER TABLE practice_answers ADD COLUMN IF NOT EXISTS table_prefix VARCHAR(100) COMMENT '题目使用的表前缀（用于追踪testdb中的表）';

-- 为tablePrefix添加索引，便于查询
CREATE INDEX IF NOT EXISTS idx_practice_answers_table_prefix ON practice_answers(table_prefix);
