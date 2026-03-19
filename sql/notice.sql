CREATE DATABASE IF NOT EXISTS `xinmiao`;
USE `xinmiao`;

DROP TABLE IF EXISTS `tb_notice`;
CREATE TABLE `tb_notice`
(
    `id`            BIGINT(20)                   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `is_required`   TINYINT(1)                   DEFAULT 1 COMMENT '是否必看（0-非必看，1-必看）',
    `title`         VARCHAR(200) COLLATE utf8_bin NOT NULL COMMENT '通知标题',
    `desc`          TEXT COLLATE utf8_bin         DEFAULT NULL COMMENT '通知简述',
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
  COLLATE = utf8_bin COMMENT = '通知';

-- 插入测试数据（所有image_urls保持默认NULL）
INSERT INTO `tb_notice` (
    `is_required`, `title`, `desc`, `scope`,
    `college`, `clazz`, `create_time`, `update_time`, `is_published`
) VALUES
      (1, '2025级新生报到须知', '包含报到流程、所需材料及宿舍分配信息', '校级',
       '', '', '2025-08-10 08:30:00', '2025-08-10 08:30:00', 1),
      (1, '计算机学院奖学金评定通知', '说明2024-2025学年奖学金申请条件及截止时间', '院级',
       '计算机科学与技术学院', '', '2025-08-09 14:20:00', '2025-08-09 16:10:00', 1),
      (0, '软件2201班班会提醒', '本周五下午3点召开学期总结班会，请携带笔记本', '班级',
       '计算机科学与技术学院', '软件2201', '2025-08-11 09:15:00', '2025-08-11 09:15:00', 1),
      (1, '全校实验室安全培训通知', '所有进入实验室的学生需参加安全培训并通过考核', '校级',
       '', '', '2025-08-08 10:00:00', '2025-08-09 11:30:00', 1),
      (0, '机电工程学院招聘会安排', '包含10家企业招聘信息及面试时间地点', '院级',
       '机电工程学院', '', '2025-08-12 15:40:00', '2025-08-12 15:40:00', 0),
      (1, '软件2203班课程调整通知', '原周三上午《数据库原理》调至周五下午，授课教师不变', '班级',
       '计算机科学与技术学院', '软件2203', '2025-08-10 16:25:00', '2025-08-10 17:05:00', 1),
      (0, '艺术与设计学院作品展预告', '展览包含油画、雕塑等作品，开放时间为周末9:00-17:00', '院级',
       '艺术与设计学院', '', '2025-08-11 11:00:00', '2025-08-11 11:00:00', 1);
