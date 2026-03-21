-- 测试数据SQL脚本
-- 用于测试的示例测验和题目数据

-- 注意：这些SQL语句用于参考和测试，
-- 实际应用中，数据库表由JPA自动创建

-- 学生表示例数据
INSERT INTO users (username, password, email, full_name, role, enabled, created_at, updated_at) VALUES
('test_teacher', '$2a$10$encoded_password', 'teacher@test.com', 'Test Teacher', 'TEACHER', true, NOW(), NOW()),
('test_student', '$2a$10$encoded_password', 'student@test.com', 'Test Student', 'STUDENT', true, NOW(), NOW());

-- 测验表示例数据
INSERT INTO quizzes (title, description, time_limit, max_attempts, is_active, start_time, end_time, teacher_id, created_at, updated_at) VALUES
('SQL基础测试', '测试基本的SQL查询知识', 60, 3, true, NOW() - INTERVAL '1 hour', NOW() + INTERVAL '7 days', 1, NOW(), NOW()),
('SQL进阶测试', '测试JOIN和子查询', 90, 2, true, NOW() - INTERVAL '1 hour', NOW() + INTERVAL '7 days', 1, NOW(), NOW());

-- 题目表示例数据
INSERT INTO questions (content, question_type, description, database_context, expected_sql, score, difficulty_level, order_index, quiz_id, created_at, updated_at) VALUES
('查询所有用户信息', 'SELECT_BASIC', '使用SELECT *语句查询用户表', '用户表包含字段：id, username, email, created_at', 'SELECT * FROM users', 10.0, 'EASY', 1, 1, NOW(), NOW()),
('查询用户及其订单', 'SELECT_JOIN', '使用INNER JOIN连接用户表和订单表', '用户表(users)和订单表(orders)通过user_id关联', 'SELECT * FROM users u INNER JOIN orders o ON u.id = o.user_id', 15.0, 'MEDIUM', 2, 1, NOW(), NOW()),
('统计每个城市的用户数量', 'SELECT_AGGREGATE', '使用COUNT和GROUP BY', '用户表包含city字段', 'SELECT city, COUNT(*) as user_count FROM users GROUP BY city', 15.0, 'MEDIUM', 3, 1, NOW(), NOW()),
('查询积分最高的前10名用户', 'SELECT_COMPLEX', '使用ORDER BY和LIMIT', '用户表包含points字段', 'SELECT * FROM users ORDER BY points DESC LIMIT 10', 20.0, 'HARD', 1, 2, NOW(), NOW());

-- 清理测试数据的脚本
-- DELETE FROM question_answers WHERE submission_id IN (SELECT id FROM submissions WHERE quiz_id IN (SELECT id FROM quizzes WHERE title LIKE 'SQL%测试'));
-- DELETE FROM submissions WHERE quiz_id IN (SELECT id FROM quizzes WHERE title LIKE 'SQL%测试');
-- DELETE FROM questions WHERE quiz_id IN (SELECT id FROM quizzes WHERE title LIKE 'SQL%测试');
-- DELETE FROM quizzes WHERE title LIKE 'SQL%测试';
-- DELETE FROM users WHERE username LIKE 'test_%';
