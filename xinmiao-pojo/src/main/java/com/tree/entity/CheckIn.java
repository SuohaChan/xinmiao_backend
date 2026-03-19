package com.tree.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学生签到记录实体类（包含createTime和updateTime字段）
 */
@Data
@TableName("tb_check_in")
public class CheckIn implements Serializable {
    /**
     * 主键ID（全局 assign_id，便于后续分库分表）
     */
    @TableId
    private Long id;

    /**
     * 关联学生ID
     */
    @TableField("student_id")
    private Long studentId;

    /**
     * 签到日期（精确到天）
     */
    @TableField("check_in_date")
    private LocalDate checkInDate;

    /**
     * 本次签到获得的经验值
     */
    @TableField("experience")
    private Integer experience = 5;

    /**
     * 创建时间（签到时间）
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间（可选，用于记录数据修改时间）
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 是否有效（1-有效，0-无效）
     */
    @TableField("is_valid")
    private Integer isValid = 1;

    @Serial
    private static final long serialVersionUID = 1L;
}
