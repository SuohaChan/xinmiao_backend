CREATE DATABASE IF NOT EXISTS `xinmiao`;
USE `xinmiao`;


-- 任务表
DROP TABLE IF EXISTS `tb_student_task`;
DROP TABLE IF EXISTS `tb_task`;


CREATE TABLE `tb_task`
(
    `id`            BIGINT(20)                   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `label`         VARCHAR(50) COLLATE utf8_bin  DEFAULT NULL COMMENT '标注',
    `score`         INT(11)                      DEFAULT NULL COMMENT '积分',
    `top`           TINYINT(1)                   DEFAULT 0 COMMENT '是否置顶（0-否，1-是）',
    `selected`      TINYINT(1)                   DEFAULT 0 COMMENT '是否精选（0-否，1-是）',
    `title`         VARCHAR(200) COLLATE utf8_bin NOT NULL COMMENT '任务标题',
    `desc`          TEXT COLLATE utf8_bin         DEFAULT NULL COMMENT '任务简述',
    `level`         VARCHAR(20) COLLATE utf8_bin  DEFAULT NULL COMMENT '发布级别（校级、院级、班级）',
    `college`       VARCHAR(100) COLLATE utf8_bin DEFAULT NULL COMMENT '学院名称（展示用）',
    `clazz`         VARCHAR(100) COLLATE utf8_bin DEFAULT NULL COMMENT '班级名称（展示用）',
    `college_id`    BIGINT(20)                   DEFAULT NULL COMMENT '学院ID（关联tb_college.id）',
    `class_id`      BIGINT(20)                   DEFAULT NULL COMMENT '班级ID（关联tb_class.id）',
    `deadline`      DATETIME                     DEFAULT NULL COMMENT '截止时间',
    `create_time`   DATETIME                     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME                     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_published`  TINYINT(1)                   DEFAULT 0 COMMENT '是否发布（0-未发布，1-已发布）',
    `teacher_id`    BIGINT(20)                   DEFAULT NULL COMMENT '发布教师ID',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_title` (`title`) USING BTREE,
    KEY `idx_create_time` (`create_time`) USING BTREE,
    KEY `idx_college_id` (`college_id`),
    KEY `idx_class_id` (`class_id`),
    FOREIGN KEY (`teacher_id`) REFERENCES `tb_counselor` (`id`) ON DELETE SET NULL,
    FOREIGN KEY (`college_id`) REFERENCES `tb_college` (`id`) ON DELETE SET NULL,
    FOREIGN KEY (`class_id`) REFERENCES `tb_class` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8
  COLLATE = utf8_bin COMMENT = '任务';

-- 插入任务数据（college_id/class_id 与 tb_college/tb_class 关联；学院 1计算机 2机电 3数学 4人文 5经济 6医学院 7机械；班级 1软件2201 2软件2202 3机电2201 4数学一班 5汉语言文学三班 6国贸二班）
INSERT INTO `tb_task` (
    `label`, `score`, `top`, `selected`, `title`,
    `desc`, `level`, `college`, `clazz`, `college_id`, `class_id`, `deadline`,
    `create_time`, `update_time`, `is_published`, `teacher_id`
) VALUES
      ('日常任务', 5, 0, 0, '计算机学院卫生检查', '对各班级卫生情况进行检查评分', '班级', '计算机科学与技术学院', '软件2201', 1, 1, '2025-08-10 23:59:59', '2025-08-01 09:30:00', '2025-08-01 09:30:00', 1, 1),
      ('重要通知', 10, 1, 1, '全校安全教育讲座报名', '参与讲座可获得综合素质学分', '校级', '', '', NULL, NULL, '2025-09-15 18:00:00', '2025-08-01 10:15:00', '2025-08-01 10:15:00', 1, 1),
      ('作业', 3, 0, 0, '高等数学课后作业提交', '完成教材第5章习题', '班级', '数学与统计学院', '数学一班', 3, 4, '2025-08-05 22:00:00', '2025-08-01 14:20:00', '2025-08-01 14:20:00', 0, 1),
      ('活动', 8, 0, 1, '外语学院演讲比赛', '主题：我的大学生活', '院级', '外国语学院', '', NULL, NULL, '2025-08-08 17:00:00', '2025-08-01 15:00:00', '2025-08-01 16:30:00', 1, 1),
      ('调研', 15, 1, 0, '大学生消费习惯调查', '提交调查问卷可参与抽奖', '校级', '', '', NULL, NULL, '2025-08-20 20:00:00', '2025-07-28 08:45:00', '2025-07-29 11:20:00', 1, 1),
      ('会议', 0, 0, 0, '学院教学工作会议', '各系主任参加，讨论新学期教学计划', '院级', '机械工程学院', '', 7, NULL, '2025-08-12 09:00:00', '2025-08-01 11:30:00', '2025-08-01 11:30:00', 0, 1),
      ('签到', 1, 0, 0, '班主任班会签到', '周五下午班会现场签到', '班级', '经济管理学院', '国贸二班', 5, 6, '2025-08-02 17:30:00', '2025-08-01 08:10:00', '2025-08-01 08:10:00', 1, 1),
      ('竞赛', 20, 1, 1, '大学生创新创业大赛', '提交创业计划书，优胜者推荐参加省赛', '校级', '', '', NULL, NULL, '2025-08-30 00:00:00', '2025-07-25 16:40:00', '2025-07-26 09:15:00', 1, 1),
      ('实践', 6, 0, 0, '暑期社会实践报告', '提交3000字以上实践报告', '班级', '人文学院', '汉语言文学三班', 4, 5, '2025-08-18 22:00:00', '2025-08-01 13:50:00', '2025-08-01 13:50:00', 0, 1),
      ('志愿', 4, 0, 1, '校园迎新志愿者招募', '帮助新生办理入学手续', '院级', '医学院', '', 6, NULL, '2025-08-25 12:00:00', '2025-08-01 10:00:00', '2025-08-01 10:00:00', 1, 1),
      ('实践', 6, 0, 0, '原神', '提交实践报告', '校级', '', '', NULL, NULL, '2025-08-18 22:00:00', '2025-08-11 13:50:00', '2025-08-01 13:50:00', 1, 1);

CREATE TABLE `tb_student_task`
(
    `id`             BIGINT(20)                        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `student_id`     BIGINT(20)                        NOT NULL COMMENT '学生ID（关联tb_student表）',
    `task_id`        BIGINT(20)                        NOT NULL COMMENT '任务ID（关联tb_task表）',
    `status`         TINYINT(1)                        NOT NULL DEFAULT 0 COMMENT '任务状态（0-未完成，1-已完成）',
    `submit_time`    DATETIME                          DEFAULT NULL COMMENT '提交时间（任务完成时更新）',
    `create_time`    DATETIME                          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（自动填充）',
    `update_time`    DATETIME                          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（自动更新）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_student_task` (`student_id`, `task_id`),
    FOREIGN KEY (`student_id`) REFERENCES `tb_student` (`id`) ON DELETE CASCADE,
    FOREIGN KEY (`task_id`) REFERENCES `tb_task` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8
  COLLATE = utf8_bin COMMENT ='学生和任务的关联关系表（记录完成状态）';

-- 为所有已发布任务创建学生-任务关联关系
-- 1. 处理校级(校级)任务
INSERT INTO tb_student_task (student_id, task_id, status, create_time, update_time)
SELECT
    sc.student_id,
    t.id as task_id,
    0 as status,  -- 初始状态：未完成
    NOW() as create_time,
    NOW() as update_time
FROM tb_task t
         CROSS JOIN tb_student_class sc
WHERE t.level = '校级'
  AND t.is_published = 1
  AND NOT EXISTS (
    SELECT 1 FROM tb_student_task st
    WHERE st.student_id = sc.student_id
      AND st.task_id = t.id
);

-- 2. 处理院级任务（按 college_id 关联）
INSERT INTO tb_student_task (student_id, task_id, status, create_time, update_time)
SELECT
    sc.student_id,
    t.id as task_id,
    0 as status,
    NOW() as create_time,
    NOW() as update_time
FROM tb_task t
         INNER JOIN tb_student_class sc ON t.college_id = sc.college_id
WHERE t.level = '院级'
  AND t.college_id IS NOT NULL
  AND t.is_published = 1
  AND NOT EXISTS (
    SELECT 1 FROM tb_student_task st
    WHERE st.student_id = sc.student_id AND st.task_id = t.id
);

-- 3. 处理班级任务（按 class_id 关联）
INSERT INTO tb_student_task (student_id, task_id, status, create_time, update_time)
SELECT
    sc.student_id,
    t.id as task_id,
    0 as status,
    NOW() as create_time,
    NOW() as update_time
FROM tb_task t
         INNER JOIN tb_student_class sc ON t.class_id = sc.class_id
WHERE t.level = '班级'
  AND t.class_id IS NOT NULL
  AND t.is_published = 1
  AND NOT EXISTS (
    SELECT 1 FROM tb_student_task st
    WHERE st.student_id = sc.student_id AND st.task_id = t.id
);

-- 8. 原神任务（任务ID=12，院级任务）
-- 不同班级完成率有差异
UPDATE tb_student_task
SET status = 1, submit_time = NOW()
WHERE task_id = 11
  AND student_id IN (1, 2); -- 软件2201班：2/3完成
UPDATE tb_student_task
SET status = 1, submit_time = NOW()
WHERE task_id = 11
  AND student_id IN (4, 5); -- 软件2202班：2/4完成

UPDATE tb_student_task
SET status = 1, submit_time = NULL
WHERE task_id = 11
  AND student_id IN (8, 9, 10); -- 完成