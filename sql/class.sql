CREATE DATABASE IF NOT EXISTS `xinmiao`;
# CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
USE `xinmiao`;

DROP TABLE IF EXISTS `tb_class`;

CREATE TABLE `tb_class` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(100) NOT NULL COMMENT '班级名称',
    `college_id` BIGINT(20) NOT NULL COMMENT '所属学院ID（关联tb_college.id）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_college` (`college_id`),
    FOREIGN KEY (`college_id`) REFERENCES `tb_college` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8 COMMENT ='班级表';

INSERT INTO `tb_class` (`name`, `college_id`) VALUES
                                                  ('软件2201', 1),
                                                  ('软件2202', 1),
                                                  ('机电2201', 2),
                                                  ('数学一班', 3),
                                                  ('汉语言文学三班', 4),
                                                  ('国贸二班', 5);