-- ====================================================================
-- MySQL沙库用户权限配置脚本
-- 用途: 为SQL Quiz系统创建沙库管理和执行用户
--
-- 使用前请先把下面两处 CHANGE_ME 替换成你自己的密码，
-- 并把相同的值填入 src/main/resources/application.properties：
--   spring.datasource.sandbox-admin.password
--   spring.datasource.sandbox-user.password
-- 该配置文件已被 .gitignore 忽略，不会进入仓库。
--
-- 注意: CREATE USER IF NOT EXISTS 对已存在的用户不会更新密码。
-- 若用户已存在、只想改密码，请改用 ALTER USER（见文件末尾）。
-- ====================================================================

-- 1. 创建沙库管理用户 (用于创建/删除临时库)
CREATE USER IF NOT EXISTS 'quiz_sandbox_admin'@'localhost' IDENTIFIED BY 'CHANGE_ME';

-- 授予沙库管理员权限: 可以创建、删除数据库
GRANT CREATE, DROP ON *.* TO 'quiz_sandbox_admin'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON *.* TO 'quiz_sandbox_admin'@'localhost';

-- 2. 创建学生沙库执行用户 (受限权限，只能操作 quiz_sb_ 开头的库)
CREATE USER IF NOT EXISTS 'quiz_sandbox_user'@'localhost' IDENTIFIED BY 'CHANGE_ME';

-- 授予对沙库的完整权限 (只限于 quiz_sb_ 开头的数据库)
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX ON `quiz\_sb\_%`.* TO 'quiz_sandbox_user'@'localhost';

-- 3. 刷新权限
FLUSH PRIVILEGES;

-- ====================================================================
-- 验证脚本 (可选执行)
-- ====================================================================

-- 显示沙库管理员用户权限
SHOW GRANTS FOR 'quiz_sandbox_admin'@'localhost';

-- 显示沙库执行用户权限
SHOW GRANTS FOR 'quiz_sandbox_user'@'localhost';

-- ====================================================================
-- 修改已有用户的密码 (需要时执行)
-- ====================================================================

-- ALTER USER 'quiz_sandbox_admin'@'localhost' IDENTIFIED BY 'CHANGE_ME';
-- ALTER USER 'quiz_sandbox_user'@'localhost'  IDENTIFIED BY 'CHANGE_ME';
-- FLUSH PRIVILEGES;

-- ====================================================================
-- 清理脚本 (需要时执行)
-- ====================================================================

-- -- 删除沙库用户
-- DROP USER IF EXISTS 'quiz_sandbox_admin'@'localhost';
-- DROP USER IF EXISTS 'quiz_sandbox_user'@'localhost';
-- FLUSH PRIVILEGES;
