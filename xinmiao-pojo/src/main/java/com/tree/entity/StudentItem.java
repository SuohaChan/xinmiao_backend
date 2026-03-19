package com.tree.entity;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 学生和开学前要准备的物品的关系
 * @TableName tb_student_item
 */
@Data
public class StudentItem implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 学生的id
     */
    private Long studentId;

    /**
     * 物品的id
     */
    private Long itemId;

    /**
     * 任务状态，0表示未准备，1表示已准备
     */
    private Integer status;

    @Serial
    private static final long serialVersionUID = 1L;
}