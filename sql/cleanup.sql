-- 连接到MySQL服务器（无需先连接到特定数据库）
USE mysql;

-- 临时禁用外键约束检查
SET FOREIGN_KEY_CHECKS = 0;

-- 切换到目标数据库
CREATE DATABASE IF NOT EXISTS `xinmiao`;
USE `xinmiao`;

drop table if exists tb_appeal;
drop table if  exists tb_check_in;
drop table if exists tb_course;
drop table if exists tb_information;
drop table if exists tb_level_rule;
drop table if exists tb_notice;
drop table if exists tb_student_class;
drop table if exists tb_student_info;
drop table if exists tb_student_task;
drop table if exists tb_student;
drop table if exists tb_task;
drop table if exists tb_class;
drop table if exists tb_college;
drop table if exists tb_counselor;


-- 重新启用外键约束检查
SET FOREIGN_KEY_CHECKS = 1;

-- 可选：如果需要完全重建数据库，可以删除后重新创建
-- DROP DATABASE IF EXISTS `xinmiao`;
-- CREATE DATABASE `xinmiao`;
-- USE `xinmiao`;

SELECT '数据库清理完成！' AS '结果';
