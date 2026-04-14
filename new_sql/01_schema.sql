/*
  xinmiao 统一结构（MySQL 8.0+）
  - 全库 utf8mb4 / utf8mb4_unicode_ci
  - 简述列统一为 description（避免保留字 desc）
  - 时间列默认 CURRENT_TIMESTAMP（学生/教师表）
  - tb_student_class 外键列取消危险默认值
  - tb_course / tb_credit_flow 补充外键
*/

SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS `xinmiao` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `xinmiao`;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `tb_credit_flow`;
DROP TABLE IF EXISTS `tb_course`;
DROP TABLE IF EXISTS `tb_student_task`;
DROP TABLE IF EXISTS `tb_task`;
DROP TABLE IF EXISTS `tb_check_in`;
DROP TABLE IF EXISTS `tb_appeal`;
DROP TABLE IF EXISTS `tb_notice`;
DROP TABLE IF EXISTS `tb_information`;
DROP TABLE IF EXISTS `tb_level_rule`;
DROP TABLE IF EXISTS `tb_student_class`;
DROP TABLE IF EXISTS `tb_student_info`;
DROP TABLE IF EXISTS `tb_student`;
DROP TABLE IF EXISTS `tb_class`;
DROP TABLE IF EXISTS `tb_college`;
DROP TABLE IF EXISTS `tb_counselor`;

CREATE TABLE `tb_college` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '学院名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学院表';

CREATE TABLE `tb_class` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '班级名称',
  `college_id` bigint NOT NULL COMMENT '所属学院ID（关联 tb_college.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_college` (`college_id`),
  CONSTRAINT `tb_class_ibfk_1` FOREIGN KEY (`college_id`) REFERENCES `tb_college` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级表';

