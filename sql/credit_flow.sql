-- 积分流水表：每次加分写一条，用于排行榜缺 key 时按时间范围聚合，兼做审计查询。
CREATE DATABASE IF NOT EXISTS `xinmiao`;
USE `xinmiao`;

CREATE TABLE IF NOT EXISTS `tb_credit_flow`
(
    `id`          BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `student_id`  BIGINT(20)    NOT NULL COMMENT '学生ID',
    `task_id`     BIGINT(20)    NOT NULL COMMENT '任务ID',
    `credit`      INT(11)       NOT NULL COMMENT '本次获得积分',
    `college`     VARCHAR(100)  DEFAULT NULL COMMENT '学院名称（冗余，便于按学院+学年聚合）',
    `occurred_at` DATETIME      NOT NULL COMMENT '加分发生时间',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_occurred_at` (`occurred_at`) USING BTREE,
    KEY `idx_student_occurred` (`student_id`, `occurred_at`) USING BTREE,
    KEY `idx_college_occurred` (`college`(50), `occurred_at`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8
  COLLATE = utf8_bin COMMENT = '积分流水表';
