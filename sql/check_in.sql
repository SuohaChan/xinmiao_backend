-- 签到表（行为记录+冗余经验）
CREATE DATABASE IF NOT EXISTS `xinmiao`;
USE `xinmiao`;

DROP TABLE IF EXISTS `tb_check_in`;
CREATE TABLE `tb_check_in` (
`id`            BIGINT(20)          NOT NULL AUTO_INCREMENT COMMENT '主键',
`student_id`    BIGINT(20)          NOT NULL COMMENT '关联学生ID',
`check_in_date`  DATE                NOT NULL COMMENT '签到日期（精确到天）',
`experience`    INT(11)             NOT NULL DEFAULT 5 COMMENT '本次签到经验（固定5）',
`create_time`  DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签到具体时间',
`is_valid`      TINYINT(1)          NOT NULL DEFAULT 1 COMMENT '是否有效（1-有效，0-无效，用于后期回溯）',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_stu_date` (`student_id`, `check_in_date`) COMMENT '防止重复签到',
KEY `idx_date` (`check_in_date`) COMMENT '按日期统计签到用',
CONSTRAINT `fk_sign_stu` FOREIGN KEY (`student_id`) REFERENCES `tb_student` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT '学生签到记录表';


-- 为签到表添加update_time字段
ALTER TABLE tb_check_in
    ADD COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
--  等级规则配置表（解耦等级规则，便于动态调整）
DROP TABLE IF EXISTS `tb_level_rule`;
CREATE TABLE `tb_level_rule` (
                                 `level` INT(11) NOT NULL COMMENT '等级',
                                 `need_experience` INT(11) NOT NULL COMMENT '升级到该等级所需的总经验',
                                 `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT '等级-经验对应规则表';

-- 初始化等级规则（100经验升1级）
INSERT INTO `tb_level_rule` (`level`, `need_experience`) VALUES
                                                             (1, 0),   -- 0经验即可为1级
                                                             (2, 10), -- 累计10经验升2级
                                                             (3, 20), -- 累计20经验升3级
                                                             (4, 30); -- 以此类推...