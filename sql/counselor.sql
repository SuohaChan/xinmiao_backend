CREATE DATABASE IF NOT EXISTS `xinmiao`;
USE `xinmiao`;

-- 教师表CREATE DATABASE IF NOT EXISTS `xinmiao`;
DROP TABLE IF EXISTS `tb_counselor`;
CREATE TABLE `tb_counselor`
(
    `id`          BIGINT(20)                   NOT NULL COMMENT '主键',
    `username`    VARCHAR(32) COLLATE utf8_bin DEFAULT 'default_value' COMMENT '用户名',
    `password`    VARCHAR(64) COLLATE utf8_bin NOT NULL COMMENT '密码',
    `avatar`      VARCHAR(255) COLLATE utf8_bin DEFAULT '' COMMENT '头像路径',
    `nickname`    VARCHAR(32) COLLATE utf8_bin  DEFAULT '' COMMENT '昵称',
    `college`     VARCHAR(100) COLLATE utf8_bin NOT NULL DEFAULT '计算机科学与技术学院' COMMENT '所属学院，默认计算机科学与技术学院',
    `is_admin`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否超级管理员(0否1是)，可管理学院/班级',
    `create_time` DATETIME                     NOT NULL COMMENT '创建时间',
    `update_time` DATETIME                     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8
  COLLATE = utf8_bin COMMENT ='教师';

-- 插入默认教师
INSERT INTO `tb_counselor` (`id`, `username`, `password`, `avatar`, `nickname`, `college`, `is_admin`, `create_time`, `update_time`)
VALUES
    (1, 'default_teacher', 'e10adc3949ba59abbe56e057f20f883e', 'https://test-for-tree.oss-cn-beijing.aliyuncs.com/2060b522.png', '系统默认教师', '计算机科学与技术学院', 1, NOW(), NOW()),
    (2, 'counselor_li', 'e10adc3949ba59abbe56e057f20f883e', '/avatar/li.jpg', '李辅导员', '计算机科学与技术学院', 0, NOW(), NOW()),
    (3, 'counselor_wang', 'e10adc3949ba59abbe56e057f20f883e', '/avatar/wang.jpg', '王辅导员', '机电工程学院', 0, NOW(), NOW());