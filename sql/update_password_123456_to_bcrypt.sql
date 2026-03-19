-- 将原始密码 123456（当前为 MD5：e10adc3949ba59abbe56e057f20f883e）改为 BCrypt 存储
-- BCrypt 哈希对应明文：123456
CREATE DATABASE IF NOT EXISTS `xinmiao`;
USE `xinmiao`;

-- 学生表
UPDATE tb_student
SET password = '$2a$10$fcKHgFB1g2OLt/JrzJlczeREYOfjSThkevJbwTJbf2/k7IWhRp6H.'
WHERE password = 'e10adc3949ba59abbe56e057f20f883e';

-- 辅导员表
UPDATE tb_counselor
SET password = '$2a$10$fcKHgFB1g2OLt/JrzJlczeREYOfjSThkevJbwTJbf2/k7IWhRp6H.'
WHERE password = 'e10adc3949ba59abbe56e057f20f883e';
