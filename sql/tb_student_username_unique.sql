-- tb_student.username 唯一约束（已有库迁移）
-- 执行前请先检查并处理重复用户名，例如：
--   SELECT username, COUNT(*) c FROM tb_student GROUP BY username HAVING c > 1;

USE `xinmiao`;

-- 去掉易与唯一约束冲突的默认值，改为 NOT NULL（新注册必须显式提供用户名）
ALTER TABLE `tb_student`
    MODIFY COLUMN `username` VARCHAR(32) COLLATE utf8_bin NOT NULL COMMENT '用户名';

-- 唯一索引：登录按 username 查询可走 uk_student_username
ALTER TABLE `tb_student`
    ADD UNIQUE KEY `uk_student_username` (`username`);