CREATE TABLE `tb_counselor` (
  `id` bigint NOT NULL COMMENT '主键',
  `username` varchar(32) DEFAULT NULL COMMENT '用户名',
  `password` varchar(64) NOT NULL COMMENT '密码（哈希）',
  `avatar` varchar(255) DEFAULT '' COMMENT '头像路径',
  `nickname` varchar(32) DEFAULT '' COMMENT '昵称',
  `college` varchar(100) NOT NULL DEFAULT '计算机科学与技术学院' COMMENT '所属学院（展示）',
  `is_admin` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否超级管理员(0否1是)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师/辅导员';

CREATE TABLE `tb_student` (
  `id` bigint NOT NULL COMMENT '主键',
  `username` varchar(32) NOT NULL COMMENT '用户名',
  `password` varchar(64) NOT NULL COMMENT '密码（哈希）',
  `avatar` varchar(255) DEFAULT '' COMMENT '头像路径',
  `nickname` varchar(32) DEFAULT '' COMMENT '昵称',
  `level` int NOT NULL DEFAULT 1 COMMENT '等级',
  `total_experience` int NOT NULL DEFAULT 0 COMMENT '总经验值',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生';

CREATE TABLE `tb_student_info` (
  `id` bigint NOT NULL COMMENT '主键（与 tb_student.id 一致）',
  `name` varchar(32) NOT NULL COMMENT '姓名',
  `gender` varchar(6) NOT NULL COMMENT '性别',
  `id_number` varchar(18) NOT NULL COMMENT '身份证号',
  `admission_number` varchar(20) NOT NULL COMMENT '录取通知书编号',
  `face` varchar(255) NOT NULL COMMENT '人脸信息路径',
  `credit` bigint NOT NULL COMMENT '积分',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  CONSTRAINT `tb_student_info_ibfk_1` FOREIGN KEY (`id`) REFERENCES `tb_student` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生扩展信息';

CREATE TABLE `tb_student_class` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `college` varchar(100) NOT NULL COMMENT '所属学院（展示冗余，以 college_id 为准）',
  `clazz` varchar(100) NOT NULL COMMENT '所属班级（展示冗余）',
  `college_id` bigint NOT NULL COMMENT '学院ID',
  `class_id` bigint NOT NULL COMMENT '班级ID',
  `counselor_id` bigint NOT NULL COMMENT '辅导员ID',
  PRIMARY KEY (`id`),
  KEY `idx_student` (`student_id`),
  KEY `idx_counselor_id` (`counselor_id`),
  KEY `idx_college_id` (`college_id`),
  KEY `idx_class_id` (`class_id`),
  CONSTRAINT `tb_student_class_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `tb_student` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `tb_student_class_ibfk_2` FOREIGN KEY (`counselor_id`) REFERENCES `tb_counselor` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `tb_student_class_ibfk_3` FOREIGN KEY (`college_id`) REFERENCES `tb_college` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `tb_student_class_ibfk_4` FOREIGN KEY (`class_id`) REFERENCES `tb_class` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生-学院-班级关联';

CREATE TABLE `tb_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `label` varchar(50) DEFAULT NULL COMMENT '标注',
  `score` int DEFAULT NULL COMMENT '积分',
  `top` tinyint(1) DEFAULT 0 COMMENT '是否置顶',
  `selected` tinyint(1) DEFAULT 0 COMMENT '是否精选',
  `title` varchar(200) NOT NULL COMMENT '任务标题',
  `description` text COMMENT '任务简述',
  `level` varchar(20) DEFAULT NULL COMMENT '发布级别（校级、院级、班级）',
  `college` varchar(100) DEFAULT NULL COMMENT '学院名称（展示）',
  `clazz` varchar(100) DEFAULT NULL COMMENT '班级名称（展示）',
  `college_id` bigint DEFAULT NULL COMMENT '学院ID',
  `class_id` bigint DEFAULT NULL COMMENT '班级ID',
  `deadline` datetime DEFAULT NULL COMMENT '截止时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_published` tinyint(1) DEFAULT 0 COMMENT '是否发布',
  `teacher_id` bigint DEFAULT NULL COMMENT '发布教师ID',
  PRIMARY KEY (`id`),
  KEY `idx_title` (`title`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_college_id` (`college_id`),
  KEY `idx_class_id` (`class_id`),
  KEY `idx_teacher_id` (`teacher_id`),
  KEY `idx_published_create` (`is_published`, `create_time`),
  CONSTRAINT `tb_task_ibfk_1` FOREIGN KEY (`teacher_id`) REFERENCES `tb_counselor` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `tb_task_ibfk_2` FOREIGN KEY (`college_id`) REFERENCES `tb_college` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `tb_task_ibfk_3` FOREIGN KEY (`class_id`) REFERENCES `tb_class` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务';

CREATE TABLE `tb_student_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0-未完成 1-已完成',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_task` (`student_id`, `task_id`),
  KEY `idx_task_id` (`task_id`),
  CONSTRAINT `tb_student_task_ibfk_1` FOREIGN KEY (`student_id`) REFERENCES `tb_student` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `tb_student_task_ibfk_2` FOREIGN KEY (`task_id`) REFERENCES `tb_task` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生任务完成关系';

CREATE TABLE `tb_check_in` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `check_in_date` date NOT NULL COMMENT '签到日期',
  `experience` int NOT NULL DEFAULT 5 COMMENT '本次签到经验',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签到时间',
  `is_valid` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否有效',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stu_date` (`student_id`, `check_in_date`),
  KEY `idx_date` (`check_in_date`),
  CONSTRAINT `fk_sign_stu` FOREIGN KEY (`student_id`) REFERENCES `tb_student` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='签到记录';

CREATE TABLE `tb_appeal` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申诉ID',
  `user_id` bigint DEFAULT NULL COMMENT '申诉人ID',
  `appeal_type` varchar(20) NOT NULL COMMENT '申诉类型',
  `appeal_title` varchar(100) NOT NULL COMMENT '申诉标题',
  `appeal_description` text NOT NULL COMMENT '申诉内容',
  `contact_info` varchar(100) NOT NULL COMMENT '联系方式',
  `submit_time` datetime NOT NULL COMMENT '提交时间',
  `accept_time` datetime DEFAULT NULL COMMENT '受理时间',
  `processing_time` datetime DEFAULT NULL COMMENT '处理时间',
  `complete_time` datetime DEFAULT NULL COMMENT '完成时间',
  `handler_id` bigint DEFAULT NULL COMMENT '处理人ID',
  `reply_content` text COMMENT '处理回复',
  `status` varchar(20) NOT NULL DEFAULT 'submit' COMMENT '状态',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_handler_id` (`handler_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `tb_appeal_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `tb_student` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `tb_appeal_ibfk_2` FOREIGN KEY (`handler_id`) REFERENCES `tb_counselor` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生申诉';

CREATE TABLE `tb_information` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `is_required` tinyint(1) DEFAULT 0 COMMENT '是否必看',
  `title` varchar(200) NOT NULL COMMENT '标题',
  `description` text COMMENT '简述',
  `scope` varchar(20) DEFAULT NULL COMMENT '发布范围',
  `college` varchar(100) DEFAULT NULL COMMENT '学院',
  `clazz` varchar(100) DEFAULT NULL COMMENT '班级',
  `image_urls` text COMMENT '图片URL',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_published` tinyint(1) DEFAULT 0 COMMENT '是否发布',
  PRIMARY KEY (`id`),
  KEY `idx_title` (`title`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_published_create` (`is_published`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资讯';

CREATE TABLE `tb_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `is_required` tinyint(1) DEFAULT 1 COMMENT '是否必看',
  `title` varchar(200) NOT NULL COMMENT '标题',
  `description` text COMMENT '简述',
  `scope` varchar(20) DEFAULT NULL COMMENT '发布范围',
  `college` varchar(100) DEFAULT NULL COMMENT '学院',
  `clazz` varchar(100) DEFAULT NULL COMMENT '班级',
  `image_urls` text COMMENT '图片URL',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_published` tinyint(1) DEFAULT 0 COMMENT '是否发布',
  PRIMARY KEY (`id`),
  KEY `idx_title` (`title`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_published_create` (`is_published`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知';

CREATE TABLE `tb_level_rule` (
  `level` int NOT NULL COMMENT '等级',
  `need_experience` int NOT NULL COMMENT '升级到该等级所需总经验',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='等级规则';

CREATE TABLE `tb_course` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` bigint DEFAULT NULL COMMENT '学生ID',
  `time_period` varchar(20) NOT NULL COMMENT '节次，如 1-2节',
  `monday` varchar(255) DEFAULT NULL,
  `tuesday` varchar(255) DEFAULT NULL,
  `wednesday` varchar(255) DEFAULT NULL,
  `thursday` varchar(255) DEFAULT NULL,
  `friday` varchar(255) DEFAULT NULL,
  `sort_order` int DEFAULT NULL COMMENT '排序',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  CONSTRAINT `fk_course_student` FOREIGN KEY (`student_id`) REFERENCES `tb_student` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课表';

CREATE TABLE `tb_credit_flow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `credit` int NOT NULL COMMENT '本次积分',
  `college` varchar(100) DEFAULT NULL COMMENT '学院名称（冗余统计）',
  `occurred_at` datetime NOT NULL COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_occurred_at` (`occurred_at`),
  KEY `idx_student_occurred` (`student_id`, `occurred_at`),
  KEY `idx_college_occurred` (`college`(64), `occurred_at`),
  CONSTRAINT `fk_credit_flow_student` FOREIGN KEY (`student_id`) REFERENCES `tb_student` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_credit_flow_task` FOREIGN KEY (`task_id`) REFERENCES `tb_task` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水';

DROP TRIGGER IF EXISTS `after_student_insert`;
DELIMITER ;;
CREATE TRIGGER `after_student_insert` AFTER INSERT ON `tb_student` FOR EACH ROW
BEGIN
  INSERT INTO `tb_student_info` (
    `id`, `name`, `gender`, `id_number`, `admission_number`, `face`, `credit`, `create_time`, `update_time`
  ) VALUES (
    NEW.`id`, '', '', '', '', '', 0, NOW(), NOW()
  );
END ;;
DELIMITER ;

SET FOREIGN_KEY_CHECKS = 1;
