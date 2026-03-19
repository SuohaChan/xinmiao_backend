CREATE DATABASE IF NOT EXISTS `xinmiao`;
# CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
USE `xinmiao`;

-- 创建学院表
DROP TABLE IF EXISTS `tb_college`;

CREATE TABLE `tb_college` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(100) NOT NULL COMMENT '学院名称',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8 COMMENT ='学院表';

INSERT INTO `tb_college` (`name`) VALUES
                                      ('计算机科学与技术学院'),
                                      ('机电工程学院'),
                                      ('数学与统计学院'),
                                      ('人文学院'),
                                      ('经济管理学院'),
                                      ('医学院'),
                                      ('机械工程学院');