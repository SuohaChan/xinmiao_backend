package com.tree.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学生
 *
 * @TableName tb_student
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Student implements Serializable {
    /**
     * 主键（更新时必填）
     */
    @NotNull(message = "学生ID不能为空")
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 头像路径
     */
    private String avatar;

    /**
     * 昵称
     */
    private String nickname;

    // 等级相关状态
    private Integer level = 1; // 当前等级
    private Integer totalExperience = 0; // 总经验

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