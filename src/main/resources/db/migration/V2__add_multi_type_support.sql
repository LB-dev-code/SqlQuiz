-- Add multi-type selection support to practice_sessions
-- This allows students to select multiple question types for practice

ALTER TABLE practice_sessions
ADD COLUMN selected_types TEXT COMMENT '学生选择的题型列表（JSON）',
ADD COLUMN question_distribution TEXT COMMENT '题型分布配置（JSON）';
