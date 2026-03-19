CREATE DATABASE IF NOT EXISTS `xinmiao`;
USE `xinmiao`;

DROP TABLE IF EXISTS `tb_information`;
CREATE TABLE `tb_information`
(
    `id`            BIGINT(20)                   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `is_required`   TINYINT(1)                   DEFAULT 0 COMMENT '是否必看（0-非必看，1-必看）',
    `title`         VARCHAR(200) COLLATE utf8_bin NOT NULL COMMENT '资讯标题',
    `desc`          TEXT COLLATE utf8_bin         DEFAULT NULL COMMENT '资讯简述',
    `scope`         VARCHAR(20) COLLATE utf8_bin  DEFAULT NULL COMMENT '发布范围（校级、院级、班级）',
    `college`       VARCHAR(100) COLLATE utf8_bin DEFAULT NULL COMMENT '学院',
    `clazz`         VARCHAR(100) COLLATE utf8_bin DEFAULT NULL COMMENT '班级',
    `image_urls`    TEXT COLLATE utf8_bin         DEFAULT NULL COMMENT '图片URL（多个用逗号分隔）',
    `create_time`   DATETIME                     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME                     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_published`  TINYINT(1)                   DEFAULT 0 COMMENT '是否发布（0-未发布，1-已发布）',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_title` (`title`) USING BTREE,
    KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8
  COLLATE = utf8_bin COMMENT = '资讯';

-- 插入测试数据（所有记录的image_urls均默认NULL）
INSERT INTO `tb_information` (
    `is_required`, `title`, `desc`, `scope`,
    `college`, `clazz`, `create_time`, `update_time`, `is_published`
) VALUES
      (1, '校园春季运动会圆满落幕', '本届运动会共有3200名师生参与，创造15项校纪录', '校级',
       '', '', '2025-08-05 10:15:00', '2025-08-05 10:15:00', 1),
      (0, '计算机学院人工智能实验室开放日', '本周六面向全院学生开放，展示最新AI研究成果', '院级',
       '计算机科学与技术学院', '', '2025-08-07 14:30:00', '2025-08-07 14:30:00', 1),
      (1, '软件2202班志愿服务活动纪实', '班级同学赴社区开展老人智能机教学活动', '班级',
       '计算机科学与技术学院', '软件2202', '2025-08-09 09:20:00', '2025-08-09 11:05:00', 1),
      (0, '校园图书馆新增电子资源数据库', '新增IEEE、Springer等5个数据库，访问指南请查看附件', '校级',
       '', '', '2025-08-11 16:40:00', '2025-08-11 16:40:00', 1),
      (1, '机电工程学院工业机器人展', '展示6台不同功能的工业机器人，包含操作演示', '院级',
       '机电工程学院', '', '2025-08-12 10:00:00', '2025-08-12 10:00:00', 0),
      (0, '2025届毕业生就业质量报告', '包含就业率、就业行业分布及平均起薪等数据', '校级',
       '', '', '2025-08-13 15:20:00', '2025-08-13 15:20:00', 1);