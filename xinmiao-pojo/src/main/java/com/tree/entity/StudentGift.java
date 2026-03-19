package com.tree.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学生和礼品关系
 * @TableName tb_student_gift
 */
@Data
public class StudentGift implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 学生的id
     */
    private Long studentId;

    /**
     * 礼品的id
     */
    private Long giftId;

    /**
     * 礼品状态，0表示未完成，1表示已完成
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}