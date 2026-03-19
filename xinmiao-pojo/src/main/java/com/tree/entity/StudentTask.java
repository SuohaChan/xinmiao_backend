package com.tree.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对应数据库表：tb_student_task
 */
@Data
@TableName("tb_student_task") // 明确指定对应的数据表名
public class StudentTask implements Serializable {

    /**
     * 主键（全局 assign_id，便于后续分库分表）
     */
    @TableId
    private Long id;

    /**
     * 学生ID（关联tb_student表）
     */
    @TableField("student_id") // 与数据库字段名保持一致
    private Long studentId;

    /**
     * 任务ID（关联tb_task表）
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 任务状态（0-未完成，1-已完成）
     */
    @TableField("status")
    private Integer status;

    /**
     * 任务提交时间（仅当status=1时有意义）
     */
    @TableField("submit_time")
    private LocalDateTime submitTime; // 使用LocalDateTime更Date更推荐

    /**
     * 创建时间（首次关联任务的时间）
     * 自动填充：插入时由MyBatis-Plus自动生成
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间（状态变更时自动更新）
     * 自动填充：插入和更新时由MyBatis-Plus自动生成
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
