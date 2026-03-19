CREATE DATABASE IF NOT EXISTS `xinmiao`;
USE `xinmiao`;
DROP TABLE IF EXISTS `tb_appeal`;

CREATE TABLE tb_appeal (
     id                 BIGINT(20)      NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '申诉ID',
     user_id            BIGINT(20)      comment '申诉人ID',
     appeal_type        VARCHAR(20)     NOT NULL COMMENT '申诉类型 account content reward other',
     appeal_title       VARCHAR(100)    NOT NULL    COMMENT '申诉标题',
     appeal_description TEXT            NOT NULL    COMMENT '申诉内容',
     contact_info       VARCHAR(100)    NOT NULL    COMMENT '联系方式，如邮箱、电话等',
     submit_time        DATETIME        NOT NULL COMMENT '提交时间',
     accept_time        DATETIME        COMMENT '受理时间',
     processing_time    DATETIME        COMMENT '处理时间',
     complete_time      DATETIME        COMMENT '完成时间',
     handler_id         BIGINT(20)      COMMENT '处理人 ID',
     reply_content      TEXT            COMMENT '处理回复内容',
     status             VARCHAR(20)     NOT NULL DEFAULT 'submit'  COMMENT '当前状态（submit-已提交、accept-已受理、processing-处理中、completed-已完成通过、rejected-不通过）',
     create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
     update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     KEY `idx_user_id` (`user_id`) COMMENT '按申诉人查询',
     KEY `idx_handler_id` (`handler_id`) COMMENT '按处理人查询',
     KEY `idx_status` (`status`) COMMENT '按状态筛选',
     FOREIGN KEY (user_id) REFERENCES tb_student(id)   ON DELETE CASCADE,
     FOREIGN KEY (handler_id) REFERENCES tb_counselor(id)  ON DELETE SET NULL
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_bin COMMENT='学生申诉表';

INSERT INTO tb_appeal (user_id, appeal_type, appeal_title, appeal_description, contact_info, submit_time, accept_time, processing_time, complete_time, handler_id, reply_content, status)
VALUES
(1, 'account', '账号登录问题', '我的账号无法正常登录，显示密码错误，但我确定密码是正确的。请帮忙检查一下。', 'student1@example.com', '2025-01-15 09:30:00', '2025-01-15 10:00:00', '2025-01-15 10:30:00', '2025-01-15 11:00:00', 2, '已帮您重置密码，请使用新密码登录。', 'completed'),

(2, 'content', '任务内容错误', '我提交的任务被错误地标记为未完成，但我确实已经完成了所有要求。请重新审核。', 'student2@example.com', '2025-01-16 14:20:00', '2025-01-16 15:00:00', '2025-01-16 15:30:00', NULL, 3, '正在重新审核您的任务', 'completed'),

(3, 'reward', '积分奖励未到账', '我完成了上周的活动任务，但积分奖励至今未到账。请帮忙确认一下。', 'student3@example.com', '2025-01-17 11:15:00', '2025-01-17 12:00:00', NULL, NULL, 2, NULL, 'completed'),

(4, 'other', '系统功能建议', '建议增加任务提醒功能，以便更好地管理任务。', 'student4@example.com', '2025-01-18 16:45:00', NULL, NULL, NULL, NULL, NULL, 'submit'),

(5, 'account', '账号信息修改', '我需要修改账号绑定的手机号码，但系统提示无法修改。请帮忙处理。', 'student5@example.com', '2025-01-19 08:30:00', '2025-01-19 09:00:00', '2025-01-19 09:30:00', '2025-01-19 10:00:00', 2, '已为您修改绑定的手机号码，请确认。', 'completed'),

(6, 'content', '任务描述不清晰', '发布的任务描述不够清晰，导致我无法理解任务要求。请提供更详细的说明。', 'student6@example.com', '2025-01-20 13:10:00', '2025-01-20 14:00:00', '2025-01-20 14:30:00', '2025-01-20 15:00:00', 3, '已更新任务描述，请查看。', 'rejected'),

(7, 'reward', '礼品兑换问题', '我兑换的礼品至今未收到，请帮忙查询物流信息。', 'student7@example.com', '2025-01-21 10:20:00', NULL, NULL, NULL, NULL, NULL, 'submit'),

(8, 'other', '系统卡顿问题', '最近几天系统经常卡顿，影响使用体验。请优化系统性能。', 'student8@example.com', '2025-01-22 15:30:00', '2025-01-22 16:00:00', NULL, NULL, 1, NULL, 'submit');