-- 创建数据库
CREATE DATABASE IF NOT EXISTS `xinmiao`;
USE `xinmiao`;
# 测试 还未与学生id 关联
-- 创建课表表
DROP TABLE IF EXISTS `tb_course`;
CREATE TABLE IF NOT EXISTS tb_course (
                                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                         student_id BIGINT, -- 新增学生 ID 属性
                                         time_period VARCHAR(20) NOT NULL,
                                         monday VARCHAR(255),
                                         tuesday VARCHAR(255),
                                         wednesday VARCHAR(255),
                                         thursday VARCHAR(255),
                                         friday VARCHAR(255)
);

-- 插入数据，为每条记录添加学生 ID，这里假设学生 ID 为 1
INSERT INTO tb_course (student_id, time_period, monday, tuesday, wednesday, thursday, friday) VALUES
                                                                                                  (1, '1-2节', '高等数学<br>张老师<br>教学楼A101', '大学英语<br>李老师<br>教学楼B202', '计算机基础<br>王老师<br>实验楼C303', '物理<br>赵老师<br>教学楼D404', '空'),
                                                                                                  (1, '3-4节', '大学物理<br>赵老师<br>教学楼A102', '线性代数<br>陈老师<br>教学楼B203', '空', '空', '体育<br>刘老师<br>体育馆'),
                                                                                                  (1, '5-6节', '实验课<br>王老师<br>实验楼A201', '空', '空', '空', '空'),
                                                                                                  (1, '7-8节', '空', '空', '空', '空', '晚自习<br>自习室E'),
                                                                                                  (1, '9节', '晚自习<br>自习室A', '晚自习<br>自习室B', '晚自习<br>自习室C', '晚自习<br>自习室D', '晚自习<br>自习室E');

ALTER TABLE tb_course ADD COLUMN sort_order INT;