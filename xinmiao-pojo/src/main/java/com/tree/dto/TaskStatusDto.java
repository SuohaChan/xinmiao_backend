package com.tree.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskStatusDto {
    // 任务 ID
    private Long id;
    // 任务标题
    private String title;
    // 任务等级（校级 / 院级 / 班级）
    private String level;
    // 截止时间
    private LocalDateTime deadline;
    // 任务积分
    private Integer score;
    // 完成状态（true = 已完成，false = 未完成）
    private boolean completed;
}