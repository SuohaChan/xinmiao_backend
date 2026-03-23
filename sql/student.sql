CREATE DATABASE IF NOT EXISTS `xinmiao`;
USE `xinmiao`;

DROP TABLE IF EXISTS `tb_student_info`;
DROP TABLE IF EXISTS `tb_student_class`;
DROP TABLE IF EXISTS `tb_student`;

CREATE TABLE `tb_student`
(
    `id`               BIGINT(20)                   NOT NULL COMMENT '主键',
    `username`         VARCHAR(32) COLLATE utf8_bin NOT NULL COMMENT '用户名',
    `password`         VARCHAR(64) COLLATE utf8_bin NOT NULL COMMENT '密码',
    `avatar`           VARCHAR(255) COLLATE utf8_bin         DEFAULT '' COMMENT '头像路径',
    `nickname`         VARCHAR(32) COLLATE utf8_bin          DEFAULT '' COMMENT '昵称',
    `level`            INT(11)                      NOT NULL DEFAULT 1 COMMENT '等级，默认1级',
    `total_experience` INT(11)                      NOT NULL DEFAULT 0 COMMENT '总经验值，默认0',
    `create_time`      DATETIME                     NOT NULL COMMENT '创建时间',
    `update_time`      DATETIME                     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_student_username` (`username`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8
  COLLATE = utf8_bin COMMENT ='学生';


-- 创建用户-学院-班级关联表
CREATE TABLE `tb_student_class`
(
    `id`           BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `student_id`   BIGINT(20)   NOT NULL COMMENT '学生ID',
    `college`      VARCHAR(100) NOT NULL DEFAULT '计算机科学与技术学院' COMMENT '所属学院，默认计算机科学与技术学院',
    `clazz`        VARCHAR(100) NOT NULL DEFAULT '软件2201' COMMENT '所属班级，默认软件2201',
    `college_id`     BIGINT(20)   NOT NULL DEFAULT 1 COMMENT '学院ID（关联tb_college.id）',
    `class_id`       BIGINT(20)   NOT NULL DEFAULT 1 COMMENT '班级ID（关联tb_class.id）',
    `counselor_id` BIGINT(20)   NOT NULL DEFAULT 1 COMMENT '辅导员ID（关联tb_counselor.id）',
    PRIMARY KEY (`id`),
    KEY `idx_student` (`student_id`),
    FOREIGN KEY (`student_id`) REFERENCES `tb_student` (`id`) ON DELETE CASCADE,
    FOREIGN KEY (`counselor_id`) REFERENCES `tb_counselor` (`id`) ON DELETE RESTRICT,
    FOREIGN KEY (`college_id`) REFERENCES `tb_college` (`id`) ON DELETE RESTRICT,
    FOREIGN KEY (`class_id`) REFERENCES `tb_class` (`id`) ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8 COMMENT ='学生-学院-班级关联表';



CREATE TABLE `tb_student_info`
(
    `id`               BIGINT(20)                    NOT NULL COMMENT '主键',
    `name`             VARCHAR(32) COLLATE utf8_bin  NOT NULL COMMENT '姓名',
    `gender`           VARCHAR(6) COLLATE utf8_bin   NOT NULL COMMENT '性别',
    `id_number`        VARCHAR(18) COLLATE utf8_bin  NOT NULL COMMENT '身份证号',
    `admission_number` VARCHAR(20) COLLATE utf8_bin  NOT NULL COMMENT '录取通知书编号',
    `school`           VARCHAR(20) COLLATE utf8_bin  NOT NULL COMMENT '学院',
    `face`             VARCHAR(255) COLLATE utf8_bin NOT NULL COMMENT '人脸信息储存路径',
    `credit`           BIGINT(20)                    NOT NULL COMMENT '积分',
    `create_time`      DATETIME                      NOT NULL COMMENT '创建时间',
    `update_time`      DATETIME                      NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    FOREIGN KEY (`id`) REFERENCES `tb_student` (`id`) ON DELETE CASCADE  -- 级联删除
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8
  COLLATE = utf8_bin COMMENT ='学生信息';

DELIMITER $$

CREATE TRIGGER after_student_insert
    AFTER INSERT
    ON tb_student
    FOR EACH ROW
BEGIN
    INSERT INTO tb_student_info (id, -- 主键
                                 name, -- 姓名
                                 gender, -- 性别
                                 id_number, -- 身份证号
                                 admission_number, -- 录取通知书编号
                                 school, -- 学院
                                 face, -- 人脸信息储存路径
                                 credit, -- 积分
                                 create_time, -- 创建时间
                                 update_time -- 更新时间
    )
    VALUES (NEW.id, -- 主键，从 tb_student 获取
            '', -- 姓名，后续完善信息时更新
            '', -- 性别，后续完善信息时更新
            '', -- 身份证号，后续完善信息时更新
            '', -- 录取通知书编号，后续完善信息时更新
            '', -- 学院，后续完善信息时更新
            '', -- 人脸信息路径，后续完善信息时更新
            0, -- 积分初始值为 0
            NOW(), -- 创建时间
            NOW()   -- 更新时间
           );
END $$

DELIMITER ;


-- 插入测试学生数据 （在触发器建立后插入学生数据）
INSERT INTO `tb_student` (`id`, `username`, `password`, `avatar`, `nickname`, `create_time`, `update_time`)
VALUES (1, 'student1', 'e10adc3949ba59abbe56e057f20f883e', '', '张三', NOW(), NOW()),
       (2, 'student2', 'e10adc3949ba59abbe56e057f20f883e', '', '李四', NOW(), NOW()),
       (3, 'student3', 'e10adc3949ba59abbe56e057f20f883e', '', '王五', NOW(), NOW()),
       (4, 'student4', 'e10adc3949ba59abbe56e057f20f883e', '', '赵六', NOW(), NOW()),
       (5, 'student5', 'e10adc3949ba59abbe56e057f20f883e', '', '钱七', NOW(), NOW()),
       (6, 'student6', 'e10adc3949ba59abbe56e057f20f883e', '', '孙八', NOW(), NOW()),
       (7, 'student7', 'e10adc3949ba59abbe56e057f20f883e', '', '周九', NOW(), NOW()),
       (8, 'student8', 'e10adc3949ba59abbe56e057f20f883e', '', '吴十', NOW(), NOW()),
       (9, 'student9', 'e10adc3949ba59abbe56e057f20f883e', '', '郑一', NOW(), NOW()),
       (10, 'student10', 'e10adc3949ba59abbe56e057f20f883e', '', '王二', NOW(), NOW());

 -- 插入测试学生信息数据

-- 插入学生-学院-班级关联数据（college_id/class_id 与 tb_college/tb_class 一致：计算机=1，机电=2；软件2201=1，软件2202=2，机电2201=3）
INSERT INTO tb_student_class (student_id, college, clazz, college_id, class_id, counselor_id) VALUES
(1, '计算机科学与技术学院', '软件2201', 1, 1, 1),
(2, '计算机科学与技术学院', '软件2201', 1, 1, 1),
(3, '计算机科学与技术学院', '软件2201', 1, 1, 1),
(4, '计算机科学与技术学院', '软件2202', 1, 2, 1),
(5, '计算机科学与技术学院', '软件2202', 1, 2, 1),
(6, '计算机科学与技术学院', '软件2202', 1, 2, 1),
(7, '计算机科学与技术学院', '软件2202', 1, 2, 1),
(8, '机电工程学院', '机电2201', 2, 3, 1),
(9, '机电工程学院', '机电2201', 2, 3, 1),
(10, '机电工程学院', '机电2201', 2, 3, 1);