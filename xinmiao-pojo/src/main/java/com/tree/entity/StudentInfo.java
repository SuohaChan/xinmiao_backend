package com.tree.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.tree.validation.ValidateGroup;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学生信息
 *
 * @TableName tb_student_info
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class StudentInfo implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 姓名
     */
    private String name;

    /**
     * 性别
     */
    private String gender;

    /**
     * 身份证号（身份验证时必填）
     */
    @NotBlank(message = "身份证号不能为空", groups = {ValidateGroup.class})
    private String idNumber;

    /**
     * 录取通知书编号（身份验证时必填）
     */
    @NotBlank(message = "录取通知书编号不能为空", groups = {ValidateGroup.class})
    private String admissionNumber;

    /**
     * 人脸信息储存路径
     */
    private String face;

    /**
     * 积分
     */
    private Long credit;

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